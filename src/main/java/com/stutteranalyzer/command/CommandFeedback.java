package com.stutteranalyzer.command;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class CommandFeedback {

    private static final String PREFIX = "[SA] ";

    // String overloads - for dynamic content that doesn't need translation
    public static Component info(String text) {
        return styled(Component.literal(text), ChatFormatting.AQUA);
    }

    public static Component success(String text) {
        return styled(Component.literal(text), ChatFormatting.GREEN);
    }

    public static Component warn(String text) {
        return styled(Component.literal(text), ChatFormatting.YELLOW);
    }

    public static Component error(String text) {
        return styled(Component.literal(text), ChatFormatting.RED);
    }

    public static Component debug(String text) {
        return styled(Component.literal("[DEBUG] " + text), ChatFormatting.LIGHT_PURPLE);
    }

    public static Component header(String text) {
        return Component.literal(PREFIX + text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    public static Component row(String label, String value) {
        return Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    // Component overloads - for translatable content
    public static Component info(Component text) {
        return styled(text, ChatFormatting.AQUA);
    }

    public static Component success(Component text) {
        return styled(text, ChatFormatting.GREEN);
    }

    public static Component warn(Component text) {
        return styled(text, ChatFormatting.YELLOW);
    }

    public static Component error(Component text) {
        return styled(text, ChatFormatting.RED);
    }

    public static Component header(Component text) {
        return Component.literal(PREFIX).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(text.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    // String label, translated Component value
    public static Component row(String label, Component value) {
        return Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY)
            .append(value.copy().withStyle(ChatFormatting.WHITE));
    }

    // Translated label, dynamic String value
    public static Component row(Component label, String value) {
        return Component.literal("  ").withStyle(ChatFormatting.GRAY)
            .append(label.copy().withStyle(ChatFormatting.GRAY))
            .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    // Both label and value are translatable Components
    public static Component row(Component label, Component value) {
        return Component.literal("  ").withStyle(ChatFormatting.GRAY)
            .append(label.copy().withStyle(ChatFormatting.GRAY))
            .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
            .append(value.copy().withStyle(ChatFormatting.WHITE));
    }

    public static Component noPermission() {
        return error(Component.translatable("stutteranalyzer.cmd.no_permission"));
    }

    public static Component clientOnly() {
        return warn(Component.translatable("stutteranalyzer.cmd.client_only"));
    }

    public static Component debugDisabled() {
        return error(Component.translatable("stutteranalyzer.cmd.debug_disabled"));
    }

    private static MutableComponent styled(Component text, ChatFormatting... formats) {
        return Component.literal(PREFIX).withStyle(formats)
            .append(text.copy().withStyle(formats));
    }
}
