"""Structural checks, NOT an Android compile or image-processing test."""
from scripts.validate_config import ROOT
import unittest

class AmpyRawStructure(unittest.TestCase):
    def test_readonly_photo_access_and_export(self):
        root = ROOT / "apps/AmpyRaw/app/src/main"
        manifest = (root / "AndroidManifest.xml").read_text()
        java = (root / "java/dev/ampybios/raw/RawActivity.java").read_text()
        self.assertNotIn("android.permission.INTERNET", manifest)
        self.assertNotIn("READ_EXTERNAL_STORAGE", manifest)
        for token in ("ACTION_OPEN_DOCUMENT", "ACTION_CREATE_DOCUMENT",
                      "ColorMatrixColorFilter", "inSampleSize", "BitmapFactory",
                      "decoder CR3/DNG via LibRaw non è ancora integrato"):
            self.assertIn(token, java)
        self.assertIn('android:permission="dev.ampybios.permission.SKIN_SYNC"', manifest)
    def test_gradle_shared_theme(self):
        s = (ROOT / "apps/AmpyRaw/app/build.gradle.kts").read_text()
        self.assertIn("compileSdk = 37", s)
        self.assertIn("../../../config/themes", s)

if __name__ == "__main__":
    unittest.main()
