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
        self.assertIn('deliverToCore(context,report)', state)
        self.assertNotIn('File releases = new File(area(context)', state)
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
        state = (APP / "java/dev/ampybios/rec/RecState.java").read_text()
        self.assertIn('getContentResolver().call(', state)
        self.assertIn('PackageManager.SIGNATURE_MATCH', state)
        self.assertIn('markDeferredIfCurrent', state)
        self.assertIn('queuedRevision', state)
        coreManifest = (ROOT / "apps/AmpyCore/app/src/main/AndroidManifest.xml").read_text()
        self.assertIn('android:permission="dev.ampybios.permission.SKIN_SYNC"',coreManifest)
        self.assertIn('android:authorities="dev.ampybios.core.themes"',coreManifest)
        self.assertIn('content://', (APP / "java/dev/ampybios/rec/RecState.java").read_text())

    def test_memory_only_flow_has_no_staging_write(self):
        scanner=(APP / "java/dev/ampybios/rec/RamPackageScanner.java").read_text()
        state=(APP / "java/dev/ampybios/rec/RecState.java").read_text()
        self.assertIn("ZipInputStream",scanner)
        self.assertIn("MAX_ZIP_BYTES = 256 * 1024",scanner)
        self.assertIn("applyDirectFromRam",state)
        self.assertIn("RamPackageScanner.inspect(memory.toByteArray())",state)
        self.assertIn("deliverToCore(context,report)",state)
        self.assertTrue((APP.parent / "test/java/dev/ampybios/rec/RamPackageScannerTest.java").is_file())

    def test_reboot_safe_zero_copy_saf_reference(self):
        state=(APP / "java/dev/ampybios/rec/RecState.java").read_text()
        screen=(APP / "java/dev/ampybios/rec/RecActivity.java").read_text()
        self.assertIn("takePersistableUriPermission",state)
        self.assertIn("releasePersistableUriPermission",state)
        self.assertIn('putString("mode","reference")',state)
        self.assertIn('RamPackageScanner.inspect(data)',state)
        self.assertIn('expectedRevision.equals(report.zipSha256)',state)
        self.assertIn("REQUEST_REFERENCE_ZIP",screen)

    def test_home_session_never_executes_uncompiled_source(self):
        activity=(APP / "java/dev/ampybios/rec/RecActivity.java").read_text()
        inspector=(APP / "java/dev/ampybios/rec/SourceZipInspector.java").read_text()
        session=(APP / "java/dev/ampybios/rec/SourceSession.java").read_text()
        self.assertIn("REQUEST_SOURCE_ZIP",activity)
        self.assertIn('AmpybiOS.zip',activity)
        self.assertIn("ACTION_HOME_SETTINGS",activity)
        self.assertIn("ZipInputStream",inspector)
        self.assertIn("MAX_ENTRIES = 5000",inspector)
        self.assertIn("takePersistableUriPermission",session)
        self.assertIn("sha256",session)
        self.assertNotIn("DexClassLoader",session)
        self.assertNotIn("Runtime.getRuntime().exec",inspector)
        self.assertTrue((APP.parent / "test/java/dev/ampybios/rec/SourceZipInspectorTest.java").is_file())

    def test_apk_workflow(self):
        self.assertTrue((ROOT/".github/workflows/ampyrec-apk.yml").is_file())
        self.assertTrue((ROOT/"scripts/build_ampyrec_local.sh").is_file())
        verifier = (ROOT/"scripts/verify_ampyrec_apk.sh").read_text()
        self.assertIn('apksigner', verifier)
        self.assertIn('sha256sum', verifier)
        self.assertIn('dev.ampybios.rec', verifier)

if __name__ == "__main__":
    unittest.main()
