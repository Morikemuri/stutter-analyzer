package com.stutteranalyzer.command;

import com.stutteranalyzer.StutterAnalyzerFabric;
import com.stutteranalyzer.classifier.FreezeCategory;
import com.stutteranalyzer.core.AlertManager;
import com.stutteranalyzer.core.AlertMode;
import com.stutteranalyzer.classifier.FreezeDetector;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.AnalyzerRuntimeState;
import com.stutteranalyzer.core.MetricsCollector;
import com.stutteranalyzer.core.StutterCounter;
import com.stutteranalyzer.core.SubsystemHealth;
import com.stutteranalyzer.core.QuietMode;
import com.stutteranalyzer.core.VerboseMode;
import com.stutteranalyzer.crash.CrashEvent;
import com.stutteranalyzer.crash.PreviousCrashImporter;
import com.stutteranalyzer.guard.EmergencyGuard;
import com.stutteranalyzer.guard.EmergencyGuardManager;
import com.stutteranalyzer.guard.EmergencyGuardReport;
import com.stutteranalyzer.knowledge.OptimizationModKnowledgeBase;
import com.stutteranalyzer.platform.SAFabricPlatform;
import com.stutteranalyzer.report.FreezeEvent;
import com.stutteranalyzer.report.FreezeReport;
import com.stutteranalyzer.report.ReportWriter;
import com.stutteranalyzer.submission.SubmissionManager;
import com.stutteranalyzer.update.UpdateCheckResult;
import com.stutteranalyzer.update.UpdateChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import com.stutteranalyzer.events.RecentEventBuffer;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommonCommandLogic {

    public static int showStatus(CommandSourceStack src) {
        FreezeEvent last = FreezeDetector.lastFreezeEvent();
        boolean isClient = SAFabricPlatform.isClient();
        boolean degraded = SubsystemHealth.anyDegraded();

        String countMode = SAConfig.INSTANCE.countMode.get();
        boolean useEpisodes = !"frames".equalsIgnoreCase(countMode);
        boolean showRaw     = SAConfig.INSTANCE.showRawFrameSpikeCount.get() || "both".equalsIgnoreCase(countMode);

        int minorEp   = StutterCounter.minorEpisodeCountInSeconds(60);
        int mediumEp  = StutterCounter.mediumEpisodeCountInSeconds(60);
        int severeEp  = StutterCounter.severeEpisodeCountInSeconds(60);
        int extremeEp = StutterCounter.extremeEpisodeCountInSeconds(60);
        int minorRaw   = StutterCounter.minorCountInSeconds(60);
        int mediumRaw  = StutterCounter.mediumCountInSeconds(60);
        long worstMinor   = StutterCounter.worstMinorInSeconds(60);
        long worstMedium  = StutterCounter.worstMediumInSeconds(60);
        long worstSevere  = StutterCounter.worstSevereInSeconds(60);
        long worstExtreme = StutterCounter.worstExtremeInSeconds(60);
        String lastSeverity = AnalyzerRuntimeState.lastStutterSeverity();
        long lastDurationMs = AnalyzerRuntimeState.lastStutterDurationMs();

        int minorDisplay   = useEpisodes ? minorEp   : minorRaw;
        int mediumDisplay  = useEpisodes ? mediumEp  : mediumRaw;
        int severeDisplay  = useEpisodes ? severeEp  : StutterCounter.severeCountInSeconds(60);
        int extremeDisplay = useEpisodes ? extremeEp : StutterCounter.extremeCountInSeconds(60);
        Component minorLabel   = Component.translatable(useEpisodes ? "stutteranalyzer.cmd.status.minor_episodes"   : "stutteranalyzer.cmd.status.minor_frames");
        Component mediumLabel  = Component.translatable(useEpisodes ? "stutteranalyzer.cmd.status.medium_episodes"  : "stutteranalyzer.cmd.status.medium_frames");
        Component severeLabel  = Component.translatable(useEpisodes ? "stutteranalyzer.cmd.status.severe_episodes"  : "stutteranalyzer.cmd.status.severe_frames");
        Component extremeLabel = Component.translatable(useEpisodes ? "stutteranalyzer.cmd.status.extreme_episodes" : "stutteranalyzer.cmd.status.extreme_frames");

        Component state = Component.translatable(degraded
            ? "stutteranalyzer.cmd.status.state_degraded"
            : "stutteranalyzer.cmd.status.state_active");
        String sideKey = isClient
            ? "stutteranalyzer.cmd.status.side.client_integrated"
            : "stutteranalyzer.cmd.status.side.dedicated";

        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.status.header")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.cmd.status.status_ui"), Component.literal("rich-v2")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.cmd.status.state"), state), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.cmd.status.side"), Component.translatable(sideKey)), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.client_tracker"),
            Component.translatable(isClient ? "stutteranalyzer.cmd.status.tracker_on" : "stutteranalyzer.cmd.status.tracker_unavailable")
        ), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.server_tracker"),
            Component.translatable("stutteranalyzer.cmd.status.tracker_on")
        ), false);

        if (lastDurationMs > 0) {
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.cmd.status.last_spike"),
                Component.literal(lastSeverity + " " + lastDurationMs + "ms")), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.cmd.status.last_spike"),
                Component.translatable("stutteranalyzer.cmd.status.last_spike_none")), false);
        }

        Component minorCountComp = worstMinor > 0
            ? Component.translatable("stutteranalyzer.cmd.status.count_in_60s_worst", minorDisplay, worstMinor)
            : Component.translatable("stutteranalyzer.cmd.status.count_in_60s", minorDisplay);
        src.sendSuccess(() -> CommandFeedback.row(minorLabel, minorCountComp), false);
        if (showRaw && useEpisodes) {
            Component rawMinorComp = Component.translatable("stutteranalyzer.cmd.status.count_in_60s", minorRaw);
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.cmd.status.minor_frames"), rawMinorComp), false);
        }
        Component mediumCountComp = worstMedium > 0
            ? Component.translatable("stutteranalyzer.cmd.status.count_in_60s_worst", mediumDisplay, worstMedium)
            : Component.translatable("stutteranalyzer.cmd.status.count_in_60s", mediumDisplay);
        src.sendSuccess(() -> CommandFeedback.row(mediumLabel, mediumCountComp), false);
        Component severeCountComp = worstSevere > 0
            ? Component.translatable("stutteranalyzer.cmd.status.count_in_60s_worst", severeDisplay, worstSevere)
            : Component.translatable("stutteranalyzer.cmd.status.count_in_60s", severeDisplay);
        src.sendSuccess(() -> CommandFeedback.row(severeLabel, severeCountComp), false);
        Component extremeCountComp = worstExtreme > 0
            ? Component.translatable("stutteranalyzer.cmd.status.count_in_60s_worst", extremeDisplay, worstExtreme)
            : Component.translatable("stutteranalyzer.cmd.status.count_in_60s", extremeDisplay);
        src.sendSuccess(() -> CommandFeedback.row(extremeLabel, extremeCountComp), false);

        int savedReports = ReportWriter.savedReports();
        boolean saveSevere  = SAConfig.INSTANCE.saveSevereStutterReports.get();
        boolean saveExtreme = SAConfig.INSTANCE.saveExtremeReports.get();
        boolean saveMedium  = SAConfig.INSTANCE.saveMediumStutterReports.get();
        boolean saveMinor   = SAConfig.INSTANCE.saveMinorStutterReports.get();
        boolean allSavingDisabled = !saveSevere && !saveExtreme && !saveMedium && !saveMinor;
        String reportsVal = allSavingDisabled ? "disabled by config" : String.valueOf(savedReports);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.row.reports_saved"),
            reportsVal), false);

        FreezeReport lastSaved = ReportWriter.lastReport();
        if (lastSaved != null) {
            FreezeEvent savedEvt = lastSaved.event;
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.cmd.status.last_report"),
                Component.literal(savedEvt.category().name() + " " + savedEvt.durationMs() + "ms")), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.cmd.status.last_report"),
                Component.translatable("stutteranalyzer.cmd.status.last_report_none")), false);
        }

        if (!allSavingDisabled) {
            boolean nothingTracked = last == null && lastDurationMs == 0
                && minorDisplay == 0 && mediumDisplay == 0 && severeDisplay == 0 && extremeDisplay == 0;
            if (nothingTracked) {
                src.sendSuccess(() -> CommandFeedback.info(
                    Component.translatable("stutteranalyzer.cmd.status.no_stutters")), false);
            } else if (savedReports == 0 && (severeDisplay > 0 || extremeDisplay > 0)) {
                src.sendSuccess(() -> CommandFeedback.warn(
                    Component.translatable("stutteranalyzer.cmd.status.reports_warn")), false);
            } else if (savedReports == 0 && (minorDisplay > 0 || mediumDisplay > 0)) {
                int severeMs = SAConfig.INSTANCE.severeFrameMs.get();
                src.sendSuccess(() -> CommandFeedback.info(
                    Component.translatable("stutteranalyzer.cmd.status.reports_silent", severeMs)), false);
            }
        }

        int crashCount = PreviousCrashImporter.allImported().size();
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.row.crashes_imported"),
            String.valueOf(crashCount)), false);

        boolean quiet = QuietMode.isEnabled();
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.quiet_mode"),
            Component.translatable(quiet ? "stutteranalyzer.cmd.status.quiet_on" : "stutteranalyzer.cmd.status.quiet_off")), false);
        long aggRemaining = StutterCounter.aggregateCooldownRemainingSeconds();
        if (aggRemaining > 0) {
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.cmd.status.agg_cooldown"),
                Component.translatable("stutteranalyzer.cmd.status.agg_cooldown_val", aggRemaining)), false);
        }

        boolean chatSevere = SAConfig.INSTANCE.chatNotifySevereStutters.get();
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.chat_severe"),
            Component.translatable(chatSevere ? "stutteranalyzer.verbose.on" : "stutteranalyzer.verbose.off")), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.verbose_mode"),
            Component.translatable(VerboseMode.isEnabled() ? "stutteranalyzer.verbose.on" : "stutteranalyzer.verbose.off")), false);

        String subTarget = SAConfig.INSTANCE.submissionTarget.get();
        Component subDisplay = "cloudflare".equalsIgnoreCase(subTarget)
            ? Component.translatable("stutteranalyzer.cmd.status.submission_cloudflare")
            : Component.translatable("stutteranalyzer.cmd.status.submission_local");
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.submission"), subDisplay), false);

        UpdateCheckResult updateResult = UpdateChecker.getCached();
        Component updateDisplay;
        if (!SAConfig.INSTANCE.checkForUpdates.get()) {
            updateDisplay = Component.translatable("stutteranalyzer.cmd.status.update.disabled");
        } else if (updateResult == null) {
            updateDisplay = Component.translatable("stutteranalyzer.cmd.status.update.not_checked");
        } else if (!updateResult.success()) {
            updateDisplay = Component.translatable("stutteranalyzer.cmd.status.update.unavailable");
        } else if (updateResult.updateAvailable()) {
            updateDisplay = Component.translatable("stutteranalyzer.cmd.status.update.available", updateResult.latestVersion());
        } else {
            updateDisplay = Component.translatable("stutteranalyzer.cmd.status.update.up_to_date");
        }
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.update_check"), updateDisplay), false);

        if (degraded) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.cmd.status.degraded")), false);
        }
        return 1;
    }

    public static int showHealth(CommandSourceStack src) {
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.health.header")), false);
        for (Map.Entry<String, SubsystemHealth.Status> e : SubsystemHealth.all().entrySet()) {
            String note = SubsystemHealth.note(e.getKey());
            String statusKey = switch (e.getValue()) {
                case OK          -> "stutteranalyzer.health.status.ok";
                case DEGRADED    -> "stutteranalyzer.health.status.degraded";
                case DISABLED    -> "stutteranalyzer.health.status.disabled";
                case FAILED      -> "stutteranalyzer.health.status.failed";
                case UNAVAILABLE -> "stutteranalyzer.health.status.unavailable";
            };
            Component statusComp = note.isEmpty()
                ? Component.translatable(statusKey)
                : Component.translatable(statusKey).copy()
                    .append(Component.literal(", " + note));
            src.sendSuccess(() -> CommandFeedback.row(e.getKey(), statusComp), false);
        }
        return 1;
    }

    public static int showLast(CommandSourceStack src) {
        FreezeEvent last = FreezeDetector.lastFreezeEvent();
        if (last == null) {
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.last.none")), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.last.header")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.category"), last.category().name()), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.duration"), last.durationMs() + " ms"), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.side"), last.side()), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.confidence"), last.confidencePct() + "%"), false);
        FreezeReport rep = ReportWriter.lastReport();
        if (rep != null) {
            src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.report_id"), rep.reportId), false);
        }
        return 1;
    }

    public static int generateReport(CommandSourceStack src) {
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.report.generating")), false);
        FreezeReport rep = ReportWriter.lastReport();
        if (rep != null) src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.cmd.report.saved", rep.reportId)), false);
        else src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.cmd.report.no_data")), false);
        return 1;
    }

    public static int exportReport(CommandSourceStack src) {
        FreezeReport rep = ReportWriter.lastReport();
        if (rep == null) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.cmd.report.no_export")), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.report.export_path", rep.reportId)), false);
        src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.cmd.report.export_auto")), false);
        return 1;
    }

    public static int listReports(CommandSourceStack src) {
        FreezeReport last = ReportWriter.lastReport();
        if (last == null) {
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.report.none")), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.report.list_header", ReportWriter.savedReports())), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.latest"), last.reportId), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.report.list_location")), false);
        return 1;
    }

    public static int listUnknownReports(CommandSourceStack src) {
        FreezeReport last = ReportWriter.lastReport();
        if (last != null && last.event.category().name().equals("UNKNOWN_FREEZE")) {
            src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.latest_unknown"), last.reportId), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.report.no_unknown")), false);
        }
        return 1;
    }

    public static int showReport(CommandSourceStack src, String reportId) {
        FreezeReport last = ReportWriter.lastReport();
        if (last != null && last.reportId.equals(reportId)) {
            FreezeEvent e = last.event;
            src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.report.header", last.reportId)), false);
            src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.category"), e.category().name()), false);
            src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.duration"), e.durationMs() + " ms"), false);
            src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.side"), e.side()), false);
            src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.confidence"), e.confidencePct() + "%"), false);
            src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.reason"), e.reason()), false);
            if (!e.recommendation().isEmpty())
                src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.recommendation"), e.recommendation()), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.cmd.report.not_found", reportId)), false);
        }
        return 1;
    }

    public static int deleteReport(CommandSourceStack src, String reportId) {
        if (!CommandPermissionHelper.canDeleteReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.cmd.report.delete_note", reportId)), false);
        return 1;
    }

    public static int reloadConfig(CommandSourceStack src) {
        if (!CommandPermissionHelper.canReloadConfig(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.cmd.config.reloaded")), false);
        return 1;
    }

    public static int enableDebug(CommandSourceStack src) {
        if (!CommandPermissionHelper.canReloadConfig(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.cmd.config.debug_enable")), false);
        return 1;
    }

    public static int disableDebug(CommandSourceStack src) {
        if (!CommandPermissionHelper.canReloadConfig(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.cmd.config.debug_disable")), false);
        return 1;
    }

    public static int verboseOn(CommandSourceStack src) {
        VerboseMode.setEnabled(true);
        src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.verbose.enabled")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.verbose.enabled_hint")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.verbose.reports_unaffected")), false);
        return 1;
    }

    public static int verboseOff(CommandSourceStack src) {
        VerboseMode.setEnabled(false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.verbose.disabled")), false);
        return 1;
    }

    public static int verboseStatus(CommandSourceStack src) {
        boolean on = VerboseMode.isEnabled();
        boolean chatMinor  = SAConfig.INSTANCE.chatNotifyMinorStutters.get();
        boolean chatMedium = SAConfig.INSTANCE.chatNotifyMediumStutters.get();
        boolean chatSevere = SAConfig.INSTANCE.chatNotifySevereStutters.get();
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.verbose.status_header")), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.verbose.mode"),
            Component.translatable(on ? "stutteranalyzer.verbose.on" : "stutteranalyzer.verbose.off")
        ), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.verbose.minor_chat"),
            Component.translatable(chatMinor ? "stutteranalyzer.verbose.on" : "stutteranalyzer.verbose.off")
        ), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.verbose.medium_chat"),
            Component.translatable(chatMedium ? "stutteranalyzer.verbose.on" : "stutteranalyzer.verbose.off")
        ), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.verbose.severe_chat"),
            Component.translatable(chatSevere ? "stutteranalyzer.verbose.on" : "stutteranalyzer.verbose.off")
        ), false);
        return 1;
    }

    public static int debugTestMinor(CommandSourceStack src) {
        FreezeDetector.injectSilent(55);
        AnalyzerRuntimeState.requestF3Refresh();
        int minorIn60 = StutterCounter.minorCountInSeconds(60);
        long worstMinor = StutterCounter.worstMinorInSeconds(60);
        src.sendSuccess(() -> CommandFeedback.success("[SA] Debug test: injected minor stutter 55ms."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] F3/status counters should now show Minor: " + minorIn60 + "/60s | Worst: " + worstMinor + "ms"), false);
        if (!VerboseMode.isEnabled()) {
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.verbose.test_minor_hint")), false);
        }
        return 1;
    }

    public static int debugTestMedium(CommandSourceStack src) {
        FreezeDetector.injectSilent(150);
        AnalyzerRuntimeState.requestF3Refresh();
        int mediumIn60 = StutterCounter.mediumCountInSeconds(60);
        src.sendSuccess(() -> CommandFeedback.success("[SA] Debug test: injected medium stutter 150ms."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] F3/status counters should now show Medium: " + mediumIn60 + "/60s"), false);
        return 1;
    }

    public static int debugTestSevere(CommandSourceStack src) {
        boolean isClient = SAFabricPlatform.isClient();
        FreezeEvent testEvent = new FreezeEvent(
            FreezeCategory.UNKNOWN_FREEZE, 0.5,
            "Synthetic severe test (300ms)",
            "Artificially generated for testing",
            isClient ? "client" : "dedicated-server", 300L,
            MetricsCollector.eventBuffer().snapshot(),
            "This is a test. Use /sa submit to test submission."
        );
        FreezeDetector.injectForTesting(testEvent, MetricsCollector.eventBuffer());
        AnalyzerRuntimeState.requestF3Refresh();
        int reports = ReportWriter.savedReports();
        src.sendSuccess(() -> CommandFeedback.success("[SA] Debug test: injected severe freeze 300ms. Report saved."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] F3 should now show: UNKNOWN_FREEZE 300ms | Reports: " + reports), false);
        return 1;
    }

    public static int debugTestExtreme(CommandSourceStack src) {
        boolean isClient = SAFabricPlatform.isClient();
        FreezeEvent testEvent = new FreezeEvent(
            FreezeCategory.UNKNOWN_FREEZE, 0.5,
            "Synthetic extreme freeze test (1200ms)",
            "Artificially generated for testing",
            isClient ? "client" : "dedicated-server", 1200L,
            MetricsCollector.eventBuffer().snapshot(),
            "This is an extreme freeze test. Use /sa submit to test submission."
        );
        FreezeDetector.injectForTesting(testEvent, MetricsCollector.eventBuffer());
        AnalyzerRuntimeState.requestF3Refresh();
        src.sendSuccess(() -> CommandFeedback.success("[SA] Debug test: injected extreme freeze 1200ms. Report saved."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Use /sa last and /sa status to verify."), false);
        return 1;
    }

    public static int debugVisibilityTest(CommandSourceStack src) {
        FreezeDetector.injectSilent(55);
        FreezeDetector.injectSilent(150);
        boolean isClient = SAFabricPlatform.isClient();
        FreezeEvent severeEvent = new FreezeEvent(
            FreezeCategory.UNKNOWN_FREEZE, 0.5,
            "Visibility test severe (300ms)",
            "Generated by test",
            isClient ? "client" : "dedicated-server", 300L,
            MetricsCollector.eventBuffer().snapshot(),
            "Visibility test event."
        );
        FreezeDetector.injectForTesting(severeEvent, MetricsCollector.eventBuffer());
        AnalyzerRuntimeState.requestF3Refresh();

        int minorIn60  = StutterCounter.minorCountInSeconds(60);
        int mediumIn60 = StutterCounter.mediumCountInSeconds(60);
        int reports    = ReportWriter.savedReports();

        src.sendSuccess(() -> CommandFeedback.success("[SA] Visibility test complete."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Expected:"), false);
        src.sendSuccess(() -> CommandFeedback.info("- Minor counter: " + minorIn60 + " (should be >= 1)"), false);
        src.sendSuccess(() -> CommandFeedback.info("- Medium counter: " + mediumIn60 + " (should be >= 1)"), false);
        src.sendSuccess(() -> CommandFeedback.info("- Severe report saved (Reports: " + reports + " should be >= 1)"), false);
        src.sendSuccess(() -> CommandFeedback.info("- F3 should show UNKNOWN_FREEZE 300ms"), false);
        src.sendSuccess(() -> CommandFeedback.info("Use /sa status to verify all counters."), false);
        return 1;
    }

    public static int showVersion(CommandSourceStack src) {
        boolean cfEnabled = SubmissionManager.isCloudflareEnabled();
        Component submitDisplay = Component.translatable(cfEnabled ? "stutteranalyzer.version.submit_cloudflare" : "stutteranalyzer.version.submit_local");
        Component uploadDisplay = Component.translatable(cfEnabled ? "stutteranalyzer.version.upload_ready" : "stutteranalyzer.version.upload_local");
        String javaVersion = System.getProperty("java.version", "unknown");
        int javaMajor = 0;
        try { javaMajor = Integer.parseInt(javaVersion.contains(".") ? javaVersion.split("[._-]")[0].equals("1") ? javaVersion.split("[._-]")[1] : javaVersion.split("[._-]")[0] : javaVersion); } catch (Exception ignored) {}
        final String javaDisplay = javaMajor > 0 ? String.valueOf(javaMajor) : javaVersion;
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.version.header")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.version"), Component.literal(StutterAnalyzerFabric.MOD_VERSION)), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.minecraft"), Component.literal("1.20.4")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.loader"), Component.translatable("stutteranalyzer.version.loader.fabric")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.java"), Component.literal(javaDisplay)), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.status"), Component.translatable("stutteranalyzer.version.status_rc")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.features"), Component.translatable("stutteranalyzer.version.features")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.submit"), submitDisplay), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.upload"), uploadDisplay), false);
        return 1;
    }

    public static int showVersionDebug(CommandSourceStack src) {
        src.sendSuccess(() -> CommandFeedback.header("[SA] Version Debug Info"), false);
        src.sendSuccess(() -> CommandFeedback.row("Version", StutterAnalyzerFabric.MOD_VERSION), false);
        src.sendSuccess(() -> CommandFeedback.row("Build", StutterAnalyzerFabric.BUILD_DATE), false);
        src.sendSuccess(() -> CommandFeedback.row("Build ID", StutterAnalyzerFabric.BUILD_ID), false);
        src.sendSuccess(() -> CommandFeedback.row("Raw features", StutterAnalyzerFabric.BUILD_FEATURES), false);
        return 1;
    }

    public static int updateCheck(CommandSourceStack src) {
        if (!SAConfig.INSTANCE.checkForUpdates.get()) {
            src.sendSuccess(() -> CommandFeedback.info("[SA] Update checks are disabled."), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.info("[SA] Checking for updates asynchronously..."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Use /sa update status in a moment to see the result."), false);
        UpdateChecker.performCheckAsync();
        return 1;
    }

    public static int updateStatus(CommandSourceStack src) {
        if (!SAConfig.INSTANCE.checkForUpdates.get()) {
            src.sendSuccess(() -> CommandFeedback.info("[SA] Update checks are disabled."), false);
            return 1;
        }
        UpdateCheckResult result = UpdateChecker.getCached();
        if (result == null) {
            src.sendSuccess(() -> CommandFeedback.info("[SA] No update check result yet. Use /sa update check."), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.header("[SA] Update Status"), false);
        if (!result.success()) {
            src.sendSuccess(() -> CommandFeedback.row("Update check", "unavailable"), false);
            String reason = result.errorReason() != null ? result.errorReason() : "unknown error";
            src.sendSuccess(() -> CommandFeedback.row("Reason", reason), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.row("Current", StutterAnalyzerFabric.MOD_VERSION), false);
        src.sendSuccess(() -> CommandFeedback.row("Latest", result.latestVersion()), false);
        if (result.updateAvailable()) {
            src.sendSuccess(() -> CommandFeedback.row("Update", "available"), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] Use /sa update link for download info."), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.row("Update", "up to date"), false);
        }
        return 1;
    }

    public static int updateLink(CommandSourceStack src) {
        UpdateCheckResult result = UpdateChecker.getCached();
        String github = (result != null && result.githubPage() != null && !result.githubPage().isEmpty())
            ? result.githubPage()
            : SAConfig.INSTANCE.updateGithubPage.get();
        String curseforge = (result != null && result.curseforgeUrl() != null && !result.curseforgeUrl().isEmpty())
            ? result.curseforgeUrl()
            : SAConfig.INSTANCE.updateCurseforgeUrl.get();

        src.sendSuccess(() -> CommandFeedback.header("[SA] Download Info"), false);
        if (result != null && result.success() && result.updateAvailable()) {
            src.sendSuccess(() -> CommandFeedback.info("[SA] Update available: " + result.latestVersion()), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] Current: " + StutterAnalyzerFabric.MOD_VERSION), false);
        }
        src.sendSuccess(() -> CommandFeedback.row("GitHub", github), false);
        if (!curseforge.isEmpty()) {
            src.sendSuccess(() -> CommandFeedback.row("CurseForge", curseforge), false);
        }
        return 1;
    }

    public static int quietOn(CommandSourceStack src) {
        QuietMode.setEnabled(true);
        src.sendSuccess(() -> CommandFeedback.success("[SA] Quiet mode: ON"), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Minor/medium stutters visible in F3 and /sa status, not in chat."), false);
        return 1;
    }

    public static int quietOff(CommandSourceStack src) {
        QuietMode.setEnabled(false);
        src.sendSuccess(() -> CommandFeedback.success("[SA] Quiet mode: OFF"), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Using normal notification rules. Use /sa alerts minor to show minor stutters in chat."), false);
        return 1;
    }

    public static int quietStatus(CommandSourceStack src) {
        boolean q = QuietMode.isEnabled();
        src.sendSuccess(() -> CommandFeedback.header("[SA] Quiet Mode"), false);
        src.sendSuccess(() -> CommandFeedback.row("Quiet mode", q ? "ON" : "OFF"), false);
        src.sendSuccess(() -> CommandFeedback.row("Minor/medium chat", q ? "suppressed" : "per config"), false);
        src.sendSuccess(() -> CommandFeedback.row("Severe/extreme chat", "always ON"), false);
        long aggCooldown = SAConfig.INSTANCE.minorAggregateChatCooldownSeconds.get();
        long remaining = StutterCounter.aggregateCooldownRemainingSeconds();
        src.sendSuccess(() -> CommandFeedback.row("Aggregate cooldown", aggCooldown + "s" + (remaining > 0 ? " (" + remaining + "s left)" : " (ready)")), false);
        return 1;
    }

    public static int submitLast(CommandSourceStack src) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        return SubmissionManager.submitLast(src);
    }

    public static int submitPreview(CommandSourceStack src) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        return SubmissionManager.submitPreview(src);
    }

    public static int submitReport(CommandSourceStack src, String reportId) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        return SubmissionManager.submitById(src, reportId);
    }

    public static int submitCrashLast(CommandSourceStack src) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        return SubmissionManager.submitCrashLast(src);
    }

    public static int submitCrash(CommandSourceStack src, String crashId) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.crash.submit_id_hint", crashId)), false);
        return 1;
    }

    public static int submitGuardLast(CommandSourceStack src) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        return SubmissionManager.submitGuardLast(src);
    }

    public static int submitGuard(CommandSourceStack src, String guardId) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.guard.submit_id_hint")), false);
        return 1;
    }

    public static int crashLast(CommandSourceStack src) {
        CrashEvent ce = PreviousCrashImporter.last();
        if (ce == null) {
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.crash.none")), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.crash.last_header")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.id"), ce.crashId), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.type"), ce.crashType), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.summary"), ce.summary), false);
        if (ce.hasKnownPattern())
            src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.known_pattern"), ce.bestMatch().patternId + " (" + ce.bestMatch().confidencePct() + "%)"), false);
        return 1;
    }

    public static int crashList(CommandSourceStack src) {
        List<CrashEvent> all = PreviousCrashImporter.allImported();
        if (all.isEmpty()) {
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.crash.list_none")), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.crash.list_header", all.size())), false);
        for (CrashEvent ce : all) {
            String pat = ce.hasKnownPattern() ? " [" + ce.bestMatch().patternId + "]" : "";
            src.sendSuccess(() -> CommandFeedback.info("- " + ce.crashId + pat), false);
        }
        return 1;
    }

    public static int crashShow(CommandSourceStack src, String crashId) {
        List<CrashEvent> all = findCrash(crashId);
        if (all.isEmpty()) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.cmd.crash.not_found", crashId)), false);
            return 1;
        }
        CrashEvent ce = all.get(0);
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.crash.header", ce.crashId)), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.type"), ce.crashType), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.summary"), ce.summary), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.timestamp"), ce.timestamp.toString()), false);
        if (ce.hasKnownPattern()) {
            src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.pattern"), ce.bestMatch().patternId), false);
            src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.confidence"), ce.bestMatch().confidencePct() + "%"), false);
            src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.reason"), ce.bestMatch().reason), false);
        }
        return 1;
    }

    public static int crashExport(CommandSourceStack src, String crashId) {
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.crash.export_hint", crashId)), false);
        return 1;
    }

    public static int guardStatus(CommandSourceStack src) {
        boolean em = SAConfig.INSTANCE.guardEmergencyMode.get();
        boolean enabled = SAConfig.INSTANCE.guardEnabled.get();
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.guard.status_header")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.system_enabled"), String.valueOf(enabled)), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.emergency_mode"), em ? "ENABLED" : "disabled"), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.session_triggers"), String.valueOf(com.stutteranalyzer.guard.GuardWarningRateLimiter.sessionTriggerCount())), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.guards_registered"), String.valueOf(EmergencyGuardManager.allGuards().size())), false);
        if (em) src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.cmd.guard.emergency_warning")), false);
        return 1;
    }

    public static int guardList(CommandSourceStack src) {
        List<EmergencyGuard> guards = EmergencyGuardManager.allGuards();
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.guard.list_header", guards.size())), false);
        for (EmergencyGuard g : guards) {
            Component statusComp = Component.literal(g.safetyLevel().name() + " | ")
                .append(Component.translatable(EmergencyGuardManager.isEnabled(g.patternId())
                    ? "stutteranalyzer.guard.list.enabled"
                    : "stutteranalyzer.guard.list.disabled"));
            src.sendSuccess(() -> CommandFeedback.row(g.patternId(), statusComp), false);
        }
        return 1;
    }

    public static int guardInfo(CommandSourceStack src, String guardId) {
        for (EmergencyGuard g : EmergencyGuardManager.allGuards()) {
            if (g.patternId().equalsIgnoreCase(guardId) || g.patternId().toLowerCase().contains(guardId.toLowerCase())) {
                src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.guard.header", g.patternId())), false);
                src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.safety_level"), g.safetyLevel().name()), false);
                src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.enabled"), String.valueOf(EmergencyGuardManager.isEnabled(g.patternId()))), false);
                return 1;
            }
        }
        src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.cmd.guard.not_found", guardId)), false);
        return 1;
    }

    public static int guardEnable(CommandSourceStack src, String guardId) {
        if (!CommandPermissionHelper.canManageGuards(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        EmergencyGuardManager.setEnabled(guardId.toUpperCase(), true);
        src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.cmd.guard.enabled", guardId)), false);
        return 1;
    }

    public static int guardDisable(CommandSourceStack src, String guardId) {
        if (!CommandPermissionHelper.canManageGuards(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        EmergencyGuardManager.setEnabled(guardId.toUpperCase(), false);
        src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.cmd.guard.disabled", guardId)), false);
        return 1;
    }

    public static int guardReportLast(CommandSourceStack src) {
        EmergencyGuardReport rep = EmergencyGuardManager.lastReport();
        if (rep == null) {
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.guard.no_reports")), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.guard.last_header")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.pattern"), rep.patternId), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.outcome"), rep.outcome.name()), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.confidence"), (int)(rep.confidence * 100) + "%"), false);
        if (!rep.action.isEmpty()) src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.action"), rep.action), false);
        return 1;
    }

    public static int alertsStatus(CommandSourceStack src) {
        AlertMode mode = AlertManager.currentMode();
        int cooldown = SAConfig.INSTANCE.alertCooldownSeconds.get();
        int catCooldown = SAConfig.INSTANCE.alertSameCategoryCooldownSeconds.get();
        int maxPerMin = SAConfig.INSTANCE.alertMaxAlertsPerMinute.get();
        boolean agg = SAConfig.INSTANCE.alertAggregateSmallStutters.get();
        boolean quiet = QuietMode.isEnabled();
        Component modeLabel = switch (mode) {
            case OFF     -> Component.translatable("stutteranalyzer.alerts.status.mode.off");
            case MINOR   -> Component.translatable("stutteranalyzer.alerts.status.mode.minor");
            case MEDIUM  -> Component.translatable("stutteranalyzer.alerts.status.mode.medium");
            case SEVERE  -> Component.translatable("stutteranalyzer.alerts.status.mode.severe");
            case EXTREME -> Component.translatable("stutteranalyzer.alerts.status.mode.extreme");
        };
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.alerts.status.header")), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.alerts.status.mode"), modeLabel), false);
        if (mode == AlertMode.OFF) {
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.alerts.status.mode"),
                Component.translatable("stutteranalyzer.alerts.status.chat_disabled")), false);
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.alerts.status.still_active")), false);
        } else {
            Component directComp = switch (mode) {
                case EXTREME -> Component.translatable("stutteranalyzer.alerts.status.direct.extreme");
                case SEVERE  -> Component.translatable("stutteranalyzer.alerts.status.direct.severe");
                case MEDIUM  -> Component.translatable("stutteranalyzer.alerts.status.direct.medium");
                case MINOR   -> Component.translatable("stutteranalyzer.alerts.status.direct.minor");
                default      -> Component.translatable("stutteranalyzer.alerts.status.direct.off");
            };
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.alerts.status.direct"), directComp), false);
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.alerts.status.cooldown"),
                Component.translatable("stutteranalyzer.alerts.status.seconds", cooldown)), false);
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.alerts.status.cat_cooldown"),
                Component.translatable("stutteranalyzer.alerts.status.seconds", catCooldown)), false);
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.alerts.status.max_per_min"),
                Component.literal(String.valueOf(maxPerMin))), false);
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.alerts.status.sched_cooldown"),
                Component.translatable("stutteranalyzer.alerts.status.seconds", SAConfig.INSTANCE.scheduledMicroHitchCooldownSeconds.get())), false);
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.alerts.status.aggregate"),
                Component.translatable(agg ? "stutteranalyzer.alerts.status.on" : "stutteranalyzer.alerts.status.off_val")), false);
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.alerts.status.quiet"),
                Component.translatable(quiet ? "stutteranalyzer.alerts.status.on" : "stutteranalyzer.alerts.status.off_val")), false);
        }
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.alerts.status.modes")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_minor")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_medium")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_severe")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_extreme")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_off")), false);
        return 1;
    }

    public static int alertsSetMode(CommandSourceStack src, AlertMode mode) {
        SAConfig.INSTANCE.alertMode.set(mode.name());
        if (mode == AlertMode.OFF) {
            src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.alerts.set.off")), false);
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.alerts.set.off_note")), false);
        } else if (mode == AlertMode.MINOR) {
            src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.alerts.set.minor")), false);
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.alerts.set.minor_note")), false);
        } else if (mode == AlertMode.MEDIUM) {
            src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.alerts.set.medium")), false);
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.alerts.set.medium_note")), false);
        } else if (mode == AlertMode.SEVERE) {
            src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.alerts.set.severe")), false);
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.alerts.set.severe_note")), false);
        } else if (mode == AlertMode.EXTREME) {
            src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.alerts.set.extreme")), false);
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.alerts.set.extreme_note")), false);
        }
        return 1;
    }

    public static int alertsCooldown(CommandSourceStack src, int seconds) {
        if (seconds < 5 || seconds > 600) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.alerts.cooldown.range")), false);
            return 0;
        }
        SAConfig.INSTANCE.alertCooldownSeconds.set(seconds);
        src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.alerts.cooldown.set", seconds)), false);
        return 1;
    }

    public static int alertsTest(CommandSourceStack src) {
        AlertMode mode = AlertManager.currentMode();
        int medium  = SAConfig.INSTANCE.mediumFrameMs.get();
        int severe  = SAConfig.INSTANCE.severeFrameMs.get();
        int extreme = SAConfig.INSTANCE.extremeFrameMs.get();
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.alerts.test.header")), false);
        long[] testMs = {76L, 184L, 485L, 3867L};
        String[] cats = {"CLIENT_RENDER_STUTTER", "CLIENT_RENDER_STUTTER", "SERVER_TICK_SPIKE", "CLIENT_RENDER_STUTTER"};
        for (int i = 0; i < testMs.length; i++) {
            final long ms = testMs[i];
            final String cat = cats[i];
            boolean shown = mode.shouldAlertDirect(ms, medium, severe, extreme);
            Component line = shown
                ? Component.translatable("stutteranalyzer.alerts.test.shown", cat, ms)
                : Component.translatable("stutteranalyzer.alerts.test.hidden", cat, ms);
            src.sendSuccess(() -> CommandFeedback.info(line), false);
        }
        return 1;
    }

    public static int showHelp(CommandSourceStack src) {
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.help.header")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.section.main")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.status")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.version")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.privacy")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.section.reports")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.last")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.show")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.reports")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.section.submit")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.preview")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.submit_preview")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.submit")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.submit_status")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.submit_health")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.section.alerts")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_status")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_minor")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_medium")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_severe")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_extreme")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_off")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.section.hud")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.f3")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.overlay")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.section.other")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.optimize_suggest")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.optimize_install")), false);
        return 1;
    }

    public static int quickDashboard(CommandSourceStack src) {
        FreezeEvent last = FreezeDetector.lastFreezeEvent();
        boolean degraded = SubsystemHealth.anyDegraded();
        boolean cfEnabled = SubmissionManager.isCloudflareEnabled();
        int reports = ReportWriter.savedReports();
        String spike = last != null
            ? last.category().name().toLowerCase().replace('_', ' ') + " " + last.durationMs() + "ms"
            : "none";
        FreezeReport lastSavedRep = ReportWriter.lastReport();
        String lastSavedStr = lastSavedRep != null
            ? lastSavedRep.event.category().name() + " " + lastSavedRep.event.durationMs() + "ms"
            : "none";
        String uploadStr = cfEnabled ? "ready" : "local only";
        src.sendSuccess(() -> CommandFeedback.header("[SA] Stutter Analyzer"), false);
        src.sendSuccess(() -> CommandFeedback.row("Status", degraded ? "DEGRADED" : "ACTIVE"), false);
        src.sendSuccess(() -> CommandFeedback.row("Last tracked spike", spike), false);
        src.sendSuccess(() -> CommandFeedback.row("Last saved report", lastSavedStr), false);
        src.sendSuccess(() -> CommandFeedback.row("Reports saved", String.valueOf(reports)), false);
        src.sendSuccess(() -> CommandFeedback.row("Upload", uploadStr), false);
        if (reports > 0) {
            src.sendSuccess(() -> CommandFeedback.info("[SA] Use /sa submit to send latest report."), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.info("[SA] Use /sa status for details."), false);
        }
        if (degraded) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Analyzer is degraded. Use /sa status for details."), false);
        }
        return 1;
    }

    public static int cancelLatestPending(CommandSourceStack src) {
        return SubmissionManager.cancelLatestPending(src);
    }

    public static int showPrivacy(CommandSourceStack src) {
        src.sendSuccess(() -> CommandFeedback.header("[SA] Privacy"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa submit sends a sanitized report to the Stutter Analyzer report server."), false);
        src.sendSuccess(() -> CommandFeedback.info("It may include mod list, Minecraft version, Java version, system info, recent performance events, and sanitized log excerpts."), false);
        src.sendSuccess(() -> CommandFeedback.info("It does not include tokens, passwords, auth data, session data, or full unredacted file paths."), false);
        src.sendSuccess(() -> CommandFeedback.info("GitHub issue creation happens server-side. You do not need a GitHub account."), false);
        return 1;
    }

    public static int showDevHelp(CommandSourceStack src) {
        src.sendSuccess(() -> CommandFeedback.info("Use /sa help for the public command list."), false);
        return 1;
    }

    public static int submitConfirm(CommandSourceStack src, String preparedId) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        return SubmissionManager.submitConfirm(src, preparedId);
    }

    public static int submitPrepareLast(CommandSourceStack src) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        return SubmissionManager.submitPrepareLast(src);
    }

    public static int submitLocalLast(CommandSourceStack src) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        return SubmissionManager.submitLocalLast(src);
    }

    public static int submitStatus(CommandSourceStack src) {
        return SubmissionManager.submitStatus(src);
    }

    public static int submitReset(CommandSourceStack src) {
        return SubmissionManager.submitReset(src);
    }

    public static int submitMinimal(CommandSourceStack src) {
        return SubmissionManager.submitMinimal(src);
    }

    public static int submitCheckStatus(CommandSourceStack src, String reportId) {
        return SubmissionManager.submitCheckStatus(src, reportId);
    }

    public static int submitModeCloudflare(CommandSourceStack src) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission()); return 0;
        }
        return SubmissionManager.submitModeCloudflare(src);
    }

    public static int submitModeLocal(CommandSourceStack src) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission()); return 0;
        }
        return SubmissionManager.submitModeLocal(src);
    }

    public static int submitModeStatus(CommandSourceStack src) {
        return SubmissionManager.submitModeStatus(src);
    }

    public static int submitDebugRouting(CommandSourceStack src) {
        return SubmissionManager.submitDebugRouting(src);
    }

    public static int submitHealth(CommandSourceStack src) {
        return SubmissionManager.submitHealth(src);
    }

    public static int submitYes(CommandSourceStack src) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission()); return 0;
        }
        return SubmissionManager.confirmLatestPending(src);
    }

    public static int submitSend(CommandSourceStack src) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission()); return 0;
        }
        return SubmissionManager.confirmLatestPending(src);
    }

    public static int submitConfirmLast(CommandSourceStack src) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission()); return 0;
        }
        return SubmissionManager.confirmLatestPending(src);
    }

    public static int submitCancelPrepared(CommandSourceStack src, String preparedId) {
        return SubmissionManager.cancelPrepared(src, preparedId);
    }

    public static int generateTestReport(CommandSourceStack src) {
        if (!CommandPermissionHelper.canUseDebug(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        boolean isClient = SAFabricPlatform.isClient();
        String side = isClient ? "client" : "dedicated-server";
        FreezeEvent testEvent = new FreezeEvent(
            FreezeCategory.UNKNOWN_FREEZE, 0.5,
            "Synthetic test report",
            "Artificially generated for testing - no real freeze occurred",
            side, 500L,
            MetricsCollector.eventBuffer().snapshot(),
            "This is a test report. Use /sa submit to test the submission flow."
        );
        FreezeDetector.injectForTesting(testEvent, MetricsCollector.eventBuffer());
        src.sendSuccess(() -> CommandFeedback.debug("Test report generated. Use /sa last to view."), true);
        return 1;
    }

    public static int debugCommandRouting(CommandSourceStack src) {
        boolean isClient = SAFabricPlatform.isClient();
        boolean cfEnabled = SubmissionManager.isCloudflareEnabled();
        src.sendSuccess(() -> CommandFeedback.header("[SA] Command routing"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa status", "CommonCommandLogic"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa submit", cfEnabled ? "CloudflareSubmitCommand" : "LocalFallback"), false);
        src.sendSuccess(() -> CommandFeedback.row("Loader", "Fabric"), false);
        return 1;
    }

    public static int selfCheck(CommandSourceStack src) {
        SelfCheckResult result = new SelfCheckResult();
        boolean isClient = SAFabricPlatform.isClient();

        result.ok("Core");

        try {
            SAConfig.INSTANCE.enabled.get();
            result.ok("Config");
        } catch (Throwable t) {
            result.error("Config", t.getMessage());
        }

        result.ok("Commands");

        try {
            java.nio.file.Path reportDir = SAFabricPlatform.getGameDir()
                .resolve("config/stutter-analyzer/reports");
            Files.createDirectories(reportDir);
            result.ok("Report folder");
        } catch (Throwable t) {
            result.error("Report folder", "cannot write reports: " + t.getMessage());
        }

        if (isClient) result.ok("Client frame tracker");
        else result.unavailable("Client frame tracker", "server-only install");

        result.ok("Server tick tracker");

        if (isClient) {
            SubsystemHealth.Status f3 = SubsystemHealth.all().getOrDefault("F3StatusLineRenderer", SubsystemHealth.Status.OK);
            if (f3 == SubsystemHealth.Status.OK) result.ok("F3 status line");
            else result.error("F3 status line", SubsystemHealth.note("F3StatusLineRenderer"));
        } else {
            result.unavailable("F3 status line", "client only");
        }

        if (OptimizationModKnowledgeBase.isLoaded()) result.ok("Knowledge base");
        else result.warn("Knowledge base", "not loaded");

        result.ok("Known pattern detector");
        result.ok("Emergency Guard");
        result.ok("Submission manager");

        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.selfcheck.header")), false);
        for (SelfCheckResult.CheckItem item : result.items()) {
            String statusKey = switch (item.status) {
                case OK          -> "stutteranalyzer.selfcheck.status.ok";
                case WARN        -> "stutteranalyzer.selfcheck.status.warn";
                case ERROR       -> "stutteranalyzer.selfcheck.status.error";
                case UNAVAILABLE -> "stutteranalyzer.selfcheck.status.unavailable";
            };
            Component statusComp = item.note.isEmpty()
                ? Component.translatable(statusKey)
                : Component.translatable(statusKey).copy()
                    .append(Component.literal(" - " + item.note));
            String nameKey = selfCheckItemKey(item.name);
            Component nameComp = nameKey != null
                ? Component.literal("- ").append(Component.translatable(nameKey))
                : Component.literal("- " + item.name);
            src.sendSuccess(() -> CommandFeedback.row(nameComp, statusComp), false);
        }
        String overall = result.overall();
        src.sendSuccess(() -> (result.isHealthy()
            ? CommandFeedback.success(Component.translatable("stutteranalyzer.cmd.selfcheck.overall_ok", overall))
            : CommandFeedback.warn(Component.translatable("stutteranalyzer.cmd.selfcheck.overall_warn", overall))), false);
        FreezeReport lastForHint = ReportWriter.lastReport();
        if (lastForHint != null && lastForHint.event.category().name().equals("UNKNOWN_FREEZE")) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.cmd.selfcheck.unknown_freeze_hint")), false);
        }
        return 1;
    }

    public static int f3Status(CommandSourceStack src) {
        if (!SAFabricPlatform.isClient()) {
            src.sendFailure(CommandFeedback.clientOnly()); return 0;
        }
        boolean enabled = com.stutteranalyzer.client.DebugHudStatusProvider.isF3Enabled();
        String pos = SAConfig.INSTANCE.f3LinePosition.get();
        src.sendSuccess(() -> CommandFeedback.row("F3 status line", enabled ? "ON" : "OFF"), false);
        src.sendSuccess(() -> CommandFeedback.row("Position", pos), false);
        return 1;
    }

    public static int f3On(CommandSourceStack src) {
        if (!SAFabricPlatform.isClient()) {
            src.sendFailure(CommandFeedback.clientOnly()); return 0;
        }
        com.stutteranalyzer.client.DebugHudStatusProvider.setF3Enabled(true);
        SAConfig.INSTANCE.debugHudEnabled.set(true);
        src.sendSuccess(() -> CommandFeedback.success("[SA] F3 status line: ON"), false);
        return 1;
    }

    public static int f3Off(CommandSourceStack src) {
        if (!SAFabricPlatform.isClient()) {
            src.sendFailure(CommandFeedback.clientOnly()); return 0;
        }
        com.stutteranalyzer.client.DebugHudStatusProvider.setF3Enabled(false);
        SAConfig.INSTANCE.debugHudEnabled.set(false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] F3 status line: OFF"), false);
        return 1;
    }

    public static int f3Toggle(CommandSourceStack src) {
        if (!SAFabricPlatform.isClient()) {
            src.sendFailure(CommandFeedback.clientOnly()); return 0;
        }
        boolean current = com.stutteranalyzer.client.DebugHudStatusProvider.isF3Enabled();
        if (current) return f3Off(src);
        return f3On(src);
    }

    public static int optimizeSuggest(CommandSourceStack src) {
        if (com.stutteranalyzer.optimize.OptimizeInstaller.isScanning()) {
            src.sendSuccess(() -> CommandFeedback.info(
                Component.translatable("stutteranalyzer.optimize.scan.in_progress")), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.info(
            Component.translatable("stutteranalyzer.optimize.scan.started")), false);

        // Fast synchronous state collection
        java.util.Set<String> installedIds = new java.util.HashSet<>();
        try {
            net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()
                .forEach(m -> installedIds.add(m.getMetadata().getId()));
        } catch (Throwable t) {
            StutterAnalyzerFabric.LOGGER.warn("[SA] Could not scan installed mods: {}", t.getMessage());
        }
        java.nio.file.Path gameDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
        String mcVersion = net.minecraft.SharedConstants.getCurrentVersion().getName();
        boolean isServer = !SAFabricPlatform.isClient();

        com.stutteranalyzer.optimize.OptimizeInstaller.startScan();
        Thread worker = new Thread(() -> {
            long start = System.currentTimeMillis();
            try {
                com.stutteranalyzer.optimize.OptimizePlan plan =
                    com.stutteranalyzer.optimize.OptimizeAssistant.buildPlan(
                        installedIds, gameDir, "fabric", mcVersion, isServer);
                com.stutteranalyzer.optimize.OptimizeInstaller.setPlan(plan, gameDir.resolve("mods"));
                com.stutteranalyzer.optimize.OptimizeInstaller.completeScan(buildPlanDisplay(plan, isServer));
            } catch (Throwable t) {
                StutterAnalyzerFabric.LOGGER.warn("[SA] optimizeSuggest background task failed: {}", t.getMessage(), t);
                com.stutteranalyzer.optimize.OptimizeInstaller.completeScan(
                    java.util.List.of(CommandFeedback.info(
                        Component.translatable("stutteranalyzer.optimize.scan.failed", t.getMessage()))));
            } finally {
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed > 2000) {
                    StutterAnalyzerFabric.LOGGER.info("[SA] Internal task slow: optimize_scan took {}ms", elapsed);
                }
            }
        }, "SA-OptimizeScan");
        worker.setDaemon(true);
        worker.start();
        return 1;
    }

    public static int optimizeInstall(CommandSourceStack src) {
        try {
            com.stutteranalyzer.optimize.OptimizeInstaller.handleInstall(src);
            return 1;
        } catch (Throwable t) {
            StutterAnalyzerFabric.LOGGER.warn("[SA] optimizeInstall failed: {}", t.getMessage(), t);
            src.sendSuccess(() -> CommandFeedback.info(
                Component.translatable("stutteranalyzer.optimize.install_failed_cmd", t.getMessage())), false);
            return 0;
        }
    }

    private static void displayOptimizePlan(CommandSourceStack src,
                                             com.stutteranalyzer.optimize.OptimizePlan plan,
                                             boolean isServer) {
        for (Component c : buildPlanDisplay(plan, isServer)) {
            src.sendSuccess(() -> c, false);
        }
    }

    static List<Component> buildPlanDisplay(com.stutteranalyzer.optimize.OptimizePlan plan, boolean isServer) {
        List<Component> out = new ArrayList<>();
        String loaderName = plan.loader.isEmpty() ? "unknown"
            : plan.loader.substring(0, 1).toUpperCase() + plan.loader.substring(1);
        out.add(CommandFeedback.header(Component.translatable("stutteranalyzer.optimize.scan.title",
            plan.mcVersion, loaderName)));
        if (isServer) {
            out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.scan.server_mode")));
        }
        if (plan.alreadyInstalled.isEmpty()) {
            out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.mods_line_none",
                plan.totalInstalledCount)));
        } else {
            out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.mods_line",
                plan.totalInstalledCount, String.join(", ", plan.alreadyInstalled))));
        }
        if (plan.isEmpty()) {
            out.add(CommandFeedback.info(Component.translatable(plan.alreadyInstalled.isEmpty()
                ? "stutteranalyzer.optimize.no_suggestions"
                : "stutteranalyzer.optimize.already_optimized")));
            return out;
        }
        Component riskLabel = Component.translatable(
            "stutteranalyzer.optimize.risk." + plan.risk.name().toLowerCase());
        out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.plan_risk",
            plan.recommended.size(), riskLabel)));
        int shown = Math.min(plan.recommended.size(), 5);
        for (int i = 0; i < shown; i++) {
            com.stutteranalyzer.optimize.OptimizeMod mod = plan.recommended.get(i);
            int num = i + 1;
            out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.mod_entry",
                num, mod.displayName, mod.reason)));
        }
        int remaining = plan.recommended.size() - shown;
        if (remaining > 0) {
            out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.more", remaining)));
        }
        out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.run_install")));
        StutterAnalyzerFabric.LOGGER.info("[SA] Full optimization plan: {}",
            plan.recommended.stream().map(m -> m.displayName).collect(Collectors.joining(", ")));
        return out;
    }

    public static int showRecentEvents(CommandSourceStack src, String timeStr) {
        int seconds = parseTimeString(timeStr);
        if (seconds <= 0) {
            src.sendSuccess(() -> CommandFeedback.info(
                Component.translatable("stutteranalyzer.show.invalid_time")), false);
            return 0;
        }
        RecentEventBuffer buffer = MetricsCollector.eventBuffer();
        List<RecentEventBuffer.GameEvent> all = buffer.recentSeconds(seconds);
        int medium = SAConfig.INSTANCE.mediumFrameMs.get();

        List<RecentEventBuffer.GameEvent> relevant = all.stream()
            .filter(e -> e.type == RecentEventBuffer.EventType.FREEZE_DETECTED
                      || e.type == RecentEventBuffer.EventType.STUTTER_DETECTED)
            .filter(e -> parseMsFromDetail(e.detail) >= medium)
            .sorted(Comparator.comparing(e -> e.timestamp))
            .collect(Collectors.toList());

        String timeLabel = seconds >= 3600 ? (seconds / 3600) + "h" : (seconds / 60) + "m";

        if (relevant.isEmpty()) {
            src.sendSuccess(() -> CommandFeedback.info(
                Component.translatable("stutteranalyzer.show.none", timeLabel)), false);
            return 1;
        }

        int total = relevant.size();
        int limit = 10;
        src.sendSuccess(() -> CommandFeedback.header(
            Component.translatable("stutteranalyzer.show.header", timeLabel)), false);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
        List<RecentEventBuffer.GameEvent> toShow = relevant.subList(Math.max(0, total - limit), total);
        for (int i = 0; i < toShow.size(); i++) {
            RecentEventBuffer.GameEvent e = toShow.get(i);
            int num = i + 1;
            String time2 = fmt.format(e.timestamp);
            String line = num + ". " + e.detail + ", " + time2;
            FreezeCategory cat = categoryFromDetail(e.detail);
            long ms = parseMsFromDetail(e.detail);
            Component visibleMsg = Component.literal(line);
            Component msg = (cat != null)
                ? com.stutteranalyzer.client.AlertHoverText.build(cat, ms, visibleMsg)
                : CommandFeedback.info(visibleMsg);
            src.sendSuccess(() -> msg, false);
        }

        if (total > limit) {
            src.sendSuccess(() -> CommandFeedback.info(
                Component.translatable("stutteranalyzer.show.truncated", limit, total)), false);
        }
        return 1;
    }

    private static int parseTimeString(String s) {
        if (s == null || s.isEmpty()) return -1;
        try {
            if (s.endsWith("m")) {
                int v = Integer.parseInt(s.substring(0, s.length() - 1));
                return (v >= 1 && v <= 120) ? v * 60 : -1;
            }
            if (s.endsWith("h")) {
                int v = Integer.parseInt(s.substring(0, s.length() - 1));
                return (v >= 1 && v <= 2) ? v * 3600 : -1;
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    private static long parseMsFromDetail(String detail) {
        if (detail == null) return 0;
        int sp = detail.lastIndexOf(' ');
        if (sp < 0) return 0;
        String part = detail.substring(sp + 1);
        if (part.endsWith("ms")) part = part.substring(0, part.length() - 2);
        try { return Long.parseLong(part); } catch (NumberFormatException e) { return 0; }
    }

    private static FreezeCategory categoryFromDetail(String detail) {
        if (detail == null) return null;
        int sp = detail.lastIndexOf(' ');
        String name = sp >= 0 ? detail.substring(0, sp) : detail;
        try { return FreezeCategory.valueOf(name); } catch (Exception e) { return null; }
    }

    private static List<CrashEvent> findCrash(String crashId) {
        return PreviousCrashImporter.allImported().stream()
            .filter(ce -> ce.crashId.equals(crashId))
            .toList();
    }

    private static String selfCheckItemKey(String itemName) {
        return switch (itemName) {
            case "Core"                   -> "stutteranalyzer.selfcheck.item.core";
            case "Config"                 -> "stutteranalyzer.selfcheck.item.config";
            case "Commands"               -> "stutteranalyzer.selfcheck.item.commands";
            case "Report folder"          -> "stutteranalyzer.selfcheck.item.report_folder";
            case "Client frame tracker"   -> "stutteranalyzer.selfcheck.item.client_tracker";
            case "Server tick tracker"    -> "stutteranalyzer.selfcheck.item.server_tracker";
            case "F3 status line"         -> "stutteranalyzer.selfcheck.item.f3";
            case "Knowledge base"         -> "stutteranalyzer.selfcheck.item.knowledge_base";
            case "Known pattern detector" -> "stutteranalyzer.selfcheck.item.pattern_detector";
            case "Emergency Guard"        -> "stutteranalyzer.selfcheck.item.emergency_guard";
            case "Submission manager"     -> "stutteranalyzer.selfcheck.item.submission";
            default                       -> null;
        };
    }
}
