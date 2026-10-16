# Verified upstream source references

The files here are **unaltered copies of public GrapheneOS source files**, retrieved from GitHub to establish the exact Android 17 source baseline. They are NOT a complete GrapheneOS checkout.

- `platform_manifest-2026100600.xml`: GrapheneOS stable source manifest, official tag `2026100600`, maintained at https://github.com/GrapheneOS/platform_manifest ; the manifest pins the hundreds of AOSP / GrapheneOS repositories to hashes.
- `Updater-config-2026100600.xml`: original updater values in `GrapheneOS/platform_packages_apps_Updater` pinned at `52c34a55bd26f7856f9141d7e4237397084d6936` — **for inspection only**. Note: it points to `releases.grapheneos.org`. This file is NEVER used for AmpybiOS publishing or OTA unless replaced with a verified custom service first.
- GrapheneOS source copyright and license terms remain with their respective upstream projects. No source-tree ownership or official affiliation is claimed.

**Do not run repo init against this local manifest copy**: follow `scripts/prepare_source.sh` to initialize the official remote and **verify its signed release tag** before running `repo sync`. Offline local copies are useful for review and locking provenance, not for replacing upstream signature verification.

A complete source sync needs 90+ GiB for sources and another ~100 GiB to build; it is NOT in this GitHub repository.
