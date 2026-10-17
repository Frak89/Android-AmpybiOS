#!/usr/bin/env bash
# Fetch the REAL AmpyRAW editor from the owner's private repository.
# Never embeds access tokens or installs APKs.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_SHA="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["source_sha"])' "$ROOT/config/ampyraw-upstream.json")"
DEST="${1:-$HOME/src/AmpyRAW-Cyberdark}"
command -v git >/dev/null || { echo "Missing git" >&2; exit 2; }
if [[ -e "$DEST" ]]; then
  echo "Destination exists; refusing to overwrite: $DEST" >&2
  exit 3
fi
git clone https://github.com/Frak89/AmpyRAW-Cyberdark.git "$DEST"
git -C "$DEST" checkout --detach "$SOURCE_SHA"
ACTUAL="$(git -C "$DEST" rev-parse HEAD)"
[[ "$ACTUAL" == "$SOURCE_SHA" ]] || {
  echo "AmpyRAW source revision verification failed" >&2; exit 3;
}
echo "Original AmpyRAW source pinned: $ACTUAL"
echo "Open $DEST in Android Studio (AGP 8.13.2 / Gradle 8.13 / SDK 36 / ARM64 NDK)."
echo "For LibRaw sources check the original Android-debug-APK GitHub workflow."
echo "Never uninstall/re-sign an installed AmpyRAW without checking OTA signer."
