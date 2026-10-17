# AmpyRec — safe install/defer model

AmpyRec is a **standard, unprivileged Android application**, not a new boot
partition or a bypass of GrapheneOS Verified Boot. The first milestone provides
safe LIVE theme application and honest OTA detection.

## State machine

```text
PICK ZIP → PRIVATE COPY / CHECK BOUNDS → INSPECT / SHA-256
  ├─ Generic source ZIP → analysis only (no install)
  ├─ A/B system payload ZIP → analysis only (not cryptographically authorized)
  └─ valid AmpyRec theme manifest
          │
          └─ verified private staged.zip
                   ├─ Apply now → verified release in AmpyCore, synchronous signed acknowledgement
                   ├─ Next boot → notification if permitted, user chooses
                   └─ Background → constrained reboot-persistent JobScheduler
```

Repeated reboots do not lose a deferred update (unless app data is removed).
The app does not start Activity from the background after reboot. It never
initiates reboot, flash, recovery, root operation or on-device firmware build.

**Why not allocate an AmpyRec partition?** Locked-down GrapheneOS cannot
modify the partition table safely from an app and a raw-source archive cannot
be turned into bootable firmware without a full Android build chain. Instead
use Android's supported A/B update_engine from an authorized *future* OTA
service, writing an inactive slot after verifying signed payload metadata.
That service must be an independently reviewed component of AmpybiOS itself,
not an exported root helper accessible to ordinary APKs.

Boot reminders depend on notification permission and user preferences.
JobScheduler background tasks depend on Android device conditions and cannot
guarantee immediate execution. See [app instructions](../apps/AmpyRec/README.md).

## Cross-app commit and failure semantics

The current LIVE path uses `content://dev.ampybios.core.themes` with a
provider protected by Android's signature permission. The receiving provider
additionally verifies the calling UID belongs to `dev.ampybios.rec`. A
complete batch is validated before staging. AmpyCore activates the release
with one committed preferences pointer; AmpyRec reports success **only after**
receiving the revision-matched reply.

Jobs carry the specific ZIP SHA-256 they were queued for. If an update is
cancelled or replaced, its delayed job cannot apply that older revision or
write a misleading deferred status over the newer one.

The filesystem staging directory is private to the app. The system installer
must eventually be separately reviewed and shipped as an AmpybiOS-signed
privileged service using Android's A/B update_engine. There is no approved
device-flashing implementation in this app.

## RAM-only immediate import

AmpyRec can now use a **direct in-memory route**:

1. User selects a ZIP using Android's document picker.
2. The ZIP is read into a bounded `byte[]` (hard cap **256 KiB**).
3. `ZipInputStream` checks entry paths, duplicate names, inflated sizes,
   allowlisted JSON schemas and per-file SHA-256.
4. A signer-matched, synchronous content-provider call imports the verified
   theme into AmpyCore. Only AmpyCore persists the newly active theme data.
5. AmpyRec returns success only if AmpyCore acknowledges that exact revision.

This avoids both a temporary ZIP copy and an app-private ZIP staging write;
heap allocations still consume RAM, and the selected original file remains
where the user stored it. The mode is immediate only: **RAM does not survive
reboot, OS memory reclaim, app crashes or power loss**. Deferred/background
mode uses app-private persistent storage instead and can therefore resume.

No path in either mode modifies user media, user files, encryption keys, OS
partitions or the Android filesystem outside the participating apps' private
directories.

### Retention of user data across genuine ROM updates

An OTA signed with the **existing AmpybiOS release keys** and compatible
with the current install can normally update the inactive A/B slot and retain
user data. It is not a guarantee without release/rollback tests.

A **first-time switch from official GrapheneOS to independently signed
AmpybiOS requires unlocking/installation and a data wipe**. The bootloader
unlock (and subsequent lock) explicitly wipe data. No recovery/RAM trick or
partition copy can honestly promise to preserve the existing encrypted data
during that change. Back up everything externally before a ROM migration.

Do not stage full OTA ZIPs into device RAM or create an unofficial
RAM-backed boot partition; use signed Android A/B streaming update support
from a privileged updater component in an installed AmpybiOS ROM.

## Persistent zero-copy staging

The reference-only option uses `ACTION_OPEN_DOCUMENT` and
`takePersistableUriPermission(READ)`, stores only the source URI and SHA-256
in preferences, and deliberately avoids duplicating the ZIP. Both deferred
and background installations re-open the original file and revalidate its
digest before invoking the signature-protected AmpyCore importer. If the
file or provider is no longer available, the request fails closed.

This is distinct from entirely volatile RAM: only the *reference*, not ZIP
bytes, persists through reboot. The original file remains in user-managed
storage and is NEVER deleted by AmpyRec.

## Prerequisites for real deployment

- A real Gradle/SDK 37 build and passing JVM tests on the *actual* parser.
- Signed APK identity and permissions inspection.
- Same-signature integration tests with AmpyCore on Android 17.
- Device tests for URI grants across reboots, cancellation, process death,
  interruptions, storage pressure, app upgrades, malformed ZIP and user data.
- A separate, release-key-authenticated update_engine/Virtual A/B system
  updater in AmpybiOS; **no source ZIP can replace a buildable ROM**.
