#!/usr/bin/env bash
# Use upstream GrapheneOS signing/packaging with the owner's existing keys.
# No key generation, bootloader changes, fastboot invocation against a device,
# automatic OTA upload or installed-device changes are performed here.
set -euo pipefail
[[ $# -eq 2 ]] || { echo "Usage: $0 /grapheneos-tree NUMERIC_BUILD_NUMBER" >&2; exit 2; }
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TREE="$(realpath "$1")"
NUMBER="$2"
[[ "$NUMBER" =~ ^[0-9]{8,20}$ ]] || { echo "Invalid build number" >&2; exit 2; }
[[ -f "$TREE/vendor/ampybios/config/AmpySandbox-verification.json" ]] || { echo "Combined AmpySandbox integration is missing" >&2; exit 3; }
[[ -s "$TREE/out/build_number.txt" && "$(cat "$TREE/out/build_number.txt")" == "$NUMBER" ]] || { echo "Build number does not match the completed OS build" >&2; exit 3; }
[[ ! -e "$TREE/releases/$NUMBER" ]] || { echo "Refusing to overwrite an existing release; use a new build number" >&2; exit 3; }
for script in script/finalize.sh script/generate-release.sh build/envsetup.sh; do
  [[ -f "$TREE/$script" ]] || { echo "Missing upstream $script" >&2; exit 3; }
done
for key in releasekey platform shared media networkstack bluetooth sdk_sandbox gmscompat_lib nfc; do
  for suffix in pk8 x509.pem; do
    [[ -s "$TREE/keys/cheetah/$key.$suffix" ]] || { echo "Missing persistent cheetah signing key: $key.$suffix; follow upstream key setup" >&2; exit 3; }
  done
done
for key in avb.pem avb_pkmd.bin id_ed25519 id_ed25519.pub; do
  [[ -s "$TREE/keys/cheetah/$key" ]] || { echo "Missing cheetah signing material: $key" >&2; exit 3; }
done
command -v ssh-keygen >/dev/null || { echo "OpenSSH is required" >&2; exit 3; }
export BUILD_NUMBER="$NUMBER" OUT_DIR="$TREE/out"
unset OFFICIAL_BUILD || true
cd "$TREE"
set +u
source build/envsetup.sh
lunch cheetah-cur-user
set -u
script/finalize.sh
# Upstream decrypts keys in tmpfs and prompts locally for their passphrases.
script/generate-release.sh cheetah "$NUMBER"
python3 "$ROOT/scripts/audit_combined_release.py" \
  --release-dir "$TREE/releases/$NUMBER/release-cheetah-$NUMBER" --build-number "$NUMBER" \
  --factory-public-key "$TREE/keys/cheetah/id_ed25519.pub" \
  --sandbox-lock "$TREE/vendor/ampybios/config/AmpySandbox-verification.json"
echo "Factory/OTA artifacts generated and host-audited. Hardware boot validation is still required."
