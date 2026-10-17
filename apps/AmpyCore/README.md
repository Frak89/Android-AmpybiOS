# AmpyCore native Android prototype

M1 UI preview implemented in **plain Android Java + Canvas** to avoid third-party runtime dependencies. It is intended for Android 17 (API 37) and is usable as a standalone, unprivileged app.

**Working in this prototype:**
- Select Fusion, Cyberpunk, Futuristic and Minimal themes from the repository's shared `config/themes` assets.
- Independently curved rectangle corners via `Path.addRoundRect`, directly from saved profiles.
- An animated violet/blue/cyan radar perimeter using GPU-compatible Canvas `SweepGradient`.
- Toggle radar, reduce motion, adjust radar speed and glow.
- Per-theme preferences persisted locally in private app storage; reset to preset defaults.
- Eco, Balanced and Fusion Extreme live rendering budgets with thermal and low-battery fallback.
- Foreground-only temperature, battery, power saver, available RAM and AmpyCore window frame-duration monitor.
- No network permission, overlays, accessibility hooks, root, device admin or system privileges.

**Not yet implemented:** global theme replacement, SVG template editing/import, launcher, status bar long-press, integration with SystemUI, privileged device tweaks, animations in other apps, actual OTA updater. Those require reviewed ROM patches and platform integration.

To build with Android Studio, open **`apps/AmpyCore`** as a separate project; ensure Android SDK 37, JDK 17 and a Gradle version compatible with AGP 9.2.0 (Gradle 9.4.1). From the project directory:

```bash
gradle :app:assembleDebug
```

Result after a successful build: `app/build/outputs/apk/debug/app-debug.apk`. No release-signed system image is implied.

References: https://developer.android.com/build/releases/agp-9-2-0-release-notes and https://developer.android.com/about/versions/17/setup-sdk

## Organic Corner Designer

A real per-theme editor for four independent corner radii (0–96 dp) is now
available inside AmpyCore. Slider settings survive app restarts; "Reset" restores
the original theme defaults. This is local, standalone Android UI, not global
SystemUI modification.

See [local APK build and troubleshooting](../../docs/BUILD_APK.md).

## AmpyKernel Lab — Fusion Extreme preset

A dedicated theme-aware panel in AmpyCore offers Eco / Balanced / Fusion
Extreme with **Fusion Extreme as the default on fresh installs**, a **120 Hz
target** and enabled-by-default toggles for:
- **Standby Saver:** pause optional radar animation and the foreground-only
  diagnostics sampler after 30 seconds without touch; always stop on app pause,
  and immediately on screen-off. Touch reactivates them.
- **Proactive Thermal Adaptation:** reduce optional effects already at thermal
  LIGHT and MODERATE statuses. Turning off this proactive layer **does not**
  disable compulsory fallback at MODERATE, SEVERE, CRITICAL or system battery
  saver.

The application requests its own window's refresh rate through
`WindowManager.LayoutParams.preferredRefreshRate` (60/90/120 Hz), and displays
the Android-reported actual refresh rate. **120 Hz is not guaranteed.**
Android may ignore the preference for power/thermal/display considerations.
No global refresh setting, CPU/GPU frequency, device power saver, root, SELinux
or kernel setting is modified. The previous user-selected performance profile
is preserved across upgrades.

## AmpyRec LIVE theme imports

The [AmpyRec](../AmpyRec/README.md) APK verifies bounded ZIP packages then
can call AmpyCore's **signature-guarded** synchronous theme provider.
AmpyCore validates the complete batch again, commits its versioned theme
directory, returns a revision-matched acknowledgement, and makes the new
palette available when the editor resumes.
It does not modify ROM partitions or third-party apps.
