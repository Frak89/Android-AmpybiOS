"""Manifest-level checks; does not replace APK or on-device security tests."""
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

from scripts.validate_config import ROOT

ANDROID = "{http://schemas.android.com/apk/res/android}"
PERMISSION = "dev.ampybios.permission.SKIN_SYNC"
ACTION = "dev.ampybios.action.SKIN_CHANGED"


def manifest(app):
    root = ROOT / "apps" / app / "app/src/main/AndroidManifest.xml"
    return ET.parse(root).getroot()


class SkinContractTests(unittest.TestCase):
    def test_core_declares_signature_permission(self):
        root = manifest("AmpyCore")
        matches = [node for node in root.findall("permission")
                   if node.get(ANDROID + "name") == PERMISSION]
        self.assertEqual(len(matches), 1)
        self.assertEqual(matches[0].get(ANDROID + "protectionLevel"), "signature")

    def test_optin_apps_restrict_incoming_broadcast(self):
        for app in ("AmpyCam", "AmpyRaw", "AmpyConnect", "AmpyLauncher"):
            with self.subTest(app=app):
                root = manifest(app)
                uses = [e.get(ANDROID + "name") for e in root.findall("uses-permission")]
                self.assertIn(PERMISSION, uses)
                receivers = root.findall("./application/receiver")
                scoped = [r for r in receivers if r.get(ANDROID + "permission") == PERMISSION]
                self.assertEqual(len(scoped), 1)
                self.assertEqual(scoped[0].get(ANDROID + "exported"), "true")
                actions = [a.get(ANDROID + "name") for a in
                           scoped[0].findall("intent-filter/action")]
                self.assertIn(ACTION, actions)

    def test_no_broad_package_visibility_in_launcher(self):
        root = manifest("AmpyLauncher")
        permissions = [e.get(ANDROID + "name") for e in root.findall("uses-permission")]
        self.assertNotIn("android.permission.QUERY_ALL_PACKAGES", permissions)
        self.assertNotIn("android.permission.INTERNET", permissions)

    def test_connect_only_normal_network_permission(self):
        root = manifest("AmpyConnect")
        permissions = [e.get(ANDROID + "name") for e in root.findall("uses-permission")]
        self.assertIn("android.permission.INTERNET", permissions)
        self.assertNotIn("android.permission.ACCESS_FINE_LOCATION", permissions)


if __name__ == "__main__":
    unittest.main()
