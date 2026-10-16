#!/usr/bin/env bash
set -euo pipefail
TREE="${1:-}"
if [[ -z "$TREE" || ! -f "$TREE/build/envsetup.sh" ]]; then
  echo "Usage: $0 /absolute/path/to/synced/grapheneos-tree" >&2
  exit 2
fi
cd "$TREE"
if [[ ! -d vendor/google_devices ]]; then
  echo "Warning: check Pixel vendor extraction (adevtool generate-all -d cheetah) before building." >&2
fi
export OFFICIAL_BUILD=false
# User builds are security-relevant; do NOT use userdebug for daily use.
source build/envsetup.sh
lunch cheetah-cur-user
m vendorbootimage vendorkernelbootimage target-files-package
echo "Unsigned build target files complete; no AmpybiOS ROM or OTA package signed."
