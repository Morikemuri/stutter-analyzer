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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("stutteranalyzer")
public class StutterAnalyzerMod {

    public static final String MOD_ID      = "stutteranalyzer";
    public static final String MOD_VERSION = "1.0.0";
    public static final String BUILD_DATE  = "2026-06-08-b1";
    public static final String BUILD_FEATURES = "update-checker,quiet-mode,episode-counting,extreme-tracking,rich-status-v2,debug-routing,submit-cloudflare-v2,config-migration";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public StutterAnalyzerMod() {
        SAConfig.register(ModLoadingContext.get());

        var modBus = FMLJavaModLoadingContext.get().getModEventBus();

        modBus.addListener(this::onCommonSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(ClientSetup::onClientSetup);

            // Forge bus - client tick and player login
            MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(ClientSetup::onPlayerLoggedIn);

            // F3 debug screen line
            MinecraftForge.EVENT_BUS.addListener(F3StatusLineRenderer::onDebugText);
        }

        // Server tick - tracks MSPT and fires FreezeDetector on both client and dedicated server
        MinecraftForge.EVENT_BUS.addListener(StutterAnalyzerMod::onServerTick);

        // Server commands - registered on Forge bus explicitly (works on both client and dedicated server)
        MinecraftForge.EVENT_BUS.addListener(ServerCommandRegistrar::onRegisterCommands);

        // Client commands - only on client
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.addListener(ClientCommandRegistrar::onRegisterClientCommands);
        }

        LOGGER.info("[StutterAnalyzer] Loaded StutterAnalyzer {} build {} features=[{}]",
            MOD_VERSION, BUILD_DATE, BUILD_FEATURES);
    }

    private static volatile long serverTickStart = 0;

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            serverTickStart = System.nanoTime();
            return;
        }
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

            // Migrate legacy submission config (old defaults: submission_target=local, browser/clipboard=true)
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
                // Always disable ask_first_time - submit goes directly to Cloudflare without manual consent step
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
                long stutterJarCount = Files.list(FMLPaths.GAMEDIR.get().resolve("mods"))
                    .filter(p -> p.getFileName().toString().toLowerCase().startsWith("stutteranalyzer") &&
                                 p.getFileName().toString().endsWith(".jar"))
                    .count();
                if (stutterJarCount > 1) {
                    LOGGER.warn("[StutterAnalyzer] WARNING: Multiple Stutter Analyzer jars detected in mods folder. Remove old versions.");
                }
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
