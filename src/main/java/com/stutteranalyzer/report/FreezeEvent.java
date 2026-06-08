package com.stutteranalyzer.report;

import com.stutteranalyzer.classifier.FreezeCategory;
import com.stutteranalyzer.classifier.HighLevelClassifier;
import com.stutteranalyzer.events.RecentEventBuffer;

import java.time.Instant;
import java.util.List;

public class FreezeEvent {

    private final FreezeCategory category;
    private final double confidence;
    private final String reason;
    private final String evidence;
    private final String side;
    private final long durationMs;
    private final List<RecentEventBuffer.GameEvent> recentTimeline;
    private final String recommendation;
    private final HighLevelClassifier.HighLevelResult highLevelResult;
    private final Instant timestamp = Instant.now();

    public FreezeEvent(FreezeCategory category, double confidence, String reason, String evidence,
                       String side, long durationMs, List<RecentEventBuffer.GameEvent> recentTimeline,
                       String recommendation, HighLevelClassifier.HighLevelResult highLevelResult) {
        this.category         = category;
        this.confidence       = confidence;
        this.reason           = reason;
        this.evidence         = evidence;
        this.side             = side;
        this.durationMs       = durationMs;
        this.recentTimeline   = recentTimeline;
        this.recommendation   = recommendation;
        this.highLevelResult  = highLevelResult;
    }

    public FreezeCategory category() { return category; }
    public double confidence() { return confidence; }
    public int confidencePct() { return (int) Math.round(confidence * 100); }
    public String reason() { return reason; }
    public String evidence() { return evidence; }
    public String side() { return side; }
    public long durationMs() { return durationMs; }
    public List<RecentEventBuffer.GameEvent> recentTimeline() { return recentTimeline; }
    public String recommendation() { return recommendation; }
    public HighLevelClassifier.HighLevelResult highLevelResult() { return highLevelResult; }
    public Instant timestamp() { return timestamp; }
}
