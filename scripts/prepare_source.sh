#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TAG="${AMPY_GRAPHENE_TAG:-2026100600}"
PINNED_TAG="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["base_release_tag"])' "$SCRIPT_DIR/../config/build.json")"
if [[ "$TAG" != "$PINNED_TAG" ]]; then
  echo "Refusing GrapheneOS tag $TAG: AmpybiOS manifest and kernel mirror are pinned to $PINNED_TAG." >&2
  exit 2
fi
DEST="${1:-$PWD/grapheneos-$TAG}"
case "$TAG" in *[!0-9]*|"") echo "Expected numeric stable tag, got: $TAG" >&2; exit 2;; esac
for tool in repo git curl ssh-keygen python3; do
  command -v "$tool" >/dev/null || { echo "Missing $tool. See https://grapheneos.org/build" >&2; exit 2; }
done
mkdir -p "$DEST"
DEST="$(cd "$DEST" && pwd)"
echo "Syncing GrapheneOS stable tag $TAG to $DEST (large download)"
cd "$DEST"
repo init -u https://github.com/GrapheneOS/platform_manifest.git -b "refs/tags/$TAG"
mkdir -p "$HOME/.ssh"
curl --fail --silent --show-error https://grapheneos.org/allowed_signers -o "$HOME/.ssh/grapheneos_allowed_signers"
git -C .repo/manifests config gpg.ssh.allowedSignersFile "$HOME/.ssh/grapheneos_allowed_signers"
# Verify the *intended pinned tag*, not whichever tag 'git describe' picks.
(cd .repo/manifests && git verify-tag "$TAG")
if [[ "${AMPY_USE_KERNEL_MIRROR:-0}" == "1" ]]; then
  echo "Opt-in: replace Pantah prebuilt download URL with SHA-pinned mirror (same upstream revision)"
  python3 "$SCRIPT_DIR/configure_kernel_mirror.py" "$DEST"
fi
repo sync -j8

# Do NOT copy the overlay tree wholesale. Overlay docs/assets can contain
# source-only files and boot artwork which must never land on system partitions.
# The integration script stages only the known AmpyCore product files after
# vendor extraction and explicit review.
echo "GrapheneOS sources synchronized at $DEST."
echo "Next on the build machine:"
echo "  yarn --cwd $DEST/vendor/adevtool/ install"
echo "  cd $DEST && adevtool generate-all -d cheetah"
echo "  bash $SCRIPT_DIR/integrate_grapheneos.sh $DEST --dry-run"
echo "  bash $SCRIPT_DIR/integrate_grapheneos.sh $DEST"
echo "  bash $SCRIPT_DIR/build_cheetah.sh $DEST"
