package com.stutteranalyzer.client;

import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.classifier.FreezeDetector;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.SubsystemHealth;
import com.stutteranalyzer.report.FreezeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup {

    private static boolean startupMessageShown = false;
    private static int tickCounter = 0;

    public static void onClientSetup(FMLClientSetupEvent event) {
        StutterAnalyzerMod.LOGGER.info("[StutterAnalyzer] Client setup complete.");
        SubsystemHealth.setStatus("F3StatusLineRenderer", SubsystemHealth.Status.OK, null);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter % 20 == 0) {
            try {
                DebugHudStatusProvider.refresh();
            } catch (Throwable ignored) {}
        }
        if (FreezeDetector.consumeUnknownFreezeNotification()) {
            showUnknownFreezeNotification();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        showStartupMessageIfNeeded();
    }

    private static void showUnknownFreezeNotification() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        FreezeEvent last = FreezeDetector.lastFreezeEvent();
        String duration = last != null ? " §7(" + last.durationMs() + " ms)" : "";
        mc.player.sendSystemMessage(Component.literal(
            "§e[Stutter Analyzer] §fUnknown freeze detected" + duration + "§f. " +
            "Report saved. Use §a/sa submit last §fto send it."));
    }

    private static void showStartupMessageIfNeeded() {
        if (!SAConfig.INSTANCE.showStartupMessage.get()) return;
        if (SAConfig.INSTANCE.showStartupMessageOncePerSession.get() && startupMessageShown) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(
            "§7[Stutter Analyzer] §aLoaded. §7Press §aF3 §7to see SA status or use §a/sa selfcheck§7."));
        startupMessageShown = true;
    }
}
