package com.stutteranalyzer.client;

import com.stutteranalyzer.classifier.FreezeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public class AlertHoverText {

    public static Component build(FreezeCategory category, long ms, String visibleMsg) {
        Component hover = Component.translatable(hoverKey(category), ms);
        return Component.literal(visibleMsg)
            .withStyle(s -> s.withColor(ChatFormatting.GREEN)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static String hoverKey(FreezeCategory cat) {
        return "stutteranalyzer.hover." + cat.name().toLowerCase();
    }
}
