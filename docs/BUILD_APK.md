# Build AmpyCore as a standalone Android APK

**Current target:** debug-only, app package `dev.ampybios.core`, no root privileges, no changes to other apps or Android SystemUI. This is **not** a bootable AmpybiOS operating system.

## Windows / Android Studio

1. Install Android Studio, JDK 17+, **Android SDK Platform API 37** and Build Tools from SDK Manager.
2. Download/clone this repository. Open the folder `apps/AmpyCore` in Android Studio, not the entire GrapheneOS source tree.
3. This experimental project currently has **no checked-in Gradle wrapper binary**. Set a local **Gradle 9.4.1** distribution in Android Studio's Gradle settings, or install Gradle 9.4.1 and build from its terminal. Do **not** run a `gradlew` command until a complete, audited Gradle wrapper is added.
4. Run Gradle Sync, then choose **Build → Build APK(s)**. The debug APK will appear at `apps/AmpyCore/app/build/outputs/apk/debug/app-debug.apk`. Install it on the Pixel only after the build succeeds.
5. **No bootloader unlocking required** to test this normal application.

Use a terminal (PowerShell example, provided local Gradle is available):
```powershell
cd apps/AmpyCore
gradle :app:assembleDebug --stacktrace --no-daemon
```

## Linux

Prerequisites: working Internet during initial dependency install, JDK 17+, Android SDK Platform 37, Gradle 9.4.1.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"  # use your actual SDK directory
bash scripts/build_ampycore_local.sh --doctor
bash scripts/build_ampycore_local.sh build
```

The dependency versions AGP 9.2.0 / Gradle 9.4.1 / JDK 17 / API 37 are documented at https://developer.android.com/build/releases/agp-9-2-0-release-notes.

## Why the GitHub Actions check fails before building

As observed on 2026-10-16, workflow jobs display `steps: []`, `runner_id: 0`, and finish in a few seconds. **No compiler step was executed**. Do not confuse this with an Android compile error.

Open **Actions** → the failed workflow → job annotations. Then inspect:
- **Settings → Actions → General** to ensure actions and runners are allowed.
- Account/org **Settings → Billing & licensing**, Actions quota / spending limits and billing configuration (only if prompted).
- Access to GitHub-hosted runners for this private repository.

We cannot determine the precise cause from the API response alone. The project must still be compiled and run on actual Android hardware after an available runner or local environment is configured.

## Smoke-test checklist

- Start AmpyCore and cycle through Fusion, Cyberpunk, Futuristic, Minimal.
- Test four independent corners, glow, radar speed and reduced motion; restart and verify settings persist.
- Toggle Eco / Balanced / Fusion Extreme; check that the preview caps and thermal fallback are reflected in UI.
- Rotate the Pixel; background/foreground the app; verify drawing resumes, without a runaway render loop.
- Verify no app requests root or accesses the network.
- Check UI with Android large text and screen magnification.

## All independent Android Studio projects (source stage)

- `apps/AmpyCore`
- `apps/AmpyCam`
- `apps/AmpyRaw`
- `apps/AmpyConnect`
- `apps/AmpyLauncher`

Open **one app's directory** in Android Studio and use `Build → Build APK(s)`.
The five projects are Gradle 9.4.1 / AGP 9.2 / Android SDK API 37 projects.
They are not yet confirmed to compile with installed Android SDK 37.

For Linux build preflight, from repo root:
```bash
ANDROID_HOME="$HOME/Android/Sdk" bash scripts/build_ampycore_local.sh --doctor
ANDROID_HOME="$HOME/Android/Sdk" bash scripts/build_ampycam_local.sh build
ANDROID_HOME="$HOME/Android/Sdk" bash scripts/build_ampyraw_local.sh build
ANDROID_HOME="$HOME/Android/Sdk" bash scripts/build_ampyconnect_local.sh build
ANDROID_HOME="$HOME/Android/Sdk" bash scripts/build_ampylauncher_local.sh build
```
