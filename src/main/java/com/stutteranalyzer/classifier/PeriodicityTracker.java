package com.stutteranalyzer.classifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Tracks recent minor unknown events to detect periodic micro-hitch patterns.
 * Uses a 10-minute window with a minimum of 4 occurrences.
 * Thread-safe via synchronized methods.
 */
public class PeriodicityTracker {

    public record PeriodicResult(
        int occurrences,
        long periodMsEstimate,
        boolean isScheduled,
        long scheduledIntervalMs
    ) {}

    private record Entry(long timestampMs, long durationMs) {}

    private static final long WINDOW_MS         = 600_000L; // 10 minutes
    private static final int  MAX_ENTRIES        = 200;
    private static final int  MIN_OCCURRENCES    = 4;
    private static final long DURATION_JITTER_MS = 10L;
    private static final long INTERVAL_JITTER_MS = 3_000L;  // 3 seconds

    private static final long[] SCHEDULER_INTERVALS_MS = {
        5_000L, 10_000L, 30_000L, 60_000L, 120_000L, 300_000L
    };

    private final Deque<Entry> buffer = new ArrayDeque<>();

    public synchronized void record(long durationMs) {
        long now = System.currentTimeMillis();
        pruneOld(now);
        buffer.addLast(new Entry(now, durationMs));
        if (buffer.size() > MAX_ENTRIES) buffer.pollFirst();
    }

    /**
     * Returns a PeriodicResult if >= 4 matching entries exist within 10 minutes
     * with stable intervals. Returns null if not enough data or jitter is too high.
     */
    public synchronized PeriodicResult detect(long durationMs) {
        long now = System.currentTimeMillis();
        pruneOld(now);

        List<Long> timestamps = new ArrayList<>();
        for (Entry e : buffer) {
            if (Math.abs(e.durationMs - durationMs) <= DURATION_JITTER_MS) {
                timestamps.add(e.timestampMs);
            }
        }

        if (timestamps.size() < MIN_OCCURRENCES) return null;

        long[] intervals = new long[timestamps.size() - 1];
        for (int i = 1; i < timestamps.size(); i++) {
            intervals[i - 1] = timestamps.get(i) - timestamps.get(i - 1);
        }

        long sum = 0;
        for (long iv : intervals) sum += iv;
        long avgInterval = sum / intervals.length;

        for (long iv : intervals) {
            if (Math.abs(iv - avgInterval) > INTERVAL_JITTER_MS) return null;
        }

        // Check if the average interval matches a known scheduler period
        for (long schedulerMs : SCHEDULER_INTERVALS_MS) {
            if (Math.abs(avgInterval - schedulerMs) <= INTERVAL_JITTER_MS) {
                return new PeriodicResult(timestamps.size(), avgInterval, true, schedulerMs);
            }
        }

        return new PeriodicResult(timestamps.size(), avgInterval, false, 0L);
    }

    private void pruneOld(long now) {
        while (!buffer.isEmpty() && now - buffer.peekFirst().timestampMs > WINDOW_MS) {
            buffer.pollFirst();
        }
    }
}
