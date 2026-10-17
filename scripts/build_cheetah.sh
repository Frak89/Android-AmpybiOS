#!/usr/bin/env bash
set -euo pipefail

TREE="${1:-}"
if [[ -z "$TREE" || ! -f "$TREE/build/envsetup.sh" ]]; then
  echo "Usage: $0 /absolute/path/to/synced/grapheneos-tree" >&2
  exit 2
fi
cd "$TREE"

if [[ ! -f vendor/ampybios/.managed-by-ampybios ]] ||
   ! grep -Fq '$(call inherit-product, vendor/ampybios/product.mk)' device/google/pantah/device-cheetah.mk; then
  echo "AmpyCore integration not detected; run scripts/integrate_grapheneos.sh first." >&2
  exit 3
fi
if [[ ! -d vendor/google_devices ]]; then
  echo "Pixel vendor extraction not detected; run 'adevtool generate-all -d cheetah' first." >&2
  exit 3
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
echo "AmpybiOS development target-files build finished (AmpyCore product addition only)."\necho "This is NOT a signed flashable ROM. SystemUI/OTA/root integration remains pending."
