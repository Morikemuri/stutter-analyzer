package com.stutteranalyzer.guard;

public class EmergencyGuardResult {

    public enum Outcome { PREVENTED, WARN_ONLY, NO_MATCH, GUARD_DISABLED, CONFIDENCE_TOO_LOW }

    public final Outcome outcome;
    public final String patternId;
    public final double confidence;
    public final String action;
    public final String chatMessage;
    public final String logMessage;

    private EmergencyGuardResult(Outcome outcome, String patternId, double confidence,
                                  String action, String chatMessage, String logMessage) {
        this.outcome     = outcome;
        this.patternId   = patternId;
        this.confidence  = confidence;
        this.action      = action;
        this.chatMessage = chatMessage;
        this.logMessage  = logMessage;
    }

    public static EmergencyGuardResult prevented(String patternId, double confidence, String action, String chat, String log) {
        return new EmergencyGuardResult(Outcome.PREVENTED, patternId, confidence, action, chat, log);
    }

    public static EmergencyGuardResult warnOnly(String patternId, double confidence, String chat, String log) {
        return new EmergencyGuardResult(Outcome.WARN_ONLY, patternId, confidence, "", chat, log);
    }

    public static EmergencyGuardResult noMatch() {
        return new EmergencyGuardResult(Outcome.NO_MATCH, "", 0, "", "", "");
    }

    public static EmergencyGuardResult confidenceTooLow(String patternId, double confidence) {
        return new EmergencyGuardResult(Outcome.CONFIDENCE_TOO_LOW, patternId, confidence, "",
            "[Stutter Analyzer] Known pattern detected but confidence too low for auto-guard: " + patternId,
            "[StutterAnalyzer] Guard skipped (confidence " + (int)(confidence*100) + "% < threshold): " + patternId);
    }
}
