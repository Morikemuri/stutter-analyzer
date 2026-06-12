package com.stutteranalyzer.report;

import com.stutteranalyzer.SAEnvironment;
import com.stutteranalyzer.StutterAnalyzerFabric;
import com.stutteranalyzer.config.SAConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportWriter {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "stutteranalyzer-report-writer");
        t.setDaemon(true);
        return t;
    });

    private static int savedReports = 0;
    private static FreezeReport lastReport = null;

    public static void writeAsync(FreezeReport report) {
        executor.submit(() -> {
            try {
                write(report);
            } catch (Throwable t) {
                StutterAnalyzerFabric.LOGGER.error("[StutterAnalyzer] ReportWriter failed: {}", t.getMessage(), t);
            }
        });
    }

    private static synchronized void write(FreezeReport report) throws IOException {
        lastReport = report;
        Path dir = resolveReportDir();
        Files.createDirectories(dir);

        enforceMaxReports(dir);

        String format = SAConfig.INSTANCE.reportFormat.get();

        if (format.contains("markdown") || SAConfig.INSTANCE.saveMarkdown.get()) {
            Path mdFile = dir.resolve(report.reportId + ".md");
            try (FileWriter fw = new FileWriter(mdFile.toFile())) {
                fw.write(report.toMarkdown());
            }
        }

        if (format.contains("json") || SAConfig.INSTANCE.saveJson.get()) {
            Path jsonFile = dir.resolve(report.reportId + ".json");
            try (FileWriter fw = new FileWriter(jsonFile.toFile())) {
                fw.write(report.toJson());
            }
        }

        Path latestFile = dir.resolve("latest-freeze.txt");
        try (FileWriter fw = new FileWriter(latestFile.toFile())) {
            fw.write(report.reportId + "\n" + report.event.category() + "\n" + report.event.durationMs() + " ms\n");
        }

        savedReports++;
        StutterAnalyzerFabric.LOGGER.info("[StutterAnalyzer] Report saved: {}", report.reportId);
    }

    private static Path resolveReportDir() {
        return SAEnvironment.getConfigDir().resolve("stutter-analyzer/reports");
    }

    private static void enforceMaxReports(Path dir) {
        int max = SAConfig.INSTANCE.maxReports.get();
        File[] files = dir.toFile().listFiles((d, n) -> n.endsWith(".md") || n.endsWith(".json"));
        if (files == null || files.length <= max * 2) return;
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        int toDelete = files.length - max * 2;
        for (int i = 0; i < toDelete; i++) {
            files[i].delete();
        }
    }

    public static int savedReports() { return savedReports; }
    public static FreezeReport lastReport() { return lastReport; }
}
