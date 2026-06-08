package com.stutteranalyzer.guard.rubidium;

import com.stutteranalyzer.guard.EmergencyGuard;
import com.stutteranalyzer.guard.EmergencyGuardResult;
import com.stutteranalyzer.guard.GuardContext;
import com.stutteranalyzer.knowledge.ModInventory;

/**
 * Guard: RUBIDIUM_LAVA_FLUID_RENDER_CRASH
 *
 * Safety: SAFE_AUTO_GUARD (client only).
 * Detects crash/exception during chunk mesh building involving Rubidium's FluidRenderer and lava.
 * Action: skip only the unsafe lava fluid render operation for the affected chunk mesh.
 *
 * NOT allowed:
 * - Remove lava or change block/fluid state
 * - Disable Rubidium or Oculus
 * - Catch all Throwable blindly
 * - Modify server/world data
 */
public class RubidiumLavaCrashGuard implements EmergencyGuard {

    private boolean enabled = true;

    @Override
    public String patternId() { return "RUBIDIUM_LAVA_FLUID_RENDER_CRASH"; }

    @Override
    public SafetyLevel safetyLevel() { return SafetyLevel.SAFE_AUTO_GUARD; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public boolean matches(GuardContext ctx) {
        if (!ctx.isClient) return false;
        if (ctx.isDedicatedServer) return false;

        boolean rubidiumPresent = ModInventory.isInstalled("rubidium") || ctx.combined().contains("rubidium");
        boolean fluidRendererInTrace = ctx.combined().contains("fluidrenderer");
        boolean chunkMeshContext = ctx.combined().contains("chunkmeshbuildingtask") || ctx.combined().contains("chunkbuildermeshingtask") || ctx.combined().contains("chunk meshes");
        boolean lavaContext = ctx.combined().contains("lava");

        return rubidiumPresent && fluidRendererInTrace && (chunkMeshContext || lavaContext);
    }

    @Override
    public EmergencyGuardResult apply(GuardContext ctx) {
        // The actual mixin that skips the lava render is in the mixin package.
        // This method records the action taken - the mixin itself prevents the crash.
        // World data is NOT modified. Only client-side rendering is skipped.
        return EmergencyGuardResult.prevented(
            patternId(),
            0.91,
            "Skipped one unsafe lava fluid render operation during chunk mesh building.",
            "[Stutter Analyzer] Emergency Guard prevented a likely Rubidium lava/fluid rendering crash.\n" +
            "This is only a temporary visual workaround.\n" +
            "Recommended: replace/update Rubidium, for example with Embeddium/Xenon if compatible.\n" +
            "Report saved.",
            "[StutterAnalyzer] Guard triggered: RUBIDIUM_LAVA_FLUID_RENDER_CRASH\n" +
            "Action: skipped one unsafe client-side lava fluid render operation.\n" +
            "World data was not modified.\n" +
            "Recommendation: replace/update Rubidium or use a compatible renderer."
        );
    }
}
