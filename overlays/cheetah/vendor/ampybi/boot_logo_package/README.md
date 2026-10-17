# AmpybiOS Organic Boot Animation

Animated morphing SVG artwork with neon violet/cyan palette, concentric rings
and a shimmering AmpybiOS logotype. This is an **Android boot animation**, never
a change to the locked Pixel bootloader's splash/Verified Boot warning.

The generator produces **editable SVG** with Python's standard library, or PNG
after installing CairoSVG (`python3 -m pip install cairosvg`). Numpy is not
required.

From the AmpybiOS repository root:

```bash
# Dependency-free vector previews:
python3 overlays/cheetah/vendor/ampybi/boot_logo_package/generate_boot_frames.py \
    --out /tmp/ampy-svg-frames --count 60 --width 1080 --height 2400 --svg-only

# PNG frames, if CairoSVG is installed:
python3 overlays/cheetah/vendor/ampybi/boot_logo_package/generate_boot_frames.py \
    --out /tmp/ampy-frames --count 90 --width 1080 --height 2400 --text AmpybiOS

# Package the real numbered PNG files in Android's bootanimation format:
bash overlays/cheetah/vendor/ampybi/boot_logo_package/make_bootanimation.sh \
    /tmp/ampy-frames /tmp/bootanimation.zip 1080 2400 30

# Only after generating and validating the ZIP, opt in during product staging:
AMPY_BOOTANIMATION_ZIP=/tmp/bootanimation.zip \
    bash scripts/integrate_grapheneos.sh /path/to/synced/grapheneos-checkout
```

Always use an **empty** output folder for frames. The generator refuses to mix
stale frames from different resolutions or frame counts. The PNG packager checks
numbering and dimensions. The ZIP is not an Android image or an APK.

The folder name `ampybi` is a legacy prototype asset location; system-stage
files are kept under `vendor/ampybios` and branding remains **AmpybiOS**.
