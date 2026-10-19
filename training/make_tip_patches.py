"""
Cuts the training patches for the second-stage tip refiner out of the flattened pictures in a YOLO folder
(the one prepare_deepdarts.py / prepare_dartscribe.py / from_captures.py fill).

Every dart tip gives a 176 x 176 positive crop centred exactly on the tip; train_tip.py shifts a 96 x 96 window
inside it, so the label "how far is the tip from the window centre" is known exactly. Negatives (nothing to refine:
board, wires, numbers, flights, other darts' barrels) are cut from places 46+ px from every tip.

Only pictures where the board is flat and square-on are used (not the side-view copies or whole frames).

    python make_tip_patches.py --data yolo_darts --out tip_patches
"""
import argparse
import hashlib
import os
import re

import cv2
import numpy as np

SIZE = 640
CROP = 176
BOARD_RADIUS_PX = 0.37628 * SIZE  # the 170 mm double-outer edge, in flat pixels
PX_PER_MM = BOARD_RADIUS_PX / 170.0


def is_flat(name):
    stem = os.path.splitext(name)[0]
    if "_whole" in stem or "_side" in stem or re.match(r"(r|extra)\d+_", stem):
        return False  # side-view copies, whole frames and the repeated copies
    return not stem.startswith("extra") or "_flat" in stem  # your own photos count through their flattened copies


def source_of(name):
    return "flat" if "_flat" in name else "deepdarts"


def read_tips(path):
    tips = []
    if os.path.exists(path):
        for line in open(path):
            p = line.split()
            if len(p) >= 5 and int(p[0]) == 0:
                tips.append((float(p[1]) * SIZE, float(p[2]) * SIZE))
    return tips


def crop_at(image, x, y):
    """CROP x CROP pixels centred on the (sub-pixel) point x, y; outside the picture is grey."""
    m = np.float32([[1, 0, CROP / 2 - x], [0, 1, CROP / 2 - y]])
    return cv2.warpAffine(image, m, (CROP, CROP), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=(114, 114, 114))


def far_from_tips(point, tips, distance):
    return all(np.hypot(point[0] - t[0], point[1] - t[1]) >= distance for t in tips)


def random_board_point(rng, tips, distance=50):
    for _ in range(50):
        angle, radius = rng.uniform(0, 2 * np.pi), BOARD_RADIUS_PX * 1.05 * np.sqrt(rng.uniform())
        point = (SIZE / 2 + radius * np.cos(angle), SIZE / 2 + radius * np.sin(angle))
        if far_from_tips(point, tips, distance):
            return point
    return None


def patches_of(image, tips, rng, negatives):
    """(crop, is_tip) pairs for one picture."""
    out = [(crop_at(image, x, y), 1) for x, y in tips]
    made = 0
    for _ in range(negatives * 6):
        if made >= negatives:
            break
        if tips and rng.random() < 0.5:
            # close to a dart but off its tip: the barrel, the flight, the shadow
            x, y = tips[rng.integers(len(tips))]
            angle, distance = rng.uniform(0, 2 * np.pi), rng.uniform(46, 100)
            point = (x + distance * np.cos(angle), y + distance * np.sin(angle))
            if not far_from_tips(point, tips, 46):
                continue
        else:
            point = random_board_point(rng, tips)
            if point is None:
                continue
        out.append((crop_at(image, *point), 0))
        made += 1
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", default="yolo_darts", help="YOLO folder with images/ and labels/")
    parser.add_argument("--out", default="tip_patches")
    parser.add_argument("--max-deepdarts", type=int, default=6000, help="most DeepDarts pictures per split (they are plentiful but all alike)")
    parser.add_argument("--negatives", type=int, default=2, help="negative patches per picture")
    parser.add_argument("--seed", type=int, default=1)
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    rng = np.random.default_rng(args.seed)

    for split in ("train", "val", "test"):
        folder = os.path.join(args.data, "images", split)
        if not os.path.isdir(folder):
            continue
        names = sorted(n for n in os.listdir(folder) if n.lower().endswith((".jpg", ".jpeg", ".png")) and is_flat(n))
        own = [n for n in names if source_of(n) != "deepdarts"]
        dd = sorted((n for n in names if source_of(n) == "deepdarts"), key=lambda n: hashlib.md5(n.encode()).hexdigest())[: args.max_deepdarts]
        crops, tip_flag, source = [], [], []
        for n in own + dd:
            image = cv2.imread(os.path.join(folder, n))
            if image is None:
                continue
            image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)  # the app gives the network RGB
            if image.shape[:2] != (SIZE, SIZE):
                image = cv2.resize(image, (SIZE, SIZE), interpolation=cv2.INTER_AREA)
            tips = read_tips(os.path.join(args.data, "labels", split, os.path.splitext(n)[0] + ".txt"))
            for crop, is_tip in patches_of(image, tips, rng, args.negatives):
                crops.append(crop)
                tip_flag.append(is_tip)
                source.append(0 if source_of(n) == "deepdarts" else 1)
        if not crops:
            continue
        np.savez(os.path.join(args.out, f"{split}.npz"), crops=np.stack(crops), tip=np.array(tip_flag, np.uint8), source=np.array(source, np.uint8))
        print(f"{split}: {len(own)} own + {len(dd)} DeepDarts pictures -> {sum(tip_flag)} tip patches, {len(tip_flag) - sum(tip_flag)} negatives", flush=True)
    print(f"{PX_PER_MM:.3f} px per mm in the flat layout")


if __name__ == "__main__":
    main()
