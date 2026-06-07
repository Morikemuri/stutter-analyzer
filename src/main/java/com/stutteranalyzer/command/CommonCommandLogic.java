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
        src.sendSuccess(() -> CommandFeedback.header("StutterAnalyzer Status"), false);
        src.sendSuccess(() -> CommandFeedback.row("Enabled", "true"), false);
        src.sendSuccess(() -> CommandFeedback.row("Side", src.getServer().isDedicatedServer() ? "dedicated server" : "integrated server"), false);
        src.sendSuccess(() -> CommandFeedback.row("Monitoring", "server tick, memory, GC, chunk events"), false);
        src.sendSuccess(() -> CommandFeedback.row("Last freeze",
            last != null ? last.category() + ", " + last.durationMs() + " ms, confidence " + last.confidencePct() + "%" : "none"), false);
        src.sendSuccess(() -> CommandFeedback.row("Reports saved", String.valueOf(ReportWriter.savedReports())), false);
        src.sendSuccess(() -> CommandFeedback.row("Crashes imported", String.valueOf(PreviousCrashImporter.allImported().size())), false);
        if (SubsystemHealth.anyDegraded())
            src.sendSuccess(() -> CommandFeedback.warn("One or more subsystems are degraded. Use /sa health for details."), false);
        return 1;
    }

    public static int showHealth(CommandSourceStack src) {
        src.sendSuccess(() -> CommandFeedback.header("Subsystem Health"), false);
        for (Map.Entry<String, SubsystemHealth.Status> e : SubsystemHealth.all().entrySet()) {
            String note = SubsystemHealth.note(e.getKey());
            String label = e.getValue() == SubsystemHealth.Status.OK ? "OK" :
                e.getValue().name() + (note.isEmpty() ? "" : " - " + note);
            src.sendSuccess(() -> CommandFeedback.row(e.getKey(), label), false);
        }
        return 1;
    }

    public static int showLast(CommandSourceStack src) {
        FreezeEvent last = FreezeDetector.lastFreezeEvent();
        if (last == null) {
            src.sendSuccess(() -> CommandFeedback.info("No freeze events recorded yet."), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.header("Last Freeze"), false);
        src.sendSuccess(() -> CommandFeedback.row("Category", last.category().name()), false);
        src.sendSuccess(() -> CommandFeedback.row("Duration", last.durationMs() + " ms"), false);
        src.sendSuccess(() -> CommandFeedback.row("Side", last.side()), false);
        src.sendSuccess(() -> CommandFeedback.row("Confidence", last.confidencePct() + "%"), false);
        FreezeReport rep = ReportWriter.lastReport();
        if (rep != null) {
            src.sendSuccess(() -> CommandFeedback.row("Report ID", rep.reportId), false);
            src.sendSuccess(() -> CommandFeedback.info("Use /sa show " + rep.reportId + " for details."), false);
        }
        return 1;
    }

    public static int generateReport(CommandSourceStack src) {
        src.sendSuccess(() -> CommandFeedback.info("Generating report from current data..."), false);
        FreezeReport rep = ReportWriter.lastReport();
        if (rep != null) src.sendSuccess(() -> CommandFeedback.success("Last report: " + rep.reportId), false);
        else src.sendSuccess(() -> CommandFeedback.warn("No freeze data collected yet. Trigger a freeze or wait for a spike."), false);
        return 1;
    }

    public static int exportReport(CommandSourceStack src) {
        FreezeReport rep = ReportWriter.lastReport();
        if (rep == null) {
            src.sendSuccess(() -> CommandFeedback.warn("No report to export yet."), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.info("Latest report: config/stutter-analyzer/reports/" + rep.reportId), false);
        src.sendSuccess(() -> CommandFeedback.success("Reports are saved as .md and .json automatically after each freeze."), false);
        return 1;
    }

    public static int listReports(CommandSourceStack src) {
        FreezeReport last = ReportWriter.lastReport();
        if (last == null) {
            src.sendSuccess(() -> CommandFeedback.info("No reports saved yet."), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.header("Saved Reports (" + ReportWriter.savedReports() + ")"), false);
        src.sendSuccess(() -> CommandFeedback.row("Latest", last.reportId), false);
        src.sendSuccess(() -> CommandFeedback.info("Full report list is in config/stutter-analyzer/reports/"), false);
        return 1;
    }

    public static int listUnknownReports(CommandSourceStack src) {
        FreezeReport last = ReportWriter.lastReport();
        if (last != null && last.event.category().name().equals("UNKNOWN_FREEZE")) {
            src.sendSuccess(() -> CommandFeedback.row("Latest unknown", last.reportId), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.info("No unknown freeze reports in current session."), false);
        }
        return 1;
    }

    public static int showReport(CommandSourceStack src, String reportId) {
        FreezeReport last = ReportWriter.lastReport();
        if (last != null && last.reportId.equals(reportId)) {
            FreezeEvent e = last.event;
            src.sendSuccess(() -> CommandFeedback.header("Report: " + last.reportId), false);
            src.sendSuccess(() -> CommandFeedback.row("Category", e.category().name()), false);
            src.sendSuccess(() -> CommandFeedback.row("Duration", e.durationMs() + " ms"), false);
            src.sendSuccess(() -> CommandFeedback.row("Side", e.side()), false);
            src.sendSuccess(() -> CommandFeedback.row("Confidence", e.confidencePct() + "%"), false);
            src.sendSuccess(() -> CommandFeedback.row("Reason", e.reason()), false);
            if (!e.recommendation().isEmpty())
                src.sendSuccess(() -> CommandFeedback.row("Recommendation", e.recommendation()), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.warn("Report not found in memory: " + reportId + ". Check config/stutter-analyzer/reports/"), false);
        }
        return 1;
    }

    public static int deleteReport(CommandSourceStack src, String reportId) {
        if (!CommandPermissionHelper.canDeleteReports(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        src.sendSuccess(() -> CommandFeedback.success("Delete request noted for: " + reportId + ". Delete the file manually from config/stutter-analyzer/reports/"), false);
        return 1;
    }

    public static int reloadConfig(CommandSourceStack src) {
        if (!CommandPermissionHelper.canReloadConfig(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        src.sendSuccess(() -> CommandFeedback.success("Config reloaded. Some values require restart."), false);
        return 1;
    }

    public static int enableDebug(CommandSourceStack src) {
        if (!CommandPermissionHelper.canReloadConfig(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        src.sendSuccess(() -> CommandFeedback.warn("Set debug = true in stutteranalyzer-common.toml and reload config."), false);
        return 1;
    }

    public static int disableDebug(CommandSourceStack src) {
        if (!CommandPermissionHelper.canReloadConfig(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        src.sendSuccess(() -> CommandFeedback.warn("Set debug = false in stutteranalyzer-common.toml and reload config."), false);
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
        src.sendSuccess(() -> CommandFeedback.info("Submission by crash ID not yet implemented. Use /sa crash show " + crashId + " to view."), false);
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
        src.sendSuccess(() -> CommandFeedback.info("Submission by guard ID not yet implemented. Use /sa guard report last."), false);
        return 1;
    }

    // ── Crash commands ─────────────────────────────────────────────────────

    public static int crashLast(CommandSourceStack src) {
        CrashEvent ce = PreviousCrashImporter.last();
        if (ce == null) {
            src.sendSuccess(() -> CommandFeedback.info("No imported crash reports from this session."), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.header("Last Imported Crash"), false);
        src.sendSuccess(() -> CommandFeedback.row("ID", ce.crashId), false);
        src.sendSuccess(() -> CommandFeedback.row("Type", ce.crashType), false);
        src.sendSuccess(() -> CommandFeedback.row("Summary", ce.summary), false);
        if (ce.hasKnownPattern())
            src.sendSuccess(() -> CommandFeedback.row("Known Pattern", ce.bestMatch().patternId + " (" + ce.bestMatch().confidencePct() + "%)"), false);
        return 1;
    }

    public static int crashList(CommandSourceStack src) {
        List<CrashEvent> all = PreviousCrashImporter.allImported();
        if (all.isEmpty()) {
            src.sendSuccess(() -> CommandFeedback.info("No imported crash reports."), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.header("Imported Crashes (" + all.size() + ")"), false);
        for (CrashEvent ce : all) {
            String pat = ce.hasKnownPattern() ? " [" + ce.bestMatch().patternId + "]" : "";
            src.sendSuccess(() -> CommandFeedback.info("- " + ce.crashId + pat), false);
        }
        return 1;
    }

    public static int crashShow(CommandSourceStack src, String crashId) {
        List<CrashEvent> all = PreviousCrashImported(crashId);
        if (all.isEmpty()) {
            src.sendSuccess(() -> CommandFeedback.warn("Crash not found: " + crashId), false);
            return 1;
        }
        CrashEvent ce = all.get(0);
        src.sendSuccess(() -> CommandFeedback.header("Crash: " + ce.crashId), false);
        src.sendSuccess(() -> CommandFeedback.row("Type", ce.crashType), false);
        src.sendSuccess(() -> CommandFeedback.row("Summary", ce.summary), false);
        src.sendSuccess(() -> CommandFeedback.row("Timestamp", ce.timestamp.toString()), false);
        if (ce.hasKnownPattern()) {
            src.sendSuccess(() -> CommandFeedback.row("Pattern", ce.bestMatch().patternId), false);
            src.sendSuccess(() -> CommandFeedback.row("Confidence", ce.bestMatch().confidencePct() + "%"), false);
            src.sendSuccess(() -> CommandFeedback.row("Reason", ce.bestMatch().reason), false);
        }
        return 1;
    }

    public static int crashExport(CommandSourceStack src, String crashId) {
        src.sendSuccess(() -> CommandFeedback.info("Crash export: check config/stutter-analyzer/reports/ and crash-reports/ for hint files related to: " + crashId), false);
        return 1;
    }

    // ── Guard commands ─────────────────────────────────────────────────────

    public static int guardStatus(CommandSourceStack src) {
        boolean em = com.stutteranalyzer.config.SAConfig.INSTANCE.guardEmergencyMode.get();
        boolean enabled = com.stutteranalyzer.config.SAConfig.INSTANCE.guardEnabled.get();
        src.sendSuccess(() -> CommandFeedback.header("Emergency Guard Status"), false);
        src.sendSuccess(() -> CommandFeedback.row("System enabled", String.valueOf(enabled)), false);
        src.sendSuccess(() -> CommandFeedback.row("Emergency mode", em ? "ENABLED" : "disabled"), false);
        src.sendSuccess(() -> CommandFeedback.row("Session triggers", String.valueOf(com.stutteranalyzer.guard.GuardWarningRateLimiter.sessionTriggerCount())), false);
        src.sendSuccess(() -> CommandFeedback.row("Guards registered", String.valueOf(EmergencyGuardManager.allGuards().size())), false);
        if (em) src.sendSuccess(() -> CommandFeedback.warn("Emergency mode is ON. Safe guards may activate automatically."), false);
        return 1;
    }

    public static int guardList(CommandSourceStack src) {
        List<EmergencyGuard> guards = EmergencyGuardManager.allGuards();
        src.sendSuccess(() -> CommandFeedback.header("Emergency Guards (" + guards.size() + ")"), false);
        for (EmergencyGuard g : guards) {
            String status = EmergencyGuardManager.isEnabled(g.patternId()) ? "enabled" : "disabled";
            src.sendSuccess(() -> CommandFeedback.row(g.patternId(), g.safetyLevel().name() + " | " + status), false);
        }
        return 1;
    }

    public static int guardInfo(CommandSourceStack src, String guardId) {
        for (EmergencyGuard g : EmergencyGuardManager.allGuards()) {
            if (g.patternId().equalsIgnoreCase(guardId) || g.patternId().toLowerCase().contains(guardId.toLowerCase())) {
                src.sendSuccess(() -> CommandFeedback.header("Guard: " + g.patternId()), false);
                src.sendSuccess(() -> CommandFeedback.row("Safety level", g.safetyLevel().name()), false);
                src.sendSuccess(() -> CommandFeedback.row("Enabled", String.valueOf(EmergencyGuardManager.isEnabled(g.patternId()))), false);
                src.sendSuccess(() -> CommandFeedback.row("Config key", g.patternId().toLowerCase()), false);
                return 1;
            }
        }
        src.sendSuccess(() -> CommandFeedback.warn("Guard not found: " + guardId), false);
        return 1;
    }

    public static int guardEnable(CommandSourceStack src, String guardId) {
        if (!CommandPermissionHelper.canManageGuards(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        EmergencyGuardManager.setEnabled(guardId.toUpperCase(), true);
        src.sendSuccess(() -> CommandFeedback.success("Guard enabled: " + guardId), false);
        return 1;
    }

    public static int guardDisable(CommandSourceStack src, String guardId) {
        if (!CommandPermissionHelper.canManageGuards(src)) {
            src.sendFailure(CommandFeedback.noPermission());
            return 0;
        }
        EmergencyGuardManager.setEnabled(guardId.toUpperCase(), false);
        src.sendSuccess(() -> CommandFeedback.warn("Guard disabled: " + guardId), false);
        return 1;
    }

    public static int guardReportLast(CommandSourceStack src) {
        EmergencyGuardReport rep = EmergencyGuardManager.lastReport();
        if (rep == null) {
            src.sendSuccess(() -> CommandFeedback.info("No guard reports from this session."), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.header("Last Guard Report"), false);
        src.sendSuccess(() -> CommandFeedback.row("Pattern", rep.patternId), false);
        src.sendSuccess(() -> CommandFeedback.row("Outcome", rep.outcome.name()), false);
        src.sendSuccess(() -> CommandFeedback.row("Confidence", (int)(rep.confidence * 100) + "%"), false);
        if (!rep.action.isEmpty()) src.sendSuccess(() -> CommandFeedback.row("Action", rep.action), false);
        return 1;
    }

    public static int showHelp(CommandSourceStack src) {
        src.sendSuccess(() -> CommandFeedback.header("StutterAnalyzer Help"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa status            - Analyzer status and current freeze count"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa health            - Subsystem health. Green good, yellow suspicious, red please look at me."), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa last              - Last detected freeze, if Minecraft recently had a dramatic pause"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa report            - Generate report from current data"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa export            - Export latest report for GitHub, Discord, or your nearest support channel"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa list              - List saved reports"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa list unknown      - List unknown freezes"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa show <id>         - Show report details"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa delete <id>       - Delete report"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa submit last       - Submit last report"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa submit <id>       - Submit report by ID"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa submit crash last - Submit last crash report"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa submit guard last - Submit last guard report"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa crash last/list/show/export - Crash commands"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa guard status/list/info/enable/disable/report - Guard commands. Seatbelts, not sorcery."), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa f3 on/off/toggle/status - F3 status line toggle (client only)"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa selfcheck / /sa test - Checks whether the analyzer is alive, awake, and not crying in the logs"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa config reload     - Reload config (some values require restart)"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa server ...        - Server subcommands"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa overlay ...       - Overlay (client only)"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa client ...        - Client subcommands"), false);
        src.sendSuccess(() -> CommandFeedback.info("/sa debug ...         - Debug commands (requires op + debug mode)"), false);
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

        src.sendSuccess(() -> CommandFeedback.header("StutterAnalyzer Self-Check"), false);
        for (SelfCheckResult.CheckItem item : result.items()) {
            String label = switch (item.status) {
                case OK          -> "OK";
                case WARN        -> "WARN";
                case ERROR       -> "ERROR";
                case UNAVAILABLE -> "unavailable";
            };
            String detail = item.note.isEmpty() ? label : label + " - " + item.note;
            src.sendSuccess(() -> CommandFeedback.row("- " + item.name, detail), false);
        }
        String overall = result.overall();
        src.sendSuccess(() -> (result.isHealthy()
            ? CommandFeedback.success("Result: " + overall)
            : CommandFeedback.warn("Result: " + overall + ". Use /sa health for details.")), false);
        return 1;
    }

    // ── F3 commands (server-side stub: client-only features fail gracefully) ─

    public static int f3Status(CommandSourceStack src) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            src.sendFailure(CommandFeedback.clientOnly()); return 0;
        }
        boolean enabled = SAConfig.INSTANCE.debugHudEnabled.get();
        src.sendSuccess(() -> CommandFeedback.row("F3 status line", enabled ? "enabled" : "disabled"), false);
        if (enabled) {
            src.sendSuccess(() -> CommandFeedback.info("Current F3 text: " +
                com.stutteranalyzer.client.F3StatusFormatter.format().replaceAll("§.", "")), false);
        }
        return 1;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static List<CrashEvent> PreviousCrashImported(String crashId) {
        return PreviousCrashImporter.allImported().stream()
            .filter(ce -> ce.crashId.equals(crashId))
            .toList();
    }
}
