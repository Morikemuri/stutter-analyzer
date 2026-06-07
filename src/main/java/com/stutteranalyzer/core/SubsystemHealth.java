package com.stutteranalyzer.core;

import java.util.LinkedHashMap;
import java.util.Map;

public class SubsystemHealth {

    public enum Status { OK, DEGRADED, DISABLED, FAILED }

    private static final Map<String, Status> statuses = new LinkedHashMap<>();
    private static final Map<String, String> notes = new LinkedHashMap<>();

    static {
        register("FrameTimeTracker",          Status.OK, null);
        register("ServerTickTracker",         Status.OK, null);
        register("MemoryGcTracker",           Status.OK, null);
        register("RecentEventBuffer",         Status.OK, null);
        register("ReportWriter",              Status.OK, null);
        register("ChunkEventTracker",         Status.OK, null);
        register("KnownPatternDetector",      Status.OK, null);
        register("CrashReportImporter",       Status.OK, null);
        register("EmergencyGuardManager",     Status.OK, null);
        register("SubmissionManager",         Status.OK, null);
        register("ModInventory",              Status.OK, null);
        register("OverlayRenderer",           Status.OK, null);
    }

    public static void register(String name, Status status, String note) {
        statuses.put(name, status);
        if (note != null) notes.put(name, note);
    }

    public static void setStatus(String name, Status status, String note) {
        statuses.put(name, status);
        if (note != null) notes.put(name, note);
        else notes.remove(name);
    }

    public static Map<String, Status> all() {
        return statuses;
    }

    public static String note(String name) {
        return notes.getOrDefault(name, "");
    }

    public static boolean anyDegraded() {
        return statuses.values().stream().anyMatch(s -> s != Status.OK);
    }
}
