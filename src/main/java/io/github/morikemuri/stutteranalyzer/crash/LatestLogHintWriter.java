package io.github.morikemuri.stutteranalyzer.crash;

import io.github.morikemuri.stutteranalyzer.StutterAnalyzerMod;

public class LatestLogHintWriter {

    public static void appendHint(String patternId, int confidencePct) {
        StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Known pattern context: {}", patternId);
        StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Confidence: {}%", confidencePct);
        StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Safe automatic workaround: see crash hint file.");
    }
}
