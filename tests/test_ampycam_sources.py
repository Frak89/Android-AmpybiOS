from pathlib import Path
import unittest
from scripts.validate_config import ROOT

class AmpyCamSourceTests(unittest.TestCase):
    def test_raw_capture_uses_scoped_android_interfaces(self):
        base = ROOT / "apps/AmpyCam/app/src/main"
        manifest = (base / "AndroidManifest.xml").read_text()
        camera = (base / "java/dev/ampybios/cam/CameraActivity.java").read_text()
        self.assertIn("android.permission.CAMERA", manifest)
        self.assertNotIn("android.permission.INTERNET", manifest)
        for token in ("REQUEST_AVAILABLE_CAPABILITIES_RAW", "ImageFormat.RAW_SENSOR",
                      "new DngCreator", "MediaStore.Images.Media.IS_PENDING",
                      "onPause()", "clearPending(", "CameraActivity.this::closeCamera"):
            self.assertIn(token, camera)

    def test_gradle_reuses_themes_without_third_party_google_services(self):
        p = ROOT / "apps/AmpyCam/app/build.gradle.kts"
        build = p.read_text()
        self.assertIn("compileSdk = 37", build)
        self.assertIn("targetSdk = 37", build)
        self.assertIn('../../../config/themes', build)
        self.assertTrue((ROOT / "apps/AmpyCam/app/src/main/java/dev/ampybios/cam/CamTheme.java").is_file())

    def test_doc_and_automated_debug_build(self):
        self.assertTrue((ROOT / "scripts/build_ampycam_local.sh").is_file())
        self.assertTrue((ROOT / ".github/workflows/ampycam-apk.yml").is_file())

if __name__ == "__main__":
    unittest.main()
