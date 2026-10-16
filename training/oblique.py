"""Geometry shared by the oblique evaluation and the oblique training augmentation.

The DeepDarts pictures are rectified (board square-on, centred, outer double edge at 0.37628 of the image). These
helpers fake a phone beside the board by warping them with a pinhole camera.
"""
import cv2
import numpy as np

SIZE = 640
R_NORM = 0.37628          # outer double edge as a share of the rectified image (measured over the dataset)
R_MM = 170.0
KN = R_NORM / R_MM        # rectified image shares per millimetre
WIRES = [19, 9, 14, 4]    # wires (index i = angle i*18+9 degrees clockwise from the top) of cal1..cal4
TEST_FOLDERS = ["d1_03_03_2020", "d1_03_19_2020", "d1_03_23_2020", "d1_03_27_2020", "d1_03_28_2020",
                "d1_03_30_2020", "d1_03_31_2020", "d2_03_03_2020", "d2_02_10_2021", "d2_02_03_2021_2"]

CAL_MM = np.array([[R_MM * np.sin(np.radians(i * 18 + 9)), R_MM * np.cos(np.radians(i * 18 + 9))] for i in WIRES])


def mm_to_rect(mm, size=1.0):
    """Board millimetres (x right, y up) to rectified-image coordinates."""
    mm = np.asarray(mm, float)
    return np.stack([(0.5 + KN * mm[..., 0]) * size, (0.5 - KN * mm[..., 1]) * size], -1)


def rect_to_mm(p, size=1.0):
    p = np.asarray(p, float) / size
    return np.stack([(p[..., 0] - 0.5) / KN, -(p[..., 1] - 0.5) / KN], -1)


def oblique(img, yaw, pitch, roll, frame=(720, 960), board_share=0.7, shift=(0.0, 0.0), border=(114, 114, 114)):
    """Warps a rectified picture as a pinhole camera would see it. Returns the frame and the 3x3 map (rect share -> frame px)."""
    w, h = frame
    D = 1500.0
    f = board_share * min(w, h) * D / (2 * R_MM)
    ry, rx, rz = np.radians([yaw, pitch, roll])
    Ry = np.array([[np.cos(ry), 0, np.sin(ry)], [0, 1, 0], [-np.sin(ry), 0, np.cos(ry)]])
    Rx = np.array([[1, 0, 0], [0, np.cos(rx), -np.sin(rx)], [0, np.sin(rx), np.cos(rx)]])
    Rz = np.array([[np.cos(rz), -np.sin(rz), 0], [np.sin(rz), np.cos(rz), 0], [0, 0, 1]])
    R = Rz @ Rx @ Ry

    def project(mm):
        P = R @ np.array([mm[0], -mm[1], 0.0])     # image y points down, board y points up
        return np.array([w / 2 + shift[0] * w + f * P[0] / (D + P[2]), h / 2 + shift[1] * h + f * P[1] / (D + P[2])])

    src = np.float32([[0, 0], [1, 0], [1, 1], [0, 1]])
    corners_mm = [rect_to_mm(c) for c in src]
    dst = np.float32([project(c) for c in corners_mm])
    H = cv2.getPerspectiveTransform(src * img.shape[1], dst)
    out = cv2.warpPerspective(img, H, (w, h), borderMode=cv2.BORDER_CONSTANT, borderValue=border)
    S = np.diag([1.0 / img.shape[1], 1.0 / img.shape[0], 1.0])
    return out, H @ np.linalg.inv(S)


def letterbox(frame):
    h, w = frame.shape[:2]
    s = SIZE / max(w, h)
    nw, nh = round(w * s), round(h * s)
    canvas = np.full((SIZE, SIZE, 3), 114, np.uint8)
    ox, oy = (SIZE - nw) // 2, (SIZE - nh) // 2
    canvas[oy:oy + nh, ox:ox + nw] = cv2.resize(frame, (nw, nh), interpolation=cv2.INTER_AREA)
    return canvas, (lambda x, y: ((x * SIZE - ox) / s, (y * SIZE - oy) / s))
