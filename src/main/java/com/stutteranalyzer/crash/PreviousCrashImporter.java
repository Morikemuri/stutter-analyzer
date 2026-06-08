package com.stutteranalyzer.crash;

import com.stutteranalyzer.StutterAnalyzerFabric;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.SubsystemHealth;
import com.stutteranalyzer.pattern.KnownPatternDetector;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scans crash-reports/ and latest.log on startup to import known patterns.
 * Runs asynchronously to avoid blocking the render or server thread on startup.
 * Do not scan every file every frame - that is how you turn an analyzer into the final boss.
 */
public class PreviousCrashImporter {

    // If the crash report is bigger than 8 MB, we cannot help anyway.
    private static final int MAX_FILE_SIZE_BYTES = 8 * 1024 * 1024;
    // Only look at the last 10 crashes. If you crashed more than that, the stutter is probably not the main problem.
    private static final int MAX_CRASH_FILES = 10;
    private static final DateTimeFormatter ID_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).withZone(ZoneId.systemDefault());

    private static final List<CrashEvent> imported = Collections.synchronizedList(new ArrayList<>());
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "stutteranalyzer-crash-importer");
        t.setDaemon(true);
        return t;
    });

    public static void scanAsync(Path gameDir) {
        executor.submit(() -> {
            try {
                scan(gameDir);
            } catch (Throwable t) {
                StutterAnalyzerFabric.LOGGER.error("[StutterAnalyzer] CrashReportImporter failed: {}", t.getMessage(), t);
                SubsystemHealth.setStatus("CrashReportImporter", SubsystemHealth.Status.DEGRADED, t.getMessage());
            }
        });
    }

    private static void scan(Path gameDir) {
        Path crashDir = gameDir.resolve("crash-reports");
        Path latestLog = gameDir.resolve("logs/latest.log");

        String logText = "";
        if (Files.exists(latestLog)) {
            logText = safeRead(latestLog);
        }

        if (Files.exists(crashDir) && Files.isDirectory(crashDir)) {
            File[] files = crashDir.toFile().listFiles((d, n) -> n.endsWith(".txt"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
                int limit = Math.min(files.length, MAX_CRASH_FILES);
                for (int i = 0; i < limit; i++) {
                    importCrash(files[i].toPath(), logText);
                }
            }
        }

        if (!imported.isEmpty()) {
            StutterAnalyzerFabric.LOGGER.info("[StutterAnalyzer] Imported {} previous crash report(s).", imported.size());
            for (CrashEvent ce : imported) {
                if (ce.hasKnownPattern()) {
                    KnownPatternDetector.PatternMatch m = ce.bestMatch();
                    StutterAnalyzerFabric.LOGGER.warn("[StutterAnalyzer] Previous crash matches known pattern: {} ({}% confidence).",
                        m.patternId, m.confidencePct());
                }
            }
        }
    }

    private static void importCrash(Path file, String logText) {
        String text = safeRead(file);
        if (text.isEmpty()) return;

        List<KnownPatternDetector.PatternMatch> matches = KnownPatternDetector.detect(text, logText);
        String crashType = detectCrashType(text);
        String summary = extractSummary(text);
        String id = ID_FMT.format(Instant.ofEpochMilli(file.toFile().lastModified())) + "_crash";
        imported.add(new CrashEvent(id, Instant.ofEpochMilli(file.toFile().lastModified()), crashType, summary, text, matches));

        if (SAConfig.INSTANCE.writeCrashHints.get() && !matches.isEmpty()) {
            CrashHintInjector.writeHint(file, matches);
        }
    }

    private static String safeRead(Path file) {
        try {
            if (Files.size(file) > MAX_FILE_SIZE_BYTES) {
                StutterAnalyzerFabric.LOGGER.warn("[StutterAnalyzer] Skipping large file: {}", file);
                return "";
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String detectCrashType(String text) {
        if (text.contains("client render")) return "client render crash";
        if (text.contains("server thread")) return "server thread crash";
        if (text.contains("main thread")) return "main thread crash";
        return "unknown crash type";
    }

    private static String extractSummary(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.startsWith("Description:")) return line.substring("Description:".length()).trim();
        }
        return lines.length > 0 ? lines[0].trim() : "No summary";
    }

    public static List<CrashEvent> allImported() { return Collections.unmodifiableList(imported); }

    public static CrashEvent last() {
        return imported.isEmpty() ? null : imported.get(imported.size() - 1);
    }
}
