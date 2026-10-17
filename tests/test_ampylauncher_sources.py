import unittest
from scripts.validate_config import ROOT

class LauncherSourceTests(unittest.TestCase):
    def test_only_launcher_visibility_and_safe_home_escape(self):
        root = ROOT / "apps/AmpyLauncher/app/src/main"
        manifest = (root / "AndroidManifest.xml").read_text()
        src = (root / "java/dev/ampybios/launcher/LauncherActivity.java").read_text()
        self.assertIn('android.intent.category.HOME', manifest)
        self.assertIn('android.intent.category.LAUNCHER', manifest)
        self.assertNotIn('QUERY_ALL_PACKAGES', manifest)
        self.assertNotIn('android.permission.INTERNET', manifest)
        for symbol in ('TextClock', 'ACTION_BATTERY_CHANGED',
                       'ACTION_HOME_SETTINGS', 'queryIntentActivities',
                       'setColumnCount(4)', 'ComponentName'):
            self.assertIn(symbol, src)

if __name__ == "__main__":
    unittest.main()
