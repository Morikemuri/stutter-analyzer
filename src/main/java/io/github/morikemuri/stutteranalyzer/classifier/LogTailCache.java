package io.github.morikemuri.stutteranalyzer.classifier;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Shared, TTL-cached tail of latest.log.
 *
 * Three classifiers used to each read the log off disk on the tick/render thread every
 * time a freeze was detected (~32K + ~64K + ~128K per spike). During a stutter burst that
 * made Stutter Analyzer a stutter source itself. Now they share one throttled read: the
 * tail is fetched at most once per TTL and reused, so rapid consecutive spikes cost no I/O.
 *
 * The caller passes the resolved log Path (resolution differs by loader and is cheap); the
 * expensive disk read is what we gate behind the TTL.
 */
public final class LogTailCache {

    // Largest window any classifier needs; we read this once and callers take a suffix.
    private static final long MAX_TAIL_BYTES = 131072L;
    private static final long TTL_MS = 3000L;

    private static volatile String cachedTail = "";
    private static volatile long cachedAt = 0L;

    private LogTailCache() {}

    /** Lowercased tail of the log (up to MAX_TAIL_BYTES), refreshed at most once per TTL. */
    public static String tailLower(Path logFile) {
        long now = System.currentTimeMillis();
        if (now - cachedAt < TTL_MS) return cachedTail;
        synchronized (LogTailCache.class) {
            now = System.currentTimeMillis();
            if (now - cachedAt < TTL_MS) return cachedTail;
            cachedTail = readTailLower(logFile);
            cachedAt = now;
            return cachedTail;
        }
    }

    /** Lowercased tail trimmed to roughly the last {@code maxBytes} characters. */
    public static String tailLower(Path logFile, int maxBytes) {
        String tail = tailLower(logFile);
        if (maxBytes <= 0 || tail.length() <= maxBytes) return tail;
        return tail.substring(tail.length() - maxBytes);
    }

    private static String readTailLower(Path logFile) {
        try {
            if (logFile == null || !Files.exists(logFile)) return "";
            long fileSize = Files.size(logFile);
            long offset = Math.max(0, fileSize - MAX_TAIL_BYTES);
            byte[] buf;
            try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                raf.seek(offset);
                int len = (int) (fileSize - offset);
                buf = new byte[len];
                raf.readFully(buf);
            }
            return new String(buf, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }
}
