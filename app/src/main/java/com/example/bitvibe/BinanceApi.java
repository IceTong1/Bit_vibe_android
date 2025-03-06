package com.example.bitvibe;

import retrofit2.Call;
import retrofit2.http.GET;

public interface BinanceApi {
    @GET("api/v3/ticker/price?symbol=BTCUSDT")
    Call<BinancePriceResponse> getBitcoinPrice();
}