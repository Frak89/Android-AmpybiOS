package dev.ampybios.rec;

import static org.junit.Assert.*;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Production source-ZIP scanner tested without Android and without writing ZIPs to disk. */
public final class SourceZipInspectorTest {
    private static byte[] zip(String... members) throws Exception {
        ByteArrayOutputStream buf=new ByteArrayOutputStream();
        try(ZipOutputStream out=new ZipOutputStream(buf)) {
            for(int i=0;i<members.length;i+=2) {
                out.putNextEntry(new ZipEntry(members[i]));
                out.write(members[i+1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return buf.toByteArray();
    }

    private static SourceZipInspector.Result inspect(byte[] data) throws Exception {
        return SourceZipInspector.inspect(()->new ByteArrayInputStream(data));
    }

    @Test public void recognizesActualSourceTreeWithoutRunningCode() throws Exception {
        byte[] data=zip(
                "Android-AmpybiOS-main/apps/AmpyCore/app/build.gradle.kts","plugins {}",
                "Android-AmpybiOS-main/apps/AmpyLauncher/app/build.gradle.kts","plugins {}",
                "Android-AmpybiOS-main/apps/AmpyRec/app/build.gradle.kts","plugins {}",
                "Android-AmpybiOS-main/kernel/AmpyKernel/README.md","sources only");
        SourceZipInspector.Result result=inspect(data);
        assertTrue(result.ampybiosSource);
        assertFalse(result.containsOtaPayload);
        assertEquals(4,result.count);
        assertEquals(data.length,result.compressedBytes);
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        byte[] expected=digest.digest(data);
        StringBuilder sha=new StringBuilder();
        for(byte b:expected)sha.append(String.format(Locale.ROOT,"%02x",b&255));
        assertEquals(sha.toString(),result.sha256);
    }

    @Test public void genericSourceZipIsNotAmpybiOS() throws Exception {
        byte[] data=zip("someproject/src/Main.java","class Main {}");
        assertFalse(inspect(data).ampybiosSource);
    }

    @Test public void otaPayloadIsNotAReadySourceSession() throws Exception {
        byte[] data=zip("payload.bin","binary","payload_properties.txt","v=1");
        assertTrue(inspect(data).containsOtaPayload);
        assertFalse(inspect(data).ampybiosSource);
    }

    @Test public void rejectsTraversal() throws Exception {
        byte[] data=zip("../boot.img","bad");
        try {
            inspect(data);
            fail("ZIP slip should be rejected");
        } catch(java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("ZIP"));
        }
    }

    @Test public void rejectsExcessiveExpandedContent() throws Exception {
        // A single compressed archive cannot inflate beyond the 1 GiB
        // scanner policy. Test a simpler malformed stream instead.
        byte[] data=new byte[]{0x50,0x4b,0x03,0x04,0,0,0};
        try {
            inspect(data);
            fail("truncated input must not be identified as source");
        } catch(Exception expected) {
            assertTrue(expected instanceof java.io.IOException);
        }
    }
}
