package dev.ampybios.rec;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Inspect UNTRUSTED local ZIP files without extracting arbitrary file names.
 * File hashes in an AmpyRec manifest detect corruption, NOT publisher identity.
 *
 * Live package schema:
 * { "schema": 1, "type": "live-theme", "files": [
 *   {"path":"config/themes/fusion.json","sha256":"64 lowercase hex chars"}
 * ]}
 * Unknown archives / OTA payloads can be identified, but never installed.
 */
public final class RecPackage {
    public static final long MAX_ARCHIVE_BYTES = 512L * 1024 * 1024;
    private static final long MAX_LIVE_BYTES = 256 * 1024;
    private static final long MAX_THEME_BYTES = 32 * 1024;
    private static final int MAX_ENTRIES = 128;
    public enum Kind { LIVE_THEME, SYSTEM_OTA_BLOCKED, UNKNOWN }

    public static final class Theme {
        public final String path, id, content;
        Theme(String path, String id, String content) {
            this.path = path; this.id = id; this.content = content;
        }
    }
    public static final class Report {
        public final Kind kind;
        public final String zipSha256, description;
        public final long archiveBytes;
        public final List<Theme> themes;
        Report(Kind kind, String hash, String description, long archiveBytes,
               List<Theme> themes) {
            this.kind=kind; this.zipSha256=hash; this.description=description;
            this.archiveBytes=archiveBytes; this.themes=themes;
        }
    }

    private static boolean validName(String s) {
        if (s == null || s.isEmpty() || s.length()>180 || s.startsWith("/") ||
                s.contains("\\") || s.contains("\u0000") || s.startsWith("./") ||
                s.contains("//")) return false;
        for (String part : s.split("/")) {
            if (part.equals(".") || part.equals("..") || part.isEmpty()) return false;
        }
        return !s.matches("^[A-Za-z]:.*");
    }

    public static String sha256(InputStream stream, long limit) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            long count = 0;
            int n;
            while ((n=stream.read(buffer))!=-1) {
                count += n;
                if (count>limit) throw new IOException("Limite dimensioni superato");
                digest.update(buffer,0,n);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
    private static String toHex(byte[] bytes) {
        StringBuilder b = new StringBuilder(bytes.length*2);
        for (byte x : bytes) b.append(String.format(Locale.ROOT,"%02x",x&255));
        return b.toString();
    }

    private static byte[] bounded(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int size;
        while ((size=input.read(buffer))!=-1) {
            if ((long)out.size()+size>limit) throw new IOException("File troppo grande");
            out.write(buffer,0,size);
        }
        return out.toByteArray();
    }

    public static Report inspect(File archive) throws Exception {
        if (!archive.isFile() || archive.length()==0 ||
                archive.length()>MAX_ARCHIVE_BYTES)
            throw new IOException("ZIP assente o oltre 512 MiB");
        String zipHash;
        try (InputStream in = new FileInputStream(archive)) {
            zipHash=sha256(in,MAX_ARCHIVE_BYTES);
        }
        try (ZipFile zip = new ZipFile(archive)) {
            Set<String> seen = new HashSet<>();
            long sum=0;
            boolean otaPayload=false, otaProps=false;
            java.util.Enumeration<? extends ZipEntry> it=zip.entries();
            while (it.hasMoreElements()) {
                ZipEntry entry=it.nextElement();
                String name=entry.getName();
                if (!validName(name) || !seen.add(name))
                    throw new IOException("Percorso ZIP ambiguo, duplicato o non sicuro");
                if (seen.size()>MAX_ENTRIES) throw new IOException("Troppi file nel pacchetto");
                if (entry.isDirectory()) continue;
                long uncompressed=entry.getSize(), compressed=entry.getCompressedSize();
                if (uncompressed<0 || compressed<0)
                    throw new IOException("Dimensioni ZIP non attendibili");
                sum+=uncompressed;
                if (sum>MAX_ARCHIVE_BYTES) throw new IOException("ZIP decompression bomb");
                if (compressed>0 && uncompressed>compressed*100L &&
                        uncompressed>1024*1024)
                    throw new IOException("Rapporto di compressione sospetto");
                if ("payload.bin".equals(name)) otaPayload=true;
                if ("payload_properties.txt".equals(name)) otaProps=true;
            }
            ZipEntry manifestEntry=zip.getEntry("ampyrec.json");
            if (manifestEntry==null) {
                Kind type=otaPayload&&otaProps?Kind.SYSTEM_OTA_BLOCKED:Kind.UNKNOWN;
                String label=type==Kind.SYSTEM_OTA_BLOCKED
                        ? "Pacchetto A/B rilevato, firma non verificata. Serve AmpyOTA di sistema."
                        : "Archivio generico/sorgenti: non installabile come aggiornamento Android.";
                return new Report(type,zipHash,label,archive.length(),new ArrayList<>());
            }
            if (manifestEntry.getSize()<2 || manifestEntry.getSize()>8192)
                throw new IOException("Manifest AmpyRec troppo grande");
            JSONObject manifest;
            try (InputStream input=zip.getInputStream(manifestEntry)) {
                manifest=new JSONObject(new String(bounded(input,8192),
                        java.nio.charset.StandardCharsets.UTF_8));
            }
            if (manifest.getInt("schema")!=1 ||
                    !"live-theme".equals(manifest.getString("type")))
                throw new IOException("Formato AmpyRec non supportato (nessuna scrittura di sistema)");
            if (sum>MAX_LIVE_BYTES) throw new IOException("Live ZIP oltre 256 KiB");
            JSONArray files=manifest.getJSONArray("files");
            if (files.length()<1 || files.length()>4 || seen.size()!=files.length()+1)
                throw new IOException("Manifest incompleto o file extra non dichiarati");
            List<Theme> themes=new ArrayList<>();
            Set<String> ids=new HashSet<>();
            for (int i=0;i<files.length();i++) {
                JSONObject file=files.getJSONObject(i);
                String path=file.getString("path");
                if (!path.matches("config/themes/(fusion|cyberpunk|futuristic|minimal)\\.json"))
                    throw new IOException("Solo i temi AmpybiOS JSON sono aggiornabili LIVE");
                String id=path.substring("config/themes/".length(),path.length()-5);
                if (!ids.add(id)) throw new IOException("Tema duplicato");
                ZipEntry entry=zip.getEntry(path);
                if (entry==null || entry.isDirectory() || entry.getSize()>MAX_THEME_BYTES)
                    throw new IOException("Tema dichiarato assente o troppo grande");
                byte[] data;
                try (InputStream input=zip.getInputStream(entry)) {
                    data=bounded(input,MAX_THEME_BYTES);
                }
                String checksum;
                try {
                    MessageDigest hash=MessageDigest.getInstance("SHA-256");
                    checksum=toHex(hash.digest(data));
                } catch (NoSuchAlgorithmException impossible) {
                    throw new AssertionError(impossible);
                }
                if (!checksum.equals(file.getString("sha256")))
                    throw new IOException("Hash SHA-256 non corrispondente: "+path);
                String content=new String(data,java.nio.charset.StandardCharsets.UTF_8);
                JSONObject json=new JSONObject(content);
                if (json.getInt("schema")!=1 || !id.equals(json.getString("id")))
                    throw new IOException("Tema incompatibile");
                JSONObject colors=json.getJSONObject("colors");
                for (String k : new String[]{"background","surface","foreground",
                        "accent","secondary","radar"}) {
                    if (!colors.getString(k).matches("#[0-9a-fA-F]{6}"))
                        throw new IOException("Colore non valido: "+k);
                }
                JSONArray corners=json.getJSONArray("corner_radii_dp");
                if (corners.length()!=4) throw new IOException("Servono quattro angoli");
                for (int j=0;j<4;j++) {
                    double radius=corners.getDouble(j);
                    if (!Double.isFinite(radius) || radius<0 || radius>96)
                        throw new IOException("Angolo SVG non valido");
                }
                JSONObject effects=json.getJSONObject("effects");
                double glow=effects.getDouble("glow_alpha");
                int radarSpeed=effects.getInt("radar_speed_dps");
                if (!Double.isFinite(glow)||glow<0||glow>1 ||
                        radarSpeed<0||radarSpeed>360)
                    throw new IOException("Effetti fuori intervallo");
                effects.getBoolean("radar_border");
                effects.getBoolean("reduced_motion");
                themes.add(new Theme(path,id,content));
            }
            return new Report(Kind.LIVE_THEME,zipHash,
                    "Pacchetto LIVE con "+themes.size()+" tema/i verificato/i",
                    archive.length(),themes);
        }
    }
}
