package com.stutteranalyzer.client;

import com.stutteranalyzer.StutterAnalyzerFabric;
import com.stutteranalyzer.classifier.FreezeCategory;
import com.stutteranalyzer.classifier.FreezeDetector;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.AlertManager;
import com.stutteranalyzer.core.AlertMode;
import com.stutteranalyzer.core.AnalyzerRuntimeState;
import com.stutteranalyzer.core.MetricsCollector;
import com.stutteranalyzer.core.QuietMode;
import com.stutteranalyzer.core.StutterCounter;
import com.stutteranalyzer.report.FreezeEvent;
import com.stutteranalyzer.update.UpdateCheckResult;
import com.stutteranalyzer.update.UpdateChecker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class FabricClientSetup {

    private static boolean startupMessageShown = false;
    private static int tickCounter = 0;
    private static long lastAggregateChatShownTime = 0;
    private static long lastAggregateShownCount = 0;
    private static long lastAggregateShownWorstMs = 0;

    public static void onClientTick(Minecraft client) {
        MetricsCollector.onClientTick();
        long frameMs = (long) MetricsCollector.frameTime().currentFrameMs();
        if (frameMs > SAConfig.INSTANCE.minorFrameMs.get()) {
            FreezeDetector.onClientFrameSpike(frameMs, MetricsCollector.eventBuffer(), false);
        }

        if (AnalyzerRuntimeState.consumeF3RefreshRequest()) {
            safeRefreshHud();
        }

        tickCounter++;
        if (tickCounter % 20 == 0) {
            safeRefreshHud();
        }

        AlertManager.PendingAlert alert = AlertManager.consumePendingAlert();
        if (alert != null) {
            showAlertMessage(alert);
        }

        // Async optimize scan result
        java.util.List<net.minecraft.network.chat.Component> scanMsgs =
            com.stutteranalyzer.optimize.OptimizeInstaller.consumePendingScanMessages();
        if (scanMsgs != null && client.player != null) {
            for (net.minecraft.network.chat.Component c : scanMsgs) {
                // sendSystemMessage left the client in 1.21.2 - displayClientMessage holds the fort
                client.player.displayClientMessage(c, false);
            }
        }

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
                if (shouldShow && client.player != null) {
                    int window = SAConfig.INSTANCE.minorStutterAggregateWindowSeconds.get();
                    client.player.displayClientMessage(
                        Component.translatable("stutteranalyzer.alerts.aggregate.small", count, window, worst)
                            .withStyle(ChatFormatting.GREEN), false);
                    lastAggregateChatShownTime = now;
                    lastAggregateShownCount = count;
                    lastAggregateShownWorstMs = worst;
                }
            }
        }
    }

    private static void safeRefreshHud() {
        try {
            DebugHudStatusProvider.refresh();
        } catch (Throwable t) {
            StutterAnalyzerFabric.LOGGER.error("[SA] HUD refresh failed: {}", t.getMessage(), t);
        }
    }

    public static void onPlayerLoggedIn() {
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
        boolean isUnknown   = event.category() == FreezeCategory.UNKNOWN_FREEZE;
        boolean isPeriodic  = event.category() == FreezeCategory.PERIODIC_MINOR_MICRO_HITCH;
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

        mc.player.displayClientMessage(AlertHoverText.build(event.category(), ms, msgComp), false);

        if (showHint && info.reportSaved() && SAConfig.INSTANCE.alertShowReportHint.get()) {
            String hintKey = isUnknown
                ? "stutteranalyzer.alert.hint.unknown"
                : (ms >= extreme ? "stutteranalyzer.alert.hint.extreme" : "stutteranalyzer.alert.hint.severe");
            mc.player.displayClientMessage(Component.translatable(hintKey).withStyle(ChatFormatting.YELLOW), false);
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
        mc.player.displayClientMessage(
            Component.translatable("stutteranalyzer.alert.update", latestVersion).withStyle(ChatFormatting.GREEN), false);
        UpdateChecker.markNotified(latestVersion);
    }

    private static void showStartupMessageIfNeeded() {
        if (!SAConfig.INSTANCE.showStartupMessage.get()) return;
        if (SAConfig.INSTANCE.showStartupMessageOncePerSession.get() && startupMessageShown) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (SAConfig.INSTANCE.mentionSilentMinorTracking.get()) {
            mc.player.displayClientMessage(
                Component.translatable("stutteranalyzer.startup.loaded_silent").withStyle(ChatFormatting.GREEN), false);
        } else {
            mc.player.displayClientMessage(
                Component.literal("[Stutter Analyzer] ").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("stutteranalyzer.startup.loaded").withStyle(ChatFormatting.GREEN)), false);
        }
        startupMessageShown = true;
    }
}
