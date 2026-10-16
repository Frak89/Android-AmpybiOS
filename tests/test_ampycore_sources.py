from pathlib import Path
import unittest
from scripts.validate_config import ROOT

class AmpyCoreSourceTests(unittest.TestCase):
    def test_prototype_and_shared_theme_source(self):
        app = ROOT / "apps/AmpyCore/app"
        self.assertTrue((app / "src/main/AndroidManifest.xml").exists())
        self.assertTrue((app / "src/main/java/dev/ampybios/core/MainActivity.java").exists())
        gradle = (app / "build.gradle.kts").read_text()
        self.assertIn("compileSdk = 37", gradle)
        self.assertIn('../../../config/themes', gradle)
        radar = (app / "src/main/java/dev/ampybios/core/RadarPreview.java").read_text()
        self.assertIn("new SweepGradient", radar)
        self.assertIn("addRoundRect", radar)

if __name__ == "__main__":
    unittest.main()
