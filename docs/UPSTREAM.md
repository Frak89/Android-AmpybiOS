# GrapheneOS upstream and build policy

- Device: Pixel 7 Pro (cheetah), Pixel kernel target: pantah.
- Pin upstream: `GrapheneOS/platform_manifest` signed tag `2026100600` (as of 2026-10-16).
- Do not copy the massive AOSP/GrapheneOS checkout into this repository. `repo` maintains its source projects separately.
- Verify signed tag against `https://grapheneos.org/allowed_signers` **before** syncing.
- Vendor binaries are extracted with `yarn --cwd vendor/adevtool/ install`, `adevtool generate-all -d cheetah`.
- Target: `lunch cheetah-cur-user`, then `m vendorbootimage vendorkernelbootimage target-files-package`.
- When custom patches are ready, keep them under `patches/` with per-tag applicability and tests. A changed upstream release is not assumed compatible until patches build and pass device tests.
- Stable production builds require privately held AmpybiOS signing keys, independent update metadata, a custom updater URL, verified boot configuration and factory / OTA tests.
- Root is not provided by the base build. A future explicitly requested Developer Root variant must be separately documented and clearly marked lower security.

Official references: https://grapheneos.org/build and https://grapheneos.org/releases
