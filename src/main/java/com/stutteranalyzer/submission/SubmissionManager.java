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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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

    private static final Executor UPLOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SA-Cloudflare-Upload");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .executor(UPLOAD_EXECUTOR)
        .build();

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
        String markdown = report.toMarkdown();
        String json = report.toJson();
        String issueBody = buildFreezeIssueBody(report);
        if (isCloudflareEnabled()) {
            submitToCloudflare(src, report.reportId, markdown, json, issueBody, report.event.category().name(),
                report.event.durationMs(), report.event.confidence());
            return 1;
        }
        return prepareLocally(src, report.reportId, markdown, json, issueBody);
    }

    public static int submitById(CommandSourceStack src, String reportId) {
        if (!SAConfig.INSTANCE.enableManualSubmission.get()) {
            src.sendFailure(CommandFeedback.error(Component.translatable("stutteranalyzer.submit.disabled")));
            return 0;
        }
        FreezeReport last = ReportWriter.lastReport();
        if (last != null && last.reportId.equals(reportId)) {
            String markdown = last.toMarkdown();
            String json = last.toJson();
            String issueBody = buildFreezeIssueBody(last);
            if (isCloudflareEnabled()) {
                submitToCloudflare(src, last.reportId, markdown, json, issueBody, last.event.category().name(),
                    last.event.durationMs(), last.event.confidence());
                return 1;
            }
            return prepareLocally(src, last.reportId, markdown, json, issueBody);
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

    // ── Cloudflare submission ─────────────────────────────────────────────

    private static boolean isCloudflareEnabled() {
        String target = SAConfig.INSTANCE.submissionTarget.get();
        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        return "cloudflare".equalsIgnoreCase(target) && !endpoint.isBlank();
    }

    private static void submitToCloudflare(CommandSourceStack src, String id, String markdown, String json,
                                           String issueBody, String category, long durationMs, double confidence) {
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.cloudflare_uploading")), false);

        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        String reportHash = sha256Hex(markdown);
        String payload = buildCloudflarePayload(id, category, durationMs, confidence, markdown, json, reportHash);

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "StutterAnalyzer/0.1.0 Minecraft/1.20.4")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                int status = resp.statusCode();
                String body = resp.body();

                if (status == 200 && body.contains("\"ok\":true")) {
                    String reportId = extractJsonField(body, "report_id");
                    src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.submit.cloudflare_ok", reportId != null ? reportId : id)), false);
                    src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.cloudflare_no_account")), false);
                } else {
                    StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Cloudflare submit failed: {} {}", status, body);
                    src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.cloudflare_failed", status)), false);
                    if (SAConfig.INSTANCE.fallbackToLocal.get()) {
                        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.cloudflare_fallback")), false);
                        prepareLocally(src, id, markdown, json, issueBody);
                    }
                }
            } catch (Exception e) {
                StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Cloudflare submit error: {}", e.getMessage());
                src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.cloudflare_error")), false);
                if (SAConfig.INSTANCE.fallbackToLocal.get()) {
                    src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.cloudflare_fallback")), false);
                    prepareLocally(src, id, markdown, json, issueBody);
                }
            }
        }, UPLOAD_EXECUTOR);
    }

    private static String buildCloudflarePayload(String id, String category, long durationMs, double confidence,
                                                 String markdown, String jsonReport, String reportHash) {
        return "{\n" +
            "  \"schema_version\": 1,\n" +
            "  \"project\": \"stutter-analyzer\",\n" +
            "  \"mod_version\": \"0.1.0\",\n" +
            "  \"minecraft_version\": \"1.20.4\",\n" +
            "  \"loader\": \"forge\",\n" +
            "  \"loader_version\": \"49.x\",\n" +
            "  \"report_type\": " + esc(category) + ",\n" +
            "  \"category\": " + esc(category) + ",\n" +
            "  \"duration_ms\": " + durationMs + ",\n" +
            "  \"confidence\": " + String.format("%.4f", confidence) + ",\n" +
            "  \"report_hash\": " + esc(reportHash) + ",\n" +
            "  \"summary\": " + esc(category.replace('_', ' ').toLowerCase() + " detected (" + durationMs + " ms)") + ",\n" +
            "  \"markdown_report\": " + escJson(markdown) + ",\n" +
            "  \"json_report\": {},\n" +
            "  \"client_generated_at\": " + esc(Instant.now().toString()) + ",\n" +
            "  \"privacy\": {\n" +
            "    \"sanitized\": true,\n" +
            "    \"contains_mod_list\": " + SAConfig.INSTANCE.submissionIncludeModList.get() + ",\n" +
            "    \"contains_system_info\": " + SAConfig.INSTANCE.submissionIncludeSystemInfo.get() + ",\n" +
            "    \"contains_logs\": false\n" +
            "  }\n" +
            "}\n";
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Long.toHexString(input.hashCode() & 0xFFFFFFFFL);
        }
    }

    private static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int start = idx + key.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != '"' && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        return json.substring(start, end).trim();
    }

    private static String escJson(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"";
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
