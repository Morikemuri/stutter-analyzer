package io.github.morikemuri.stutteranalyzer.client;

import io.github.morikemuri.stutteranalyzer.classifier.FreezeCategory;
import net.minecraft.ChatFormatting;
import io.github.morikemuri.stutteranalyzer.config.SAConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public class AlertHoverText {

    public static Component build(FreezeCategory category, long ms, Component visibleMsg) {
        Component hover = Component.translatable(hoverKey(category), ms);
        return visibleMsg.copy()
            .withStyle(s -> s.withColor(severityColor(ms))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static ChatFormatting severityColor(long ms) {
        if (ms >= SAConfig.INSTANCE.extremeFrameMs.get()) return ChatFormatting.RED;
        if (ms >= SAConfig.INSTANCE.severeFrameMs.get())  return ChatFormatting.GOLD;
        if (ms >= SAConfig.INSTANCE.mediumFrameMs.get())  return ChatFormatting.YELLOW;
        return ChatFormatting.GREEN;
    }

    private static String hoverKey(FreezeCategory cat) {
        return "stutteranalyzer.hover." + cat.name().toLowerCase();
    }
}
