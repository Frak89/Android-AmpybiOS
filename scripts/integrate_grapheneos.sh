#!/usr/bin/env bash
# Integrate the standalone AmpyCore prototype into a synchronized GrapheneOS/AOSP tree.
# No permanent changes are made outside the supplied checkout.
set -euo pipefail

usage() {
  echo "Usage: $0 /absolute/path/to/grapheneos-checkout [--dry-run]" >&2
  echo "Run only after 'repo sync' and the Pixel vendor-file preparation." >&2
}
[[ $# -ge 1 && $# -le 2 ]] || { usage; exit 2; }
TREE="$(realpath "$1")"
DRY_RUN=false
if [[ ${2:-} == --dry-run ]]; then DRY_RUN=true
elif [[ $# -eq 2 ]]; then usage; exit 2; fi
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AMPY_INCLUDE_CAM="${AMPY_INCLUDE_CAM:-0}"
AMPY_INCLUDE_RAW="${AMPY_INCLUDE_RAW:-0}"
AMPY_INCLUDE_CONNECT="${AMPY_INCLUDE_CONNECT:-0}"
AMPY_INCLUDE_LAUNCHER="${AMPY_INCLUDE_LAUNCHER:-0}"
AMPY_INCLUDE_REC="${AMPY_INCLUDE_REC:-0}"
for value in "$AMPY_INCLUDE_CAM" "$AMPY_INCLUDE_RAW" "$AMPY_INCLUDE_CONNECT" "$AMPY_INCLUDE_LAUNCHER" "$AMPY_INCLUDE_REC"; do
  case "$value" in 0|1) ;; *) echo "Optional app flags accept only 0 or 1" >&2; exit 2;; esac
done
DEVICE="$TREE/device/google/pantah/device-cheetah.mk"
APP_SRC="$ROOT/apps/AmpyCore/app/src/main"
BP_SRC="$ROOT/platform/AmpyCore/Android.bp"
PRODUCT_SRC="$ROOT/platform/ampybios/product.mk"
TARGET="$TREE/vendor/ampybios"

for file in "$TREE/build/envsetup.sh" "$DEVICE" "$APP_SRC/AndroidManifest.xml" "$BP_SRC" "$PRODUCT_SRC" "$ROOT/config/themes/fusion.json"; do
  [[ -f "$file" ]] || { echo "Required source missing: $file" >&2; exit 3; }
done
if [[ -e "$TARGET" && ! -f "$TARGET/.managed-by-ampybios" ]]; then
  echo "Refusing to change unexpected directory: $TARGET" >&2
  exit 4
fi
if grep -q "BEGIN AMPYBIOS DEVICE INTEGRATION" "$DEVICE"; then
  grep -Fq '$(call inherit-product, vendor/ampybios/product.mk)' "$DEVICE" || {
    echo "Conflicting AmpybiOS device integration found; manual review needed." >&2
    exit 4
  }
fi
# Validate the optional archive BEFORE modifying device files.
if [[ -n "${AMPY_BOOTANIMATION_ZIP:-}" ]]; then
  [[ -f "$AMPY_BOOTANIMATION_ZIP" ]] || { echo "Missing AMPY_BOOTANIMATION_ZIP" >&2; exit 4; }
  python3 "$ROOT/scripts/verify_bootanimation.py" "$AMPY_BOOTANIMATION_ZIP"
fi
for app in AmpyCam AmpyConnect AmpyLauncher AmpyRec; do
  enabled="$AMPY_INCLUDE_CAM"
  if [[ "$app" == AmpyConnect ]]; then enabled="$AMPY_INCLUDE_CONNECT"; fi
  if [[ "$app" == AmpyLauncher ]]; then enabled="$AMPY_INCLUDE_LAUNCHER"; fi
  if [[ "$app" == AmpyRec ]]; then enabled="$AMPY_INCLUDE_REC"; fi
  if [[ "$enabled" == 1 ]]; then
    for required in "$ROOT/apps/$app/app/src/main/AndroidManifest.xml" \
        "$ROOT/platform/$app/Android.bp" "$ROOT/apps/$app/app/src/main/java"; do
      [[ -e "$required" ]] || { echo "Missing optional $app source: $required" >&2; exit 3; }
    done
  fi
done
if [[ "$AMPY_INCLUDE_RAW" == "1" ]]; then
  # Validation is fail-closed and performed BEFORE mutating the source checkout.
  # Inputs: AMPYRAW_PREBUILT_APK, AMPYRAW_EXPECTED_APK_SHA256,
  # AMPYRAW_EXPECTED_SIGNER_SHA256. Package identity must remain it.ampy.ampyraw.
  bash "$ROOT/scripts/verify_ampyraw_prebuilt.sh"
fi
if [[ "$DRY_RUN" == true ]]; then
  echo "DRY RUN: would stage AmpyCore native app into $TARGET"
  echo "DRY RUN: optional AmpyCam=$AMPY_INCLUDE_CAM, presigned ORIGINAL AmpyRAW=$AMPY_INCLUDE_RAW, AmpyConnect=$AMPY_INCLUDE_CONNECT, AmpyLauncher=$AMPY_INCLUDE_LAUNCHER, AmpyRec=$AMPY_INCLUDE_REC"
  echo "DRY RUN: would add product include to $DEVICE (once)"
  exit 0
fi

mkdir -p "$TARGET/apps/AmpyCore" "$TARGET/config"
# Copy as an independent AOSP module; Gradle is NOT invoked during the ROM build.
cp "$BP_SRC" "$TARGET/apps/AmpyCore/Android.bp"
cp "$APP_SRC/AndroidManifest.xml" "$TARGET/apps/AmpyCore/AndroidManifest.xml"
rm -rf "$TARGET/apps/AmpyCore/src" "$TARGET/apps/AmpyCore/res" "$TARGET/apps/AmpyCore/assets"
cp -R "$APP_SRC/java" "$TARGET/apps/AmpyCore/src"
cp -R "$APP_SRC/res" "$TARGET/apps/AmpyCore/res"
mkdir -p "$TARGET/apps/AmpyCore/assets"
cp "$ROOT"/config/themes/*.json "$TARGET/apps/AmpyCore/assets/"
cp "$PRODUCT_SRC" "$TARGET/product.mk"
for app in AmpyCam AmpyConnect AmpyLauncher AmpyRec; do
  enabled="$AMPY_INCLUDE_CAM"
  if [[ "$app" == AmpyConnect ]]; then enabled="$AMPY_INCLUDE_CONNECT"; fi
  if [[ "$app" == AmpyLauncher ]]; then enabled="$AMPY_INCLUDE_LAUNCHER"; fi
  if [[ "$app" == AmpyRec ]]; then enabled="$AMPY_INCLUDE_REC"; fi
  if [[ "$enabled" != 1 ]]; then continue; fi
  dest="$TARGET/apps/$app"
  source="$ROOT/apps/$app/app/src/main"
  mkdir -p "$dest"
  cp "$ROOT/platform/$app/Android.bp" "$dest/Android.bp"
  cp "$source/AndroidManifest.xml" "$dest/AndroidManifest.xml"
  rm -rf "$dest/src" "$dest/res" "$dest/assets"
  cp -R "$source/java" "$dest/src"
  cp -R "$source/res" "$dest/res"
  mkdir -p "$dest/assets"
  cp "$ROOT"/config/themes/*.json "$dest/assets/"
  printf '\n# Optional experimental %s app; enable only for test builds\nPRODUCT_PACKAGES += %s\n' "$app" "$app" >> "$TARGET/product.mk"
done
if [[ "$AMPY_INCLUDE_RAW" == "1" ]]; then
  mkdir -p "$TARGET/apps/AmpyRAW"
  cp "$ROOT/platform/AmpyRAW/Android.bp" "$TARGET/apps/AmpyRAW/Android.bp"
  cp "$AMPYRAW_PREBUILT_APK" "$TARGET/apps/AmpyRAW/AmpyRAW.apk"
  printf '\n# Original AmpyRAW Cyberdark, pre-signed from its independent repository\nPRODUCT_PACKAGES += AmpyRAW\n' >> "$TARGET/product.mk"
fi
# Remove obsolete AOSP copy of the old minimal AmpyRaw prototype only if this
# entire vendor folder is managed by this script.
if [[ -d "$TARGET/apps/AmpyRaw" ]]; then
  rm -rf "$TARGET/apps/AmpyRaw"
fi
cp "$ROOT/config/build.json" "$TARGET/config/build.json"
printf 'Generated by AmpybiOS integration script; checkout-owned content.\n' > "$TARGET/.managed-by-ampybios"

# Optional boot animation. It must be a ZIP made from numbered PNG frames.
# The initial bootloader splash / Verified Boot warning are untouched.
if [[ -n "${AMPY_BOOTANIMATION_ZIP:-}" ]]; then
  [[ -f "$AMPY_BOOTANIMATION_ZIP" ]] || { echo "Invalid AMPY_BOOTANIMATION_ZIP" >&2; exit 4; }
  cp "$AMPY_BOOTANIMATION_ZIP" "$TARGET/bootanimation.zip"
  cat >> "$TARGET/product.mk" <<'MK'
PRODUCT_COPY_FILES += \
    vendor/ampybios/bootanimation.zip:$(TARGET_COPY_OUT_PRODUCT)/media/bootanimation.zip
MK
fi

if ! grep -q "BEGIN AMPYBIOS DEVICE INTEGRATION" "$DEVICE"; then
  cat >> "$DEVICE" <<'MK'

# BEGIN AMPYBIOS DEVICE INTEGRATION
$(call inherit-product, vendor/ampybios/product.mk)
# END AMPYBIOS DEVICE INTEGRATION
MK
fi

echo "AmpyCore staged for Pixel 7 Pro; optional AmpyCam=$AMPY_INCLUDE_CAM, original AmpyRAW=$AMPY_INCLUDE_RAW, AmpyConnect=$AMPY_INCLUDE_CONNECT, AmpyLauncher=$AMPY_INCLUDE_LAUNCHER, AmpyRec=$AMPY_INCLUDE_REC."
echo "Not configured: root, unsigned OTA, third-party APK redistribution or system-wide theme hooks."
