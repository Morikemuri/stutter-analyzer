package com.stutteranalyzer.guard;

import com.stutteranalyzer.config.SAConfig;

public class GuardConfig {

    public static EmergencyGuard.SafetyLevel levelFor(String patternId) {
        String val = switch (patternId) {
            case "RUBIDIUM_LAVA_FLUID_RENDER_CRASH"       -> SAConfig.INSTANCE.guardRubidiumLava.get();
            case "DYNAMIC_FPS_BACKGROUND_FALSE_POSITIVE"  -> SAConfig.INSTANCE.guardDynamicFps.get();
            case "C2ME_CORRUPTED_CHUNK_CONTEXT"           -> SAConfig.INSTANCE.guardC2meCorruptedChunk.get();
            case "C2ME_WORLDGEN_DEADLOCK_CONTEXT"         -> SAConfig.INSTANCE.guardC2meDeadlock.get();
            case "DISTANT_HORIZONS_LOD_STUTTER_OR_CACHE_IO" -> SAConfig.INSTANCE.guardDistantHorizons.get();
            case "IRIS_OCULUS_SHADER_PIPELINE_CONTEXT"    -> SAConfig.INSTANCE.guardIrisOculus.get();
            case "LIGHTING_ENGINE_OPTIMIZATION_CONTEXT"   -> SAConfig.INSTANCE.guardStarlightScalableLux.get();
            case "EMBEDDIUM_OCULUS_TAINT_CONTEXT"         -> SAConfig.INSTANCE.guardEmbeddiumOculus.get();
            case "CHUNK_ANIMATOR_RENDERER_MIXIN_CONFLICT" -> SAConfig.INSTANCE.guardChunkAnimator.get();
            default -> "warn_only";
        };
        return switch (val) {
            case "auto"        -> EmergencyGuard.SafetyLevel.SAFE_AUTO_GUARD;
            case "manual"      -> EmergencyGuard.SafetyLevel.MANUAL_GUARD_ONLY;
            case "report_only" -> EmergencyGuard.SafetyLevel.REPORT_ONLY;
            case "disabled"    -> EmergencyGuard.SafetyLevel.DISABLED;
            default            -> EmergencyGuard.SafetyLevel.WARN_ONLY;
        };
    }
}
