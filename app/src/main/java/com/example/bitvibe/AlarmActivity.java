package com.example.bitvibe;


import static com.example.bitvibe.MainActivity.binanceApi; // Accès statique (pas idéal mais suit le code existant)

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
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
    private TextView currentPriceTextView;
    private SharedPreferences sharedPreferences;
    private boolean isAbove;
    private Switch onOffSwitch;
    private boolean isAlarmOn;


    private Handler priceHandler = new Handler(Looper.getMainLooper());
    private Runnable priceRunnable;
    private int priceUpdateIntervalSeconds = 5; // Intervalle par défaut (secondes)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        // Bouton Retour
        Button backButton = findViewById(R.id.mainActivityButton);
        backButton.setOnClickListener(v -> {

            OnBackPressedDispatcher dispatcher = getOnBackPressedDispatcher();
            dispatcher.onBackPressed();
        });

        // Initialiser SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Bind UI elements
        triggerPriceEditText = findViewById(R.id.triggerPriceEditText);
        setAlarmButton = findViewById(R.id.setAlarmButton);
        onOffSwitch = findViewById(R.id.onOFFSwitch);
        currentPriceTextView = findViewById(R.id.currentPriceTextView); // Binding du nouveau TextView

        // Lire l'intervalle de rafraîchissement depuis les préférences
        priceUpdateIntervalSeconds = sharedPreferences.getInt("refresh_interval", 5);

        // Charger l'alarme sauvegardée
        loadAlarm();

        // Listener bouton Set Alarm
        setAlarmButton.setOnClickListener(v -> saveAlarm());

        // Listener Switch ON/OFF
        onOffSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isAlarmOn = isChecked;
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(PREF_ALARM_ON, isAlarmOn);
                editor.apply();
                updateSwitchText();
                Log.d(TAG, "Alarm state changed to: " + (isAlarmOn ? "ON" : "OFF"));
                // Gérer le démarrage/arrêt du service global si nécessaire via MainActivity ou Broadcast
            }
        });


        priceRunnable = new Runnable() {
            @Override
            public void run() {
                fetchCurrentPrice();
                priceHandler.postDelayed(this, priceUpdateIntervalSeconds * 1000L);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        priceHandler.post(priceRunnable);
        Log.d(TAG, "Démarrage fetchCurrentPrice loop");
    }

    @Override
    protected void onPause() {
        super.onPause();
        priceHandler.removeCallbacks(priceRunnable);
        Log.d(TAG, "Arrêt fetchCurrentPrice loop");
    }


    // Charger l'alarme depuis SharedPreferences
    private void loadAlarm() {
        updateSwitchText();
        onOffSwitch.setChecked(sharedPreferences.getBoolean(PREF_ALARM_ON, false));

        if (sharedPreferences.contains(PREF_TRIGGER_PRICE)) {
            double triggerPrice = Double.parseDouble(sharedPreferences.getString(PREF_TRIGGER_PRICE, "0.0"));
            boolean lastIsAbove = sharedPreferences.getBoolean(PREF_IS_ABOVE, false); // Renommé pour clarté
            triggerPriceEditText.setText(String.valueOf(triggerPrice));
            Log.d(TAG, "Alarm loaded: Trigger Price = " + triggerPrice + ", Last condition was Above = " + lastIsAbove);
        } else {
            Log.d(TAG, "No alarm saved found.");
        }
    }

    // Sauvegarder l'alarme dans SharedPreferences
    private void saveAlarm() {
        String triggerPriceStr = triggerPriceEditText.getText().toString();
        if (triggerPriceStr.isEmpty()) {
            Toast.makeText(AlarmActivity.this, "Please enter a trigger price", Toast.LENGTH_SHORT).show();
            return;
        }

        // Récupérer crypto sélectionnée
        String selectedCrypto = sharedPreferences.getString("crypto", "DOGEUSDT");

        // Vérifier le prix actuel pour déterminer 'isAbove' au moment de la sauvegarde
        if (binanceApi == null) {
            Log.e(TAG, "Binance API non initialisée !");
            Toast.makeText(this, "API Error", Toast.LENGTH_SHORT).show();
            return;
        }

        Call<BinancePriceResponse> call = binanceApi.getBitcoinPrice(selectedCrypto);
        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(Call<BinancePriceResponse> call, Response<BinancePriceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    double currentPrice = response.body().getPrice();
                    try {
                        double triggerPrice = Double.parseDouble(triggerPriceStr);
                        isAbove = currentPrice > triggerPrice; // Détermine la condition initiale

                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(PREF_TRIGGER_PRICE, String.valueOf(triggerPrice));
                        editor.putBoolean(PREF_IS_ABOVE, isAbove);
                        editor.putBoolean(PREF_ALARM_ON, onOffSwitch.isChecked()); // Sauvegarder aussi l'état du switch
                        editor.apply();

                        Toast.makeText(AlarmActivity.this, "Alarm saved", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Alarm saved: Trigger Price = " + triggerPrice + ", Current Price = " + currentPrice + " -> Condition initiale Is Above = " + isAbove);
                        // Lancer le service si ON (géré dans onResume/onPause de MainActivity via Broadcast ou état partagé)

                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parsing trigger price: " + triggerPriceStr, e);
                        Toast.makeText(AlarmActivity.this, "Invalid trigger price: " + triggerPriceStr, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "Échec sauvegarde alarme - Erreur API : " + response.code());
                    Toast.makeText(AlarmActivity.this, "Failed to save alarm - API error", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<BinancePriceResponse> call, Throwable t) {
                Log.e(TAG, "Échec sauvegarde alarme - Requête API : " + t.getMessage(), t);
                Toast.makeText(AlarmActivity.this, "Failed to save alarm - Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void updateSwitchText() {
        onOffSwitch.setText(sharedPreferences.getBoolean(PREF_ALARM_ON, false) ? "ON" : "OFF");
    }


    private void fetchCurrentPrice() {
        if (binanceApi == null) {
            Log.w(TAG, "fetchCurrentPrice: Binance API non initialisée.");
            if (currentPriceTextView != null) {
                currentPriceTextView.setText("API Error");
            }
            return;
        }

        String selectedCrypto = sharedPreferences.getString("crypto", "DOGEUSDT"); // Lire la crypto sélectionnée

        Call<BinancePriceResponse> call = binanceApi.getBitcoinPrice(selectedCrypto);
        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(Call<BinancePriceResponse> call, Response<BinancePriceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    double currentPrice = response.body().getPrice();
                    String symbol = response.body().getSymbol();
                    if (currentPriceTextView != null) {

                        runOnUiThread(() -> currentPriceTextView.setText(String.format(java.util.Locale.US, "%.2f", currentPrice)));
                        Log.d(TAG, "Current price updated: " + symbol + " = " + currentPrice);
                    }
                } else {
                    Log.e(TAG, "fetchCurrentPrice - Erreur API : " + response.code());
                    if (currentPriceTextView != null) {
                        runOnUiThread(() -> currentPriceTextView.setText("API Err:" + response.code()));
                    }
                }
            }

            @Override
            public void onFailure(Call<BinancePriceResponse> call, Throwable t) {
                Log.e(TAG, "fetchCurrentPrice - Échec requête API : " + t.getMessage());
                if (currentPriceTextView != null) {
                    runOnUiThread(() -> currentPriceTextView.setText("Network Err"));
                }
            }
        });
    }

}