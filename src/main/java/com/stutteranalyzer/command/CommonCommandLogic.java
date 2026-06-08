package com.stutteranalyzer.command;

import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.classifier.FreezeCategory;
import com.stutteranalyzer.classifier.FreezeDetector;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.AlertManager;
import com.stutteranalyzer.core.AlertMode;
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
import com.stutteranalyzer.report.FreezeEvent;
import com.stutteranalyzer.report.FreezeReport;
import com.stutteranalyzer.report.ReportWriter;
import com.stutteranalyzer.submission.SubmissionManager;
import com.stutteranalyzer.update.UpdateCheckResult;
import com.stutteranalyzer.update.UpdateChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Server-safe command logic shared between /stutteranalyzer and /sa.
 * Must not reference any client-only classes.
 */
public class CommonCommandLogic {

    public static int showStatus(CommandSourceStack src) {
        FreezeEvent last = FreezeDetector.lastFreezeEvent();
        boolean isClient = FMLEnvironment.dist == Dist.CLIENT;
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
        String minorLabel   = useEpisodes ? "Minor episodes"   : "Minor frames";
        String mediumLabel  = useEpisodes ? "Medium episodes"  : "Medium frames";
        String severeLabel  = useEpisodes ? "Severe episodes"  : "Severe frames";
        String extremeLabel = useEpisodes ? "Extreme episodes" : "Extreme frames";

        Component state = Component.translatable(degraded
            ? "stutteranalyzer.cmd.status.state_degraded"
            : "stutteranalyzer.cmd.status.state_active");
        String sideKey = isClient
            ? "stutteranalyzer.cmd.status.side.client_integrated"
            : "stutteranalyzer.cmd.status.side.dedicated";

        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.status.header")), false);
        src.sendSuccess(() -> CommandFeedback.row("Status UI", "rich-v2"), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.cmd.status.state"), state), false);
        src.sendSuccess(() -> CommandFeedback.row("Side", Component.translatable(sideKey)), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.client_tracker"),
            Component.translatable(isClient ? "stutteranalyzer.cmd.status.tracker_on" : "stutteranalyzer.cmd.status.tracker_unavailable")
        ), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.server_tracker"),
            Component.translatable("stutteranalyzer.cmd.status.tracker_on")
        ), false);

        // Last tracked spike
        if (lastDurationMs > 0) {
            src.sendSuccess(() -> CommandFeedback.row("Last tracked spike",
                lastSeverity + " " + lastDurationMs + "ms"), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.row("Last tracked spike", "none"), false);
        }

        // Episode/frame counts
        src.sendSuccess(() -> CommandFeedback.row(minorLabel,
            minorDisplay + " in last 60s" + (worstMinor > 0 ? " | worst: " + worstMinor + "ms" : "")), false);
        if (showRaw && useEpisodes) {
            src.sendSuccess(() -> CommandFeedback.row("Raw minor frames", minorRaw + " in last 60s"), false);
        }
        src.sendSuccess(() -> CommandFeedback.row(mediumLabel,
            mediumDisplay + " in last 60s" + (worstMedium > 0 ? " | worst: " + worstMedium + "ms" : "")), false);
        if (showRaw && useEpisodes && mediumRaw != mediumDisplay) {
            src.sendSuccess(() -> CommandFeedback.row("Raw medium frames", mediumRaw + " in last 60s"), false);
        }
        src.sendSuccess(() -> CommandFeedback.row(severeLabel,
            severeDisplay + " in last 60s" + (worstSevere > 0 ? " | worst: " + worstSevere + "ms" : "")), false);
        src.sendSuccess(() -> CommandFeedback.row(extremeLabel,
            extremeDisplay + " in last 60s" + (worstExtreme > 0 ? " | worst: " + worstExtreme + "ms" : "")), false);

        // Reports
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

        // Status notes about report saving
        if (!allSavingDisabled) {
            if (minorDisplay == 0 && mediumDisplay == 0 && severeDisplay == 0 && extremeDisplay == 0) {
                src.sendSuccess(() -> CommandFeedback.info("[SA] No stutters recorded yet. Use /sa debug test minor to verify."), false);
            } else if (savedReports == 0 && (severeDisplay > 0 || extremeDisplay > 0)) {
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Severe/extreme detected but no reports saved - check game log for errors."), false);
            } else if (savedReports == 0 && (minorDisplay > 0 || mediumDisplay > 0)) {
                int severeMs = SAConfig.INSTANCE.severeFrameMs.get();
                src.sendSuccess(() -> CommandFeedback.info("[SA] Minor/medium tracked silently; reports start at " + severeMs + "ms."), false);
            }
        }

        // Last saved report
        if (savedReports > 0 && last != null) {
            Component reportVal = Component.literal(last.category().name() + " " + last.durationMs() + "ms");
            src.sendSuccess(() -> CommandFeedback.row("Last saved report", reportVal), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.row("Last saved report", "none"), false);
        }

        // Crashes imported
        int crashCount = PreviousCrashImporter.allImported().size();
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.row.crashes_imported"),
            String.valueOf(crashCount)), false);

        // Quiet mode and aggregate cooldown
        boolean quiet = QuietMode.isEnabled();
        src.sendSuccess(() -> CommandFeedback.row("Quiet mode",
            quiet ? "ON (minor/medium in F3 only)" : "OFF"), false);
        long aggRemaining = StutterCounter.aggregateCooldownRemainingSeconds();
        if (aggRemaining > 0) {
            src.sendSuccess(() -> CommandFeedback.row("Aggregate chat cooldown", aggRemaining + "s remaining"), false);
        }

        // Verbose mode and severe chat
        boolean chatSevere = SAConfig.INSTANCE.chatNotifySevereStutters.get();
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.chat_severe"),
            Component.translatable(chatSevere ? "stutteranalyzer.verbose.on" : "stutteranalyzer.verbose.off")), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.verbose_mode"),
            Component.translatable(VerboseMode.isEnabled() ? "stutteranalyzer.verbose.on" : "stutteranalyzer.verbose.off")), false);

        // Submission target
        String subTarget = SAConfig.INSTANCE.submissionTarget.get();
        String subDisplay = "cloudflare".equalsIgnoreCase(subTarget) ? "Cloudflare enabled" : "local";
        src.sendSuccess(() -> CommandFeedback.row("Submission", subDisplay), false);

        // Update check status
        UpdateCheckResult updateResult = UpdateChecker.getCached();
        String updateDisplay;
        if (!SAConfig.INSTANCE.checkForUpdates.get()) {
            updateDisplay = "disabled";
        } else if (updateResult == null) {
            updateDisplay = "not checked";
        } else if (!updateResult.success()) {
            updateDisplay = "unavailable";
        } else if (updateResult.updateAvailable()) {
            updateDisplay = "update available: " + updateResult.latestVersion();
        } else {
            updateDisplay = "up to date";
        }
        src.sendSuccess(() -> CommandFeedback.row("Update check", updateDisplay), false);

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
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.last.use_show", rep.reportId)), false);
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

    // ── Verbose mode ──────────────────────────────────────────────────────

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

    // ── Debug test commands ───────────────────────────────────────────────

    public static int debugTestMinor(CommandSourceStack src) {
        FreezeDetector.injectSilent(55);
        // Request immediate F3 refresh so user sees the update without waiting
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
        boolean isClient = FMLEnvironment.dist == Dist.CLIENT;
        FreezeEvent testEvent = new FreezeEvent(
            FreezeCategory.UNKNOWN_FREEZE, 0.5,
            "Synthetic severe test (300ms, /sa debug test severe)",
            "Artificially generated for testing",
            isClient ? "client" : "dedicated-server", 300L,
            MetricsCollector.eventBuffer().snapshot(),
            "This is a test. Use /sa submit last to test submission."
        );
        FreezeDetector.injectForTesting(testEvent, MetricsCollector.eventBuffer());
        AnalyzerRuntimeState.requestF3Refresh();
        int reports = ReportWriter.savedReports();
        src.sendSuccess(() -> CommandFeedback.success("[SA] Debug test: injected severe freeze 300ms. Report saved."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] F3 should now show: UNKNOWN_FREEZE 300ms | Reports: " + reports), false);
        return 1;
    }

    public static int debugTestExtreme(CommandSourceStack src) {
        boolean isClient = FMLEnvironment.dist == Dist.CLIENT;
        FreezeEvent testEvent = new FreezeEvent(
            FreezeCategory.UNKNOWN_FREEZE, 0.5,
            "Synthetic extreme freeze test (1200ms, /sa debug test extreme)",
            "Artificially generated for testing",
            isClient ? "client" : "dedicated-server", 1200L,
            MetricsCollector.eventBuffer().snapshot(),
            "This is an extreme freeze test. Use /sa submit last to test submission."
        );
        FreezeDetector.injectForTesting(testEvent, MetricsCollector.eventBuffer());
        AnalyzerRuntimeState.requestF3Refresh();
        src.sendSuccess(() -> CommandFeedback.success("[SA] Debug test: injected extreme freeze 1200ms. Report saved."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Use /sa last and /sa status to verify."), false);
        return 1;
    }

    public static int debugVisibilityTest(CommandSourceStack src) {
        // Step 1: inject minor
        FreezeDetector.injectSilent(55);
        // Step 2: inject medium
        FreezeDetector.injectSilent(150);
        // Step 3: inject severe (saves report)
        boolean isClient = FMLEnvironment.dist == Dist.CLIENT;
        FreezeEvent severeEvent = new FreezeEvent(
            FreezeCategory.UNKNOWN_FREEZE, 0.5,
            "Visibility test severe (300ms)",
            "Generated by /sa debug visibility-test",
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
        src.sendSuccess(() -> CommandFeedback.header("[SA] Stutter Analyzer Version Info"), false);
        src.sendSuccess(() -> CommandFeedback.row("Version", StutterAnalyzerMod.MOD_VERSION), false);
        src.sendSuccess(() -> CommandFeedback.row("Build ID", StutterAnalyzerMod.BUILD_ID), false);
        src.sendSuccess(() -> CommandFeedback.row("Build", StutterAnalyzerMod.BUILD_DATE), false);
        src.sendSuccess(() -> CommandFeedback.row("Minecraft", "1.20.4"), false);
        src.sendSuccess(() -> CommandFeedback.row("Loader", "Forge 49.x"), false);
        src.sendSuccess(() -> CommandFeedback.row("Java", System.getProperty("java.version", "unknown")), false);

        // Jar file loaded
        String jarName = StutterAnalyzerMod.getLoadedJarName();
        src.sendSuccess(() -> CommandFeedback.row("Jar file", jarName), false);

        // Submission config
        String endpoint = com.stutteranalyzer.config.SAConfig.INSTANCE.cloudflareEndpoint.get();
        int timeoutSec = com.stutteranalyzer.config.SAConfig.INSTANCE.uploadTimeoutSeconds.get();
        src.sendSuccess(() -> CommandFeedback.row("Submit endpoint", endpoint.isBlank() ? "(not set)" : endpoint), false);
        src.sendSuccess(() -> CommandFeedback.row("Submit timeout", timeoutSec + "s"), false);
        src.sendSuccess(() -> CommandFeedback.row("Submit impl", "AsyncQueuedSubmitClient v2"), false);
        src.sendSuccess(() -> CommandFeedback.row("Worker response mode", "stored/queued supported"), false);

        if (!SAConfig.INSTANCE.checkForUpdates.get()) {
            src.sendSuccess(() -> CommandFeedback.row("Update check", "disabled"), false);
        } else {
            UpdateCheckResult result = UpdateChecker.getCached();
            if (result == null) {
                src.sendSuccess(() -> CommandFeedback.row("Update check", "not checked - use /sa update check"), false);
            } else if (!result.success()) {
                src.sendSuccess(() -> CommandFeedback.row("Update check", "unavailable"), false);
                String reason = result.errorReason() != null ? result.errorReason() : "unknown error";
                src.sendSuccess(() -> CommandFeedback.row("Reason", reason), false);
            } else if (result.updateAvailable()) {
                src.sendSuccess(() -> CommandFeedback.row("Latest", result.latestVersion()), false);
                src.sendSuccess(() -> CommandFeedback.row("Update", "available"), false);
                src.sendSuccess(() -> CommandFeedback.row("Download", result.githubPage()), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] CurseForge link is available on the GitHub page."), false);
            } else {
                src.sendSuccess(() -> CommandFeedback.row("Update check", "up to date"), false);
            }
        }
        return 1;
    }

    // ── Update commands ───────────────────────────────────────────────────

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
        src.sendSuccess(() -> CommandFeedback.row("Current", StutterAnalyzerMod.MOD_VERSION), false);
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
            src.sendSuccess(() -> CommandFeedback.info("[SA] Current: " + StutterAnalyzerMod.MOD_VERSION), false);
        }
        src.sendSuccess(() -> CommandFeedback.row("GitHub", github), false);
        if (!curseforge.isEmpty()) {
            src.sendSuccess(() -> CommandFeedback.row("CurseForge", curseforge), false);
        }
        return 1;
    }

    // ── Quiet mode ────────────────────────────────────────────────────────

    public static int quietOn(CommandSourceStack src) {
        QuietMode.setEnabled(true);
        src.sendSuccess(() -> CommandFeedback.success("[SA] Quiet mode: ON"), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Minor/medium stutters visible in F3 and /sa status, not in chat."), false);
        return 1;
    }

    public static int quietOff(CommandSourceStack src) {
        QuietMode.setEnabled(false);
        src.sendSuccess(() -> CommandFeedback.success("[SA] Quiet mode: OFF"), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Using normal notification rules. Use /sa verbose on for per-stutter chat."), false);
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

    // ── Alerts ────────────────────────────────────────────────────────────

    public static int alertsSetMode(CommandSourceStack src, String modeName) {
        AlertMode mode = AlertMode.fromString(modeName);
        SAConfig.INSTANCE.alertMode.set(mode.name());
        int medium  = SAConfig.INSTANCE.mediumFrameMs.get();
        int severe  = SAConfig.INSTANCE.severeFrameMs.get();
        int extreme = SAConfig.INSTANCE.extremeFrameMs.get();
        switch (mode) {
            case OFF -> {
                src.sendSuccess(() -> CommandFeedback.success("[SA] Alert mode: OFF"), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] Chat alerts disabled. F3, /sa status, reports, and /sa submit still work."), false);
            }
            case MINOR -> {
                src.sendSuccess(() -> CommandFeedback.success("[SA] Alert mode: MINOR"), false);
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Warning: minor alerts can be noisy. Use /sa alerts off to disable."), false);
            }
            case MEDIUM -> {
                src.sendSuccess(() -> CommandFeedback.success("[SA] Alert mode: MEDIUM"), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] Medium, severe, and extreme stutters will appear in chat."), false);
            }
            case SEVERE -> {
                src.sendSuccess(() -> CommandFeedback.success("[SA] Alert mode: SEVERE"), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] Only important freezes will appear in chat."), false);
            }
            case EXTREME -> {
                src.sendSuccess(() -> CommandFeedback.success("[SA] Alert mode: EXTREME"), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] Only extreme freezes will appear in chat."), false);
            }
        }
        return 1;
    }

    public static int alertsCooldown(CommandSourceStack src, int seconds) {
        int clamped = Math.max(5, Math.min(600, seconds));
        if (clamped != seconds) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Cooldown must be between 5 and 600 seconds."), false);
            return 0;
        }
        SAConfig.INSTANCE.alertCooldownSeconds.set(clamped);
        src.sendSuccess(() -> CommandFeedback.success("[SA] Alert cooldown set to " + clamped + " seconds."), false);
        return 1;
    }

    public static int alertsStatus(CommandSourceStack src) {
        AlertMode mode = AlertManager.currentMode();
        int medium  = SAConfig.INSTANCE.mediumFrameMs.get();
        int severe  = SAConfig.INSTANCE.severeFrameMs.get();
        int extreme = SAConfig.INSTANCE.extremeFrameMs.get();
        int cooldown = SAConfig.INSTANCE.alertCooldownSeconds.get();
        int catCooldown = SAConfig.INSTANCE.alertSameCategoryCooldownSeconds.get();
        int maxPerMin = SAConfig.INSTANCE.alertMaxAlertsPerMinute.get();
        boolean agg = SAConfig.INSTANCE.alertAggregateSmallStutters.get();
        boolean quiet = QuietMode.isEnabled();
        src.sendSuccess(() -> CommandFeedback.header("[SA] Alert Status"), false);
        src.sendSuccess(() -> CommandFeedback.row("Mode", mode.name()), false);
        if (mode == AlertMode.OFF) {
            src.sendSuccess(() -> CommandFeedback.row("Chat alerts", "disabled"), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] F3/status/report saving: still active"), false);
        } else {
            String alertsOn = mode.alertsOnDescription(medium, severe, extreme);
            src.sendSuccess(() -> CommandFeedback.row("Direct chat alerts", alertsOn), false);
            src.sendSuccess(() -> CommandFeedback.row("Cooldown", cooldown + "s"), false);
            src.sendSuccess(() -> CommandFeedback.row("Same-category cooldown", catCooldown + "s"), false);
            src.sendSuccess(() -> CommandFeedback.row("Max alerts/minute", String.valueOf(maxPerMin)), false);
            src.sendSuccess(() -> CommandFeedback.row("Small stutter aggregate", agg ? "ON" : "OFF"), false);
            src.sendSuccess(() -> CommandFeedback.row("Quiet mode", quiet ? "ON" : "OFF"), false);
        }
        return 1;
    }

    public static int alertsTest(CommandSourceStack src) {
        AlertMode mode = AlertManager.currentMode();
        int medium  = SAConfig.INSTANCE.mediumFrameMs.get();
        int severe  = SAConfig.INSTANCE.severeFrameMs.get();
        int extreme = SAConfig.INSTANCE.extremeFrameMs.get();
        src.sendSuccess(() -> CommandFeedback.header("[SA] Alert Test"), false);
        long[] testMs   = {76L, 184L, 485L, 3867L};
        String[] cats   = {"CLIENT_RENDER_STUTTER", "CLIENT_RENDER_STUTTER", "SERVER_TICK_SPIKE", "CLIENT_RENDER_STUTTER"};
        for (int i = 0; i < testMs.length; i++) {
            final long ms = testMs[i];
            final String cat = cats[i];
            boolean shown = mode.shouldAlertDirect(ms, medium, severe, extreme);
            String label = shown ? "would be shown" : "would be hidden in this mode";
            src.sendSuccess(() -> CommandFeedback.info("Test: " + cat + " " + ms + "ms " + label), false);
        }
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

    // ── Crash commands ─────────────────────────────────────────────────────

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
        List<CrashEvent> all = PreviousCrashImported(crashId);
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

    // ── Guard commands ─────────────────────────────────────────────────────

    public static int guardStatus(CommandSourceStack src) {
        boolean em = com.stutteranalyzer.config.SAConfig.INSTANCE.guardEmergencyMode.get();
        boolean enabled = com.stutteranalyzer.config.SAConfig.INSTANCE.guardEnabled.get();
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
                src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.config_key"), g.patternId().toLowerCase()), false);
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

    public static int showHelp(CommandSourceStack src) {
        src.sendSuccess(() -> CommandFeedback.header("[SA] Stutter Analyzer"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa              - quick status"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa status       - detailed analyzer status"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa submit       - send latest report and logs"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa last         - show latest report"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa reports      - list reports"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa version      - version and update info"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa privacy      - what gets submitted"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa alerts severe  - notify only important freezes"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa alerts medium  - notify medium and higher stutters"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa alerts minor   - notify all detected stutters, noisy"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa alerts extreme - notify only extreme freezes"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa alerts off     - disable chat alerts"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa alerts status  - show alert mode"), false);
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
        String uploadStr = cfEnabled ? "ready" : "local only";
        src.sendSuccess(() -> CommandFeedback.header("[SA] Stutter Analyzer"), false);
        src.sendSuccess(() -> CommandFeedback.row("Status", degraded ? "DEGRADED" : "ACTIVE"), false);
        src.sendSuccess(() -> CommandFeedback.row("Last spike", spike), false);
        src.sendSuccess(() -> CommandFeedback.row("Reports saved", String.valueOf(reports)), false);
        src.sendSuccess(() -> CommandFeedback.row("Upload", uploadStr), false);
        if (reports > 0) {
            src.sendSuccess(() -> CommandFeedback.info("[SA] Use /sa submit to send latest report."), false);
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
        src.sendSuccess(() -> CommandFeedback.header("[SA] Developer Commands"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa dev test minor|medium|severe|extreme - inject test event"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa dev generate-test-report             - generate a test report"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa dev submit-routing                   - show submit routing table"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa dev command-routing                  - show command routing"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa admin submit mode cloudflare|local   - set upload mode"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa admin submit local                   - manual local save (legacy)"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa admin submit health                  - check Cloudflare Worker health"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa admin submit status                  - show full submission config"), false);
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

    public static int submitConfigReset(CommandSourceStack src) {
        return SubmissionManager.submitConfigReset(src);
    }

    public static int submitExportPayload(CommandSourceStack src) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        return SubmissionManager.submitExportPayload(src);
    }

    public static int submitValidatePayload(CommandSourceStack src) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        return SubmissionManager.submitValidatePayload(src);
    }

    // ── Network diagnostic commands ───────────────────────────────────────

    public static int netHealth(CommandSourceStack src) {
        return SubmissionManager.netHealth(src);
    }

    public static int netEcho(CommandSourceStack src) {
        return SubmissionManager.netEcho(src);
    }

    public static int netPostMinimal(CommandSourceStack src) {
        return SubmissionManager.netPostMinimal(src);
    }

    public static int netStatus(CommandSourceStack src) {
        return SubmissionManager.netStatus(src);
    }

    public static int netEchoJava(CommandSourceStack src) { return SubmissionManager.netEchoJava(src); }
    public static int netEchoUrlConn(CommandSourceStack src) { return SubmissionManager.netEchoUrlConn(src); }
    public static int netPostMinimalJava(CommandSourceStack src) { return SubmissionManager.netPostMinimalJava(src); }
    public static int netPostMinimalUrlConn(CommandSourceStack src) { return SubmissionManager.netPostMinimalUrlConn(src); }

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
        boolean isClient = FMLEnvironment.dist == Dist.CLIENT;
        String side = isClient ? "client" : "dedicated-server";
        FreezeEvent testEvent = new FreezeEvent(
            FreezeCategory.UNKNOWN_FREEZE, 0.5,
            "Synthetic test report (generated via /sa debug generate-test-report)",
            "Artificially generated for testing - no real freeze occurred",
            side, 500L,
            MetricsCollector.eventBuffer().snapshot(),
            "This is a test report. Use /sa submit last to test the submission flow."
        );
        FreezeDetector.injectForTesting(testEvent, MetricsCollector.eventBuffer());
        src.sendSuccess(() -> CommandFeedback.debug("Test report generated. Use /sa last to view."), true);
        return 1;
    }

    public static int debugCommandRouting(CommandSourceStack src) {
        boolean isClient = FMLEnvironment.dist == Dist.CLIENT;
        boolean cfEnabled = SubmissionManager.isCloudflareEnabled();
        src.sendSuccess(() -> CommandFeedback.header("[SA] Command routing"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa status", "RichStatusCommand"), false);
        src.sendSuccess(() -> CommandFeedback.row("/stutteranalyzer status", "RichStatusCommand"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa submit last", cfEnabled ? "CloudflareSubmitCommand" : "ManualGitHubIssueFlow (WRONG)"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa submit local last", "ManualGitHubIssueFlow (explicit)"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa submit debug-routing", "SubmitDebugRoutingCommand"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa version", "VersionCommand"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa update", "UpdateCommand"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa verbose on/off/status", "VerboseCommand"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa quiet on/off/status", "QuietCommand"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa health", "HealthCommand"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa selfcheck", "SelfCheckCommand"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa debug test *", "DebugTestCommand"), false);
        src.sendSuccess(() -> CommandFeedback.row("All routes", "CommonCommandLogic (server-safe)"), false);
        if (isClient) {
            src.sendSuccess(() -> CommandFeedback.row("Client registrar", "ClientCommandRegistrar"), false);
        }
        src.sendSuccess(() -> CommandFeedback.row("Server registrar", "ServerCommandRegistrar"), false);
        return 1;
    }

    // ── Self-check ────────────────────────────────────────────────────────

    public static int selfCheck(CommandSourceStack src) {
        SelfCheckResult result = new SelfCheckResult();
        boolean isClient = FMLEnvironment.dist == Dist.CLIENT;

        result.ok("Core");

        try {
            SAConfig.INSTANCE.enabled.get();
            result.ok("Config");
        } catch (Throwable t) {
            result.error("Config", t.getMessage());
        }

        result.ok("Commands");

        try {
            java.nio.file.Path reportDir = isClient
                ? net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("config/stutter-analyzer/reports")
                : Paths.get("config/stutter-analyzer/reports");
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

    // ── F3 commands (server-side stub: client-only features fail gracefully) ─

    public static int f3Status(CommandSourceStack src) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            src.sendFailure(CommandFeedback.clientOnly()); return 0;
        }
        boolean enabled = SAConfig.INSTANCE.debugHudEnabled.get();
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.row.f3_status_line"),
            Component.translatable(enabled ? "stutteranalyzer.cmd.f3.enabled" : "stutteranalyzer.cmd.f3.disabled")
        ), false);
        if (enabled) {
            String current = com.stutteranalyzer.client.F3StatusFormatter.format().replaceAll("§.", "");
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.f3.current", current)), false);
        }
        return 1;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static List<CrashEvent> PreviousCrashImported(String crashId) {
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
