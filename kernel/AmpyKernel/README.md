# AmpyKernel — verified editable function map (Pixel 7 Pro)

These 23 symbols were inspected against **GrapheneOS/kernel_common-6.1**,
branch `17`, source HEAD `a001019bb34ebe7b18cfc025b75a7550744a3e60`.
They are **real functions in the Linux 6.1 common kernel fork**, not features
already modified in a bootable AmpybiOS kernel.

> The complete **GrapheneOS/kernel_pixel_6.1** kernel tree was not downloaded
> here. The repository `Frak89/device_google_pantah-kernels_6.1` contains
> prebuilt `Image.lz4`, `.ko`, etc., not editable C bodies. Common-kernel branch
> HEAD is **not** guaranteed to match the `2026100600` OS source pin. Running
> the extractor against the actual pinned monorepo checkout is required.

## 1. Functions actually located in GrapheneOS common kernel

| Area | Functions | What can be changed | Risk |
| --- | --- | --- | --- |
| CPU governor | `sugov_should_update_freq`, `sugov_get_util`, `sugov_update_single_freq`, `sugov_next_freq_shared`, `sugov_update_shared`, `sugov_limits` | Reaction rate and CPU load/frequency mapping, *not* hardware safety limits | High |
| Scheduler | `find_energy_efficient_cpu`, `select_task_rq_fair`, `enqueue_task_fair`, `dequeue_task_fair`, `pick_next_task_fair` | Task placement/latency/energy policies | High |
| I/O cache | `page_cache_ra_unbounded`, `ondemand_readahead`, `page_cache_sync_ra`, `page_cache_async_ra` | Sequential read-ahead and cache strategy | Medium |
| RAM reclaim | `shrink_lruvec`, `shrink_node`, `do_try_to_free_pages` | Behavior under memory pressure | High |
| Frequency framework | `cpufreq_update_policy`, `cpufreq_driver_target` | CPU frequency framework; possible policy boundaries | High |
| Suspend | `suspend_enter`, `suspend_devices_and_enter`, `enter_state` | Suspend/wakeup control — DO NOT change in initial release | Critical |

All file locations and verified branch line numbers are in
[`config/ampykernel-functions.json`](../../config/ampykernel-functions.json).
Source links point to the reviewed branch commit, not an unverified blob.
Read-only editor tooling is available for a complete local checkout.

## 2. Extract exact editable C function bodies

```bash
# Linux workstation, from Android-AmpybiOS checkout:
bash scripts/prepare_kernel_sources.sh ~/src/kernel_pixel_6.1

# Then inspect 23 named functions from the REAL downloaded source.
python3 scripts/extract_ampykernel_functions.py ~/src/kernel_pixel_6.1 \
  --out ~/ampykernel-functions --include-bodies

# outputs:
# ~/ampykernel-functions/functions.json
# ~/ampykernel-functions/FUNCTIONS.md
# ~/ampykernel-functions/snippets/<symbol>.c.txt
```

The extractor detects whether sources are under `common/` (monorepo) or at
repo root (common kernel only), ignores comments/calls/prototypes, balances
curly braces, and records **source hash, exact function hash and line range**.
Missing symbols are flagged, rather than guessed. `--strict` returns an
error if any are missing.

**Do not paste isolated snippets into a kernel and expect them to compile.**
Make reviewed minimal patches against the original checked-out source; preserve
copyright headers, GPL-2.0 conditions and upstream file history.

## 3. Recommended AmpyKernel customization order

1. **Measure first** on unchanged kernel: 120 Hz frame timeline, device heat,
   app launch times, idle drain and available memory under reproducible load.
2. **Power HAL / userspace tuning before custom kernel patches** for UI-specific
   Eco/Balanced/Fusion profiles. Maintain Android thermal management.
3. Evaluate `sugov_should_update_freq` behavior and read-ahead under tests.
4. Only if benchmarking proves necessity, adjust energy-aware task placement,
   then consider reclaim. Measure thermals and power against baseline.
5. Leave suspend paths, thermal trip points, Pixel vendor module ABI, AVB,
   SELinux, crypto and verified-boot logic unchanged.

## 4. Vendor-specific functions not yet extracted

Pixel 7 Pro hardware-specific drivers reside separately in the full kernel
source, such as `private/devices/google/pantah` and
`private/google-modules`. **Do not infer their C function names from the
prebuilt .ko binaries.** After syncing the monorepo, index exact vendor
paths and source revisions independently, including CPU/thermal, GPU,
display, charging, touch and radio components. Treat firmware thresholds
and thermals as high-risk.

## 5. Build / sign / ROM constraints

GrapheneOS officially documents:

```bash
git clone -b 17 --recurse-submodules \
  https://gitlab.com/grapheneos/kernel_pixel_6.1.git
cd kernel_pixel_6.1
KLEAF_REPO_MANIFEST=aosp_manifest.xml ./build_pantah.sh --lto=full
```

The **verified** output intended for the ROM (after vendor ABI/security tests)
would replace **matching** prebuilt files under
`device/google/pantah-kernels/6.1/grapheneos/`. Do **not** mix kernels/modules
between releases, flash an unsigned image, or claim the product is ready.

Reference: https://grapheneos.org/build and https://grapheneos.org/source
