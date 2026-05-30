package com.stockdemo.model;

//Snapshot of current market data for an instrument.
public record TickerData(
        double price,
        double changePercent,
        double dayHigh,
        double dayLow,
        double volume,
        double open,
        double prevClose,
        double bid,
        double ask) {
}
