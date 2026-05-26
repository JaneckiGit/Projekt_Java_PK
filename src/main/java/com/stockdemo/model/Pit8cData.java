package com.stockdemo.model;

/**
 * Dane do formularza PIT-8C.
 */
public class Pit8cData {
    // A: Miejsce i cel
    public String urzadSkarbowy = "";
    public boolean celZlozenie = true; // true=złożenie, false=korekta

    // B: Dane składającego
    public String nipSkladajacego = "";
    public boolean osobaFizyczna = true;
    public String nazwaPelna = "";

    // C: Dane podatnika
    public String nipPesel = "";
    public String nazwisko = "";
    public String imie = "";
    public String dataUrodzenia = "";
    public String kraj = "POLSKA";
    public String wojewodztwo = "";
    public String powiat = "";
    public String gmina = "";
    public String ulica = "";
    public String nrDomu = "";
    public String nrLokalu = "";
    public String miejscowosc = "";
    public String kodPocztowy = "";

    // Rok podatkowy
    public int rok = java.time.LocalDate.now().getYear() - 1;
}
