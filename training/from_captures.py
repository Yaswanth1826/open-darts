"""Turns the photos collected by the app ("Collect training photos" in the camera menu, then "Share training photos")
into YOLO training data, so the network can learn from your own board, phone and room.

Each sample in the zip is <stamp>.jpg plus <stamp>.json (the four calibration points and the darts the network found, in
pixels). The labels are the network's own detections, so only photos it read correctly are good teachers: samples where
you corrected a dart in the app have a <stamp>.flag file and are NOT used; they are copied with the detections drawn on
to --review so you can fix the labels by hand (or just leave them out).

The app looks at the board twice: once at the whole frame and again after flattening the board square-on with the
corners it found. Every photo therefore becomes one whole-frame sample and --flat slightly different flattened ones.

    python from_captures.py open-darts-training.zip --out my_photos
    python prepare_deepdarts.py --dataset-dir dataset --out yolo_darts --oblique 2 --extra my_photos --extra-repeat 20
"""
import argparse
import json
import math
import os
import shutil
import zipfile

import cv2
import numpy as np

SIZE = 640
BOX = 0.025
PAD = 114
FLAT_SCALE = 0.37628  # share of the flattened image taken by the 170 mm board edge, as in TwoPassDetector
CORNER_ANGLES = [351.0, 171.0, 261.0, 81.0]  # wires of cal1..cal4, degrees clockwise from the 20


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


def overlay(img, corners, darts):
    out = img.copy()
    for x, y in corners:
        cv2.circle(out, (int(x), int(y)), 8, (0, 230, 118), 2)
    for x, y in darts:
        cv2.circle(out, (int(x), int(y)), 10, (0, 0, 255), 2)
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", help="the shared zip, or the folder it was unpacked into")
    parser.add_argument("--out", default="my_photos", help="YOLO folder to create (images/ and labels/)")
    parser.add_argument("--review", default=None, help="where to put the corrected (flagged) photos, default <out>_review")
    parser.add_argument("--flat", type=int, default=3, help="flattened copies per photo, each with slightly different corners")
    args = parser.parse_args()
    rng = np.random.RandomState(0)

    source = args.source
    if zipfile.is_zipfile(source):
        folder = args.out + "_raw"
        shutil.rmtree(folder, ignore_errors=True)
        zipfile.ZipFile(source).extractall(folder)
        source = folder
    review = args.review or args.out + "_review"
    os.makedirs(os.path.join(args.out, "images"), exist_ok=True)
    os.makedirs(os.path.join(args.out, "labels"), exist_ok=True)

    used = flagged = skipped = 0
    for name in sorted(os.listdir(source)):
        stem, ext = os.path.splitext(name)
        if ext != ".json":
            continue
        img = cv2.imread(os.path.join(source, stem + ".jpg"))
        if img is None:
            continue
        meta = json.load(open(os.path.join(source, name)))
        corners, darts = meta["corners"], meta["darts"]
        if os.path.exists(os.path.join(source, stem + ".flag")):
            os.makedirs(review, exist_ok=True)
            cv2.imwrite(os.path.join(review, stem + ".jpg"), overlay(img, corners, darts))
            flagged += 1
            continue
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
    print(f"{used} training pictures written to {args.out}; {flagged} corrected photos set aside in {review}; {skipped} skipped (a corner cut off)")


if __name__ == "__main__":
    main()