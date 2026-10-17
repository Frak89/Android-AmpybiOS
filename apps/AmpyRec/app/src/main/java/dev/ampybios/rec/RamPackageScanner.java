package dev.ampybios.rec;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Strict, in-memory ZIP validator for an immediate LIVE theme import.
 * No files, no /tmp, no app-private staging, no scripts and no firmware.
 * Deliberately caps the entire archive at 256 KiB and inflated content too.
 */
public final class RamPackageScanner {
    public static final int MAX_ZIP_BYTES = 256 * 1024;
    private static final int MAX_INFLATED_BYTES = 256 * 1024;
    private static final int MAX_ENTRY_BYTES = 32 * 1024;
    private static final int MAX_ENTRY_COUNT = 5;
    private RamPackageScanner() {}

    public static RecPackage.Report inspect(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_ZIP_BYTES)
            throw new IOException("Il pacchetto RAM deve essere inferiore a 256 KiB");
        Map<String,byte[]> items = new HashMap<>();
        int inflated = 0;
        try (ZipInputStream stream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                String name = entry.getName();
                if (!isSafePath(name) || entry.isDirectory() || items.containsKey(name))
                    throw new IOException("ZIP con file duplicati o percorsi non sicuri");
                if (items.size() >= MAX_ENTRY_COUNT)
                    throw new IOException("Numero di file eccessivo");
                if (!"ampyrec.json".equals(name) && !name.matches(
                        "config/themes/(fusion|cyberpunk|futuristic|minimal)\\.json"))
                    throw new IOException("File non LIVE rilevato: aggiornamento rifiutato");
                int max = "ampyrec.json".equals(name) ? 8192 : MAX_ENTRY_BYTES;
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = stream.read(buf)) != -1) {
                    if (content.size() + n > max ||
                            (long) inflated + content.size() + n > MAX_INFLATED_BYTES)
                        throw new IOException("ZIP RAM supera i limiti di decompressione");
                    content.write(buf,0,n);
                }
                inflated += content.size();
                items.put(name,content.toByteArray());
                stream.closeEntry(); // forces ZIP CRC verification
            }
        }
        byte[] manifestBytes = items.get("ampyrec.json");
        if (manifestBytes == null || manifestBytes.length < 2)
            throw new IOException("Manca un manifest LIVE");
        JSONObject manifest = new JSONObject(new String(manifestBytes,StandardCharsets.UTF_8));
        if (manifest.getInt("schema") != 1 ||
                !"live-theme".equals(manifest.getString("type")))
            throw new IOException("Formato RAM non supportato");
        JSONArray records = manifest.getJSONArray("files");
        if (records.length() < 1 || records.length() > 4 ||
                items.size() != records.length() + 1)
            throw new IOException("Manifest RAM incompleto o contiene file extra");
        List<RecPackage.Theme> themes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            String path = record.getString("path");
            if (!path.matches("config/themes/(fusion|cyberpunk|futuristic|minimal)\\.json"))
                throw new IOException("Percorso tema non consentito");
            String id = path.substring("config/themes/".length(),path.length() - 5);
            if (!ids.add(id)) throw new IOException("Tema duplicato");
            byte[] raw = items.get(path);
            if (raw == null || raw.length > MAX_ENTRY_BYTES)
                throw new IOException("Tema mancante o troppo grande");
            String digest = sha(raw);
            if (!digest.equals(record.getString("sha256")))
                throw new IOException("SHA-256 del tema non corrispondente");
            String json = new String(raw, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(json);
            if (obj.getInt("schema") != 1 || !id.equals(obj.getString("id")) ||
                    obj.getString("label").length() > 60)
                throw new IOException("Tema non compatibile");
            JSONObject colors = obj.getJSONObject("colors");
            for (String k : new String[]{"background","surface","foreground",
                    "accent","secondary","radar"}) {
                if (!colors.getString(k).matches("#[0-9a-fA-F]{6}"))
                    throw new IOException("Colore tema non valido");
            }
            JSONArray radii = obj.getJSONArray("corner_radii_dp");
            if (radii.length() != 4) throw new IOException("Angoli tema non validi");
            for (int j = 0; j < 4; j++) {
                double radius = radii.getDouble(j);
                if (!Double.isFinite(radius) || radius < 0 || radius > 96)
                    throw new IOException("Angolo tema fuori intervallo");
            }
            JSONObject effects = obj.getJSONObject("effects");
            double glow = effects.getDouble("glow_alpha");
            int speed = effects.getInt("radar_speed_dps");
            if (!Double.isFinite(glow) || glow < 0 || glow > 1 ||
                    speed < 0 || speed > 360)
                throw new IOException("Effetti tema non validi");
            effects.getBoolean("radar_border");
            effects.getBoolean("reduced_motion");
            themes.add(new RecPackage.Theme(path,id,json));
        }
        return new RecPackage.Report(RecPackage.Kind.LIVE_THEME,sha(bytes),
                "ZIP LIVE verificato in RAM",bytes.length,themes);
    }

    private static boolean isSafePath(String path) {
        if (path == null || path.isEmpty() || path.length() > 180 ||
                path.startsWith("/") || path.indexOf('\\') >= 0 ||
                path.indexOf(0) >= 0 || path.contains("//")) return false;
        for (String token : path.split("/"))
            if (".".equals(token) || "..".equals(token) || token.isEmpty()) return false;
        return !path.matches("^[A-Za-z]:.*");
    }

    private static String sha(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder result = new StringBuilder(64);
        for (byte b : hash) result.append(String.format(Locale.ROOT,"%02x",b&255));
        return result.toString();
    }
}
