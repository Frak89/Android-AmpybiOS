package dev.ampybios.rec;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import java.util.ArrayList;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * AmpyRec is an APP-PRIVATE STAGING DIRECTORY, NOT an Android disk partition.
 * NEVER writes to /system, /vendor, /boot, recovery, BCB or inactive A/B slot.
 */
public final class RecState {
    public static final String SKIN_PERMISSION = "dev.ampybios.permission.SKIN_SYNC";
    public static final String THEME_AUTHORITY = "dev.ampybios.core.themes";
    public static final int JOB_ID = 2601701;
    private static final String STORE = "ampyrec.state";
    private static final Object LOCK = new Object();

    private RecState() {}

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    public static File area(Context context) {
        File area = new File(context.getFilesDir(), "AmpyRec");
        if (!area.exists() && !area.mkdirs()) {
            throw new IllegalStateException("Impossibile creare AmpyRec privato");
        }
        return area;
    }

    public static File staged(Context context) {
        return new File(area(context), "staged.zip");
    }

    /** Copy from SAF into a bounded private file; reject unrelated sources. */
    public static RecPackage.Report stage(Context context, android.net.Uri selected)
            throws Exception {
        synchronized (LOCK) {
            File tmp = new File(area(context), "incoming.tmp");
            File dest = staged(context);
            try {
                try (InputStream input = context.getContentResolver().openInputStream(selected);
                     FileOutputStream output = new FileOutputStream(tmp)) {
                    if (input == null) throw new IOException("Impossibile aprire lo ZIP");
                    byte[] chunk = new byte[32768];
                    long size = 0;
                    int n;
                    while ((n = input.read(chunk)) != -1) {
                        size += n;
                        if (size > RecPackage.MAX_ARCHIVE_BYTES)
                            throw new IOException("Pacchetto oltre 512 MiB: usa l'OTA firmato della ROM");
                        output.write(chunk, 0, n);
                    }
                    output.getFD().sync();
                }
                RecPackage.Report report = RecPackage.inspect(tmp);
                // Arbitrary source ZIP and unsigned system OTA are read-only analysis.
                if (report.kind != RecPackage.Kind.LIVE_THEME) {
                    // A newly selected non-installable archive invalidates old
                    // pending state: the UI must never offer applying an older ZIP.
                    cancelJob(context);
                    prefs(context).edit().remove("state").remove("hash")
                            .remove("kind").remove("message").commit();
                    if (dest.exists() && !dest.delete())
                        throw new IOException("Impossibile eliminare staging precedente");
                    return report;
                }
                // A previously scheduled job must not run against a replaced ZIP.
                cancelJob(context);
                try {
                    java.nio.file.Files.move(tmp.toPath(),dest.toPath(),
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException noAtomic) {
                    // Same-private-directory fallback with rollback of prior ZIP.
                    File backup = new File(area(context), "staged.backup");
                    if (backup.exists() && !backup.delete())
                        throw new IOException("Backup staging non cancellabile");
                    if (dest.exists() && !dest.renameTo(backup))
                        throw new IOException("Backup staging non possibile");
                    if (!tmp.renameTo(dest)) {
                        if (backup.exists()) backup.renameTo(dest);
                        throw new IOException("Impossibile finalizzare staging");
                    }
                    backup.delete();
                }
                boolean persisted = prefs(context).edit()
                        .putString("mode", "disk")
                        .remove("source_uri")
                        .putString("state", "staged")
                        .putString("hash", report.zipSha256)
                        .putString("kind", report.kind.name())
                        .putString("message", report.description)
                        .commit();
                if (!persisted) throw new IOException("Impossibile salvare lo stato");
                return report;
            } finally {
                // Never retain unknown/executable/source ZIPs in private storage.
                if (tmp.exists()) tmp.delete();
            }
        }
    }

    public static String apply(Context context) throws Exception {
        return apply(context, null);
    }

    /** If background work belongs to an obsolete ZIP, it must never apply. */
    public static String apply(Context context, String expectedRevision) throws Exception {
        synchronized (LOCK) {
            SharedPreferences preferences = prefs(context);
            String state = preferences.getString("state", "");
            if (!"staged".equals(state) && !"deferred".equals(state) &&
                    !"background".equals(state))
                throw new IOException("Nessun aggiornamento LIVE approvato");
            String mode = preferences.getString("mode","disk");
            RecPackage.Report report;
            if ("reference".equals(mode)) {
                String source = preferences.getString("source_uri","");
                if (source.isEmpty()) throw new IOException("Riferimento originale assente");
                byte[] data = readRamBytes(context,Uri.parse(source));
                report = RamPackageScanner.inspect(data);
            } else if ("disk".equals(mode)) {
                report = RecPackage.inspect(staged(context));
            } else {
                throw new IOException("Modalità staging sconosciuta");
            }
            if (expectedRevision != null && !expectedRevision.equals(report.zipSha256))
                throw new IOException("Aggiornamento sostituito; vecchio job annullato");
            if (report.kind != RecPackage.Kind.LIVE_THEME ||
                    !report.zipSha256.equals(preferences.getString("hash","")))
                throw new IOException("Lo ZIP è cambiato dopo la verifica");
            File releases = new File(area(context), "releases");
            if (!releases.isDirectory() && !releases.mkdirs())
                throw new IOException("Area release non accessibile");
            File release = new File(releases, report.zipSha256);
            if (!release.isDirectory() && !release.mkdirs())
                throw new IOException("Impossibile preparare i temi");
            for (RecPackage.Theme theme : report.themes) {
                File target = new File(release, theme.id + ".json");
                File tmp = new File(release, theme.id + ".partial");
                try (FileOutputStream stream = new FileOutputStream(tmp)) {
                    stream.write(theme.content.getBytes(StandardCharsets.UTF_8));
                    stream.getFD().sync();
                }
                if (target.exists() && !target.delete())
                    throw new IOException("Impossibile sostituire tema");
                if (!tmp.renameTo(target)) throw new IOException("Commit tema fallito");
            }
            deliverToCore(context,report);
            // Acknowledgement is received from AmpyCore; only NOW mark applied.
            if (!preferences.edit().putString("active",report.zipSha256)
                    .putString("state","applied")
                    .putString("message","AmpyCore ha confermato i temi LIVE").commit())
                throw new IOException("Impossibile salvare lo stato confermato");
            if ("reference".equals(mode)) {
                releaseReference(context, preferences.getString("source_uri",""));
                preferences.edit().remove("source_uri").remove("mode").commit();
            } else {
                staged(context).delete();
            }
            return "AmpyCore ha confermato l'installazione di "+ids.size()+
                    " temi (revisione "+report.zipSha256.substring(0,12)+")";
        }
    }

    /**
     * Transient staging: zero filesystem writes by AmpyRec. Data lives only in
     * bounded app heap until validation + synchronous AmpyCore acknowledgement.
     * The receiver persists the selected active theme in ITS private app data.
     * No deferred/reboot semantics are offered for a memory-only import.
     */
    public static String applyDirectFromRam(Context context, android.net.Uri selected)
            throws Exception {
        RecPackage.Report report=RamPackageScanner.inspect(readRamBytes(context,selected));
        if (report.kind != RecPackage.Kind.LIVE_THEME)
            throw new IOException("Pacchetto non consentito per RAM LIVE");
        deliverToCore(context,report);
        return "Applicazione LIVE confermata da AmpyCore senza staging ZIP su disco ("
                +report.zipSha256.substring(0,12)+")";
    }

    private static byte[] readRamBytes(Context context, Uri source) throws IOException {
        ByteArrayOutputStream memory = new ByteArrayOutputStream();
        try (InputStream stream = context.getContentResolver().openInputStream(source)) {
            if (stream == null) throw new IOException("File originale non disponibile");
            byte[] chunk = new byte[8192];
            int count;
            while ((count=stream.read(chunk))!=-1) {
                if (memory.size()+count > RamPackageScanner.MAX_ZIP_BYTES)
                    throw new IOException("ZIP supera 256 KiB: RAM-only non disponibile");
                memory.write(chunk,0,count);
            }
        }
        return memory.toByteArray();
    }

    /**
     * Reboot-safe, zero-copy deferred staging. The user's original SAF document
     * must remain available; we store only its URI, read grant, and SHA-256.
     * No ZIP is copied to AmpyRec's private filesystem.
     */
    public static RecPackage.Report stageReference(Context context, Uri source)
            throws Exception {
        RecPackage.Report report = RamPackageScanner.inspect(readRamBytes(context,source));
        if (report.kind != RecPackage.Kind.LIVE_THEME)
            throw new IOException("Pacchetto non supportato per staging senza copia");
        synchronized (LOCK) {
            // Acquisition must precede storing a URI; failure is non-destructive.
            context.getContentResolver().takePersistableUriPermission(source,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            SharedPreferences preferences=prefs(context);
            String old=preferences.getString("source_uri","");
            cancelJob(context);
            if (!preferences.edit()
                    .putString("mode","reference")
                    .putString("source_uri",source.toString())
                    .putString("hash",report.zipSha256)
                    .putString("kind",report.kind.name())
                    .putString("state","staged")
                    .putString("message","Riferimento SAF persistente: nessuna copia ZIP")
                    .commit())
                throw new IOException("Impossibile conservare il riferimento SAF");
            if (!old.isEmpty() && !old.equals(source.toString()))
                releaseReference(context,old);
            // Older disk staging is no longer part of the active transaction.
            File oldZip=staged(context);
            if (oldZip.exists() && !oldZip.delete())
                throw new IOException("Impossibile eliminare ZIP duplicato precedente");
        }
        return report;
    }

    private static void releaseReference(Context context,String value) {
        if (value==null || value.isEmpty()) return;
        try {
            context.getContentResolver().releasePersistableUriPermission(
                    Uri.parse(value),Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // The permission may have been revoked by the document provider.
        }
    }

    /** Shared single IPC transaction; never reports success without confirmation. */
    private static void deliverToCore(Context context, RecPackage.Report report)
            throws Exception {
        // Never claim applied while AmpyCore is missing or signed differently.
        PackageManager packageManager=context.getPackageManager();
        if (packageManager.checkSignatures(context.getPackageName(),
            "dev.ampybios.core")!=PackageManager.SIGNATURE_MATCH)
            throw new SecurityException("AmpyCore assente o firma differente");
        ArrayList<String> ids=new ArrayList<>();
        ArrayList<String> json=new ArrayList<>();
        for (RecPackage.Theme theme:report.themes) {
            ids.add(theme.id);
            json.add(theme.content);
        }
        Bundle input=new Bundle();
        input.putString("revision",report.zipSha256);
        input.putStringArrayList("ids",ids);
        input.putStringArrayList("json",json);
        Bundle reply=context.getContentResolver().call(
            Uri.parse("content://"+THEME_AUTHORITY),"install-v1",null,input);
        if (reply==null || !reply.getBoolean("applied",false) ||
            !report.zipSha256.equals(reply.getString("revision","")) ||
            reply.getInt("count",-1)!=ids.size())
            throw new IOException("AmpyCore non ha confermato l'installazione");

    }

    public static void defer(Context context) {
        synchronized (LOCK) {
            prefs(context).edit().putString("state","deferred").commit();
        }
    }

    public static String queuedRevision(Context context) {
        synchronized (LOCK) {
            SharedPreferences preferences=prefs(context);
            if (!"background".equals(preferences.getString("state","")))
                return null;
            return preferences.getString("hash",null);
        }
    }

    /** Do not resurrect a cancelled job or overwrite the state of a newer ZIP. */
    public static void markDeferredIfCurrent(Context context, String revision,
                                             String reason) {
        synchronized (LOCK) {
            SharedPreferences preferences=prefs(context);
            if (revision != null && revision.equals(preferences.getString("hash","")) &&
                    "background".equals(preferences.getString("state",""))) {
                preferences.edit().putString("state","deferred")
                        .putString("message", reason).commit();
            }
        }
    }

    public static boolean scheduleBackground(Context context) {
        synchronized (LOCK) {
            SharedPreferences preferences=prefs(context);
            if (!"staged".equals(preferences.getString("state","")) &&
                    !"deferred".equals(preferences.getString("state","")))
                return false;
            JobScheduler jobs=context.getSystemService(JobScheduler.class);
            if (jobs==null) return false;
            JobInfo task=new JobInfo.Builder(JOB_ID,
                    new ComponentName(context,LiveApplyJob.class))
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .setPersisted(true)
                    .build();
            // Commit BEFORE scheduling: the system may launch the job promptly.
            String previous=preferences.getString("state","staged");
            if (!preferences.edit().putString("state","background").commit())
                return false;
            if (jobs.schedule(task)!=JobScheduler.RESULT_SUCCESS) {
                preferences.edit().putString("state",previous).commit();
                return false;
            }
            return true;
        }
    }

    /** Cancel only under the same lock used by stage/apply. */
    public static void cancelPending(Context context) {
        synchronized (LOCK) {
            cancelJob(context);
            SharedPreferences preferences=prefs(context);
            String old=preferences.getString("source_uri","");
            preferences.edit().remove("state").remove("hash")
                    .remove("kind").remove("message").remove("mode")
                    .remove("source_uri").commit();
            staged(context).delete();
            releaseReference(context,old);
        }
    }

    public static void cancelJob(Context context) {
        JobScheduler jobs=context.getSystemService(JobScheduler.class);
        if (jobs!=null) jobs.cancel(JOB_ID);
    }
}
