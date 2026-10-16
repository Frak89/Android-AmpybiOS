package dev.ampybios.core;

public final class PerformancePolicyTest {
    private static int tests;
    private static void verify(boolean result, String message) {
        tests++;
        if (!result) throw new AssertionError(message);
    }
    public static void main(String[] ignored) {
        PerformancePolicy.Budget eco = PerformancePolicy.resolve(0, 0, false, 90, false);
        verify(eco.targetFps == 30 && eco.maxGlowPercent == 15 && eco.maxRadarSpeed == 45, "Eco");
        PerformancePolicy.Budget balanced = PerformancePolicy.resolve(1, 0, false, 90, false);
        verify(balanced.targetFps == 60 && balanced.maxGlowPercent == 40, "Balanced");
        PerformancePolicy.Budget fusion = PerformancePolicy.resolve(2, 0, false, 90, false);
        verify(fusion.targetFps == 120 && fusion.maxRadarSpeed == 360, "Fusion Extreme");
        verify(PerformancePolicy.resolve(2, 2, false, 80, false).effectiveProfile == 1,
                "Moderate thermal limits to Balanced");
        verify(PerformancePolicy.resolve(2, 3, false, 80, false).effectiveProfile == 0,
                "Severe thermal limits to Eco");
        PerformancePolicy.Budget critical = PerformancePolicy.resolve(2, 4, false, 80, false);
        verify(critical.effectiveProfile == 0 && critical.forceReducedMotion,
                "Critical thermal freezes optional motion");
        verify(PerformancePolicy.resolve(2, 0, true, 80, false).effectiveProfile == 0,
                "System battery saver has priority");
        verify(PerformancePolicy.resolve(2, 0, false, 14, false).effectiveProfile == 0,
                "Low battery limits to Eco");
        verify(PerformancePolicy.resolve(2, 0, false, 14, true).effectiveProfile == 2,
                "Battery guard not needed while charging");
        verify(PerformancePolicy.resolve(2, -1, false, -1, false).effectiveProfile == 2,
                "Unavailable battery and thermal readings keep selection");
        verify(PerformancePolicy.resolve(999, 0, false, 80, false).effectiveProfile == 2,
                "Clamp invalid profile index");
        verify("Balanced".equals(PerformancePolicy.name(1)), "Display name");
        System.out.println("OK: " + tests + " AmpyKernel performance policy checks");
    }
}
