package com.stutteranalyzer.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-safe windowed counters for minor and medium stutters.
 * Updated before the rate-limiter gate so F3/status show real counts.
 * No file I/O, no report generation.
 */
public class StutterCounter {

    private static final long STATUS_WINDOW_MS = 60_000;

    private record Entry(long time, long durationMs) {}

    private static final Deque<Entry> minorEntries  = new ArrayDeque<>();
    private static final Deque<Entry> mediumEntries = new ArrayDeque<>();

    private static long lastMinorMs   = -1;
    private static long lastMinorTime = 0;
    private static long lastMediumMs  = -1;

    private static long lastAggregateChatTime = 0;

    public static synchronized void recordMinor(long durationMs) {
        long now = System.currentTimeMillis();
        lastMinorMs = durationMs;
        lastMinorTime = now;
        minorEntries.addLast(new Entry(now, durationMs));
        prune(minorEntries, now, STATUS_WINDOW_MS);
    }

    public static synchronized void recordMedium(long durationMs) {
        long now = System.currentTimeMillis();
        lastMediumMs = durationMs;
        mediumEntries.addLast(new Entry(now, durationMs));
        prune(mediumEntries, now, STATUS_WINDOW_MS);
    }

    public static synchronized int minorCountInSeconds(int seconds) {
        long cutoff = System.currentTimeMillis() - seconds * 1000L;
        prune(minorEntries, System.currentTimeMillis(), STATUS_WINDOW_MS);
        return (int) minorEntries.stream().filter(e -> e.time() >= cutoff).count();
    }

    public static synchronized int mediumCountInSeconds(int seconds) {
        long cutoff = System.currentTimeMillis() - seconds * 1000L;
        prune(mediumEntries, System.currentTimeMillis(), STATUS_WINDOW_MS);
        return (int) mediumEntries.stream().filter(e -> e.time() >= cutoff).count();
    }

    public static synchronized long worstMinorInSeconds(int seconds) {
        long cutoff = System.currentTimeMillis() - seconds * 1000L;
        return minorEntries.stream()
            .filter(e -> e.time() >= cutoff)
            .mapToLong(Entry::durationMs)
            .max().orElse(0);
    }

    public static synchronized long lastMinorMs()    { return lastMinorMs; }
    public static synchronized long lastMediumMs()   { return lastMediumMs; }
    public static synchronized long lastMinorAgeSecs() {
        return lastMinorTime <= 0 ? -1 : (System.currentTimeMillis() - lastMinorTime) / 1000L;
    }

    /**
     * Returns true if enough minor stutters accumulated in the window and cooldown has passed.
     * Resets the cooldown timer on true.
     */
    public static synchronized boolean shouldNotifyAggregate(int windowSeconds, int threshold, long cooldownMs) {
        long now = System.currentTimeMillis();
        if (now - lastAggregateChatTime < cooldownMs) return false;
        long cutoff = now - windowSeconds * 1000L;
        int count = (int) minorEntries.stream().filter(e -> e.time() >= cutoff).count();
        if (count >= threshold) {
            lastAggregateChatTime = now;
            return true;
        }
        return false;
    }

    private static void prune(Deque<Entry> q, long now, long windowMs) {
        while (!q.isEmpty() && now - q.peekFirst().time() > windowMs) {
            q.pollFirst();
        }
    }
}
