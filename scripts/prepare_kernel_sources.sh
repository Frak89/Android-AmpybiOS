#!/usr/bin/env bash
# Download separately built GrapheneOS Pixel 7 Pro kernel sources.
# Does not modify the ROM checkout or prebuilt images.
set -euo pipefail

SOURCE_URL="https://gitlab.com/grapheneos/kernel_pixel_6.1.git"
BRANCH="${AMPY_KERNEL_BRANCH:-17}"
DEST="${1:-$PWD/kernel_pixel_6.1}"
if [[ "$BRANCH" != "17" ]]; then
  echo "Only the reviewed GrapheneOS kernel branch 17 is allowed here." >&2
  exit 2
fi
command -v git >/dev/null || { echo "Git is required" >&2; exit 2; }
if [[ -e "$DEST" ]]; then
  echo "Destination already exists. Refusing to overwrite existing sources: $DEST" >&2
  exit 2
fi
git clone -b "$BRANCH" --recurse-submodules "$SOURCE_URL" "$DEST"
cd "$DEST"
echo "Kernel source revision: $(git rev-parse HEAD)"
echo "Downloaded kernel sources into $(pwd)."
echo "Next: audit this revision's compatibility with the pinned AmpybiOS OS build."
echo "Manual compile (after source review): KLEAF_REPO_MANIFEST=aosp_manifest.xml ./build_pantah.sh --lto=full"
echo "The output is NOT automatically copied into the GrapheneOS ROM checkout."
