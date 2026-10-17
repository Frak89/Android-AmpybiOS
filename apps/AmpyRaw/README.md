# AmpyRaw — non-destructive image editor (M0)

A standalone Android app with a real **Open Document → Preview → Brightness/
Contrast/Saturation → Export JPEG copy** path. Android's framework decodes
JPEG, PNG, WebP and HEIF/AVIF where supported. Uses Android Storage Access
Framework only, without broad storage permission, root, Internet, telemetry,
cloud services or modifying the user's original document.

- Four shared AmpySkin JSON themes and signature-protected AmpyCore theme updates.
- Preview is sampled to a maximum edge of **2048 pixels**, with color edits
  applied as a preview color matrix. A new file is written only after the user
  chooses an output using ACTION_CREATE_DOCUMENT.
- Output is an edited JPEG **at preview resolution**. This is *not yet* a
  full-resolution professional pipeline or persistent adjustment sidecar.
- **Canon CR3, DNG, CR2 and NEF remain explicitly unsupported at this stage.**
  Selecting one displays a truthful notice; the app does not fake RAW support.

## Source and build

Open `apps/AmpyRaw` in Android Studio, select Android SDK 37 and Gradle 9.4.1.
Run `gradle :app:assembleDebug` to attempt an APK build.

Future decoder must use tested LibRaw (license review, ABI support, memory and
pixel-size limits, sandboxing of untrusted RAW files, Canon R6 Mark II samples)
before we advertise CR3/DNG. Full resolution exports, lens profiles, curves,
history/undo and GPU processing are separate milestones.
