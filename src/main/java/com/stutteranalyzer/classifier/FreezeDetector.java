package com.stutteranalyzer.classifier;

import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.MetricsCollector;
import com.stutteranalyzer.core.SafeExecutor;
import com.stutteranalyzer.core.StutterCounter;
import com.stutteranalyzer.events.RecentEventBuffer;
import com.stutteranalyzer.report.FreezeEvent;
import com.stutteranalyzer.report.FreezeReport;
import com.stutteranalyzer.report.ReportWriter;

import java.util.List;

/**
 * Entry point for freeze detection on both client and server.
 * Call onClientFrameSpike() from client tick, onServerTickSpike() from server tick.
 * Unknown freezes are not failure - they are Minecraft saying "guess what I did".
 */
public class FreezeDetector {

    private static final FreezeClassifier classifier = new FreezeClassifier();
    private static FreezeEvent lastFreezeEvent = null;
    private static volatile boolean unknownFreezePendingNotification = false;
    private static volatile boolean aggregatePendingNotification = false;
    private static volatile long aggregatePendingWorstMs = 0;
    private static volatile int aggregatePendingCount = 0;
    private static volatile boolean verboseNotificationPending = false;
    private static volatile long verboseNotificationMs = 0;
    private static int unknownFreezeCount = 0;

    private static long lastReportTime = 0;
    // Rate-limit full classification. One per 5 seconds is enough drama.
    private static final long REPORT_RATE_LIMIT_MS = 5000;

    public static void onClientFrameSpike(long frameMs, RecentEventBuffer buffer, boolean isDedicatedServer) {
        if (!SAConfig.INSTANCE.enableClientStutterDetection.get()) return;
        if (frameMs < SAConfig.INSTANCE.minorFrameMs.get()) return;

        // Always count before rate-limit so F3/status stay accurate
        countSilent(frameMs);

        if (System.currentTimeMillis() - lastReportTime < REPORT_RATE_LIMIT_MS) return;

        SafeExecutor.run("FreezeDetector", () -> {
            List<RecentEventBuffer.GameEvent> recent = buffer.recentSeconds(30);
            FreezeEvent event = classifier.classify(frameMs, true, isDedicatedServer,
                MetricsCollector.frameTime(), MetricsCollector.serverTick(), MetricsCollector.memoryGc(), recent);
            handleEvent(event, buffer, frameMs);
        });
    }

    public static void onServerTickSpike(long mspt, RecentEventBuffer buffer, boolean isDedicatedServer) {
        if (!SAConfig.INSTANCE.enableServerTickDetection.get()) return;
        if (mspt < SAConfig.INSTANCE.warningMspt.get()) return;
        if (System.currentTimeMillis() - lastReportTime < REPORT_RATE_LIMIT_MS) return;

        SafeExecutor.run("FreezeDetector", () -> {
            List<RecentEventBuffer.GameEvent> recent = buffer.recentSeconds(30);
            FreezeEvent event = classifier.classify(mspt, false, isDedicatedServer,
                MetricsCollector.frameTime(), MetricsCollector.serverTick(), MetricsCollector.memoryGc(), recent);
            handleEvent(event, buffer, mspt);
        });
    }

    private static void countSilent(long frameMs) {
        int severe = SAConfig.INSTANCE.severeFrameMs.get();
        int medium = SAConfig.INSTANCE.mediumFrameMs.get();
        if (frameMs < medium) {
            StutterCounter.recordMinor(frameMs);
            if (com.stutteranalyzer.core.VerboseMode.isEnabled()) {
                verboseNotificationPending = true;
                verboseNotificationMs = frameMs;
            }
            // aggregate check
            if (SAConfig.INSTANCE.aggregateRepeatedMinorStutters.get()) {
                int window  = SAConfig.INSTANCE.minorStutterAggregateWindowSeconds.get();
                int thresh  = SAConfig.INSTANCE.minorStutterAggregateCount.get();
                long coolMs = SAConfig.INSTANCE.minorStutterAggregateChatCooldownSeconds.get() * 1000L;
                if (StutterCounter.shouldNotifyAggregate(window, thresh, coolMs)) {
                    aggregatePendingNotification = true;
                    aggregatePendingCount  = StutterCounter.minorCountInSeconds(window);
                    aggregatePendingWorstMs = StutterCounter.worstMinorInSeconds(window);
                }
            }
        } else if (frameMs < severe) {
            StutterCounter.recordMedium(frameMs);
            if (com.stutteranalyzer.core.VerboseMode.isEnabled()) {
                verboseNotificationPending = true;
                verboseNotificationMs = frameMs;
            }
        }
    }

    private static boolean shouldSaveReport(long durationMs) {
        int severe = SAConfig.INSTANCE.severeFrameMs.get();
        int extreme = SAConfig.INSTANCE.extremeFrameMs.get();
        int medium = SAConfig.INSTANCE.mediumFrameMs.get();
        if (durationMs >= extreme) return SAConfig.INSTANCE.saveExtremeReports.get();
        if (durationMs >= severe)  return SAConfig.INSTANCE.saveSevereStutterReports.get();
        if (durationMs >= medium)  return SAConfig.INSTANCE.saveMediumStutterReports.get();
        return SAConfig.INSTANCE.saveMinorStutterReports.get();
    }

    private static boolean shouldNotifyChat(long durationMs) {
        int severe = SAConfig.INSTANCE.severeFrameMs.get();
        int extreme = SAConfig.INSTANCE.extremeFrameMs.get();
        int medium = SAConfig.INSTANCE.mediumFrameMs.get();
        if (durationMs >= extreme) return SAConfig.INSTANCE.chatNotifyExtremeFreeze.get();
        if (durationMs >= severe)  return SAConfig.INSTANCE.chatNotifySevereStutters.get();
        if (durationMs >= medium)  return SAConfig.INSTANCE.chatNotifyMediumStutters.get();
        return SAConfig.INSTANCE.chatNotifyMinorStutters.get();
    }

    private static void handleEvent(FreezeEvent event, RecentEventBuffer buffer, long durationMs) {
        lastFreezeEvent = event;
        lastReportTime = System.currentTimeMillis();
        buffer.push(RecentEventBuffer.EventType.FREEZE_DETECTED, event.category().name() + " " + event.durationMs() + "ms");

        // Notify chat only for stutters above the configured threshold
        if (event.category() == FreezeCategory.UNKNOWN_FREEZE && shouldNotifyChat(durationMs)) {
            unknownFreezePendingNotification = true;
            unknownFreezeCount++;
        }

        // Save report only for stutters above the configured threshold
        if (shouldSaveReport(durationMs)) {
            FreezeReport report = FreezeReport.from(event);
            ReportWriter.writeAsync(report);
        }
    }

    // kept for testing injection (injectForTesting always saves the report)
    private static void handleEvent(FreezeEvent event, RecentEventBuffer buffer) {
        handleEvent(event, buffer, event.durationMs());
    }

    public static FreezeEvent lastFreezeEvent() { return lastFreezeEvent; }
    public static int unknownFreezeCount() { return unknownFreezeCount; }

    public static void injectForTesting(FreezeEvent event, RecentEventBuffer buffer) {
        handleEvent(event, buffer);
    }

    /** Simulates a silent stutter for /sa debug test minor/medium. */
    public static void injectSilent(long durationMs) {
        countSilent(durationMs);
    }

    /** Returns true (and clears flag) if an Unknown Freeze was detected since last check. */
    public static boolean consumeUnknownFreezeNotification() {
        if (unknownFreezePendingNotification) {
            unknownFreezePendingNotification = false;
            return true;
        }
        return false;
    }

    /** Returns the pending verbose stutter ms (0 = none) and clears the flag. */
    public static long consumeVerboseNotification() {
        if (verboseNotificationPending) {
            verboseNotificationPending = false;
            return verboseNotificationMs;
        }
        return 0;
    }

    /** Returns [count, worstMs] if an aggregate notification is pending, null otherwise. */
    public static long[] consumeAggregateNotification() {
        if (aggregatePendingNotification) {
            aggregatePendingNotification = false;
            return new long[]{ aggregatePendingCount, aggregatePendingWorstMs };
        }
        return null;
    }
}
