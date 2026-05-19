package com.stockdemo.ui;

import com.stockdemo.model.Instrument;
import com.stockdemo.service.MarketDataService;
import com.stockdemo.service.PortfolioService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

/**
 * Główny layout: po lewej Watchlist, na środku Wykres, po prawej Portfolio.
 */
public class MainLayout extends BorderPane {

    private final MarketDataService marketData;
    private final PortfolioService portfolio;
    private final ChartPanel chartPanel;
    private final PortfolioPanel portfolioPanel;
    private final WatchlistPanel watchlistPanel;

    public MainLayout() {
        this.marketData = new MarketDataService();
        this.portfolio = new PortfolioService();

        // Te trzy panele stworzymy w kolejnych commitach!
        this.chartPanel = new ChartPanel(marketData);
        this.portfolioPanel = new PortfolioPanel(portfolio, chartPanel);
        this.watchlistPanel = new WatchlistPanel(marketData);

        // Połącz kliknięcie na liście z odświeżaniem wykresu
        watchlistPanel.setOnInstrumentSelected(inst -> {
            chartPanel.loadInstrument(inst);
            portfolioPanel.setInstrument(inst);
        });

        // W pętli co każdy "tik" (np. co 2 sekundy):
        // 1. Odśwież listę, 2. Sprawdź StopLoss, 3. Przelicz PnL
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

        this.setLeft(watchlistPanel);
        this.setCenter(centerStack);
        this.setRight(portfolioPanel);
    }
}
