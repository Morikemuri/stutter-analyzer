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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SubmissionManager {

    private record PendingSubmission(FreezeReport report, String markdown, String reportHash) {}

    private static final ConcurrentHashMap<String, PendingSubmission> PENDING = new ConcurrentHashMap<>();

    private static final Executor UPLOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SA-Cloudflare-Upload");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .executor(UPLOAD_EXECUTOR)
        .build();

    // ── Public entry points ───────────────────────────────────────────────────

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
        if (!isCloudflareEnabled()) {
            return submitLocalLast(src);
        }
        if (needsConsent()) {
            return prepareForConsent(src, report);
        }
        String markdown = report.toMarkdown();
        String hash = sha256Hex(markdown);
        submitToCloudflare(src, report, markdown, hash);
        return 1;
    }

    public static int submitById(CommandSourceStack src, String reportId) {
        if (!SAConfig.INSTANCE.enableManualSubmission.get()) {
            src.sendFailure(CommandFeedback.error(Component.translatable("stutteranalyzer.submit.disabled")));
            return 0;
        }
        FreezeReport last = ReportWriter.lastReport();
        if (last == null || !last.reportId.equals(reportId)) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.not_found", reportId)), false);
            return 1;
        }
        if (!isCloudflareEnabled()) {
            return submitLocalReport(src, last);
        }
        if (needsConsent()) {
            return prepareForConsent(src, last);
        }
        String markdown = last.toMarkdown();
        submitToCloudflare(src, last, markdown, sha256Hex(markdown));
        return 1;
    }

    public static int submitPrepareLast(CommandSourceStack src) {
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
        String hash = sha256Hex(markdown);
        PENDING.put(report.reportId, new PendingSubmission(report, markdown, hash));

        try {
            Path dir = resolveSubmissionsDir();
            Files.createDirectories(dir);
            Path mdFile = dir.resolve(report.reportId + "-prepared.md");
            Files.writeString(mdFile, markdown);
            String mdPath = mdFile.toString();
            String rid = report.reportId;
            src.sendSuccess(() -> CommandFeedback.success("[SA] Report prepared: " + rid), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] Review: " + mdPath), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] Run /sa submit confirm " + rid + " to upload."), false);
        } catch (Exception e) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Could not save prepared report: " + e.getMessage()), false);
        }
        return 1;
    }

    public static int submitConfirm(CommandSourceStack src, String preparedId) {
        if (!SAConfig.INSTANCE.enableManualSubmission.get()) {
            src.sendFailure(CommandFeedback.error(Component.translatable("stutteranalyzer.submit.disabled")));
            return 0;
        }
        PendingSubmission pending = PENDING.remove(preparedId);
        if (pending == null) {
            FreezeReport last = ReportWriter.lastReport();
            if (last != null && last.reportId.equals(preparedId)) {
                String md = last.toMarkdown();
                pending = new PendingSubmission(last, md, sha256Hex(md));
            }
        }
        if (pending == null) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] No prepared report found with ID: " + preparedId), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] Run /sa submit prepare last first."), false);
            return 1;
        }
        final PendingSubmission sub = pending;
        markConsentGiven();
        submitToCloudflare(src, sub.report(), sub.markdown(), sub.reportHash());
        return 1;
    }

    public static int submitLocalLast(CommandSourceStack src) {
        if (!SAConfig.INSTANCE.enableManualSubmission.get()) {
            src.sendFailure(CommandFeedback.error(Component.translatable("stutteranalyzer.submit.disabled")));
            return 0;
        }
        FreezeReport report = ReportWriter.lastReport();
        if (report == null) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.no_report")), false);
            return 1;
        }
        return submitLocalReport(src, report);
    }

    public static int submitStatus(CommandSourceStack src) {
        boolean cfEnabled = isCloudflareEnabled();
        boolean browserOpen = SAConfig.INSTANCE.openIssueUrlOnClient.get();
        boolean clipboard = SAConfig.INSTANCE.copyIssueBodyToClipboard.get();
        boolean fallback = SAConfig.INSTANCE.fallbackToLocal.get();
        String target = SAConfig.INSTANCE.submissionTarget.get();

        src.sendSuccess(() -> CommandFeedback.header("[SA] Submission status"), false);
        src.sendSuccess(() -> CommandFeedback.row("Mode", cfEnabled ? "Cloudflare" : target), false);
        src.sendSuccess(() -> CommandFeedback.row("Endpoint", cfEnabled ? "configured" : "not set"), false);
        src.sendSuccess(() -> CommandFeedback.row("Remote submission", cfEnabled ? "enabled" : "disabled"), false);
        src.sendSuccess(() -> CommandFeedback.row("GitHub forwarding", cfEnabled ? "server-side" : "disabled"), false);
        src.sendSuccess(() -> CommandFeedback.row("Browser opening", browserOpen ? "enabled" : "disabled"), false);
        src.sendSuccess(() -> CommandFeedback.row("Clipboard issue body", clipboard ? "enabled" : "disabled"), false);
        src.sendSuccess(() -> CommandFeedback.row("Fallback", fallback ? "local" : "none"), false);
        return 1;
    }

    public static int submitCrashLast(CommandSourceStack src) {
        CrashEvent ce = PreviousCrashImporter.last();
        if (ce == null) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.no_crash")), false);
            return 1;
        }
        return submitLocalRaw(src, ce.crashId, buildCrashMarkdown(ce), buildCrashJson(ce), buildCrashIssueBody(ce));
    }

    public static int submitGuardLast(CommandSourceStack src) {
        EmergencyGuardReport rep = EmergencyGuardManager.lastReport();
        if (rep == null) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.no_guard")), false);
            return 1;
        }
        return submitLocalRaw(src, rep.guardId, rep.toMarkdown(), "{}", buildGuardIssueBody(rep));
    }

    // ── Cloudflare submission ─────────────────────────────────────────────────

    private static boolean isCloudflareEnabled() {
        String target = SAConfig.INSTANCE.submissionTarget.get();
        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        return "cloudflare".equalsIgnoreCase(target) && !endpoint.isBlank();
    }

    private static boolean needsConsent() {
        if (SAConfig.INSTANCE.askEveryTime.get()) return true;
        if (!SAConfig.INSTANCE.askFirstTime.get()) return false;
        return !isConsentGiven();
    }

    private static boolean isConsentGiven() {
        try {
            Path f = resolveConsentFile();
            return Files.exists(f) && Files.readString(f).trim().equals("accepted");
        } catch (Exception e) {
            return false;
        }
    }

    private static void markConsentGiven() {
        try {
            Path f = resolveConsentFile();
            Files.createDirectories(f.getParent());
            Files.writeString(f, "accepted");
        } catch (Exception ignored) {}
    }

    private static int prepareForConsent(CommandSourceStack src, FreezeReport report) {
        String markdown = report.toMarkdown();
        String hash = sha256Hex(markdown);
        PENDING.put(report.reportId, new PendingSubmission(report, markdown, hash));
        String rid = report.reportId;
        src.sendSuccess(() -> CommandFeedback.info("[SA] This will send a sanitized performance report to the Stutter Analyzer report server."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] It may include mod list, Minecraft version, Java version, system info, and recent performance events."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] It will not include tokens, passwords, auth data, full file paths, or session data."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] GitHub issue creation happens server-side. You do not need a GitHub account."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Run /sa submit confirm " + rid + " to send."), false);
        return 1;
    }

    private static void submitToCloudflare(CommandSourceStack src, FreezeReport report, String markdown, String reportHash) {
        src.sendSuccess(() -> CommandFeedback.info("[SA] Preparing latest report..."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Report sanitized."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Uploading report to Stutter Analyzer report server..."), false);

        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        String payload = buildCloudflarePayload(
            report.reportId, report.event.category().name(),
            report.event.durationMs(), report.event.confidence(),
            markdown, reportHash);

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "StutterAnalyzer/1.0.0 Minecraft/1.20.4")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                int status = resp.statusCode();
                String body = resp.body();

                handleCloudflareResponse(src, report, markdown, status, body);
            } catch (Exception e) {
                StutterAnalyzerMod.LOGGER.warn("[SA] Cloudflare submit error: {}", e.getMessage());
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Report server is unavailable."), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] No data was lost."), false);
                if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
            }
        }, UPLOAD_EXECUTOR);
    }

    private static void handleCloudflareResponse(CommandSourceStack src, FreezeReport report,
                                                  String markdown, int status, String body) {
        if (status == 409) {
            src.sendSuccess(() -> CommandFeedback.info("[SA] This report was already submitted."), false);
            return;
        }
        if (status == 429) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Report server rate limit reached. Local fallback saved."), false);
            if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
            return;
        }
        if (status == 200 && body.contains("\"ok\":true")) {
            String reportId   = extractJsonField(body, "report_id");
            String issueNum   = extractJsonField(body, "github_issue_number");
            String warning    = extractJsonField(body, "warning");
            String finalId    = reportId != null ? reportId : report.reportId;

            src.sendSuccess(() -> CommandFeedback.success("[SA] Report submitted successfully."), false);

            if ("GITHUB_FORWARD_FAILED".equals(warning)) {
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Report submitted and stored."), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] GitHub forwarding failed on server side, but no data was lost."), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] Report ID: " + finalId), false);
            } else if (issueNum != null && !issueNum.equals("null")) {
                src.sendSuccess(() -> CommandFeedback.info("[SA] GitHub issue created: #" + issueNum), false);
            } else {
                src.sendSuccess(() -> CommandFeedback.info("[SA] Report ID: " + finalId), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] Stored for developer review."), false);
            }
            src.sendSuccess(() -> CommandFeedback.info("[SA] Thank you. This helps improve future versions."), false);
            markConsentGiven();
        } else {
            StutterAnalyzerMod.LOGGER.warn("[SA] Cloudflare submit failed: {} {}", status, body);
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Report server is unavailable."), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] No data was lost."), false);
            if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
        }
    }

    private static void saveLocalFallback(CommandSourceStack src, FreezeReport report, String markdown) {
        try {
            Path dir = resolveSubmissionsDir();
            Files.createDirectories(dir);
            Path mdFile = dir.resolve(report.reportId + ".md");
            Files.writeString(mdFile, markdown);
            src.sendSuccess(() -> CommandFeedback.info("[SA] Local fallback saved:"), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] config/stutter-analyzer/submissions/" + report.reportId + ".md"), false);
        } catch (Exception ex) {
            StutterAnalyzerMod.LOGGER.error("[SA] Failed to save local fallback: {}", ex.getMessage());
        }
    }

    // ── Local submission ──────────────────────────────────────────────────────

    private static int submitLocalReport(CommandSourceStack src, FreezeReport report) {
        String markdown = report.toMarkdown();
        String json = report.toJson();
        String issueBody = buildFreezeIssueBody(report);
        return submitLocalRaw(src, report.reportId, markdown, json, issueBody);
    }

    private static int submitLocalRaw(CommandSourceStack src, String id,
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
                if (SAConfig.INSTANCE.copyIssueBodyToClipboard.get()) copyToClipboard(src, issueBody);
                if (SAConfig.INSTANCE.openIssueUrlOnClient.get()) openBrowser(src, issueUrl);
            }
        } catch (Exception e) {
            StutterAnalyzerMod.LOGGER.error("[SA] Failed to write submission files: {}", e.getMessage(), e);
            src.sendFailure(CommandFeedback.error(Component.literal("Failed to write submission files: " + e.getMessage())));
            return 0;
        }
        return 1;
    }

    // ── Client extras ─────────────────────────────────────────────────────────

    private static void copyToClipboard(CommandSourceStack src, String text) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> {
                try {
                    org.lwjgl.glfw.GLFW.glfwSetClipboardString(mc.getWindow().getWindow(), text);
                } catch (Exception ex) {
                    StutterAnalyzerMod.LOGGER.warn("[SA] GLFW clipboard failed: {}", ex.getMessage());
                }
            });
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.copied_to_clipboard")), false);
        } catch (Exception e) {
            StutterAnalyzerMod.LOGGER.warn("[SA] Clipboard copy failed: {}", e.getMessage());
        }
    }

    private static void openBrowser(CommandSourceStack src, String url) {
        try {
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                try {
                    net.minecraft.Util.getPlatform().openUri(new URI(url));
                } catch (Exception e) {
                    StutterAnalyzerMod.LOGGER.warn("[SA] Failed to open browser: {}", e.getMessage());
                }
            });
            src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.opening_issue_url")), false);
        } catch (Exception e) {
            StutterAnalyzerMod.LOGGER.warn("[SA] Failed to schedule browser open: {}", e.getMessage());
        }
    }

    // ── Path resolution ───────────────────────────────────────────────────────

    private static Path resolveSubmissionsDir() {
        try {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config/stutter-analyzer/submissions");
            }
        } catch (Exception ignored) {}
        return FMLPaths.GAMEDIR.get().resolve("config/stutter-analyzer/submissions");
    }

    private static Path resolveConsentFile() {
        try {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config/stutter-analyzer/submission-consent.txt");
            }
        } catch (Exception ignored) {}
        return FMLPaths.GAMEDIR.get().resolve("config/stutter-analyzer/submission-consent.txt");
    }

    // ── Payload builder ───────────────────────────────────────────────────────

    private static String buildCloudflarePayload(String id, String category, long durationMs, double confidence,
                                                  String markdown, String reportHash) {
        return "{\n" +
            "  \"schema_version\": 1,\n" +
            "  \"project\": \"stutter-analyzer\",\n" +
            "  \"mod_version\": \"" + StutterAnalyzerMod.MOD_VERSION + "\",\n" +
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

    // ── Issue body builders ───────────────────────────────────────────────────

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
            "*Prepared via StutterAnalyzer /sa submit local last*\n";
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

    // ── Utilities ─────────────────────────────────────────────────────────────

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

    private static String esc(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
