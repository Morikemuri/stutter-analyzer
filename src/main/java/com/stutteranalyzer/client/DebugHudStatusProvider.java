package com.stutteranalyzer.client;

import com.stutteranalyzer.classifier.FreezeDetector;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.SubsystemHealth;
import com.stutteranalyzer.guard.EmergencyGuardManager;
import com.stutteranalyzer.report.FreezeEvent;
import com.stutteranalyzer.report.ReportWriter;

/**
 * Holds a cached snapshot of analyzer state for the F3 line.
 * Updated on client tick, read every frame - no expensive work here.
 * Cache this. Reflection is expensive and dramatic.
 */
public class DebugHudStatusProvider {

    public enum OverallStatus { ACTIVE, WARNING, ERROR, DISABLED }

    private static volatile String cachedLine = buildLine();
    private static volatile OverallStatus cachedStatus = OverallStatus.ACTIVE;
    private static volatile boolean f3EnabledOverride = true;

    public static void refresh() {
        cachedStatus = computeStatus();
        cachedLine   = buildLine();
    }

    public static String cachedLine() { return cachedLine; }
    public static OverallStatus cachedStatus() { return cachedStatus; }
    public static boolean isF3Enabled() { return f3EnabledOverride && SAConfig.INSTANCE.debugHudEnabled.get(); }
    public static void setF3Enabled(boolean v) { f3EnabledOverride = v; }

    private static OverallStatus computeStatus() {
        if (!SAConfig.INSTANCE.enabled.get()) return OverallStatus.DISABLED;
        if (SubsystemHealth.all().values().stream().anyMatch(s -> s == SubsystemHealth.Status.FAILED)) return OverallStatus.ERROR;
        if (SubsystemHealth.anyDegraded()) return OverallStatus.WARNING;
        return OverallStatus.ACTIVE;
    }

    private static String buildLine() {
        if (!SAConfig.INSTANCE.enabled.get()) {
            return colored("SA: DISABLED | Config disabled", F3StatusFormatter.COLOR_GRAY);
        }

        OverallStatus status = computeStatus();

        if (status == OverallStatus.ERROR || status == OverallStatus.WARNING) {
            String degraded = SubsystemHealth.all().entrySet().stream()
                .filter(e -> e.getValue() != SubsystemHealth.Status.OK)
                .map(e -> e.getKey() + " " + e.getValue().name().toLowerCase())
                .findFirst().orElse("subsystem degraded");
            String color = status == OverallStatus.ERROR ? F3StatusFormatter.COLOR_RED : F3StatusFormatter.COLOR_YELLOW;
            String statusLabel = status == OverallStatus.ERROR ? "ERROR" : "WARNING";
            return colored("SA: " + statusLabel + " | " + degraded + " | Use /sa health", color);
        }

        if (!SAConfig.INSTANCE.debugHudCompactMode.get()) {
            return buildFull();
        }
        return buildCompact();
    }

    private static String buildCompact() {
        StringBuilder sb = new StringBuilder();
        sb.append(colored("SA: ACTIVE", F3StatusFormatter.COLOR_GREEN));

        if (SAConfig.INSTANCE.debugHudShowLastFreeze.get()) {
            FreezeEvent last = FreezeDetector.lastFreezeEvent();
            if (last != null) {
                sb.append(colored(" | Last: " + last.category().name() + " " + last.durationMs() + "ms", F3StatusFormatter.COLOR_YELLOW));
            } else {
                sb.append(colored(" | Last: none", F3StatusFormatter.COLOR_GRAY));
            }
        }

        if (SAConfig.INSTANCE.debugHudShowReportCount.get()) {
            sb.append(colored(" | Reports: " + ReportWriter.savedReports(), F3StatusFormatter.COLOR_GRAY));
        }

        if (SAConfig.INSTANCE.debugHudShowEmergencyMode.get() && SAConfig.INSTANCE.guardEmergencyMode.get()) {
            long activeGuards = EmergencyGuardManager.allGuards().stream()
                .filter(g -> EmergencyGuardManager.isEnabled(g.patternId())).count();
            sb.append(colored(" | Emergency Mode: ON | Safe Guards: " + activeGuards, F3StatusFormatter.COLOR_AQUA));
        }

        return sb.toString();
    }

    private static String buildFull() {
        FreezeEvent last = FreezeDetector.lastFreezeEvent();
        if (last != null) {
            return colored("SA: ACTIVE | Last: " + last.category().name() + " " + last.durationMs() + " ms | Report saved", F3StatusFormatter.COLOR_YELLOW);
        }
        return colored("SA: ACTIVE | FPS watch: ON | Last freeze: none | Reports: " + ReportWriter.savedReports(), F3StatusFormatter.COLOR_GREEN);
    }

    private static String colored(String text, String code) {
        return SAConfig.INSTANCE.debugHudShowColored.get() ? code + text + F3StatusFormatter.COLOR_RESET : text;
    }
}
