package com.example.bitvibe;

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
    private Spinner vibrationIntensitySpinner;
    private Spinner languageSpinner;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);


        // Get the reference to the button in the layout
        Button backButton = findViewById(R.id.mainActivityButton);
        // Set the click listener
        backButton.setOnClickListener(v -> {
            saveSettings();
            OnBackPressedDispatcher dispatcher = getOnBackPressedDispatcher();
            dispatcher.onBackPressed();
        });


        // Initialize SharedPreferences
        prefs = getSharedPreferences("BitVibePrefs", MODE_PRIVATE); // charger la base de données des preferences (parametres) sauvegardées



        // Bind UI elements
        refreshIntervalEditText = findViewById(R.id.refresh_interval_edittext);
        tolerancePercentageEditText = findViewById(R.id.tolerance_percentage_edittext);
        currencySpinner = findViewById(R.id.currency_spinner);
        vibrationIntensitySpinner = findViewById(R.id.vibration_intensity_spinner);
        languageSpinner = findViewById(R.id.language_spinner);

        // Load current settings
        int currentInterval = prefs.getInt("refresh_interval", 5); // Default: 5 seconds
        refreshIntervalEditText.setText(String.valueOf(currentInterval));

        float currentTolerance = prefs.getFloat("tolerance_percentage", 1.111f); // Default tolerance: 1.111
        tolerancePercentageEditText.setText(String.valueOf(currentTolerance));

        // Set up the currency spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.currency_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        currencySpinner.setAdapter(adapter);

        // Set the current currency in the spinner
        String currentCurrency = prefs.getString("currency", "USD");
        int spinnerPosition = adapter.getPosition(currentCurrency);
        currencySpinner.setSelection(spinnerPosition);

        // Set up the vibration intensity spinner
        ArrayAdapter<CharSequence> intensityAdapter = ArrayAdapter.createFromResource(this,
                R.array.vibration_intensity_array, android.R.layout.simple_spinner_item);
        intensityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vibrationIntensitySpinner.setAdapter(intensityAdapter);

        // Set the current vibration intensity in the spinner
        int currentIntensity = prefs.getInt("vibration_intensity", 2); // Default intensity: 2
        vibrationIntensitySpinner.setSelection(currentIntensity - 1); // Adjust for array index (1-3 becomes 0-2)


        // Set up the language spinner
        ArrayAdapter<CharSequence> languageAdapter = ArrayAdapter.createFromResource(this,
                R.array.language_array, android.R.layout.simple_spinner_item);
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(languageAdapter);

        // Set the current language in the spinner
        String currentLanguage = prefs.getString("language", "english");
        int languagePosition = languageAdapter.getPosition(currentLanguage);
        languageSpinner.setSelection(languagePosition);


        //add edit text for tolerance percentage


        //Log data for debug
        Log.d("SettingsActivity",
                "Loaded settings - Refresh Interval: " + currentInterval
                + " seconds, Currency: " + currentCurrency
                + ", Vibration Intensity: " + currentIntensity
                + ", Language: " + currentLanguage
                + ", Tolerance Percentage: " + currentTolerance);


    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();

        // Save refresh interval
        String intervalStr = refreshIntervalEditText.getText().toString();
        if (!intervalStr.isEmpty()) {
            int interval = Integer.parseInt(intervalStr);
            editor.putInt("refresh_interval", interval);
        }

        // Save tolerance percentage
        String toleranceStr = tolerancePercentageEditText.getText().toString();
        if (!toleranceStr.isEmpty()) {
            float tolerance = Float.parseFloat(toleranceStr);
            editor.putFloat("tolerance_percentage", tolerance);
        }

        // Save currency
        String selectedCurrency = currencySpinner.getSelectedItem().toString();
        editor.putString("currency", selectedCurrency);

        // Save vibration intensity
        int selectedIntensity = vibrationIntensitySpinner.getSelectedItemPosition() + 1; // Adjust back to 1-3
        editor.putInt("vibration_intensity", selectedIntensity);

        // Save language
        String selectedLanguage = languageSpinner.getSelectedItem().toString();
        editor.putString("language", selectedLanguage);

        // Apply changes
        editor.apply();
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();

        Log.d("SettingsActivity",
                "Saved settings - Refresh Interval: " + prefs.getInt("refresh_interval", -1)
                + " seconds, Currency: " + prefs.getString("currency", "null")
                + ", Vibration Intensity: " + prefs.getInt("vibration_intensity", -1)
                + ", Language: " + prefs.getString("language", "null")
                + ", Tolerance Percentage: " + prefs.getFloat("tolerance_percentage", -1.0f));
    }
}

