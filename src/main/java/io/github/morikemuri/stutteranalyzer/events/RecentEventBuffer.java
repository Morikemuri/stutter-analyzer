package io.github.morikemuri.stutteranalyzer.events;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Bounded buffer of recent in-game events for freeze context.
 *
 * Retention is time-window first, hard cap second: events are kept by age (default 2h) and the
 * total is capped (default 1000). When the cap is hit, eviction is importance-aware - low-value
 * minor/noisy events are dropped before severe/extreme freeze context. This stops a burst of
 * tiny micro-hitches from erasing the important older context that reports / `/sa show` /
 * `/sa preview` / `/sa submit` rely on (the old fixed-size buffer + blind pollFirst lost it).
 *
 * pruning runs on add and on read. Operations stay light (no I/O, no classification, no logs).
 * Java 17 compatible.
 */
public class RecentEventBuffer {

    public enum EventType {
        WORLD_LOAD, WORLD_UNLOAD, DIMENSION_CHANGE,
        PLAYER_JOIN, PLAYER_LEAVE,
        TELEPORT,
        CHUNK_LOAD, CHUNK_UNLOAD, CHUNK_GENERATION_START, CHUNK_GENERATION_END,
        CHUNK_RENDER_REBUILD,
        RESOURCE_RELOAD_START, RESOURCE_RELOAD_END,
        SHADER_RELOAD_START, SHADER_RELOAD_END,
        DATAPACK_RELOAD_START, DATAPACK_RELOAD_END,
        GC_EVENT, MEMORY_PRESSURE,
        SERVER_SAVE_START, SERVER_SAVE_END,
        SCREEN_OPEN, SCREEN_CLOSE,
        LARGE_ENTITY_COUNT_CHANGE, LARGE_BLOCK_ENTITY_COUNT_CHANGE,
        INTEGRATED_SERVER_START, INTEGRATED_SERVER_STOP,
        NETWORK_SPIKE,
        CONFIG_RELOAD,
        GUARD_TRIGGERED,
        CRASH_IMPORTED,
        REPORT_SUBMISSION_PREPARED,
        FREEZE_DETECTED,
        STUTTER_DETECTED
    }

    public static final class GameEvent {
        public final Instant timestamp;
        public final EventType type;
        public final String detail;

        public GameEvent(EventType type, String detail) {
            this.timestamp = Instant.now();
            this.type = type;
            this.detail = detail;
        }

        GameEvent(EventType type, String detail, Instant timestamp) {
            this.timestamp = timestamp;
            this.type = type;
            this.detail = detail;
        }

        @Override public String toString() {
            return "[" + timestamp + "] " + type.name() + (detail != null && !detail.isEmpty() ? " - " + detail : "");
        }
    }

    // Retention defaults. /sa show parser allows up to 2h, so keep 2h of context; the hard cap
    // keeps memory bounded regardless.
    public static final long DEFAULT_MAX_AGE_SECONDS = 2 * 60 * 60;
    public static final int  DEFAULT_HARD_CAP        = 1000;
    public static final long MIN_MAX_AGE_SECONDS     = 60;
    public static final long MAX_MAX_AGE_SECONDS     = 2 * 60 * 60;
    public static final int  MIN_HARD_CAP            = 100;
    public static final int  MAX_HARD_CAP            = 5000;

    // Conservative duration thresholds (ms) used for importance, kept independent of SAConfig
    // so the buffer has no dependency on config and no cycle.
    private static final long SEVERE_MS  = 200;
    private static final long EXTREME_MS = 1000;

    private final long maxAgeSeconds;
    private final int  hardCap;
    private final Deque<GameEvent> buffer = new ArrayDeque<>();

    public RecentEventBuffer() {
        this(DEFAULT_MAX_AGE_SECONDS, DEFAULT_HARD_CAP);
    }

    public RecentEventBuffer(long maxAgeSeconds, int hardCap) {
        this.maxAgeSeconds = clamp(maxAgeSeconds, MIN_MAX_AGE_SECONDS, MAX_MAX_AGE_SECONDS);
        this.hardCap = (int) clamp(hardCap, MIN_HARD_CAP, MAX_HARD_CAP);
    }

    private static long clamp(long v, long lo, long hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public synchronized void push(EventType type, String detail) {
        Instant now = Instant.now();
        pruneExpired(now);
        buffer.addLast(new GameEvent(type, detail, now));
        enforceHardCap();
    }

    public synchronized List<GameEvent> snapshot() {
        prune(Instant.now());
        return new ArrayList<>(buffer);
    }

    public synchronized List<GameEvent> recentSeconds(int seconds) {
        Instant now = Instant.now();
        prune(now);
        Instant cutoff = now.minusSeconds(seconds);
        List<GameEvent> out = new ArrayList<>();
        for (GameEvent e : buffer) {
            // !isBefore so an event exactly at the cutoff is kept (boundary inclusive).
            if (!e.timestamp.isBefore(cutoff)) out.add(e);
        }
        return out;
    }

    /**
     * Retroactively reclassify STUTTER_DETECTED events whose detail starts with
     * "UNCLASSIFIED_MICRO_HITCH" and whose duration (parsed from detail) is within jitterMs of
     * targetDurationMs. The original timestamp is preserved. Pruning + hard cap are re-applied.
     */
    public synchronized void reclassifyMicroHitches(long targetDurationMs, long jitterMs, String newCategoryName) {
        List<GameEvent> updated = new ArrayList<>(buffer.size());
        boolean changed = false;
        for (GameEvent e : buffer) {
            if (e.type == EventType.STUTTER_DETECTED
                    && e.detail != null
                    && e.detail.startsWith("UNCLASSIFIED_MICRO_HITCH ")) {
                try {
                    String durStr = e.detail.substring("UNCLASSIFIED_MICRO_HITCH ".length()).replace("ms", "").trim();
                    long evDur = Long.parseLong(durStr);
                    if (Math.abs(evDur - targetDurationMs) <= jitterMs) {
                        updated.add(new GameEvent(e.type, newCategoryName + " " + evDur + "ms", e.timestamp));
                        changed = true;
                        continue;
                    }
                } catch (NumberFormatException ignored) {}
            }
            updated.add(e);
        }
        if (changed) {
            buffer.clear();
            buffer.addAll(updated);
        }
        prune(Instant.now());
    }

    public synchronized void clear() { buffer.clear(); }

    // ---- pruning ----

    private void prune(Instant now) {
        pruneExpired(now);
        enforceHardCap();
    }

    private void pruneExpired(Instant now) {
        Instant cutoff = now.minusSeconds(maxAgeSeconds);
        // Events are appended in time order, so the oldest live at the front.
        while (!buffer.isEmpty() && isExpired(buffer.peekFirst(), cutoff)) {
            buffer.pollFirst();
        }
    }

    private static boolean isExpired(GameEvent event, Instant cutoff) {
        return event.timestamp.isBefore(cutoff);
    }

    private void enforceHardCap() {
        int over = buffer.size() - hardCap;
        if (over <= 0) return;
        List<GameEvent> all = new ArrayList<>(buffer);
        // Choose the `over` events to drop: lowest importance first, oldest first among ties.
        List<GameEvent> byDrop = new ArrayList<>(all);
        byDrop.sort((a, b) -> {
            int cmp = Integer.compare(eventImportance(a), eventImportance(b));
            if (cmp != 0) return cmp;
            return a.timestamp.compareTo(b.timestamp);
        });
        Set<GameEvent> drop = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < over && i < byDrop.size(); i++) drop.add(byDrop.get(i));
        buffer.clear();
        for (GameEvent e : all) {
            if (!drop.contains(e)) buffer.addLast(e);
        }
    }

    // ---- importance ----

    private static int eventImportance(GameEvent e) {
        if (e.type == EventType.FREEZE_DETECTED || e.type == EventType.STUTTER_DETECTED) {
            long ms = parseDurationMs(e);
            if (ms >= EXTREME_MS) return 6;   // extreme freeze/stutter
            if (ms >= SEVERE_MS)  return 5;    // severe freeze/stutter
            if (ms >= 100)        return 3;    // medium stutter
            return 2;                          // minor micro-hitch
        }
        switch (e.type) {
            case SERVER_SAVE_START, SERVER_SAVE_END,
                 RESOURCE_RELOAD_START, RESOURCE_RELOAD_END,
                 GC_EVENT, MEMORY_PRESSURE,
                 GUARD_TRIGGERED, CRASH_IMPORTED -> {
                return 4;                      // useful system context
            }
            default -> {
                return 1;                      // noisy low-value events
            }
        }
    }

    private static boolean isSevereOrExtreme(GameEvent e) {
        return parseDurationMs(e) >= SEVERE_MS;
    }

    /** Parse the trailing "<n>ms" duration from a detail like "CATEGORY 123ms"; -1 if absent. */
    private static long parseDurationMs(GameEvent e) {
        if (e == null || e.detail == null) return -1;
        String d = e.detail;
        int ms = d.lastIndexOf("ms");
        if (ms <= 0) return -1;
        int i = ms - 1;
        while (i >= 0 && Character.isDigit(d.charAt(i))) i--;
        if (i + 1 >= ms) return -1;
        try {
            return Long.parseLong(d.substring(i + 1, ms));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    // ---- dev self-check (no test framework available) ----

    /** Lightweight self-test of the retention behaviour. Returns "OK" or the first failure. */
    public static String selfCheck() {
        // 1. Time pruning: an event older than maxAge is dropped by snapshot().
        RecentEventBuffer b1 = new RecentEventBuffer(60, 1000); // 60s window
        Instant old = Instant.now().minusSeconds(120);
        b1.buffer.addLast(new GameEvent(EventType.STUTTER_DETECTED, "X 50ms", old));
        b1.buffer.addLast(new GameEvent(EventType.STUTTER_DETECTED, "X 50ms", Instant.now()));
        if (b1.snapshot().size() != 1) return "FAIL: time pruning";

        // 2. recentSeconds boundary: event exactly at cutoff is included.
        RecentEventBuffer b2 = new RecentEventBuffer(7200, 1000);
        Instant now = Instant.now();
        b2.buffer.addLast(new GameEvent(EventType.STUTTER_DETECTED, "X 50ms", now.minusSeconds(300)));
        if (b2.recentSeconds(300).isEmpty()) return "FAIL: recentSeconds boundary";

        // 3. Hard cap: size never exceeds the cap after push().
        RecentEventBuffer b3 = new RecentEventBuffer(7200, 100);
        for (int i = 0; i < 250; i++) b3.push(EventType.SCREEN_OPEN, null);
        if (b3.snapshot().size() > 100) return "FAIL: hard cap";

        // 4. Reclassification preserves timestamp.
        RecentEventBuffer b4 = new RecentEventBuffer(7200, 1000);
        Instant ts = Instant.now().minusSeconds(10);
        b4.buffer.addLast(new GameEvent(EventType.STUTTER_DETECTED, "UNCLASSIFIED_MICRO_HITCH 70ms", ts));
        b4.reclassifyMicroHitches(70, 5, "PERIODIC_SCHEDULED_MICRO_HITCH");
        GameEvent only = b4.snapshot().get(0);
        if (!only.timestamp.equals(ts)) return "FAIL: reclassify timestamp";
        if (!only.detail.startsWith("PERIODIC_SCHEDULED_MICRO_HITCH")) return "FAIL: reclassify detail";

        // 5. Reclassification still respects hard cap.
        RecentEventBuffer b5 = new RecentEventBuffer(7200, 100);
        for (int i = 0; i < 150; i++) b5.buffer.addLast(new GameEvent(EventType.SCREEN_OPEN, null, Instant.now()));
        b5.reclassifyMicroHitches(70, 5, "X");
        if (b5.snapshot().size() > 100) return "FAIL: reclassify hard cap";

        // 6. Micro-hitch storm: a severe event survives a flood of minor ones under importance eviction.
        RecentEventBuffer b6 = new RecentEventBuffer(7200, 100);
        b6.buffer.addLast(new GameEvent(EventType.FREEZE_DETECTED, "UNKNOWN_EXTREME_FREEZE 1500ms", Instant.now().minusSeconds(5)));
        for (int i = 0; i < 300; i++) b6.push(EventType.STUTTER_DETECTED, "UNCLASSIFIED_MICRO_HITCH 60ms");
        boolean severeKept = false;
        for (GameEvent e : b6.snapshot()) {
            if (e.type == EventType.FREEZE_DETECTED && isSevereOrExtreme(e)) { severeKept = true; break; }
        }
        if (!severeKept) return "FAIL: micro-hitch storm dropped severe event";

        return "OK";
    }
}
