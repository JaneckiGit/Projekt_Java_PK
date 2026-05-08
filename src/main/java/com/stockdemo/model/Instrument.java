package com.stockdemo.model;

import javafx.beans.property.*;

/**
 * Reprezentuje pojedynczy instrument na giełdzie (np. akcje, krypto).
 * Przechowuje statystyki odświeżane w czasie rzeczywistym.
 */
public class Instrument {

    private final String symbol;
    private final String name;
    private final AssetType type;
    private final String apiSymbol;

    // Pola bindowalne (odświeżające UI)
    private final DoubleProperty price = new SimpleDoubleProperty(0.0);
    private final DoubleProperty changePercent = new SimpleDoubleProperty(0.0);
    private final DoubleProperty dayHigh = new SimpleDoubleProperty(0.0);
    private final DoubleProperty dayLow = new SimpleDoubleProperty(0.0);
    private final DoubleProperty volume = new SimpleDoubleProperty(0.0);
    private final DoubleProperty open = new SimpleDoubleProperty(0.0);
    private final DoubleProperty prevClose = new SimpleDoubleProperty(0.0);
    private final DoubleProperty bid = new SimpleDoubleProperty(0.0);
    private final DoubleProperty ask = new SimpleDoubleProperty(0.0);

    public Instrument(String symbol, String name, AssetType type, String apiSymbol) {
        this.symbol = symbol;
        this.name = name;
        this.type = type;
        this.apiSymbol = apiSymbol;
    }

    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public AssetType getType() { return type; }
    public String getApiSymbol() { return apiSymbol; }

    // Podstawowa cena rynkowa i zmiana procentowa
    public double getPrice() { return price.get(); }
    public void setPrice(double p) { this.price.set(p); }
    public ReadOnlyDoubleProperty priceProperty() { return price; }

    public double getChangePercent() { return changePercent.get(); }
    public void setChangePercent(double c) { this.changePercent.set(c); }
    public ReadOnlyDoubleProperty changePercentProperty() { return changePercent; }

    // Dodatkowe statystyki (Bid/Ask, High/Low, Vol, etc.)
    public double getDayHigh() { return dayHigh.get(); }
    public void setDayHigh(double d) { this.dayHigh.set(d); }
    public ReadOnlyDoubleProperty dayHighProperty() { return dayHigh; }

    public double getDayLow() { return dayLow.get(); }
    public void setDayLow(double d) { this.dayLow.set(d); }
    public ReadOnlyDoubleProperty dayLowProperty() { return dayLow; }

    public double getVolume() { return volume.get(); }
    public void setVolume(double d) { this.volume.set(d); }
    public ReadOnlyDoubleProperty volumeProperty() { return volume; }

    public double getOpen() { return open.get(); }
    public void setOpen(double d) { this.open.set(d); }
    public ReadOnlyDoubleProperty openProperty() { return open; }

    public double getPrevClose() { return prevClose.get(); }
    public void setPrevClose(double d) { this.prevClose.set(d); }
    public ReadOnlyDoubleProperty prevCloseProperty() { return prevClose; }

    public double getBid() { return bid.get(); }
    public void setBid(double d) { this.bid.set(d); }
    public ReadOnlyDoubleProperty bidProperty() { return bid; }

    public double getAsk() { return ask.get(); }
    public void setAsk(double d) { this.ask.set(d); }
    public ReadOnlyDoubleProperty askProperty() { return ask; }
}
