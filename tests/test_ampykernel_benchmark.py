import tempfile
import unittest
from pathlib import Path
from scripts.ampykernel_benchmark_compare import compare

class AmpyKernelBenchmarkTests(unittest.TestCase):
    def test_parses_realistic_gfxinfo_without_invented_thermal_claims(self):
        with tempfile.TemporaryDirectory() as root:
            base = Path(root) / "stock"
            opt = Path(root) / "eco"
            base.mkdir()
            opt.mkdir()
            (base / "gfxinfo.txt").write_text("Total frames rendered: 250\nJanky frames: 50 (20.00%)\n")
            (opt / "gfxinfo.txt").write_text("Total frames rendered: 250\nJanky frames: 40 (16.00%)\n")
            (base / "battery.txt").write_text("level: 82\ntemperature: 310\n")
            (opt / "battery.txt").write_text("level: 80\ntemperature: 305\n")
            summary = compare(base, opt)
            self.assertEqual(summary["baseline"]["janky_percent"], 20.0)
            self.assertEqual(summary["candidate"]["janky_percent"], 16.0)
            self.assertEqual(summary["janky_percentage_points_delta"], -4.0)
            self.assertEqual(summary["baseline"]["battery_temperature_c"], 31.0)
            self.assertIn("cannot prove", summary["explanation"])

    def test_unavailable_counters_remain_null(self):
        with tempfile.TemporaryDirectory() as root:
            a = Path(root)/"a"; b = Path(root)/"b"; a.mkdir(); b.mkdir()
            for folder in (a,b):
                (folder/"gfxinfo.txt").write_text("Not found")
                (folder/"battery.txt").write_text("level: 50")
            result = compare(a,b)
            self.assertIsNone(result["janky_percentage_points_delta"])
            self.assertIsNone(result["baseline"]["battery_temperature_c"])

if __name__ == "__main__":
    unittest.main()
