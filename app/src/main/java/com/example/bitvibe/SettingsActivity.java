package com.example.bitvibe;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedDispatcher;
import android.util.Log;

public class SettingsActivity extends AppCompatActivity {
    private EditText refreshIntervalEditText;
    private EditText tolerancePercentageEditText;
    private Spinner currencySpinner;
    private Spinner notificationTypeSpinner; // Anciennement vibrationIntensitySpinner
    private Spinner languageSpinner;
    private Spinner cryptoSpinner;
    private SharedPreferences prefs;

    // Clé pour le nouveau paramètre
    private static final String PREF_NOTIFICATION_TYPE = "notification_type";

    // Constantes pour les types de notification (correspondant aux valeurs de ringType)
    private static final int NOTIF_TYPE_BEEP = 1;
    private static final int NOTIF_TYPE_LED = 2;
    private static final int NOTIF_TYPE_VIBRATE = 4;
    private static final int NOTIF_TYPE_LED_VIBRATE = 6; // (LED | VIBRATE)
    private static final int NOTIF_TYPE_VIB_LED_BEEP = 7; // Ajouté : (VIB | LED | BEEP)


    private static final int DEFAULT_NOTIFICATION_TYPE = NOTIF_TYPE_LED_VIBRATE; // Défaut: LED + VIB (6)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Bouton Retour
        Button backButton = findViewById(R.id.mainActivityButton);
        backButton.setOnClickListener(v -> {
            saveSettings();
            OnBackPressedDispatcher dispatcher = getOnBackPressedDispatcher();
            dispatcher.onBackPressed();
        });

        // Initialiser SharedPreferences
        prefs = getSharedPreferences("BitVibePrefs", Context.MODE_PRIVATE);

        // Bind UI elements
        refreshIntervalEditText = findViewById(R.id.refresh_interval_edittext);
        tolerancePercentageEditText = findViewById(R.id.tolerance_percentage_edittext);
        currencySpinner = findViewById(R.id.currency_spinner);
        notificationTypeSpinner = findViewById(R.id.notification_type_spinner);
        languageSpinner = findViewById(R.id.language_spinner);
        cryptoSpinner = findViewById(R.id.crypto_spinner);

        // --- Charger les paramètres courants ---

        // Intervalle de rafraîchissement
        int currentInterval = prefs.getInt("refresh_interval", 5);
        refreshIntervalEditText.setText(String.valueOf(currentInterval));

        // Tolérance en pourcentage
        float currentTolerance = prefs.getFloat("tolerance_percentage", 0.01f);
        tolerancePercentageEditText.setText(String.valueOf(currentTolerance));

        // Spinner Devise
        ArrayAdapter<CharSequence> currencyAdapter = ArrayAdapter.createFromResource(this,
                R.array.currency_array, android.R.layout.simple_spinner_item);
        currencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        currencySpinner.setAdapter(currencyAdapter);
        String currentCurrency = prefs.getString("currency", "USD");
        int currencyPosition = currencyAdapter.getPosition(currentCurrency);
        currencySpinner.setSelection(currencyPosition);


        // Spinner Type Notification
        ArrayAdapter<CharSequence> notificationTypeAdapter = ArrayAdapter.createFromResource(this,
                R.array.notification_type_array, android.R.layout.simple_spinner_item);
        notificationTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        notificationTypeSpinner.setAdapter(notificationTypeAdapter);
        // Charger la valeur sauvegardée (int) et trouver la position correspondante
        int currentNotificationType = prefs.getInt(PREF_NOTIFICATION_TYPE, DEFAULT_NOTIFICATION_TYPE); // Utilisation de la nouvelle valeur par défaut
        int notificationTypePosition = mapRingTypeToPosition(currentNotificationType);
        notificationTypeSpinner.setSelection(notificationTypePosition);


        // Spinner Langue
        ArrayAdapter<CharSequence> languageAdapter = ArrayAdapter.createFromResource(this,
                R.array.language_array, android.R.layout.simple_spinner_item);
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(languageAdapter);
        String currentLanguage = prefs.getString("language", "english");
        int languagePosition = languageAdapter.getPosition(currentLanguage);
        languageSpinner.setSelection(languagePosition);


        // Spinner Crypto
        ArrayAdapter<CharSequence> cryptoAdapter = ArrayAdapter.createFromResource(this,
                R.array.crypto_array, android.R.layout.simple_spinner_item);
        cryptoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cryptoSpinner.setAdapter(cryptoAdapter);
        String currentCrypto = prefs.getString("crypto", "DOGEUSDT");
        int cryptoPosition = cryptoAdapter.getPosition(currentCrypto);
        if (cryptoPosition < 0) {
            cryptoPosition = cryptoAdapter.getPosition("DOGEUSDT");
        }
        cryptoSpinner.setSelection(cryptoPosition);


        logLoadedPrefs();
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();

        // Sauvegarder intervalle de rafraîchissement
        String intervalStr = refreshIntervalEditText.getText().toString();
        if (!intervalStr.isEmpty()) {
            try {
                int interval = Integer.parseInt(intervalStr);
                if (interval > 0) {
                    editor.putInt("refresh_interval", interval);
                } else {
                    Toast.makeText(this, "Invalid refresh interval", Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format for interval", Toast.LENGTH_SHORT).show();
            }
        }

        // Sauvegarder tolérance en pourcentage
        String toleranceStr = tolerancePercentageEditText.getText().toString();
        if (!toleranceStr.isEmpty()) {
            try {
                float tolerance = Float.parseFloat(toleranceStr);
                if (tolerance >= 0) {
                    editor.putFloat("tolerance_percentage", tolerance);
                } else {
                    Toast.makeText(this, "Invalid tolerance percentage", Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format for tolerance", Toast.LENGTH_SHORT).show();
            }
        }

        // Sauvegarder devise
        String selectedCurrency = currencySpinner.getSelectedItem().toString();
        editor.putString("currency", selectedCurrency);

        // Sauvegarde Type Notification
        int selectedNotificationPosition = notificationTypeSpinner.getSelectedItemPosition();
        int selectedNotificationType = mapPositionToRingType(selectedNotificationPosition); // Utilisation de la méthode de mapping mise à jour
        editor.putInt(PREF_NOTIFICATION_TYPE, selectedNotificationType);

        // Sauvegarder langue
        String selectedLanguage = languageSpinner.getSelectedItem().toString();
        editor.putString("language", selectedLanguage);

        // Sauvegarder crypto
        String selectedCrypto = cryptoSpinner.getSelectedItem().toString();
        editor.putString("crypto", selectedCrypto);

        // Appliquer les changements
        editor.apply();
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();

        Log.d("SettingsActivity",
                "Saved settings - Refresh Interval: " + prefs.getInt("refresh_interval", -1)
                        + ", Tolerance Percentage: " + prefs.getFloat("tolerance_percentage", -1.0f)
                        + ", Currency: " + prefs.getString("currency", "null")
                        + ", Notification Type: " + prefs.getInt(PREF_NOTIFICATION_TYPE, -1)
                        + ", Language: " + prefs.getString("language", "null")
                        + ", Crypto: " + prefs.getString("crypto", "null"));
    }


    private int mapPositionToRingType(int position) {
        switch (position) {
            case 0: return NOTIF_TYPE_BEEP;
            case 1: return NOTIF_TYPE_LED;
            case 2: return NOTIF_TYPE_VIBRATE;
            case 3: return NOTIF_TYPE_LED_VIBRATE;
            case 4: return NOTIF_TYPE_VIB_LED_BEEP;
            default: return DEFAULT_NOTIFICATION_TYPE;
        }
    }


    private int mapRingTypeToPosition(int ringType) {
        switch (ringType) {
            case NOTIF_TYPE_BEEP: return 0;
            case NOTIF_TYPE_LED: return 1;
            case NOTIF_TYPE_VIBRATE: return 2;
            case NOTIF_TYPE_LED_VIBRATE: return 3;
            case NOTIF_TYPE_VIB_LED_BEEP: return 4;
            default: return mapRingTypeToPosition(DEFAULT_NOTIFICATION_TYPE);
        }
    }

    private void logLoadedPrefs() {
        int currentInterval = prefs.getInt("refresh_interval", -1);
        float currentTolerance = prefs.getFloat("tolerance_percentage", -1.0f);
        String currentCurrency = prefs.getString("currency", "N/A");
        int currentNotificationType = prefs.getInt(PREF_NOTIFICATION_TYPE, -1);
        String currentLanguage = prefs.getString("language", "N/A");
        String currentCrypto = prefs.getString("crypto", "N/A");

        Log.d("SettingsActivity",
                "Loaded settings - Refresh Interval: " + currentInterval
                        + ", Tolerance Percentage: " + currentTolerance
                        + ", Currency: " + currentCurrency
                        + ", Notification Type: " + currentNotificationType
                        + ", Language: " + currentLanguage
                        + ", Crypto: " + currentCrypto);
    }
}