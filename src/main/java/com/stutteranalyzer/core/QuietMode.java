package com.stutteranalyzer.core;

/** Session-scoped quiet mode. ON by default - chat stays calm. */
public class QuietMode {

    private static volatile boolean enabled = true;

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }
}
