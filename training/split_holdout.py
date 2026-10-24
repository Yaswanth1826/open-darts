"""Keeps a part of your labelled photos out of training, so the next evaluation is honest.

    python training/split_holdout.py Screenshots/Training --to Screenshots/Holdout          (shows what would move)
    python training/split_holdout.py Screenshots/Training --to Screenshots/Holdout --apply

Photos taken seconds apart look almost the same, so whole blocks of consecutive photos are moved (not every 6th photo),
together with their .labels.json files. Evaluate on the moved photos with eval_real.py; train on what is left.
"""
import argparse
import os
import shutil

from from_captures import labels_path, photos_in


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("--to", required=True)
    parser.add_argument("--block", type=int, default=10, help="photos per block")
    parser.add_argument("--every", type=int, default=6, help="every n-th block is held out")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    photos = sorted(p for p in photos_in(args.source) if os.path.exists(labels_path(p)))
    held = [p for i, p in enumerate(photos) if (i // args.block) % args.every == args.every - 1]
    print(f"{len(held)} of {len(photos)} labelled photos are held out ({len(held) / max(1, len(photos)):.0%})")
    if not args.apply:
        print("Add --apply to move them.")
        return
    os.makedirs(args.to, exist_ok=True)
    for p in held:
        for f in (p, labels_path(p)):
            shutil.move(f, os.path.join(args.to, os.path.basename(f)))
    print(f"Moved to {args.to}")


if __name__ == "__main__":
    main()
