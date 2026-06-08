package com.stutteranalyzer.core;

import com.stutteranalyzer.events.RecentEventBuffer;
import com.stutteranalyzer.metrics.FrameTimeTracker;
import com.stutteranalyzer.metrics.MemoryGcTracker;
import com.stutteranalyzer.metrics.ServerTickTracker;

public class MetricsCollector {

    private static final FrameTimeTracker frameTime = new FrameTimeTracker();
    private static final ServerTickTracker serverTick = new ServerTickTracker();
    private static final MemoryGcTracker memoryGc = new MemoryGcTracker();
    private static final RecentEventBuffer eventBuffer = new RecentEventBuffer();

    public static FrameTimeTracker frameTime() { return frameTime; }
    public static ServerTickTracker serverTick() { return serverTick; }
    public static MemoryGcTracker memoryGc() { return memoryGc; }
    public static RecentEventBuffer eventBuffer() { return eventBuffer; }

    public static void onClientTick() {
        SafeExecutor.run("FrameTimeTracker", frameTime::onClientTick);
        SafeExecutor.run("MemoryGcTracker", memoryGc::sample);
    }

    public static void onServerTick(long tickDurationNanos) {
        SafeExecutor.run("ServerTickTracker", () -> serverTick.record(tickDurationNanos));
        SafeExecutor.run("MemoryGcTracker", memoryGc::sample);
    }
}
