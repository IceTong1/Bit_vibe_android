package com.example.bitvibe;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.util.Log;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private BinanceApi binanceApi;
    private TextView bitcoinPriceTextView;
    private EditText toleranceEditText;
    private Button updateToleranceButton;
    private Handler handler;
    private Runnable priceRunnable;
    private double lastPrice = -1;
    private double tolerancePercentage = 1.0; // Valeur par défaut

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bitcoinPriceTextView = findViewById(R.id.bitcoinPriceTextView);
        toleranceEditText = findViewById(R.id.toleranceEditText);
        updateToleranceButton = findViewById(R.id.updateToleranceButton);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.binance.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        binanceApi = retrofit.create(BinanceApi.class);

        handler = new Handler(Looper.getMainLooper());
        priceRunnable = new Runnable() {
            @Override
            public void run() {
                fetchBitcoinPrice();
                handler.postDelayed(this, 5000);
            }
        };

        // Mettre à jour la tolérance quand le bouton est cliqué
        updateToleranceButton.setOnClickListener(v -> {
            String toleranceInput = toleranceEditText.getText().toString();
            if (!toleranceInput.isEmpty()) {
                try {
                    tolerancePercentage = Double.parseDouble(toleranceInput);
                    Log.d(TAG, "Tolérance mise à jour : " + tolerancePercentage + "%");
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Entrée invalide pour la tolérance");
                    toleranceEditText.setText(String.valueOf(tolerancePercentage)); // Réinitialiser
                }
            }
        });

        handler.post(priceRunnable);
    }

    private void fetchBitcoinPrice() {
        Call<BinancePriceResponse> call = binanceApi.getBitcoinPrice();
        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(Call<BinancePriceResponse> call, Response<BinancePriceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    double currentPrice = response.body().getPrice();
                    Log.d(TAG, "Prix du Bitcoin : " + currentPrice);
                    bitcoinPriceTextView.setText("Prix du Bitcoin : $" + currentPrice);

                    if (lastPrice != -1) {
                        double percentageChange = ((currentPrice - lastPrice) / lastPrice) * 100;
                        if (percentageChange > tolerancePercentage) {
                            Log.d(TAG, "Hausse détectée (+" + String.format("%.2f", percentageChange) + "%)");
                        } else if (percentageChange < -tolerancePercentage) {
                            Log.d(TAG, "Baisse détectée (" + String.format("%.2f", percentageChange) + "%)");
                        } else {
                            Log.d(TAG, "Prix stable (" + String.format("%.2f", percentageChange) + "%)");
                        }
                    }
                    lastPrice = currentPrice;
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
        handler.removeCallbacks(priceRunnable);
    }
}