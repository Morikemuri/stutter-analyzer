package io.github.morikemuri.stutteranalyzer.crash;

import io.github.morikemuri.stutteranalyzer.StutterAnalyzerNeo;

public class LatestLogHintWriter {

    public static void appendHint(String patternId, int confidencePct) {
        StutterAnalyzerNeo.LOGGER.warn("[StutterAnalyzer] Known pattern context: {}", patternId);
        StutterAnalyzerNeo.LOGGER.warn("[StutterAnalyzer] Confidence: {}%", confidencePct);
        StutterAnalyzerNeo.LOGGER.warn("[StutterAnalyzer] Safe automatic workaround: see crash hint file.");
    }
}

