package com.example.bitvibe;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager; // Importation ajoutée
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private BinanceApi binanceApi;
    private TextView bitcoinPriceTextView;
    private EditText toleranceEditText;
    private Button updateToleranceButton;
    private Handler handler;
    private Runnable priceRunnable;
    private double lastPrice = -1;
    private double tolerancePercentage = 1.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bitcoinPriceTextView = findViewById(R.id.bitcoinPriceTextView);
        toleranceEditText = findViewById(R.id.toleranceEditText);
        updateToleranceButton = findViewById(R.id.updateToleranceButton);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    REQUEST_BLUETOOTH_PERMISSIONS);
        }

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

        updateToleranceButton.setOnClickListener(v -> {
            String toleranceInput = toleranceEditText.getText().toString();
            if (!toleranceInput.isEmpty()) {
                try {
                    tolerancePercentage = Double.parseDouble(toleranceInput);
                    Log.d(TAG, "Tolérance mise à jour : " + tolerancePercentage + "%");
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Entrée invalide pour la tolérance");
                    toleranceEditText.setText(String.valueOf(tolerancePercentage));
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
                            Toast.makeText(MainActivity.this, "Hausse détectée (+" + String.format("%.2f", percentageChange) + "%)", Toast.LENGTH_SHORT).show();
                        } else if (percentageChange < -tolerancePercentage) {
                            Log.d(TAG, "Baisse détectée (" + String.format("%.2f", percentageChange) + "%)");
                            Toast.makeText(MainActivity.this, "Baisse détectée (" + String.format("%.2f", percentageChange) + "%)", Toast.LENGTH_SHORT).show();
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permissions Bluetooth accordées");
            } else {
                Log.e(TAG, "Permissions Bluetooth refusées");
                bitcoinPriceTextView.setText("Bluetooth requis");
            }
        }
    }
}