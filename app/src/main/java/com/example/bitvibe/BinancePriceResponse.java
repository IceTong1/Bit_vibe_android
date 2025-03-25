package com.example.bitvibe;

import com.google.gson.annotations.SerializedName;

public class BinancePriceResponse {
    @SerializedName("symbol")
    private String symbol;
    @SerializedName("price")
    private String price; // String car Binance renvoie une valeur décimale comme texte

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        return Double.parseDouble(price); // Convertir en double pour l'affichage
    }
}