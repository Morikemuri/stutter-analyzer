package com.stutteranalyzer.client;

import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.core.SubsystemHealth;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Adds the SA status line to the F3 debug screen.
 * Registered on the Forge event bus (client only).
 * Reads only cached state - no expensive work here.
 * In Forge 47.x (1.20.1) CustomizeGuiOverlayEvent.DebugText fires every frame,
 * so options.renderDebug is required to gate rendering to F3-only.
 */
public class F3StatusLineRenderer {

    private static boolean failed = false;

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (failed) return;
        try {
            if (!Minecraft.getInstance().options.renderDebug) return;
            if (!DebugHudStatusProvider.isF3Enabled()) return;
            event.getLeft().add(F3StatusFormatter.format());
        } catch (Throwable t) {
            failed = true;
            StutterAnalyzerMod.LOGGER.error("[StutterAnalyzer] F3StatusLineRenderer failed, disabling: {}", t.getMessage());
            SubsystemHealth.setStatus("F3StatusLineRenderer", SubsystemHealth.Status.FAILED, t.getMessage());
        }
    }
}
