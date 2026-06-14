package io.github.morikemuri.stutteranalyzer.optimize;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Set;

/**
 * Per-entry structural validation for optimization_mods.json.
 *
 * Returns {@code null} when the entry is safe to parse, or a human-readable reason when it
 * must be skipped. The optimize loader parses each entry in isolation; this keeps a single
 * malformed entry (wrong field type/shape) from throwing and truncating the whole database,
 * which would silently drop every entry after it. Pure type/shape checks only - softer data
 * quality issues (unknown loader value, missing install source) are reported by the startup
 * {@link io.github.morikemuri.stutteranalyzer.knowledge.JsonRuleValidator} as warnings.
 */
final class OptimizationDbValidator {

    private static final Set<String> STRING_ARRAY_FIELDS = Set.of(
        "display_names", "known_aliases", "loader_support", "category_tags",
        "risk_areas", "known_patterns", "install_conflicts", "install_requires");

    private static final Set<String> STRING_FIELDS = Set.of(
        "side", "install_modrinth", "install_reason", "notes", "safe_report_language",
        "install_note", "risk_level", "source_note", "reason_key", "curseforge_slug");

    // Stage-5 optional boolean flags.
    private static final Set<String> BOOLEAN_FIELDS = Set.of("deprecated", "advice_only", "manual_only");

    private OptimizationDbValidator() {}

    static String validate(String modKey, JsonObject obj) {
        if (obj.has("install_priority") && !isNumber(obj.get("install_priority")))
            return "install_priority must be a number";
        if (obj.has("install_safe") && !isBoolean(obj.get("install_safe")))
            return "install_safe must be a boolean";
        for (String f : BOOLEAN_FIELDS) {
            if (obj.has(f) && !isBoolean(obj.get(f))) return f + " must be a boolean";
        }

        for (String f : STRING_FIELDS) {
            if (obj.has(f) && !isString(obj.get(f))) return f + " must be a string";
        }

        for (String f : STRING_ARRAY_FIELDS) {
            if (!obj.has(f)) continue;
            JsonElement el = obj.get(f);
            if (!el.isJsonArray()) return f + " must be an array";
            for (JsonElement e : el.getAsJsonArray()) {
                if (!isString(e)) return f + " must contain only strings";
            }
        }

        return null;
    }

    private static boolean isNumber(JsonElement e) {
        return e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }

    private static boolean isBoolean(JsonElement e) {
        return e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean();
    }

    private static boolean isString(JsonElement e) {
        return e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }
}
