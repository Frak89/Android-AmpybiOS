# AmpyKernel Performance Lab — first working stage

This is **AmpyCore's on-device performance lab** and a design foundation for a future AmpyKernel. It is **not** a custom kernel or a system-wide CPU/GPU frequency controller.

## Current implementation
- Eco (30 FPS radar redraw budget, 15% glow cap, radar speed <=45 deg/s).
- Balanced (60 FPS, 40% glow cap, speed <=180 deg/s).
- Fusion Extreme (120 FPS, 65% glow cap, speed <=360 deg/s).
- Adaptive lower limits on Android thermal status, system battery saver, or <15% battery off charger.
- At thermal CRITICAL or higher, pause unnecessary animation.
- Activity lifecycle-aware monitoring of Android thermal callbacks, power save mode, battery, available system RAM, reported screen refresh rate and this **window's** total-frame durations.
- User-requested values are saved independently of temporary limits; when conditions improve, user presets are restored.
- All behavior is limited to the AmpyCore preview. Android OS thermal management remains authoritative.

The displayed slow-frame share estimates the fraction of rendered window frames whose TOTAL_DURATION exceeds 130% of one reported display interval, over the last sample window. **It is not a device-wide FPS counter or a measure of all missed VSYNCs.** Rendering frame pacing is best verified with Perfetto/FrameTimeline on a real Pixel.

## Kernel development reference
GrapheneOS kernel source for Pixel 7 Pro/pantah is separate from the prebuilt mirror:
```bash
git clone -b 17 --recurse-submodules https://gitlab.com/grapheneos/kernel_pixel_6.1.git
cd kernel_pixel_6.1
KLEAF_REPO_MANIFEST=aosp_manifest.xml ./build_pantah.sh --lto=full
```
Reference: https://grapheneos.org/build (Kernel → 6th through 9th generation Pixels).

**Do not replace prebuilts with this build automatically.** Kernel sources and prebuilts must be matched to the exact OS release and tested for vendor module / firmware ABI, Verified Boot, SELinux, OTA compatibility, boot, battery, radio, camera, and recovery. GrapheneOS update signing is *not* compatible with AmpybiOS signing.

## Future stages
1. Build AmpyCore debug APK on an Android 17 SDK-enabled runner.
2. Measure actual frame timing, battery, thermals and memory on cheetah against unchanged GrapheneOS.
3. Design minimal privileged platform integration, carefully permission-gated; do not write governor/sysfs nodes indiscriminately.
4. Only if metrics demonstrate benefits, produce a separately reproducible kernel patch series with regression tests.

Developers can run host-policy unit tests:
```bash
bash scripts/test_performance_policy.sh
```
