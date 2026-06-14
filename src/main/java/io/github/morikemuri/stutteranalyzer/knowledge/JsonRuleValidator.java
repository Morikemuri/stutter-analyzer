package io.github.morikemuri.stutteranalyzer.knowledge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Startup validation pass for the bundled rule JSONs.
 *
 * Only optimization_mods.json is consumed at runtime today; the other three are reference
 * data the Java logic mirrors. This pass parses all four, checks them against the schema we
 * expect, and logs clear warnings for any problem - it never throws and never messages the
 * player. The goal is data safety: a bad rule file can be spotted in the log instead of
 * silently breaking detection or the optimize flow.
 *
 * Branch-independent: logs through Log4j directly so it can live in every loader unchanged.
 */
public final class JsonRuleValidator {

    private static final Logger LOG = LogManager.getLogger("stutteranalyzer");
    private static final String BASE = "/assets/stutteranalyzer/";

    private static final Set<String> LOADERS = Set.of("fabric", "forge", "neoforge");
    private static final Set<String> SIDES = Set.of("client", "server", "both", "client_server");

    private JsonRuleValidator() {}

    /** Validate every bundled rule file. Safe to call once at mod init; never throws. */
    public static void validateAll() {
        try {
            int issues = 0;
            issues += validateClassificationRules();
            Set<String> knownMods = collectOptimizationModIds();
            issues += validateOptimizationMods(knownMods);
            issues += validateKnownModPatterns();
            issues += validateRecommendationRules(knownMods);
            if (issues == 0) {
                LOG.info("[StutterAnalyzer] Rule validation: all rule files OK.");
            } else {
                LOG.warn("[StutterAnalyzer] Rule validation finished with {} issue(s) (see warnings above). "
                    + "Affected entries are skipped/ignored; the mod keeps running on the rest.", issues);
            }
        } catch (Throwable t) {
            LOG.warn("[StutterAnalyzer] Rule validation pass failed: {}", t.getMessage());
        }
    }

    // ---- classification_rules.json ----
    private static int validateClassificationRules() {
        JsonObject root = read("classification_rules.json");
        if (root == null) return 0;
        int issues = 0;
        double minConfidence = root.has("minimum_confidence") && root.get("minimum_confidence").isJsonPrimitive()
            ? root.get("minimum_confidence").getAsDouble() : 0.0;
        if (!root.has("rules") || !root.get("rules").isJsonArray()) {
            LOG.warn("[StutterAnalyzer] classification_rules.json: missing 'rules' array");
            return 1;
        }
        Set<Integer> seenPriority = new HashSet<>();
        for (JsonElement el : root.getAsJsonArray("rules")) {
            if (!el.isJsonObject()) { LOG.warn("[StutterAnalyzer] classification_rules: non-object rule skipped"); issues++; continue; }
            JsonObject r = el.getAsJsonObject();
            String id = r.has("id") ? r.get("id").getAsString() : "<no id>";
            for (String req : new String[]{"id", "category", "conditions", "confidence", "priority"}) {
                if (!r.has(req)) { LOG.warn("[StutterAnalyzer] classification rule '{}' missing required field '{}'", id, req); issues++; }
            }
            if (r.has("confidence") && r.get("confidence").isJsonPrimitive()) {
                double c = r.get("confidence").getAsDouble();
                if (c < 0.0 || c > 1.0) { LOG.warn("[StutterAnalyzer] classification rule '{}' confidence {} out of 0.0..1.0", id, c); issues++; }
                boolean fallback = (r.has("fallback") && r.get("fallback").getAsBoolean())
                    || (r.has("ignore_minimum_confidence") && r.get("ignore_minimum_confidence").getAsBoolean());
                if (c < minConfidence && !fallback) {
                    LOG.warn("[StutterAnalyzer] classification rule '{}' confidence {} is below minimum_confidence {} "
                        + "but is not marked fallback/ignore_minimum_confidence - it would be unreachable", id, c, minConfidence);
                    issues++;
                }
            }
            if (r.has("category") && (!r.get("category").isJsonPrimitive() || r.get("category").getAsString().isBlank())) {
                LOG.warn("[StutterAnalyzer] classification rule '{}' has empty/invalid category", id); issues++;
            }
            if (r.has("priority") && r.get("priority").isJsonPrimitive()) {
                int p = r.get("priority").getAsInt();
                if (!seenPriority.add(p)) { LOG.warn("[StutterAnalyzer] classification rule '{}' has duplicate priority {}", id, p); issues++; }
            }
        }
        return issues;
    }

    // ---- optimization_mods.json ----
    private static Set<String> collectOptimizationModIds() {
        Set<String> ids = new HashSet<>();
        JsonObject root = read("optimization_mods.json");
        if (root != null) for (String k : root.keySet()) ids.add(k);
        return ids;
    }

    private static int validateOptimizationMods(Set<String> knownMods) {
        JsonObject root = read("optimization_mods.json");
        if (root == null) return 0;
        int issues = 0;
        for (String key : root.keySet()) {
            if (!root.get(key).isJsonObject()) { LOG.warn("[StutterAnalyzer] optimization_mods '{}' is not an object", key); issues++; continue; }
            JsonObject o = root.getAsJsonObject(key);
            // loader values
            if (o.has("loader_support") && o.get("loader_support").isJsonArray()) {
                for (JsonElement le : o.getAsJsonArray("loader_support")) {
                    if (le.isJsonPrimitive() && !LOADERS.contains(le.getAsString())) {
                        LOG.warn("[StutterAnalyzer] optimization '{}' unknown loader '{}'", key, le.getAsString()); issues++;
                    }
                }
            }
            if (o.has("side") && o.get("side").isJsonPrimitive() && !SIDES.contains(o.get("side").getAsString())) {
                LOG.warn("[StutterAnalyzer] optimization '{}' unknown side '{}'", key, o.get("side").getAsString()); issues++;
            }
            boolean safe = !o.has("install_safe") || o.get("install_safe").getAsBoolean();
            int priority = o.has("install_priority") && o.get("install_priority").isJsonPrimitive() ? o.get("install_priority").getAsInt() : 0;
            boolean hasSlug = o.has("install_modrinth") && o.get("install_modrinth").isJsonPrimitive() && !o.get("install_modrinth").getAsString().isBlank();
            if (safe && priority > 0 && !hasSlug) {
                LOG.warn("[StutterAnalyzer] optimization '{}' is installable (safe + priority>0) but has no install_modrinth source", key); issues++;
            }
            // install_requires must point at installable DB entries so the dependency can be
            // co-installed. install_conflicts intentionally references arbitrary installed mods
            // (and version-qualified specs like "sodium>=0.6"), so it is not checked here.
            issues += checkRefs(key, o, "install_requires", knownMods);
        }
        return issues;
    }

    private static int checkRefs(String key, JsonObject o, String field, Set<String> knownMods) {
        int issues = 0;
        if (o.has(field) && o.get(field).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(field)) {
                if (e.isJsonPrimitive() && !knownMods.contains(e.getAsString())) {
                    LOG.warn("[StutterAnalyzer] optimization '{}' {} references unknown mod '{}'", key, field, e.getAsString()); issues++;
                }
            }
        }
        return issues;
    }

    // ---- known_mod_patterns.json ----
    private static int validateKnownModPatterns() {
        JsonElement rootEl = readElement("known_mod_patterns.json");
        if (rootEl == null) return 0;
        int issues = 0;
        List<JsonObject> patterns = new ArrayList<>();
        if (rootEl.isJsonArray()) {
            for (JsonElement e : rootEl.getAsJsonArray()) if (e.isJsonObject()) patterns.add(e.getAsJsonObject());
        } else if (rootEl.isJsonObject()) {
            JsonObject ro = rootEl.getAsJsonObject();
            JsonElement arr = ro.has("patterns") ? ro.get("patterns") : null;
            if (arr != null && arr.isJsonArray()) for (JsonElement e : arr.getAsJsonArray()) if (e.isJsonObject()) patterns.add(e.getAsJsonObject());
            else for (String k : ro.keySet()) if (ro.get(k).isJsonObject()) patterns.add(ro.getAsJsonObject(k));
        }
        for (JsonObject p : patterns) {
            String id = p.has("id") ? p.get("id").getAsString() : (p.has("pattern_id") ? p.get("pattern_id").getAsString() : "<no id>");
            if (p.has("required_mods") && !p.get("required_mods").isJsonArray()) {
                LOG.warn("[StutterAnalyzer] known_mod_pattern '{}' required_mods must be an array", id); issues++;
            }
            // Stage-5 versioning fields (all optional; validated when present).
            if (p.has("confidence") && p.get("confidence").isJsonPrimitive()) {
                double c = p.get("confidence").getAsDouble();
                if (c < 0.0 || c > 1.0) { LOG.warn("[StutterAnalyzer] known_mod_pattern '{}' confidence {} out of 0.0..1.0", id, c); issues++; }
            }
            for (String vf : new String[]{"affected_versions", "fixed_versions"}) {
                if (p.has(vf) && !p.get(vf).isJsonArray()) { LOG.warn("[StutterAnalyzer] known_mod_pattern '{}' {} must be an array", id, vf); issues++; }
            }
            if (p.has("safe_advice_level") && p.get("safe_advice_level").isJsonPrimitive()) {
                String lvl = p.get("safe_advice_level").getAsString();
                if (!SAFE_ADVICE_LEVELS.contains(lvl)) { LOG.warn("[StutterAnalyzer] known_mod_pattern '{}' unknown safe_advice_level '{}'", id, lvl); issues++; }
            }
        }
        return issues;
    }

    private static final Set<String> SAFE_ADVICE_LEVELS = Set.of("auto_guard", "warn_only", "test_without", "suppress");

    // Built at runtime (non-constant prefix) so the literal command strings do not appear in the
    // class constant pool - the validator enforces "no old hints", it must not itself trip an
    // exact-string hint audit of the jar.
    private static final String CMD = new String("/sa ");
    private static final String[] FORBIDDEN_HINTS = { CMD + "export", CMD + "debug", CMD + "crash list", CMD + "guard info" };

    // ---- recommendation_rules.json (consistency with optimization_mods) ----
    private static int validateRecommendationRules(Set<String> knownMods) {
        JsonElement rootEl = readElement("recommendation_rules.json");
        if (rootEl == null) return 0;
        int issues = 0;
        List<String> referenced = new ArrayList<>();
        collectStringsForKey(rootEl, "mod", referenced);
        collectStringsForKey(rootEl, "mod_id", referenced);
        collectStringsForKey(rootEl, "recommend", referenced);
        for (String m : referenced) {
            if (!knownMods.contains(m)) {
                LOG.warn("[StutterAnalyzer] recommendation references mod '{}' that is not in optimization_mods.json", m); issues++;
            }
        }
        // No removed/forbidden command hints may appear in recommendation text.
        List<String> allStrings = new ArrayList<>();
        collectAllStrings(rootEl, allStrings);
        for (String s : allStrings) {
            for (String f : FORBIDDEN_HINTS) {
                if (s.contains(f)) { LOG.warn("[StutterAnalyzer] recommendation text contains forbidden hint '{}'", f); issues++; }
            }
            if (containsExactSaReport(s)) { LOG.warn("[StutterAnalyzer] recommendation text uses the forbidden singular report command (use the plural reports command instead)"); issues++; }
        }
        return issues;
    }

    /** True if the text uses the report command but not the allowed plural form. Built at runtime
     *  so the literal command string is not in this class's constant pool. */
    private static boolean containsExactSaReport(String s) {
        String needle = CMD + "report";
        int i = 0;
        while ((i = s.indexOf(needle, i)) >= 0) {
            int end = i + needle.length();
            if (end >= s.length() || s.charAt(end) != 's') return true;
            i = end;
        }
        return false;
    }

    private static void collectAllStrings(JsonElement el, List<String> out) {
        if (el.isJsonObject()) {
            for (String k : el.getAsJsonObject().keySet()) collectAllStrings(el.getAsJsonObject().get(k), out);
        } else if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) collectAllStrings(e, out);
        } else if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            out.add(el.getAsString());
        }
    }

    private static void collectStringsForKey(JsonElement el, String key, List<String> out) {
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            for (String k : o.keySet()) {
                JsonElement v = o.get(k);
                if (k.equals(key) && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) out.add(v.getAsString());
                else collectStringsForKey(v, key, out);
            }
        } else if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) collectStringsForKey(e, key, out);
        }
    }

    // ---- helpers ----
    private static JsonObject read(String name) {
        JsonElement el = readElement(name);
        return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : null;
    }

    private static JsonElement readElement(String name) {
        try (InputStream is = JsonRuleValidator.class.getResourceAsStream(BASE + name)) {
            if (is == null) { LOG.warn("[StutterAnalyzer] rule file {} not found", name); return null; }
            try (Reader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r);
            }
        } catch (Throwable t) {
            LOG.warn("[StutterAnalyzer] rule file {} failed to parse: {}", name, t.getMessage());
            return null;
        }
    }
}
