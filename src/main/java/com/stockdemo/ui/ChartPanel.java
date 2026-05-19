package com.stockdemo.ui;

import com.stockdemo.model.Candle;
import com.stockdemo.model.Instrument;
import com.stockdemo.model.Position;
import com.stockdemo.service.MarketDataService;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
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
import java.util.function.Consumer;

public class ChartPanel extends BorderPane {

    public enum ChartState {
        IDLE, SELECTING_SL, SELECTING_TP
    }

    private static final Color BG = Color.web("#0d1117");
    private static final Color GRID = Color.web("#21262d");
    private static final Color AXIS_TEXT = Color.web("#8b949e");
    private static final Color BULL = Color.web("#26a69a");
    private static final Color BEAR = Color.web("#ef5350");
    private static final Color LINE_COLOR = Color.web("#8b949e");
    private static final Color ENTRY_COLOR = Color.web("#1f6feb");
    private static final Color SL_COLOR = Color.web("#f85149");
    private static final Color TP_COLOR = Color.web("#3fb950");
    private static final Color CROSS = Color.web("#30363d");

    private static final int PAD_LEFT = 12;
    private static final int PAD_RIGHT = 60;
    private static final int PAD_TOP = 20;
    private static final int PAD_BOT = 36;
    private static final double DRAG_HIT = 8.0;

    private final MarketDataService marketData;
    private Instrument currentInstrument;
    private List<Candle> candles = new ArrayList<>();
    private String activeRange = "1M";
    private boolean showCandles = true;

    private ChartState state = ChartState.IDLE;
    private Consumer<Double> onPriceSelected;

    // Pending order preview
    private double pendingSlPrice = 0;
    private double pendingTpPrice = 0;
    private double pendingEntryPrice = 0; 
    private boolean showPendingPreview = false;
    private Runnable onPendingSlTpChanged;

    // Open positions for current instrument
    private List<Position> openPositions = new ArrayList<>();
    
    // Dragging state
    private boolean draggingPendingSL = false;
    private boolean draggingPendingTP = false;
    private Position draggedPosSL = null;
    private Position draggedPosTP = null;

    private double crossX = -1, crossY = -1;
    private double minPrice, maxPrice;

    private final Canvas canvas = new Canvas();
    private final Label symbolLabel = new Label("Select instrument");
    private final Label priceLabel = new Label("");
    private final Label changeLabel = new Label("");
    private final Label loadingLbl = new Label("Loading…");

    public ChartPanel(MarketDataService marketData) {
        this.marketData = marketData;
        this.setId("chartPanel");
        
        // Ensure panel can receive key events for ESC
        this.setFocusTraversable(true);
        this.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && state != ChartState.IDLE) {
                setState(ChartState.IDLE, null);
            }
        });

        symbolLabel.setId("chartSymbolLabel");
        priceLabel.setId("chartPriceLabel");
        changeLabel.getStyleClass().add("change-positive");

        HBox ranges = new HBox(4);
        for (String r : new String[] { "1D", "1T", "1M", "3M", "1R", "5R" }) {
            Button btn = new Button(r);
            btn.getStyleClass().add("range-btn");
            if (r.equals(activeRange))
                btn.getStyleClass().add("range-btn-active");
            btn.setOnAction(e -> {
                ranges.getChildren().forEach(n -> n.getStyleClass().removeAll("range-btn-active"));
                btn.getStyleClass().add("range-btn-active");
                activeRange = r;
                loadChart();
            });
            ranges.getChildren().add(btn);
        }

        ToggleButton btnLine = new ToggleButton("Line");
        ToggleButton btnCandle = new ToggleButton("Candles");
        ToggleGroup tg = new ToggleGroup();
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
            if (selected)
                btnCandle.getStyleClass().add("chart-type-btn-active");
            else
                btnLine.getStyleClass().add("chart-type-btn-active");
            redraw();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(12, symbolLabel, priceLabel, changeLabel, spacer, ranges, new Separator(),
                new HBox(4, btnLine, btnCandle));
        toolbar.setId("chartToolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        StackPane canvasPane = new StackPane(canvas, loadingLbl);
        loadingLbl.getStyleClass().add("loading-label");
        loadingLbl.setVisible(false);

        canvas.widthProperty().bind(canvasPane.widthProperty());
        canvas.heightProperty().bind(canvasPane.heightProperty());
        canvas.widthProperty().addListener(e -> redraw());
        canvas.heightProperty().addListener(e -> redraw());

        canvas.setOnMouseMoved(e -> {
            crossX = e.getX();
            crossY = e.getY();
            redraw();
        });
        canvas.setOnMouseExited(e -> {
            crossX = -1;
            crossY = -1;
            redraw();
        });
        canvas.setOnMousePressed(e -> {
            this.requestFocus(); // For ESC key
            
            if (state != ChartState.IDLE) {
                double price = yToPrice(e.getY());
                if (onPriceSelected != null) {
                    onPriceSelected.accept(price);
                }
                setState(ChartState.IDLE, null);
                return;
            }

            // Check dragging hits
            double my = e.getY();
            
            // Check open positions first
            for (Position p : openPositions) {
                if (p.getInstrument().equals(currentInstrument)) {
                    if (p.getStopLoss() > 0 && Math.abs(my - priceToY(p.getStopLoss())) < DRAG_HIT) {
                        draggedPosSL = p;
                        return;
                    }
                    if (p.getTakeProfit() > 0 && Math.abs(my - priceToY(p.getTakeProfit())) < DRAG_HIT) {
                        draggedPosTP = p;
                        return;
                    }
                }
            }
            
            // Check pending order lines
            if (pendingSlPrice > 0 && Math.abs(my - priceToY(pendingSlPrice)) < DRAG_HIT) {
                draggingPendingSL = true;
                return;
            }
            if (pendingTpPrice > 0 && Math.abs(my - priceToY(pendingTpPrice)) < DRAG_HIT) {
                draggingPendingTP = true;
                return;
            }
        });
        canvas.setOnMouseDragged(e -> {
            crossX = e.getX();
            crossY = e.getY();
            double price = yToPrice(e.getY());

            if (draggedPosSL != null) {
                draggedPosSL.setStopLoss(price);
            } else if (draggedPosTP != null) {
                draggedPosTP.setTakeProfit(price);
            } else if (draggingPendingSL) {
                pendingSlPrice = price;
                if (onPendingSlTpChanged != null) onPendingSlTpChanged.run();
            } else if (draggingPendingTP) {
                pendingTpPrice = price;
                if (onPendingSlTpChanged != null) onPendingSlTpChanged.run();
            }
            redraw();
        });
        canvas.setOnMouseReleased(e -> {
            draggingPendingSL = false;
            draggingPendingTP = false;
            draggedPosSL = null;
            draggedPosTP = null;
        });

        this.setTop(toolbar);
        this.setCenter(canvasPane);
    }
    
    public void setState(ChartState newState, Consumer<Double> callback) {
        this.state = newState;
        this.onPriceSelected = callback;
        if (state != ChartState.IDLE) {
            canvas.setCursor(Cursor.CROSSHAIR);
        } else {
            canvas.setCursor(Cursor.DEFAULT);
        }
        redraw();
    }

    public void loadInstrument(Instrument instrument) {
        this.currentInstrument = instrument;
        symbolLabel.setText(instrument.getSymbol());
        pendingSlPrice = 0;
        pendingTpPrice = 0;
        showPendingPreview = false;
        loadChart();
    }

    public void setPendingSlPrice(double price) {
        this.pendingSlPrice = price;
        redraw();
    }

    public void setPendingTpPrice(double price) {
        this.pendingTpPrice = price;
        redraw();
    }
    
    public void setPendingPreview(double entryPrice, boolean isLong) {
        this.pendingEntryPrice = entryPrice;
        this.showPendingPreview = true;
        redraw();
    }
    
    public void hidePendingPreview() {
        this.showPendingPreview = false;
        redraw();
    }

    public double getPendingSlPrice() { return pendingSlPrice; }
    public double getPendingTpPrice() { return pendingTpPrice; }

    public void setOnPendingSlTpChanged(Runnable r) {
        this.onPendingSlTpChanged = r;
    }

    public void setOpenPositions(List<Position> positions) {
        this.openPositions = positions;
        redraw();
    }

    public void refreshPrice(Instrument inst) {
        if (inst == null || currentInstrument == null || !inst.getSymbol().equals(currentInstrument.getSymbol()))
            return;
        double p = inst.getPrice();
        double ch = inst.getChangePercent();
        priceLabel.setText(formatPrice(p));
        String sign = ch >= 0 ? "+" : "";
        changeLabel.setText(sign + String.format(Locale.US, "%.2f%%", ch));
        changeLabel.getStyleClass().removeAll("change-positive", "change-negative");
        changeLabel.getStyleClass().add(ch >= 0 ? "change-positive" : "change-negative");
        
        // Update pending preview if following market
        if (showPendingPreview && currentInstrument != null) {
            redraw(); // Simple redraw will fetch latest ask/bid if needed
        }
    }

    private void loadChart() {
        if (currentInstrument == null)
            return;
        loadingLbl.setVisible(true);
        marketData.loadCandles(currentInstrument, activeRange, data -> {
            this.candles = data;
            loadingLbl.setVisible(false);
            refreshPrice(currentInstrument);
            redraw();
        });
    }

    private void redraw() {
        if (canvas.getWidth() <= 0 || canvas.getHeight() <= 0)
            return;
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

        minPrice = candles.stream().mapToDouble(Candle::low).min().orElse(0);
        maxPrice = candles.stream().mapToDouble(Candle::high).max().orElse(1);
        double priceSpan = maxPrice - minPrice;
        if (priceSpan == 0)
            priceSpan = 1;
        minPrice -= priceSpan * 0.05;
        maxPrice += priceSpan * 0.05;
        priceSpan = maxPrice - minPrice;

        double cX = PAD_LEFT;
        double cY = PAD_TOP;
        double cW = W - PAD_LEFT - PAD_RIGHT;
        double cH = H - PAD_TOP - PAD_BOT;

        drawGrid(gc, cX, cY, cW, cH, priceSpan);
        if (showCandles)
            drawCandles(gc, cX, cY, cW, cH);
        else
            drawLine(gc, cX, cY, cW, cH);

        drawPositions(gc, cX, cY, cW, cH);
        drawPendingLines(gc, cX, cY, cW, cH);
        
        drawCrosshairAndSelection(gc, cX, cY, cW, cH, W, H);
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
        double bodyW = Math.max(1, totalW * 0.6);
        double startX = cX + cW - (n * totalW);

        for (int i = 0; i < n; i++) {
            Candle c = candles.get(i);
            double cx = startX + (i + 0.5) * totalW;
            double oY = priceToYInArea(c.open(), cY, cH);
            double cY2 = priceToYInArea(c.close(), cY, cH);
            double hY = priceToYInArea(c.high(), cY, cH);
            double lY = priceToYInArea(c.low(), cY, cH);

            Color col = c.isBullish() ? BULL : BEAR;
            gc.setStroke(col);
            gc.setFill(col);
            gc.setLineWidth(1.0);
            gc.strokeLine(cx, hY, cx, lY);

            double top = Math.min(oY, cY2);
            double height = Math.max(1, Math.abs(oY - cY2));
            gc.fillRect(cx - bodyW / 2, top, bodyW, height);
        }
    }

    private void drawLine(GraphicsContext gc, double cX, double cY, double cW, double cH) {
        int n = candles.size();
        if (n < 2)
            return;
        double effectiveN = Math.max(n, 50.0);
        double totalW = cW / effectiveN;
        double startX = cX + cW - (n * totalW);

        gc.setStroke(LINE_COLOR);
        gc.setLineWidth(1.5);
        gc.beginPath();
        for (int i = 0; i < n; i++) {
            double x = startX + (i + 0.5) * totalW;
            double y = priceToYInArea(candles.get(i).close(), cY, cH);
            if (i == 0)
                gc.moveTo(x, y);
            else
                gc.lineTo(x, y);
        }
        gc.stroke();
    }
    
    private void drawPositions(GraphicsContext gc, double cX, double cY, double cW, double cH) {
        if (currentInstrument == null) return;
        
        for (Position p : openPositions) {
            if (!p.getInstrument().getSymbol().equals(currentInstrument.getSymbol())) continue;
            
            // Draw Entry Line
            double entryY = priceToYInArea(p.getEntryPrice(), cY, cH);
            if (entryY >= cY && entryY <= cY + cH) {
                gc.setStroke(ENTRY_COLOR);
                gc.setLineWidth(1.5);
                gc.strokeLine(cX, entryY, cX + cW, entryY);
                gc.setFill(ENTRY_COLOR);
                gc.setFont(Font.font("Inter", FontWeight.BOLD, 10));
                gc.fillText("Entry " + formatPrice(p.getEntryPrice()), cX + 5, entryY - 4);
            }
            
            // Draw SL Line
            if (p.getStopLoss() > 0) {
                double slY = priceToYInArea(p.getStopLoss(), cY, cH);
                if (slY >= cY && slY <= cY + cH) {
                    gc.setStroke(SL_COLOR);
                    gc.setLineWidth(1.5);
                    gc.setLineDashes(4, 4);
                    gc.strokeLine(cX, slY, cX + cW, slY);
                    gc.setLineDashes();
                    gc.setFill(SL_COLOR);
                    gc.setFont(Font.font("Inter", FontWeight.BOLD, 10));
                    gc.fillText("SL " + formatPrice(p.getStopLoss()), cX + cW + 4, slY + 4);
                }
            }
            
            // Draw TP Line
            if (p.getTakeProfit() > 0) {
                double tpY = priceToYInArea(p.getTakeProfit(), cY, cH);
                if (tpY >= cY && tpY <= cY + cH) {
                    gc.setStroke(TP_COLOR);
                    gc.setLineWidth(1.5);
                    gc.setLineDashes(4, 4);
                    gc.strokeLine(cX, tpY, cX + cW, tpY);
                    gc.setLineDashes();
                    gc.setFill(TP_COLOR);
                    gc.setFont(Font.font("Inter", FontWeight.BOLD, 10));
                    gc.fillText("TP " + formatPrice(p.getTakeProfit()), cX + cW + 4, tpY + 4);
                }
            }
        }
    }

    private void drawPendingLines(GraphicsContext gc, double cX, double cY, double cW, double cH) {
        if (showPendingPreview && pendingEntryPrice > 0) {
            double entryY = priceToYInArea(pendingEntryPrice, cY, cH);
            if (entryY >= cY && entryY <= cY + cH) {
                gc.setStroke(ENTRY_COLOR.deriveColor(1, 1, 1, 0.6)); // Transparent blue
                gc.setLineWidth(1.5);
                gc.setLineDashes(6, 4);
                gc.strokeLine(cX, entryY, cX + cW, entryY);
                gc.setLineDashes();
                gc.setFill(ENTRY_COLOR.deriveColor(1, 1, 1, 0.8));
                gc.setFont(Font.font("Inter", FontWeight.NORMAL, 10));
                gc.fillText("New Entry " + formatPrice(pendingEntryPrice), cX + 5, entryY - 4);
            }
        }

        if (pendingSlPrice > 0 && pendingSlPrice >= minPrice && pendingSlPrice <= maxPrice) {
            double y = priceToYInArea(pendingSlPrice, cY, cH);
            gc.setStroke(SL_COLOR.deriveColor(1, 1, 1, 0.6));
            gc.setLineWidth(1.5);
            gc.setLineDashes(6, 4);
            gc.strokeLine(cX, y, cX + cW, y);
            gc.setLineDashes();
            gc.setFill(SL_COLOR);
            gc.setFont(Font.font("Inter", FontWeight.NORMAL, 10));
            gc.fillText("SL " + formatPrice(pendingSlPrice), cX + cW + 4, y + 4);
        }
        if (pendingTpPrice > 0 && pendingTpPrice >= minPrice && pendingTpPrice <= maxPrice) {
            double y = priceToYInArea(pendingTpPrice, cY, cH);
            gc.setStroke(TP_COLOR.deriveColor(1, 1, 1, 0.6));
            gc.setLineWidth(1.5);
            gc.setLineDashes(6, 4);
            gc.strokeLine(cX, y, cX + cW, y);
            gc.setLineDashes();
            gc.setFill(TP_COLOR);
            gc.setFont(Font.font("Inter", FontWeight.NORMAL, 10));
            gc.fillText("TP " + formatPrice(pendingTpPrice), cX + cW + 4, y + 4);
        }
    }

    private void drawCrosshairAndSelection(GraphicsContext gc, double cX, double cY, double cW, double cH, double W, double H) {
        if (crossX < cX || crossX > cX + cW || crossY < cY || crossY > cY + cH)
            return;
            
        Color crossColor = CROSS;
        if (state == ChartState.SELECTING_SL) crossColor = SL_COLOR;
        else if (state == ChartState.SELECTING_TP) crossColor = TP_COLOR;

        gc.setStroke(crossColor);
        gc.setLineWidth(0.7);
        gc.setLineDashes(3, 3);
        gc.strokeLine(cX, crossY, cX + cW, crossY);
        gc.strokeLine(crossX, cY, crossX, cY + cH);
        gc.setLineDashes();

        double price = yToPrice(crossY);
        gc.setFill(crossColor.equals(CROSS) ? Color.web("#30363d") : crossColor.deriveColor(1,1,1,0.8));
        gc.fillRoundRect(cX + cW + 2, crossY - 9, 58, 18, 4, 4);
        
        gc.setFill(state == ChartState.IDLE ? AXIS_TEXT : Color.WHITE);
        gc.setFont(Font.font("Inter", FontWeight.BOLD, 10));
        gc.fillText(formatPrice(price), cX + cW + 6, crossY + 5);
        
        if (state != ChartState.IDLE) {
            gc.setFill(crossColor);
            gc.fillText(state == ChartState.SELECTING_SL ? "Click to set SL" : "Click to set TP", crossX + 10, crossY - 10);
            gc.setFont(Font.font("Inter", 10));
            gc.fillText("Press ESC to cancel", crossX + 10, crossY + 15);
        }
    }

    private void drawAxisLabels(GraphicsContext gc, double cX, double cY, double cW, double cH, double W,
            double priceSpan) {
        gc.setFill(AXIS_TEXT);
        gc.setFont(Font.font("Inter", 10));

        for (int i = 0; i <= 6; i++) {
            double p = maxPrice - (priceSpan * i / 6);
            double y = cY + cH * i / 6;
            gc.fillText(formatPrice(p), cX + cW + 4, y + 4);
        }

        if (candles.isEmpty())
            return;
        int n = candles.size();
        int step = Math.max(1, n / 6);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd").withZone(ZoneId.systemDefault());
        double effectiveN = Math.max(n, 50.0);
        double totalW = cW / effectiveN;
        double startX = cX + cW - (n * totalW);

        for (int i = 0; i < n; i += step) {
            double x = startX + (i + 0.5) * totalW;
            String label = fmt.format(Instant.ofEpochSecond(candles.get(i).timestamp()));
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
            if (p < 1)
                return String.format(Locale.US, "%.5f", p);
            if (p < 100)
                return String.format(Locale.US, "%.4f", p);
            return String.format(Locale.US, "%.2f", p);
        } catch (Exception e) {
            return String.valueOf(p);
        }
    }
}
