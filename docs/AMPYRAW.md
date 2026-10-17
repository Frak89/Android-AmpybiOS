# AmpyRAW Cyberdark — canonical raw editor for AmpybiOS

**Use the existing application** [Frak89/AmpyRAW-Cyberdark](https://github.com/Frak89/AmpyRAW-Cyberdark),
NOT a second `dev.ampybios.raw` editor. The duplicate prototype was removed.
The original Android package ID **must remain `it.ampy.ampyraw`** to preserve
installed user data and compatibility with existing APK updates.

Canonical reviewed source revision is pinned in `config/ampyraw-upstream.json`.
At this revision the app calls itself **AmpyRAW Cyberdark v0.9.0**
(versionCode 13), includes Android Java UI and C++/LibRaw JNI integration,
Canon CR3 support paths, XMP presets, composition grids, undo and Canon FTP.
**Do not mistake the presence of native decoder code for verified CR3
compatibility on the Pixel; it still requires compiling, field testing and
real EOS R6 Mark II images.**

## Open the actual editor in Android Studio

The original editor uses **Gradle 8.13 / AGP 8.13.2 / SDK 36 / NDK
27.2.12479018 / CMake 3.22.1**, not AmpyCore's Gradle 9.4.1 project.

```bash
# From Android-AmpybiOS (requires authenticated access to the private repo):
bash scripts/prepare_ampyraw_source.sh "$HOME/src/AmpyRAW-Cyberdark"

# The original repo's debug workflow fetches LibRaw 0.22.2:
git clone --depth 1 --branch 0.22.2 \
  https://github.com/LibRaw/LibRaw.git "$HOME/src/AmpyRAW-Cyberdark/vendor/LibRaw"

# Use original Android Studio project at:
# $HOME/src/AmpyRAW-Cyberdark (NOT apps/AmpyRaw)
# Or run with Gradle 8.13 available on PATH:
bash scripts/build_ampyraw_local.sh "$HOME/src/AmpyRAW-Cyberdark"
```

A debug APK signed by another machine's Android debug key cannot be installed
over the existing AmpyRAW APK while retaining its data. **Never uninstall the
existing app without a verified backup**. Only an APK with a matching stable
signer can upgrade the existing installation; do not commit signing keys.

## ROM integration with signature and checksum verification

Build and sign the original app separately. AmpybiOS accepts **only** an
explicitly approved pre-signed APK; it does not repackage the code under a new
package ID or silently re-sign it with a ROM/platform certificate.

On a Linux build workstation with Android SDK Build Tools (apksigner, aapt)
and a prepared GrapheneOS checkout:

```bash
export AMPY_INCLUDE_RAW=1
export AMPYRAW_PREBUILT_APK="/absolute/path/to/AmpyRAW-Cyberdark.apk"
export AMPYRAW_EXPECTED_APK_SHA256="$(sha256sum "$AMPYRAW_PREBUILT_APK" | cut -d' ' -f1)"
export AMPYRAW_EXPECTED_SIGNER_SHA256="REPLACE_WITH_VERIFIED_INSTALLED_CERT_SHA256"

# View the candidate cert; DO NOT use any unverified fingerprint.
apksigner verify --verbose --print-certs "$AMPYRAW_PREBUILT_APK"

# All checks, including package name/signature/integrity, must pass first.
bash scripts/verify_ampyraw_prebuilt.sh
bash scripts/integrate_grapheneos.sh "$HOME/src/grapheneos-2026100600" --dry-run
bash scripts/integrate_grapheneos.sh "$HOME/src/grapheneos-2026100600"
```

The local manifest integration creates `vendor/ampybios/apps/AmpyRAW/`
containing the approved APK and an `android_app_import` module, with
`presigned: true`. It opts into product package `AmpyRAW`. No root or
system UID permissions are granted. Disabled by default.

**Security limitation:** matching an APK's SHA-256 to a value you just
calculated proves consistent bytes, *not* trust by itself. The trusted
certificate fingerprint must come from a known-good installed/released APK,
not merely from the unverified download. The original private repository
does not currently expose a published signed GitHub Release asset through
its Releases API; obtaining/providing a trusted APK remains the owner's step.

## AmpySkin integration

The original AmpyRAW now includes an optional receiver guarded by AmpyCore's
signature-level permission. AmpyRAW Settings → Look & Feel → Segui tema
AmpybiOS is **off by default**; local manual theme selection disables it.
Mapping: Fusion/Cyberpunk → Cyberpunk, Futuristic → Futuristic, Minimal →
Modern. It does not change RAW processing, imported photos or export colors.

**Signature requirement:** the original AmpyRAW OTA signing certificate may
not match AmpyCore's Android signature. In that case synchronization does NOT
work; the editor still works independently. Do **not** weaken the permission
or change the original signing identity to force theme sync.
