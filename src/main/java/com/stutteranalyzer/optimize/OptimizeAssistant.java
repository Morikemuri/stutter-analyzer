package com.stutteranalyzer.optimize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OptimizeAssistant {

    private static final Logger LOGGER = LogManager.getLogger("StutterAnalyzer-Optimize");
    private static final long CACHE_TTL_MS = 24L * 3600L * 1000L;
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;

    private static int maxSuggestions(int installedCount) {
        if (installedCount > 160) return 2;
        if (installedCount > 80)  return 3;
        if (installedCount > 20)  return 5;
        return 7;
    }

    public static OptimizePlan buildPlan(
            Set<String> installedModIds,
            Path gameDir,
            String loader,
            String mcVersion,
            boolean isServer) {

        List<OptimizeMod> database = loadDatabase();

        Set<String> normalizedInstalled = installedModIds.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toCollection(HashSet::new));

        // Find already installed optimization mods from our database
        List<String> alreadyInstalled = new ArrayList<>();
        Set<String> expandedInstalled = new HashSet<>(normalizedInstalled);
        for (OptimizeMod mod : database) {
            if (mod.alreadyInstalled(normalizedInstalled)) {
                alreadyInstalled.add(mod.displayName);
                if (mod.aliases != null) {
                    for (String alias : mod.aliases) {
                        expandedInstalled.add(alias.toLowerCase());
                    }
                }
                expandedInstalled.add(mod.id.toLowerCase());
            }
        }

        // Filter candidates
        List<OptimizeMod> candidates = database.stream()
            .filter(m -> m.priority > 0)
            .filter(m -> m.safeDefault)
            .filter(m -> m.supportsLoader(loader))
            .filter(m -> m.supportsEnvironment(isServer))
            .filter(m -> !m.alreadyInstalled(normalizedInstalled))
            .filter(m -> !m.conflictsWith(expandedInstalled))
            .sorted((a, b) -> Integer.compare(b.priority, a.priority))
            .limit(maxSuggestions(installedModIds.size()))
            .collect(Collectors.toList());

        // Resolve Modrinth download URLs
        Path cacheFile = gameDir.resolve("config/stutteranalyzer/optimization_cache.json");
        Map<String, JsonObject> cache = loadCache(cacheFile);
        boolean cacheUpdated = false;

        for (OptimizeMod mod : candidates) {
            if (mod.modrinthSlug != null && !mod.modrinthSlug.isEmpty()) {
                boolean before = mod.resolvedOnline;
                resolveModrinth(mod, loader, mcVersion, cache);
                if (mod.resolvedOnline != before) cacheUpdated = true;
            }
        }

        if (cacheUpdated) {
            saveCache(cacheFile, cache);
        }

        OptimizePlan plan = new OptimizePlan();
        plan.recommended = candidates;
        plan.alreadyInstalled = alreadyInstalled;
        plan.loader = loader;
        plan.mcVersion = mcVersion;
        plan.serverOnly = isServer;
        plan.totalInstalledCount = normalizedInstalled.size();
        scoreRisk(plan);
        return plan;
    }

    private static List<OptimizeMod> loadDatabase() {
        List<OptimizeMod> result = new ArrayList<>();
        try (InputStream is = OptimizeAssistant.class.getResourceAsStream(
                "/assets/stutteranalyzer/optimization_mods.json")) {
            if (is == null) {
                LOGGER.warn("[SA] optimization_mods.json not found in resources");
                return result;
            }
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    String modKey = entry.getKey();
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject obj = entry.getValue().getAsJsonObject();

                    int priority = obj.has("install_priority")
                        ? obj.get("install_priority").getAsInt() : 0;
                    if (priority <= 0) continue;

                    boolean safe = !obj.has("install_safe") || obj.get("install_safe").getAsBoolean();
                    if (!safe) continue;

                    OptimizeMod mod = new OptimizeMod();
                    mod.id = modKey;
                    mod.priority = priority;
                    mod.safeDefault = true;

                    if (obj.has("display_names") && obj.get("display_names").isJsonArray()) {
                        JsonArray names = obj.getAsJsonArray("display_names");
                        mod.displayName = names.size() > 0
                            ? names.get(0).getAsString() : modKey;
                    } else {
                        mod.displayName = modKey;
                    }

                    mod.loaders = new ArrayList<>();
                    if (obj.has("loader_support") && obj.get("loader_support").isJsonArray()) {
                        for (JsonElement le : obj.getAsJsonArray("loader_support")) {
                            mod.loaders.add(le.getAsString());
                        }
                    }

                    mod.environment = obj.has("side") ? obj.get("side").getAsString() : "client_server";

                    mod.modrinthSlug = obj.has("install_modrinth")
                        ? obj.get("install_modrinth").getAsString() : null;

                    mod.reason = obj.has("install_reason")
                        ? obj.get("install_reason").getAsString() : "";

                    mod.aliases = new ArrayList<>();
                    if (obj.has("known_aliases") && obj.get("known_aliases").isJsonArray()) {
                        for (JsonElement ae : obj.getAsJsonArray("known_aliases")) {
                            mod.aliases.add(ae.getAsString());
                        }
                    }

                    mod.conflicts = new ArrayList<>();
                    if (obj.has("install_conflicts") && obj.get("install_conflicts").isJsonArray()) {
                        for (JsonElement ce : obj.getAsJsonArray("install_conflicts")) {
                            mod.conflicts.add(ce.getAsString());
                        }
                    }

                    result.add(mod);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[SA] Failed to load optimization database: {}", e.getMessage());
        }
        return result;
    }

    private static void resolveModrinth(OptimizeMod mod, String loader, String mcVersion,
                                        Map<String, JsonObject> cache) {
        String cacheKey = mod.modrinthSlug + ":" + loader + ":" + mcVersion;

        if (cache.containsKey(cacheKey)) {
            JsonObject entry = cache.get(cacheKey);
            long cachedAt = entry.has("cached_at") ? entry.get("cached_at").getAsLong() : 0L;
            if (System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                applyResolved(mod, entry);
                return;
            }
        }

        try {
            String encodedLoader = java.net.URLEncoder.encode(loader, "UTF-8");
            String encodedVersion = java.net.URLEncoder.encode(mcVersion, "UTF-8");
            String urlStr = "https://api.modrinth.com/v2/project/"
                + java.net.URLEncoder.encode(mod.modrinthSlug, "UTF-8")
                + "/version?loaders=%5B%22" + encodedLoader + "%22%5D"
                + "&game_versions=%5B%22" + encodedVersion + "%22%5D";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent",
                "StutterAnalyzer/0.2.0 (github.com/Morikemuri/stutter-analyzer)");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code != 200) {
                LOGGER.debug("[SA] Modrinth {} HTTP {}", mod.modrinthSlug, code);
                return;
            }

            try (InputStream is = conn.getInputStream();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                JsonArray versions = JsonParser.parseReader(reader).getAsJsonArray();
                if (versions.size() == 0) return;

                JsonObject version = versions.get(0).getAsJsonObject();
                JsonArray files = version.has("files") ? version.getAsJsonArray("files") : null;
                if (files == null || files.size() == 0) return;

                JsonObject primaryFile = null;
                for (JsonElement fe : files) {
                    JsonObject f = fe.getAsJsonObject();
                    if (f.has("primary") && f.get("primary").getAsBoolean()) {
                        primaryFile = f;
                        break;
                    }
                }
                if (primaryFile == null) primaryFile = files.get(0).getAsJsonObject();

                String fileUrl = primaryFile.get("url").getAsString();
                String filename = primaryFile.get("filename").getAsString();
                long fileSize = primaryFile.has("size") ? primaryFile.get("size").getAsLong() : 0L;

                String sha512 = null;
                if (primaryFile.has("hashes")) {
                    JsonObject hashes = primaryFile.getAsJsonObject("hashes");
                    if (hashes.has("sha512")) sha512 = hashes.get("sha512").getAsString();
                }

                if (!fileUrl.startsWith("https://cdn.modrinth.com/")) {
                    LOGGER.warn("[SA] Modrinth non-CDN URL for {}: {}", mod.modrinthSlug, fileUrl);
                    return;
                }
                if (!filename.endsWith(".jar")) {
                    LOGGER.warn("[SA] Modrinth non-jar for {}: {}", mod.modrinthSlug, filename);
                    return;
                }

                mod.resolvedUrl = fileUrl;
                mod.resolvedFilename = filename;
                mod.resolvedSha512 = sha512;
                mod.resolvedFileSize = fileSize;
                mod.resolvedOnline = true;

                JsonObject cacheEntry = new JsonObject();
                cacheEntry.addProperty("url", fileUrl);
                cacheEntry.addProperty("filename", filename);
                if (sha512 != null) cacheEntry.addProperty("sha512", sha512);
                cacheEntry.addProperty("size", fileSize);
                cacheEntry.addProperty("cached_at", System.currentTimeMillis());
                cache.put(cacheKey, cacheEntry);
            }
        } catch (Exception e) {
            LOGGER.debug("[SA] Modrinth query failed for {}: {}", mod.modrinthSlug, e.getMessage());
        }
    }

    private static void applyResolved(OptimizeMod mod, JsonObject entry) {
        if (entry.has("url")) mod.resolvedUrl = entry.get("url").getAsString();
        if (entry.has("filename")) mod.resolvedFilename = entry.get("filename").getAsString();
        if (entry.has("sha512")) mod.resolvedSha512 = entry.get("sha512").getAsString();
        if (entry.has("size")) mod.resolvedFileSize = entry.get("size").getAsLong();
        mod.resolvedOnline = mod.resolvedUrl != null;
    }

    private static void scoreRisk(OptimizePlan plan) {
        int count = plan.recommended.size();
        int installed = plan.totalInstalledCount;

        if (count == 0) {
            plan.risk = OptimizePlan.RiskLevel.LOW;
            plan.riskReason = "Nothing to install.";
        } else if (installed > 100 && count >= 3) {
            plan.risk = OptimizePlan.RiskLevel.HIGH;
            plan.riskReason = installed + " mods detected. Large modpacks are more sensitive to changes.";
        } else if (installed > 50 || count >= 4) {
            plan.risk = OptimizePlan.RiskLevel.MEDIUM;
            plan.riskReason = count + " mods will be added to a " + installed + "-mod modpack.";
        } else if (count == 1) {
            plan.risk = OptimizePlan.RiskLevel.LOW;
            plan.riskReason = "Single safe mod addition.";
        } else {
            plan.risk = OptimizePlan.RiskLevel.MEDIUM;
            plan.riskReason = count + " compatible mods will be added.";
        }
    }

    private static Map<String, JsonObject> loadCache(Path cacheFile) {
        Map<String, JsonObject> cache = new HashMap<>();
        try {
            if (Files.exists(cacheFile)) {
                String content = Files.readString(cacheFile, StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                if (root.has("entries") && root.get("entries").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e :
                            root.getAsJsonObject("entries").entrySet()) {
                        if (e.getValue().isJsonObject()) {
                            cache.put(e.getKey(), e.getValue().getAsJsonObject());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Return empty on any error
        }
        return cache;
    }

    private static void saveCache(Path cacheFile, Map<String, JsonObject> cache) {
        try {
            Files.createDirectories(cacheFile.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("saved_at", System.currentTimeMillis());
            JsonObject entries = new JsonObject();
            for (Map.Entry<String, JsonObject> e : cache.entrySet()) {
                entries.add(e.getKey(), e.getValue());
            }
            root.add("entries", entries);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(cacheFile, gson.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Non-critical
        }
    }
}
