package com.stockdemo.ui;

import com.stockdemo.model.Instrument;
import com.stockdemo.model.Position;
import com.stockdemo.model.ClosedPosition;
import com.stockdemo.service.PortfolioService;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import java.time.format.DateTimeFormatter;

public class PortfolioPanel extends VBox {

    private final PortfolioService portfolio;
    private final ChartPanel chartPanel;

    private Instrument selectedInstrument;

    //Etykiety z saldem konta
    private final Label balanceLabel = new Label("$100,000.00");
    private final Label equityLabel = new Label("Equity: $100,000.00");
    private final Label pnlLabel = new Label("P&L: $0.00");

    //Pola tekstowe w formularzu
    private final TextField qtyField = new TextField("1");
    private final TextField slField = new TextField();
    private final TextField tpField = new TextField();

    //Listy
    private final ListView<Position> positionsList = new ListView<>();
    private final ListView<ClosedPosition> historyList = new ListView<>();

    private final Label availableFundsLbl = new Label("$0.00");
    private final Label marginValueLbl = new Label("≈ $0.00");
    private final Label contractValueLbl = new Label("≈ $0.00");

    // Etykiety statystyk
    private final Label winRateLbl = new Label("—");
    private final Label totalPnlLbl = new Label("—");
    private final Label bestTradeLbl = new Label("—");
    private final Label worstTradeLbl = new Label("—");
    private final Label avgPnlLbl = new Label("—");
    private final Label buyPriceLbl = new Label("0.00");

    // Wewnętrzny kontener na zawartość
    private final VBox content = new VBox(0);

    private final Button menuBtn = new Button("\u2630");
    private Runnable onMenuRequested;

    public PortfolioPanel(PortfolioService portfolio, ChartPanel chartPanel) {
        this.portfolio = portfolio;
        this.chartPanel = chartPanel;
        this.setId("portfolioPanel");
        this.setSpacing(0);

        buildAccountSection();
        buildUnifiedTradeForm();
        buildPositionsAndHistorySection();
        
        //Przekaż listę otwartych pozycji do ChartPanel
        chartPanel.setOpenPositions(portfolio.openPositions);

        chartPanel.setOnPendingSlTpChanged(() -> {
            slField.setText(formatPrice(chartPanel.getPendingSlPrice()));
            tpField.setText(formatPrice(chartPanel.getPendingTpPrice()));
        });

        //Odświeżaj listę pozycji automatycznie
        portfolio.openPositions.addListener((javafx.collections.ListChangeListener<Position>) c -> refresh());
        portfolio.closedPositions.addListener((javafx.collections.ListChangeListener<ClosedPosition>) c -> refresh());
        refresh();

        // Owinięcie zawartości w ScrollPane żeby nie ucinało przy zmniejszaniu okna.
        // setFitToHeight(true) sprawia, że content rozciąga się do pełnej wysokości,
        // dzięki czemu sekcja z pozycjami (z VGrow ALWAYS) wypełnia puste miejsce na dole.
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        this.getChildren().add(scrollPane);
    }

    public void setInstrument(Instrument instrument) {
        this.selectedInstrument = instrument;

        slField.setText("0.00");
        tpField.setText("0.00");
        chartPanel.setPendingSlPrice(0.0);
        chartPanel.setPendingTpPrice(0.0);

        //Resetuje wolumen
        qtyField.setText("0.01");
        updateOrderValue();
    }

    public void refresh() {
        balanceLabel.setText("$" + String.format("%,.2f", portfolio.getBalance()));
        equityLabel.setText("Equity: $" + String.format("%,.2f", portfolio.getEquity()));

        //Obliczanie zysków w otwartych pozycjach
        double pnl = portfolio.openPositions.stream().mapToDouble(Position::getPnl).sum();
        String sign = pnl >= 0 ? "+" : "";
        pnlLabel.setText("P&L: " + sign + "$" + String.format("%,.2f", pnl));
        pnlLabel.setStyle("-fx-text-fill: " + (pnl >= 0 ? "#3fb950" : "#f85149") + ";");
        availableFundsLbl.setText("$" + String.format("%,.2f", portfolio.getBalance()));
        
        positionsList.refresh();
        historyList.refresh();
        refreshStats();
        
        updateOrderValue(); //to refresh pending preview on chart
    }

    /** Zwraca przycisk menu (potrzebne do pozycjonowania mini-menu). */
    public Button getMenuButton() {
        return menuBtn;
    }

    /** Ustawia handler wywoływany po kliknięciu przycisku menu (☰). */
    public void setOnMenuRequested(Runnable handler) {
        this.onMenuRequested = handler;
    }

    private void buildAccountSection() {
        Label sectionTitle = new Label("ACCOUNT");
        sectionTitle.getStyleClass().add("section-title");

        // Przycisk menu (☰) obok tytułu ACCOUNT
        menuBtn.getStyleClass().add("chart-type-btn");
        menuBtn.setTooltip(new javafx.scene.control.Tooltip("Menu"));
        menuBtn.setOnAction(e -> {
            if (onMenuRequested != null) onMenuRequested.run();
        });

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(8, sectionTitle, headerSpacer, menuBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        balanceLabel.getStyleClass().add("balance-value");
        equityLabel.getStyleClass().add("equity-value");
        pnlLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");

        Button resetBtn = new Button("Reset Portfolio");
        resetBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #58a6ff; -fx-cursor: hand; -fx-padding: 4 0 0 0; -fx-font-size: 12px;");
        resetBtn.setOnAction(e -> {
            com.stockdemo.service.PortfolioPersistence.reset(portfolio);
            refresh();
            showToast("Reset", "Portfolio has been reset to $100,000.00");
        });

        VBox section = new VBox(6, header, balanceLabel, equityLabel, pnlLabel, resetBtn);
        section.getStyleClass().add("portfolio-section");
        content.getChildren().add(section);
    }

    private void buildUnifiedTradeForm() {
        VBox form = new VBox(15);
        form.setPadding(new Insets(16));
        form.getStyleClass().add("portfolio-section");
        
        Label sectionTitle = new Label("NEW ORDER");
        sectionTitle.getStyleClass().add("section-title");
        form.getChildren().add(sectionTitle);

        // Volume
        HBox volBox = new HBox();
        volBox.getStyleClass().add("xtb-volume-box");
        VBox volLeft = new VBox(2);
        volLeft.getStyleClass().add("xtb-vol-left");
        volLeft.setAlignment(Pos.CENTER_LEFT);
        Label volLbl = new Label("Volume");
        volLbl.setStyle("-fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;");
        Label marLbl = new Label("Margin");
        marLbl.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 10px;");
        volLeft.getChildren().addAll(volLbl, marLbl);

        HBox volRight = new HBox();
        volRight.setAlignment(Pos.CENTER);
        HBox.setHgrow(volRight, Priority.ALWAYS);
        Button minusBtn = new Button("\u2212");
        minusBtn.getStyleClass().add("xtb-vol-btn");
        minusBtn.setOnAction(e -> adjustVolume(-0.01));

        qtyField.getStyleClass().addAll("trade-field", "xtb-vol-field");
        qtyField.setText("0.01");
        qtyField.textProperty().addListener((obs, o, n) -> updateOrderValue());

        VBox centerVal = new VBox(0, qtyField, marginValueLbl);
        centerVal.setAlignment(Pos.CENTER);
        marginValueLbl.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");

        Button plusBtn = new Button("+");
        plusBtn.getStyleClass().add("xtb-vol-btn");
        plusBtn.setOnAction(e -> adjustVolume(0.01));

        volRight.getChildren().addAll(minusBtn, centerVal, plusBtn);
        volBox.getChildren().addAll(volLeft, volRight);

        // SL & TP Row
        HBox slTpRow = new HBox(10);
        
        VBox slBox = new VBox(5);
        HBox.setHgrow(slBox, Priority.ALWAYS);
        Label slFieldLbl = new Label("Stop Loss");
        slFieldLbl.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");
        
        HBox slInputBox = new HBox(2);
        slField.getStyleClass().addAll("trade-field", "sl-field");
        slField.setPromptText("0.00");
        slField.textProperty().addListener((o, old, val) -> {
            try { chartPanel.setPendingSlPrice(Double.parseDouble(val.replace(",", "."))); } catch (Exception ignored) {}
        });
        HBox.setHgrow(slField, Priority.ALWAYS);
        Button slTargetBtn = new Button("⌖");
        slTargetBtn.getStyleClass().add("target-btn");
        slTargetBtn.setTooltip(new Tooltip("Set SL from chart"));
        slTargetBtn.setOnAction(e -> {
            chartPanel.setState(ChartPanel.ChartState.SELECTING_SL, price -> {
                slField.setText(formatPrice(price));
            });
        });
        slInputBox.getChildren().addAll(slField, slTargetBtn);
        slBox.getChildren().addAll(slFieldLbl, slInputBox);

        VBox tpBox = new VBox(5);
        HBox.setHgrow(tpBox, Priority.ALWAYS);
        Label tpFieldLbl = new Label("Take Profit");
        tpFieldLbl.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");
        
        HBox tpInputBox = new HBox(2);
        tpField.getStyleClass().addAll("trade-field", "tp-field");
        tpField.setPromptText("0.00");
        tpField.textProperty().addListener((o, old, val) -> {
            try { chartPanel.setPendingTpPrice(Double.parseDouble(val.replace(",", "."))); } catch (Exception ignored) {}
        });
        HBox.setHgrow(tpField, Priority.ALWAYS);
        Button tpTargetBtn = new Button("⌖");
        tpTargetBtn.getStyleClass().add("target-btn");
        tpTargetBtn.setTooltip(new Tooltip("Set TP from chart"));
        tpTargetBtn.setOnAction(e -> {
            chartPanel.setState(ChartPanel.ChartState.SELECTING_TP, price -> {
                tpField.setText(formatPrice(price));
            });
        });
        tpInputBox.getChildren().addAll(tpField, tpTargetBtn);
        tpBox.getChildren().addAll(tpFieldLbl, tpInputBox);

        slTpRow.getChildren().addAll(slBox, tpBox);
        
        Button updateSlTpBtn = new Button("Update Open Position SL/TP");
        updateSlTpBtn.setStyle("-fx-background-color: #21262d; -fx-text-fill: white; -fx-border-color: #30363d; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 6 12; -fx-font-weight: bold; -fx-cursor: hand;");
        updateSlTpBtn.setMaxWidth(Double.MAX_VALUE);
        updateSlTpBtn.setOnAction(e -> {
            double sl = parseDouble(slField.getText());
            double tp = parseDouble(tpField.getText());
            boolean applied = false;
            for (Position p : portfolio.openPositions) {
                if (p.getInstrument().equals(selectedInstrument)) {
                    p.setStopLoss(sl);
                    p.setTakeProfit(tp);
                    applied = true;
                }
            }
            if (applied) {
                refresh();
                showToast("Updated", "SL/TP has been updated for the open position.");
            } else {
                showToast("No Position", "You have no open position on this instrument.");
            }
        });

        // Funds and Buy Button
        HBox fundsBox = new HBox(4);
        fundsBox.setAlignment(Pos.CENTER);
        Label fLbl = new Label("Available:");
        fLbl.getStyleClass().add("xtb-funds");
        availableFundsLbl.getStyleClass().add("xtb-funds-val");
        fundsBox.getChildren().addAll(fLbl, availableFundsLbl);

        VBox buyBtn = new VBox(2);
        buyBtn.getStyleClass().add("xtb-btn-buy");
        buyBtn.setAlignment(Pos.CENTER);
        buyBtn.setPadding(new Insets(10, 0, 10, 0));
        Label buyTitle = new Label("Buy");
        buyTitle.getStyleClass().add("xtb-btn-title");
        buyPriceLbl.getStyleClass().add("xtb-btn-price");
        buyBtn.getChildren().addAll(buyTitle, buyPriceLbl);
        buyBtn.setOnMouseClicked(e -> placeOrder(true));

        form.getChildren().addAll(volBox, slTpRow, updateSlTpBtn, fundsBox, buyBtn);
        content.getChildren().add(form);
    }

    private void buildPositionsAndHistorySection() {
        VBox section = new VBox(0);
        section.getStyleClass().add("portfolio-section");
        VBox.setVgrow(section, Priority.ALWAYS);

        HBox tabs = new HBox(10);
        tabs.setPadding(new Insets(14, 16, 6, 16));
        Label openTab = new Label("OPEN POSITIONS");
        Label historyTab = new Label("HISTORY");
        openTab.getStyleClass().addAll("section-title", "tab-active");
        historyTab.getStyleClass().addAll("section-title", "tab-inactive");
        
        tabs.getChildren().addAll(openTab, historyTab);

        positionsList.setItems(portfolio.openPositions);
        positionsList.getStyleClass().add("positions-list");
        positionsList.setCellFactory(lv -> new PositionCell());
        positionsList.setMinHeight(120);
        positionsList.setPrefHeight(200);
        VBox.setVgrow(positionsList, Priority.ALWAYS);
        
        historyList.setItems(portfolio.closedPositions);
        historyList.getStyleClass().add("positions-list");
        historyList.setCellFactory(lv -> new ClosedPositionCell());
        historyList.setMinHeight(120);
        historyList.setPrefHeight(200);
        VBox.setVgrow(historyList, Priority.ALWAYS);
        
        historyList.setVisible(false);
        historyList.setManaged(false);
        
        openTab.setOnMouseClicked(e -> {
            openTab.getStyleClass().remove("tab-inactive");
            openTab.getStyleClass().add("tab-active");
            historyTab.getStyleClass().remove("tab-active");
            historyTab.getStyleClass().add("tab-inactive");
            positionsList.setVisible(true);
            positionsList.setManaged(true);
            historyList.setVisible(false);
            historyList.setManaged(false);
        });
        
        historyTab.setOnMouseClicked(e -> {
            historyTab.getStyleClass().remove("tab-inactive");
            historyTab.getStyleClass().add("tab-active");
            openTab.getStyleClass().remove("tab-active");
            openTab.getStyleClass().add("tab-inactive");
            positionsList.setVisible(false);
            positionsList.setManaged(false);
            historyList.setVisible(true);
            historyList.setManaged(true);
        });

        section.getChildren().addAll(tabs, positionsList, historyList);
        content.getChildren().add(section);
    }

    // Sekcja STATISTICS została przeniesiona do osobnego modala (MainLayout#buildStatisticsContent).
    // Etykiety pozostają jako pola, refreshStats() nadal je aktualizuje.

    // === Gettery do etykiet statystyk (używane przez modal Statistics) ===
    public Label getLabelWinRate()    { return winRateLbl; }
    public Label getLabelTotalPnl()   { return totalPnlLbl; }
    public Label getLabelBestTrade()  { return bestTradeLbl; }
    public Label getLabelWorstTrade() { return worstTradeLbl; }
    public Label getLabelAvgPnl()     { return avgPnlLbl; }

    private void refreshStats() {
        var list = portfolio.closedPositions;
        if (list.isEmpty()) {
            winRateLbl.setText("—");
            totalPnlLbl.setText("—");
            bestTradeLbl.setText("—");
            worstTradeLbl.setText("—");
            avgPnlLbl.setText("—");
            String neutral = "-fx-text-fill: #8b949e; -fx-font-size: 12px; -fx-font-weight: bold;";
            winRateLbl.setStyle(neutral);
            totalPnlLbl.setStyle(neutral);
            bestTradeLbl.setStyle(neutral);
            worstTradeLbl.setStyle(neutral);
            avgPnlLbl.setStyle(neutral);
            return;
        }

        long wins = list.stream().filter(p -> p.realizedPnl() > 0).count();
        double winRate = (double) wins / list.size() * 100.0;
        double totalPnl = list.stream().mapToDouble(ClosedPosition::realizedPnl).sum();
        double best = list.stream().mapToDouble(ClosedPosition::realizedPnl).max().orElse(0);
        double worst = list.stream().mapToDouble(ClosedPosition::realizedPnl).min().orElse(0);
        double avg = list.stream().mapToDouble(ClosedPosition::realizedPnl).average().orElse(0);

        winRateLbl.setText(String.format("%.1f%%", winRate));
        applyPnlStyle(totalPnlLbl, totalPnl, true);
        applyPnlStyle(bestTradeLbl, best, true);
        applyPnlStyle(worstTradeLbl, worst, true);
        applyPnlStyle(avgPnlLbl, avg, true);

        // Win rate: green if >= 50%, red otherwise
        String wrColor = winRate >= 50 ? "#3fb950" : "#f85149";
        winRateLbl.setStyle("-fx-text-fill: " + wrColor + "; -fx-font-size: 12px; -fx-font-weight: bold;");
    }

    private void applyPnlStyle(Label label, double value, boolean withDollar) {
        String sign = value >= 0 ? "+" : "";
        label.setText(sign + "$" + String.format("%,.2f", value));
        String color = value >= 0 ? "#3fb950" : "#f85149";
        label.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; -fx-font-weight: bold;");
    }

    private void adjustVolume(double delta) {
        double current = parseDouble(qtyField.getText());
        double next = Math.max(0.01, current + delta);
        qtyField.setText(String.format(java.util.Locale.US, "%.2f", next));
    }

    private void updateOrderValue() {
        if (selectedInstrument == null) {
            marginValueLbl.setText("≈ $0.00");
            buyPriceLbl.setText("0.00");
            contractValueLbl.setText("≈ $0.00");
            chartPanel.hidePendingPreview();
            return;
        }

        double qty = parseDouble(qtyField.getText());
        double price = selectedInstrument.getPrice();
        double ask = selectedInstrument.getAsk();

        buyPriceLbl.setText(formatPrice(ask));

        double costUsd = qty * price;
        marginValueLbl.setText(String.format("≈ $%,.2f", costUsd));
        contractValueLbl.setText(String.format("≈ $%,.2f", costUsd));
        
        if (qty > 0) {
            chartPanel.setPendingPreview(ask, true);
        } else {
            chartPanel.hidePendingPreview();
        }
    }

    private void placeOrder(boolean isLong) {
        if (selectedInstrument == null) {
            showToast("No instrument", "Please select an instrument from the list on the left.");
            return;
        }
        double qty;
        try {
            qty = Double.parseDouble(qtyField.getText().replace(",", "."));
        } catch (NumberFormatException e) {
            showToast("Invalid quantity", "Please enter a valid volume.");
            return;
        }
        double sl = parseDouble(slField.getText());
        double tp = parseDouble(tpField.getText());

        double currentPrice = selectedInstrument.getAsk();
        if (isLong) {
            if (sl >= currentPrice) sl = 0.0;
            if (tp > 0 && tp <= currentPrice) tp = 0.0;
        } else {
            if (sl > 0 && sl <= currentPrice) sl = 0.0;
            if (tp > 0 && tp >= currentPrice) tp = 0.0;
        }

        boolean success = portfolio.openPosition(selectedInstrument, isLong, qty, sl, tp);
        if (!success) {
            showToast("Insufficient funds", "You don't have enough balance to place this order.");
            return;
        }
        
        //Reset formularza
        slField.setText("");
        tpField.setText("");
        chartPanel.setPendingSlPrice(0);
        chartPanel.setPendingTpPrice(0);
        
        refresh();
    }

    private double parseDouble(String text) {
        try {
            return Double.parseDouble(text.replace(",", "."));
        } catch (Exception e) {
            return 0;
        }
    }

    private String formatPrice(double p) {
        if (p < 1) return String.format(java.util.Locale.US, "%.5f", p);
        if (p < 100) return String.format(java.util.Locale.US, "%.4f", p);
        return String.format(java.util.Locale.US, "%.2f", p);
    }

    private void showToast(String title, String msg) {
        // Find the root StackPane of the scene
        StackPane overlay = findRootStackPane();
        if (overlay == null) return;

        // --- Toast container ---
        VBox toast = new VBox(6);
        toast.setPadding(new Insets(14, 16, 10, 16));
        toast.setMaxWidth(320);
        toast.setMinWidth(260);
        toast.setStyle(
            "-fx-background-color: #161b22;" +
            "-fx-border-color: #30363d;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 8;" +
            "-fx-background-radius: 8;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 16, 0, 0, 4);"
        );
        toast.setMouseTransparent(false);

        // --- Header: title + close button ---
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 13px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #8b949e;" +
            "-fx-font-size: 13px; -fx-padding: 0 0 0 8; -fx-cursor: hand;"
        );
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #f85149;" +
            "-fx-font-size: 13px; -fx-padding: 0 0 0 8; -fx-cursor: hand;"
        ));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #8b949e;" +
            "-fx-font-size: 13px; -fx-padding: 0 0 0 8; -fx-cursor: hand;"
        ));
        header.getChildren().addAll(titleLabel, spacer, closeBtn);

        // --- Message body ---
        Label msgLabel = new Label(msg);
        msgLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");
        msgLabel.setWrapText(true);

        // --- Progress bar (countdown indicator) ---
        double barWidth = 288;
        Rectangle progressBar = new Rectangle(barWidth, 3);
        progressBar.setArcWidth(3);
        progressBar.setArcHeight(3);
        progressBar.setFill(Color.web("#58a6ff"));
        VBox.setMargin(progressBar, new Insets(4, 0, 0, 0));

        toast.getChildren().addAll(header, msgLabel, progressBar);

        // --- Wrapper to stack toasts from bottom-right ---
        // Use the overlay's existing toast container or create one
        VBox toastContainer = findOrCreateToastContainer(overlay);
        toastContainer.getChildren().add(0, toast); // newest on bottom visually = index 0 pushes up

        // --- Auto-dismiss animation ---
        Timeline countdown = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(progressBar.widthProperty(), barWidth)),
            new KeyFrame(Duration.seconds(4), new KeyValue(progressBar.widthProperty(), 0))
        );
        countdown.setOnFinished(e -> removeToast(toast, toastContainer, overlay));
        countdown.play();

        // --- Manual close ---
        closeBtn.setOnAction(e -> {
            countdown.stop();
            removeToast(toast, toastContainer, overlay);
        });
    }

    private StackPane findRootStackPane() {
        if (getScene() == null) return null;
        Parent root = getScene().getRoot();
        if (root instanceof StackPane) return (StackPane) root;
        return null;
    }

    private VBox findOrCreateToastContainer(StackPane overlay) {
        // Look for existing toast container
        for (javafx.scene.Node child : overlay.getChildren()) {
            if (child instanceof VBox && "toast-container".equals(child.getId())) {
                return (VBox) child;
            }
        }
        // Create a new one
        VBox container = new VBox(8);
        container.setId("toast-container");
        container.setAlignment(Pos.BOTTOM_RIGHT);
        container.setPadding(new Insets(0, 24, 24, 0));
        container.setPickOnBounds(false); // allow clicks to pass through empty area
        container.setMouseTransparent(false);
        overlay.getChildren().add(container);
        StackPane.setAlignment(container, Pos.BOTTOM_RIGHT);
        return container;
    }

    private void removeToast(VBox toast, VBox container, StackPane overlay) {
        container.getChildren().remove(toast);
        if (container.getChildren().isEmpty()) {
            overlay.getChildren().remove(container);
        }
    }

    //Komórka listy aktywnych pozycji

    private class PositionCell extends ListCell<Position> {
        private final VBox root = new VBox(4);
        private final HBox top = new HBox(8);
        private final HBox bottom = new HBox(8);
        private final Label symLabel = new Label();
        private final Label dirLabel = new Label();
        private final Label pnlLabel = new Label();
        private final Label infoLabel = new Label();
        private final Button closeBtn = new Button("Close");

        PositionCell() {
            symLabel.getStyleClass().add("pos-symbol");
            pnlLabel.getStyleClass().add("pos-pnl-positive");
            closeBtn.getStyleClass().add("btn-close-pos");
            infoLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 10px;");
            infoLabel.setMaxWidth(Double.MAX_VALUE);
            infoLabel.setWrapText(true);
            dirLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700;");

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            top.getChildren().addAll(dirLabel, symLabel, sp, pnlLabel);
            top.setMinWidth(0);
            Region sp2 = new Region();
            HBox.setHgrow(sp2, Priority.ALWAYS);
            bottom.getChildren().addAll(infoLabel, sp2, closeBtn);
            bottom.setMinWidth(0);
            HBox.setHgrow(infoLabel, Priority.ALWAYS);
            root.getChildren().addAll(top, bottom);
            root.getStyleClass().add("position-cell");
            root.setPadding(new Insets(8, 10, 8, 10));
            root.setMinWidth(0);
            root.setMaxWidth(Double.MAX_VALUE);

            closeBtn.setOnAction(e -> {
                Position pos = getItem();
                if (pos != null) {
                    portfolio.closePosition(pos);
                    slField.setText("");
                    tpField.setText("");
                    chartPanel.setPendingSlPrice(0);
                    chartPanel.setPendingTpPrice(0);
                    PortfolioPanel.this.refresh();
                }
            });
        }

        @Override
        protected void updateItem(Position pos, boolean empty) {
            super.updateItem(pos, empty);
            if (empty || pos == null) {
                setGraphic(null);
                return;
            }

            symLabel.setText(pos.getInstrument().getSymbol());
            dirLabel.setText(pos.isLong() ? "▲ BUY" : "▼ SELL");
            dirLabel.setStyle("-fx-text-fill: " + (pos.isLong() ? "#3fb950" : "#f85149")
                    + "; -fx-font-size: 11px; -fx-font-weight: 700;");

            double pnl = pos.getPnl();
            String sign = pnl >= 0 ? "+" : "";
            pnlLabel.setText(sign + "$" + String.format("%.2f", pnl));
            pnlLabel.getStyleClass().removeAll("pos-pnl-positive", "pos-pnl-negative");
            pnlLabel.getStyleClass().add(pnl >= 0 ? "pos-pnl-positive" : "pos-pnl-negative");

            infoLabel.setText(String.format(java.util.Locale.US, "%.4f @ %.2f\nSL:%.2f  TP:%.2f",
                    pos.getQuantity(), pos.getEntryPrice(),
                    pos.getStopLoss(), pos.getTakeProfit()));

            setGraphic(root);
            setPadding(new Insets(4, 8, 4, 8));
        }
    }
    
    //Komórka listy zamkniętych pozycji (Historia)

    private class ClosedPositionCell extends ListCell<ClosedPosition> {
        private final VBox root = new VBox(4);
        private final HBox top = new HBox(8);
        private final HBox bottom = new HBox(8);
        private final Label symLabel = new Label();
        private final Label dirLabel = new Label();
        private final Label pnlLabel = new Label();
        private final Label infoLabel = new Label();
        private final Label dateLabel = new Label();

        ClosedPositionCell() {
            symLabel.getStyleClass().add("pos-symbol");
            pnlLabel.getStyleClass().add("pos-pnl-positive");
            infoLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 10px;");
            infoLabel.setMaxWidth(Double.MAX_VALUE);
            infoLabel.setWrapText(true);
            dateLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 10px;");
            dirLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700;");

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            top.getChildren().addAll(dirLabel, symLabel, sp, pnlLabel);
            top.setMinWidth(0);
            
            Region sp2 = new Region();
            HBox.setHgrow(sp2, Priority.ALWAYS);
            HBox.setHgrow(infoLabel, Priority.ALWAYS);
            bottom.getChildren().addAll(infoLabel, sp2, dateLabel);
            bottom.setMinWidth(0);
            
            root.getChildren().addAll(top, bottom);
            root.getStyleClass().add("position-cell");
            root.setPadding(new Insets(8, 10, 8, 10));
            root.setMinWidth(0);
            root.setMaxWidth(Double.MAX_VALUE);
        }

        @Override
        protected void updateItem(ClosedPosition pos, boolean empty) {
            super.updateItem(pos, empty);
            if (empty || pos == null) {
                setGraphic(null);
                return;
            }

            symLabel.setText(pos.instrument().getSymbol());
            dirLabel.setText(pos.isLong() ? "▲ BUY" : "▼ SELL");
            dirLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px; -fx-font-weight: 700;");

            double pnl = pos.realizedPnl();
            String sign = pnl >= 0 ? "+" : "";
            pnlLabel.setText(sign + "$" + String.format("%.2f", pnl));
            pnlLabel.getStyleClass().removeAll("pos-pnl-positive", "pos-pnl-negative");
            pnlLabel.getStyleClass().add(pnl >= 0 ? "pos-pnl-positive" : "pos-pnl-negative");

            infoLabel.setText(String.format(java.util.Locale.US, "%.4f | Open: %.2f\nClose: %.2f",
                    pos.quantity(), pos.entryPrice(), pos.closePrice()));
                    
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm");
            dateLabel.setText(pos.closeDate().format(fmt));

            setGraphic(root);
            setPadding(new Insets(4, 8, 4, 8));
        }
    }
}
