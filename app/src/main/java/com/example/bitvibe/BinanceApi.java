package com.example.bitvibe;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface BinanceApi {

    @GET("api/v3/ticker/price") // No specific symbol here
    Call<BinancePriceResponse> getBitcoinPrice(@Query("symbol") String symbol); // Add symbol as a query parameter
}