package com.stutteranalyzer.client;

import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.classifier.FreezeDetector;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.AnalyzerRuntimeState;
import com.stutteranalyzer.core.MetricsCollector;
import com.stutteranalyzer.core.QuietMode;
import com.stutteranalyzer.core.StutterCounter;
import com.stutteranalyzer.core.SubsystemHealth;
import com.stutteranalyzer.core.VerboseMode;
import com.stutteranalyzer.report.FreezeEvent;
import net.minecraft.ChatFormatting;
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
    private static long lastChatNotifyTime = 0;
    // Aggregate chat suppression state
    private static long lastAggregateChatShownTime = 0;
    private static long lastAggregateShownCount = 0;
    private static long lastAggregateShownWorstMs = 0;

    public static void onClientSetup(FMLClientSetupEvent event) {
        StutterAnalyzerMod.LOGGER.info("[StutterAnalyzer] Client setup complete.");
        SubsystemHealth.setStatus("F3StatusLineRenderer", SubsystemHealth.Status.OK, null);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MetricsCollector.onClientTick();
        long frameMs = (long) MetricsCollector.frameTime().currentFrameMs();
        // Strictly above threshold: a tick of exactly minorFrameMs is normal, not a stutter
        if (frameMs > SAConfig.INSTANCE.minorFrameMs.get()) {
            FreezeDetector.onClientFrameSpike(frameMs, MetricsCollector.eventBuffer(), false);
        }

        // Immediate F3 refresh requested by debug commands or stutter injection
        if (AnalyzerRuntimeState.consumeF3RefreshRequest()) {
            safeRefreshF3();
        }

        tickCounter++;
        if (tickCounter % 20 == 0) {
            safeRefreshF3();
        }

        // Severe/extreme freeze chat notification with cooldown
        if (FreezeDetector.consumeUnknownFreezeNotification()) {
            long cooldownMs = SAConfig.INSTANCE.chatNotificationCooldownSeconds.get() * 1000L;
            long now = System.currentTimeMillis();
            if (now - lastChatNotifyTime >= cooldownMs) {
                showUnknownFreezeNotification();
                lastChatNotifyTime = now;
            }
        }

        // Verbose mode: show minor/medium stutters in chat (suppressed by quiet mode)
        long verboseMs = FreezeDetector.consumeVerboseNotification();
        if (verboseMs > 0 && VerboseMode.isEnabled() && !QuietMode.isEnabled()) {
            int severe = SAConfig.INSTANCE.severeFrameMs.get();
            int medium = SAConfig.INSTANCE.mediumFrameMs.get();
            boolean showMinor  = verboseMs < medium && SAConfig.INSTANCE.minorChatInVerbose.get();
            boolean showMedium = verboseMs >= medium && verboseMs < severe && SAConfig.INSTANCE.mediumChatInVerbose.get();
            if ((showMinor || showMedium) && VerboseMode.tryNotify(SAConfig.INSTANCE.verboseChatCooldownSeconds.get())) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.translatable("stutteranalyzer.verbose.stutter_detected", verboseMs).withStyle(ChatFormatting.GREEN));
                }
            }
        }

        // Aggregate minor stutter notification - smart suppression
        long[] aggregate = FreezeDetector.consumeAggregateNotification();
        if (aggregate != null && SAConfig.INSTANCE.aggregateRepeatedMinorStutters.get()
                && SAConfig.INSTANCE.minorAggregateChatEnabled.get()
                && !QuietMode.isEnabled()) {
            long now = System.currentTimeMillis();
            long cooldownMs = SAConfig.INSTANCE.minorAggregateChatCooldownSeconds.get() * 1000L;
            if (now - lastAggregateChatShownTime >= cooldownMs) {
                long count = aggregate[0];
                long worst = aggregate[1];
                boolean shouldShow = true;
                if (SAConfig.INSTANCE.minorAggregateShowOnlyIfWorse.get() && lastAggregateShownCount > 0) {
                    long countIncrease = count - lastAggregateShownCount;
                    long worstIncrease = worst - lastAggregateShownWorstMs;
                    int minCount = SAConfig.INSTANCE.minorAggregateMinCountIncrease.get();
                    int minWorst = SAConfig.INSTANCE.minorAggregateMinWorstIncreaseMs.get();
                    shouldShow = countIncrease >= minCount || worstIncrease >= minWorst;
                }
                if (shouldShow) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        int window = SAConfig.INSTANCE.minorStutterAggregateWindowSeconds.get();
                        mc.player.sendSystemMessage(Component.translatable(
                            "stutteranalyzer.verbose.aggregate", count, window, worst).withStyle(ChatFormatting.GREEN));
                        lastAggregateChatShownTime = now;
                        lastAggregateShownCount = count;
                        lastAggregateShownWorstMs = worst;
                    }
                }
            }
        }
    }

    private static void safeRefreshF3() {
        try {
            DebugHudStatusProvider.refresh();
        } catch (Throwable t) {
            StutterAnalyzerMod.LOGGER.error("[SA] F3 refresh failed: {}", t.getMessage(), t);
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
        if (last != null) {
            mc.player.sendSystemMessage(Component.translatable("stutteranalyzer.unknown_freeze.notify_duration", last.durationMs()).withStyle(ChatFormatting.GREEN));
        } else {
            mc.player.sendSystemMessage(Component.translatable("stutteranalyzer.unknown_freeze.notify").withStyle(ChatFormatting.GREEN));
        }
    }

    private static void showStartupMessageIfNeeded() {
        if (!SAConfig.INSTANCE.showStartupMessage.get()) return;
        if (SAConfig.INSTANCE.showStartupMessageOncePerSession.get() && startupMessageShown) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (SAConfig.INSTANCE.mentionSilentMinorTracking.get()) {
            mc.player.sendSystemMessage(Component.translatable("stutteranalyzer.startup.loaded_silent").withStyle(ChatFormatting.GREEN));
        } else {
            mc.player.sendSystemMessage(
                Component.literal("[Stutter Analyzer] ").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("stutteranalyzer.startup.loaded").withStyle(ChatFormatting.GREEN))
            );
        }
        startupMessageShown = true;
    }
}
