package com.stutteranalyzer.client;

import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.classifier.FreezeCategory;
import com.stutteranalyzer.classifier.FreezeDetector;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.AlertManager;
import com.stutteranalyzer.core.AlertMode;
import com.stutteranalyzer.core.AnalyzerRuntimeState;
import com.stutteranalyzer.core.MetricsCollector;
import com.stutteranalyzer.core.QuietMode;
import com.stutteranalyzer.core.StutterCounter;
import com.stutteranalyzer.core.SubsystemHealth;
import com.stutteranalyzer.report.FreezeEvent;
import com.stutteranalyzer.update.UpdateCheckResult;
import com.stutteranalyzer.update.UpdateChecker;
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

        // AlertManager: preset-based chat alerts for all categories
        AlertManager.PendingAlert alert = AlertManager.consumePendingAlert();
        if (alert != null) {
            showAlertMessage(alert);
        }

        // Async optimize scan result
        java.util.List<net.minecraft.network.chat.Component> scanMsgs =
            com.stutteranalyzer.optimize.OptimizeInstaller.consumePendingScanMessages();
        if (scanMsgs != null) {
            Minecraft mc2 = Minecraft.getInstance();
            if (mc2.player != null) {
                for (net.minecraft.network.chat.Component c : scanMsgs) {
                    mc2.player.sendSystemMessage(c);
                }
            }
        }

        // Aggregate minor stutter notification - smart suppression (gated on alert mode)
        long[] aggregate = FreezeDetector.consumeAggregateNotification();
        if (aggregate != null && SAConfig.INSTANCE.aggregateRepeatedMinorStutters.get()
                && SAConfig.INSTANCE.minorAggregateChatEnabled.get()
                && AlertManager.currentMode() != AlertMode.OFF
                && SAConfig.INSTANCE.alertAggregateSmallStutters.get()
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
                            "stutteranalyzer.alerts.aggregate.small", count, window, worst).withStyle(ChatFormatting.GREEN));
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
        showUpdateNotificationIfNeeded();
    }

    private static void showAlertMessage(AlertManager.PendingAlert info) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        FreezeEvent event = info.event();
        long ms = info.durationMs();
        int severe  = SAConfig.INSTANCE.severeFrameMs.get();
        int extreme = SAConfig.INSTANCE.extremeFrameMs.get();
        int medium  = SAConfig.INSTANCE.mediumFrameMs.get();
        boolean isUnknown = event.category() == FreezeCategory.UNKNOWN_FREEZE;
        boolean isPeriodic = event.category() == FreezeCategory.PERIODIC_MINOR_MICRO_HITCH;
        boolean isScheduled = event.category() == FreezeCategory.PERIODIC_SCHEDULED_MICRO_HITCH;
        String catName = event.category().name();

        Component msgComp;
        boolean showHint = false;
        if (ms >= extreme) {
            msgComp = Component.translatable("stutteranalyzer.alert.extreme", catName, ms);
            showHint = true;
        } else if (ms >= severe) {
            msgComp = isUnknown
                ? Component.translatable("stutteranalyzer.alert.severe_unknown", ms)
                : Component.translatable("stutteranalyzer.alert.severe", catName, ms);
            showHint = true;
        } else if (ms >= medium) {
            msgComp = Component.translatable("stutteranalyzer.alert.medium", catName, ms);
        } else if (isScheduled) {
            FreezeEvent.PeriodicMeta meta = event.periodicMeta();
            String periodStr = meta != null ? "~" + (meta.periodMsEstimate() / 1000L) + "s" : "?";
            msgComp = Component.translatable("stutteranalyzer.alert.periodic_scheduled", ms, periodStr);
        } else if (isPeriodic) {
            msgComp = Component.translatable("stutteranalyzer.alert.periodic_minor", ms);
        } else {
            msgComp = Component.translatable("stutteranalyzer.alert.minor", catName, ms);
        }

        mc.player.sendSystemMessage(AlertHoverText.build(event.category(), ms, msgComp));

        if (showHint && info.reportSaved() && SAConfig.INSTANCE.alertShowReportHint.get()) {
            String hintKey = isUnknown
                ? "stutteranalyzer.alert.hint.unknown"
                : (ms >= extreme ? "stutteranalyzer.alert.hint.extreme" : "stutteranalyzer.alert.hint.severe");
            mc.player.sendSystemMessage(Component.translatable(hintKey).withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void showUpdateNotificationIfNeeded() {
        if (!SAConfig.INSTANCE.checkForUpdates.get()) return;
        UpdateCheckResult result = UpdateChecker.getCached();
        if (result == null || !result.success() || !result.updateAvailable()) return;
        String latestVersion = result.latestVersion();
        if (SAConfig.INSTANCE.notifyOnlyOncePerVersion.get()
                && latestVersion.equals(UpdateChecker.getLastNotifiedVersion())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(
            Component.translatable("stutteranalyzer.alert.update", latestVersion).withStyle(ChatFormatting.GREEN));
        UpdateChecker.markNotified(latestVersion);
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
