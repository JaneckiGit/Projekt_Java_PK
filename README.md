# 📈 Stock Market Demo — Java Desktop App

Desktopowa aplikacja giełdowa napisana w **JavaFX + JFreeChart**.

# Autorzy
- [Mateusz Janecki](https://github.com/JaneckiGit)
- [Paweł Drabik](https://github.com/LiIWind)

---

## Struktura projektu

```
src/main/java/com/stockdemo/
├── MainApp.java              
├── api/
│   └── 
├── model/
│   ├── 
│   ├── 
│   └── 
├── service/
│   └── 
└── ui/
    ├── 
    ├── 
    └── 
```

---

## Wymagania

- **Java 17+**
- **Maven 3.8+**
- Połączenie z internetem

---

## Uruchomienie


Lub w IntelliJ:
1. File → Open → wybierz `pom.xml` → Open as Project
2. Poczekaj aż Maven pobierze zależności
3. Uruchom klasę `MainApp`

---

## Funkcje

| Feature | Status |
|---|---|
| Lista instrumentów (indeksy, akcje, krypto) | ✅ |
| Wykres liniowy | ✅ |
| Wykres świecowy (OHLC Candlestick) | ✅ |
| Zakresy czasowe: 1D / 1T / 1M / 3M / 6M / YTD / 1R / 5R | ✅ |
| Konto demo ($100,000 startowy balans) | ✅ |
| Zlecenia KUP / SPRZEDAJ | ✅ |
| Stop Loss / Take Profit (auto-trigger) | ✅ |
| Zamykanie pozycji | ✅ |
| Auto-refresh co 30s | ✅ |

---

## Instrumenty w watchliście

- `^DJI` — Dow Jones Industrial
- `^GSPC` — S&P 500
- `^IXIC` — NASDAQ
- `AAPL` — Apple
- `MSFT` — Microsoft
- `GOOGL` — Alphabet
- `AMZN` — Amazon
- `NVDA` — NVIDIA
- `TSLA` — Tesla
- `BTC-USD` — Bitcoin
- `ETH-USD` — Ethereum
- `GC=F` — Gold Futures

---

## Road Mapa (kolejne kroki)

- [ ] Historia transakcji (tabela closed orders)
- [ ] Eksport historii do CSV
- [ ] Powiadomienia dźwiękowe (SL/TP triggered)
- [ ] Wskaźniki techniczne (RSI, MACD, MA)
- [ ] Tryb live (WebSocket / krótki polling)
- [ ] Wiele zakładek / okien dla różnych instrumentów
