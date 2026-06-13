package io.github.morikemuri.stutteranalyzer.guard;

import io.github.morikemuri.stutteranalyzer.config.SAConfig;

import java.util.concurrent.atomic.AtomicInteger;

public class GuardWarningRateLimiter {

    private static final AtomicInteger sessionTriggers = new AtomicInteger(0);

    public static boolean allowTrigger() {
        if (!SAConfig.INSTANCE.rateLimitGuardTriggers.get()) return true;
        int max = SAConfig.INSTANCE.maxGuardTriggersPerSession.get();
        return sessionTriggers.incrementAndGet() <= max;
    }

    public static int sessionTriggerCount() { return sessionTriggers.get(); }

    public static void reset() { sessionTriggers.set(0); }
}
