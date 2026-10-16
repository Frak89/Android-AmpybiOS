# AmpybiOS Pantah kernel provenance and mirror

The linked repository [Frak89/device_google_pantah-kernels_6.1](https://github.com/Frak89/device_google_pantah-kernels_6.1/tree/17/grapheneos) is a **Git mirror of prebuilt Pantah kernel artifacts**. The URL refers to branch `17`, folder `grapheneos/`, not a `17/grapheneos` branch. It has `Image.lz4`, `dtbo.img`, `.ko` modules, `System.map`, module load/blocklists. **It is not the complete C source tree of Linux 6.1, and it does not by itself add performance patches.**

Observed as of 2026-10-16:
- `Frak89/... ` branch `17` HEAD: `41587d506866a5b569dfb27259994ed8d4e4100d` (same commit as upstream GrapheneOS, dated 2026-10-15).
- GrapheneOS pinned manifest `2026100600` uses the **older** commit `2be4d1db4e292ddbe13be98fb6436bbcf1c55890` of this kernel-prebuilts repository.
- The fork also contains the older commit, verified through GitHub. We must NOT use the latest branch HEAD with the older OS base: kernel modules, firmware and OS must match.

## Opt-in mirror (reproducible)

Normally GrapheneOS `repo sync` downloads kernel binaries from GrapheneOS itself. To use the user's mirror **without changing their versions**:

```bash
mkdir -p ~/src/grapheneos-ampybios
cd ~/src/grapheneos-ampybios
repo init -u https://github.com/GrapheneOS/platform_manifest.git -b refs/tags/2026100600

# Verify the upstream signed tag as described in https://grapheneos.org/build.
# BEFORE repo sync, run from the AmpybiOS working tree:
python3 /path/to/Android-AmpybiOS/scripts/configure_kernel_mirror.py ~/src/grapheneos-ampybios
repo sync -j8
```

The script inspects the upstream release manifest, checks tag/codename/project and revision, then writes `.repo/local_manifests/ampybios-kernel-mirror.xml`. It never uses the fork's moving branch tip. It fails instead of mixing incompatible revisions.

**An optional clone alone does not alter AmpybiOS ROM performance.** Real kernel optimizations would require the corresponding kernel source, reproducible builds, ABI/module compatibility, SELinux and security tests, and measurements of frame timing, heat, battery and memory. Do not flash image or modules directly from this repository.

To return to upstream-only mode, remove only the generated local-manifest XML (after confirming its contents), then `repo sync` again. Upstream release signing, custom AmpybiOS signing and A/B OTA integration are separate tasks.

## Source kernel build (separate from prebuilts)

`scripts/prepare_kernel_sources.sh <destination>` clones the **actual source**
repository for the Pixel 7 Pro/pantah kernel from the upstream GrapheneOS GitLab.
This is separate from the owner's mirror of compiled `Image.lz4` and `.ko`
artifacts. Building with `KLEAF_REPO_MANIFEST=aosp_manifest.xml
./build_pantah.sh --lto=full` produces *unverified* developer kernel outputs.
The script intentionally never copies those outputs into the ROM checkout:
source commit, OS release, vendor modules, boot validation, security and battery
regression tests must match before such integration.

For non-invasive on-device measurements use
`scripts/benchmark_cheetah.sh /path/to/results` with USB debugging on a real
Pixel 7 Pro and `dev.ampybios.core` installed. See [PERFORMANCE.md](PERFORMANCE.md).
