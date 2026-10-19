"""
Trains the second-stage tip refiner on the patches from make_tip_patches.py and exports tiprefine.tflite.

The network sees a 96 x 96 patch of the flattened board (the same 640 px layout the detector uses) and says
  dx, dy  where the dart tip is, in patch pixels from the patch centre (1.417 px = 1 mm)
  tip     how likely a dart tip is in the patch (0..1)
so the app can nudge every detected dart mark onto the real tip, and drop marks that are not a tip.

Training shows the network a random 96 x 96 window of each 176 x 176 crop, so the tip is anywhere within +-MAX_SHIFT px
of the centre and the answer is known exactly. Negative crops have no tip to find.

    python train_tip.py --patches tip_patches --out tiprefine.tflite
"""
import argparse
import os

import numpy as np
import tensorflow as tf

PATCH = 96
CROP = 176
MARGIN = (CROP - PATCH) // 2  # 40: how far the window can move inside a crop
MAX_SHIFT = 36  # px (about 25 mm): how far off the detector's mark may be
PX_PER_MM = 1.4166


def load(folder, split):
    d = np.load(os.path.join(folder, f"{split}.npz"))
    return d["crops"], d["tip"], d["source"]


def window(crops, sx, sy):
    """The 96 x 96 windows whose centres are shifted by (sx, sy) from the crop centres."""
    out = np.empty((len(crops), PATCH, PATCH, 3), np.uint8)
    for i, (c, x, y) in enumerate(zip(crops, sx, sy)):
        out[i] = c[MARGIN + y: MARGIN + y + PATCH, MARGIN + x: MARGIN + x + PATCH]
    return out


def shifts(rng, n):
    """Random shifts, uniform over a disc of radius MAX_SHIFT."""
    radius = MAX_SHIFT * np.sqrt(rng.uniform(size=n))
    angle = rng.uniform(0, 2 * np.pi, n)
    sx = np.rint(radius * np.cos(angle)).astype(int)
    sy = np.rint(radius * np.sin(angle)).astype(int)
    return sx, sy


def augment(patches, rng):
    x = patches.astype(np.float32) / 255.0
    n = len(x)
    gain = rng.uniform(0.7, 1.3, (n, 1, 1, 1))
    bias = rng.uniform(-0.12, 0.12, (n, 1, 1, 1))
    tint = rng.uniform(0.92, 1.08, (n, 1, 1, 3))
    x = (x - 0.5) * gain + 0.5 + bias
    x = x * tint
    x += rng.normal(0, rng.uniform(0, 0.03), x.shape).astype(np.float32)
    return np.clip(x, 0.0, 1.0)


class Batches(tf.keras.utils.Sequence):
    """Each epoch draws new shifts, flips and lighting."""

    def __init__(self, crops, tip, batch, steps, seed):
        super().__init__()
        self.crops, self.tip, self.batch, self.steps = crops, tip, batch, steps
        self.rng = np.random.default_rng(seed)
        self.positives = np.flatnonzero(tip == 1)
        self.negatives = np.flatnonzero(tip == 0)

    def __len__(self):
        return self.steps

    def __getitem__(self, _):
        n = self.batch
        pick = np.concatenate([
            self.rng.choice(self.positives, n // 2),
            self.rng.choice(self.negatives, n - n // 2),
        ])
        crops = self.crops[pick]
        flip = self.rng.random(n) < 0.5
        crops = np.where(flip[:, None, None, None], crops[:, :, ::-1], crops)
        sx, sy = shifts(self.rng, n)
        is_tip = self.tip[pick]
        sx, sy = sx * is_tip, sy * is_tip  # a shifted window around a non-tip could land on a tip nearby
        x = augment(window(crops, sx, sy), self.rng)
        y = np.stack([-sx, -sy, is_tip], 1).astype(np.float32)
        return x, y


def block(x, filters, stride):
    x = tf.keras.layers.Conv2D(filters, 3, stride, "same", use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.ReLU()(x)
    x = tf.keras.layers.Conv2D(filters, 3, 1, "same", use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    return tf.keras.layers.ReLU()(x)


def build():
    inputs = tf.keras.Input((PATCH, PATCH, 3))
    x = block(inputs, 24, 2)   # 48
    x = block(x, 32, 2)        # 24
    x = block(x, 48, 2)        # 12
    x = block(x, 64, 2)        # 6
    x = tf.keras.layers.Flatten()(x)  # keep the positions: global pooling would throw away where things are
    x = tf.keras.layers.Dense(128, activation="relu")(x)
    x = tf.keras.layers.Dropout(0.2)(x)
    offset = tf.keras.layers.Rescaling(MAX_SHIFT)(tf.keras.layers.Dense(2)(x))
    tip = tf.keras.layers.Dense(1, activation="sigmoid")(x)
    return tf.keras.Model(inputs, tf.keras.layers.Concatenate()([offset, tip]))


def loss(y_true, y_pred):
    is_tip = y_true[:, 2]
    huber = tf.keras.losses.huber(y_true[:, :2] / 8.0, y_pred[:, :2] / 8.0, delta=1.0)
    bce = tf.keras.losses.binary_crossentropy(is_tip[:, None], y_pred[:, 2:3])
    return tf.reduce_sum(huber * is_tip) / tf.maximum(tf.reduce_sum(is_tip), 1.0) + bce


def evaluate(predict, crops, tip, seed=7, repeat=3):
    """Median / 90th percentile tip error in mm before and after the refiner, and how well tip / not-tip are told apart."""
    rng = np.random.default_rng(seed)
    pos = np.flatnonzero(tip == 1)
    neg = np.flatnonzero(tip == 0)
    before, after, prob_pos, prob_neg = [], [], [], []
    for _ in range(repeat):
        for idx, is_tip in ((pos, True), (neg, False)):
            if not len(idx):
                continue
            sx, sy = shifts(rng, len(idx))
            if not is_tip:
                sx, sy = sx * 0, sy * 0
            out = predict(window(crops[idx], sx, sy).astype(np.float32) / 255.0)
            (prob_pos if is_tip else prob_neg).extend(out[:, 2])
            if is_tip:
                before.extend(np.hypot(sx, sy) / PX_PER_MM)
                after.extend(np.hypot(out[:, 0] + sx, out[:, 1] + sy) / PX_PER_MM)
    before, after = np.array(before), np.array(after)
    pp, pn = np.array(prob_pos), np.array(prob_neg)
    auc = float((pp[:, None] > pn[None, :]).mean() + 0.5 * (pp[:, None] == pn[None, :]).mean()) if len(pn) and len(pp) else float("nan")
    return {
        "tip error before (median / p90 mm)": (float(np.median(before)), float(np.percentile(before, 90))),
        "tip error after  (median / p90 mm)": (float(np.median(after)), float(np.percentile(after, 90))),
        "share of tips kept at 0.4": float((pp >= 0.4).mean()),
        "share of non-tips dropped at 0.4": float((pn < 0.4).mean()) if len(pn) else float("nan"),
        "auc": auc,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--patches", default="tip_patches")
    parser.add_argument("--out", default="tiprefine.tflite")
    parser.add_argument("--epochs", type=int, default=40)
    parser.add_argument("--steps", type=int, default=300, help="batches per epoch")
    parser.add_argument("--batch", type=int, default=128)
    args = parser.parse_args()

    train_crops, train_tip, _ = load(args.patches, "train")
    val_crops, val_tip, _ = load(args.patches, "val")
    print(f"train: {int(train_tip.sum())} tips, {int((1 - train_tip).sum())} negatives; val: {int(val_tip.sum())} tips, {int((1 - val_tip).sum())} negatives")

    model = build()
    rate = tf.keras.optimizers.schedules.CosineDecay(5e-4, args.epochs * args.steps, alpha=0.02)
    model.compile(optimizer=tf.keras.optimizers.Adam(rate, clipnorm=1.0), loss=loss)
    model.fit(Batches(train_crops, train_tip, args.batch, args.steps, 1), epochs=args.epochs,
              validation_data=Batches(val_crops, val_tip, args.batch, 40, 2), verbose=2)

    predict = lambda x: model.predict(x, batch_size=256, verbose=0)
    print("keras  :", evaluate(predict, val_crops, val_tip))

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = []
    data = converter.convert()
    with open(args.out, "wb") as f:
        f.write(data)
    interpreter = tf.lite.Interpreter(model_content=data)
    interpreter.allocate_tensors()
    i, o = interpreter.get_input_details()[0], interpreter.get_output_details()[0]

    def lite(x):
        out = []
        for patch in x:
            interpreter.set_tensor(i["index"], patch[None])
            interpreter.invoke()
            out.append(interpreter.get_tensor(o["index"])[0])
        return np.array(out)

    small = (val_crops[:400], val_tip[:400])
    print("tflite :", evaluate(lite, *small), f"\nwrote {args.out} ({len(data) / 1e6:.2f} MB)")


if __name__ == "__main__":
    main()
