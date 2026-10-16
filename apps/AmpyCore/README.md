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
