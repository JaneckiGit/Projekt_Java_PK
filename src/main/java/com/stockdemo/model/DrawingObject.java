package com.stockdemo.model;

/**
 * Obiekty rysowane przez użytkownika na wykresie.
 * Współrzędne przechowywane jako indeksy świec i ceny (nie piksele).
 */
public sealed interface DrawingObject {
    record TrendLine(int candleIdx1, double price1, int candleIdx2, double price2) implements DrawingObject {}
    record HorizontalLine(double price) implements DrawingObject {}
    record Rectangle(int candleIdx1, double price1, int candleIdx2, double price2) implements DrawingObject {}
    record TextLabel(int candleIdx, double price, String text) implements DrawingObject {}
}
