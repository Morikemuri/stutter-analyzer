package com.stutteranalyzer.metrics;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tracks client-side frame times. Computes average FPS, 1% low, 0.1% low, variance, and peak.
 * Ring buffer capped at 3600 samples (~60s at 60fps).
 * Keep this cheap. The render thread is already carrying the modpack like a tired donkey.
 */
public class FrameTimeTracker {

    private static final int MAX_SAMPLES = 3600;

    private final Deque<Long> samples = new ArrayDeque<>(MAX_SAMPLES);
    private long lastNano = -1;

    private double currentFps = 0;
    private double averageFps = 0;
    private double currentFrameMs = 0;
    private double peakFrameMs = 0;
    private double onePercentLow = 0;
    private double pointOnePercentLow = 0;
    private double variance = 0;

    public void onClientTick() {
        long now = System.nanoTime();
        if (lastNano > 0) {
            long deltaMs = (now - lastNano) / 1_000_000L;
            if (deltaMs > 0 && deltaMs < 60_000L) {
                record(deltaMs);
            }
        }
        lastNano = now;
    }

    private void record(long frameMs) {
        if (samples.size() >= MAX_SAMPLES) samples.pollFirst();
        samples.addLast(frameMs);

        currentFrameMs = frameMs;
        if (frameMs > peakFrameMs) peakFrameMs = frameMs;

        currentFps = frameMs > 0 ? 1000.0 / frameMs : 0;
        computeStats();
    }

    private void computeStats() {
        if (samples.isEmpty()) return;
        long[] arr = samples.stream().mapToLong(Long::longValue).sorted().toArray();

        double sum = 0;
        for (long v : arr) sum += v;
        double mean = sum / arr.length;
        averageFps = mean > 0 ? 1000.0 / mean : 0;

        double varianceSum = 0;
        for (long v : arr) varianceSum += (v - mean) * (v - mean);
        variance = varianceSum / arr.length;

        int onePct = Math.max(0, arr.length - Math.max(1, arr.length / 100) - 1);
        int pointOnePct = Math.max(0, arr.length - Math.max(1, arr.length / 1000) - 1);
        onePercentLow = arr[onePct] > 0 ? 1000.0 / arr[onePct] : 0;
        pointOnePercentLow = arr[pointOnePct] > 0 ? 1000.0 / arr[pointOnePct] : 0;
    }

    public double currentFps() { return currentFps; }
    public double averageFps() { return averageFps; }
    public double currentFrameMs() { return currentFrameMs; }
    public double peakFrameMs() { return peakFrameMs; }
    public double onePercentLow() { return onePercentLow; }
    public double pointOnePercentLow() { return pointOnePercentLow; }
    public double variance() { return variance; }
    public int sampleCount() { return samples.size(); }
}
