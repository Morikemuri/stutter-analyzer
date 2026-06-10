package com.stutteranalyzer.submission;

import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.classifier.FreezeDetector;
import com.stutteranalyzer.classifier.HighLevelCategory;
import com.stutteranalyzer.classifier.HighLevelClassifier;
import com.stutteranalyzer.command.CommandFeedback;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.MetricsCollector;
import com.stutteranalyzer.core.StutterCounter;
import com.stutteranalyzer.crash.CrashEvent;
import com.stutteranalyzer.crash.PreviousCrashImporter;
import com.stutteranalyzer.guard.EmergencyGuardManager;
import com.stutteranalyzer.guard.EmergencyGuardReport;
import com.stutteranalyzer.knowledge.ModInventory;
import com.stutteranalyzer.report.FreezeEvent;
import com.stutteranalyzer.report.FreezeReport;
import com.stutteranalyzer.report.ReportWriter;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SubmissionManager {

    private record PendingSubmission(FreezeReport report, String markdown, String reportHash) {}

    private static final ConcurrentHashMap<String, PendingSubmission> PENDING = new ConcurrentHashMap<>();
    private static volatile String lastPendingId = null;

    private static final Executor UPLOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SA-Cloudflare-Upload");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .executor(UPLOAD_EXECUTOR)
        .build();

    private static volatile String lastSubmissionStatus = "none";
    private static volatile boolean pendingMigrationNotice = false;
    private static final AtomicBoolean submissionInProgress = new AtomicBoolean(false);
    private static volatile long lastSubmissionEpochMs = 0L;
    private static volatile String currentUploadId = null;
    private static volatile long uploadStartMs = 0L;
    private static volatile String lastSubmissionError = "";
    private static volatile String sessionTransport = null; // session-learned working transport
    private static volatile String lastUploadStage = "none";
    private static volatile String lastUploadTiming = "";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private record PreparedUploadPayload(JsonObject json, String jsonString, byte[] bodyBytes, String sha256) {}

    public static void setPendingMigrationNotice() {
        pendingMigrationNotice = true;
    }

    // ── Public entry points ───────────────────────────────────────────────────

    public static int submitLast(CommandSourceStack src) {
        if (!SAConfig.INSTANCE.enableManualSubmission.get()) {
            src.sendFailure(CommandFeedback.error(Component.translatable("stutteranalyzer.submit.disabled")));
            return 0;
        }
        if (submissionInProgress.get()) {
            src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.already_in_progress")), false);
            return 1;
        }
        int cooldown = SAConfig.INSTANCE.submitCommandCooldownSeconds.get();
        long elapsedSec = (System.currentTimeMillis() - lastSubmissionEpochMs) / 1000L;
        if (cooldown > 0 && elapsedSec < cooldown) {
            src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.wait_cooldown")), false);
            return 1;
        }
        checkMigrationNotice(src);
        FreezeReport report = ReportWriter.lastReport();
        if (report == null) {
            src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.no_report_yet")), false);
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.no_report_hint")), false);
            return 1;
        }
        if (!isCloudflareEnabled()) {
            src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.no_endpoint")), false);
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.no_endpoint_hint")), false);
            return 1;
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
        lastPendingId = report.reportId;

        try {
            Path dir = resolveSubmissionsDir();
            Files.createDirectories(dir);
            Path mdFile = dir.resolve(report.reportId + "-prepared.md");
            Files.writeString(mdFile, markdown);
            String mdPath = mdFile.toString();
            String rid = report.reportId;
            src.sendSuccess(() -> CommandFeedback.success("[SA] Report prepared: " + rid), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] Review: " + mdPath), false);
            src.sendSuccess(() -> CommandFeedback.success("[SA] Run /sa submit to upload."), false);
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
            src.sendSuccess(() -> CommandFeedback.info("[SA] Run /sa submit preview first."), false);
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
        checkMigrationNotice(src);
        boolean cfEnabled = isCloudflareEnabled();
        boolean fallback = SAConfig.INSTANCE.fallbackToLocal.get();
        String target = SAConfig.INSTANCE.submissionTarget.get();

        src.sendSuccess(() -> CommandFeedback.header(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.header")), false);
        src.sendSuccess(() -> CommandFeedback.row(
            net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.mode"),
            net.minecraft.network.chat.Component.literal(cfEnabled ? "Cloudflare" : target)), false);

        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        src.sendSuccess(() -> CommandFeedback.row(
            net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.endpoint"),
            net.minecraft.network.chat.Component.literal(endpoint.isBlank() ? "(not set)" : endpoint)), false);
        src.sendSuccess(() -> CommandFeedback.row(
            net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.fallback"),
            net.minecraft.network.chat.Component.literal(fallback ? "local" : "none")), false);

        // Show timeout source: from config or default
        int timeoutSec = SAConfig.INSTANCE.uploadTimeoutSeconds.get();
        int defaultTimeout = 30;
        String timeoutSource = (timeoutSec == defaultTimeout) ? timeoutSec + "s default" : timeoutSec + "s from config";
        src.sendSuccess(() -> CommandFeedback.row(
            net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.timeout"),
            net.minecraft.network.chat.Component.literal(timeoutSource)), false);

        // Show transport
        String configuredTransport = SAConfig.INSTANCE.httpTransport.get();
        String activeTransport = sessionTransport != null ? sessionTransport + " (session-learned)" : configuredTransport;
        src.sendSuccess(() -> CommandFeedback.row(
            net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.transport"),
            net.minecraft.network.chat.Component.literal(activeTransport)), false);
        src.sendSuccess(() -> CommandFeedback.row(
            net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.impl"),
            net.minecraft.network.chat.Component.literal("AsyncQueuedSubmitClient v2")), false);

        boolean inProgress = submissionInProgress.get();
        src.sendSuccess(() -> CommandFeedback.row(
            net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.in_progress"),
            net.minecraft.network.chat.Component.literal(inProgress ? "true" : "false")), false);

        if (inProgress) {
            String uid = currentUploadId;
            if (uid != null) src.sendSuccess(() -> CommandFeedback.row(
                net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.upload_id"),
                net.minecraft.network.chat.Component.literal(uid)), false);
            long elapsed = (System.currentTimeMillis() - uploadStartMs) / 1000L;
            src.sendSuccess(() -> CommandFeedback.row(
                net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.started"),
                net.minecraft.network.chat.Component.literal(elapsed + "s ago")), false);
            if (elapsed > timeoutSec + 5) {
                src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.stuck")), false);
            }
        } else {
            String uid = currentUploadId;
            if (uid != null) src.sendSuccess(() -> CommandFeedback.row(
                net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.last_upload"),
                net.minecraft.network.chat.Component.literal(uid)), false);
        }

        src.sendSuccess(() -> CommandFeedback.row(
            net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.last_result"),
            net.minecraft.network.chat.Component.literal(lastSubmissionStatus)), false);
        if (!lastUploadStage.equals("none") && !lastUploadStage.isEmpty()) {
            src.sendSuccess(() -> CommandFeedback.row(
                net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.last_stage"),
                net.minecraft.network.chat.Component.literal(lastUploadStage)), false);
        }
        if (!lastUploadTiming.isEmpty()) {
            src.sendSuccess(() -> CommandFeedback.row(
                net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.last_timing"),
                net.minecraft.network.chat.Component.literal(lastUploadTiming)), false);
        }
        if (!lastSubmissionError.isEmpty() && !"none".equals(lastSubmissionStatus) && !"success".equals(lastSubmissionStatus)) {
            src.sendSuccess(() -> CommandFeedback.row(
                net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.status.last_error"),
                net.minecraft.network.chat.Component.literal(lastSubmissionError)), false);
        }
        return 1;
    }

    public static int submitReset(CommandSourceStack src) {
        submissionInProgress.set(false);
        currentUploadId = null;
        uploadStartMs = 0L;
        lastSubmissionStatus = "none";
        lastSubmissionError = "";
        src.sendSuccess(() -> CommandFeedback.success("[SA] Submission lock reset."), false);
        return 1;
    }

    public static int submitModeCloudflare(CommandSourceStack src) {
        SAConfig.INSTANCE.submissionTarget.set("cloudflare");
        SAConfig.INSTANCE.openIssueUrlOnClient.set(false);
        SAConfig.INSTANCE.copyIssueBodyToClipboard.set(false);
        src.sendSuccess(() -> CommandFeedback.success("[SA] Submission mode set to Cloudflare."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] /sa submit will upload to the report server."), false);
        return 1;
    }

    public static int submitModeLocal(CommandSourceStack src) {
        SAConfig.INSTANCE.submissionTarget.set("local");
        src.sendSuccess(() -> CommandFeedback.warn("[SA] Submission mode set to local."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Use /sa submit status to check submission configuration."), false);
        return 1;
    }

    public static int submitModeStatus(CommandSourceStack src) {
        boolean cfEnabled = isCloudflareEnabled();
        String target = SAConfig.INSTANCE.submissionTarget.get();
        boolean browserOpen = SAConfig.INSTANCE.openIssueUrlOnClient.get();
        boolean clipboard = SAConfig.INSTANCE.copyIssueBodyToClipboard.get();
        src.sendSuccess(() -> CommandFeedback.header("[SA] Submit mode"), false);
        src.sendSuccess(() -> CommandFeedback.row("Current mode", cfEnabled ? "cloudflare" : target), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa submit routes to", cfEnabled ? "CloudflareSubmitCommand" : "LocalFallback"), false);
        src.sendSuccess(() -> CommandFeedback.row("Browser opening", browserOpen ? "enabled" : "disabled"), false);
        src.sendSuccess(() -> CommandFeedback.row("Clipboard issue body", clipboard ? "enabled" : "disabled"), false);
        if (!cfEnabled) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Upload endpoint not configured. Use /sa submit status."), false);
        }
        return 1;
    }

    public static int submitDebugRouting(CommandSourceStack src) {
        boolean cfEnabled = isCloudflareEnabled();
        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        boolean browserOpen = SAConfig.INSTANCE.openIssueUrlOnClient.get();
        boolean clipboard = SAConfig.INSTANCE.copyIssueBodyToClipboard.get();
        boolean fallback = SAConfig.INSTANCE.fallbackToLocal.get();

        String pendingInfo = lastPendingId != null ? lastPendingId : "none";
        src.sendSuccess(() -> CommandFeedback.header("[SA] Submit routing"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa submit",
            cfEnabled ? "CloudflareSubmitCommand" : "LocalFallback"), false);
        src.sendSuccess(() -> CommandFeedback.row("browser opening", browserOpen ? "enabled (WRONG)" : "disabled"), false);
        src.sendSuccess(() -> CommandFeedback.row("clipboard issue body", clipboard ? "enabled (WRONG)" : "disabled"), false);
        src.sendSuccess(() -> CommandFeedback.row("endpoint", cfEnabled ? endpoint : "not set"), false);
        src.sendSuccess(() -> CommandFeedback.row("fallback", fallback ? "local" : "none"), false);
        src.sendSuccess(() -> CommandFeedback.row("pending submission", pendingInfo), false);
        return 1;
    }

    public static int submitHealth(CommandSourceStack src) {
        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        if (endpoint.isBlank()) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] No Cloudflare endpoint configured."), false);
            return 1;
        }
        String healthUrl = endpoint.contains("/api/report")
            ? endpoint.replace("/api/report", "/api/health")
            : endpoint.replaceAll("/+$", "") + "/api/health";

        src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.health.start")), false);
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .GET()
                    .header("User-Agent", "StutterAnalyzer/" + StutterAnalyzerMod.MOD_VERSION + " Minecraft/1.20.4")
                    .timeout(Duration.ofSeconds(5))
                    .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                int status = resp.statusCode();
                String body = resp.body() != null ? resp.body() : "";
                if (status == 200 && body.contains("\"ok\":true")) {
                    String version = extractJsonField(body, "version");
                    String fwd = extractJsonField(body, "github_forwarding");
                    String storage = extractJsonField(body, "storage");
                    src.sendSuccess(() -> CommandFeedback.success(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.health.ok")), false);
                    if (version != null) src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.health.version", version)), false);
                    if (fwd != null) src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.health.forwarding", fwd)), false);
                    if (storage != null) src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.health.storage", storage)), false);
                } else {
                    StutterAnalyzerMod.LOGGER.warn("[SA] Submit health check failed: HTTP {} from report server", status);
                    src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.health.invalid_response")), false);
                }
            } catch (java.net.http.HttpTimeoutException e) {
                StutterAnalyzerMod.LOGGER.warn("[SA] Worker health check timed out");
                src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.health.timeout")), false);
            } catch (java.io.IOException e) {
                StutterAnalyzerMod.LOGGER.warn("[SA] Worker health check network error: {}", e.getMessage());
                src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.health.network_error")), false);
            } catch (Exception e) {
                StutterAnalyzerMod.LOGGER.warn("[SA] Worker health check failed: {}", e.getMessage());
                src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.health.unavailable")), false);
            }
        }, UPLOAD_EXECUTOR);
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

    public static boolean isCloudflareEnabled() {
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
        lastPendingId = report.reportId;
        src.sendSuccess(() -> CommandFeedback.info("[SA] This will send a sanitized performance report to the Stutter Analyzer report server."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] GitHub issue creation happens server-side. You do not need a GitHub account."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Run /sa yes to send, or /sa cancel to cancel."), false);
        Component sendBtn = Component.literal("[Send Report]")
            .withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sa yes"))
                .withColor(ChatFormatting.GREEN)
                .withBold(true));
        Component cancelBtn = Component.literal(" [Cancel]")
            .withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sa cancel"))
                .withColor(ChatFormatting.RED));
        Component privacyBtn = Component.literal(" [Privacy]")
            .withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sa privacy"))
                .withColor(ChatFormatting.AQUA));
        Component buttons = Component.empty().append(sendBtn).append(cancelBtn).append(privacyBtn);
        src.sendSuccess(() -> buttons, false);
        return 1;
    }

    public static int cancelLatestPending(CommandSourceStack src) {
        String id = lastPendingId;
        if (id == null || !PENDING.containsKey(id)) {
            src.sendSuccess(() -> CommandFeedback.info("[SA] No pending submission to cancel."), false);
            return 1;
        }
        return cancelPrepared(src, id);
    }

    public static int confirmLatestPending(CommandSourceStack src) {
        if (!SAConfig.INSTANCE.enableManualSubmission.get()) {
            src.sendFailure(CommandFeedback.error(Component.translatable("stutteranalyzer.submit.disabled")));
            return 0;
        }
        String id = lastPendingId;
        if (id == null || !PENDING.containsKey(id)) {
            // Try last report as fallback
            FreezeReport last = ReportWriter.lastReport();
            if (last != null && PENDING.containsKey(last.reportId)) {
                id = last.reportId;
            } else {
                src.sendSuccess(() -> CommandFeedback.warn("[SA] No prepared submission is waiting for confirmation."), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] Use /sa submit first."), false);
                return 1;
            }
        }
        final String finalId = id;
        return submitConfirm(src, finalId);
    }

    public static int cancelPrepared(CommandSourceStack src, String preparedId) {
        PendingSubmission removed = PENDING.remove(preparedId);
        if (removed == null) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] No pending submission found with ID: " + preparedId), false);
            return 1;
        }
        if (preparedId.equals(lastPendingId)) lastPendingId = null;
        src.sendSuccess(() -> CommandFeedback.info("[SA] Submission cancelled: " + preparedId), false);
        return 1;
    }

    // Worker enforces 512 KB; stop before sending to avoid a useless round-trip
    private static final int MAX_PAYLOAD_CHARS = 480 * 1024;

    private static void submitToCloudflare(CommandSourceStack src, FreezeReport report, String markdown, String reportHash) {
        // ── Command thread: lock + first message only ─────────────────────────
        if (!submissionInProgress.compareAndSet(false, true)) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] A report upload is already in progress. Please wait."), false);
            return;
        }
        lastSubmissionEpochMs = System.currentTimeMillis();
        lastSubmissionError = "";
        lastSubmissionStatus = "in-progress";

        String uploadId = generateUploadId();
        currentUploadId = uploadId;
        uploadStartMs = System.currentTimeMillis();

        src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preparing")), false);

        // All heavy work is async - command thread returns immediately after this
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    asyncSubmitWork(src, report, markdown, reportHash, uploadId);
                } catch (Throwable t) {
                    String errClass = t.getClass().getSimpleName();
                    String errMsg = t.getMessage() != null ? t.getMessage() : errClass;
                    String shortErr = errMsg.length() > 80 ? errMsg.substring(0, 80) : errMsg;
                    lastSubmissionStatus = "failure";
                    lastSubmissionError = "internal: " + shortErr;
                    StutterAnalyzerMod.LOGGER.error("[SA] Unexpected submit error (upload_id={}): {}", uploadId, errMsg, t);
                    src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.upload_failed")), false);
                    src.sendSuccess(() -> CommandFeedback.info("[SA] Reason: " + errClass + " - " + shortErr), false);
                    src.sendSuccess(() -> CommandFeedback.info("[SA] Upload lock cleared."), false);
                    if (SAConfig.INSTANCE.fallbackToLocal.get()) {
                        saveLocalFallback(src, report, markdown);
                    }
                } finally {
                    submissionInProgress.set(false);
                }
            }, UPLOAD_EXECUTOR);
        } catch (Throwable t) {
            // runAsync itself failed (e.g. executor shut down)
            submissionInProgress.set(false);
            lastSubmissionStatus = "failure";
            lastSubmissionError = "executor: " + t.getClass().getSimpleName();
            StutterAnalyzerMod.LOGGER.error("[SA] Failed to queue upload task: {}", t.getMessage(), t);
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Failed to start upload: " + t.getClass().getSimpleName()), false);
        }
    }

    private static void asyncSubmitWork(CommandSourceStack src, FreezeReport report, String markdown,
                                        String reportHash, String uploadId) throws Exception {
        // Sanitize markdown
        ReportSanitizer.SanitizeResult mdResult = ReportSanitizer.sanitize(markdown);
        if (mdResult.hadSensitiveData()) {
            lastSubmissionStatus = "blocked-privacy";
            src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.privacy_blocked")), false);
            if (SAConfig.INSTANCE.fallbackToLocal.get()) {
                saveLocalFallback(src, report, markdown);
                src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.privacy_fallback")), false);
            }
            return;
        }
        String sanitizedMarkdown = mdResult.text();

        src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.collecting")), false);

        // Read log sections with null-safe fallbacks
        String logExcerpt       = safeGet(() -> LogExcerpter.extractExcerpt(report.event.timestamp()),       "latest.log excerpt unavailable.");
        String fullLog          = safeGet(() -> LogExcerpter.readFullLog(),                                    null);
        String stutterLogEvts   = safeGet(() -> LogExcerpter.extractStutterLogEvents(),                       "No Stutter Analyzer log events were found in latest.log.");
        String freezeContext    = safeGet(() -> LogExcerpter.extractUnknownFreezeContext(),                    "No Unknown Freeze context was found in latest.log.");
        String suspiciousSignals = safeGet(() -> LogExcerpter.extractSuspiciousSignals(),                      "No suspicious log signals were found.");

        // Payload debug summary
        if (SAConfig.INSTANCE.showPayloadSummary.get()) {
            int mdChars  = sanitizedMarkdown.length();
            int sChars   = stutterLogEvts   != null ? stutterLogEvts.length()    : 0;
            int fChars   = freezeContext    != null ? freezeContext.length()      : 0;
            int spChars  = suspiciousSignals != null ? suspiciousSignals.length() : 0;
            int lChars   = logExcerpt       != null ? logExcerpt.length()         : 0;
            int flChars  = fullLog          != null ? fullLog.length()            : 0;
            int sLines   = isMeaningfulLogContent(stutterLogEvts)    ? countLogLines(stutterLogEvts)    : 0;
            int fEvts    = isMeaningfulLogContent(freezeContext)     ? countFreezeEventsInStr(freezeContext) : 0;
            int spLines  = isMeaningfulLogContent(suspiciousSignals) ? countLogLines(suspiciousSignals)  : 0;
            int lLines   = (logExcerpt != null && !logExcerpt.startsWith("No relevant") && !logExcerpt.startsWith("Log excerpt was blocked"))
                           ? countLogLines(logExcerpt) : 0;
            src.sendSuccess(() -> CommandFeedback.header("[SA] Upload payload"), false);
            src.sendSuccess(() -> CommandFeedback.info("- markdown_report: " + mdChars + " chars"), false);
            src.sendSuccess(() -> CommandFeedback.info("- json_report: included"), false);
            src.sendSuccess(() -> CommandFeedback.info("- runtime_status_snapshot: included"), false);
            src.sendSuccess(() -> CommandFeedback.info("- stutter_log_events: " + sLines + " lines (" + sChars + " chars)"), false);
            src.sendSuccess(() -> CommandFeedback.info("- unknown_freeze_context: " + fEvts + " events (" + fChars + " chars)"), false);
            src.sendSuccess(() -> CommandFeedback.info("- suspicious_log_signals: " + spLines + " lines (" + spChars + " chars)"), false);
            src.sendSuccess(() -> CommandFeedback.info("- latest_log_excerpt: " + lLines + " lines (" + lChars + " chars)"), false);
            src.sendSuccess(() -> CommandFeedback.info("- full_latest_log: " + (flChars > 0 ? flChars + " chars" : "disabled/unavailable")), false);
        }

        // Build payload with Gson (proper JSON encoding, no string concatenation)
        PreparedUploadPayload prepared = buildUploadPayload(report, sanitizedMarkdown, reportHash,
            logExcerpt, fullLog, stutterLogEvts, freezeContext, suspiciousSignals, uploadId);
        String payload = prepared.jsonString();

        // Local JSON validation - catch encoding bugs before sending
        try {
            JsonParser.parseString(payload).getAsJsonObject();
        } catch (Exception parseEx) {
            lastSubmissionStatus = "failure";
            lastSubmissionError = "local JSON invalid: " + parseEx.getMessage();
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Submit blocked: generated JSON is invalid."), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] Reason: " + parseEx.getMessage()), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] Local fallback saved."), false);
            if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
            return;
        }

        int payloadKb = Math.max(1, payload.length() / 1024);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Upload payload size: " + payloadKb + " KB"), false);

        // Guard against oversized payload before sending
        if (payload.length() > MAX_PAYLOAD_CHARS) {
            lastSubmissionStatus = "too-large";
            lastSubmissionError = "payload " + payloadKb + " KB exceeds limit";
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Report payload is too large for upload."), false);
            if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
            return;
        }

        // Submit debug info - always shown so user can confirm correct build/endpoint/timeout
        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        int timeoutSec = SAConfig.INSTANCE.uploadTimeoutSeconds.get();
        src.sendSuccess(() -> CommandFeedback.header("[SA] Submit debug"), false);
        src.sendSuccess(() -> CommandFeedback.info("- Build ID: " + StutterAnalyzerMod.BUILD_ID), false);
        src.sendSuccess(() -> CommandFeedback.info("- Endpoint: " + endpoint), false);
        src.sendSuccess(() -> CommandFeedback.info("- Timeout: " + timeoutSec + "s"), false);
        src.sendSuccess(() -> CommandFeedback.info("- Method: POST"), false);
        src.sendSuccess(() -> CommandFeedback.info("- Content-Type: application/json"), false);
        src.sendSuccess(() -> CommandFeedback.info("- Payload size: " + Math.max(1, payload.length() / 1024) + " KB"), false);
        src.sendSuccess(() -> CommandFeedback.info("- client_upload_id: " + uploadId), false);
        src.sendSuccess(() -> CommandFeedback.info("- HTTP client: AsyncQueuedSubmitClient v2"), false);
        src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.cloudflare_uploading")), false);

        // Transport: use http_url_connection unless explicitly configured to java_http_client
        // "auto" and any other value now maps to http_url_connection (Java HttpClient times out in Forge JVM)
        String configuredTransport = SAConfig.INSTANCE.httpTransport.get();
        String[] transports;
        if ("java_http_client".equals(configuredTransport)) {
            transports = new String[]{"java_http_client"};
            src.sendSuccess(() -> CommandFeedback.info("[SA] HTTP transport: Java HttpClient"), false);
        } else {
            transports = new String[]{"http_url_connection"};
            src.sendSuccess(() -> CommandFeedback.info("[SA] HTTP transport: HttpURLConnection"), false);
        }

        boolean sent = false;
        for (int ti = 0; ti < transports.length && !sent; ti++) {
            String transport = transports[ti];
            lastUploadStage = "http_send:" + transport;
            long tSend = System.currentTimeMillis();
            try {
                int[] statusHolder = {0};
                String[] bodyHolder = {""};
                if ("http_url_connection".equals(transport)) {
                    String result = postWithUrlConn(endpoint, payload, timeoutSec * 1000);
                    int sep = result.indexOf('|');
                    statusHolder[0] = Integer.parseInt(result.substring(0, sep));
                    bodyHolder[0] = result.substring(sep + 1);
                } else {
                    HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "StutterAnalyzer/" + StutterAnalyzerMod.MOD_VERSION + " Minecraft/1.20.4")
                        .timeout(Duration.ofSeconds(timeoutSec))
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();
                    HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                    statusHolder[0] = resp.statusCode();
                    bodyHolder[0] = resp.body() != null ? resp.body() : "";
                }
                long tRecv = System.currentTimeMillis();
                long roundTripMs = tRecv - tSend;
                lastUploadStage = "response_received:" + transport;
                lastUploadTiming = "http_send=" + roundTripMs + "ms transport=" + transport;
                sessionTransport = transport;
                StutterAnalyzerMod.LOGGER.info("[SA] Upload HTTP round-trip {}ms transport={} upload_id={}", roundTripMs, transport, uploadId);
                lastUploadStage = "parse_response";
                handleCloudflareResponse(src, report, markdown, statusHolder[0], bodyHolder[0]);
                lastUploadStage = "done";
                sent = true;
            } catch (java.net.http.HttpTimeoutException e) {
                long ms = System.currentTimeMillis() - tSend;
                lastUploadStage = "timeout:" + transport;
                StutterAnalyzerMod.LOGGER.warn("[SA] Upload timed out after {}ms transport={} upload_id={}", ms, transport, uploadId);
                if (ti + 1 >= transports.length) {
                    // Both transports failed
                    lastSubmissionStatus = "timeout";
                    lastSubmissionError = "timed out after " + timeoutSec + "s (both transports)";
                    lastUploadTiming = "timeout at " + ms + "ms";
                    src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.timeout")), false);
                    if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
                }
            } catch (java.net.SocketTimeoutException e) {
                long ms = System.currentTimeMillis() - tSend;
                lastUploadStage = "timeout:" + transport;
                StutterAnalyzerMod.LOGGER.warn("[SA] Upload socket timeout {}ms transport={} upload_id={}", ms, transport, uploadId);
                if (ti + 1 >= transports.length) {
                    lastSubmissionStatus = "timeout";
                    lastSubmissionError = "socket timeout after " + timeoutSec + "s (both transports)";
                    lastUploadTiming = "socket timeout at " + ms + "ms";
                    src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.timeout")), false);
                    if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
                }
            } catch (Exception e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                String shortErr = errMsg.length() > 80 ? errMsg.substring(0, 80) : errMsg;
                lastUploadStage = "error:" + transport;
                StutterAnalyzerMod.LOGGER.warn("[SA] Upload error transport={} upload_id={}: {}", transport, uploadId, errMsg);
                if (ti + 1 >= transports.length) {
                    lastSubmissionStatus = "failure";
                    lastSubmissionError = shortErr;
                    src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.network_error")), false);
                    if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
                }
            }
        }
    }

    @FunctionalInterface
    private interface StringSupplier { String get() throws Exception; }

    private static String safeGet(StringSupplier supplier, String fallback) {
        try {
            return supplier.get();
        } catch (Exception e) {
            StutterAnalyzerMod.LOGGER.warn("[SA] Log section read failed: {}", e.getMessage());
            return fallback;
        }
    }

    private static void handleCloudflareResponse(CommandSourceStack src, FreezeReport report,
                                                  String markdown, int status, String body) {
        if (status == 409) {
            lastSubmissionStatus = "duplicate";
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.duplicate")), false);
            return;
        }
        if (status == 429) {
            lastSubmissionStatus = "rate-limited";
            src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.rate_limited")), false);
            if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
            return;
        }
        if (status == 200 && body.contains("\"ok\":true")) {
            String reportId        = extractJsonField(body, "report_id");
            String githubFwd       = extractJsonField(body, "github_forwarding");
            String issueNum        = extractJsonField(body, "github_issue_number");
            String issueUrl        = extractJsonField(body, "github_issue_url");
            String warning         = extractJsonField(body, "warning");
            String incMd           = extractJsonField(body, "included_markdown_report");
            String incJson         = extractJsonField(body, "included_json_report");
            String incRuntime      = extractJsonField(body, "included_runtime_status");
            String incStutterLog   = extractJsonField(body, "included_stutter_log_events");
            String incFreezeCtx    = extractJsonField(body, "included_unknown_freeze_context");
            String incSuspicious   = extractJsonField(body, "included_suspicious_log_signals");
            String incLogExcerpt   = extractJsonField(body, "included_latest_log_excerpt");
            String storedFullLog   = extractJsonField(body, "stored_full_latest_log");
            // received_* fields show exact char counts the Worker saw
            int recStutterChars   = parseIntOrZero(extractJsonField(body, "received_stutter_log_events_chars"));
            int recFreezeChars    = parseIntOrZero(extractJsonField(body, "received_unknown_freeze_context_chars"));
            int recSuspChars      = parseIntOrZero(extractJsonField(body, "received_suspicious_log_signals_chars"));
            int recLogExcChars    = parseIntOrZero(extractJsonField(body, "received_latest_log_excerpt_chars"));
            String finalId        = reportId != null ? reportId : report.reportId;

            lastSubmissionStatus = "success";

            src.sendSuccess(() -> CommandFeedback.success(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.received")), false);
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.report_id_line", finalId)), false);

            if ("queued".equals(githubFwd)) {
                src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.github_queued")), false);
                src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.github_queued_hint")), false);
            } else if ("GITHUB_FORWARD_FAILED".equals(warning)) {
                src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.github_fwd_failed")), false);
            } else if (issueNum != null && !issueNum.equals("null") && !issueNum.isBlank()) {
                src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.github_issue", issueNum)), false);
                if (issueUrl != null && !issueUrl.isBlank()) {
                    src.sendSuccess(() -> CommandFeedback.info(issueUrl), false);
                }
            } else {
                src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.stored_review")), false);
            }

            StringBuilder included = new StringBuilder();
            if ("true".equals(incRuntime))      included.append("runtime status, ");
            if ("true".equals(incStutterLog))   included.append("log events, ");
            if ("true".equals(incFreezeCtx))    included.append("Unknown Freeze context, ");
            if ("true".equals(incSuspicious))   included.append("suspicious signals, ");
            if ("true".equals(incLogExcerpt))   included.append("latest.log excerpt, ");
            if (included.length() > 2) {
                String inclStr = included.substring(0, included.length() - 2);
                src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.included", inclStr)), false);
            }
            if ("true".equals(storedFullLog)) {
                src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.full_log_stored")), false);
            }

            // Warn if Worker received 0 log content despite log sections being configured
            int totalReceivedLogChars = recStutterChars + recFreezeChars + recSuspChars + recLogExcChars;
            boolean logConfigEnabled = SAConfig.INSTANCE.includeStutterAnalyzerLogEvents.get()
                || SAConfig.INSTANCE.includeUnknownFreezeContext.get()
                || SAConfig.INSTANCE.includeSuspiciousLogSignals.get()
                || SAConfig.INSTANCE.includeLogExcerpt.get();
            if (totalReceivedLogChars == 0 && logConfigEnabled) {
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Warning: Worker received 0 log chars. Use /sa submit preview to diagnose."), false);
            }

            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.thankyou")), false);
            markConsentGiven();
        } else {
            StutterAnalyzerMod.LOGGER.warn("[SA] Cloudflare submit failed: {} {}", status, body);
            lastSubmissionStatus = "failure";
            String errorCode = extractJsonField(body, "error_code");
            if ("MALFORMED_JSON".equals(errorCode)) {
                lastSubmissionStatus = "malformed_json";
                lastSubmissionError = "MALFORMED_JSON";
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Report upload failed: MALFORMED_JSON"), false);
                showWorkerDetails(src, body);
                if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
            } else if ("RATE_LIMITED".equals(errorCode) || status == 429) {
                lastSubmissionStatus = "rate-limited";
                src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.rate_limited")), false);
                if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
            } else if ("DUPLICATE_REPORT".equals(errorCode) || status == 409) {
                lastSubmissionStatus = "duplicate";
                src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.duplicate")), false);
            } else if ("PAYLOAD_TOO_LARGE".equals(errorCode) || status == 413) {
                lastSubmissionStatus = "too-large";
                src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.too_large")), false);
                if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
            } else {
                String desc = errorCode != null ? errorCode : ("HTTP " + status);
                lastSubmissionError = desc;
                src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.upload_failed")), false);
                showWorkerDetails(src, body);
                if (SAConfig.INSTANCE.fallbackToLocal.get()) {
                    saveLocalFallback(src, report, markdown);
                } else {
                    src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.no_data_lost")), false);
                }
            }
        }
    }

    private static void showWorkerDetails(CommandSourceStack src, String body) {
        if (body == null || body.isBlank()) return;
        try {
            com.google.gson.JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            if (obj.has("message")) {
                String msg = obj.get("message").getAsString();
                src.sendSuccess(() -> CommandFeedback.info("[SA] Worker: " + msg), false);
            }
            if (obj.has("details") && obj.get("details").isJsonArray()) {
                for (com.google.gson.JsonElement el : obj.getAsJsonArray("details")) {
                    String d = el.getAsString();
                    src.sendSuccess(() -> CommandFeedback.info("[SA]   - " + d), false);
                }
            }
        } catch (Exception ignored) {
            // body is not JSON - show as-is if short
            if (body.length() < 200) {
                src.sendSuccess(() -> CommandFeedback.info("[SA] Worker response: " + body), false);
            }
        }
    }

    private static void saveLocalFallback(CommandSourceStack src, FreezeReport report, String markdown) {
        try {
            Path dir = resolveSubmissionsDir();
            Files.createDirectories(dir);
            Path mdFile = dir.resolve(report.reportId + ".md");
            Files.writeString(mdFile, markdown);
            String rid = report.reportId;
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.local_fallback_saved", rid)), false);
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

    // ── Migration notice ──────────────────────────────────────────────────────

    private static void checkMigrationNotice(CommandSourceStack src) {
        if (pendingMigrationNotice) {
            pendingMigrationNotice = false;
            src.sendSuccess(() -> CommandFeedback.info("[SA] Submission config migrated: default submit now uses Cloudflare Worker. Manual GitHub browser flow is disabled."), false);
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

    private static JsonObject buildRuntimeSnapshotObject() {
        boolean isClient = FMLEnvironment.dist == Dist.CLIENT;
        boolean clientTracker = isClient && SAConfig.INSTANCE.enableClientStutterDetection.get();
        boolean serverTracker = SAConfig.INSTANCE.enableServerTickDetection.get();
        FreezeReport last = ReportWriter.lastReport();
        String lastSaved = last != null
            ? last.event.category().name() + " " + last.event.durationMs() + "ms"
            : "none";
        FreezeEvent lastTracked = FreezeDetector.lastFreezeEvent();
        String lastTrackedStr = lastTracked != null
            ? lastTracked.category().name() + " " + lastTracked.durationMs() + "ms"
            : "none";
        JsonObject obj = new JsonObject();
        obj.addProperty("side", isClient ? "Client + Integrated Server" : "Dedicated Server");
        obj.addProperty("client_frame_tracker", clientTracker ? "ON" : "unavailable");
        obj.addProperty("server_tick_tracker", serverTracker ? "ON" : "OFF");
        obj.addProperty("minor_episodes_60s", StutterCounter.minorEpisodeCountInSeconds(60));
        obj.addProperty("minor_worst_ms", StutterCounter.worstMinorInSeconds(60));
        obj.addProperty("medium_episodes_60s", StutterCounter.mediumEpisodeCountInSeconds(60));
        obj.addProperty("medium_worst_ms", StutterCounter.worstMediumInSeconds(60));
        obj.addProperty("severe_episodes_60s", StutterCounter.severeEpisodeCountInSeconds(60));
        obj.addProperty("severe_worst_ms", StutterCounter.worstSevereInSeconds(60));
        obj.addProperty("extreme_episodes_60s", StutterCounter.extremeEpisodeCountInSeconds(60));
        obj.addProperty("extreme_worst_ms", StutterCounter.worstExtremeInSeconds(60));
        obj.addProperty("reports_saved", ReportWriter.savedReports());
        obj.addProperty("last_tracked_spike", lastTrackedStr);
        obj.addProperty("last_saved_report", lastSaved);
        obj.addProperty("quiet_mode", SAConfig.INSTANCE.quietMode.get());
        obj.addProperty("verbose_mode", SAConfig.INSTANCE.verboseMode.get());
        obj.addProperty("submission", isCloudflareEnabled() ? "Cloudflare enabled" : "local");
        return obj;
    }

    private static JsonObject buildJsonReportObject(FreezeReport report) {
        boolean inclMods = SAConfig.INSTANCE.submissionIncludeModList.get();
        boolean inclSys  = SAConfig.INSTANCE.submissionIncludeSystemInfo.get();
        boolean inclEvt  = SAConfig.INSTANCE.submissionIncludeRecentEvents.get();

        JsonObject jr = new JsonObject();
        jr.addProperty("report_id", report.reportId);
        jr.addProperty("side", String.valueOf(report.event.side()));
        jr.addProperty("reason", report.event.reason());
        jr.addProperty("evidence", report.event.evidence());
        jr.addProperty("recommendation", report.event.recommendation());

        HighLevelClassifier.HighLevelResult hlResult = report.event.highLevelResult();
        if (hlResult != null && hlResult.category() != HighLevelCategory.NONE) {
            jr.addProperty("high_level_category", hlResult.category().name());
            jr.addProperty("user_facing_name", hlResult.userFacingName());
            jr.addProperty("classification_reason", hlResult.classificationReason());
            jr.addProperty("root_cause_certainty", hlResult.rootCauseCertainty());
        }

        FreezeEvent.PeriodicMeta periodicMeta = report.event.periodicMeta();
        if (periodicMeta != null) {
            jr.addProperty("periodic", true);
            jr.addProperty("period_ms_estimate", periodicMeta.periodMsEstimate());
            jr.addProperty("periodic_occurrences_10m", periodicMeta.occurrences());
            jr.addProperty("root_cause_certainty", "low");
            if (periodicMeta.wasReclassified() && periodicMeta.originalCategory() != null) {
                jr.addProperty("was_reclassified", true);
                jr.addProperty("original_category", periodicMeta.originalCategory());
                jr.addProperty("reclassified_category", report.event.category().name());
            }
            if (periodicMeta.possibleContext() != null && !periodicMeta.possibleContext().isEmpty()) {
                JsonArray ctxArr = new JsonArray();
                for (String c : periodicMeta.possibleContext()) ctxArr.add(c);
                jr.add("possible_context", ctxArr);
            }
        }

        JsonObject metrics = new JsonObject();
        var st = MetricsCollector.serverTick();
        JsonObject serverTickObj = new JsonObject();
        serverTickObj.addProperty("current_mspt", st.currentMspt());
        serverTickObj.addProperty("rolling_avg_mspt", st.rollingAvgMspt());
        serverTickObj.addProperty("peak_mspt", st.peakMspt());
        serverTickObj.addProperty("tps_estimate", st.tpsEstimate());
        metrics.add("server_tick", serverTickObj);

        var mem = MetricsCollector.memoryGc();
        JsonObject memGc = new JsonObject();
        memGc.addProperty("heap_used_mb", mem.heapUsedMb());
        memGc.addProperty("heap_max_mb", mem.heapMaxMb());
        memGc.addProperty("heap_percent", mem.heapPercent());
        memGc.addProperty("recent_gc_count", mem.recentGcCount());
        memGc.addProperty("last_gc_pause_ms", mem.lastGcPauseMs());
        metrics.add("memory_gc", memGc);

        JsonObject stutterCounts = new JsonObject();
        stutterCounts.addProperty("minor_episodes_60s", StutterCounter.minorEpisodeCountInSeconds(60));
        stutterCounts.addProperty("minor_worst_ms", StutterCounter.worstMinorInSeconds(60));
        stutterCounts.addProperty("medium_episodes_60s", StutterCounter.mediumEpisodeCountInSeconds(60));
        stutterCounts.addProperty("medium_worst_ms", StutterCounter.worstMediumInSeconds(60));
        stutterCounts.addProperty("severe_episodes_60s", StutterCounter.severeEpisodeCountInSeconds(60));
        stutterCounts.addProperty("severe_worst_ms", StutterCounter.worstSevereInSeconds(60));
        stutterCounts.addProperty("extreme_episodes_60s", StutterCounter.extremeEpisodeCountInSeconds(60));
        stutterCounts.addProperty("extreme_worst_ms", StutterCounter.worstExtremeInSeconds(60));
        metrics.add("stutter_counts", stutterCounts);
        jr.add("metrics", metrics);

        if (inclSys) {
            jr.addProperty("system_info", report.systemInfo != null ? report.systemInfo : "");
            jr.addProperty("heap_info", report.heapInfo != null ? report.heapInfo : "");
        }

        if (inclMods && report.loadedMods != null && !report.loadedMods.isEmpty()) {
            JsonArray modsArr = new JsonArray();
            for (com.stutteranalyzer.knowledge.ModInventory.ModEntry m : report.loadedMods) {
                JsonObject modObj = new JsonObject();
                modObj.addProperty("mod_id", m.modId);
                modObj.addProperty("name", m.displayName);
                modObj.addProperty("version", m.version);
                modsArr.add(modObj);
            }
            jr.add("optimization_mods", modsArr);
        }

        if (inclEvt && report.event.recentTimeline() != null && !report.event.recentTimeline().isEmpty()) {
            JsonArray tlArr = new JsonArray();
            var tl = report.event.recentTimeline();
            int start = Math.max(0, tl.size() - 30);
            for (int i = start; i < tl.size(); i++) tlArr.add(tl.get(i).toString());
            jr.add("timeline", tlArr);
        }

        JsonArray recs = new JsonArray();
        recs.add(report.event.recommendation());
        jr.add("recommendations", recs);
        return jr;
    }

    private static PreparedUploadPayload buildUploadPayload(FreezeReport report, String sanitizedMarkdown,
            String reportHash, String logExcerpt, String fullLog,
            String stutterLogEvents, String freezeContext, String suspiciousSignals, String uploadId) {

        String category   = report.event.category().name().toUpperCase();
        long durationMs   = report.event.durationMs();
        double confidence = report.event.confidence();

        boolean inclMods    = SAConfig.INSTANCE.submissionIncludeModList.get();
        boolean inclSys     = SAConfig.INSTANCE.submissionIncludeSystemInfo.get();

        // Truncate large log sections
        String stutterTrunc = (stutterLogEvents != null && stutterLogEvents.length() > 25000)
            ? stutterLogEvents.substring(0, 24800) + "\n...(truncated)" : stutterLogEvents;
        String freezeTrunc = (freezeContext != null && freezeContext.length() > 50000)
            ? freezeContext.substring(0, 49800) + "\n...(truncated)" : freezeContext;
        String suspTrunc = (suspiciousSignals != null && suspiciousSignals.length() > 25000)
            ? suspiciousSignals.substring(0, 24800) + "\n...(truncated)" : suspiciousSignals;

        boolean inclLog     = logExcerpt != null && !logExcerpt.isBlank()
            && !logExcerpt.startsWith("No relevant") && !logExcerpt.startsWith("Log excerpt was blocked");
        boolean inclFullLog = fullLog != null && !fullLog.isBlank();
        boolean inclStutter = stutterTrunc != null && !stutterTrunc.isBlank()
            && !stutterTrunc.startsWith("No Stutter") && !stutterTrunc.startsWith("Could not") && !stutterTrunc.startsWith("latest.log");
        boolean inclFreeze  = freezeTrunc != null && !freezeTrunc.isBlank()
            && !freezeTrunc.startsWith("No Unknown") && !freezeTrunc.startsWith("Could not") && !freezeTrunc.startsWith("latest.log");
        boolean inclSusp    = suspTrunc != null && !suspTrunc.isBlank()
            && !suspTrunc.startsWith("No suspicious") && !suspTrunc.startsWith("Could not") && !suspTrunc.startsWith("latest.log");

        // Build json_report with Gson and sanitize it
        JsonObject jsonReportRaw = buildJsonReportObject(report);
        ReportSanitizer.SanitizeResult jrSanitized = ReportSanitizer.sanitize(GSON.toJson(jsonReportRaw));
        JsonObject jsonReportFinal;
        if (jrSanitized.hadSensitiveData()) {
            jsonReportFinal = new JsonObject();
            jsonReportFinal.addProperty("error", "sensitive data redacted");
        } else {
            try {
                jsonReportFinal = JsonParser.parseString(jrSanitized.text()).getAsJsonObject();
            } catch (Exception e) {
                jsonReportFinal = jsonReportRaw;
            }
        }

        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.addProperty("project", "stutter-analyzer");
        root.addProperty("source", "real_mod_submit");
        root.addProperty("mod_version", StutterAnalyzerMod.MOD_VERSION);
        root.addProperty("minecraft_version", "1.20.4");
        root.addProperty("loader", "forge");
        root.addProperty("loader_version", "49.x");
        root.addProperty("report_type", category);
        root.addProperty("category", category);
        HighLevelClassifier.HighLevelResult hlRoot = report.event.highLevelResult();
        if (hlRoot != null && hlRoot.category() != HighLevelCategory.NONE) {
            root.addProperty("high_level_category", hlRoot.category().name());
            root.addProperty("user_facing_name", hlRoot.userFacingName());
            root.addProperty("classification_reason", hlRoot.classificationReason());
            root.addProperty("root_cause_certainty", hlRoot.rootCauseCertainty());
        }
        root.addProperty("duration_ms", durationMs);
        root.addProperty("confidence", confidence);
        root.addProperty("report_hash", reportHash);
        root.addProperty("summary", category.replace('_', ' ').toLowerCase() + " detected (" + durationMs + " ms)");
        root.addProperty("markdown_report", sanitizedMarkdown);
        root.add("json_report", jsonReportFinal);
        root.add("runtime_status_snapshot", buildRuntimeSnapshotObject());
        root.addProperty("stutter_log_events",
            inclStutter ? stutterTrunc : (stutterLogEvents != null ? stutterLogEvents : "No Stutter Analyzer log events were found in latest.log."));
        root.addProperty("unknown_freeze_context",
            inclFreeze ? freezeTrunc : (freezeContext != null ? freezeContext : "No Unknown Freeze context was found in latest.log."));
        root.addProperty("suspicious_log_signals",
            inclSusp ? suspTrunc : (suspiciousSignals != null ? suspiciousSignals : "No suspicious log signals were found."));
        // Always include latest_log_excerpt and full_latest_log (empty string if unavailable)
        root.addProperty("latest_log_excerpt", inclLog ? logExcerpt : (logExcerpt != null ? logExcerpt : ""));
        root.addProperty("full_latest_log", inclFullLog ? fullLog : "");
        root.addProperty("client_upload_id", uploadId);
        root.addProperty("client_generated_at", Instant.now().toString());

        JsonObject privacy = new JsonObject();
        privacy.addProperty("sanitized", true);
        privacy.addProperty("contains_mod_list", inclMods);
        privacy.addProperty("contains_system_info", inclSys);
        privacy.addProperty("contains_logs", inclLog);
        privacy.addProperty("contains_stutter_log_events", inclStutter);
        privacy.addProperty("contains_unknown_freeze_context", inclFreeze);
        privacy.addProperty("contains_suspicious_log_signals", inclSusp);
        privacy.addProperty("contains_full_latest_log", inclFullLog);
        privacy.addProperty("contains_tokens", false);
        root.add("privacy", privacy);

        String jsonString = GSON.toJson(root);
        byte[] bodyBytes = jsonString.getBytes(StandardCharsets.UTF_8);
        return new PreparedUploadPayload(root, jsonString, bodyBytes, sha256Hex(jsonString));
    }

    // ── Submit preview ────────────────────────────────────────────────────────

    public static int submitPreview(CommandSourceStack src) {
        FreezeReport report = ReportWriter.lastReport();
        if (report == null) {
            src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.no_report_yet")), false);
            return 1;
        }

        String markdown = report.toMarkdown();
        ReportSanitizer.SanitizeResult mdResult = ReportSanitizer.sanitize(markdown);

        // Extract all log sections to get real sizes and line counts
        String stutterLogStr   = LogExcerpter.extractStutterLogEvents();
        String freezeCtxStr    = LogExcerpter.extractUnknownFreezeContext();
        String suspiciousStr   = LogExcerpter.extractSuspiciousSignals();
        String logExcerptStr   = LogExcerpter.extractExcerpt(report.event.timestamp());
        String fullLogStr      = LogExcerpter.readFullLog();

        boolean stutterDisabled = !SAConfig.INSTANCE.includeStutterAnalyzerLogEvents.get();
        boolean freezeDisabled  = !SAConfig.INSTANCE.includeUnknownFreezeContext.get();
        boolean suspDisabled    = !SAConfig.INSTANCE.includeSuspiciousLogSignals.get();
        boolean fullLogEnabled  = SAConfig.INSTANCE.includeFullLatestLog.get();

        boolean stutterMeaningful = isMeaningfulLogContent(stutterLogStr);
        boolean freezeMeaningful  = isMeaningfulLogContent(freezeCtxStr);
        boolean suspMeaningful    = isMeaningfulLogContent(suspiciousStr);
        boolean logMeaningful     = logExcerptStr != null && !logExcerptStr.startsWith("No relevant") && !logExcerptStr.startsWith("Log excerpt was blocked");

        int stutterLines = stutterMeaningful ? countLogLines(stutterLogStr) : 0;
        int stutterKb    = logKb(stutterLogStr);
        int freezeEvts   = freezeMeaningful  ? countFreezeEventsInStr(freezeCtxStr) : 0;
        int freezeKb     = logKb(freezeCtxStr);
        int suspLines    = suspMeaningful    ? countLogLines(suspiciousStr) : 0;
        int suspKb       = logKb(suspiciousStr);
        int logLines     = logMeaningful     ? countLogLines(logExcerptStr) : 0;
        int logKb        = logKb(logExcerptStr);
        int fullLogKb    = logKb(fullLogStr);
        int mdKb         = logKb(markdown);

        String category  = report.event.category().name();
        long durationMs  = report.event.durationMs();
        String hash      = sha256Hex(markdown);

        src.sendSuccess(() -> CommandFeedback.header(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.header")), false);
        src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.report_id", report.reportId)), false);
        src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.category", category)), false);
        src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.duration", durationMs)), false);
        src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.report_included", mdKb)), false);
        src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.json_included")), false);
        src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.runtime_included")), false);

        // Stutter log events
        if (stutterDisabled) {
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.stutter_log_disabled")), false);
        } else if (stutterMeaningful) {
            int sl = stutterLines; int sk = stutterKb;
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.stutter_log_lines", sl, sk)), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.stutter_log_none")), false);
        }

        // Unknown Freeze context
        if (freezeDisabled) {
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.freeze_ctx_disabled")), false);
        } else if (freezeMeaningful) {
            int fe = freezeEvts; int fk = freezeKb;
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.freeze_ctx_events", fe, fk)), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.freeze_ctx_none")), false);
        }

        // Suspicious signals
        if (suspDisabled) {
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.suspicious_disabled")), false);
        } else if (suspMeaningful) {
            int ss = suspLines; int sk = suspKb;
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.suspicious_lines", ss, sk)), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.suspicious_none")), false);
        }

        // Log excerpt
        if (!SAConfig.INSTANCE.includeLogExcerpt.get()) {
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.log_excerpt_disabled")), false);
        } else if (logMeaningful) {
            int ll = logLines; int lk = logKb;
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.log_excerpt_lines", ll, lk)), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.log_excerpt_unavailable")), false);
        }

        // Full log
        if (!fullLogEnabled) {
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.full_log_disabled")), false);
        } else if (fullLogStr != null) {
            int fk = fullLogKb;
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.full_log_included", fk)), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.full_log_unavailable")), false);
        }

        net.minecraft.network.chat.Component sensitivityComp =
            net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.sensitivity")
                .append(mdResult.hadSensitiveData()
                    ? net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.sensitivity.blocked")
                    : net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.sensitivity.passed"));
        src.sendSuccess(() -> CommandFeedback.info(sensitivityComp), false);
        String hashShort = hash.substring(0, 16);
        src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.hash", hashShort)), false);
        src.sendSuccess(() -> CommandFeedback.info(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.preview.not_uploaded")), false);
        return 1;
    }

    private static boolean isMeaningfulLogContent(String s) {
        if (s == null || s.isBlank()) return false;
        return !s.startsWith("No Stutter") && !s.startsWith("No Unknown") && !s.startsWith("No suspicious")
            && !s.startsWith("Could not") && !s.startsWith("latest.log") && !s.startsWith("Log events blocked")
            && !s.startsWith("Context blocked") && !s.startsWith("Signals blocked");
    }

    private static int countLogLines(String s) {
        if (s == null || s.isBlank()) return 0;
        return s.split("\n", -1).length;
    }

    private static int logKb(String s) {
        if (s == null) return 0;
        return Math.max(1, s.length() / 1024);
    }

    private static int countFreezeEventsInStr(String s) {
        if (s == null || s.isBlank()) return 0;
        int count = 0;
        for (String line : s.split("\n", -1)) { if (line.startsWith("### Event ")) count++; }
        return Math.max(count, 1);
    }

    private static int parseIntOrZero(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
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
            "*Prepared via StutterAnalyzer*\n";
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

    private static String generateUploadId() {
        LocalDateTime now = LocalDateTime.now();
        String ts = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String rand = String.format("%04x", System.nanoTime() & 0xFFFFL);
        return "UP-" + ts + "-" + rand;
    }

    // ── HTTP transport helpers ────────────────────────────────────────────────

    // Returns "<statusCode>|<body>" or throws on error/timeout
    private static String postWithUrlConn(String endpoint, String body, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "StutterAnalyzer/" + StutterAnalyzerMod.MOD_VERSION + " Minecraft/1.20.4");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        int code = conn.getResponseCode();
        InputStream stream = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
        String responseBody = readStreamUtf8(stream);
        return code + "|" + responseBody;
    }

    // Returns "<statusCode>|<body>" for GET requests
    private static String getWithUrlConn(String url, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "StutterAnalyzer/" + StutterAnalyzerMod.MOD_VERSION + " Minecraft/1.20.4");
        int code = conn.getResponseCode();
        InputStream stream = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
        String responseBody = readStreamUtf8(stream);
        return code + "|" + responseBody;
    }

    private static String readStreamUtf8(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static void handleTopLevelSubmitCrash(CommandSourceStack src, Throwable t) {
        try {
            submissionInProgress.set(false);
            currentUploadId = null;
            lastSubmissionStatus = "failure";
            String errClass = t.getClass().getSimpleName();
            String rawMsg = t.getMessage() != null ? t.getMessage() : "no message";
            String shortMsg = rawMsg.length() > 60 ? rawMsg.substring(0, 60) : rawMsg;
            lastSubmissionError = errClass + ": " + shortMsg;
            StutterAnalyzerMod.LOGGER.error("[SA] Submit command crash ({}): {}", errClass, rawMsg, t);
            try {
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Submit failed before upload could complete."), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] Reason: " + errClass + ": " + shortMsg), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] Upload lock cleared."), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] Full stacktrace was written to latest.log."), false);
            } catch (Throwable chatErr) {
                StutterAnalyzerMod.LOGGER.error("[SA] Submit crash handler: failed to send chat message", chatErr);
            }
        } catch (Throwable handlerErr) {
            StutterAnalyzerMod.LOGGER.error("[SA] Submit crash handler itself failed", handlerErr);
        }
    }

    // ── Config reset ──────────────────────────────────────────────────────────

    public static int submitConfigReset(CommandSourceStack src) {
        String defaultEndpoint = "https://stutter-analyzer-reports.morikemuri.workers.dev/api/report";
        SAConfig.INSTANCE.submissionTarget.set("cloudflare");
        SAConfig.INSTANCE.cloudflareEndpoint.set(defaultEndpoint);
        SAConfig.INSTANCE.uploadTimeoutSeconds.set(30);
        SAConfig.INSTANCE.httpTransport.set("http_url_connection");
        SAConfig.INSTANCE.openIssueUrlOnClient.set(false);
        SAConfig.INSTANCE.copyIssueBodyToClipboard.set(false);
        sessionTransport = null;
        src.sendSuccess(() -> CommandFeedback.success("[SA] Submission config reset to Cloudflare defaults."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Endpoint: " + defaultEndpoint), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Timeout: 30s"), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] HTTP transport: http_url_connection"), false);
        return 1;
    }

    // ── Export payload ────────────────────────────────────────────────────────

    public static int submitExportPayload(CommandSourceStack src) {
        FreezeReport report = ReportWriter.lastReport();
        if (report == null) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] No report available to export."), false);
            return 1;
        }
        CompletableFuture.runAsync(() -> {
            try {
                String markdown = report.toMarkdown();
                ReportSanitizer.SanitizeResult mdResult = ReportSanitizer.sanitize(markdown);
                if (mdResult.hadSensitiveData()) {
                    src.sendSuccess(() -> CommandFeedback.warn("[SA] Payload export blocked: sensitive data in report."), false);
                    return;
                }
                String sanitizedMarkdown = mdResult.text();
                String logExcerpt       = safeGet(() -> LogExcerpter.extractExcerpt(report.event.timestamp()),    "latest.log excerpt unavailable.");
                String fullLog          = safeGet(() -> LogExcerpter.readFullLog(),                                null);
                String stutterLogEvts   = safeGet(() -> LogExcerpter.extractStutterLogEvents(),                    "No Stutter Analyzer log events were found in latest.log.");
                String freezeContext    = safeGet(() -> LogExcerpter.extractUnknownFreezeContext(),                "No Unknown Freeze context was found in latest.log.");
                String suspiciousSignals = safeGet(() -> LogExcerpter.extractSuspiciousSignals(),                   "No suspicious log signals were found.");
                String hash = sha256Hex(sanitizedMarkdown);
                String uploadId = generateUploadId();
                PreparedUploadPayload prepared = buildUploadPayload(report, sanitizedMarkdown, hash,
                    logExcerpt, fullLog, stutterLogEvts, freezeContext, suspiciousSignals, uploadId);
                String payload = prepared.jsonString();
                // Local JSON validation
                try {
                    JsonParser.parseString(payload).getAsJsonObject();
                    src.sendSuccess(() -> CommandFeedback.success("[SA] Payload JSON: valid"), false);
                } catch (Exception parseEx) {
                    src.sendSuccess(() -> CommandFeedback.warn("[SA] Payload JSON: INVALID - " + parseEx.getMessage()), false);
                }
                Path debugDir = resolveDebugDir();
                Files.createDirectories(debugDir);
                Path outFile = debugDir.resolve("last-submit-payload.json");
                Files.writeString(outFile, payload);
                String outPath = outFile.toString();
                src.sendSuccess(() -> CommandFeedback.success("[SA] Payload exported to:"), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] " + outPath), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] Payload size: " + Math.max(1, payload.length() / 1024) + " KB"), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] SHA-256: " + prepared.sha256()), false);
                src.sendSuccess(() -> CommandFeedback.info("[SA] Use /sa submit health to check the upload endpoint."), false);
            } catch (Exception e) {
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Export failed: " + e.getMessage()), false);
            }
        }, UPLOAD_EXECUTOR);
        return 1;
    }

    // ── Validate payload ─────────────────────────────────────────────────────

    public static int submitValidatePayload(CommandSourceStack src) {
        FreezeReport report = ReportWriter.lastReport();
        if (report == null) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] No report available to validate."), false);
            return 1;
        }
        src.sendSuccess(() -> CommandFeedback.header("[SA] Validating payload..."), false);
        CompletableFuture.runAsync(() -> {
            try {
                String markdown = report.toMarkdown();
                ReportSanitizer.SanitizeResult mdResult = ReportSanitizer.sanitize(markdown);
                if (mdResult.hadSensitiveData()) {
                    src.sendSuccess(() -> CommandFeedback.warn("[SA] Validation blocked: sensitive data in report."), false);
                    return;
                }
                String sanitizedMarkdown = mdResult.text();
                String logExcerpt       = safeGet(() -> LogExcerpter.extractExcerpt(report.event.timestamp()),    "latest.log excerpt unavailable.");
                String fullLog          = safeGet(() -> LogExcerpter.readFullLog(),                                null);
                String stutterLogEvts   = safeGet(() -> LogExcerpter.extractStutterLogEvents(),                    "No Stutter Analyzer log events were found in latest.log.");
                String freezeContext    = safeGet(() -> LogExcerpter.extractUnknownFreezeContext(),                "No Unknown Freeze context was found in latest.log.");
                String suspiciousSignals = safeGet(() -> LogExcerpter.extractSuspiciousSignals(),                   "No suspicious log signals were found.");
                String hash = sha256Hex(sanitizedMarkdown);
                String uploadId = generateUploadId();
                PreparedUploadPayload prepared = buildUploadPayload(report, sanitizedMarkdown, hash,
                    logExcerpt, fullLog, stutterLogEvts, freezeContext, suspiciousSignals, uploadId);
                String payload = prepared.jsonString();
                // Local JSON validation
                try {
                    JsonParser.parseString(payload).getAsJsonObject();
                    src.sendSuccess(() -> CommandFeedback.success("[SA] Payload JSON: valid"), false);
                } catch (Exception parseEx) {
                    src.sendSuccess(() -> CommandFeedback.warn("[SA] Payload JSON: INVALID"), false);
                    src.sendSuccess(() -> CommandFeedback.info("[SA] " + parseEx.getMessage()), false);
                    return;
                }
                // POST to /api/validate-report
                String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
                if (endpoint == null || endpoint.isBlank()) {
                    src.sendSuccess(() -> CommandFeedback.warn("[SA] No endpoint configured. Use /sa submit status."), false);
                    return;
                }
                String validateUrl = endpoint.replaceFirst("/api/submit-report$", "") + "/api/validate-report";
                src.sendSuccess(() -> CommandFeedback.info("[SA] Posting to Worker schema validator..."), false);
                byte[] body = prepared.bodyBytes();
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(validateUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Content-Length", String.valueOf(body.length));
                conn.setRequestProperty("User-Agent", "StutterAnalyzer/" + StutterAnalyzerMod.MOD_VERSION + " Minecraft/1.20.4");
                try (java.io.OutputStream os = conn.getOutputStream()) { os.write(body); }
                int status = conn.getResponseCode();
                java.io.InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
                String respBody = is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
                if (status == 200 && respBody.contains("\"ok\":true")) {
                    src.sendSuccess(() -> CommandFeedback.success("[SA] Worker schema validation: valid"), false);
                } else {
                    src.sendSuccess(() -> CommandFeedback.warn("[SA] Worker schema validation: failed (HTTP " + status + ")"), false);
                    showWorkerDetails(src, respBody);
                }
            } catch (Exception e) {
                final String errMsg = e.getMessage();
                src.sendSuccess(() -> CommandFeedback.warn(net.minecraft.network.chat.Component.translatable("stutteranalyzer.submit.validate_failed", errMsg)), false);
            }
        }, UPLOAD_EXECUTOR);
        return 1;
    }

    private static Path resolveDebugDir() {
        try {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config/stutter-analyzer/debug");
            }
        } catch (Exception ignored) {}
        return FMLPaths.GAMEDIR.get().resolve("config/stutter-analyzer/debug");
    }

    // ── Network diagnostic commands ───────────────────────────────────────────

    private static volatile String lastNetHealthResult = "not tested";
    private static volatile String lastNetEchoResult   = "not tested";
    private static volatile String lastNetMinimalResult = "not tested";

    public static int netHealth(CommandSourceStack src) {
        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        if (endpoint == null || endpoint.isBlank()) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] No endpoint configured. Use /sa submit status."), false);
            return 1;
        }
        String healthUrl = endpoint.replaceFirst("/api/.*$", "") + "/api/health";
        src.sendSuccess(() -> CommandFeedback.header("[SA] Network health test"), false);
        src.sendSuccess(() -> CommandFeedback.info("- Method: GET"), false);
        src.sendSuccess(() -> CommandFeedback.info("- URL: " + healthUrl), false);
        long t0 = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "StutterAnalyzer/" + StutterAnalyzerMod.MOD_VERSION + " Minecraft/1.20.4")
                    .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                long ms = System.currentTimeMillis() - t0;
                int code = resp.statusCode();
                String body = resp.body() != null ? resp.body() : "";
                if (code == 200 && body.contains("\"ok\":true")) {
                    String version = extractJsonField(body, "version");
                    String fwd = extractJsonField(body, "github_forwarding");
                    lastNetHealthResult = "ok " + ms + "ms";
                    src.sendSuccess(() -> CommandFeedback.success("- HTTP: " + code), false);
                    src.sendSuccess(() -> CommandFeedback.info("- Response time: " + ms + " ms"), false);
                    if (version != null) src.sendSuccess(() -> CommandFeedback.info("- Worker version: " + version), false);
                    if (fwd != null) src.sendSuccess(() -> CommandFeedback.info("- GitHub forwarding: " + fwd), false);
                } else {
                    lastNetHealthResult = "http " + code;
                    src.sendSuccess(() -> CommandFeedback.warn("- HTTP: " + code + " (unexpected)"), false);
                    src.sendSuccess(() -> CommandFeedback.info("- Response time: " + ms + " ms"), false);
                }
            } catch (java.net.http.HttpTimeoutException e) {
                lastNetHealthResult = "timeout";
                src.sendSuccess(() -> CommandFeedback.warn("- TIMEOUT: Worker did not respond within 10s"), false);
                src.sendSuccess(() -> CommandFeedback.info("- Check firewall, DNS, Java SSL, endpoint config"), false);
            } catch (Exception e) {
                lastNetHealthResult = "error: " + e.getClass().getSimpleName();
                src.sendSuccess(() -> CommandFeedback.warn("- Error: " + e.getClass().getSimpleName() + " - " + e.getMessage()), false);
            }
        }, UPLOAD_EXECUTOR);
        return 1;
    }

    public static int netEcho(CommandSourceStack src) {
        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        if (endpoint == null || endpoint.isBlank()) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] No endpoint configured. Use /sa submit status."), false);
            return 1;
        }
        String echoUrl = endpoint.replaceFirst("/api/.*$", "") + "/api/echo";
        String uploadId = generateUploadId();
        String payload = "{\"project\":\"stutter-analyzer\",\"source\":\"minecraft_net_echo\",\"client_upload_id\":\"" + uploadId + "\"}";
        src.sendSuccess(() -> CommandFeedback.header("[SA] Network echo test"), false);
        src.sendSuccess(() -> CommandFeedback.info("- Method: POST"), false);
        src.sendSuccess(() -> CommandFeedback.info("- URL: " + echoUrl), false);
        src.sendSuccess(() -> CommandFeedback.info("- client_upload_id: " + uploadId), false);
        long t0 = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(echoUrl))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "StutterAnalyzer/" + StutterAnalyzerMod.MOD_VERSION + " Minecraft/1.20.4")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                long ms = System.currentTimeMillis() - t0;
                int code = resp.statusCode();
                String body = resp.body() != null ? resp.body() : "";
                if (code == 200 && body.contains("\"ok\":true")) {
                    String returnedId = extractJsonField(body, "client_upload_id");
                    String bodySize = extractJsonField(body, "body_size");
                    boolean idMatched = uploadId.equals(returnedId);
                    lastNetEchoResult = "ok " + ms + "ms id-matched=" + idMatched;
                    src.sendSuccess(() -> CommandFeedback.success("- HTTP: " + code), false);
                    src.sendSuccess(() -> CommandFeedback.info("- Response time: " + ms + " ms"), false);
                    src.sendSuccess(() -> CommandFeedback.info("- Body size accepted: " + (bodySize != null ? bodySize : "?") + " bytes"), false);
                    src.sendSuccess(() -> CommandFeedback.info("- client_upload_id matched: " + (idMatched ? "yes" : "NO - got " + returnedId)), false);
                } else {
                    lastNetEchoResult = "http " + code;
                    src.sendSuccess(() -> CommandFeedback.warn("- HTTP: " + code + " (unexpected)"), false);
                    src.sendSuccess(() -> CommandFeedback.info("- Response time: " + ms + " ms"), false);
                }
            } catch (java.net.http.HttpTimeoutException e) {
                lastNetEchoResult = "timeout";
                src.sendSuccess(() -> CommandFeedback.warn("- TIMEOUT: POST to Worker timed out after 10s"), false);
                src.sendSuccess(() -> CommandFeedback.info("- GET health worked but POST failed - check mod POST handling"), false);
            } catch (Exception e) {
                lastNetEchoResult = "error: " + e.getClass().getSimpleName();
                src.sendSuccess(() -> CommandFeedback.warn("- Error: " + e.getClass().getSimpleName() + " - " + e.getMessage()), false);
            }
        }, UPLOAD_EXECUTOR);
        return 1;
    }

    public static int netPostMinimal(CommandSourceStack src) {
        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        if (endpoint == null || endpoint.isBlank()) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] No endpoint configured. Use /sa submit status."), false);
            return 1;
        }
        String uploadId = generateUploadId();
        String minHash = "minecraft-minimal-" + Long.toHexString(System.nanoTime() & 0xFFFFFFFFL);
        String payload = "{\n" +
            "  \"schema_version\": 1,\n" +
            "  \"project\": \"stutter-analyzer\",\n" +
            "  \"source\": \"minecraft_net_test\",\n" +
            "  \"mod_version\": " + esc(StutterAnalyzerMod.MOD_VERSION) + ",\n" +
            "  \"minecraft_version\": \"1.20.4\",\n" +
            "  \"loader\": \"forge\",\n" +
            "  \"loader_version\": \"49.x\",\n" +
            "  \"report_type\": \"TEST\",\n" +
            "  \"category\": \"SERVER_TICK_SPIKE\",\n" +
            "  \"duration_ms\": 392,\n" +
            "  \"confidence\": 0.7,\n" +
            "  \"report_hash\": " + esc(minHash) + ",\n" +
            "  \"summary\": \"Minecraft minimal submit test\",\n" +
            "  \"markdown_report\": \"Minimal report from Minecraft\",\n" +
            "  \"json_report\": {},\n" +
            "  \"runtime_status_snapshot\": {},\n" +
            "  \"stutter_log_events\": \"Minimal Minecraft test event\",\n" +
            "  \"unknown_freeze_context\": \"Minimal Minecraft test context\",\n" +
            "  \"suspicious_log_signals\": \"Minimal Minecraft test signal\",\n" +
            "  \"latest_log_excerpt\": \"Minimal Minecraft test excerpt\",\n" +
            "  \"client_upload_id\": " + esc(uploadId) + ",\n" +
            "  \"client_generated_at\": " + esc(Instant.now().toString()) + ",\n" +
            "  \"privacy\": {\"sanitized\": true, \"contains_logs\": true, \"contains_tokens\": false}\n" +
            "}";
        int timeoutSec = SAConfig.INSTANCE.uploadTimeoutSeconds.get();
        src.sendSuccess(() -> CommandFeedback.header("[SA] Minimal report POST"), false);
        src.sendSuccess(() -> CommandFeedback.info("- Endpoint: " + endpoint), false);
        src.sendSuccess(() -> CommandFeedback.info("- client_upload_id: " + uploadId), false);
        src.sendSuccess(() -> CommandFeedback.info("- Timeout: " + timeoutSec + "s"), false);
        long t0 = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "StutterAnalyzer/" + StutterAnalyzerMod.MOD_VERSION + " Minecraft/1.20.4")
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                long ms = System.currentTimeMillis() - t0;
                int code = resp.statusCode();
                String body = resp.body() != null ? resp.body() : "";
                if (code == 200 && body.contains("\"ok\":true")) {
                    String reportId = extractJsonField(body, "report_id");
                    String status   = extractJsonField(body, "status");
                    String fwd      = extractJsonField(body, "github_forwarding");
                    lastNetMinimalResult = "ok " + ms + "ms report_id=" + reportId;
                    src.sendSuccess(() -> CommandFeedback.success("- HTTP: " + code), false);
                    src.sendSuccess(() -> CommandFeedback.info("- Response time: " + ms + " ms"), false);
                    src.sendSuccess(() -> CommandFeedback.info("- Report ID: " + reportId), false);
                    src.sendSuccess(() -> CommandFeedback.info("- Status: " + status), false);
                    src.sendSuccess(() -> CommandFeedback.info("- GitHub forwarding: " + fwd), false);
                } else {
                    String errorCode = extractJsonField(body, "error_code");
                    String message   = extractJsonField(body, "message");
                    lastNetMinimalResult = "http " + code + " " + errorCode;
                    src.sendSuccess(() -> CommandFeedback.warn("- HTTP: " + code), false);
                    src.sendSuccess(() -> CommandFeedback.info("- Response time: " + ms + " ms"), false);
                    if (errorCode != null) src.sendSuccess(() -> CommandFeedback.info("- Error: " + errorCode + " - " + message), false);
                }
            } catch (java.net.http.HttpTimeoutException e) {
                lastNetMinimalResult = "timeout";
                src.sendSuccess(() -> CommandFeedback.warn("- TIMEOUT: POST timed out after " + timeoutSec + "s"), false);
                src.sendSuccess(() -> CommandFeedback.info("- Use /sa submit status to check configuration"), false);
            } catch (Exception e) {
                lastNetMinimalResult = "error: " + e.getClass().getSimpleName();
                src.sendSuccess(() -> CommandFeedback.warn("- Error: " + e.getClass().getSimpleName() + " - " + e.getMessage()), false);
            }
        }, UPLOAD_EXECUTOR);
        return 1;
    }

    public static int netStatus(CommandSourceStack src) {
        src.sendSuccess(() -> CommandFeedback.header("[SA] Network diagnostic status"), false);
        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        int timeoutSec = SAConfig.INSTANCE.uploadTimeoutSeconds.get();
        src.sendSuccess(() -> CommandFeedback.row("Endpoint", endpoint.isBlank() ? "(not set)" : endpoint), false);
        src.sendSuccess(() -> CommandFeedback.row("Timeout", timeoutSec + "s"), false);
        src.sendSuccess(() -> CommandFeedback.row("Config transport", SAConfig.INSTANCE.httpTransport.get()), false);
        src.sendSuccess(() -> CommandFeedback.row("Session transport", sessionTransport != null ? sessionTransport : "not determined"), false);
        src.sendSuccess(() -> CommandFeedback.row("Last health", lastNetHealthResult), false);
        src.sendSuccess(() -> CommandFeedback.row("Last echo", lastNetEchoResult), false);
        src.sendSuccess(() -> CommandFeedback.row("Last post-minimal", lastNetMinimalResult), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Use /sa submit health to check the upload endpoint."), false);
        return 1;
    }

    public static int netEchoJava(CommandSourceStack src) { return netEchoWithTransport(src, "java_http_client"); }
    public static int netEchoUrlConn(CommandSourceStack src) { return netEchoWithTransport(src, "http_url_connection"); }
    public static int netPostMinimalJava(CommandSourceStack src) { return netPostMinimalWithTransport(src, "java_http_client"); }
    public static int netPostMinimalUrlConn(CommandSourceStack src) { return netPostMinimalWithTransport(src, "http_url_connection"); }

    private static int netEchoWithTransport(CommandSourceStack src, String transport) {
        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        if (endpoint == null || endpoint.isBlank()) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] No endpoint configured. Use /sa submit status."), false);
            return 1;
        }
        String echoUrl = endpoint.replaceFirst("/api/.*$", "") + "/api/echo";
        String uploadId = generateUploadId();
        String payload = "{\"project\":\"stutter-analyzer\",\"source\":\"minecraft_net_echo\",\"client_upload_id\":\"" + uploadId + "\"}";
        String label = "java_http_client".equals(transport) ? "java" : "urlconn";
        src.sendSuccess(() -> CommandFeedback.header("[SA] Echo " + label), false);
        src.sendSuccess(() -> CommandFeedback.info("- Transport: " + transport), false);
        src.sendSuccess(() -> CommandFeedback.info("- client_upload_id: " + uploadId), false);
        long t0 = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            try {
                String result;
                if ("http_url_connection".equals(transport)) {
                    result = postWithUrlConn(echoUrl, payload, 10000);
                } else {
                    HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(echoUrl))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "StutterAnalyzer/" + StutterAnalyzerMod.MOD_VERSION + " Minecraft/1.20.4")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();
                    HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                    result = resp.statusCode() + "|" + (resp.body() != null ? resp.body() : "");
                }
                long ms = System.currentTimeMillis() - t0;
                int sep = result.indexOf('|');
                int code = Integer.parseInt(result.substring(0, sep));
                String body = result.substring(sep + 1);
                if (code == 200 && body.contains("\"ok\":true")) {
                    String returnedId = extractJsonField(body, "client_upload_id");
                    boolean idMatched = uploadId.equals(returnedId);
                    lastNetEchoResult = label + ":ok " + ms + "ms";
                    src.sendSuccess(() -> CommandFeedback.success("[SA] Echo " + label + ": success, " + ms + " ms"), false);
                    src.sendSuccess(() -> CommandFeedback.info("- client_upload_id matched: " + (idMatched ? "yes" : "NO - got " + returnedId)), false);
                } else {
                    lastNetEchoResult = label + ":http " + code;
                    src.sendSuccess(() -> CommandFeedback.warn("[SA] Echo " + label + ": HTTP " + code + " (" + ms + " ms)"), false);
                }
            } catch (java.net.http.HttpTimeoutException | java.net.SocketTimeoutException e) {
                lastNetEchoResult = label + ":timeout";
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Echo " + label + ": TIMEOUT"), false);
            } catch (Exception e) {
                lastNetEchoResult = label + ":error";
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Echo " + label + ": error - " + e.getMessage()), false);
            }
        }, UPLOAD_EXECUTOR);
        return 1;
    }

    private static int netPostMinimalWithTransport(CommandSourceStack src, String transport) {
        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        if (endpoint == null || endpoint.isBlank()) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] No endpoint configured. Use /sa submit status."), false);
            return 1;
        }
        String uploadId = generateUploadId();
        String minHash = "minecraft-minimal-" + Long.toHexString(System.nanoTime() & 0xFFFFFFFFL);
        String payload = "{\n" +
            "  \"schema_version\": 1,\n" +
            "  \"project\": \"stutter-analyzer\",\n" +
            "  \"source\": \"minecraft_net_test\",\n" +
            "  \"mod_version\": " + esc(StutterAnalyzerMod.MOD_VERSION) + ",\n" +
            "  \"minecraft_version\": \"1.20.4\",\n" +
            "  \"loader\": \"forge\",\n" +
            "  \"loader_version\": \"49.x\",\n" +
            "  \"report_type\": \"TEST\",\n" +
            "  \"category\": \"SERVER_TICK_SPIKE\",\n" +
            "  \"duration_ms\": 392,\n" +
            "  \"confidence\": 0.7,\n" +
            "  \"report_hash\": " + esc(minHash) + ",\n" +
            "  \"summary\": \"Minecraft minimal submit test\",\n" +
            "  \"markdown_report\": \"Minimal report from Minecraft\",\n" +
            "  \"json_report\": {},\n" +
            "  \"runtime_status_snapshot\": {},\n" +
            "  \"stutter_log_events\": \"Minimal Minecraft test event\",\n" +
            "  \"unknown_freeze_context\": \"Minimal Minecraft test context\",\n" +
            "  \"suspicious_log_signals\": \"Minimal Minecraft test signal\",\n" +
            "  \"latest_log_excerpt\": \"Minimal Minecraft test excerpt\",\n" +
            "  \"client_upload_id\": " + esc(uploadId) + ",\n" +
            "  \"client_generated_at\": " + esc(Instant.now().toString()) + ",\n" +
            "  \"privacy\": {\"sanitized\": true, \"contains_logs\": true, \"contains_tokens\": false}\n" +
            "}";
        int timeoutSec = SAConfig.INSTANCE.uploadTimeoutSeconds.get();
        String label = "java_http_client".equals(transport) ? "java" : "urlconn";
        src.sendSuccess(() -> CommandFeedback.header("[SA] Minimal report POST " + label), false);
        src.sendSuccess(() -> CommandFeedback.info("- Transport: " + transport), false);
        src.sendSuccess(() -> CommandFeedback.info("- client_upload_id: " + uploadId), false);
        src.sendSuccess(() -> CommandFeedback.info("- Timeout: " + timeoutSec + "s"), false);
        long t0 = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            try {
                String result;
                if ("http_url_connection".equals(transport)) {
                    result = postWithUrlConn(endpoint, payload, timeoutSec * 1000);
                } else {
                    HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "StutterAnalyzer/" + StutterAnalyzerMod.MOD_VERSION + " Minecraft/1.20.4")
                        .timeout(Duration.ofSeconds(timeoutSec))
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();
                    HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                    result = resp.statusCode() + "|" + (resp.body() != null ? resp.body() : "");
                }
                long ms = System.currentTimeMillis() - t0;
                int sep = result.indexOf('|');
                int code = Integer.parseInt(result.substring(0, sep));
                String body = result.substring(sep + 1);
                if (code == 200 && body.contains("\"ok\":true")) {
                    String reportId = extractJsonField(body, "report_id");
                    String status   = extractJsonField(body, "status");
                    String fwd      = extractJsonField(body, "github_forwarding");
                    lastNetMinimalResult = label + ":ok " + ms + "ms";
                    src.sendSuccess(() -> CommandFeedback.success("[SA] Minimal POST " + label + ": success, " + ms + " ms"), false);
                    src.sendSuccess(() -> CommandFeedback.info("- Report ID: " + reportId), false);
                    src.sendSuccess(() -> CommandFeedback.info("- Status: " + status), false);
                    src.sendSuccess(() -> CommandFeedback.info("- GitHub forwarding: " + fwd), false);
                } else {
                    String errorCode = extractJsonField(body, "error_code");
                    lastNetMinimalResult = label + ":http " + code;
                    src.sendSuccess(() -> CommandFeedback.warn("[SA] Minimal POST " + label + ": HTTP " + code + " (" + ms + " ms)"), false);
                    if (errorCode != null) src.sendSuccess(() -> CommandFeedback.info("- Error: " + errorCode), false);
                }
            } catch (java.net.http.HttpTimeoutException | java.net.SocketTimeoutException e) {
                lastNetMinimalResult = label + ":timeout";
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Minimal POST " + label + ": TIMEOUT after " + timeoutSec + "s"), false);
            } catch (Exception e) {
                lastNetMinimalResult = label + ":error";
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Minimal POST " + label + ": error - " + e.getMessage()), false);
            }
        }, UPLOAD_EXECUTOR);
        return 1;
    }

    private static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int start = idx + key.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        boolean quoted = start < json.length() && json.charAt(start) == '"';
        if (quoted) start++;
        int end = start;
        if (quoted) {
            while (end < json.length() && json.charAt(end) != '"') end++;
        } else {
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != '\n') end++;
        }
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
