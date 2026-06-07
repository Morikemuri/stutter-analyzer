package com.stutteranalyzer;

import com.stutteranalyzer.client.ClientSetup;
import com.stutteranalyzer.client.F3StatusLineRenderer;
import com.stutteranalyzer.command.ClientCommandRegistrar;
import com.stutteranalyzer.command.ServerCommandRegistrar;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.SubsystemHealth;
import com.stutteranalyzer.crash.PreviousCrashImporter;
import com.stutteranalyzer.knowledge.OptimizationModKnowledgeBase;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
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

    public static final String MOD_ID = "stutteranalyzer";
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

        // Server commands - registered on Forge bus explicitly (works on both client and dedicated server)
        MinecraftForge.EVENT_BUS.addListener(ServerCommandRegistrar::onRegisterCommands);

        // Client commands - only on client
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.addListener(ClientCommandRegistrar::onRegisterClientCommands);
        }

        LOGGER.info("[StutterAnalyzer] Initializing...");
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                SubsystemHealth.setStatus("F3StatusLineRenderer", SubsystemHealth.Status.UNAVAILABLE, "dedicated server has no debug screen");
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

            if (SAConfig.INSTANCE.guardEmergencyMode.get()) {
                LOGGER.warn("[StutterAnalyzer] Emergency mode is ENABLED. Safe guards may activate automatically.");
            }

            LOGGER.info("[StutterAnalyzer] Ready. Use /sa status or /sa help in-game.");
        });
    }
}
