#!/usr/bin/env bash
# AmpyRec is a standard Android APK; this does NOT compile GrapheneOS.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export AMPY_ANDROID_APP=AmpyRec
exec "$HERE/build_ampycore_local.sh" "$@"
