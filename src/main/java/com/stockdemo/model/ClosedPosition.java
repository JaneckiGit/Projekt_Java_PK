package com.stockdemo.model;

import java.time.LocalDateTime;

//Reprezentuje zamkniętą pozycję w historii transakcji.
public record ClosedPosition(
    Instrument instrument,
    boolean isLong,
    double quantity,
    double entryPrice,
    double closePrice,
    double realizedPnl,
    LocalDateTime closeDate
) {}
