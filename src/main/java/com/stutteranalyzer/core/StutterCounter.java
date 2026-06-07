package com.stutteranalyzer.core;

import com.stutteranalyzer.config.SAConfig;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-safe windowed counters for minor, medium, and severe stutters.
 * Tracks both raw frame counts and episode counts (continuous bad-frame periods).
 * Updated before the rate-limiter gate so F3/status show real counts.
 * No file I/O, no report generation.
 */
public class StutterCounter {

    private static final long STATUS_WINDOW_MS = 60_000;

    private record Entry(long time, long durationMs) {}

    // Raw frame entries
    private static final Deque<Entry> minorEntries  = new ArrayDeque<>();
    private static final Deque<Entry> mediumEntries = new ArrayDeque<>();
    private static final Deque<Entry> severeEntries = new ArrayDeque<>();

    // Episode timestamps (one entry per episode start)
    private static final Deque<Long> minorEpisodes  = new ArrayDeque<>();
    private static final Deque<Long> mediumEpisodes = new ArrayDeque<>();

    // Last stutter timestamps for episode boundary detection
    private static long lastMinorEpisodeTs  = 0;
    private static long lastMediumEpisodeTs = 0;

    private static long lastMinorMs   = -1;
    private static long lastMinorTime = 0;
    private static long lastMediumMs  = -1;
    private static long lastSevereMs  = -1;

    private static long lastAggregateChatTime = 0;

    public static synchronized void recordMinor(long durationMs) {
        long now = System.currentTimeMillis();
        lastMinorMs = durationMs;
        lastMinorTime = now;
        minorEntries.addLast(new Entry(now, durationMs));
        prune(minorEntries, now, STATUS_WINDOW_MS);
        // Episode: new episode if gap since last stutter exceeds threshold
        long gapMs = SAConfig.INSTANCE.episodeGapMs.get();
        if (now - lastMinorEpisodeTs > gapMs) {
            minorEpisodes.addLast(now);
            pruneEpisodes(minorEpisodes, now, STATUS_WINDOW_MS);
        }
        lastMinorEpisodeTs = now;
    }

    public static synchronized void recordMedium(long durationMs) {
        long now = System.currentTimeMillis();
        lastMediumMs = durationMs;
        mediumEntries.addLast(new Entry(now, durationMs));
        prune(mediumEntries, now, STATUS_WINDOW_MS);
        long gapMs = SAConfig.INSTANCE.episodeGapMs.get();
        if (now - lastMediumEpisodeTs > gapMs) {
            mediumEpisodes.addLast(now);
            pruneEpisodes(mediumEpisodes, now, STATUS_WINDOW_MS);
        }
        lastMediumEpisodeTs = now;
    }

    public static synchronized void recordSevere(long durationMs) {
        long now = System.currentTimeMillis();
        lastSevereMs = durationMs;
        severeEntries.addLast(new Entry(now, durationMs));
        prune(severeEntries, now, STATUS_WINDOW_MS);
    }

    // Raw frame counts
    public static synchronized int minorCountInSeconds(int seconds) {
        long now = System.currentTimeMillis();
        long cutoff = now - seconds * 1000L;
        prune(minorEntries, now, STATUS_WINDOW_MS);
        return (int) minorEntries.stream().filter(e -> e.time() >= cutoff).count();
    }

    public static synchronized int mediumCountInSeconds(int seconds) {
        long now = System.currentTimeMillis();
        long cutoff = now - seconds * 1000L;
        prune(mediumEntries, now, STATUS_WINDOW_MS);
        return (int) mediumEntries.stream().filter(e -> e.time() >= cutoff).count();
    }

    public static synchronized int severeCountInSeconds(int seconds) {
        long now = System.currentTimeMillis();
        long cutoff = now - seconds * 1000L;
        prune(severeEntries, now, STATUS_WINDOW_MS);
        return (int) severeEntries.stream().filter(e -> e.time() >= cutoff).count();
    }

    // Episode counts
    public static synchronized int minorEpisodeCountInSeconds(int seconds) {
        long now = System.currentTimeMillis();
        long cutoff = now - seconds * 1000L;
        pruneEpisodes(minorEpisodes, now, STATUS_WINDOW_MS);
        return (int) minorEpisodes.stream().filter(t -> t >= cutoff).count();
    }

    public static synchronized int mediumEpisodeCountInSeconds(int seconds) {
        long now = System.currentTimeMillis();
        long cutoff = now - seconds * 1000L;
        pruneEpisodes(mediumEpisodes, now, STATUS_WINDOW_MS);
        return (int) mediumEpisodes.stream().filter(t -> t >= cutoff).count();
    }

    // Worst values
    public static synchronized long worstMinorInSeconds(int seconds) {
        long cutoff = System.currentTimeMillis() - seconds * 1000L;
        return minorEntries.stream()
            .filter(e -> e.time() >= cutoff)
            .mapToLong(Entry::durationMs)
            .max().orElse(0);
    }

    public static synchronized long worstMediumInSeconds(int seconds) {
        long cutoff = System.currentTimeMillis() - seconds * 1000L;
        return mediumEntries.stream()
            .filter(e -> e.time() >= cutoff)
            .mapToLong(Entry::durationMs)
            .max().orElse(0);
    }

    public static synchronized long worstSevereInSeconds(int seconds) {
        long cutoff = System.currentTimeMillis() - seconds * 1000L;
        return severeEntries.stream()
            .filter(e -> e.time() >= cutoff)
            .mapToLong(Entry::durationMs)
            .max().orElse(0);
    }

    public static synchronized long lastMinorMs()    { return lastMinorMs; }
    public static synchronized long lastMediumMs()   { return lastMediumMs; }
    public static synchronized long lastSevereMs()   { return lastSevereMs; }
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

    /** Remaining cooldown seconds until next aggregate chat is allowed. -1 if ready now. */
    public static synchronized long aggregateCooldownRemainingSeconds() {
        long cooldownMs = SAConfig.INSTANCE.minorAggregateChatCooldownSeconds.get() * 1000L;
        long elapsed = System.currentTimeMillis() - lastAggregateChatTime;
        long remaining = cooldownMs - elapsed;
        return remaining > 0 ? remaining / 1000L : -1;
    }

    private static void prune(Deque<Entry> q, long now, long windowMs) {
        while (!q.isEmpty() && now - q.peekFirst().time() > windowMs) {
            q.pollFirst();
        }
    }

    private static void pruneEpisodes(Deque<Long> q, long now, long windowMs) {
        while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
            q.pollFirst();
        }
    }
}
