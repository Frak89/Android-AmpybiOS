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
                      "onPause()", "clearPending("):
            self.assertIn(token, camera)

    def test_module_gradle(self):
        p = ROOT / "apps/AmpyCam/app/build.gradle.kts"
        build = p.read_text()
        self.assertIn("compileSdk = 37", build)
        self.assertIn("targetSdk = 37", build)

if __name__ == "__main__":
    unittest.main()
