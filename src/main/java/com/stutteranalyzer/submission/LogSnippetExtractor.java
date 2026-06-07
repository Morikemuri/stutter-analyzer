package com.stutteranalyzer.submission;

import com.stutteranalyzer.StutterAnalyzerMod;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/**
 * Extracts the tail of latest.log for inclusion in bug reports.
 * Reads only the last N lines so we do not upload the entire session history.
 */
public class LogSnippetExtractor {

    private static final int MAX_LINES   = 250;
    // Tail-read window for very large logs - saves time and bandwidth.
    private static final long TAIL_BYTES = 512 * 1024L;

    public static String extract(Path gameDir) {
        Path logFile = gameDir.resolve("logs/latest.log");
        if (!Files.exists(logFile)) return "";
        try {
            long size = Files.size(logFile);
            if (size > TAIL_BYTES * 20) {
                return readTail(logFile, size);
            }
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - MAX_LINES);
            return "[last " + (lines.size() - from) + " lines of latest.log]\n\n" +
                   String.join("\n", lines.subList(from, lines.size()));
        } catch (Exception e) {
            StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Could not read latest.log: {}", e.getMessage());
            return "";
        }
    }

    private static String readTail(Path file, long size) throws Exception {
        long skip = Math.max(0, size - TAIL_BYTES);
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(skip);
            byte[] buf = new byte[(int) (size - skip)];
            raf.readFully(buf);
            String text = new String(buf, StandardCharsets.UTF_8);
            // Drop the partial first line after the seek.
            int nl = text.indexOf('\n');
            String tail = nl >= 0 ? text.substring(nl + 1) : text;
            return "[last ~" + TAIL_BYTES / 1024 + " KB of latest.log]\n\n" + tail;
        }
    }
}
