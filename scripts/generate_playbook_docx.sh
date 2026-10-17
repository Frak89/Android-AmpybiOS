#!/usr/bin/env bash
# Genera playbook.docx da docs/PLAYBOOK.md usando pandoc
set -euo pipefail

if ! command -v pandoc >/dev/null 2>&1; then
  echo "Install pandoc to use this script. On Debian/Ubuntu: sudo apt install pandoc" >&2
  exit 2
fi

MD=docs/PLAYBOOK.md
OUT=docs/playbook.docx
pandoc "$MD" -o "$OUT" --metadata title="AmpybiOS Playbook"

echo "Generated $OUT"
