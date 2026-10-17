#!/usr/bin/env bash
# The same safe local build preflight as AmpyCore, targeting AmpyCam.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export AMPY_ANDROID_APP=AmpyCam
exec "$HERE/build_ampycore_local.sh" "$@"
