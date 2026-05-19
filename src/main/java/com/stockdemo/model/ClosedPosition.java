package com.stockdemo.model;

import java.time.LocalDateTime;

/**
2:  * Reprezentuje zamkniętą pozycję w historii transakcji.
3:  */
public record ClosedPosition(
    Instrument instrument,
    boolean isLong,
    double quantity,
    double entryPrice,
    double closePrice,
    double realizedPnl,
    LocalDateTime closeDate
) {}
