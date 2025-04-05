package com.example.bitvibe;

import static com.example.bitvibe.MainActivity.binanceApi;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlarmActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "BitVibePrefs";
    private static final String PREF_TRIGGER_PRICE = "trigger_price";
    private static final String PREF_IS_ABOVE = "is_above";
    private static final String TAG = "AlarmActivity";

    private EditText triggerPriceEditText;
    private Button setAlarmButton;
    private SharedPreferences sharedPreferences;
    private boolean isAbove;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Bind UI elements
        triggerPriceEditText = findViewById(R.id.triggerPriceEditText);
        setAlarmButton = findViewById(R.id.setAlarmButton);

        // Load saved alarm if it exists
        loadAlarm();

        // Set Alarm Button click listener
        setAlarmButton.setOnClickListener(v -> saveAlarm());
    }

    // Load the alarm from SharedPreferences
    private void loadAlarm() {
        if (sharedPreferences.contains(PREF_TRIGGER_PRICE)) {
            double triggerPrice = Double.parseDouble(sharedPreferences.getString(PREF_TRIGGER_PRICE, "0.0"));
            boolean isAbove = sharedPreferences.getBoolean(PREF_IS_ABOVE, false);
            triggerPriceEditText.setText(String.valueOf(triggerPrice)); // set the trigger price in the EditText

            Log.d(TAG, "Alarm loaded: Trigger Price = " + triggerPrice + ", Last alarm was Above = " + isAbove);
        }
    }

    // Save the alarm to SharedPreferences
    private void saveAlarm() {
        String triggerPriceStr = triggerPriceEditText.getText().toString();
        // Check if the EditText is empty
        if (triggerPriceStr.isEmpty()) {
            Toast.makeText(AlarmActivity.this, "Please enter a trigger price", Toast.LENGTH_SHORT).show();

            return;
        }


        // Crée une requête pour obtenir le prix
        Call<BinancePriceResponse> call = binanceApi.getBitcoinPrice();
        // Exécute la requête de manière asynchrone
        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(Call<BinancePriceResponse> call, Response<BinancePriceResponse> response) {
                // Vérifie si la réponse est valide
                if (response.isSuccessful() && response.body() != null) {

                    //SET IS ABOVE
                    double currentPrice = response.body().getPrice(); // Récupère le prix actuel
                    Log.d(TAG, response.body().getSymbol() + " : $" + currentPrice);
                    isAbove = currentPrice > Double.parseDouble(triggerPriceStr);
                    Log.d(TAG, "Is ABOOOOOOOOOOOOOVE = " + isAbove);


                    //SAVE ALARM
                    try {
                        double triggerPrice = Double.parseDouble(triggerPriceStr);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(PREF_TRIGGER_PRICE, String.valueOf(triggerPrice));
                        editor.putBoolean(PREF_IS_ABOVE, isAbove);
                        editor.apply();
                        Toast.makeText(AlarmActivity.this, "Alarm saved", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Alarm saved: Trigger Price = " + triggerPrice + ", Is Above = " + isAbove);

                        // TODO Here you will need to start the service or something to compare with the current price.

                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parsing trigger price: " + triggerPriceStr, e);
                        Toast.makeText(AlarmActivity.this, "Invalid trigger price: " + triggerPriceStr, Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<BinancePriceResponse> call, Throwable t) { // En cas d'échec réseau ou autre problème
                Log.e(TAG, "Échec de la requête API : " + t.getMessage(), t); // Logger l'exception complète est utile
            }

        });



    }
}