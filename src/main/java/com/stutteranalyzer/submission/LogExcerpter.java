package com.stutteranalyzer.submission;

import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.config.SAConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

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
            StutterAnalyzerMod.LOGGER.debug("[SA] Log excerpt extraction failed: {}", e.getMessage());
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
                StutterAnalyzerMod.LOGGER.warn("[SA] Full log blocked: sensitive data detected.");
                return null;
            }
            return sanitized.text();
        } catch (Exception e) {
            StutterAnalyzerMod.LOGGER.debug("[SA] Full log read failed: {}", e.getMessage());
            return null;
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
