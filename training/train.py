"""Trains the dart detector and exports it for the Android app.

    python train.py --data yolo_darts/data.yaml --epochs 60

Result: dartnet.tflite, to be copied to app/src/main/assets/dartnet.tflite.

The augmentation is chosen so the labels stay valid: no flips (they would swap which calibration point is which),
only small rotations and perspective changes, and no mosaic (it would put several boards in one picture).
"""
import argparse
import glob
import os
import shutil

from ultralytics import YOLO


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", required=True)
    parser.add_argument("--epochs", type=int, default=60)
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--batch", type=int, default=32)
    parser.add_argument("--model", default="yolov8n.pt")
    parser.add_argument("--out", default="dartnet.tflite")
    parser.add_argument("--project", default="runs")
    args = parser.parse_args()

    model = YOLO(args.model)
    model.train(
        data=args.data, epochs=args.epochs, imgsz=args.imgsz, batch=args.batch, project=args.project, name="dartnet",
        exist_ok=True, patience=20,
        fliplr=0.0, flipud=0.0, mosaic=0.0, mixup=0.0, copy_paste=0.0,
        degrees=4.0, translate=0.08, scale=0.35, perspective=0.0004, shear=2.0,
        hsv_h=0.015, hsv_s=0.6, hsv_v=0.45,
    )
    metrics = model.val(data=args.data, split="test", imgsz=args.imgsz)
    print("test mAP50:", metrics.box.map50)

    best = YOLO(os.path.join(args.project, "dartnet", "weights", "best.pt"))
    exported = best.export(format="tflite", imgsz=args.imgsz, half=True)
    tflite = str(exported) if str(exported).endswith(".tflite") else None
    if tflite is None or not os.path.exists(tflite):
        found = glob.glob(os.path.join(args.project, "dartnet", "weights", "**", "*float16.tflite"), recursive=True)
        tflite = found[0]
    shutil.copy(tflite, args.out)
    print("wrote", args.out, "-", round(os.path.getsize(args.out) / 1e6, 1), "MB")


if __name__ == "__main__":
    main()
