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
        self.assertIn("setCornerRadii", radar)

    def test_organic_corner_editor(self):
        java = (ROOT / "apps/AmpyCore/app/src/main/java/dev/ampybios/core/MainActivity.java").read_text()
        self.assertIn("Organic Corner Designer", java)
        self.assertIn("new SeekBar[4]", java)
        self.assertIn("preview.setCornerRadii(customRadii)", java)
        self.assertIn('".corner." + i', java)

    def test_local_build_instructions(self):
        self.assertTrue((ROOT / "scripts/build_ampycore_local.sh").exists())
        self.assertTrue((ROOT / "docs/BUILD_APK.md").exists())

if __name__ == "__main__":
    unittest.main()
