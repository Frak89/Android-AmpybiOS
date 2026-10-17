# AmpybiOS

**AmpybiOS** is an experimental Android 17 / GrapheneOS-derived operating system project for the **Google Pixel 7 Pro (cheetah)**. It is an **independent derivative, not an official GrapheneOS release**.

> Status: **M0 foundation / source-build tooling**. No flashable ROM, signed images, root implementation, automatic OTA server, or preinstalled third-party applications have been produced yet. **Do not flash development artifacts onto your daily-use phone.**

## Project goals

- Preserve the Android application model, GrapheneOS sandbox and security hardening.
- AmpyCore: system preferences, privacy controls, adaptive performance modes, live theme previews.
- AmpyFusion: cyberpunk/futuristic/organic interfaces and per-corner SVG-based shapes.
- AmpyLauncher, AmpyRadar window borders, animated rotations and transitions.
- AmpyBoot: numbered boot-animation frames (0.png, 1.png, 2.png, …); **not** a change to the proprietary Pixel bootloader splash.
- AmpyOTA: custom signed OTA channel `dev`, Wi-Fi-only automatic download, persistent theme profiles, reboot on confirmation.
- AmpyCam (RAW DNG capture), **AmpyRAW Cyberdark** (original existing photo editor), AmpyConnect (smart devices/industrial protocols).
- Privacy-first optional integration of Proton Mail/Calendar/VPN/Drive, Signal, Bitwarden, Aegis, Organic Maps, VLC and Accrescent; no Google apps preinstalled.

## Repository layout

- `config/`: device, OTA, privacy application selection and theme profiles.
- `assets/boot/`: editable SVG boot concepts, plus the generated organic motion tool under `overlays/`.
- `scripts/`: pinned-source bootstrap, explicit product integration, developer build and validated bootanimation packaging.
- `tests/`: host-side checks for JSON profiles and animation output.
- `docs/`: upstream guidance, project milestones, security model.

## Getting started (Linux x86_64)

GrapheneOS's official instructions: https://grapheneos.org/build

A source checkout typically requires **90–136+ GiB**, plus **100+ GiB** build space and **32+ GiB RAM**. Install the Android `repo` tool, Git, OpenSSH, GPG, Node.js 24 LTS, yarn, and other prerequisites from the upstream guide.

```bash
python3 scripts/validate_config.py
python3 -m unittest discover -s tests -v
bash scripts/prepare_source.sh ~/android/grapheneos-2026100600
# Inside the checkout, follow upstream steps to generate Pixel vendor files:
# yarn --cwd vendor/adevtool/ install
# adevtool generate-all -d cheetah
bash scripts/integrate_grapheneos.sh ~/android/grapheneos-2026100600 --dry-run
bash scripts/integrate_grapheneos.sh ~/android/grapheneos-2026100600
bash scripts/build_cheetah.sh ~/android/grapheneos-2026100600
```

The integration currently adds only the unprivileged AmpyCore app to the GrapheneOS product. Source synchronization never blindly copies `overlays/` into the ROM. The build is **not signed or flashable**, and the system UI remains unchanged.

Optional PNG boot animation packager:
```bash
python3 scripts/build_bootanimation.py --input /path/to/numbered-pngs --output /tmp/bootanimation.zip --width 1080 --height 2400 --fps 30
```

Original SVG examples can be rasterized with a trusted, offline SVG editor before packaging. The resulting zip is **not automatically integrated** into a system image.

## Security and updates

Never commit signing keys, unlocked firmware, API tokens or root binaries. Updater and signature keys must be configured for an independently hosted AmpyOTA service; **never point a derivative build at the official GrapheneOS update server**. See [security](docs/SECURITY.md), [upstream](docs/UPSTREAM.md) and [roadmap](docs/ROADMAP.md).

Current upstream source tag target: **2026100600** (checked on 2026-10-16; change only after verifying the newest stable `cheetah` release).

## AmpyKernel Performance Lab (development)

AmpyCore now contains pure-Java adaptive **Eco / Balanced / Fusion Extreme**
preview budgets and a foreground thermal, battery, RAM and window-frame monitor.
These are **app-scoped rendering limits**, not kernel governor changes.
See [Performance Lab](docs/PERFORMANCE.md).

For the actual Pantah kernel sources (separate from precompiled mirror):
```bash
bash scripts/prepare_kernel_sources.sh ~/src/kernel_pixel_6.1
```
To gather non-privileged baseline measurements on an attached Pixel 7 Pro:
```bash
bash scripts/benchmark_cheetah.sh ~/benchmarks/pixel7pro-baseline
```

## Compile the first test APK

See **[Build AmpyCore APK](docs/BUILD_APK.md)** for Android Studio, local
Gradle, prerequisites and diagnosis of GitHub Actions jobs that fail before
runner allocation. You do not need root or a flashed ROM to test AmpyCore.

## App prototypes and skin contract

- **[AmpyCore](apps/AmpyCore/README.md)**: four live themes, organic corners,
  radar effects and adaptive rendering/thermal diagnostics.
- **[AmpyCam](apps/AmpyCam/README.md)**: standalone Camera2 live preview and
  RAW_SENSOR DNG capture to local scoped storage. Source stage, not device-tested.
- **[AmpySkin JSON contract](docs/THEME_CONTRACT.md)**: shared palettes and corner
  presets across the new in-tree apps, plus optional signed theme mapping to
  the original AmpyRAW editor. This is not global SystemUI skinning.
- **[AmpyBoot Organic Generator](overlays/cheetah/vendor/ampybi/boot_logo_package/README.md)**:
  reproducible vector frames, optional PNG rendering and integrity-checked ZIP.
- **[Current audit/status](docs/STATUS.md)**: implemented, planned and blockers.

Android Studio: open `apps/AmpyCore` or `apps/AmpyCam` independently.
Neither project is a flashable firmware image. See [APK build notes](docs/BUILD_APK.md).

## Canonical AmpyRAW editor

The RAW editor is the **existing** [Frak89/AmpyRAW-Cyberdark](https://github.com/Frak89/AmpyRAW-Cyberdark)
project (Android package `it.ampy.ampyraw`). The duplicate minimal
`apps/AmpyRaw` prototype has been removed, along with its obsolete CI.
AmpyRAW has native LibRaw/CR3 source code, optical tools, presets, TIFF/JPEG
exports and its **own independent OTA signing identity**.

Use [AmpyRAW integration and signing instructions](docs/AMPYRAW.md)
to build the original app in Android Studio or stage a **verified presigned**
APK in a ROM (explicit `AMPY_INCLUDE_RAW=1`; disabled by default).
The original editor requires **Gradle 8.13 / AGP 8.13.2 / NDK 27.2**.
AmpyCore/Cam/Connect/Launcher are independent, unverified API 37 projects.
No Android APK or signed ROM has been produced in this run.

- [AmpyConnect](apps/AmpyConnect/README.md): HTTPS-only read-only Home Assistant viewer.
- [AmpyLauncher](apps/AmpyLauncher/README.md): independent Android HOME candidate.
- [AmpyCore](apps/AmpyCore/README.md): theme/performance studio.
- [AmpyCam](apps/AmpyCam/README.md): separate Camera2 DNG capture prototype.

## ROM developer build (Linux x86_64)

Use the [validated source-to-target-files pipeline](docs/ROM_BUILD.md):
```bash
python3 scripts/check_build_host.py ~/src/grapheneos-2026100600
bash scripts/build_ampybios_dev.sh ~/src/grapheneos-2026100600 --dry-run
```
Neither command flashes a device or signs a production ROM.

- [AmpyLauncher](apps/AmpyLauncher/README.md): standalone HOME candidate
  with themeable app drawer, search, clock and battery. Opt in for experimental
  ROM staging with `AMPY_INCLUDE_LAUNCHER=1`; Android does **not**
  automatically change the default Home app.
