package com.stutteranalyzer.classifier;

import com.stutteranalyzer.SAEnvironment;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class LogContextClassifier {

    public record ContextResult(
        boolean serverTickSpikeEvidence,
        boolean watchdogEvidence,
        boolean c2meWorldgenEvidence,
        boolean gcPauseEvidence,
        int evidenceScore
    ) {
        public boolean hasStrongServerTickEvidence() {
            return serverTickSpikeEvidence || watchdogEvidence;
        }
        public static ContextResult empty() {
            return new ContextResult(false, false, false, false, 0);
        }
    }

    private static final String[] SERVER_TICK_PATTERNS = {
        "can't keep up", "server overloaded", "server tick spike",
        "tick duration exceeded", "skipping tick", "running behind"
    };

    private static final String[] WATCHDOG_PATTERNS = {
        "watchdog", "tick duration exceeded", "server hang"
    };

    private static final String[] C2ME_WORLDGEN_PATTERNS = {
        "c2me", "thread pool saturated", "worldgen", "queueing chunks",
        "chunk queue", "thread contention", "chunk batch", "chunk loading",
        "chunk generation", "distant horizons", "lod generation",
        "region r.", "loaded chunks"
    };

    private static final String[] GC_PAUSE_PATTERNS = {
        "gc pause", "g1gc", "mixed collection", "gc overhead",
        "outofmemoryerror", "out of memory", "memory pressure",
        "garbage collection", "concurrent mark"
    };

    public static ContextResult detectContext() {
        try {
            Path logFile = SAEnvironment.getLogFile();
            if (logFile == null || !Files.exists(logFile)) return ContextResult.empty();

            long fileSize = Files.size(logFile);
            long readOffset = Math.max(0, fileSize - 131072L);
            byte[] buf;
            try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                raf.seek(readOffset);
                int len = (int) (fileSize - readOffset);
                buf = new byte[len];
                raf.readFully(buf);
            }

            String tail = new String(buf, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);

            boolean serverTick = matchesAny(tail, SERVER_TICK_PATTERNS);
            boolean watchdog   = matchesAny(tail, WATCHDOG_PATTERNS);
            boolean c2me       = matchesAny(tail, C2ME_WORLDGEN_PATTERNS);
            boolean gc         = matchesAny(tail, GC_PAUSE_PATTERNS);

            int score = (serverTick ? 1 : 0) + (watchdog ? 1 : 0) + (c2me ? 1 : 0) + (gc ? 1 : 0);
            return new ContextResult(serverTick, watchdog, c2me, gc, score);
        } catch (Exception e) {
            return ContextResult.empty();
        }
    }

    private static boolean matchesAny(String text, String[] patterns) {
        for (String p : patterns) {
            if (text.contains(p)) return true;
        }
        return false;
    }
}
