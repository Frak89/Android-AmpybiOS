# GrapheneOS integration — AmpybiOS (Pixel 7 Pro)

Source baseline: pinned `2026100600` / cheetah. Scripts and add-ons live in
this repository. Full Android/GrapheneOS sources must be synchronized on a
Linux host (32+ GiB RAM, ~220+ GiB storage recommended). This repository
**does not contain the entire upstream Android source tree**.

## Development checkout and app staging

```bash
bash scripts/prepare_source.sh "$HOME/src/grapheneos-2026100600"
# In the checkout, install vendor extraction prerequisites per upstream:
cd "$HOME/src/grapheneos-2026100600"
yarn --cwd vendor/adevtool/ install
adevtool generate-all -d cheetah

# Back in Android-AmpybiOS repository root:
bash scripts/integrate_grapheneos.sh "$HOME/src/grapheneos-2026100600" --dry-run
bash scripts/integrate_grapheneos.sh "$HOME/src/grapheneos-2026100600"
bash scripts/build_cheetah.sh "$HOME/src/grapheneos-2026100600"
```

The default integration stages only **unprivileged AmpyCore**. It adds
`vendor/ampybios/apps/AmpyCore` and a guarded product inheritance line in
`device/google/pantah/device-cheetah.mk`. Optional source modules
`AMPY_INCLUDE_CAM=1`, `AMPY_INCLUDE_CONNECT=1`,
`AMPY_INCLUDE_LAUNCHER=1` are all **off by default** and should only
be used for unverified developer builds. AmpyLauncher is not silently made
the default HOME app.

**Original AmpyRAW Cyberdark** is the ONLY RAW editor target (no shadow app).
It is kept in `Frak89/AmpyRAW-Cyberdark`. To include it, use
`AMPY_INCLUDE_RAW=1` plus an explicitly approved **pre-signed** APK and the
expected package checksum + installed signer certificate fingerprint.
See [full AmpyRAW instructions](AMPYRAW.md). The integration validates the
artifact **before** changing the GrapheneOS checkout, then stages it as
`android_app_import` `presigned: true` on the product partition.
It does not change `it.ampy.ampyraw` or copy a second editor source tree.

## Boot animation, OTA and security

```bash
AMPY_BOOTANIMATION_ZIP=/absolute/path/verified/bootanimation.zip \
  bash scripts/integrate_grapheneos.sh "$HOME/src/grapheneos-2026100600"
```

The ZIP must pass PNG/ZIP checks. This replaces Android's later bootanimation,
**not** proprietary Pixel bootloader art or Verified Boot warnings.

Do not copy arbitrary `overlays/` files to product partitions. Scripts refuse
unmanaged vendor folders. The custom GrapheneOS Updater is intentionally
unconfigured until an independently signed AmpyOTA service exists. Do not
use the official GrapheneOS updater for derivative builds.

The privacy app list remains a specification, not bundled third-party APKs.
No default root, unverified kernel swap, insecure permissions or OTA bypass
is introduced. A device release still needs signed images and hardware tests.
