// ----- Fichier : app/src/main/java/com/example/bitvibe/AlarmActivity.java -----

package com.example.bitvibe;

import static com.example.bitvibe.MainActivity.binanceApi;

import android.content.Context;
import android.content.Intent; // Ajout de l'import Intent
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
import androidx.core.content.ContextCompat; // Ajout de l'import ContextCompat


import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlarmActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "BitVibePrefs";
    // Clés de préférences
    private static final String PREF_HIGH_TRIGGER_PRICE = "high_trigger_price";
    private static final String PREF_IS_HIGH_ALARM_ON = "is_high_alarm_on";
    private static final String PREF_LOW_TRIGGER_PRICE = "low_trigger_price";
    private static final String PREF_IS_LOW_ALARM_ON = "is_low_alarm_on";

    private static final String TAG = "AlarmActivity";

    private EditText highTriggerPriceEditText;
    private EditText lowTriggerPriceEditText;
    private Switch highAlarmSwitch;
    private Switch lowAlarmSwitch;
    private Button saveAlarmsButton;
    private TextView currentPriceTextView;
    private SharedPreferences sharedPreferences;

    private Handler priceHandler = new Handler(Looper.getMainLooper());
    private Runnable priceRunnable;
    private int priceUpdateIntervalSeconds = 5;

    // Note : On n'utilise pas la variable statique isServiceRunning de MainActivity ici
    // pour éviter les problèmes de synchronisation entre activités. On se base
    // uniquement sur les préférences pour décider de démarrer/arrêter.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        // Définir le titre ici si besoin (si la modif strings.xml n'a pas été faite)
        // setTitle("Alarm Settings");

        Button backButton = findViewById(R.id.mainActivityButton);
        backButton.setOnClickListener(v -> {
            OnBackPressedDispatcher dispatcher = getOnBackPressedDispatcher();
            dispatcher.onBackPressed();
        });

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        highTriggerPriceEditText = findViewById(R.id.highTriggerPriceEditText);
        lowTriggerPriceEditText = findViewById(R.id.lowTriggerPriceEditText);
        highAlarmSwitch = findViewById(R.id.highAlarmSwitch);
        lowAlarmSwitch = findViewById(R.id.lowAlarmSwitch);
        saveAlarmsButton = findViewById(R.id.saveAlarmsButton);
        currentPriceTextView = findViewById(R.id.currentPriceTextView);

        priceUpdateIntervalSeconds = sharedPreferences.getInt("refresh_interval", 5);

        loadAlarmSettings();

        saveAlarmsButton.setOnClickListener(v -> saveAlarmSettings());

        highAlarmSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSwitchState(PREF_IS_HIGH_ALARM_ON, isChecked);
            updateSwitchText(highAlarmSwitch, isChecked);
            Log.d(TAG, "High Alarm state changed to: " + (isChecked ? "ON" : "OFF"));
            // Gérer le service immédiatement après le changement de switch
            manageAlarmCheckService();
        });

        lowAlarmSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSwitchState(PREF_IS_LOW_ALARM_ON, isChecked);
            updateSwitchText(lowAlarmSwitch, isChecked);
            Log.d(TAG, "Low Alarm state changed to: " + (isChecked ? "ON" : "OFF"));
            // Gérer le service immédiatement après le changement de switch
            manageAlarmCheckService();
        });

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
        loadAlarmSettings();
        // S'assurer que l'état du service est correct au retour sur l'activité
        manageAlarmCheckService();
    }

    @Override
    protected void onPause() {
        super.onPause();
        priceHandler.removeCallbacks(priceRunnable);
        Log.d(TAG, "Arrêt fetchCurrentPrice loop in onPause");
        // Pas besoin d'arrêter le service ici, il doit continuer en arrière-plan si activé
    }

    private void loadAlarmSettings() {
        String highPriceStr = sharedPreferences.getString(PREF_HIGH_TRIGGER_PRICE, "");
        boolean isHighOn = sharedPreferences.getBoolean(PREF_IS_HIGH_ALARM_ON, false);
        highTriggerPriceEditText.setText(highPriceStr);
        // Éviter de déclencher le listener lors du chargement
        highAlarmSwitch.setOnCheckedChangeListener(null);
        highAlarmSwitch.setChecked(isHighOn);
        updateSwitchText(highAlarmSwitch, isHighOn);
        highAlarmSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> { // Rattacher le listener
            saveSwitchState(PREF_IS_HIGH_ALARM_ON, isChecked);
            updateSwitchText(highAlarmSwitch, isChecked);
            Log.d(TAG, "High Alarm state changed to: " + (isChecked ? "ON" : "OFF"));
            manageAlarmCheckService();
        });


        String lowPriceStr = sharedPreferences.getString(PREF_LOW_TRIGGER_PRICE, "");
        boolean isLowOn = sharedPreferences.getBoolean(PREF_IS_LOW_ALARM_ON, false);
        lowTriggerPriceEditText.setText(lowPriceStr);
        // Éviter de déclencher le listener lors du chargement
        lowAlarmSwitch.setOnCheckedChangeListener(null);
        lowAlarmSwitch.setChecked(isLowOn);
        updateSwitchText(lowAlarmSwitch, isLowOn);
        lowAlarmSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> { // Rattacher le listener
            saveSwitchState(PREF_IS_LOW_ALARM_ON, isChecked);
            updateSwitchText(lowAlarmSwitch, isChecked);
            Log.d(TAG, "Low Alarm state changed to: " + (isChecked ? "ON" : "OFF"));
            manageAlarmCheckService();
        });


        Log.d(TAG, "Settings loaded: HighPrice=" + highPriceStr + ", HighOn=" + isHighOn +
                ", LowPrice=" + lowPriceStr + ", LowOn=" + isLowOn);
    }

    private void saveSwitchState(String key, boolean value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
        // Pas besoin d'appeler manageAlarmCheckService ici, car le listener le fait déjà
    }

    private void saveAlarmSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        boolean formatError = false;

        // Sauvegarder Prix Haut
        String highPriceStr = highTriggerPriceEditText.getText().toString();
        if (!highPriceStr.isEmpty()) {
            try {
                Double.parseDouble(highPriceStr);
                editor.putString(PREF_HIGH_TRIGGER_PRICE, highPriceStr);
            } catch (NumberFormatException e) {
                highTriggerPriceEditText.setError("Invalid number format");
                formatError = true;
            }
        } else {
            editor.remove(PREF_HIGH_TRIGGER_PRICE);
        }

        // Sauvegarder Prix Bas
        String lowPriceStr = lowTriggerPriceEditText.getText().toString();
        if (!lowPriceStr.isEmpty()) {
            try {
                Double.parseDouble(lowPriceStr);
                editor.putString(PREF_LOW_TRIGGER_PRICE, lowPriceStr);
            } catch (NumberFormatException e) {
                lowTriggerPriceEditText.setError("Invalid number format");
                formatError = true;
            }
        } else {
            editor.remove(PREF_LOW_TRIGGER_PRICE);
        }

        // Sauvegarder états des switches (déjà à jour grâce aux listeners)
        editor.putBoolean(PREF_IS_HIGH_ALARM_ON, highAlarmSwitch.isChecked());
        editor.putBoolean(PREF_IS_LOW_ALARM_ON, lowAlarmSwitch.isChecked());

        if (!formatError) {
            editor.apply();
            Toast.makeText(this, "Alarm settings saved", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Alarm settings saved. HighPrice=" + highPriceStr + ", LowPrice=" + lowPriceStr +
                    ", HighOn=" + highAlarmSwitch.isChecked() + ", LowOn=" + lowAlarmSwitch.isChecked());

            highTriggerPriceEditText.setError(null);
            lowTriggerPriceEditText.setError(null);

            // --- AJOUT : Gérer le service APRÈS sauvegarde réussie ---
            manageAlarmCheckService();

        } else {
            Toast.makeText(this, "Please fix errors before saving", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateSwitchText(Switch targetSwitch, boolean isChecked) {
        targetSwitch.setText(isChecked ? "ON" : "OFF");
    }

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

    // --- AJOUT : Méthode pour démarrer/arrêter AlarmCheckService ---
    // Similaire à celle de MainActivity, mais adaptée pour ce contexte
    private void manageAlarmCheckService() {
        // Lire l'état actuel des alarmes depuis les préférences
        boolean isHighAlarmOn = sharedPreferences.getBoolean(PREF_IS_HIGH_ALARM_ON, false);
        boolean isLowAlarmOn = sharedPreferences.getBoolean(PREF_IS_LOW_ALARM_ON, false);

        // Déterminer si le service doit tourner
        boolean shouldServiceRun = isHighAlarmOn || isLowAlarmOn;

        Intent serviceIntent = new Intent(this, AlarmCheckService.class);

        if (shouldServiceRun) {
            // Si le service doit tourner
            try {
                // On utilise startForegroundService car on ne sait pas si l'activité
                // est au premier plan quand cette méthode est appelée (ex: via listener switch)
                ContextCompat.startForegroundService(this, serviceIntent);
                Log.d(TAG, "Tentative de démarrage/maintien de AlarmCheckService (au moins une alarme ON)");
            } catch (Exception e) {
                Log.e(TAG, "Erreur démarrage AlarmCheckService depuis AlarmActivity", e);
            }
        } else {
            // Si le service ne doit PAS tourner (les deux alarmes sont OFF)
            stopService(serviceIntent);
            Log.d(TAG, "Tentative d'arrêt de AlarmCheckService (les deux alarmes sont OFF)");
        }
    }
}
// ----- Fin Fichier : app/src/main/java/com/example/bitvibe/AlarmActivity.java -----