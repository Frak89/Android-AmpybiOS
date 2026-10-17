# AmpyCam — first native Camera2 DNG capture prototype

AmpyCam is a **standalone Android app**, not yet preinstalled in AmpybiOS.

- Uses Android Camera2 (not GrapheneOS Camera code copied without review).
- Requests **only** camera permission; no Internet/root/background recording.
- Selects a back-facing RAW_SENSOR-capable camera, or reports unsupported hardware.
- Uses TextureView for local live preview, auto exposure/focus, a single DNG shutter.
- Writes DNG through DngCreator to app-created `Pictures/AmpyCam` in MediaStore.
- No Google services, account, telemetry or cloud uploads.
- Four selectable native AmpySkin presets from the same shared `config/themes/*.json` as AmpyCore, with per-app persistence. System-wide automatic synchronization is **not** yet available.
- Camera availability / exact RAW dimensions / thermal effects require **real Pixel testing**.

The same camera session must **not** be used by other apps simultaneously.
If permissions are denied, AmpyCam does not attempt capture.

## Compile

Open `apps/AmpyCam` as a separate Android Studio project. Use Android SDK
Platform 37, JDK 17+, Android Gradle Plugin 9.2.0 and Gradle 9.4.1.
Run Gradle Sync followed by **Build APK(s)**, or:

```bash
cd apps/AmpyCam
gradle :app:assembleDebug
```

APK target: `apps/AmpyCam/app/build/outputs/apk/debug/app-debug.apk`.

**Development limitations:** the code has not yet been compiled or tested on
actual hardware. It does not yet implement ISO/shutter/exposure controls,
focus stacking, RAW+JPEG pairing, multi-frame computational RAW, consistent
EXIF rotation, or animated global AmpySkin integration. Canon CR3 decoding
belongs to the future *separate* AmpyRaw editor. Never describe this prototype
as finished or camera-parity with GrapheneOS Camera.
