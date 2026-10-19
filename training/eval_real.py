"""Measures how well the shipped model reads darts on your own photos, the way the app does.

    python label_photos.py Training                       (check the dart tips once)
    python eval_real.py Training --model ../app/src/main/assets/dartnet.tflite [--tips tiprefine.tflite]

Uses the photos you checked with label_photos.py (<photo>.labels.json). For each photo it runs the same two passes as
the app (whole frame to find the board, then the flattened board to find the darts), converts the darts to board
millimetres and scores them, then compares with your labels:

  score accuracy   share of real darts that got exactly the right score (segment and ring). This is the number that matters.
  recall           real darts that were found at all
  precision        found darts that are real (the rest are fake darts: barrels, logos...)
  tip error        distance in mm between the found and the real tip, for the matched darts
  perfect photos   photos where every dart was found and scored right and nothing extra was found

It is shown without and with the "keep the tip, not the barrel" step (DartAxis) so you can see what that step brings.
By default the board position of the previous look is assumed known (the app's normal state: --cold reads the board
from scratch each time). --out FOLDER saves the photos that went wrong, with your labels (green) and the result (red).
"""
import argparse
import json
import math
import os
import sys

import cv2
import numpy as np

from from_captures import CORNER_ANGLES, FLAT_SCALE, SIZE, flat_corners, labels_path, photos_in

PAD = 114
DART_SCORE = 0.35
CAL_SCORE = 0.3
MM_TO_FLAT = FLAT_SCALE * SIZE / 170.0
SECTORS = [20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5]
MATCH_MM = 25.0
IGNORE_BEYOND_MM = 176.0  # the app ignores marks this far from the bull


class Net:
    def __init__(self, path):
        try:
            from ai_edge_litert.interpreter import Interpreter
        except ImportError:
            from tensorflow.lite.python.interpreter import Interpreter
        self.it = Interpreter(model_path=path)
        self.it.allocate_tensors()
        self.inp = self.it.get_input_details()[0]
        self.out = self.it.get_output_details()[0]

    def run(self, rgb640, min_score=0.25):
        x = np.transpose(rgb640.astype(np.float32) / 255.0, (2, 0, 1))[None]
        self.it.set_tensor(self.inp["index"], x)
        self.it.invoke()
        out = self.it.get_tensor(self.out["index"])[0]
        boxes, scores = out[:4].T, out[4:].T
        if boxes.max() > 2:
            boxes = boxes / SIZE
        cls, conf = scores.argmax(1), scores.max(1)
        keep = conf >= min_score
        found = sorted(zip(cls[keep], conf[keep], boxes[keep, 0] * SIZE, boxes[keep, 1] * SIZE), key=lambda d: -d[1])
        picked = []
        for c, s, x, y in found:
            if all(c != c2 or math.hypot(x - x2, y - y2) > 0.02 * SIZE for c2, _, x2, y2 in picked):
                picked.append((int(c), float(s), float(x), float(y)))
        return picked


class TipNet:
    """The optional second stage (TipRefiner.kt): moves each dart mark onto the tip and drops marks that are no tip."""

    PATCH = 96

    def __init__(self, path, min_tip=0.4, rounds=2, merge=5.0):
        try:
            from ai_edge_litert.interpreter import Interpreter
        except ImportError:
            from tensorflow.lite.python.interpreter import Interpreter
        self.it = Interpreter(model_path=path)
        self.it.allocate_tensors()
        self.inp = self.it.get_input_details()[0]
        self.out = self.it.get_output_details()[0]
        self.min_tip, self.rounds, self.merge = min_tip, rounds, merge

    def run(self, flat, x, y):
        m = np.float32([[1, 0, self.PATCH / 2 - x], [0, 1, self.PATCH / 2 - y]])
        patch = cv2.warpAffine(flat, m, (self.PATCH, self.PATCH), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=(PAD, PAD, PAD))
        self.it.set_tensor(self.inp["index"], patch[None].astype(np.float32) / 255.0)
        self.it.invoke()
        dx, dy, tip = self.it.get_tensor(self.out["index"])[0]
        return float(dx), float(dy), float(tip)

    def refine(self, flat, marks):
        kept = []
        for x, y in marks:
            believed = False
            for _ in range(self.rounds):
                dx, dy, tip = self.run(flat, x, y)
                if tip < self.min_tip:
                    break
                believed = True
                x, y = x + dx, y + dy
                if math.hypot(dx, dy) < 1.5:
                    break
            if believed and all(math.hypot(x - kx, y - ky) >= self.merge for kx, ky in kept):
                kept.append((x, y))
        return kept


# ---- the app's two-pass detection -------------------------------------------------------------

def letterbox(rgb):
    h, w = rgb.shape[:2]
    scale = SIZE / max(w, h)
    canvas = np.full((SIZE, SIZE, 3), PAD, np.uint8)
    nw, nh = round(w * scale), round(h * scale)
    ox, oy = (SIZE - nw) // 2, (SIZE - nh) // 2
    canvas[oy:oy + nh, ox:ox + nw] = cv2.resize(rgb, (nw, nh), interpolation=cv2.INTER_AREA)
    return canvas, lambda x, y: ((x - ox) / scale, (y - oy) / scale)


def read(found, to_frame, refine=None):
    corners = []
    for cls in range(1, 5):
        best = max((d for d in found if d[0] == cls and d[1] >= CAL_SCORE), key=lambda d: d[1], default=None)
        corners.append(to_frame(best[2], best[3]) if best else None)
    marks = [(d[2], d[3]) for d in sorted(found, key=lambda d: -d[1]) if d[0] == 0 and d[1] >= DART_SCORE]
    if refine:
        marks = refine(marks)
    return corners, [to_frame(x, y) for x, y in marks]


def complete_corners(corners, previous=None):
    missing = [i for i, c in enumerate(corners) if c is None]
    if not missing:
        return corners
    if len(missing) > 1:
        return None
    m = missing[0]
    if previous is not None:
        guess = previous[m]
    else:
        ring = [0, 3, 1, 2]
        at = ring.index(m)
        before, after, across = corners[ring[(at + 3) % 4]], corners[ring[(at + 1) % 4]], corners[ring[(at + 2) % 4]]
        guess = (before[0] + after[0] - across[0], before[1] + after[1] - across[1])
    return [c if c is not None else guess for c in corners]


def flattened(net, rgb, corners, tips=None):
    flat_to_frame = cv2.getPerspectiveTransform(flat_corners(), np.float32(corners))
    flat = cv2.warpPerspective(rgb, flat_to_frame, (SIZE, SIZE), flags=cv2.WARP_INVERSE_MAP | cv2.INTER_LINEAR, borderValue=(PAD, PAD, PAD))
    def to_frame(x, y):
        v = flat_to_frame @ np.array([x, y, 1.0])
        return (v[0] / v[2], v[1] / v[2])
    return read(net.run(flat), to_frame, (lambda marks: tips.refine(flat, marks)) if tips else None)


def detect(net, rgb, hint, tips=None):
    if hint is not None:
        corners, darts = flattened(net, rgb, hint, tips)
        if sum(c is not None for c in corners) >= 3:
            return corners, darts
    canvas, back = letterbox(rgb)
    whole = read(net.run(canvas), back)
    rough = complete_corners(whole[0])
    if rough is None:
        return whole
    refined = flattened(net, rgb, rough, tips)
    return refined if sum(c is not None for c in refined[0]) >= 3 else whole


# ---- scoring and the barrel filter ------------------------------------------------------------

def to_mm_matrix(corners):
    """3x3 matrix taking frame pixels to board millimetres (x right, y down, the bull at 0,0)."""
    to_flat = cv2.getPerspectiveTransform(np.float32(corners), flat_corners())
    flat_to_mm = np.array([[1 / MM_TO_FLAT, 0, -SIZE / 2 / MM_TO_FLAT], [0, 1 / MM_TO_FLAT, -SIZE / 2 / MM_TO_FLAT], [0, 0, 1]])
    return flat_to_mm @ to_flat


def apply(h, points):
    if not len(points):
        return np.zeros((0, 2))
    return cv2.perspectiveTransform(np.float32(points)[None], h)[0]


def score_of(x, y):
    r = math.hypot(x, y)
    if r <= 6.35:
        return "DB"
    if r <= 15.9:
        return "SB"
    if r > 170:
        return "miss"
    number = SECTORS[int(((math.degrees(math.atan2(x, -y)) + 9) % 360) // 18)]
    if 99 <= r <= 107:
        return "T%d" % number
    if r >= 162:
        return "D%d" % number
    return "S%d" % number


def axis_at(mm_to_px, pixel, width, height):
    """Unit pixel direction from a dart's tip towards its flights, and the pixel length of 10 mm of dart (as DartAxis.kt)."""
    m = mm_to_px
    cx, cy, longest = width / 2.0, height / 2.0, float(max(width, height))
    denom = m[2, 0] * m[2, 1]
    if denom == 0:
        return None
    squared = -((m[0, 0] - cx * m[2, 0]) * (m[0, 1] - cx * m[2, 1]) + (m[1, 0] - cy * m[2, 0]) * (m[1, 1] - cy * m[2, 1])) / denom
    if not np.isfinite(squared) or squared <= 0:
        return None
    f = math.sqrt(squared)
    if f < 0.3 * longest or f > 8 * longest:
        return None
    k_inv = np.array([[1 / f, 0, -cx / f], [0, 1 / f, -cy / f], [0, 0, 1]])
    b = k_inv @ m
    b = b / np.linalg.norm(b[:, 0])
    if b[2, 2] < 0:
        b = -b
    r1, r2, t = b[:, 0], b[:, 1], b[:, 2]
    r3 = np.cross(r1, r2)
    if abs(r3[2]) < 0.05:
        return None
    toward = -1.0 if r3[2] > 0 else 1.0
    inv = np.linalg.inv(m) @ np.array([pixel[0], pixel[1], 1.0])
    x, y = inv[0] / inv[2], inv[1] / inv[2]
    base = r1 * x + r2 * y + t
    moved = base + r3 * toward * 10.0
    if base[2] <= 0 or moved[2] <= 0:
        return None
    d = np.array([f * moved[0] / moved[2] - f * base[0] / base[2], f * moved[1] / moved[2] - f * base[1] / base[2]])
    length = np.linalg.norm(d)
    return None if length < 1.5 else (d / length, length)


def keep_tips(pixels, mm_to_px, width, height):
    if len(pixels) < 2:
        return list(pixels)
    axes = [axis_at(mm_to_px, p, width, height) for p in pixels]
    kept = []
    for j, behind in enumerate(pixels):
        hidden = False
        for i, axis in enumerate(axes):
            if i == j or axis is None:
                continue
            dx, dy = behind[0] - pixels[i][0], behind[1] - pixels[i][1]
            gap = math.hypot(dx, dy)
            if 4.0 <= gap <= 6.0 * axis[1] and (dx * axis[0][0] + dy * axis[0][1]) / gap >= 0.8:
                hidden = True
                break
        if not hidden:
            kept.append(behind)
    return kept


# ---- comparing with the labels ------------------------------------------------------------------

def compare(truth_mm, found_mm):
    """Greedy nearest matching within MATCH_MM. Returns (matches [(truth i, found j, error mm)], missed truth, extra found)."""
    pairs = sorted(((math.hypot(t[0] - f[0], t[1] - f[1]), i, j) for i, t in enumerate(truth_mm) for j, f in enumerate(found_mm)))
    used_t, used_f, matches = set(), set(), []
    for d, i, j in pairs:
        if d <= MATCH_MM and i not in used_t and j not in used_f:
            used_t.add(i); used_f.add(j); matches.append((i, j, d))
    return matches, [i for i in range(len(truth_mm)) if i not in used_t], [j for j in range(len(found_mm)) if j not in used_f]


class Tally:
    def __init__(self):
        self.truth = self.found = self.matched = self.right = self.photos = self.perfect = 0
        self.errors = []
        self.lost = 0

    def add(self, truth_mm, found_mm):
        matches, missed, extra = compare(truth_mm, found_mm)
        right = sum(score_of(*truth_mm[i]) == score_of(*found_mm[j]) for i, j, _ in matches)
        self.photos += 1
        self.truth += len(truth_mm); self.found += len(found_mm); self.matched += len(matches); self.right += right
        self.errors += [e for _, _, e in matches]
        ok = not missed and not extra and right == len(matches)
        self.perfect += ok
        return ok

    def report(self, name):
        pct = lambda a, b: "%5.1f%%" % (100.0 * a / b) if b else "   n/a"
        errors = np.array(self.errors) if self.errors else np.array([0.0])
        print("%-26s score accuracy %s   recall %s   precision %s   tip error median %.1f mm, 90%% under %.1f mm   perfect photos %d/%d" % (
            name, pct(self.right, self.truth), pct(self.matched, self.truth), pct(self.matched, self.found),
            np.median(errors), np.percentile(errors, 90), self.perfect, self.photos))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("folder", help="photos with .labels.json files from label_photos.py")
    parser.add_argument("--model", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "dartnet.tflite"))
    parser.add_argument("--tips", default=None, help="also use the second-stage tip refiner (tiprefine.tflite), as the app does when the file is in its assets")
    parser.add_argument("--cold", action="store_true", help="read the board from scratch in every photo instead of starting from your labelled corners")
    parser.add_argument("--out", default=None, help="folder for pictures of the photos that went wrong")
    args = parser.parse_args()
    net = Net(args.model)
    tips = TipNet(args.tips) if args.tips else None
    plain, filtered = Tally(), Tally()
    lost = 0
    for photo in photos_in(args.folder):
        if not os.path.exists(labels_path(photo)):
            continue
        label = json.load(open(labels_path(photo)))
        if label.get("skip") or len(label.get("corners", [])) != 4:
            continue
        bgr = cv2.imread(photo)
        if bgr is None:
            continue
        rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
        height, width = rgb.shape[:2]
        truth_corners = [tuple(c) for c in label["corners"]]
        truth_mm = apply(to_mm_matrix(truth_corners), label["darts"])

        corners, darts = detect(net, rgb, None if args.cold else truth_corners, tips)
        fit_corners = complete_corners(corners, None if args.cold else truth_corners)
        if fit_corners is None:
            lost += 1
            plain.add(truth_mm, np.zeros((0, 2))); filtered.add(truth_mm, np.zeros((0, 2)))
            continue
        to_mm = to_mm_matrix(fit_corners)
        darts = [d for d, (x, y) in zip(darts, apply(to_mm, darts)) if math.hypot(x, y) <= IGNORE_BEYOND_MM]
        mm_to_px = np.linalg.inv(to_mm)
        a = plain.add(truth_mm, apply(to_mm, darts))
        kept = keep_tips(darts, mm_to_px, width, height)
        b = filtered.add(truth_mm, apply(to_mm, kept))
        if args.out and not b:
            os.makedirs(args.out, exist_ok=True)
            shown = bgr.copy()
            for x, y in label["darts"]:
                cv2.circle(shown, (int(x), int(y)), 10, (118, 230, 0), 2)
            for x, y in kept:
                cv2.circle(shown, (int(x), int(y)), 6, (40, 40, 255), 2)
            cv2.imwrite(os.path.join(args.out, os.path.basename(photo)), shown)
            print("wrong:", os.path.basename(photo), "real", [score_of(*p) for p in truth_mm], "found", [score_of(*p) for p in apply(to_mm, kept)])
    print()
    plain.report("model as it is")
    filtered.report("with the barrel filter")
    if lost:
        print("%d photos: the board could not be found at all (their darts count as missed)" % lost)


def selftest():
    assert score_of(0, 0) == "DB" and score_of(0, -10) == "SB"
    assert score_of(0, -103) == "T20" and score_of(0, -166) == "D20" and score_of(0, -50) == "S20"
    assert score_of(0, 103) == "T3" and score_of(103, 0) == "T6" and score_of(-103, 0) == "T11"
    assert score_of(103 * math.sin(math.radians(18)), -103 * math.cos(math.radians(18))) == "T1"
    assert score_of(0, -200) == "miss"
    # a pinhole camera 480 mm away, 40 degrees to the side: the barrel mark goes, the tip stays
    w, h, f = 1280, 720, 900.0
    yaw, pitch = math.radians(40), math.radians(10)

    def project(x, y, z):
        x1, z1 = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
        y2, z2 = y * math.cos(pitch) - z1 * math.sin(pitch), y * math.sin(pitch) + z1 * math.cos(pitch) + 480.0
        return (f * x1 / z2 + w / 2, f * y2 / z2 + h / 2)
    corner_mm = [(170 * math.sin(math.radians(a)), -170 * math.cos(math.radians(a))) for a in CORNER_ANGLES]
    px = [project(x, y, 0) for x, y in corner_mm]
    to_mm = to_mm_matrix(px)
    tip, barrel = project(30, -20, 0), project(30, -20, -30)
    kept = keep_tips([barrel, tip], np.linalg.inv(to_mm), w, h)
    assert len(kept) == 1 and math.hypot(kept[0][0] - tip[0], kept[0][1] - tip[1]) < 1e-6, kept
    back = apply(to_mm, [tip])[0]
    assert math.hypot(back[0] - 30, back[1] + 20) < 0.01, back
    print("self test passed")


if __name__ == "__main__":
    if "--selftest" in sys.argv:
        selftest()
    else:
        main()