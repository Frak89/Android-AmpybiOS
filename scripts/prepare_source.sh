#!/usr/bin/env bash
set -euo pipefail

TAG="${AMPY_GRAPHENE_TAG:-2026100600}"
DEST="${1:-$PWD/grapheneos-$TAG}"
case "$TAG" in *[!0-9]*|"") echo "Expected numeric stable tag, got: $TAG" >&2; exit 2;; esac
for tool in repo git curl ssh-keygen python3; do
  command -v "$tool" >/dev/null || { echo "Missing $tool. See https://grapheneos.org/build" >&2; exit 2; }
done
mkdir -p "$DEST"
DEST="$(cd "$DEST" && pwd)"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "Syncing GrapheneOS stable tag $TAG to $DEST (large download)"
cd "$DEST"
repo init -u https://github.com/GrapheneOS/platform_manifest.git -b "refs/tags/$TAG"
mkdir -p "$HOME/.ssh"
curl --fail --silent --show-error https://grapheneos.org/allowed_signers -o "$HOME/.ssh/grapheneos_allowed_signers"
git -C .repo/manifests config gpg.ssh.allowedSignersFile "$HOME/.ssh/grapheneos_allowed_signers"
(cd .repo/manifests && git verify-tag "$(git describe)")
if [[ "${AMPY_USE_KERNEL_MIRROR:-0}" == "1" ]]; then
  echo "Opt-in: replace Pantah prebuilt download URL with SHA-pinned mirror (same upstream revision)"
  python3 "$SCRIPT_DIR/configure_kernel_mirror.py" "$DEST"
fi
repo sync -j8

# Apply AmpybiOS overlays (device/product fragments, vendor files, boot animation, APK prebuilds)
if [[ -d "$SCRIPT_DIR/../overlays/cheetah" ]]; then
  echo "Applying AmpybiOS overlays to $DEST"
  python3 "$SCRIPT_DIR/copy_overlays.py" "$DEST" || { echo "Overlay copy failed" >&2; exit 1; }
fi

echo "Sources synchronized. Next: upstream Pixel vendor extraction, then build."
