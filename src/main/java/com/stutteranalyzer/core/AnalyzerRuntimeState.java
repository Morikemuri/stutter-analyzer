package com.stutteranalyzer.core;

/**
 * Single source of truth for live stutter tracking state.
 * Updated before any rate-limit gates so F3, /sa status, and debug commands all see the same data.
 * Thread-safe via volatile fields. No heavy dependencies.
 */
public class AnalyzerRuntimeState {

    private static volatile long lastStutterDurationMs = 0;
    private static volatile String lastStutterSeverity = "none";
    private static volatile long lastStutterTimestamp = 0;
    private static volatile boolean f3RefreshRequested = false;

    /** Called every time any stutter is detected (real or injected). Always call before rate-limit gates. */
    public static void recordStutter(long durationMs, String severity) {
        lastStutterDurationMs = durationMs;
        lastStutterSeverity = severity;
        lastStutterTimestamp = System.currentTimeMillis();
        f3RefreshRequested = true;
    }

    /** Request an immediate F3 refresh on the next client tick. */
    public static void requestF3Refresh() {
        f3RefreshRequested = true;
    }

    /** Returns true (and clears flag) if an immediate F3 refresh was requested. */
    public static boolean consumeF3RefreshRequest() {
        if (f3RefreshRequested) {
            f3RefreshRequested = false;
            return true;
        }
        return false;
    }

    public static long lastStutterDurationMs() { return lastStutterDurationMs; }
    public static String lastStutterSeverity() { return lastStutterSeverity; }
    public static long lastStutterTimestamp() { return lastStutterTimestamp; }

    /** Returns milliseconds since last stutter, or -1 if none recorded. */
    public static long lastStutterAgeMs() {
        return lastStutterTimestamp > 0 ? System.currentTimeMillis() - lastStutterTimestamp : -1;
    }
}
