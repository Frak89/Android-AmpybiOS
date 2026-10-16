package dev.ampybios.core;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Display;
import android.view.FrameMetrics;
import android.view.Window;

import java.util.Locale;

/**
 * Unprivileged on-device diagnostics. Reports system thermal / battery state
 * and *only this window's* rendered frame durations. No root or background
 * polling while AmpyCore is paused.
 */
final class PerformanceMonitor {
    interface Observer { void onSnapshot(Snapshot snapshot); }

    static final class Snapshot {
        final int thermal;
        final boolean batterySaver;
        final int batteryPercent;
        final boolean charging;
        final long availableMemoryMiB;
        final float refreshRate;
        final int measuredFrames;
        final float meanFrameMs;
        final float slowFramePercent;

        Snapshot(int thermal, boolean saver, int battery, boolean charging,
                 long memory, float hz, int frames, float mean, float slow) {
            this.thermal = thermal;
            this.batterySaver = saver;
            this.batteryPercent = battery;
            this.charging = charging;
            this.availableMemoryMiB = memory;
            this.refreshRate = hz;
            this.measuredFrames = frames;
            this.meanFrameMs = mean;
            this.slowFramePercent = slow;
        }

        String describe() {
            String frames = measuredFrames > 0
                    ? String.format(Locale.ITALIAN, "Frame finestra: %.1f ms media · %.0f%% oltre budget",
                            meanFrameMs, slowFramePercent)
                    : "Frame finestra: nessun campione disponibile";
            return String.format(Locale.ITALIAN,
                    "Termico: %s · Batteria: %s · Risparmio: %s\n"
                  + "Display: %.0f Hz · RAM sistema disponibile: %d MiB\n%s",
                    thermalName(thermal),
                    batteryPercent < 0 ? "n/d" : batteryPercent + "%" + (charging ? " in carica" : ""),
                    batterySaver ? "attivo" : "non attivo",
                    refreshRate, availableMemoryMiB, frames);
        }

        private static String thermalName(int value) {
            switch (value) {
                case 0: return "normale";
                case 1: return "leggero";
                case 2: return "moderato";
                case 3: return "severo";
                case 4: return "critico";
                case 5: return "emergenza";
                case 6: return "arresto";
                default: return "non disponibile";
            }
        }
    }

    private final Activity activity;
    private final Observer observer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PowerManager powerManager;
    private final ActivityManager activityManager;
    private final PowerManager.OnThermalStatusChangedListener thermalListener;
    private final Window.OnFrameMetricsAvailableListener framesListener;
    private boolean active;
    private boolean thermalRegistered;
    private boolean framesRegistered;
    private int thermalStatus = -1;
    private int count, slow;
    private double durationSumMs;

    private final Runnable sampler = new Runnable() {
        @Override public void run() {
            if (!active) return;
            collect();
            handler.postDelayed(this, 3000);
        }
    };

    PerformanceMonitor(Activity activity, Observer observer) {
        this.activity = activity;
        this.observer = observer;
        powerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        activityManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        thermalListener = status -> {
            thermalStatus = status;
            if (active) collect();
        };
        framesListener = (window, metrics, dropCountSinceLastInvocation) -> {
            if (!active) return;
            long nanos = metrics.getMetric(FrameMetrics.TOTAL_DURATION);
            if (nanos <= 0) return;
            double ms = nanos / 1_000_000.0;
            count++;
            durationSumMs += ms;
            float hz = currentRefreshRate();
            if (ms > 1_000.0 / Math.max(30, hz) * 1.30) slow++;
        };
    }

    void start() {
        if (active) return;
        active = true;
        try {
            if (powerManager != null) {
                thermalStatus = powerManager.getCurrentThermalStatus();
                powerManager.addThermalStatusListener(activity.getMainExecutor(), thermalListener);
                thermalRegistered = true;
            }
        } catch (RuntimeException ignored) {
            thermalStatus = -1; // unavailable on some devices
        }
        try {
            activity.getWindow().addOnFrameMetricsAvailableListener(framesListener, handler);
            framesRegistered = true;
        } catch (RuntimeException ignored) {
            framesRegistered = false;
        }
        handler.post(sampler);
    }

    void stop() {
        if (!active) return;
        active = false;
        handler.removeCallbacks(sampler);
        if (thermalRegistered && powerManager != null) {
            powerManager.removeThermalStatusListener(thermalListener);
            thermalRegistered = false;
        }
        if (framesRegistered) {
            activity.getWindow().removeOnFrameMetricsAvailableListener(framesListener);
            framesRegistered = false;
        }
    }

    private float currentRefreshRate() {
        Display d = activity.getDisplay();
        return d == null ? 60f : Math.max(1f, d.getRefreshRate());
    }

    private void collect() {
        Intent state = activity.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int battery = -1;
        boolean charging = false;
        if (state != null) {
            int level = state.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = state.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (scale > 0 && level >= 0) battery = Math.round(100f * level / scale);
            int mode = state.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = mode == BatteryManager.BATTERY_STATUS_CHARGING
                    || mode == BatteryManager.BATTERY_STATUS_FULL;
        }
        long mem = 0L;
        if (activityManager != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(mi);
            mem = mi.availMem / (1024L * 1024L);
        }
        boolean saver = powerManager != null && powerManager.isPowerSaveMode();
        float hz = currentRefreshRate();
        int captured = count;
        float avg = captured > 0 ? (float) (durationSumMs / captured) : 0f;
        float slowPercent = captured > 0 ? 100f * slow / captured : 0f;
        count = slow = 0;
        durationSumMs = 0;
        observer.onSnapshot(new Snapshot(thermalStatus, saver, battery, charging,
                mem, hz, captured, avg, slowPercent));
    }
}
