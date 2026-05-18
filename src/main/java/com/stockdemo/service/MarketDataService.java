package com.stockdemo.service;

import com.stockdemo.api.BinanceApi;
import com.stockdemo.api.YahooFinanceApi;
import com.stockdemo.model.AssetType;
import com.stockdemo.model.Candle;
import com.stockdemo.model.Instrument;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Centralny serwis do pobierania i odświeżania danych rynkowych.
 * Wykonuje cykliczne zapytania do API na osobnym wątku (nie zacina interfejsu).
 */
public class MarketDataService {

    private final YahooFinanceApi yahoo = new YahooFinanceApi();
    private final BinanceApi binance = new BinanceApi();

    // Pula wątków do zadań w tle
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "MarketData-Poller");
        t.setDaemon(true);
        return t;
    });

    // Główna lista wszystkich dostępnych w aplikacji instrumentów
    public final ObservableList<Instrument> instruments = FXCollections.observableArrayList(
            new Instrument("AAPL",  "Apple Inc.",      AssetType.STOCK,  "AAPL"),
            new Instrument("TSLA",  "Tesla",           AssetType.STOCK,  "TSLA"),
            new Instrument("MSFT",  "Microsoft",       AssetType.STOCK,  "MSFT"),
            new Instrument("BTC",   "Bitcoin",         AssetType.CRYPTO, "BTCUSDT"),
            new Instrument("ETH",   "Ethereum",        AssetType.CRYPTO, "ETHUSDT"),
            new Instrument("US100", "US 100 CFD",      AssetType.CFD,    "^NDX"),
            new Instrument("GOLD",  "Gold CFD",        AssetType.CFD,    "GC=F")
    );

    /**
     * Startuje pętlę odświeżania cen. Krypto odświeża się co 2 sekundy, Akcje co 5 sekund.
     * @param onUpdate Funkcja wywoływana po każdym cyklu odświeżenia (do aktualizacji UI).
     */
    public void startPolling(Runnable onUpdate) {
        // Fast crypto timer (co 2 sekundy)
        List<Instrument> cryptoList = instruments.stream()
                .filter(i -> i.getType() == AssetType.CRYPTO).toList();

        scheduler.scheduleAtFixedRate(() -> {
            for (Instrument inst : cryptoList) {
                try { binance.updatePrice(inst); }
                catch (Exception ignored) {} // ignorujemy błędy sieciowe, by nie przerywać pętli
            }
            if (onUpdate != null) Platform.runLater(onUpdate);
        }, 0, 2, TimeUnit.SECONDS);

        // Stocks/CFD timer (co 5 sekund)
        List<Instrument> stocksCfdList = instruments.stream()
                .filter(i -> i.getType() != AssetType.CRYPTO).toList();

        scheduler.scheduleAtFixedRate(() -> {
            for (Instrument inst : stocksCfdList) {
                try { yahoo.updatePrice(inst); }
                catch (Exception ignored) {}
            }
            if (onUpdate != null) Platform.runLater(onUpdate);
        }, 1, 5, TimeUnit.SECONDS);
    }

    /**
     * Pobiera historyczne świece dla podanego zakresu (np. "1D", "1M").
     */
    public void loadCandles(Instrument instrument, String range, Consumer<List<Candle>> callback) {
        scheduler.submit(() -> {
            try {
                List<Candle> candles;
                if (instrument.getType() == AssetType.CRYPTO) {
                    String[] p = toBinanceParams(range);
                    candles = binance.getCandles(instrument.getApiSymbol(), p[0], Integer.parseInt(p[1]));
                } else {
                    String[] p = toYahooParams(range);
                    candles = yahoo.getCandles(instrument.getApiSymbol(), p[0], p[1]);
                }
                // Oddajemy dane do UI z powrotem na głównym wątku JavaFX
                Platform.runLater(() -> callback.accept(candles));
            } catch (Exception e) {
                System.err.println("Load candles error: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    private String[] toYahooParams(String range) {
        return switch (range) {
            case "1D" -> new String[]{"5m",  "1d"};
            case "1T" -> new String[]{"30m", "5d"};
            case "1M" -> new String[]{"1d",  "1mo"};
            default   -> new String[]{"1d",  "1mo"};
        };
    }

    private String[] toBinanceParams(String range) {
        return switch (range) {
            case "1D" -> new String[]{"5m",  "288"};
            case "1T" -> new String[]{"30m", "240"};
            case "1M" -> new String[]{"1d",  "30"};
            default   -> new String[]{"1d",  "30"};
        };
    }
}
