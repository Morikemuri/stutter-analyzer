package com.stutteranalyzer.report;

import com.stutteranalyzer.core.MetricsCollector;
import com.stutteranalyzer.knowledge.ModInventory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class FreezeReport {

    private static final DateTimeFormatter ID_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).withZone(ZoneId.systemDefault());

    public final String reportId;
    public final FreezeEvent event;
    public final List<ModInventory.ModEntry> loadedMods;
    public final String heapInfo;
    public final String systemInfo;
    public final String sanitizationStatus;

    private FreezeReport(String reportId, FreezeEvent event, List<ModInventory.ModEntry> loadedMods,
                         String heapInfo, String systemInfo) {
        this.reportId         = reportId;
        this.event            = event;
        this.loadedMods       = loadedMods;
        this.heapInfo         = heapInfo;
        this.systemInfo       = systemInfo;
        this.sanitizationStatus = "Usernames, paths, and IPs redacted (see config).";
    }

    public static FreezeReport from(FreezeEvent event) {
        String id = ID_FMT.format(event.timestamp()) + "_" + event.category().name().toLowerCase(Locale.ROOT);
        String heap = MetricsCollector.memoryGc().heapUsedMb() + "/" + MetricsCollector.memoryGc().heapMaxMb() + " MB";
        String sys  = "Java " + System.getProperty("java.version") + " | " + System.getProperty("os.name") + " | " +
                      Runtime.getRuntime().availableProcessors() + " cores";
        return new FreezeReport(id, event, ModInventory.snapshot(), heap, sys);
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# StutterAnalyzer Freeze Report\n\n");
        sb.append("## Summary\n");
        sb.append("- **Category:** ").append(event.category()).append("\n");
        sb.append("- **Confidence:** ").append(event.confidencePct()).append("%\n");
        sb.append("- **Duration:** ").append(event.durationMs()).append(" ms\n");
        sb.append("- **Side:** ").append(event.side()).append("\n");
        sb.append("- **Timestamp:** ").append(event.timestamp()).append("\n");
        sb.append("- **Report ID:** `").append(reportId).append("`\n\n");

        sb.append("## Reason\n").append(event.reason()).append("\n\n");
        if (!event.evidence().isEmpty())
            sb.append("## Evidence\n").append(event.evidence()).append("\n\n");
        sb.append("## Recommendation\n").append(event.recommendation()).append("\n\n");

        sb.append("## System Info\n").append(systemInfo).append("\n");
        sb.append("Heap: ").append(heapInfo).append("\n\n");

        if (!loadedMods.isEmpty()) {
            sb.append("## Loaded Mods (").append(loadedMods.size()).append(")\n");
            for (ModInventory.ModEntry m : loadedMods)
                sb.append("- ").append(m.modId).append(" ").append(m.version).append("\n");
            sb.append("\n");
        }

        if (!event.recentTimeline().isEmpty()) {
            sb.append("## Recent Event Timeline\n");
            List<?> recent = event.recentTimeline();
            int start = Math.max(0, recent.size() - 20);
            for (int i = start; i < recent.size(); i++)
                sb.append("- ").append(recent.get(i)).append("\n");
        }

        sb.append("\n## Privacy\n").append(sanitizationStatus).append("\n");
        return sb.toString();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"report_id\": \"").append(reportId).append("\",\n");
        sb.append("  \"category\": \"").append(event.category()).append("\",\n");
        sb.append("  \"confidence\": ").append(event.confidencePct()).append(",\n");
        sb.append("  \"duration_ms\": ").append(event.durationMs()).append(",\n");
        sb.append("  \"side\": \"").append(event.side()).append("\",\n");
        sb.append("  \"timestamp\": \"").append(event.timestamp()).append("\",\n");
        sb.append("  \"reason\": \"").append(escape(event.reason())).append("\",\n");
        sb.append("  \"evidence\": \"").append(escape(event.evidence())).append("\",\n");
        sb.append("  \"recommendation\": \"").append(escape(event.recommendation())).append("\",\n");
        sb.append("  \"heap\": \"").append(heapInfo).append("\",\n");
        sb.append("  \"system\": \"").append(escape(systemInfo)).append("\",\n");
        sb.append("  \"mod_count\": ").append(loadedMods.size()).append(",\n");
        sb.append("  \"sanitization\": \"").append(sanitizationStatus).append("\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
