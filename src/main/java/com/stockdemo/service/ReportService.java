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
        if (file == null)
            return;

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
        if (file == null)
            return;

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

    public static void exportAllPositionsCSV(ObservableList<Position> openPositions,
            ObservableList<ClosedPosition> closedPositions, Window owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Positions History");
        fc.setInitialFileName("positions_history.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        java.io.File file = fc.showSaveDialog(owner);
        if (file == null)
            return;

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
}
