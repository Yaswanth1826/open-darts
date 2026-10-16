"""Converts the DeepDarts dataset (https://ieee-dataport.org/open-access/deepdarts-dataset) to the YOLO format.

Expected layout of --dataset-dir:
    labels.pkl                          from https://github.com/wmcnally/deep-darts/tree/master/dataset
    cropped_images/800/<folder>/*.JPG   from cropped_images.zip on IEEE Dataport

Classes: 0 = dart tip, 1..4 = calibration points (see BoardGeometry.MODEL_CALIBRATION_POINTS in the app):
the outer edge of the double ring at the 5|20, 17|3, 8|11 and 13|6 wires.

Extra labelled data in the same YOLO layout (for example frames from the app's "Save every dart" captures after you
label them) can be added with --extra, which expects <dir>/images/*.jpg and <dir>/labels/*.txt.
"""
import argparse
import os
import shutil

import numpy as np
import pandas as pd

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
    args = parser.parse_args()

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
