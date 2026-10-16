"""Measures how the trained network copes with a camera that looks at the board from the side.

The DeepDarts training pictures are *rectified*: in all 16,050 of them the four calibration points sit at exactly the
same square, so the network has only ever seen a board that is square-on. A phone beside the board sees an ellipse.
This script fakes that by warping held-out training pictures with a pinhole camera model (yaw = how far to the side,
pitch = how far above or below) and compares two ways of reading them:

  single : one pass over the whole frame (what the app did before)
  rectify: pass 1 finds the corners, the frame is warped square-on using them, pass 2 reads darts and refines corners

Usage:
    python eval_oblique.py --zip cropped_images.zip --labels labels.pkl --model ../app/src/main/assets/dartnet.tflite
"""
import argparse
import io
import zipfile

import cv2
import numpy as np
import pandas as pd

from oblique import CAL_MM, SIZE, TEST_FOLDERS, letterbox, oblique, rect_to_mm, mm_to_rect  # noqa: F401

class Net:
    def __init__(self, path):
        from ai_edge_litert.interpreter import Interpreter
        self.it = Interpreter(model_path=path)
        self.it.allocate_tensors()
        self.inp = self.it.get_input_details()[0]
        self.out = self.it.get_output_details()[0]

    def run(self, rgb640, min_score=0.25):
        x = rgb640.astype(np.float32) / 255.0
        x = np.transpose(x, (2, 0, 1))[None]
        self.it.set_tensor(self.inp["index"], x)
        self.it.invoke()
        out = self.it.get_tensor(self.out["index"])[0]          # 9 x 8400
        boxes, scores = out[:4].T, out[4:].T
        if boxes.max() > 2:
            boxes = boxes / SIZE
        cls = scores.argmax(1)
        conf = scores.max(1)
        keep = conf >= min_score
        dets = sorted(zip(cls[keep], conf[keep], boxes[keep, 0], boxes[keep, 1]), key=lambda d: -d[1])
        picked = []
        for c, s, x, y in dets:
            if all(c != c2 or np.hypot(x - x2, y - y2) > 0.02 for c2, _, x2, y2 in picked):
                picked.append((c, s, x, y))
        return picked


def corners_from(dets):
    pts = []
    for c in range(1, 5):
        best = max((d for d in dets if d[0] == c and d[1] >= 0.3), key=lambda d: d[1], default=None)
        if best is None:
            return None
        pts.append(best)
    return pts


def read_single(net, frame):
    canvas, back = letterbox(frame)
    dets = net.run(canvas)
    cal = corners_from(dets)
    if cal is None:
        return None
    px = np.float32([back(d[2], d[3]) for d in cal])
    darts = [back(d[2], d[3]) for d in dets if d[0] == 0 and d[1] >= 0.35]
    return px, darts


def read_rectified(net, frame, rounds=1):
    first = read_single(net, frame)
    if first is None:
        return None
    px, darts = first
    for _ in range(rounds):
        canon = (mm_to_rect(CAL_MM, SIZE)).astype(np.float32)
        Hr = cv2.getPerspectiveTransform(canon, px.astype(np.float32))        # rectified px -> frame px
        rect = cv2.warpPerspective(frame, Hr, (SIZE, SIZE), flags=cv2.WARP_INVERSE_MAP | cv2.INTER_LINEAR, borderValue=(114, 114, 114))
        dets = net.run(rect)
        cal = corners_from(dets)
        if cal is None:
            return px, darts
        def to_frame(x, y):
            v = Hr @ np.array([x * SIZE, y * SIZE, 1.0])
            return v[:2] / v[2]
        px = np.float32([to_frame(d[2], d[3]) for d in cal])
        darts = [to_frame(d[2], d[3]) for d in dets if d[0] == 0 and d[1] >= 0.35]
    return px, darts


def to_mm(px_corners, pts):
    H = cv2.getPerspectiveTransform(px_corners.astype(np.float32), CAL_MM.astype(np.float32))
    if not len(pts):
        return np.zeros((0, 2))
    return cv2.perspectiveTransform(np.float32(pts)[None], H)[0]


def score(read, frame, truth_mm, tol=8.0):
    """Returns (found corners?, matched darts, errors in mm)."""
    if read is None:
        return False, 0, []
    px, darts = read
    mm = to_mm(px, darts)
    errors, used = [], set()
    for t in truth_mm:
        d = np.hypot(*(mm - t).T) if len(mm) else np.array([])
        d = [(v, i) for i, v in enumerate(d) if i not in used]
        if d and min(d)[0] <= tol * 2:
            used.add(min(d)[1])
            errors.append(min(d)[0])
    return True, len(errors), errors


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--zip", required=True)
    ap.add_argument("--labels", required=True)
    ap.add_argument("--model", required=True)
    ap.add_argument("--n", type=int, default=60)
    ap.add_argument("--yaws", type=float, nargs="+", default=[0, 20, 35, 50, 60, 70])
    ap.add_argument("--pitch", type=float, default=8.0)
    ap.add_argument("--size", default="800")
    args = ap.parse_args()

    labels = pd.read_pickle(args.labels)
    labels = labels[labels.img_folder.isin(TEST_FOLDERS)]
    labels = labels[labels.xy.map(len) > 4].sample(n=min(args.n, len(labels)), random_state=1)
    net = Net(args.model)
    zf = zipfile.ZipFile(args.zip)
    rng = np.random.RandomState(0)

    print(f"{'yaw':>4} | {'mode':8} | corners | darts found | median err | within 8mm")
    for yaw in args.yaws:
        stats = {"single": [0, 0, 0, []], "rectify": [0, 0, 0, []]}
        for _, r in labels.iterrows():
            data = zf.read(f"cropped_images/{args.size}/{r.img_folder}/{r.img_name}")
            img = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
            img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
            side = rng.choice([-1, 1])
            frame, _ = oblique(img, side * yaw, rng.uniform(-args.pitch, args.pitch), rng.uniform(-4, 4),
                               shift=(rng.uniform(-0.05, 0.05), rng.uniform(-0.05, 0.05)))
            truth = rect_to_mm(np.array(r.xy[4:], float))
            for mode, reader in (("single", read_single), ("rectify", read_rectified)):
                ok, found, errs = score(reader(net, frame), frame, truth)
                s = stats[mode]
                s[0] += 1
                s[1] += ok
                s[2] += found
                s[3] += errs
        total = sum(len(row.xy) - 4 for _, row in labels.iterrows())
        for mode, (n, ok, found, errs) in stats.items():
            e = np.array(errs) if errs else np.array([np.nan])
            print(f"{yaw:4.0f} | {mode:8} | {ok / n:6.0%} | {found / total:10.0%} | {np.nanmedian(e):8.1f}mm | {np.mean(e <= 8):8.0%}")


if __name__ == "__main__":
    main()
