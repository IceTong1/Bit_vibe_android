package com.example.bitvibe;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.util.Log;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private BinanceApi binanceApi; // Remplace CoinGeckoApi
    private TextView bitcoinPriceTextView;
    private Handler handler;
    private Runnable priceRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bitcoinPriceTextView = findViewById(R.id.bitcoinPriceTextView);

        // Initialiser Retrofit avec l'URL de Binance
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.binance.com/") // Nouvelle URL de base
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        binanceApi = retrofit.create(BinanceApi.class); // Remplace CoinGeckoApi

        // Initialiser le Handler pour les mises à jour périodiques
        handler = new Handler(Looper.getMainLooper());
        priceRunnable = new Runnable() {
            @Override
            public void run() {
                fetchBitcoinPrice();
                handler.postDelayed(this, 5000); // Toutes les 5 secondes
            }
        };

        // Démarrer les mises à jour
        handler.post(priceRunnable);
    }

    private void fetchBitcoinPrice() {
        Call<BinancePriceResponse> call = binanceApi.getBitcoinPrice();
        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(Call<BinancePriceResponse> call, Response<BinancePriceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    double price = response.body().getPrice();
                    Log.d(TAG, "Prix du Bitcoin : " + price);
                    bitcoinPriceTextView.setText("Prix du Bitcoin : $" + price);
                } else {
                    Log.e(TAG, "Erreur API : " + response.code());
                    bitcoinPriceTextView.setText("Erreur API : " + response.code());
                }
            }

            @Override
            public void onFailure(Call<BinancePriceResponse> call, Throwable t) {
                Log.e(TAG, "Échec de la requête : " + t.getMessage());
                bitcoinPriceTextView.setText("Échec de la connexion");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(priceRunnable); // Arrêter les mises à jour
    }
}