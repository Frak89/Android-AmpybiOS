# AmpyLauncher — experimental Android HOME interface

Native Android app drawer and optional HOME candidate with:
- Hour/minute/second TextClock and battery percentage.
- Fast local search, sortable app labels, adjustable icon sizes 48–96 dp.
- Four shared AmpySkin presets with organic corner and radar-inspired borders.
- Signer-protected theme updates from AmpyCore; user can override locally.
- Tap to open an app; long-press to view its native Android app settings.
- Escape through **Impostazioni Android / Home** to select another HOME.

**No root, no Internet, no overlays, no accessibility service, no privileged
SystemUI patches.** Only apps matching MAIN+LAUNCHER are queried via Android
package-visibility `<queries>`, not `QUERY_ALL_PACKAGES`.

The Android manifest declares HOME but AmpyLauncher is **not** made the default
launcher without the user's selection in Settings → Apps → Default apps → Home.
The launcher is not preinstalled in developer ROM builds unless opted in.

Build independently in Android Studio (`apps/AmpyLauncher`, SDK 37, Gradle
9.4.1). Source stage only: APK compilation and real device runtime remain
unverified. Organic SVG editing, widget layouts, icon effects, gestures and
system overlays are separate implementation stages.
