"""YOLOX experiment for the Open Darts detector (Apache-2.0 code, trained from the COCO-pretrained YOLOX weights).

Same five classes as before (dart, cal1..cal4) on 640x640 pictures. Chosen with environment variables, so the training
notebook needs no edits here:
    DARTNET_DATA     folder made by to_coco.py (images/, labels/, annotations/)
    DARTNET_SIZE     "tiny" (default) or "nano" (about a quarter of the compute, less accurate)
    DARTNET_EPOCHS   number of epochs (default 25)

Augmentation keeps the labels valid: no flips (they would swap which calibration point is which), no mosaic and no mixup
(several boards in one picture), only small rotation, shift, zoom and shear plus colour changes.
"""
import os

import numpy as np
import torch.nn as nn
import yolox.layers

from yolox.data.data_augment import random_affine
from yolox.data.datasets.datasets_wrapper import Dataset
from yolox.data.datasets.mosaicdetection import MosaicDetection
from yolox.exp import Exp as BaseExp

if hasattr(yolox.layers, "COCOeval_opt"):  # its C++ build can fail on a fresh machine; the plain pycocotools evaluation is fast enough
    del yolox.layers.COCOeval_opt


class AffineDetection(MosaicDetection):
    """Like MosaicDetection, but every sample is one picture that gets a small random affine warp instead of a mosaic."""

    @Dataset.mosaic_getitem
    def __getitem__(self, idx):
        self._dataset._input_dim = self.input_dim
        img, label, img_info, img_id = self._dataset.pull_item(idx)
        if self.enable_mosaic:  # the last epochs turn the augmentation off
            input_h, input_w = self.input_dim[0], self.input_dim[1]
            canvas = np.full((input_h, input_w, 3), 114, dtype=np.uint8)
            canvas[: img.shape[0], : img.shape[1]] = img
            img, label = random_affine(
                canvas, label, target_size=(input_w, input_h),
                degrees=self.degrees, translate=self.translate, scales=self.scale, shear=self.shear,
            )
        img, label = self.preproc(img, label, self.input_dim)
        return img, label, img_info, img_id


class Exp(BaseExp):
    def __init__(self):
        super().__init__()
        self.size = os.environ.get("DARTNET_SIZE", "tiny")
        self.depth, self.width = {"tiny": (0.33, 0.375), "nano": (0.33, 0.25)}[self.size]
        self.exp_name = "dartnet"
        self.num_classes = 5
        side = int(os.environ.get("DARTNET_INPUT", "640"))
        self.input_size = (side, side)
        self.test_size = (side, side)
        self.data_dir = os.environ.get("DARTNET_DATA", "datasets/darts")
        self.train_ann, self.val_ann, self.test_ann = "instances_train.json", "instances_val.json", "instances_test.json"
        self.max_epoch = int(os.environ.get("DARTNET_EPOCHS", "25"))
        self.warmup_epochs = 2
        self.no_aug_epochs = 3
        self.eval_interval = 2
        self.print_interval = 50
        self.save_history_ckpt = False
        self.data_num_workers = 4
        self.test_conf = 0.01

        self.flip_prob = 0.0
        self.mosaic_prob = 0.0
        self.mixup_prob = 0.0
        self.enable_mixup = False
        self.degrees = 4.0
        self.translate = 0.08
        self.mosaic_scale = (0.7, 1.3)
        self.shear = 2.0

    def get_model(self):
        from yolox.models import YOLOX, YOLOPAFPN, YOLOXHead

        def init_yolo(module):
            for m in module.modules():
                if isinstance(m, nn.BatchNorm2d):
                    m.eps = 1e-3
                    m.momentum = 0.03

        if getattr(self, "model", None) is None:
            depthwise = self.size == "nano"
            channels = [256, 512, 1024]
            backbone = YOLOPAFPN(self.depth, self.width, in_channels=channels, act=self.act, depthwise=depthwise)
            head = YOLOXHead(self.num_classes, self.width, in_channels=channels, act=self.act, depthwise=depthwise)
            self.model = YOLOX(backbone, head)
        self.model.apply(init_yolo)
        self.model.head.initialize_biases(1e-2)
        self.model.train()
        return self.model

    def get_dataset(self, cache=False, cache_type="ram"):
        from yolox.data import COCODataset, TrainTransform
        return COCODataset(
            data_dir=self.data_dir, json_file=self.train_ann, name="images/train", img_size=self.input_size,
            preproc=TrainTransform(max_labels=50, flip_prob=self.flip_prob, hsv_prob=self.hsv_prob),
            cache=cache, cache_type=cache_type,
        )

    def get_data_loader(self, batch_size, is_distributed, no_aug=False, cache_img=None):
        import yolox.data
        yolox.data.MosaicDetection = AffineDetection  # the base class looks the wrapper up here when it builds the loader
        return super().get_data_loader(batch_size, is_distributed, no_aug=no_aug, cache_img=cache_img)

    def get_eval_dataset(self, **kwargs):
        from yolox.data import COCODataset, ValTransform
        return COCODataset(
            data_dir=self.data_dir, json_file=self.val_ann, name="images/val", img_size=self.test_size,
            preproc=ValTransform(legacy=kwargs.get("legacy", False)),
        )

    def random_resize(self, data_loader, epoch, rank, is_distributed):
        return self.input_size  # the warp above assumes one fixed size
