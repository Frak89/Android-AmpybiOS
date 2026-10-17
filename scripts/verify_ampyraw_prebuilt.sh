#!/usr/bin/env bash
# Validate an ORIGINAL AmpyRAW APK before inclusion in GrapheneOS.
# Reject repacked/debug/mismatched or unverifiable builds; never sign APKs.
set -euo pipefail
: "${AMPYRAW_PREBUILT_APK:?Set AMPYRAW_PREBUILT_APK to a locally built/signed AmpyRAW APK}"
: "${AMPYRAW_EXPECTED_APK_SHA256:?Set exact SHA256 of the approved APK}"
: "${AMPYRAW_EXPECTED_SIGNER_SHA256:?Set signer certificate SHA256 (ideally already installed)}"

[[ -f "$AMPYRAW_PREBUILT_APK" && -s "$AMPYRAW_PREBUILT_APK" ]] || {
  echo "AmpyRAW APK not found or empty" >&2; exit 3;
}
for binary in sha256sum apksigner aapt; do
  command -v "$binary" >/dev/null || {
    echo "Missing $binary (Android SDK Build Tools required)" >&2; exit 3;
  }
done
normalize() {
  printf '%s' "$1" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]'
}
expected_apk="$(normalize "$AMPYRAW_EXPECTED_APK_SHA256")"
expected_cert="$(normalize "$AMPYRAW_EXPECTED_SIGNER_SHA256")"
[[ "$expected_apk" =~ ^[a-f0-9]{64}$ && "$expected_cert" =~ ^[a-f0-9]{64}$ ]] || {
  echo "Expected APK and signer SHA256 values must each have 64 hex digits" >&2; exit 3;
}
actual_apk="$(sha256sum "$AMPYRAW_PREBUILT_APK" | cut -d' ' -f1)"
[[ "$actual_apk" == "$expected_apk" ]] || {
  echo "AmpyRAW APK SHA256 mismatch. Refusing ROM inclusion." >&2; exit 4;
}
signature="$(apksigner verify --verbose --print-certs "$AMPYRAW_PREBUILT_APK")" || {
  echo "AmpyRAW APK signature verification failed" >&2; exit 4;
}
actual_cert="$(printf '%s\n' "$signature" |
  sed -nE 's/^Signer #1 certificate SHA-256 digest: ([A-Fa-f0-9:]+).*/\1/p' |
  head -n1)"
actual_cert="$(normalize "$actual_cert")"
[[ "$actual_cert" == "$expected_cert" ]] || {
  echo "AmpyRAW signer certificate mismatch. Refusing ROM inclusion." >&2; exit 4;
}
# The package id MUST stay unchanged or OTA/installed app data break.
badging="$(aapt dump badging "$AMPYRAW_PREBUILT_APK")" || {
  echo "Unable to inspect APK manifest package name" >&2; exit 4;
}
first_line="$(printf '%s\n' "$badging" | head -n1)"
[[ "$first_line" == "package: name='it.ampy.ampyraw' "* ]] || {
  echo "Unexpected package identity: expected it.ampy.ampyraw" >&2; exit 4;
}
echo "Approved AmpyRAW: it.ampy.ampyraw; APK checksum and signer SHA256 match."
