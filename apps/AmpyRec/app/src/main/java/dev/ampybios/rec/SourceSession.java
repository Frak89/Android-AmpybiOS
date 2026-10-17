package dev.ampybios.rec;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.IOException;

/**
 * Purely userspace source-archive reference for an AmpyLauncher HOME session.
 * NO on-device compilation, init replacement, chroot, boot image or APK loading.
 * The archive remains in Downloads, with a persisted SAF read grant.
 */
public final class SourceSession {
    private static final String PREFS = "ampyrec.source.v1";
    private SourceSession() {}

    public static SharedPreferences settings(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static SourceZipInspector.Result select(Context ctx, Uri document) throws Exception {
        SourceZipInspector.Result inspected = SourceZipInspector.inspect(ctx, document);
        if (!inspected.ampybiosSource || inspected.containsOtaPayload)
            throw new IOException("Lo ZIP non contiene sorgenti AmpybiOS riconoscibili e coerenti");
        // Keep source on its user's location. Do not duplicate it.
        ctx.getContentResolver().takePersistableUriPermission(
                document, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        SharedPreferences prefs = settings(ctx);
        String previous = prefs.getString("uri","");
        boolean committed = prefs.edit()
                .putString("uri",document.toString())
                .putString("sha256",inspected.sha256)
                .putInt("entries",inspected.count)
                .putLong("bytes",inspected.compressedBytes)
                .putBoolean("approved",true)
                .commit();
        if (!committed) {
            if (!document.toString().equals(previous)) release(ctx,document.toString());
            throw new IOException("Impossibile salvare il riferimento al sorgente");
        }
        if (!previous.isEmpty() && !previous.equals(document.toString()))
            release(ctx, previous);
        return inspected;
    }

    public static SourceZipInspector.Result recheck(Context ctx) throws Exception {
        SharedPreferences prefs = settings(ctx);
        String selected = prefs.getString("uri","");
        if (!prefs.getBoolean("approved",false) || selected.isEmpty())
            throw new IOException("Nessuno ZIP AmpybiOS selezionato");
        SourceZipInspector.Result result = SourceZipInspector.inspect(
                ctx,Uri.parse(selected));
        if (!result.ampybiosSource || result.containsOtaPayload ||
                !result.sha256.equals(prefs.getString("sha256","")))
            throw new IOException("Lo ZIP sorgente è stato modificato o non è più valido");
        return result;
    }

    public static boolean isSelected(Context ctx) {
        SharedPreferences prefs = settings(ctx);
        return prefs.getBoolean("approved",false) &&
                !prefs.getString("uri","").isEmpty();
    }

    public static void clear(Context ctx) {
        SharedPreferences prefs = settings(ctx);
        String current = prefs.getString("uri","");
        prefs.edit().clear().commit();
        release(ctx,current);
    }

    private static void release(Context ctx,String value) {
        if(value==null || value.isEmpty())return;
        try {
            ctx.getContentResolver().releasePersistableUriPermission(
                    Uri.parse(value),Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch(SecurityException ignored) {
            // User or provider has already revoked this permission.
        }
    }
}
