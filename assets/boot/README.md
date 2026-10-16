# AmpyBoot

`svg/0.svg` … `svg/3.svg` are **source concepts**, not Android bootloader assets. Convert trusted SVG locally to 1080×2400 PNG named `0.png`, `1.png`, `2.png`, `3.png` (for example with Inkscape). Then package using `scripts/build_bootanimation.py`.

Android boot animation playback begins after the vendor-controlled bootloader stage. The earliest Pixel branding / verified-boot screen **cannot** be replaced by ordinary Android ROM theming. Frame ordering starts at 0 and must be contiguous.
