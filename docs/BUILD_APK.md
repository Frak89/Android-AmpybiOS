# Android Studio — AmpybiOS app builds

## AmpybiOS in-tree apps (source stage)

Open any of the following **four** independent projects in Android Studio:

- `apps/AmpyCore`
- `apps/AmpyCam`
- `apps/AmpyConnect`
- `apps/AmpyLauncher`

Their projects target Android SDK API 37, Android Gradle Plugin 9.2.0,
Gradle 9.4.1, JDK 17+. The repository currently has no complete Gradle
wrapper in these app directories. Debug APKs have **not** been confirmed
compiled because the configured private Actions jobs failed to start.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
bash scripts/build_ampycore_local.sh build
bash scripts/build_ampycam_local.sh build
bash scripts/build_ampyconnect_local.sh build
bash scripts/build_ampylauncher_local.sh build
```

## AmpyRAW is a DIFFERENT existing Android Studio project

Use the **original** [AmpyRAW Cyberdark repository](https://github.com/Frak89/AmpyRAW-Cyberdark)
instead of the removed `apps/AmpyRaw` prototype.
It uses SDK 36, AGP 8.13.2, Gradle 8.13, NDK 27.2 and LibRaw 0.22.2.

```bash
bash scripts/prepare_ampyraw_source.sh "$HOME/src/AmpyRAW-Cyberdark"
# Fetch the required LibRaw library per the original project's build workflow:
git clone --depth 1 --branch 0.22.2 https://github.com/LibRaw/LibRaw.git \
  "$HOME/src/AmpyRAW-Cyberdark/vendor/LibRaw"
# Open $HOME/src/AmpyRAW-Cyberdark in Android Studio.
# Or use Gradle 8.13:
bash scripts/build_ampyraw_local.sh "$HOME/src/AmpyRAW-Cyberdark"
```

**Signing matters:** changing AmpyRAW signing keys would block updates over
the existing installed APK and may require uninstalling, losing private data.
Do not use unsigned or differently signed test APKs as OTA releases.

To bundle AmpyRAW as a presigned AOSP product app, follow
[the SHA-256 + signer checks](AMPYRAW.md); no APK is bundled by default.

## CI and ROM constraints

Previous GitHub Actions jobs failed with no runner assignment, so no Android
compiler outputs have been verified. Check Actions permissions/runner
availability or compile with local Android Studio. A signed flashable
AmpybiOS ROM additionally requires a full GrapheneOS Linux build environment,
vendor files, custom verified-boot signing, OTA and Pixel 7 Pro smoke tests.
