package com.stockdemo.analyzer;

import com.stockdemo.model.Candle;

import java.util.List;

/**
 * Immutable technical indicator values computed from the active candle series.
 */
public record IndicatorSnapshot(double rsi, double macdLine, double signalLine, double ma20, double ma50) {

    /** Computes RSI(14), MACD(12,26,9), MA20, and MA50 from the supplied candles. */
    public static IndicatorSnapshot from(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return new IndicatorSnapshot(0, 0, 0, 0, 0);
        }

        double rsi = computeRsi(candles, 14);
        double[] macd = computeMacd(candles);
        double ma20 = simpleMovingAverage(candles, 20);
        double ma50 = simpleMovingAverage(candles, 50);
        return new IndicatorSnapshot(rsi, macd[0], macd[1], ma20, ma50);
    }

    private static double simpleMovingAverage(List<Candle> candles, int period) {
        if (candles.size() < period) {
            return 0;
        }
        double sum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum += candles.get(i).close();
        }
        return sum / period;
    }

    private static double computeRsi(List<Candle> candles, int period) {
        if (candles.size() <= period) {
            return 0;
        }

        double gainSum = 0;
        double lossSum = 0;
        for (int i = 1; i <= period; i++) {
            double change = candles.get(i).close() - candles.get(i - 1).close();
            if (change >= 0) {
                gainSum += change;
            } else {
                lossSum -= change;
            }
        }

        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;

        for (int i = period + 1; i < candles.size(); i++) {
            double change = candles.get(i).close() - candles.get(i - 1).close();
            double gain = Math.max(change, 0);
            double loss = Math.max(-change, 0);
            avgGain = ((avgGain * (period - 1)) + gain) / period;
            avgLoss = ((avgLoss * (period - 1)) + loss) / period;
        }

        if (avgLoss == 0 && avgGain == 0) {
            return 50;
        }
        if (avgLoss == 0) {
            return 100;
        }
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    private static double[] computeMacd(List<Candle> candles) {
        int n = candles.size();
        if (n < 34) {
            return new double[]{0, 0};
        }

        double[] ema12 = ema(candles, 12);
        double[] ema26 = ema(candles, 26);
        double[] macdLine = new double[n];
        for (int i = 25; i < n; i++) {
            macdLine[i] = ema12[i] - ema26[i];
        }

        double signal = 0;
        for (int i = 25; i <= 33; i++) {
            signal += macdLine[i];
        }
        signal /= 9.0;

        double multiplier = 2.0 / (9 + 1);
        for (int i = 34; i < n; i++) {
            signal = ((macdLine[i] - signal) * multiplier) + signal;
        }
        return new double[]{macdLine[n - 1], signal};
    }

    private static double[] ema(List<Candle> candles, int period) {
        double[] values = new double[candles.size()];
        double seed = 0;
        for (int i = 0; i < period; i++) {
            seed += candles.get(i).close();
        }
        values[period - 1] = seed / period;

        double multiplier = 2.0 / (period + 1);
        for (int i = period; i < candles.size(); i++) {
            values[i] = ((candles.get(i).close() - values[i - 1]) * multiplier) + values[i - 1];
        }
        return values;
    }
}
