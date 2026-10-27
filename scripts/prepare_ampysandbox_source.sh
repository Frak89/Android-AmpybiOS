#!/usr/bin/env bash
# Fetch the private app separately; uses the owner's existing Git authentication.
set -euo pipefail
[[ $# -eq 1 ]] || { echo "Usage: $0 /absolute/path/outside-this-repo/AmpybiOS" >&2; exit 2; }
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$(realpath -m "$1")"
case "$DEST/" in "$ROOT/"*) echo "Private source must remain outside the public integration repository" >&2; exit 2;; esac
mapfile -t config < <(python3 - "$ROOT/config/ampysandbox-upstream.json" <<'PY'
import json,sys
c=json.load(open(sys.argv[1]));print(c['git_url']);print(c['source_sha'])
PY
)
[[ "${config[1]}" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid source pin" >&2; exit 2; }
if [[ -e "$DEST" ]]; then
  [[ -d "$DEST/.git" ]] || { echo "Refusing existing non-Git destination" >&2; exit 3; }
  [[ "$(git -C "$DEST" remote get-url origin)" == "${config[0]}" ]] || { echo "Unexpected app origin" >&2; exit 3; }
  [[ -z "$(git -C "$DEST" status --porcelain)" ]] || { echo "Refusing to overwrite local app edits" >&2; exit 3; }
else
  git clone --no-checkout "${config[0]}" "$DEST"
fi
git -C "$DEST" fetch --depth=1 origin "${config[1]}"
git -C "$DEST" checkout --detach "${config[1]}"
[[ "$(git -C "$DEST" rev-parse HEAD)" == "${config[1]}" ]] || exit 4
echo "Pinned private AmpybiOS source ready; nothing published or installed."
