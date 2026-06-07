package com.stutteranalyzer.classifier;

import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.MetricsCollector;
import com.stutteranalyzer.core.SafeExecutor;
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

    private static long lastReportTime = 0;
    // Rate-limit reports. One freeze every 5 seconds is enough drama.
    private static final long REPORT_RATE_LIMIT_MS = 5000;

    public static void onClientFrameSpike(long frameMs, RecentEventBuffer buffer, boolean isDedicatedServer) {
        if (!SAConfig.INSTANCE.enableClientStutterDetection.get()) return;
        if (frameMs < SAConfig.INSTANCE.minorFrameMs.get()) return;
        if (System.currentTimeMillis() - lastReportTime < REPORT_RATE_LIMIT_MS) return;

        SafeExecutor.run("FreezeDetector", () -> {
            List<RecentEventBuffer.GameEvent> recent = buffer.recentSeconds(30);
            FreezeEvent event = classifier.classify(frameMs, true, isDedicatedServer,
                MetricsCollector.frameTime(), MetricsCollector.serverTick(), MetricsCollector.memoryGc(), recent);
            handleEvent(event, buffer);
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
            handleEvent(event, buffer);
        });
    }

    private static void handleEvent(FreezeEvent event, RecentEventBuffer buffer) {
        lastFreezeEvent = event;
        lastReportTime = System.currentTimeMillis();
        buffer.push(RecentEventBuffer.EventType.FREEZE_DETECTED, event.category().name() + " " + event.durationMs() + "ms");
        if (event.category() == FreezeCategory.UNKNOWN_FREEZE) {
            unknownFreezePendingNotification = true;
        }
        FreezeReport report = FreezeReport.from(event);
        ReportWriter.writeAsync(report);
    }

    public static FreezeEvent lastFreezeEvent() { return lastFreezeEvent; }

    /** Returns true (and clears the flag) if an Unknown Freeze was detected since last check. */
    public static boolean consumeUnknownFreezeNotification() {
        if (unknownFreezePendingNotification) {
            unknownFreezePendingNotification = false;
            return true;
        }
        return false;
    }
}
