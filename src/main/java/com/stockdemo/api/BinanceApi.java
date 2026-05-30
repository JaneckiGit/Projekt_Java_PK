package com.stockdemo.api;

import com.stockdemo.model.Candle;
import com.stockdemo.model.Instrument;
import com.stockdemo.model.TickerData;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Klient do komunikacji z publicznym API Binance (kryptowaluty).
 * Nie wymaga klucza API.
 */
public class BinanceApi {

    private static final String BASE = "https://api.binance.com/api/v3";
    private final HttpClient http;

    public BinanceApi() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<Candle> getCandles(String symbol, String interval, int limit) throws Exception {
        String url = BASE + "/klines?symbol=" + symbol + "&interval=" + interval + "&limit=" + limit;
        String body = get(url);

        List<Candle> candles = new ArrayList<>();
        JSONArray array = new JSONArray(body);
        for (int i = 0; i < array.length(); i++) {
            JSONArray k = array.getJSONArray(i);
            long ts     = k.getLong(0) / 1000;
            double open  = k.getDouble(1);
            double high  = k.getDouble(2);
            double low   = k.getDouble(3);
            double close = k.getDouble(4);
            double vol   = k.getDouble(5);
            candles.add(new Candle(ts, open, high, low, close, vol));
        }
        return candles;
    }

    public TickerData fetchTicker(String apiSymbol) throws Exception {
        String url = BASE + "/ticker/24hr?symbol=" + apiSymbol;
        String body = get(url);
        JSONObject obj = new JSONObject(body);

        double lastPrice = obj.getDouble("lastPrice");
        double changePercent = obj.getDouble("priceChangePercent");
        double dayHigh = obj.getDouble("highPrice");
        double dayLow = obj.getDouble("lowPrice");
        double volume = obj.getDouble("volume");
        double open = obj.getDouble("openPrice");
        double prevClose = obj.getDouble("prevClosePrice");

        // Symulujemy spread rzędu 0.1% dla krypto
        double bid = lastPrice * 0.999;
        double ask = lastPrice * 1.001;

        return new TickerData(
            lastPrice,
            changePercent,
            dayHigh,
            dayLow,
            volume,
            open,
            prevClose,
            bid,
            ask
        );
    }

    private String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Binance HTTP " + resp.statusCode() + " for " + url);
        }
        return resp.body();
    }
}
