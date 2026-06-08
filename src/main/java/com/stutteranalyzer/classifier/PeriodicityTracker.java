package com.stutteranalyzer.classifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Tracks recent minor unknown events to detect periodic micro-hitch patterns.
 * Thread-safe via synchronized methods.
 */
public class PeriodicityTracker {

    public record PeriodicResult(int occurrences, long periodMsEstimate) {}

    private record Entry(long timestampMs, long durationMs) {}

    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_ENTRIES = 30;

    private final Deque<Entry> buffer = new ArrayDeque<>();

    /** Record a minor unknown event. Call before periodicity check. */
    public synchronized void record(long durationMs) {
        long now = System.currentTimeMillis();
        pruneOld(now);
        buffer.addLast(new Entry(now, durationMs));
        if (buffer.size() > MAX_ENTRIES) buffer.pollFirst();
    }

    /**
     * Check if recent entries show a periodic pattern for the given duration.
     * Returns null if not enough matching events or jitter is too high.
     * Criteria: >= 3 entries within 10ms of durationMs, interval jitter <= 500ms.
     */
    public synchronized PeriodicResult detect(long durationMs) {
        long now = System.currentTimeMillis();
        pruneOld(now);

        List<Long> timestamps = new ArrayList<>();
        for (Entry e : buffer) {
            if (Math.abs(e.durationMs - durationMs) <= 10) {
                timestamps.add(e.timestampMs);
            }
        }

        if (timestamps.size() < 3) return null;

        // Compute intervals between consecutive matching entries
        long[] intervals = new long[timestamps.size() - 1];
        for (int i = 1; i < timestamps.size(); i++) {
            intervals[i - 1] = timestamps.get(i) - timestamps.get(i - 1);
        }

        long sum = 0;
        for (long iv : intervals) sum += iv;
        long avgInterval = sum / intervals.length;

        // Reject if any interval deviates more than 500ms from average
        for (long iv : intervals) {
            if (Math.abs(iv - avgInterval) > 500) return null;
        }

        return new PeriodicResult(timestamps.size(), avgInterval);
    }

    private void pruneOld(long now) {
        while (!buffer.isEmpty() && now - buffer.peekFirst().timestampMs > WINDOW_MS) {
            buffer.pollFirst();
        }
    }
}
