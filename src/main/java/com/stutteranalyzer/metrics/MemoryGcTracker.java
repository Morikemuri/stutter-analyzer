package com.stutteranalyzer.metrics;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;

/**
 * Tracks JVM heap usage and GC pause events.
 */
public class MemoryGcTracker {

    private final MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

    private long lastGcCount = 0;
    private long lastGcTimeMs = 0;
    private long heapUsedBytes = 0;
    private long heapMaxBytes = 0;
    private long lastGcPauseMs = 0;
    private boolean recentGc = false;

    public void sample() {
        heapUsedBytes = memBean.getHeapMemoryUsage().getUsed();
        heapMaxBytes  = memBean.getHeapMemoryUsage().getMax();

        long totalCount = 0, totalTime = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            if (gc.getCollectionCount() >= 0) totalCount += gc.getCollectionCount();
            if (gc.getCollectionTime()  >= 0) totalTime  += gc.getCollectionTime();
        }

        if (totalCount > lastGcCount) {
            lastGcPauseMs = totalTime - lastGcTimeMs;
            recentGc = true;
        } else {
            recentGc = false;
        }
        lastGcCount  = totalCount;
        lastGcTimeMs = totalTime;
    }

    public long heapUsedMb() { return heapUsedBytes / (1024 * 1024); }
    public long heapMaxMb()  { return heapMaxBytes  / (1024 * 1024); }
    public double heapPercent() { return heapMaxBytes > 0 ? 100.0 * heapUsedBytes / heapMaxBytes : 0; }
    public boolean recentGc() { return recentGc; }
    public long lastGcPauseMs() { return lastGcPauseMs; }
}
