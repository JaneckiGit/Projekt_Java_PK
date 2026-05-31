package com.stockdemo.service;

import com.stockdemo.api.BinanceApi;
import com.stockdemo.api.YahooFinanceApi;
import com.stockdemo.model.AssetType;
import com.stockdemo.model.Candle;
import com.stockdemo.model.Instrument;
import com.stockdemo.model.TickerData;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

//odpowiedzialne za refresh marketu i cen w tle
public class MarketDataService {

    private final YahooFinanceApi yahoo = new YahooFinanceApi();
    private final BinanceApi binance    = new BinanceApi();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "MarketData-Poller");
                t.setDaemon(true);
                return t;
            });

    // Master list of all instruments
    public final ObservableList<Instrument> instruments = FXCollections.observableArrayList(
            //STOCKS (US)
            new Instrument("AAPL",  "Apple Inc.",          AssetType.STOCK,  "AAPL"),
            new Instrument("MSFT",  "Microsoft",           AssetType.STOCK,  "MSFT"),
            new Instrument("TSLA",  "Tesla",               AssetType.STOCK,  "TSLA"),
            new Instrument("NVDA",  "NVIDIA",              AssetType.STOCK,  "NVDA"),
            new Instrument("AMZN",  "Amazon",              AssetType.STOCK,  "AMZN"),
            new Instrument("GOOGL", "Alphabet",            AssetType.STOCK,  "GOOGL"),
            new Instrument("META",  "Meta Platforms",      AssetType.STOCK,  "META"),
            new Instrument("AMD",   "Advanced Micro Dev.", AssetType.STOCK,  "AMD"),
            new Instrument("INTC",  "Intel",               AssetType.STOCK,  "INTC"),
            new Instrument("NFLX",  "Netflix",             AssetType.STOCK,  "NFLX"),
            //STOCKS (WIG20 - Poland)
            new Instrument("CDR",   "CD Projekt",          AssetType.STOCK,  "CDR.WA"),
            new Instrument("PKN",   "Orlen",               AssetType.STOCK,  "PKN.WA"),
            new Instrument("PKO",   "PKO BP",              AssetType.STOCK,  "PKO.WA"),
            new Instrument("DNP",   "Dino Polska",         AssetType.STOCK,  "DNP.WA"),
            new Instrument("KGH",   "KGHM",                AssetType.STOCK,  "KGH.WA"),
            //INDICES
            new Instrument("SPX",   "S&P 500",             AssetType.STOCK,  "^GSPC"),
            new Instrument("NDX",   "NASDAQ 100",          AssetType.STOCK,  "^NDX"),
            new Instrument("DJI",   "Dow Jones",           AssetType.STOCK,  "^DJI"),
            //CFD (Commodities & Forex)
            new Instrument("GOLD",  "Gold CFD",            AssetType.CFD,    "GC=F"),
            new Instrument("SILV",  "Silver CFD",          AssetType.CFD,    "SI=F"),
            new Instrument("OIL",   "Crude Oil CFD",       AssetType.CFD,    "CL=F"),
            new Instrument("NGAS",  "Natural Gas CFD",     AssetType.CFD,    "NG=F"),
            new Instrument("COPP",  "Copper CFD",          AssetType.CFD,    "HG=F"),
            new Instrument("EURUSD","EUR/USD CFD",         AssetType.CFD,    "EURUSD=X"),
            new Instrument("GBPUSD","GBP/USD CFD",         AssetType.CFD,    "GBPUSD=X"),
            new Instrument("USDJPY","USD/JPY CFD",         AssetType.CFD,    "USDJPY=X"),
            new Instrument("EURPLN","EUR/PLN CFD",         AssetType.CFD,    "EURPLN=X"),
            new Instrument("USDPLN","USD/PLN CFD",         AssetType.CFD,    "USDPLN=X"),
            //CRYPTO (Binance)
            new Instrument("BTC",   "Bitcoin",             AssetType.CRYPTO, "BTCUSDT"),
            new Instrument("ETH",   "Ethereum",            AssetType.CRYPTO, "ETHUSDT"),
            new Instrument("BNB",   "BNB",                 AssetType.CRYPTO, "BNBUSDT"),
            new Instrument("SOL",   "Solana",              AssetType.CRYPTO, "SOLUSDT"),
            new Instrument("XRP",   "XRP",                 AssetType.CRYPTO, "XRPUSDT"),
            new Instrument("ADA",   "Cardano",             AssetType.CRYPTO, "ADAUSDT"),
            new Instrument("DOGE",  "Dogecoin",            AssetType.CRYPTO, "DOGEUSDT"),
            new Instrument("DOT",   "Polkadot",            AssetType.CRYPTO, "DOTUSDT"),
            new Instrument("LINK",  "Chainlink",           AssetType.CRYPTO, "LINKUSDT"),
            new Instrument("MATIC", "Polygon",             AssetType.CRYPTO, "MATICUSDT")
    );

    /**
     * Start polling:
     *  - Crypto (Binance): every 5 seconds
     *  - Stocks/CFD (Yahoo): every 15 seconds
     * onUpdate is called on FX thread after EACH refresh cycle.
     */
    public void startPolling(Runnable onUpdate) {
        //Fast crypto timer (every 2 seconds)
        List<Instrument> cryptoList = instruments.stream()
                .filter(i -> i.getType() == AssetType.CRYPTO)
                .toList();
        scheduler.scheduleAtFixedRate(() -> {
            cryptoList.forEach(inst -> updatePriceSafely(inst, binance::fetchTicker, "Poll-Crypto"));
            if (onUpdate != null) {
                Platform.runLater(onUpdate);
            }
        }, 0, 2, TimeUnit.SECONDS);

        //Stocks/CFD timer (every 5 seconds)
        List<Instrument> stocksCfdList = instruments.stream()
                .filter(i -> i.getType() != AssetType.CRYPTO)
                .toList();
        scheduler.scheduleAtFixedRate(() -> {
            stocksCfdList.forEach(inst -> updatePriceSafely(inst, yahoo::fetchTicker, "Poll-Stock"));
            if (onUpdate != null) {
                Platform.runLater(onUpdate);
            }
        }, 1, 5, TimeUnit.SECONDS);
    }

    private void updatePriceSafely(Instrument inst, PriceFetcher fetcher, String logPrefix) {
        try {
            TickerData data = fetcher.fetch(inst.getApiSymbol());
            Platform.runLater(() -> {
                inst.setPrice(data.price());
                inst.setChangePercent(data.changePercent());
                inst.setDayHigh(data.dayHigh());
                inst.setDayLow(data.dayLow());
                inst.setVolume(data.volume());
                inst.setOpen(data.open());
                inst.setPrevClose(data.prevClose());
                inst.setBid(data.bid());
                inst.setAsk(data.ask());
            });
        } catch (Exception e) {
            System.err.println("[" + logPrefix + "] " + inst.getSymbol() + ": " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface PriceFetcher {
        TickerData fetch(String apiSymbol) throws Exception;
    }


    /** Fetch OHLCV candles for the selected instrument and time range. */
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
                List<Candle> finalCandles = candles;
                Platform.runLater(() -> callback.accept(finalCandles));
            } catch (Exception e) {
                System.err.println("[MarketDataService] loadCandles error: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }


    /** Yahoo Finance interval/range params based on time range label. */
    private String[] toYahooParams(String range) {
        return switch (range) {
            case "1D" -> new String[]{"5m",  "1d"};
            case "1T" -> new String[]{"30m", "5d"};
            case "1M" -> new String[]{"1d",  "1mo"};
            case "3M" -> new String[]{"1d",  "3mo"};
            case "1R" -> new String[]{"1wk", "1y"};
            case "5R" -> new String[]{"1mo", "5y"};
            default   -> new String[]{"1d",  "1mo"};
        };
    }

    /** Binance interval/limit params based on time range label. */
    private String[] toBinanceParams(String range) {
        return switch (range) {
            case "1D" -> new String[]{"5m",  "288"};
            case "1T" -> new String[]{"30m", "240"};
            case "1M" -> new String[]{"1d",  "30"};
            case "3M" -> new String[]{"1d",  "90"};
            case "1R" -> new String[]{"1w",  "52"};
            case "5R" -> new String[]{"1M",  "60"};
            default   -> new String[]{"1d",  "30"};
        };
    }
}
