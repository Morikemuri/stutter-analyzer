package com.stutteranalyzer.client;

import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.core.SubsystemHealth;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class F3StatusLineRenderer {

    private static boolean failed = false;

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (failed) return;
        if (event.getSide() != CustomizeGuiOverlayEvent.DebugText.Side.Left) return;
        try {
            if (!DebugHudStatusProvider.isF3Enabled()) return;
            event.getText().add(F3StatusFormatter.format());
        } catch (Throwable t) {
            failed = true;
            StutterAnalyzerMod.LOGGER.error("[StutterAnalyzer] F3StatusLineRenderer failed, disabling: {}", t.getMessage());
            SubsystemHealth.setStatus("F3StatusLineRenderer", SubsystemHealth.Status.FAILED, t.getMessage());
        }
    }
}
