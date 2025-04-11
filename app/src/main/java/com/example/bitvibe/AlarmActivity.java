package com.example.bitvibe;

import static com.example.bitvibe.MainActivity.binanceApi;

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
    // Nouvelles clés de préférences
    private static final String PREF_HIGH_TRIGGER_PRICE = "high_trigger_price";
    private static final String PREF_IS_HIGH_ALARM_ON = "is_high_alarm_on";
    private static final String PREF_LOW_TRIGGER_PRICE = "low_trigger_price";
    private static final String PREF_IS_LOW_ALARM_ON = "is_low_alarm_on";

    private static final String TAG = "AlarmActivity";

    private EditText highTriggerPriceEditText;
    private EditText lowTriggerPriceEditText;
    private Switch highAlarmSwitch;
    private Switch lowAlarmSwitch;
    private Button saveAlarmsButton; // Renommé
    private TextView currentPriceTextView;
    private SharedPreferences sharedPreferences;

    private Handler priceHandler = new Handler(Looper.getMainLooper());
    private Runnable priceRunnable;
    private int priceUpdateIntervalSeconds = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm); // Utilise le nouveau layout

        // Bouton Retour
        Button backButton = findViewById(R.id.mainActivityButton);
        backButton.setOnClickListener(v -> {
            OnBackPressedDispatcher dispatcher = getOnBackPressedDispatcher();
            dispatcher.onBackPressed();
        });

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Bind UI elements
        highTriggerPriceEditText = findViewById(R.id.highTriggerPriceEditText);
        lowTriggerPriceEditText = findViewById(R.id.lowTriggerPriceEditText);
        highAlarmSwitch = findViewById(R.id.highAlarmSwitch);
        lowAlarmSwitch = findViewById(R.id.lowAlarmSwitch);
        saveAlarmsButton = findViewById(R.id.saveAlarmsButton);
        currentPriceTextView = findViewById(R.id.currentPriceTextView);

        priceUpdateIntervalSeconds = sharedPreferences.getInt("refresh_interval", 5);

        // Charger les états et valeurs sauvegardés
        loadAlarmSettings();

        // Listener bouton Sauvegarder
        saveAlarmsButton.setOnClickListener(v -> saveAlarmSettings());

        // Listeners pour les Switches (sauvegarde immédiate de leur état)
        highAlarmSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSwitchState(PREF_IS_HIGH_ALARM_ON, isChecked);
            updateSwitchText(highAlarmSwitch, isChecked);
            Log.d(TAG, "High Alarm state changed to: " + (isChecked ? "ON" : "OFF"));
        });

        lowAlarmSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSwitchState(PREF_IS_LOW_ALARM_ON, isChecked);
            updateSwitchText(lowAlarmSwitch, isChecked);
            Log.d(TAG, "Low Alarm state changed to: " + (isChecked ? "ON" : "OFF"));
        });

        // Runnable pour afficher le prix actuel
        priceRunnable = new Runnable() {
            @Override
            public void run() {
                fetchCurrentPrice();
                priceUpdateIntervalSeconds = sharedPreferences.getInt("refresh_interval", 5);
                priceHandler.postDelayed(this, priceUpdateIntervalSeconds * 1000L);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        priceHandler.post(priceRunnable);
        Log.d(TAG, "Démarrage fetchCurrentPrice loop in onResume");
        // Recharger au cas où l'état aurait été changé par le service
        loadAlarmSettings();
    }

    @Override
    protected void onPause() {
        super.onPause();
        priceHandler.removeCallbacks(priceRunnable);
        Log.d(TAG, "Arrêt fetchCurrentPrice loop in onPause");
    }

    private void loadAlarmSettings() {
        // Charger Alarme Haute
        String highPriceStr = sharedPreferences.getString(PREF_HIGH_TRIGGER_PRICE, "");
        boolean isHighOn = sharedPreferences.getBoolean(PREF_IS_HIGH_ALARM_ON, false);
        highTriggerPriceEditText.setText(highPriceStr);
        highAlarmSwitch.setChecked(isHighOn);
        updateSwitchText(highAlarmSwitch, isHighOn);

        // Charger Alarme Basse
        String lowPriceStr = sharedPreferences.getString(PREF_LOW_TRIGGER_PRICE, "");
        boolean isLowOn = sharedPreferences.getBoolean(PREF_IS_LOW_ALARM_ON, false);
        lowTriggerPriceEditText.setText(lowPriceStr);
        lowAlarmSwitch.setChecked(isLowOn);
        updateSwitchText(lowAlarmSwitch, isLowOn);

        Log.d(TAG, "Settings loaded: HighPrice=" + highPriceStr + ", HighOn=" + isHighOn +
                ", LowPrice=" + lowPriceStr + ", LowOn=" + isLowOn);
    }

    // Sauvegarde uniquement l'état d'un switch
    private void saveSwitchState(String key, boolean value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    // Sauvegarde les prix seuils entrés par l'utilisateur
    private void saveAlarmSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        boolean settingsChanged = false;
        boolean formatError = false;

        // Sauvegarder Prix Haut
        String highPriceStr = highTriggerPriceEditText.getText().toString();
        if (!highPriceStr.isEmpty()) {
            try {
                Double.parseDouble(highPriceStr); // Just check format
                editor.putString(PREF_HIGH_TRIGGER_PRICE, highPriceStr);
                settingsChanged = true;
            } catch (NumberFormatException e) {
                highTriggerPriceEditText.setError("Invalid number format");
                formatError = true;
            }
        } else {
            editor.remove(PREF_HIGH_TRIGGER_PRICE); // Remove if empty
            settingsChanged = true; // Consider empty as a change if previously set
        }

        // Sauvegarder Prix Bas
        String lowPriceStr = lowTriggerPriceEditText.getText().toString();
        if (!lowPriceStr.isEmpty()) {
            try {
                Double.parseDouble(lowPriceStr); // Just check format
                editor.putString(PREF_LOW_TRIGGER_PRICE, lowPriceStr);
                settingsChanged = true;
            } catch (NumberFormatException e) {
                lowTriggerPriceEditText.setError("Invalid number format");
                formatError = true;
            }
        } else {
            editor.remove(PREF_LOW_TRIGGER_PRICE); // Remove if empty
            settingsChanged = true;
        }

        // Sauvegarder états des switches (déjà fait par les listeners, mais on re-sauvegarde ici pour être sûr)
        editor.putBoolean(PREF_IS_HIGH_ALARM_ON, highAlarmSwitch.isChecked());
        editor.putBoolean(PREF_IS_LOW_ALARM_ON, lowAlarmSwitch.isChecked());
        settingsChanged = true; // Assume switch state might be part of the save action intent


        if (!formatError) {
            editor.apply();
            Toast.makeText(this, "Alarm settings saved", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Alarm settings saved. HighPrice=" + highPriceStr + ", LowPrice=" + lowPriceStr +
                    ", HighOn=" + highAlarmSwitch.isChecked() + ", LowOn=" + lowAlarmSwitch.isChecked());
            // Clear errors if any
            highTriggerPriceEditText.setError(null);
            lowTriggerPriceEditText.setError(null);
        } else {
            Toast.makeText(this, "Please fix errors before saving", Toast.LENGTH_SHORT).show();
        }
        // MainActivity gérera le démarrage/arrêt du service dans son onResume en lisant ces nouvelles valeurs
    }

    // Met à jour le texte du Switch donné ("ON" ou "OFF")
    private void updateSwitchText(Switch targetSwitch, boolean isChecked) {
        targetSwitch.setText(isChecked ? "ON" : "OFF");
    }

    // Récupère et affiche le prix actuel (inchangé)
    private void fetchCurrentPrice() {
        if (binanceApi == null) {
            Log.w(TAG, "fetchCurrentPrice: Binance API non initialisée.");
            if (currentPriceTextView != null) {
                runOnUiThread(() -> currentPriceTextView.setText("API Error"));
            }
            return;
        }

        String selectedCrypto = sharedPreferences.getString("crypto", "DOGEUSDT");

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
                        final int responseCode = response.code();
                        runOnUiThread(() -> currentPriceTextView.setText("API Err:" + responseCode));
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