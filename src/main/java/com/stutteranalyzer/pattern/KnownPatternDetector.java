package com.stutteranalyzer.pattern;

import com.stutteranalyzer.knowledge.ModInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Matches crash report text, stack traces, and log fragments against known patterns.
 * Returns a list of PatternMatch results sorted by confidence (highest first).
 * Never blames a mod without evidence. We are debugging, not starting a Reddit war.
 */
public class KnownPatternDetector {

    public static final class PatternMatch {
        public final String patternId;
        public final double confidence;
        public final String reason;
        public PatternMatch(String patternId, double confidence, String reason) {
            this.patternId  = patternId;
            this.confidence = confidence;
            this.reason     = reason;
        }
        public int confidencePct() { return (int) Math.round(confidence * 100); }
    }

    public static List<PatternMatch> detect(String crashText, String logText) {
        List<PatternMatch> matches = new ArrayList<>();

        // ── RUBIDIUM_LAVA_FLUID_RENDER_CRASH ──────────────────────────────
        if (matchesRubidiumLava(crashText, logText))
            matches.add(new PatternMatch("RUBIDIUM_LAVA_FLUID_RENDER_CRASH", computeRubidiumLavaConfidence(crashText, logText),
                buildRubidiumLavaReason(crashText, logText)));

        // ── DYNAMIC_FPS_BACKGROUND_FALSE_POSITIVE ─────────────────────────
        // (handled at runtime in FreezeDetector, not crash-based)

        // ── C2ME_CORRUPTED_CHUNK_CONTEXT ──────────────────────────────────
        if (ModInventory.isInstalled("c2me") && containsAny(crashText + logText,
                "chunk loading", "corrupted chunk", "region file", "c2me", "async chunk"))
            matches.add(new PatternMatch("C2ME_CORRUPTED_CHUNK_CONTEXT", 0.65,
                "C2ME is installed and crash/hang mentions chunk loading or region file."));

        // ── C2ME_WORLDGEN_DEADLOCK_CONTEXT ────────────────────────────────
        if (ModInventory.isInstalled("c2me") && containsAny(crashText + logText,
                "deadlock", "completablefuture", "worldgen", "chunk future", "c2me worker"))
            matches.add(new PatternMatch("C2ME_WORLDGEN_DEADLOCK_CONTEXT", 0.60,
                "C2ME is installed and crash/log contains worldgen deadlock signals."));

        // ── IRIS_OCULUS_SHADER_PIPELINE_CONTEXT ───────────────────────────
        if ((ModInventory.isInstalled("iris") || ModInventory.isInstalled("oculus")) &&
            containsAny(crashText + logText, "shader", "compile", "glsl", "pipeline", "shaderpack"))
            matches.add(new PatternMatch("IRIS_OCULUS_SHADER_PIPELINE_CONTEXT", 0.70,
                "Iris/Oculus installed and crash/log contains shader pipeline signals."));

        // ── EMBEDDIUM_OCULUS_TAINT_CONTEXT ────────────────────────────────
        if (ModInventory.isInstalled("embeddium") && ModInventory.isInstalled("oculus") &&
            containsAny(crashText + logText, "tainted", "embeddium instance tainted"))
            matches.add(new PatternMatch("EMBEDDIUM_OCULUS_TAINT_CONTEXT", 0.80,
                "Crash contains 'tainted' and Embeddium+Oculus are both installed."));

        // ── CHUNK_ANIMATOR_RENDERER_MIXIN_CONFLICT ────────────────────────
        if (ModInventory.isInstalled("chunk_animator") &&
            containsAny(crashText + logText, "InvalidInjectionException", "WorldRendererMixin", "chunk rendering failed injection"))
            matches.add(new PatternMatch("CHUNK_ANIMATOR_RENDERER_MIXIN_CONFLICT", 0.75,
                "Chunk Animator is installed and crash contains mixin injection failure in chunk renderer."));

        matches.sort((a, b) -> Double.compare(b.confidence, a.confidence));
        return matches;
    }

    // ── Rubidium lava helpers ──────────────────────────────────────────────

    private static boolean matchesRubidiumLava(String crash, String log) {
        String combined = (crash + " " + log).toLowerCase();
        return (ModInventory.isInstalled("rubidium") || combined.contains("rubidium"))
            && containsAny(combined, "fluidrenderer", "chunkmeshbuildingtask", "chunkbuildermeshingtask", "textureatlasssprite")
            && containsAny(combined, "lava", "fluid");
    }

    private static double computeRubidiumLavaConfidence(String crash, String log) {
        String c = (crash + " " + log).toLowerCase();
        int score = 0;
        if (c.contains("fluidrenderer.render") || c.contains("fluidrenderer")) score += 25;
        if (c.contains("chunkmeshbuildingtask") || c.contains("chunkbuildermeshingtask")) score += 20;
        if (c.contains("textureatlasssprite") || c.contains("sprite is null")) score += 20;
        if (c.contains("lava") || c.contains("minecraft:lava")) score += 15;
        if (c.contains("rubidium") || ModInventory.isInstalled("rubidium")) score += 15;
        if (c.contains("encountered exception while building chunk meshes")) score += 5;
        return Math.min(1.0, score / 100.0);
    }

    private static String buildRubidiumLavaReason(String crash, String log) {
        String c = (crash + " " + log).toLowerCase();
        StringBuilder sb = new StringBuilder("Known pattern: Rubidium Lava Fluid Render Crash.\n");
        if (c.contains("fluidrenderer")) sb.append("- Stack trace contains FluidRenderer.\n");
        if (c.contains("chunkmeshbuildingtask") || c.contains("chunkbuildermeshingtask")) sb.append("- ChunkBuilderMeshingTask appears in stack trace.\n");
        if (c.contains("lava")) sb.append("- Context mentions lava.\n");
        if (c.contains("textureatlasssprite") || c.contains("sprite is null")) sb.append("- TextureAtlasSprite null pointer in fluid renderer.\n");
        return sb.toString().trim();
    }

    // ── Utilities ──────────────────────────────────────────────────────────

    private static boolean containsAny(String text, String... tokens) {
        String lower = text.toLowerCase();
        for (String t : tokens) if (lower.contains(t.toLowerCase())) return true;
        return false;
    }
}
