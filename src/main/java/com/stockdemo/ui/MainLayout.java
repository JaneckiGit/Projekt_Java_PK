package com.stockdemo.ui;

import com.stockdemo.model.Instrument;
import com.stockdemo.model.Position;
import com.stockdemo.service.MarketDataService;
import com.stockdemo.service.PortfolioService;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

//Główny layout: po lewej Watchlist, na środku Wykres, po prawej Portfolio.

public class MainLayout extends BorderPane {

    private final MarketDataService marketData;
    private final PortfolioService portfolio;
    private final ChartPanel chartPanel;
    private final PortfolioPanel portfolioPanel;
    private final WatchlistPanel watchlistPanel;

    // Referencja do StackPane z MainApp — używana do nakładania modali i mini-menu
    private StackPane appRoot;

    // Aktywne mini-menu (wraz z wrapperem przechwytującym kliknięcia poza menu)
    private Pane activeMenuWrapper;

    // Preferencje (ustawienia z modala Settings)
    private static final Preferences PREFS = Preferences.userNodeForPackage(MainLayout.class);
    private static final String PREF_SOUND_ALERTS = "soundAlerts";
    private static final String PREF_DARK_MODE = "darkMode";

    public PortfolioService getPortfolio() { return portfolio; }
    public MarketDataService getMarketData() { return marketData; }

    public StackPane getAppRoot() { return appRoot; }
    public void setAppRoot(StackPane appRoot) { this.appRoot = appRoot; }

    public MainLayout() {
        this.marketData = new MarketDataService();
        this.portfolio = new PortfolioService();

        this.chartPanel = new ChartPanel(marketData);
        this.portfolioPanel = new PortfolioPanel(portfolio, chartPanel);
        this.watchlistPanel = new WatchlistPanel(marketData);

        //Połącz kliknięcie na liście z odświeżaniem wykresu
        watchlistPanel.setOnInstrumentSelected(inst -> {
            chartPanel.loadInstrument(inst);
            portfolioPanel.setInstrument(inst);
        });

        //W pętli co każdy "tik" (np. co 2 sekundy):
        //1. Odśwież listę, 2. Sprawdź StopLoss, 3. Przelicz PnL
        marketData.startPolling(() -> {
            watchlistPanel.refreshList();
            portfolio.refreshPortfolio();
            portfolioPanel.refresh();

            Instrument sel = watchlistPanel.listView().getSelectionModel().getSelectedItem();
            if (sel != null)
                chartPanel.refreshPrice(sel);
        });

        StackPane centerStack = new StackPane(chartPanel);
        centerStack.setAlignment(Pos.TOP_CENTER);
        centerStack.setMinWidth(0);
        centerStack.setMinHeight(0);

        this.setLeft(watchlistPanel);
        this.setCenter(centerStack);
        this.setRight(portfolioPanel);

        // Po zbudowaniu paneli — podłącz menu przycisk (w PortfolioPanel)
        portfolioPanel.setOnMenuRequested(this::showMenu);
    }

    // ===================== MINI-MENU =====================

    private void showMenu() {
        if (appRoot == null) return;
        if (activeMenuWrapper != null) {
            closeMenu();
            return;
        }

        VBox menu = new VBox(2);
        menu.getStyleClass().add("menu-popup");
        menu.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        menu.getChildren().addAll(
                makeMenuItem("\uD83D\uDCCA  Statistics", () -> {
                    closeMenu();
                    new ModalOverlay("Statistics", buildStatisticsContent(portfolioPanel)).showOn(appRoot);
                }),
                makeMenuItem("\u2699  Settings", () -> {
                    closeMenu();
                    new ModalOverlay("Settings", buildSettingsContent()).showOn(appRoot);
                }),
                makeMenuItem("\uD83D\uDCB0  Set Balance", () -> {
                    closeMenu();
                    new ModalOverlay("Set Balance", buildBalanceContent(portfolio)).showOn(appRoot);
                })
        );

        // Wrapper — przezroczysta warstwa na cały appRoot, łapiąca kliknięcia poza menu
        Pane wrapper = new Pane(menu);
        wrapper.setPickOnBounds(true);
        wrapper.setStyle("-fx-background-color: transparent;");
        wrapper.setOnMousePressed(e -> {
            if (e.getTarget() == wrapper) closeMenu();
        });

        appRoot.getChildren().add(wrapper);
        activeMenuWrapper = wrapper;

        // Wymuś layout, by poznać pref. rozmiar menu
        menu.applyCss();
        menu.layout();
        double menuWidth = menu.prefWidth(-1);

        // Pozycjonuj menu w prawym górnym rogu aplikacji (po prawej stronie panelu Account)
        Button menuBtn = portfolioPanel.getMenuButton();
        Bounds btnBounds = menuBtn.localToScene(menuBtn.getBoundsInLocal());
        double y = btnBounds.getMaxY() + 4;

        double appWidth = appRoot.getWidth();
        double x = appWidth - menuWidth - 8;
        if (x < 8) x = 8;
        menu.setLayoutX(x);
        menu.setLayoutY(y);

        // Animacja fade-in
        menu.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(150), menu);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        fade.play();
    }

    private void closeMenu() {
        if (activeMenuWrapper == null) return;
        Pane wrapper = activeMenuWrapper;
        activeMenuWrapper = null;

        Node menu = wrapper.getChildren().isEmpty() ? null : wrapper.getChildren().get(0);
        if (menu == null) {
            appRoot.getChildren().remove(wrapper);
            return;
        }

        FadeTransition fade = new FadeTransition(Duration.millis(120), menu);
        fade.setFromValue(menu.getOpacity());
        fade.setToValue(0);
        fade.setInterpolator(Interpolator.EASE_OUT);
        fade.setOnFinished(e -> {
            if (wrapper.getParent() instanceof Pane parent) {
                parent.getChildren().remove(wrapper);
            }
        });
        fade.play();
    }

    private Button makeMenuItem(String label, Runnable action) {
        Button b = new Button(label);
        b.getStyleClass().add("menu-popup-item");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setFocusTraversable(false);
        b.setOnAction(e -> action.run());
        return b;
    }

    // ===================== ZAWARTOŚĆ MODALI =====================

    /** Modal: statystyki (Win Rate, Total P&L, Best/Worst Trade, Avg P&L) */
    private Node buildStatisticsContent(PortfolioPanel panel) {
        VBox box = new VBox(0);
        box.setPadding(new Insets(20));
        box.setFillWidth(true);

        box.getChildren().addAll(
                buildStatRow("Win Rate",    panel.getLabelWinRate()),
                buildStatRow("Total P&L",   panel.getLabelTotalPnl()),
                buildStatRow("Best Trade",  panel.getLabelBestTrade()),
                buildStatRow("Worst Trade", panel.getLabelWorstTrade()),
                buildStatRow("Avg P&L",     panel.getLabelAvgPnl())
        );

        // Wymuś odświeżenie etykiet (na wypadek, gdyby były puste przed otwarciem)
        panel.refresh();
        return box;
    }

    private HBox buildStatRow(String name, Label valueLabel) {
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-text-fill: #c9d1d9; -fx-font-size: 14px; -fx-font-weight: 600;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        HBox row = new HBox(12, nameLbl, sp, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 8, 12, 8));
        row.setStyle("-fx-border-color: transparent transparent #21262d transparent; -fx-border-width: 0 0 1 0;");
        return row;
    }

    /** Modal: ustawienia */
    private Node buildSettingsContent() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(20));
        box.setFillWidth(true);

        HBox sound = buildToggleRow(
                "Sound alerts",
                "Play alert.wav when SL/TP is triggered",
                PREFS.getBoolean(PREF_SOUND_ALERTS, true),
                v -> PREFS.putBoolean(PREF_SOUND_ALERTS, v)
        );

        HBox dark = buildToggleRow(
                "Dark mode",
                "Use dark UI theme (placeholder)",
                PREFS.getBoolean(PREF_DARK_MODE, true),
                v -> PREFS.putBoolean(PREF_DARK_MODE, v)
        );

        Separator sep = new Separator();
        sep.setStyle("-fx-padding: 6 0 6 0;");

        Label aboutTitle = new Label("ABOUT");
        aboutTitle.getStyleClass().add("section-title");
        aboutTitle.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 10px; -fx-font-weight: 700; -fx-padding: 4 0 4 0;");

        Label appName = new Label("Stock Demo — Trading Platform");
        appName.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label version = new Label("Version 1.0.0");
        version.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");

        box.getChildren().addAll(sound, dark, sep, aboutTitle, appName, version);
        return box;
    }

    private HBox buildToggleRow(String label, String desc, boolean initial, Consumer<Boolean> onChange) {
        Label name = new Label(label);
        name.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label d = new Label(desc);
        d.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");

        VBox text = new VBox(2, name, d);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        CheckBox toggle = new CheckBox();
        toggle.setSelected(initial);
        toggle.selectedProperty().addListener((o, oldV, newV) -> onChange.accept(newV));

        HBox row = new HBox(12, text, sp, toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 4, 8, 4));
        return row;
    }

    /** Modal: ustawianie balansu */
    private Node buildBalanceContent(PortfolioService portfolio) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(20));
        box.setFillWidth(true);

        Label info = new Label("Current balance: $" + String.format(Locale.US, "%,.2f", portfolio.getBalance()));
        info.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");

        Label fieldLbl = new Label("New balance ($)");
        fieldLbl.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");

        TextField field = new TextField(String.format(Locale.US, "%.2f", portfolio.getBalance()));
        field.getStyleClass().add("trade-field");
        field.setMaxWidth(Double.MAX_VALUE);

        Label quickLbl = new Label("Quick amounts");
        quickLbl.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");

        HBox quick = new HBox(8);
        for (double v : new double[]{10_000, 50_000, 100_000, 250_000}) {
            Button b = new Button("$" + String.format(Locale.US, "%,.0f", v));
            b.getStyleClass().add("chart-type-btn");
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
            b.setOnAction(e -> field.setText(String.format(Locale.US, "%.2f", v)));
            quick.getChildren().add(b);
        }

        Label warning = new Label("⚠ Setting a new balance will close all open positions");
        warning.setStyle("-fx-text-fill: #f0883e; -fx-font-size: 12px; -fx-font-weight: bold;");
        boolean hasOpen = !portfolio.openPositions.isEmpty();
        warning.setVisible(hasOpen);
        warning.setManaged(hasOpen);

        Button applyBtn = new Button("Apply");
        applyBtn.setMaxWidth(Double.MAX_VALUE);
        applyBtn.setStyle(
                "-fx-background-color: #1f6feb; -fx-text-fill: white; -fx-font-weight: bold; " +
                        "-fx-padding: 10 16; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 13px;"
        );

        String fieldNormalStyle = "";
        Runnable validate = () -> {
            try {
                double v = Double.parseDouble(field.getText().replace(",", "."));
                if (v <= 0) throw new NumberFormatException();
                field.setStyle(fieldNormalStyle);
                applyBtn.setDisable(false);
            } catch (Exception ex) {
                field.setStyle("-fx-border-color: #f85149; -fx-border-width: 1.5; -fx-border-radius: 6;");
                applyBtn.setDisable(true);
            }
        };
        field.textProperty().addListener((o, ov, nv) -> validate.run());
        validate.run();

        applyBtn.setOnAction(e -> {
            try {
                double v = Double.parseDouble(field.getText().replace(",", "."));
                if (v <= 0) return;

                // Zamknij wszystkie otwarte pozycje przed ustawieniem nowego balansu
                if (!portfolio.openPositions.isEmpty()) {
                    List<Position> toClose = new ArrayList<>(portfolio.openPositions);
                    for (Position p : toClose) {
                        portfolio.closePosition(p);
                    }
                    portfolio.openPositions.clear();
                }

                portfolio.balanceProperty().set(v);
                portfolio.refreshPortfolio();
                portfolioPanel.refresh();

                closeParentModal((Node) e.getSource());
            } catch (NumberFormatException ignored) {
            }
        });

        box.getChildren().addAll(info, fieldLbl, field, quickLbl, quick, warning, applyBtn);
        return box;
    }

    /** Wędruje w górę drzewa scen w poszukiwaniu ModalOverlay i go zamyka. */
    private void closeParentModal(Node node) {
        Node n = node;
        while (n != null) {
            if (n instanceof ModalOverlay m) {
                m.close();
                return;
            }
            n = n.getParent();
        }
    }
}
