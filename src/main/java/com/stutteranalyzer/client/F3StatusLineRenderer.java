package com.stutteranalyzer.client;

import com.stutteranalyzer.StutterAnalyzerNeo;
import com.stutteranalyzer.core.SubsystemHealth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

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
            StutterAnalyzerNeo.LOGGER.error("[StutterAnalyzer] F3StatusLineRenderer failed, disabling: {}", t.getMessage());
            SubsystemHealth.setStatus("F3StatusLineRenderer", SubsystemHealth.Status.FAILED, t.getMessage());
        }
    }
}
