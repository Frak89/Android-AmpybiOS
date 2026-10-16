"""Kernel mirror must refuse incompatible GrapheneOS manifests."""
import json
import tempfile
import unittest
from pathlib import Path

from scripts.configure_kernel_mirror import CONFIG_PATH, BUILD_PATH, generate_manifest, configure


class KernelMirrorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.checkout = Path(self.temp.name)
        p = self.checkout / ".repo/manifests"
        p.mkdir(parents=True)
        self.manifest = p / "default.xml"
        self.config = json.loads(CONFIG_PATH.read_text())
        self.build = json.loads(BUILD_PATH.read_text())
        self.render_xml(self.config["baseline_revision"])

    def render_xml(self, revision):
        self.manifest.write_text(
            '<manifest><project name="device_google_pantah-kernels_6.1" '
            'path="device/google/pantah-kernels/6.1" '
            f'revision="{revision}"/></manifest>', encoding="utf-8")

    def test_pinned_baseline_only(self):
        xml = generate_manifest(self.checkout, self.config, self.build)
        self.assertIn(self.config["baseline_revision"], xml)
        self.assertNotIn(self.config["latest_observed_mirror_revision"], xml)
        self.assertIn('<remove-project name="device_google_pantah-kernels_6.1"', xml)

    def test_no_kernel_cross_release(self):
        self.render_xml(self.config["latest_observed_mirror_revision"])
        with self.assertRaisesRegex(ValueError, "do not mix releases"):
            generate_manifest(self.checkout, self.config, self.build)

    def test_refuse_mismatched_build_tag(self):
        self.build["base_release_tag"] = "2099010100"
        with self.assertRaisesRegex(ValueError, "baseline"):
            generate_manifest(self.checkout, self.config, self.build)

    def test_idempotent_and_protect_modifications(self):
        output = Path(configure(self.checkout))
        self.assertTrue(output.is_file())
        self.assertEqual(configure(self.checkout), str(output))
        output.write_text("user edits")
        with self.assertRaisesRegex(ValueError, "Refusing"):
            configure(self.checkout)


if __name__ == "__main__":
    unittest.main()
