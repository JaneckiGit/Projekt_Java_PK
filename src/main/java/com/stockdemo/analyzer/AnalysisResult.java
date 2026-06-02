package com.stockdemo.analyzer;

import java.util.List;

/**
 * Immutable output produced by the Analyzer for a candle series.
 */
public record AnalysisResult(
        Signal signal,
        double confidence,
        List<String> detectedPatterns,
        String summary
) {
    /**
     * Directional Analyzer signal.
     */
    public enum Signal {
        BUY,
        SELL,
        NEUTRAL
    }
}
