#!/usr/bin/env bash
# Compose both repositories and build target-files; never signs or flashes.
set -euo pipefail
[[ $# -ge 1 && $# -le 2 ]] || { echo "Usage: $0 /absolute/grapheneos-tree [--plan]" >&2; exit 2; }
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TREE="$(realpath -m "$1")"
if [[ ${2:-} == --plan ]]; then
  cat <<EOF
AmpybiOS combined build plan — cheetah / Pixel 7 Pro
1. Check Linux build host (32 GiB RAM, 220 GiB free, upstream tools).
2. Verify an approved signed io.ampy.bios ARM64 release APK.
3. Synchronize and verify the configured GrapheneOS stable tag.
4. Generate cheetah vendor files with adevtool.
5. Integrate AmpyCore, AmpyCam, AmpyConnect, AmpyLauncher, AmpyRec and AmpySandbox.
6. Build vendorbootimage, vendorkernelbootimage, target-files-package and otatools-package.
7. Run sign_combined_release.sh separately with your existing persistent keys.
Destination: $TREE
No command in this plan flashes a phone. Target-files are not an installable ROM.
Private app source is fetched separately; its signed APK is staged without re-signing.
EOF
  exit 0
elif [[ $# -eq 2 ]]; then echo "Unknown option: $2" >&2; exit 2; fi
: "${AMPY_BUILD_NUMBER:?Set a unique numeric build number, for example YYYYMMDD00}"
[[ "$AMPY_BUILD_NUMBER" =~ ^[0-9]{8,20}$ ]] || { echo "Invalid build number" >&2; exit 2; }
: "${AMPY_SANDBOX_PREBUILT_APK:?Set the release APK built from Frak89/AmpybiOS}"
: "${AMPY_SANDBOX_EXPECTED_APK_SHA256:?Set its approved APK SHA256}"
: "${AMPY_SANDBOX_EXPECTED_SIGNER_SHA256:?Set its approved signer certificate SHA256}"
python3 "$ROOT/scripts/check_build_host.py" "$TREE"
python3 "$ROOT/scripts/verify_ampysandbox_prebuilt.py" --apk "$AMPY_SANDBOX_PREBUILT_APK" \
  --sha256 "$AMPY_SANDBOX_EXPECTED_APK_SHA256" --signer-sha256 "$AMPY_SANDBOX_EXPECTED_SIGNER_SHA256"
export BUILD_NUMBER="$AMPY_BUILD_NUMBER"
export OUT_DIR="$TREE/out"
export AMPY_INCLUDE_SANDBOX=1 AMPY_INCLUDE_CAM=1 AMPY_INCLUDE_CONNECT=1 AMPY_INCLUDE_LAUNCHER=1 AMPY_INCLUDE_REC=1
# The separate third-repository AmpyRAW editor is not silently redistributed.
export AMPY_INCLUDE_RAW=0
export AMPY_BUILD_OTATOOLS=1
bash "$ROOT/scripts/build_ampybios_dev.sh" "$TREE"
echo "Combined UNSIGNED target-files completed. Next: sign_combined_release.sh '$TREE' '$BUILD_NUMBER'."
