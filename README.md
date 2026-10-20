# Open Darts

Open Darts is a free, offline-first Android darts companion. It supports multiple game modes, multiplayer, manual scoring and camera-based automatic scoring.

## Features

- **Games:** X01 (101-1001, straight/double/master in and out, legs and sets, checkout suggestions), Cricket (standard, cut-throat, no-score), Around the Clock (with skip and doubles-only variants) and Count Up.
- **Multiplayer:** 1-8 players on one device, saved player names, per-turn undo, averages and highest visits. An unfinished game is saved after every dart: press Back to pause it and pick it up again with "Resume game" on the home screen, even after the app was closed.
- **Home and flow:** four colour-coded categories (X01, Cricket, Practice, Party), a player picker with a tick-to-select roster, and a rules sheet (match mode, in/out, scoring method) before every game.
- **Manual scoring:** a full keypad (miss, bulls, singles, doubles and trebles) with Undo and Next player. The AI button in the game header switches between camera and keypad at any time.
- **Camera scoring:** point the phone at the board, the app finds the board automatically (no calibration taps), and darts are detected and scored automatically. "Rescan board", "Align board by hand" and a four-tap setup are in the game menu as fallbacks. Any dart can be corrected by hand (Undo, then switch to the keypad) and a Miss / Next player button covers bounce-outs.

## Architecture

- `core/` - pure Kotlin JVM module: game engines, board geometry and homography, and the classical-CV dart tracker. Fully unit-tested without Android (`./gradlew :core:test`). Includes a synthetic perspective renderer used to test camera accuracy on all 62 scoring areas.
- `app/` - Jetpack Compose UI, CameraX capture, calibration and session state.

## Camera accuracy

Detection is tested on synthetic frames only. Real-world accuracy depends on lighting, camera angle and board condition, so expect to tune and to use manual correction occasionally. Tips: mount the phone steady, put the phone beside the board, a little to one side and facing across it (anything from straight-on to about 70 degrees to the side works, and the app tells you if the angle, distance or framing is off), use even light without strong glare, keep the 20 roughly at the top, and tap Rescan if the phone or board moves.
## Requirements

- Android Studio with Android SDK 35 installed
- JDK 17 for the Android Gradle build (the app itself targets Android API 26+)
- Internet access on the first build to download Gradle dependencies

## Build and test

Open this folder in Android Studio, allow Gradle sync, then run the `app` configuration on an emulator or device.

Run unit tests from the project root:

```bash
./gradlew test
```

Build a debug APK:

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk` after a successful build.

## Free/offline principles

No login, backend, analytics SDK, paid API, subscription, or ad SDK is included. Gameplay is local. The camera feature asks for camera permission only when opened. Camera frames are processed on-device and never uploaded. While **Collect training photos** is on (it is on by default, with a cap of 700 photos; switch it off or use **Delete training photos** in the camera menu) the app saves photos of the board to `Pictures/OpenDarts/Training` on your phone, where only you can see them, so you can retrain the model on your own board.

## Build an APK without installing Android Studio

This repository includes a GitHub Actions workflow at `.github/workflows/build-apk.yml`.

1. Create a new GitHub repository for Open Darts.
2. Upload the extracted project files (including the hidden `.github` folder) to the repository and commit them to `main`.
3. Open the repository's **Actions** tab and select **Build Open Darts APK**.
4. Click **Run workflow** and wait for the build to finish.
5. Open the completed workflow run and download the `open-darts-debug-apk` artifact.
6. Extract that downloaded artifact ZIP. It contains `app-debug.apk`.
7. Transfer `app-debug.apk` to your Android phone and install it. Android may ask you to allow installation from the file manager/browser you used.

The APK is a debug build for testing. It is not a Play Store release. The cloud build runs on GitHub's hosted runner, so Android Studio and the Android SDK are not needed on your Windows PC.

## AI dart detection (recommended)

The app has two detection modes. If `app/src/main/assets/dartnet.tflite` exists, a small neural network (YOLOv8-nano) finds the four calibration points and the dart tips in every look, with no calibration and no alignment. The phone is meant to sit beside the board: the app finds the board, flattens the picture square-on using the four calibration points, and reads the darts on the flat board. The first pass is skipped once the board is known, a hidden corner is predicted from the other three, and the last board position is held while a hand is in the way. Without the file the app falls back to the classic detector.

To create the model without your own GPU, open `training/darts_training.ipynb` in Google Colab (free T4 GPU) and follow the steps. It trains on the public [DeepDarts dataset](https://ieee-dataport.org/open-access/deepdarts-dataset) (check its terms for your use) and can include your own labelled pictures. The DeepDarts pictures are all flattened square-on, so the notebook also generates side-view copies (`prepare_deepdarts.py --oblique 2`) so the network can find the board in a raw side-on frame; `training/eval_oblique.py` measures how a model copes with different side angles. The camera screen has zoom buttons (0.5x on phones with an ultra wide lens, 1x to 5x) and pinch zoom; the choice is remembered. Zoom out if the board is cut off, zoom in if it is small. When the network marks a dart on its barrel instead of the tip, `DartAxis` works out from the board's perspective which way a dart points and keeps only the mark nearest the board. To teach it your own board, phone and room, play with **Collect training photos** on in the camera menu: the app saves a photo at every dart, when the board empties and every few seconds, each with the corners and darts the network found. Whenever you correct a dart in the editor, the app also keeps the latest picture as a `...-fix.jpg` (it ignores the 700 cap, up to 100 more) noting what the camera said and what you chose, so the darts the network gets wrong become the first photos in `label_photos.py`, with the area you named marked. Copy `Pictures/OpenDarts/Training` from the phone to your PC, then run `python training/label_photos.py Training` (needs `pip install opencv-python`) to click the dart tips right in each photo, and `python training/from_captures.py Training --out my_photos`. To measure the model on your own photos run `python training/eval_real.py Training`: it reports score accuracy, recall, precision and tip error in mm (with and without the barrel filter) and lists the photos that went wrong. Zip `my_photos`, put it in Drive as `darts/my_photos.zip` and run the last cells of the notebook, which fine-tune the existing model with your pictures (a tenth of them is held back to measure the result). The public [dartscribe](https://huggingface.co/datasets/geforcefan/dartscribe) set (CC-BY-SA-4.0, Ercan Aky?rek) has side-view photos with clear-flight darts and the 81 wire crossings of the board in every picture, so `training/prepare_dartscribe.py` turns it into corner and dart-tip labels with no clicking (`--download DIR` fetches a public copy of it from a Hugging Face bucket, about 2 GB with plain HTTP, then `--dataset-dir DIR --out yolo_darts` adds it to the training folder after `prepare_deepdarts.py`; whole sessions go to one split). An optional second stage refines the tips: `training/make_tip_patches.py` cuts close-ups of every labelled tip out of the flattened pictures and `training/train_tip.py` trains a small network that says how far a detected mark is from the real tip (and whether it is a tip at all). Copy the resulting `tiprefine.tflite` next to `dartnet.tflite` in `app/src/main/assets/`; the app then moves every dart mark onto the tip it finds, and without the file nothing changes. `eval_real.py --tips tiprefine.tflite` measures it on your own photos. Even a few hundred checked photos make a visible difference, because the side-on accuracy otherwise comes from synthetic warps only. Copy the resulting `dartnet.tflite` into `app/src/main/assets/` and rebuild.
