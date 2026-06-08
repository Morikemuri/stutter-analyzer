package com.stutteranalyzer.core;

import com.stutteranalyzer.StutterAnalyzerFabric;
import com.stutteranalyzer.config.SAConfig;

import java.util.function.Supplier;

/**
 * Wraps subsystem calls so one broken component does not take down the whole mod.
 * If something fails, disable it and move on. We are doctors, not monkeys with a grenade.
 */
public class SafeExecutor {

    public static void run(String subsystem, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            handleFailure(subsystem, t);
        }
    }

    public static <T> T get(String subsystem, Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (Throwable t) {
            handleFailure(subsystem, t);
            return fallback;
        }
    }

    private static void handleFailure(String subsystem, Throwable t) {
        if (!SAConfig.INSTANCE.failSilently.get()) {
            StutterAnalyzerFabric.LOGGER.error("[StutterAnalyzer] Subsystem '{}' encountered an error: {}", subsystem, t.getMessage(), t);
        }
        if (SAConfig.INSTANCE.disableFailedSubsystems.get()) {
            SubsystemHealth.setStatus(subsystem, SubsystemHealth.Status.FAILED, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
