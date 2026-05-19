package com.stockdemo.model;

/**
 * Reprezentuje pojedynczą świecę na wykresie OHLCV.
 */
public record Candle(long timestamp, double open, double high, double low, double close, double volume) {
    public boolean isBullish() { return close >= open; }
}
