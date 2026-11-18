"""Turns a YOLO-format dataset (images/<split>, labels/<split>) into the COCO json files that YOLOX reads.

    python to_coco.py /tmp/yolo_mix

Writes <root>/annotations/instances_<split>.json for every split folder found (train, val, test). The picture files
are not copied: the json names them relative to <root>/images/<split>. Class ids are shifted by one (COCO has no id 0).
"""
import argparse
import json
import os

from PIL import Image

NAMES = ["dart", "cal1", "cal2", "cal3", "cal4"]
PICTURES = (".jpg", ".jpeg", ".png")


def convert(root, split):
    pictures, labels = os.path.join(root, "images", split), os.path.join(root, "labels", split)
    images, annotations = [], []
    for name in sorted(os.listdir(pictures)):
        stem, extension = os.path.splitext(name)
        if extension.lower() not in PICTURES:
            continue
        with Image.open(os.path.join(pictures, name)) as picture:
            width, height = picture.size
        image_id = len(images) + 1
        images.append({"id": image_id, "file_name": name, "width": width, "height": height})
        label = os.path.join(labels, stem + ".txt")
        if not os.path.exists(label):
            continue
        with open(label) as lines:
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
