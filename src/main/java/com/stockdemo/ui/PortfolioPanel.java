package com.stockdemo.ui;

import com.stockdemo.model.Instrument;
import com.stockdemo.model.Position;
import com.stockdemo.service.PortfolioService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.function.BiConsumer;

/**
 * Prawy panel: Podsumowanie konta + formularz zleceń (XTB Style) + lista
 * pozycji.
 */
public class PortfolioPanel extends VBox {

    private final PortfolioService portfolio;
    private final ChartPanel chartPanel;

    private Instrument selectedInstrument;

    // Etykiety z saldem konta
    private final Label balanceLabel = new Label("$100,000.00");
    private final Label equityLabel = new Label("Equity: $100,000.00");
    private final Label pnlLabel = new Label("P&L: $0.00");

    // Pola tekstowe w formularzu
    private final TextField qtyField = new TextField("1");
    private final TextField slField = new TextField();
    private final TextField tpField = new TextField();

    // Lista otwartych pozycji
    private final ListView<Position> positionsList = new ListView<>();

    public PortfolioPanel(PortfolioService portfolio, ChartPanel chartPanel) {
        this.portfolio = portfolio;
        this.chartPanel = chartPanel;
        this.setId("portfolioPanel");
        this.setSpacing(0);

        buildAccountSection();
        buildTradeForm();
        buildPositionsList();

        // Kiedy ruszasz liniami SL/TP na wykresie, aktualizuj pola tekstowe!
        chartPanel.setOnSlTpChanged(() -> {
            slField.setText(formatPrice(chartPanel.getSlPrice()));
            tpField.setText(formatPrice(chartPanel.getTpPrice()));
        });

        // Odświeżaj listę pozycji automatycznie
        portfolio.openPositions.addListener((javafx.collections.ListChangeListener<Position>) c -> refresh());
        refresh();
    }

    public void setInstrument(Instrument instrument) {
        this.selectedInstrument = instrument;

        slField.setText("0.00");
        tpField.setText("0.00");
        chartPanel.setSlPrice(0.0);
        chartPanel.setTpPrice(0.0);

        // Resetuje wolumen do bezpiecznego "0.01" przy zmianie instrumentu
        qtyField.setText("0.01");
        updateOrderValue();
    }

    public void refresh() {
        balanceLabel.setText("$" + String.format("%,.2f", portfolio.getBalance()));
        equityLabel.setText("Equity: $" + String.format("%,.2f", portfolio.getEquity()));

        // Obliczanie zysków w otwartych pozycjach
        double pnl = portfolio.openPositions.stream().mapToDouble(Position::getPnl).sum();
        String sign = pnl >= 0 ? "+" : "";
        pnlLabel.setText("P&L: " + sign + "$" + String.format("%,.2f", pnl));
        pnlLabel.setStyle("-fx-text-fill: " + (pnl >= 0 ? "#3fb950" : "#f85149") + ";");
        availableFundsLbl.setText("$" + String.format("%,.2f", portfolio.getBalance()));
        positionsList.refresh();
    }

    // ── Budowanie UI ───────────────────────────────────────────────────────

    private void buildAccountSection() {
        Label sectionTitle = new Label("ACCOUNT");
        sectionTitle.getStyleClass().add("section-title");

        balanceLabel.getStyleClass().add("balance-value");
        equityLabel.getStyleClass().add("equity-value");
        pnlLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");

        VBox section = new VBox(6, sectionTitle, balanceLabel, equityLabel, pnlLabel);
        section.getStyleClass().add("portfolio-section");
        this.getChildren().add(section);
    }

    private final Label availableFundsLbl = new Label("$0.00");
    private final Label marginValueLbl = new Label("≈ $0.00");
    private final Label spreadLbl = new Label("$0.00 / 0 PIPS");
    private final Label contractValueLbl = new Label("≈ $0.00");
    private final Label buyPriceLbl = new Label("0.00");

    private void buildTradeForm() {
        // ZAKŁADKI: Market Order | SL/TP
        HBox tabsBox = new HBox();
        tabsBox.setAlignment(Pos.CENTER);
        tabsBox.setPadding(new Insets(0, 0, 15, 0));
        HBox tabsBg = new HBox();
        tabsBg.getStyleClass().add("xtb-tabs");
        Button marketTab = new Button("Market order");
        marketTab.getStyleClass().addAll("xtb-tab-btn", "xtb-tab-btn-active");
        Button stopTab = new Button("SL / TP");
        stopTab.getStyleClass().add("xtb-tab-btn");
        tabsBg.getChildren().addAll(marketTab, stopTab);
        tabsBox.getChildren().add(tabsBg);

        // --- ZAKŁADKA 1: MARKET ORDER ---
        VBox marketView = new VBox(0);

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
        Button minusBtn = new Button("—");
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

        // Sekcja ze spreadem i prowizją
        HBox spreadBox = new HBox(4);
        spreadBox.setAlignment(Pos.CENTER);
        spreadBox.setPadding(new Insets(15, 0, 15, 0));
        Label sLbl = new Label("Spread:");
        sLbl.setStyle(
                "-fx-text-fill: #8b949e; -fx-font-size: 13px; -fx-border-color: transparent transparent #8b949e transparent; -fx-border-style: dotted;");
        spreadLbl.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 14px;");
        spreadBox.getChildren().addAll(sLbl, spreadLbl);

        GridPane infoGrid = new GridPane();
        infoGrid.setVgap(15);
        Label cLbl = new Label("Commission");
        cLbl.getStyleClass().add("xtb-row-lbl");
        cLbl.setStyle("-fx-border-color: transparent transparent #8b949e transparent; -fx-border-style: dotted;");
        Label cVal = new Label("$0.00");
        cVal.getStyleClass().add("xtb-row-val");

        Label cvLbl = new Label("Contract value");
        cvLbl.getStyleClass().add("xtb-row-lbl");
        cvLbl.setStyle("-fx-border-color: transparent transparent #8b949e transparent; -fx-border-style: dotted;");
        contractValueLbl.getStyleClass().add("xtb-row-val");

        infoGrid.add(cLbl, 0, 0);
        infoGrid.add(cVal, 1, 0);
        infoGrid.add(cvLbl, 0, 1);
        infoGrid.add(contractValueLbl, 1, 1);
        ColumnConstraints cc0 = new ColumnConstraints();
        ColumnConstraints cc1 = new ColumnConstraints();
        cc1.setHgrow(Priority.ALWAYS);
        cc1.setHalignment(javafx.geometry.HPos.RIGHT);
        infoGrid.getColumnConstraints().addAll(cc0, cc1);

        marketView.getChildren().addAll(volBox, spreadBox, infoGrid);

        // --- ZAKŁADKA 2: SL / TP ---
        VBox slTpView = new VBox(15);
        slTpView.setPadding(new Insets(10, 0, 10, 0));
        slTpView.setVisible(false);
        slTpView.setManaged(false);

        Label slDesc = new Label("Set Stop Loss and Take Profit levels.");
        slDesc.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");

        VBox slBox = new VBox(5);
        Label slFieldLbl = new Label("Stop Loss Price");
        slFieldLbl.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
        slField.getStyleClass().addAll("trade-field", "sl-field");
        slField.setPromptText("Stop Loss");
        slField.textProperty().addListener((o, old, val) -> {
            try {
                chartPanel.setSlPrice(Double.parseDouble(val));
            } catch (Exception ignored) {
            }
        });
        slBox.getChildren().addAll(slFieldLbl, slField);

        VBox tpBox = new VBox(5);
        Label tpFieldLbl = new Label("Take Profit Price");
        tpFieldLbl.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
        tpField.getStyleClass().addAll("trade-field", "tp-field");
        tpField.setPromptText("Take Profit");
        tpField.textProperty().addListener((o, old, val) -> {
            try {
                chartPanel.setTpPrice(Double.parseDouble(val));
            } catch (Exception ignored) {
            }
        });
        tpBox.getChildren().addAll(tpFieldLbl, tpField);

        slTpView.getChildren().addAll(slDesc, slBox, tpBox);

        // Przełączanie zakładek
        marketTab.setOnAction(e -> {
            marketTab.getStyleClass().add("xtb-tab-btn-active");
            stopTab.getStyleClass().remove("xtb-tab-btn-active");
            marketView.setVisible(true);
            marketView.setManaged(true);
            slTpView.setVisible(false);
            slTpView.setManaged(false);
        });
        stopTab.setOnAction(e -> {
            stopTab.getStyleClass().add("xtb-tab-btn-active");
            marketTab.getStyleClass().remove("xtb-tab-btn-active");
            marketView.setVisible(false);
            marketView.setManaged(false);
            slTpView.setVisible(true);
            slTpView.setManaged(true);
        });

        // PRZYCISKI BUY/SELL
        HBox fundsBox = new HBox(4);
        fundsBox.setAlignment(Pos.CENTER);
        fundsBox.setPadding(new Insets(20, 0, 10, 0));
        Label fLbl = new Label("Available funds:");
        fLbl.getStyleClass().add("xtb-funds");
        availableFundsLbl.getStyleClass().add("xtb-funds-val");
        fundsBox.getChildren().addAll(fLbl, availableFundsLbl);

        HBox buttonsBox = new HBox(10);
        VBox buyBtn = new VBox(2);
        buyBtn.getStyleClass().add("xtb-btn-buy");
        buyBtn.setAlignment(Pos.CENTER);
        buyBtn.setPadding(new Insets(10, 0, 10, 0));
        HBox.setHgrow(buyBtn, Priority.ALWAYS);
        Label buyTitle = new Label("Buy");
        buyTitle.getStyleClass().add("xtb-btn-title");
        buyPriceLbl.getStyleClass().add("xtb-btn-price");
        buyBtn.getChildren().addAll(buyTitle, buyPriceLbl);
        buyBtn.setOnMouseClicked(e -> placeOrder(true));

        buttonsBox.getChildren().addAll(buyBtn);

        VBox formContainer = new VBox(0, marketView, slTpView);

        VBox section = new VBox(0, tabsBox, formContainer, fundsBox, buttonsBox);
        section.getStyleClass().add("portfolio-section");
        section.setPadding(new Insets(16));
        this.getChildren().add(section);
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
            spreadLbl.setText("$0.00 / 0 PIPS");
            return;
        }

        double qty = parseDouble(qtyField.getText());
        double price = selectedInstrument.getPrice();
        double bid = selectedInstrument.getBid();
        double ask = selectedInstrument.getAsk();

        buyPriceLbl.setText(formatPrice(ask));

        double costUsd = qty * price;
        marginValueLbl.setText(String.format("≈ $%,.2f", costUsd));
        contractValueLbl.setText(String.format("≈ $%,.2f", costUsd));

        double spread = ask - bid;
        double spreadUsd = spread * qty;
        int pips = (int) (spread / (price * 0.0001));
        if (pips <= 0)
            pips = 2;
        spreadLbl.setText(String.format("$%.2f / %d PIPS", spreadUsd, pips));
    }

    private void buildPositionsList() {
        Label sectionTitle = new Label("OPEN POSITIONS");
        sectionTitle.getStyleClass().add("section-title");
        sectionTitle.setPadding(new Insets(14, 16, 6, 16));

        positionsList.setItems(portfolio.openPositions);
        positionsList.getStyleClass().add("positions-list");
        positionsList.setCellFactory(lv -> new PositionCell());
        VBox.setVgrow(positionsList, Priority.ALWAYS);

        this.getChildren().addAll(sectionTitle, positionsList);
    }

    // ── Zlecenia giełdowe ─────────────────────────────────────────────────

    private void placeOrder(boolean isLong) {
        if (selectedInstrument == null) {
            showAlert("No instrument", "Wybierz najpierw walor z listy po lewej stronie.");
            return;
        }
        double qty;
        try {
            qty = Double.parseDouble(qtyField.getText().replace(",", "."));
        } catch (NumberFormatException e) {
            showAlert("Invalid quantity", "Wprowadź prawidłowy wolumen.");
            return;
        }
        double sl = parseDouble(slField.getText());
        double tp = parseDouble(tpField.getText());

        double currentPrice = selectedInstrument.getPrice();
        if (isLong) {
            if (sl >= currentPrice)
                sl = 0.0;
            if (tp > 0 && tp <= currentPrice)
                tp = 0.0;
        } else {
            if (sl > 0 && sl <= currentPrice)
                sl = 0.0;
            if (tp > 0 && tp >= currentPrice)
                tp = 0.0;
        }

        portfolio.openPosition(selectedInstrument, isLong, qty, sl, tp);
        refresh();

        Position pos = portfolio.openPositions.get(portfolio.openPositions.size() - 1);
        chartPanel.showPositionLines(pos);
    }

    private double parseDouble(String text) {
        try {
            return Double.parseDouble(text.replace(",", "."));
        } catch (Exception e) {
            return 0;
        }
    }

    private String formatPrice(double p) {
        if (p < 1)
            return String.format("%.5f", p);
        if (p < 100)
            return String.format("%.4f", p);
        return String.format("%.2f", p);
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.getDialogPane().setStyle("-fx-background-color: #161b22;");
        alert.showAndWait();
    }

    // ── Komórka listy pozycji ─────────────────────────────────────────────

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
            infoLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");
            dirLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700;");

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            top.getChildren().addAll(dirLabel, symLabel, sp, pnlLabel);
            bottom.getChildren().addAll(infoLabel, sp, closeBtn);
            root.getChildren().addAll(top, bottom);
            root.getStyleClass().add("position-cell");
            root.setPadding(new Insets(8, 10, 8, 10));

            closeBtn.setOnAction(e -> {
                Position pos = getItem();
                if (pos != null) {
                    portfolio.closePosition(pos);
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

            infoLabel.setText(String.format("%.4f @ %.2f  SL:%.2f  TP:%.2f",
                    pos.getQuantity(), pos.getEntryPrice(),
                    pos.getStopLoss(), pos.getTakeProfit()));

            setGraphic(root);
            setPadding(new Insets(4, 8, 4, 8));
        }
    }
}
