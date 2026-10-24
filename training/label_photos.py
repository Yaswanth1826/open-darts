"""Check and fix the labels of the photos collected by the app, so the network can be retrained on them.

    pip install opencv-python
    python label_photos.py Training

Shows every photo with what the app's network found: the four board corners (green, numbered) and the dart tips (red
circles), plus the board rings worked out from the corners. Make the circles sit on the real dart TIPS (the point
that touches the board, not the barrel or the flights) and the green corners on the wire ends, as in the picture of
the rings. Your work is saved next to each photo as <photo>.labels.json, so you can stop and continue any time.

    left click    add a dart tip here            right click   remove the nearest dart
    z             zoom in around the mouse (press again to zoom out)
    1 2 3 4       then click: move that corner. They sit where the outer edge of the double ring meets the wire between
                  1 = the 5 and the 20, 2 = the 17 and the 3, 3 = the 8 and the 11, 4 = the 13 and the 6
    SPACE / n     photo is right, next        b   previous photo        x   leave this photo out of training
    u             undo the last added dart    q / Esc   save and quit
"""
import argparse
import json
import os

import cv2
import numpy as np

from from_captures import FLAT_SCALE, SIZE, flat_corners, labels_path, load_meta, photos_in

WINDOW = (1500, 900)
RING_RADII_MM = [6.35, 15.9, 99.0, 107.0, 162.0, 170.0]
GREEN = (118, 230, 0)
RED = (40, 40, 255)
YELLOW = (0, 220, 255)
ORANGE = (0, 140, 255)
BLUE = (255, 160, 0)


def ring_points(corners, radius_mm, steps=90):
    """Pixel positions of the circle of [radius_mm] around the bull, from the four corners found in the photo."""
    to_image = cv2.getPerspectiveTransform(flat_corners(), np.array(corners, np.float32))
    r = radius_mm * FLAT_SCALE * SIZE / 170.0
    angles = np.linspace(0, 2 * np.pi, steps, endpoint=False)
    flat = np.stack([SIZE / 2 + r * np.sin(angles), SIZE / 2 - r * np.cos(angles)], axis=1).astype(np.float32)
    return cv2.perspectiveTransform(flat[None], to_image)[0]


class Photo:
    def __init__(self, path):
        self.path = path
        self.image = cv2.imread(path)
        saved = json.load(open(labels_path(path))) if os.path.exists(labels_path(path)) else None
        meta = saved or load_meta(path) or {}
        self.corners = [list(map(float, c)) for c in meta.get("corners", [])]
        self.darts = [list(map(float, d)) for d in meta.get("darts", [])]
        meta_note = load_meta(path) or {}
        self.note = meta_note.get("note", "")
        self.fix = meta_note.get("fix")
        self.checked = saved is not None
        if saved is None and self.fix:
            self.apply_fix()

    def apply_fix(self):
        """Start from what the player said: drop the dart the camera made up, and put one where the player named the area."""
        was, now = self.fix.get("wasPixel"), self.fix.get("nowPixel")
        if was and self.darts:
            at = nearest(self.darts, was[0], was[1], 40)
            if at is not None:
                self.darts.pop(at)
        if now and self.fix.get("now") != "Miss":
            self.darts.append([float(now[0]), float(now[1])])

    def save(self, skip=False):
        with open(labels_path(self.path), "w") as f:
            json.dump({"corners": self.corners, "darts": self.darts, "skip": skip}, f)
        self.checked = True


class View:
    def __init__(self, photo, size=WINDOW):
        self.photo = photo
        self.w, self.h = size
        h, w = photo.image.shape[:2]
        self.fit = min(self.w / w, self.h / h)
        self.zoom = 1.0
        self.centre = (w / 2, h / 2)

    @property
    def scale(self):
        return self.fit * self.zoom

    def to_canvas(self, x, y):
        return ((x - self.centre[0]) * self.scale + self.w / 2, (y - self.centre[1]) * self.scale + self.h / 2)

    def to_image(self, cx, cy):
        return ((cx - self.w / 2) / self.scale + self.centre[0], (cy - self.h / 2) / self.scale + self.centre[1])

    def toggle_zoom(self, cx, cy):
        if self.zoom == 1.0:
            self.centre = self.to_image(cx, cy)
            self.zoom = 3.0
        else:
            self.zoom = 1.0
            self.centre = (self.photo.image.shape[1] / 2, self.photo.image.shape[0] / 2)

    def render(self, index, total, pending_corner=None):
        s = self.scale
        m = np.float32([[s, 0, self.w / 2 - self.centre[0] * s], [0, s, self.h / 2 - self.centre[1] * s]])
        canvas = cv2.warpAffine(self.photo.image, m, (self.w, self.h), flags=cv2.INTER_AREA if s < 1 else cv2.INTER_LINEAR)
        p = self.photo
        if len(p.corners) == 4:
            for r in RING_RADII_MM:
                pts = np.array([self.to_canvas(*q) for q in ring_points(p.corners, r)], np.int32)
                cv2.polylines(canvas, [pts], True, YELLOW, 1, cv2.LINE_AA)
        for i, c in enumerate(p.corners):
            x, y = map(int, self.to_canvas(*c))
            cv2.circle(canvas, (x, y), 9, GREEN, 2, cv2.LINE_AA)
            cv2.putText(canvas, str(i + 1), (x + 10, y - 8), cv2.FONT_HERSHEY_SIMPLEX, 0.6, GREEN, 2, cv2.LINE_AA)
        for d in p.darts:
            x, y = map(int, self.to_canvas(*d))
            cv2.circle(canvas, (x, y), 9, RED, 2, cv2.LINE_AA)
            cv2.drawMarker(canvas, (x, y), RED, cv2.MARKER_CROSS, 7, 1, cv2.LINE_AA)
        fix = p.fix
        if fix:
            if fix.get("wasPixel"):
                x, y = map(int, self.to_canvas(*fix["wasPixel"]))
                cv2.circle(canvas, (x, y), 16, ORANGE, 2, cv2.LINE_AA)
            if fix.get("nowPixel"):
                x, y = map(int, self.to_canvas(*fix["nowPixel"]))
                cv2.drawMarker(canvas, (x, y), BLUE, cv2.MARKER_DIAMOND, 22, 2, cv2.LINE_AA)
        status = "set corner %d: click it" % pending_corner if pending_corner else \
            "%d darts   LMB add tip   RMB remove   z zoom   1-4 corner   SPACE next   b back   x skip   u undo   q quit" % len(p.darts)
        title = "%d/%d  %s  [%s]%s" % (index + 1, total, os.path.basename(p.path), p.note, "  checked" if p.checked else "")
        lines = [title, status]
        if fix:
            lines.append("PLAYER FIX: camera said '%s', the player says '%s'. Orange ring = where the camera saw it, blue diamond = middle of the area the player named. The red circle starts at that diamond: right-click it and click the real tip instead." % (fix.get("was") or "nothing", fix.get("now") or "no dart"))
        for k, line in enumerate(lines):
            cv2.putText(canvas, line, (10, 24 + 24 * k), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 0), 4, cv2.LINE_AA)
            cv2.putText(canvas, line, (10, 24 + 24 * k), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 1, cv2.LINE_AA)
        return canvas


def nearest(points, x, y, within):
    best, at = within, None
    for i, (px, py) in enumerate(points):
        d = ((px - x) ** 2 + (py - y) ** 2) ** 0.5
        if d < best:
            best, at = d, i
    return at


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("folder", help="the folder with the photos copied from the phone")
    parser.add_argument("--all", action="store_true", help="also show the photos that were already checked")
    args = parser.parse_args()
    paths = [p for p in photos_in(args.folder) if args.all or not os.path.exists(labels_path(p))]
    paths.sort(key=lambda p: "-fix" not in os.path.basename(p))
    if not paths:
        print("Nothing to check (use --all to go through the checked ones again).")
        return
    state = {"i": 0, "view": None, "pending": None, "mouse": (0, 0)}

    def open_photo(i):
        photo = Photo(paths[i])
        state["i"], state["view"], state["pending"] = i, View(photo), None

    def on_mouse(event, x, y, flags, _):
        state["mouse"] = (x, y)
        view = state["view"]
        photo = view.photo
        if event == cv2.EVENT_LBUTTONDOWN:
            ix, iy = view.to_image(x, y)
            if state["pending"]:
                n = state["pending"] - 1
                if n < len(photo.corners):
                    photo.corners[n] = [ix, iy]
                state["pending"] = None
            else:
                photo.darts.append([ix, iy])
        elif event == cv2.EVENT_RBUTTONDOWN and photo.darts:
            at = nearest([view.to_canvas(*d) for d in photo.darts], x, y, 40)
            if at is not None:
                photo.darts.pop(at)

    cv2.namedWindow("labels", cv2.WINDOW_AUTOSIZE)
    cv2.setMouseCallback("labels", on_mouse)
    open_photo(0)
    while True:
        view = state["view"]
        cv2.imshow("labels", view.render(state["i"], len(paths), state["pending"]))
        key = cv2.waitKey(30) & 0xFF
        photo = view.photo
        if key in (ord("q"), 27):
            photo.save() if len(photo.corners) == 4 else None
            break
        elif key == ord("z"):
            view.toggle_zoom(*state["mouse"])
        elif key == ord("u") and photo.darts:
            photo.darts.pop()
        elif key in (ord("1"), ord("2"), ord("3"), ord("4")):
            if len(photo.corners) < 4:
                photo.corners = [[photo.image.shape[1] * (0.3 + 0.1 * k), photo.image.shape[0] / 2] for k in range(4)]
            state["pending"] = key - ord("0")
        elif key in (ord(" "), ord("n"), ord("x")) and (len(photo.corners) == 4 or key == ord("x")):
            photo.save(skip=key == ord("x"))
            if state["i"] + 1 >= len(paths):
                print("That was the last photo.")
                break
            open_photo(state["i"] + 1)
        elif key == ord("b") and state["i"] > 0:
            open_photo(state["i"] - 1)
    cv2.destroyAllWindows()
    print("Now run: python from_captures.py %s --out my_photos" % args.folder)


if __name__ == "__main__":
    main()