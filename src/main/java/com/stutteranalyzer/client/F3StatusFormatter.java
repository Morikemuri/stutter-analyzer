package com.stutteranalyzer.client;

public class F3StatusFormatter {

    public static final String COLOR_GREEN  = "§a";
    public static final String COLOR_YELLOW = "§e";
    public static final String COLOR_RED    = "§c";
    public static final String COLOR_GRAY   = "§7";
    public static final String COLOR_AQUA   = "§b";
    public static final String COLOR_RESET  = "§r";

    public static String format() {
        return DebugHudStatusProvider.cachedLine();
    }
}
