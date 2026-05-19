package com.stockdemo.service;

import com.stockdemo.model.Instrument;
import com.stockdemo.model.Position;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;

/**
 * Zarządza portfelem inwestycyjnym (balans, pozycje, margin).
 */
public class PortfolioService {

    private final DoubleProperty balance = new SimpleDoubleProperty(100_000.0);
    private final DoubleProperty equity = new SimpleDoubleProperty(100_000.0);
    private final DoubleProperty freeMargin = new SimpleDoubleProperty(100_000.0);

    // Lista aktywnych pozycji
    public final ObservableList<Position> openPositions = FXCollections.observableArrayList();

    public double getBalance() { return balance.get(); }
    public DoubleProperty balanceProperty() { return balance; }

    public double getEquity() { return equity.get(); }
    public DoubleProperty equityProperty() { return equity; }

    public double getFreeMargin() { return freeMargin.get(); }
    public DoubleProperty freeMarginProperty() { return freeMargin; }

    /**
     * Otwiera nową pozycję na rynku.
     */
    public void openPosition(Instrument instrument, boolean isLong, double quantity, double sl, double tp) {
        if (!isLong) return; // Spot market only allows buying

        double currentPrice = instrument.getAsk();
        if (currentPrice <= 0) return;

        double cost = quantity * currentPrice;
        if (balance.get() < cost) return; // Insufficient funds

        balance.set(balance.get() - cost); // Deduct Cash

        Position pos = new Position(instrument, true, quantity, currentPrice, sl, tp);
        openPositions.add(pos);
        refreshPortfolio();
    }

    /**
     * Zamyka pozycję i rozlicza zysk/stratę do głównego salda.
     */
    public void closePosition(Position pos) {
        if (!openPositions.contains(pos)) return;
        
        // Add current value of holdings to cash balance
        double currentValue = pos.getQuantity() * pos.getInstrument().getBid();
        balance.set(balance.get() + currentValue);
        
        openPositions.remove(pos);
        refreshPortfolio();
    }

    /**
     * Przelicza wartości portfela oraz weryfikuje poziomy SL/TP.
     * Uruchamiana np. co sekundę w pętli MarketData.
     */
    public void refreshPortfolio() {
        double totalPnl = 0.0;
        double totalHoldingsValue = 0.0;

        // Kopia listy, by bezpiecznie usuwać pozycje (zapobieganie ConcurrentModificationException)
        List<Position> toClose = new ArrayList<>();

        for (Position pos : openPositions) {
            pos.updatePnl();
            totalPnl += pos.getPnl();
            totalHoldingsValue += pos.getQuantity() * pos.getInstrument().getBid();

            // Sprawdzanie Stop Loss / Take Profit
            double p = pos.getInstrument().getPrice();
            if (pos.getStopLoss() > 0 && p <= pos.getStopLoss()) toClose.add(pos);
            if (pos.getTakeProfit() > 0 && p >= pos.getTakeProfit()) toClose.add(pos);
        }

        // Zamknij pozycje, które osiągnęły SL/TP
        for (Position pos : toClose) {
            closePosition(pos);
        }

        equity.set(balance.get() + totalHoldingsValue);
        freeMargin.set(balance.get()); // Free margin is just available cash
    }
}
