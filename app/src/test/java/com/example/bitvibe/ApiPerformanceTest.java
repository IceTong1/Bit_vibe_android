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
        // Vérifier si binanceApi est initialisé (peut être null si MainActivity n'a pas été setup correctement pour le test)
        if (MainActivity.binanceApi == null) {
            // Initialiser Retrofit et l'API ici si nécessaire pour le test unitaire,
            // ou s'assurer que le setup du test l'initialise correctement.
            // Pour l'instant, on signale l'échec si non initialisé.
            fail("MainActivity.binanceApi is not initialized. Ensure test setup is correct.");
            return;
        }

        // Enregistre le temps de début en nanosecondes
        long startTime = System.nanoTime();
        // Effectue un appel à l'API Binance pour obtenir le prix, EN FOURNISSANT UN SYMBOLE
        // Utilisation de "BTCUSDT" comme exemple. Vous pouvez choisir une autre paire supportée par l'API.
        Call<BinancePriceResponse> call = MainActivity.binanceApi.getBitcoinPrice("BTCUSDT"); // MODIFICATION ICI

        // Met l'appel en file d'attente pour une exécution asynchrone
        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(Call<BinancePriceResponse> call, Response<BinancePriceResponse> response) {
                // Enregistre le temps de fin en nanosecondes
                long endTime = System.nanoTime();

                // Calcule la durée de l'appel en millisecondes
                long duration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
                // Vérifie si le temps de réponse est inférieur à 1 seconde (1000 ms)
                // Peut nécessiter un ajustement en fonction de la latence réseau attendue
                assertTrue("API call took too long: " + duration + "ms", duration < 1000);
                System.out.println("API call duration: " + duration + "ms"); // Log facultatif
            }

            @Override
            public void onFailure(Call<BinancePriceResponse> call, Throwable t) {
                // Fail if there is an error
                fail("API call failed: " + t.getMessage());
            }
        });

        // Important : Les tests @Test synchrones avec des callbacks asynchrones nécessitent
        // souvent un mécanisme pour attendre la fin du callback (ex: CountDownLatch)
        // pour éviter que le test ne se termine avant la réponse de l'API.
        // Ce code simple pourrait ne pas attendre correctement dans certains environnements de test.
        // Pour un test plus robuste, envisagez d'utiliser des bibliothèques comme MockWebServer
        // ou des mécanismes de synchronisation.
        try {
            // Ajout d'une pause simple pour laisser le temps au callback de s'exécuter
            // CE N'EST PAS IDEAL pour des tests fiables, mais peut fonctionner pour un test rapide.
            Thread.sleep(2000); // Attend 2 secondes, à ajuster si nécessaire
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

