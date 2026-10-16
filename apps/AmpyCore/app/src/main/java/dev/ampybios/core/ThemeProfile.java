package dev.ampybios.core;

import android.content.res.Resources;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

final class ThemeProfile {
    final String id;
    final String label;
    final int background;
    final int surface;
    final int foreground;
    final int accent;
    final int secondary;
    final int radar;
    final float[] radii; // TL, TR, BR, BL pairs in physical pixels.
    final int glowDefault;
    final int speedDefault;
    final boolean radarDefault;
    final boolean reducedMotion;

    private ThemeProfile(String id, String label, int background, int surface, int foreground,
                         int accent, int secondary, int radar, float[] radii, int glow,
                         int speed, boolean radarDefault, boolean reducedMotion) {
        this.id = id; this.label = label; this.background = background; this.surface = surface;
        this.foreground = foreground; this.accent = accent; this.secondary = secondary;
        this.radar = radar; this.radii = radii;
        this.glowDefault = glow; this.speedDefault = speed;
        this.radarDefault = radarDefault; this.reducedMotion = reducedMotion;
    }

    static ThemeProfile parse(JSONObject json, Resources resources) throws Exception {
        JSONObject c = json.getJSONObject("colors");
        JSONObject e = json.getJSONObject("effects");
        JSONArray corners = json.getJSONArray("corner_radii_dp");
        if (corners.length() != 4) throw new IllegalArgumentException("Exactly 4 radii required");
        float density = resources.getDisplayMetrics().density;
        float[] radii = new float[8];
        for (int i = 0; i < 4; i++) {
            float r = (float) corners.getDouble(i);
            if (r < 0 || r > 200) throw new IllegalArgumentException("Corner radius out of range");
            radii[2*i] = r * density;
            radii[2*i+1] = r * density;
        }
        return new ThemeProfile(
                json.getString("id"), json.getString("label"),
                Color.parseColor(c.getString("background")),
                Color.parseColor(c.getString("surface")),
                Color.parseColor(c.getString("foreground")),
                Color.parseColor(c.getString("accent")),
                Color.parseColor(c.getString("secondary")),
                Color.parseColor(c.getString("radar")),
                radii,
                Math.round((float) e.getDouble("glow_alpha") * 100),
                e.getInt("radar_speed_dps"),
                e.getBoolean("radar_border"),
                e.getBoolean("reduced_motion"));
    }
}
