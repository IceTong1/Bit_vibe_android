package com.example.bitvibe;

import android.Manifest;
import android.content.BroadcastReceiver;
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.bitvibe.bracelet.BraceletConnectActivity; // Pour les constantes MAC si besoin
import com.kkmcn.kbeaconlib2.KBConnState;
import com.kkmcn.kbeaconlib2.KBeacon;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    // --- Constantes et Variables Existantes ---
    private static final String TAG = "MainActivity";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    public static BinanceApi binanceApi;

    private TextView bitcoinPriceTextView;
    private TextView statusBraceletLeftTextView;
    private TextView statusBraceletRightTextView;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private double lastPrice = -1;
    private int minInterval;
    private boolean asBeenInitialized = false;
    private static boolean isServiceRunning = false;
    private SharedPreferences prefs;

    // --- Variables pour le Service Bluetooth ---
    private BluetoothConnectionService mBluetoothService;
    private boolean mBound = false;
    private KBConnState mLeftBraceletState = KBConnState.Disconnected;
    private KBConnState mRightBraceletState = KBConnState.Disconnected;


    private static final String LEFT_BRACELET_MAC = "BC:57:29:13:FA:DE";
    private static final String RIGHT_BRACELET_MAC = "BC:57:29:13:FA:E0";

    // --- ServiceConnection pour BluetoothConnectionService ---
    private final ServiceConnection mBluetoothConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            BluetoothConnectionService.LocalBinder binder = (BluetoothConnectionService.LocalBinder) service;
            mBluetoothService = binder.getService();
            mBound = true;
            Log.d(TAG, "BluetoothConnectionService connecté");
            updateUiWithCurrentState(); // Mettre à jour l'UI avec les états actuels
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "BluetoothConnectionService déconnecté");
            mBound = false;
            mBluetoothService = null;
            mLeftBraceletState = KBConnState.Disconnected;
            mRightBraceletState = KBConnState.Disconnected;
            updateIndividualStatusTextViews(); // Mettre à jour l'UI
        }
    };

    // --- BroadcastReceiver pour les états de connexion Bluetooth ---
    private final BroadcastReceiver mConnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothConnectionService.ACTION_CONN_STATE_CHANGED.equals(action)) {
                String mac = intent.getStringExtra(BluetoothConnectionService.EXTRA_CONN_STATE_MAC);
                int stateOrdinal = intent.getIntExtra(BluetoothConnectionService.EXTRA_CONN_STATE, KBConnState.Disconnected.ordinal());
                KBConnState state = KBConnState.values()[stateOrdinal];


                Log.d(TAG, "MainActivity: Broadcast reçu pour " + mac + " -> " + state);


                boolean stateChanged = false;
                if (LEFT_BRACELET_MAC.equalsIgnoreCase(mac)) {
                    if (mLeftBraceletState != state) {
                        mLeftBraceletState = state;
                        stateChanged = true;
                    }
                } else if (RIGHT_BRACELET_MAC.equalsIgnoreCase(mac)) {
                    if (mRightBraceletState != state) {
                        mRightBraceletState = state;
                        stateChanged = true;
                    }
                }

                if(stateChanged){
                    updateIndividualStatusTextViews();
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Initialisation UI ---
        bitcoinPriceTextView = findViewById(R.id.bitcoinPriceTextView);

        try {
            statusBraceletLeftTextView = findViewById(R.id.status_bracelet_left);
            statusBraceletRightTextView = findViewById(R.id.status_bracelet_right);
        } catch (Exception e) {
            Log.e(TAG, "Erreur: TextView(s) de statut de bracelet non trouvé(s) dans activity_main.xml.", e);
            statusBraceletLeftTextView = null;
            statusBraceletRightTextView = null;
        }


        prefs = getSharedPreferences("BitVibePrefs", MODE_PRIVATE);
        initializeDefaultPrefs();
        logLoadedPrefs();
        setupButtons();
        checkAndRequestBluetoothPermissions();
        setupRetrofit();
        initializeLoop();


        Intent serviceIntent = new Intent(this, BluetoothConnectionService.class);
        try {
            ContextCompat.startForegroundService(this, serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Impossible de démarrer le service en foreground", e);
            startService(serviceIntent);
        }
        bindService(serviceIntent, mBluetoothConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "Tentative de démarrage et de liaison au BluetoothConnectionService.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        minInterval = prefs.getInt("refresh_interval", 5);
        manageAlarmCheckService(); // Vérifie et démarre/arrête le service d'alarme si nécessaire


        IntentFilter filter = new IntentFilter(BluetoothConnectionService.ACTION_CONN_STATE_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mConnStateReceiver, filter);
        Log.d(TAG, "BroadcastReceiver Bluetooth enregistré.");


        if (mBound) {
            updateUiWithCurrentState();
        } else {
            mLeftBraceletState = KBConnState.Disconnected;
            mRightBraceletState = KBConnState.Disconnected;
            updateIndividualStatusTextViews();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mConnStateReceiver);
        Log.d(TAG, "BroadcastReceiver Bluetooth désenregistré.");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mBluetoothConnection);
            mBound = false;
            mBluetoothService = null;
            Log.d(TAG, "Délié du BluetoothConnectionService.");
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
                Toast.makeText(this, "Permissions manquantes, certaines fonctionnalités peuvent ne pas marcher.", Toast.LENGTH_LONG).show();
            }
        }
    }



    private void initializeDefaultPrefs() {
        SharedPreferences.Editor editor = prefs.edit();
        if (!prefs.contains("refresh_interval")) editor.putInt("refresh_interval", 5);
        if (!prefs.contains("vibration_intensity")) editor.putInt("vibration_intensity", 2);
        if (!prefs.contains("currency")) editor.putString("currency", "USD");
        if (!prefs.contains("language")) editor.putString("language", "fr");
        if (!prefs.contains("tolerance_percentage")) editor.putFloat("tolerance_percentage", 0.01f);
        editor.apply();
    }

    private void logLoadedPrefs() {
        minInterval = prefs.getInt("refresh_interval", -1);
        int intensity = prefs.getInt("vibration_intensity", -1);
        String currency = prefs.getString("currency", "N/A");
        String language = prefs.getString("language", "N/A");
        float tolerancePercentage = prefs.getFloat("tolerance_percentage", -1.0f);
        boolean isAlarmOn = prefs.getBoolean("is_alarm_on", false);
        Log.d(TAG, "VALEURS CHARGEES: refresh=" + minInterval + "\n"
                + ", intensity=" + intensity + "\n"
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
            runnable = new Runnable() {
                @Override
                public void run() {
                    fetchBitcoinPrice();
                    handler.postDelayed(this, minInterval * 1000L);
                }
            };
            handler.post(runnable);
        }
    }




    private void fetchBitcoinPrice() {
        if (binanceApi == null) return;
        Call<BinancePriceResponse> call = binanceApi.getBitcoinPrice();
        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(@NonNull Call<BinancePriceResponse> call, @NonNull Response<BinancePriceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    double currentPrice = response.body().getPrice();
                    String symbol = response.body().getSymbol();
                    Log.d(TAG, symbol + " : currentPrice = " + currentPrice + ", lastPrice = " + lastPrice);
                    if (bitcoinPriceTextView != null) {
                        bitcoinPriceTextView.setText(String.format(symbol + " : " + currentPrice));
                    }
                    float tolerancePercentage = prefs.getFloat("tolerance_percentage", 0.01f);
                    if (lastPrice != -1) {
                        double percentageChange = ((currentPrice - lastPrice) / lastPrice) * 100;
                        if (Math.abs(percentageChange) > tolerancePercentage) {
                            /*
                            if(percentageChange > 0){
                                //TODO : faire vibrer le bracelet droit
                            }else{
                                //TODO : faire vibrer le bracelet gauche
                            }
                            */
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
                if (bitcoinPriceTextView != null) bitcoinPriceTextView.setText("Échec connexion API");
            }
        });
    }

    private void manageAlarmCheckService() {
        boolean isAlarmOn = prefs.getBoolean("is_alarm_on", false);
        Intent serviceIntent = new Intent(this, AlarmCheckService.class);

        // Démarrer seulement si l'alarme est activée et que le service n'est pas déjà en cours
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
        // Arrêter seulement si l'alarme est désactivée et que le service est en cours
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


    /**
     * Met à jour les états locaux et l'UI en interrogeant le service.
     */
    private void updateUiWithCurrentState() {
        if (mBound && mBluetoothService != null) {
            Log.d(TAG, "Mise à jour UI avec état actuel du service");
            mLeftBraceletState = mBluetoothService.getBeaconState(LEFT_BRACELET_MAC);
            mRightBraceletState = mBluetoothService.getBeaconState(RIGHT_BRACELET_MAC);
            updateIndividualStatusTextViews();
        } else {
            Log.w(TAG, "Impossible de mettre à jour l'UI, service non lié");

            mLeftBraceletState = KBConnState.Disconnected;
            mRightBraceletState = KBConnState.Disconnected;
            updateIndividualStatusTextViews();
        }
    }


    /**
     * Met à jour les TextViews affichant l'état individuel des bracelets.
     */
    private void updateIndividualStatusTextViews() {
        runOnUiThread(() -> {
            if (statusBraceletLeftTextView != null) {
                statusBraceletLeftTextView.setText(getStateString(mLeftBraceletState));
                // Optionnel: Changer couleur selon état
                // statusBraceletLeftTextView.setTextColor(getStateColor(mLeftBraceletState));
            } else {
                Log.w(TAG,"statusBraceletLeftTextView est null");
            }

            if (statusBraceletRightTextView != null) {
                statusBraceletRightTextView.setText(getStateString(mRightBraceletState));
                // Optionnel: Changer couleur selon état
                // statusBraceletRightTextView.setTextColor(getStateColor(mRightBraceletState));
            } else {
                Log.w(TAG,"statusBraceletRightTextView est null");
            }
        });
    }

    /**
     * Convertit l'état de connexion en une chaîne de caractères lisible.
     */
    private String getStateString(KBConnState state) {
        if (state == null) return "Inconnu";
        switch (state) {
            case Connected:
                return "Connecté";
            case Connecting:
                return "Connexion...";
            case Disconnecting:
                return "Déconnexion...";
            case Disconnected:
            default:
                return "Déconnecté";
        }
    }


}