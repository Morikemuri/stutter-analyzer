package com.stutteranalyzer.knowledge;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.stutteranalyzer.SAEnvironment;
import com.stutteranalyzer.StutterAnalyzerFabric;
import com.stutteranalyzer.core.SubsystemHealth;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OptimizationModKnowledgeBase {

    private static Map<String, Object> data = Collections.emptyMap();
    private static boolean loaded = false;

    public static void load() {
        try {
            InputStream bundled = OptimizationModKnowledgeBase.class.getResourceAsStream(
                "/assets/stutteranalyzer/optimization_mods.json");

            Path localOverride = SAEnvironment.getConfigDir().resolve("stutter-analyzer/optimization_mods.local.json");
            InputStream src = (Files.exists(localOverride) && !Files.isDirectory(localOverride))
                ? Files.newInputStream(localOverride)
                : bundled;

            if (src == null) {
                StutterAnalyzerFabric.LOGGER.warn("[StutterAnalyzer] optimization_mods.json not found. Knowledge subsystem disabled.");
                SubsystemHealth.setStatus("ModInventory", SubsystemHealth.Status.DISABLED, "optimization_mods.json missing");
                return;
            }

            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            try (Reader r = new InputStreamReader(src, StandardCharsets.UTF_8)) {
                data = gson.fromJson(r, type);
            }
            loaded = true;
            StutterAnalyzerFabric.LOGGER.info("[StutterAnalyzer] Optimization mod knowledge base loaded ({} entries).", data.size());
        } catch (Throwable t) {
            StutterAnalyzerFabric.LOGGER.error("[StutterAnalyzer] Failed to load knowledge base: {}", t.getMessage(), t);
            SubsystemHealth.setStatus("ModInventory", SubsystemHealth.Status.DEGRADED, "knowledge base load error: " + t.getMessage());
        }
    }

    public static boolean isLoaded() { return loaded; }

    @SuppressWarnings("unchecked")
    public static List<String> riskAreasFor(String modId) {
        if (!loaded) return Collections.emptyList();
        Object entry = data.get(modId);
        if (entry instanceof Map<?, ?> m) {
            Object ra = m.get("risk_areas");
            if (ra instanceof List<?> l) return (List<String>) l;
        }
        return Collections.emptyList();
    }
}
