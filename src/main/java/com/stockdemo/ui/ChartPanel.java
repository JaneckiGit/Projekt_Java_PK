package com.stockdemo.ui;

import com.stockdemo.model.Candle;
import com.stockdemo.model.DrawingObject;
import com.stockdemo.model.DrawingTool;
import com.stockdemo.model.Instrument;
import com.stockdemo.model.Position;
import com.stockdemo.service.MarketDataService;
import javafx.geometry.Insets;
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
import java.util.Optional;
import java.util.function.Consumer;

public class ChartPanel extends BorderPane {

    public enum ChartState {
        IDLE, SELECTING_SL, SELECTING_TP
    }

    // ── Viewport (pan & zoom) ───────────────────────────────
    private int viewCandleCount = 0;   // 0 = auto (show all)
    private double viewOffset = 0.0;   // how many candles are hidden on the right
    private boolean panning = false;
    private double panStartX = -1;
    private double panStartY = -1;
    private double panStartOffset = 0.0;
    private double panStartPriceOffset = 0.0;
    private double priceOffset = 0.0;

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

    // Kolory linii MA
    private static final Color MA20_COLOR = Color.YELLOW;
    private static final Color MA50_COLOR = Color.web("#4da6ff");

    // Kolor rysowania
    private static final Color DRAWING_COLOR = Color.web("#ffffffcc"); // kolor biały, przezroczystość 0.8

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

    //Podgląd zlecenia oczekującego
    private double pendingSlPrice = 0;
    private double pendingTpPrice = 0;
    private double pendingEntryPrice = 0; 
    private boolean showPendingPreview = false;
    private Runnable onPendingSlTpChanged;

    //Otwarte pozycje obecnego instrumentu
    private List<Position> openPositions = new ArrayList<>();
    
    //Stan przeciągania
    private boolean draggingPendingSL = false;
    private boolean draggingPendingTP = false;
    private Position draggedPosSL = null;
    private Position draggedPosTP = null;

    private double crossX = -1, crossY = -1;
    private double minPrice, maxPrice;

    // === Przełączniki MA ===
    private boolean showMA20 = false;
    private boolean showMA50 = false;

    // === Przełącznik RSI ===
    private boolean showRSI = false;

    // === Narzędzia rysowania ===
    private DrawingTool activeTool = DrawingTool.NONE;
    private final List<DrawingObject> drawings = new ArrayList<>();
    private int drawStartCandleIdx = -1;
    private double drawStartPrice = -1;

    private final Canvas canvas = new Canvas();
    private final Label symbolLabel = new Label("Select instrument");
    private final Label priceLabel = new Label("");
    private final Label changeLabel = new Label("");
    private final Label loadingLbl = new Label("Loadingâ€¦");

    // Drawing toolbar buttons â€” kept as field for active state styling
    private final List<Button> drawingBtns = new ArrayList<>();

    // Przycisk MA50 do włączania/wyłączania w zależności od ilości wczytanych świec
    private ToggleButton btnMA50;

    public ChartPanel(MarketDataService marketData) {
        this.marketData = marketData;
        this.setId("chartPanel");
        
        //upewnienie sie ze panem moze otrzymac event na exc
        this.setFocusTraversable(true);
        this.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                // Anuluj rysowanie, które jest w toku
                if (drawStartCandleIdx >= 0) {
                    drawStartCandleIdx = -1;
                    drawStartPrice = -1;
                    redraw();
                    return;
                }
                // Anuluj tryb wybierania SL/TP
                if (state != ChartState.IDLE) {
                    setState(ChartState.IDLE, null);
                }
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

        // === Przyciski przełączania MA ===
        ToggleButton btnMA20 = new ToggleButton("MA20");
        btnMA20.getStyleClass().add("ma-toggle");
        btnMA20.setStyle("-fx-text-fill: #ffff00;");
        btnMA20.selectedProperty().addListener((o, old, sel) -> {
            showMA20 = sel;
            btnMA20.getStyleClass().removeAll("ma-toggle-active");
            if (sel) btnMA20.getStyleClass().add("ma-toggle-active");
            redraw();
        });

        btnMA50 = new ToggleButton("MA50");
        btnMA50.getStyleClass().add("ma-toggle");
        btnMA50.setStyle("-fx-text-fill: #4da6ff;");
        btnMA50.selectedProperty().addListener((o, old, sel) -> {
            showMA50 = sel;
            btnMA50.getStyleClass().removeAll("ma-toggle-active");
            if (sel) btnMA50.getStyleClass().add("ma-toggle-active");
            redraw();
        });

        // === Przycisk przełączania RSI ===
        ToggleButton btnRSI = new ToggleButton("RSI");
        btnRSI.getStyleClass().add("rsi-toggle");
        btnRSI.selectedProperty().addListener((o, old, sel) -> {
            showRSI = sel;
            btnRSI.getStyleClass().removeAll("rsi-toggle-active");
            if (sel) btnRSI.getStyleClass().add("rsi-toggle-active");
            redraw();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(12, symbolLabel, priceLabel, changeLabel, spacer, ranges, new Separator(),
                new HBox(4, btnLine, btnCandle), new Separator(),
                new HBox(4, btnMA20, btnMA50), new Separator(),
                btnRSI);
        toolbar.setId("chartToolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // === Pasek narzędzi rysowania (lewy VBox) ===
        VBox drawingToolbar = buildDrawingToolbar();

        StackPane canvasPane = new StackPane(canvas, loadingLbl);
        canvasPane.setMinWidth(0);
        canvasPane.setMinHeight(0);
        loadingLbl.getStyleClass().add("loading-label");
        loadingLbl.setVisible(false);

        canvas.widthProperty().bind(canvasPane.widthProperty());
        canvas.heightProperty().bind(canvasPane.heightProperty());
        canvas.widthProperty().addListener(e -> redraw());
        canvas.heightProperty().addListener(e -> redraw());

        // ── Zoom (scroll wheel) ────────────────────────────────
        canvas.setOnScroll(e -> {
            if (candles.isEmpty()) return;
            int n = candles.size();
            int current = viewCandleCount <= 0 ? n : viewCandleCount;
            int delta = (int) Math.max(1, current * 0.1);
            if (e.getDeltaY() > 0) {
                // Zoom in – show fewer candles
                current = Math.max(10, current - delta);
            } else {
                // Zoom out – show more candles
                current = Math.min(n, current + delta);
            }
            viewCandleCount = current;
            // Clamp offset so we don't scroll past the data
            double maxOff = Math.max(0.0, n - viewCandleCount);
            viewOffset = Math.max(0.0, Math.min(viewOffset, maxOff));
            redraw();
        });

        canvas.setOnMouseMoved(e -> {
            crossX = e.getX();
            crossY = e.getY();
            redraw();
        });
        canvas.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                priceOffset = 0.0;
                redraw();
            }
        });
        canvas.setOnMouseExited(e -> {
            crossX = -1;
            crossY = -1;
            redraw();
        });
        canvas.setOnMousePressed(e -> {
            this.requestFocus(); // Dla klawisza ESC

            // Middle button always pans
            if (e.isMiddleButtonDown()) {
                panning = true;
                panStartX = e.getX();
                panStartOffset = viewOffset;
                canvas.setCursor(Cursor.CLOSED_HAND);
                return;
            }

            // --- Narzędzia rysowania (tylko w trybie IDLE, gdy wybrane jest jakieś narzędzie) ---
            if (state == ChartState.IDLE && activeTool != DrawingTool.NONE) {
                handleDrawingClick(e.getX(), e.getY());
                return;
            }
            
            if (state != ChartState.IDLE) {
                double price = yToPrice(e.getY());
                if (onPriceSelected != null) {
                    onPriceSelected.accept(price);
                }
                setState(ChartState.IDLE, null);
                return;
            }

            //Sprawdzenie trafień (czy kliknięto na istniejący element)
            double my = e.getY();
            
            //sprawdz otwarte pozycje
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
            
            //sprawdz pozycje pending
            if (pendingSlPrice > 0 && Math.abs(my - priceToY(pendingSlPrice)) < DRAG_HIT) {
                draggingPendingSL = true;
                return;
            }
            if (pendingTpPrice > 0 && Math.abs(my - priceToY(pendingTpPrice)) < DRAG_HIT) {
                draggingPendingTP = true;
                return;
            }

            // Left-click on background starts panning (when cursor tool is active)
            if (activeTool == DrawingTool.NONE && state == ChartState.IDLE) {
                panning = true;
                panStartX = e.getX();
                panStartY = e.getY();
                panStartOffset = viewOffset;
                panStartPriceOffset = priceOffset;
                canvas.setCursor(Cursor.CLOSED_HAND);
            }
        });
        canvas.setOnMouseDragged(e -> {
            crossX = e.getX();
            crossY = e.getY();

            if (panning) {
                double cW = canvas.getWidth() - PAD_LEFT - PAD_RIGHT;
                double cH = getChartAreaH(canvas.getHeight());
                int visCount = getVisibleCandleCount();
                
                double totalW = cW / getEffectiveN();
                double candleDelta = (e.getX() - panStartX) / totalW;
                double maxOff = Math.max(0.0, candles.size() - visCount);
                viewOffset = Math.max(0.0, Math.min(maxOff, panStartOffset + candleDelta));

                double currentPriceSpan = maxPrice - minPrice;
                double priceDelta = (e.getY() - panStartY) / cH * currentPriceSpan;
                double targetOffset = panStartPriceOffset + priceDelta;
                
                // Limit the vertical offset to 30% of the visible price span
                // so the chart never completely disappears off screen
                double limit = currentPriceSpan * 0.3;
                priceOffset = Math.max(-limit, Math.min(limit, targetOffset));

                redraw();
                return;
            }

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
            if (panning) {
                panning = false;
                if (activeTool != DrawingTool.NONE) {
                    canvas.setCursor(Cursor.CROSSHAIR);
                } else if (state != ChartState.IDLE) {
                    canvas.setCursor(Cursor.CROSSHAIR);
                } else {
                    canvas.setCursor(Cursor.DEFAULT);
                }
            }
            draggingPendingSL = false;
            draggingPendingTP = false;
            draggedPosSL = null;
            draggedPosTP = null;
        });

        this.setMinWidth(0);
        this.setMinHeight(0);
        this.setTop(toolbar);
        this.setLeft(drawingToolbar);
        this.setCenter(canvasPane);
    }

    // ===================== Pasek rysowania =====================

    private VBox buildDrawingToolbar() {
        VBox toolbar = new VBox(4);
        toolbar.setId("drawingToolbar");
        toolbar.setAlignment(Pos.TOP_CENTER);
        toolbar.setPadding(new Insets(8, 4, 8, 4));

        String[][] tools = {
            {"↖", "NONE", "Cursor (default)"},
            {"╱", "TREND_LINE", "Trend Line"},
            {"—", "HORIZONTAL_LINE", "Horizontal Line"},
            {"⬜", "RECTANGLE", "Rectangle"},
            {"T", "TEXT", "Text Label"},
        };

        for (String[] t : tools) {
            Button btn = new Button(t[0]);
            btn.getStyleClass().add("drawing-tool-btn");
            btn.setTooltip(new Tooltip(t[2]));
            if (t[1].equals("NONE")) btn.getStyleClass().add("drawing-tool-btn-active");
            DrawingTool tool = DrawingTool.valueOf(t[1]);
            btn.setOnAction(e -> selectDrawingTool(tool));
            drawingBtns.add(btn);
            toolbar.getChildren().add(btn);
        }

        // Linia oddzielająca
        Region sep = new Region();
        sep.setMinHeight(8);
        toolbar.getChildren().add(sep);

        // Przycisk cofnięcia ostatniego obiektu
        Button btnUndo = new Button("↺");
        btnUndo.getStyleClass().add("drawing-tool-btn");
        btnUndo.setTooltip(new Tooltip("Remove last object"));
        btnUndo.setStyle("-fx-text-fill: #f85149;");
        btnUndo.setOnAction(e -> {
            if (!drawings.isEmpty()) {
                drawings.remove(drawings.size() - 1);
                redraw();
            }
        });
        toolbar.getChildren().add(btnUndo);

        // Przycisk czyszczenia wszystkich rysunków
        Button btnClearAll = new Button("✕");
        btnClearAll.getStyleClass().add("drawing-tool-btn");
        btnClearAll.setTooltip(new Tooltip("Clear all drawings"));
        btnClearAll.setStyle("-fx-text-fill: #f85149;");
        btnClearAll.setOnAction(e -> {
            if (!drawings.isEmpty()) {
                drawings.clear();
                redraw();
            }
        });
        toolbar.getChildren().add(btnClearAll);

        return toolbar;
    }

    private void selectDrawingTool(DrawingTool tool) {
        this.activeTool = tool;
        this.drawStartCandleIdx = -1;
        this.drawStartPrice = -1;

        // Aktualizacja stylów przycisków
        for (int i = 0; i < drawingBtns.size(); i++) {
            drawingBtns.get(i).getStyleClass().removeAll("drawing-tool-btn-active");
        }
        // Zmapuj narzędzie na indeks przycisku
        int idx = tool.ordinal(); // NONE=0, TREND_LINE=1, HORIZONTAL_LINE=2, RECTANGLE=3, TEXT=4
        if (idx >= 0 && idx < drawingBtns.size()) {
            drawingBtns.get(idx).getStyleClass().add("drawing-tool-btn-active");
        }

        if (tool != DrawingTool.NONE) {
            canvas.setCursor(Cursor.CROSSHAIR);
        } else {
            canvas.setCursor(Cursor.DEFAULT);
        }
    }

    private void handleDrawingClick(double mx, double my) {
        if (candles.isEmpty()) return;

        double W = canvas.getWidth();
        double H = canvas.getHeight();
        double cX = PAD_LEFT;
        double cH = getChartAreaH(H);
        double cW = W - PAD_LEFT - PAD_RIGHT;

        // Obsługuj kliknięcia tylko wewnątrz obszaru wykresu
        if (my < PAD_TOP || my > PAD_TOP + cH) return;

        int candleIdx = xToCandleIndex(mx, cX, cW);
        double price = yToPriceInArea(my, PAD_TOP, cH);

        switch (activeTool) {
            case HORIZONTAL_LINE -> {
                drawings.add(new DrawingObject.HorizontalLine(price));
                redraw();
            }
            case TEXT -> {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Text");
                dialog.setHeaderText("Enter label text:");
                dialog.setContentText("Text:");
                Optional<String> result = dialog.showAndWait();
                result.ifPresent(text -> {
                    if (!text.isBlank()) {
                        drawings.add(new DrawingObject.TextLabel(candleIdx, price, text));
                        redraw();
                    }
                });
            }
            case TREND_LINE, RECTANGLE -> {
                if (drawStartCandleIdx < 0) {
                    // Pierwsze kliknięcie
                    drawStartCandleIdx = candleIdx;
                    drawStartPrice = price;
                } else {
                    // Second click â€” save object
                    if (activeTool == DrawingTool.TREND_LINE) {
                        drawings.add(new DrawingObject.TrendLine(drawStartCandleIdx, drawStartPrice, candleIdx, price));
                    } else {
                        drawings.add(new DrawingObject.Rectangle(drawStartCandleIdx, drawStartPrice, candleIdx, price));
                    }
                    drawStartCandleIdx = -1;
                    drawStartPrice = -1;
                    redraw();
                }
            }
            default -> {}
        }
    }

    // ===================== Publiczne API =====================
    
    public void setState(ChartState newState, Consumer<Double> callback) {
        this.state = newState;
        this.onPriceSelected = callback;
        if (state != ChartState.IDLE) {
            canvas.setCursor(Cursor.CROSSHAIR);
        } else if (activeTool == DrawingTool.NONE) {
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

        // ── Aktualizacja ostatniej świecy w czasie rzeczywistym ──────────────
        // Na podstawie aktualnej ceny z Instrument aktualizujemy close, high i low
        // ostatniej świecy bez dodatkowych zapytań HTTP. Dzięki temu wykres
        // odzwierciedla bieżący ruch ceny między kolejnymi pobraniami świec z API.
        if (!candles.isEmpty()) {
            double newPrice = p;
            Candle last = candles.get(candles.size() - 1);
            Candle updated = new Candle(
                    last.timestamp(),
                    last.open(),
                    Math.max(last.high(), newPrice),
                    Math.min(last.low(), newPrice),
                    newPrice,
                    last.volume()
            );
            candles.set(candles.size() - 1, updated);
            redraw();
        } else if (showPendingPreview && currentInstrument != null) {
            // Jeśli brak świec, odświeżamy tylko podgląd oczekującego zlecenia
            redraw();
        }
    }

    // ===================== Ładowanie wykresu =====================

    private void loadChart() {
        if (currentInstrument == null)
            return;
        drawings.clear();
        drawStartCandleIdx = -1;
        drawStartPrice = -1;
        // Reset viewport on new instrument/range
        viewCandleCount = 0;
        viewOffset = 0.0;
        priceOffset = 0.0;
        loadingLbl.setVisible(true);
        marketData.loadCandles(currentInstrument, activeRange, data -> {
            this.candles = data;
            loadingLbl.setVisible(false);
            updateMA50State();
            refreshPrice(currentInstrument);
            redraw();
        });
    }

    private void updateMA50State() {
        boolean enough = candles.size() >= 50;
        btnMA50.setDisable(!enough);
        if (!enough) {
            btnMA50.setSelected(false);
            showMA50 = false;
            btnMA50.setStyle("-fx-text-fill: #484f58;");
            btnMA50.setTooltip(new Tooltip("Not enough data for this range"));
        } else {
            btnMA50.setStyle("-fx-text-fill: #4da6ff;");
            btnMA50.setTooltip(null);
        }
    }

    // ===================== Metody pomocnicze do współrzędnych =====================

    /** Zwraca liczbę widocznych świec z uwzględnieniem viewCandleCount */
    private int getVisibleCandleCount() {
        if (viewCandleCount <= 0 || viewCandleCount >= candles.size()) return candles.size();
        return viewCandleCount;
    }

    /** Indeks pierwszej widocznej świecy */
    private int getFirstVisibleIndex() {
        int n = candles.size();
        int vis = getVisibleCandleCount();
        return Math.max(0, (int) Math.floor(n - vis - viewOffset));
    }

    /** Indeks za ostatnią widoczną świecą (exclusive) */
    private int getLastVisibleIndex() {
        int first = getFirstVisibleIndex();
        return Math.min(candles.size(), first + getVisibleCandleCount() + 2);
    }

    /** Zwraca wysokość obszaru wykresu (bez uwzględnienia panelu RSI) */
    private double getChartAreaH(double totalH) {
        double available = totalH - PAD_TOP - PAD_BOT;
        return showRSI ? available * 0.70 : available;
    }

    /** Candle layout parameters — respects viewport */
    private double getEffectiveN() {
        if (viewCandleCount > 0 && viewCandleCount < candles.size()) {
            return Math.max(getVisibleCandleCount(), 1.0);
        }
        return Math.max(candles.size(), 50.0);
    }

    private double getTotalW(double cW) {
        return cW / getEffectiveN();
    }

    /** Convert candle index (within visible range) to pixel X */
    private double candleIndexToX(int idx, double cX, double cW) {
        double totalW = getTotalW(cW);
        int n = candles.size();
        return cX + cW - (n - 1 - idx - viewOffset + 0.5) * totalW;
    }

    /** Convert pixel X to global candle index */
    private int xToCandleIndex(double x, double cX, double cW) {
        double totalW = getTotalW(cW);
        int n = candles.size();
        double rawIdx = n - 0.5 - viewOffset - (cX + cW - x) / totalW;
        int idx = (int) Math.round(rawIdx);
        return Math.max(0, Math.min(n - 1, idx));
    }

    private double priceToYInArea(double price, double areaY, double areaH) {
        double ratio = (maxPrice - price) / (maxPrice - minPrice);
        return areaY + ratio * areaH;
    }

    private double yToPriceInArea(double y, double areaY, double areaH) {
        double ratio = (y - areaY) / areaH;
        return maxPrice - ratio * (maxPrice - minPrice);
    }

    private double priceToY(double price) {
        double cH = getChartAreaH(canvas.getHeight());
        return priceToYInArea(price, PAD_TOP, cH);
    }

    private double yToPrice(double y) {
        double cH = getChartAreaH(canvas.getHeight());
        return yToPriceInArea(y, PAD_TOP, cH);
    }

    // ===================== Główne odświeżanie (Redraw) =====================

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

        // Compute min/max price only for visible candle range
        int firstVis = getFirstVisibleIndex();
        int lastVis = getLastVisibleIndex();
        minPrice = Double.MAX_VALUE;
        maxPrice = Double.MIN_VALUE;
        for (int i = firstVis; i < lastVis; i++) {
            Candle c = candles.get(i);
            if (c.low() < minPrice) minPrice = c.low();
            if (c.high() > maxPrice) maxPrice = c.high();
        }
        if (minPrice == Double.MAX_VALUE) { minPrice = 0; maxPrice = 1; }
        double baseSpan = maxPrice - minPrice;
        if (baseSpan == 0) baseSpan = 1;
        minPrice -= baseSpan * 0.05;
        maxPrice += baseSpan * 0.05;

        // Aplikuj przesunięcie wertykalne
        minPrice += priceOffset;
        maxPrice += priceOffset;
        double priceSpan = maxPrice - minPrice;

        double cX = PAD_LEFT;
        double cY = PAD_TOP;
        double cW = W - PAD_LEFT - PAD_RIGHT;
        double cH = getChartAreaH(H);

        gc.save();
        gc.beginPath();
        gc.rect(cX, cY, cW, cH);
        gc.clip();

        drawGrid(gc, cX, cY, cW, cH, priceSpan);
        if (showCandles)
            drawCandles(gc, cX, cY, cW, cH);
        else
            drawLine(gc, cX, cY, cW, cH);

        // Wskaźniki MA
        if (showMA20) drawMA(gc, cX, cY, cW, cH, 20, MA20_COLOR);
        if (showMA50) drawMA(gc, cX, cY, cW, cH, 50, MA50_COLOR);

        drawPositions(gc, cX, cY, cW, cH);
        drawPendingLines(gc, cX, cY, cW, cH);

        // Narysowane obiekty
        drawDrawings(gc, cX, cY, cW, cH);
        drawTempDrawing(gc, cX, cY, cW, cH);

        gc.restore();
        
        drawCrosshairAndSelection(gc, cX, cY, cW, cH, W, H);
        drawAxisLabels(gc, cX, cY, cW, cH, W, priceSpan);

        // Panel RSI
        if (showRSI) {
            double rsiY = cY + cH + 10;
            double rsiH = (H - PAD_TOP - PAD_BOT) * 0.30 - 10;
            drawRSIPanel(gc, rsiY, rsiH, cX, cW);
        }
    }

    // ===================== Siatka =====================

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

    // ===================== Świece / Linia =====================

    private void drawCandles(GraphicsContext gc, double cX, double cY, double cW, double cH) {
        int firstVis = getFirstVisibleIndex();
        int lastVis = getLastVisibleIndex();
        double totalW = getTotalW(cW);
        double bodyW = Math.max(1, totalW * 0.6);

        for (int i = firstVis; i < lastVis; i++) {
            Candle c = candles.get(i);
            double cx = candleIndexToX(i, cX, cW);
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
        int firstVis = getFirstVisibleIndex();
        int lastVis = getLastVisibleIndex();
        if (lastVis - firstVis < 2)
            return;

        gc.setStroke(LINE_COLOR);
        gc.setLineWidth(1.5);
        gc.beginPath();
        boolean first = true;
        for (int i = firstVis; i < lastVis; i++) {
            double x = candleIndexToX(i, cX, cW);
            double y = priceToYInArea(candles.get(i).close(), cY, cH);
            if (first) {
                gc.moveTo(x, y);
                first = false;
            } else {
                gc.lineTo(x, y);
            }
        }
        gc.stroke();
    }

    // ===================== Średnia ruchoma (MA) =====================

    private void drawMA(GraphicsContext gc, double cX, double cY, double cW, double cH, int period, Color color) {
        int n = candles.size();
        if (n < period) return;

        int firstVis = getFirstVisibleIndex();
        int lastVis = getLastVisibleIndex();

        gc.setStroke(color);
        gc.setLineWidth(1.5);
        gc.beginPath();

        boolean started = false;
        double sum = 0;

        // Wstępne obliczenie pierwszej sumy
        for (int i = 0; i < period - 1; i++) {
            sum += candles.get(i).close();
        }

        for (int i = period - 1; i < n; i++) {
            sum += candles.get(i).close();
            // Only draw points that fall within the visible range
            if (i >= firstVis && i < lastVis) {
                double ma = sum / period;
                double x = candleIndexToX(i, cX, cW);
                double y = priceToYInArea(ma, cY, cH);

                if (!started) {
                    gc.moveTo(x, y);
                    started = true;
                } else {
                    gc.lineTo(x, y);
                }
            } else if (i >= lastVis) {
                break;
            }

            sum -= candles.get(i - period + 1).close();
        }
        gc.stroke();
    }

    // ===================== Wskaźnik RSI =====================

    private double[] computeRSI(int period) {
        int n = candles.size();
        double[] rsi = new double[n];
        if (n <= period) return rsi;

        // Obliczenie różnic (zmian) ceny
        double[] gains = new double[n];
        double[] losses = new double[n];
        for (int i = 1; i < n; i++) {
            double change = candles.get(i).close() - candles.get(i - 1).close();
            gains[i] = Math.max(0, change);
            losses[i] = Math.max(0, -change);
        }

        // Pierwsza średnia (wg Wildera to prosta średnia ruchoma)
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            avgGain += gains[i];
            avgLoss += losses[i];
        }
        avgGain /= period;
        avgLoss /= period;

        if (avgLoss == 0) {
            rsi[period] = 100;
        } else {
            double rs = avgGain / avgLoss;
            rsi[period] = 100 - (100.0 / (1 + rs));
        }

        // Kolejne wartości (wykorzystujące wygładzanie Wildera)
        for (int i = period + 1; i < n; i++) {
            avgGain = (avgGain * (period - 1) + gains[i]) / period;
            avgLoss = (avgLoss * (period - 1) + losses[i]) / period;

            if (avgLoss == 0) {
                rsi[i] = 100;
            } else {
                double rs = avgGain / avgLoss;
                rsi[i] = 100 - (100.0 / (1 + rs));
            }
        }

        return rsi;
    }

    private void drawRSIPanel(GraphicsContext gc, double rsiY, double rsiH, double cX, double cW) {
        if (rsiH <= 0) return;

        // Tło
        gc.setFill(BG);
        gc.fillRect(cX, rsiY, cW, rsiH);

        // Linia oddzielająca panele
        gc.setStroke(GRID);
        gc.setLineWidth(1);
        gc.strokeLine(cX, rsiY, cX + cW, rsiY);

        // Strefa wykupienia (70-100): jasnoczerwona
        double y70 = rsiY + rsiH * (1 - 70.0 / 100.0);
        double y100 = rsiY;
        gc.setFill(Color.rgb(239, 83, 80, 0.05));
        gc.fillRect(cX, y100, cW, y70 - y100);

        // Strefa wyprzedania (0-30): jasnozielona
        double y30 = rsiY + rsiH * (1 - 30.0 / 100.0);
        double y0 = rsiY + rsiH;
        gc.setFill(Color.rgb(38, 166, 154, 0.05));
        gc.fillRect(cX, y30, cW, y0 - y30);

        // Linie siatki dla wskaźnika RSI
        gc.setStroke(GRID);
        gc.setLineWidth(0.5);
        for (int level : new int[]{0, 30, 50, 70, 100}) {
            double y = rsiY + rsiH * (1 - level / 100.0);
            gc.strokeLine(cX, y, cX + cW, y);
        }

        // Przerywane linie poziome na wysokości 30 i 70
        gc.setLineWidth(1.0);
        gc.setLineDashes(4, 4);

        gc.setStroke(Color.web("#3fb950")); // zielona dla 30
        gc.strokeLine(cX, y30, cX + cW, y30);

        gc.setStroke(Color.web("#ef5350")); // czerwona dla 70
        gc.strokeLine(cX, y70, cX + cW, y70);

        gc.setLineDashes();

        gc.save();
        gc.beginPath();
        gc.rect(cX, rsiY, cW, rsiH);
        gc.clip();

        // Linia wskaźnika RSI
        double[] rsi = computeRSI(14);
        int n = candles.size();
        if (n > 14) {
            int firstVis = getFirstVisibleIndex();
            int lastVis = getLastVisibleIndex();

            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.5);
            gc.beginPath();
            boolean started = false;
            for (int i = Math.max(14, firstVis); i < lastVis; i++) {
                double x = candleIndexToX(i, cX, cW);
                double y = rsiY + rsiH * (1 - rsi[i] / 100.0);
                if (!started) {
                    gc.moveTo(x, y);
                    started = true;
                } else {
                    gc.lineTo(x, y);
                }
            }
            gc.stroke();
        }
        
        gc.restore();

        // Podpis osi dla wartości RSI
        gc.setFill(AXIS_TEXT);
        gc.setFont(Font.font("Inter", 9));
        for (int level : new int[]{0, 30, 50, 70, 100}) {
            double y = rsiY + rsiH * (1 - level / 100.0);
            gc.fillText(String.valueOf(level), cX + cW + 4, y + 4);
        }

        // Etykieta "RSI(14)"
        gc.setFill(AXIS_TEXT);
        gc.setFont(Font.font("Inter", FontWeight.BOLD, 10));
        gc.fillText("RSI(14)", cX + 4, rsiY + 14);
    }

    // ===================== Rysowanie elementów =====================

    private void drawDrawings(GraphicsContext gc, double cX, double cY, double cW, double cH) {
        gc.setStroke(DRAWING_COLOR);
        gc.setFill(DRAWING_COLOR);
        gc.setLineWidth(1.5);
        gc.setLineDashes();

        for (DrawingObject obj : drawings) {
            if (obj instanceof DrawingObject.TrendLine tl) {
                double x1 = candleIndexToX(tl.candleIdx1(), cX, cW);
                double y1 = priceToYInArea(tl.price1(), cY, cH);
                double x2 = candleIndexToX(tl.candleIdx2(), cX, cW);
                double y2 = priceToYInArea(tl.price2(), cY, cH);
                gc.setStroke(DRAWING_COLOR);
                gc.setLineWidth(1.5);
                gc.strokeLine(x1, y1, x2, y2);
            } else if (obj instanceof DrawingObject.HorizontalLine hl) {
                double y = priceToYInArea(hl.price(), cY, cH);
                if (y >= cY && y <= cY + cH) {
                    gc.setStroke(DRAWING_COLOR);
                    gc.setLineWidth(1.5);
                    gc.strokeLine(cX, y, cX + cW, y);
                    gc.setFont(Font.font("Inter", 10));
                    gc.fillText(formatPrice(hl.price()), cX + cW + 4, y + 4);
                }
            } else if (obj instanceof DrawingObject.Rectangle rect) {
                double x1 = candleIndexToX(rect.candleIdx1(), cX, cW);
                double y1 = priceToYInArea(rect.price1(), cY, cH);
                double x2 = candleIndexToX(rect.candleIdx2(), cX, cW);
                double y2 = priceToYInArea(rect.price2(), cY, cH);
                double rx = Math.min(x1, x2);
                double ry = Math.min(y1, y2);
                double rw = Math.abs(x2 - x1);
                double rh = Math.abs(y2 - y1);
                gc.setStroke(DRAWING_COLOR);
                gc.setLineWidth(2.5);
                gc.strokeRect(rx, ry, rw, rh);
                gc.setFill(Color.rgb(255, 255, 255, 0.10));
                gc.fillRect(rx, ry, rw, rh);
                gc.setFill(DRAWING_COLOR); // przywrócenie
            } else if (obj instanceof DrawingObject.TextLabel tl) {
                double x = candleIndexToX(tl.candleIdx(), cX, cW);
                double y = priceToYInArea(tl.price(), cY, cH);
                gc.setFill(DRAWING_COLOR);
                gc.setFont(Font.font("Inter", FontWeight.BOLD, 12));
                gc.fillText(tl.text(), x, y);
            }
        }
    }

    private void drawTempDrawing(GraphicsContext gc, double cX, double cY, double cW, double cH) {
        if (drawStartCandleIdx < 0 || crossX < 0 || crossY < 0) return;
        if (activeTool != DrawingTool.TREND_LINE && activeTool != DrawingTool.RECTANGLE) return;

        double x1 = candleIndexToX(drawStartCandleIdx, cX, cW);
        double y1 = priceToYInArea(drawStartPrice, cY, cH);
        double clampedY = Math.max(cY, Math.min(cY + cH, crossY));

        gc.setStroke(DRAWING_COLOR);
        gc.setLineWidth(1.5);
        gc.setLineDashes(4, 4);

        if (activeTool == DrawingTool.TREND_LINE) {
            gc.strokeLine(x1, y1, crossX, clampedY);
        } else { // Obsługa rysowania prostokąta
            double rx = Math.min(x1, crossX);
            double ry = Math.min(y1, clampedY);
            double rw = Math.abs(crossX - x1);
            double rh = Math.abs(clampedY - y1);
            gc.strokeRect(rx, ry, rw, rh);
        }
        gc.setLineDashes();
    }

    // ===================== Pozycje =====================
    
    private void drawPositions(GraphicsContext gc, double cX, double cY, double cW, double cH) {
        if (currentInstrument == null) return;
        
        for (Position p : openPositions) {
            if (!p.getInstrument().getSymbol().equals(currentInstrument.getSymbol())) continue;
            
            // Rysowanie poziomej linii ceny wejścia (Entry)
            double entryY = priceToYInArea(p.getEntryPrice(), cY, cH);
            if (entryY >= cY && entryY <= cY + cH) {
                gc.setStroke(ENTRY_COLOR);
                gc.setLineWidth(1.5);
                gc.strokeLine(cX, entryY, cX + cW, entryY);
                gc.setFill(ENTRY_COLOR);
                gc.setFont(Font.font("Inter", FontWeight.BOLD, 10));
                gc.fillText("Entry " + formatPrice(p.getEntryPrice()), cX + 5, entryY - 4);
            }
            
            // Rysowanie poziomej linii Stop Loss (SL)
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
            
            // Rysowanie poziomej linii Take Profit (TP)
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

    // ===================== Linie oczekujące =====================

    private void drawPendingLines(GraphicsContext gc, double cX, double cY, double cW, double cH) {
        if (showPendingPreview && pendingEntryPrice > 0) {
            double entryY = priceToYInArea(pendingEntryPrice, cY, cH);
            if (entryY >= cY && entryY <= cY + cH) {
                gc.setStroke(ENTRY_COLOR.deriveColor(1, 1, 1, 0.6)); // Półprzezroczysty niebieski
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

    // ===================== Kursor celownika (Crosshair) =====================

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

        double price = yToPriceInArea(crossY, cY, cH);
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

    // ===================== Etykiety osi =====================

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
        int firstVis = getFirstVisibleIndex();
        int lastVis = getLastVisibleIndex();
        int vis = lastVis - firstVis;
        int step = Math.max(1, vis / 6);
        // Dla wykresu 1D (intraday) pokazujemy godziny zamiast dat
        String pattern = "1D".equals(activeRange) ? "HH:mm" : "MM/dd";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());

        // Gdy widoczny jest RSI, rysuj etykiety z datą poniżej panelu RSI
        double dateLabelsY = cY + cH + 20;
        if (showRSI) {
            double rsiH = (canvas.getHeight() - PAD_TOP - PAD_BOT) * 0.30 - 10;
            dateLabelsY = cY + cH + 10 + rsiH + 16;
        }

        for (int i = firstVis; i < lastVis; i += step) {
            double x = candleIndexToX(i, cX, cW);
            String label = fmt.format(Instant.ofEpochSecond(candles.get(i).timestamp()));
            gc.fillText(label, x - 15, dateLabelsY);
        }
    }

    // ===================== Formatowanie =====================

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

