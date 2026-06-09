package com.stutteranalyzer.client;

/**
 * F3 debug line is injected via DebugHudMixin into DebugScreenOverlay.drawGameInformation().
 * This class is reserved for a future optional always-on overlay (controlled by /sa overlay).
 * Do not register HudRenderCallback here for the F3 line.
 */
public class FabricHudRenderer {
    private FabricHudRenderer() {}
}
