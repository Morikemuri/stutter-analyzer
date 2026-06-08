package com.stutteranalyzer.config;

import com.stutteranalyzer.SAEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SAConfig {

    private static final Logger LOGGER = LogManager.getLogger("stutteranalyzer");
    public static final SAConfig INSTANCE = new SAConfig();

    // ── Wrapper types (match ForgeConfigSpec API) ──────────────────────────
    public static final class BooleanValue {
        private volatile boolean v;
        BooleanValue(boolean d) { v = d; }
        public boolean get() { return v; }
        public void set(boolean val) { v = val; INSTANCE.scheduleSave(); }
    }

    public static final class IntValue {
        private volatile int v;
        private final int min, max;
        IntValue(int d, int min, int max) { v = d; this.min = min; this.max = max; }
        public int get() { return v; }
        public void set(int val) { v = Math.max(min, Math.min(max, val)); INSTANCE.scheduleSave(); }
    }

    public static final class DoubleValue {
        private volatile double v;
        DoubleValue(double d) { v = d; }
        public double get() { return v; }
        public void set(double val) { v = val; INSTANCE.scheduleSave(); }
    }

    public static final class StringValue {
        private volatile String v;
        StringValue(String d) { v = d; }
        public String get() { return v; }
        public void set(String val) { v = val; INSTANCE.scheduleSave(); }
    }

    // ── [general] ─────────────────────────────────────────────────────────
    public final BooleanValue enabled              = new BooleanValue(true);
    public final BooleanValue debug                = new BooleanValue(false);
    public final BooleanValue overlay              = new BooleanValue(false);
    public final StringValue  reportFormat         = new StringValue("markdown,json");
    public final IntValue     maxReports           = new IntValue(50, 1, 500);

    // ── [commands] ────────────────────────────────────────────────────────
    public final BooleanValue enableCommands                           = new BooleanValue(true);
    public final BooleanValue enableAliasSa                           = new BooleanValue(true);
    public final BooleanValue allowPlayersBasicStatus                 = new BooleanValue(true);
    public final BooleanValue allowPlayersExportOwnClientReports      = new BooleanValue(true);
    public final IntValue     serverReportPermissionLevel             = new IntValue(2, 0, 4);
    public final IntValue     configReloadPermissionLevel             = new IntValue(3, 0, 4);
    public final IntValue     deleteReportPermissionLevel             = new IntValue(3, 0, 4);
    public final IntValue     debugPermissionLevel                    = new IntValue(4, 0, 4);
    public final IntValue     submitReportPermissionLevel             = new IntValue(3, 0, 4);
    public final IntValue     guardPermissionLevel                    = new IntValue(4, 0, 4);

    // ── [client] ──────────────────────────────────────────────────────────
    public final BooleanValue enableClientStutterDetection = new BooleanValue(true);
    public final IntValue     minorFrameMs                 = new IntValue(50, 1, 10000);
    public final IntValue     mediumFrameMs                = new IntValue(100, 1, 10000);
    public final IntValue     severeFrameMs                = new IntValue(250, 1, 10000);
    public final IntValue     extremeFrameMs               = new IntValue(1000, 1, 60000);
    public final BooleanValue trackOnePercentLows          = new BooleanValue(true);
    public final BooleanValue trackPointOnePercentLows     = new BooleanValue(true);

    // ── [server] ──────────────────────────────────────────────────────────
    public final BooleanValue enableServerTickDetection = new BooleanValue(true);
    public final IntValue     warningMspt               = new IntValue(50, 1, 10000);
    public final IntValue     mediumMspt                = new IntValue(100, 1, 10000);
    public final IntValue     severeMspt                = new IntValue(250, 1, 10000);
    public final IntValue     extremeMspt               = new IntValue(1000, 1, 60000);

    // ── [classification] ──────────────────────────────────────────────────
    public final DoubleValue  minimumConfidence    = new DoubleValue(0.60);
    public final BooleanValue unknownFreezeEnabled = new BooleanValue(true);

    // ── [safety] ──────────────────────────────────────────────────────────
    public final BooleanValue failSilently           = new BooleanValue(false);
    public final BooleanValue disableFailedSubsystems = new BooleanValue(true);
    public final BooleanValue neverCrashGame          = new BooleanValue(true);

    // ── [reports] ─────────────────────────────────────────────────────────
    public final BooleanValue saveMarkdown               = new BooleanValue(true);
    public final BooleanValue saveJson                   = new BooleanValue(true);
    public final BooleanValue includeModList             = new BooleanValue(true);
    public final BooleanValue includeSystemInfo          = new BooleanValue(true);
    public final BooleanValue includeRecentEvents        = new BooleanValue(true);
    public final BooleanValue saveMinorStutterReports    = new BooleanValue(false);
    public final BooleanValue saveMediumStutterReports   = new BooleanValue(false);
    public final BooleanValue saveSevereStutterReports   = new BooleanValue(true);
    public final BooleanValue saveExtremeReports         = new BooleanValue(true);
    public final BooleanValue chatNotifyMinorStutters    = new BooleanValue(false);
    public final BooleanValue chatNotifyMediumStutters   = new BooleanValue(false);
    public final BooleanValue chatNotifySevereStutters   = new BooleanValue(true);
    public final BooleanValue chatNotifyExtremeFreeze    = new BooleanValue(true);
    public final IntValue     minimumAutoReportFrameMs   = new IntValue(250, 1, 60000);
    public final IntValue     minimumAutoReportMspt      = new IntValue(100, 1, 60000);
    public final IntValue     chatNotificationCooldownSeconds  = new IntValue(15, 1, 3600);
    public final BooleanValue aggregateRepeatedMinorStutters  = new BooleanValue(true);
    public final IntValue     minorStutterAggregateWindowSeconds = new IntValue(30, 5, 3600);
    public final IntValue     minorStutterAggregateCount          = new IntValue(5, 2, 100);
    public final IntValue     minorStutterAggregateChatCooldownSeconds = new IntValue(60, 5, 3600);

    // ── [debug] ───────────────────────────────────────────────────────────
    public final BooleanValue logDetectionPipeline = new BooleanValue(false);

    // ── [stutter_counting] ────────────────────────────────────────────────
    public final StringValue  countMode            = new StringValue("episodes");
    public final IntValue     episodeGapMs         = new IntValue(250, 50, 5000);
    public final IntValue     episodeMinDurationMs = new IntValue(50, 1, 1000);
    public final BooleanValue showRawFrameSpikeCount = new BooleanValue(false);

    // ── [notifications] ───────────────────────────────────────────────────
    public final BooleanValue quietMode                     = new BooleanValue(true);
    public final BooleanValue minorAggregateChatEnabled     = new BooleanValue(true);
    public final IntValue     minorAggregateChatCooldownSeconds = new IntValue(120, 5, 3600);
    public final BooleanValue minorAggregateShowOnlyIfWorse = new BooleanValue(true);
    public final IntValue     minorAggregateMinCountIncrease   = new IntValue(25, 1, 500);
    public final IntValue     minorAggregateMinWorstIncreaseMs = new IntValue(15, 1, 1000);
    public final BooleanValue verboseMode                   = new BooleanValue(false);
    public final BooleanValue verboseModeSessionOnly        = new BooleanValue(true);
    public final BooleanValue minorChatInVerbose            = new BooleanValue(true);
    public final BooleanValue mediumChatInVerbose           = new BooleanValue(true);
    public final IntValue     verboseChatCooldownSeconds    = new IntValue(5, 1, 60);

    // ── [notifications.alerts] ────────────────────────────────────────────
    public final StringValue  alertMode                          = new StringValue("SEVERE");
    public final IntValue     alertCooldownSeconds               = new IntValue(30, 5, 600);
    public final IntValue     alertSameCategoryCooldownSeconds   = new IntValue(60, 5, 600);
    public final IntValue     alertMaxAlertsPerMinute            = new IntValue(5, 1, 60);
    public final IntValue     scheduledMicroHitchCooldownSeconds = new IntValue(300, 30, 3600);
    public final BooleanValue alertAggregateSmallStutters        = new BooleanValue(true);
    public final IntValue     alertMinorAggregateCooldownSeconds = new IntValue(120, 30, 3600);
    public final BooleanValue alertShowReportHint                = new BooleanValue(true);
    public final BooleanValue alertShowSubmitHint                = new BooleanValue(true);

    // ── [debug_hud] ───────────────────────────────────────────────────────
    public final StringValue  f3CounterMode           = new StringValue("episodes");
    public final BooleanValue showRawSpikeCountOnF3   = new BooleanValue(false);
    public final BooleanValue debugHudEnabled         = new BooleanValue(true);
    public final BooleanValue debugHudShowColored     = new BooleanValue(true);
    public final BooleanValue debugHudShowLastFreeze  = new BooleanValue(true);
    public final BooleanValue debugHudShowEmergencyMode   = new BooleanValue(true);
    public final BooleanValue debugHudShowReportCount = new BooleanValue(true);
    public final BooleanValue debugHudShowSubsystemWarnings = new BooleanValue(true);
    public final BooleanValue debugHudCompactMode     = new BooleanValue(true);
    public final StringValue  f3LinePosition          = new StringValue("BELOW_DEBUG_TEXT");
    public final IntValue     f3LineOffsetX           = new IntValue(4, 0, 200);
    public final IntValue     f3LineExtraGap          = new IntValue(2, 0, 20);
    public final IntValue     f3LineManualYOffset     = new IntValue(0, -50, 200);
    public final IntValue     f3LineCustomX           = new IntValue(4, 0, 500);
    public final IntValue     f3LineCustomY           = new IntValue(240, 2, 1000);

    // ── [submission] ──────────────────────────────────────────────────────
    public final BooleanValue enableManualSubmission              = new BooleanValue(true);
    public final BooleanValue enableAutomaticUnknownFreezeUpload  = new BooleanValue(false);
    public final StringValue  submissionTarget                    = new StringValue("cloudflare");
    public final StringValue  cloudflareEndpoint                  = new StringValue("https://stutter-analyzer-reports.morikemuri.workers.dev/api/report");
    public final BooleanValue fallbackToLocal                     = new BooleanValue(true);
    public final BooleanValue askFirstTime                        = new BooleanValue(false);
    public final BooleanValue askEveryTime                        = new BooleanValue(false);
    public final StringValue  githubIssueUrl                      = new StringValue("https://github.com/Morikemuri/stutter-analyzer/issues/new");
    public final BooleanValue copyIssueBodyToClipboard            = new BooleanValue(false);
    public final BooleanValue openIssueUrlOnClient                = new BooleanValue(false);
    public final BooleanValue enableGistUpload                    = new BooleanValue(false);
    public final BooleanValue enableTokenUpload                   = new BooleanValue(false);
    public final BooleanValue anonymizeReports                    = new BooleanValue(true);
    public final BooleanValue redactUsernames                     = new BooleanValue(true);
    public final BooleanValue redactFilePaths                     = new BooleanValue(true);
    public final BooleanValue redactIpAddresses                   = new BooleanValue(true);
    public final BooleanValue submissionIncludeModList            = new BooleanValue(true);
    public final BooleanValue submissionIncludeSystemInfo         = new BooleanValue(true);
    public final BooleanValue submissionIncludeRecentEvents       = new BooleanValue(true);
    public final BooleanValue askBeforeEveryUpload                = new BooleanValue(true);
    public final IntValue     submitCommandCooldownSeconds        = new IntValue(10, 0, 300);
    public final IntValue     uploadTimeoutSeconds                = new IntValue(30, 5, 120);
    public final StringValue  httpTransport                       = new StringValue("http_url_connection");
    public final BooleanValue includeLogExcerpt                   = new BooleanValue(true);
    public final IntValue     logExcerptMaxLines                  = new IntValue(200, 10, 1000);
    public final IntValue     logExcerptContextSeconds            = new IntValue(30, 5, 300);
    public final BooleanValue includeFullLatestLog                = new BooleanValue(true);
    public final IntValue     maxLogExcerptChars                  = new IntValue(30000, 1000, 65000);
    public final BooleanValue includeDebugLog                     = new BooleanValue(false);
    public final IntValue     maxFullLogChars                     = new IntValue(120000, 10000, 500000);
    public final IntValue     maxIssueBodyChars                   = new IntValue(55000, 10000, 65000);
    public final BooleanValue storeOversizedLogsInWorkerStorage   = new BooleanValue(true);
    public final BooleanValue includeStutterAnalyzerLogEvents     = new BooleanValue(true);
    public final BooleanValue includeUnknownFreezeContext         = new BooleanValue(true);
    public final BooleanValue includeSuspiciousLogSignals         = new BooleanValue(true);
    public final IntValue     maxLogEventLines                    = new IntValue(300, 10, 2000);
    public final IntValue     maxLogContextEvents                 = new IntValue(20, 1, 100);
    public final IntValue     logContextLinesBefore               = new IntValue(20, 1, 200);
    public final IntValue     logContextLinesAfter                = new IntValue(40, 1, 200);
    public final IntValue     maxSuspiciousLogLines               = new IntValue(300, 10, 2000);
    public final BooleanValue showPayloadSummary                  = new BooleanValue(false);

    // ── [updates] ─────────────────────────────────────────────────────────
    public final BooleanValue checkForUpdates         = new BooleanValue(true);
    public final BooleanValue checkOnStartup          = new BooleanValue(true);
    public final IntValue     startupCheckDelaySeconds = new IntValue(10, 1, 300);
    public final IntValue     checkIntervalHours       = new IntValue(12, 1, 168);
    public final BooleanValue notifyOnlyOncePerVersion = new BooleanValue(true);
    public final BooleanValue notifyWhenUpToDate       = new BooleanValue(false);
    public final StringValue  updateVersionUrl         = new StringValue("https://raw.githubusercontent.com/Morikemuri/stutter-analyzer/main/version.json");
    public final StringValue  updateGithubPage         = new StringValue("https://github.com/Morikemuri/stutter-analyzer");
    public final StringValue  updateCurseforgeUrl      = new StringValue("https://www.curseforge.com/minecraft/mc-mods/stutter-analyzer");
    public final BooleanValue openLinksOnClick         = new BooleanValue(true);

    // ── [startup_message] ─────────────────────────────────────────────────
    public final BooleanValue showStartupMessage            = new BooleanValue(true);
    public final BooleanValue showStartupMessageOncePerSession = new BooleanValue(true);
    public final BooleanValue mentionSilentMinorTracking    = new BooleanValue(true);

    // ── [compatibility_guard] ─────────────────────────────────────────────
    public final BooleanValue guardEnabled              = new BooleanValue(true);
    public final BooleanValue guardEmergencyMode        = new BooleanValue(false);
    public final BooleanValue automaticSafeGuards       = new BooleanValue(true);
    public final BooleanValue warnOnlyForUnsafePatterns = new BooleanValue(true);
    public final BooleanValue writeCrashHints           = new BooleanValue(true);
    public final BooleanValue writeLatestLogHints       = new BooleanValue(true);
    public final BooleanValue writeSeparateHintReports  = new BooleanValue(true);
    public final BooleanValue rateLimitGuardTriggers    = new BooleanValue(true);
    public final IntValue     maxGuardTriggersPerSession = new IntValue(20, 1, 1000);
    public final DoubleValue  minimumAutoGuardConfidence = new DoubleValue(0.90);
    public final DoubleValue  minimumWarnConfidence      = new DoubleValue(0.60);

    public final StringValue guardRubidiumLava             = new StringValue("auto");
    public final StringValue guardDynamicFps               = new StringValue("auto");
    public final StringValue guardC2meCorruptedChunk       = new StringValue("warn_only");
    public final StringValue guardC2meDeadlock             = new StringValue("warn_only");
    public final StringValue guardDistantHorizons          = new StringValue("warn_only");
    public final StringValue guardIrisOculus               = new StringValue("warn_only");
    public final StringValue guardStarlightScalableLux     = new StringValue("warn_only");
    public final StringValue guardEmbeddiumOculus          = new StringValue("warn_only");
    public final StringValue guardChunkAnimator            = new StringValue("warn_only");

    // ── Config file I/O ───────────────────────────────────────────────────

    private final AtomicBoolean savePending = new AtomicBoolean(false);
    private final ScheduledExecutorService saveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SA-ConfigSave");
        t.setDaemon(true);
        return t;
    });

    private void scheduleSave() {
        if (savePending.compareAndSet(false, true)) {
            saveScheduler.schedule(() -> {
                savePending.set(false);
                save();
            }, 2, TimeUnit.SECONDS);
        }
    }

    public static void load() {
        Path file = configFile();
        if (!Files.exists(file)) {
            INSTANCE.save();
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            Map<String, String> flat = parseToml(lines);
            INSTANCE.applyMap(flat);
            INSTANCE.migrateIfNeeded();
            LOGGER.info("[StutterAnalyzer] Config loaded from {}", file);
        } catch (Exception e) {
            LOGGER.warn("[StutterAnalyzer] Config load failed ({}), using defaults: {}", file, e.getMessage());
        }
    }

    private void migrateIfNeeded() {
        // BOTTOM_LEFT was the wrong old default; migrate to BELOW_DEBUG_TEXT automatically
        if ("BOTTOM_LEFT".equalsIgnoreCase(f3LinePosition.get())) {
            f3LinePosition.set("BELOW_DEBUG_TEXT");
            LOGGER.info("[StutterAnalyzer] Migrated f3_line_position: BOTTOM_LEFT -> BELOW_DEBUG_TEXT");
        }
    }

    public void save() {
        Path file = configFile();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, buildToml(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("[StutterAnalyzer] Config save failed: {}", e.getMessage());
        }
    }

    private static Path configFile() {
        return SAEnvironment.getConfigDir().resolve("stutter-analyzer/stutteranalyzer-common.toml");
    }

    private static Map<String, String> parseToml(List<String> lines) {
        Map<String, String> result = new LinkedHashMap<>();
        String section = "";
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[")) {
                int end = line.indexOf(']');
                if (end > 0) section = line.substring(1, end).trim() + ".";
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = section + line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                val = val.substring(1, val.length() - 1);
            }
            result.put(key, val);
        }
        return result;
    }

    private void applyMap(Map<String, String> m) {
        b(m, "general.enabled", enabled);
        b(m, "general.debug", debug);
        b(m, "general.overlay", overlay);
        s(m, "general.report_format", reportFormat);
        i(m, "general.max_reports", maxReports);
        b(m, "commands.enable_commands", enableCommands);
        b(m, "commands.enable_alias_sa", enableAliasSa);
        b(m, "commands.allow_players_to_view_basic_status", allowPlayersBasicStatus);
        b(m, "commands.allow_players_to_export_own_client_reports", allowPlayersExportOwnClientReports);
        i(m, "commands.server_report_permission_level", serverReportPermissionLevel);
        i(m, "commands.config_reload_permission_level", configReloadPermissionLevel);
        i(m, "commands.delete_report_permission_level", deleteReportPermissionLevel);
        i(m, "commands.debug_permission_level", debugPermissionLevel);
        i(m, "commands.submit_report_permission_level", submitReportPermissionLevel);
        i(m, "commands.guard_permission_level", guardPermissionLevel);
        b(m, "client.enable_client_stutter_detection", enableClientStutterDetection);
        i(m, "client.minor_frame_ms", minorFrameMs);
        i(m, "client.medium_frame_ms", mediumFrameMs);
        i(m, "client.severe_frame_ms", severeFrameMs);
        i(m, "client.extreme_frame_ms", extremeFrameMs);
        b(m, "client.track_one_percent_lows", trackOnePercentLows);
        b(m, "client.track_point_one_percent_lows", trackPointOnePercentLows);
        b(m, "server.enable_server_tick_detection", enableServerTickDetection);
        i(m, "server.warning_mspt", warningMspt);
        i(m, "server.medium_mspt", mediumMspt);
        i(m, "server.severe_mspt", severeMspt);
        i(m, "server.extreme_mspt", extremeMspt);
        d(m, "classification.minimum_confidence", minimumConfidence);
        b(m, "classification.unknown_freeze_enabled", unknownFreezeEnabled);
        b(m, "safety.fail_silently", failSilently);
        b(m, "safety.disable_failed_subsystems", disableFailedSubsystems);
        b(m, "safety.never_crash_game", neverCrashGame);
        b(m, "reports.save_markdown", saveMarkdown);
        b(m, "reports.save_json", saveJson);
        b(m, "reports.include_mod_list", includeModList);
        b(m, "reports.include_system_info", includeSystemInfo);
        b(m, "reports.include_recent_events", includeRecentEvents);
        b(m, "reports.save_minor_stutter_reports", saveMinorStutterReports);
        b(m, "reports.save_medium_stutter_reports", saveMediumStutterReports);
        b(m, "reports.save_severe_stutter_reports", saveSevereStutterReports);
        b(m, "reports.save_extreme_freeze_reports", saveExtremeReports);
        b(m, "reports.chat_notify_minor_stutters", chatNotifyMinorStutters);
        b(m, "reports.chat_notify_medium_stutters", chatNotifyMediumStutters);
        b(m, "reports.chat_notify_severe_stutters", chatNotifySevereStutters);
        b(m, "reports.chat_notify_extreme_freezes", chatNotifyExtremeFreeze);
        i(m, "reports.minimum_auto_report_frame_ms", minimumAutoReportFrameMs);
        i(m, "reports.minimum_auto_report_mspt", minimumAutoReportMspt);
        i(m, "reports.chat_notification_cooldown_seconds", chatNotificationCooldownSeconds);
        b(m, "reports.aggregate_repeated_minor_stutters", aggregateRepeatedMinorStutters);
        i(m, "reports.minor_stutter_aggregate_window_seconds", minorStutterAggregateWindowSeconds);
        i(m, "reports.minor_stutter_aggregate_count", minorStutterAggregateCount);
        i(m, "reports.minor_stutter_aggregate_chat_cooldown_seconds", minorStutterAggregateChatCooldownSeconds);
        b(m, "debug.log_detection_pipeline", logDetectionPipeline);
        s(m, "stutter_counting.count_mode", countMode);
        i(m, "stutter_counting.episode_gap_ms", episodeGapMs);
        i(m, "stutter_counting.episode_min_duration_ms", episodeMinDurationMs);
        b(m, "stutter_counting.show_raw_frame_spike_count", showRawFrameSpikeCount);
        b(m, "notifications.quiet_mode", quietMode);
        b(m, "notifications.minor_aggregate_chat_enabled", minorAggregateChatEnabled);
        i(m, "notifications.minor_aggregate_chat_cooldown_seconds", minorAggregateChatCooldownSeconds);
        b(m, "notifications.minor_aggregate_show_only_if_worse", minorAggregateShowOnlyIfWorse);
        i(m, "notifications.minor_aggregate_min_count_increase", minorAggregateMinCountIncrease);
        i(m, "notifications.minor_aggregate_min_worst_increase_ms", minorAggregateMinWorstIncreaseMs);
        b(m, "notifications.verbose_mode", verboseMode);
        b(m, "notifications.verbose_mode_session_only", verboseModeSessionOnly);
        b(m, "notifications.minor_chat_in_verbose", minorChatInVerbose);
        b(m, "notifications.medium_chat_in_verbose", mediumChatInVerbose);
        i(m, "notifications.verbose_chat_cooldown_seconds", verboseChatCooldownSeconds);
        s(m, "notifications.alerts.alert_mode", alertMode);
        i(m, "notifications.alerts.alert_cooldown_seconds", alertCooldownSeconds);
        i(m, "notifications.alerts.same_category_cooldown_seconds", alertSameCategoryCooldownSeconds);
        i(m, "notifications.alerts.max_alerts_per_minute", alertMaxAlertsPerMinute);
        i(m, "notifications.alerts.scheduled_micro_hitch_cooldown_seconds", scheduledMicroHitchCooldownSeconds);
        b(m, "notifications.alerts.aggregate_small_stutters", alertAggregateSmallStutters);
        i(m, "notifications.alerts.minor_aggregate_cooldown_seconds", alertMinorAggregateCooldownSeconds);
        b(m, "notifications.alerts.show_report_hint", alertShowReportHint);
        b(m, "notifications.alerts.show_submit_hint", alertShowSubmitHint);
        s(m, "compatibility_guard.debug_hud.f3_counter_mode", f3CounterMode);
        b(m, "compatibility_guard.debug_hud.show_raw_spike_count_on_f3", showRawSpikeCountOnF3);
        b(m, "compatibility_guard.debug_hud.enable_f3_status_line", debugHudEnabled);
        b(m, "compatibility_guard.debug_hud.show_colored_status", debugHudShowColored);
        b(m, "compatibility_guard.debug_hud.show_last_freeze_on_f3", debugHudShowLastFreeze);
        b(m, "compatibility_guard.debug_hud.show_emergency_mode_on_f3", debugHudShowEmergencyMode);
        b(m, "compatibility_guard.debug_hud.show_report_count_on_f3", debugHudShowReportCount);
        b(m, "compatibility_guard.debug_hud.show_subsystem_warnings_on_f3", debugHudShowSubsystemWarnings);
        b(m, "compatibility_guard.debug_hud.compact_mode", debugHudCompactMode);
        s(m, "compatibility_guard.debug_hud.f3_line_position", f3LinePosition);
        i(m, "compatibility_guard.debug_hud.f3_line_offset_x", f3LineOffsetX);
        i(m, "compatibility_guard.debug_hud.f3_line_extra_gap", f3LineExtraGap);
        i(m, "compatibility_guard.debug_hud.f3_line_manual_y_offset", f3LineManualYOffset);
        i(m, "compatibility_guard.debug_hud.f3_line_custom_x", f3LineCustomX);
        i(m, "compatibility_guard.debug_hud.f3_line_custom_y", f3LineCustomY);
        b(m, "compatibility_guard.startup_message.show_startup_message", showStartupMessage);
        b(m, "compatibility_guard.startup_message.show_only_once_per_session", showStartupMessageOncePerSession);
        b(m, "compatibility_guard.startup_message.mention_silent_minor_tracking", mentionSilentMinorTracking);
        b(m, "compatibility_guard.enabled", guardEnabled);
        b(m, "compatibility_guard.emergency_mode", guardEmergencyMode);
        b(m, "compatibility_guard.automatic_safe_guards", automaticSafeGuards);
        b(m, "compatibility_guard.warn_only_for_unsafe_patterns", warnOnlyForUnsafePatterns);
        b(m, "compatibility_guard.write_crash_hints", writeCrashHints);
        b(m, "compatibility_guard.write_latest_log_hints", writeLatestLogHints);
        b(m, "compatibility_guard.write_separate_hint_reports", writeSeparateHintReports);
        b(m, "compatibility_guard.rate_limit_guard_triggers", rateLimitGuardTriggers);
        i(m, "compatibility_guard.max_guard_triggers_per_session", maxGuardTriggersPerSession);
        d(m, "compatibility_guard.minimum_auto_guard_confidence", minimumAutoGuardConfidence);
        d(m, "compatibility_guard.minimum_warn_confidence", minimumWarnConfidence);
        s(m, "compatibility_guard.guards.rubidium_lava_fluid_render", guardRubidiumLava);
        s(m, "compatibility_guard.guards.dynamic_fps_background_false_positive", guardDynamicFps);
        s(m, "compatibility_guard.guards.c2me_corrupted_chunk_context", guardC2meCorruptedChunk);
        s(m, "compatibility_guard.guards.c2me_worldgen_deadlock_context", guardC2meDeadlock);
        s(m, "compatibility_guard.guards.distant_horizons_lod_stutter", guardDistantHorizons);
        s(m, "compatibility_guard.guards.iris_oculus_shader_pipeline_crash", guardIrisOculus);
        s(m, "compatibility_guard.guards.starlight_scalablelux_lighting_crash", guardStarlightScalableLux);
        s(m, "compatibility_guard.guards.embeddium_oculus_taint_context", guardEmbeddiumOculus);
        s(m, "compatibility_guard.guards.chunk_animator_embeddium_mixin_conflict", guardChunkAnimator);
        b(m, "submission.enable_manual_submission", enableManualSubmission);
        b(m, "submission.enable_automatic_unknown_freeze_upload", enableAutomaticUnknownFreezeUpload);
        s(m, "submission.submission_target", submissionTarget);
        s(m, "submission.cloudflare_endpoint", cloudflareEndpoint);
        b(m, "submission.fallback_to_local", fallbackToLocal);
        b(m, "submission.ask_first_time", askFirstTime);
        b(m, "submission.ask_every_time", askEveryTime);
        s(m, "submission.github_issue_url", githubIssueUrl);
        b(m, "submission.copy_issue_body_to_clipboard", copyIssueBodyToClipboard);
        b(m, "submission.open_issue_url_on_client", openIssueUrlOnClient);
        b(m, "submission.enable_gist_upload", enableGistUpload);
        b(m, "submission.enable_token_upload", enableTokenUpload);
        b(m, "submission.anonymize_reports", anonymizeReports);
        b(m, "submission.redact_usernames", redactUsernames);
        b(m, "submission.redact_file_paths", redactFilePaths);
        b(m, "submission.redact_ip_addresses", redactIpAddresses);
        b(m, "submission.include_mod_list", submissionIncludeModList);
        b(m, "submission.include_system_info", submissionIncludeSystemInfo);
        b(m, "submission.include_recent_events", submissionIncludeRecentEvents);
        b(m, "submission.ask_before_every_upload", askBeforeEveryUpload);
        i(m, "submission.submit_command_cooldown_seconds", submitCommandCooldownSeconds);
        i(m, "submission.upload_timeout_seconds", uploadTimeoutSeconds);
        s(m, "submission.http_transport", httpTransport);
        b(m, "submission.logs.include_log_excerpt", includeLogExcerpt);
        i(m, "submission.logs.log_excerpt_max_lines", logExcerptMaxLines);
        i(m, "submission.logs.log_excerpt_context_seconds", logExcerptContextSeconds);
        b(m, "submission.logs.include_full_latest_log", includeFullLatestLog);
        i(m, "submission.logs.max_log_excerpt_chars", maxLogExcerptChars);
        b(m, "submission.logs.include_debug_log", includeDebugLog);
        i(m, "submission.logs.max_full_log_chars", maxFullLogChars);
        i(m, "submission.logs.max_issue_body_chars", maxIssueBodyChars);
        b(m, "submission.logs.store_oversized_logs_in_worker_storage", storeOversizedLogsInWorkerStorage);
        b(m, "submission.logs.include_stutter_analyzer_log_events", includeStutterAnalyzerLogEvents);
        b(m, "submission.logs.include_unknown_freeze_context", includeUnknownFreezeContext);
        b(m, "submission.logs.include_suspicious_log_signals", includeSuspiciousLogSignals);
        i(m, "submission.logs.max_log_event_lines", maxLogEventLines);
        i(m, "submission.logs.max_log_context_events", maxLogContextEvents);
        i(m, "submission.logs.log_context_lines_before", logContextLinesBefore);
        i(m, "submission.logs.log_context_lines_after", logContextLinesAfter);
        i(m, "submission.logs.max_suspicious_log_lines", maxSuspiciousLogLines);
        b(m, "submission.debug.show_payload_summary", showPayloadSummary);
        b(m, "updates.check_for_updates", checkForUpdates);
        b(m, "updates.check_on_startup", checkOnStartup);
        i(m, "updates.startup_check_delay_seconds", startupCheckDelaySeconds);
        i(m, "updates.check_interval_hours", checkIntervalHours);
        b(m, "updates.notify_only_once_per_version", notifyOnlyOncePerVersion);
        b(m, "updates.notify_when_up_to_date", notifyWhenUpToDate);
        s(m, "updates.version_url", updateVersionUrl);
        s(m, "updates.github_page", updateGithubPage);
        s(m, "updates.curseforge_url", updateCurseforgeUrl);
        b(m, "updates.open_links_on_click", openLinksOnClick);
    }

    private static void b(Map<String, String> m, String key, BooleanValue f) {
        String v = m.get(key); if (v != null) try { f.v = Boolean.parseBoolean(v.trim()); } catch (Exception ignored) {}
    }
    private static void i(Map<String, String> m, String key, IntValue f) {
        String v = m.get(key); if (v != null) try { f.set(Integer.parseInt(v.trim())); } catch (Exception ignored) {}
    }
    private static void d(Map<String, String> m, String key, DoubleValue f) {
        String v = m.get(key); if (v != null) try { f.v = Double.parseDouble(v.trim()); } catch (Exception ignored) {}
    }
    private static void s(Map<String, String> m, String key, StringValue f) {
        String v = m.get(key); if (v != null) f.v = v;
    }

    private String buildToml() {
        StringBuilder sb = new StringBuilder();
        sb.append("# StutterAnalyzer Fabric Config\n\n");
        sec(sb, "general");
        kv(sb, "enabled", enabled.v); kv(sb, "debug", debug.v); kv(sb, "overlay", overlay.v);
        kv(sb, "report_format", reportFormat.v); kv(sb, "max_reports", maxReports.v);
        sec(sb, "commands");
        kv(sb, "enable_commands", enableCommands.v); kv(sb, "enable_alias_sa", enableAliasSa.v);
        kv(sb, "allow_players_to_view_basic_status", allowPlayersBasicStatus.v);
        kv(sb, "allow_players_to_export_own_client_reports", allowPlayersExportOwnClientReports.v);
        kv(sb, "server_report_permission_level", serverReportPermissionLevel.v);
        kv(sb, "config_reload_permission_level", configReloadPermissionLevel.v);
        kv(sb, "delete_report_permission_level", deleteReportPermissionLevel.v);
        kv(sb, "debug_permission_level", debugPermissionLevel.v);
        kv(sb, "submit_report_permission_level", submitReportPermissionLevel.v);
        kv(sb, "guard_permission_level", guardPermissionLevel.v);
        sec(sb, "client");
        kv(sb, "enable_client_stutter_detection", enableClientStutterDetection.v);
        kv(sb, "minor_frame_ms", minorFrameMs.v); kv(sb, "medium_frame_ms", mediumFrameMs.v);
        kv(sb, "severe_frame_ms", severeFrameMs.v); kv(sb, "extreme_frame_ms", extremeFrameMs.v);
        kv(sb, "track_one_percent_lows", trackOnePercentLows.v); kv(sb, "track_point_one_percent_lows", trackPointOnePercentLows.v);
        sec(sb, "server");
        kv(sb, "enable_server_tick_detection", enableServerTickDetection.v);
        kv(sb, "warning_mspt", warningMspt.v); kv(sb, "medium_mspt", mediumMspt.v);
        kv(sb, "severe_mspt", severeMspt.v); kv(sb, "extreme_mspt", extremeMspt.v);
        sec(sb, "classification");
        kv(sb, "minimum_confidence", minimumConfidence.v); kv(sb, "unknown_freeze_enabled", unknownFreezeEnabled.v);
        sec(sb, "safety");
        kv(sb, "fail_silently", failSilently.v); kv(sb, "disable_failed_subsystems", disableFailedSubsystems.v); kv(sb, "never_crash_game", neverCrashGame.v);
        sec(sb, "reports");
        kv(sb, "save_markdown", saveMarkdown.v); kv(sb, "save_json", saveJson.v);
        kv(sb, "include_mod_list", includeModList.v); kv(sb, "include_system_info", includeSystemInfo.v); kv(sb, "include_recent_events", includeRecentEvents.v);
        kv(sb, "save_minor_stutter_reports", saveMinorStutterReports.v); kv(sb, "save_medium_stutter_reports", saveMediumStutterReports.v);
        kv(sb, "save_severe_stutter_reports", saveSevereStutterReports.v); kv(sb, "save_extreme_freeze_reports", saveExtremeReports.v);
        kv(sb, "chat_notify_minor_stutters", chatNotifyMinorStutters.v); kv(sb, "chat_notify_medium_stutters", chatNotifyMediumStutters.v);
        kv(sb, "chat_notify_severe_stutters", chatNotifySevereStutters.v); kv(sb, "chat_notify_extreme_freezes", chatNotifyExtremeFreeze.v);
        kv(sb, "minimum_auto_report_frame_ms", minimumAutoReportFrameMs.v); kv(sb, "minimum_auto_report_mspt", minimumAutoReportMspt.v);
        kv(sb, "chat_notification_cooldown_seconds", chatNotificationCooldownSeconds.v);
        kv(sb, "aggregate_repeated_minor_stutters", aggregateRepeatedMinorStutters.v);
        kv(sb, "minor_stutter_aggregate_window_seconds", minorStutterAggregateWindowSeconds.v);
        kv(sb, "minor_stutter_aggregate_count", minorStutterAggregateCount.v);
        kv(sb, "minor_stutter_aggregate_chat_cooldown_seconds", minorStutterAggregateChatCooldownSeconds.v);
        sec(sb, "debug"); kv(sb, "log_detection_pipeline", logDetectionPipeline.v);
        sec(sb, "stutter_counting");
        kv(sb, "count_mode", countMode.v); kv(sb, "episode_gap_ms", episodeGapMs.v);
        kv(sb, "episode_min_duration_ms", episodeMinDurationMs.v); kv(sb, "show_raw_frame_spike_count", showRawFrameSpikeCount.v);
        sec(sb, "notifications");
        kv(sb, "quiet_mode", quietMode.v); kv(sb, "minor_aggregate_chat_enabled", minorAggregateChatEnabled.v);
        kv(sb, "minor_aggregate_chat_cooldown_seconds", minorAggregateChatCooldownSeconds.v);
        kv(sb, "minor_aggregate_show_only_if_worse", minorAggregateShowOnlyIfWorse.v);
        kv(sb, "minor_aggregate_min_count_increase", minorAggregateMinCountIncrease.v);
        kv(sb, "minor_aggregate_min_worst_increase_ms", minorAggregateMinWorstIncreaseMs.v);
        kv(sb, "verbose_mode", verboseMode.v); kv(sb, "verbose_mode_session_only", verboseModeSessionOnly.v);
        kv(sb, "minor_chat_in_verbose", minorChatInVerbose.v); kv(sb, "medium_chat_in_verbose", mediumChatInVerbose.v);
        kv(sb, "verbose_chat_cooldown_seconds", verboseChatCooldownSeconds.v);
        sec(sb, "notifications.alerts");
        kv(sb, "alert_mode", alertMode.v); kv(sb, "alert_cooldown_seconds", alertCooldownSeconds.v);
        kv(sb, "same_category_cooldown_seconds", alertSameCategoryCooldownSeconds.v);
        kv(sb, "max_alerts_per_minute", alertMaxAlertsPerMinute.v);
        kv(sb, "scheduled_micro_hitch_cooldown_seconds", scheduledMicroHitchCooldownSeconds.v);
        kv(sb, "aggregate_small_stutters", alertAggregateSmallStutters.v);
        kv(sb, "minor_aggregate_cooldown_seconds", alertMinorAggregateCooldownSeconds.v);
        kv(sb, "show_report_hint", alertShowReportHint.v); kv(sb, "show_submit_hint", alertShowSubmitHint.v);
        sec(sb, "compatibility_guard.debug_hud");
        kv(sb, "enable_f3_status_line", debugHudEnabled.v); kv(sb, "show_colored_status", debugHudShowColored.v);
        kv(sb, "show_last_freeze_on_f3", debugHudShowLastFreeze.v); kv(sb, "show_emergency_mode_on_f3", debugHudShowEmergencyMode.v);
        kv(sb, "show_report_count_on_f3", debugHudShowReportCount.v); kv(sb, "show_subsystem_warnings_on_f3", debugHudShowSubsystemWarnings.v);
        kv(sb, "compact_mode", debugHudCompactMode.v); kv(sb, "f3_counter_mode", f3CounterMode.v); kv(sb, "show_raw_spike_count_on_f3", showRawSpikeCountOnF3.v);
        kv(sb, "f3_line_position", f3LinePosition.v); kv(sb, "f3_line_offset_x", f3LineOffsetX.v); kv(sb, "f3_line_extra_gap", f3LineExtraGap.v); kv(sb, "f3_line_manual_y_offset", f3LineManualYOffset.v); kv(sb, "f3_line_custom_x", f3LineCustomX.v); kv(sb, "f3_line_custom_y", f3LineCustomY.v);
        sec(sb, "compatibility_guard.startup_message");
        kv(sb, "show_startup_message", showStartupMessage.v); kv(sb, "show_only_once_per_session", showStartupMessageOncePerSession.v);
        kv(sb, "mention_silent_minor_tracking", mentionSilentMinorTracking.v);
        sec(sb, "compatibility_guard");
        kv(sb, "enabled", guardEnabled.v); kv(sb, "emergency_mode", guardEmergencyMode.v);
        kv(sb, "automatic_safe_guards", automaticSafeGuards.v); kv(sb, "warn_only_for_unsafe_patterns", warnOnlyForUnsafePatterns.v);
        kv(sb, "write_crash_hints", writeCrashHints.v); kv(sb, "write_latest_log_hints", writeLatestLogHints.v);
        kv(sb, "write_separate_hint_reports", writeSeparateHintReports.v); kv(sb, "rate_limit_guard_triggers", rateLimitGuardTriggers.v);
        kv(sb, "max_guard_triggers_per_session", maxGuardTriggersPerSession.v);
        kv(sb, "minimum_auto_guard_confidence", minimumAutoGuardConfidence.v); kv(sb, "minimum_warn_confidence", minimumWarnConfidence.v);
        sec(sb, "compatibility_guard.guards");
        kv(sb, "rubidium_lava_fluid_render", guardRubidiumLava.v); kv(sb, "dynamic_fps_background_false_positive", guardDynamicFps.v);
        kv(sb, "c2me_corrupted_chunk_context", guardC2meCorruptedChunk.v); kv(sb, "c2me_worldgen_deadlock_context", guardC2meDeadlock.v);
        kv(sb, "distant_horizons_lod_stutter", guardDistantHorizons.v); kv(sb, "iris_oculus_shader_pipeline_crash", guardIrisOculus.v);
        kv(sb, "starlight_scalablelux_lighting_crash", guardStarlightScalableLux.v); kv(sb, "embeddium_oculus_taint_context", guardEmbeddiumOculus.v);
        kv(sb, "chunk_animator_embeddium_mixin_conflict", guardChunkAnimator.v);
        sec(sb, "submission");
        kv(sb, "enable_manual_submission", enableManualSubmission.v);
        kv(sb, "enable_automatic_unknown_freeze_upload", enableAutomaticUnknownFreezeUpload.v);
        kv(sb, "submission_target", submissionTarget.v); kv(sb, "cloudflare_endpoint", cloudflareEndpoint.v);
        kv(sb, "fallback_to_local", fallbackToLocal.v); kv(sb, "ask_first_time", askFirstTime.v); kv(sb, "ask_every_time", askEveryTime.v);
        kv(sb, "github_issue_url", githubIssueUrl.v); kv(sb, "copy_issue_body_to_clipboard", copyIssueBodyToClipboard.v);
        kv(sb, "open_issue_url_on_client", openIssueUrlOnClient.v); kv(sb, "enable_gist_upload", enableGistUpload.v);
        kv(sb, "enable_token_upload", enableTokenUpload.v); kv(sb, "anonymize_reports", anonymizeReports.v);
        kv(sb, "redact_usernames", redactUsernames.v); kv(sb, "redact_file_paths", redactFilePaths.v); kv(sb, "redact_ip_addresses", redactIpAddresses.v);
        kv(sb, "include_mod_list", submissionIncludeModList.v); kv(sb, "include_system_info", submissionIncludeSystemInfo.v);
        kv(sb, "include_recent_events", submissionIncludeRecentEvents.v); kv(sb, "ask_before_every_upload", askBeforeEveryUpload.v);
        kv(sb, "submit_command_cooldown_seconds", submitCommandCooldownSeconds.v); kv(sb, "upload_timeout_seconds", uploadTimeoutSeconds.v);
        kv(sb, "http_transport", httpTransport.v);
        sec(sb, "submission.logs");
        kv(sb, "include_log_excerpt", includeLogExcerpt.v); kv(sb, "log_excerpt_max_lines", logExcerptMaxLines.v);
        kv(sb, "log_excerpt_context_seconds", logExcerptContextSeconds.v); kv(sb, "include_full_latest_log", includeFullLatestLog.v);
        kv(sb, "max_log_excerpt_chars", maxLogExcerptChars.v); kv(sb, "include_debug_log", includeDebugLog.v);
        kv(sb, "max_full_log_chars", maxFullLogChars.v); kv(sb, "max_issue_body_chars", maxIssueBodyChars.v);
        kv(sb, "store_oversized_logs_in_worker_storage", storeOversizedLogsInWorkerStorage.v);
        kv(sb, "include_stutter_analyzer_log_events", includeStutterAnalyzerLogEvents.v);
        kv(sb, "include_unknown_freeze_context", includeUnknownFreezeContext.v);
        kv(sb, "include_suspicious_log_signals", includeSuspiciousLogSignals.v);
        kv(sb, "max_log_event_lines", maxLogEventLines.v); kv(sb, "max_log_context_events", maxLogContextEvents.v);
        kv(sb, "log_context_lines_before", logContextLinesBefore.v); kv(sb, "log_context_lines_after", logContextLinesAfter.v);
        kv(sb, "max_suspicious_log_lines", maxSuspiciousLogLines.v);
        sec(sb, "submission.debug"); kv(sb, "show_payload_summary", showPayloadSummary.v);
        sec(sb, "updates");
        kv(sb, "check_for_updates", checkForUpdates.v); kv(sb, "check_on_startup", checkOnStartup.v);
        kv(sb, "startup_check_delay_seconds", startupCheckDelaySeconds.v); kv(sb, "check_interval_hours", checkIntervalHours.v);
        kv(sb, "notify_only_once_per_version", notifyOnlyOncePerVersion.v); kv(sb, "notify_when_up_to_date", notifyWhenUpToDate.v);
        kv(sb, "version_url", updateVersionUrl.v); kv(sb, "github_page", updateGithubPage.v);
        kv(sb, "curseforge_url", updateCurseforgeUrl.v); kv(sb, "open_links_on_click", openLinksOnClick.v);
        return sb.toString();
    }

    private static void sec(StringBuilder sb, String name) { sb.append("\n[").append(name).append("]\n"); }
    private static void kv(StringBuilder sb, String k, boolean v) { sb.append(k).append(" = ").append(v).append("\n"); }
    private static void kv(StringBuilder sb, String k, int v) { sb.append(k).append(" = ").append(v).append("\n"); }
    private static void kv(StringBuilder sb, String k, double v) { sb.append(k).append(" = ").append(v).append("\n"); }
    private static void kv(StringBuilder sb, String k, String v) { sb.append(k).append(" = \"").append(v).append("\"\n"); }
}
