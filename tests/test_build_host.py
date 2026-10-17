import tempfile
import unittest
from pathlib import Path
from scripts.check_build_host import inspect
from scripts.validate_config import ROOT

class BuildHostTests(unittest.TestCase):
    def test_report_schema_and_readonly_behavior(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "not-created"
            result = inspect(path, minimum_gib=0, minimum_memory_gib=0)
            self.assertEqual(result["source_dir"], str(path.resolve()))
            self.assertIn("free_storage_gib", result)
            self.assertIn("memory_gib", result)
            self.assertIn("tools", result)
            self.assertFalse(path.exists())

    def test_build_script_exposes_dry_run(self):
        script = (ROOT / "scripts/build_ampybios_dev.sh").read_text()
        self.assertIn('--dry-run', script)
        self.assertIn('check_build_host.py', script)
        self.assertIn('prepare_source.sh', script)
        self.assertIn('adevtool generate-all -d cheetah', script)
        self.assertIn('integrate_grapheneos.sh', script)
        self.assertIn('build_cheetah.sh', script)
        self.assertNotIn("fastboot flash", script)

if __name__ == "__main__":
    unittest.main()
