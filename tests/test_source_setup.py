"""Guard the build setup against silent upstream-only builds or missing overlay helpers."""
from pathlib import Path
import unittest
from scripts.validate_config import ROOT

class SourceSetupTests(unittest.TestCase):
    def test_source_sync_does_not_invoke_missing_overlay_copy(self):
        code = (ROOT / "scripts/prepare_source.sh").read_text(encoding="utf-8")
        self.assertNotIn("copy_overlays.py", code)
        self.assertIn("integrate_grapheneos.sh", code)
        self.assertIn('PINNED_TAG=', code)
        self.assertIn("repo sync -j8", code)

    def test_ampybios_build_fails_closed(self):
        code = (ROOT / "scripts/build_cheetah.sh").read_text(encoding="utf-8")
        self.assertIn(".managed-by-ampybios", code)
        self.assertIn("unset OFFICIAL_BUILD", code)
        self.assertIn("target-files-package", code)

if __name__ == "__main__":
    unittest.main()
