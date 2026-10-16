# AmpybiOS milestones

## M0 — repository foundation (in progress)
- [x] Fixed GrapheneOS stable tag target for Pixel 7 Pro and documented prerequisites
- [x] Source synchronization and build scripts
- [x] AmpyOTA dev configuration (not a live server)
- [x] Google-free privacy app plan, not yet bundled
- [x] Cyberpunk, Futuristic, Minimal, Fusion profile JSON and sample SVG frames
- [x] PNG boot animation packager and host tests
- [ ] Full OS source downloaded on build machine, hardware vendor blobs extracted
- [ ] Signed ROM and smoke test

## M1 — AmpyCore / AmpyFusion
- [ ] Installable Android AmpyCore prototype with persisted themes and previews
- [ ] Organic corners, animated radar border and system integration contract
- [ ] Import SVG templates with safe validation and fallback
- [ ] Launch screen, status bar long-press, safe-mode escape and AmpyLauncher

## M2 — SystemUI and boot
- [ ] Patch SystemUI with adjustable Quick Settings, transitions, shape renderer
- [ ] System-level animation integration and fullscreen accessibility validation
- [ ] Boot animation integrated into signed images; bootloader splash remains stock

## M3 — releases and privacy
- [ ] AmpybiOS private signing keys (generated off-repo)
- [ ] AmpyOTA real server + custom updater URL + dev metadata
- [ ] A/B OTA tests and retention across updates
- [ ] Optional privacy apps after license and signature audit
- [ ] Secure/research-root modes, no silent global superuser

## M4 — photography and connected devices
- [ ] AmpyCam DNG capture (Camera2 availability gates)
- [ ] AmpyRaw safe LibRaw-based CR3 decoding and non-destructive pipeline
- [ ] Shared AmpySkin theme contract
- [ ] AmpyConnect MQTT, Bluetooth LE, USB, HTTP, Home Assistant; guard PLC writes

## M5 — measured performance
- [x] Eco / Balanced / Fusion Extreme adaptive *AmpyCore-only* preview profile logic
- [x] Local thermal / battery / RAM / app-frame diagnostics (source stage, not device-tested)
- [x] Pure Java profile policy test harness (device-independent)
- [ ] Verify APK compilation on Android SDK 37 runner
- [ ] Frame-time and jank benchmarks at 60/120 Hz on Pixel 7 Pro
- [ ] Memory, storage, battery and thermal A/B tests
- [ ] Consider kernel changes only with measured benefit and security review
