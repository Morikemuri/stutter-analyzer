package com.stutteranalyzer.classifier;

import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.AlertManager;
import com.stutteranalyzer.core.AnalyzerRuntimeState;
import com.stutteranalyzer.core.MetricsCollector;
import com.stutteranalyzer.core.SafeExecutor;
import com.stutteranalyzer.core.StutterCounter;
import com.stutteranalyzer.core.VerboseMode;
import com.stutteranalyzer.events.RecentEventBuffer;
import com.stutteranalyzer.report.FreezeEvent;
import com.stutteranalyzer.report.FreezeReport;
import com.stutteranalyzer.report.ReportWriter;

import java.util.List;

/**
 * Entry point for freeze detection on both client and server.
 *
 * Pipeline (always in this order, no early returns before step 4):
 *   1. Detect stutter above minor threshold.
 *   2. Update AnalyzerRuntimeState (live state for F3 and /sa status).
 *   3. Update StutterCounter (windowed counts for F3 display).
 *   4. Rate-limit expensive operations.
 *   5. Full classification, report saving, chat notification.
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
    private static final long REPORT_RATE_LIMIT_MS = 5000;

    public static void onClientFrameSpike(long frameMs, RecentEventBuffer buffer, boolean isDedicatedServer) {
        if (!SAConfig.INSTANCE.enableClientStutterDetection.get()) return;
        // Strictly above threshold: normal 50ms ticks are NOT stutters
        if (frameMs <= SAConfig.INSTANCE.minorFrameMs.get()) return;

        // Step 1-3: Always update live state and counters - no rate limit
        String severity = classifySeverityString(frameMs);
        AnalyzerRuntimeState.recordStutter(frameMs, severity);
        recordToCounter(frameMs);
        updateVerbosePending(frameMs);

        if (SAConfig.INSTANCE.logDetectionPipeline.get()) {
            StutterAnalyzerMod.LOGGER.info("[SA DEBUG] Real client frame spike detected: {}ms severity={}", frameMs, severity);
        }

        // Step 4: Rate-limit minor/medium. Severe/extreme always go through.
        boolean isSevereOrExtreme = frameMs >= SAConfig.INSTANCE.severeFrameMs.get();
        if (!isSevereOrExtreme && System.currentTimeMillis() - lastReportTime < REPORT_RATE_LIMIT_MS) {
            if (SAConfig.INSTANCE.logDetectionPipeline.get()) {
                StutterAnalyzerMod.LOGGER.info("[SA DEBUG] Full classification rate-limited ({}/{}ms)",
                    System.currentTimeMillis() - lastReportTime, REPORT_RATE_LIMIT_MS);
            }
            return;
        }

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
        boolean isSevereOrExtreme = mspt >= SAConfig.INSTANCE.severeFrameMs.get();
        if (!isSevereOrExtreme && System.currentTimeMillis() - lastReportTime < REPORT_RATE_LIMIT_MS) return;

        SafeExecutor.run("FreezeDetector", () -> {
            List<RecentEventBuffer.GameEvent> recent = buffer.recentSeconds(30);
            FreezeEvent event = classifier.classify(mspt, false, isDedicatedServer,
                MetricsCollector.frameTime(), MetricsCollector.serverTick(), MetricsCollector.memoryGc(), recent);
            handleEvent(event, buffer, mspt);
        });
    }

    /** Record to the appropriate StutterCounter tier. */
    private static void recordToCounter(long frameMs) {
        int medium  = SAConfig.INSTANCE.mediumFrameMs.get();
        int severe  = SAConfig.INSTANCE.severeFrameMs.get();
        int extreme = SAConfig.INSTANCE.extremeFrameMs.get();
        if (frameMs < medium) {
            StutterCounter.recordMinor(frameMs);
            checkAggregateThreshold(frameMs);
        } else if (frameMs < severe) {
            StutterCounter.recordMedium(frameMs);
        } else if (frameMs < extreme) {
            StutterCounter.recordSevere(frameMs);
        } else {
            StutterCounter.recordExtreme(frameMs);
        }
    }

    /** Set verbose notification flag if verbose mode is on. */
    private static void updateVerbosePending(long frameMs) {
        if (VerboseMode.isEnabled()) {
            verboseNotificationPending = true;
            verboseNotificationMs = frameMs;
        }
    }

    /** Check aggregate minor stutter threshold for chat notification. */
    private static void checkAggregateThreshold(long frameMs) {
        if (SAConfig.INSTANCE.aggregateRepeatedMinorStutters.get()) {
            int window  = SAConfig.INSTANCE.minorStutterAggregateWindowSeconds.get();
            int thresh  = SAConfig.INSTANCE.minorStutterAggregateCount.get();
            long coolMs = SAConfig.INSTANCE.minorStutterAggregateChatCooldownSeconds.get() * 1000L;
            if (StutterCounter.shouldNotifyAggregate(window, thresh, coolMs)) {
                aggregatePendingNotification = true;
                aggregatePendingCount   = StutterCounter.minorCountInSeconds(window);
                aggregatePendingWorstMs = StutterCounter.worstMinorInSeconds(window);
            }
        }
    }

    private static String classifySeverityString(long frameMs) {
        int severe  = SAConfig.INSTANCE.severeFrameMs.get();
        int extreme = SAConfig.INSTANCE.extremeFrameMs.get();
        int medium  = SAConfig.INSTANCE.mediumFrameMs.get();
        if (frameMs >= extreme) return "extreme";
        if (frameMs >= severe)  return "severe";
        if (frameMs >= medium)  return "medium";
        return "minor";
    }

    private static boolean shouldSaveReport(long durationMs) {
        int severe  = SAConfig.INSTANCE.severeFrameMs.get();
        int extreme = SAConfig.INSTANCE.extremeFrameMs.get();
        int medium  = SAConfig.INSTANCE.mediumFrameMs.get();
        if (durationMs >= extreme) return SAConfig.INSTANCE.saveExtremeReports.get();
        if (durationMs >= severe)  return SAConfig.INSTANCE.saveSevereStutterReports.get();
        if (durationMs >= medium)  return SAConfig.INSTANCE.saveMediumStutterReports.get();
        return SAConfig.INSTANCE.saveMinorStutterReports.get();
    }

    private static boolean shouldNotifyChat(long durationMs) {
        int severe  = SAConfig.INSTANCE.severeFrameMs.get();
        int extreme = SAConfig.INSTANCE.extremeFrameMs.get();
        int medium  = SAConfig.INSTANCE.mediumFrameMs.get();
        if (durationMs >= extreme) return SAConfig.INSTANCE.chatNotifyExtremeFreeze.get();
        if (durationMs >= severe)  return SAConfig.INSTANCE.chatNotifySevereStutters.get();
        if (durationMs >= medium)  return SAConfig.INSTANCE.chatNotifyMediumStutters.get();
        return SAConfig.INSTANCE.chatNotifyMinorStutters.get();
    }

    private static void handleEvent(FreezeEvent event, RecentEventBuffer buffer, long durationMs) {
        lastFreezeEvent = event;
        lastReportTime  = System.currentTimeMillis();
        buffer.push(RecentEventBuffer.EventType.FREEZE_DETECTED, event.category().name() + " " + event.durationMs() + "ms");

        if (SAConfig.INSTANCE.logDetectionPipeline.get()) {
            StutterAnalyzerMod.LOGGER.info("[SA DEBUG] Event classified: {} {}ms", event.category().name(), event.durationMs());
        }

        if (event.category() == FreezeCategory.UNKNOWN_FREEZE) {
            unknownFreezeCount++;
        }

        boolean reportSaved = shouldSaveReport(durationMs);
        if (reportSaved) {
            FreezeReport report = FreezeReport.from(event);
            ReportWriter.writeAsync(report);
            if (SAConfig.INSTANCE.logDetectionPipeline.get()) {
                StutterAnalyzerMod.LOGGER.info("[SA DEBUG] Report saved for {}ms", durationMs);
            }
        } else if (SAConfig.INSTANCE.logDetectionPipeline.get()) {
            StutterAnalyzerMod.LOGGER.info("[SA DEBUG] Report skipped: below threshold for {}ms", durationMs);
        }

        // Queue chat alert via AlertManager (handles all categories, cooldowns, and mode check)
        AlertManager.checkAndQueue(event, durationMs, reportSaved);
    }

    private static void handleEvent(FreezeEvent event, RecentEventBuffer buffer) {
        handleEvent(event, buffer, event.durationMs());
    }

    public static FreezeEvent lastFreezeEvent() { return lastFreezeEvent; }
    public static int unknownFreezeCount() { return unknownFreezeCount; }

    /**
     * Inject a freeze event for testing (severe/extreme). Goes through full pipeline.
     * Updates AnalyzerRuntimeState, StutterCounter, report saving, and chat.
     */
    public static void injectForTesting(FreezeEvent event, RecentEventBuffer buffer) {
        long durationMs = event.durationMs();
        String severity = classifySeverityString(durationMs);
        AnalyzerRuntimeState.recordStutter(durationMs, severity);
        if (durationMs >= SAConfig.INSTANCE.extremeFrameMs.get()) {
            StutterCounter.recordExtreme(durationMs);
        } else {
            StutterCounter.recordSevere(durationMs);
        }
        handleEvent(event, buffer);
    }

    /**
     * Inject a silent stutter for testing (minor/medium). Updates live state and counters.
     * Does NOT save a report. Does NOT go through the rate limiter.
     */
    public static void injectSilent(long durationMs) {
        String severity = classifySeverityString(durationMs);
        AnalyzerRuntimeState.recordStutter(durationMs, severity);
        recordToCounter(durationMs);
        updateVerbosePending(durationMs);
        if (SAConfig.INSTANCE.logDetectionPipeline.get()) {
            StutterAnalyzerMod.LOGGER.info("[SA DEBUG] Injected silent stutter: {}ms severity={}", durationMs, severity);
        }
    }

    public static boolean consumeUnknownFreezeNotification() {
        if (unknownFreezePendingNotification) {
            unknownFreezePendingNotification = false;
            return true;
        }
        return false;
    }

    public static long consumeVerboseNotification() {
        if (verboseNotificationPending) {
            verboseNotificationPending = false;
            return verboseNotificationMs;
        }
        return 0;
    }

    public static long[] consumeAggregateNotification() {
        if (aggregatePendingNotification) {
            aggregatePendingNotification = false;
            return new long[]{ aggregatePendingCount, aggregatePendingWorstMs };
        }
        return null;
    }
}
