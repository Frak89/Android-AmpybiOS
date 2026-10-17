import unittest
from pathlib import Path
from scripts.validate_config import ROOT

APPS = ("AmpyCore", "AmpyCam", "AmpyRaw", "AmpyConnect", "AmpyLauncher")


class AppModuleTests(unittest.TestCase):
    def test_each_app_has_own_project_and_aosp_optin_module(self):
        for app in APPS:
            with self.subTest(app=app):
                folder = ROOT / "apps" / app
                self.assertTrue((folder / "settings.gradle.kts").is_file())
                self.assertTrue((folder / "build.gradle.kts").is_file())
                self.assertTrue((folder / "app/build.gradle.kts").is_file())
                self.assertTrue((folder / "app/src/main/AndroidManifest.xml").is_file())
                self.assertTrue((ROOT / "platform" / app / "Android.bp").is_file())
                self.assertTrue((ROOT / ".github/workflows" / (app.lower() + "-apk.yml")).is_file())

    def test_optional_modules_disabled_by_default(self):
        script = (ROOT / "scripts/integrate_grapheneos.sh").read_text()
        for name in ("CAM", "RAW", "CONNECT", "LAUNCHER"):
            self.assertIn('AMPY_INCLUDE_' + name + '="${AMPY_INCLUDE_' + name + ':-0}"', script)
        product = (ROOT / "platform/ampybios/product.mk").read_text()
        self.assertIn("PRODUCT_PACKAGES += AmpyCore", product)
        for app in ("AmpyCam", "AmpyRaw", "AmpyConnect", "AmpyLauncher"):
            self.assertNotIn("PRODUCT_PACKAGES += " + app, product)


if __name__ == "__main__":
    unittest.main()
