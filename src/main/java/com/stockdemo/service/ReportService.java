package com.stockdemo.service;

import com.stockdemo.model.ClosedPosition;
import com.stockdemo.model.Position;
import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Generuje raporty CSV (historia pozycji) oraz PIT-8C (PDF-like plain text).
 */
public class ReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // ── Export historii zamkniętych pozycji do CSV ──

    public static void exportClosedPositionsCSV(ObservableList<ClosedPosition> positions, Window owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Closed Positions");
        fc.setInitialFileName("closed_positions.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        java.io.File file = fc.showSaveDialog(owner);
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Symbol,Direction,Quantity,Entry Price,Close Price,Realized P&L,Close Date");
            for (ClosedPosition cp : positions) {
                pw.printf(Locale.US, "%s,%s,%.4f,%.2f,%.2f,%.2f,%s%n",
                        cp.instrument().getSymbol(),
                        cp.isLong() ? "BUY" : "SELL",
                        cp.quantity(),
                        cp.entryPrice(),
                        cp.closePrice(),
                        cp.realizedPnl(),
                        cp.closeDate().format(DATE_FMT));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Export historii otwartych pozycji do CSV ──

    public static void exportOpenPositionsCSV(ObservableList<Position> positions, Window owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Open Positions");
        fc.setInitialFileName("open_positions.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        java.io.File file = fc.showSaveDialog(owner);
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Symbol,Direction,Quantity,Entry Price,Current Price,Unrealized P&L,SL,TP");
            for (Position p : positions) {
                pw.printf(Locale.US, "%s,%s,%.4f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                        p.getInstrument().getSymbol(),
                        p.isLong() ? "BUY" : "SELL",
                        p.getQuantity(),
                        p.getEntryPrice(),
                        p.getInstrument().getPrice(),
                        p.getPnl(),
                        p.getStopLoss(),
                        p.getTakeProfit());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Export historii otwartych i zamkniętych pozycji do jednego pliku CSV ──

    public static void exportAllPositionsCSV(ObservableList<Position> openPositions, ObservableList<ClosedPosition> closedPositions, Window owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Positions History");
        fc.setInitialFileName("positions_history.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        java.io.File file = fc.showSaveDialog(owner);
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("=== OPEN POSITIONS ===");
            pw.println("Symbol,Direction,Quantity,Entry Price,Current Price,Unrealized P&L,SL,TP");
            for (Position p : openPositions) {
                pw.printf(Locale.US, "%s,%s,%.4f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                        p.getInstrument().getSymbol(),
                        p.isLong() ? "BUY" : "SELL",
                        p.getQuantity(),
                        p.getEntryPrice(),
                        p.getInstrument().getPrice(),
                        p.getPnl(),
                        p.getStopLoss(),
                        p.getTakeProfit());
            }
            pw.println();
            pw.println("=== CLOSED POSITIONS ===");
            pw.println("Symbol,Direction,Quantity,Entry Price,Close Price,Realized P&L,Close Date");
            for (ClosedPosition cp : closedPositions) {
                pw.printf(Locale.US, "%s,%s,%.4f,%.2f,%.2f,%.2f,%s%n",
                        cp.instrument().getSymbol(),
                        cp.isLong() ? "BUY" : "SELL",
                        cp.quantity(),
                        cp.entryPrice(),
                        cp.closePrice(),
                        cp.realizedPnl(),
                        cp.closeDate().format(DATE_FMT));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Generowanie PIT-8C (formularz tekstowy) ──

    public static void exportPIT8C(ObservableList<ClosedPosition> closedPositions, Window owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export PIT-8C");
        fc.setInitialFileName("PIT-8C_" + LocalDate.now().getYear() + ".txt");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        java.io.File file = fc.showSaveDialog(owner);
        if (file == null) return;

        // Oblicz sumy dla sekcji D formularza
        double totalRevenue = 0;   // Przychody (poz. 23 — odpłatne zbycie papierów wartościowych)
        double totalCost = 0;      // Koszty uzyskania przychodu (poz. 24)

        for (ClosedPosition cp : closedPositions) {
            double sellValue = cp.closePrice() * cp.quantity();
            double buyValue = cp.entryPrice() * cp.quantity();
            totalRevenue += sellValue;
            totalCost += buyValue;
        }

        int year = LocalDate.now().getYear() - 1; // PIT za rok poprzedni
        String today = LocalDate.now().format(DATE_SHORT);

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("╔══════════════════════════════════════════════════════════════════════════╗");
            pw.println("║                              POLTAX                                     ║");
            pw.println("║  POLA JASNE WYPEŁNIA SKŁADAJĄCY, POLA CIEMNE WYPEŁNIA URZĄD.            ║");
            pw.println("║  WYPEŁNIĆ DUŻYMI, DRUKOWANYMI LITERAMI, CZARNYM LUB NIEBIESKIM KOLOREM. ║");
            pw.println("╠══════════════════════════════════════════════════════════════════════════╣");
            pw.println("║                                                                          ║");
            pw.printf( "║  PIT-8C(13)    Informacja o wysokości niektórych dochodów                 ║%n");
            pw.printf( "║                z kapitałów pieniężnych                                    ║%n");
            pw.println("║                                                                          ║");
            pw.printf( "║  4. Rok: %d                                                              ║%n", year);
            pw.println("╠══════════════════════════════════════════════════════════════════════════╣");
            pw.println();
            pw.println("Podstawa prawna:  Art. 39 ust. 3 ustawy z dnia 26 lipca 1991 r. o podatku");
            pw.println("                  dochodowym od osób fizycznych, zwanej dalej \"ustawą\".");
            pw.println("Składający:       Osoba fizyczna prowadząca działalność gospodarczą, osoba");
            pw.println("                  prawna i jej jednostka organizacyjna oraz jednostka");
            pw.println("                  organizacyjna niemająca osobowości prawnej.");
            pw.println("Termin składania: Do końca stycznia roku następującego po roku podatkowym.");
            pw.println();
            pw.println("═══════════════════════════════════════════════════════════════════════════");
            pw.println("A. Miejsce i cel składania informacji");
            pw.println("───────────────────────────────────────────────────────────────────────────");
            pw.println("  5. Urząd skarbowy: ____________________________________________________");
            pw.println("  6. Cel złożenia:   [X] 1. złożenie informacji   [ ] 2. korekta informacji");
            pw.println();
            pw.println("═══════════════════════════════════════════════════════════════════════════");
            pw.println("B. Dane identyfikacyjne składającego");
            pw.println("───────────────────────────────────────────────────────────────────────────");
            pw.println("  7. Rodzaj składającego: [ ] 1. niebędący osobą fizyczną  [ ] 2. osoba fizyczna");
            pw.println("  8. Nazwa pełna / Nazwisko, imię: _____________________________________");
            pw.println();
            pw.println("═══════════════════════════════════════════════════════════════════════════");
            pw.println("C. Dane identyfikacyjne i adres zamieszkania podatnika");
            pw.println("───────────────────────────────────────────────────────────────────────────");
            pw.println("  10. NIP / PESEL: ______________________________________________________");
            pw.println("  11. Nazwisko: _________________  12. Imię: _______________");
            pw.println("  13. Data urodzenia: ______________");
            pw.println("  14. Kraj: _____  15. Województwo: __________  16. Powiat: __________");
            pw.println("  17. Gmina: _____________  18. Ulica: _____________");
            pw.println("  19. Nr domu: ____  20. Nr lokalu: ____");
            pw.println("  21. Miejscowość: _________________  22. Kod pocztowy: __________");
            pw.println();
            pw.println("═══════════════════════════════════════════════════════════════════════════");
            pw.println("D. Informacja o wysokości przychodów i kosztów uzyskania przychodów,");
            pw.println("   o których mowa w art. 30b ust. 2 ustawy");
            pw.println("───────────────────────────────────────────────────────────────────────────");
            pw.println();
            pw.printf( "  %-55s %15s %15s%n", "Rodzaje przychodów", "Przychody", "Koszty");
            pw.printf( "  %-55s %15s %15s%n", "", "(b)", "(c)");
            pw.println("  ─────────────────────────────────────────────────────────────────────────");
            pw.printf( "  1. Odpłatne zbycie papierów wartościowych        23. %12s zł  24. %12s zł%n",
                    fmtPln(totalRevenue), fmtPln(totalCost));
            pw.printf( "  2. Realizacja praw wynikających z pap. wart.     25.              zł  26.              zł%n");
            pw.printf( "  3. Odpłatne zbycie pochodnych instrumentów       27.              zł  28.              zł%n");
            pw.printf( "  4. Odpłatne zbycie udziałów (akcji)              29.              zł  30.              zł%n");
            pw.printf( "  5. Objęcie udziałów za wkład niepieniężny        31.              zł  32.              zł%n");
            pw.printf( "  6. Umorzenie, odkupienie, wykupienie              33.              zł  34.              zł%n");
            pw.println("  ─────────────────────────────────────────────────────────────────────────");
            pw.printf( "  Razem (suma wierszy 1-6)                         35. %12s zł  36. %12s zł%n",
                    fmtPln(totalRevenue), fmtPln(totalCost));
            pw.println();
            pw.println("═══════════════════════════════════════════════════════════════════════════");
            pw.println("E. Informacja o wysokości przychodów niewykazanych w części D");
            pw.println("───────────────────────────────────────────────────────────────────────────");
            pw.println("  Przychody z odpłatnego zbycia pap. wartościowych   37.              zł");
            pw.println();
            pw.println("═══════════════════════════════════════════════════════════════════════════");
            pw.println("F. Podpis osoby upoważnionej do sporządzenia informacji");
            pw.println("───────────────────────────────────────────────────────────────────────────");
            pw.println("  38. Imię, nazwisko, podpis: ___________________________________________");
            pw.println();
            pw.println("═══════════════════════════════════════════════════════════════════════════");
            pw.println("                            Objaśnienia");
            pw.println("───────────────────────────────────────────────────────────────────────────");
            pw.println("  1) W przypadku przedsiębiorstwa w spadku należy podać NIP zmarłego.");
            pw.println("  2) Ilekroć w informacji jest mowa o urzędzie skarbowym — oznacza to");
            pw.println("     urząd skarbowy, do którego jest adresowana informacja.");
            pw.println("  5) W części D nie wykazuje się przychodów (dochodów) wolnych od podatku.");
            pw.println("  6) Składający informację wykazuje przychody z odpłatnego zbycia papierów");
            pw.println("     wartościowych i realizacji praw z nich wynikających.");
            pw.println();
            pw.println("                             Pouczenie");
            pw.println("───────────────────────────────────────────────────────────────────────────");
            pw.println("  Za złożenie informacji nieprawdziwej grozi odpowiedzialność przewidziana");
            pw.println("  w Kodeksie karnym skarbowym.");
            pw.println();
            pw.println("═══════════════════════════════════════════════════════════════════════════");
            pw.println();
            pw.println("--- Szczegółowe zestawienie transakcji ---");
            pw.println();
            pw.printf("%-6s %-12s %-6s %10s %12s %12s %12s   %-16s%n",
                    "Lp.", "Symbol", "Kier.", "Wolumen", "Cena otw.", "Cena zamk.", "Zysk/Strata", "Data zamkn.");
            pw.println("──────────────────────────────────────────────────────────────────────────────────────────");

            int i = 1;
            for (ClosedPosition cp : closedPositions) {
                pw.printf(Locale.US, "%-6d %-12s %-6s %10.4f %12.2f %12.2f %12.2f   %s%n",
                        i++,
                        cp.instrument().getSymbol(),
                        cp.isLong() ? "BUY" : "SELL",
                        cp.quantity(),
                        cp.entryPrice(),
                        cp.closePrice(),
                        cp.realizedPnl(),
                        cp.closeDate().format(DATE_FMT));
            }

            pw.println("──────────────────────────────────────────────────────────────────────────────────────────");
            pw.printf(Locale.US, "RAZEM: Przychody = %.2f zł  |  Koszty = %.2f zł  |  Dochód = %.2f zł%n",
                    totalRevenue, totalCost, totalRevenue - totalCost);
            pw.println();
            pw.printf("Wygenerowano: %s przez Stock Demo — Trading Platform%n", today);
            pw.println("PIT-8C(13)                                                            1/1");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String fmtPln(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }
}
