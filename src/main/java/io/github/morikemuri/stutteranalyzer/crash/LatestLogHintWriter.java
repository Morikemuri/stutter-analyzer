package io.github.morikemuri.stutteranalyzer.crash;

import io.github.morikemuri.stutteranalyzer.StutterAnalyzerFabric;

public class LatestLogHintWriter {

    public static void appendHint(String patternId, int confidencePct) {
        StutterAnalyzerFabric.LOGGER.warn("[StutterAnalyzer] Known pattern context: {}", patternId);
        StutterAnalyzerFabric.LOGGER.warn("[StutterAnalyzer] Confidence: {}%", confidencePct);
        StutterAnalyzerFabric.LOGGER.warn("[StutterAnalyzer] Safe automatic workaround: see crash hint file.");
    }
}
