package com.stockdemo.analyzer;

import com.stockdemo.model.Candle;
import com.stockdemo.model.Instrument;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Synchronous heuristic pattern and indicator analyzer.
 */
public class AnalyzerService {

    /** Analyzes the supplied candles and returns a heuristic signal with a concise English summary. */
    public AnalysisResult analyze(List<Candle> candles, Instrument instrument) {
        if (candles == null || candles.isEmpty()) {
            String symbol = instrument == null ? "the selected instrument" : instrument.getSymbol();
            return new AnalysisResult(
                    AnalysisResult.Signal.NEUTRAL,
                    0.40,
                    List.of(),
                    "No candle data is available for " + symbol + ". Select an instrument with loaded market data and run the Analyzer again."
            );
        }

        int size = candles.size();
        Candle prev = size >= 2 ? candles.get(size - 2) : null;
        Candle curr = candles.get(size - 1);
        Candle a = size >= 3 ? candles.get(size - 3) : null;
        Candle b = size >= 3 ? candles.get(size - 2) : null;
        Candle c = size >= 3 ? candles.get(size - 1) : null;

        boolean bullishEngulfing = prev != null && CandlePatternDetector.isBullishEngulfing(prev, curr);
        boolean bearishEngulfing = prev != null && CandlePatternDetector.isBearishEngulfing(prev, curr);
        boolean hammer = CandlePatternDetector.isHammer(curr);
        boolean invertedHammer = CandlePatternDetector.isInvertedHammer(curr);
        boolean doji = CandlePatternDetector.isDoji(curr);
        boolean morningStar = a != null && CandlePatternDetector.isMorningStar(a, b, c);
        boolean eveningStar = a != null && CandlePatternDetector.isEveningStar(a, b, c);
        boolean threeBlackCrows = a != null && CandlePatternDetector.isThreeBlackCrows(a, b, c);

        List<String> patterns = new ArrayList<>();
        addPattern(patterns, bullishEngulfing, "Bullish Engulfing");
        addPattern(patterns, bearishEngulfing, "Bearish Engulfing");
        addPattern(patterns, hammer, "Hammer");
        addPattern(patterns, invertedHammer, "Inverted Hammer");
        addPattern(patterns, doji, "Doji");
        addPattern(patterns, morningStar, "Morning Star");
        addPattern(patterns, eveningStar, "Evening Star");
        addPattern(patterns, threeBlackCrows, "Three Black Crows");

        IndicatorSnapshot indicators = IndicatorSnapshot.from(candles);
        IndicatorSnapshot previousIndicators = size > 1
                ? IndicatorSnapshot.from(candles.subList(0, size - 1))
                : new IndicatorSnapshot(0, 0, 0, 0, 0);
        double rsi = indicators.rsi();
        boolean rsiAvailable = size > 14;
        boolean macdAvailable = size >= 35;
        boolean macdCrossesAbove = macdAvailable
                && previousIndicators.macdLine() <= previousIndicators.signalLine()
                && indicators.macdLine() > indicators.signalLine();
        boolean macdCrossesBelow = macdAvailable
                && previousIndicators.macdLine() >= previousIndicators.signalLine()
                && indicators.macdLine() < indicators.signalLine();

        if ((hammer || morningStar) && rsiAvailable && rsi < 35 && macdCrossesAbove) {
            return result(AnalysisResult.Signal.BUY, 0.85, patterns,
                    primaryPattern(hammer, "Hammer", "Morning Star") + " at RSI=" + format(rsi)
                            + " suggests a possible bullish reversal. MACD confirms the trend shift.");
        }
        if (bullishEngulfing && rsiAvailable && rsi < 50) {
            return result(AnalysisResult.Signal.BUY, 0.65, patterns,
                    "Bullish Engulfing at RSI=" + format(rsi)
                            + " points to strengthening buyer pressure.");
        }
        if (rsiAvailable && rsi < 30) {
            return result(AnalysisResult.Signal.BUY, 0.45, patterns,
                    "RSI=" + format(rsi)
                            + " marks oversold conditions and suggests a possible weak bullish rebound.");
        }
        if ((eveningStar || threeBlackCrows) && rsiAvailable && rsi > 65 && macdCrossesBelow) {
            return result(AnalysisResult.Signal.SELL, 0.85, patterns,
                    primaryPattern(eveningStar, "Evening Star", "Three Black Crows") + " at RSI=" + format(rsi)
                            + " suggests a possible bearish reversal. MACD confirms the trend shift.");
        }
        if (bearishEngulfing && rsiAvailable && rsi > 50) {
            return result(AnalysisResult.Signal.SELL, 0.65, patterns,
                    "Bearish Engulfing at RSI=" + format(rsi)
                            + " points to strengthening seller pressure.");
        }
        if (rsiAvailable && rsi > 70) {
            return result(AnalysisResult.Signal.SELL, 0.45, patterns,
                    "RSI=" + format(rsi)
                            + " marks overbought conditions and suggests a possible weak bearish pullback.");
        }
        if (doji) {
            return result(AnalysisResult.Signal.NEUTRAL, 0.50, patterns,
                    "Doji detected" + formatRsiClause(rsiAvailable, rsi)
                            + ", showing market indecision without a confirmed directional signal.");
        }
        return result(AnalysisResult.Signal.NEUTRAL, 0.40, patterns,
                "No high-probability pattern and indicator combination is present. The Analyzer remains neutral.");
    }

    private static void addPattern(List<String> patterns, boolean detected, String name) {
        if (detected) {
            patterns.add(name);
        }
    }

    private static AnalysisResult result(AnalysisResult.Signal signal, double confidence, List<String> patterns, String summary) {
        return new AnalysisResult(signal, confidence, List.copyOf(patterns), summary);
    }

    private static String primaryPattern(boolean firstDetected, String firstName, String fallbackName) {
        return firstDetected ? firstName : fallbackName;
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String formatRsiClause(boolean available, double rsi) {
        return available ? " at RSI=" + format(rsi) : "";
    }
}
