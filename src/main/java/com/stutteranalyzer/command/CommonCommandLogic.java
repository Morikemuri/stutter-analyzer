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

import com.stutteranalyzer.events.RecentEventBuffer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        // Last tracked spike
        if (lastDurationMs > 0) {
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.cmd.status.last_spike"),
                Component.literal(lastSeverity + " " + lastDurationMs + "ms")), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.cmd.status.last_spike"),
                Component.translatable("stutteranalyzer.cmd.status.last_spike_none")), false);
        }

        // Episode/frame counts
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
        if (showRaw && useEpisodes && mediumRaw != mediumDisplay) {
            Component rawMediumComp = Component.translatable("stutteranalyzer.cmd.status.count_in_60s", mediumRaw);
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.cmd.status.medium_frames"), rawMediumComp), false);
        }
        Component severeCountComp = worstSevere > 0
            ? Component.translatable("stutteranalyzer.cmd.status.count_in_60s_worst", severeDisplay, worstSevere)
            : Component.translatable("stutteranalyzer.cmd.status.count_in_60s", severeDisplay);
        src.sendSuccess(() -> CommandFeedback.row(severeLabel, severeCountComp), false);
        Component extremeCountComp = worstExtreme > 0
            ? Component.translatable("stutteranalyzer.cmd.status.count_in_60s_worst", extremeDisplay, worstExtreme)
            : Component.translatable("stutteranalyzer.cmd.status.count_in_60s", extremeDisplay);
        src.sendSuccess(() -> CommandFeedback.row(extremeLabel, extremeCountComp), false);

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

        // Last saved report
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

        // Status notes about report saving
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

        // Crashes imported
        int crashCount = PreviousCrashImporter.allImported().size();
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.row.crashes_imported"),
            String.valueOf(crashCount)), false);

        // Aggregate cooldown
        long aggRemaining = StutterCounter.aggregateCooldownRemainingSeconds();
        if (aggRemaining > 0) {
            src.sendSuccess(() -> CommandFeedback.row(
                Component.translatable("stutteranalyzer.cmd.status.agg_cooldown"),
                Component.translatable("stutteranalyzer.cmd.status.agg_cooldown_val", aggRemaining)), false);
        }

        // Chat notifications
        boolean chatSevere = SAConfig.INSTANCE.chatNotifySevereStutters.get();
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.chat_severe"),
            Component.translatable(chatSevere ? "stutteranalyzer.alerts.status.on" : "stutteranalyzer.alerts.status.off_val")), false);

        // Submission target
        String subTarget = SAConfig.INSTANCE.submissionTarget.get();
        Component subDisplay = "cloudflare".equalsIgnoreCase(subTarget)
            ? Component.translatable("stutteranalyzer.cmd.status.submission_cloudflare")
            : Component.translatable("stutteranalyzer.cmd.status.submission_local");
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.submission"), subDisplay), false);

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

    public static int showVersion(CommandSourceStack src) {
        boolean cfEnabled = SubmissionManager.isCloudflareEnabled();
        Component submitDisplay = Component.translatable(cfEnabled ? "stutteranalyzer.version.submit_cloudflare" : "stutteranalyzer.version.submit_local");
        Component uploadDisplay = Component.translatable(cfEnabled ? "stutteranalyzer.version.upload_ready" : "stutteranalyzer.version.upload_local");
        String javaVersion = System.getProperty("java.version", "unknown");
        int javaMajor = 0;
        try { javaMajor = Integer.parseInt(javaVersion.contains(".") ? javaVersion.split("[._-]")[0].equals("1") ? javaVersion.split("[._-]")[1] : javaVersion.split("[._-]")[0] : javaVersion); } catch (Exception ignored) {}
        final String javaDisplay = javaMajor > 0 ? String.valueOf(javaMajor) : javaVersion;
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.version.header")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.version"), Component.literal(StutterAnalyzerMod.MOD_VERSION)), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.minecraft"), Component.literal("1.20.4")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.loader"), Component.translatable("stutteranalyzer.version.loader.forge")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.java"), Component.literal(javaDisplay)), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.build"), Component.literal(StutterAnalyzerMod.BUILD_ID)), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.submit"), submitDisplay), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.upload"), uploadDisplay), false);
        // Update status (cached only - non-blocking)
        UpdateCheckResult updateResult = UpdateChecker.getCached();
        boolean updatesEnabled = SAConfig.INSTANCE.checkForUpdates.get();
        Component updateStatus;
        if (!updatesEnabled) {
            updateStatus = Component.translatable("stutteranalyzer.version.updates.disabled");
        } else if (updateResult == null) {
            UpdateChecker.performCheckAsync();
            updateStatus = Component.translatable("stutteranalyzer.version.updates.checking");
        } else if (!updateResult.success()) {
            updateStatus = Component.translatable("stutteranalyzer.version.updates.failed");
        } else if (updateResult.updateAvailable()) {
            final String latest = updateResult.latestVersion();
            updateStatus = Component.translatable("stutteranalyzer.version.updates.available", latest);
        } else {
            updateStatus = Component.translatable("stutteranalyzer.version.updates.current");
        }
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.version.updates"), updateStatus), false);
        // Always show project links - /sa update is gone, this is now the one stop shop
        Component cfLink = Component.translatable("stutteranalyzer.version.link.open_page")
            .withStyle(s -> s
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                    net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,
                    "https://www.curseforge.com/minecraft/mc-mods/stutter-analyzer/"))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                    net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                    Component.translatable("stutteranalyzer.version.link.hover.curseforge")))
                .withColor(net.minecraft.ChatFormatting.AQUA)
                .withUnderlined(true));
        Component ghLink = Component.translatable("stutteranalyzer.version.link.open_repo")
            .withStyle(s -> s
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                    net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,
                    "https://github.com/Morikemuri/stutter-analyzer"))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                    net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                    Component.translatable("stutteranalyzer.version.link.hover.github")))
                .withColor(net.minecraft.ChatFormatting.AQUA)
                .withUnderlined(true));
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.version.link.curseforge"), cfLink), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.version.link.github"), ghLink), false);
        return 1;
    }

    // ── Alerts ────────────────────────────────────────────────────────────

    public static int alertsSetMode(CommandSourceStack src, String modeName) {
        AlertMode mode = AlertMode.fromString(modeName);
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
        } else {
            src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.alerts.set.medium")), false);
        }
        return 1;
    }

    public static int alertsCooldown(CommandSourceStack src, int seconds) {
        int clamped = Math.max(5, Math.min(600, seconds));
        if (clamped != seconds) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.alerts.cooldown.range")), false);
            return 0;
        }
        SAConfig.INSTANCE.alertCooldownSeconds.set(clamped);
        src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.alerts.cooldown.set", clamped)), false);
        return 1;
    }

    public static int alertsStatus(CommandSourceStack src) {
        AlertMode mode = AlertManager.currentMode();
        int cooldown = SAConfig.INSTANCE.alertCooldownSeconds.get();
        int catCooldown = SAConfig.INSTANCE.alertSameCategoryCooldownSeconds.get();
        int maxPerMin = SAConfig.INSTANCE.alertMaxAlertsPerMinute.get();
        boolean agg = SAConfig.INSTANCE.alertAggregateSmallStutters.get();
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
        }
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.alerts.status.modes")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_minor")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_medium")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_severe")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_extreme")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.help.line.alerts_off")), false);
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
        src.sendSuccess(() -> CommandFeedback.info("Submitting latest report (crash-ID lookup not yet supported, submitting last report instead)."), false);
        return SubmissionManager.submitLast(src);
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
        src.sendSuccess(() -> CommandFeedback.info("Submitting latest report (guard-ID lookup not yet supported, submitting last report instead)."), false);
        return SubmissionManager.submitLast(src);
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
        Component spikeComp = last != null
            ? Component.literal(last.category().name().toLowerCase().replace('_', ' ') + " " + last.durationMs() + "ms")
            : Component.translatable("stutteranalyzer.cmd.status.last_spike_none");
        FreezeReport lastSavedRep = ReportWriter.lastReport();
        Component reportComp = lastSavedRep != null
            ? Component.literal(lastSavedRep.event.category().name() + " " + lastSavedRep.event.durationMs() + "ms")
            : Component.translatable("stutteranalyzer.cmd.status.last_report_none");
        Component uploadComp = Component.translatable(cfEnabled ? "stutteranalyzer.version.upload_ready" : "stutteranalyzer.version.upload_local");
        Component stateComp = Component.translatable(degraded ? "stutteranalyzer.cmd.status.state_degraded" : "stutteranalyzer.cmd.status.state_active");
        src.sendSuccess(() -> CommandFeedback.header("[SA] Stutter Analyzer"), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.row.status"), stateComp), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.last_spike"), spikeComp), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.last_report"), reportComp), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.row.reports_saved"),
            Component.literal(String.valueOf(reports))), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.row.upload"), uploadComp), false);
        if (reports > 0) {
            src.sendSuccess(() -> CommandFeedback.info(
                Component.translatable("stutteranalyzer.cmd.dashboard.submit_hint")), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.info(
                Component.translatable("stutteranalyzer.cmd.dashboard.status_hint")), false);
        }
        if (degraded) {
            src.sendSuccess(() -> CommandFeedback.warn(
                Component.translatable("stutteranalyzer.cmd.dashboard.degraded_hint")), false);
        }
        return 1;
    }

    public static int cancelLatestPending(CommandSourceStack src) {
        return SubmissionManager.cancelLatestPending(src);
    }

    public static int showPrivacy(CommandSourceStack src) {
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.privacy.header")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.privacy.line1")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.privacy.line2")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.privacy.line3")), false);
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

    public static int submitHealth(CommandSourceStack src) {
        return SubmissionManager.submitHealth(src);
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

    public static int overlayUnavailable(CommandSourceStack src) {
        src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.overlay.not_available")), false);
        src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.overlay.future")), false);
        src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.overlay.use_f3")), false);
        return 1;
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
            net.minecraftforge.fml.ModList.get().forEachModContainer((id, c) -> installedIds.add(id));
        } catch (Throwable t) {
            StutterAnalyzerMod.LOGGER.warn("[SA] Could not scan installed mods: {}", t.getMessage());
        }
        java.nio.file.Path gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
        String mcVersion = net.minecraft.SharedConstants.getCurrentVersion().getName();
        boolean isServer = !(FMLEnvironment.dist == Dist.CLIENT);

        com.stutteranalyzer.optimize.OptimizeInstaller.startScan();
        Thread worker = new Thread(() -> {
            long start = System.currentTimeMillis();
            try {
                com.stutteranalyzer.optimize.OptimizePlan plan =
                    com.stutteranalyzer.optimize.OptimizeAssistant.buildPlan(
                        installedIds, gameDir, "forge", mcVersion, isServer);
                com.stutteranalyzer.optimize.OptimizeInstaller.setPlan(plan, gameDir.resolve("mods"));
                com.stutteranalyzer.optimize.OptimizeInstaller.completeScan(buildPlanDisplay(plan, isServer));
            } catch (Throwable t) {
                StutterAnalyzerMod.LOGGER.warn("[SA] optimizeSuggest background task failed: {}", t.getMessage(), t);
                com.stutteranalyzer.optimize.OptimizeInstaller.completeScan(
                    java.util.List.of(CommandFeedback.info(
                        Component.translatable("stutteranalyzer.optimize.scan.failed", t.getMessage()))));
            } finally {
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed > 2000) {
                    StutterAnalyzerMod.LOGGER.info("[SA] Internal task slow: optimize_scan took {}ms", elapsed);
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
            StutterAnalyzerMod.LOGGER.warn("[SA] optimizeInstall failed: {}", t.getMessage(), t);
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
        out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.loaded_mods",
            plan.totalInstalledCount)));
        out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.optimization_active_count",
            plan.alreadyInstalled.size())));
        if (!plan.alreadyInstalled.isEmpty()) {
            out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.already_active",
                String.join(", ", plan.alreadyInstalled))));
        }
        if (!plan.pendingRestart.isEmpty()) {
            out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.optimization_pending_count",
                plan.pendingRestart.size())));
            out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.pending_restart",
                String.join(", ", plan.pendingRestart))));
        }
        if (plan.isEmpty()) {
            if (!plan.pendingRestart.isEmpty()) {
                out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.restart_required")));
                out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.no_plan_until_restart")));
            } else if (plan.alreadyInstalled.isEmpty()) {
                out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.no_suggestions")));
            } else {
                out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.already_optimized")));
            }
            return out;
        }
        Component riskLabel = Component.translatable(
            "stutteranalyzer.optimize.risk." + plan.risk.name().toLowerCase());
        out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.plan_risk",
            plan.recommended.size(), riskLabel)));
        int shown = plan.recommended.size();
        for (int i = 0; i < shown; i++) {
            com.stutteranalyzer.optimize.OptimizeMod mod = plan.recommended.get(i);
            int num = i + 1;
            Component reasonComp = Component.translatable("stutteranalyzer.optimize.reason." + mod.id);
            out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.mod_entry",
                num, mod.displayName, reasonComp)));
        }
        int remaining = 0;
        if (remaining > 0) {
            out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.more", remaining)));
        }
        if (!plan.skippedCandidates.isEmpty()) {
            out.add(CommandFeedback.info(Component.translatable("stutteranalyzer.optimize.skipped",
                plan.skippedCandidates.size(), plan.loader, plan.mcVersion)));
        }
        Component installBtn = Component.translatable("stutteranalyzer.optimize.btn.install")
            .withStyle(s -> s
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                    net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/sa optimize install"))
                .withUnderlined(true)
                .withColor(net.minecraft.ChatFormatting.GREEN));
        out.add(CommandFeedback.info(installBtn));
        StutterAnalyzerMod.LOGGER.info("[SA] Full optimization plan: {}",
            plan.recommended.stream().map(m -> m.displayName).collect(Collectors.joining(", ")));
        if (!plan.skippedCandidates.isEmpty()) {
            StutterAnalyzerMod.LOGGER.info("[SA] Skipped candidates (no file): {}",
                plan.skippedCandidates.stream().map(m -> m.id).collect(Collectors.joining(", ")));
        }
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
            String timeStr2 = fmt.format(e.timestamp);
            String line = num + ". " + e.detail + ", " + timeStr2;
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
