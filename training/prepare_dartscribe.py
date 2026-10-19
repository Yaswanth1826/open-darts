"""Adds the dartscribe dataset (https://huggingface.co/datasets/geforcefan/dartscribe) to the YOLO training data.

Why: it is the only public set with pictures taken from the side, low over the board, with clear-flight darts, which is
what a phone beside the board sees. Every picture has the 81 wire crossings of the board in pixels and every throw has the
real segment, ring and board position of each dart, so the corners and the dart tips come out of the labels without any
clicking: a homography is fitted from the 81 crossings to the board in millimetres, the four calibration points are the
crossings of the double ring's outer wire at the 5|20, 17|3, 8|11 and 13|6 wires, and each dart tip is its board position
carried through the same homography.

    python prepare_dartscribe.py --download dartscribe          (once, about 2 GB; needs: pip install pyyaml)
    python prepare_deepdarts.py --dataset-dir dataset --out yolo_darts --oblique 2
    python prepare_dartscribe.py --dataset-dir dartscribe --out yolo_darts

Run it after prepare_deepdarts.py with the same --out: it adds to yolo_darts/{images,labels}/{train,val,test}. Whole
sessions go to one split (frames of a session look alike, so splitting inside a session would leak). Like the app, each
photo gives one whole-frame picture and --flat flattened ones. Throws are used as they were recorded: "throw-n" shows
the darts 1..n, "throw-0" is the empty board; the take-out frames are not used.

Labels are checked: a picture is skipped if a dart's board position does not give the segment and ring it was labelled with
(--lenient uses the position anyway). Darts that missed the board are not in the picture. Many labels were made by the author's
app (source: detected), so a tip can be some millimetres off.
The dataset is CC-BY-SA-4.0 (Ercan Akyuerek): keep the attribution, and see the licence before shipping a trained model.
"""
import argparse
import concurrent.futures
import math
import os
import shutil
import socket
import time
import urllib.request

import cv2
import numpy as np

from from_captures import CORNER_ANGLES, SIZE, flattened, whole_frame

BUCKET = "YaswanthNimmala/dartscribe-bucket"  # a copy of https://huggingface.co/datasets/geforcefan/dartscribe
SECTORS = [20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5]
IGNORE_BEYOND_MM = 176.0  # the app ignores marks this far from the bull


class Board:
    """Ring radii in millimetres, from the data.yaml of a picture."""

    def __init__(self, spec):
        self.double_outer = spec["double_outer_diameter"] / 2
        self.double_inner = self.double_outer - spec["double_bed_size"]
        self.treble_outer = spec["treble_outer_diameter"] / 2
        self.treble_inner = self.treble_outer - spec["treble_bed_size"]
        self.bull_outer = spec["bull_outer_diameter"] / 2
        self.bull_inner = spec["bull_inner_diameter"] / 2
        self.units = [spec["board_diameter"] / 2, self.double_outer, spec["board_diameter"], spec["double_outer_diameter"]]

    def ring_radius(self, ring):
        return {"DoubleOuter": self.double_outer, "DoubleInner": self.double_inner,
                "TrebleOuter": self.treble_outer, "TrebleInner": self.treble_inner}[ring]

    def hit(self, x, y):
        """(segment, multiplier) at x, y millimetres from the bull (x right, y down); (0, 0) off the board."""
        r = math.hypot(x, y)
        if r <= self.bull_inner:
            return 25, 2
        if r <= self.bull_outer:
            return 25, 1
        if r > self.double_outer:
            return 0, 0
        number = SECTORS[int(((math.degrees(math.atan2(x, -y)) + 9) % 360) // 18)]
        if self.treble_inner <= r <= self.treble_outer:
            return number, 3
        if r >= self.double_inner:
            return number, 2
        return number, 1


def wire_angle(radial_wire):
    """Degrees clockwise from the 20 of a wire called 'Between20And1'."""
    first = int(radial_wire.replace("Between", "").split("And")[0])
    return 9 + 18 * SECTORS.index(first)


def fit(camera, board):
    """Homography from board millimetres (x right, y down) to this camera's pixels, and its worst error in pixels."""
    points = camera["board_points"]
    src, dst = [[0.0, 0.0]], [[points["bull"]["x"], points["bull"]["y"]]]
    for c in points["crossings"]:
        a = math.radians(wire_angle(c["radial_wire"]))
        r = board.ring_radius(c["ring_wire"])
        src.append([r * math.sin(a), -r * math.cos(a)])
        dst.append([c["position"]["x"], c["position"]["y"]])
    h, _ = cv2.findHomography(np.float32(src), np.float32(dst), cv2.RANSAC, 3.0)
    if h is None:
        return None, math.inf
    projected = cv2.perspectiveTransform(np.float32(src)[None], h)[0]
    return h, float(np.hypot(*(projected - np.float32(dst)).T).max())


def project(h, points):
    return [tuple(map(float, p)) for p in cv2.perspectiveTransform(np.float32(points)[None], h)[0]] if len(points) else []


def corners_px(h, board):
    return project(h, [[board.double_outer * math.sin(math.radians(a)), -board.double_outer * math.cos(math.radians(a))] for a in CORNER_ANGLES])


def pick_unit(throws, board):
    """board_position is a fraction of some length (with y pointing up, unlike the picture); the length that reproduces the labelled scores is the right one."""
    best, best_share = None, -1.0
    for unit in board.units:
        right = total = 0
        for t in throws:
            p = t["hit"]["board_position"]
            total += 1
            right += board.hit(p["x"] * unit, -p["y"] * unit) == (t["hit"]["segment"], t["hit"]["multiplier"])
        if total and right / total > best_share:
            best, best_share = unit, right / total
    return best, best_share


def find_unit(throws_root, sessions, wanted=80):
    """Decides the unit from the first few dozen scored throws (a handful can match by luck with a wrong unit)."""
    sample, board = [], None
    for session in sessions:
        for group in sorted(os.listdir(os.path.join(throws_root, session))):
            path = os.path.join(throws_root, session, group, "data.yaml")
            if not os.path.exists(path):
                continue
            spec = load_yaml(path)
            board = Board(spec["board"])
            sample += [t for t in spec.get("dart_throws") or [] if t.get("hit") and t["hit"]["multiplier"] > 0]
            if len(sample) >= wanted:
                break
        if len(sample) >= wanted:
            break
    if not sample:
        return None
    unit, share = pick_unit(sample, board)
    print(f"board_position is a fraction of {unit} mm ({share:.0%} of {len(sample)} labels reproduce their score)", flush=True)
    return unit


def split_of(index, count):
    """Whole sessions to one split: about one in nine to val and one in nine to test."""
    if count < 6:
        return "train"
    return "val" if index % 9 == 4 else "test" if index % 9 == 8 else "train"


class Writer:
    def __init__(self, out, flat, repeat, rng):
        self.out, self.flat, self.repeat, self.rng = out, flat, repeat, rng
        self.counts = {"train": 0, "val": 0, "test": 0}
        for split in self.counts:
            os.makedirs(os.path.join(out, "images", split), exist_ok=True)
            os.makedirs(os.path.join(out, "labels", split), exist_ok=True)

    def add(self, split, stem, image, corners, darts):
        samples = [(f"{stem}_whole", whole_frame(image, corners, darts))]
        samples += [(f"{stem}_flat{k}", flattened(image, corners, darts, self.rng, 0.0 if k == 0 else 0.012)) for k in range(self.flat)]
        made = 0
        for name, (picture, lines) in samples:
            if lines is None or sum(1 for l in lines if not l.startswith("0 ")) < 4:
                continue
            image_path = os.path.join(self.out, "images", split, name + ".jpg")
            label_path = os.path.join(self.out, "labels", split, name + ".txt")
            cv2.imwrite(image_path, picture, [cv2.IMWRITE_JPEG_QUALITY, 95])
            with open(label_path, "w") as f:
                f.write("\n".join(lines) + "\n")
            for k in range(1, self.repeat if split == "train" else 1):
                shutil.copy(image_path, os.path.join(self.out, "images", split, f"r{k}_{name}.jpg"))
                shutil.copy(label_path, os.path.join(self.out, "labels", split, f"r{k}_{name}.txt"))
            made += self.repeat if split == "train" else 1
        self.counts[split] += made
        return made


def load_yaml(path):
    import yaml
    with open(path) as f:
        return yaml.safe_load(f)


def listing(bucket):
    """Every file of a public Hugging Face bucket (the listing is paged: each page links to the next one)."""
    import json
    import re
    url = f"https://huggingface.co/api/buckets/{bucket}/tree?recursive=true&limit=1000&sort=path"
    while url:
        with urllib.request.urlopen(url, timeout=60) as response:
            for entry in json.load(response):
                if entry["type"] == "file":
                    yield entry["path"]
            link = re.search(r'<([^>]+)>;\s*rel="next"', response.headers.get("Link", ""))
            url = link.group(1) if link else None


def download(target, bucket, max_groups):
    socket.setdefaulttimeout(60)  # a stalled connection must fail (and be retried), not hang the whole download
    wanted, groups = [], {}
    for f in listing(bucket):
        parts = f.split("/")
        if parts[0] == "boards" and len(parts) == 3:
            wanted.append(f)
        elif parts[0] == "throws" and len(parts) == 4 and "takeout" not in f:
            groups.setdefault(parts[1], {}).setdefault(parts[2], []).append(f)
    for found in groups.values():
        for group in sorted(found)[:max_groups or None]:
            wanted += found[group]
    print(f"downloading {len(wanted)} files to {target}", flush=True)

    def fetch(name):
        path = os.path.join(target, *name.split("/"))
        if os.path.exists(path):
            return True
        os.makedirs(os.path.dirname(path), exist_ok=True)
        for attempt in range(8):
            try:
                urllib.request.urlretrieve(f"https://huggingface.co/buckets/{bucket}/resolve/{name}", path + ".part")
                os.replace(path + ".part", path)
                return True
            except OSError:
                time.sleep(min(60, 3 * 2 ** attempt))  # the server slows down when asked too often: back off
        return False

    todo = wanted
    for attempt in range(3):
        with concurrent.futures.ThreadPoolExecutor(8) as pool:
            results = []
            for n, ok in enumerate(pool.map(fetch, todo)):
                results.append(ok)
                if n % 500 == 0:
                    print(f"{n}/{len(todo)}", flush=True)
        todo = [name for name, ok in zip(todo, results) if not ok]
        if not todo:
            break
        print(f"{len(todo)} files failed; waiting a minute and trying them again", flush=True)
        time.sleep(60)
    if todo:
        print(f"{len(todo)} files could not be downloaded (run the same command again to fetch them; the rest is kept)", flush=True)
    else:
        print("all files downloaded", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset-dir", default="dartscribe", help="folder with boards/ and throws/ (see --download)")
    parser.add_argument("--download", metavar="DIR", help="fetch the dataset into DIR first (without the take-out frames) and stop")
    parser.add_argument("--bucket", default=BUCKET, help="public Hugging Face bucket (or any bucket with the same folders) to download from")
    parser.add_argument("--max-groups", type=int, default=0, help="with --download: at most this many throw groups per session (0 = all)")
    parser.add_argument("--out", default="yolo_darts", help="YOLO folder to add to (the one prepare_deepdarts.py made)")
    parser.add_argument("--unit", type=float, help="length (mm) that board_position is a fraction of; found from the scores when left out (170 in this dataset)")
    parser.add_argument("--flat", type=int, default=2, help="flattened copies per photo (each with slightly different corners)")
    parser.add_argument("--repeat", type=int, default=3, help="copies of every training picture, so ~1500 side-view photos are not drowned by the 37000 flat ones")
    parser.add_argument("--lenient", action="store_true", help="also use pictures where a dart's board position does not give its labelled score (the position is used)")
    parser.add_argument("--no-boards", action="store_true", help="leave out the single photos of empty boards (boards/)")
    args = parser.parse_args()
    if args.download:
        download(args.download, args.bucket, args.max_groups)
        return

    rng = np.random.RandomState(0)
    writer = Writer(args.out, args.flat, args.repeat, rng)
    root = args.dataset_dir
    used = dropped = 0

    if not args.no_boards and os.path.isdir(os.path.join(root, "boards")):
        ids = sorted(os.listdir(os.path.join(root, "boards")))
        for n, board_id in enumerate(ids):
            folder = os.path.join(root, "boards", board_id)
            if not os.path.exists(os.path.join(folder, "data.yaml")):
                continue
            spec = load_yaml(os.path.join(folder, "data.yaml"))
            board = Board(spec["board"])
            for camera in spec["cameras"]:
                image = cv2.imread(os.path.join(folder, camera["name"] + ".jpg"))
                h, error = fit(camera, board)
                if image is None or h is None or error > 3.0:
                    dropped += 1
                    continue
                split = "val" if n % 10 == 4 else "test" if n % 10 == 9 else "train"
                used += bool(writer.add(split, f"ds_board_{board_id}_{camera['name']}", image, corners_px(h, board), []))
        print(f"boards done: {used} photos used", flush=True)

    throws_root = os.path.join(root, "throws")
    sessions = sorted(os.listdir(throws_root)) if os.path.isdir(throws_root) else []
    unit = args.unit or find_unit(throws_root, sessions)
    if unit is None:
        print("no scored throws found")
        return
    wrong = darts_total = 0
    for s, session in enumerate(sessions):
        split = split_of(s, len(sessions))
        for group in sorted(os.listdir(os.path.join(throws_root, session))):
            folder = os.path.join(throws_root, session, group)
            if not os.path.exists(os.path.join(folder, "data.yaml")):
                continue
            spec = load_yaml(os.path.join(folder, "data.yaml"))
            board = Board(spec["board"])
            throws = spec.get("dart_throws") or []
            for camera in spec["cameras"]:
                h, error = fit(camera, board)
                if h is None or error > 3.0:
                    dropped += 1
                    continue
                corners = corners_px(h, board)
                for n in range(0, max([0] + [t["throw_number"] for t in throws]) + 1):
                    image = cv2.imread(os.path.join(folder, f"throw-{n}-{camera['name']}.jpg"))
                    if image is None:
                        continue
                    tips, disagree = [], False
                    for t in throws:
                        if t["throw_number"] > n:
                            continue
                        if not t.get("hit"):
                            disagree = True  # a dart without a score: unknown where it is
                            continue
                        if t["hit"]["multiplier"] == 0:
                            continue  # a dart that missed the board is not in the picture
                        p = t["hit"]["board_position"]
                        x, y = p["x"] * unit, -p["y"] * unit
                        darts_total += 1
                        if board.hit(x, y) != (t["hit"]["segment"], t["hit"]["multiplier"]):
                            wrong += 1
                            disagree = True
                        if math.hypot(x, y) <= IGNORE_BEYOND_MM:
                            tips.append([x, y])
                    if disagree and not args.lenient:
                        dropped += 1  # the label and the position tell different stories: one of them is wrong
                        continue
                    used += bool(writer.add(split, f"ds_{session[:8]}_{group[:19]}_{n}_{camera['name']}", image, corners, project(h, tips)))
        print(f"session {s + 1}/{len(sessions)} ({split}): {writer.counts}", flush=True)
    print(f"{used} photos used, {dropped} skipped; {wrong}/{darts_total} dart labels did not match their own board position" + (" (used anyway)" if args.lenient else " (their pictures were skipped)"))
    print("pictures added per split:", writer.counts)


if __name__ == "__main__":
    main()
