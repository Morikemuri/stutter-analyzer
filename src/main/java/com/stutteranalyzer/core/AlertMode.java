package com.stutteranalyzer.core;

public enum AlertMode {
    OFF, MINOR, MEDIUM, SEVERE, EXTREME;

    public static AlertMode fromString(String s) {
        if (s == null) return SEVERE;
        try { return valueOf(s.toUpperCase()); } catch (IllegalArgumentException e) { return SEVERE; }
    }

    /** True if a stutter of durationMs should produce a direct chat alert in this mode. */
    public boolean shouldAlertDirect(long durationMs, int mediumMs, int severeMs, int extremeMs) {
        if (this == OFF) return false;
        if (durationMs >= extremeMs) return true;
        if (durationMs >= severeMs)  return this != EXTREME;
        if (durationMs >= mediumMs)  return this == MINOR || this == MEDIUM;
        return this == MINOR;
    }

    /** Human-readable description of what this mode alerts on. */
    public String alertsOnDescription(int mediumMs, int severeMs, int extremeMs) {
        return switch (this) {
            case OFF     -> "none";
            case EXTREME -> "extreme (" + extremeMs + "ms+)";
            case SEVERE  -> "severe, extreme";
            case MEDIUM  -> "medium, severe, extreme";
            case MINOR   -> "minor, medium, severe, extreme";
        };
    }
}
