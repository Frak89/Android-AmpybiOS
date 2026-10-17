package dev.ampybios.cam;

import android.content.Context;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Read-only visual theme for AmpyCam. JSONs are versioned at repo/config/themes.
 * Theme choice is currently per-app; future ROM integration will provide a
 * privileged, safely validated shared theme provider.
 */
final class CamTheme {
    final int background, foreground, accent, secondary;
    final float[] radii;

    private CamTheme(int background, int foreground, int accent,
                     int secondary, float[] radii) {
        this.background = background;
        this.foreground = foreground;
        this.accent = accent;
        this.secondary = secondary;
        this.radii = radii;
    }

    static CamTheme load(Context context, String id) {
        if (!id.matches("[a-z-]{1,32}")) id = "fusion";
        try (InputStream stream = context.getAssets().open(id + ".json")) {
            // InputStream.readAllBytes() is not available on every minSdk 29 device.
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int count;
            while ((count = stream.read(chunk)) != -1) {
                bytes.write(chunk, 0, count);
                if (bytes.size() > 64 * 1024) {
                    throw new IllegalArgumentException("Theme exceeds size limit");
                }
            }
            JSONObject data = new JSONObject(new String(
                    bytes.toByteArray(), StandardCharsets.UTF_8));
            if (data.getInt("schema") != 1 || !id.equals(data.getString("id"))) {
                throw new IllegalArgumentException("Unsupported or mismatched theme");
            }
            JSONObject colors = data.getJSONObject("colors");
            JSONArray corners = data.getJSONArray("corner_radii_dp");
            if (corners.length() != 4) throw new IllegalArgumentException("Bad radii");
            float[] r = new float[8];
            for (int i = 0; i < 4; i++) {
                float dp = (float) corners.getDouble(i);
                if (dp < 0 || dp > 96) throw new IllegalArgumentException("Unsafe radius");
                r[2 * i] = dp;
                r[2 * i + 1] = dp;
            }
            return new CamTheme(
                    Color.parseColor(colors.getString("background")),
                    Color.parseColor(colors.getString("foreground")),
                    Color.parseColor(colors.getString("accent")),
                    Color.parseColor(colors.getString("secondary")), r);
        } catch (Exception e) {
            // Fail to a known safe local palette instead of crashing camera UI.
            return new CamTheme(Color.rgb(6, 9, 22), Color.WHITE,
                    Color.rgb(24, 225, 238), Color.rgb(154, 76, 246),
                    new float[]{36,36,36,36,36,36,36,36});
        }
    }

    float[] radiiDp(Context context) {
        float[] result = new float[8];
        float density = context.getResources().getDisplayMetrics().density;
        for (int i = 0; i < 8; i++) result[i] = radii[i] * density;
        return result;
    }
}
