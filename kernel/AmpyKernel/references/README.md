# Verbatim editable function references

These **five functions** were directly extracted from the verified
GrapheneOS/kernel_common-6.1 `17` source tree at revision
`a001019bb34ebe7b18cfc025b75a7550744a3e60`:

- `sugov_should_update_freq` — `kernel/sched/cpufreq_schedutil.c:65-107`
- `sugov_next_freq_shared` — `kernel/sched/cpufreq_schedutil.c:439-462`
- `page_cache_sync_ra` — `mm/readahead.c:715-740`
- `page_cache_async_ra` — `mm/readahead.c:743-762`
- `cpufreq_update_policy` — `drivers/cpufreq/cpufreq.c:2675-2694`

The extracted files are **plain text reference copies** (GPL-2.0), not a
buildable kernel module or a patch applied to AmpybiOS. Comments and function
logic are preserved; changing these copies **will not change the phone**.

For real changes, use the source extractor against the matching **full**
kernel_pixel_6.1 checkout, modify the function in its original location,
produce and review a git patch, compile Pantah + matching vendor modules,
run ABI/thermal/power tests, then integrate the *matching* artifact set into
an independently signed ROM. Never put newer head kernel binaries into a
different GrapheneOS release.

Run `python3 scripts/extract_ampykernel_functions.py <local-checkout>
--include-bodies --out <destination>` to get the entire catalog
(23 functions, with true hashes/line numbers for your checkout).
