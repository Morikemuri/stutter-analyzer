package com.stutteranalyzer.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.config.SAConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class UpdateChecker {

    private static final AtomicReference<UpdateCheckResult> cached = new AtomicReference<>();
    private static volatile String lastNotifiedVersion = null;

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SA-UpdateChecker");
        t.setDaemon(true);
        return t;
    });

    public static UpdateCheckResult getCached() {
        return cached.get();
    }

    public static String getLastNotifiedVersion() {
        return lastNotifiedVersion;
    }

    public static void markNotified(String version) {
        lastNotifiedVersion = version;
        UpdateCheckResult current = cached.get();
        if (current != null && current.success()) {
            saveCacheToDisk(current);
        }
    }

    /** Schedules the startup update check with delay from config. Loads disk cache first. */
    public static void scheduleStartupCheck() {
        if (!SAConfig.INSTANCE.checkForUpdates.get()) return;
        if (!SAConfig.INSTANCE.checkOnStartup.get()) return;
        loadCacheFromDisk();
        int delay = SAConfig.INSTANCE.startupCheckDelaySeconds.get();
        scheduler.schedule(UpdateChecker::performCheck, delay, TimeUnit.SECONDS);
    }

    /** Submits an immediate async update check (used by /sa update check). */
    public static void performCheckAsync() {
        scheduler.submit(UpdateChecker::performCheck);
    }

    private static void performCheck() {
        if (!SAConfig.INSTANCE.checkForUpdates.get()) {
            StutterAnalyzerMod.LOGGER.info("[SA] Update checks disabled by config.");
            return;
        }
        String url = SAConfig.INSTANCE.updateVersionUrl.get();
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                cached.set(UpdateCheckResult.error("HTTP " + response.statusCode()));
                return;
            }
            UpdateCheckResult result = parseJson(response.body());
            cached.set(result);
            saveCacheToDisk(result);
            StutterAnalyzerMod.LOGGER.info("[SA] Update check complete. Latest: {} updateAvailable: {}",
                result.latestVersion(), result.updateAvailable());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cached.set(UpdateCheckResult.error("interrupted"));
        } catch (Exception e) {
            StutterAnalyzerMod.LOGGER.warn("[SA] Update check failed: {}", e.getMessage());
            cached.set(UpdateCheckResult.error("could not reach GitHub"));
        }
    }

    private static UpdateCheckResult parseJson(String body) {
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            String latestVersion = str(obj, "latest_version", "");
            String githubPage = str(obj, "github_page", SAConfig.INSTANCE.updateGithubPage.get());
            String curseforgeUrl = str(obj, "curseforge_url", SAConfig.INSTANCE.updateCurseforgeUrl.get());
            String changelogUrl = str(obj, "changelog_url", "");
            String message = str(obj, "message", "");
            boolean critical = obj.has("critical") && !obj.get("critical").isJsonNull()
                && obj.get("critical").getAsBoolean();
            boolean updateAvailable = SemanticVersion.isNewer(latestVersion, StutterAnalyzerMod.MOD_VERSION);
            return new UpdateCheckResult(true, latestVersion, updateAvailable,
                githubPage, curseforgeUrl, changelogUrl, message, critical, null,
                System.currentTimeMillis());
        } catch (Exception e) {
            return UpdateCheckResult.error("malformed response: " + e.getMessage());
        }
    }

    private static void loadCacheFromDisk() {
        try {
            Path p = cacheFile();
            if (!Files.exists(p)) return;
            String content = Files.readString(p);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            String latestVersion = str(obj, "latest_version", "");
            if (latestVersion.isEmpty()) return;
            boolean updateAvailable = obj.has("update_available") && !obj.get("update_available").isJsonNull()
                && obj.get("update_available").getAsBoolean();
            String githubPage = str(obj, "github_page", SAConfig.INSTANCE.updateGithubPage.get());
            String curseforgeUrl = str(obj, "curseforge_url", SAConfig.INSTANCE.updateCurseforgeUrl.get());
            long checkedAt = obj.has("last_checked_at_ms") && !obj.get("last_checked_at_ms").isJsonNull()
                ? obj.get("last_checked_at_ms").getAsLong() : 0;
            String notified = str(obj, "notified_version", null);
            lastNotifiedVersion = notified;
            cached.set(new UpdateCheckResult(true, latestVersion, updateAvailable,
                githubPage, curseforgeUrl, "", "", false, null, checkedAt));
        } catch (Exception e) {
            StutterAnalyzerMod.LOGGER.debug("[SA] Could not load update cache: {}", e.getMessage());
        }
    }

    private static void saveCacheToDisk(UpdateCheckResult result) {
        if (!result.success()) return;
        try {
            Path p = cacheFile();
            Files.createDirectories(p.getParent());
            String json = "{\n"
                + "  \"last_checked_at_ms\": " + result.checkedAtMs() + ",\n"
                + "  \"latest_version\": " + jsonStr(result.latestVersion()) + ",\n"
                + "  \"update_available\": " + result.updateAvailable() + ",\n"
                + "  \"github_page\": " + jsonStr(result.githubPage()) + ",\n"
                + "  \"curseforge_url\": " + jsonStr(result.curseforgeUrl()) + ",\n"
                + "  \"notified_version\": " + jsonStr(lastNotifiedVersion) + "\n"
                + "}\n";
            Files.writeString(p, json);
        } catch (IOException e) {
            StutterAnalyzerMod.LOGGER.debug("[SA] Could not save update cache: {}", e.getMessage());
        }
    }

    private static Path cacheFile() {
        return FMLPaths.CONFIGDIR.get().resolve("stutter-analyzer/update-cache.json");
    }

    private static String str(JsonObject obj, String key, String def) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : def;
    }

    private static String jsonStr(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
