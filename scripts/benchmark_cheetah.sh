#!/usr/bin/env bash
# Non-invasive AmpyCore frame/thermal snapshot for Pixel 7 Pro.
# Requires Android platform-tools and USB debugging authorized by the user.
set -euo pipefail
command -v adb >/dev/null || { echo "Missing adb platform-tools" >&2; exit 2; }
PACKAGE="${AMPY_APP_ID:-dev.ampybios.core}"
DEST="${1:-$PWD/benchmarks/$(date -u +%Y%m%d-%H%M%S)}"
# Do not leak phone serial or unrelated app data into reports.
if [[ ! "$PACKAGE" =~ ^[a-zA-Z0-9_.]+$ ]]; then
  echo "Invalid package name" >&2; exit 2
fi
TARGET="$(adb shell getprop ro.product.device | tr -d '\r')"
if [[ "$TARGET" != "cheetah" ]]; then
  echo "Expected Pixel 7 Pro cheetah, got '$TARGET'; not collecting data." >&2
  exit 2
fi
mkdir -p "$DEST"
{
  echo "AmpybiOS AmpyCore baseline diagnostic"
  echo "Timestamp UTC: $(date -u +%FT%TZ)"
  echo "Device: cheetah"
  echo "Application: $PACKAGE"
  echo "Scope: sample only; no root or kernel configuration changes"
} > "$DEST/context.txt"

# Android gfxinfo gives window drawing stats, not global display FPS.
adb shell dumpsys gfxinfo "$PACKAGE" framestats > "$DEST/gfxinfo.txt"
adb shell dumpsys battery > "$DEST/battery.txt"
adb shell dumpsys thermalservice > "$DEST/thermal.txt" || true
adb shell cat /proc/meminfo > "$DEST/meminfo.txt"
echo "Saved $DEST (inspect for private data before sharing)."
