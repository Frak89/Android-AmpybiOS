#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export AMPY_ANDROID_APP=AmpyLauncher
exec "$HERE/build_ampycore_local.sh" "$@"
