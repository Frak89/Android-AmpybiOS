#!/usr/bin/env bash
# End-to-end *unsigned* Pixel 7 Pro developer build. NOT a device flash script.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 /path/to/source-tree [--dry-run]" >&2
  exit 2
fi
DEST="$(realpath -m "$1")"
DRY_RUN=false
if [[ ${2:-} == "--dry-run" ]]; then
  DRY_RUN=true
elif [[ $# -eq 2 ]]; then
  echo "Unknown argument ${2}" >&2
  exit 2
fi

echo "AmpybiOS source pin: 2026100600 • Device: cheetah"
echo "Checkout destination: $DEST"
echo "Developer build only. NO flashing, no release signing, no OTA server."
if [[ "$DRY_RUN" == true ]]; then
  cat <<EOF
Planned steps (no commands executed):
1. python3 scripts/check_build_host.py '$DEST'
2. bash scripts/prepare_source.sh '$DEST'  # verify pinned tag, repo sync
3. yarn --cwd '$DEST/vendor/adevtool/' install
4. (cd '$DEST' && adevtool generate-all -d cheetah)
5. bash scripts/integrate_grapheneos.sh '$DEST' --dry-run
6. bash scripts/integrate_grapheneos.sh '$DEST'  # managed product files
7. bash scripts/build_cheetah.sh '$DEST'  # unsigned target-files
Optional: AMPY_USE_KERNEL_MIRROR=1 and AMPY_INCLUDE_CAM/RAW/CONNECT=1
EOF
  exit 0
fi

host_options=()
case "${AMPY_ALLOW_CONSTRAINED_HOST:-0}" in
  0) ;;
  1) host_options+=(--allow-constrained) ;;
  *) echo "AMPY_ALLOW_CONSTRAINED_HOST must be 0 or 1" >&2; exit 2 ;;
esac
python3 "$SCRIPT_DIR/check_build_host.py" "$DEST" "${host_options[@]}"
# The Android source tree is intentionally kept separate from AmpybiOS Git.
bash "$SCRIPT_DIR/prepare_source.sh" "$DEST"
if ! command -v yarn >/dev/null; then
  echo "Missing yarn from GrapheneOS vendor extraction prerequisites." >&2
  exit 3
fi
yarn --cwd "$DEST/vendor/adevtool/" install
if ! command -v adevtool >/dev/null; then
  echo "adevtool not in PATH. Install/activate it as documented by GrapheneOS." >&2
  exit 3
fi
(
  cd "$DEST"
  adevtool generate-all -d cheetah
)
bash "$SCRIPT_DIR/integrate_grapheneos.sh" "$DEST" --dry-run
bash "$SCRIPT_DIR/integrate_grapheneos.sh" "$DEST"
bash "$SCRIPT_DIR/build_cheetah.sh" "$DEST"
echo "Target files produced for development. DO NOT FLASH: not signed or tested."
