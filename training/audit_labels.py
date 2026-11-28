"""Finds the photos whose labels are most likely wrong, so you can review those first (and then everything else).

The network was trained on these very labels, so where it still disagrees with them the label is the usual suspect:
a tip clicked on the barrel, a missing or an invented dart, a corner that is a few millimetres off. Each photo is
flattened with its OWN labelled corners and read by the network; the result is compared with the labels.

    python audit_labels.py ../Screenshots/Training ../Screenshots/Holdout --model ../app/src/main/assets/dartnet.tflite
    python label_photos.py ../Screenshots/Training --queue audit.json

Writes audit.json (suspicious photos first, with the reasons and what the network found) and prints a summary.
What is checked (mm are on the board):
  corner_off   a labelled corner is more than 3 mm from where the network puts it (hurts every dart in the photo)
  missing      a labelled dart that the network does not find within 6 mm (weak = it only gets a weak score)
  extra        a dart the network is sure of (>= 0.5) that has no label within 6 mm
  tip_off      the network and the label are the same dart but more than 3 mm apart (tip clicked on the barrel?)
  too_many     more than 3 darts labelled
  duplicate    two labelled darts closer than 3 mm
  no_board     the network cannot find the board with these corners (corners wrong, or a hand in the way)
"""
import argparse
import json
import os
import sys

import cv2
import numpy as np

from from_captures import FLAT_SCALE, SIZE, flat_corners, labels_path, photos_in

PX_PER_MM = SIZE * FLAT_SCALE / 170.0


class Net:
    def __init__(self, path):
        try:
            from ai_edge_litert.interpreter import Interpreter
        except ImportError:
            from tensorflow.lite import Interpreter
        self.it = Interpreter(model_path=path)
        self.it.allocate_tensors()
        self.inp = self.it.get_input_details()[0]
        self.out = self.it.get_output_details()[0]
        self.nchw = self.inp["shape"][1] == 3

    def run(self, rgb, min_score=0.08, separate=0.007):
        x = rgb.astype(np.float32) / 255.0
        if self.nchw:
            x = np.transpose(x, (2, 0, 1))
        self.it.set_tensor(self.inp["index"], x[None])
        self.it.invoke()
        out = self.it.get_tensor(self.out["index"])[0]
        if out.shape[0] != 9:
            out = out.T
        boxes, scores = out[:4].T, out[4:].T
        if boxes.max() > 2:
            boxes = boxes / SIZE
        cls, conf = scores.argmax(1), scores.max(1)
        keep = conf >= min_score
        kept = []
        for c, s, x, y in sorted(zip(cls[keep], conf[keep], boxes[keep, 0], boxes[keep, 1]), key=lambda d: -d[1]):
            if all(c != c2 or np.hypot(x - x2, y - y2) >= separate for c2, _, x2, y2 in kept):
                kept.append((int(c), float(s), float(x) * SIZE, float(y) * SIZE))
        return kept


def audit_photo(net, photo, meta):
    img = cv2.imread(photo)
    corners = np.array(meta["corners"], np.float32)
    to_flat = cv2.getPerspectiveTransform(corners, flat_corners())
    flat = cv2.warpPerspective(img, to_flat, (SIZE, SIZE), flags=cv2.INTER_LINEAR, borderValue=(114, 114, 114))
    found = net.run(cv2.cvtColor(flat, cv2.COLOR_BGR2RGB))
    to_photo = np.linalg.inv(to_flat)

    def back(x, y):
        v = to_photo @ np.array([x, y, 1.0])
        return [float(v[0] / v[2]), float(v[1] / v[2])]

    reasons, score = [], 0.0
    expected = flat_corners()
    worst = 0.0
    for i in range(4):
        best = max((d for d in found if d[0] == i + 1 and d[1] >= 0.3), key=lambda d: d[1], default=None)
        if best is None:
            worst = max(worst, 99.0)
            continue
        worst = max(worst, float(np.hypot(best[2] - expected[i][0], best[3] - expected[i][1])) / PX_PER_MM)
    if worst >= 99:
        reasons.append("no_board"); score += 6
    elif worst > 3.0:
        reasons.append("corner_off %.1f mm" % worst); score += worst

    labelled = [tuple(p) for p in cv2.perspectiveTransform(np.array(meta["darts"], np.float32)[None], to_flat)[0]] if meta["darts"] else []
    model = [d for d in found if d[0] == 0]
    sure = [d for d in model if d[1] >= 0.35]
    if len(labelled) > 3:
        reasons.append("too_many %d" % len(labelled)); score += 2
    for i in range(len(labelled)):
        for j in range(i + 1, len(labelled)):
            if np.hypot(labelled[i][0] - labelled[j][0], labelled[i][1] - labelled[j][1]) < 3 * PX_PER_MM:
                reasons.append("duplicate"); score += 2
    used = set()
    for lx, ly in labelled:
        options = [(np.hypot(d[2] - lx, d[3] - ly) / PX_PER_MM, k) for k, d in enumerate(model) if k not in used and d[1] >= 0.1]
        options = [o for o in options if o[0] <= 6.0]
        if not options:
            reasons.append("missing"); score += 3
            continue
        gap, k = min(options)
        used.add(k)
        if model[k][1] < 0.35:
            reasons.append("missing (weak %.2f)" % model[k][1]); score += 1.5
        elif gap > 3.0:
            reasons.append("tip_off %.1f mm" % gap); score += gap / 2
    for k, d in enumerate(sure):
        if d[1] >= 0.5 and not any(np.hypot(d[2] - lx, d[3] - ly) <= 6 * PX_PER_MM for lx, ly in labelled):
            reasons.append("extra (%.2f)" % d[1]); score += 3
    return {
        "path": os.path.abspath(photo),
        "score": round(score, 2),
        "reasons": reasons,
        "model_darts": [back(d[2], d[3]) + [round(d[1], 2)] for d in model if d[1] >= 0.1],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("folders", nargs="+", help="folders with photos and their .labels.json files")
    parser.add_argument("--model", default=os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "dartnet.tflite"))
    parser.add_argument("--out", default="audit.json")
    args = parser.parse_args()
    net = Net(args.model)
    results = []
    for folder in args.folders:
        paths = [p for p in photos_in(folder) if os.path.exists(labels_path(p))]
        for n, photo in enumerate(paths):
            meta = json.load(open(labels_path(photo)))
            if meta.get("skip") or len(meta.get("corners", [])) != 4:
                continue
            results.append(audit_photo(net, photo, meta))
            if (n + 1) % 50 == 0:
                print(f"{folder}: {n + 1}/{len(paths)}", file=sys.stderr, flush=True)
    results.sort(key=lambda r: -r["score"])
    json.dump(results, open(args.out, "w"), indent=1, default=float)
    flagged = [r for r in results if r["reasons"]]
    print(f"{len(results)} photos audited, {len(flagged)} flagged; written to {args.out}")
    kinds = {}
    for r in flagged:
        for reason in set(x.split(" ")[0] for x in r["reasons"]):
            kinds[reason] = kinds.get(reason, 0) + 1
    for kind, count in sorted(kinds.items(), key=lambda kv: -kv[1]):
        print(f"  {kind}: {count}")


if __name__ == "__main__":
    main()
