package com.stockdemo.ui;

import com.stockdemo.model.Candle;
import com.stockdemo.model.Instrument;
import com.stockdemo.model.Position;
import com.stockdemo.service.MarketDataService;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChartPanel extends BorderPane {

    private static final Color BG         = Color.web("#0d1117");
    private static final Color GRID       = Color.web("#21262d");
    private static final Color AXIS_TEXT  = Color.web("#8b949e");
    private static final Color BULL       = Color.web("#26a69a");
    private static final Color BEAR       = Color.web("#ef5350");
    private static final Color LINE_COLOR = Color.web("#1f6feb");
    private static final Color SL_COLOR   = Color.web("#f85149");
    private static final Color TP_COLOR   = Color.web("#3fb950");
    private static final Color CROSS      = Color.web("#30363d");

    private static final int PAD_LEFT  = 12;
    private static final int PAD_RIGHT = 60;
    private static final int PAD_TOP   = 20;
    private static final int PAD_BOT   = 36;
    private static final double DRAG_HIT = 8.0;

    private final MarketDataService marketData;
    private Instrument currentInstrument;
    private List<Candle> candles = new ArrayList<>();
    private String activeRange = "1M";
    private boolean showCandles  = true;

    private double slPrice = 0;
    private double tpPrice = 0;
    private boolean draggingSL = false;
    private boolean draggingTP = false;
    private Runnable onSlTpChanged;

    private double crossX = -1, crossY = -1;
    private double minPrice, maxPrice;

    private final Canvas canvas = new Canvas();
    private final Label  symbolLabel = new Label("Select instrument");
    private final Label  priceLabel  = new Label("");
    private final Label  changeLabel = new Label("");
    private final Label  loadingLbl  = new Label("Loading…");

    public ChartPanel(MarketDataService marketData) {
        this.marketData = marketData;
        this.setId("chartPanel");

        symbolLabel.setId("chartSymbolLabel");
        priceLabel.setId("chartPriceLabel");
        changeLabel.getStyleClass().add("change-positive");

        HBox ranges = new HBox(4);
        for (String r : new String[]{"1D", "1T", "1M", "3M", "1R", "5R"}) {
            Button btn = new Button(r);
            btn.getStyleClass().add("range-btn");
            if (r.equals(activeRange)) btn.getStyleClass().add("range-btn-active");
            btn.setOnAction(e -> {
                ranges.getChildren().forEach(n -> n.getStyleClass().removeAll("range-btn-active"));
                btn.getStyleClass().add("range-btn-active");
                activeRange = r;
                loadChart();
            });
            ranges.getChildren().add(btn);
        }

        ToggleButton btnLine   = new ToggleButton("Line");
        ToggleButton btnCandle = new ToggleButton("Candles");
        ToggleGroup  tg        = new ToggleGroup();
        btnLine.setToggleGroup(tg);
        btnCandle.setToggleGroup(tg);
        btnCandle.setSelected(true);
        btnLine.getStyleClass().add("chart-type-btn");
        btnCandle.getStyleClass().add("chart-type-btn");
        btnCandle.getStyleClass().add("chart-type-btn-active");

        btnCandle.selectedProperty().addListener((o, old, selected) -> {
            showCandles = selected;
            btnCandle.getStyleClass().removeAll("chart-type-btn-active");
            btnLine.getStyleClass().removeAll("chart-type-btn-active");
            if (selected) btnCandle.getStyleClass().add("chart-type-btn-active");
            else          btnLine.getStyleClass().add("chart-type-btn-active");
            redraw();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(12, symbolLabel, priceLabel, changeLabel, spacer, ranges, new Separator(), new HBox(4, btnLine, btnCandle));
        toolbar.setId("chartToolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        StackPane canvasPane = new StackPane(canvas, loadingLbl);
        loadingLbl.getStyleClass().add("loading-label");
        loadingLbl.setVisible(false);

        canvas.widthProperty().bind(canvasPane.widthProperty());
        canvas.heightProperty().bind(canvasPane.heightProperty());
        canvas.widthProperty().addListener(e  -> redraw());
        canvas.heightProperty().addListener(e -> redraw());

        canvas.setOnMouseMoved(e -> { crossX = e.getX(); crossY = e.getY(); redraw(); });
        canvas.setOnMouseExited(e -> { crossX = -1; crossY = -1; redraw(); });
        canvas.setOnMousePressed(e -> {
            double slY = priceToY(slPrice);
            double tpY = priceToY(tpPrice);
            if (slPrice > 0 && Math.abs(e.getY() - slY) < DRAG_HIT) draggingSL = true;
            else if (tpPrice > 0 && Math.abs(e.getY() - tpY) < DRAG_HIT) draggingTP = true;
        });
        canvas.setOnMouseDragged(e -> {
            crossX = e.getX(); crossY = e.getY();
            if (draggingSL) { slPrice = yToPrice(e.getY()); if (onSlTpChanged != null) onSlTpChanged.run(); }
            if (draggingTP) { tpPrice = yToPrice(e.getY()); if (onSlTpChanged != null) onSlTpChanged.run(); }
            redraw();
        });
        canvas.setOnMouseReleased(e -> { draggingSL = false; draggingTP = false; });

        this.setTop(toolbar);
        this.setCenter(canvasPane);
    }

    public void loadInstrument(Instrument instrument) {
        this.currentInstrument = instrument;
        symbolLabel.setText(instrument.getSymbol());
        slPrice = 0; tpPrice = 0;
        loadChart();
    }

    public void setSlPrice(double price) { this.slPrice = price; redraw(); }
    public void setTpPrice(double price) { this.tpPrice = price; redraw(); }
    public double getSlPrice()           { return slPrice; }
    public double getTpPrice()           { return tpPrice; }
    public void setOnSlTpChanged(Runnable r) { this.onSlTpChanged = r; }

    public void showPositionLines(Position pos) {
        if (pos == null) { slPrice = 0; tpPrice = 0; }
        else {
            slPrice = pos.getStopLoss();
            tpPrice = pos.getTakeProfit();
            pos.stopLossProperty().addListener((o, old, v) -> { slPrice = v.doubleValue(); redraw(); });
            pos.takeProfitProperty().addListener((o, old, v) -> { tpPrice = v.doubleValue(); redraw(); });
        }
        redraw();
    }

    public void refreshPrice(Instrument inst) {
        if (inst == null) return;
        double p = inst.getPrice();
        double ch = inst.getChangePercent();
        priceLabel.setText(formatPrice(p));
        String sign = ch >= 0 ? "+" : "";
        changeLabel.setText(sign + String.format(Locale.US, "%.2f%%", ch));
        changeLabel.getStyleClass().removeAll("change-positive", "change-negative");
        changeLabel.getStyleClass().add(ch >= 0 ? "change-positive" : "change-negative");
    }

    private void loadChart() {
        if (currentInstrument == null) return;
        loadingLbl.setVisible(true);
        marketData.loadCandles(currentInstrument, activeRange, data -> {
            this.candles = data;
            loadingLbl.setVisible(false);
            refreshPrice(currentInstrument);
            redraw();
        });
    }

    private void redraw() {
        if (canvas.getWidth() <= 0 || canvas.getHeight() <= 0) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double W = canvas.getWidth();
        double H = canvas.getHeight();

        gc.setFill(BG);
        gc.fillRect(0, 0, W, H);

        if (candles.isEmpty()) {
            gc.setFill(AXIS_TEXT);
            gc.setFont(Font.font("Inter", 14));
            gc.fillText("No data", W / 2 - 30, H / 2);
            return;
        }

        minPrice = candles.stream().mapToDouble(Candle::getLow).min().orElse(0);
        maxPrice = candles.stream().mapToDouble(Candle::getHigh).max().orElse(1);
        double priceSpan = maxPrice - minPrice;
        if (priceSpan == 0) priceSpan = 1;
        minPrice -= priceSpan * 0.05;
        maxPrice += priceSpan * 0.05;
        priceSpan = maxPrice - minPrice;

        double cX = PAD_LEFT;
        double cY = PAD_TOP;
        double cW = W - PAD_LEFT - PAD_RIGHT;
        double cH = H - PAD_TOP - PAD_BOT;

        drawGrid(gc, cX, cY, cW, cH, priceSpan);
        if (showCandles) drawCandles(gc, cX, cY, cW, cH);
        else             drawLine(gc, cX, cY, cW, cH);

        drawSlTpLines(gc, cX, cY, cW, cH, W);
        drawCrosshair(gc, cX, cY, cW, cH, W, H, priceSpan);
        drawAxisLabels(gc, cX, cY, cW, cH, W, priceSpan);
    }

    private void drawGrid(GraphicsContext gc, double cX, double cY, double cW, double cH, double priceSpan) {
        gc.setStroke(GRID);
        gc.setLineWidth(0.5);
        for (int i = 0; i <= 6; i++) {
            double y = cY + cH * i / 6;
            gc.strokeLine(cX, y, cX + cW, y);
        }
        for (int i = 0; i <= 6; i++) {
            double x = cX + cW * i / 6;
            gc.strokeLine(x, cY, x, cY + cH);
        }
    }

    private void drawCandles(GraphicsContext gc, double cX, double cY, double cW, double cH) {
        int n = candles.size();
        double effectiveN = Math.max(n, 50.0);
        double totalW = cW / effectiveN;
        double bodyW  = Math.max(1, totalW * 0.6);
        double startX = cX + cW - (n * totalW);

        for (int i = 0; i < n; i++) {
            Candle c  = candles.get(i);
            double cx = startX + (i + 0.5) * totalW;
            double oY = priceToYInArea(c.getOpen(),  cY, cH);
            double cY2= priceToYInArea(c.getClose(), cY, cH);
            double hY = priceToYInArea(c.getHigh(),  cY, cH);
            double lY = priceToYInArea(c.getLow(),   cY, cH);

            Color col = c.isBullish() ? BULL : BEAR;
            gc.setStroke(col);
            gc.setFill(col);
            gc.setLineWidth(1.0);
            gc.strokeLine(cx, hY, cx, lY);

            double top    = Math.min(oY, cY2);
            double height = Math.max(1, Math.abs(oY - cY2));
            gc.fillRect(cx - bodyW / 2, top, bodyW, height);
        }
    }

    private void drawLine(GraphicsContext gc, double cX, double cY, double cW, double cH) {
        int n = candles.size();
        if (n < 2) return;
        double effectiveN = Math.max(n, 50.0);
        double totalW = cW / effectiveN;
        double startX = cX + cW - (n * totalW);

        gc.setStroke(LINE_COLOR);
        gc.setLineWidth(1.5);
        gc.beginPath();
        for (int i = 0; i < n; i++) {
            double x = startX + (i + 0.5) * totalW;
            double y = priceToYInArea(candles.get(i).getClose(), cY, cH);
            if (i == 0) gc.moveTo(x, y);
            else        gc.lineTo(x, y);
        }
        gc.stroke();
    }

    private void drawSlTpLines(GraphicsContext gc, double cX, double cY, double cW, double cH, double W) {
        if (slPrice > 0 && slPrice >= minPrice && slPrice <= maxPrice) {
            double y = priceToYInArea(slPrice, cY, cH);
            gc.setStroke(SL_COLOR);
            gc.setLineWidth(1.5);
            gc.setLineDashes(6, 4);
            gc.strokeLine(cX, y, cX + cW, y);
            gc.setLineDashes();
            gc.setFill(SL_COLOR);
            gc.setFont(Font.font("Inter", FontWeight.BOLD, 11));
            gc.fillText("SL " + formatPrice(slPrice), cX + cW + 4, y + 4);
        }
        if (tpPrice > 0 && tpPrice >= minPrice && tpPrice <= maxPrice) {
            double y = priceToYInArea(tpPrice, cY, cH);
            gc.setStroke(TP_COLOR);
            gc.setLineWidth(1.5);
            gc.setLineDashes(6, 4);
            gc.strokeLine(cX, y, cX + cW, y);
            gc.setLineDashes();
            gc.setFill(TP_COLOR);
            gc.setFont(Font.font("Inter", FontWeight.BOLD, 11));
            gc.fillText("TP " + formatPrice(tpPrice), cX + cW + 4, y + 4);
        }
    }

    private void drawCrosshair(GraphicsContext gc, double cX, double cY, double cW, double cH, double W, double H, double priceSpan) {
        if (crossX < cX || crossX > cX + cW || crossY < cY || crossY > cY + cH) return;
        gc.setStroke(CROSS);
        gc.setLineWidth(0.7);
        gc.setLineDashes(3, 3);
        gc.strokeLine(cX, crossY, cX + cW, crossY);
        gc.strokeLine(crossX, cY, crossX, cY + cH);
        gc.setLineDashes();

        double price = yToPrice(crossY);
        gc.setFill(Color.web("#30363d"));
        gc.fillRoundRect(cX + cW + 2, crossY - 9, 58, 18, 4, 4);
        gc.setFill(AXIS_TEXT);
        gc.setFont(Font.font("Inter", 10));
        gc.fillText(formatPrice(price), cX + cW + 6, crossY + 5);
    }

    private void drawAxisLabels(GraphicsContext gc, double cX, double cY, double cW, double cH, double W, double priceSpan) {
        gc.setFill(AXIS_TEXT);
        gc.setFont(Font.font("Inter", 10));

        for (int i = 0; i <= 6; i++) {
            double p = maxPrice - (priceSpan * i / 6);
            double y = cY + cH * i / 6;
            gc.fillText(formatPrice(p), cX + cW + 4, y + 4);
        }

        if (candles.isEmpty()) return;
        int n = candles.size();
        int step = Math.max(1, n / 6);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd").withZone(ZoneId.systemDefault());
        double effectiveN = Math.max(n, 50.0);
        double totalW = cW / effectiveN;
        double startX = cX + cW - (n * totalW);

        for (int i = 0; i < n; i += step) {
            double x = startX + (i + 0.5) * totalW;
            String label = fmt.format(Instant.ofEpochSecond(candles.get(i).getTimestamp()));
            gc.fillText(label, x - 15, cY + cH + 20);
        }
    }

    private double priceToYInArea(double price, double areaY, double areaH) {
        double ratio = (maxPrice - price) / (maxPrice - minPrice);
        return areaY + ratio * areaH;
    }
    private double priceToY(double price) {
        double cH = canvas.getHeight() - PAD_TOP - PAD_BOT;
        return priceToYInArea(price, PAD_TOP, cH);
    }
    private double yToPrice(double y) {
        double cH = canvas.getHeight() - PAD_TOP - PAD_BOT;
        double ratio = (y - PAD_TOP) / cH;
        return maxPrice - ratio * (maxPrice - minPrice);
    }

    private String formatPrice(double p) {
        try {
            if (p < 1)   return String.format(Locale.US, "%.5f", p);
            if (p < 100) return String.format(Locale.US, "%.4f", p);
            return String.format(Locale.US, "%.2f", p);
        } catch (Exception e) {
            return String.valueOf(p);
        }
    }
}
