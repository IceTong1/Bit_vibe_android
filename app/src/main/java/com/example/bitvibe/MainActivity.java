package com.example.bitvibe;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.bitvibe.BraceletConnectActivity;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    public static BinanceApi binanceApi;

    private TextView bitcoinPriceTextView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private int minInterval;
    private boolean asBeenInitialized = false;
    private static boolean isServiceRunning = false;
    private SharedPreferences prefs;

    private static final String PREF_IS_HIGH_ALARM_ON = "is_high_alarm_on";
    private static final String PREF_IS_LOW_ALARM_ON = "is_low_alarm_on";
    private static final String PREF_IS_VOLATILITY_ALARM_ON = "is_volatility_alarm_on";

    private BluetoothConnectionService mBluetoothService;
    private boolean mBound = false;

    private final ServiceConnection mBluetoothConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            BluetoothConnectionService.LocalBinder binder = (BluetoothConnectionService.LocalBinder) service;
            mBluetoothService = binder.getService();
            mBound = true;
            Log.d(TAG, "BluetoothConnectionService connecté");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "BluetoothConnectionService déconnecté");
            mBound = false;
            mBluetoothService = null;
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bitcoinPriceTextView = findViewById(R.id.bitcoinPriceTextView);

        prefs = getSharedPreferences("BitVibePrefs", MODE_PRIVATE);
        initializeDefaultPrefs();
        logLoadedPrefs();
        setupButtons();
        checkAndRequestBluetoothPermissions();
        setupRetrofit();
        initializeLoop();


        Intent btServiceIntent = new Intent(this, BluetoothConnectionService.class);
        try {
            Log.d(TAG, "Tentative de démarrage en foreground du BluetoothConnectionService.");
            ContextCompat.startForegroundService(this, btServiceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Impossible de démarrer le service BT en foreground, tentative avec startService", e);
            startService(btServiceIntent);
        }
        Log.d(TAG, "Tentative de liaison au BluetoothConnectionService.");
        bindService(btServiceIntent, mBluetoothConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume appelé.");
        minInterval = prefs.getInt("refresh_interval", 5);
        manageAlarmCheckService();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause appelé.");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop appelé.");
        if (mBound) {
            try {
                unbindService(mBluetoothConnection);
                mBound = false;
                mBluetoothService = null;
                Log.d(TAG, "Délié du BluetoothConnectionService.");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Service BT non lié ou déjà délié lors de onStop.", e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
            Log.d(TAG, "onDestroy: Boucle de récupération du prix arrêtée.");
        }

        if (isServiceRunning) {
            Intent serviceIntent = new Intent(this, AlarmCheckService.class);
            stopService(serviceIntent);
            isServiceRunning = false;
            Log.d(TAG, "AlarmCheckService arrêté explicitement depuis MainActivity onDestroy");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            if (grantResults.length > 0) {
                for (int grantResult : grantResults) {
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
            } else {
                allGranted = false;
            }

            if (allGranted) {
                Log.i(TAG, "Toutes les permissions nécessaires (Bluetooth/Localisation) ont été accordées.");
            } else {
                Log.w(TAG, "Certaines permissions Bluetooth/Localisation ont été refusées.");
                Toast.makeText(this, "Missing permissions, some features may not work.", Toast.LENGTH_LONG).show();
            }
        }
    }


    private void fetchBitcoinPrice() {
        if (binanceApi == null) {
            Log.e(TAG, "fetchBitcoinPrice: binanceApi is null.");
            return;
        }
        String selectedCrypto = prefs.getString("crypto", "DOGEUSDT");
        Call<BinancePriceResponse> call = binanceApi.getBitcoinPrice(selectedCrypto);

        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(@NonNull Call<BinancePriceResponse> call, @NonNull Response<BinancePriceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    double currentPrice = response.body().getPrice();
                    String symbol = response.body().getSymbol();

                    if (bitcoinPriceTextView != null) {
                        runOnUiThread(() -> bitcoinPriceTextView.setText(String.format(symbol + " : %.3f", currentPrice)));
                    }

                } else {
                    Log.e(TAG, "Erreur API (fetchBitcoinPrice): " + response.code());
                    if (bitcoinPriceTextView != null) {
                        final int code = response.code();
                        runOnUiThread(() -> bitcoinPriceTextView.setText("Erreur API : " + code));
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<BinancePriceResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Échec requête API (fetchBitcoinPrice): " + t.getMessage(), t);
                if (bitcoinPriceTextView != null) {
                    runOnUiThread(() -> bitcoinPriceTextView.setText("API Connection Failed"));
                }
            }
        });
    }


    private void initializeDefaultPrefs() {
        SharedPreferences.Editor editor = prefs.edit();
        if (!prefs.contains("refresh_interval")) editor.putInt("refresh_interval", 5);
        if (!prefs.contains("notification_type")) editor.putInt("notification_type", 6);
        if (!prefs.contains("currency")) editor.putString("currency", "USD");
        if (!prefs.contains("language")) editor.putString("language", "fr");
        editor.apply();
    }

    private void logLoadedPrefs() {
        minInterval = prefs.getInt("refresh_interval", -1);
        int notificationType = prefs.getInt("notification_type", -1);
        String currency = prefs.getString("currency", "N/A");
        String language = prefs.getString("language", "N/A");
        boolean isHighAlarmOn = prefs.getBoolean(PREF_IS_HIGH_ALARM_ON, false);
        boolean isLowAlarmOn = prefs.getBoolean(PREF_IS_LOW_ALARM_ON, false);
        boolean isVolatilityAlarmOn = prefs.getBoolean(PREF_IS_VOLATILITY_ALARM_ON, false);

        Log.d(TAG, "VALEURS CHARGEES: refresh=" + minInterval
                + ", notificationType=" + notificationType
                + ", currency=" + currency
                + ", lang=" + language
                + ", isHighAlarmOn=" + isHighAlarmOn
                + ", isLowAlarmOn=" + isLowAlarmOn
                + ", isVolatilityAlarmOn=" + isVolatilityAlarmOn);
    }


    private void setupButtons() {
        Button settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
        Button braceletConnectButton = findViewById(R.id.braceletConnectButton);
        braceletConnectButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BraceletConnectActivity.class);
            startActivity(intent);
        });
        Button alarmButton = findViewById(R.id.alarmButton);
        alarmButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AlarmActivity.class);
            startActivity(intent);
        });
    }


    private void checkAndRequestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        },
                        REQUEST_BLUETOOTH_PERMISSIONS);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_BLUETOOTH_PERMISSIONS);
            }
        }
    }

    private void setupRetrofit() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.binance.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        binanceApi = retrofit.create(BinanceApi.class);
    }

    private void initializeLoop() {
        if (!this.asBeenInitialized) {
            this.asBeenInitialized = true;
            Log.d(TAG, "Initialisation boucle affichage prix");
            minInterval = prefs.getInt("refresh_interval", 5);
            runnable = new Runnable() {
                @Override
                public void run() {
                    fetchBitcoinPrice();
                    int currentInterval = prefs.getInt("refresh_interval", 5);
                    handler.postDelayed(this, currentInterval * 1000L);
                }
            };
            handler.post(runnable);
        }
    }


    private void manageAlarmCheckService() {
        boolean isHighAlarmOn = prefs.getBoolean(PREF_IS_HIGH_ALARM_ON, false);
        boolean isLowAlarmOn = prefs.getBoolean(PREF_IS_LOW_ALARM_ON, false);
        boolean isVolatilityAlarmOn = prefs.getBoolean(PREF_IS_VOLATILITY_ALARM_ON, false);

        boolean shouldServiceRun = isHighAlarmOn || isLowAlarmOn || isVolatilityAlarmOn;

        Intent serviceIntent = new Intent(this, AlarmCheckService.class);

        if (shouldServiceRun) {
            if (!isServiceRunning) {
                try {
                    ContextCompat.startForegroundService(this, serviceIntent);
                    isServiceRunning = true;
                    Log.d(TAG, "AlarmCheckService démarré (au moins une alarme est ON)");
                } catch (Exception e) {
                    Log.e(TAG, "Erreur démarrage AlarmCheckService", e);
                }
            } else {
                Log.d(TAG, "AlarmCheckService déjà en cours.");
            }
        }
        else {
            if (isServiceRunning) {
                stopService(serviceIntent);
                isServiceRunning = false;
                Log.d(TAG, "AlarmCheckService arrêté (toutes alarmes sont OFF)");
            } else {
                Log.d(TAG, "AlarmCheckService déjà arrêté.");
            }
        }
    }

}