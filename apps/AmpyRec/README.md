# AmpyRec — Safe staging installer

**Status: source prototype.** Standalone Android 17 (API 37) app with
Storage Access Framework ZIP picker, local SHA-256 checks, private staging,
user confirmation, constrained JobScheduler and boot reminders.

**AmpyRec is NOT a physical partition.** It is the directory
`/data/user/0/dev.ampybios.rec/files/AmpyRec`, accessible only to this
application. It neither enters recovery nor replaces mounted OS partitions.
Nothing touches the Android bootloader, verified boot, boot/vendor, kernel,
`/system`, root, SELinux or the active/inactive A/B slots.

## LIVE package format (editable skin assets only)

```text
my-live-update.zip
├── ampyrec.json
└── config/themes/fusion.json
```

The root manifest is:
```json
{
  "schema": 1,
  "type": "live-theme",
  "files": [{
    "path": "config/themes/fusion.json",
    "sha256": "SHA256_OF_THE_EXACT_JSON_FILE_BYTES"
  }]
}
```

The theme JSON must use the existing AmpyCore theme schema (schema 1,
matching allowed theme ID, colors, corners, effects). Only four preset names
may be replaced, never arbitrary paths, binaries or scripts. All file names,
sizes and hashes are validated. The hashes protect file integrity, **not the
identity of the author**. Users must explicitly choose and confirm updates.
Use only packages from sources you trust.

The content becomes a new immutable `releases/<ZIP_HASH>` folder in the app
private storage. A single SharedPreferences pointer activates the completed
release. The optional, signature-permission broadcast sends small verified
JSON assets into AmpyCore's private theme area, where they are validated again.
**AmpyRec and AmpyCore must be signed by the SAME certificate** for live sync
to work. AmpyRAW uses its own signer and is not modified.

## The three buttons after verifying a LIVE ZIP

- **Applica ora:** execute safely within this app's UI; no reboot.
- **Rimanda:** the pending ZIP remains in private storage across reboots.
  Android delivers a **notification** (if notifications are permitted) after
  each user login/boot; Android will not let the app pop up over the lockscreen.
  The user chooses again when opening AmpyRec.
- **In background:** schedule a persisted JobScheduler job requiring battery
  not low and storage not low. Android decides its actual execution time. This
  applies ONLY themes; no silent APK/firmware installation.

Canceling removes a staged ZIP and its pending state, not already applied
themes. Android may delete app data when AmpyRec is uninstalled; export your
own package ZIP to retain a backup.

## SYSTEM / OTA packages

AmpyRec recognizes a conventional A/B OTA ZIP containing `payload.bin` and
`payload_properties.txt`, but ONLY ANALYZES IT; it never applies it. An OTA
requires an AmpybiOS-signed ROM, trusted update metadata, update_engine
privileges, Verified Boot compatible keys and rollback protection. Generic
source ZIP archives are not installable.

The app caps any ZIP to **512 MiB** to limit storage abuse. Large complete ROM
packages must be handled by a future dedicated system updater and are refused
rather than truncated.

## Build

Open `apps/AmpyRec` as an Android Studio project (SDK 37, AGP 9.2.0,
Gradle 9.4.1, JDK 17+), or run:

```bash
bash scripts/build_ampyrec_local.sh --doctor
bash scripts/build_ampyrec_local.sh build
```

After an actual successful build, expected output:
`apps/AmpyRec/app/build/outputs/apk/debug/app-debug.apk`.

No compiled APK or signed update payload is bundled with the sources.
