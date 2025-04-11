package com.example.bitvibe;

import static com.example.bitvibe.MainActivity.binanceApi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedDispatcher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlarmActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "BitVibePrefs";
    private static final String PREF_HIGH_TRIGGER_PRICE = "high_trigger_price";
    private static final String PREF_IS_HIGH_ALARM_ON = "is_high_alarm_on";
    private static final String PREF_LOW_TRIGGER_PRICE = "low_trigger_price";
    private static final String PREF_IS_LOW_ALARM_ON = "is_low_alarm_on";

    private static final String TAG = "AlarmActivity";

    private EditText highTriggerPriceEditText;
    private EditText lowTriggerPriceEditText;
    private Switch highAlarmSwitch;
    private Switch lowAlarmSwitch;
    private TextView currentPriceTextView;
    private TextView currentCryptoSymbolTextView;
    private SharedPreferences sharedPreferences;

    private Handler priceHandler = new Handler(Looper.getMainLooper());
    private Runnable priceRunnable;
    private int priceUpdateIntervalSeconds = 5;

    private BroadcastReceiver alarmStateReceiver;
    private boolean isLoading = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);
        setTitle(getString(R.string.title_activity_alarm));

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
        currentPriceTextView = findViewById(R.id.currentPriceTextView);
        currentCryptoSymbolTextView = findViewById(R.id.currentCryptoSymbolTextView);

        priceUpdateIntervalSeconds = sharedPreferences.getInt("refresh_interval", 5);

        String initialSymbol = sharedPreferences.getString("crypto", "Loading...");
        currentCryptoSymbolTextView.setText(initialSymbol);

        loadAlarmSettings();
        setupListeners();
        setupAlarmStateReceiver();

        priceRunnable = new Runnable() {
            @Override
            public void run() {
                fetchCurrentPrice();
                priceUpdateIntervalSeconds = sharedPreferences.getInt("refresh_interval", 5);
                priceHandler.postDelayed(this, priceUpdateIntervalSeconds * 1000L);
            }
        };
    }

    private void setupListeners() {
        highAlarmSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isLoading) return;
            handleSwitchToggle(highAlarmSwitch, highTriggerPriceEditText, PREF_IS_HIGH_ALARM_ON, PREF_HIGH_TRIGGER_PRICE, isChecked);
        });

        lowAlarmSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isLoading) return;
            handleSwitchToggle(lowAlarmSwitch, lowTriggerPriceEditText, PREF_IS_LOW_ALARM_ON, PREF_LOW_TRIGGER_PRICE, isChecked);
        });

        highTriggerPriceEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!isLoading && highAlarmSwitch.isChecked()) {
                    Log.d(TAG, "High price text changed while alarm was ON. Disabling.");
                    highAlarmSwitch.setChecked(false);
                }
            }
        });

        lowTriggerPriceEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!isLoading && lowAlarmSwitch.isChecked()) {
                    Log.d(TAG, "Low price text changed while alarm was ON. Disabling.");
                    lowAlarmSwitch.setChecked(false);
                }
            }
        });
    }

    private void handleSwitchToggle(Switch targetSwitch, EditText priceEditText, String prefIsOnKey, String prefPriceKey, boolean isChecked) {
        if (isChecked) {
            String priceStr = priceEditText.getText().toString();
            boolean isValidPrice = false;
            if (!priceStr.isEmpty()) {
                try {
                    Double.parseDouble(priceStr);
                    isValidPrice = true;
                    priceEditText.setError(null);
                } catch (NumberFormatException e) {
                    isValidPrice = false;
                    priceEditText.setError("Invalid number format");
                }
            } else {
                priceEditText.setError("Price cannot be empty to activate");
            }

            if (isValidPrice) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(prefIsOnKey, true);
                editor.putString(prefPriceKey, priceStr);
                editor.apply();
                updateSwitchText(targetSwitch, true);
                manageAlarmCheckService();
                Log.d(TAG, "Alarm " + prefIsOnKey + " activated with price " + priceStr);
            } else {
                targetSwitch.post(() -> targetSwitch.setChecked(false));
                Toast.makeText(this, "Please enter a valid price before activating.", Toast.LENGTH_SHORT).show();
            }
        } else {
            saveSwitchState(prefIsOnKey, false);
            updateSwitchText(targetSwitch, false);
            manageAlarmCheckService();
            Log.d(TAG, "Alarm " + prefIsOnKey + " deactivated.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isLoading = true;
        priceHandler.post(priceRunnable);
        Log.d(TAG, "Démarrage fetchCurrentPrice loop in onResume");
        loadAlarmSettings();
        manageAlarmCheckService();
        LocalBroadcastManager.getInstance(this).registerReceiver(alarmStateReceiver,
                new IntentFilter(AlarmCheckService.ACTION_ALARM_STATE_CHANGED));
        Log.d(TAG, "Alarm state receiver registered");
        isLoading = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        priceHandler.removeCallbacks(priceRunnable);
        Log.d(TAG, "Arrêt fetchCurrentPrice loop in onPause");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(alarmStateReceiver);
        Log.d(TAG, "Alarm state receiver unregistered");
    }

    private void setupAlarmStateReceiver() {
        alarmStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && AlarmCheckService.ACTION_ALARM_STATE_CHANGED.equals(intent.getAction())) {
                    String alarmType = intent.getStringExtra(AlarmCheckService.EXTRA_ALARM_TYPE);
                    boolean newState = intent.getBooleanExtra(AlarmCheckService.EXTRA_ALARM_STATE, false);
                    Log.d(TAG, "Received broadcast: Alarm " + alarmType + " state changed to " + newState);
                    runOnUiThread(() -> {
                        isLoading = true;
                        if ("high".equals(alarmType) && !newState) {
                            highAlarmSwitch.setChecked(false);
                            updateSwitchText(highAlarmSwitch, false);
                            Toast.makeText(AlarmActivity.this, "High alarm was triggered and disabled.", Toast.LENGTH_SHORT).show();
                        } else if ("low".equals(alarmType) && !newState) {
                            lowAlarmSwitch.setChecked(false);
                            updateSwitchText(lowAlarmSwitch, false);
                            Toast.makeText(AlarmActivity.this, "Low alarm was triggered and disabled.", Toast.LENGTH_SHORT).show();
                        }
                        isLoading = false;
                    });
                }
            }
        };
    }

    private void loadAlarmSettings() {
        isLoading = true;
        String highPriceStr = sharedPreferences.getString(PREF_HIGH_TRIGGER_PRICE, "");
        boolean isHighOn = sharedPreferences.getBoolean(PREF_IS_HIGH_ALARM_ON, false);
        highTriggerPriceEditText.setText(highPriceStr);
        highAlarmSwitch.setChecked(isHighOn);
        updateSwitchText(highAlarmSwitch, isHighOn);
        String lowPriceStr = sharedPreferences.getString(PREF_LOW_TRIGGER_PRICE, "");
        boolean isLowOn = sharedPreferences.getBoolean(PREF_IS_LOW_ALARM_ON, false);
        lowTriggerPriceEditText.setText(lowPriceStr);
        lowAlarmSwitch.setChecked(isLowOn);
        updateSwitchText(lowAlarmSwitch, isLowOn);
        Log.d(TAG, "Settings loaded: HighPrice=" + highPriceStr + ", HighOn=" + isHighOn +
                ", LowPrice=" + lowPriceStr + ", LowOn=" + isLowOn);
        isLoading = false;
    }

    private void saveSwitchState(String key, boolean value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    private void updateSwitchText(Switch targetSwitch, boolean isChecked) {
        targetSwitch.setText(isChecked ? "ON" : "OFF");
    }

    private void fetchCurrentPrice() {
        if (binanceApi == null) {
            Log.w(TAG, "fetchCurrentPrice: Binance API non initialisée.");
            if (currentPriceTextView != null) {
                runOnUiThread(() -> {
                    currentPriceTextView.setText("API Error");
                    currentCryptoSymbolTextView.setText("?");
                });
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
                    if (currentPriceTextView != null && currentCryptoSymbolTextView != null) {
                        runOnUiThread(() -> {
                            currentCryptoSymbolTextView.setText(symbol);
                            currentPriceTextView.setText(String.format(java.util.Locale.US, "%.3f", currentPrice));
                        });

                    }
                } else {
                    Log.e(TAG, "fetchCurrentPrice - Erreur API : " + response.code());
                    if (currentPriceTextView != null && currentCryptoSymbolTextView != null) {
                        final int responseCode = response.code();
                        runOnUiThread(() -> {
                            currentPriceTextView.setText("API Err:" + responseCode);
                            currentCryptoSymbolTextView.setText("Error");
                        });
                    }
                }
            }
            @Override
            public void onFailure(Call<BinancePriceResponse> call, Throwable t) {
                Log.e(TAG, "fetchCurrentPrice - Échec requête API : " + t.getMessage());
                if (currentPriceTextView != null && currentCryptoSymbolTextView != null) {
                    runOnUiThread(() -> {
                        currentPriceTextView.setText("Network Err");
                        currentCryptoSymbolTextView.setText("?");
                    });
                }
            }
        });
    }

    private void manageAlarmCheckService() {
        boolean isHighAlarmOn = sharedPreferences.getBoolean(PREF_IS_HIGH_ALARM_ON, false);
        boolean isLowAlarmOn = sharedPreferences.getBoolean(PREF_IS_LOW_ALARM_ON, false);
        boolean shouldServiceRun = isHighAlarmOn || isLowAlarmOn;
        Intent serviceIntent = new Intent(this, AlarmCheckService.class);
        if (shouldServiceRun) {
            try {
                ContextCompat.startForegroundService(this, serviceIntent);
                Log.d(TAG, "Tentative de démarrage/maintien de AlarmCheckService (au moins une alarme ON)");
            } catch (Exception e) {
                Log.e(TAG, "Erreur démarrage AlarmCheckService depuis AlarmActivity", e);
            }
        } else {
            stopService(serviceIntent);
            Log.d(TAG, "Tentative d'arrêt de AlarmCheckService (les deux alarmes sont OFF)");
        }
    }
}