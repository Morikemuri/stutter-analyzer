package com.stutteranalyzer.submission;

import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.command.CommandFeedback;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.crash.CrashEvent;
import com.stutteranalyzer.crash.PreviousCrashImporter;
import com.stutteranalyzer.guard.EmergencyGuardManager;
import com.stutteranalyzer.guard.EmergencyGuardReport;
import com.stutteranalyzer.report.FreezeReport;
import com.stutteranalyzer.report.ReportWriter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Prepares local submission files only.
 * No upload. No tokens. No Gist. Reports stay on disk until the user submits manually.
 */
public class SubmissionManager {

    // Dev-only upload gate: requires both a system property AND an environment variable.
    // Never active in public builds. Never stored. Never logged.
    private static final boolean DEV_UPLOAD_ENABLED =
        Boolean.getBoolean("stutterAnalyzer.devUpload") &&
        System.getenv("STUTTER_ANALYZER_GITHUB_TOKEN") != null &&
        !System.getenv("STUTTER_ANALYZER_GITHUB_TOKEN").isBlank();

    public static int submitLast(CommandSourceStack src) {
        if (!SAConfig.INSTANCE.enableManualSubmission.get()) {
            src.sendFailure(CommandFeedback.error(Component.translatable("stutteranalyzer.submit.disabled")));
            return 0;
        }
        FreezeReport report = ReportWriter.lastReport();
        if (report == null) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.no_report")), false);
            return 1;
        }
        return prepareLocally(src, report.reportId, report.toMarkdown(), report.toJson(), buildFreezeIssueBody(report));
    }

    public static int submitById(CommandSourceStack src, String reportId) {
        if (!SAConfig.INSTANCE.enableManualSubmission.get()) {
            src.sendFailure(CommandFeedback.error(Component.translatable("stutteranalyzer.submit.disabled")));
            return 0;
        }
        FreezeReport last = ReportWriter.lastReport();
        if (last != null && last.reportId.equals(reportId)) {
            return prepareLocally(src, last.reportId, last.toMarkdown(), last.toJson(), buildFreezeIssueBody(last));
        }
        src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.not_found", reportId)), false);
        return 1;
    }

    public static int submitCrashLast(CommandSourceStack src) {
        CrashEvent ce = PreviousCrashImporter.last();
        if (ce == null) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.no_crash")), false);
            return 1;
        }
        return prepareLocally(src, ce.crashId, buildCrashMarkdown(ce), buildCrashJson(ce), buildCrashIssueBody(ce));
    }

    public static int submitGuardLast(CommandSourceStack src) {
        EmergencyGuardReport rep = EmergencyGuardManager.lastReport();
        if (rep == null) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.no_guard")), false);
            return 1;
        }
        return prepareLocally(src, rep.guardId, rep.toMarkdown(), "{}", buildGuardIssueBody(rep));
    }

    // ── Core: prepare local files ─────────────────────────────────────────

    private static int prepareLocally(CommandSourceStack src, String id,
                                      String markdown, String json, String issueBody) {
        Path submissionsDir = resolveSubmissionsDir();

        try {
            Files.createDirectories(submissionsDir);

            Path mdPath    = submissionsDir.resolve(id + ".md");
            Path jsonPath  = submissionsDir.resolve(id + ".json");
            Path issuePath = submissionsDir.resolve(id + "-github-issue-body.md");

            Files.writeString(mdPath, markdown);
            Files.writeString(jsonPath, json);
            Files.writeString(issuePath, issueBody);

            String mdStr    = mdPath.toString();
            String jsonStr  = jsonPath.toString();
            String issueStr = issuePath.toString();
            String issueUrl = SAConfig.INSTANCE.githubIssueUrl.get();

            src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.submit.prepared")), false);
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.prepared_md", mdStr)), false);
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.prepared_json", jsonStr)), false);
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.prepared_issue_body", issueStr)), false);
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.prepared_open_hint")), false);
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.prepared_issue_url", issueUrl)), false);
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.prepared_no_upload")), false);

            if (FMLEnvironment.dist == Dist.CLIENT) {
                if (SAConfig.INSTANCE.copyIssueBodyToClipboard.get()) {
                    copyToClipboard(src, issueBody);
                }
                if (SAConfig.INSTANCE.openIssueUrlOnClient.get()) {
                    openBrowser(src, issueUrl);
                }
            }

        } catch (Exception e) {
            StutterAnalyzerMod.LOGGER.error("[StutterAnalyzer] Failed to write submission files: {}", e.getMessage(), e);
            src.sendFailure(CommandFeedback.error(Component.literal("Failed to write submission files: " + e.getMessage())));
            return 0;
        }

        return 1;
    }

    // ── Client extras ─────────────────────────────────────────────────────

    private static void copyToClipboard(CommandSourceStack src, String text) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> {
                try {
                    org.lwjgl.glfw.GLFW.glfwSetClipboardString(mc.getWindow().getWindow(), text);
                } catch (Exception ex) {
                    StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] GLFW clipboard failed: {}", ex.getMessage());
                }
            });
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.copied_to_clipboard")), false);
        } catch (Exception e) {
            StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Clipboard copy failed: {}", e.getMessage());
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.clipboard_failed")), false);
        }
    }

    private static void openBrowser(CommandSourceStack src, String url) {
        try {
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                try {
                    net.minecraft.Util.getPlatform().openUri(new URI(url));
                } catch (Exception e) {
                    StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Failed to open browser: {}", e.getMessage());
                }
            });
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.opening_issue_url")), false);
        } catch (Exception e) {
            StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Failed to schedule browser open: {}", e.getMessage());
        }
    }

    // ── Path resolution ───────────────────────────────────────────────────

    private static Path resolveSubmissionsDir() {
        try {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config/stutter-analyzer/submissions");
            }
        } catch (Exception ignored) {}
        return FMLPaths.GAMEDIR.get().resolve("config/stutter-analyzer/submissions");
    }

    // ── Issue body builders ───────────────────────────────────────────────

    private static String buildFreezeIssueBody(FreezeReport report) {
        return "## StutterAnalyzer Freeze Report\n\n" +
            "**Report ID:** `" + report.reportId + "`\n" +
            "**Category:** " + report.event.category() + "\n" +
            "**Duration:** " + report.event.durationMs() + " ms\n" +
            "**Confidence:** " + report.event.confidencePct() + "%\n" +
            "**Side:** " + report.event.side() + "\n\n" +
            "## What were you doing?\n\n" +
            "(describe the situation when the freeze occurred)\n\n" +
            "## Attached files\n\n" +
            "Please attach or paste the contents of `" + report.reportId + ".md`" +
            " from `config/stutter-analyzer/submissions/`.\n\n" +
            "---\n" +
            "*Prepared via StutterAnalyzer /sa submit last*\n";
    }

    private static String buildCrashMarkdown(CrashEvent ce) {
        return "# Crash Report Import\n\n" +
            "**ID:** " + ce.crashId + "\n" +
            "**Timestamp:** " + ce.timestamp + "\n" +
            "**Type:** " + ce.crashType + "\n" +
            "**Summary:** " + ce.summary + "\n" +
            (ce.hasKnownPattern()
                ? "\n**Known Pattern:** " + ce.bestMatch().patternId +
                  " (" + ce.bestMatch().confidencePct() + "% confidence)\n"
                : "\n**Pattern:** Unknown\n");
    }

    private static String buildCrashJson(CrashEvent ce) {
        return "{\n" +
            "  \"crash_id\": \"" + ce.crashId + "\",\n" +
            "  \"timestamp\": \"" + ce.timestamp + "\",\n" +
            "  \"type\": \"" + esc(ce.crashType) + "\",\n" +
            "  \"summary\": \"" + esc(ce.summary) + "\",\n" +
            "  \"known_pattern\": " + (ce.hasKnownPattern() ? "\"" + ce.bestMatch().patternId + "\"" : "null") + "\n" +
            "}\n";
    }

    private static String buildCrashIssueBody(CrashEvent ce) {
        return "## Crash Report\n\n" +
            "**Crash ID:** `" + ce.crashId + "`\n" +
            "**Type:** " + ce.crashType + "\n" +
            "**Summary:** " + ce.summary + "\n\n" +
            "## What were you doing?\n\n" +
            "(describe the situation when the crash occurred)\n\n" +
            "## Attached files\n\n" +
            "Please attach or paste the contents of `" + ce.crashId + ".md`" +
            " from `config/stutter-analyzer/submissions/`.\n\n" +
            "---\n" +
            "*Prepared via StutterAnalyzer /sa submit crash last*\n";
    }

    private static String buildGuardIssueBody(EmergencyGuardReport rep) {
        return "## Emergency Guard Report\n\n" +
            "**Guard ID:** `" + rep.guardId + "`\n" +
            "**Pattern:** " + rep.patternId + "\n" +
            "**Outcome:** " + rep.outcome.name() + "\n" +
            "**Confidence:** " + (int)(rep.confidence * 100) + "%\n\n" +
            "## What were you doing?\n\n" +
            "(describe the situation)\n\n" +
            "## Attached files\n\n" +
            "Please attach or paste the contents of `" + rep.guardId + ".md`" +
            " from `config/stutter-analyzer/submissions/`.\n\n" +
            "---\n" +
            "*Prepared via StutterAnalyzer /sa submit guard last*\n";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
