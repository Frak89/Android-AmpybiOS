package dev.ampybios.rec;

import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real source under test: bounded in-memory ZIP parser, no disk staging. */
public final class RamPackageScannerTest {
    private static final String THEME =
            "{\"schema\":1,\"id\":\"fusion\",\"label\":\"Fusion\","
            +"\"colors\":{\"background\":\"#071222\",\"surface\":\"#151929\","
            +"\"foreground\":\"#EBF6FF\",\"accent\":\"#13DFF9\","
            +"\"secondary\":\"#8638E8\",\"radar\":\"#8638E8\"},"
            +"\"corner_radii_dp\":[30,10,30,10],"
            +"\"effects\":{\"radar_border\":true,\"reduced_motion\":false,"
            +"\"glow_alpha\":0.4,\"radar_speed_dps\":100}}";

    private static String hash(byte[] bytes) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder s=new StringBuilder(64);
        for (byte b:digest) s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return s.toString();
    }
    private static String manifest(String theme) throws Exception {
        JSONObject file=new JSONObject();
        file.put("path","config/themes/fusion.json");
        file.put("sha256",hash(theme.getBytes(StandardCharsets.UTF_8)));
        JSONObject root=new JSONObject();
        root.put("schema",1);
        root.put("type","live-theme");
        root.put("files",new JSONArray().put(file));
        return root.toString();
    }
    private static byte[] zip(String... files) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream out=new ZipOutputStream(bytes)) {
            for(int i=0;i<files.length;i+=2) {
                out.putNextEntry(new ZipEntry(files[i]));
                out.write(files[i+1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    private static byte[] valid() throws Exception {
        return zip("ampyrec.json",manifest(THEME),"config/themes/fusion.json",THEME);
    }
    @Test public void acceptsCheckedThemeWithoutDiskStaging() throws Exception {
        byte[] archive=valid();
        RecPackage.Report report=RamPackageScanner.inspect(archive);
        assertEquals(RecPackage.Kind.LIVE_THEME,report.kind);
        assertEquals(hash(archive),report.zipSha256);
        assertEquals("fusion",report.themes.get(0).id);
        assertEquals(1,report.themes.size());
    }
    @Test public void rejectsPathTraversal() throws Exception {
        try {
            RamPackageScanner.inspect(zip("../boot.img","untrusted"));
            fail("must fail closed");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("ZIP"));
        }
    }
    @Test public void rejectsInjectedScript() throws Exception {
        try {
            RamPackageScanner.inspect(zip("ampyrec.json",manifest(THEME),
                    "config/themes/fusion.json",THEME,
                    "scripts/ota.sh","echo bad"));
            fail("must refuse executable payload");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("File non LIVE"));
        }
    }
    @Test public void rejectsForgedHash() throws Exception {
        String manifest=manifest(THEME).replace(hash(THEME.getBytes(StandardCharsets.UTF_8)),
                "0".repeat(64));
        try {
            RamPackageScanner.inspect(zip("ampyrec.json",manifest,
                    "config/themes/fusion.json",THEME));
            fail("must check hash of exact uncompressed bytes");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("SHA-256"));
        }
    }
    @Test public void rejectsOversizeInMemoryZip() throws Exception {
        byte[] tooLarge=new byte[RamPackageScanner.MAX_ZIP_BYTES+1];
        try {
            RamPackageScanner.inspect(tooLarge);
            fail("must refuse large packages");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("256 KiB"));
        }
    }
    @Test public void producesSameRevisionAndThemesAsDiskScanner() throws Exception {
        byte[] archive=valid();
        java.io.File tempFile=java.io.File.createTempFile("ampyrec-compare",".zip");
        try {
            java.nio.file.Files.write(tempFile.toPath(),archive);
            RecPackage.Report disk=RecPackage.inspect(tempFile);
            RecPackage.Report memory=RamPackageScanner.inspect(archive);
            assertEquals(disk.zipSha256,memory.zipSha256);
            assertEquals(disk.kind,memory.kind);
            assertEquals(disk.themes.size(),memory.themes.size());
            assertEquals(disk.themes.get(0).content,memory.themes.get(0).content);
        } finally {
            tempFile.delete();
        }
    }

    @Test public void rejectsInflatedZipBombEvenIfCompressedIsSmall() throws Exception {
        String huge="Z".repeat(40*1024);
        try {
            RamPackageScanner.inspect(zip("ampyrec.json",huge));
            fail("must cap inflated entry");
        } catch(java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("limiti"));
        }
    }

    @Test public void rejectsOtaInMemory() throws Exception {
        try {
            RamPackageScanner.inspect(zip("payload.bin","firmware",
                    "payload_properties.txt","properties"));
            fail("must not accept OTA as RAM live content");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("File non LIVE"));
        }
    }
}
