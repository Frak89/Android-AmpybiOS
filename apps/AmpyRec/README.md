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

AmpyRec does not retain duplicate decompressed theme files. After a
signed acknowledgement, AmpyCore stores the active theme in its own versioned
private release directory. AmpyRec retains only minimal transaction metadata. A signature-permission protected **ContentProvider.call()** sends the validated
release to AmpyCore synchronously. AmpyRec checks matching app certificates
before invoking the provider. AmpyCore validates the entire batch again,
commits its own private release atomically, and returns a positive acknowledgement.
Only after confirming that response does AmpyRec mark the update **applied**.
On error it remains staged for explicit retry. **Both APKs must share the
same signing certificate**. AmpyRAW remains independent.

## Memory-only immediate import

The screen now offers **Applica LIVE direttamente da RAM**. This never writes
a temporary ZIP to app-private storage: the selected compressed file is read
into app heap memory (maximum 256 KiB) and validated with `ZipInputStream`,
then sent to AmpyCore with same-signer authentication and an acknowledged
versioned import. The active config needs a small persistent copy **inside
AmpyCore**; not even a RAM-only path can persist customization without some
storage.

For **reboot-resumable jobs**, select persistent staging instead. Private
app storage is used; user photos, documents and existing apps are never
deleted. A first-time custom ROM installation is fundamentally different:
unlocking the bootloader wipes data, so export backups first.

## The three buttons after verifying a LIVE ZIP

- **Applica ora:** execute safely within this app's UI; no reboot.
- **Rimanda:** the pending ZIP or its persistent SAF reference remains available across reboots.
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
bash scripts/verify_ampyrec_apk.sh apps/AmpyRec/app/build/outputs/apk/debug/app-debug.apk
```

After an actual successful build, expected output:
`apps/AmpyRec/app/build/outputs/apk/debug/app-debug.apk`.

No compiled APK or signed update payload is bundled with the sources.


## Release engineering gates

**No release APK exists yet.** A distributable build requires an actual SDK 37
compilation, reproducible artifact hash, real APK signature verification,
instrumented ZIP scanner tests, IPC tests with both APKs signed by the same
key, reboot/deferred-job tests on Pixel 7 Pro and explicit signing/rollback
policy. The CI job will reject invalid package IDs and unintended permissions
after successful compilation. Never publish debug keys or test artifacts as a
production release.

**Trust boundary:** an embedded SHA-256 proves integrity of the selected ZIP's
contents, not its publisher. For unattended *system* updates, a separately
pinned trusted signing key and verified A/B OTA pipeline are mandatory. AmpyRec
does not claim to install source ZIPs, ROMs, kernels or SystemUI.

## Reboot-safe update WITHOUT a second ZIP copy

Choose **Seleziona ZIP · conserva solo riferimento, nessuna copia**. With
Android's Storage Access Framework, AmpyRec asks the document provider for
a **persistable read grant** and keeps only the `content://` URI, original
SHA-256, and pending state in app-private preferences. The ZIP itself remains
at its user-chosen location. The data is not copied into AmpyRec storage.

On a later execution, including after a reboot, AmpyRec reopens the original
document, checks the full ZIP digest and validates the themes again in memory.
If the file was moved, replaced or permissions revoked, installation stops.
JobScheduler still decides when background execution is allowed.

Three distinct modes now exist:
- RAM immediate: 256 KiB maximum; no ZIP staging on disk; not resumable.
- SAF reference: 256 KiB maximum; no duplicate ZIP; resumable while original
  document and read permission remain accessible.
- Private ZIP: up to 512 MiB for analysis, only allowlisted LIVE themes are
  installable; stores a copy to enable robust deferred operation.

No mode can modify firmware, existing user photos or account data. Same-ROM
signed A/B OTA can normally preserve user data but is not implemented in this
app. Initial migration from independently signed GrapheneOS **wipes data** due
to bootloader unlock and ROM installation; export backups beforehand.

## Source ZIP in Downloads → AmpybiOS graphical HOME session

The standard Android document picker now starts in Downloads where supported.
Choose the exact file **AmpybiOS.zip**. AmpyRec streams its compressed contents
from the original SAF URI with strict limits (256 MiB ZIP, 1 GiB inflated,
5,000 entries), validates safe paths, recognizes the expected AmpyCore,
AmpyRec and AmpyLauncher project structure, and computes a full-file SHA-256.
There is **no source extraction, dynamic compilation, DEX loading, chroot,
recovery boot, RAM disk, partition change, or firmware install**.

AmpyRec persists only the SAF read grant, source URI, digest and summary.
It can revalidate the same file later or after reboot. If it changes, disappears,
or access is revoked, revalidation fails closed and the original is not touched.

**A source ZIP is never an executable Android system.** To use the available
AmpybiOS-themed HOME interface instead, install a separately compiled
`dev.ampybios.launcher` APK, then in AmpyRec select
**Configura AmpyLauncher come Home Android**. Android requires user approval
for the default HOME selection. On subsequent boots, after unlocking, Android
uses the chosen HOME app automatically. GrapheneOS remains the underlying OS.
AmpyRec can also launch AmpyLauncher immediately without a reboot.

The user can revert the default HOME at any time in Settings → Apps → Default
apps → Home. **No user-data wipe occurs from simply selecting a launcher.**
The source archive is never executed or treated as a safe update payload.

The installed APK still needs compilation and on-device tests; the GitHub Actions
build environment has been failing before allocating any runner.
