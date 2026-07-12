<div align="center">

# 📈 Stock Market Demo + AI Analyzer

**Desktopowy symulator giełdy z danymi live, wykresami świecowymi i analizą AI formacji świecowych**

![Java](https://img.shields.io/badge/Java-17+-ED8B00?logo=openjdk&logoColor=white)
![JavaFX](https://img.shields.io/badge/JavaFX-Desktop_UI-1B6AC6?logo=openjdk&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36?logo=apachemaven&logoColor=white)
![Apache PDFBox](https://img.shields.io/badge/Apache_PDFBox-PIT--8C_reports-D22128?logo=apache&logoColor=white)
![Status](https://img.shields.io/badge/status-active-success)

*Projekt zrealizowany w ramach studiów na Politechnice Krakowskiej*

</div>

---

## 🎯 O projekcie

Aplikacja desktopowa symulująca platformę do handlu giełdowego. Pobiera **dane rynkowe na żywo**
z API Yahoo Finance i Binance, rysuje interaktywne wykresy (liniowe i świecowe OHLC) w wielu
zakresach czasowych, a wbudowany **analizator AI** wykrywa formacje świecowe i przewiduje ruch rynku.
Konto demo pozwala bezpiecznie ćwiczyć zlecenia KUP/SPRZEDAJ ze Stop Lossem i Take Profitem,
a moduł raportowy generuje **deklarację podatkową PIT-8C w PDF**.

## ✨ Funkcje

| Funkcja | Status |
|---|:---:|
| Lista instrumentów (indeksy, akcje, krypto, surowce) | ✅ |
| Dane live z Yahoo Finance + Binance | ✅ |
| Wykres liniowy | ✅ |
| Wykres świecowy (OHLC Candlestick) | ✅ |
| Zakresy czasowe: 1D / 1T / 1M / 3M / 6M / YTD / 1R / 5R | ✅ |
| Konto demo ($100 000 startowego balansu) | ✅ |
| Zlecenia KUP / SPRZEDAJ + zamykanie pozycji | ✅ |
| Stop Loss / Take Profit (auto-trigger) | ✅ |
| Auto-odświeżanie co 30 s | ✅ |
| 🤖 AI Analyzer — wykrywanie formacji świecowych i predykcja ruchu | ✅ |
| Generowanie raportu podatkowego **PIT-8C (PDF)** z filtrowaniem po roku i osobnym PnL dla CFD | ✅ |
| Narzędzia rysowania na wykresie | ✅ |

## 🖥️ Instrumenty w watchliście

`^DJI` Dow Jones · `^GSPC` S&P 500 · `^IXIC` NASDAQ · `AAPL` Apple · `MSFT` Microsoft ·
`GOOGL` Alphabet · `AMZN` Amazon · `NVDA` NVIDIA · `TSLA` Tesla ·
`BTC-USD` Bitcoin · `ETH-USD` Ethereum · `GC=F` Gold Futures

## 🚀 Uruchomienie

**Wymagania:** Java 17+, Maven 3.8+, połączenie z internetem

```bash
git clone https://github.com/JaneckiGit/Projekt_Java_PK.git
cd Projekt_Java_PK
mvn clean javafx:run
```

## 🏗️ Architektura

```
src/main/java/com/stockdemo/
├── MainApp.java        # Punkt wejścia aplikacji (JavaFX)
├── api/                # Klienci API — YahooFinanceApi, BinanceApi
├── analyzer/           # 🤖 AI: CandlePatternDetector, AnalyzerService, wskaźniki
├── model/              # Domenowe: Candle, Position, Instrument, Pit8cData…
├── service/            # Logika: MarketDataService, PortfolioService,
│                       #         Pit8cPdfGenerator, ReportService, persystencja
└── ui/                 # Widoki: ChartPanel, WatchlistPanel, PortfolioPanel,
                        #         AnalyzerWindow, MainLayout
```

**Stack:** Java 17 · JavaFX (controls + FXML) · Apache PDFBox · org.json · Maven

## 🗺️ Roadmapa

- [ ] Historia transakcji (tabela zamkniętych zleceń)
- [ ] Powiadomienia dźwiękowe (SL/TP triggered)
- [ ] Wskaźniki techniczne (RSI, MACD, MA)
- [ ] Tryb live (WebSocket / krótki polling)
- [ ] Wiele zakładek / okien dla różnych instrumentów

## 👥 Autorzy

| | |
|---|---|
| [**Mateusz Janecki**](https://github.com/JaneckiGit) | 🌐 [janeckimateusz.com](https://janeckimateusz.com) · [LinkedIn](https://www.linkedin.com/in/mateusz-j-621b1a196/) |
| [**Paweł Drabik**](https://github.com/LiIWind) | |
