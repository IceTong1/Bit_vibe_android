package com.example.bitvibe;

import retrofit2.Call;
import retrofit2.http.GET;

public interface CoinGeckoApi {
    @GET("simple/price?ids=bitcoin&vs_currencies=usd")
    Call<BitcoinPriceResponse> getBitcoinPrice();
}