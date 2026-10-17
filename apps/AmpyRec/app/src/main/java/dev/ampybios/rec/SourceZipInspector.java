package dev.ampybios.rec;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Read-only source archive inspection via Android SAF. Never extracts,
 * executes, loads dex, mounts RAM disks or touches boot/system partitions.
 * This identifies source content, NOT an executable Android image.
 */
public final class SourceZipInspector {
    private static final long MAX_ARCHIVE = 256L * 1024 * 1024;
    private static final long MAX_INFLATED = 1024L * 1024 * 1024;
    private static final int MAX_ENTRIES = 5000;
    private static final int CHUNK = 16 * 1024;

    public static final class Result {
        public final String sha256;
        public final int count;
        public final long compressedBytes;
        public final long uncompressedBytes;
        public final boolean ampybiosSource;
        public final boolean containsOtaPayload;

        Result(String sha, int entries, long packed, long unpacked,
               boolean source, boolean ota) {
            sha256 = sha;
            count = entries;
            compressedBytes = packed;
            uncompressedBytes = unpacked;
            ampybiosSource = source;
            containsOtaPayload = ota;
        }
    }

    private SourceZipInspector() {}

    private static final class Bounded extends java.io.FilterInputStream {
        long consumed;
        final long limit;
        Bounded(InputStream input, long maxBytes) {
            super(input);
            limit = maxBytes;
        }
        @Override public int read() throws IOException {
            int result = super.read();
            if (result >= 0 && ++consumed > limit)
                throw new IOException("Archivio sorgente oltre 256 MiB");
            return result;
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0 && (consumed += n) > limit)
                throw new IOException("Archivio sorgente oltre 256 MiB");
            return n;
        }
    }

    private static boolean safe(String path) {
        if (path == null || path.isEmpty() || path.length() > 360 ||
                path.startsWith("/") || path.indexOf((char)0) >= 0 ||
                path.indexOf('\\') >= 0 || path.contains("//"))
            return false;
        for (String part : path.split("/"))
            if (part.isEmpty() || ".".equals(part) || "..".equals(part))
                return false;
        return !path.matches("^[A-Za-z]:.*");
    }

    public static Result inspect(Context context, Uri source) throws Exception {
        int count = 0;
        long expanded = 0;
        boolean core = false, launcher = false, rec = false, ota = false;
        Set<String> names = new HashSet<>();
        long compressedDuringParse;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) throw new IOException("Il documento sorgente non è accessibile");
            Bounded bounded = new Bounded(new BufferedInputStream(in),MAX_ARCHIVE);
            try (ZipInputStream zip = new ZipInputStream(bounded)) {
                byte[] chunk = new byte[CHUNK];
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (!safe(name) || !names.add(name))
                        throw new IOException("Percorso ZIP duplicato o non sicuro");
                    if (++count > MAX_ENTRIES)
                        throw new IOException("Troppi file per analisi sorgenti");
                    if (!entry.isDirectory()) {
                        String normalized = name;
                        core |= normalized.endsWith(
                                "apps/AmpyCore/app/build.gradle.kts");
                        launcher |= normalized.endsWith(
                                "apps/AmpyLauncher/app/build.gradle.kts");
                        rec |= normalized.endsWith(
                                "apps/AmpyRec/app/build.gradle.kts");
                        ota |= normalized.equals("payload.bin") ||
                                normalized.endsWith("/payload.bin");
                        int n;
                        while ((n = zip.read(chunk)) != -1) {
                            expanded += n;
                            if (expanded > MAX_INFLATED)
                                throw new IOException("ZIP troppo grande dopo decompressione");
                        }
                    }
                    zip.closeEntry();
                }
                compressedDuringParse = bounded.consumed;
            }
        }
        if (count == 0) throw new IOException("Archivio ZIP vuoto o non valido");
        // ZipInputStream can stop before central directory. Hash the entire
        // original compressed document independently, for a stable revision.
        String digest;
        long total = 0;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream input = context.getContentResolver().openInputStream(source)) {
                if (input == null) throw new IOException("Il documento è scomparso");
                byte[] chunk = new byte[CHUNK];
                int n;
                while ((n = input.read(chunk)) != -1) {
                    total += n;
                    if (total > MAX_ARCHIVE)
                        throw new IOException("ZIP sorgente oltre 256 MiB");
                    md.update(chunk, 0, n);
                }
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : md.digest())
                hex.append(String.format(Locale.ROOT,"%02x",b & 255));
            digest = hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
        if (total < compressedDuringParse)
            throw new IOException("Documento variato durante la verifica");
        return new Result(digest,count,total,expanded,core && launcher && rec,ota);
    }
}
