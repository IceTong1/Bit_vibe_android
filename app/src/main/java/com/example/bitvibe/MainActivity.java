package com.example.bitvibe;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.TextView;
import android.util.Log;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private CoinGeckoApi coinGeckoApi;
    private TextView bitcoinPriceTextView;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bitcoinPriceTextView = findViewById(R.id.bitcoinPriceTextView);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.coingecko.com/api/v3/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        coinGeckoApi = retrofit.create(CoinGeckoApi.class);

        fetchBitcoinPrice();
    }

    private void fetchBitcoinPrice() {
        Call<BitcoinPriceResponse> call = coinGeckoApi.getBitcoinPrice();
        call.enqueue(new Callback<BitcoinPriceResponse>() {
            @Override
            public void onResponse(Call<BitcoinPriceResponse> call, Response<BitcoinPriceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    double price = response.body().getBitcoin().getUsd();
                    Log.d(TAG, "Prix du Bitcoin : " + price);
                    bitcoinPriceTextView.setText("Prix du Bitcoin : $" + price);
                } else {
                    Log.e(TAG, "Erreur API : " + response.code());
                    bitcoinPriceTextView.setText("Erreur API : " + response.code());
                }
            }

            @Override
            public void onFailure(Call<BitcoinPriceResponse> call, Throwable t) {
                Log.e(TAG, "Échec de la requête : " + t.getMessage());
                bitcoinPriceTextView.setText("Échec de la connexion");
            }
        });
    }
}