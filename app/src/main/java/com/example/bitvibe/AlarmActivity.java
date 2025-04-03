package com.example.bitvibe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AlarmActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "BitVibePrefs";
    private static final String TRIGGER_PRICE_KEY = "trigger_price";
    private static final String IS_ABOVE_KEY = "is_above";
    private static final String CURRENCY_KEY = "currency";
    private static final String TAG = "AlarmActivity";

    private EditText triggerPriceEditText;
    private Switch isAboveSwitch;
    private Spinner currencySpinner;
    private Button setAlarmButton;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Bind UI elements
        triggerPriceEditText = findViewById(R.id.triggerPriceEditText);
        isAboveSwitch = findViewById(R.id.isAboveSwitch);
        currencySpinner = findViewById(R.id.currencySpinner);
        setAlarmButton = findViewById(R.id.setAlarmButton);

        // Set up the currency spinner
        ArrayAdapter<CharSequence> currencyAdapter = ArrayAdapter.createFromResource(this,
                R.array.currency_array, android.R.layout.simple_spinner_item);
        currencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        currencySpinner.setAdapter(currencyAdapter);

        // Load saved alarm if it exists
        loadAlarm();

        // Set Alarm Button click listener
        setAlarmButton.setOnClickListener(v -> saveAlarm());
    }

    // Load the alarm from SharedPreferences
    private void loadAlarm() {
        if (sharedPreferences.contains(TRIGGER_PRICE_KEY) && sharedPreferences.contains(IS_ABOVE_KEY) && sharedPreferences.contains(CURRENCY_KEY)) {
            double triggerPrice = Double.parseDouble(sharedPreferences.getString(TRIGGER_PRICE_KEY, "0.0"));
            boolean isAbove = sharedPreferences.getBoolean(IS_ABOVE_KEY, false);
            String currency = sharedPreferences.getString(CURRENCY_KEY, "USD");

            triggerPriceEditText.setText(String.valueOf(triggerPrice));
            isAboveSwitch.setChecked(isAbove);
            int spinnerPosition = ((ArrayAdapter<String>) currencySpinner.getAdapter()).getPosition(currency);
            currencySpinner.setSelection(spinnerPosition);

            Log.d(TAG, "Alarm loaded: Trigger Price = " + triggerPrice + ", Is Above = " + isAbove + ", Currency : " + currency);
        }
    }

    // Save the alarm to SharedPreferences
    private void saveAlarm() {
        String priceStr = triggerPriceEditText.getText().toString();
        boolean isAbove = isAboveSwitch.isChecked();
        String currency = currencySpinner.getSelectedItem().toString();

        if (priceStr.isEmpty()) {
            Toast.makeText(this, "Please enter a trigger price", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double triggerPrice = Double.parseDouble(priceStr);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(TRIGGER_PRICE_KEY, String.valueOf(triggerPrice));
            editor.putBoolean(IS_ABOVE_KEY, isAbove);
            editor.putString(CURRENCY_KEY, currency);
            editor.apply();

            Toast.makeText(this, "Alarm saved", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Alarm saved: Trigger Price = " + triggerPrice + ", Is Above = " + isAbove + ", Currency : " + currency);

            // Here you will need to start the service or something to compare with the current price.

        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing trigger price: " + priceStr, e);
            Toast.makeText(this, "Invalid trigger price", Toast.LENGTH_SHORT).show();
        }
    }
}