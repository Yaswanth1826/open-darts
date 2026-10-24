"""Packs a YOLO folder (images/ and labels/) into a zip with forward-slash paths, which Kaggle (Linux) can unpack.

    python training/pack_for_kaggle.py my_photos my_photos.zip
"""
import os
import sys
import zipfile

source, target = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(target, "w", zipfile.ZIP_STORED) as z:
    for root, _, names in os.walk(source):
        for name in sorted(names):
            path = os.path.join(root, name)
            z.write(path, os.path.relpath(path, source).replace(os.sep, "/"))
print(target, round(os.path.getsize(target) / 1e6), "MB")
