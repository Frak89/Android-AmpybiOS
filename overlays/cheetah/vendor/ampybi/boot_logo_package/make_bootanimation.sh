#!/usr/bin/env bash
set -euo pipefail

FRAMES_DIR="${1:-./frames}"
OUT_ZIP="${2:-./bootanimation.zip}"
WIDTH="${3:-1080}"
HEIGHT="${4:-2400}"
FPS="${5:-30}"

if [[ ! -d "$FRAMES_DIR" ]]; then
  echo "Frames directory $FRAMES_DIR does not exist" >&2
  exit 2
fi

# try to locate repository packer
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
REPO_PKG_SCRIPT="$REPO_ROOT/scripts/build_bootanimation.py"
if [[ ! -f "$REPO_PKG_SCRIPT" ]]; then
  REPO_PKG_SCRIPT="$(pwd)/scripts/build_bootanimation.py"
fi

python3 "$REPO_PKG_SCRIPT" --input "$FRAMES_DIR" --output "$OUT_ZIP" --width "$WIDTH" --height "$HEIGHT" --fps "$FPS"

echo "Created $OUT_ZIP"
