"""Host-only tests for SVG morph frames; CairoSVG is not required."""
from pathlib import Path
import importlib.util
import tempfile
import unittest
from xml.etree import ElementTree as ET

from scripts.validate_config import ROOT

SOURCE = (ROOT / "overlays/cheetah/vendor/ampybi/"
          "boot_logo_package/generate_boot_frames.py")
spec = importlib.util.spec_from_file_location("ampy_bootlogo", SOURCE)
generator = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(generator)


class BootLogoTests(unittest.TestCase):
    def test_frames_change_and_are_valid_xml(self):
        first = generator.make_svg(0, 20, 640, 640, "AmpybiOS")
        other = generator.make_svg(10, 20, 640, 640, "AmpybiOS")
        self.assertNotEqual(first, other)
        self.assertTrue(ET.fromstring(first).tag.endswith("svg"))
        self.assertIn('id="titleMask"', first)
        self.assertIn("linearGradient", first)

    def test_escape_user_title(self):
        svg = generator.make_svg(0, 1, 640, 640, "<Ampy&>")
        ET.fromstring(svg)
        self.assertIn("&lt;Ampy&amp;&gt;", svg)

    def test_svg_only_and_stale_frame_guard(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / "frames"
            result = generator.generate(dest, 3, 640, 640, "AmpybiOS",
                                        svg_only=True)
            self.assertEqual([p.name for p in result], ["0.svg", "1.svg", "2.svg"])
            with self.assertRaisesRegex(ValueError, "empty"):
                generator.generate(dest, 3, 640, 640, "AmpybiOS",
                                   svg_only=True)

    def test_invalid_count_and_size(self):
        with self.assertRaises(ValueError):
            generator.make_svg(3, 3, 640, 640, "AmpybiOS")
        with self.assertRaises(ValueError):
            generator.make_svg(0, 3, 0, 640, "AmpybiOS")


if __name__ == "__main__":
    unittest.main()
