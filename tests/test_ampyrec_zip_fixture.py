import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from scripts.make_ampyrec_demo import build


class AmpyRecFixtureTests(unittest.TestCase):
    def test_demo_manifest_hash_matches_original_theme(self):
        with tempfile.TemporaryDirectory() as folder:
            dest = Path(folder) / "demo.zip"
            build(dest, ("fusion", "minimal"))
            with zipfile.ZipFile(dest) as z:
                manifest = json.loads(z.read("ampyrec.json"))
                self.assertEqual(manifest["schema"], 1)
                self.assertEqual(manifest["type"], "live-theme")
                self.assertEqual(len(z.namelist()), 3)
                for entry in manifest["files"]:
                    self.assertEqual(hashlib.sha256(z.read(entry["path"])).hexdigest(),
                                     entry["sha256"])
                    self.assertFalse(entry["path"].startswith("/"))
                    self.assertNotIn("..", entry["path"])

    def test_unknown_name_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(ValueError):
                build(Path(folder) / "bad.zip", ("../../boot.img",))

if __name__ == "__main__":
    unittest.main()
