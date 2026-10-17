package dev.ampybios.rec;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * AmpyRec is an APP-PRIVATE STAGING DIRECTORY, NOT an Android disk partition.
 * NEVER writes to /system, /vendor, /boot, recovery, BCB or inactive A/B slot.
 */
public final class RecState {
    public static final String SKIN_PERMISSION = "dev.ampybios.permission.SKIN_SYNC";
    public static final String IMPORT_ACTION = "dev.ampybios.action.IMPORT_THEME_JSON";
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
                if (report.kind != RecPackage.Kind.LIVE_THEME) return report;
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
        synchronized (LOCK) {
            SharedPreferences preferences = prefs(context);
            String state = preferences.getString("state", "");
            if (!"staged".equals(state) && !"deferred".equals(state) &&
                    !"background".equals(state))
                throw new IOException("Nessun aggiornamento LIVE approvato");
            RecPackage.Report report = RecPackage.inspect(staged(context));
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
            // The pointer is committed only after all theme assets are written.
            if (!preferences.edit().putString("active",report.zipSha256)
                    .putString("state","applied")
                    .putString("message","Temi LIVE installati localmente").commit())
                throw new IOException("Impossibile attivare il pacchetto");

            // Signature-protected IPC: cooperating AmpyCore imports JSON into
            // ITS OWN app-private storage; no system property/partition is changed.
            for (RecPackage.Theme theme : report.themes) {
                Intent event = new Intent(IMPORT_ACTION);
                event.setPackage("dev.ampybios.core");
                event.putExtra("id", theme.id);
                event.putExtra("json", theme.content);
                context.sendBroadcast(event, SKIN_PERMISSION);
            }
            staged(context).delete(); // Keep only activated JSON data, not stale ZIP.
            return "Applicati "+report.themes.size()+
                    " temi nell'area privata AmpyRec; inviati ad AmpyCore se firmato con la stessa chiave.";
        }
    }

    public static void defer(Context context) {
        prefs(context).edit().putString("state","deferred").apply();
    }

    public static boolean scheduleBackground(Context context) {
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
        if (jobs.schedule(task)!=JobScheduler.RESULT_SUCCESS) return false;
        preferences.edit().putString("state","background").apply();
        return true;
    }

    /** Cancel only under the same lock used by stage/apply. */
    public static void cancelPending(Context context) {
        synchronized (LOCK) {
            cancelJob(context);
            prefs(context).edit().remove("state").remove("hash")
                    .remove("kind").remove("message").commit();
            staged(context).delete();
        }
    }

    public static void cancelJob(Context context) {
        JobScheduler jobs=context.getSystemService(JobScheduler.class);
        if (jobs!=null) jobs.cancel(JOB_ID);
    }
}
