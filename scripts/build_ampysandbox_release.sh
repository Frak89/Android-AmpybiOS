#!/usr/bin/env bash
# Build the original private Python/C++ project with its own Gradle toolchain.
# The caller supplies a persistent APK keystore; no keys or credentials are made.
set -euo pipefail
[[ $# -eq 2 ]] || { echo "Usage: $0 /path/to/pinned/AmpybiOS /output-directory" >&2; exit 2; }
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$(realpath "$1")"
OUTPUT="$(realpath -m "$2")"
: "${AMPY_SANDBOX_KEYSTORE:?Set an existing persistent APK signing keystore outside Git}"
: "${AMPY_SANDBOX_KEY_ALIAS:?Set its signing alias}"
[[ -f "$AMPY_SANDBOX_KEYSTORE" ]] || { echo "APK keystore not found" >&2; exit 2; }
python3 "$ROOT/scripts/check_ampysandbox_source.py" "$SOURCE"
for binary in gradle sdkmanager python3.11; do
  command -v "$binary" >/dev/null || { echo "Missing build dependency: $binary" >&2; exit 2; }
done
[[ "$(gradle --version | sed -n '/^Gradle /p' | head -1)" == "Gradle 8.13" ]] || { echo "AmpybiOS requires Gradle 8.13" >&2; exit 2; }
[[ ! -e "$OUTPUT/AmpybiOS-release.apk" && ! -e "$OUTPUT/AmpySandbox.lock.json" ]] || { echo "Refusing to overwrite an existing app release" >&2; exit 2; }
# fontTools is a documented test dependency, not installed globally by this script.
python3.11 -c 'import fontTools' || { echo "Install fonttools in your Python 3.11 build environment" >&2; exit 2; }
sdkmanager 'platforms;android-37.0' 'build-tools;37.0.0' 'ndk;27.2.12479018' 'cmake;3.22.1'
AMPY_SDK_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -d "$AMPY_SDK_HOME/build-tools/37.0.0" ]] || { echo "Set ANDROID_HOME to the installed Android SDK" >&2; exit 2; }
export PATH="$AMPY_SDK_HOME/build-tools/37.0.0:$PATH"
for binary in apksigner aapt; do
  command -v "$binary" >/dev/null || { echo "Android Build Tools executable missing: $binary" >&2; exit 2; }
done
(
  cd "$SOURCE"
  python3.11 tools/fetch_ampy_assets.py
  PYTHONPATH=app/src/main/python python3.11 -m unittest discover -s tests -v
  python3.11 tools/generate_android_icon.py
  gradle --no-daemon :app:assembleRelease
)
UNSIGNED="$SOURCE/app/build/outputs/apk/release/app-release-unsigned.apk"
[[ -s "$UNSIGNED" ]] || { echo "Unsigned release APK was not generated" >&2; exit 3; }
mkdir -p "$OUTPUT"
sign_args=(sign --ks "$AMPY_SANDBOX_KEYSTORE" --ks-key-alias "$AMPY_SANDBOX_KEY_ALIAS")
# Omit these variables for interactive SDK password prompts. Never echo secrets.
if [[ -n "${AMPY_SANDBOX_KS_PASS:-}" ]]; then sign_args+=(--ks-pass env:AMPY_SANDBOX_KS_PASS); fi
if [[ -n "${AMPY_SANDBOX_KEY_PASS:-}" ]]; then sign_args+=(--key-pass env:AMPY_SANDBOX_KEY_PASS); fi
apksigner "${sign_args[@]}" --out "$OUTPUT/AmpybiOS-release.apk" "$UNSIGNED"
checksum="$(sha256sum "$OUTPUT/AmpybiOS-release.apk" | cut -d' ' -f1)"
signature="$(apksigner verify --print-certs "$OUTPUT/AmpybiOS-release.apk")"
certificate="$(printf '%s\n' "$signature" | sed -nE 's/^Signer #1 certificate SHA-256 digest: ([A-Fa-f0-9:]+).*/\1/p')"
python3 "$ROOT/scripts/verify_ampysandbox_prebuilt.py" --apk "$OUTPUT/AmpybiOS-release.apk" \
  --sha256 "$checksum" --signer-sha256 "$certificate" > "$OUTPUT/AmpySandbox.lock.json"
echo "Original AmpybiOS release APK and verification lock generated at: $OUTPUT"
echo "APK SHA256: $checksum"
echo "Signer certificate SHA256: $certificate"
echo "The signing identity is persistent only if you retain and reuse your keystore."
