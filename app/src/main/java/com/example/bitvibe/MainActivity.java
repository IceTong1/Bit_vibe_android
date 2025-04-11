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

import com.example.bitvibe.bracelet.BraceletConnectActivity;

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
    private double lastPrice = -1;
    private int minInterval;
    private boolean asBeenInitialized = false;
    private static boolean isServiceRunning = false;
    private SharedPreferences prefs;

    private static final String LEFT_BRACELET_MAC = "BC:57:29:13:FA:DE";
    private static final String RIGHT_BRACELET_MAC = "BC:57:29:13:FA:E0";
    private static final int RING_TYPE_VIBRATE = 4;

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

        Intent serviceIntent = new Intent(this, BluetoothConnectionService.class);
        try {
            Log.d(TAG, "Tentative de démarrage en foreground du BluetoothConnectionService.");
            ContextCompat.startForegroundService(this, serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Impossible de démarrer le service en foreground, tentative avec startService", e);
            startService(serviceIntent);
        }
        Log.d(TAG, "Tentative de liaison au BluetoothConnectionService.");
        bindService(serviceIntent, mBluetoothConnection, Context.BIND_AUTO_CREATE);
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
                Log.w(TAG, "Service non lié ou déjà délié lors de onStop.", e);
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
        boolean isAlarmOn = prefs.getBoolean("is_alarm_on", false);
        if (isAlarmOn && isServiceRunning) {
            Intent serviceIntent = new Intent(this, AlarmCheckService.class);
            stopService(serviceIntent);
            isServiceRunning = false;
            Log.d(TAG, "AlarmCheckService arrêté depuis MainActivity onDestroy");
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
        if (binanceApi == null) return;
        String selectedCrypto = prefs.getString("crypto", "DOGEUSDT");
        Call<BinancePriceResponse> call = binanceApi.getBitcoinPrice(selectedCrypto);

        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(@NonNull Call<BinancePriceResponse> call, @NonNull Response<BinancePriceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    double currentPrice = response.body().getPrice();
                    String symbol = response.body().getSymbol();
                    Log.d(TAG, symbol + " : currentPrice = " + currentPrice + ", lastPrice = " + lastPrice);
                    if (bitcoinPriceTextView != null) {
                        bitcoinPriceTextView.setText(String.format(symbol + " : %.2f", currentPrice));
                    }
                    float tolerancePercentage = prefs.getFloat("tolerance_percentage", 0.01f);
                    if (lastPrice != -1) {
                        double percentageChange = ((currentPrice - lastPrice) / lastPrice) * 100;
                        if (Math.abs(percentageChange) > tolerancePercentage) {
                            if (mBound && mBluetoothService != null) {
                                if (percentageChange > 0) {
                                    Log.d(TAG, "Déclenchement vibration (Hausse) sur bracelet DROIT");
                                    mBluetoothService.ringBeacon(RIGHT_BRACELET_MAC, RING_TYPE_VIBRATE);
                                } else {
                                    Log.d(TAG, "Déclenchement vibration (Baisse) sur bracelet GAUCHE");
                                    mBluetoothService.ringBeacon(LEFT_BRACELET_MAC, RING_TYPE_VIBRATE);
                                }
                            } else {
                                Log.w(TAG, "Service Bluetooth non lié, impossible de faire vibrer les bracelets.");
                            }
                            Log.d(TAG, (percentageChange > 0 ? "Hausse" : "Baisse") + " détectée (" + String.format(java.util.Locale.US, "%.2f", percentageChange) + "%)");
                            lastPrice = currentPrice;
                        } else {
                            Log.d(TAG, "Prix stable (" + String.format(java.util.Locale.US, "%.2f", percentageChange) + "%)");
                        }
                    } else {
                        lastPrice = currentPrice;
                    }
                } else {
                    Log.e(TAG, "Erreur API : " + response.code());
                    if (bitcoinPriceTextView != null) bitcoinPriceTextView.setText("Erreur API : " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<BinancePriceResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Échec requête API : " + t.getMessage(), t);
                if (bitcoinPriceTextView != null) bitcoinPriceTextView.setText("API Connection Failed");
            }
        });
    }

    private void initializeDefaultPrefs() {
        SharedPreferences.Editor editor = prefs.edit();
        if (!prefs.contains("refresh_interval")) editor.putInt("refresh_interval", 5);
        if (!prefs.contains("notification_type")) editor.putInt("notification_type", 6);
        if (!prefs.contains("currency")) editor.putString("currency", "USD");
        if (!prefs.contains("language")) editor.putString("language", "fr");
        if (!prefs.contains("tolerance_percentage")) editor.putFloat("tolerance_percentage", 0.01f);
        editor.apply();
    }

    private void logLoadedPrefs() {
        minInterval = prefs.getInt("refresh_interval", -1);
        int notificationType = prefs.getInt("notification_type", -1);
        String currency = prefs.getString("currency", "N/A");
        String language = prefs.getString("language", "N/A");
        float tolerancePercentage = prefs.getFloat("tolerance_percentage", -1.0f);
        boolean isAlarmOn = prefs.getBoolean("is_alarm_on", false);
        Log.d(TAG, "VALEURS CHARGEES: refresh=" + minInterval + "\n"
                + ", notificationType=" + notificationType + "\n"
                + ", currency=" + currency + "\n"
                + ", lang=" + language + "\n"
                + ", tolerance=" + tolerancePercentage + "\n"
                + ", alarmOn=" + isAlarmOn);
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
            Log.d(TAG, "Initialisation boucle prix");
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
        boolean isAlarmOn = prefs.getBoolean("is_alarm_on", false);
        Intent serviceIntent = new Intent(this, AlarmCheckService.class);

        if (isAlarmOn) {
            if (!isServiceRunning) {
                try {
                    ContextCompat.startForegroundService(this, serviceIntent);
                    isServiceRunning = true;
                    Log.d(TAG, "AlarmCheckService démarré depuis MainActivity onResume");
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
                Log.d(TAG, "AlarmCheckService arrêté depuis MainActivity onResume (alarme OFF)");
            } else {
                Log.d(TAG, "AlarmCheckService déjà arrêté.");
            }
        }
    }

}