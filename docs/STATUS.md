# AmpybiOS engineering audit — 2026-10-17

This document distinguishes verified repository contents from **unbuilt** code.

## Compared repositories

**Kernel mirror**
[Frak89/device_google_pantah-kernels_6.1](https://github.com/Frak89/device_google_pantah-kernels_6.1)
branch `17` contains **precompiled Pantah kernel artifacts** (Image.lz4, dtbo.img,
System.map, .ko modules and load/blocklist files), not all Linux C sources.
The reference GrapheneOS release `2026100600` pins kernel prebuild commit
`2be4d1db4e292ddbe13be98fb6436bbcf1c55890`. The fork has that commit;
the observed newer branch head was
`41587d506866a5b569dfb27259994ed8d4e4100d`.
The owner mirror is opt-in and pinned via `scripts/configure_kernel_mirror.py`.
Do not combine incompatible artifacts.

**AmpybiOS repository**
[Frak89/Android-AmpybiOS](https://github.com/Frak89/Android-AmpybiOS)
is a small integration/template repository, **not** a clone of all GrapheneOS or
the kernel source. Full Android checkout + build require a larger Linux host.
`prepare_source.sh` now synchronizes only; `integrate_grapheneos.sh` explicitly
stages AmpyCore after vendor extraction. Upstream OTA server remains disabled.

## Implemented as repository source (not yet built on a device)

| Component | Code delivered | Remaining verification |
| --- | --- | --- |
| GrapheneOS pinned source pipeline | Signed-tag sync scripts, vendor-step docs | Full checkout, verified tag, build |
| Pantah kernel provenance | SHA-pinned mirror option, regression tests | Pixel ABI/firmware validation |
| AmpyCore | Four preset skins, corners, radar, performance lab | APK compile, UI and lifecycle tests |
| AmpyCam | Camera2 RAW_SENSOR preview, DngCreator save, local themes | APK compile and actual camera DNG captures |
| AmpyBoot | Original morphing SVG generator, optional CairoSVG PNG and ZIP packager | Boot animation playback on device |
| OTA | Dev configuration only | Independent signing, updater, server, AB OTA |
| AmpyRaw photo editor | Basic native preview/edit/export source added | APK compile, export resolution/quality/device tests |
| AmpyRaw CR3/DNG decoder | **Not implemented** | LibRaw integration + safety/licensing review |
| AmpyConnect | Home Assistant HTTPS read-only source prototype | APK build, TLS/device validation, local gateway tests |
| AmpyLauncher | Standalone HOME candidate source, app grid/clock/search | APK compile, Android HOME fallback testing |
| SystemUI global skin | **Not implemented** | Reproducible framework patches and tests |
| Secure root variant | **Not implemented** | Threat model / signing review |

## CI condition

As last observed, private GitHub Actions jobs fail without a runner allocated,
with zero executed steps. This is not evidence of a Java compilation error, but
**neither AmpyCore nor AmpyCam is compiled or device-tested**.

Do not flash development artifacts. Do not copy newer kernel binary blobs
into an older release or misrepresent a prebuilt kernel mirror as source C code.

## Update: source-stage modules added 2026-10-17

- `AmpyRaw` native non-destructive JPEG/PNG/WebP color preview and new JPEG
  copy: **not** Canon CR3/DNG, not full-resolution and not APK-tested.
- `AmpyConnect` HTTPS-only, user-initiated Home Assistant GET state viewer:
  **does not issue device commands or PLC writes**.
- `AmpyLauncher` standalone HOME candidate with clock/battery/app drawer
  and opt-in default selection, **not yet device-tested**.
- `AmpyCore` signature-permission broadcast now targets Cam/Raw/Connect/
  Launcher. Only same-signer recipients can accept the broadcast.
- `scripts/build_ampybios_dev.sh` and `check_build_host.py` provide a
  resource-checked path to unsigned target-files on a compatible Linux host.
  These scripts cannot build the full ROM on this limited runtime.
- Five independent Android Studio projects are present. Neither host Gradle
  nor Android SDK 37 is available in this assistant's container. **No APK or
  flashable signed image has been produced or tested.**
- GitHub Actions has consistently failed without a runner being assigned;
  this repository's Actions/billing status needs review by an administrator.

The project is at **prototype/source-integration stage**. A secure, flashable
AmpybiOS OS still requires complete synchronized upstream sources, build
and vendor setup, verified security patches, platform integration (especially
SystemUI), device validation, custom signing and OTA releases.
