package com.stutteranalyzer.guard;

import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.guard.rubidium.RubidiumLavaCrashGuard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EmergencyGuardManager {

    private static final List<EmergencyGuard> guards = new ArrayList<>();
    private static final List<EmergencyGuardReport> reports = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, Boolean> enabledMap = new ConcurrentHashMap<>();
    private static EmergencyGuardReport lastReport = null;

    static {
        register(new RubidiumLavaCrashGuard());
    }

    public static void register(EmergencyGuard guard) {
        guards.add(guard);
        enabledMap.put(guard.patternId(), guard.isEnabled());
    }

    public static EmergencyGuardResult evaluate(GuardContext ctx) {
        if (!SAConfig.INSTANCE.guardEnabled.get()) return EmergencyGuardResult.noMatch();
        if (!GuardWarningRateLimiter.allowTrigger()) return EmergencyGuardResult.noMatch();

        for (EmergencyGuard guard : guards) {
            if (!isEnabled(guard.patternId())) continue;
            if (!guard.matches(ctx)) continue;

            EmergencyGuard.SafetyLevel level = GuardConfig.levelFor(guard.patternId());
            if (level == EmergencyGuard.SafetyLevel.DISABLED) continue;

            double minConf = SAConfig.INSTANCE.minimumAutoGuardConfidence.get();
            double warnConf = SAConfig.INSTANCE.minimumWarnConfidence.get();

            boolean emergencyMode = SAConfig.INSTANCE.guardEmergencyMode.get();

            // Compute confidence from token matching. No guessing, no drama.
            double confidence = estimateConfidence(guard, ctx);

            if (level == EmergencyGuard.SafetyLevel.SAFE_AUTO_GUARD && emergencyMode && confidence >= minConf) {
                EmergencyGuardResult result = guard.apply(ctx);
                recordReport(guard.patternId(), result);
                StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Guard triggered: {} - {}", guard.patternId(), result.logMessage);
                return result;
            } else if (confidence >= warnConf) {
                EmergencyGuardResult result = EmergencyGuardResult.warnOnly(guard.patternId(), confidence,
                    "[Stutter Analyzer] Known pattern detected: " + guard.patternId() + ". Emergency mode is " +
                        (emergencyMode ? "enabled but confidence too low" : "disabled") + ". No workaround applied. See crash hint.",
                    "[StutterAnalyzer] Pattern detected (warn only): " + guard.patternId() + " (" + (int)(confidence*100) + "%).");
                recordReport(guard.patternId(), result);
                return result;
            } else if (confidence > 0) {
                return EmergencyGuardResult.confidenceTooLow(guard.patternId(), confidence);
            }
        }
        return EmergencyGuardResult.noMatch();
    }

    private static double estimateConfidence(EmergencyGuard guard, GuardContext ctx) {
        // Use pattern detector tokens for confidence estimation
        String combined = ctx.combined();
        switch (guard.patternId()) {
            case "RUBIDIUM_LAVA_FLUID_RENDER_CRASH" -> {
                int score = 0;
                if (combined.contains("fluidrenderer")) score += 25;
                if (combined.contains("chunkmeshbuildingtask") || combined.contains("chunkbuildermeshingtask")) score += 20;
                if (combined.contains("textureatlasssprite") || combined.contains("sprite is null")) score += 20;
                if (combined.contains("lava")) score += 15;
                if (combined.contains("rubidium")) score += 15;
                if (combined.contains("encountered exception while building chunk meshes")) score += 5;
                return Math.min(1.0, score / 100.0);
            }
            default -> { return 0.5; }
        }
    }

    private static void recordReport(String guardId, EmergencyGuardResult result) {
        EmergencyGuardReport rep = new EmergencyGuardReport(guardId, result);
        reports.add(rep);
        lastReport = rep;
    }

    public static boolean isEnabled(String patternId) {
        return enabledMap.getOrDefault(patternId, true);
    }

    public static void setEnabled(String patternId, boolean enabled) {
        enabledMap.put(patternId, enabled);
        for (EmergencyGuard g : guards) {
            if (g.patternId().equals(patternId)) g.setEnabled(enabled);
        }
    }

    public static List<EmergencyGuard> allGuards() { return Collections.unmodifiableList(guards); }
    public static List<EmergencyGuardReport> allReports() { return Collections.unmodifiableList(reports); }
    public static EmergencyGuardReport lastReport() { return lastReport; }
}
