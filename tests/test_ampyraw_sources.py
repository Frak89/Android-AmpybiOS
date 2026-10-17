"""Original AmpyRAW source provenance checks (no local shadow editor)."""
from pathlib import Path
import json
import unittest
from scripts.validate_config import ROOT

class AmpyRawIntegrationTests(unittest.TestCase):
    def test_pinned_canonical_repository(self):
        config = json.loads((ROOT / "config/ampyraw-upstream.json").read_text())
        self.assertEqual(config["repository"], "Frak89/AmpyRAW-Cyberdark")
        self.assertEqual(config["package_name"], "it.ampy.ampyraw")
        self.assertRegex(config["source_sha"], r"^[0-9a-f]{40}$")
        self.assertEqual(config["build"]["native_decoder"], "LibRaw 0.22.2")
        self.assertFalse((ROOT / "apps/AmpyRaw").exists())

    def test_prebuilt_integrity_and_no_platform_resigning(self):
        validate = (ROOT / "scripts/verify_ampyraw_prebuilt.sh").read_text()
        for token in ("apksigner verify", "aapt dump badging", "sha256sum",
                      "AMPYRAW_EXPECTED_SIGNER_SHA256", "it.ampy.ampyraw"):
            self.assertIn(token, validate)
        soong = (ROOT / "platform/AmpyRAW/Android.bp").read_text()
        self.assertIn("presigned: true", soong)
        self.assertNotIn('certificate: "platform"', soong)

if __name__ == "__main__":
    unittest.main()
