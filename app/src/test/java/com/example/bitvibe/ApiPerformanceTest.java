package com.example.bitvibe;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ApiPerformanceTest {

    // Teste le temps de réponse de l'appel API
    @Test
    public void apiCallResponseTime() {
        // Enregistre le temps de début en nanosecondes
        long startTime = System.nanoTime();

        // Effectue un appel à l'API Binance pour obtenir le prix du Bitcoin
        Call<BinancePriceResponse> call = MainActivity.binanceApi.getBitcoinPrice();
        // Met l'appel en file d'attente pour une exécution asynchrone
        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(Call<BinancePriceResponse> call, Response<BinancePriceResponse> response) {
                // Enregistre le temps de fin en nanosecondes
                long endTime = System.nanoTime();
                // Calcule la durée de l'appel en millisecondes
                long duration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
                // Vérifie si le temps de réponse est inférieur à 1 seconde (1000 ms)
                assertTrue(duration < 1000);
            }
            @Override
            public void onFailure(Call<BinancePriceResponse> call, Throwable t) {
                // Fail if there is an error
                fail("API call failed");
            }
        });
    }
}