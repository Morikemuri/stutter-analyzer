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
import com.stutteranalyzer.core.VerboseMode;
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

        long verboseMs = FreezeDetector.consumeVerboseNotification();
        if (verboseMs > 0 && VerboseMode.isEnabled() && !QuietMode.isEnabled()) {
            int severe  = SAConfig.INSTANCE.severeFrameMs.get();
            int medium  = SAConfig.INSTANCE.mediumFrameMs.get();
            boolean showMinor  = verboseMs < medium && SAConfig.INSTANCE.minorChatInVerbose.get();
            boolean showMedium = verboseMs >= medium && verboseMs < severe && SAConfig.INSTANCE.mediumChatInVerbose.get();
            if ((showMinor || showMedium) && VerboseMode.tryNotify(SAConfig.INSTANCE.verboseChatCooldownSeconds.get())) {
                if (client.player != null) {
                    client.player.sendSystemMessage(
                        Component.translatable("stutteranalyzer.verbose.stutter_detected", verboseMs)
                            .withStyle(ChatFormatting.GREEN));
                }
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
                    client.player.sendSystemMessage(
                        Component.translatable("stutteranalyzer.verbose.aggregate", count, window, worst)
                            .withStyle(ChatFormatting.GREEN));
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

        String msg;
        boolean showHint = false;
        if (ms >= extreme) {
            msg = "[SA] Extreme freeze detected: " + catName + " " + ms + "ms";
            showHint = true;
        } else if (ms >= severe) {
            msg = isUnknown ? "[SA] Unknown freeze detected: " + ms + "ms"
                            : "[SA] Freeze detected: " + catName + " " + ms + "ms";
            showHint = true;
        } else if (ms >= medium) {
            msg = "[SA] Medium stutter detected: " + catName + " " + ms + "ms";
        } else if (isScheduled) {
            FreezeEvent.PeriodicMeta meta = event.periodicMeta();
            String periodStr = meta != null ? "~" + (meta.periodMsEstimate() / 1000L) + "s" : "unknown";
            msg = "[SA] Scheduled minor micro-hitch: ~" + ms + "ms every " + periodStr + ". Usually harmless.";
        } else if (isPeriodic) {
            msg = "[SA] Minor micro-hitch detected: periodic " + ms + "ms hitch";
        } else {
            msg = "[SA] Minor stutter detected: " + catName + " " + ms + "ms";
        }

        mc.player.sendSystemMessage(AlertHoverText.build(event.category(), ms, msg));

        if (showHint && info.reportSaved() && SAConfig.INSTANCE.alertShowReportHint.get()) {
            String hint = isUnknown
                ? "[SA] Report saved. Use /sa submit to help improve detection."
                : (ms >= extreme
                    ? "[SA] Report saved. Use /sa submit to send logs."
                    : "[SA] Report saved. Use /sa submit to send diagnostics.");
            mc.player.sendSystemMessage(Component.literal(hint).withStyle(ChatFormatting.YELLOW));
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
        mc.player.sendSystemMessage(Component.literal(
            "[SA] Update available: " + latestVersion + ". Use /sa update link for download info."
        ).withStyle(ChatFormatting.GREEN));
        UpdateChecker.markNotified(latestVersion);
    }

    private static void showStartupMessageIfNeeded() {
        if (!SAConfig.INSTANCE.showStartupMessage.get()) return;
        if (SAConfig.INSTANCE.showStartupMessageOncePerSession.get() && startupMessageShown) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (SAConfig.INSTANCE.mentionSilentMinorTracking.get()) {
            mc.player.sendSystemMessage(
                Component.translatable("stutteranalyzer.startup.loaded_silent").withStyle(ChatFormatting.GREEN));
        } else {
            mc.player.sendSystemMessage(
                Component.literal("[Stutter Analyzer] ").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("stutteranalyzer.startup.loaded").withStyle(ChatFormatting.GREEN)));
        }
        startupMessageShown = true;
    }
}
