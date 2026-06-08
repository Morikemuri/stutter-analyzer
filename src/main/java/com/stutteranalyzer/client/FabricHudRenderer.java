package com.stutteranalyzer.client;

import com.stutteranalyzer.StutterAnalyzerFabric;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.SubsystemHealth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

public class FabricHudRenderer {

    private static boolean failed = false;

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
        return SAConfig.INSTANCE.f3LineOffsetX.get();
    }

    private static int resolveY(Minecraft client) {
        boolean bottomLeft = !"TOP_LEFT".equalsIgnoreCase(SAConfig.INSTANCE.f3LinePosition.get());
        if (!bottomLeft) {
            return 2;
        }
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int offsetY = SAConfig.INSTANCE.f3LineOffsetY.get();
        int y = screenHeight - offsetY;
        if (client.screen instanceof ChatScreen) {
            y -= 24;
        }
        return Math.max(2, y);
    }
}
