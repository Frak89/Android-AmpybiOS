from scripts.validate_config import ROOT
import unittest

class AmpyConnectSourceTests(unittest.TestCase):
    def test_https_only_and_no_actuator_controls(self):
        home = ROOT / "apps/AmpyConnect/app/src/main"
        manifest = (home / "AndroidManifest.xml").read_text()
        src = (home / "java/dev/ampybios/connect/ConnectActivity.java").read_text()
        self.assertIn("android.permission.INTERNET", manifest)
        self.assertIn('android:usesCleartextTraffic="false"', manifest)
        self.assertIn('setInstanceFollowRedirects(false)', src)
        self.assertIn('setRequestMethod("GET")', src)
        self.assertIn('"/api/states"', src)
        self.assertNotIn('setRequestMethod("POST")', src)
        self.assertNotIn("getSharedPreferences(\"auth\"", src)

if __name__ == "__main__":
    unittest.main()
