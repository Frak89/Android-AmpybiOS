"""Host-only integration and artifact gates. Synthetic ZIPs are NOT firmware.

Android SDK tools are test doubles for APK metadata tests. Factory signature
tests use real, temporary OpenSSH keys, never a phone or production key.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from scripts.verify_ampysandbox_prebuilt import verify, digest, ROOT
from scripts.check_ampysandbox_source import snapshot_tree, inspect, MANIFEST
from scripts.audit_combined_release import audit

CERT = "1" * 64


def apk(path, arm=True):
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("AndroidManifest.xml", b"UNIT TEST ONLY - no compiled APK manifest")
        z.writestr("classes.dex", b"UNIT TEST ONLY")
        z.writestr("lib/" + ("arm64-v8a" if arm else "x86_64") + "/test.so", b"UNIT TEST ONLY")


class AppIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / "approved.apk"
        apk(self.apk)
        tools = self.root / "tools"
        tools.mkdir()
        signer = tools / "apksigner"
        signer.write_text('#!/usr/bin/env python3\nimport os,sys\n'
            'if os.environ.get("FAIL_SIGNATURE"):sys.exit(1)\n'
            'print("Signer #1 certificate SHA-256 digest: " + os.environ.get("TEST_CERT", "1"*64))\n')
        signer.chmod(0o755)
        aapt = tools / "aapt"
        aapt.write_text('#!/usr/bin/env python3\nimport os\n'
            'print("package: name=\\\'"+os.environ.get("TEST_PACKAGE","io.ampy.bios")+"\\\' versionCode=\\\'5\\\'")\n'
            'print("targetSdkVersion:\\\'37\\\'")\n'
            'if os.environ.get("TEST_DEBUG"):print("application-debuggable")\n')
        aapt.chmod(0o755)
        self.env = {**os.environ, "PATH": str(tools)+os.pathsep+os.environ["PATH"]}
        self.environment = patch.dict(os.environ, self.env)
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def source(self):
        tree = self.root / "os"
        device = tree / "device/google/pantah/device-cheetah.mk"
        device.parent.mkdir(parents=True)
        device.write_text("# existing device product\n")
        env = tree / "build/envsetup.sh"
        env.parent.mkdir()
        env.write_text("# unit fixture, never a full OS\n")
        return tree, device

    def test_original_apk_is_verified(self):
        result = verify(self.apk, digest(self.apk), CERT)
        self.assertEqual(result["package_name"], "io.ampy.bios")
        self.assertEqual(result["abi"], "arm64-v8a")

    def test_checksum_signer_debug_and_package_fail_closed(self):
        with self.assertRaisesRegex(ValueError, "SHA256 mismatch"):
            verify(self.apk, "0"*64, CERT)
        with patch.dict(os.environ, {"TEST_CERT": "2"*64}):
            with self.assertRaisesRegex(ValueError, "signer mismatch"):
                verify(self.apk, digest(self.apk), CERT)
        with patch.dict(os.environ, {"TEST_DEBUG": "1"}):
            with self.assertRaisesRegex(ValueError, "debuggable"):
                verify(self.apk, digest(self.apk), CERT)
        with patch.dict(os.environ, {"TEST_PACKAGE": "unrelated.package"}):
            with self.assertRaisesRegex(ValueError, "io.ampy.bios"):
                verify(self.apk, digest(self.apk), CERT)

    def test_missing_arm64_or_invalid_signature_rejected(self):
        apk(self.apk, arm=False)
        with self.assertRaisesRegex(ValueError, "ARM64"):
            verify(self.apk, digest(self.apk), CERT)
        with patch.dict(os.environ, {"FAIL_SIGNATURE": "1"}):
            with self.assertRaisesRegex(ValueError, "apksigner verification failed"):
                verify(self.apk, digest(self.apk), CERT)

    def test_staging_is_byte_exact_idempotent_and_optional(self):
        tree, device = self.source()
        command = ["bash", str(ROOT/"scripts/integrate_grapheneos.sh"), str(tree)]
        environment = {**os.environ, "AMPY_INCLUDE_SANDBOX": "1",
            "AMPY_SANDBOX_PREBUILT_APK": str(self.apk),
            "AMPY_SANDBOX_EXPECTED_APK_SHA256": digest(self.apk),
            "AMPY_SANDBOX_EXPECTED_SIGNER_SHA256": CERT}
        before = device.read_bytes()
        subprocess.run(command+["--dry-run"], env=environment, check=True, capture_output=True)
        self.assertEqual(device.read_bytes(), before)
        self.assertFalse((tree/"vendor/ampybios").exists())
        for _ in range(2):
            subprocess.run(command, env=environment, check=True, capture_output=True)
        target = tree/"vendor/ampybios"
        self.assertEqual((target/"apps/AmpySandbox/AmpybiOS.apk").read_bytes(), self.apk.read_bytes())
        self.assertEqual((target/"product.mk").read_text().count("PRODUCT_PACKAGES += AmpySandbox"), 1)
        self.assertEqual(device.read_text().count("BEGIN AMPYBIOS DEVICE INTEGRATION"), 1)
        report = json.loads((target/"config/AmpySandbox-verification.json").read_text())
        self.assertEqual(report["apk_sha256"], digest(self.apk))
        environment["AMPY_INCLUDE_SANDBOX"] = "0"
        subprocess.run(command, env=environment, check=True, capture_output=True)
        self.assertFalse((target/"apps/AmpySandbox").exists())
        self.assertNotIn("PRODUCT_PACKAGES += AmpySandbox", (target/"product.mk").read_text())

    def test_rejected_apk_does_not_mutate_device_checkout(self):
        tree, device = self.source()
        before = device.read_bytes()
        env = {**os.environ, "AMPY_INCLUDE_SANDBOX": "1",
            "AMPY_SANDBOX_PREBUILT_APK": str(self.apk),
            "AMPY_SANDBOX_EXPECTED_APK_SHA256": "0"*64,
            "AMPY_SANDBOX_EXPECTED_SIGNER_SHA256": CERT}
        result = subprocess.run(["bash", str(ROOT/"scripts/integrate_grapheneos.sh"), str(tree)],
                                env=env, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(device.read_bytes(), before)
        self.assertFalse((tree/"vendor/ampybios").exists())

    def test_plan_needs_no_phone_or_signing_keys(self):
        result = subprocess.run(["bash", str(ROOT/"scripts/build_combined_cheetah.sh"),
            str(self.root/"not-created"), "--plan"], check=True, capture_output=True, text=True)
        self.assertIn("AmpySandbox", result.stdout)
        self.assertIn("not an installable ROM", result.stdout)
        self.assertFalse((self.root/"not-created").exists())


class SnapshotTests(unittest.TestCase):
    def test_tree_matches_real_git_and_detects_edits(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp)
            (source/"a").mkdir()
            (source/"a/file.py").write_bytes(b"print('source')\n")
            (source/"a.c").write_bytes(b"int x;\n")
            subprocess.run(["git", "init", str(source)], check=True, capture_output=True)
            subprocess.run(["git", "-C", str(source), "add", "."], check=True)
            actual = subprocess.run(["git", "-C", str(source), "write-tree"], check=True,
                                    capture_output=True, text=True).stdout.strip()
            entries = []
            for name in ("a/file.py", "a.c"):
                data = (source/name).read_bytes()
                sha = hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
                entries.append({"path": name, "mode": "100644", "sha": sha})
            shutil.rmtree(source/".git")
            config = {"repository": "owner/private", "source_sha": "1"*40, "source_tree_sha": actual}
            (source/MANIFEST).write_text(json.dumps({"repository": config["repository"],
                "commit": config["source_sha"], "files": entries}))
            self.assertEqual(snapshot_tree(source, entries), actual)
            self.assertEqual(inspect(source, config), "snapshot")
            (source/"a.c").write_bytes(b"modified\n")
            with self.assertRaisesRegex(ValueError, "modified"):
                inspect(source, config)


@unittest.skipUnless(shutil.which("ssh-keygen"), "OpenSSH needed for real factory signature test")
class ReleaseAuditTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.number = "2026102700"
        self.key = self.root/"ephemeral-unit-test-key"
        subprocess.run(["ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-f", str(self.key)],
                       check=True, capture_output=True)
        self.factory = self.root/f"cheetah-install-{self.number}.zip"
        with zipfile.ZipFile(self.factory, "w") as z:
            z.writestr("cheetah/flash-all.sh", "# UNIT TEST: NOT A DEVICE SCRIPT\n")
            z.writestr("cheetah/android-info.txt", "require board=cheetah\n")
            for name in ("boot.img", "init_boot.img", "vendor_boot.img", "vendor_kernel_boot.img", "vbmeta.img", "dtbo.img"):
                z.writestr("cheetah/"+name, b"UNIT TEST - NOT BOOTABLE")
        subprocess.run(["ssh-keygen", "-Y", "sign", "-f", str(self.key), "-n", "factory images", str(self.factory)],
                       check=True, capture_output=True)
        self.payload = b"CrAU" + b"UNIT TEST PAYLOAD ONLY"
        self.ota()
        original_apk = b"UNIT TEST APK ONLY"
        self.targets = self.root/"cheetah-target_files.zip"
        with zipfile.ZipFile(self.targets, "w") as z:
            z.writestr("PRODUCT/app/AmpySandbox/AmpySandbox.apk", original_apk)
        cfg = json.loads((ROOT/"config/ampysandbox-upstream.json").read_text())
        self.lock = self.root/"lock.json"
        self.lock.write_text(json.dumps({"package_name": cfg["package_name"],
            "configured_source_sha": cfg["source_sha"], "signature_verified": True,
            "debuggable": False, "apk_sha256": hashlib.sha256(original_apk).hexdigest()}))

    def ota(self, device="cheetah", valid_hash=True):
        with zipfile.ZipFile(self.root/f"cheetah-ota_update-{self.number}.zip", "w") as z:
            z.writestr("META-INF/com/android/metadata", f"pre-device={device}\nota-type=AB\npost-build-incremental={self.number}\n")
            z.writestr("payload.bin", self.payload)
            checksum = base64.b64encode(hashlib.sha256(self.payload).digest()).decode() if valid_hash else "broken"
            z.writestr("payload_properties.txt", f"FILE_SIZE={len(self.payload)}\nFILE_HASH={checksum}\n")

    def run_audit(self):
        return audit(self.root, self.number, Path(str(self.key)+".pub"), self.lock)

    def test_authentic_factory_and_exact_apk_are_checked_without_claiming_boot(self):
        report = self.run_audit()
        self.assertTrue(report["factory_signature_verified"])
        self.assertTrue(report["approved_sandbox_apk_in_signed_target_files"])
        self.assertFalse(report["hardware_boot_validated"])

    def test_tampered_factory_signature_rejected(self):
        with self.factory.open("ab") as stream:
            stream.write(b"altered")
        with self.assertRaisesRegex(ValueError, "SSH signature"):
            self.run_audit()

    def test_wrong_ota_device_or_hash_rejected(self):
        self.ota(device="panther")
        with self.assertRaisesRegex(ValueError, "OTA device"):
            self.run_audit()
        self.ota(valid_hash=False)
        with self.assertRaisesRegex(ValueError, "payload"):
            self.run_audit()

    def test_resigned_or_changed_app_rejected(self):
        with zipfile.ZipFile(self.targets, "w") as z:
            z.writestr("PRODUCT/app/AmpySandbox/AmpySandbox.apk", b"changed APK")
        with self.assertRaisesRegex(ValueError, "changed or was re-signed"):
            self.run_audit()
