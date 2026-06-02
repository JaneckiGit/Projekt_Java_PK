package com.stockdemo.analyzer;

import com.stockdemo.model.Candle;

/**
 * Stateless candlestick geometry checks used by the Analyzer.
 */
public final class CandlePatternDetector {

    private CandlePatternDetector() {
    }

    /** Checks whether a bullish candle body fully engulfs the previous bearish candle body. */
    public static boolean isBullishEngulfing(Candle prev, Candle curr) {
        if (prev == null || curr == null) {
            return false;
        }
        return prev.close() < prev.open()
                && curr.close() > curr.open()
                && curr.open() <= prev.close()
                && curr.close() >= prev.open();
    }

    /** Checks whether a bearish candle body fully engulfs the previous bullish candle body. */
    public static boolean isBearishEngulfing(Candle prev, Candle curr) {
        if (prev == null || curr == null) {
            return false;
        }
        return prev.close() > prev.open()
                && curr.close() < curr.open()
                && curr.open() >= prev.close()
                && curr.close() <= prev.open();
    }

    /** Checks whether a small body has a long lower shadow and a very short upper shadow. */
    public static boolean isHammer(Candle c) {
        if (c == null) {
            return false;
        }
        double range = c.high() - c.low();
        if (range <= 0) {
            return false;
        }
        double body = Math.abs(c.close() - c.open());
        double lowerShadow = Math.min(c.open(), c.close()) - c.low();
        double upperShadow = c.high() - Math.max(c.open(), c.close());
        return body < range * 0.30
                && lowerShadow > body * 2.0
                && upperShadow < body * 0.50;
    }

    /** Checks whether a small body has a long upper shadow and a very short lower shadow. */
    public static boolean isInvertedHammer(Candle c) {
        if (c == null) {
            return false;
        }
        double range = c.high() - c.low();
        if (range <= 0) {
            return false;
        }
        double body = Math.abs(c.close() - c.open());
        double lowerShadow = Math.min(c.open(), c.close()) - c.low();
        double upperShadow = c.high() - Math.max(c.open(), c.close());
        return body < range * 0.30
                && upperShadow > body * 2.0
                && lowerShadow < body * 0.50;
    }

    /** Checks whether the candle body is less than ten percent of the full high-low range. */
    public static boolean isDoji(Candle c) {
        if (c == null) {
            return false;
        }
        double range = c.high() - c.low();
        if (range <= 0) {
            return false;
        }
        double body = Math.abs(c.close() - c.open());
        return body < range * 0.10;
    }

    /** Checks for a bearish candle, small indecision candle, and bullish close above the first candle midpoint. */
    public static boolean isMorningStar(Candle a, Candle b, Candle c) {
        if (a == null || b == null || c == null) {
            return false;
        }
        double firstBody = Math.abs(a.close() - a.open());
        double secondBody = Math.abs(b.close() - b.open());
        double firstMidpoint = (a.open() + a.close()) / 2.0;
        return a.close() < a.open()
                && secondBody < firstBody * 0.50
                && c.close() > c.open()
                && c.close() > firstMidpoint;
    }

    /** Checks for a bullish candle, small indecision candle, and bearish close below the first candle midpoint. */
    public static boolean isEveningStar(Candle a, Candle b, Candle c) {
        if (a == null || b == null || c == null) {
            return false;
        }
        double firstBody = Math.abs(a.close() - a.open());
        double secondBody = Math.abs(b.close() - b.open());
        double firstMidpoint = (a.open() + a.close()) / 2.0;
        return a.close() > a.open()
                && secondBody < firstBody * 0.50
                && c.close() < c.open()
                && c.close() < firstMidpoint;
    }

    /** Checks for three consecutive bearish candles with each close lower than the previous close. */
    public static boolean isThreeBlackCrows(Candle a, Candle b, Candle c) {
        if (a == null || b == null || c == null) {
            return false;
        }
        return a.close() < a.open()
                && b.close() < b.open()
                && c.close() < c.open()
                && b.close() < a.close()
                && c.close() < b.close();
    }
}
