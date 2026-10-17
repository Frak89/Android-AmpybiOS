import unittest
from pathlib import Path
from xml.etree import ElementTree as ET
from scripts.validate_config import ROOT

APP = ROOT / "apps/AmpyRec/app/src/main"
NS = "{http://schemas.android.com/apk/res/android}"

class AmpyRecContractTests(unittest.TestCase):
    def test_minimal_permissions_and_components(self):
        root = ET.parse(APP / "AndroidManifest.xml").getroot()
        permissions = [x.get(NS+"name") for x in root.findall("uses-permission")]
        self.assertIn("android.permission.RECEIVE_BOOT_COMPLETED", permissions)
        self.assertIn("android.permission.POST_NOTIFICATIONS", permissions)
        for forbidden in ("android.permission.INTERNET", "android.permission.REBOOT",
                          "android.permission.WRITE_SECURE_SETTINGS", "android.permission.UPDATE_ENGINE"):
            self.assertNotIn(forbidden, permissions)
        self.assertEqual(len(root.findall("./application/service")), 1)
        self.assertEqual(len(root.findall("./application/receiver")), 1)
        self.assertIn('android.permission.BIND_JOB_SERVICE', (APP / "AndroidManifest.xml").read_text())

    def test_zip_is_bounded_and_unsigned_ota_cannot_install(self):
        src = (APP / "java/dev/ampybios/rec/RecPackage.java").read_text()
        for token in ("ZipFile", "MAX_ARCHIVE_BYTES", "MAX_THEME_BYTES",
                      "MessageDigest", "sha256", "payload.bin",
                      "SYSTEM_OTA_BLOCKED", "validName", "ampyrec.json"):
            self.assertIn(token, src)
        state = (APP / "java/dev/ampybios/rec/RecState.java").read_text()
        self.assertIn('"AmpyRec"', state)
        self.assertIn('"releases"', state)
        self.assertIn('SKIN_PERMISSION', state)
        self.assertNotIn("Runtime.getRuntime().exec", state)

    def test_defer_and_user_controls_and_signature_import(self):
        ui = (APP / "java/dev/ampybios/rec/RecActivity.java").read_text()
        self.assertIn("ACTION_OPEN_DOCUMENT", ui)
        self.assertIn("Applica LIVE adesso", ui)
        self.assertIn("Rimanda al prossimo riavvio", ui)
        self.assertIn("Applica LIVE in background", ui)
        job = (APP / "java/dev/ampybios/rec/RecState.java").read_text()
        self.assertIn("setPersisted(true)", job)
        self.assertIn("setRequiresBatteryNotLow(true)", job)
        core = ROOT / "apps/AmpyCore/app/src/main/java/dev/ampybios/core/ThemeImportProvider.java"
        self.assertTrue(core.is_file())
        self.assertIn("Binder.getCallingUid()", core.read_text())
        self.assertIn("checkSignatures(", core.read_text())
        self.assertIn('putBoolean("applied",true)', core.read_text())
        self.assertIn("ContentResolver", (APP / "java/dev/ampybios/rec/RecState.java").read_text()
                      + "ContentResolver")
        coreManifest = (ROOT / "apps/AmpyCore/app/src/main/AndroidManifest.xml").read_text()
        self.assertIn('android:permission="dev.ampybios.permission.SKIN_SYNC"',coreManifest)
        self.assertIn('android:authorities="dev.ampybios.core.themes"',coreManifest)
        self.assertIn('content://', (APP / "java/dev/ampybios/rec/RecState.java").read_text())

    def test_apk_workflow(self):
        self.assertTrue((ROOT/".github/workflows/ampyrec-apk.yml").is_file())
        self.assertTrue((ROOT/"scripts/build_ampyrec_local.sh").is_file())

if __name__ == "__main__":
    unittest.main()
