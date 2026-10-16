"""Converts the DeepDarts dataset (https://ieee-dataport.org/open-access/deepdarts-dataset) to the YOLO format.

Expected layout of --dataset-dir:
    labels.pkl                          from https://github.com/wmcnally/deep-darts/tree/master/dataset
    cropped_images/800/<folder>/*.JPG   from cropped_images.zip on IEEE Dataport

Classes: 0 = dart tip, 1..4 = calibration points (see BoardGeometry.MODEL_CALIBRATION_POINTS in the app):
the outer edge of the double ring at the 5|20, 17|3, 8|11 and 13|6 wires.

Extra labelled data in the same YOLO layout (for example frames from the app's "Save every dart" captures after you
label them) can be added with --extra, which expects <dir>/images/*.jpg and <dir>/labels/*.txt.

--oblique N adds N side-view copies of every training picture. The DeepDarts pictures are all flattened to a square-on
board, but the app's phone sits beside the board, so each copy is warped the way a camera turned up to 65 degrees to
the side (with some tilt and roll, board size and position) would see it, in a letterboxed 4:3 or 3:4 frame like the
app's. The app flattens the board again before reading the darts, so the network needs both kinds of picture.
"""
import argparse
import os
import shutil

import cv2
import numpy as np
import pandas as pd

from oblique import SIZE, oblique

BOX = 0.025  # box size as a share of the image, as used by DeepDarts
VAL = {
    "d1": ["d1_02_06_2020", "d1_02_16_2020", "d1_02_22_2020"],
    "d2": ["d2_02_03_2021", "d2_02_05_2021"],
}
TEST = {
    "d1": ["d1_03_03_2020", "d1_03_19_2020", "d1_03_23_2020", "d1_03_27_2020", "d1_03_28_2020", "d1_03_30_2020", "d1_03_31_2020"],
    "d2": ["d2_03_03_2020", "d2_02_10_2021", "d2_02_03_2021_2"],
}


def split_of(folder: str) -> str:
    key = folder[:2]
    if folder in VAL.get(key, []):
        return "val"
    if folder in TEST.get(key, []):
        return "test"
    return "train"


def label_lines(xy) -> list:
    xy = np.array(xy, dtype=float)
    lines = []
    for i, (x, y) in enumerate(xy):
        if x - BOX / 2 <= 0 or x + BOX / 2 >= 1 or y - BOX / 2 <= 0 or y + BOX / 2 >= 1:
            continue
        cls = i + 1 if i < 4 else 0
        lines.append(f"{cls} {x:.6f} {y:.6f} {BOX} {BOX}")
    return lines


def side_view(img, xy, rng):
    """One random side view of a flattened picture: (letterboxed 640 image, YOLO label lines), or None if a corner is cut off."""
    frame_size = (960, 720) if rng.rand() < 0.5 else (720, 960)
    yaw = rng.uniform(5, 65) * rng.choice([-1, 1])
    share = min(rng.uniform(0.4, 0.8) * (1.0 + 0.4 * abs(yaw) / 65), 0.9)
    shift = (rng.uniform(-0.08, 0.08), rng.uniform(-0.08, 0.08))
    frame, h = oblique(img, yaw, rng.uniform(-15, 20), rng.uniform(-5, 5), frame=frame_size, board_share=share, shift=shift,
                         border=tuple(int(v) for v in rng.randint(20, 160, 3)))
    fw, fh = frame_size
    scale = SIZE / max(fw, fh)
    nw, nh = round(fw * scale), round(fh * scale)
    ox, oy = (SIZE - nw) // 2, (SIZE - nh) // 2
    canvas = np.full((SIZE, SIZE, 3), 114, np.uint8)
    canvas[oy:oy + nh, ox:ox + nw] = cv2.resize(frame, (nw, nh), interpolation=cv2.INTER_AREA)

    mapped = cv2.perspectiveTransform(np.array(xy, dtype=np.float64)[None], h)[0]
    mapped = mapped * scale + [ox, oy]
    inside_frame = lambda x, y: ox + margin < x < ox + nw - margin and oy + margin < y < oy + nh - margin
    margin = BOX / 2 * SIZE
    mapped_share = mapped / SIZE
    lines = []
    for i, ((px, py), (x, y)) in enumerate(zip(mapped, mapped_share)):
        inside = inside_frame(px, py)
        if i < 4 and not inside:
            return None
        if inside:
            lines.append(f"{i + 1 if i < 4 else 0} {x:.6f} {y:.6f} {BOX} {BOX}")
    return canvas, lines


def link(src: str, dst: str):
    try:
        os.symlink(os.path.abspath(src), dst)
    except OSError:
        shutil.copy(src, dst)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset-dir", required=True)
    parser.add_argument("--out", default="yolo_darts")
    parser.add_argument("--size", default="800")
    parser.add_argument("--datasets", nargs="+", default=["d1", "d2"], help="which DeepDarts sets to use")
    parser.add_argument("--extra", nargs="*", default=[], help="extra YOLO-format folders (images/ and labels/)")
    parser.add_argument("--oblique", type=int, default=0, help="side-view copies of every training picture")
    args = parser.parse_args()
    rng = np.random.RandomState(0)

    labels = pd.read_pickle(os.path.join(args.dataset_dir, "labels.pkl"))
    counts = {"train": 0, "val": 0, "test": 0}
    for split in counts:
        os.makedirs(os.path.join(args.out, "images", split), exist_ok=True)
        os.makedirs(os.path.join(args.out, "labels", split), exist_ok=True)

    for _, row in labels.iterrows():
        if row.img_folder[:2] not in args.datasets:
            continue
        src = os.path.join(args.dataset_dir, "cropped_images", args.size, row.img_folder, row.img_name)
        if not os.path.exists(src):
            continue
        lines = label_lines(row.xy)
        if len(lines) < 4:
            continue
        split = split_of(row.img_folder)
        stem = f"{row.img_folder}_{os.path.splitext(row.img_name)[0]}"
        link(src, os.path.join(args.out, "images", split, stem + os.path.splitext(row.img_name)[1]))
        with open(os.path.join(args.out, "labels", split, stem + ".txt"), "w") as f:
            f.write("\n".join(lines) + "\n")
        counts[split] += 1
        if split == "train" and args.oblique:
            img = cv2.imread(src)
            for k in range(args.oblique):
                made = side_view(img, row.xy, rng)
                if made is None:
                    continue
                cv2.imwrite(os.path.join(args.out, "images", split, f"{stem}_side{k}.jpg"), made[0])
                with open(os.path.join(args.out, "labels", split, f"{stem}_side{k}.txt"), "w") as f:
                    f.write("\n".join(made[1]) + "\n")
                counts[split] += 1

    for extra in args.extra:
        for name in os.listdir(os.path.join(extra, "images")):
            stem, ext = os.path.splitext(name)
            label = os.path.join(extra, "labels", stem + ".txt")
            if not os.path.exists(label):
                continue
            link(os.path.join(extra, "images", name), os.path.join(args.out, "images", "train", f"extra_{stem}{ext}"))
            shutil.copy(label, os.path.join(args.out, "labels", "train", f"extra_{stem}.txt"))
            counts["train"] += 1

    with open(os.path.join(args.out, "data.yaml"), "w") as f:
        f.write(
            f"path: {os.path.abspath(args.out)}\ntrain: images/train\nval: images/val\ntest: images/test\n"
            "names:\n  0: dart\n  1: cal1\n  2: cal2\n  3: cal3\n  4: cal4\n"
        )
    print("images per split:", counts)


if __name__ == "__main__":
    main()
