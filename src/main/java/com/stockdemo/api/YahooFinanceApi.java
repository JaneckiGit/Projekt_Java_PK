package com.stockdemo.api;

import com.stockdemo.model.Candle;
import com.stockdemo.model.Instrument;
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
 * Klient do komunikacji z publicznym API Yahoo Finance (akcje, CFD).
 * Nie wymaga klucza API.
 */
public class YahooFinanceApi {

    private static final String BASE = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private final HttpClient http;

    public YahooFinanceApi() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<Candle> getCandles(String symbol, String interval, String range) throws Exception {
        String url = BASE + encode(symbol) + "?interval=" + interval + "&range=" + range;
        String body = get(url);
        return parseCandles(body);
    }

    public void updatePrice(Instrument instrument) throws Exception {
        String url = BASE + encode(instrument.getApiSymbol()) + "?interval=1d&range=5d";
        String body = get(url);
        JSONObject root = new JSONObject(body);
        JSONObject result = root
                .getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0);

        JSONObject meta = result.getJSONObject("meta");

        double price     = meta.optDouble("regularMarketPrice",
                meta.optDouble("previousClose", 0));
        double prevClose = meta.optDouble("chartPreviousClose",
                meta.optDouble("previousClose", price));
        double changePct = prevClose != 0 ? ((price - prevClose) / prevClose) * 100.0 : 0;

        instrument.setPrice(price);
        instrument.setChangePercent(changePct);
        instrument.setPrevClose(prevClose);
        instrument.setDayHigh(meta.optDouble("regularMarketDayHigh",
                meta.optDouble("fiftyTwoWeekHigh", 0)));
        instrument.setDayLow(meta.optDouble("regularMarketDayLow",
                meta.optDouble("fiftyTwoWeekLow", 0)));
        instrument.setVolume(meta.optDouble("regularMarketVolume", 0));
        instrument.setOpen(meta.optDouble("regularMarketOpen", price));

        // Symulacja spreadu rzędu 0.05% dla akcji
        instrument.setBid(price * 0.9995);
        instrument.setAsk(price * 1.0005);
    }

    private String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Yahoo HTTP " + resp.statusCode() + " for " + url);
        }
        return resp.body();
    }

    private List<Candle> parseCandles(String body) {
        List<Candle> candles = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(body);
            JSONObject result = root.getJSONObject("chart").getJSONArray("result").getJSONObject(0);

            JSONArray timestamps = result.getJSONArray("timestamp");
            JSONObject indicators = result.getJSONObject("indicators");
            JSONObject q = indicators.getJSONArray("quote").getJSONObject(0);

            JSONArray opens  = q.getJSONArray("open");
            JSONArray highs  = q.getJSONArray("high");
            JSONArray lows   = q.getJSONArray("low");
            JSONArray closes = q.getJSONArray("close");
            JSONArray vols   = q.optJSONArray("volume");

            for (int i = 0; i < timestamps.length(); i++) {
                if (closes.isNull(i) || opens.isNull(i)) {
                    continue;
                }
                long ts     = timestamps.getLong(i);
                double open  = opens.optDouble(i, 0);
                double high  = highs.optDouble(i, 0);
                double low   = lows.optDouble(i, 0);
                double close = closes.optDouble(i, 0);
                double vol   = (vols != null && !vols.isNull(i)) ? vols.optDouble(i, 0) : 0;
                candles.add(new Candle(ts, open, high, low, close, vol));
            }
        } catch (Exception e) {
            System.err.println("[YahooFinanceApi] Parse error: " + e.getMessage());
        }
        return candles;
    }

    private String encode(String symbol) {
        return symbol.replace("^", "%5E").replace("=", "%3D");
    }
}
