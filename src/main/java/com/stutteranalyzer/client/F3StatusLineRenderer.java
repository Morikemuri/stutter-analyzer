package com.stutteranalyzer.client;

import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.core.SubsystemHealth;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Adds the SA status line to the F3 debug screen.
 * Registered on the Forge event bus (client only).
 * Reads only cached state - no expensive work here.
 * Note: CustomizeGuiOverlayEvent.DebugText fires only when debug screen is visible.
 */
public class F3StatusLineRenderer {

    private static boolean failed = false;

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (failed) return;
        try {
            if (!DebugHudStatusProvider.isF3Enabled()) return;
            event.getLeft().add(F3StatusFormatter.format());
        } catch (Throwable t) {
            failed = true;
            StutterAnalyzerMod.LOGGER.error("[StutterAnalyzer] F3StatusLineRenderer failed, disabling: {}", t.getMessage());
            SubsystemHealth.setStatus("F3StatusLineRenderer", SubsystemHealth.Status.FAILED, t.getMessage());
        }
    }
}
