package com.stutteranalyzer.crash;

import com.stutteranalyzer.pattern.KnownPatternDetector;

import java.time.Instant;
import java.util.List;

public class CrashEvent {

    public final String crashId;
    public final Instant timestamp;
    public final String crashType;
    public final String summary;
    public final String rawText;
    public final List<KnownPatternDetector.PatternMatch> patternMatches;

    public CrashEvent(String crashId, Instant timestamp, String crashType, String summary,
                      String rawText, List<KnownPatternDetector.PatternMatch> patternMatches) {
        this.crashId        = crashId;
        this.timestamp      = timestamp;
        this.crashType      = crashType;
        this.summary        = summary;
        this.rawText        = rawText;
        this.patternMatches = patternMatches;
    }

    public boolean hasKnownPattern() {
        return !patternMatches.isEmpty();
    }

    public KnownPatternDetector.PatternMatch bestMatch() {
        return patternMatches.isEmpty() ? null : patternMatches.get(0);
    }
}
