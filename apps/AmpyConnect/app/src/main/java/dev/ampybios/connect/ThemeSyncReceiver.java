package dev.ampybios.connect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Explicit, signature-protected AmpyCore skin updates (no exported service). */
public final class ThemeSyncReceiver extends BroadcastReceiver {
    private static final String ACTION = "dev.ampybios.action.SKIN_CHANGED";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        String theme = intent.getStringExtra("theme");
        if (!"fusion".equals(theme) && !"cyberpunk".equals(theme)
                && !"futuristic".equals(theme) && !"minimal".equals(theme)) {
            return;
        }
        SharedPreferences preferences = context.getSharedPreferences(
                "ampyconnect.theme", Context.MODE_PRIVATE);
        if (preferences.getBoolean("follow_core", true)) {
            preferences.edit().putString("theme", theme).apply();
        }
    }
}
