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
