package com.example.bitvibe;

import com.google.gson.annotations.SerializedName;

public class BitcoinPriceResponse {
    @SerializedName("bitcoin")
    private BitcoinData bitcoin;

    public BitcoinData getBitcoin() {
        return bitcoin;
    }

    public static class BitcoinData {
        @SerializedName("usd")
        private double usd;

        public double getUsd() {
            return usd;
        }
    }
}