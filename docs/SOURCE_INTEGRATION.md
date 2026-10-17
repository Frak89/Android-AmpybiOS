# Integrating AmpybiOS into GrapheneOS Android 17

## What is actually available

- **Official pinned source manifest** and a real upstream Updater configuration have been fetched and committed in `upstream/grapheneos/` for inspection and provenance.
- **The full GrapheneOS/AOSP source tree has not been downloaded** in this environment: 30 GiB storage / 6 GiB RAM are not enough.
- An **AOSP Soong app integration** has been implemented for the AmpyCore *prototype*, without requiring root or introducing system privileges.

## On your Linux build workstation

Plan for **at least 32 GiB RAM and ~200 GiB free storage** (more recommended). Install dependencies from https://grapheneos.org/build, `repo` and Android vendor extraction tooling.

```bash
# From the AmpybiOS repository:
bash scripts/prepare_source.sh "$HOME/grapheneos-2026100600"

# After synchronization completes:
cd "$HOME/grapheneos-2026100600"
yarn --cwd vendor/adevtool/ install
adevtool generate-all -d cheetah

# Back in the AmpybiOS repository:
bash scripts/integrate_grapheneos.sh "$HOME/grapheneos-2026100600" --dry-run
bash scripts/integrate_grapheneos.sh "$HOME/grapheneos-2026100600"

# Build the OS from source (not yet a signed, functional AmpybiOS ROM):
bash scripts/build_cheetah.sh "$HOME/grapheneos-2026100600"
```

**The integration (a separate explicit step after vendor extraction)** copies `apps/AmpyCore/app/src/main` Java sources and resources plus theme JSONs into `vendor/ampybios/apps/AmpyCore`, builds the app as `android_app` on the product partition and appends a one-time product include to `device/google/pantah/device-cheetah.mk`. Other source projects (SystemUI, Settings, Launcher3, Updater) are not modified yet. A signed, supported AOSP product must be built and tested before flashing.

### Optional Android boot animation

Render `assets/boot/svg/0.svg` … to numbered PNGs, package with `scripts/build_bootanimation.py` and set:

```bash
AMPY_BOOTANIMATION_ZIP=/absolute/path/bootanimation.zip bash scripts/integrate_grapheneos.sh "$HOME/grapheneos-2026100600"
```

This installs an Android boot animation under `/product/media`, not the proprietary bootloader splash. It is safe to skip this stage until the animation is verified.

### OTA safety

Never copy the entire `overlays/` directory into a product partition: it contains source-only examples and documentation. The `prepare_source.sh` script performs only manifest verification and `repo sync`.\n\nUntil independent AmpyOTA hosting/signing is implemented and tested, the GrapheneOS Updater remains **disabled** in the custom build. The imported Updater XML is for reference only; its original official URL MUST NOT be used for any AmpybiOS build.

### Privacy apps and photographic modules

The app list is a specification, not packaged third-party APKs. AmpyCam now has a separate **unverified standalone Camera2/DNG source prototype**
under `apps/AmpyCam`. It is deliberately NOT preinstalled in the ROM until
its APK compilation, runtime tests, hardware capability checks and crash
handling have been verified. AmpyRaw and AmpyConnect remain future modules. Their upstream project licenses and binary signatures must be reviewed before inclusion.

## Release and patch strategy

The stable manifest `2026100600` is pinned by the signed `repo init` tag. AmpyCore overlays are reapplied from this repo after source updates; the script is designed to be idempotent and fails for unmanaged vendor paths. SystemUI and framework changes will be maintained as separately reviewed, versioned patches and must be rebuilt/tested against each new GrapheneOS tag. This cannot provide automatic compatibility with arbitrary upstream changes.

### Optional additional apps (developer builds only)

`AmpyCore` is the default product addition. The unverified standalone
`AmpyCam` and `AmpyRaw` Soong modules can be staged **explicitly**:

```bash
AMPY_INCLUDE_CAM=1 AMPY_INCLUDE_RAW=1 \
    bash scripts/integrate_grapheneos.sh "$HOME/grapheneos-2026100600" --dry-run
AMPY_INCLUDE_CAM=1 AMPY_INCLUDE_RAW=1 \
    bash scripts/integrate_grapheneos.sh "$HOME/grapheneos-2026100600"
```

The script copies only the app's Java/resources/assets/manifest into managed
`vendor/ampybios/apps` folders and appends **opt-in**
`PRODUCT_PACKAGES` entries. Neither app gets privileged/system UID rights.
The flags default to 0 until builds and Pixel tests pass. The signature-protected
theme sync requires the modules to be signed by the same key.

### Read-only AmpyConnect

The standalone `AmpyConnect` development module can also be staged with
`AMPY_INCLUDE_CONNECT=1`. It uses only user-entered HTTPS
`GET /api/states` for a Home Assistant server, stores no access token, and
does not control equipment. It needs the normal `INTERNET` permission,
not Android privileged permissions. All optional modules still default to off.
