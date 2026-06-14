package io.github.morikemuri.stutteranalyzer.classifier;

import io.github.morikemuri.stutteranalyzer.config.SAConfig;
import io.github.morikemuri.stutteranalyzer.events.RecentEventBuffer;
import io.github.morikemuri.stutteranalyzer.metrics.FrameTimeTracker;
import io.github.morikemuri.stutteranalyzer.metrics.MemoryGcTracker;
import io.github.morikemuri.stutteranalyzer.metrics.ServerTickTracker;
import io.github.morikemuri.stutteranalyzer.report.FreezeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FreezeClassifier {

    private final PeriodicityTracker periodicityTracker = new PeriodicityTracker();

    public FreezeEvent classify(
            long durationMs,
            boolean isClient,
            boolean isDedicatedServer,
            FrameTimeTracker frame,
            ServerTickTracker tick,
            MemoryGcTracker mem,
            List<RecentEventBuffer.GameEvent> recentEvents) {

        FreezeCategory category = FreezeCategory.UNKNOWN_FREEZE;
        double confidence = 0.0;
        String reason = "No pattern matched.";
        String evidence = "";
        String recommendation = "Reproduce the freeze, then send a report with /sa submit.";
        FreezeEvent.PeriodicMeta periodicMeta = null;

        double minConf = SAConfig.INSTANCE.minimumConfidence.get();

        // GC pause check
        if (mem.recentGc() && mem.lastGcPauseMs() >= durationMs * 0.5) {
            confidence = 0.80;
            category = FreezeCategory.GARBAGE_COLLECTION;
            reason = "GC pause duration matches freeze duration.";
            evidence = "GC pause ~" + mem.lastGcPauseMs() + " ms, heap at " + String.format("%.0f", mem.heapPercent()) + "%.";
            recommendation = "Increase JVM heap, reduce allocation rate, check GC log.";
        }
        // Memory pressure check
        else if (mem.heapPercent() > 90) {
            confidence = 0.70;
            category = FreezeCategory.MEMORY_PRESSURE;
            reason = "Heap usage above 90%.";
            evidence = "Heap: " + mem.heapUsedMb() + "/" + mem.heapMaxMb() + " MB.";
            recommendation = "Increase -Xmx, add FerriteCore/ModernFix, reduce loaded chunks.";
        }
        // Server tick spike on non-client
        else if (!isClient && tick.currentMspt() >= SAConfig.INSTANCE.warningMspt.get()) {
            double mspt = tick.currentMspt();
            if (mspt >= SAConfig.INSTANCE.extremeMspt.get()) confidence = 0.85;
            else if (mspt >= SAConfig.INSTANCE.severeMspt.get()) confidence = 0.75;
            else confidence = 0.65;
            category = FreezeCategory.SERVER_TICK_SPIKE;
            reason = "Server MSPT exceeded threshold.";
            evidence = "MSPT: " + String.format("%.1f", mspt) + " ms (avg " + String.format("%.1f", tick.rollingAvgMspt()) + " ms), TPS ~" + String.format("%.1f", tick.tpsEstimate()) + ".";
            recommendation = "Use spark profiler to identify the ticking hotspot.";
        }
        // Client render stutter
        else if (isClient && frame.currentFrameMs() >= SAConfig.INSTANCE.minorFrameMs.get()) {
            double frameMs = frame.currentFrameMs();
            if (frameMs >= SAConfig.INSTANCE.extremeFrameMs.get()) confidence = 0.70;
            else if (frameMs >= SAConfig.INSTANCE.severeFrameMs.get()) confidence = 0.65;
            else confidence = 0.55;
            category = FreezeCategory.CLIENT_RENDER_STUTTER;
            reason = "Client frame time exceeded threshold.";
            evidence = "Frame time: " + String.format("%.1f", frameMs) + " ms.";
            recommendation = "Check GPU usage, reduce render distance, disable shaders to test.";
        }
        // Recent events pattern matching
        else {
            for (RecentEventBuffer.GameEvent evt : recentEvents) {
                switch (evt.type) {
                    case CHUNK_GENERATION_START, CHUNK_GENERATION_END -> {
                        category = FreezeCategory.CHUNK_GENERATION;
                        confidence = 0.65;
                        reason = "Chunk generation event near freeze.";
                        evidence = evt.toString();
                        recommendation = "Pregenerate the world; try reducing view distance.";
                    }
                    case CHUNK_LOAD -> {
                        category = FreezeCategory.CHUNK_LOADING;
                        confidence = 0.60;
                        reason = "Chunk load event near freeze.";
                        evidence = evt.toString();
                        recommendation = "Reduce view distance or add a chunk loading mod.";
                    }
                    case RESOURCE_RELOAD_START, RESOURCE_RELOAD_END -> {
                        category = FreezeCategory.RESOURCE_RELOAD;
                        confidence = 0.78;
                        reason = "Resource reload event near freeze.";
                        evidence = evt.toString();
                        recommendation = "Expected during resource pack reload. No action needed.";
                    }
                    case SHADER_RELOAD_START, SHADER_RELOAD_END -> {
                        category = FreezeCategory.SHADER_OR_RESOURCE_PACK_RELOAD;
                        confidence = 0.80;
                        reason = "Shader reload event near freeze.";
                        evidence = evt.toString();
                        recommendation = "Expected during shader reload. Update Iris/Oculus if frequent.";
                    }
                    case SERVER_SAVE_START, SERVER_SAVE_END -> {
                        category = FreezeCategory.WORLD_SAVE;
                        confidence = 0.72;
                        reason = "World save event near freeze.";
                        evidence = evt.toString();
                        recommendation = "Enable async chunk saving if available.";
                    }
                    case PLAYER_JOIN -> {
                        category = FreezeCategory.PLAYER_JOIN;
                        confidence = 0.62;
                        reason = "Player join event near freeze.";
                        evidence = evt.toString();
                        recommendation = "Use Krypton/Pluto to reduce join overhead.";
                    }
                    case TELEPORT, DIMENSION_CHANGE -> {
                        category = FreezeCategory.PLAYER_TELEPORT_OR_DIMENSION_CHANGE;
                        confidence = 0.65;
                        reason = "Teleport/dimension change event near freeze.";
                        evidence = evt.toString();
                        recommendation = "Expected for cross-dimension travel. Reduce chunk loading radius.";
                    }
                    default -> {}
                }
                if (confidence > 0) break;
            }
        }

        // Micro-hitch detection: 50-99ms events
        if (confidence < minConf && durationMs >= 50 && durationMs < 100) {
            periodicityTracker.record(durationMs);
            PeriodicityTracker.PeriodicResult periodic = periodicityTracker.detect(durationMs);
            if (periodic != null && periodic.isScheduled()) {
                // Matches a known scheduler interval (5s/10s/30s/60s/120s/300s)
                category = FreezeCategory.PERIODIC_SCHEDULED_MICRO_HITCH;
                confidence = 0.70;
                reason = "Repeated minor hitch at a stable ~" + formatIntervalS(periodic.periodMsEstimate()) + " interval matching a common scheduler period.";
                evidence = periodic.occurrences() + " hitches of ~" + durationMs + "ms each, repeating every ~" + formatIntervalS(periodic.periodMsEstimate()) + ".";
                recommendation = "Usually harmless. A small hitch repeats at a scheduled interval. If noticeable, try disabling overlays, skin/cape polling mods, or launcher cosmetic features one by one.";
                periodicMeta = new FreezeEvent.PeriodicMeta(periodic.periodMsEstimate(), periodic.occurrences(), true, "UNCLASSIFIED_MICRO_HITCH", detectPossibleContext());
            } else if (periodic != null) {
                // Regular interval but not a known scheduler period
                category = FreezeCategory.PERIODIC_MINOR_MICRO_HITCH;
                confidence = 0.65;
                reason = "Repeated minor hitch detected at a stable interval.";
                evidence = periodic.occurrences() + " ~" + durationMs + "ms hitches at near-regular " + periodic.periodMsEstimate() + "ms intervals.";
                recommendation = "Usually harmless. Check background polling tasks only if it becomes frequent or noticeable.";
                periodicMeta = new FreezeEvent.PeriodicMeta(periodic.periodMsEstimate(), periodic.occurrences(), true, "UNCLASSIFIED_MICRO_HITCH", java.util.List.of("UNKNOWN_SCHEDULED_TASK"));
            } else {
                // Not enough history yet
                category = FreezeCategory.UNCLASSIFIED_MICRO_HITCH;
                confidence = 0.50;
                reason = "Minor frame-time spike detected, but no reliable pattern matched yet.";
                evidence = (isClient ? "Frame hitch" : "Tick spike") + ": " + durationMs + " ms.";
                recommendation = "Usually harmless. Monitor for recurrence.";
            }
        } else if (confidence < minConf && durationMs >= 100 && durationMs < 250) {
            // Medium-range hitch (100-249ms) - do not call it UNKNOWN_FREEZE
            category = FreezeCategory.UNCLASSIFIED_FRAME_HITCH;
            confidence = 0.50;
            reason = "Medium frame hitch with no matching pattern.";
            evidence = "Hitch duration: " + durationMs + " ms.";
            recommendation = "If this recurs, check GPU load and server tick around that time.";
        } else if (confidence < minConf && SAConfig.INSTANCE.unknownFreezeEnabled.get()) {
            // Only use UNKNOWN_FREEZE for events >= 250ms that truly have no pattern
            category = FreezeCategory.UNKNOWN_FREEZE;
            reason = "No pattern matched with sufficient confidence (score " + String.format("%.0f", confidence * 100) + "% < minimum " + String.format("%.0f", minConf * 100) + "%).";
            recommendation = "Reproduce the freeze, then send a report with /sa submit.";
        }

        // Secondary log-context pass: for UNKNOWN_FREEZE >= 250ms, scan latest.log for
        // watchdog/C2ME/GC patterns and promote to SERVER_TICK_SPIKE when evidence is strong.
        if (category == FreezeCategory.UNKNOWN_FREEZE && durationMs >= 250) {
            LogContextClassifier.ContextResult ctx = LogContextClassifier.detectContext();
            if (ctx.hasStrongServerTickEvidence()) {
                category   = FreezeCategory.SERVER_TICK_SPIKE;
                confidence = 0.70;
                reason     = "Server tick spike inferred from log evidence (log-context classifier).";
                List<String> evParts = new ArrayList<>();
                if (ctx.watchdogEvidence() || ctx.serverTickSpikeEvidence())
                    evParts.add("Watchdog/server overload warnings detected");
                if (ctx.c2meWorldgenEvidence())
                    evParts.add("Possible contributing context: C2ME/worldgen thread pressure");
                if (ctx.gcPauseEvidence())
                    evParts.add("GC pause near freeze detected");
                evidence       = String.join(". ", evParts) + ".";
                recommendation = buildContextRecommendation(ctx);
            } else if (ctx.evidenceScore() >= 2) {
                // Keep UNKNOWN_FREEZE but enrich with observed contributing factors
                List<String> evParts = new ArrayList<>();
                if (ctx.serverTickSpikeEvidence()) evParts.add("server tick spike signals");
                if (ctx.c2meWorldgenEvidence())    evParts.add("C2ME/worldgen chunk pressure");
                if (ctx.gcPauseEvidence())         evParts.add("GC pause near freeze");
                evidence       = "Contributing factors detected: " + String.join("; ", evParts) + ".";
                recommendation = buildContextRecommendation(ctx);
            }
        }

        // Additive log-signal pass: only refine when nothing else matched, so it never
        // overrides a confident classification. Wording stays cautious ("Possible") on purpose.
        if (category == FreezeCategory.UNKNOWN_FREEZE && durationMs >= 100) {
            String tail = LogTailCache.tailLower(resolveLogFileForContext());
            if (isClient && containsAny(tail, "iris", "oculus", "shaderpack", "compiling shader",
                    "shader compilation", "program compile", "pipeline compile", "shader warmup",
                    "better clouds", "euphoria patcher")) {
                category = FreezeCategory.SHADER_COMPILATION;
                confidence = 0.62;
                reason = "Possible shader compilation or warmup near the freeze.";
                evidence = "Shader-related activity (Iris/Oculus/shader pipeline) seen in the recent log.";
                recommendation = "Possible cause: shader compilation/warmup. Try disabling shaders first; test without Better Clouds / Euphoria Patcher. If it only happens on first launch or first world join with shaders, it is likely one-time warmup.";
            } else if (containsAny(tail, "saving chunks", "world save", "ms to save", "saving the world",
                    "slow save", "file watcher", "took too long to save")) {
                category = FreezeCategory.DISK_IO;
                confidence = 0.60;
                reason = "Possible disk I/O stall near the freeze.";
                evidence = "Save/load or file activity seen in the recent log around the freeze.";
                recommendation = "Possible cause: slow disk I/O. Likely fixes: move the instance to an SSD, check disk health, avoid background indexing/scanning, and consider excluding the Minecraft instance folder from antivirus scanning.";
            } else if (containsAny(tail, "ticking entity", "entities loaded", "pathfind", "spawner",
                    "too many entities", "entity count")) {
                category = FreezeCategory.ENTITY_OVERLOAD;
                confidence = 0.60;
                reason = "Possible entity overload near the freeze.";
                evidence = "Entity ticking / pathfinding / spawner activity seen in the recent log.";
                recommendation = "Possible cause: too many ticking entities. Likely checks: look at the entity count, test entity-heavy mods, use spark or Observable if available, and reduce mob farms/spawners.";
            }
        }

        String side = isClient
            ? (isDedicatedServer ? "dedicated server" : "client-side")
            : (isDedicatedServer ? "dedicated server" : "integrated server");

        HighLevelClassifier.HighLevelResult hlResult = HighLevelClassifier.classify(category, recentEvents);
        return new FreezeEvent(category, confidence, reason, evidence, side, durationMs, recentEvents, recommendation, hlResult, periodicMeta);
    }

    private static String formatIntervalS(long ms) {
        long s = ms / 1000L;
        return s + "s";
    }

    private static boolean containsAny(String tail, String... tokens) {
        if (tail == null || tail.isEmpty()) return false;
        for (String t : tokens) {
            if (tail.contains(t)) return true;
        }
        return false;
    }


    private static List<String> detectPossibleContext() {
        List<String> ctx = new ArrayList<>();
        try {
            String tail = LogTailCache.tailLower(resolveLogFileForContext(), 32768);
            if (!tail.isEmpty()) {
                if (matchesContext(tail, new String[]{"skin", "cape", "tlskins", "sessionserver.mojang", "skins.minecraft"}))
                    ctx.add("SKIN_OR_CAPE_POLLING");
                if (matchesContext(tail, new String[]{"stutteranalyzer", "stutter analyzer", "update check", "checking update"}))
                    ctx.add("ANALYZER_UPDATE_CHECK");
                if (matchesContext(tail, new String[]{"saving chunks", "autosave", "world save", "auto-save", "chunk save"}))
                    ctx.add("WORLD_SAVE_OR_CHUNK_IO");
            }
        } catch (Exception ignored) {}
        ctx.add(0, "UNKNOWN_SCHEDULED_TASK");
        return ctx;
    }

    private static boolean matchesContext(String text, String[] patterns) {
        for (String p : patterns) { if (text.contains(p)) return true; }
        return false;
    }

    private static Path resolveLogFileForContext() {
        try {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                try {
                    return net.minecraft.client.Minecraft.getInstance()
                        .gameDirectory.toPath().resolve("logs/latest.log");
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return FMLPaths.GAMEDIR.get().resolve("logs/latest.log");
    }

    private String buildContextRecommendation(LogContextClassifier.ContextResult ctx) {
        List<String> recs = new ArrayList<>();
        if (ctx.watchdogEvidence() || ctx.serverTickSpikeEvidence())
            recs.add("Use spark profiler to find the server tick hotspot");
        if (ctx.c2meWorldgenEvidence()) {
            recs.add("Check C2ME thread count settings; reduce worldgen threads if unstable");
            recs.add("If Distant Horizons is installed, check LOD generation settings");
            recs.add("Investigate mods generating or loading chunks aggressively");
        }
        if (ctx.gcPauseEvidence())
            recs.add("Increase JVM heap size (-Xmx8G or more) and review GC settings");
        if (recs.isEmpty())
            return "Reproduce the freeze, then send a report with /sa submit.";
        return String.join(". ", recs) + ".";
    }
}

