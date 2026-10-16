#!/usr/bin/env bash
set -euo pipefail

TREE="${1:-}"
if [[ -z "$TREE" || ! -f "$TREE/build/envsetup.sh" ]]; then
  echo "Usage: $0 /absolute/path/to/synced/grapheneos-tree" >&2
  exit 2
fi
cd "$TREE"

if [[ ! -d vendor/google_devices ]]; then
  echo "Warning: verify Pixel vendor extraction (adevtool generate-all -d cheetah) before building." >&2
fi

# Critical: GrapheneOS includes Updater when OFFICIAL_BUILD is enabled. Never
# set it to the nonempty string 'false'; unset it for this unspecialized build.
# We have NOT configured an independent AmpyOTA endpoint or signed release.
unset OFFICIAL_BUILD || true

# AOSP envsetup may refer to unset shell variables.
set +u
source build/envsetup.sh
lunch cheetah-cur-user
set -u

m vendorbootimage vendorkernelbootimage target-files-package
echo "Upstream development target-files built. No AmpybiOS patches, release signing or OTA server were applied."
