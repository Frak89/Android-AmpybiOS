# Build AmpybiOS developer target-files (Pixel 7 Pro)

**Not a factory image, not a signed release, not safe to flash.** This repo
holds patches/add-ons and source bootstrap scripts, not the ~90–136 GiB full
GrapheneOS tree. Current baseline: **GrapheneOS 2026100600** / Android 17.

The upstream GrapheneOS guide:
https://grapheneos.org/build

## Linux workstation

- Linux x86_64
- At least 32 GiB RAM
- For a fresh lightweight source sync and build, reserve **220 GiB free**
  (upstream notes 90+ GiB source plus 100+ GiB build). More recommended.
- Upstream dependencies include Git, `repo`, Python, OpenSSH, curl,
  Node.js 24 LTS, `yarn`, zip, rsync and more; see upstream guide.

```bash
# From Android-AmpybiOS:
python3 scripts/check_build_host.py ~/src/grapheneos-2026100600
bash scripts/build_ampybios_dev.sh ~/src/grapheneos-2026100600 --dry-run

# If preflight passes:
bash scripts/build_ampybios_dev.sh ~/src/grapheneos-2026100600
```

Optional developer-only apps:
```bash
AMPY_INCLUDE_CAM=1 AMPY_INCLUDE_RAW=1 AMPY_INCLUDE_CONNECT=1 \
    bash scripts/build_ampybios_dev.sh ~/src/grapheneos-2026100600
```

The script never generates signing keys, unlocks bootloader, flashes images
or publishes OTA packages. It refuses low-resource hosts and missing tools.
It performs a verified fixed-tag source sync, vendor generation, explicit
AmpybiOS product integration and `target-files-package`.

## Sign and test before any installation

A real AmpybiOS ROM still needs signed release keys stored **off-repository**,
Verified Boot setup, independently signed A/B OTA, privacy app licensing,
device boot smoke tests and upstream CVE support strategy. Do not repurpose
GrapheneOS's official update server. Do not assume that a package named
`target-files` is user-flashable.

If GitHub Actions is blocked before a runner starts, this script can be
executed independently on a suitable **local or self-hosted** workstation.
