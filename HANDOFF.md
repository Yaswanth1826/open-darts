# Open Darts: session handoff

Read this first in a new session. It replaces the long chat history.

## Project
Free Android darts app (Kotlin, Jetpack Compose, CameraX, TFLite), repo `D:\Projects\open-darts`.
Modules: `core` (pure Kotlin: engine, games, vision/tracker), `app` (UI, session, camera), `backend` (Firebase functions, project `open-darts-y1814`), `training` (Python, model training and labelling).
Camera scoring is the paid feature (Google sign-in, plan checked on the server, `entitlements?.access()?.premium != false`).
Goal: a premium, stable, fast app ready for Google Play.

## Owner preferences (important)
- Short, precise, verified answers. No vague or unchecked claims. Focus on solving problems.
- Commit locally with **no Co-authored-by trailer**. The owner pushes.
- Don't commit Kaggle notebook-only edits.
- Phone: Realme Narzo 30 Pro 5G (RMX2117, Android 12, Dimensity 800U, 8 GB).
- Google identity verification for the Play developer account was still pending.

## How to build and test (Windows, PowerShell)
```
$env:JAVA_HOME='C:\temp\jdk17\extract\jdk-17.0.14+7'; $env:Path="$env:JAVA_HOME\bin;$env:Path"
```
- Full check: `.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain -q`. It can take 10+ minutes; run it via `Start-Process cmd ... > t.log` hidden, wait, exit 0 with an empty log means success. Delete `t.log` afterwards.
- One class: `--tests "*Name*"`.
- Failures: `app\build\test-results\testDebugUnitTest\*.xml`, `core\build\test-results\test\*.xml`. Robolectric screenshots: `app\build\shots\*.png`.
- Files use CRLF. Commit with `git add -A; git -c core.safecrlf=false commit -q -m "..."` (CRLF warnings are harmless).
- Compose tests: labels like "25", "Miss", "Bull" also appear as buttons, so use `onAllNodesWithText(x).onLast()`. The message text includes the round label, so use `substring = true`.

## What exists (all committed, latest `5c09e8e`, nothing pushed)
- **Games:** X01, practice (Bob's 27, target drills, checkout practice, 121, JDC challenge, count-up, weak spots), party (Shanghai, Baseball, Halve-It, Killer), dart bot (premium), advanced stats, drills.
- **Bull-off at the start of a match:** on by default, also for 2 players, camera games measure the exact distance from the bull. One dart at a time: measure, remove, next player throws; ties are rethrown by the tied players only. Rematch skips the bull-off. Players can say who was closer in keypad mode.
- **Caller:** says "You need N" (not names). Bust and game shot are not spoken; they get visuals (red shake, confetti) and generated sound effects (`ui/common/SoundFx.kt`, `Announcer.effect`). Only plain calls and "180" are spoken.
- **UI redesign:** the top bar was replaced by a bottom-sheet `GameMenu` (⋮ button on the current card in keypad mode, overlay button on the camera view). Tapping a player tab hands over the turn (`GameSession.selectPlayer`). Each thrown dart chip has a red bin to delete it; tapping the number opens `DartEditor`. The camera's undo/refresh icons were removed (Undo and Rescan stay in the menu).
- **Theme:** lightened on 2026-11-26 (`ui/theme/Theme.kt`: Bg `#161B23`, Surface `#212833`, Raised `#2D3644`, Line `#414C5C`, Muted `#B4BFCE`).
- **Camera tracker** (`core/.../vision/ml/MlDartTracker.kt`): fixes for vanished darts, a dart hidden by another (`hiddenByMm` 40), closest-pair matching, the editor deferring the turn pass, and faster re-lock when the mat shifts (`jumpLooksToReset` 2, `graceLooks` 3, corner agreement `jumpCorners`).
  - Other constants: `sameDartMm` 8, `driftMm` 16, `removeAfterMisses` 3, `removeAllAfterMisses` 2, `removeAfterMissesNoHand` 16, `calmLooksToRemove` 2, `jumpFraction` 0.04, `holdLooks` 16, `handChangeShare` 0.05.
  - Detector: `TwoPassDetector`, model `dartnet.tflite` (19.3 MB, fp32), CPU 4 threads. A look runs every 200 ms (`LOOK_INTERVAL_MS`).
- **Training photos** (`ui/camera/TrainingCollector.kt`, `ScoringCamera.collect`): menu item "Save training photos to Gallery", saves to `Pictures/OpenDarts/Training` at each dart, when the board empties, and every 6 s. Only in camera games. **No cap** (removed). The counter is recounted from the Gallery on start. It shows fewer than the real number when earlier installs saved some (Android 11+ hides other installs' files). Photos carry corners, darts and notes in a JPEG comment; `training/label_photos.py` and `training/from_captures.py` turn them into YOLO data.
- **Data:** players, history and the saved game live in SharedPreferences. A reinstall over the same signing key keeps them; the Play build uses a different key, so one uninstall loses them unless an export/import is built.

## Open items and ideas
1. **Practice modes and bot games: not audited.** The owner asked "any bug in practice modes or bots?". Ask which mode misbehaves, then check `BotDriver`/`botStep`/`advance` padding, `selectPlayer`/`editDart`/`undo`/`previousPlayer` with bots, single-player `selectPlayer`, and `PlayerTabs` with 0 or 1 player.
2. **Photos "not saving":** the cap theory is fixed. If still nothing saves, check the menu shows a count and that it is a camera game (not keypad, not bot or practice on the keypad).
3. **Detection:** flight ends of new darts are sometimes read as darts (needs retraining photos). Two tips closer than about 5 mm: the model gives the second tip only a weak score (about 0.12, measured on the owner's D15 photo, 2026-11-28). `TwoPassDetector` now accepts weak marks (>= 0.08, `YoloDecoder.WEAK_SCORE`) within 16 mm of a sure dart on the flat pass and pulls them to 6 mm from it; decoder suppression is 0.007 (about 3 mm). On 200 holdout photos this found 1 extra dart and 0 false ones. **Retrain with close-pair darts** (copy-paste augmentation of darts 3-6 mm apart), then the weak path becomes a fallback. "Hand on board" can still fire from a moving mat (>5% of the picture changing). Suggest anti-slip tape or a weight on the mat. Ask for the `lookMs` figure from the phone and use "Save this frame for debugging". Verify on the phone that no phantom third dart appears from barrels.
4. **Performance:** consider a lighter or fp16 model and GPU use.
5. **Export/import of players and stats** before the first Play build.
6. **WDF bull-off option:** winner chooses to throw first or second.
7. **Not verified on a real phone:** the menu button over the live camera, sound volume and quality, the 36 dp bin target, the faster re-lock, the camera bull-off, the lighter theme.
8. `plan.md` is stale (last refreshed 2026-11-22). It is not in the repo, only in the old session folder. Use this file instead.
9. **Label review, then retrain (2026-11-28):** photos live in two git-ignored folders, `Screenshots\Training` (4395 photos, each with a `.labels.json`) and `Screenshots\Holdout` (382). Everything else (the old per-date folders, `my_photos*`, `my_holdout*`, the zips) was deleted; regenerate with `from_captures.py` and `pack_for_kaggle.py`. The owner is re-checking every label: `python label_photos.py ../Screenshots/Training --unreviewed` (a photo passed with SPACE is stamped `"reviewed": true`; `m` shows the model's cyan marks, `h` shows the key help). `audit_labels.py` (model vs label disagreements, writes `audit.json`) feeds `label_photos.py --queue audit.json`. After the review: rebuild the YOLO data from both folders, add close-pair copy-paste augmentation, retrain YOLOX-Tiny on Kaggle, and re-run the holdout evaluation.

## Release and Play
- Upload keystore `C:\Users\YashwanthNimmala\open-darts-keys\upload.jks`, alias `upload`. GitHub secrets are set. The `release-bundle` workflow builds the signed `.aab`. CI is `.github/workflows/build-apk.yml` (Robolectric cache and pre-fetch).
- To do after Google verification: create the app `com.opendarts`, upload the `.aab` to Internal testing, add the Play App Signing SHA-1 to Firebase and refresh the `GOOGLE_SERVICES_JSON` secret, create the subscription `camera_scoring` with base plans `monthly` and `yearly`, link the service account and the `play-rtdn` topic, register Play Integrity, set `APPCHECK_ENFORCE=true` and redeploy functions, then run the closed test (12 testers, 14 days).
- Firebase deploy: from `backend`, `$env:Path="C:\Program Files\nodejs;$env:Path"; $env:FUNCTIONS_DISCOVERY_TIMEOUT='120'`, then `firebase deploy --only functions` (run hidden, log to `deploy.log`). `APPCHECK_ENFORCE=false` until a Play build exists.
- The Blaze trial ends **20 Feb 2027**.
- Play listing kit: `play-listing.md` in the old session folder `C:\Users\YashwanthNimmala\.copilot\session-state\8a27cb30-2020-485e-8799-b92bacbd3aba\files\`. Older checkpoints are in `...\checkpoints\` there if details are needed.
- ABIs: arm64-v8a and armeabi-v7a. Debug APK is about 45 MB, release about 28 MB.

## Risks
- A patched APK could bypass the paywall because the model runs on the phone.
- Git history contains old AGPL/nano models: keep the repo private or rewrite the history.
- The dartscribe dataset is CC BY-SA 4.0.

## Engine notes
- `DartGame` snapshots are immutable. `throwDart` after `turnOver` implicitly calls `endTurn`. `advance()` pads a short visit with `DartHit.MISS`, so going back shows those misses (they can be corrected). The X01 leg starter rotates each leg; player order is throwing order and index 0 starts.
- `DartHit(base, multiplier)` has `single`, `double`, `treble` factories. The enum value is `Multiplier.TRIPLE`.
- `VisitCall.of` still returns "Bust"/"Game shot" text; the session just doesn't speak it.
