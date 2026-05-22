package com.stockdemo.service;

import com.stockdemo.model.ClosedPosition;
import com.stockdemo.model.Instrument;
import com.stockdemo.model.Position;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PortfolioPersistence {

    private static final String FILE_NAME = "portfolio.json";
    // Zapisuje plik bezpośrednio w głównym katalogu projektu
    private static final String FILE_PATH = FILE_NAME;

    public static void save(PortfolioService portfolio) {
        JSONObject root = new JSONObject();
        root.put("balance", portfolio.getBalance());

        JSONArray openArr = new JSONArray();
        for (Position p : portfolio.openPositions) {
            JSONObject obj = new JSONObject();
            obj.put("symbol", p.getInstrument().getSymbol());
            obj.put("isLong", p.isLong());
            obj.put("quantity", p.getQuantity());
            obj.put("entryPrice", p.getEntryPrice());
            obj.put("stopLoss", p.getStopLoss());
            obj.put("takeProfit", p.getTakeProfit());
            openArr.put(obj);
        }
        root.put("openPositions", openArr);

        JSONArray closedArr = new JSONArray();
        for (ClosedPosition p : portfolio.closedPositions) {
            JSONObject obj = new JSONObject();
            obj.put("symbol", p.instrument().getSymbol());
            obj.put("isLong", p.isLong());
            obj.put("quantity", p.quantity());
            obj.put("entryPrice", p.entryPrice());
            obj.put("closePrice", p.closePrice());
            obj.put("realizedPnl", p.realizedPnl());
            obj.put("closeDate", p.closeDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            closedArr.put(obj);
        }
        root.put("closedPositions", closedArr);

        try (FileWriter file = new FileWriter(FILE_PATH)) {
            file.write(root.toString(2));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load(PortfolioService portfolio, MarketDataService marketData) {
        File file = new File(FILE_PATH);
        if (!file.exists()) return;

        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            JSONObject root = new JSONObject(content);

            portfolio.balanceProperty().set(root.getDouble("balance"));
            portfolio.openPositions.clear();
            portfolio.closedPositions.clear();

            if (root.has("openPositions")) {
                JSONArray openArr = root.getJSONArray("openPositions");
                for (int i = 0; i < openArr.length(); i++) {
                    JSONObject obj = openArr.getJSONObject(i);
                    String symbol = obj.getString("symbol");
                    Instrument inst = findInstrument(symbol, marketData);
                    if (inst != null) {
                        Position p = new Position(
                                inst,
                                obj.getBoolean("isLong"),
                                obj.getDouble("quantity"),
                                obj.getDouble("entryPrice"),
                                obj.getDouble("stopLoss"),
                                obj.getDouble("takeProfit")
                        );
                        portfolio.openPositions.add(p);
                    }
                }
            }

            if (root.has("closedPositions")) {
                JSONArray closedArr = root.getJSONArray("closedPositions");
                for (int i = 0; i < closedArr.length(); i++) {
                    JSONObject obj = closedArr.getJSONObject(i);
                    String symbol = obj.getString("symbol");
                    Instrument inst = findInstrument(symbol, marketData);
                    if (inst != null) {
                        ClosedPosition cp = new ClosedPosition(
                                inst,
                                obj.getBoolean("isLong"),
                                obj.getDouble("quantity"),
                                obj.getDouble("entryPrice"),
                                obj.getDouble("closePrice"),
                                obj.getDouble("realizedPnl"),
                                LocalDateTime.parse(obj.getString("closeDate"), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        );
                        portfolio.closedPositions.add(cp);
                    }
                }
            }
            portfolio.refreshPortfolio();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void reset(PortfolioService portfolio) {
        File file = new File(FILE_PATH);
        if (file.exists()) {
            file.delete();
        }
        portfolio.balanceProperty().set(100_000.0);
        portfolio.openPositions.clear();
        portfolio.closedPositions.clear();
        portfolio.refreshPortfolio();
    }

    private static Instrument findInstrument(String symbol, MarketDataService marketData) {
        for (Instrument i : marketData.instruments) {
            if (i.getSymbol().equals(symbol)) {
                return i;
            }
        }
        return null;
    }
}
