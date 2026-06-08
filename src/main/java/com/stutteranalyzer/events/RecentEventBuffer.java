package com.stutteranalyzer.events;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bounded ring buffer of recent in-game events for freeze context.
 * Used to explain what happened shortly before a freeze.
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

    private static final int MAX_EVENTS = 512;
    private final Deque<GameEvent> buffer = new ArrayDeque<>(MAX_EVENTS);

    public synchronized void push(EventType type, String detail) {
        if (buffer.size() >= MAX_EVENTS) buffer.pollFirst();
        buffer.addLast(new GameEvent(type, detail));
    }

    public synchronized List<GameEvent> snapshot() {
        return new ArrayList<>(buffer);
    }

    public synchronized List<GameEvent> recentSeconds(int seconds) {
        Instant cutoff = Instant.now().minusSeconds(seconds);
        List<GameEvent> out = new ArrayList<>();
        for (GameEvent e : buffer) {
            if (e.timestamp.isAfter(cutoff)) out.add(e);
        }
        return out;
    }

    /**
     * Retroactively reclassify STUTTER_DETECTED events whose detail starts with
     * "UNCLASSIFIED_MICRO_HITCH" and whose duration (parsed from detail) is within
     * jitterMs of targetDurationMs. Replaces the detail prefix with newCategoryName.
     * Called when a periodic pattern is first confirmed.
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
    }

    public synchronized void clear() { buffer.clear(); }
}
