#!/usr/bin/env bash
# AmpyCore/AmpyCam standalone debug APK: local build with deterministic preflight.
# Use 'bash scripts/build_ampycore_local.sh --doctor' to inspect requirements.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="${AMPY_ANDROID_APP:-AmpyCore}"
case "$APP" in
  AmpyCore|AmpyCam) ;;
  *) echo "Only AmpyCore/AmpyCam standalone debug builds are allowed" >&2; exit 2 ;;
esac
PROJECT="$ROOT/apps/$APP"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
MODE="${1:-build}"

usage() {
  echo "Usage: bash scripts/build_ampycore_local.sh [--doctor|build]" >&2
  exit 2
}

[[ "$MODE" == "--doctor" || "$MODE" == "build" ]] || usage
failure=0
missing() { echo "FAIL: $1" >&2; failure=1; }
found() { echo "OK: $1"; }

if ! command -v java >/dev/null 2>&1; then
  missing "JDK 17+ not installed (JAVA_HOME / PATH)"
else
  version="$(java -version 2>&1 | head -1)"
  echo "Java: $version"
  major="$(echo "$version" | sed -nE 's/.*version "([0-9]+).*/\1/p')"
  if [[ -z "$major" || "$major" -lt 17 ]]; then
    missing "Java runtime must be JDK 17 or newer"
  else
    found "Java 17+"
  fi
fi
if [[ -z "$SDK" ]]; then
  missing "ANDROID_HOME (or ANDROID_SDK_ROOT) not configured"
elif [[ ! -f "$SDK/platforms/android-37/android.jar" ]]; then
  missing "Android SDK Platform 37 missing at $SDK/platforms/android-37; install from Android Studio SDK Manager"
else
  found "Android 17 SDK (API 37) at $SDK"
fi
if ! command -v gradle >/dev/null 2>&1; then
  missing "Gradle 9.4.1 command is not available; install Gradle and add it to PATH"
else
  gradle_info="$(gradle --version 2>&1 | grep -E '^Gradle ' | head -1 || true)"
  if [[ "$gradle_info" != "Gradle 9.4.1" ]]; then
    missing "Expected Gradle 9.4.1, found: ${gradle_info:-unknown}"
  else
    found "$gradle_info"
  fi
fi
if [[ "$failure" -ne 0 ]]; then
  echo "Prerequisites missing. See docs/BUILD_APK.md" >&2
  exit 2
fi
if [[ "$MODE" == "--doctor" ]]; then
  echo "Preflight OK: build prerequisites found."
  exit 0
fi

# No release keys, no bootloader unlock, no root and no firmware modification.
cd "$PROJECT"
gradle --no-daemon --stacktrace :app:assembleDebug
APK="$PROJECT/app/build/outputs/apk/debug/app-debug.apk"
[[ -s "$APK" ]] || { echo "Build ended without a debug APK at $APK" >&2; exit 1; }
echo "$APP debug APK: $APK"
