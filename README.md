# Open Darts

Open Darts is a free, offline-first Android darts companion. The first prototype includes a dark Jetpack Compose UI, a single-player X01 game, both digital-board taps and quick score entry, undo/reset, and an opt-in CameraX preview prototype.

## Current status

- Implemented: pure Kotlin dart scoring model and X01 rules; 301/501/701 selection; single/double/treble quick entry; tappable board; undo; double-out; optional double-in engine support; camera preview screen.
- Prototype only: camera preview. It does **not** yet detect dart tips or calculate scores from images.
- Next: improve board rendering, persist match history with Room, add Cricket and Around the Clock, and begin camera board calibration.

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

No login, backend, analytics SDK, paid API, subscription, or ad SDK is included. Gameplay is local. The camera feature asks for camera permission only when opened. Camera detection and model assets have not been added yet.

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
