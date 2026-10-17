package dev.ampybios.rec;

import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

/** Actual production RecPackage tests on JVM; no Android Activity or SDK device. */
public final class RecPackageTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static final String THEME = "{\"schema\":1,\"id\":\"fusion\",\"label\":\"Fusion\","
            + "\"colors\":{\"background\":\"#071222\",\"surface\":\"#151929\","
            + "\"foreground\":\"#EBF6FF\",\"accent\":\"#13DFF9\","
            + "\"secondary\":\"#8638E8\",\"radar\":\"#8638E8\"},"
            + "\"corner_radii_dp\":[30,10,30,10],"
            + "\"effects\":{\"radar_border\":true,\"reduced_motion\":false,"
            + "\"glow_alpha\":0.4,\"radar_speed_dps\":100}}";

    private static String sha(String value) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result=new StringBuilder(64);
        for (byte b:digest) result.append(String.format("%02x",b&0xff));
        return result.toString();
    }

    private String manifest(String expectedHash) throws Exception {
        JSONObject f=new JSONObject();
        f.put("path","config/themes/fusion.json");
        f.put("sha256",expectedHash);
        JSONObject m=new JSONObject();
        m.put("schema",1);
        m.put("type","live-theme");
        m.put("files",new JSONArray().put(f));
        return m.toString();
    }

    private File zip(String... namesAndContents) throws Exception {
        File result=temp.newFile("package-"+System.nanoTime()+".zip");
        try(ZipOutputStream stream=new ZipOutputStream(new FileOutputStream(result))) {
            for (int i=0;i<namesAndContents.length;i+=2) {
                stream.putNextEntry(new ZipEntry(namesAndContents[i]));
                stream.write(namesAndContents[i+1].getBytes(StandardCharsets.UTF_8));
                stream.closeEntry();
            }
        }
        return result;
    }

    @Test public void acceptsOnlyDeclaredAndHashedJson() throws Exception {
        File file=zip("ampyrec.json",manifest(sha(THEME)),
                "config/themes/fusion.json",THEME);
        RecPackage.Report report=RecPackage.inspect(file);
        assertEquals(RecPackage.Kind.LIVE_THEME,report.kind);
        assertEquals(1,report.themes.size());
        assertEquals("fusion",report.themes.get(0).id);
        assertEquals(64,report.zipSha256.length());
    }

    @Test public void rejectsWrongDigest() throws Exception {
        File file=zip("ampyrec.json",manifest("a".repeat(64)),
                "config/themes/fusion.json",THEME);
        try {
            RecPackage.inspect(file);
            fail("Must reject a mismatched digest");
        } catch(java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("SHA-256"));
        }
    }

    @Test public void rejectsZipSlipPaths() throws Exception {
        File file=zip("../vendor/boot.img","untrusted");
        try {
            RecPackage.inspect(file);
            fail("Must reject path traversal");
        } catch(java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("Percorso ZIP"));
        }
    }

    @Test public void rejectsUndeclaredFiles() throws Exception {
        File file=zip("ampyrec.json",manifest(sha(THEME)),
                "config/themes/fusion.json",THEME,
                "scripts/modify_kernel.sh","echo bypass");
        try {
            RecPackage.inspect(file);
            fail("Must not import extra executable content");
        } catch(java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("file extra"));
        }
    }

    @Test public void detectsButCannotInstallOtaArchives() throws Exception {
        File ota=zip("payload.bin","placeholder","payload_properties.txt","payload");
        RecPackage.Report report=RecPackage.inspect(ota);
        assertEquals(RecPackage.Kind.SYSTEM_OTA_BLOCKED,report.kind);
        assertTrue(report.themes.isEmpty());
    }

    @Test public void genericSourceArchiveIsNotAnUpdate() throws Exception {
        File archive=zip("kernel/sched/fair.c","do_not_execute");
        RecPackage.Report report=RecPackage.inspect(archive);
        assertEquals(RecPackage.Kind.UNKNOWN,report.kind);
        assertEquals(0,report.themes.size());
    }

    @Test public void rejectsOverlongThemeLabel() throws Exception {
        String altered=THEME.replace("\"Fusion\"",
                "\""+"X".repeat(61)+"\"");
        File file=zip("ampyrec.json",manifest(sha(altered)),
                "config/themes/fusion.json",altered);
        try {
            RecPackage.inspect(file);
            fail("Must reject long labels");
        } catch(java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("Etichetta"));
        }
    }
}
