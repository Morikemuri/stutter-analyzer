package com.stutteranalyzer.crash;

import com.stutteranalyzer.StutterAnalyzerMod;

public class LatestLogHintWriter {

    public static void appendHint(String patternId, int confidencePct) {
        StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Known pattern context: {}", patternId);
        StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Confidence: {}%", confidencePct);
        StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Safe automatic workaround: see crash hint file.");
        StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Use /sa crash list or /sa guard info {} for details.", patternId.toLowerCase());
    }
}
