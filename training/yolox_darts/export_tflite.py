"""Exports a trained YOLOX checkpoint as dartnet.tflite for the app.

    python export_tflite.py --exp exp_darts.py --ckpt best_ckpt.pth --out dartnet.tflite

The exported network takes a 640x640 RGB picture with values 0..1 (as the app feeds it; YOLOX itself wants BGR 0..255,
which the wrapper converts) and returns [4 + classes, candidates]: centre x, centre y, width, height in pixels, then one
score per class (objectness times class score). That is the layout YoloDecoder and eval_real.py already read.
"""
import argparse
import glob
import os
import shutil
import subprocess
import sys

import numpy as np
import torch
import torch.nn as nn


class Deployable(nn.Module):
    def __init__(self, net):
        super().__init__()
        self.net = net

    def forward(self, rgb):  # [1, 3, H, W], 0..1
        bgr = torch.cat([rgb[:, 2:3], rgb[:, 1:2], rgb[:, 0:1]], dim=1) * 255.0
        out = self.net(bgr)  # [1, candidates, 5 + classes]
        scores = out[..., 4:5] * out[..., 5:]
        return torch.cat([out[..., :4], scores], dim=-1).permute(0, 2, 1)


def load(exp_file, ckpt):
    from yolox.exp import get_exp
    from yolox.models.network_blocks import SiLU
    from yolox.utils import replace_module

    exp = get_exp(exp_file, None)
    model = exp.get_model()
    state = torch.load(ckpt, map_location="cpu", weights_only=False)
    model.load_state_dict(state["model"] if "model" in state else state)
    model.eval()
    model = replace_module(model, nn.SiLU, SiLU)
    model.head.decode_in_inference = True
    return exp, Deployable(model).eval()


def to_onnx(wrapper, size, path):
    dummy = torch.rand(1, 3, size[0], size[1])
    try:
        torch.onnx.export(wrapper, dummy, path, input_names=["images"], output_names=["output"], opset_version=13, dynamo=False)
    except TypeError:  # an older torch has no dynamo switch
        torch.onnx.export(wrapper, dummy, path, input_names=["images"], output_names=["output"], opset_version=13)
    import onnx
    from onnxsim import simplify  # folds the constant grids of the head away, which onnx2tf cannot convert otherwise
    simplified, valid = simplify(onnx.load(path))
    assert valid, "the simplified ONNX model does not match the original"
    onnx.save(simplified, path)
    return dummy.numpy()


def to_tflite(onnx_path, work, out):
    shutil.rmtree(work, ignore_errors=True)
    os.makedirs(work)
    # onnx2tf looks for (and otherwise downloads) a sample picture file in the working folder; it only feeds optional checks
    np.save(os.path.join(work, "calibration_image_sample_data_20x128x128x3_float32.npy"), np.random.rand(20, 128, 128, 3).astype(np.float32))
    command = [sys.executable, "-m", "onnx2tf", "-i", os.path.abspath(onnx_path), "-o", "saved_model", "-n"]
    print("$", " ".join(command), flush=True)
    subprocess.run(command, check=True, cwd=work)
    found = sorted(glob.glob(os.path.join(work, "saved_model", "*float32.tflite"))) or sorted(glob.glob(os.path.join(work, "saved_model", "*.tflite")))
    if not found:
        raise SystemExit("onnx2tf produced no .tflite file")
    shutil.copy(found[0], out)


def check(onnx_path, tflite_path, sample):
    """Runs the same picture through the ONNX and the TFLite model and compares the scores."""
    import onnxruntime
    from ai_edge_litert.interpreter import Interpreter

    expected = onnxruntime.InferenceSession(onnx_path).run(None, {"images": sample})[0][0]  # [4 + classes, candidates]
    interpreter = Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()
    detail = interpreter.get_input_details()[0]
    picture = sample if detail["shape"][1] == 3 else sample.transpose(0, 2, 3, 1)
    interpreter.set_tensor(detail["index"], picture.astype(np.float32))
    interpreter.invoke()
    got = interpreter.get_tensor(interpreter.get_output_details()[0]["index"])[0]
    if got.shape != expected.shape:
        got = got.T
    assert got.shape == expected.shape, f"output shape {got.shape} differs from {expected.shape}"
    worst = float(np.abs(got - expected).max())
    print("output", tuple(got.shape), "| largest difference to the ONNX model:", worst)
    assert worst < 1.0, "the TFLite model does not match the ONNX model"
    return got.shape


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--exp", required=True)
    parser.add_argument("--ckpt", required=True)
    parser.add_argument("--out", default="dartnet.tflite")
    parser.add_argument("--work", default="export_work")
    args = parser.parse_args()

    exp, wrapper = load(args.exp, args.ckpt)
    os.makedirs(args.work, exist_ok=True)
    onnx_path = os.path.join(args.work, "dartnet.onnx")
    sample = to_onnx(wrapper, exp.test_size, onnx_path)
    to_tflite(onnx_path, os.path.join(args.work, "tf"), args.out)
    check(onnx_path, args.out, sample)
    print("wrote", args.out, "-", round(os.path.getsize(args.out) / 1e6, 1), "MB")


if __name__ == "__main__":
    main()
