// ----- Fichier : app/src/main/java/com/example/bitvibe/AlarmActivity.java -----

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
    private boolean isAbove; // Utilisé pour déterminer la condition initiale lors de la sauvegarde
    private Switch onOffSwitch;
    // La variable locale isAlarmOn n'est plus vraiment nécessaire ici car on force à ON lors du save
    // mais le listener du switch en a encore besoin pour la désactivation manuelle.
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
            // Pas besoin de saveSettings ici si le bouton retour ne doit pas sauvegarder/activer
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

        // Lire l'intervalle de rafraîchissement depuis les préférences (pour l'affichage du prix actuel)
        priceUpdateIntervalSeconds = sharedPreferences.getInt("refresh_interval", 5);

        // Charger l'alarme sauvegardée (pour afficher les valeurs précédentes)
        loadAlarm();

        // Listener bouton Set Alarm
        setAlarmButton.setOnClickListener(v -> saveAlarm());

        // Listener Switch ON/OFF (pour la désactivation manuelle)
        onOffSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isAlarmOn = isChecked; // Met à jour la variable locale
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(PREF_ALARM_ON, isAlarmOn);
                editor.apply();
                updateSwitchText(); // Met à jour le texte du switch ("ON" ou "OFF")
                Log.d(TAG, "Alarm state manually changed to: " + (isAlarmOn ? "ON" : "OFF"));
                // MainActivity gèrera le démarrage/arrêt du service dans son onResume
            }
        });


        priceRunnable = new Runnable() {
            @Override
            public void run() {
                fetchCurrentPrice();
                // Utiliser la valeur lue (ou mise à jour) de l'intervalle
                priceUpdateIntervalSeconds = sharedPreferences.getInt("refresh_interval", 5);
                priceHandler.postDelayed(this, priceUpdateIntervalSeconds * 1000L);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Relancer la boucle de récupération du prix actuel pour cet écran
        priceHandler.post(priceRunnable);
        Log.d(TAG, "Démarrage fetchCurrentPrice loop in onResume");
        // Recharger l'état actuel au cas où il aurait changé
        loadAlarm();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Arrêter la boucle de récupération du prix actuel pour économiser les ressources
        priceHandler.removeCallbacks(priceRunnable);
        Log.d(TAG, "Arrêt fetchCurrentPrice loop in onPause");
    }


    // Charger l'alarme depuis SharedPreferences pour affichage
    private void loadAlarm() {
        // Lire l'état ON/OFF sauvegardé et mettre à jour le switch et son texte
        isAlarmOn = sharedPreferences.getBoolean(PREF_ALARM_ON, false);
        onOffSwitch.setChecked(isAlarmOn);
        updateSwitchText();

        // Charger et afficher le dernier prix de déclenchement sauvegardé, s'il existe
        if (sharedPreferences.contains(PREF_TRIGGER_PRICE)) {
            String triggerPriceStr = sharedPreferences.getString(PREF_TRIGGER_PRICE, "0.0");
            try {
                double triggerPrice = Double.parseDouble(triggerPriceStr);
                triggerPriceEditText.setText(String.valueOf(triggerPrice));
                // La variable 'isAbove' est interne à la logique de sauvegarde/déclenchement,
                // pas besoin de la stocker ici comme état de l'activité.
                boolean lastIsAbove = sharedPreferences.getBoolean(PREF_IS_ABOVE, false);
                Log.d(TAG, "Alarm loaded: Trigger Price = " + triggerPrice + ", Last condition was Above = " + lastIsAbove + ", Alarm On = " + isAlarmOn);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error parsing loaded trigger price: " + triggerPriceStr, e);
                triggerPriceEditText.setText(""); // Clear invalid value
            }
        } else {
            Log.d(TAG, "No alarm saved found.");
            triggerPriceEditText.setText(""); // Clear if no value saved
        }
    }

    // Sauvegarder l'alarme dans SharedPreferences ET FORCER L'ACTIVATION
    private void saveAlarm() {
        String triggerPriceStr = triggerPriceEditText.getText().toString();
        if (triggerPriceStr.isEmpty()) {
            Toast.makeText(AlarmActivity.this, "Please enter a trigger price", Toast.LENGTH_SHORT).show();
            return;
        }

        // Récupérer crypto sélectionnée
        String selectedCrypto = sharedPreferences.getString("crypto", "DOGEUSDT");

        // Vérifier que l'API est initialisée
        if (binanceApi == null) {
            Log.e(TAG, "Binance API non initialisée !");
            Toast.makeText(this, "API Error - Cannot save alarm", Toast.LENGTH_SHORT).show();
            return;
        }

        // Faire l'appel API pour obtenir le prix actuel afin de déterminer 'isAbove'
        Call<BinancePriceResponse> call = binanceApi.getBitcoinPrice(selectedCrypto);
        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(Call<BinancePriceResponse> call, Response<BinancePriceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    double currentPrice = response.body().getPrice();
                    try {
                        // Parser le prix cible entré par l'utilisateur
                        double triggerPrice = Double.parseDouble(triggerPriceStr);

                        // Déterminer la condition initiale 'isAbove'
                        isAbove = currentPrice > triggerPrice;

                        // Sauvegarder les informations dans SharedPreferences
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(PREF_TRIGGER_PRICE, String.valueOf(triggerPrice));
                        editor.putBoolean(PREF_IS_ABOVE, isAbove);
                        // --- MODIFICATION CLÉ ICI ---
                        // Forcer l'état de l'alarme à ON lors de la sauvegarde
                        editor.putBoolean(PREF_ALARM_ON, true);
                        editor.apply(); // Appliquer les changements

                        // Afficher confirmation
                        Toast.makeText(AlarmActivity.this, "Alarm saved and activated", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Alarm saved: Trigger Price = " + triggerPrice + ", Current Price = " + currentPrice + " -> Condition initiale Is Above = " + isAbove + ". ALARM FORCED ON.");

                        // --- MISE A JOUR UI ---
                        // Mettre à jour le switch pour qu'il reflète l'état activé
                        // Utiliser runOnUiThread pour garantir l'exécution sur le thread principal
                        runOnUiThread(() -> {
                            onOffSwitch.setChecked(true);
                            updateSwitchText(); // Met à jour le texte du switch en "ON"
                        });
                        // MainActivity démarrera le service lors de son prochain onResume si ce n'est déjà fait

                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parsing trigger price: " + triggerPriceStr, e);
                        Toast.makeText(AlarmActivity.this, "Invalid trigger price format: " + triggerPriceStr, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "Échec sauvegarde alarme - Erreur API : " + response.code());
                    Toast.makeText(AlarmActivity.this, "Failed to save alarm - API error: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<BinancePriceResponse> call, Throwable t) {
                Log.e(TAG, "Échec sauvegarde alarme - Requête API : " + t.getMessage(), t);
                Toast.makeText(AlarmActivity.this, "Failed to save alarm - Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }


    // Met à jour le texte du Switch ("ON" ou "OFF")
    private void updateSwitchText() {
        // Lire la valeur la plus récente depuis les prefs pour être sûr
        boolean currentState = sharedPreferences.getBoolean(PREF_ALARM_ON, false);
        onOffSwitch.setText(currentState ? "ON" : "OFF");
    }


    // Récupère et affiche le prix actuel
    private void fetchCurrentPrice() {
        if (binanceApi == null) {
            Log.w(TAG, "fetchCurrentPrice: Binance API non initialisée.");
            if (currentPriceTextView != null) {
                // Utiliser runOnUiThread par sécurité pour les mises à jour UI
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
                    String symbol = response.body().getSymbol(); // Récupérer aussi le symbole
                    if (currentPriceTextView != null) {
                        // Mettre à jour le TextView sur le thread UI
                        runOnUiThread(() -> currentPriceTextView.setText(String.format(java.util.Locale.US, "%.2f", currentPrice)));
                        Log.d(TAG, "Current price updated: " + symbol + " = " + currentPrice);
                    }
                } else {
                    Log.e(TAG, "fetchCurrentPrice - Erreur API : " + response.code());
                    if (currentPriceTextView != null) {
                        // Mettre à jour le TextView sur le thread UI
                        final int responseCode = response.code(); // Capturer pour lambda
                        runOnUiThread(() -> currentPriceTextView.setText("API Err:" + responseCode));
                    }
                }
            }

            @Override
            public void onFailure(Call<BinancePriceResponse> call, Throwable t) {
                Log.e(TAG, "fetchCurrentPrice - Échec requête API : " + t.getMessage());
                if (currentPriceTextView != null) {
                    // Mettre à jour le TextView sur le thread UI
                    runOnUiThread(() -> currentPriceTextView.setText("Network Err"));
                }
            }
        });
    }

}
// ----- Fin Fichier : app/src/main/java/com/example/bitvibe/AlarmActivity.java -----