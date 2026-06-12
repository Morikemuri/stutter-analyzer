package com.stutteranalyzer.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class SAConfig {

    public static final ModConfigSpec SPEC;
    public static final SAConfig INSTANCE;

    static {
        Pair<SAConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(SAConfig::new);
        INSTANCE = specPair.getLeft();
        SPEC     = specPair.getRight();
    }

    // â”€â”€ [general] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue enabled;
    public final ModConfigSpec.BooleanValue debug;
    public final ModConfigSpec.BooleanValue overlay;
    public final ModConfigSpec.ConfigValue<String> reportFormat;
    public final ModConfigSpec.IntValue maxReports;

    // â”€â”€ [commands] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue enableCommands;
    public final ModConfigSpec.BooleanValue enableAliasSa;
    public final ModConfigSpec.BooleanValue allowPlayersBasicStatus;
    public final ModConfigSpec.BooleanValue allowPlayersExportOwnClientReports;
    public final ModConfigSpec.IntValue serverReportPermissionLevel;
    public final ModConfigSpec.IntValue configReloadPermissionLevel;
    public final ModConfigSpec.IntValue debugPermissionLevel;
    public final ModConfigSpec.IntValue submitReportPermissionLevel;
    public final ModConfigSpec.IntValue guardPermissionLevel;

    // â”€â”€ [client] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue enableClientStutterDetection;
    public final ModConfigSpec.IntValue minorFrameMs;
    public final ModConfigSpec.IntValue mediumFrameMs;
    public final ModConfigSpec.IntValue severeFrameMs;
    public final ModConfigSpec.IntValue extremeFrameMs;
    public final ModConfigSpec.BooleanValue trackOnePercentLows;
    public final ModConfigSpec.BooleanValue trackPointOnePercentLows;

    // â”€â”€ [server] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue enableServerTickDetection;
    public final ModConfigSpec.IntValue warningMspt;
    public final ModConfigSpec.IntValue mediumMspt;
    public final ModConfigSpec.IntValue severeMspt;
    public final ModConfigSpec.IntValue extremeMspt;

    // â”€â”€ [classification] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.DoubleValue minimumConfidence;
    public final ModConfigSpec.BooleanValue unknownFreezeEnabled;

    // â”€â”€ [safety] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue failSilently;
    public final ModConfigSpec.BooleanValue disableFailedSubsystems;
    public final ModConfigSpec.BooleanValue neverCrashGame;

    // â”€â”€ [reports] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue saveMarkdown;
    public final ModConfigSpec.BooleanValue saveJson;
    public final ModConfigSpec.BooleanValue includeModList;
    public final ModConfigSpec.BooleanValue includeSystemInfo;
    public final ModConfigSpec.BooleanValue includeRecentEvents;

    // â”€â”€ [reports] chat + auto-report thresholds â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue saveMinorStutterReports;
    public final ModConfigSpec.BooleanValue saveMediumStutterReports;
    public final ModConfigSpec.BooleanValue saveSevereStutterReports;
    public final ModConfigSpec.BooleanValue saveExtremeReports;
    public final ModConfigSpec.BooleanValue chatNotifyMinorStutters;
    public final ModConfigSpec.BooleanValue chatNotifyMediumStutters;
    public final ModConfigSpec.BooleanValue chatNotifySevereStutters;
    public final ModConfigSpec.BooleanValue chatNotifyExtremeFreeze;
    public final ModConfigSpec.IntValue minimumAutoReportFrameMs;
    public final ModConfigSpec.IntValue minimumAutoReportMspt;
    public final ModConfigSpec.IntValue chatNotificationCooldownSeconds;
    public final ModConfigSpec.BooleanValue aggregateRepeatedMinorStutters;
    public final ModConfigSpec.IntValue minorStutterAggregateWindowSeconds;
    public final ModConfigSpec.IntValue minorStutterAggregateCount;
    public final ModConfigSpec.IntValue minorStutterAggregateChatCooldownSeconds;

    // â”€â”€ [debug] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue logDetectionPipeline;

    // â”€â”€ [stutter_counting] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.ConfigValue<String> countMode;
    public final ModConfigSpec.IntValue episodeGapMs;
    public final ModConfigSpec.IntValue episodeMinDurationMs;
    public final ModConfigSpec.BooleanValue showRawFrameSpikeCount;

    // â”€â”€ [notifications] chat alert settings â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue quietMode;
    public final ModConfigSpec.BooleanValue minorAggregateChatEnabled;
    public final ModConfigSpec.IntValue minorAggregateChatCooldownSeconds;
    public final ModConfigSpec.BooleanValue minorAggregateShowOnlyIfWorse;
    public final ModConfigSpec.IntValue minorAggregateMinCountIncrease;
    public final ModConfigSpec.IntValue minorAggregateMinWorstIncreaseMs;
    public final ModConfigSpec.BooleanValue verboseMode;
    public final ModConfigSpec.BooleanValue verboseModeSessionOnly;
    public final ModConfigSpec.BooleanValue minorChatInVerbose;
    public final ModConfigSpec.BooleanValue mediumChatInVerbose;
    public final ModConfigSpec.IntValue verboseChatCooldownSeconds;

    // â”€â”€ [notifications.alerts] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.ConfigValue<String> alertMode;
    public final ModConfigSpec.IntValue alertCooldownSeconds;
    public final ModConfigSpec.IntValue alertSameCategoryCooldownSeconds;
    public final ModConfigSpec.IntValue alertMaxAlertsPerMinute;
    public final ModConfigSpec.IntValue scheduledMicroHitchCooldownSeconds;
    public final ModConfigSpec.BooleanValue alertAggregateSmallStutters;
    public final ModConfigSpec.IntValue alertMinorAggregateCooldownSeconds;
    public final ModConfigSpec.BooleanValue alertShowReportHint;
    public final ModConfigSpec.BooleanValue alertShowSubmitHint;

    // â”€â”€ [debug_hud] extra â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.ConfigValue<String> f3CounterMode;
    public final ModConfigSpec.BooleanValue showRawSpikeCountOnF3;

    // â”€â”€ [submission] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue enableManualSubmission;
    public final ModConfigSpec.BooleanValue enableAutomaticUnknownFreezeUpload;
    public final ModConfigSpec.ConfigValue<String> submissionTarget;
    public final ModConfigSpec.ConfigValue<String> cloudflareEndpoint;
    public final ModConfigSpec.BooleanValue fallbackToLocal;
    public final ModConfigSpec.BooleanValue askFirstTime;
    public final ModConfigSpec.BooleanValue askEveryTime;
    public final ModConfigSpec.ConfigValue<String> githubIssueUrl;
    public final ModConfigSpec.BooleanValue copyIssueBodyToClipboard;
    public final ModConfigSpec.BooleanValue openIssueUrlOnClient;
    public final ModConfigSpec.BooleanValue enableGistUpload;
    public final ModConfigSpec.BooleanValue enableTokenUpload;
    public final ModConfigSpec.BooleanValue anonymizeReports;
    public final ModConfigSpec.BooleanValue redactUsernames;
    public final ModConfigSpec.BooleanValue redactFilePaths;
    public final ModConfigSpec.BooleanValue redactIpAddresses;
    public final ModConfigSpec.BooleanValue submissionIncludeModList;
    public final ModConfigSpec.BooleanValue submissionIncludeSystemInfo;
    public final ModConfigSpec.BooleanValue submissionIncludeRecentEvents;
    public final ModConfigSpec.BooleanValue askBeforeEveryUpload;
    public final ModConfigSpec.IntValue submitCommandCooldownSeconds;
    public final ModConfigSpec.IntValue uploadTimeoutSeconds;
    public final ModConfigSpec.ConfigValue<String> httpTransport;
    public final ModConfigSpec.BooleanValue includeLogExcerpt;
    public final ModConfigSpec.IntValue logExcerptMaxLines;
    public final ModConfigSpec.IntValue logExcerptContextSeconds;
    public final ModConfigSpec.BooleanValue includeFullLatestLog;
    public final ModConfigSpec.IntValue maxLogExcerptChars;
    public final ModConfigSpec.BooleanValue includeDebugLog;
    public final ModConfigSpec.IntValue maxFullLogChars;
    public final ModConfigSpec.IntValue maxIssueBodyChars;
    public final ModConfigSpec.BooleanValue storeOversizedLogsInWorkerStorage;
    public final ModConfigSpec.BooleanValue includeStutterAnalyzerLogEvents;
    public final ModConfigSpec.BooleanValue includeUnknownFreezeContext;
    public final ModConfigSpec.BooleanValue includeSuspiciousLogSignals;
    public final ModConfigSpec.IntValue maxLogEventLines;
    public final ModConfigSpec.IntValue maxLogContextEvents;
    public final ModConfigSpec.IntValue logContextLinesBefore;
    public final ModConfigSpec.IntValue logContextLinesAfter;
    public final ModConfigSpec.IntValue maxSuspiciousLogLines;

    // â”€â”€ [submission.debug] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue showPayloadSummary;

    // â”€â”€ [compatibility_guard] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue guardEnabled;
    public final ModConfigSpec.BooleanValue guardEmergencyMode;
    public final ModConfigSpec.BooleanValue automaticSafeGuards;
    public final ModConfigSpec.BooleanValue warnOnlyForUnsafePatterns;
    public final ModConfigSpec.BooleanValue writeCrashHints;
    public final ModConfigSpec.BooleanValue writeLatestLogHints;
    public final ModConfigSpec.BooleanValue writeSeparateHintReports;
    public final ModConfigSpec.BooleanValue rateLimitGuardTriggers;
    public final ModConfigSpec.IntValue maxGuardTriggersPerSession;
    public final ModConfigSpec.DoubleValue minimumAutoGuardConfidence;
    public final ModConfigSpec.DoubleValue minimumWarnConfidence;

    // â”€â”€ [updates] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue checkForUpdates;
    public final ModConfigSpec.BooleanValue checkOnStartup;
    public final ModConfigSpec.IntValue startupCheckDelaySeconds;
    public final ModConfigSpec.IntValue checkIntervalHours;
    public final ModConfigSpec.BooleanValue notifyOnlyOncePerVersion;
    public final ModConfigSpec.BooleanValue notifyWhenUpToDate;
    public final ModConfigSpec.ConfigValue<String> updateVersionUrl;
    public final ModConfigSpec.ConfigValue<String> updateGithubPage;
    public final ModConfigSpec.ConfigValue<String> updateCurseforgeUrl;
    public final ModConfigSpec.BooleanValue openLinksOnClick;

    // â”€â”€ [debug_hud] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue debugHudEnabled;
    public final ModConfigSpec.BooleanValue debugHudShowColored;
    public final ModConfigSpec.BooleanValue debugHudShowLastFreeze;
    public final ModConfigSpec.BooleanValue debugHudShowEmergencyMode;
    public final ModConfigSpec.BooleanValue debugHudShowReportCount;
    public final ModConfigSpec.BooleanValue debugHudShowSubsystemWarnings;
    public final ModConfigSpec.BooleanValue debugHudCompactMode;

    // â”€â”€ [startup_message] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.BooleanValue showStartupMessage;
    public final ModConfigSpec.BooleanValue showStartupMessageOncePerSession;
    public final ModConfigSpec.BooleanValue mentionSilentMinorTracking;

    // â”€â”€ [compatibility_guard.guards] â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public final ModConfigSpec.ConfigValue<String> guardRubidiumLava;
    public final ModConfigSpec.ConfigValue<String> guardDynamicFps;
    public final ModConfigSpec.ConfigValue<String> guardC2meCorruptedChunk;
    public final ModConfigSpec.ConfigValue<String> guardC2meDeadlock;
    public final ModConfigSpec.ConfigValue<String> guardDistantHorizons;
    public final ModConfigSpec.ConfigValue<String> guardIrisOculus;
    public final ModConfigSpec.ConfigValue<String> guardStarlightScalableLux;
    public final ModConfigSpec.ConfigValue<String> guardEmbeddiumOculus;
    public final ModConfigSpec.ConfigValue<String> guardChunkAnimator;

    private SAConfig(ModConfigSpec.Builder b) {
        b.comment("StutterAnalyzer - Watches Minecraft suffer and writes down what happened.").push("general");
        enabled = b.comment("Enable the analyzer. Disabling this means flying blind into lag spikes.").define("enabled", true);
        debug = b.comment("Enable debug mode. Heavier than a fully-loaded chunk column - use only when hunting freezes, not flexing FPS.").define("debug", false);
        overlay = b.comment("Show HUD overlay (client only). Work in progress.").define("overlay", false);
        reportFormat = b.comment("Report formats to generate: markdown, json").define("report_format", "markdown,json");
        maxReports = b.comment("Maximum reports to keep on disk. The rest are evicted like ancient loot nobody wanted.").defineInRange("max_reports", 50, 1, 500);
        b.pop();

        b.comment("Command settings").push("commands");
        enableCommands = b.comment("Enable /stutteranalyzer and /sa commands").define("enable_commands", true);
        enableAliasSa = b.comment("Enable short alias /sa. /stutteranalyzer is for people with a lot of spare keystrokes.").define("enable_alias_sa", true);
        allowPlayersBasicStatus = b.comment("Allow all players to use /sa status and /sa health").define("allow_players_to_view_basic_status", true);
        allowPlayersExportOwnClientReports = b.comment("Allow players to export their own client reports").define("allow_players_to_export_own_client_reports", true);
        serverReportPermissionLevel = b.comment("Permission level for server report access").defineInRange("server_report_permission_level", 2, 0, 4);
        configReloadPermissionLevel = b.comment("Permission level for config reload actions").defineInRange("config_reload_permission_level", 3, 0, 4);
        debugPermissionLevel = b.comment("Permission level for restricted diagnostic actions").defineInRange("debug_permission_level", 4, 0, 4);
        submitReportPermissionLevel = b.comment("Permission level to submit reports").defineInRange("submit_report_permission_level", 3, 0, 4);
        guardPermissionLevel = b.comment("Permission level for guard enable/disable").defineInRange("guard_permission_level", 4, 0, 4);
        b.pop();

        b.comment("Client stutter detection").push("client");
        enableClientStutterDetection = b.comment("Enable client-side frame time stutter detection").define("enable_client_stutter_detection", true);
        minorFrameMs = b.comment("Frame time (ms) considered a minor stutter").defineInRange("minor_frame_ms", 50, 1, 10000);
        mediumFrameMs = b.comment("Frame time (ms) considered a medium stutter").defineInRange("medium_frame_ms", 100, 1, 10000);
        severeFrameMs = b.comment("Frame time (ms) considered a severe stutter").defineInRange("severe_frame_ms", 250, 1, 10000);
        extremeFrameMs = b.comment("Frame time (ms) considered an extreme freeze").defineInRange("extreme_frame_ms", 1000, 1, 60000);
        trackOnePercentLows = b.comment("Track 1% low FPS").define("track_one_percent_lows", true);
        trackPointOnePercentLows = b.comment("Track 0.1% low FPS").define("track_point_one_percent_lows", true);
        b.pop();

        b.comment("Server tick spike detection").push("server");
        enableServerTickDetection = b.comment("Enable server-side MSPT/TPS spike detection").define("enable_server_tick_detection", true);
        warningMspt = b.comment("MSPT considered a warning spike").defineInRange("warning_mspt", 50, 1, 10000);
        mediumMspt = b.comment("MSPT considered a medium spike").defineInRange("medium_mspt", 100, 1, 10000);
        severeMspt = b.comment("MSPT considered a severe spike").defineInRange("severe_mspt", 250, 1, 10000);
        extremeMspt = b.comment("MSPT considered an extreme spike").defineInRange("extreme_mspt", 1000, 1, 60000);
        b.pop();

        b.comment("Freeze classification").push("classification");
        minimumConfidence = b.comment("Minimum confidence to assign a freeze category. Below this threshold the analyzer refuses to guess and reports UNKNOWN_FREEZE instead of hallucinating a diagnosis.").defineInRange("minimum_confidence", 0.60, 0.0, 1.0);
        unknownFreezeEnabled = b.comment("Enable UNKNOWN_FREEZE classification. That is not a bug - that is the analyzer refusing to make things up like a cursed fortune cookie.").define("unknown_freeze_enabled", true);
        b.pop();

        b.comment("Safety and failure isolation").push("safety");
        failSilently = b.comment("Suppress all non-fatal analyzer errors from the game log").define("fail_silently", false);
        disableFailedSubsystems = b.comment("Automatically disable subsystems that fail rather than crashing everything. We are doctors, not monkeys with a grenade.").define("disable_failed_subsystems", true);
        neverCrashGame = b.comment("Never throw from event callbacks. The analyzer watching Minecraft crash is ironic enough already.").define("never_crash_game", true);
        b.pop();

        b.comment("Report generation").push("reports");
        saveMarkdown = b.comment("Save freeze reports as Markdown files").define("save_markdown", true);
        saveJson = b.comment("Save freeze reports as JSON files").define("save_json", true);
        includeModList = b.comment("Include the loaded mod list in reports").define("include_mod_list", true);
        includeSystemInfo = b.comment("Include system info (Java, OS, CPU, memory) in reports").define("include_system_info", true);
        includeRecentEvents = b.comment("Include the recent event timeline in reports").define("include_recent_events", true);
        saveMinorStutterReports = b.comment("Save reports for minor stutters (50-99ms). Disabled by default - tracking only.").define("save_minor_stutter_reports", false);
        saveMediumStutterReports = b.comment("Save reports for medium stutters (100-249ms). Disabled by default.").define("save_medium_stutter_reports", false);
        saveSevereStutterReports = b.comment("Save reports for severe stutters (250-999ms).").define("save_severe_stutter_reports", true);
        saveExtremeReports = b.comment("Save reports for extreme freezes (1000ms+).").define("save_extreme_freeze_reports", true);
        chatNotifyMinorStutters = b.comment("Send chat notification for minor stutters. Disabled - F3 only.").define("chat_notify_minor_stutters", false);
        chatNotifyMediumStutters = b.comment("Send chat notification for medium stutters. Disabled by default.").define("chat_notify_medium_stutters", false);
        chatNotifySevereStutters = b.comment("Send chat notification for severe stutters (250ms+).").define("chat_notify_severe_stutters", true);
        chatNotifyExtremeFreeze = b.comment("Send chat notification for extreme freezes (1000ms+).").define("chat_notify_extreme_freezes", true);
        minimumAutoReportFrameMs = b.comment("Minimum frame time (ms) to auto-save and notify. Reports below this are tracked silently.").defineInRange("minimum_auto_report_frame_ms", 250, 1, 60000);
        minimumAutoReportMspt = b.comment("Minimum MSPT to auto-save a server tick report.").defineInRange("minimum_auto_report_mspt", 100, 1, 60000);
        chatNotificationCooldownSeconds = b.comment("Minimum seconds between chat notifications. Prevents spam.").defineInRange("chat_notification_cooldown_seconds", 15, 1, 3600);
        aggregateRepeatedMinorStutters = b.comment("Aggregate repeated minor stutters into a cluster report instead of spamming.").define("aggregate_repeated_minor_stutters", true);
        minorStutterAggregateWindowSeconds = b.comment("Time window (seconds) for aggregating minor stutters.").defineInRange("minor_stutter_aggregate_window_seconds", 30, 5, 3600);
        minorStutterAggregateCount = b.comment("Number of minor stutters within the window to trigger a cluster report.").defineInRange("minor_stutter_aggregate_count", 5, 2, 100);
        minorStutterAggregateChatCooldownSeconds = b.comment("Minimum seconds between aggregate minor stutter chat messages.").defineInRange("minor_stutter_aggregate_chat_cooldown_seconds", 60, 5, 3600);
        b.pop();

        b.comment("Debug and development logging").push("debug");
        logDetectionPipeline = b.comment("Log every stutter detection event to latest.log. Very verbose - disabled by default.").define("log_detection_pipeline", false);
        b.pop();

        b.comment("How stutters are counted for F3 and /sa status").push("stutter_counting");
        countMode = b.comment("Count mode: episodes (continuous bad-frame periods), frames (every individual frame), or both.").define("count_mode", "episodes");
        episodeGapMs = b.comment("Gap in ms between stutter frames to start a new episode. 250ms = any frame within 5 ticks is part of the same episode.").defineInRange("episode_gap_ms", 250, 50, 5000);
        episodeMinDurationMs = b.comment("Minimum frame duration (ms) for episode tracking. Should match minor_frame_ms.").defineInRange("episode_min_duration_ms", 50, 1, 1000);
        showRawFrameSpikeCount = b.comment("Show raw frame spike count in /sa status alongside episode count.").define("show_raw_frame_spike_count", false);
        b.pop();

        b.comment("Chat notification settings").push("notifications");
        quietMode = b.comment("Minimum alert level for chat notifications. When true, minor/medium stutters are tracked silently in F3 and /sa status only.").define("quiet_mode", true);
        minorAggregateChatEnabled = b.comment("Show aggregate minor stutter summary in chat (subject to alert mode and cooldown).").define("minor_aggregate_chat_enabled", true);
        minorAggregateChatCooldownSeconds = b.comment("Minimum seconds between aggregate minor stutter chat messages.").defineInRange("minor_aggregate_chat_cooldown_seconds", 120, 5, 3600);
        minorAggregateShowOnlyIfWorse = b.comment("Only show another aggregate message if things got significantly worse since the last one.").define("minor_aggregate_show_only_if_worse", true);
        minorAggregateMinCountIncrease = b.comment("Minimum stutter count increase since last message to trigger another.").defineInRange("minor_aggregate_min_count_increase", 25, 1, 500);
        minorAggregateMinWorstIncreaseMs = b.comment("Minimum worst spike increase (ms) since last message to trigger another.").defineInRange("minor_aggregate_min_worst_increase_ms", 15, 1, 1000);
        verboseMode = b.comment("Temporarily show minor/medium stutters in chat. Off by default. Use /sa alerts minor to control chat notifications.").define("verbose_mode", false);
        verboseModeSessionOnly = b.comment("Chat alert mode resets when the game exits. Prevents accidental permanent spam.").define("verbose_mode_session_only", true);
        minorChatInVerbose = b.comment("Show minor (50-99ms) stutters in chat when chat alerts are on.").define("minor_chat_in_verbose", true);
        mediumChatInVerbose = b.comment("Show medium (100-249ms) stutters in chat when chat alerts are on.").define("medium_chat_in_verbose", true);
        verboseChatCooldownSeconds = b.comment("Seconds between chat alert messages. Prevents spam.").defineInRange("verbose_chat_cooldown_seconds", 5, 1, 60);
        b.comment("Preset-based chat alert system").push("alerts");
        alertMode = b.comment("Alert mode: OFF, MINOR, MEDIUM, SEVERE, EXTREME. SEVERE = notify only for severe/extreme freezes.").define("alert_mode", "SEVERE");
        alertCooldownSeconds = b.comment("Global cooldown in seconds between any two chat alerts.").defineInRange("alert_cooldown_seconds", 30, 5, 600);
        alertSameCategoryCooldownSeconds = b.comment("Cooldown in seconds between alerts of the same category.").defineInRange("same_category_cooldown_seconds", 60, 5, 600);
        alertMaxAlertsPerMinute = b.comment("Maximum number of chat alerts per minute.").defineInRange("max_alerts_per_minute", 5, 1, 60);
        scheduledMicroHitchCooldownSeconds = b.comment("Minimum cooldown in seconds between alerts for repeated scheduled micro-hitch events. Prevents printing the same scheduled hitch every minute.").defineInRange("scheduled_micro_hitch_cooldown_seconds", 300, 30, 3600);
        alertAggregateSmallStutters = b.comment("In SEVERE/EXTREME mode, still show an aggregated minor/medium summary occasionally.").define("aggregate_small_stutters", true);
        alertMinorAggregateCooldownSeconds = b.comment("Cooldown in seconds for the aggregated small stutter message.").defineInRange("minor_aggregate_cooldown_seconds", 120, 30, 3600);
        alertShowReportHint = b.comment("Show 'Report saved. Use /sa submit' hint after severe/extreme alerts.").define("show_report_hint", true);
        alertShowSubmitHint = b.comment("Show /sa submit hint in severe/extreme alerts.").define("show_submit_hint", true);
        b.pop(); // end alerts
        b.pop(); // end notifications

        b.comment("Report submission").push("submission");
        enableManualSubmission = b.comment("Allow manual report submission through commands").define("enable_manual_submission", true);
        enableAutomaticUnknownFreezeUpload = b.comment("Upload unknown freeze reports automatically. DISABLED by default. Automatic uploads require explicit opt-in - trust matters more than data hoarding.").define("enable_automatic_unknown_freeze_upload", false);
        submissionTarget = b.comment("Submission target: cloudflare or local. Default is cloudflare.").define("submission_target", "cloudflare");
        cloudflareEndpoint = b.comment("Cloudflare Worker endpoint URL for report submission.").define("cloudflare_endpoint", "https://stutter-analyzer-reports.morikemuri.workers.dev/api/report");
        fallbackToLocal = b.comment("If Cloudflare submission fails, save report locally.").define("fallback_to_local", true);
        askFirstTime = b.comment("Ask the player before the first Cloudflare submission. Default off - submit goes directly to Cloudflare.").define("ask_first_time", false);
        askEveryTime = b.comment("Ask the player before every Cloudflare submission.").define("ask_every_time", false);
        githubIssueUrl = b.comment("GitHub new issue URL (used only for /sa submit local last)").define("github_issue_url", "https://github.com/Morikemuri/stutter-analyzer/issues/new");
        copyIssueBodyToClipboard = b.comment("Copy issue body to clipboard (only for /sa submit local last).").define("copy_issue_body_to_clipboard", false);
        openIssueUrlOnClient = b.comment("Open GitHub issue URL in browser (only for /sa submit local last).").define("open_issue_url_on_client", false);
        enableGistUpload = b.comment("Enable GitHub Gist upload. Disabled by default - public builds never upload.").define("enable_gist_upload", false);
        enableTokenUpload = b.comment("Enable token-based upload. Disabled by default. Never store tokens in config.").define("enable_token_upload", false);
        anonymizeReports = b.comment("Anonymize reports before submission").define("anonymize_reports", true);
        redactUsernames = b.comment("Redact usernames from file paths in reports").define("redact_usernames", true);
        redactFilePaths = b.comment("Redact absolute file paths in reports").define("redact_file_paths", true);
        redactIpAddresses = b.comment("Redact IP addresses from reports").define("redact_ip_addresses", true);
        submissionIncludeModList = b.comment("Include mod list in submitted reports").define("include_mod_list", true);
        submissionIncludeSystemInfo = b.comment("Include system info in submitted reports").define("include_system_info", true);
        submissionIncludeRecentEvents = b.comment("Include recent event timeline in submitted reports").define("include_recent_events", true);
        askBeforeEveryUpload = b.comment("Ask for confirmation before every upload").define("ask_before_every_upload", true);
        submitCommandCooldownSeconds = b.comment("Cooldown in seconds between /sa submit invocations. Prevents spam and duplicate uploads.").defineInRange("submit_command_cooldown_seconds", 10, 0, 300);
        uploadTimeoutSeconds = b.comment("HTTP upload timeout in seconds. If the upload takes longer, it is cancelled and treated as a failure.").defineInRange("upload_timeout_seconds", 30, 5, 120);
        httpTransport = b.comment("HTTP transport to use for uploads: auto, java_http_client, http_url_connection. Default is http_url_connection (most reliable in Forge/Minecraft JVM).").define("http_transport", "http_url_connection");
        b.comment("Log and report inclusion settings").push("logs");
        includeLogExcerpt = b.comment("Include a sanitized latest.log excerpt around the event. Paths and IPs are redacted.").define("include_log_excerpt", true);
        logExcerptMaxLines = b.comment("Maximum number of log lines to include in the excerpt.").defineInRange("log_excerpt_max_lines", 200, 10, 1000);
        logExcerptContextSeconds = b.comment("Seconds before/after the event to include from the log.").defineInRange("log_excerpt_context_seconds", 30, 5, 300);
        includeFullLatestLog = b.comment("Include the full sanitized latest.log in submitted reports. Large logs are stored server-side if too big for GitHub.").define("include_full_latest_log", true);
        maxLogExcerptChars = b.comment("Maximum characters of log excerpt to include.").defineInRange("max_log_excerpt_chars", 30000, 1000, 65000);
        includeDebugLog = b.comment("Include debug.log in submitted reports. Disabled by default.").define("include_debug_log", false);
        maxFullLogChars = b.comment("Maximum characters of full latest.log to include in payload.").defineInRange("max_full_log_chars", 120000, 10000, 500000);
        maxIssueBodyChars = b.comment("Maximum characters for the GitHub issue body. Content is truncated and stored server-side if larger.").defineInRange("max_issue_body_chars", 55000, 10000, 65000);
        storeOversizedLogsInWorkerStorage = b.comment("Store full sanitized log in Cloudflare KV/R2 if too large for GitHub issue body.").define("store_oversized_logs_in_worker_storage", true);
        includeStutterAnalyzerLogEvents = b.comment("Include extracted Stutter Analyzer log events from latest.log.").define("include_stutter_analyzer_log_events", true);
        includeUnknownFreezeContext = b.comment("Include context around Unknown Freeze / spike lines from latest.log.").define("include_unknown_freeze_context", true);
        includeSuspiciousLogSignals = b.comment("Include suspicious signals scanned from the whole latest.log.").define("include_suspicious_log_signals", true);
        maxLogEventLines = b.comment("Maximum SA log event lines to extract.").defineInRange("max_log_event_lines", 300, 10, 2000);
        maxLogContextEvents = b.comment("Maximum Unknown Freeze context events to include.").defineInRange("max_log_context_events", 20, 1, 100);
        logContextLinesBefore = b.comment("Lines before each freeze line to include as context.").defineInRange("log_context_lines_before", 20, 1, 200);
        logContextLinesAfter = b.comment("Lines after each freeze line to include as context.").defineInRange("log_context_lines_after", 40, 1, 200);
        maxSuspiciousLogLines = b.comment("Maximum suspicious log signal lines to extract.").defineInRange("max_suspicious_log_lines", 300, 10, 2000);
        b.pop();
        b.comment("Submit diagnostic settings").push("debug");
        showPayloadSummary = b.comment("Print payload field sizes in chat when submitting. Useful for verifying log collection works end-to-end.").define("show_payload_summary", false);
        b.pop();
        b.pop();

        b.comment("Automatic update checker").push("updates");
        checkForUpdates = b.comment("Check GitHub for new releases. Only downloads a public version.json file. No user data is sent.").define("check_for_updates", true);
        checkOnStartup = b.comment("Run the update check automatically on startup.").define("check_on_startup", true);
        startupCheckDelaySeconds = b.comment("Seconds after startup before the first update check. Does not block game loading.").defineInRange("startup_check_delay_seconds", 10, 1, 300);
        checkIntervalHours = b.comment("Minimum hours between automatic update checks.").defineInRange("check_interval_hours", 12, 1, 168);
        notifyOnlyOncePerVersion = b.comment("Notify in chat only once per discovered new version.").define("notify_only_once_per_version", true);
        notifyWhenUpToDate = b.comment("Show a chat message when already up to date. Off by default.").define("notify_when_up_to_date", false);
        updateVersionUrl = b.comment("URL of the remote version.json to check. GET only, no authentication.").define("version_url", "https://raw.githubusercontent.com/Morikemuri/stutter-analyzer/main/version.json");
        updateGithubPage = b.comment("GitHub page shown in /sa version when an update is available.").define("github_page", "https://github.com/Morikemuri/stutter-analyzer");
        updateCurseforgeUrl = b.comment("CurseForge page shown in /sa version when an update is available.").define("curseforge_url", "https://www.curseforge.com/minecraft/mc-mods/stutter-analyzer");
        openLinksOnClick = b.comment("Allow clickable chat links for update URLs on client.").define("open_links_on_click", true);
        b.pop();

        b.comment("Emergency Compatibility Guard").push("compatibility_guard");
        b.comment("F3 debug screen status line").push("debug_hud");
        debugHudEnabled = b.comment("Show SA status in the F3 debug screen. OFF by default - use /sa f3 on to enable.").define("enable_f3_status_line", false);
        debugHudShowColored = b.comment("Use colored text in F3 status line. Green is good, yellow is suspicious, red means look at /sa health.").define("show_colored_status", true);
        debugHudShowLastFreeze = b.comment("Show last freeze on F3 line").define("show_last_freeze_on_f3", true);
        debugHudShowEmergencyMode = b.comment("Show emergency mode state on F3 line").define("show_emergency_mode_on_f3", true);
        debugHudShowReportCount = b.comment("Show report count on F3 line").define("show_report_count_on_f3", true);
        debugHudShowSubsystemWarnings = b.comment("Show subsystem warnings on F3 line").define("show_subsystem_warnings_on_f3", true);
        debugHudCompactMode = b.comment("Use compact single-line format. F3 is crowded enough already.").define("compact_mode", true);
        f3CounterMode = b.comment("What to show on F3: episodes (default), frames, or both.").define("f3_counter_mode", "episodes");
        showRawSpikeCountOnF3 = b.comment("Also show raw frame spike count on F3 when counter mode is episodes.").define("show_raw_spike_count_on_f3", false);
        b.pop();

        b.comment("Startup confirmation message").push("startup_message");
        showStartupMessage = b.comment("Show a startup message when joining a world. Once per session - not every time Minecraft loads 847 mods.").define("show_startup_message", true);
        showStartupMessageOncePerSession = b.comment("Only show the startup message once per session").define("show_only_once_per_session", true);
        mentionSilentMinorTracking = b.comment("Include a note in startup message that minor stutters are tracked silently.").define("mention_silent_minor_tracking", true);
        b.pop();

        b.comment("Emergency Compatibility Guard").push("compatibility_guard");
        guardEnabled = b.comment("Enable the compatibility guard system").define("enabled", true);
        guardEmergencyMode = b.comment("Enable emergency mode (allows SAFE_AUTO_GUARD patterns to run automatically). Disabled by default.").define("emergency_mode", false);
        automaticSafeGuards = b.comment("Allow automatic safe guards in emergency mode").define("automatic_safe_guards", true);
        warnOnlyForUnsafePatterns = b.comment("Only warn (never patch) for patterns that are not SAFE_AUTO_GUARD").define("warn_only_for_unsafe_patterns", true);
        writeCrashHints = b.comment("Write crash hints to log when known patterns are detected").define("write_crash_hints", true);
        writeLatestLogHints = b.comment("Write hints to latest.log").define("write_latest_log_hints", true);
        writeSeparateHintReports = b.comment("Write separate hint report files").define("write_separate_hint_reports", true);
        rateLimitGuardTriggers = b.comment("Rate-limit guard triggers to prevent spam").define("rate_limit_guard_triggers", true);
        maxGuardTriggersPerSession = b.comment("Maximum guard triggers per session").defineInRange("max_guard_triggers_per_session", 20, 1, 1000);
        minimumAutoGuardConfidence = b.comment("Minimum confidence to allow automatic guard activation").defineInRange("minimum_auto_guard_confidence", 0.90, 0.0, 1.0);
        minimumWarnConfidence = b.comment("Minimum confidence to show a warning for a known pattern").defineInRange("minimum_warn_confidence", 0.60, 0.0, 1.0);

        b.comment("Per-guard configuration. Values: auto, warn_only, report_only, disabled.\nauto = seatbelt fastened for you | warn_only = it just beeps | report_only = writes it down | disabled = good luck").push("guards");
        guardRubidiumLava = b.comment("Rubidium lava fluid render crash guard").define("rubidium_lava_fluid_render", "auto");
        guardDynamicFps = b.comment("Dynamic FPS background false-positive guard").define("dynamic_fps_background_false_positive", "auto");
        guardC2meCorruptedChunk = b.comment("C2ME corrupted chunk context").define("c2me_corrupted_chunk_context", "warn_only");
        guardC2meDeadlock = b.comment("C2ME worldgen deadlock context").define("c2me_worldgen_deadlock_context", "warn_only");
        guardDistantHorizons = b.comment("Distant Horizons LOD stutter context").define("distant_horizons_lod_stutter", "warn_only");
        guardIrisOculus = b.comment("Iris/Oculus shader pipeline crash context").define("iris_oculus_shader_pipeline_crash", "warn_only");
        guardStarlightScalableLux = b.comment("Starlight/ScalableLux lighting context").define("starlight_scalablelux_lighting_crash", "warn_only");
        guardEmbeddiumOculus = b.comment("Embeddium/Oculus taint context").define("embeddium_oculus_taint_context", "warn_only");
        guardChunkAnimator = b.comment("Chunk Animator/Embeddium mixin conflict").define("chunk_animator_embeddium_mixin_conflict", "warn_only");
        b.pop();
        b.pop();
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "stutteranalyzer-common.toml");
    }
}

