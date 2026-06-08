package com.stutteranalyzer.core;

import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.report.FreezeEvent;

import java.util.concurrent.ConcurrentHashMap;

/** Manages preset-based chat alert system. Thread-safe via volatile + ConcurrentHashMap. */
public class AlertManager {

    public record PendingAlert(FreezeEvent event, long durationMs, boolean reportSaved) {}

    private static volatile PendingAlert pending = null;
    private static volatile long lastGlobalAlertMs = 0;
    private static final ConcurrentHashMap<String, Long> lastCategoryAlertMs = new ConcurrentHashMap<>();
    private static volatile int alertsThisMinute = 0;
    private static volatile long minuteWindowStartMs = 0;

    public static PendingAlert consumePendingAlert() {
        PendingAlert p = pending;
        if (p != null) { pending = null; return p; }
        return null;
    }

    /**
     * Called from FreezeDetector after classifying an event.
     * Queues a chat alert if mode, cooldowns, and rate limits allow.
     */
    public static void checkAndQueue(FreezeEvent event, long durationMs, boolean reportSaved) {
        AlertMode mode = currentMode();
        if (mode == AlertMode.OFF) return;

        int medium  = SAConfig.INSTANCE.mediumFrameMs.get();
        int severe  = SAConfig.INSTANCE.severeFrameMs.get();
        int extreme = SAConfig.INSTANCE.extremeFrameMs.get();

        if (mode.shouldAlertDirect(durationMs, medium, severe, extreme)) {
            if (passesCooldowns(event.category().name())) {
                recordAlert(event.category().name());
                pending = new PendingAlert(event, durationMs, reportSaved);
            }
        }
    }

    private static boolean passesCooldowns(String category) {
        long now = System.currentTimeMillis();
        long globalCoolMs = SAConfig.INSTANCE.alertCooldownSeconds.get() * 1000L;
        if (now - lastGlobalAlertMs < globalCoolMs) return false;

        long catCoolMs = "PERIODIC_SCHEDULED_MICRO_HITCH".equals(category)
            ? SAConfig.INSTANCE.scheduledMicroHitchCooldownSeconds.get() * 1000L
            : SAConfig.INSTANCE.alertSameCategoryCooldownSeconds.get() * 1000L;
        long lastCat = lastCategoryAlertMs.getOrDefault(category, 0L);
        if (now - lastCat < catCoolMs) return false;

        if (now - minuteWindowStartMs >= 60_000L) {
            minuteWindowStartMs = now;
            alertsThisMinute = 0;
        }
        return alertsThisMinute < SAConfig.INSTANCE.alertMaxAlertsPerMinute.get();
    }

    private static void recordAlert(String category) {
        long now = System.currentTimeMillis();
        lastGlobalAlertMs = now;
        lastCategoryAlertMs.put(category, now);
        if (now - minuteWindowStartMs >= 60_000L) {
            minuteWindowStartMs = now;
            alertsThisMinute = 0;
        }
        alertsThisMinute++;
    }

    public static AlertMode currentMode() {
        return AlertMode.fromString(SAConfig.INSTANCE.alertMode.get());
    }
}
