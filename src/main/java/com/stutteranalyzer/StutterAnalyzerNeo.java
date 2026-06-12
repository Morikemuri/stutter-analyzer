package com.stutteranalyzer;

import com.stutteranalyzer.classifier.FreezeDetector;
import com.stutteranalyzer.client.ClientSetup;
import com.stutteranalyzer.submission.SubmissionManager;
import com.stutteranalyzer.client.F3StatusLineRenderer;
import com.stutteranalyzer.command.ClientCommandRegistrar;
import com.stutteranalyzer.command.ServerCommandRegistrar;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.MetricsCollector;
import com.stutteranalyzer.core.SubsystemHealth;
import com.stutteranalyzer.crash.PreviousCrashImporter;
import com.stutteranalyzer.knowledge.OptimizationModKnowledgeBase;
import com.stutteranalyzer.update.UpdateChecker;

import java.nio.file.Files;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("stutteranalyzer")
public class StutterAnalyzerNeo {

    public static final String MOD_ID         = "stutteranalyzer";
    public static final String MOD_VERSION    = "0.4.0";
    // The one true Minecraft version - hardcoded strings hiding in dark corners are how ports go stale
    public static final String MC_VERSION     = "1.21.6";
    public static final String BUILD_ID       = "release";
    public static final String BUILD_DATE     = "2026-06-11-b1-1216";
    public static final String BUILD_FEATURES = "update-checker,quiet-mode,episode-counting,extreme-tracking,rich-status-v2,submit-cloudflare-v2,config-migration,simplified-ux,rich-issue-body,simplified-submit,log-events,freeze-context,suspicious-signals,runtime-snapshot,payload-diagnostics,received-fields,log-context-classifier,upload-lock-fix,upload-timeout,upload-id,async-submit,safe-submit,brigadier-crash-guard,submit-minimal,fast-response";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public StutterAnalyzerNeo(IEventBus modEventBus, ModContainer modContainer) {
        SAConfig.register(modContainer);

        modEventBus.addListener(this::onCommonSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ClientSetup::onClientSetup);

            NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
            NeoForge.EVENT_BUS.addListener(ClientSetup::onPlayerLoggedIn);

            NeoForge.EVENT_BUS.addListener(F3StatusLineRenderer::onDebugText);
        }

        NeoForge.EVENT_BUS.addListener(StutterAnalyzerNeo::onServerTickPre);
        NeoForge.EVENT_BUS.addListener(StutterAnalyzerNeo::onServerTickPost);

        NeoForge.EVENT_BUS.addListener(ServerCommandRegistrar::onRegisterCommands);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(ClientCommandRegistrar::onRegisterClientCommands);
        }

        LOGGER.info("[StutterAnalyzer] Loaded StutterAnalyzer {} build={} id=[{}]",
            MOD_VERSION, BUILD_DATE, BUILD_ID);
    }

    public static String getLoadedJarName() {
        try {
            return Files.list(FMLPaths.GAMEDIR.get().resolve("mods"))
                .filter(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    return n.startsWith("stutteranalyzer") && n.endsWith(".jar");
                })
                .map(p -> p.getFileName().toString())
                .findFirst()
                .orElse("unknown");
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static volatile long serverTickStart = 0;

    private static void onServerTickPre(ServerTickEvent.Pre event) {
        serverTickStart = System.nanoTime();
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (serverTickStart == 0) return;
        long elapsed = System.nanoTime() - serverTickStart;
        MetricsCollector.onServerTick(elapsed);
        if (SAConfig.INSTANCE.enableServerTickDetection.get()) {
            long mspt = elapsed / 1_000_000L;
            if (mspt >= SAConfig.INSTANCE.warningMspt.get()) {
                FreezeDetector.onServerTickSpike(mspt, MetricsCollector.eventBuffer(), FMLEnvironment.dist != Dist.CLIENT);
            }
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                SubsystemHealth.setStatus("F3StatusLineRenderer", SubsystemHealth.Status.UNAVAILABLE, "dedicated server has no debug screen");
            }

            try {
                String target = SAConfig.INSTANCE.submissionTarget.get();
                boolean browserOpen = SAConfig.INSTANCE.openIssueUrlOnClient.get();
                boolean clipboard = SAConfig.INSTANCE.copyIssueBodyToClipboard.get();
                if ("local".equalsIgnoreCase(target) || browserOpen || clipboard) {
                    SAConfig.INSTANCE.submissionTarget.set("cloudflare");
                    SAConfig.INSTANCE.openIssueUrlOnClient.set(false);
                    SAConfig.INSTANCE.copyIssueBodyToClipboard.set(false);
                    LOGGER.info("[StutterAnalyzer] Submission config migrated: default submit now uses Cloudflare Worker. Manual GitHub browser flow is disabled.");
                    SubmissionManager.setPendingMigrationNotice();
                }
                if (SAConfig.INSTANCE.askFirstTime.get()) {
                    SAConfig.INSTANCE.askFirstTime.set(false);
                    LOGGER.info("[StutterAnalyzer] Submission config: ask_first_time disabled, submit no longer requires manual confirmation.");
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
                PreviousCrashImporter.scanAsync(FMLPaths.GAMEDIR.get());
            } catch (Throwable t) {
                LOGGER.error("[StutterAnalyzer] Crash importer failed: {}", t.getMessage(), t);
                SubsystemHealth.setStatus("CrashReportImporter", SubsystemHealth.Status.FAILED, t.getMessage());
            }

            try {
                UpdateChecker.scheduleStartupCheck();
            } catch (Throwable t) {
                LOGGER.warn("[StutterAnalyzer] Update checker failed to schedule: {}", t.getMessage());
            }

            try {
                java.util.List<String> stutterJars = Files.list(FMLPaths.GAMEDIR.get().resolve("mods"))
                    .filter(p -> p.getFileName().toString().toLowerCase().startsWith("stutteranalyzer") &&
                                 p.getFileName().toString().endsWith(".jar"))
                    .map(p -> p.getFileName().toString())
                    .collect(java.util.stream.Collectors.toList());
                if (stutterJars.size() > 1) {
                    LOGGER.warn("[SA] WARNING: Multiple Stutter Analyzer jars detected in mods folder. Remove old versions.");
                }
                String jarName = stutterJars.isEmpty() ? "unknown" : stutterJars.get(0);
                LOGGER.info("[SA] Loaded jar: {}", jarName);
                LOGGER.info("[SA] Build ID: {}", BUILD_ID);
            } catch (Throwable t) {
                LOGGER.debug("[StutterAnalyzer] Jar duplicate check skipped: {}", t.getMessage());
            }

            if (SAConfig.INSTANCE.guardEmergencyMode.get()) {
                LOGGER.warn("[StutterAnalyzer] Emergency mode is ENABLED. Safe guards may activate automatically.");
            }

            LOGGER.info("[StutterAnalyzer] Ready. Use /sa status or /sa help in-game.");
        });
    }
}
