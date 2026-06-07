package com.stutteranalyzer.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

public class SAConfig {

    public static final ForgeConfigSpec SPEC;
    public static final SAConfig INSTANCE;

    static {
        Pair<SAConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(SAConfig::new);
        INSTANCE = specPair.getLeft();
        SPEC     = specPair.getRight();
    }

    // ── [general] ─────────────────────────────────────────────────────────
    public final ForgeConfigSpec.BooleanValue enabled;
    public final ForgeConfigSpec.BooleanValue debug;
    public final ForgeConfigSpec.BooleanValue overlay;
    public final ForgeConfigSpec.ConfigValue<String> reportFormat;
    public final ForgeConfigSpec.IntValue maxReports;

    // ── [commands] ────────────────────────────────────────────────────────
    public final ForgeConfigSpec.BooleanValue enableCommands;
    public final ForgeConfigSpec.BooleanValue enableAliasSa;
    public final ForgeConfigSpec.BooleanValue allowPlayersBasicStatus;
    public final ForgeConfigSpec.BooleanValue allowPlayersExportOwnClientReports;
    public final ForgeConfigSpec.IntValue serverReportPermissionLevel;
    public final ForgeConfigSpec.IntValue configReloadPermissionLevel;
    public final ForgeConfigSpec.IntValue deleteReportPermissionLevel;
    public final ForgeConfigSpec.IntValue debugPermissionLevel;
    public final ForgeConfigSpec.IntValue submitReportPermissionLevel;
    public final ForgeConfigSpec.IntValue guardPermissionLevel;

    // ── [client] ──────────────────────────────────────────────────────────
    public final ForgeConfigSpec.BooleanValue enableClientStutterDetection;
    public final ForgeConfigSpec.IntValue minorFrameMs;
    public final ForgeConfigSpec.IntValue mediumFrameMs;
    public final ForgeConfigSpec.IntValue severeFrameMs;
    public final ForgeConfigSpec.IntValue extremeFrameMs;
    public final ForgeConfigSpec.BooleanValue trackOnePercentLows;
    public final ForgeConfigSpec.BooleanValue trackPointOnePercentLows;

    // ── [server] ──────────────────────────────────────────────────────────
    public final ForgeConfigSpec.BooleanValue enableServerTickDetection;
    public final ForgeConfigSpec.IntValue warningMspt;
    public final ForgeConfigSpec.IntValue mediumMspt;
    public final ForgeConfigSpec.IntValue severeMspt;
    public final ForgeConfigSpec.IntValue extremeMspt;

    // ── [classification] ──────────────────────────────────────────────────
    public final ForgeConfigSpec.DoubleValue minimumConfidence;
    public final ForgeConfigSpec.BooleanValue unknownFreezeEnabled;

    // ── [safety] ──────────────────────────────────────────────────────────
    public final ForgeConfigSpec.BooleanValue failSilently;
    public final ForgeConfigSpec.BooleanValue disableFailedSubsystems;
    public final ForgeConfigSpec.BooleanValue neverCrashGame;

    // ── [reports] ─────────────────────────────────────────────────────────
    public final ForgeConfigSpec.BooleanValue saveMarkdown;
    public final ForgeConfigSpec.BooleanValue saveJson;
    public final ForgeConfigSpec.BooleanValue includeModList;
    public final ForgeConfigSpec.BooleanValue includeSystemInfo;
    public final ForgeConfigSpec.BooleanValue includeRecentEvents;

    // ── [submission] ──────────────────────────────────────────────────────
    public final ForgeConfigSpec.BooleanValue enableManualSubmission;
    public final ForgeConfigSpec.BooleanValue enableAutomaticUnknownFreezeUpload;
    public final ForgeConfigSpec.ConfigValue<String> submissionTarget;
    public final ForgeConfigSpec.ConfigValue<String> githubIssueUrl;
    public final ForgeConfigSpec.BooleanValue anonymizeReports;
    public final ForgeConfigSpec.BooleanValue redactUsernames;
    public final ForgeConfigSpec.BooleanValue redactFilePaths;
    public final ForgeConfigSpec.BooleanValue redactIpAddresses;
    public final ForgeConfigSpec.BooleanValue submissionIncludeModList;
    public final ForgeConfigSpec.BooleanValue submissionIncludeSystemInfo;
    public final ForgeConfigSpec.BooleanValue submissionIncludeRecentEvents;
    public final ForgeConfigSpec.BooleanValue askBeforeEveryUpload;

    // ── [compatibility_guard] ─────────────────────────────────────────────
    public final ForgeConfigSpec.BooleanValue guardEnabled;
    public final ForgeConfigSpec.BooleanValue guardEmergencyMode;
    public final ForgeConfigSpec.BooleanValue automaticSafeGuards;
    public final ForgeConfigSpec.BooleanValue warnOnlyForUnsafePatterns;
    public final ForgeConfigSpec.BooleanValue writeCrashHints;
    public final ForgeConfigSpec.BooleanValue writeLatestLogHints;
    public final ForgeConfigSpec.BooleanValue writeSeparateHintReports;
    public final ForgeConfigSpec.BooleanValue rateLimitGuardTriggers;
    public final ForgeConfigSpec.IntValue maxGuardTriggersPerSession;
    public final ForgeConfigSpec.DoubleValue minimumAutoGuardConfidence;
    public final ForgeConfigSpec.DoubleValue minimumWarnConfidence;

    // ── [debug_hud] ───────────────────────────────────────────────────────
    public final ForgeConfigSpec.BooleanValue debugHudEnabled;
    public final ForgeConfigSpec.BooleanValue debugHudShowColored;
    public final ForgeConfigSpec.BooleanValue debugHudShowLastFreeze;
    public final ForgeConfigSpec.BooleanValue debugHudShowEmergencyMode;
    public final ForgeConfigSpec.BooleanValue debugHudShowReportCount;
    public final ForgeConfigSpec.BooleanValue debugHudShowSubsystemWarnings;
    public final ForgeConfigSpec.BooleanValue debugHudCompactMode;

    // ── [startup_message] ─────────────────────────────────────────────────
    public final ForgeConfigSpec.BooleanValue showStartupMessage;
    public final ForgeConfigSpec.BooleanValue showStartupMessageOncePerSession;

    // ── [compatibility_guard.guards] ──────────────────────────────────────
    public final ForgeConfigSpec.ConfigValue<String> guardRubidiumLava;
    public final ForgeConfigSpec.ConfigValue<String> guardDynamicFps;
    public final ForgeConfigSpec.ConfigValue<String> guardC2meCorruptedChunk;
    public final ForgeConfigSpec.ConfigValue<String> guardC2meDeadlock;
    public final ForgeConfigSpec.ConfigValue<String> guardDistantHorizons;
    public final ForgeConfigSpec.ConfigValue<String> guardIrisOculus;
    public final ForgeConfigSpec.ConfigValue<String> guardStarlightScalableLux;
    public final ForgeConfigSpec.ConfigValue<String> guardEmbeddiumOculus;
    public final ForgeConfigSpec.ConfigValue<String> guardChunkAnimator;

    private SAConfig(ForgeConfigSpec.Builder b) {
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
        serverReportPermissionLevel = b.comment("Permission level for /sa server report/list/show").defineInRange("server_report_permission_level", 2, 0, 4);
        configReloadPermissionLevel = b.comment("Permission level for /sa config reload").defineInRange("config_reload_permission_level", 3, 0, 4);
        deleteReportPermissionLevel = b.comment("Permission level for /sa delete").defineInRange("delete_report_permission_level", 3, 0, 4);
        debugPermissionLevel = b.comment("Permission level for /sa debug commands").defineInRange("debug_permission_level", 4, 0, 4);
        submitReportPermissionLevel = b.comment("Permission level to submit reports").defineInRange("submit_report_permission_level", 3, 0, 4);
        guardPermissionLevel = b.comment("Permission level for /sa guard enable/disable").defineInRange("guard_permission_level", 4, 0, 4);
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
        disableFailedSubsystems = b.comment("Automatically disable subsystems that fail rather than crashing everything. We are a doctor, not a grenade.").define("disable_failed_subsystems", true);
        neverCrashGame = b.comment("Never throw from event callbacks. The analyzer watching Minecraft crash is ironic enough already.").define("never_crash_game", true);
        b.pop();

        b.comment("Report generation").push("reports");
        saveMarkdown = b.comment("Save freeze reports as Markdown files").define("save_markdown", true);
        saveJson = b.comment("Save freeze reports as JSON files").define("save_json", true);
        includeModList = b.comment("Include the loaded mod list in reports").define("include_mod_list", true);
        includeSystemInfo = b.comment("Include system info (Java, OS, CPU, memory) in reports").define("include_system_info", true);
        includeRecentEvents = b.comment("Include the recent event timeline in reports").define("include_recent_events", true);
        b.pop();

        b.comment("Report submission").push("submission");
        enableManualSubmission = b.comment("Allow manual report submission through commands").define("enable_manual_submission", true);
        enableAutomaticUnknownFreezeUpload = b.comment("Upload unknown freeze reports automatically. DISABLED by default. Automatic uploads require explicit opt-in - trust matters more than data hoarding.").define("enable_automatic_unknown_freeze_upload", false);
        submissionTarget = b.comment("Submission target: github, local").define("submission_target", "github");
        githubIssueUrl = b.comment("GitHub new issue URL for manual submission").define("github_issue_url", "https://github.com/Morikemuri/stutter-analyzer/issues/new");
        anonymizeReports = b.comment("Anonymize reports before submission").define("anonymize_reports", true);
        redactUsernames = b.comment("Redact usernames from file paths in reports").define("redact_usernames", true);
        redactFilePaths = b.comment("Redact absolute file paths in reports").define("redact_file_paths", true);
        redactIpAddresses = b.comment("Redact IP addresses from reports").define("redact_ip_addresses", true);
        submissionIncludeModList = b.comment("Include mod list in submitted reports").define("include_mod_list", true);
        submissionIncludeSystemInfo = b.comment("Include system info in submitted reports").define("include_system_info", true);
        submissionIncludeRecentEvents = b.comment("Include recent event timeline in submitted reports").define("include_recent_events", true);
        askBeforeEveryUpload = b.comment("Ask for confirmation before every upload").define("ask_before_every_upload", true);
        b.pop();

        b.comment("Emergency Compatibility Guard").push("compatibility_guard");
        b.comment("F3 debug screen status line").push("debug_hud");
        debugHudEnabled = b.comment("Show a tiny SA line on the F3 screen so you know the mod is alive and not just pretending.").define("enable_f3_status_line", true);
        debugHudShowColored = b.comment("Use colored text in F3 status line. Green is good, yellow is suspicious, red means look at /sa health.").define("show_colored_status", true);
        debugHudShowLastFreeze = b.comment("Show last freeze on F3 line").define("show_last_freeze_on_f3", true);
        debugHudShowEmergencyMode = b.comment("Show emergency mode state on F3 line").define("show_emergency_mode_on_f3", true);
        debugHudShowReportCount = b.comment("Show report count on F3 line").define("show_report_count_on_f3", true);
        debugHudShowSubsystemWarnings = b.comment("Show subsystem warnings on F3 line").define("show_subsystem_warnings_on_f3", true);
        debugHudCompactMode = b.comment("Use compact single-line format. F3 is crowded enough already.").define("compact_mode", true);
        b.pop();

        b.comment("Startup confirmation message").push("startup_message");
        showStartupMessage = b.comment("Show a startup message when joining a world. Once per session - not every time Minecraft loads 847 mods.").define("show_startup_message", true);
        showStartupMessageOncePerSession = b.comment("Only show the startup message once per session").define("show_only_once_per_session", true);
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

    public static void register(ModLoadingContext ctx) {
        ctx.registerConfig(ModConfig.Type.COMMON, SPEC, "stutteranalyzer-common.toml");
    }
}
