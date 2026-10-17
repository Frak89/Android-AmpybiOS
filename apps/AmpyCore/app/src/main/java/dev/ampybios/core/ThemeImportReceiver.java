package dev.ampybios.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Receiver guarded by the SIGNATURE permission declared in AmpyCore manifest.
 * Validation is repeated here: never trust even an already-checked ZIP.
 * No root, no arbitrary paths, no network, no writable SystemUI/OS files.
 */
public final class ThemeImportReceiver extends BroadcastReceiver {
    static final String ACTION = "dev.ampybios.action.IMPORT_THEME_JSON";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        String id = intent.getStringExtra("id");
        String content = intent.getStringExtra("json");
        if (id == null || content == null ||
                !id.matches("(fusion|cyberpunk|futuristic|minimal)") ||
                content.length() > 24 * 1024) return;
        try {
            JSONObject data = new JSONObject(content);
            if (data.getInt("schema")!=1 || !id.equals(data.getString("id"))) return;
            // ThemeProfile validates colors, shape dimensions and effect fields.
            ThemeProfile profile = ThemeProfile.parse(data, context.getResources());
            if (profile.glowDefault < 0 || profile.glowDefault > 100 ||
                    profile.speedDefault < 0 || profile.speedDefault > 360) return;
            File directory = new File(context.getFilesDir(), "AmpyRecThemes");
            if (!directory.isDirectory() && !directory.mkdirs()) return;
            File tmp = new File(directory, id + ".part");
            File target = new File(directory, id + ".json");
            try (FileOutputStream stream = new FileOutputStream(tmp)) {
                stream.write(content.getBytes(StandardCharsets.UTF_8));
                stream.getFD().sync();
            }
            if (target.exists() && !target.delete()) return;
            if (!tmp.renameTo(target)) return;
            SharedPreferences pref = context.getSharedPreferences(
                    "ampycore.local.v1", Context.MODE_PRIVATE);
            pref.edit().putInt("ampyrec.import_version",
                    pref.getInt("ampyrec.import_version",0)+1).apply();
        } catch (Exception invalid) {
            // Reject malformed or incompatible data; leave existing UI intact.
        }
    }
}
