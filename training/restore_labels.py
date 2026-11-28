"""Gives back the .labels.json files of photos whose labels only survive inside a YOLO dataset (my_photos).

The labels of Training171126_already_labelled were made earlier and only the training pictures built from them were kept.
The whole-frame picture of each photo (<name>_whole.jpg / .txt) has the photo letterboxed into 640 x 640, so its corners
and dart tips can be mapped straight back to photo pixels. After this the photos can be audited and corrected like any other:

    python restore_labels.py ../Screenshots/Training171126_already_labelled ../my_photos
    python audit_labels.py ../Screenshots/Training171126_already_labelled
"""
import argparse
import json
import os

import cv2

from from_captures import SIZE, labels_path, photos_in


def restore(photo, labels_dir):
    stem = os.path.splitext(os.path.basename(photo))[0]
    path = os.path.join(labels_dir, stem + "_whole.txt")
    if not os.path.exists(path):
        return None
    img = cv2.imread(photo)
    if img is None:
        return None
    h, w = img.shape[:2]
    scale = SIZE / max(w, h)
    ox, oy = (SIZE - round(w * scale)) // 2, (SIZE - round(h * scale)) // 2
    corners, darts = {}, []
    for line in open(path):
        parts = line.split()
        if len(parts) < 3:
            continue
        cls = int(parts[0])
        x, y = (float(parts[1]) * SIZE - ox) / scale, (float(parts[2]) * SIZE - oy) / scale
        if cls == 0:
            darts.append([x, y])
        else:
            corners[cls] = [x, y]
    if len(corners) != 4:
        return None
    return {"corners": [corners[i] for i in (1, 2, 3, 4)], "darts": darts, "skip": False, "restored": True}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("photos", help="folder with the photos")
    parser.add_argument("dataset", help="YOLO folder (images/ and labels/) built from them, e.g. ../my_photos")
    args = parser.parse_args()
    labels_dir = os.path.join(args.dataset, "labels")
    done = missing = existing = 0
    for photo in photos_in(args.photos):
        if os.path.exists(labels_path(photo)):
            existing += 1
            continue
        meta = restore(photo, labels_dir)
        if meta is None:
            missing += 1
            continue
        with open(labels_path(photo), "w") as f:
            json.dump(meta, f)
        done += 1
    print(f"{done} label files restored, {existing} already had one, {missing} have no usable labels in {args.dataset}")


if __name__ == "__main__":
    main()
