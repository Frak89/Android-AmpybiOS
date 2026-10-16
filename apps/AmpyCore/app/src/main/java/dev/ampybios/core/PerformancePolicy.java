package dev.ampybios.core;

/**
 * Pure Java adaptive UI rendering policy.
 *
 * No system permissions, no kernel writes, no clock/governor tweaks. The policy
 * limits only animations owned by AmpyCore. Hardware thermal protection stays
 * entirely under Android control.
 */
public final class PerformancePolicy {
    public static final int ECO = 0;
    public static final int BALANCED = 1;
    public static final int FUSION_EXTREME = 2;

    private PerformancePolicy() {}

    public static final class Budget {
        public final int requestedProfile;
        public final int effectiveProfile;
        public final int targetFps;
        public final int maxGlowPercent;
        public final int maxRadarSpeed;
        public final boolean forceReducedMotion;
        public final String reason;

        private Budget(int requested, int effective, int fps, int glow, int speed,
                       boolean reduce, String message) {
            requestedProfile = requested;
            effectiveProfile = effective;
            targetFps = fps;
            maxGlowPercent = glow;
            maxRadarSpeed = speed;
            forceReducedMotion = reduce;
            reason = message;
        }
    }

    public static Budget resolve(int requested, int thermalStatus, boolean powerSaver,
                                 int batteryPercent, boolean charging) {
        int selected = Math.max(ECO, Math.min(FUSION_EXTREME, requested));
        int effective = selected;
        String reason = "Profilo selezionato";

        // Android thermal status: NONE=0, LIGHT=1, MODERATE=2,
        // SEVERE=3, CRITICAL=4, EMERGENCY=5, SHUTDOWN=6.
        if (thermalStatus >= 3) {
            effective = ECO;
            reason = "Protezione termica";
        } else if (thermalStatus >= 2 && effective > BALANCED) {
            effective = BALANCED;
            reason = "Temperatura elevata";
        }
        if (powerSaver && effective > ECO) {
            effective = ECO;
            reason = "Risparmio energetico Android";
        }
        if (batteryPercent >= 0 && batteryPercent < 15 && !charging && effective > ECO) {
            effective = ECO;
            reason = "Batteria sotto il 15%";
        }
        int fps = effective == ECO ? 30 : (effective == BALANCED ? 60 : 120);
        int glow = effective == ECO ? 15 : (effective == BALANCED ? 40 : 65);
        int speed = effective == ECO ? 45 : (effective == BALANCED ? 180 : 360);
        boolean reduced = thermalStatus >= 4;
        if (reduced) reason = "Temperatura critica: animazioni ridotte";
        return new Budget(selected, effective, fps, glow, speed, reduced, reason);
    }

    public static String name(int profile) {
        switch (profile) {
            case ECO: return "Eco";
            case BALANCED: return "Balanced";
            case FUSION_EXTREME: return "Fusion Extreme";
            default: return "Balanced";
        }
    }
}
