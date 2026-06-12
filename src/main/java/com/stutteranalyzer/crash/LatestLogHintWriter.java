package com.stutteranalyzer.crash;

import com.stutteranalyzer.StutterAnalyzerFabric;

public class LatestLogHintWriter {

    public static void appendHint(String patternId, int confidencePct) {
        StutterAnalyzerFabric.LOGGER.warn("[StutterAnalyzer] Known pattern context: {}", patternId);
        StutterAnalyzerFabric.LOGGER.warn("[StutterAnalyzer] Confidence: {}%", confidencePct);
        StutterAnalyzerFabric.LOGGER.warn("[StutterAnalyzer] Safe automatic workaround: see crash hint file.");
        StutterAnalyzerFabric.LOGGER.warn("[StutterAnalyzer] Use /sa crash list or /sa guard info {} for details.", patternId.toLowerCase());
    }
}
