package dev.ampybios.rec;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Android decides when constrained, reboot-persistent LIVE work is run. */
public final class LiveApplyJob extends JobService {
    private volatile Thread worker;

    @Override public boolean onStartJob(JobParameters params) {
        worker=new Thread(() -> {
            try {
                RecState.apply(getApplicationContext());
            } catch (Exception error) {
                RecState.prefs(this).edit()
                        .putString("state","deferred")
                        .putString("message","Installazione rinviata: "+
                                error.getClass().getSimpleName()).apply();
            } finally {
                jobFinished(params,false);
            }
        },"AmpyRec-LiveApply");
        worker.start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        // Package installation is short and guarded by a process-local lock;
        // do not retry automatically while a stopped job can still be running.
        return false;
    }
}
