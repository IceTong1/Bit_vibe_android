

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

import com.example.bitvibe.BraceletConnectActivity; // Import corrigé précédemment

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
    private double lastPrice = -1; // Pour la logique de vibration basée sur la tolérance
    private int minInterval;
    private boolean asBeenInitialized = false;
    private static boolean isServiceRunning = false; // État local du service d'alarme
    private SharedPreferences prefs;

    // Préférences pour vérifier si le service d'alarme doit tourner
    private static final String PREF_IS_HIGH_ALARM_ON = "is_high_alarm_on";
    private static final String PREF_IS_LOW_ALARM_ON = "is_low_alarm_on";


    private static final String LEFT_BRACELET_MAC = "BC:57:29:13:FA:DE";
    private static final String RIGHT_BRACELET_MAC = "BC:57:29:13:FA:E0";
    private static final int RING_TYPE_VIBRATE = 4; // Utilisé pour la vibration sur variation %

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
        initializeDefaultPrefs(); // Conserver pour les autres prefs (interval, notif_type etc)
        logLoadedPrefs();
        setupButtons();
        checkAndRequestBluetoothPermissions();
        setupRetrofit();
        initializeLoop(); // Pour affichage prix et vibration sur %

        // Démarrage/Liaison Service Bluetooth (inchangé)
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
        minInterval = prefs.getInt("refresh_interval", 5); // Pour boucle affichage prix
        manageAlarmCheckService(); // Gérer le service d'alarme basé sur les DEUX états
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
        // Arrêter le service d'alarme s'il tourne encore (sécurité)
        // La logique de manageAlarmCheckService devrait déjà l'arrêter si besoin, mais double vérification.
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

    // --- fetchBitcoinPrice: Logique pour afficher le prix et vibrer sur variation % (Non liée aux alarmes seuil) ---
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
                    Log.d(TAG, symbol + " : currentPrice = " + currentPrice + ", lastPrice = " + lastPrice);

                    // Mise à jour UI Prix Actuel
                    if (bitcoinPriceTextView != null) {
                        runOnUiThread(() -> bitcoinPriceTextView.setText(String.format(symbol + " : %.2f", currentPrice)));
                    }

                    // Logique de vibration sur variation de Tolérance (%)
                    float tolerancePercentage = prefs.getFloat("tolerance_percentage", 0.01f); // Lire la tolérance
                    if (lastPrice != -1) {
                        double percentageChange = ((currentPrice - lastPrice) / lastPrice) * 100;
                        if (Math.abs(percentageChange) > tolerancePercentage) {
                            if (mBound && mBluetoothService != null) {
                                // Hausse -> Bracelet Droit (RIGHT) selon la logique originale de cette fonction
                                if (percentageChange > 0) {
                                    Log.d(TAG, "Vibration (Hausse > tolérance) sur bracelet DROIT");
                                    mBluetoothService.ringBeacon(RIGHT_BRACELET_MAC, RING_TYPE_VIBRATE);
                                }
                                // Baisse -> Bracelet Gauche (LEFT) selon la logique originale de cette fonction
                                else {
                                    Log.d(TAG, "Vibration (Baisse > tolérance) sur bracelet GAUCHE");
                                    mBluetoothService.ringBeacon(LEFT_BRACELET_MAC, RING_TYPE_VIBRATE);
                                }
                            } else {
                                Log.w(TAG, "Service Bluetooth non lié, impossible de vibrer sur tolérance.");
                            }
                            Log.d(TAG, (percentageChange > 0 ? "Hausse" : "Baisse") + " > tolérance détectée (" + String.format(java.util.Locale.US, "%.2f", percentageChange) + "%)");
                            lastPrice = currentPrice; // Mettre à jour le dernier prix seulement si variation > tolérance
                        } else {
                            Log.d(TAG, "Prix stable (variation < tolérance: " + String.format(java.util.Locale.US, "%.2f", percentageChange) + "%)");
                            // Ne pas mettre à jour lastPrice ici pour comparer à la dernière variation significative
                        }
                    } else {
                        lastPrice = currentPrice; // Initialisation du lastPrice
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

    // --- Initialisation des préférences par défaut (si elles n'existent pas) ---
    private void initializeDefaultPrefs() {
        SharedPreferences.Editor editor = prefs.edit();
        // Conserver les prefs existantes
        if (!prefs.contains("refresh_interval")) editor.putInt("refresh_interval", 5);
        if (!prefs.contains("notification_type")) editor.putInt("notification_type", 6); // 6 = LED + VIB
        if (!prefs.contains("currency")) editor.putString("currency", "USD");
        if (!prefs.contains("language")) editor.putString("language", "fr");
        if (!prefs.contains("tolerance_percentage")) editor.putFloat("tolerance_percentage", 0.01f); // 0.01%
        // Ne pas initialiser les prefs d'alarme ici (high/low price/on), elles sont gérées dans AlarmActivity
        editor.apply();
    }

    // --- Log des préférences chargées (pour debug) ---
    private void logLoadedPrefs() {
        minInterval = prefs.getInt("refresh_interval", -1);
        int notificationType = prefs.getInt("notification_type", -1);
        String currency = prefs.getString("currency", "N/A");
        String language = prefs.getString("language", "N/A");
        float tolerancePercentage = prefs.getFloat("tolerance_percentage", -1.0f);
        // Log aussi les états des nouvelles alarmes
        boolean isHighAlarmOn = prefs.getBoolean(PREF_IS_HIGH_ALARM_ON, false);
        boolean isLowAlarmOn = prefs.getBoolean(PREF_IS_LOW_ALARM_ON, false);

        Log.d(TAG, "VALEURS CHARGEES: refresh=" + minInterval
                + ", notificationType=" + notificationType
                + ", currency=" + currency
                + ", lang=" + language
                + ", tolerance=" + tolerancePercentage
                + ", isHighAlarmOn=" + isHighAlarmOn // Ajout log
                + ", isLowAlarmOn=" + isLowAlarmOn); // Ajout log
    }

    // --- Configuration des boutons de navigation ---
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

    // --- Vérification et demande des permissions Bluetooth/Localisation ---
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

    // --- Configuration de Retrofit pour l'API Binance ---
    private void setupRetrofit() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.binance.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        binanceApi = retrofit.create(BinanceApi.class);
    }

    // --- Initialisation de la boucle pour fetchBitcoinPrice (affichage & vibration %) ---
    private void initializeLoop() {
        if (!this.asBeenInitialized) {
            this.asBeenInitialized = true;
            Log.d(TAG, "Initialisation boucle affichage prix / vibration %");
            minInterval = prefs.getInt("refresh_interval", 5); // Lire l'intervalle
            runnable = new Runnable() {
                @Override
                public void run() {
                    fetchBitcoinPrice(); // Appelle la fonction d'affichage/vibration %
                    // Relire l'intervalle à chaque itération au cas où il change
                    int currentInterval = prefs.getInt("refresh_interval", 5);
                    handler.postDelayed(this, currentInterval * 1000L);
                }
            };
            handler.post(runnable); // Démarrer la boucle
        }
    }

    // --- Gestion du démarrage/arrêt du service AlarmCheckService ---
    // MODIFIÉ POUR VÉRIFIER LES DEUX ALARMES
    private void manageAlarmCheckService() {
        // Lire l'état des deux alarmes depuis les préférences
        boolean isHighAlarmOn = prefs.getBoolean(PREF_IS_HIGH_ALARM_ON, false);
        boolean isLowAlarmOn = prefs.getBoolean(PREF_IS_LOW_ALARM_ON, false);

        // Déterminer si le service doit tourner (si au moins une alarme est ON)
        boolean shouldServiceRun = isHighAlarmOn || isLowAlarmOn;

        Intent serviceIntent = new Intent(this, AlarmCheckService.class);

        if (shouldServiceRun) {
            // Si le service doit tourner mais ne tourne pas encore
            if (!isServiceRunning) {
                try {
                    ContextCompat.startForegroundService(this, serviceIntent);
                    isServiceRunning = true;
                    Log.d(TAG, "AlarmCheckService démarré (au moins une alarme est ON)");
                } catch (Exception e) {
                    Log.e(TAG, "Erreur démarrage AlarmCheckService", e);
                    // Gérer l'erreur si nécessaire (ex: fallback avec startService ?)
                }
            } else {
                Log.d(TAG, "AlarmCheckService déjà en cours.");
            }
        }
        else { // Si le service ne doit pas tourner (les deux alarmes sont OFF)
            // Si le service tourne mais ne devrait plus
            if (isServiceRunning) {
                stopService(serviceIntent);
                isServiceRunning = false;
                Log.d(TAG, "AlarmCheckService arrêté (les deux alarmes sont OFF)");
            } else {
                Log.d(TAG, "AlarmCheckService déjà arrêté.");
            }
        }
    }

}