package com.stutteranalyzer.submission;

import com.stutteranalyzer.StutterAnalyzerNeo;
import com.stutteranalyzer.config.SAConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LogExcerpter {

    private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
        .withZone(ZoneId.systemDefault());

    private static final String[] KEYWORDS = {
        "stutteranalyzer", "[sa]", "freeze", "stutter", "spike",
        "gc pause", "garbage", "mspt", "tps", "tick took",
        "crash", "emergency", "guard", "pattern match",
        "report saved", "submitted",
    };

    private static final String[] SA_EVENT_KEYWORDS = {
        "[sa]", "[stutter analyzer]", "[stutteranalyzer]",
        "unknown freeze detected", "unknown_freeze", "client_render_stutter",
        "server_tick_spike", "extreme_freeze", "severe episode",
        "report saved", "known pattern", "emergency guard",
        "repeated small stutters", "minor episodes", "medium episodes",
        "severe episodes", "extreme episodes", "mspt", " tps ", "gc pause",
        "memory pressure", "stutter analyzer"
    };

    private static final String[] FREEZE_CONTEXT_KEYWORDS = {
        "unknown freeze", "report saved", "spike detected", "freeze detected",
        "server_tick_spike", "client_render_stutter", "extreme_freeze",
        "emergency guard triggered", "severe episode", "stutter cluster"
    };

    private static final String[] SUSPICIOUS_KEYWORDS = {
        "/warn]", "/error]", "exception", "caused by",
        "timeout", "watchdog", "server overloaded", "can't keep up",
        "chunk", "worldgen", "resource reload", "shader", "texture",
        "model", "datapack", "gc pause", "outofmemory", "out of memory",
        "packet", "connection lost", "mixin", "injection",
        "sodium", "embeddium", "rubidium", "oculus", "iris", "c2me",
        "distant horizons", "modernfix", "ferritecore", "lithium",
        "starlight", "scalablelux"
    };

    public static String extractExcerpt(Instant eventTime) {
        if (!SAConfig.INSTANCE.includeLogExcerpt.get()) return null;

        int maxLines      = SAConfig.INSTANCE.logExcerptMaxLines.get();
        int contextSecs   = SAConfig.INSTANCE.logExcerptContextSeconds.get();
        int maxChars      = SAConfig.INSTANCE.maxLogExcerptChars.get();

        try {
            Path logFile = resolveLogFile();
            if (logFile == null || !Files.exists(logFile)) {
                return "No relevant latest.log excerpt was found for this report.";
            }

            long fileSize = Files.size(logFile);
            if (fileSize == 0) return "No relevant latest.log excerpt was found for this report.";

            long readOffset = Math.max(0, fileSize - 262144); // read last 256KB
            byte[] buf;
            try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                raf.seek(readOffset);
                int len = (int) (fileSize - readOffset);
                buf = new byte[len];
                raf.readFully(buf);
            }

            String tail = new String(buf, StandardCharsets.UTF_8);
            String[] allLines = tail.split("\n", -1);

            String eventHHMMSS = HH_MM_SS.format(eventTime);
            int eventTotalSecs = parseHHMMSS(eventHHMMSS);

            List<String> relevant = new ArrayList<>();
            for (String line : allLines) {
                String stripped = line.stripTrailing();
                if (stripped.isBlank()) continue;
                if (isRelevantLine(stripped, eventTotalSecs, contextSecs)) {
                    relevant.add(stripped);
                }
            }

            // If no timestamp/keyword match, use last maxLines lines
            if (relevant.isEmpty()) {
                for (int i = Math.max(0, allLines.length - maxLines); i < allLines.length; i++) {
                    String stripped = allLines[i].stripTrailing();
                    if (!stripped.isBlank()) relevant.add(stripped);
                }
            }

            if (relevant.isEmpty()) return "No relevant latest.log excerpt was found for this report.";

            if (relevant.size() > maxLines) {
                relevant = relevant.subList(relevant.size() - maxLines, relevant.size());
            }

            String excerpt = String.join("\n", relevant);
            if (excerpt.length() > maxChars) {
                excerpt = "...(truncated to last " + maxChars + " chars)\n" +
                    excerpt.substring(excerpt.length() - maxChars);
            }

            ReportSanitizer.SanitizeResult sanitized = ReportSanitizer.sanitize(excerpt);
            if (sanitized.hadSensitiveData()) {
                return "Log excerpt was blocked: sensitive data pattern detected during sanitization.";
            }
            return sanitized.text();
        } catch (Exception e) {
            StutterAnalyzerNeo.LOGGER.debug("[SA] Log excerpt extraction failed: {}", e.getMessage());
            return "No relevant latest.log excerpt was found for this report.";
        }
    }

    private static boolean isRelevantLine(String line, int eventTotalSecs, int contextSecs) {
        String lineLower = line.toLowerCase(Locale.ROOT);
        // Check timestamp proximity (Minecraft log format: [HH:MM:SS])
        if (line.length() >= 10 && line.charAt(0) == '[') {
            int closeIdx = line.indexOf(']');
            if (closeIdx > 0 && closeIdx <= 9) {
                int lineSecs = parseHHMMSS(line.substring(1, closeIdx));
                if (lineSecs >= 0 && eventTotalSecs >= 0) {
                    int diff = Math.abs(lineSecs - eventTotalSecs);
                    // Handle midnight wrap
                    if (diff > 43200) diff = 86400 - diff;
                    if (diff <= contextSecs) return true;
                }
            }
        }
        // Check keywords
        for (String kw : KEYWORDS) {
            if (lineLower.contains(kw)) return true;
        }
        return false;
    }

    private static int parseHHMMSS(String ts) {
        try {
            String[] parts = ts.split(":", 3);
            if (parts.length != 3) return -1;
            return Integer.parseInt(parts[0].trim()) * 3600
                + Integer.parseInt(parts[1].trim()) * 60
                + Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String readFullLog() {
        if (!SAConfig.INSTANCE.includeFullLatestLog.get()) return null;

        int maxChars = SAConfig.INSTANCE.maxFullLogChars.get();
        try {
            Path logFile = resolveLogFile();
            if (logFile == null || !Files.exists(logFile)) return null;

            long fileSize = Files.size(logFile);
            if (fileSize == 0) return null;

            byte[] buf;
            long readOffset = Math.max(0, fileSize - (long) maxChars * 2);
            try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                raf.seek(readOffset);
                int len = (int) (fileSize - readOffset);
                buf = new byte[len];
                raf.readFully(buf);
            }

            String content = new String(buf, StandardCharsets.UTF_8);
            if (content.length() > maxChars) {
                content = "...(truncated to last " + maxChars + " chars)\n" + content.substring(content.length() - maxChars);
            }

            ReportSanitizer.SanitizeResult sanitized = ReportSanitizer.sanitize(content);
            if (sanitized.hadSensitiveData()) {
                StutterAnalyzerNeo.LOGGER.warn("[SA] Full log blocked: sensitive data detected.");
                return null;
            }
            return sanitized.text();
        } catch (Exception e) {
            StutterAnalyzerNeo.LOGGER.debug("[SA] Full log read failed: {}", e.getMessage());
            return null;
        }
    }

    public static String extractStutterLogEvents() {
        if (!SAConfig.INSTANCE.includeStutterAnalyzerLogEvents.get()) return null;
        int maxLines = SAConfig.INSTANCE.maxLogEventLines.get();
        return doExtractStutterLogEvents(maxLines);
    }

    static int countStutterLogEventLines() {
        try {
            String result = doExtractStutterLogEvents(SAConfig.INSTANCE.maxLogEventLines.get());
            if (result == null || result.startsWith("No ") || result.startsWith("Could ") || result.startsWith("latest.log")) return 0;
            return result.split("\n", -1).length;
        } catch (Exception e) { return 0; }
    }

    private static String doExtractStutterLogEvents(int maxLines) {
        try {
            Path logFile = resolveLogFile();
            if (logFile == null || !Files.exists(logFile)) return "latest.log could not be read.";
            List<String> matching = new ArrayList<>();
            try (var reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null && matching.size() < maxLines) {
                    String lower = line.toLowerCase(Locale.ROOT);
                    for (String kw : SA_EVENT_KEYWORDS) {
                        if (lower.contains(kw)) { matching.add(line.stripTrailing()); break; }
                    }
                }
            }
            if (matching.isEmpty()) return "No Stutter Analyzer log events were found in latest.log.";
            ReportSanitizer.SanitizeResult r = ReportSanitizer.sanitize(String.join("\n", matching));
            return r.hadSensitiveData() ? "Log events blocked by sanitizer." : r.text();
        } catch (Exception e) {
            return "Could not extract Stutter Analyzer log events: " + e.getMessage();
        }
    }

    public static String extractUnknownFreezeContext() {
        if (!SAConfig.INSTANCE.includeUnknownFreezeContext.get()) return null;
        return doExtractUnknownFreezeContext(
            SAConfig.INSTANCE.logContextLinesBefore.get(),
            SAConfig.INSTANCE.logContextLinesAfter.get(),
            SAConfig.INSTANCE.maxLogContextEvents.get());
    }

    static int countUnknownFreezeContextEvents() {
        try {
            String result = doExtractUnknownFreezeContext(
                SAConfig.INSTANCE.logContextLinesBefore.get(),
                SAConfig.INSTANCE.logContextLinesAfter.get(),
                SAConfig.INSTANCE.maxLogContextEvents.get());
            if (result == null || result.startsWith("No ") || result.startsWith("Could ") || result.startsWith("latest.log")) return 0;
            int count = 0;
            for (String s : result.split("\n", -1)) { if (s.startsWith("### Event ")) count++; }
            return count;
        } catch (Exception e) { return 0; }
    }

    private static String doExtractUnknownFreezeContext(int linesBefore, int linesAfter, int maxEvents) {
        try {
            Path logFile = resolveLogFile();
            if (logFile == null || !Files.exists(logFile)) return "latest.log could not be read.";
            List<String> allLines = new ArrayList<>();
            long maxBytes = 2 * 1024 * 1024L;
            long readBytes = 0;
            try (var reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null && readBytes < maxBytes) {
                    allLines.add(line);
                    readBytes += line.length() + 1;
                }
            }
            List<Integer> triggers = new ArrayList<>();
            for (int i = 0; i < allLines.size() && triggers.size() < maxEvents; i++) {
                String lower = allLines.get(i).toLowerCase(Locale.ROOT);
                for (String kw : FREEZE_CONTEXT_KEYWORDS) {
                    if (lower.contains(kw)) { triggers.add(i); break; }
                }
            }
            if (triggers.isEmpty()) return "No Unknown Freeze context was found in latest.log.";
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < triggers.size(); idx++) {
                int trig = triggers.get(idx);
                int start = Math.max(0, trig - linesBefore);
                int end = Math.min(allLines.size() - 1, trig + linesAfter);
                sb.append("### Event ").append(idx + 1).append("\n");
                for (int i = start; i <= end; i++) sb.append(allLines.get(i).stripTrailing()).append("\n");
                sb.append("\n");
            }
            if (sb.length() > 50000) sb.setLength(49800);
            ReportSanitizer.SanitizeResult r = ReportSanitizer.sanitize(sb.toString());
            return r.hadSensitiveData() ? "Context blocked by sanitizer." : r.text();
        } catch (Exception e) {
            return "Could not extract Unknown Freeze context: " + e.getMessage();
        }
    }

    public static String extractSuspiciousSignals() {
        if (!SAConfig.INSTANCE.includeSuspiciousLogSignals.get()) return null;
        int maxLines = SAConfig.INSTANCE.maxSuspiciousLogLines.get();
        return doExtractSuspiciousSignals(maxLines);
    }

    static int countSuspiciousSignalLines() {
        try {
            String result = doExtractSuspiciousSignals(SAConfig.INSTANCE.maxSuspiciousLogLines.get());
            if (result == null || result.startsWith("No ") || result.startsWith("Could ") || result.startsWith("latest.log")) return 0;
            return result.split("\n", -1).length;
        } catch (Exception e) { return 0; }
    }

    private static String doExtractSuspiciousSignals(int maxLines) {
        try {
            Path logFile = resolveLogFile();
            if (logFile == null || !Files.exists(logFile)) return "latest.log could not be read.";
            List<String> matching = new ArrayList<>();
            try (var reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null && matching.size() < maxLines) {
                    String lower = line.toLowerCase(Locale.ROOT);
                    for (String kw : SUSPICIOUS_KEYWORDS) {
                        if (lower.contains(kw)) { matching.add(line.stripTrailing()); break; }
                    }
                }
            }
            if (matching.isEmpty()) return "No suspicious log signals were found.";
            ReportSanitizer.SanitizeResult r = ReportSanitizer.sanitize(String.join("\n", matching));
            return r.hadSensitiveData() ? "Signals blocked by sanitizer." : r.text();
        } catch (Exception e) {
            return "Could not extract suspicious signals: " + e.getMessage();
        }
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

