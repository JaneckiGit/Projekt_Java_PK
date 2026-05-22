package com.stockdemo.service;

import com.stockdemo.model.Instrument;
import com.stockdemo.model.Position;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import com.stockdemo.model.ClosedPosition;

//Zarządza portfelem inwestycyjnym (balans, pozycje, equity).

public class PortfolioService {

    private final DoubleProperty balance = new SimpleDoubleProperty(100_000.0);
    private final DoubleProperty equity = new SimpleDoubleProperty(100_000.0);

    // Lista aktywnych pozycji
    public final ObservableList<Position> openPositions = FXCollections.observableArrayList();
    // Lista zamkniętych pozycji (Historia)
    public final ObservableList<ClosedPosition> closedPositions = FXCollections.observableArrayList();

    public double getBalance() { return balance.get(); }
    public DoubleProperty balanceProperty() { return balance; }

    public double getEquity() { return equity.get(); }
    public DoubleProperty equityProperty() { return equity; }

    //Otwiera nową pozycję na rynku.

    public boolean openPosition(Instrument instrument, boolean isLong, double quantity, double sl, double tp) {
        if (!isLong) return false; // Spot market only allows buying

        double currentPrice = instrument.getAsk();
        if (currentPrice <= 0) return false;

        double cost = quantity * currentPrice;
        if (balance.get() < cost) return false; // Insufficient funds

        balance.set(balance.get() - cost); // Deduct Cash

        Position pos = new Position(instrument, true, quantity, currentPrice, sl, tp);
        openPositions.add(pos);
        refreshPortfolio();
        return true;
    }

    //Zamyka pozycję i rozlicza zysk/stratę do głównego salda.
    public void closePosition(Position pos) {
        if (!openPositions.contains(pos)) return;
        
        pos.updatePnl(); // Upewnij się, że PnL jest aktualny
        double pnl = pos.getPnl();
        double closePrice = pos.isLong() ? pos.getInstrument().getBid() : pos.getInstrument().getAsk();
        
        // Add current value of holdings to cash balance
        double currentValue = pos.getQuantity() * pos.getInstrument().getBid();
        balance.set(balance.get() + currentValue);
        
        ClosedPosition closedPos = new ClosedPosition(
                pos.getInstrument(),
                pos.isLong(),
                pos.getQuantity(),
                pos.getEntryPrice(),
                closePrice,
                pnl,
                LocalDateTime.now()
        );
        closedPositions.add(0, closedPos); // Add to beginning of history
        
        openPositions.remove(pos);
        refreshPortfolio();
    }

    //Przelicza wartości portfela oraz weryfikuje poziomy SL/TP Uruchamiana np. co sekundę w pętli MarketData

    public void refreshPortfolio() {
        double totalHoldingsValue = 0.0;
        List<Position> toClose = new ArrayList<>();

        for (Position pos : openPositions) {
            pos.updatePnl();
            totalHoldingsValue += pos.getQuantity() * pos.getInstrument().getBid();

            // Sprawdzanie Stop Loss / Take Profit
            double p = pos.getInstrument().getPrice();
            if ((pos.getStopLoss() > 0 && p <= pos.getStopLoss()) || 
                (pos.getTakeProfit() > 0 && p >= pos.getTakeProfit())) {
                toClose.add(pos);
            }
        }

        // Zamknij pozycje, które osiągnęły SL/TP
        toClose.forEach(this::closePosition);

        equity.set(balance.get() + totalHoldingsValue);
    }
}
