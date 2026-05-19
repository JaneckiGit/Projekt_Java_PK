package com.stockdemo.ui;

import com.stockdemo.model.AssetType;
import com.stockdemo.model.Instrument;
import com.stockdemo.service.MarketDataService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.function.Consumer;

/**
 * Left panel: watchlist with Stock/CFD/Crypto filter tabs,
 * live-updating price cells, and an instrument detail card.
 */
public class WatchlistPanel extends VBox {

    private final MarketDataService marketData;
    private final ListView<Instrument> listView;
    private final javafx.collections.ObservableList<Instrument> filtered = javafx.collections.FXCollections
            .observableArrayList();

    private Consumer<Instrument> onSelect;

    // ── Detail panel labels ───────────────────────────────────────────────
    private final Label detailName = new Label("—");
    private final Label detailBid = new Label("—");
    private final Label detailAsk = new Label("—");
    private final Label detailHigh = new Label("—");
    private final Label detailLow = new Label("—");
    private final Label detailOpen = new Label("—");
    private final Label detailPrev = new Label("—");
    private final Label detailVol = new Label("—");
    private final Label detailChg = new Label("—");
    private Instrument currentDetail = null;

    public WatchlistPanel(MarketDataService marketData) {
        this.marketData = marketData;
        this.setId("watchlistPanel");
        this.setSpacing(0);

        // ── Title ─────────────────────────────────────────────────────────
        Label title = new Label("Market Watch");
        title.setId("watchlistTitle");

        // ── Filter buttons ────────────────────────────────────────────────
        ToggleGroup tg = new ToggleGroup();
        ToggleButton btnStock = filterBtn("STOCK", AssetType.STOCK, tg);
        ToggleButton btnCfd = filterBtn("CFD", AssetType.CFD, tg);
        ToggleButton btnCrypto = filterBtn("CRYPTO", AssetType.CRYPTO, tg);

        HBox filters = new HBox(6, btnStock, btnCfd, btnCrypto);
        filters.setPadding(new Insets(0, 16, 10, 16));
        btnStock.setSelected(true);
        applyFilter(AssetType.STOCK);

        // ── ListView ──────────────────────────────────────────────────────
        listView = new ListView<>(filtered);
        listView.getStyleClass().add("instrument-list");
        listView.setCellFactory(lv -> new InstrumentCell());
        listView.setOnMouseClicked(e -> {
            Instrument sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                currentDetail = sel;
                updateDetailPanel(sel);
                if (onSelect != null)
                    onSelect.accept(sel);
            }
        });
        VBox.setVgrow(listView, Priority.ALWAYS);

        // ── Detail panel ──────────────────────────────────────────────────
        VBox detailPanel = buildDetailPanel();

        this.getChildren().addAll(title, filters, listView, detailPanel);
    }

    /** Expose the ListView for external selection queries. */
    public ListView<Instrument> listView() {
        return listView;
    }

    public void setOnInstrumentSelected(Consumer<Instrument> handler) {
        this.onSelect = handler;
    }

    /** Call to refresh cell rendering and update detail panel. */
    public void refreshList() {
        listView.refresh();
        if (currentDetail != null)
            updateDetailPanel(currentDetail);
    }

    // ── Detail panel builder ──────────────────────────────────────────────

    private VBox buildDetailPanel() {
        Label secTitle = new Label("INSTRUMENT DETAILS");
        secTitle.getStyleClass().add("section-title");

        // Style all detail labels
        for (Label lbl : new Label[] { detailBid, detailAsk, detailHigh, detailLow,
                detailOpen, detailPrev, detailVol, detailChg }) {
            lbl.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 12px; -fx-font-weight: 600;");
        }
        detailName.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(5);
        addDetailRow(grid, 0, "Bid", detailBid, "#3fb950");
        addDetailRow(grid, 1, "Ask", detailAsk, "#f85149");
        addDetailRow(grid, 2, "Day High", detailHigh, "#e6edf3");
        addDetailRow(grid, 3, "Day Low", detailLow, "#e6edf3");
        addDetailRow(grid, 4, "Open", detailOpen, "#e6edf3");
        addDetailRow(grid, 5, "Prev Close", detailPrev, "#e6edf3");
        addDetailRow(grid, 6, "Volume", detailVol, "#8b949e");
        addDetailRow(grid, 7, "Change", detailChg, "#e6edf3");

        VBox panel = new VBox(8, secTitle, detailName, grid);
        panel.setPadding(new Insets(12, 16, 16, 16));
        panel.setStyle("-fx-background-color: #161b22; -fx-border-color: #21262d; " +
                "-fx-border-width: 1 0 0 0;");
        return panel;
    }

    private void addDetailRow(GridPane grid, int row, String label, Label valueLabel, String color) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");
        valueLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; -fx-font-weight: 600;");
        grid.add(lbl, 0, row);
        grid.add(valueLabel, 1, row);
        ColumnConstraints c0 = new ColumnConstraints(90);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        if (row == 0)
            grid.getColumnConstraints().addAll(c0, c1);
    }

    private void updateDetailPanel(Instrument inst) {
        detailName.setText(inst.getName() + " (" + inst.getSymbol() + ")");
        detailBid.setText(formatPrice(inst.getBid()));
        detailAsk.setText(formatPrice(inst.getAsk()));
        detailHigh.setText(formatPrice(inst.getDayHigh()));
        detailLow.setText(formatPrice(inst.getDayLow()));
        detailOpen.setText(formatPrice(inst.getOpen()));
        detailPrev.setText(formatPrice(inst.getPrevClose()));

        // Volume formatting
        double vol = inst.getVolume();
        if (vol >= 1_000_000)
            detailVol.setText(String.format("%.2fM", vol / 1_000_000));
        else if (vol >= 1_000)
            detailVol.setText(String.format("%.1fK", vol / 1_000));
        else
            detailVol.setText(String.format("%.2f", vol));

        double ch = inst.getChangePercent();
        String sign = ch >= 0 ? "+" : "";
        detailChg.setText(sign + String.format("%.2f%%", ch));
        detailChg.setStyle("-fx-text-fill: " + (ch >= 0 ? "#3fb950" : "#f85149") +
                "; -fx-font-size: 12px; -fx-font-weight: 600;");
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private ToggleButton filterBtn(String text, AssetType type, ToggleGroup tg) {
        ToggleButton btn = new ToggleButton(text);
        btn.getStyleClass().add("filter-btn");
        btn.setToggleGroup(tg);
        btn.selectedProperty().addListener((obs, o, selected) -> {
            if (selected) {
                btn.getStyleClass().add("filter-btn-active");
                applyFilter(type);
            } else {
                btn.getStyleClass().remove("filter-btn-active");
            }
        });
        return btn;
    }

    private void applyFilter(AssetType type) {
        filtered.setAll(marketData.instruments.filtered(i -> i.getType() == type));
    }

    // ── Cell renderer ─────────────────────────────────────────────────────

    private static class InstrumentCell extends ListCell<Instrument> {
        private final HBox root = new HBox(8);
        private final VBox left = new VBox(2);
        private final Label symbol = new Label();
        private final Label name = new Label();
        private final VBox right = new VBox(2);
        private final Label price = new Label();
        private final Label change = new Label();
        private final Label badge = new Label();

        InstrumentCell() {
            symbol.getStyleClass().add("inst-symbol");
            name.getStyleClass().add("inst-name");
            price.getStyleClass().add("inst-price");
            badge.getStyleClass().add("type-badge");

            left.getChildren().addAll(symbol, name);
            right.getChildren().addAll(price, change);
            right.setAlignment(Pos.CENTER_RIGHT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            root.getChildren().addAll(badge, left, spacer, right);
            root.setAlignment(Pos.CENTER_LEFT);
            root.setPadding(new Insets(2, 0, 2, 0));
        }

        @Override
        protected void updateItem(Instrument inst, boolean empty) {
            super.updateItem(inst, empty);
            if (empty || inst == null) {
                setGraphic(null);
                return;
            }

            symbol.setText(inst.getSymbol());
            name.setText(inst.getName());

            double p = inst.getPrice();
            double ch = inst.getChangePercent();
            price.setText(p > 0 ? formatPrice(p) : "—");

            String sign = ch >= 0 ? "+" : "";
            change.setText(sign + String.format("%.2f%%", ch));
            change.getStyleClass().removeAll("change-positive", "change-negative");
            change.getStyleClass().add(ch >= 0 ? "change-positive" : "change-negative");

            badge.getStyleClass().removeAll("badge-stock", "badge-cfd", "badge-crypto");
            switch (inst.getType()) {
                case STOCK -> {
                    badge.setText("STK");
                    badge.getStyleClass().add("badge-stock");
                }
                case CFD -> {
                    badge.setText("CFD");
                    badge.getStyleClass().add("badge-cfd");
                }
                case CRYPTO -> {
                    badge.setText("CRP");
                    badge.getStyleClass().add("badge-crypto");
                }
            }

            setGraphic(root);
        }

        private String formatPrice(double p) {
            if (p < 1)
                return String.format("%.5f", p);
            if (p < 100)
                return String.format("%.4f", p);
            return String.format("%.2f", p);
        }
    }

    private String formatPrice(double p) {
        if (p <= 0)
            return "—";
        if (p < 1)
            return String.format("%.5f", p);
        if (p < 100)
            return String.format("%.4f", p);
        return String.format("%.2f", p);
    }
}
