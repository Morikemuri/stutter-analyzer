package com.stutteranalyzer.submission;

import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.command.CommandFeedback;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.MetricsCollector;
import com.stutteranalyzer.core.StutterCounter;
import com.stutteranalyzer.crash.CrashEvent;
import com.stutteranalyzer.crash.PreviousCrashImporter;
import com.stutteranalyzer.guard.EmergencyGuardManager;
import com.stutteranalyzer.guard.EmergencyGuardReport;
import com.stutteranalyzer.knowledge.ModInventory;
import com.stutteranalyzer.report.FreezeReport;
import com.stutteranalyzer.report.ReportWriter;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.net.URI;
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
    private static volatile boolean submissionInProgress = false;
    private static volatile long lastSubmissionEpochMs = 0L;
    private static volatile String currentUploadId = null;
    private static volatile long uploadStartMs = 0L;
    private static volatile String lastSubmissionError = "";

    public static void setPendingMigrationNotice() {
        pendingMigrationNotice = true;
    }

    // ── Public entry points ───────────────────────────────────────────────────

    public static int submitLast(CommandSourceStack src) {
        if (!SAConfig.INSTANCE.enableManualSubmission.get()) {
            src.sendFailure(CommandFeedback.error(Component.translatable("stutteranalyzer.submit.disabled")));
            return 0;
        }
        if (submissionInProgress) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] A report upload is already in progress. Please wait."), false);
            return 1;
        }
        int cooldown = SAConfig.INSTANCE.submitCommandCooldownSeconds.get();
        long elapsedSec = (System.currentTimeMillis() - lastSubmissionEpochMs) / 1000L;
        if (cooldown > 0 && elapsedSec < cooldown) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Please wait before submitting another report."), false);
            return 1;
        }
        checkMigrationNotice(src);
        FreezeReport report = ReportWriter.lastReport();
        if (report == null) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] No saved freeze report is available yet."), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] Severe/extreme events create reports automatically. Minor stutters are tracked in F3/status only."), false);
            return 1;
        }
        if (!isCloudflareEnabled()) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Cloudflare endpoint not configured. Use /sa admin submit mode cloudflare to fix."), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] For manual local save, use /sa admin submit local."), false);
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
            src.sendSuccess(() -> CommandFeedback.success("[SA] Run /sa submit yes to upload."), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] Advanced: /sa submit confirm " + rid), false);
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
        checkMigrationNotice(src);
        boolean cfEnabled = isCloudflareEnabled();
        boolean fallback = SAConfig.INSTANCE.fallbackToLocal.get();
        String target = SAConfig.INSTANCE.submissionTarget.get();

        src.sendSuccess(() -> CommandFeedback.header("[SA] Submission status"), false);
        src.sendSuccess(() -> CommandFeedback.row("Mode", cfEnabled ? "Cloudflare" : target), false);
        src.sendSuccess(() -> CommandFeedback.row("Endpoint", cfEnabled ? "configured" : "not set"), false);
        src.sendSuccess(() -> CommandFeedback.row("Fallback", fallback ? "local" : "none"), false);

        boolean inProgress = submissionInProgress;
        src.sendSuccess(() -> CommandFeedback.row("Upload in progress", inProgress ? "true" : "false"), false);

        if (inProgress) {
            String uid = currentUploadId;
            if (uid != null) {
                src.sendSuccess(() -> CommandFeedback.row("Current upload ID", uid), false);
            }
            long elapsed = (System.currentTimeMillis() - uploadStartMs) / 1000L;
            src.sendSuccess(() -> CommandFeedback.row("Started", elapsed + "s ago"), false);
            int timeoutSec = SAConfig.INSTANCE.uploadTimeoutSeconds.get();
            if (elapsed > timeoutSec + 5) {
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Upload appears stuck. Use /sa submit reset or wait for timeout cleanup."), false);
            }
        } else {
            String uid = currentUploadId;
            if (uid != null) {
                src.sendSuccess(() -> CommandFeedback.row("Last upload ID", uid), false);
            }
        }

        src.sendSuccess(() -> CommandFeedback.row("Last result", lastSubmissionStatus), false);
        if (!lastSubmissionError.isEmpty() && !"none".equals(lastSubmissionStatus) && !"success".equals(lastSubmissionStatus)) {
            src.sendSuccess(() -> CommandFeedback.row("Last error", lastSubmissionError), false);
        }
        return 1;
    }

    public static int submitReset(CommandSourceStack src) {
        submissionInProgress = false;
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
        src.sendSuccess(() -> CommandFeedback.info("[SA] /sa submit last will upload to the report server, not open GitHub."), false);
        return 1;
    }

    public static int submitModeLocal(CommandSourceStack src) {
        SAConfig.INSTANCE.submissionTarget.set("local");
        src.sendSuccess(() -> CommandFeedback.warn("[SA] Submission mode set to local."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] /sa submit last will save locally only."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Use /sa submit local last for the manual GitHub fallback flow."), false);
        return 1;
    }

    public static int submitModeStatus(CommandSourceStack src) {
        boolean cfEnabled = isCloudflareEnabled();
        String target = SAConfig.INSTANCE.submissionTarget.get();
        boolean browserOpen = SAConfig.INSTANCE.openIssueUrlOnClient.get();
        boolean clipboard = SAConfig.INSTANCE.copyIssueBodyToClipboard.get();
        src.sendSuccess(() -> CommandFeedback.header("[SA] Submit mode"), false);
        src.sendSuccess(() -> CommandFeedback.row("Current mode", cfEnabled ? "cloudflare" : target), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa submit last routes to", cfEnabled ? "CloudflareSubmitCommand" : "ManualGitHubIssueFlow"), false);
        src.sendSuccess(() -> CommandFeedback.row("Browser opening", browserOpen ? "enabled" : "disabled"), false);
        src.sendSuccess(() -> CommandFeedback.row("Clipboard issue body", clipboard ? "enabled" : "disabled"), false);
        if (!cfEnabled) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Cloudflare mode is not active. Run /sa submit mode cloudflare to fix."), false);
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
        src.sendSuccess(() -> CommandFeedback.row("/sa submit last",
            cfEnabled ? "CloudflareSubmitCommand.prepareOrSubmitLatest" : "ManualGitHubIssueFlow (WRONG - run /sa submit mode cloudflare)"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa submit yes", "CloudflareSubmitCommand.confirmLatestPrepared"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa submit send", "CloudflareSubmitCommand.confirmLatestPrepared"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa submit confirm last", "CloudflareSubmitCommand.confirmLatestPrepared"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa submit confirm <prepared_id>", "CloudflareSubmitCommand.confirmPrepared"), false);
        src.sendSuccess(() -> CommandFeedback.row("/sa submit local last", "LocalManualSubmissionCommand"), false);
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

        src.sendSuccess(() -> CommandFeedback.info("[SA] Checking Worker health..."), false);
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .GET()
                    .header("User-Agent", "StutterAnalyzer/" + StutterAnalyzerMod.MOD_VERSION + " Minecraft/1.20.4")
                    .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                int status = resp.statusCode();
                String body = resp.body();
                if (status == 200 && body.contains("\"ok\":true")) {
                    String version = extractJsonField(body, "version");
                    String fwd = extractJsonField(body, "github_forwarding");
                    String storage = extractJsonField(body, "storage");
                    src.sendSuccess(() -> CommandFeedback.success("[SA] Worker health: OK"), false);
                    if (version != null) src.sendSuccess(() -> CommandFeedback.info("[SA] Worker version: " + version), false);
                    if (fwd != null) src.sendSuccess(() -> CommandFeedback.info("[SA] GitHub forwarding: " + fwd), false);
                    if (storage != null) src.sendSuccess(() -> CommandFeedback.info("[SA] Storage: " + storage), false);
                } else {
                    src.sendSuccess(() -> CommandFeedback.warn("[SA] Worker health: unexpected response (" + status + ")"), false);
                }
            } catch (Exception e) {
                StutterAnalyzerMod.LOGGER.warn("[SA] Worker health check failed: {}", e.getMessage());
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Worker health: unavailable"), false);
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
                src.sendSuccess(() -> CommandFeedback.info("[SA] Use /sa submit last first."), false);
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
        submissionInProgress = true;
        lastSubmissionEpochMs = System.currentTimeMillis();
        lastSubmissionError = "";
        lastSubmissionStatus = "in-progress";

        String uploadId = generateUploadId();
        currentUploadId = uploadId;
        uploadStartMs = System.currentTimeMillis();

        src.sendSuccess(() -> CommandFeedback.info("[SA] Preparing latest report..."), false);

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
                    src.sendSuccess(() -> CommandFeedback.warn("[SA] Report upload failed: internal submit error."), false);
                    src.sendSuccess(() -> CommandFeedback.info("[SA] Reason: " + errClass + " - " + shortErr), false);
                    src.sendSuccess(() -> CommandFeedback.info("[SA] Upload lock cleared."), false);
                    if (SAConfig.INSTANCE.fallbackToLocal.get()) {
                        saveLocalFallback(src, report, markdown);
                    }
                } finally {
                    submissionInProgress = false;
                }
            }, UPLOAD_EXECUTOR);
        } catch (Throwable t) {
            // runAsync itself failed (e.g. executor shut down)
            submissionInProgress = false;
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
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Submission blocked for privacy: sensitive data detected."), false);
            if (SAConfig.INSTANCE.fallbackToLocal.get()) {
                saveLocalFallback(src, report, markdown);
                src.sendSuccess(() -> CommandFeedback.info("[SA] Local sanitized fallback saved."), false);
            }
            return;
        }
        String sanitizedMarkdown = mdResult.text();

        src.sendSuccess(() -> CommandFeedback.info("[SA] Collecting diagnostics and sanitized logs..."), false);

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

        // Build payload (can throw - caught by caller)
        String payload = buildCloudflarePayload(report, sanitizedMarkdown, reportHash,
            logExcerpt, fullLog, stutterLogEvts, freezeContext, suspiciousSignals);

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

        src.sendSuccess(() -> CommandFeedback.info("[SA] Uploading report to Stutter Analyzer report server..."), false);

        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        int timeoutSec = SAConfig.INSTANCE.uploadTimeoutSeconds.get();

        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("User-Agent", "StutterAnalyzer/" + StutterAnalyzerMod.MOD_VERSION + " Minecraft/1.20.4")
                .timeout(Duration.ofSeconds(timeoutSec))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            handleCloudflareResponse(src, report, markdown, resp.statusCode(), resp.body());
        } catch (java.net.http.HttpTimeoutException e) {
            lastSubmissionStatus = "timeout";
            lastSubmissionError = "timed out after " + timeoutSec + "s";
            StutterAnalyzerMod.LOGGER.warn("[SA] Upload timed out after {}s (upload_id={})", timeoutSec, uploadId);
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Report upload timed out after " + timeoutSec + "s."), false);
            if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            String shortErr = errMsg.length() > 80 ? errMsg.substring(0, 80) : errMsg;
            lastSubmissionStatus = "failure";
            lastSubmissionError = shortErr;
            StutterAnalyzerMod.LOGGER.warn("[SA] Upload network error (upload_id={}): {}", uploadId, errMsg);
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Report upload failed: could not reach report server."), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] Reason: " + shortErr), false);
            if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
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
            src.sendSuccess(() -> CommandFeedback.info("[SA] This report was already submitted."), false);
            return;
        }
        if (status == 429) {
            lastSubmissionStatus = "rate-limited";
            src.sendSuccess(() -> CommandFeedback.warn("[SA] Report server rate limit reached. Local fallback saved."), false);
            if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
            return;
        }
        if (status == 200 && body.contains("\"ok\":true")) {
            String reportId        = extractJsonField(body, "report_id");
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

            src.sendSuccess(() -> CommandFeedback.success("[SA] Report submitted successfully."), false);
            src.sendSuccess(() -> CommandFeedback.info("[SA] Report ID: " + finalId), false);

            if ("GITHUB_FORWARD_FAILED".equals(warning)) {
                src.sendSuccess(() -> CommandFeedback.warn("[SA] GitHub forwarding failed server-side, but no data was lost."), false);
            } else if (issueNum != null && !issueNum.equals("null") && !issueNum.isBlank()) {
                src.sendSuccess(() -> CommandFeedback.info("[SA] GitHub issue created: #" + issueNum), false);
                if (issueUrl != null && !issueUrl.isBlank()) {
                    src.sendSuccess(() -> CommandFeedback.info("[SA] " + issueUrl), false);
                }
            } else {
                src.sendSuccess(() -> CommandFeedback.info("[SA] Stored for developer review."), false);
            }

            StringBuilder included = new StringBuilder();
            if ("true".equals(incRuntime))      included.append("runtime status, ");
            if ("true".equals(incStutterLog))   included.append("log events, ");
            if ("true".equals(incFreezeCtx))    included.append("Unknown Freeze context, ");
            if ("true".equals(incSuspicious))   included.append("suspicious signals, ");
            if ("true".equals(incLogExcerpt))   included.append("latest.log excerpt, ");
            if (included.length() > 2) {
                String inclStr = included.substring(0, included.length() - 2);
                src.sendSuccess(() -> CommandFeedback.info("[SA] Included: " + inclStr + "."), false);
            }
            if ("true".equals(storedFullLog)) {
                src.sendSuccess(() -> CommandFeedback.info("[SA] Full latest.log stored server-side."), false);
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

            src.sendSuccess(() -> CommandFeedback.info("[SA] Thank you. This helps improve future versions."), false);
            markConsentGiven();
        } else {
            StutterAnalyzerMod.LOGGER.warn("[SA] Cloudflare submit failed: {} {}", status, body);
            lastSubmissionStatus = "failure";
            String errorCode = extractJsonField(body, "error_code");
            if ("RATE_LIMITED".equals(errorCode) || status == 429) {
                lastSubmissionStatus = "rate-limited";
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Report server rate limit reached."), false);
                if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
            } else if ("DUPLICATE_REPORT".equals(errorCode) || status == 409) {
                lastSubmissionStatus = "duplicate";
                src.sendSuccess(() -> CommandFeedback.info("[SA] This report was already submitted."), false);
            } else if ("PAYLOAD_TOO_LARGE".equals(errorCode) || status == 413) {
                lastSubmissionStatus = "too-large";
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Report was too large for upload."), false);
                if (SAConfig.INSTANCE.fallbackToLocal.get()) saveLocalFallback(src, report, markdown);
            } else {
                String desc = errorCode != null ? errorCode : ("HTTP " + status);
                lastSubmissionError = desc;
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Report upload failed: " + desc), false);
                if (SAConfig.INSTANCE.fallbackToLocal.get()) {
                    saveLocalFallback(src, report, markdown);
                } else {
                    src.sendSuccess(() -> CommandFeedback.info("[SA] No data was lost."), false);
                }
            }
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

    private static String buildRuntimeSnapshot() {
        boolean isClient = FMLEnvironment.dist == Dist.CLIENT;
        boolean clientTracker = isClient && SAConfig.INSTANCE.enableClientStutterDetection.get();
        boolean serverTracker = SAConfig.INSTANCE.enableServerTickDetection.get();
        int minorEp = StutterCounter.minorEpisodeCountInSeconds(60);
        long worstMinor = StutterCounter.worstMinorInSeconds(60);
        int mediumEp = StutterCounter.mediumEpisodeCountInSeconds(60);
        long worstMedium = StutterCounter.worstMediumInSeconds(60);
        int severeEp = StutterCounter.severeEpisodeCountInSeconds(60);
        long worstSevere = StutterCounter.worstSevereInSeconds(60);
        int extremeEp = StutterCounter.extremeEpisodeCountInSeconds(60);
        long worstExtreme = StutterCounter.worstExtremeInSeconds(60);
        int savedReports = ReportWriter.savedReports();
        FreezeReport last = ReportWriter.lastReport();
        String lastSaved = last != null
            ? last.event.category().name() + " " + last.event.durationMs() + "ms"
            : "none";
        boolean quiet = SAConfig.INSTANCE.quietMode.get();
        boolean verbose = SAConfig.INSTANCE.verboseMode.get();
        String submission = isCloudflareEnabled() ? "Cloudflare enabled" : "local";
        String side = isClient ? "Client + Integrated Server" : "Dedicated Server";
        return "{\n" +
            "  \"side\": " + esc(side) + ",\n" +
            "  \"client_frame_tracker\": " + esc(clientTracker ? "ON" : "unavailable") + ",\n" +
            "  \"server_tick_tracker\": " + esc(serverTracker ? "ON" : "OFF") + ",\n" +
            "  \"minor_episodes_60s\": " + minorEp + ",\n" +
            "  \"minor_worst_ms\": " + worstMinor + ",\n" +
            "  \"medium_episodes_60s\": " + mediumEp + ",\n" +
            "  \"medium_worst_ms\": " + worstMedium + ",\n" +
            "  \"severe_episodes_60s\": " + severeEp + ",\n" +
            "  \"severe_worst_ms\": " + worstSevere + ",\n" +
            "  \"extreme_episodes_60s\": " + extremeEp + ",\n" +
            "  \"extreme_worst_ms\": " + worstExtreme + ",\n" +
            "  \"reports_saved\": " + savedReports + ",\n" +
            "  \"last_saved_report\": " + esc(lastSaved) + ",\n" +
            "  \"quiet_mode\": " + quiet + ",\n" +
            "  \"verbose_mode\": " + verbose + ",\n" +
            "  \"submission\": " + esc(submission) + "\n" +
            "}";
    }

    private static String buildCloudflarePayload(FreezeReport report, String markdown, String reportHash,
                                                  String logExcerpt, String fullLog,
                                                  String stutterLogEvents, String freezeContext,
                                                  String suspiciousSignals) {
        String category   = report.event.category().name();
        long durationMs   = report.event.durationMs();
        double confidence = report.event.confidence();
        boolean inclMods    = SAConfig.INSTANCE.submissionIncludeModList.get();
        boolean inclSys     = SAConfig.INSTANCE.submissionIncludeSystemInfo.get();
        boolean inclEvt     = SAConfig.INSTANCE.submissionIncludeRecentEvents.get();
        boolean inclLog          = logExcerpt != null && !logExcerpt.isBlank();
        boolean inclFullLog      = fullLog != null && !fullLog.isBlank();
        // truncate large new sections before building payload
        String stutterLogEvtsTrunc = (stutterLogEvents != null && stutterLogEvents.length() > 25000)
            ? stutterLogEvents.substring(0, 24800) + "\n...(truncated)" : stutterLogEvents;
        String freezeCtxTrunc = (freezeContext != null && freezeContext.length() > 50000)
            ? freezeContext.substring(0, 49800) + "\n...(truncated)" : freezeContext;
        String suspiciousTrunc = (suspiciousSignals != null && suspiciousSignals.length() > 25000)
            ? suspiciousSignals.substring(0, 24800) + "\n...(truncated)" : suspiciousSignals;
        boolean inclStutterLog  = stutterLogEvtsTrunc != null && !stutterLogEvtsTrunc.isBlank()
            && !stutterLogEvtsTrunc.startsWith("No Stutter") && !stutterLogEvtsTrunc.startsWith("Could not") && !stutterLogEvtsTrunc.startsWith("latest.log");
        boolean inclFreezeCtx   = freezeCtxTrunc != null && !freezeCtxTrunc.isBlank()
            && !freezeCtxTrunc.startsWith("No Unknown") && !freezeCtxTrunc.startsWith("Could not") && !freezeCtxTrunc.startsWith("latest.log");
        boolean inclSuspicious  = suspiciousTrunc != null && !suspiciousTrunc.isBlank()
            && !suspiciousTrunc.startsWith("No suspicious") && !suspiciousTrunc.startsWith("Could not") && !suspiciousTrunc.startsWith("latest.log");

        // ── json_report object ──────────────────────────────────────────────
        StringBuilder jr = new StringBuilder();
        jr.append("{\n");
        jr.append("      \"report_id\": ").append(esc(report.reportId)).append(",\n");
        jr.append("      \"side\": ").append(esc(String.valueOf(report.event.side()))).append(",\n");
        jr.append("      \"reason\": ").append(esc(report.event.reason())).append(",\n");
        jr.append("      \"evidence\": ").append(esc(report.event.evidence())).append(",\n");
        jr.append("      \"recommendation\": ").append(esc(report.event.recommendation())).append(",\n");

        // metrics
        jr.append("      \"metrics\": {\n");
        // server tick
        var st = MetricsCollector.serverTick();
        jr.append("        \"server_tick\": {\n");
        jr.append("          \"current_mspt\": ").append(String.format("%.2f", st.currentMspt())).append(",\n");
        jr.append("          \"rolling_avg_mspt\": ").append(String.format("%.2f", st.rollingAvgMspt())).append(",\n");
        jr.append("          \"peak_mspt\": ").append(String.format("%.2f", st.peakMspt())).append(",\n");
        jr.append("          \"tps_estimate\": ").append(String.format("%.2f", st.tpsEstimate())).append("\n");
        jr.append("        },\n");
        // memory/GC
        var mem = MetricsCollector.memoryGc();
        jr.append("        \"memory_gc\": {\n");
        jr.append("          \"heap_used_mb\": ").append(mem.heapUsedMb()).append(",\n");
        jr.append("          \"heap_max_mb\": ").append(mem.heapMaxMb()).append(",\n");
        jr.append("          \"heap_percent\": ").append(String.format("%.1f", mem.heapPercent())).append(",\n");
        jr.append("          \"recent_gc_count\": ").append(mem.recentGc()).append(",\n");
        jr.append("          \"last_gc_pause_ms\": ").append(mem.lastGcPauseMs()).append("\n");
        jr.append("        },\n");
        // episode counters
        jr.append("        \"stutter_counts\": {\n");
        jr.append("          \"minor_episodes_60s\": ").append(StutterCounter.minorEpisodeCountInSeconds(60)).append(",\n");
        jr.append("          \"minor_worst_ms\": ").append(StutterCounter.worstMinorInSeconds(60)).append(",\n");
        jr.append("          \"medium_episodes_60s\": ").append(StutterCounter.mediumEpisodeCountInSeconds(60)).append(",\n");
        jr.append("          \"medium_worst_ms\": ").append(StutterCounter.worstMediumInSeconds(60)).append(",\n");
        jr.append("          \"severe_episodes_60s\": ").append(StutterCounter.severeEpisodeCountInSeconds(60)).append(",\n");
        jr.append("          \"severe_worst_ms\": ").append(StutterCounter.worstSevereInSeconds(60)).append(",\n");
        jr.append("          \"extreme_episodes_60s\": ").append(StutterCounter.extremeEpisodeCountInSeconds(60)).append(",\n");
        jr.append("          \"extreme_worst_ms\": ").append(StutterCounter.worstExtremeInSeconds(60)).append("\n");
        jr.append("        }\n");
        jr.append("      },\n");

        // system info
        if (inclSys) {
            jr.append("      \"system_info\": ").append(esc(report.systemInfo)).append(",\n");
            jr.append("      \"heap_info\": ").append(esc(report.heapInfo)).append(",\n");
        }

        // mods
        if (inclMods && report.loadedMods != null && !report.loadedMods.isEmpty()) {
            jr.append("      \"optimization_mods\": [\n");
            List<ModInventory.ModEntry> mods = report.loadedMods;
            for (int i = 0; i < mods.size(); i++) {
                ModInventory.ModEntry m = mods.get(i);
                jr.append("        {\"mod_id\":").append(esc(m.modId))
                  .append(",\"name\":").append(esc(m.displayName))
                  .append(",\"version\":").append(esc(m.version)).append("}");
                if (i < mods.size() - 1) jr.append(",");
                jr.append("\n");
            }
            jr.append("      ],\n");
        }

        // timeline
        if (inclEvt && report.event.recentTimeline() != null && !report.event.recentTimeline().isEmpty()) {
            jr.append("      \"timeline\": [\n");
            var tl = report.event.recentTimeline();
            int start = Math.max(0, tl.size() - 30);
            for (int i = start; i < tl.size(); i++) {
                jr.append("        ").append(esc(tl.get(i).toString()));
                if (i < tl.size() - 1) jr.append(",");
                jr.append("\n");
            }
            jr.append("      ],\n");
        }

        jr.append("      \"recommendations\": [").append(esc(report.event.recommendation())).append("]\n");
        jr.append("    }");

        // Sanitize json_report
        ReportSanitizer.SanitizeResult jrSanitized = ReportSanitizer.sanitize(jr.toString());
        String jsonReport = jrSanitized.hadSensitiveData() ? "{\"error\":\"sensitive data redacted\"}" : jrSanitized.text();

        String runtimeSnapshot = buildRuntimeSnapshot();
        return "{\n" +
            "  \"schema_version\": 1,\n" +
            "  \"source\": \"real_mod_submit\",\n" +
            "  \"project\": \"stutter-analyzer\",\n" +
            "  \"mod_version\": " + esc(StutterAnalyzerMod.MOD_VERSION) + ",\n" +
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
            "  \"json_report\": " + jsonReport + ",\n" +
            "  \"runtime_status_snapshot\": " + runtimeSnapshot + ",\n" +
            (inclStutterLog  ? "  \"stutter_log_events\": " + escJson(stutterLogEvtsTrunc) + ",\n" : "  \"stutter_log_events\": " + escJson(stutterLogEvents != null ? stutterLogEvents : "No Stutter Analyzer log events were found in latest.log.") + ",\n") +
            (inclFreezeCtx   ? "  \"unknown_freeze_context\": " + escJson(freezeCtxTrunc) + ",\n" : "  \"unknown_freeze_context\": " + escJson(freezeContext != null ? freezeContext : "No Unknown Freeze context was found in latest.log.") + ",\n") +
            (inclSuspicious  ? "  \"suspicious_log_signals\": " + escJson(suspiciousTrunc) + ",\n" : "  \"suspicious_log_signals\": " + escJson(suspiciousSignals != null ? suspiciousSignals : "No suspicious log signals were found.") + ",\n") +
            (inclLog ? "  \"latest_log_excerpt\": " + escJson(logExcerpt) + ",\n" : "") +
            (inclFullLog ? "  \"full_latest_log\": " + escJson(fullLog) + ",\n" : "") +
            "  \"client_generated_at\": " + esc(Instant.now().toString()) + ",\n" +
            "  \"privacy\": {\n" +
            "    \"sanitized\": true,\n" +
            "    \"contains_mod_list\": " + inclMods + ",\n" +
            "    \"contains_system_info\": " + inclSys + ",\n" +
            "    \"contains_logs\": " + inclLog + ",\n" +
            "    \"contains_stutter_log_events\": " + inclStutterLog + ",\n" +
            "    \"contains_unknown_freeze_context\": " + inclFreezeCtx + ",\n" +
            "    \"contains_suspicious_log_signals\": " + inclSuspicious + ",\n" +
            "    \"contains_full_latest_log\": " + inclFullLog + ",\n" +
            "    \"contains_tokens\": false\n" +
            "  }\n" +
            "}\n";
    }

    // ── Submit preview ────────────────────────────────────────────────────────

    public static int submitPreview(CommandSourceStack src) {
        FreezeReport report = ReportWriter.lastReport();
        if (report == null) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] No report available to preview."), false);
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

        src.sendSuccess(() -> CommandFeedback.header("[SA] Submit preview"), false);
        src.sendSuccess(() -> CommandFeedback.info("- Report ID: " + report.reportId), false);
        src.sendSuccess(() -> CommandFeedback.info("- Category: " + category), false);
        src.sendSuccess(() -> CommandFeedback.info("- Duration: " + durationMs + " ms"), false);
        src.sendSuccess(() -> CommandFeedback.info("- Report: included, " + mdKb + " KB"), false);
        src.sendSuccess(() -> CommandFeedback.info("- JSON: included (metrics, mods, timeline)"), false);
        src.sendSuccess(() -> CommandFeedback.info("- Runtime status: included"), false);

        // Stutter log events
        if (stutterDisabled) {
            src.sendSuccess(() -> CommandFeedback.info("- Stutter Analyzer log events: disabled in config"), false);
        } else if (stutterMeaningful) {
            int sl = stutterLines; int sk = stutterKb;
            src.sendSuccess(() -> CommandFeedback.info("- Stutter Analyzer log events: " + sl + " lines, " + sk + " KB"), false);
        } else {
            String reason = stutterLogStr != null ? stutterLogStr : "latest.log could not be read";
            src.sendSuccess(() -> CommandFeedback.info("- Stutter Analyzer log events: 0 lines, " + reason), false);
        }

        // Unknown Freeze context
        if (freezeDisabled) {
            src.sendSuccess(() -> CommandFeedback.info("- Unknown Freeze context: disabled in config"), false);
        } else if (freezeMeaningful) {
            int fe = freezeEvts; int fk = freezeKb;
            src.sendSuccess(() -> CommandFeedback.info("- Unknown Freeze context: " + fe + " events, " + fk + " KB"), false);
        } else {
            String reason = freezeCtxStr != null ? freezeCtxStr : "latest.log could not be read";
            src.sendSuccess(() -> CommandFeedback.info("- Unknown Freeze context: 0 events, " + reason), false);
        }

        // Suspicious signals
        if (suspDisabled) {
            src.sendSuccess(() -> CommandFeedback.info("- Suspicious log signals: disabled in config"), false);
        } else if (suspMeaningful) {
            int ss = suspLines; int sk = suspKb;
            src.sendSuccess(() -> CommandFeedback.info("- Suspicious log signals: " + ss + " lines, " + sk + " KB"), false);
        } else {
            String reason = suspiciousStr != null ? suspiciousStr : "latest.log could not be read";
            src.sendSuccess(() -> CommandFeedback.info("- Suspicious log signals: 0 lines, " + reason), false);
        }

        // Log excerpt
        if (!SAConfig.INSTANCE.includeLogExcerpt.get()) {
            src.sendSuccess(() -> CommandFeedback.info("- Latest log excerpt: disabled in config"), false);
        } else if (logMeaningful) {
            int ll = logLines; int lk = logKb;
            src.sendSuccess(() -> CommandFeedback.info("- Latest log excerpt: " + ll + " lines, " + lk + " KB"), false);
        } else {
            String reason = logExcerptStr != null ? logExcerptStr : "latest.log could not be read";
            src.sendSuccess(() -> CommandFeedback.info("- Latest log excerpt: unavailable, " + reason), false);
        }

        // Full log
        if (!fullLogEnabled) {
            src.sendSuccess(() -> CommandFeedback.info("- Full latest.log: disabled in config"), false);
        } else if (fullLogStr != null) {
            int fk = fullLogKb;
            src.sendSuccess(() -> CommandFeedback.info("- Full latest.log: included, " + fk + " KB / stored server-side"), false);
        } else {
            src.sendSuccess(() -> CommandFeedback.info("- Full latest.log: unavailable or sanitizer blocked it"), false);
        }

        src.sendSuccess(() -> CommandFeedback.info("- Sensitive data scan: " + (mdResult.hadSensitiveData() ? "BLOCKED - sensitive data found" : "passed")), false);
        src.sendSuccess(() -> CommandFeedback.info("- Report hash: " + hash.substring(0, 16) + "..."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Run /sa submit to upload this report."), false);
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

    private static String generateUploadId() {
        LocalDateTime now = LocalDateTime.now();
        String ts = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String rand = String.format("%04x", System.nanoTime() & 0xFFFFL);
        return "UP-" + ts + "-" + rand;
    }

    public static void handleTopLevelSubmitCrash(CommandSourceStack src, Throwable t) {
        try {
            submissionInProgress = false;
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

    public static int submitMinimal(CommandSourceStack src) {
        src.sendSuccess(() -> CommandFeedback.info("[SA] Sending minimal test payload to Worker..."), false);
        String endpoint = SAConfig.INSTANCE.cloudflareEndpoint.get();
        if (endpoint == null || endpoint.isBlank()) {
            src.sendSuccess(() -> CommandFeedback.warn("[SA] No Cloudflare endpoint configured."), false);
            return 1;
        }
        String minimalHash = "devtest-" + Long.toHexString(System.currentTimeMillis());
        String payload = "{\"schema_version\":1,\"source\":\"dev_test_submit\","
            + "\"project\":\"stutter-analyzer\","
            + "\"report_type\":\"UNKNOWN_FREEZE\",\"category\":\"UNKNOWN_FREEZE\","
            + "\"duration_ms\":300,\"confidence\":0.5,"
            + "\"report_hash\":" + esc(minimalHash) + ","
            + "\"summary\":\"Minimal submit test\","
            + "\"markdown_report\":\"Minimal report\","
            + "\"json_report\":{},"
            + "\"runtime_status_snapshot\":{},"
            + "\"stutter_log_events\":\"Minimal test event\","
            + "\"unknown_freeze_context\":\"Minimal test context\","
            + "\"suspicious_log_signals\":\"Minimal test signal\","
            + "\"latest_log_excerpt\":\"Minimal test excerpt\","
            + "\"privacy\":{\"sanitized\":true}}";
        int timeoutSec = SAConfig.INSTANCE.uploadTimeoutSeconds.get();
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "StutterAnalyzer/" + StutterAnalyzerMod.MOD_VERSION + " Minecraft/1.20.4 (minimal-test)")
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                int status = resp.statusCode();
                String body = resp.body() != null ? resp.body() : "(empty)";
                String preview = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                src.sendSuccess(() -> CommandFeedback.info("[SA] Minimal test HTTP " + status + ": " + preview), false);
                if (status == 200) {
                    src.sendSuccess(() -> CommandFeedback.success("[SA] Worker accepted minimal payload."), false);
                    src.sendSuccess(() -> CommandFeedback.info("[SA] If /sa submit still fails, the bug is in payload/log building."), false);
                } else {
                    src.sendSuccess(() -> CommandFeedback.warn("[SA] Worker rejected minimal payload (HTTP " + status + ")."), false);
                    src.sendSuccess(() -> CommandFeedback.info("[SA] Bug is in command routing, Worker, or network."), false);
                }
            } catch (Exception e) {
                src.sendSuccess(() -> CommandFeedback.warn("[SA] Minimal test error: " + e.getClass().getSimpleName() + ": " + e.getMessage()), false);
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
