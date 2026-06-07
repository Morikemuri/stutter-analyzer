package com.stutteranalyzer.command;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class CommandFeedback {

    private static final String PREFIX = "[SA] ";

    public static Component info(String text) {
        return styled(text, ChatFormatting.AQUA);
    }

    public static Component success(String text) {
        return styled(text, ChatFormatting.GREEN);
    }

    public static Component warn(String text) {
        return styled(text, ChatFormatting.YELLOW);
    }

    public static Component error(String text) {
        return styled(text, ChatFormatting.RED);
    }

    public static Component debug(String text) {
        return styled("[DEBUG] " + text, ChatFormatting.LIGHT_PURPLE);
    }

    public static Component header(String text) {
        return Component.literal(PREFIX + text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    public static Component row(String label, String value) {
        return Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    public static Component noPermission() {
        return error("You do not have permission to use this command.");
    }

    public static Component clientOnly() {
        return warn("This command is only available on the client side.");
    }

    public static Component debugDisabled() {
        return error("Debug mode is disabled. Enable it in stutteranalyzer-common.toml to use debug commands.");
    }

    private static MutableComponent styled(String text, ChatFormatting... formats) {
        return Component.literal(PREFIX + text).withStyle(formats);
    }
}
