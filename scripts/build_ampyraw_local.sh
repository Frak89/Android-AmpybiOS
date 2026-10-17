#!/usr/bin/env bash
# Build REAL AmpyRAW Cyberdark using the original project's Gradle toolchain.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="${1:-$HOME/src/AmpyRAW-Cyberdark}"
if [[ ! -f "$SOURCE/app/build.gradle" || ! -f "$SOURCE/settings.gradle" ]]; then
  echo "Original AmpyRAW checkout missing. Run scripts/prepare_ampyraw_source.sh first." >&2
  exit 2
fi
PIN="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["source_sha"])' "$ROOT/config/ampyraw-upstream.json")"
[[ "$(git -C "$SOURCE" rev-parse HEAD)" == "$PIN" ]] || {
  echo "AmpyRAW source is not the pinned reviewed commit $PIN" >&2; exit 2;
}
if [[ ! -f "$SOURCE/vendor/LibRaw/libraw/libraw.h" ]]; then
  echo "Missing LibRaw 0.22.2 in $SOURCE/vendor/LibRaw." >&2
  echo "The original AmpyRAW workflow clones the pinned LibRaw tag." >&2
  exit 2
fi
command -v gradle >/dev/null || { echo "Gradle 8.13 is required" >&2; exit 2; }
# Use the app's own Gradle version instead of the Gradle 9.4.1 AmpyCore toolchain.
gradle_ver="$(gradle --version | grep '^Gradle ' | head -1 || true)"
[[ "$gradle_ver" == "Gradle 8.13" ]] || {
  echo "Expected Gradle 8.13, found: $gradle_ver" >&2; exit 2;
}
(cd "$SOURCE" && gradle --no-daemon --stacktrace :app:assembleDebug)
echo "Original AmpyRAW debug APK: $SOURCE/app/build/outputs/apk/debug/app-debug.apk"
echo "Not suitable for existing installed OTA app unless its signing certificate matches."
