#!/usr/bin/env bash
# Package verified 0.png…N.png frames without modifying bootloader images.
set -euo pipefail
if [[ $# -lt 2 || $# -gt 5 ]]; then
  echo "Usage: $0 PNG_FRAMES_DIRECTORY OUTPUT.zip [WIDTH=1080] [HEIGHT=2400] [FPS=30]" >&2
  exit 2
fi
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../../../../.." && pwd)"
python3 "$ROOT/scripts/build_bootanimation.py" --input "$1" --output "$2" \
  --width "${3:-1080}" --height "${4:-2400}" --fps "${5:-30}"
