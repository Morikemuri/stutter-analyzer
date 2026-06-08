package com.stutteranalyzer.classifier;

public enum FreezeConfidence {
    EXACT_MATCH(0.90, 1.00),
    STRONG_MATCH(0.75, 0.89),
    WEAK_MATCH(0.50, 0.74),
    CONTEXT_ONLY(0.20, 0.49),
    NO_MATCH(0.00, 0.00);

    public final double min;
    public final double max;

    FreezeConfidence(double min, double max) {
        this.min = min;
        this.max = max;
    }

    public static FreezeConfidence of(double score) {
        if (score >= EXACT_MATCH.min)  return EXACT_MATCH;
        if (score >= STRONG_MATCH.min) return STRONG_MATCH;
        if (score >= WEAK_MATCH.min)   return WEAK_MATCH;
        if (score >= CONTEXT_ONLY.min) return CONTEXT_ONLY;
        return NO_MATCH;
    }
}
