package com.stutteranalyzer.command;

import com.stutteranalyzer.classifier.FreezeDetector;
import com.stutteranalyzer.config.SAConfig;
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
        boolean isDedicated = src.getServer().isDedicatedServer();
        boolean isClient = FMLEnvironment.dist == Dist.CLIENT;
        boolean degraded = SubsystemHealth.anyDegraded();

        Component state = Component.translatable(degraded
            ? "stutteranalyzer.cmd.status.state_degraded"
            : "stutteranalyzer.cmd.status.state_active");
        Component sideValue = Component.translatable(isDedicated
            ? "stutteranalyzer.cmd.status.side.dedicated"
            : isClient ? "stutteranalyzer.cmd.status.side.client_integrated" : "stutteranalyzer.cmd.status.side.integrated");
        Component lastFreezeValue = last != null
            ? Component.literal(last.category() + ", " + last.durationMs() + " ms")
            : Component.translatable("stutteranalyzer.cmd.status.no_freeze");

        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.status.header")), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.cmd.status.state"), state), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.side"), sideValue), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.client_tracker"),
            Component.translatable(isClient ? "stutteranalyzer.cmd.status.tracker_on" : "stutteranalyzer.cmd.status.tracker_unavailable")
        ), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.server_tracker"),
            Component.translatable("stutteranalyzer.cmd.status.tracker_on")
        ), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.f3_line"),
            Component.translatable(isClient ? "stutteranalyzer.cmd.status.f3_on" : "stutteranalyzer.cmd.status.f3_unavailable")
        ), false);
        src.sendSuccess(() -> CommandFeedback.row(Component.translatable("stutteranalyzer.row.last_freeze"), lastFreezeValue), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.row.reports_saved"),
            String.valueOf(ReportWriter.savedReports())
        ), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.unknown_freezes"),
            String.valueOf(com.stutteranalyzer.classifier.FreezeDetector.unknownFreezeCount())
        ), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.row.emergency_mode"),
            Component.translatable(SAConfig.INSTANCE.guardEmergencyMode.get()
                ? "stutteranalyzer.cmd.status.emergency_on"
                : "stutteranalyzer.cmd.status.emergency_off")
        ), false);
        src.sendSuccess(() -> CommandFeedback.row(
            Component.translatable("stutteranalyzer.cmd.status.submission"),
            Component.translatable("local".equalsIgnoreCase(SAConfig.INSTANCE.submissionTarget.get())
                ? "stutteranalyzer.cmd.status.submission_local"
                : "stutteranalyzer.cmd.status.submission_upload")
        ), false);
        if (degraded)
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.cmd.status.degraded")), false);
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

    public static int submitLast(CommandSourceStack src) {
        if (!CommandPermissionHelper.canSubmitReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        return SubmissionManager.submitLast(src);
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
        src.sendSuccess(() -> CommandFeedback.header(Component.translatable("stutteranalyzer.cmd.help.header")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.status")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.health")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.last")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.report")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.export")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.list")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.list_unknown")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.show")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.delete")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.submit_last")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.submit_id")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.submit_crash")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.submit_guard")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.crash_cmds")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.guard_cmds")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.f3")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.selfcheck")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.config")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.server")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.overlay")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.client_cmds")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.help.debug")), false);
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
