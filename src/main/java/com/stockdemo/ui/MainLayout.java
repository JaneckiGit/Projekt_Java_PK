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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.stage.Window;
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

    // Settings sidebar state
    private Pane settingsWrapper;

    // Dane do formularza PIT-8C zachowane w pamięci sesji
    private final Pit8cData pit8cData = new Pit8cData();

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
        // Podłącz przycisk ustawień (⚙)
        portfolioPanel.setOnSettingsRequested(this::toggleSettings);
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

        double panelWidth = 300;

        VBox panel = new VBox(0);
        panel.getStyleClass().add("settings-panel");
        panel.setMinWidth(panelWidth);
        panel.setPrefWidth(panelWidth);
        panel.setMaxWidth(panelWidth);

        // Header
        Label title = new Label("⚙  Settings");
        title.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 16px; -fx-font-weight: 700;");
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("modal-close-btn");
        closeBtn.setOnAction(e -> closeSettings());
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox header = new HBox(8, title, sp, closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 16, 12, 16));
        header.setStyle("-fx-border-color: transparent transparent #21262d transparent; -fx-border-width: 0 0 1 0;");

        // Items container
        VBox items = new VBox(2);
        items.setPadding(new Insets(8, 8, 8, 8));

        // Section: Export
        Label exportSection = new Label("EXPORT");
        exportSection.getStyleClass().add("section-title");
        exportSection.setPadding(new Insets(8, 8, 4, 8));

        items.getChildren().addAll(
                exportSection,
                makeSettingsItem("📋  Export Positions History", "Download open & closed positions in one CSV", () -> {
                    Window w = getScene() != null ? getScene().getWindow() : null;
                    ReportService.exportAllPositionsCSV(portfolio.openPositions, portfolio.closedPositions, w);
                }),
                makeSettingsItem("📄  Download PIT-8C (PDF)", "Fill form and download tax PDF", () -> {
                    closeSettings();
                    new ModalOverlay("PIT-8C Tax Form Details", buildPit8cFormContent()).showOn(appRoot);
                })
        );

        // Separator
        Separator sep1 = new Separator();
        sep1.setStyle("-fx-padding: 4 8;");
        items.getChildren().add(sep1);

        // Section: Account
        Label accountSection = new Label("ACCOUNT");
        accountSection.getStyleClass().add("section-title");
        accountSection.setPadding(new Insets(8, 8, 4, 8));

        items.getChildren().addAll(
                accountSection,
                makeSettingsItem("🔄  Reset Balance", "Reset portfolio to $100,000.00", () -> {
                    closeSettings();
                    com.stockdemo.service.PortfolioPersistence.reset(portfolio);
                    portfolioPanel.refresh();
                }),
                makeSettingsItem("💰  Set New Balance", "Change account value", () -> {
                    closeSettings();
                    new ModalOverlay("Set Balance", buildBalanceContent(portfolio)).showOn(appRoot);
                })
        );

        // Separator
        Separator sep2 = new Separator();
        sep2.setStyle("-fx-padding: 4 8;");
        items.getChildren().add(sep2);

        // Section: App
        Label appSection = new Label("APPLICATION");
        appSection.getStyleClass().add("section-title");
        appSection.setPadding(new Insets(8, 8, 4, 8));

        items.getChildren().addAll(
                appSection,
                makeSettingsItem("⚙  Preferences", "Sound alerts, dark mode, about", () -> {
                    closeSettings();
                    new ModalOverlay("Settings", buildSettingsContent()).showOn(appRoot);
                }),
                makeSettingsItem("🚪  Exit Application", "Save and quit", () -> {
                    com.stockdemo.service.PortfolioPersistence.save(portfolio);
                    Platform.exit();
                })
        );

        ScrollPane scroll = new ScrollPane(items);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        panel.getChildren().addAll(header, scroll);

        // Wrapper z półprzezroczystym tłem
        Pane wrapper = new Pane();
        wrapper.setStyle("-fx-background-color: rgba(0,0,0,0.35);");
        wrapper.setPickOnBounds(true);
        wrapper.setOnMousePressed(e -> {
            if (e.getTarget() == wrapper) closeSettings();
        });

        // Panel ustawiamy po prawej stronie
        wrapper.getChildren().add(panel);
        wrapper.layoutBoundsProperty().addListener((o, ov, nv) -> {
            panel.setLayoutX(nv.getWidth() - panelWidth);
            panel.setPrefHeight(nv.getHeight());
        });

        appRoot.getChildren().add(wrapper);
        settingsWrapper = wrapper;

        // Po dodaniu — wymiar jest znany
        Platform.runLater(() -> {
            double h = appRoot.getHeight();
            panel.setPrefHeight(h);
            panel.setLayoutX(appRoot.getWidth() - panelWidth);

            // Slide-in animation
            panel.setTranslateX(panelWidth);
            TranslateTransition slide = new TranslateTransition(Duration.millis(200), panel);
            slide.setFromX(panelWidth);
            slide.setToX(0);
            slide.setInterpolator(Interpolator.EASE_OUT);

            FadeTransition fade = new FadeTransition(Duration.millis(200), wrapper);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setInterpolator(Interpolator.EASE_OUT);

            slide.play();
            fade.play();
        });
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
        slide.setToX(300);
        slide.setInterpolator(Interpolator.EASE_IN);

        FadeTransition fade = new FadeTransition(Duration.millis(180), wrapper);
        fade.setToValue(0);
        fade.setInterpolator(Interpolator.EASE_IN);

        slide.setOnFinished(e -> appRoot.getChildren().remove(wrapper));
        slide.play();
        fade.play();
    }

    private VBox makeSettingsItem(String label, String description, Runnable action) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 13px; -fx-font-weight: 600;");

        Label desc = new Label(description);
        desc.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");

        VBox item = new VBox(2, lbl, desc);
        item.getStyleClass().add("settings-item");
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
                    new ModalOverlay("Statistics", buildStatisticsContent(portfolioPanel)).showOn(appRoot);
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

    /** Modal: ustawienia (preferencje aplikacji) */
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
        generateBtn.setStyle(
                "-fx-background-color: #238636; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 12 16; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 14px;"
        );
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
        title.setStyle("-fx-text-fill: #58a6ff; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 0;");
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
}
