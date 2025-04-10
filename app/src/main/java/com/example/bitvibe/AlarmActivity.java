package com.example.bitvibe;

import static com.example.bitvibe.MainActivity.binanceApi;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.OnBackPressedDispatcher;
import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlarmActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "BitVibePrefs";
    private static final String PREF_TRIGGER_PRICE = "trigger_price";
    private static final String PREF_IS_ABOVE = "is_above";
    private static final String PREF_ALARM_ON = "is_alarm_on";
    private static final String TAG = "AlarmActivity";

    private EditText triggerPriceEditText;
    private Button setAlarmButton;
    private SharedPreferences sharedPreferences;
    private boolean isAbove;
    private Switch onOffSwitch;
    private boolean isAlarmOn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        // Get the reference to the button in the layout
        Button backButton = findViewById(R.id.mainActivityButton);
        // Set the click listener
        backButton.setOnClickListener(v -> {
            OnBackPressedDispatcher dispatcher = getOnBackPressedDispatcher();
            dispatcher.onBackPressed();
        });

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Bind UI elements
        triggerPriceEditText = findViewById(R.id.triggerPriceEditText);
        setAlarmButton = findViewById(R.id.setAlarmButton);
        onOffSwitch = findViewById(R.id.onOFFSwitch);

        // Load saved alarm if it exists
        loadAlarm();

        // Set Alarm Button click listener
        setAlarmButton.setOnClickListener(v -> saveAlarm());


        // Switch change listener
        onOffSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Save the new state of the switch
                isAlarmOn = isChecked;
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(PREF_ALARM_ON, isAlarmOn);
                editor.apply();

                // Update the text of the switch
                updateSwitchText();

                Log.d(TAG, "Alarm state changed to: " + (isAlarmOn ? "ON" : "OFF"));

            }
        });
    }



    // Load the alarm from SharedPreferences
    private void loadAlarm() {

        // update the text of the switch and the switch itself
        updateSwitchText();
        onOffSwitch.setChecked(sharedPreferences.getBoolean(PREF_ALARM_ON, false));

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
// Retrieve the selected cryptocurrency symbol from SharedPreferences
        String selectedCrypto = sharedPreferences.getString("crypto", "DOGEUSDT"); // Default to DOGEUSDT if not set

        Call<BinancePriceResponse> call = binanceApi.getBitcoinPrice(selectedCrypto); // Pass the symbol to the API call
        // Exécute la requête de manière asynchrone
        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(Call<BinancePriceResponse> call, Response<BinancePriceResponse> response) {
                // Vérifie si la réponse est valide
                if (response.isSuccessful() && response.body() != null) {

                    //SET IS ABOVE
                    double currentPrice = response.body().getPrice(); // Récupère le prix actuel
                    isAbove = currentPrice > Double.parseDouble(triggerPriceStr);


                    //SAVE ALARM
                    try {
                        double triggerPrice = Double.parseDouble(triggerPriceStr);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(PREF_TRIGGER_PRICE, String.valueOf(triggerPrice));
                        editor.putBoolean(PREF_IS_ABOVE, isAbove);
                        editor.apply();
                        Toast.makeText(AlarmActivity.this, "Alarm saved", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Alarm saved: Trigger Price = " + triggerPrice + ", Current Price = " + currentPrice + " Is Above = " + isAbove);

                        // todo :  lauch a loop or something to check if the alarm should be triggered


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

    // update the text of the switch
    private void updateSwitchText() {
        onOffSwitch.setText(sharedPreferences.getBoolean(PREF_ALARM_ON, false) ? "ON" : "OFF");
    }
}