#!/usr/bin/env bash
# Inspect a built AmpyRec APK; no attempt to install or sign it.
set -euo pipefail
APK="${1:?Usage: $0 /absolute/path/to/AmpyRec.apk}"
[[ -s "$APK" ]] || { echo "Missing/empty APK: $APK" >&2; exit 2; }

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$SDK" && -d "$SDK/build-tools" ]] || {
  echo "Android SDK Build Tools unavailable" >&2; exit 2;
}
AAPT="$(find "$SDK/build-tools" -name aapt -type f | sort -V | tail -n 1)"
SIGNER="$(find "$SDK/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
[[ -x "$AAPT" && -x "$SIGNER" ]] || {
  echo "Missing aapt or apksigner from Android SDK" >&2; exit 2;
}
"$SIGNER" verify --verbose --print-certs "$APK"
BADGING="$("$AAPT" dump badging "$APK")"
FIRST="$(printf '%s\n' "$BADGING" | head -n 1)"
[[ "$FIRST" == "package: name='dev.ampybios.rec' "* ]] || {
  echo "Unexpected APK application ID: $FIRST" >&2; exit 3;
}
PERMISSIONS="$("$AAPT" dump permissions "$APK")"
if printf '%s\n' "$PERMISSIONS" | grep -Eq "uses-permission.*name='(android.permission.INTERNET|android.permission.REBOOT|android.permission.WRITE_SECURE_SETTINGS|android.permission.MANAGE_EXTERNAL_STORAGE|android.permission.REQUEST_INSTALL_PACKAGES)'"; then
  echo "Refusing APK requesting unexpected dangerous/system-update capabilities" >&2
  exit 3
fi
printf 'APP PACKAGE: dev.ampybios.rec\nSHA-256: '
sha256sum "$APK" | cut -d' ' -f 1
echo "Signature and basic permission policy: PASS (not a device test)."
