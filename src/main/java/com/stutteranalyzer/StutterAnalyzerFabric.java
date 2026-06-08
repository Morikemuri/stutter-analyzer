package com.stutteranalyzer;

import com.stutteranalyzer.classifier.FreezeDetector;
import com.stutteranalyzer.command.FabricCommandRegistrar;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.MetricsCollector;
import com.stutteranalyzer.core.SubsystemHealth;
import com.stutteranalyzer.crash.PreviousCrashImporter;
import com.stutteranalyzer.events.RecentEventBuffer;
import com.stutteranalyzer.knowledge.OptimizationModKnowledgeBase;
import com.stutteranalyzer.submission.SubmissionManager;
import com.stutteranalyzer.update.UpdateChecker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StutterAnalyzerFabric implements ModInitializer {

    public static final String MOD_ID        = "stutteranalyzer";
    public static final String MOD_VERSION   = "0.1.0-rc1";
    public static final String BUILD_ID      = "fabric-port-1.20.4";
    public static final String BUILD_DATE    = "2026-06-09-fabric";
    public static final String BUILD_FEATURES = "update-checker,quiet-mode,episode-counting,extreme-tracking,rich-status-v2,debug-routing,submit-cloudflare-v2,simplified-ux,rich-issue-body,simplified-submit,log-events,freeze-context,suspicious-signals,runtime-snapshot,payload-diagnostics,received-fields,log-context-classifier,upload-lock-fix,upload-timeout,upload-id,async-submit,safe-submit,brigadier-crash-guard,submit-minimal,fast-response,submit-check,fabric-port";
    public static final Logger LOGGER        = LogManager.getLogger(MOD_ID);

    private static volatile long serverTickStart = 0;

    @Override
    public void onInitialize() {
        // Environment must be set before config loads
        SAEnvironment.init(
            FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT,
            FabricLoader.getInstance().getGameDir(),
            "fabric",
            FabricLoader.getInstance().getModContainer("fabricloader")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown")
        );

        SAConfig.load();

        // Commands - registered for both client and dedicated server
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if (!SAConfig.INSTANCE.enableCommands.get()) return;
            FabricCommandRegistrar.register(dispatcher, "stutteranalyzer");
            if (SAConfig.INSTANCE.enableAliasSa.get()) {
                FabricCommandRegistrar.register(dispatcher, "sa");
            }
        });

        // Server tick tracking
        ServerTickEvents.START_SERVER_TICK.register(server -> serverTickStart = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(StutterAnalyzerFabric::onServerTickEnd);

        // Track player joins for world-join classification
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            MetricsCollector.eventBuffer().push(
                RecentEventBuffer.EventType.PLAYER_JOIN,
                handler.player.getName().getString()));

        // Track server start for world-join classification
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
            MetricsCollector.eventBuffer().push(
                RecentEventBuffer.EventType.INTEGRATED_SERVER_START, "server started"));

        doCommonSetup();

        LOGGER.info("[StutterAnalyzer] Loaded {} build={} id=[{}]", MOD_VERSION, BUILD_DATE, BUILD_ID);
    }

    private static void onServerTickEnd(MinecraftServer server) {
        if (serverTickStart == 0) return;
        long elapsed = System.nanoTime() - serverTickStart;
        MetricsCollector.onServerTick(elapsed);
        if (SAConfig.INSTANCE.enableServerTickDetection.get()) {
            long mspt = elapsed / 1_000_000L;
            if (mspt >= SAConfig.INSTANCE.warningMspt.get()) {
                boolean isDedicated = !SAEnvironment.isClientSide();
                FreezeDetector.onServerTickSpike(mspt, MetricsCollector.eventBuffer(), isDedicated);
            }
        }
    }

    private void doCommonSetup() {
        if (!SAEnvironment.isClientSide()) {
            SubsystemHealth.setStatus("F3StatusLineRenderer",
                SubsystemHealth.Status.UNAVAILABLE, "dedicated server has no debug screen");
        }

        try {
            String target = SAConfig.INSTANCE.submissionTarget.get();
            boolean browserOpen = SAConfig.INSTANCE.openIssueUrlOnClient.get();
            boolean clipboard = SAConfig.INSTANCE.copyIssueBodyToClipboard.get();
            if ("local".equalsIgnoreCase(target) || browserOpen || clipboard) {
                SAConfig.INSTANCE.submissionTarget.set("cloudflare");
                SAConfig.INSTANCE.openIssueUrlOnClient.set(false);
                SAConfig.INSTANCE.copyIssueBodyToClipboard.set(false);
                LOGGER.info("[StutterAnalyzer] Submission config migrated to Cloudflare Worker.");
                SubmissionManager.setPendingMigrationNotice();
            }
            if (SAConfig.INSTANCE.askFirstTime.get()) {
                SAConfig.INSTANCE.askFirstTime.set(false);
            }
        } catch (Throwable t) {
            LOGGER.warn("[StutterAnalyzer] Config migration check failed: {}", t.getMessage());
        }

        try {
            OptimizationModKnowledgeBase.load();
        } catch (Throwable t) {
            LOGGER.error("[StutterAnalyzer] Knowledge base failed to load: {}", t.getMessage(), t);
        }

        try {
            PreviousCrashImporter.scanAsync(SAEnvironment.getGameDir());
        } catch (Throwable t) {
            LOGGER.error("[StutterAnalyzer] Crash importer failed: {}", t.getMessage(), t);
            SubsystemHealth.setStatus("CrashReportImporter", SubsystemHealth.Status.FAILED, t.getMessage());
        }

        try {
            UpdateChecker.scheduleStartupCheck();
        } catch (Throwable t) {
            LOGGER.warn("[StutterAnalyzer] Update checker failed: {}", t.getMessage());
        }

        if (SAConfig.INSTANCE.guardEmergencyMode.get()) {
            LOGGER.warn("[StutterAnalyzer] Emergency mode is ENABLED.");
        }

        LOGGER.info("[SA] Build ID: {}", BUILD_ID);
        LOGGER.info("[StutterAnalyzer] Ready. Use /sa status or /sa help in-game.");
    }
}
