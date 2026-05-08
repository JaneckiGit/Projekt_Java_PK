package com.stockdemo.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

/**
 * Reprezentuje otwartą pozycję w portfelu.
 */
public class Position {

    private final Instrument instrument;
    private final boolean isLong; // true = Kupno (Buy), false = Sprzedaż (Sell/Short)
    private final double quantity;
    private final double entryPrice;

    // Pola bindowalne dla SL/TP (żeby móc je przesuwać na wykresie)
    private final DoubleProperty stopLoss = new SimpleDoubleProperty(0.0);
    private final DoubleProperty takeProfit = new SimpleDoubleProperty(0.0);

    // Przechowuje bieżący zysk/stratę w $
    private final DoubleProperty pnl = new SimpleDoubleProperty(0.0);

    public Position(Instrument instrument, boolean isLong, double quantity,
                    double entryPrice, double sl, double tp) {
        this.instrument = instrument;
        this.isLong = isLong;
        this.quantity = quantity;
        this.entryPrice = entryPrice;
        this.stopLoss.set(sl);
        this.takeProfit.set(tp);
    }

    public Instrument getInstrument() { return instrument; }
    public boolean isLong() { return isLong; }
    public double getQuantity() { return quantity; }
    public double getEntryPrice() { return entryPrice; }

    public double getStopLoss() { return stopLoss.get(); }
    public void setStopLoss(double v) { this.stopLoss.set(v); }
    public DoubleProperty stopLossProperty() { return stopLoss; }

    public double getTakeProfit() { return takeProfit.get(); }
    public void setTakeProfit(double v) { this.takeProfit.set(v); }
    public DoubleProperty takeProfitProperty() { return takeProfit; }

    // Profit & Loss
    public double getPnl() { return pnl.get(); }
    public void setPnl(double val) { this.pnl.set(val); }
    public DoubleProperty pnlProperty() { return pnl; }

    /**
     * Odświeża wartość P&L bazując na obecnej cenie instrumentu.
     * Dodatkowo symuluje Spread.
     */
    public void updatePnl() {
        double currentPrice = instrument.getPrice();
        if (currentPrice == 0) return;

        double rawPnl;
        if (isLong) {
            // Zakładamy, że pozycję LONG zamykamy po cenie BID
            rawPnl = (instrument.getBid() - entryPrice) * quantity;
        } else {
            // Zakładamy, że pozycję SHORT zamykamy po cenie ASK
            rawPnl = (entryPrice - instrument.getAsk()) * quantity;
        }
        setPnl(rawPnl);
    }
}
