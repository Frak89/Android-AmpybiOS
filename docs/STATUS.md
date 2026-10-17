# AmpybiOS engineering status — 2026-10-17

This is a source and integration repository, **not** a complete GrapheneOS OS
checkout or a signed bootable firmware image.

## Upstream provenance

- Pixel 7 Pro (cheetah), GrapheneOS pinned tag `2026100600`.
- `Frak89/device_google_pantah-kernels_6.1` contains **prebuilt** Pantah
  binaries. The pinned compatible commit is
  `2be4d1db4e292ddbe13be98fb6436bbcf1c55890` (not necessarily branch HEAD).
- Actual Pixel kernel source comes from a separate upstream source repository.
- No full GrapheneOS checkout, vendor extraction or device build has completed
  in this execution environment.

## Applications

| Component | Repository/source | Status |
| --- | --- | --- |
| AmpyCore | `apps/AmpyCore` | Theme/performance source; APK not verified |
| AmpyCam | `apps/AmpyCam` | Camera2 DNG source; APK/Pixel capture not verified |
| **AmpyRAW Cyberdark** | **`Frak89/AmpyRAW-Cyberdark`** | Canonical existing editor with native LibRaw source; original signing identity preserved |
| AmpyConnect | `apps/AmpyConnect` | HTTPS-only Home Assistant read-only source; APK unverified |
| AmpyLauncher | `apps/AmpyLauncher` | HOME candidate source; APK/device tests unverified |
| **AmpyRec** | `apps/AmpyRec` | ZIP scanner, LIVE theme staging, reboot reminders, JobScheduler source; APK compilation unverified |
| AmpyBoot | `overlays/cheetah/…/boot_logo_package` | Source generator, PNG packager and validation |
| AmpyOTA | `config/ampyota-dev.json` | Only a configuration, no signed update server |
| SystemUI/AmpySkin system-level | Planned | No system-wide framework patches yet |

**AmpyRAW replaces the earlier in-repository AmpyRaw prototype.** The
duplicate source app and its CI workflow were deleted. The original
Android package remains `it.ampy.ampyraw`. The integration pins its source
commit and requires an explicit local **approved, pre-signed APK**, verifying
package ID, APK SHA-256 and Android signer certificate SHA-256 before staging.

## Build and security blockers

- Private repository Actions jobs have previously failed before any runner
  was assigned; an Android compilation has NOT been confirmed here.
- A full GrapheneOS ROM requires a suitable Linux build host, signed images,
  Pixel-specific validation and an independent, authenticated AmpyOTA chain.
- No signing keys are stored in AmpybiOS. Do not unlock/flash daily-use devices
  with unsigned or unverified artifacts.
- Original AmpyRAW's pre-existing signing certificate may differ from
  AmpyCore's; signature-protected theme broadcasts fail closed when so.
- Test EOS R6 Mark II CR3 and C-RAW files on real hardware before claiming
  decoder correctness. Retain originals and backups.

See [AmpyRAW integration](AMPYRAW.md) and [GrapheneOS source integration](SOURCE_INTEGRATION.md).

## AmpyRec staging milestone

A standard nonprivileged APK project has been added. User-selected ZIPs are
scanned with resource limits and path/hash checks; only allowlisted LIVE JSON
themes can be applied. Repeated reboots preserve user-deferred work and a
notification can remind the user; queued background work uses Android's own
JobScheduler. System OTA ZIPs and raw source archives are never installed.
The storage location `files/AmpyRec` is NOT a physical partition.

As observed on 2026-10-17, the AmpyRec GitHub Actions build fails without a
runner or compiler step (`runner_name=null`, `steps=[]`). **No debug APK or
tested ROM has yet been generated.** See [AmpyRec](AMPYREC.md).

## Engineering correction — signature-acknowledged AmpyRec

The original asynchronous theme broadcast reported success without
confirmation. It has been replaced by a protected, synchronous provider call
requiring signature matching and an explicit acknowledgement. The consumer
versions its assets and commits an atomic release pointer. Background jobs
carry a SHA-256 revision guard to reject stale/cancelled work.

The APK workflow now includes a post-build APK signature/package/permissions
gate, but GitHub Actions is still unable to allocate a runner. This verifies
source controls **only**, not a compiled binary.

## 2026-10-17: memory and zero-copy hardening

- AmpyRec now offers direct RAM scanning and application for 256 KiB
  allowlisted themes with no temporary ZIP written to disk.
- Deferred execution can instead retain a read-only SAF document URI and
  SHA-256, avoiding a second ZIP copy. It rechecks bytes after reboot and
  aborts on missing/mutated content.
- The original private-disk staging path remains optional.
- RAM/disk parsers have separately added JUnit tests, plus comparison of
  accepted content and checks against ZIP path traversal/bombs; tests are
  committed but **not executed** because GitHub Actions has not provisioned
  a runner.
- Neither the app nor the kernel has passed Android APK/ROM compilation.
- No user data deletion is introduced by AmpyRec. A first-time custom ROM
  install cannot promise data retention across bootloader unlock.

### Source hardening note

AmpyRec's previous second copy of uncompressed active themes was eliminated.
AmpyCore alone holds the installed theme data. The original file is never
deleted; selecting reference-only staging persists a read grant and hash,
rather than copying the selected package. Execution on Android hardware is
still unverified; the latest GitHub Actions job had no allocated runner.

## Downloads source selection / HOME session (October 17)

AmpyRec source now recognizes a user-selected `Downloads/AmpybiOS.zip`
(with strict limits and full SHA-256) and retains a read-only SAF reference.
It links to Android's default HOME selector for separately installed
AmpyLauncher. This is a **userspace visual session**, not a RAM-booted ROM or
the execution of uncompiled GrapheneOS sources. The boot receiver can show a
user-visible notification; it does not auto-launch an Activity or flash images.
AmpyRec dev version is 0.2.0-dev. **Neither AmpyRec nor AmpyLauncher APK is
compiled and validated in this environment.**
