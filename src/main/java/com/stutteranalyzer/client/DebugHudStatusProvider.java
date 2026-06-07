package com.stutteranalyzer.client;

import com.stutteranalyzer.classifier.FreezeDetector;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.AnalyzerRuntimeState;
import com.stutteranalyzer.core.StutterCounter;
import com.stutteranalyzer.core.SubsystemHealth;
import com.stutteranalyzer.guard.EmergencyGuardManager;
import com.stutteranalyzer.report.FreezeEvent;
import com.stutteranalyzer.report.ReportWriter;
import net.minecraft.client.resources.language.I18n;

/**
 * Holds a cached snapshot of analyzer state for the F3 line.
 * Updated on client tick and on-demand from debug commands.
 */
public class DebugHudStatusProvider {

    public enum OverallStatus { ACTIVE, WARNING, ERROR, DISABLED }

    private static volatile String cachedLine = "SA: ACTIVE";
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
            return colored(I18n.get("stutteranalyzer.f3.disabled_config"), F3StatusFormatter.COLOR_GRAY);
        }

        OverallStatus status = computeStatus();

        if (status == OverallStatus.ERROR || status == OverallStatus.WARNING) {
            String degraded = SubsystemHealth.all().entrySet().stream()
                .filter(e -> e.getValue() != SubsystemHealth.Status.OK)
                .map(e -> e.getKey() + " " + e.getValue().name().toLowerCase())
                .findFirst().orElse("subsystem degraded");
            String color = status == OverallStatus.ERROR ? F3StatusFormatter.COLOR_RED : F3StatusFormatter.COLOR_YELLOW;
            String key   = status == OverallStatus.ERROR ? "stutteranalyzer.f3.status_error" : "stutteranalyzer.f3.status_warn";
            return colored(I18n.get(key, degraded), color);
        }

        if (!SAConfig.INSTANCE.debugHudCompactMode.get()) {
            return buildFull();
        }
        return buildCompact();
    }

    private static String buildCompact() {
        StringBuilder sb = new StringBuilder();
        sb.append(colored(I18n.get("stutteranalyzer.f3.active"), F3StatusFormatter.COLOR_GREEN));

        // Live stutter counters - always shown, read from AnalyzerRuntimeState + StutterCounter
        FreezeEvent lastClassified = FreezeDetector.lastFreezeEvent();
        int severeMs    = SAConfig.INSTANCE.severeFrameMs.get();
        int mediumMs    = SAConfig.INSTANCE.mediumFrameMs.get();
        int minorIn60   = StutterCounter.minorCountInSeconds(60);
        long worstMinor = StutterCounter.worstMinorInSeconds(60);
        int mediumIn60  = StutterCounter.mediumCountInSeconds(60);
        long worstMedium = StutterCounter.worstMediumInSeconds(60);

        if (lastClassified != null && lastClassified.durationMs() >= severeMs) {
            // Severe or extreme: show full event
            sb.append(colored(" | " + I18n.get("stutteranalyzer.f3.compact_last",
                lastClassified.category().name(), lastClassified.durationMs()), F3StatusFormatter.COLOR_YELLOW));
        } else if (mediumIn60 > 0) {
            // Medium stutters in last 60s
            sb.append(colored(" | " + I18n.get("stutteranalyzer.f3.compact_medium_count",
                mediumIn60, 60, worstMedium), F3StatusFormatter.COLOR_YELLOW));
        } else if (minorIn60 > 0) {
            // Minor stutters in last 60s
            sb.append(colored(" | " + I18n.get("stutteranalyzer.f3.compact_minor",
                minorIn60, 60, worstMinor), F3StatusFormatter.COLOR_GRAY));
        } else {
            sb.append(colored(" | " + I18n.get("stutteranalyzer.f3.compact_no_freeze"), F3StatusFormatter.COLOR_GRAY));
        }

        if (SAConfig.INSTANCE.debugHudShowReportCount.get()) {
            sb.append(colored(" | " + I18n.get("stutteranalyzer.f3.compact_reports",
                ReportWriter.savedReports()), F3StatusFormatter.COLOR_GRAY));
        }

        // Cloudflare upload status
        String cfEndpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        if (!cfEndpoint.isBlank() && "cloudflare".equalsIgnoreCase(SAConfig.INSTANCE.submissionTarget.get())) {
            sb.append(colored(" | " + I18n.get("stutteranalyzer.f3.upload_ready"), F3StatusFormatter.COLOR_AQUA));
        }

        if (SAConfig.INSTANCE.debugHudShowEmergencyMode.get() && SAConfig.INSTANCE.guardEmergencyMode.get()) {
            long activeGuards = EmergencyGuardManager.allGuards().stream()
                .filter(g -> EmergencyGuardManager.isEnabled(g.patternId())).count();
            sb.append(colored(" | " + I18n.get("stutteranalyzer.f3.emergency_on", activeGuards), F3StatusFormatter.COLOR_AQUA));
        }

        return sb.toString();
    }

    private static String buildFull() {
        FreezeEvent last = FreezeDetector.lastFreezeEvent();
        if (last != null) {
            return colored(I18n.get("stutteranalyzer.f3.active_full_last", last.category().name(), last.durationMs()), F3StatusFormatter.COLOR_YELLOW);
        }
        return colored(I18n.get("stutteranalyzer.f3.active_full_no_freeze", ReportWriter.savedReports()), F3StatusFormatter.COLOR_GREEN);
    }

    private static String colored(String text, String code) {
        return SAConfig.INSTANCE.debugHudShowColored.get() ? code + text + F3StatusFormatter.COLOR_RESET : text;
    }
}
