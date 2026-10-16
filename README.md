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
- AmpyCam (RAW DNG capture), AmpyRaw (CR3/DNG photo processing), AmpyConnect (smart devices/industrial protocols).
- Privacy-first optional integration of Proton Mail/Calendar/VPN/Drive, Signal, Bitwarden, Aegis, Organic Maps, VLC and Accrescent; no Google apps preinstalled.

## Repository layout

- `config/`: device, OTA, privacy application selection and theme profiles.
- `assets/boot/`: editable SVG boot concepts.
- `scripts/`: pinned-source bootstrap, developer build and bootanimation packaging.
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
bash /path/to/Android-AmpybiOS/scripts/build_cheetah.sh ~/android/grapheneos-2026100600
```

The build script compiles **unmodified upstream sources** until AmpybiOS patches are reviewed and explicitly applied. A build produced this way is NOT yet branded or signed as AmpybiOS.

Optional PNG boot animation packager:
```bash
python3 scripts/build_bootanimation.py --input /path/to/numbered-pngs --output /tmp/bootanimation.zip --width 1080 --height 2400 --fps 30
```

Original SVG examples can be rasterized with a trusted, offline SVG editor before packaging. The resulting zip is **not automatically integrated** into a system image.

## Security and updates

Never commit signing keys, unlocked firmware, API tokens or root binaries. Updater and signature keys must be configured for an independently hosted AmpyOTA service; **never point a derivative build at the official GrapheneOS update server**. See [security](docs/SECURITY.md), [upstream](docs/UPSTREAM.md) and [roadmap](docs/ROADMAP.md).

Current upstream source tag target: **2026100600** (checked on 2026-10-16; change only after verifying the newest stable `cheetah` release).
