"""Turns a YOLO-format dataset (images/<split>, labels/<split>) into the COCO json files that YOLOX reads.

    python to_coco.py /tmp/yolo_mix

Writes <root>/annotations/instances_<split>.json for every split folder found (train, val, test). The picture files
are not copied: the json names them relative to <root>/images/<split>. Class ids are shifted by one (COCO has no id 0).
"""
import argparse
import json
import os
from concurrent.futures import ThreadPoolExecutor

from PIL import Image

NAMES = ["dart", "cal1", "cal2", "cal3", "cal4"]
PICTURES = (".jpg", ".jpeg", ".png")


def read_one(pictures, labels, name):
    """Picture size and label lines of one picture; (None, []) if the picture cannot be read."""
    try:
        with Image.open(os.path.join(pictures, name)) as picture:
            size = picture.size
    except Exception:
        return None, []
    try:
        with open(os.path.join(labels, os.path.splitext(name)[0] + ".txt")) as lines:
            return size, lines.read().splitlines()
    except OSError:
        return size, []


def convert(root, split):
    pictures, labels = os.path.join(root, "images", split), os.path.join(root, "labels", split)
    images, annotations = [], []
    names = [n for n in sorted(os.listdir(pictures)) if os.path.splitext(n)[1].lower() in PICTURES]
    read = []
    with ThreadPoolExecutor(32) as pool:   # the files may sit on slow network storage: read many at once
        for at in range(0, len(names), 2000):
            read += list(pool.map(lambda n: read_one(pictures, labels, n), names[at:at + 2000]))
            print(f"  {split}: {min(at + 2000, len(names))} / {len(names)} pictures read", flush=True)
    for name, (size, lines) in zip(names, read):
        if size is None:
            print("skipping unreadable picture:", name, flush=True)
            continue
        width, height = size
        image_id = len(images) + 1
        images.append({"id": image_id, "file_name": name, "width": width, "height": height})
        for line in lines:
            parts = line.split()
            if len(parts) != 5:
                continue
            cls = int(parts[0])
            cx, cy, w, h = (float(v) for v in parts[1:])
            box = [(cx - w / 2) * width, (cy - h / 2) * height, w * width, h * height]
            annotations.append({
                "id": len(annotations) + 1, "image_id": image_id, "category_id": cls + 1,
                "bbox": box, "area": box[2] * box[3], "iscrowd": 0,
            })
    return {
        "images": images, "annotations": annotations,
        "categories": [{"id": i + 1, "name": n} for i, n in enumerate(NAMES)],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root", help="folder holding images/ and labels/")
    args = parser.parse_args()
    os.makedirs(os.path.join(args.root, "annotations"), exist_ok=True)
    for split in ("train", "val", "test"):
        if not os.path.isdir(os.path.join(args.root, "images", split)):
            continue
        data = convert(args.root, split)
        with open(os.path.join(args.root, "annotations", f"instances_{split}.json"), "w") as out:
            json.dump(data, out)
        print(f"{split}: {len(data['images'])} pictures, {len(data['annotations'])} objects")


if __name__ == "__main__":
    main()
