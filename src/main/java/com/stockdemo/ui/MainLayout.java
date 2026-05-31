package com.stockdemo.ui;

import com.stockdemo.model.Instrument;
import com.stockdemo.model.Position;
import com.stockdemo.service.MarketDataService;
import com.stockdemo.service.PortfolioService;
import com.stockdemo.service.ReportService;
import com.stockdemo.model.Pit8cData;
import com.stockdemo.service.Pit8cPdfGenerator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.stage.FileChooser;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.Cursor;

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

    // Settings sidebar state
    private Pane settingsWrapper;

    // Dane do formularza PIT-8C zachowane w pamięci sesji
    private final Pit8cData pit8cData = new Pit8cData();

    // Preferencje (ustawienia z modala Settings)
    private static final Preferences PREFS = Preferences.userNodeForPackage(MainLayout.class);
    private static final String PREF_SOUND_ALERTS = "soundAlerts";
    private static final String PREF_DARK_MODE = "darkMode";
    private static final String DARK_STYLESHEET = "/styles.css";
    private static final String LIGHT_STYLESHEET = "/styles-light.css";
    private static boolean darkTheme = PREFS.getBoolean(PREF_DARK_MODE, true);

    private static final String[][] DARK_TO_LIGHT = {
            {"#0d1117", "#ffffff"},
            {"#161b22", "#f6f8fa"},
            {"#21262d", "#d0d7de"},
            {"#1c2128", "#eaeef2"},
            {"#1f3a5f", "#dce8f5"},
            {"#8b949e", "#656d76"},
            {"#c9d1d9", "#1f2328"},
            {"#e6edf3", "#1f2328"},
            {"#30363d", "#d0d7de"},
            {"#484f58", "#afb8c1"}
    };

    private static final String[][] LIGHT_TO_DARK = {
            {"#ffffff", "#0d1117"},
            {"#f6f8fa", "#161b22"},
            {"#d0d7de", "#21262d"},
            {"#eaeef2", "#1c2128"},
            {"#dce8f5", "#1f3a5f"},
            {"#656d76", "#8b949e"},
            {"#1f2328", "#e6edf3"},
            {"#afb8c1", "#484f58"}
    };

    public PortfolioService getPortfolio() { return portfolio; }
    public MarketDataService getMarketData() { return marketData; }

    public static boolean isDarkTheme() { return darkTheme; }

    public StackPane getAppRoot() { return appRoot; }
    public void setAppRoot(StackPane appRoot) { this.appRoot = appRoot; }

    public MainLayout() {
        PortfolioService.setSoundEnabled(PREFS.getBoolean(PREF_SOUND_ALERTS, true));

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
        // Podłącz przycisk ustawień (⚙)
        portfolioPanel.setOnSettingsRequested(this::toggleSettings);

        applyTheme(darkTheme);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            applyTheme(darkTheme);
            newScene.windowProperty().addListener((windowObs, oldWindow, newWindow) -> {
                if (newWindow != null) {
                    applyTheme(darkTheme);
                }
            });
        });
    }

    public void applyTheme(boolean isDark) {
        darkTheme = isDark;
        chartPanel.applyTheme(isDark);
        watchlistPanel.applyTheme(isDark);
        portfolioPanel.applyTheme(isDark);

        Scene scene = getScene();
        if (scene != null) {
            applySceneStylesheet(scene, isDark);
            applyInlineTheme(scene.getRoot(), isDark);
        }
    }

    private void setDarkTheme(boolean isDark) {
        PREFS.putBoolean(PREF_DARK_MODE, isDark);
        applyTheme(isDark);
    }

    private void applySceneStylesheet(Scene scene, boolean isDark) {
        String darkUrl = getClass().getResource(DARK_STYLESHEET).toExternalForm();
        String lightUrl = getClass().getResource(LIGHT_STYLESHEET).toExternalForm();
        String desired = isDark ? darkUrl : lightUrl;

        scene.getStylesheets().removeAll(darkUrl, lightUrl);
        scene.getStylesheets().add(desired);
    }

    private void applyInlineTheme(Node node, boolean isDark) {
        if (node == null) return;

        String style = node.getStyle();
        if (style != null && !style.isEmpty()) {
            node.setStyle(themeStyle(style, isDark));
        }
        if (node instanceof ToggleSwitch toggle) {
            toggle.applyTheme(isDark);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyInlineTheme(child, isDark);
            }
        }
    }

    public static String themeStyle(String style, boolean isDark) {
        String themed = style;
        String[][] replacements = isDark ? LIGHT_TO_DARK : DARK_TO_LIGHT;
        for (String[] pair : replacements) {
            themed = themed.replace(pair[0], pair[1]);
        }
        return themed;
    }

    private static String primaryText() {
        return darkTheme ? "#e6edf3" : "#1f2328";
    }

    private static String secondaryText() {
        return darkTheme ? "#c9d1d9" : "#1f2328";
    }

    private static String mutedText() {
        return darkTheme ? "#8b949e" : "#656d76";
    }

    private static String panelBg() {
        return darkTheme ? "#0d1117" : "#ffffff";
    }

    private static String borderColor() {
        return darkTheme ? "#21262d" : "#d0d7de";
    }

    private static String primaryTextStyle(int size, boolean bold) {
        return "-fx-text-fill: " + primaryText() + "; -fx-font-size: " + size + "px;" +
                (bold ? " -fx-font-weight: 700;" : "");
    }

    private static String mutedTextStyle(int size, boolean bold) {
        return "-fx-text-fill: " + mutedText() + "; -fx-font-size: " + size + "px;" +
                (bold ? " -fx-font-weight: 700;" : "");
    }

    private void showModal(String title, Node content) {
        new ModalOverlay(title, content).showOn(appRoot);
        applyTheme(darkTheme);
    }

    // ===================== SETTINGS SIDEBAR =====================

    private void toggleSettings() {
        if (settingsWrapper != null) {
            closeSettings();
        } else {
            openSettings();
        }
    }

    private void openSettings() {
        if (appRoot == null || settingsWrapper != null) return;

        double panelWidth = 320;

        VBox panel = new VBox(0);
        panel.getStyleClass().add("settings-panel");
        panel.setMinWidth(panelWidth);
        panel.setPrefWidth(panelWidth);
        panel.setMaxWidth(panelWidth);

        // Header
        Label title = new Label("Settings");
        title.setStyle(primaryTextStyle(16, true));
        
        SVGPath gearPath = new SVGPath();
        gearPath.setContent("M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z");
        gearPath.setFill(Color.web("#58a6ff"));
        gearPath.setStroke(Color.TRANSPARENT);
        gearPath.setScaleX(0.55);
        gearPath.setScaleY(0.55);
        
        StackPane headerIcon = new StackPane(gearPath);
        headerIcon.setMinSize(28, 28);
        headerIcon.setPrefSize(28, 28);
        headerIcon.setMaxSize(28, 28);
        headerIcon.setStyle(
            "-fx-background-color: rgba(88,166,255,0.12); " +
            "-fx-background-radius: 6;"
        );
        headerIcon.setAlignment(Pos.CENTER);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("modal-close-btn");
        closeBtn.setOnAction(e -> closeSettings());
        
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        
        HBox header = new HBox(8, headerIcon, title, sp, closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 16, 12, 16));
        header.setStyle("-fx-border-color: transparent transparent " + borderColor() + " transparent; -fx-border-width: 0 0 1 0;");

        // Items container
        VBox items = new VBox(4);
        items.setPadding(new Insets(8, 8, 8, 8));

        // Section: Export
        Label exportSection = new Label("EXPORT");
        exportSection.getStyleClass().add("settings-section-title");

        items.getChildren().addAll(
                exportSection,
                makeSettingsItem(
                    "Export Positions History",
                    "Download open & closed positions in CSV format",
                    "M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6zM13 3.5L18.5 9H13V3.5zM5 19V4h7v6h6v9H5z",
                    "#58a6ff", "rgba(88,166,255,0.1)",
                    () -> {
                        Window w = getScene() != null ? getScene().getWindow() : null;
                        ReportService.exportAllPositionsCSV(portfolio.openPositions, portfolio.closedPositions, w);
                    }
                ),
                makeSettingsItem(
                    "Download PIT-8C (PDF)",
                    "Fill form and download tax statement PDF",
                    "M12 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6z M11 3.5L16.5 9H11V3.5z M7 12h10v2H7zm0 4h7v2H7z",
                    "#8957e5", "rgba(137,87,229,0.1)",
                    () -> {
                        closeSettings();
                        showModal("PIT-8C Tax Form Details", buildPit8cFormContent());
                    }
                )
        );

        // Section: Account
        Label accountSection = new Label("ACCOUNT MANAGEMENT");
        accountSection.getStyleClass().add("settings-section-title");

        items.getChildren().addAll(
                accountSection,
                makeSettingsItem(
                    "Reset Portfolio Balance",
                    "Reset portfolio value back to starting $100,000.00",
                    "M17.65 6.35A7.958 7.958 0 0012 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z",
                    "#f0883e", "rgba(240,136,62,0.1)",
                    () -> {
                        closeSettings();
                        com.stockdemo.service.PortfolioPersistence.reset(portfolio);
                        portfolioPanel.refresh();
                    }
                ),
                makeSettingsItem(
                    "Set Custom Balance",
                    "Manually adjust your primary trading balance",
                    "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h-1c-.55 0-1-.45-1-1v-2c0-.55.45-1 1-1h3v-1H9.5c-.28 0-.5-.22-.5-.5s.22-.5.5-.5H11V7h2v2h1c.55 0 1 .45 1 1v2c0 .55-.45 1-1 1h-3v1h3.5c.28 0 .5.22.5.5s-.22.5-.5.5H13v2z",
                    "#3fb950", "rgba(63,185,80,0.1)",
                    () -> {
                        closeSettings();
                        showModal("Set Balance", buildBalanceContent(portfolio));
                    }
                )
        );

        // Section: App
        Label appSection = new Label("APPLICATION SETTINGS");
        appSection.getStyleClass().add("settings-section-title");

        items.getChildren().addAll(
                appSection,
                makeSettingsItem(
                    "System Preferences",
                    "Adjust sound alerts, toggle dark mode, about info",
                    "M3 17v2h6v-2H3z M9 15H7v5h2v-5z M13 7v2h10V7H13z M19 5h-2v4h2V5z M3 12v2h18v-2H3z M15 10h-2v4h2v-4z",
                    "#bc8cff", "rgba(188,140,255,0.1)",
                    () -> {
                        closeSettings();
                        showModal("Settings", buildSettingsContent());
                    }
                ),
                makeSettingsItem(
                    "Exit Application",
                    "Save current portfolio state and close window",
                    "M10.09 15.59L11.5 17l5-5-5-5-1.41 1.41L12.67 11H3v2h9.67l-2.58 2.59zM19 3H5c-1.11 0-2 .9-2 2v4h2V5h14v14H5v-4H3v4c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z",
                    "#f85149", "rgba(248,81,73,0.1)",
                    () -> {
                        com.stockdemo.service.PortfolioPersistence.save(portfolio);
                        Platform.exit();
                    }
                )
        );

        ScrollPane scroll = new ScrollPane(items);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        panel.getChildren().addAll(header, scroll);

        // Wrapper z półprzezroczystym tłem
        StackPane wrapper = new StackPane();
        wrapper.setStyle("-fx-background-color: rgba(0,0,0,0.35);");
        wrapper.setPickOnBounds(true);
        wrapper.setOnMousePressed(e -> {
            if (e.getTarget() == wrapper) closeSettings();
        });

        // Panel ustawiamy po prawej stronie
        panel.setTranslateX(panelWidth);
        wrapper.getChildren().add(panel);
        StackPane.setAlignment(panel, Pos.CENTER_RIGHT);

        appRoot.getChildren().add(wrapper);
        settingsWrapper = wrapper;
        applyTheme(darkTheme);

        // Slide-in animation
        TranslateTransition slide = new TranslateTransition(Duration.millis(180), panel);
        slide.setFromX(panelWidth);
        slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_IN);
        slide.play();
    }

    private void closeSettings() {
        if (settingsWrapper == null) return;
        Pane wrapper = settingsWrapper;
        settingsWrapper = null;

        Node panel = wrapper.getChildren().isEmpty() ? null : wrapper.getChildren().get(0);
        if (panel == null) {
            appRoot.getChildren().remove(wrapper);
            return;
        }

        TranslateTransition slide = new TranslateTransition(Duration.millis(180), panel);
        slide.setToX(320);
        slide.setInterpolator(Interpolator.EASE_IN);

        FadeTransition fade = new FadeTransition(Duration.millis(180), wrapper);
        fade.setToValue(0);
        fade.setInterpolator(Interpolator.EASE_IN);

        slide.setOnFinished(e -> appRoot.getChildren().remove(wrapper));
        slide.play();
        fade.play();
    }

    private HBox makeSettingsItem(String label, String description, String pathContent, String iconColor, String iconBg, Runnable action) {
        StackPane iconContainer = createIconContainer(pathContent, iconColor, iconBg);

        Label lbl = new Label(label);
        lbl.setStyle(primaryTextStyle(13, true));

        Label desc = new Label(description);
        desc.setStyle(mutedTextStyle(11, false));

        VBox textContainer = new VBox(2, lbl, desc);
        textContainer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textContainer, Priority.ALWAYS);

        HBox item = new HBox(12, iconContainer, textContainer);
        item.getStyleClass().add("settings-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(10, 12, 10, 12));
        item.setOnMouseClicked(e -> action.run());
        return item;
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
                    showModal("Statistics", buildStatisticsContent(portfolioPanel));
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
        nameLbl.setStyle("-fx-text-fill: " + secondaryText() + "; -fx-font-size: 14px; -fx-font-weight: 600;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        HBox row = new HBox(12, nameLbl, sp, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 8, 12, 8));
        row.setStyle("-fx-border-color: transparent transparent " + borderColor() + " transparent; -fx-border-width: 0 0 1 0;");
        return row;
    }

    /** Modal: ustawienia (preferencje aplikacji) */
    private Node buildSettingsContent() {
        VBox box = new VBox(16);
        box.setPadding(new Insets(20));
        box.setFillWidth(true);
        box.setStyle("-fx-background-color: " + panelBg() + ";");

        Label generalTitle = new Label("SYSTEM PREFERENCES");
        generalTitle.getStyleClass().add("settings-section-title");
        generalTitle.setStyle("-fx-text-fill: #58a6ff; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 0 0 4 0;");

        HBox sound = buildToggleRow(
                "Sound Alerts",
                "Play alert.wav when Stop Loss or Take Profit is triggered",
                PREFS.getBoolean(PREF_SOUND_ALERTS, true),
                v -> { PREFS.putBoolean(PREF_SOUND_ALERTS, v); PortfolioService.setSoundEnabled(v); }
        );

        HBox dark = buildToggleRow(
                "Dark Mode",
                "Use high-contrast sleek dark UI theme styling",
                darkTheme,
                this::setDarkTheme
        );

        VBox generalSection = new VBox(8, generalTitle, sound, dark);

        Label aboutTitle = new Label("PLATFORM INFO");
        aboutTitle.getStyleClass().add("settings-section-title");
        aboutTitle.setStyle("-fx-text-fill: #58a6ff; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 0 0 4 0;");

        VBox aboutCard = new VBox(10);
        aboutCard.getStyleClass().add("preferences-row-card");
        aboutCard.setPadding(new Insets(20));
        aboutCard.setAlignment(Pos.CENTER);

        // Stock chart logo
        SVGPath logo = new SVGPath();
        logo.setContent("M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-1 16H6c-.55 0-1-.45-1-1V6c0-.55.45-1 1-1h12c.55 0 1 .45 1 1v12c0 .55-.45 1-1 1z M15 13h-2v3h-2V9h-2v3H7V7h2v3h2V8h2v2h2v3z");
        logo.setFill(Color.web("#3fb950"));
        logo.setScaleX(1.8);
        logo.setScaleY(1.8);

        Label appName = new Label("STOCK DEMO");
        appName.setStyle("-fx-text-fill: " + primaryText() + "; -fx-font-size: 16px; -fx-font-weight: 800; -fx-letter-spacing: 1px;");

        Label desc = new Label("Advanced Trading Simulator Platform");
        desc.setStyle(mutedTextStyle(11, false));

        Label versionChip = new Label("v1.0.0");
        versionChip.setStyle(
            "-fx-background-color: rgba(88,166,255,0.1); " +
            "-fx-text-fill: #58a6ff; " +
            "-fx-font-size: 10px; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 3 8; " +
            "-fx-background-radius: 10; " +
            "-fx-border-color: rgba(88,166,255,0.2); " +
            "-fx-border-radius: 10; " +
            "-fx-border-width: 1;"
        );

        Region aboutSpacer = new Region();
        aboutSpacer.setMinHeight(8);
        
        aboutCard.getChildren().addAll(aboutSpacer, logo, appName, desc, versionChip);
        
        VBox aboutSection = new VBox(8, aboutTitle, aboutCard);

        box.getChildren().addAll(generalSection, aboutSection);
        return box;
    }

    private HBox buildToggleRow(String label, String desc, boolean initial, Consumer<Boolean> onChange) {
        Label name = new Label(label);
        name.setStyle(primaryTextStyle(14, true));

        Label d = new Label(desc);
        d.setStyle(mutedTextStyle(11, false));

        VBox text = new VBox(2, name, d);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        ToggleSwitch toggle = new ToggleSwitch(initial);
        toggle.setOnToggle(onChange);

        HBox row = new HBox(12, text, sp, toggle);
        row.getStyleClass().add("preferences-row-card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));
        return row;
    }

    /** Modal: ustawianie balansu */
    private Node buildBalanceContent(PortfolioService portfolio) {
        VBox box = new VBox(16);
        box.setPadding(new Insets(20));
        box.setFillWidth(true);
        box.setStyle("-fx-background-color: #0d1117;");

        VBox currentCard = new VBox(6);
        currentCard.getStyleClass().add("preferences-row-card");
        currentCard.setPadding(new Insets(16));
        currentCard.setAlignment(Pos.CENTER);
        
        Label currentTitle = new Label("CURRENT BALANCE");
        currentTitle.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 10px; -fx-font-weight: bold;");
        
        Label info = new Label("$" + String.format(Locale.US, "%,.2f", portfolio.getBalance()));
        info.setStyle("-fx-text-fill: #3fb950; -fx-font-size: 24px; -fx-font-weight: 800;");
        currentCard.getChildren().addAll(currentTitle, info);

        Label fieldLbl = new Label("ENTER NEW ACCOUNT BALANCE ($)");
        fieldLbl.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 10px; -fx-font-weight: 700;");

        TextField field = new TextField(String.format(Locale.US, "%.2f", portfolio.getBalance()));
        field.getStyleClass().add("trade-field");
        field.setMaxWidth(Double.MAX_VALUE);
        field.setStyle("-fx-font-size: 14px; -fx-padding: 10 12;");

        Label quickLbl = new Label("QUICK AMOUNT PRESETS");
        quickLbl.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 10px; -fx-font-weight: 700;");

        HBox quick = new HBox(8);
        for (double v : new double[]{10_000, 50_000, 100_000, 250_000}) {
            Button b = new Button("$" + String.format(Locale.US, "%,.0f", v));
            b.getStyleClass().add("chart-type-btn");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setStyle("-fx-padding: 8 12; -fx-font-size: 12px; -fx-font-weight: 600; -fx-background-radius: 6;");
            HBox.setHgrow(b, Priority.ALWAYS);
            b.setOnAction(e -> field.setText(String.format(Locale.US, "%.2f", v)));
            quick.getChildren().add(b);
        }

        Label warning = new Label("⚠ Setting a new balance will automatically close all active positions");
        warning.setStyle("-fx-text-fill: #f0883e; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 0;");
        boolean hasOpen = !portfolio.openPositions.isEmpty();
        warning.setVisible(hasOpen);
        warning.setManaged(hasOpen);

        Button applyBtn = new Button("Apply Balance Changes");
        applyBtn.setMaxWidth(Double.MAX_VALUE);
        applyBtn.getStyleClass().add("apply-balance-btn");

        String fieldNormalStyle = "-fx-font-size: 14px; -fx-padding: 10 12;";
        Runnable validate = () -> {
            try {
                double v = Double.parseDouble(field.getText().replace(",", "."));
                if (v <= 0) throw new NumberFormatException();
                field.setStyle(fieldNormalStyle);
                applyBtn.setDisable(false);
            } catch (Exception ex) {
                field.setStyle(fieldNormalStyle + " -fx-border-color: #f85149; -fx-border-width: 1.5; -fx-border-radius: 6;");
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

        box.getChildren().addAll(currentCard, fieldLbl, field, quickLbl, quick, warning, applyBtn);
        return box;
    }

    private Node buildPit8cFormContent() {
        VBox mainBox = new VBox(12);
        mainBox.setPadding(new Insets(16));
        mainBox.setFillWidth(true);
        mainBox.setStyle("-fx-background-color: #0d1117;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox form = new VBox(16);
        form.setPadding(new Insets(4, 12, 12, 12));
        form.setStyle("-fx-background-color: #0d1117;");

        // --- SEKCYJNE GRUPY ---
        // A. Urząd Skarbowy & Cel
        VBox secA = new VBox(8);
        secA.getChildren().add(createFormSectionTitle("A. MIEJSCE I CEL SKŁADANIA INFORMACJI"));
        GridPane gridA = createFormGrid();
        
        TextField tfUrzad = createFormField("Urząd Skarbowy:", pit8cData.urzadSkarbowy, "np. Urząd Skarbowy Kraków-Śródmieście", gridA, 0);
        
        HBox cellBox = new HBox(12);
        cellBox.setAlignment(Pos.CENTER_LEFT);
        RadioButton rbZlozenie = new RadioButton("Złożenie informacji");
        rbZlozenie.setStyle("-fx-text-fill: #c9d1d9;");
        RadioButton rbKorekta = new RadioButton("Korekta informacji");
        rbKorekta.setStyle("-fx-text-fill: #c9d1d9;");
        ToggleGroup tgCel = new ToggleGroup();
        rbZlozenie.setToggleGroup(tgCel);
        rbKorekta.setToggleGroup(tgCel);
        if (pit8cData.celZlozenie) rbZlozenie.setSelected(true); else rbKorekta.setSelected(true);
        cellBox.getChildren().addAll(rbZlozenie, rbKorekta);
        
        Label lblCel = new Label("Cel złożenia:");
        lblCel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");
        gridA.add(lblCel, 0, 1);
        gridA.add(cellBox, 1, 1);
        
        TextField tfRok = createFormField("Rok podatkowy:", String.valueOf(pit8cData.rok), "Rok", gridA, 2);
        
        secA.getChildren().add(gridA);

        // B. Dane Płatnika (Składającego)
        VBox secB = new VBox(8);
        secB.getChildren().add(createFormSectionTitle("B. DANE PŁATNIKA (SKŁADAJĄCEGO)"));
        GridPane gridB = createFormGrid();
        TextField tfNipSkladajacego = createFormField("NIP Płatnika:", pit8cData.nipSkladajacego, "NIP płatnika (10 cyfr)", gridB, 0);
        TextField tfNazwaPelna = createFormField("Nazwa pełna / Firma:", pit8cData.nazwaPelna, "Nazwa firmy płatnika", gridB, 1);
        secB.getChildren().add(gridB);

        // C. Dane Podatnika (Odbiorcy - Użytkownika)
        VBox secC = new VBox(8);
        secC.getChildren().add(createFormSectionTitle("C. DANE PODATNIKA (DANE OSOBOWE I ADRES)"));
        GridPane gridC = createFormGrid();
        TextField tfNipPesel = createFormField("PESEL / NIP:", pit8cData.nipPesel, "PESEL lub NIP podatnika", gridC, 0);
        TextField tfImie = createFormField("Imię:", pit8cData.imie, "Pierwsze imię", gridC, 1);
        TextField tfNazwisko = createFormField("Nazwisko:", pit8cData.nazwisko, "Nazwisko", gridC, 2);
        TextField tfDataUr = createFormField("Data urodzenia:", pit8cData.dataUrodzenia, "DD-MM-YYYY", gridC, 3);
        TextField tfKraj = createFormField("Kraj:", pit8cData.kraj, "Kraj zamieszkania", gridC, 4);
        TextField tfWojewodztwo = createFormField("Województwo:", pit8cData.wojewodztwo, "Województwo", gridC, 5);
        TextField tfPowiat = createFormField("Powiat:", pit8cData.powiat, "Powiat", gridC, 6);
        TextField tfGmina = createFormField("Gmina:", pit8cData.gmina, "Gmina", gridC, 7);
        TextField tfUlica = createFormField("Ulica:", pit8cData.ulica, "Ulica", gridC, 8);
        
        // Dom / Lokal w jednym wierszu w gridzie
        Label lblDomLokal = new Label("Nr domu / nr lokalu:");
        lblDomLokal.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");
        HBox hbDomLokal = new HBox(8);
        hbDomLokal.setAlignment(Pos.CENTER_LEFT);
        TextField tfNrDomu = new TextField(pit8cData.nrDomu);
        tfNrDomu.setPromptText("Dom");
        tfNrDomu.getStyleClass().add("trade-field");
        tfNrDomu.setPrefWidth(80);
        TextField tfNrLokalu = new TextField(pit8cData.nrLokalu);
        tfNrLokalu.setPromptText("Lokal");
        tfNrLokalu.getStyleClass().add("trade-field");
        tfNrLokalu.setPrefWidth(80);
        hbDomLokal.getChildren().addAll(tfNrDomu, new Label("/"), tfNrLokalu);
        gridC.add(lblDomLokal, 0, 9);
        gridC.add(hbDomLokal, 1, 9);

        TextField tfMiejscowosc = createFormField("Miejscowość:", pit8cData.miejscowosc, "Miejscowość", gridC, 10);
        TextField tfKodPocztowy = createFormField("Kod pocztowy:", pit8cData.kodPocztowy, "np. 00-001", gridC, 11);
        
        secC.getChildren().add(gridC);

        form.getChildren().addAll(secA, new Separator(), secB, new Separator(), secC);
        scrollPane.setContent(form);

        // Dolny przycisk akcji
        Button generateBtn = new Button("Pobierz PIT-8C (PDF)");
        generateBtn.setMaxWidth(Double.MAX_VALUE);
        generateBtn.getStyleClass().add("btn-gradient-green");
        generateBtn.setOnAction(e -> {
            // Walidacja i zapis danych do pit8cData
            pit8cData.urzadSkarbowy = tfUrzad.getText().trim();
            pit8cData.celZlozenie = rbZlozenie.isSelected();
            try {
                pit8cData.rok = Integer.parseInt(tfRok.getText().trim());
            } catch (Exception ex) {
                pit8cData.rok = java.time.LocalDate.now().getYear() - 1;
            }
            pit8cData.nipSkladajacego = tfNipSkladajacego.getText().trim();
            pit8cData.nazwaPelna = tfNazwaPelna.getText().trim();
            pit8cData.nipPesel = tfNipPesel.getText().trim();
            pit8cData.imie = tfImie.getText().trim();
            pit8cData.nazwisko = tfNazwisko.getText().trim();
            pit8cData.dataUrodzenia = tfDataUr.getText().trim();
            pit8cData.kraj = tfKraj.getText().trim();
            pit8cData.wojewodztwo = tfWojewodztwo.getText().trim();
            pit8cData.powiat = tfPowiat.getText().trim();
            pit8cData.gmina = tfGmina.getText().trim();
            pit8cData.ulica = tfUlica.getText().trim();
            pit8cData.nrDomu = tfNrDomu.getText().trim();
            pit8cData.nrLokalu = tfNrLokalu.getText().trim();
            pit8cData.miejscowosc = tfMiejscowosc.getText().trim();
            pit8cData.kodPocztowy = tfKodPocztowy.getText().trim();

            // Uruchomienie zapisu pliku PDF
            Window w = getScene() != null ? getScene().getWindow() : null;
            FileChooser fc = new FileChooser();
            fc.setTitle("Save PIT-8C PDF");
            fc.setInitialFileName("PIT-8C_" + pit8cData.rok + "_" + pit8cData.nazwisko + "_" + pit8cData.imie + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            java.io.File file = fc.showSaveDialog(w);
            if (file != null) {
                try {
                    new Pit8cPdfGenerator().generate(pit8cData, portfolio.closedPositions, file);
                    // Zamknij modal po udanym generowaniu
                    closeParentModal((Node) e.getSource());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        mainBox.getChildren().addAll(scrollPane, generateBtn);
        return mainBox;
    }

    private Label createFormSectionTitle(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("form-section-header");
        return title;
    }

    private GridPane createFormGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(4, 0, 4, 0));
        ColumnConstraints col1 = new ColumnConstraints(130);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);
        return grid;
    }

    private TextField createFormField(String labelText, String val, String prompt, GridPane grid, int row) {
        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");
        TextField field = new TextField(val);
        field.setPromptText(prompt);
        field.getStyleClass().add("trade-field");
        field.setMaxWidth(Double.MAX_VALUE);
        grid.add(label, 0, row);
        grid.add(field, 1, row);
        return field;
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

    private StackPane createIconContainer(String pathContent, String iconColor, String bgColor) {
        SVGPath path = new SVGPath();
        path.setContent(pathContent);
        path.setFill(Color.web(iconColor));
        path.setStroke(Color.TRANSPARENT);
        
        path.setScaleX(0.85);
        path.setScaleY(0.85);
        
        StackPane container = new StackPane(path);
        container.setMinSize(28, 28);
        container.setPrefSize(28, 28);
        container.setMaxSize(28, 28);
        container.setStyle(
            "-fx-background-color: " + bgColor + "; " +
            "-fx-background-radius: 6;"
        );
        container.setAlignment(Pos.CENTER);
        return container;
    }

    public static class ToggleSwitch extends Pane {
        private final Rectangle track;
        private final Circle thumb;
        private boolean selected;
        private boolean darkTheme = MainLayout.isDarkTheme();
        private Consumer<Boolean> onToggleListener;

        public ToggleSwitch(boolean initialValue) {
            this.selected = initialValue;
            setPrefSize(36, 20);
            setMinSize(36, 20);
            setMaxSize(36, 20);

            track = new Rectangle(36, 20);
            track.setArcWidth(20);
            track.setArcHeight(20);
            track.setFill(Color.web(selected ? "#1f6feb" : inactiveTrackColor()));
            track.setStroke(Color.web(trackStrokeColor()));
            track.setStrokeWidth(1);

            thumb = new Circle(8);
            thumb.setFill(Color.WHITE);
            thumb.setCenterX(10);
            thumb.setCenterY(10);
            thumb.setTranslateX(selected ? 16 : 0);
            thumb.setEffect(new javafx.scene.effect.DropShadow(3, Color.web("rgba(0,0,0,0.35)")));

            getChildren().addAll(track, thumb);
            setCursor(Cursor.HAND);

            setOnMouseClicked(e -> {
                boolean next = !selected;
                if (onToggleListener != null) {
                    onToggleListener.accept(next);
                }
                setSelected(next);
            });
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean value) {
            if (this.selected == value) return;
            this.selected = value;

            thumb.setTranslateX(selected ? 16 : 0);
            track.setFill(Color.web(selected ? "#1f6feb" : inactiveTrackColor()));
        }

        public void setOnToggle(Consumer<Boolean> listener) {
            this.onToggleListener = listener;
        }

        public void applyTheme(boolean isDark) {
            darkTheme = isDark;
            track.setFill(Color.web(selected ? "#1f6feb" : inactiveTrackColor()));
            track.setStroke(Color.web(trackStrokeColor()));
        }

        private String inactiveTrackColor() {
            return darkTheme ? "#30363d" : "#d0d7de";
        }

        private String trackStrokeColor() {
            return darkTheme ? "#444c56" : "#afb8c1";
        }
    }
}
