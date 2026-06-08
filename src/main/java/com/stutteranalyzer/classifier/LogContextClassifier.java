package com.stutteranalyzer.classifier;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Secondary classifier that reads recent log content to identify contributing
 * context when the primary metrics-based classifier cannot determine a cause.
 * Only runs for UNKNOWN_FREEZE events >= 250 ms.
 * Reads last 128 KB of latest.log - fast and non-blocking on background thread.
 */
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
            Path logFile = resolveLogFile();
            if (logFile == null || !Files.exists(logFile)) return ContextResult.empty();

            long fileSize = Files.size(logFile);
            long readOffset = Math.max(0, fileSize - 131072L); // last 128 KB
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

    private static Path resolveLogFile() {
        try {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                try {
                    return net.minecraft.client.Minecraft.getInstance()
                        .gameDirectory.toPath().resolve("logs/latest.log");
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return FMLPaths.GAMEDIR.get().resolve("logs/latest.log");
    }
}
