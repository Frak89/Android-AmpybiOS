#!/usr/bin/env bash
# Standalone AmpyRaw debug APK; no system firmware or root changes.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export AMPY_ANDROID_APP=AmpyRaw
exec "$HERE/build_ampycore_local.sh" "$@"
