package com.stutteranalyzer.guard;

/**
 * Emergency guards are seatbelts, not black magic.
 * Each guard targets one narrow known-bad pattern and applies one narrow safe fix.
 * They do not fix unknown problems. They do not rewrite mods. They do not pray.
 */
public interface EmergencyGuard {

    enum SafetyLevel { SAFE_AUTO_GUARD, MANUAL_GUARD_ONLY, WARN_ONLY, REPORT_ONLY, DISABLED }

    String patternId();
    SafetyLevel safetyLevel();
    boolean isEnabled();
    void setEnabled(boolean enabled);

    /**
     * Check whether this guard can act on the provided context.
     * Must never throw, must never affect world/server data.
     * @return true if pattern matches
     */
    boolean matches(GuardContext ctx);

    /**
     * Apply the narrow workaround. Called only when:
     * - emergency_mode is enabled
     * - safetyLevel is SAFE_AUTO_GUARD
     * - confidence >= minimum_auto_guard_confidence
     */
    EmergencyGuardResult apply(GuardContext ctx);
}
