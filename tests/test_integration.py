"""Host-only checks for applying AmpyCore to a *mock*, never a real device."""
from pathlib import Path
import shutil
import os
import subprocess
import tempfile
import unittest

from scripts.validate_config import ROOT


class GrapheneIntegrationTests(unittest.TestCase):
    def test_dry_run_install_and_idempotence(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "grapheneos"
            device = source / "device/google/pantah/device-cheetah.mk"
            device.parent.mkdir(parents=True)
            device.write_text("PRODUCT_PACKAGES += Base\n", encoding="utf-8")
            env = source / "build/envsetup.sh"
            env.parent.mkdir(parents=True)
            env.write_text("# mock\n", encoding="utf-8")
            command = ["bash", str(ROOT / "scripts/integrate_grapheneos.sh"), str(source)]
            before = device.read_text(encoding="utf-8")
            subprocess.run(command + ["--dry-run"], check=True, capture_output=True)
            self.assertEqual(device.read_text(encoding="utf-8"), before)
            for _ in range(2):
                subprocess.run(command, check=True, capture_output=True)
            contents = device.read_text(encoding="utf-8")
            self.assertEqual(contents.count("# BEGIN AMPYBIOS DEVICE INTEGRATION"), 1)
            app = source / "vendor/ampybios/apps/AmpyCore"
            self.assertTrue((app / "Android.bp").is_file())
            self.assertTrue((app / "assets/fusion.json").is_file())
            self.assertTrue((app / "src/dev/ampybios/core/RadarPreview.java").is_file())
            self.assertIn("PRODUCT_PACKAGES += AmpyCore",
                          (source / "vendor/ampybios/product.mk").read_text())

    def test_optional_camera_and_editor_are_explicit_and_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "grapheneos"
            device = source / "device/google/pantah/device-cheetah.mk"
            device.parent.mkdir(parents=True)
            device.write_text("PRODUCT_PACKAGES += Base\n", encoding="utf-8")
            envsetup = source / "build/envsetup.sh"
            envsetup.parent.mkdir(parents=True)
            envsetup.write_text("# mock\n")
            cmd = ["bash", str(ROOT / "scripts/integrate_grapheneos.sh"), str(source)]
            flags = {**os.environ, "AMPY_INCLUDE_CAM": "1", "AMPY_INCLUDE_RAW": "1"}
            subprocess.run(cmd + ["--dry-run"], check=True, capture_output=True, env=flags)
            for _ in range(2):
                subprocess.run(cmd, check=True, capture_output=True, env=flags)
            product = (source / "vendor/ampybios/product.mk").read_text()
            self.assertEqual(product.count("PRODUCT_PACKAGES += AmpyCam"), 1)
            self.assertEqual(product.count("PRODUCT_PACKAGES += AmpyRaw"), 1)
            for name in ("AmpyCam", "AmpyRaw"):
                app = source / "vendor/ampybios/apps" / name
                self.assertTrue((app / "Android.bp").is_file())
                self.assertTrue((app / "AndroidManifest.xml").is_file())
                self.assertTrue((app / "assets/fusion.json").is_file())

    def test_rejects_unmanaged_vendor_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "grapheneos"
            device = source / "device/google/pantah/device-cheetah.mk"
            device.parent.mkdir(parents=True)
            device.write_text("# mock\n")
            env = source / "build/envsetup.sh"
            env.parent.mkdir(parents=True)
            env.write_text("# mock\n")
            existing = source / "vendor/ampybios"
            existing.mkdir(parents=True)
            (existing / "someone-elses-file").write_text("preserve me")
            result = subprocess.run(
                ["bash", str(ROOT / "scripts/integrate_grapheneos.sh"), str(source)],
                capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertTrue((existing / "someone-elses-file").exists())


if __name__ == "__main__":
    unittest.main()
