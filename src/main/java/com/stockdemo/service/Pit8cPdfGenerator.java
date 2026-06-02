package com.stockdemo.service;

import com.stockdemo.model.AssetType;
import com.stockdemo.model.ClosedPosition;
import com.stockdemo.model.Pit8cData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Generuje PDF formularza PIT-8C zgodnego z wzorem urzędowym.
 * Współrzędne: y=0 na górze strony, rośnie w dół.
 */
public class Pit8cPdfGenerator {

    private static final float PW = 595.28f, PH = 841.89f;
    private static final float ML = 30, MR = 30;
    private static final float CW = PW - ML - MR; // ~535

    // Font Paths
    private static final String[] REGULAR_FONT_PATHS = {
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"
    };
    private static final String[] BOLD_FONT_PATHS = {
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "/Library/Fonts/Arial Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"
    };

    // Color tones
    private static final float COLOR_GRAY_FILL = 0.88f;
    private static final float COLOR_BLACK = 0f;

    // Header & Version Strings
    private static final String PIT_8C_VERSION = "PIT-8C(13)";
    private static final String POLTAX_TITLE = "POLTAX";
    private static final String POLTAX_SUBTITLE = "POLA JASNE WYPELNIA SKLADAJACY, POLA CIEMNE WYPELNIA URZAD. WYPELNIC DUZYMI, DRUKOWANYMI LITERAMI, CZARNYM LUB NIEBIESKIM KOLOREM.";
    private static final String POLTAX_URL = "Skladanie w wersji elektronicznej: www.podatki.gov.pl";

    // Currencies
    private static final String CURRENCY_ZL = "zl";
    private static final String CURRENCY_GR = "gr";

    private PDDocument doc;
    private PDFont f, fb; // regular, bold
    private PDPageContentStream cs;
    private boolean ttf; // czy font obsługuje polskie znaki

    public void generate(Pit8cData d, List<ClosedPosition> positions, File out) throws IOException {
        doc = new PDDocument();
        loadFonts();

        double stockRev = 0, stockCost = 0;
        double cfdRev = 0, cfdCost = 0;

        for (ClosedPosition cp : positions) {
            // 1. Filtrowanie po roku podatkowym
            if (cp.closeDate().getYear() != d.rok) {
                continue;
            }

            // 2. Kwalifikacja według typu instrumentu
            AssetType type = cp.instrument().getType();
            if (type == AssetType.STOCK) {
                stockRev += cp.closePrice() * cp.quantity();
                stockCost += cp.entryPrice() * cp.quantity();
            } else if (type == AssetType.CFD) {
                // CFD: rozliczenie zysków/strat netto z zamkniętych pozycji
                double pnl = cp.realizedPnl();
                if (pnl > 0) {
                    cfdRev += pnl;
                } else if (pnl < 0) {
                    cfdCost += Math.abs(pnl);
                }
            }
            // CRYPTO jest całkowicie wykluczone z PIT-8C (rozliczane na PIT-38 część E przez podatnika)
        }

        page1(d, stockRev, stockCost, cfdRev, cfdCost);
        page2(d);
        doc.save(out);
        doc.close();
    }

    // ── Fonts ──

    private void loadFonts() throws IOException {
        f = tryLoad(REGULAR_FONT_PATHS);
        fb = tryLoad(BOLD_FONT_PATHS);
        if (f != null && fb != null) {
            ttf = true;
            return;
        }
        f = PDType1Font.HELVETICA;
        fb = PDType1Font.HELVETICA_BOLD;
        ttf = false;
    }

    private PDFont tryLoad(String[] paths) {
        for (String p : paths) {
            File file = new File(p);
            if (file.exists()) {
                try { return PDType0Font.load(doc, file); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private String s(String t) {
        if (t == null) {
            return "";
        }
        if (ttf) {
            return t;
        }
        return t.replace('ą','a').replace('Ą','A').replace('ć','c').replace('Ć','C')
                .replace('ę','e').replace('Ę','E').replace('ł','l').replace('Ł','L')
                .replace('ń','n').replace('Ń','N').replace('ś','s').replace('Ś','S')
                .replace('ź','z').replace('Ź','Z').replace('ż','z').replace('Ż','Z');
    }

    // ── Helpers (y=0 top, rosnące w dół) ──

    private void rect(float x, float y, float w, float h) throws IOException {
        cs.addRect(x, PH - y - h, w, h); cs.stroke();
    }

    private void grayBar(float x, float y, float w, float h) throws IOException {
        cs.setNonStrokingColor(COLOR_GRAY_FILL);
        cs.addRect(x, PH - y - h, w, h); cs.fill();
        cs.setNonStrokingColor(COLOR_BLACK);
        cs.addRect(x, PH - y - h, w, h); cs.stroke();
    }

    private void txt(float x, float y, String text, PDFont font, float size) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }
        cs.beginText(); cs.setFont(font, size);
        cs.newLineAtOffset(x, PH - y);
        cs.showText(s(text)); cs.endText();
    }

    private void checkbox(float x, float y, boolean checked) throws IOException {
        rect(x, y, 8, 8);
        if (checked) {
            txt(x + 1.5f, y + 6.5f, "X", fb, 7);
        }
    }

    private String fmtZl(double v) {
        if (v == 0) {
            return "";
        }
        long gr = Math.round(v * 100);
        long zl = gr / 100;
        long g = Math.abs(gr % 100);
        return String.format(Locale.US, "%,d", zl) + "    " + String.format("%02d", g);
    }

    // ── Refactoring Helper Methods ──

    private void drawPoltaxHeader(float y) throws IOException {
        grayBar(ML, y, CW, 16);
        txt(ML + 4, y + 11, POLTAX_TITLE, fb, 9);
        txt(ML + 65, y + 6, POLTAX_SUBTITLE, f, 4);
        txt(PW - MR - 130, y + 12, POLTAX_URL, f, 5);
    }

    private void drawSectionHeader(float y, String title) throws IOException {
        grayBar(ML, y, CW, 13);
        txt(ML + 4, y + 10, title, fb, 7.5f);
    }

    private void drawField(float x, float y, float w, float h, String label, String value) throws IOException {
        drawField(x, y, w, h, label, value, 5.5f, 8f, 7f, 17f);
    }

    private void drawField(float x, float y, float w, float h, String label, String value, float labelSize, float valueSize) throws IOException {
        drawField(x, y, w, h, label, value, labelSize, valueSize, 7f, 17f);
    }

    private void drawField(float x, float y, float w, float h, String label, String value, float labelSize, float valueSize, float labelYOffset, float valueYOffset) throws IOException {
        rect(x, y, w, h);
        txt(x + 3, y + labelYOffset, label, f, labelSize);
        txt(x + 3, y + valueYOffset, value, f, valueSize);
    }

    private void drawLabeledCheckbox(float x, float y, String label, boolean checked, float labelSize) throws IOException {
        checkbox(x, y + 5, checked);
        txt(x + 11, y + 12, label, f, labelSize);
    }

    // ── Strona 1 ──

    private void page1(Pit8cData d, double stockRev, double stockCost, double cfdRev, double cfdCost) throws IOException {
        PDPage pg = new PDPage(PDRectangle.A4);
        doc.addPage(pg);
        cs = new PDPageContentStream(doc, pg);
        cs.setLineWidth(0.4f);

        float y = 25;

        // ─── POLTAX Header ───
        drawPoltaxHeader(y);
        y += 18;

        // ─── Wiersz: NIP | Nr dok. | Status ───
        float c1 = CW * 0.4f, c2 = CW * 0.35f, c3 = CW * 0.25f;
        drawField(ML, y, c1, 24, "1. Identyfikator podatkowy NIP skladajacego", d.nipSkladajacego, 5.5f, 9f, 8f, 19f);
        drawField(ML + c1, y, c2, 24, "2. Nr dokumentu", null);
        drawField(ML + c1 + c2, y, c3, 24, "3. Status", null);
        y += 27;

        // ─── PIT-8C Tytuł ───
        txt(ML + 4, y + 14, "PIT-8C", fb, 16);
        y += 20;
        txt(ML + CW * 0.12f, y + 10, "Informacja o wysokosci niektorych dochodow z kapitalow pienieznych", fb, 9);
        y += 16;

        // Rok
        float yrX = ML + CW * 0.55f;
        txt(yrX, y + 10, "za rok", f, 8);
        rect(yrX + 40, y, 80, 22);
        txt(yrX + 43, y + 8, "4. Rok", f, 5.5f);
        txt(yrX + 55, y + 18, String.valueOf(d.rok), fb, 11);
        y += 26;

        // ─── Blok prawny ───
        rect(ML, y, CW, 52);
        float lx = ML + 4;
        txt(lx, y + 8, "Podstawa prawna:", fb, 5.5f);
        txt(lx + 65, y + 8, "Art. 39 ust. 3 ustawy z dnia 26 lipca 1991 r. o podatku dochodowym od osob fizycznych, zwanej dalej \"ustawa\".", f, 5.5f);
        txt(lx, y + 15, "Skladajacy:", fb, 5.5f);
        txt(lx + 65, y + 15, "Osoba fizyczna prowadzaca dzialalnosc gospodarcza, osoba prawna i jej jednostka organizacyjna oraz jednostka", f, 5.5f);
        txt(lx + 65, y + 21, "organizacyjna niemajaca osobowosci prawnej.", f, 5.5f);
        txt(lx, y + 28, "Termin skladania:", fb, 5.5f);
        txt(lx + 65, y + 28, "Do konca stycznia roku nastepujacego po roku podatkowym — dla informacji skladanych urzedom skarbowym;", f, 5.5f);
        txt(lx + 65, y + 34, "do konca lutego roku nastepujacego po roku podatkowym — dla informacji przesylanych podatnikom.", f, 5.5f);
        txt(lx, y + 42, "Otrzymuje:", fb, 5.5f);
        txt(lx + 65, y + 42, "Podatnik oraz urzad skarbowy wedlug miejsca zamieszkania podatnika.", f, 5.5f);
        y += 56;

        // ═══ SEKCJA A ═══
        drawSectionHeader(y, "A. Miejsce i cel skladania informacji");
        y += 15;

        drawField(ML, y, CW, 22, "5. Urzad skarbowy, do ktorego jest adresowana informacja", d.urzadSkarbowy, 5.5f, 9f, 8f, 18f);
        y += 24;

        rect(ML, y, CW, 18);
        txt(ML + 3, y + 7, "6. Cel zlozenia formularza (zaznaczyc wlasciwy kwadrat):", f, 5.5f);
        drawLabeledCheckbox(ML + CW * 0.38f, y, "1. zlozenie informacji", d.celZlozenie, 6.5f);
        drawLabeledCheckbox(ML + CW * 0.65f, y, "2. korekta informacji", !d.celZlozenie, 6.5f);
        y += 20;

        // ═══ SEKCJA B ═══
        drawSectionHeader(y, "B. Dane identyfikacyjne skladajacego");
        y += 15;

        txt(ML + 30, y + 6, "* - dotyczy skladajacego niebedacego osoba fizyczna", f, 5);
        txt(ML + CW * 0.55f, y + 6, "** - dotyczy skladajacego bedacego osoba fizyczna", f, 5);
        y += 8;

        rect(ML, y, CW, 18);
        txt(ML + 3, y + 7, "7. Rodzaj skladajacego (zaznaczyc wlasciwy kwadrat):", f, 5.5f);
        drawLabeledCheckbox(ML + CW * 0.38f, y, "1. skladajacy niebedacy osoba fizyczna", !d.osobaFizyczna, 6f);
        drawLabeledCheckbox(ML + CW * 0.72f, y, "2. osoba fizyczna", d.osobaFizyczna, 6f);
        y += 20;

        drawField(ML, y, CW, 20, "8. Nazwa pelna*", d.nazwaPelna);
        y += 22;

        drawField(ML, y, CW, 20, "9. Nazwisko, pierwsze imie, data urodzenia**", d.nazwisko + ", " + d.imie + ", " + d.dataUrodzenia);
        y += 22;

        // ═══ SEKCJA C ═══
        drawSectionHeader(y, "C. Dane identyfikacyjne i adres zamieszkania podatnika");
        y += 15;

        drawField(ML, y, CW, 20, "10. Identyfikator podatkowy NIP / numer PESEL", d.nipPesel, 5.5f, 9f);
        y += 22;

        // 11-13
        float w11 = CW * 0.38f, w12 = CW * 0.3f, w13 = CW * 0.32f;
        drawField(ML, y, w11, 20, "11. Nazwisko", d.nazwisko);
        drawField(ML + w11, y, w12, 20, "12. Pierwsze imie", d.imie);
        drawField(ML + w11 + w12, y, w13, 20, "13. Data urodzenia (dzien – miesiac – rok)", d.dataUrodzenia, 4.5f, 8f);
        y += 22;

        // 14-16
        float w14 = CW * 0.22f, w15 = CW * 0.42f, w16 = CW * 0.36f;
        drawField(ML, y, w14, 20, "14. Kraj", d.kraj);
        drawField(ML + w14, y, w15, 20, "15. Wojewodztwo", d.wojewodztwo);
        drawField(ML + w14 + w15, y, w16, 20, "16. Powiat", d.powiat);
        y += 22;

        // 17-20
        float w17 = CW * 0.28f, w18 = CW * 0.40f, w19 = CW * 0.16f, w20 = CW * 0.16f;
        drawField(ML, y, w17, 20, "17. Gmina", d.gmina);
        drawField(ML + w17, y, w18, 20, "18. Ulica", d.ulica);
        drawField(ML + w17 + w18, y, w19, 20, "19. Nr domu", d.nrDomu);
        drawField(ML + w17 + w18 + w19, y, w20, 20, "20. Nr lokalu", d.nrLokalu);
        y += 22;

        // 21-22
        float w21 = CW * 0.7f, w22 = CW * 0.3f;
        drawField(ML, y, w21, 20, "21. Miejscowosc", d.miejscowosc);
        drawField(ML + w21, y, w22, 20, "22. Kod pocztowy", d.kodPocztowy);
        y += 24;

        // ═══ SEKCJA D ═══
        grayBar(ML, y, CW, 16);
        txt(ML + 4, y + 7, "D. Informacja o wysokosci przychodow i kosztow uzyskania przychodow, o ktorych mowa w art. 30b ust. 2", fb, 6.5f);
        txt(ML + 18, y + 13, "ustawy", fb, 6.5f);
        y += 18;

        // Nagłówek tabeli
        float dW = CW * 0.54f; // kolumna opisu
        float pW = CW * 0.23f; // przychody
        float kW = CW * 0.23f; // koszty

        rect(ML, y, dW, 26);
        rect(ML + dW, y, pW, 26);
        rect(ML + dW + pW, y, kW, 26);
        txt(ML + dW * 0.25f, y + 10, "Rodzaje przychodow", fb, 6.5f);
        txt(ML + dW * 0.46f, y + 20, "a", f, 6);
        txt(ML + dW + pW * 0.3f, y + 10, "Przychody", fb, 6.5f);
        txt(ML + dW + pW * 0.47f, y + 20, "b", f, 6);
        txt(ML + dW + pW + kW * 0.07f, y + 10, "Koszty uzyskania przychodow", fb, 5.5f);
        txt(ML + dW + pW + kW * 0.47f, y + 20, "c", f, 6);
        y += 28;

        double totalRev = stockRev + cfdRev;
        double totalCost = stockCost + cfdCost;

        // Wiersze tabeli
        Object[][] rows = {
            {"1. Odplatne zbycie papierow wartosciowych", "23.", stockRev, "24.", stockCost},
            {"2. Realizacja praw wynikajacych z papierow wartosciowych", "25.", 0.0, "26.", 0.0},
            {"3. Odplatne zbycie pochodnych instrumentow finansowych oraz realizacja praw z nich", "27.", cfdRev, "28.", cfdCost},
            {"4. Odplatne zbycie niebedacych papierami wartosciowymi udzialow (akcji)", "29.", 0.0, "30.", 0.0},
            {"5. Objecie udzialow (akcji) w spolkach albo wkladow w spoldzielniach za wklad", "31.", 0.0, "32.", 0.0},
            {"6. Umorzenie, odkupienie, wykupienie albo unicestwienie w inny sposob tytulow", "33.", 0.0, "34.", 0.0},
            {"Razem", "35.", totalRev, "36.", totalCost}
        };

        for (int i = 0; i < rows.length; i++) {
            Object[] r = rows[i];
            float rh = 20;
            rect(ML, y, dW, rh);
            rect(ML + dW, y, pW, rh);
            rect(ML + dW + pW, y, kW, rh);

            txt(ML + 3, y + 8, (String) r[0], i == rows.length - 1 ? fb : f, 5.5f);
            txt(ML + dW + 3, y + 8, (String) r[1], fb, 6);
            String rv = fmtZl((double) r[2]);
            if (!rv.isEmpty()) {
                txt(ML + dW + pW * 0.3f, y + 16, rv, f, 7);
            }
            txt(ML + dW + pW + 3, y + 8, (String) r[3], fb, 6);
            String cv = fmtZl((double) r[4]);
            if (!cv.isEmpty()) {
                txt(ML + dW + pW + kW * 0.3f, y + 16, cv, f, 7);
            }
            
            // zł / gr labels
            txt(ML + dW + pW * 0.7f, y + 16, CURRENCY_ZL, f, 5);
            txt(ML + dW + pW * 0.88f, y + 16, CURRENCY_GR, f, 5);
            txt(ML + dW + pW + kW * 0.7f, y + 16, CURRENCY_ZL, f, 5);
            txt(ML + dW + pW + kW * 0.88f, y + 16, CURRENCY_GR, f, 5);
            y += rh;
        }

        // Footer
        y = PH - 22;
        txt(PW - MR - 80, y, PIT_8C_VERSION, fb, 7);
        txt(PW - MR - 20, y, "1/2", f, 7);

        cs.close();
    }

    // ── Strona 2 ──

    private void page2(Pit8cData d) throws IOException {
        PDPage pg = new PDPage(PDRectangle.A4);
        doc.addPage(pg);
        cs = new PDPageContentStream(doc, pg);
        cs.setLineWidth(0.4f);

        float y = 25;

        // POLTAX Header
        drawPoltaxHeader(y);
        y += 20;

        // ═══ SEKCJA E ═══
        drawSectionHeader(y, "E. Informacja o wysokosci przychodow niewykazanych w czesci D");
        y += 15;

        rect(ML, y, CW * 0.7f, 22);
        rect(ML + CW * 0.7f, y, CW * 0.3f, 22);
        txt(ML + 3, y + 8, "Przychody z odplatnego zbycia papierow wartosciowych", f, 5.5f);
        
        float col2X = ML + CW * 0.7f;
        txt(col2X + 3, y + 8, "37.", fb, 6);
        txt(col2X + CW * 0.17f, y + 17, CURRENCY_ZL, f, 5);
        txt(col2X + CW * 0.26f, y + 17, CURRENCY_GR, f, 5);
        y += 26;

        // ═══ SEKCJA F ═══
        drawSectionHeader(y, "F. Podpis osoby upowaznionej do sporzadzenia informacji");
        y += 15;

        rect(ML, y, CW, 40);
        txt(ML + 3, y + 8, "38. Imie, nazwisko, podpis albo nadruk z imieniem, nazwiskiem oraz stanowiskiem sluzbowym", f, 5.5f);
        y += 46;

        // ─── Objaśnienia ───
        txt(ML + CW * 0.38f, y + 10, "Objasnienia", fb, 9);
        y += 18;

        String[] notes = {
            "1)   W przypadku przedsiebiorstwa w spadku nalezy podac identyfikator podatkowy NIP zmarlego przedsiebiorcy.",
            "2)   Ilekroc w informacji jest mowa o urzedzie skarbowym, w tym urzedzie skarbowym, do ktorego jest adresowana informacja — oznacza to urzad skarbowy, przy",
            "      pomocy ktorego wlasciwy dla podatnika naczelnik urzedu skarbowego wykonuje swoje zadania.",
            "3)   Zgodnie z art. 81 ustawy z dnia 29 sierpnia 1997 r. — Ordynacja podatkowa, zwanej dalej \"Ordynacja podatkowa\".",
            "4)   W przypadku przedsiebiorstwa w spadku nalezy podac dane identyfikacyjne zmarlego przedsiebiorcy.",
            "5)   W czesci D nie wykazuje sie przychodow (dochodow) wolnych od podatku dochodowego na podstawie przepisow ustawy - z wyjatkiem dochodow, o ktorych mowa",
            "      w art. 21 ust. 1 pkt 105a ustawy - oraz przychodow (dochodow), od ktorych na podstawie Ordynacji podatkowej zaniechano poboru podatku.",
            "6)   Skladajacy informacje wykazuje przychody z odplatnego zbycia papierow wartosciowych i realizacji praw z nich wynikajacych, tylko co do ktorych nie jest",
            "      w stanie okreslic, czy podlegaja opodatkowaniu, czy nie podlegaja opodatkowaniu na podstawie art. 19 ustawy z dnia 12 listopada 2003 r. o zmianie ustawy",
            "      o podatku dochodowym od osob fizycznych oraz niektorych innych ustaw (Dz. U. poz. 1956, z pozn. zm.); przychodow niepodlegajacych opodatkowaniu podatnik",
            "      nie wykazuje w zeznaniu podatkowym."
        };
        for (String note : notes) {
            txt(ML + 3, y + 8, note, f, 5.5f);
            y += 9;
        }
        y += 10;

        // ─── Pouczenie ───
        txt(ML + CW * 0.4f, y + 10, "Pouczenie", fb, 9);
        y += 18;
        txt(ML + 3, y + 8, "Za zlozenie informacji nieprawdziwej grozi odpowiedzialnosc przewidziana w Kodeksie karnym skarbowym.", f, 6);
        y += 12;

        // Footer
        y = PH - 30;
        rect(ML, y, 65, 14);
        txt(ML + 4, y + 10, PIT_8C_VERSION, fb, 7);
        txt(ML + 55, y + 10, "2/2", f, 7);

        cs.close();
    }
}
