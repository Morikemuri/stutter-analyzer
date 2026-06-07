package com.stutteranalyzer.metrics;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tracks server tick durations (MSPT) and estimates TPS.
 * Ring buffer capped at 1200 ticks (~60s at 20 TPS).
 */
public class ServerTickTracker {

    private static final int MAX_SAMPLES = 1200;

    private final Deque<Long> samples = new ArrayDeque<>(MAX_SAMPLES);
    private double currentMspt = 0;
    private double rollingAvgMspt = 0;
    private double peakMspt = 0;
    private double tpsEstimate = 20.0;

    public void record(long tickNanos) {
        double ms = tickNanos / 1_000_000.0;
        currentMspt = ms;
        if (ms > peakMspt) peakMspt = ms;

        if (samples.size() >= MAX_SAMPLES) samples.pollFirst();
        samples.addLast((long) ms);

        computeStats();
    }

    private void computeStats() {
        if (samples.isEmpty()) return;
        double sum = samples.stream().mapToLong(Long::longValue).sum();
        rollingAvgMspt = sum / samples.size();
        tpsEstimate = rollingAvgMspt > 0 ? Math.min(20.0, 1000.0 / rollingAvgMspt) : 20.0;
    }

    public double currentMspt() { return currentMspt; }
    public double rollingAvgMspt() { return rollingAvgMspt; }
    public double peakMspt() { return peakMspt; }
    public double tpsEstimate() { return tpsEstimate; }
    public int sampleCount() { return samples.size(); }
}
