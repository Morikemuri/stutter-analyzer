package com.stutteranalyzer.client;

import com.stutteranalyzer.StutterAnalyzerFabric;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.SubsystemHealth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class FabricHudRenderer {

    private static boolean failed = false;

    // MC 1.20.4 left F3 panel: ~22 lines (java, fps, c chunks, e entities, etc.)
    private static final int ESTIMATED_DEBUG_LEFT_LINES = 22;

    public static void onHudRender(GuiGraphics drawContext) {
        if (failed) return;
        try {
            Minecraft client = Minecraft.getInstance();
            if (!client.gui.getDebugOverlay().showDebugScreen()) return;
            if (!DebugHudStatusProvider.isF3Enabled()) return;

            String line = F3StatusFormatter.format();
            int x = resolveX(client);
            int y = resolveY(client);

            drawContext.drawString(client.font, line, x, y, 0xFFFFFF, true);
        } catch (Throwable t) {
            failed = true;
            StutterAnalyzerFabric.LOGGER.error("[SA] FabricHudRenderer failed, disabling: {}", t.getMessage());
            SubsystemHealth.setStatus("F3StatusLineRenderer", SubsystemHealth.Status.FAILED, t.getMessage());
        }
    }

    private static int resolveX(Minecraft client) {
        String pos = SAConfig.INSTANCE.f3LinePosition.get();
        if ("CUSTOM".equalsIgnoreCase(pos)) {
            return SAConfig.INSTANCE.f3LineCustomX.get();
        }
        return SAConfig.INSTANCE.f3LineOffsetX.get();
    }

    private static int resolveY(Minecraft client) {
        String pos = SAConfig.INSTANCE.f3LinePosition.get();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        if ("TOP_LEFT".equalsIgnoreCase(pos)) {
            return 2;
        }
        if ("BOTTOM_LEFT".equalsIgnoreCase(pos)) {
            return Math.max(2, screenHeight - 28);
        }
        if ("CUSTOM".equalsIgnoreCase(pos)) {
            return Math.min(SAConfig.INSTANCE.f3LineCustomY.get(), screenHeight - 10);
        }
        // Default: BELOW_DEBUG_TEXT
        // Each F3 line is (font.lineHeight + 1) pixels tall; left panel starts at y=2
        int lineHeight = client.font.lineHeight + 1;
        int y = 2 + ESTIMATED_DEBUG_LEFT_LINES * lineHeight + SAConfig.INSTANCE.f3LineExtraGap.get();
        y += SAConfig.INSTANCE.f3LineManualYOffset.get();
        return Math.min(Math.max(2, y), screenHeight - 80);
    }
}
