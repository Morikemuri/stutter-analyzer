package com.stutteranalyzer.client;

import com.stutteranalyzer.StutterAnalyzerFabric;
import com.stutteranalyzer.core.SubsystemHealth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class FabricHudRenderer {

    private static boolean failed = false;

    public static void onHudRender(GuiGraphics drawContext) {
        if (failed) return;
        try {
            if (!Minecraft.getInstance().gui.getDebugOverlay().showDebugScreen()) return;
            if (!DebugHudStatusProvider.isF3Enabled()) return;
            String line = F3StatusFormatter.format();
            drawContext.drawString(Minecraft.getInstance().font, line, 2, 2, 0xFFFFFF, true);
        } catch (Throwable t) {
            failed = true;
            StutterAnalyzerFabric.LOGGER.error("[SA] FabricHudRenderer failed, disabling: {}", t.getMessage());
            SubsystemHealth.setStatus("F3StatusLineRenderer", SubsystemHealth.Status.FAILED, t.getMessage());
        }
    }
}
