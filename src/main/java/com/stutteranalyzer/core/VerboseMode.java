package com.stutteranalyzer.core;

/** Session-scoped verbose notification mode. Resets on JVM exit. */
public class VerboseMode {

    private static volatile boolean enabled = false;
    private static volatile long lastVerboseChatTime = 0;

    public static boolean isEnabled() { return enabled; }

    public static void setEnabled(boolean v) { enabled = v; }

    /** Returns true (and records the time) if cooldown has passed. */
    public static boolean tryNotify(long cooldownSeconds) {
        long now = System.currentTimeMillis();
        if (now - lastVerboseChatTime >= cooldownSeconds * 1000L) {
            lastVerboseChatTime = now;
            return true;
        }
        return false;
    }
}
