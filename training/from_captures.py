"""Turns the photos collected by the app ("Collect training photos" in the camera menu) into YOLO training data, so
the network can learn from your own board, phone and room.

The app stores them in Pictures/OpenDarts/Training on the phone. Copy that folder to the PC (USB, or share them from
the Gallery). Each JPEG carries, as a comment, the four calibration points and the darts the network found, in pixels.
The network's own detections are wrong exactly where it fails, so look at every photo first with label_photos.py,
which pre-fills the detections and lets you click the dart tips right. It saves <photo>.labels.json next to each photo.
Only photos that have been looked at are used (pass --trust-model to also use the unchecked ones as they are).

The app looks at the board twice: once at the whole frame and again after flattening the board square-on with the
corners it found. Every photo therefore becomes one whole-frame sample and --flat slightly different flattened ones.

    python label_photos.py Training
    python from_captures.py Training --out my_photos
    python prepare_deepdarts.py --dataset-dir dataset --out yolo_darts --oblique 2 --extra my_photos --extra-repeat 20
"""
import argparse
import json
import math
import os

import cv2
import numpy as np

SIZE = 640
BOX = 0.025
PAD = 114
FLAT_SCALE = 0.37628  # share of the flattened image taken by the 170 mm board edge, as in TwoPassDetector
CORNER_ANGLES = [351.0, 171.0, 261.0, 81.0]  # wires of cal1..cal4, degrees clockwise from the 20


def read_comment(path):
    """The text of the first JPEG comment segment (what the app stores its detections in), or None."""
    with open(path, "rb") as f:
        data = f.read()
    if data[:2] != b"\xff\xd8":
        return None
    i = 2
    while i + 4 <= len(data) and data[i] == 0xFF:
        marker = data[i + 1]
        if marker == 0xDA or marker == 0xD9:
            break
        length = (data[i + 2] << 8) | data[i + 3]
        if marker == 0xFE:
            return data[i + 4:i + 2 + length].decode("utf-8", "replace")
        i += 2 + length
    return None


def load_meta(path):
    """What the app found in a photo: {"corners": [[x, y] * 4], "darts": [[x, y], ...], "note": str}, or None."""
    text = read_comment(path)
    if not text:
        return None
    try:
        meta = json.loads(text)
        return meta if "corners" in meta and "darts" in meta else None
    except ValueError:
        return None


def labels_path(photo):
    return os.path.splitext(photo)[0] + ".labels.json"


def flat_corners():
    return np.array([[SIZE * (0.5 + FLAT_SCALE * math.sin(math.radians(a))), SIZE * (0.5 - FLAT_SCALE * math.cos(math.radians(a)))]
                     for a in CORNER_ANGLES], dtype=np.float32)


def label_lines(corners, darts):
    lines = []
    for cls, (x, y) in [(i + 1, p) for i, p in enumerate(corners)] + [(0, p) for p in darts]:
        x, y = x / SIZE, y / SIZE
        if x - BOX / 2 <= 0 or x + BOX / 2 >= 1 or y - BOX / 2 <= 0 or y + BOX / 2 >= 1:
            if cls != 0:
                return None  # a calibration point is cut off
            continue
        lines.append(f"{cls} {x:.6f} {y:.6f} {BOX} {BOX}")
    return lines


def whole_frame(img, corners, darts):
    h, w = img.shape[:2]
    side = max(w, h)
    scale = SIZE / side
    canvas = np.full((SIZE, SIZE, 3), PAD, np.uint8)
    nw, nh = round(w * scale), round(h * scale)
    ox, oy = (SIZE - nw) // 2, (SIZE - nh) // 2
    canvas[oy:oy + nh, ox:ox + nw] = cv2.resize(img, (nw, nh), interpolation=cv2.INTER_AREA)
    move = lambda pts: [(x * scale + ox, y * scale + oy) for x, y in pts]
    return canvas, label_lines(move(corners), move(darts))


def flattened(img, corners, darts, rng, jitter):
    target = flat_corners()
    if jitter:
        target = target + rng.uniform(-jitter, jitter, target.shape).astype(np.float32) * SIZE
    h = cv2.getPerspectiveTransform(np.array(corners, np.float32), target)
    flat = cv2.warpPerspective(img, h, (SIZE, SIZE), flags=cv2.INTER_LINEAR, borderValue=(PAD, PAD, PAD))
    pts = lambda p: [tuple(q) for q in cv2.perspectiveTransform(np.array(p, np.float32)[None], h)[0]] if len(p) else []
    return flat, label_lines(target.tolist(), pts(darts))


def photos_in(folder):
    for root, _, names in os.walk(folder):
        for name in sorted(names):
            if name.lower().endswith((".jpg", ".jpeg")):
                yield os.path.join(root, name)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", help="folder with the photos from the phone (and their .labels.json files)")
    parser.add_argument("--out", default="my_photos", help="YOLO folder to create (images/ and labels/)")
    parser.add_argument("--flat", type=int, default=3, help="flattened copies per photo, each with slightly different corners")
    parser.add_argument("--trust-model", action="store_true", help="also use photos that were not checked with label_photos.py")
    args = parser.parse_args()
    rng = np.random.RandomState(0)

    os.makedirs(os.path.join(args.out, "images"), exist_ok=True)
    os.makedirs(os.path.join(args.out, "labels"), exist_ok=True)

    used = unchecked = excluded = skipped = 0
    for photo in photos_in(args.source):
        stem = os.path.splitext(os.path.basename(photo))[0]
        meta = None
        if os.path.exists(labels_path(photo)):
            meta = json.load(open(labels_path(photo)))
            if meta.get("skip"):
                excluded += 1
                continue
        elif args.trust_model:
            meta = load_meta(photo)
        else:
            unchecked += 1
            continue
        img = cv2.imread(photo)
        if img is None or meta is None or len(meta.get("corners", [])) != 4:
            skipped += 1
            continue
        corners, darts = meta["corners"], meta["darts"]
        samples = [(f"{stem}_whole", whole_frame(img, corners, darts))]
        samples += [(f"{stem}_flat{k}", flattened(img, corners, darts, rng, 0.0 if k == 0 else 0.012)) for k in range(args.flat)]
        for sample_name, (image, lines) in samples:
            if lines is None or sum(1 for l in lines if not l.startswith("0 ")) < 4:
                skipped += 1
                continue
            cv2.imwrite(os.path.join(args.out, "images", sample_name + ".jpg"), image, [cv2.IMWRITE_JPEG_QUALITY, 95])
            with open(os.path.join(args.out, "labels", sample_name + ".txt"), "w") as f:
                f.write("\n".join(lines) + "\n")
            used += 1
    print(f"{used} training pictures written to {args.out}; {unchecked} photos not checked yet (run label_photos.py), "
          f"{excluded} left out on purpose, {skipped} unusable (a corner cut off)")


if __name__ == "__main__":
    main()