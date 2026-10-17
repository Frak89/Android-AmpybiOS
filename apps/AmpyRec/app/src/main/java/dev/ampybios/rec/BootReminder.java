package dev.ampybios.rec;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * No boot-time Activity launches (restricted by Android).
 * If notification permission was granted, show an opt-in reminder on reboot.
 */
public final class BootReminder extends BroadcastReceiver {
    static final String CHANNEL="ampyrec_pending";
    static final int NOTIFICATION_ID=17017;

    @Override public void onReceive(Context context, Intent intent) {
        if (intent==null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        String state=RecState.prefs(context).getString("state","");
        if ("background".equals(state)) {
            // Recover if Android restored app data without a persisted job.
            android.app.job.JobScheduler scheduler=
                    context.getSystemService(android.app.job.JobScheduler.class);
            if (scheduler != null && scheduler.getPendingJob(RecState.JOB_ID)!=null)
                return;
            RecState.markDeferredIfCurrent(context,
                    RecState.prefs(context).getString("hash",null),
                    "Job di background assente dopo il riavvio; conferma necessaria");
            state=RecState.prefs(context).getString("state","");
        }
        if (!"deferred".equals(state) && !"staged".equals(state)) return;
        notifyUser(context);
    }
    static void notifyUser(Context context) {
        if (Build.VERSION.SDK_INT>=33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                        PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if (manager==null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                "AmpyRec: aggiornamenti in attesa",
                NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(context,RecActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending=PendingIntent.getActivity(context,0,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification alert=new Notification.Builder(context,CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("AmpyRec — tema LIVE pronto")
                .setContentText("Tocca: applica ora, rimanda o esegui in background")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();
        manager.notify(NOTIFICATION_ID,alert);
    }
    static void clear(Context context) {
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if (manager!=null) manager.cancel(NOTIFICATION_ID);
    }
}
