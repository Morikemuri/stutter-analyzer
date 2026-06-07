package com.stutteranalyzer.submission;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stutteranalyzer.StutterAnalyzerMod;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Uploads freeze reports to GitHub Gist (anonymous, no token required).
 * The returned URL is then linked from a GitHub Issues form.
 */
public class GistUploader {

    private static final String GIST_API = "https://api.github.com/gists";
    private static final int TIMEOUT_MS = 12_000;
    // Gist content cap per file. Large logs get trimmed, not dropped.
    private static final int MAX_CONTENT_CHARS = 800_000;

    public record Result(boolean success, String gistUrl, String error) {}

    public static Result upload(String reportId, String reportMarkdown, String logSnippet) {
        try {
            String body = buildBody(reportId, reportMarkdown, logSnippet);
            URL url = new URL(GIST_API);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            conn.setRequestProperty("User-Agent", "StutterAnalyzer-Mod/1.0.0");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);

            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(payload.length));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            int code = conn.getResponseCode();
            if (code == 201) {
                try (Reader r = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject resp = JsonParser.parseReader(r).getAsJsonObject();
                    String gistUrl = resp.get("html_url").getAsString();
                    StutterAnalyzerMod.LOGGER.info("[StutterAnalyzer] Gist uploaded: {}", gistUrl);
                    return new Result(true, gistUrl, null);
                }
            } else {
                String errBody = readErrorBody(conn);
                StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Gist API returned {}: {}", code, errBody);
                return new Result(false, null, "GitHub API HTTP " + code);
            }
        } catch (Exception e) {
            StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Gist upload failed: {}", e.getMessage());
            return new Result(false, null, e.getMessage());
        }
    }

    private static String buildBody(String reportId, String reportMarkdown, String logSnippet) {
        JsonObject files = new JsonObject();

        JsonObject reportFile = new JsonObject();
        reportFile.addProperty("content", trimIfNeeded(reportMarkdown));
        files.add("freeze-report-" + reportId + ".md", reportFile);

        if (logSnippet != null && !logSnippet.isBlank()) {
            JsonObject logFile = new JsonObject();
            logFile.addProperty("content", trimIfNeeded(logSnippet));
            files.add("latest-log-snippet.txt", logFile);
        }

        JsonObject body = new JsonObject();
        body.addProperty("description", "StutterAnalyzer Freeze Report - " + reportId);
        body.addProperty("public", false);
        body.add("files", files);
        return body.toString();
    }

    private static String trimIfNeeded(String s) {
        if (s == null) return "";
        return s.length() > MAX_CONTENT_CHARS ? s.substring(s.length() - MAX_CONTENT_CHARS) : s;
    }

    private static String readErrorBody(HttpURLConnection conn) {
        try (InputStream es = conn.getErrorStream()) {
            if (es == null) return "(no body)";
            return new String(es.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "(unreadable)";
        }
    }
}
