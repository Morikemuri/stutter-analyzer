package com.stutteranalyzer.optimize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stutteranalyzer.StutterAnalyzerFabric;
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
        if (installedCount > 80)  return 3;
        if (installedCount > 5)   return 5;
        return 7;
    }

    public static OptimizePlan buildPlan(
            Set<String> installedModIds,
            Path gameDir,
            String loader,
            String mcVersion,
            boolean isServer) {

        List<OptimizeMod> database = loadDatabase();

        // Include both lowercase and fully-stripped (no hyphens/underscores) forms for fuzzy matching
        Set<String> normalizedInstalled = new HashSet<>();
        for (String modId : installedModIds) {
            String lower = modId.toLowerCase();
            normalizedInstalled.add(lower);
            String stripped = lower.replaceAll("[\\s_\\-]", "");
            if (!stripped.equals(lower)) normalizedInstalled.add(stripped);
        }

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

        // Scan the physical mods folder to detect pending-restart mods
        Map<String, String> physicalJarIds = ModsFolderScanner.scan(gameDir.resolve("mods"));
        List<String> pendingRestartNames = new ArrayList<>();
        Set<String> pendingIds = new java.util.HashSet<>();

        for (OptimizeMod dbMod : database) {
            if (dbMod.alreadyInstalled(normalizedInstalled)) continue;
            boolean inFolder = physicalJarIds.containsKey(dbMod.id.toLowerCase())
                || physicalJarIds.containsKey(OptimizeMod.normalize(dbMod.id));
            if (!inFolder && dbMod.aliases != null) {
                for (String alias : dbMod.aliases) {
                    if (physicalJarIds.containsKey(alias.toLowerCase())
                            || physicalJarIds.containsKey(OptimizeMod.normalize(alias))) {
                        inFolder = true; break;
                    }
                }
            }
            if (!inFolder && dbMod.modrinthSlug != null) {
                inFolder = physicalJarIds.containsKey(dbMod.modrinthSlug.toLowerCase())
                    || physicalJarIds.containsKey(OptimizeMod.normalize(dbMod.modrinthSlug));
            }
            if (inFolder) {
                pendingIds.add(dbMod.id.toLowerCase());
                pendingRestartNames.add(dbMod.displayName);
                expandedInstalled.add(dbMod.id.toLowerCase());
                if (dbMod.aliases != null) {
                    for (String alias : dbMod.aliases) expandedInstalled.add(alias.toLowerCase());
                }
                LOGGER.info("[SA] Pending restart (jar in mods folder, not loaded): {}", dbMod.displayName);
            }
        }

        // Count physical .jar files for the user-visible counter (loader entries inflate to 100+)
        int modsJarCount = 0;
        try {
            modsJarCount = (int) Files.list(gameDir.resolve("mods"))
                .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar"))
                .count();
        } catch (Exception e) {
            LOGGER.warn("[SA] Could not count mods folder jars: {}", e.getMessage());
        }

        // Don't drip-feed a second install wave while restart is still required
        if (!pendingRestartNames.isEmpty()) {
            OptimizePlan earlyPlan = new OptimizePlan();
            earlyPlan.recommended    = new ArrayList<>();
            earlyPlan.skippedCandidates = new ArrayList<>();
            earlyPlan.alreadyInstalled  = alreadyInstalled;
            earlyPlan.pendingRestart    = pendingRestartNames;
            earlyPlan.loader      = loader;
            earlyPlan.mcVersion   = mcVersion;
            earlyPlan.serverOnly  = isServer;
            earlyPlan.totalInstalledCount = modsJarCount;
            earlyPlan.risk        = OptimizePlan.RiskLevel.LOW;
            earlyPlan.riskReason  = "Restart required before next wave.";
            writePlanJson(earlyPlan, gameDir);
            return earlyPlan;
        }

        // Build lookup map for dep resolution
        Map<String, OptimizeMod> dbById = new HashMap<>();
        for (OptimizeMod m : database) {
            dbById.put(m.id.toLowerCase(), m);
        }

        // Filter primary candidates (exclude loaded, pending, and conflicting mods) - no limit yet
        List<OptimizeMod> allCandidates = database.stream()
            .filter(m -> m.primarySuggestion)
            .filter(m -> m.supportsLoader(loader))
            .filter(m -> m.supportsEnvironment(isServer))
            .filter(m -> !m.alreadyInstalled(normalizedInstalled))
            .filter(m -> !pendingIds.contains(m.id.toLowerCase()))
            .filter(m -> !m.conflictsWith(expandedInstalled))
            .sorted((a, b) -> Integer.compare(b.priority, a.priority))
            .collect(Collectors.toList());

        // Resolve Modrinth download URLs for all candidates before applying limit
        Path cacheFile = gameDir.resolve("config/stutteranalyzer/optimization_cache.json");
        Map<String, JsonObject> cache = loadCache(cacheFile);
        boolean cacheUpdated = false;

        for (OptimizeMod mod : allCandidates) {
            if (mod.modrinthSlug != null && !mod.modrinthSlug.isEmpty()) {
                boolean before = mod.resolvedOnline;
                resolveModrinth(mod, loader, mcVersion, cache);
                if (mod.resolvedOnline != before) cacheUpdated = true;
            }
        }

        // Apply limit only to resolved mods so the full quota is always filled
        int limit = maxSuggestions(installedModIds.size());
        List<OptimizeMod> ready = new ArrayList<>();
        List<OptimizeMod> skipped = new ArrayList<>();
        for (OptimizeMod mod : allCandidates) {
            boolean isReady = mod.resolvedUrl != null && !mod.resolvedUrl.isEmpty();
            if (isReady && ready.size() < limit) {
                ready.add(mod);
            } else if (!isReady) {
                LOGGER.info("[SA] Skipping {} - no compatible file on Modrinth for {} {}", mod.id, loader, mcVersion);
                skipped.add(mod);
            }
        }

        // Dependency resolution: walk each candidate's required deps (recursively,
        // with a depth cap and cycle guard) and either pull the whole chain into the
        // plan or drop the candidate. All-or-nothing per candidate - a mod that ships
        // without its library is just a crash with extra steps.
        List<OptimizeMod> depsToAdd = new ArrayList<>();
        List<OptimizeMod> failedDeps = new ArrayList<>();
        boolean[] cacheDirty = { cacheUpdated };
        for (OptimizeMod candidate : new ArrayList<>(ready)) {
            if (candidate.installRequires == null || candidate.installRequires.isEmpty()) continue;
            List<OptimizeMod> chain = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            visited.add(candidate.id.toLowerCase());
            String failReason = resolveDepChain(candidate, candidate.installRequires, chain,
                ready, depsToAdd, expandedInstalled, dbById, loader, mcVersion, cache,
                visited, 0, cacheDirty);
            if (failReason == null) {
                for (OptimizeMod dep : chain) {
                    dep.depForMod = candidate.displayName;
                    depsToAdd.add(dep);
                    LOGGER.info("[SA] Added dependency: {} for {}", dep.displayName, candidate.displayName);
                }
            } else {
                LOGGER.info("[SA] Skipped {} - {}", candidate.displayName, failReason);
                candidate.skipReason = failReason;
                ready.remove(candidate);
                failedDeps.add(candidate);
            }
        }
        if (cacheDirty[0]) saveCache(cacheFile, cache);
        skipped.addAll(failedDeps);
        ready.addAll(depsToAdd);

        // Whole-set compatibility pass: the plan must survive as a TEAM,
        // not as a bunch of individually charming mods that hate each other
        List<OptimizeMod> incompatible = validatePlanSet(ready, expandedInstalled, loadedModVersions());
        skipped.addAll(incompatible);

        OptimizePlan plan = new OptimizePlan();
        plan.recommended = ready;
        plan.skippedCandidates = skipped;
        plan.alreadyInstalled = alreadyInstalled;
        plan.pendingRestart = pendingRestartNames;
        plan.loader = loader;
        plan.mcVersion = mcVersion;
        plan.serverOnly = isServer;
        plan.totalInstalledCount = modsJarCount;
        scoreRisk(plan);
        writePlanJson(plan, gameDir);
        return plan;
    }

    /** Deps of deps of deps... at some point you have to suspect a cycle. */
    private static final int MAX_DEP_DEPTH = 5;

    /**
     * Recursively resolves the required-dependency chain for one candidate.
     * Collected deps land in {@code chain}; returns null on success or a
     * human-readable reason why the candidate must be skipped.
     */
    private static String resolveDepChain(OptimizeMod candidate, List<String> requires,
            List<OptimizeMod> chain, List<OptimizeMod> ready, List<OptimizeMod> plannedDeps,
            Set<String> expandedInstalled, Map<String, OptimizeMod> dbById,
            String loader, String mcVersion, Map<String, JsonObject> cache,
            Set<String> visited, int depth, boolean[] cacheDirty) {

        if (depth > MAX_DEP_DEPTH) {
            return "dependency chain could not be resolved safely (too deep or cyclic)";
        }
        for (String depId : requires) {
            String depNorm = depId.toLowerCase();
            if (!visited.add(depNorm)) continue; // been there, resolved that (cycle guard)

            // Already loaded, pending restart, or otherwise known to be present?
            if (expandedInstalled.contains(depNorm)
                    || expandedInstalled.contains(OptimizeMod.normalize(depId))) {
                LOGGER.info("[SA] Dependency already present: {}", depId);
                continue;
            }
            OptimizeMod depMod = dbById.get(depNorm);
            if (depMod == null) {
                candidate.skipMissingDep = depId;
                return "required dependency " + depId + " not in database";
            }
            if (depMod.alreadyInstalled(expandedInstalled)) {
                LOGGER.info("[SA] Dependency already present: {}", depMod.displayName);
                continue;
            }
            // Already heading into the plan via another candidate?
            boolean inPlan = ready.stream().anyMatch(m -> m.id.equalsIgnoreCase(depId))
                || plannedDeps.stream().anyMatch(m -> m.id.equalsIgnoreCase(depId))
                || chain.stream().anyMatch(m -> m.id.equalsIgnoreCase(depId));
            if (inPlan) continue;

            if (!depMod.supportsLoader(loader)) {
                candidate.skipMissingDep = depMod.displayName;
                return "required dependency " + depMod.displayName + " not available for " + loader;
            }
            if (depMod.modrinthSlug != null && !depMod.resolvedOnline) {
                resolveModrinth(depMod, loader, mcVersion, cache);
                cacheDirty[0] = true;
            }
            if (depMod.resolvedUrl == null || depMod.resolvedUrl.isEmpty()) {
                candidate.skipMissingDep = depMod.displayName;
                return "required dependency " + depMod.displayName
                    + " could not be resolved for " + loader + " " + mcVersion;
            }
            // The dependency may have dependencies of its own. It's turtles all the way down.
            if (depMod.installRequires != null && !depMod.installRequires.isEmpty()) {
                String sub = resolveDepChain(candidate, depMod.installRequires, chain,
                    ready, plannedDeps, expandedInstalled, dbById, loader, mcVersion,
                    cache, visited, depth + 1, cacheDirty);
                if (sub != null) return sub;
            }
            chain.add(depMod);
        }
        return null;
    }

    /** id -> friendly version string for every currently loaded mod. */
    private static Map<String, String> loadedModVersions() {
        Map<String, String> map = new HashMap<>();
        try {
            for (net.fabricmc.loader.api.ModContainer mc :
                    net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
                map.put(mc.getMetadata().getId().toLowerCase(),
                        mc.getMetadata().getVersion().getFriendlyString());
            }
        } catch (Throwable t) {
            LOGGER.warn("[SA] Could not read loaded mod versions: {}", t.getMessage());
        }
        return map;
    }

    /**
     * Validates the plan as a set: every planned mod's conflicts are checked
     * against installed mods AND the rest of the plan, with version ranges.
     * Violators get evicted, then dependents without deps and orphaned deps
     * are pruned. Returns the evicted mods (orphaned deps leave quietly).
     */
    static List<OptimizeMod> validatePlanSet(List<OptimizeMod> ready,
                                             Set<String> installedIds,
                                             Map<String, String> installedVersions) {
        List<OptimizeMod> removed = new ArrayList<>();
        boolean changed = true;
        while (changed) {
            changed = false;

            // 1. Conflict eviction (installed mods + the rest of the plan)
            for (OptimizeMod mod : new ArrayList<>(ready)) {
                if (mod.conflicts == null) continue;
                String conflictName = null;
                for (String token : mod.conflicts) {
                    String targetId = OptimizeMod.conflictTargetId(token);

                    if (installedIds.contains(targetId)
                            || installedIds.contains(OptimizeMod.normalize(targetId))) {
                        String ver = installedVersions.get(targetId);
                        if (!OptimizeMod.isVersionedConflict(token)
                                || OptimizeMod.versionConflicts(token, ver)) {
                            conflictName = targetId + (ver != null ? " " + ver : "");
                        }
                    }
                    if (conflictName == null) {
                        for (OptimizeMod other : ready) {
                            if (other == mod || !other.matchesId(targetId)) continue;
                            if (!OptimizeMod.isVersionedConflict(token)
                                    || OptimizeMod.versionConflicts(token, other.resolvedVersion)) {
                                conflictName = other.displayName
                                    + (other.resolvedVersion != null ? " " + other.resolvedVersion : "");
                            }
                            break;
                        }
                    }
                    if (conflictName != null) break;
                }
                if (conflictName != null) {
                    mod.skipConflictWith = conflictName;
                    mod.skipReason = "incompatible with " + conflictName;
                    ready.remove(mod);
                    removed.add(mod);
                    changed = true;
                    LOGGER.info("[SA] Plan validation: skipping {} - incompatible with {}",
                        mod.displayName, conflictName);
                }
            }

            // 2. Dependents whose required deps are no longer in plan or installed
            for (OptimizeMod mod : new ArrayList<>(ready)) {
                if (mod.installRequires == null || mod.installRequires.isEmpty()) continue;
                for (String depId : mod.installRequires) {
                    boolean satisfied = installedIds.contains(depId.toLowerCase())
                        || installedIds.contains(OptimizeMod.normalize(depId))
                        || ready.stream().anyMatch(m2 -> m2 != mod && m2.matchesId(depId));
                    if (!satisfied) {
                        mod.skipMissingDep = depId;
                        mod.skipReason = "missing compatible " + depId;
                        ready.remove(mod);
                        removed.add(mod);
                        changed = true;
                        LOGGER.info("[SA] Plan validation: skipping {} - missing dependency {}",
                            mod.displayName, depId);
                        break;
                    }
                }
            }

            // 3. Orphaned deps: libraries that only came along for a mod that left
            Set<String> requiredIds = new HashSet<>();
            for (OptimizeMod mod : ready) {
                if (mod.depForMod == null && mod.installRequires != null) {
                    for (String d : mod.installRequires) requiredIds.add(d.toLowerCase());
                }
            }
            for (OptimizeMod mod : new ArrayList<>(ready)) {
                if (mod.depForMod == null) continue;
                boolean stillNeeded = requiredIds.stream().anyMatch(mod::matchesId);
                if (!stillNeeded) {
                    ready.remove(mod);
                    changed = true;
                    LOGGER.info("[SA] Plan validation: dropping orphaned dependency {}", mod.displayName);
                }
            }
        }
        return removed;
    }

    /**
     * Final pre-install validation: re-checks the complete plan against the
     * live mod list right before any download. Returns evicted mods.
     */
    public static List<OptimizeMod> finalValidatePlan(OptimizePlan plan) {
        Map<String, String> versions = loadedModVersions();
        Set<String> ids = new HashSet<>();
        for (String id : versions.keySet()) {
            ids.add(id);
            ids.add(OptimizeMod.normalize(id));
        }
        List<OptimizeMod> removed = validatePlanSet(plan.recommended, ids, versions);
        plan.skippedCandidates.addAll(removed);
        return removed;
    }

    private static void writePlanJson(OptimizePlan plan, Path gameDir) {
        try {
            Path configDir = gameDir.resolve("config").resolve("stutteranalyzer");
            Files.createDirectories(configDir);
            Path planFile = configDir.resolve("optimize_last_plan.json");

            JsonObject root = new JsonObject();
            root.addProperty("timestamp", java.time.Instant.now().toString());
            root.addProperty("minecraft_version", plan.mcVersion);
            root.addProperty("loader", plan.loader);
            root.addProperty("risk", plan.risk.name());

            JsonArray readyArr = new JsonArray();
            for (OptimizeMod m : plan.recommended) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", m.id);
                obj.addProperty("name", m.displayName);
                obj.addProperty("url", m.resolvedUrl != null ? m.resolvedUrl : "");
                if (m.resolvedVersion != null) obj.addProperty("version", m.resolvedVersion);
                if (m.depForMod != null) obj.addProperty("required_dep_for", m.depForMod);
                readyArr.add(obj);
            }
            root.add("ready_to_install", readyArr);

            JsonArray alreadyArr = new JsonArray();
            for (String s : plan.alreadyInstalled) alreadyArr.add(s);
            root.add("already_installed", alreadyArr);

            JsonArray skippedArr = new JsonArray();
            for (OptimizeMod m : plan.skippedCandidates) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", m.id);
                obj.addProperty("name", m.displayName);
                obj.addProperty("reason", m.skipReason != null ? m.skipReason : "no compatible file on Modrinth");
                skippedArr.add(obj);
            }
            root.add("skipped_candidates", skippedArr);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(planFile, gson.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("[SA] Failed to write plan JSON: {}", e.getMessage());
        }
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

                    boolean safe = !obj.has("install_safe") || obj.get("install_safe").getAsBoolean();

                    OptimizeMod mod = new OptimizeMod();
                    mod.id = modKey;
                    mod.priority = priority;
                    mod.safeDefault = safe;
                    mod.primarySuggestion = priority > 0 && safe;

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

                    mod.installRequires = new ArrayList<>();
                    if (obj.has("install_requires") && obj.get("install_requires").isJsonArray()) {
                        for (JsonElement re : obj.getAsJsonArray("install_requires")) {
                            mod.installRequires.add(re.getAsString());
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
                "StutterAnalyzer/" + StutterAnalyzerFabric.MOD_VERSION + " (github.com/Morikemuri/stutter-analyzer)");
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
                String versionNumber = version.has("version_number")
                    ? version.get("version_number").getAsString() : null;

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
                mod.resolvedVersion = versionNumber;
                mod.resolvedOnline = true;

                JsonObject cacheEntry = new JsonObject();
                cacheEntry.addProperty("url", fileUrl);
                cacheEntry.addProperty("filename", filename);
                if (sha512 != null) cacheEntry.addProperty("sha512", sha512);
                if (versionNumber != null) cacheEntry.addProperty("version_number", versionNumber);
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
        if (entry.has("version_number")) mod.resolvedVersion = entry.get("version_number").getAsString();
        mod.resolvedOnline = mod.resolvedUrl != null;
    }

    private static void scoreRisk(OptimizePlan plan) {
        int count = plan.recommended.size();
        int installed = plan.totalInstalledCount;

        if (count == 0) {
            plan.risk = OptimizePlan.RiskLevel.LOW;
            plan.riskReason = "Nothing to install.";
        } else if (installed > 80) {
            plan.risk = OptimizePlan.RiskLevel.HIGH;
            plan.riskReason = installed + " mods detected. Large modpacks are more sensitive to changes.";
        } else if (installed > 20 || count >= 5) {
            plan.risk = OptimizePlan.RiskLevel.MEDIUM;
            plan.riskReason = count + " mods will be added to a " + installed + "-mod modpack.";
        } else {
            plan.risk = OptimizePlan.RiskLevel.LOW;
            plan.riskReason = count == 1 ? "Single safe mod addition." : count + " mods in a small modpack.";
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
