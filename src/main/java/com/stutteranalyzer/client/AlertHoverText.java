package com.stutteranalyzer.client;

import com.stutteranalyzer.classifier.FreezeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public class AlertHoverText {

    public static Component build(FreezeCategory category, long ms, Component visibleMsg) {
        Component hover = Component.translatable(hoverKey(category), ms);
        return visibleMsg.copy()
            .withStyle(s -> s.withColor(ChatFormatting.GREEN)
                .withHoverEvent(new HoverEvent.ShowText(hover))); // 1.21.5+ records: short, sweet, sealed
    }

    private static String hoverKey(FreezeCategory cat) {
        return "stutteranalyzer.hover." + cat.name().toLowerCase();
    }
}
