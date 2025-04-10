package com.example.bitvibe.bracelet; // Assurez-vous que le package est correct

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.bitvibe.BluetoothConnectionService;
import com.example.bitvibe.R;
import com.kkmcn.kbeaconlib2.KBConnState;
import com.kkmcn.kbeaconlib2.KBeacon;
import com.kkmcn.kbeaconlib2.KBeaconsMgr;
import com.kkmcn.kbeaconlib2.KBConnectionEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class BraceletConnectActivity extends AppCompatActivity implements KBeaconsMgr.KBeaconMgrDelegate {

    private static final String TAG = "BraceletConnectAct";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;

    // Adresses MAC des bracelets
    private static final String LEFT_BRACELET_MAC = "BC:57:29:13:FA:DE";
    private static final String RIGHT_BRACELET_MAC = "BC:57:29:13:FA:E0";

    private static final String DEFAULT_PASSWORD = "0000000000000000";

    // --- Variables pour le Service ---
    private BluetoothConnectionService mService;
    private boolean mBound = false;

    // --- KBeaconsMgr pour le SCAN UNIQUEMENT ---
    private KBeaconsMgr mBeaconMgrForScan;
    private HashMap<String, KBeacon> mDiscoveredBeacons = new HashMap<>();


    // --- UI Elements ---
    private Button btnScan;
    private Button btnConnectLeft, btnConnectRight;
    private TextView tvStatusLeft, tvStatusRight;
    private TextView tvMacLeft, tvMacRight;
    private Button btnBeepLeft, btnLedLeft, btnVibrateLeft, btnLedVibrateLeft, btnStopLeft;
    private Button btnBeepRight, btnLedRight, btnVibrateRight, btnLedVibrateRight, btnStopRight;

    // Constantes pour les types de ring
    private static final int RING_TYPE_STOP = 0;
    private static final int RING_TYPE_BEEP = 1;
    private static final int RING_TYPE_LED = 2;
    private static final int RING_TYPE_VIBRATE = 4;


    // --- ServiceConnection pour se lier au service ---
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            BluetoothConnectionService.LocalBinder binder = (BluetoothConnectionService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            Log.d(TAG, "Service connecté");
            updateUiWithCurrentState();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "Service déconnecté");
            mBound = false;
            mService = null;
            setLeftButtonsEnabled(false);
            setRightButtonsEnabled(false);
            updateStatusUi(LEFT_BRACELET_MAC, KBConnState.Disconnected, 0);
            updateStatusUi(RIGHT_BRACELET_MAC, KBConnState.Disconnected, 0);
        }
    };

    // --- BroadcastReceiver pour écouter les changements d'état ---
    private final BroadcastReceiver mConnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothConnectionService.ACTION_CONN_STATE_CHANGED.equals(action)) {
                String mac = intent.getStringExtra(BluetoothConnectionService.EXTRA_CONN_STATE_MAC);
                int stateOrdinal = intent.getIntExtra(BluetoothConnectionService.EXTRA_CONN_STATE, KBConnState.Disconnected.ordinal());
                KBConnState state = KBConnState.values()[stateOrdinal];
                int reason = intent.getIntExtra(BluetoothConnectionService.EXTRA_CONN_REASON, 0);

                Log.d(TAG, "Broadcast reçu: MAJ état pour " + mac + " -> " + state);
                updateStatusUi(mac, state, reason);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bracelet_connect);

        // Initialisation UI
        bindUiElements();
        setupButtonClickListeners();

        // Afficher les MAC cibles
        tvMacLeft.setText("MAC: " + LEFT_BRACELET_MAC);
        tvMacRight.setText("MAC: " + RIGHT_BRACELET_MAC);

        // Initialiser KBeaconsMgr pour le SCAN SEULEMENT
        mBeaconMgrForScan = KBeaconsMgr.sharedBeaconManager(this);
        if (mBeaconMgrForScan == null) {
            showToast("L'appareil ne supporte pas le Bluetooth LE.");
            finish();
            return;
        }
        mBeaconMgrForScan.delegate = this;

        checkPermissionsAndBluetooth();
    }

    private void bindUiElements() {
        btnScan = findViewById(R.id.btnScan);
        btnConnectLeft = findViewById(R.id.btnConnectLeft);
        btnConnectRight = findViewById(R.id.btnConnectRight);
        tvStatusLeft = findViewById(R.id.tvStatusLeft);
        tvStatusRight = findViewById(R.id.tvStatusRight);
        tvMacLeft = findViewById(R.id.tvMacLeft);
        tvMacRight = findViewById(R.id.tvMacRight);
        btnBeepLeft = findViewById(R.id.btnBeepLeft);
        btnLedLeft = findViewById(R.id.btnLedLeft);
        btnVibrateLeft = findViewById(R.id.btnVibrateLeft);
        btnLedVibrateLeft = findViewById(R.id.btnLedVibrateLeft);
        btnStopLeft = findViewById(R.id.btnStopLeft);
        btnBeepRight = findViewById(R.id.btnBeepRight);
        btnLedRight = findViewById(R.id.btnLedRight);
        btnVibrateRight = findViewById(R.id.btnVibrateRight);
        btnLedVibrateRight = findViewById(R.id.btnLedVibrateRight);
        btnStopRight = findViewById(R.id.btnStopRight);
    }

    private void setupButtonClickListeners() {
        btnScan.setOnClickListener(v -> {
            if (mBeaconMgrForScan != null && mBeaconMgrForScan.isScanning()) {
                stopScan();
            } else {
                startScan();
            }
        });

        Button backButton = findViewById(R.id.mainActivityButton);
        backButton.setOnClickListener(v -> {
            finish();
        });

        btnConnectLeft.setOnClickListener(v -> handleConnectButton(LEFT_BRACELET_MAC));
        btnConnectRight.setOnClickListener(v -> handleConnectButton(RIGHT_BRACELET_MAC));

        btnBeepLeft.setOnClickListener(v -> triggerRingCommand(LEFT_BRACELET_MAC, "Gauche", RING_TYPE_BEEP));
        btnLedLeft.setOnClickListener(v -> triggerRingCommand(LEFT_BRACELET_MAC, "Gauche", RING_TYPE_LED));
        btnVibrateLeft.setOnClickListener(v -> triggerRingCommand(LEFT_BRACELET_MAC, "Gauche", RING_TYPE_VIBRATE));
        btnLedVibrateLeft.setOnClickListener(v -> triggerRingCommand(LEFT_BRACELET_MAC, "Gauche", RING_TYPE_LED | RING_TYPE_VIBRATE));
        btnStopLeft.setOnClickListener(v -> triggerRingCommand(LEFT_BRACELET_MAC, "Gauche", RING_TYPE_STOP));

        btnBeepRight.setOnClickListener(v -> triggerRingCommand(RIGHT_BRACELET_MAC, "Droit", RING_TYPE_BEEP));
        btnLedRight.setOnClickListener(v -> triggerRingCommand(RIGHT_BRACELET_MAC, "Droit", RING_TYPE_LED));
        btnVibrateRight.setOnClickListener(v -> triggerRingCommand(RIGHT_BRACELET_MAC, "Droit", RING_TYPE_VIBRATE));
        btnLedVibrateRight.setOnClickListener(v -> triggerRingCommand(RIGHT_BRACELET_MAC, "Droit", RING_TYPE_LED | RING_TYPE_VIBRATE));
        btnStopRight.setOnClickListener(v -> triggerRingCommand(RIGHT_BRACELET_MAC, "Droit", RING_TYPE_STOP));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, BluetoothConnectionService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "Binding au service...");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
            mService = null;
            Log.d(TAG, "Unbinding du service...");
        }
        stopScan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(BluetoothConnectionService.ACTION_CONN_STATE_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mConnStateReceiver, filter);
        Log.d(TAG, "BroadcastReceiver enregistré.");
        if (mBound) {
            updateUiWithCurrentState();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mConnStateReceiver);
        Log.d(TAG, "BroadcastReceiver désenregistré.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Activité détruite.");
        stopScan();
    }

    // --- Mise à jour UI basée sur l'état reçu ---

    private void updateUiWithCurrentState() {
        if (!mBound || mService == null) return;
        Log.d(TAG, "Mise à jour UI avec l'état actuel du service");
        updateStatusUi(LEFT_BRACELET_MAC, mService.getBeaconState(LEFT_BRACELET_MAC), 0);
        updateStatusUi(RIGHT_BRACELET_MAC, mService.getBeaconState(RIGHT_BRACELET_MAC), 0);
    }

    private void updateStatusUi(String mac, KBConnState state, int reason) {
        boolean isLeft = LEFT_BRACELET_MAC.equalsIgnoreCase(mac);
        boolean isRight = RIGHT_BRACELET_MAC.equalsIgnoreCase(mac);

        if (!isLeft && !isRight) return;

        TextView statusTv = isLeft ? tvStatusLeft : tvStatusRight;
        Button connectBtn = isLeft ? btnConnectLeft : btnConnectRight;
        String deviceLabel = isLeft ? "Gauche" : "Droit";

        switch (state) {
            case Connected:
                statusTv.setText("Connecté");
                connectBtn.setEnabled(true);
                connectBtn.setText("Déconnecter " + deviceLabel);
                if (isLeft) setLeftButtonsEnabled(true); else setRightButtonsEnabled(true);
                break;
            case Disconnected:
                String reasonMsg;
                if (reason == KBConnectionEvent.ConnManualDisconnecting) { reasonMsg = "Déconn. manuelle"; }
                else if (reason == KBConnectionEvent.ConnTimeout) { reasonMsg = "Échec: Timeout"; }
                else if (reason == KBConnectionEvent.ConnAuthFail) { reasonMsg = "Échec: Auth"; }
                else if (reason == KBConnectionEvent.ConnServiceNotSupport) { reasonMsg = "Échec: Service KKM non trouvé";}
                else if (reason == KBConnectionEvent.ConnException) { reasonMsg = "Échec: Exception BT (" + reason + ")";}
                else { reasonMsg = "Déconnecté (" + reason + ")"; }
                statusTv.setText(reasonMsg);
                connectBtn.setEnabled(true);
                connectBtn.setText("Connecter " + deviceLabel);
                if (isLeft) setLeftButtonsEnabled(false); else setRightButtonsEnabled(false);
                break;
            case Connecting:
                statusTv.setText("Connexion...");
                connectBtn.setEnabled(false);
                if (isLeft) setLeftButtonsEnabled(false); else setRightButtonsEnabled(false);
                break;
            case Disconnecting:
                statusTv.setText("Déconnexion...");
                connectBtn.setEnabled(false);
                if (isLeft) setLeftButtonsEnabled(false); else setRightButtonsEnabled(false);
                break;
        }
    }

    // --- Logique de Connexion/Action via le Service ---

    private void handleConnectButton(String macAddress) {
        if (!checkPermissionsAndBluetoothBeforeAction()) return;
        if (!mBound || mService == null) {
            showToast("Service non connecté.");
            return;
        }

        stopScan();

        KBConnState currentState = mService.getBeaconState(macAddress);

        if (currentState == KBConnState.Connected) {
            Log.d(TAG, "Demande de déconnexion pour " + macAddress);
            mService.disconnectBeacon(macAddress);
        } else if (currentState == KBConnState.Disconnected) {
            Log.d(TAG, "Demande de connexion pour " + macAddress);
            mService.connectToBeacon(macAddress, DEFAULT_PASSWORD);
        } else {
            showToast("Opération déjà en cours pour " + macAddress);
        }
    }

    private void triggerRingCommand(String macAddress, String label, int ringType) {
        if (!mBound || mService == null) {
            showToast("Service non connecté.");
            return;
        }

        KBConnState currentState = mService.getBeaconState(macAddress);
        if (currentState != KBConnState.Connected) {
            showToast("Bracelet " + label + " non connecté.");
            return;
        }

        Log.d(TAG, "Demande Ring type " + ringType + " pour " + macAddress);
        mService.ringBeacon(macAddress, ringType);
    }


    // --- Gestion Bluetooth & Permissions (identique à l'original) ---

    private void checkPermissionsAndBluetooth() {
        if (mBeaconMgrForScan != null && !mBeaconMgrForScan.isBluetoothEnable()) {
            try {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (enableBtIntent.resolveActivity(getPackageManager()) != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        showToast("Permission BLUETOOTH_CONNECT manquante pour activer BT.");
                        requestNeededPermissions();
                        return;
                    }
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    showToast("Impossible de demander l'activation du Bluetooth.");
                    finish();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException lors de la demande d'activation BT", e);
                showToast("Permission Bluetooth manquante pour activer.");
                requestNeededPermissions();
            }
        } else {
            requestNeededPermissions();
        }
    }

    private void requestNeededPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        boolean permissionsRequired = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
                permissionsRequired = true;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
                permissionsRequired = true;
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
                permissionsRequired = true;
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
                permissionsRequired = true;
            }
        }

        if (permissionsRequired && !permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), REQUEST_PERMISSIONS);
        } else if (!permissionsRequired || permissionsToRequest.isEmpty()) {
            proceedAfterPermissions();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            if (grantResults.length > 0) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
            } else {
                allGranted = false;
            }

            if (allGranted) {
                showToast("Permissions accordées.");
                proceedAfterPermissions();
            } else {
                showToast("Certaines permissions nécessaires au scan/connexion sont manquantes.");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                requestNeededPermissions();
            } else {
                showToast("Le Bluetooth est nécessaire pour cette application.");
                finish();
            }
        }
    }

    private void proceedAfterPermissions() {
        Log.d(TAG, "Prêt après vérification des permissions et du Bluetooth.");
    }

    private boolean checkPermissionsAndBluetoothBeforeAction() {
        if (mBeaconMgrForScan == null) return false;

        if (!mBeaconMgrForScan.isBluetoothEnable()) {
            showToast("Veuillez activer le Bluetooth.");
            try {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (enableBtIntent.resolveActivity(getPackageManager()) != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        showToast("Permission BLUETOOTH_CONNECT manquante.");
                        requestNeededPermissions();
                        return false;
                    }
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    showToast("Impossible de demander l'activation du Bluetooth.");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException lors de la vérification BT avant action", e);
                showToast("Permission Bluetooth manquante.");
                requestNeededPermissions();
            }
            return false;
        }

        boolean permissionsGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsGranted = false;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsGranted = false;
            }
        }

        if (!permissionsGranted) {
            showToast("Permissions manquantes nécessaires.");
            requestNeededPermissions();
            return false;
        }

        return true;
    }


    // --- Scan --- (Reste dans l'activité)
    private void startScan() {
        if (!checkPermissionsAndBluetoothBeforeAction()) return;
        mDiscoveredBeacons.clear();

        try {
            int result = mBeaconMgrForScan.startScanning();
            if (result != 0) {
                handleScanError(result);
                btnScan.setText("Scanner");
            } else {
                showToast("Scan démarré...");
                btnScan.setText("Arrêter Scan");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException au démarrage du scan", e);
            showToast("Permission Bluetooth manquante pour le scan.");
            btnScan.setText("Scanner");
            requestNeededPermissions();
        }
    }

    private void stopScan() {
        try {
            if (mBeaconMgrForScan != null && mBeaconMgrForScan.isScanning()) {
                mBeaconMgrForScan.stopScanning();
                showToast("Scan arrêté.");
                btnScan.setText("Scanner");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException à l'arrêt du scan", e);
            showToast("Permission Bluetooth manquante pour arrêter le scan.");
        } catch (Exception e) {
            Log.e(TAG, "Erreur inconnue à l'arrêt du scan", e);
        }
    }

    private void handleScanError(int result) {
        if (result == KBeaconsMgr.SCAN_ERROR_BLE_NOT_ENABLE) {
            showToast("Veuillez activer le Bluetooth.");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (enableBtIntent.resolveActivity(getPackageManager()) != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requestNeededPermissions();
                } else {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
            }
        } else if (result == KBeaconsMgr.SCAN_ERROR_NO_PERMISSION) {
            showToast("Permissions manquantes pour le scan.");
            requestNeededPermissions();
        } else {
            showToast("Erreur de scan inconnue: " + result);
        }
    }

    // --- Callbacks KBeaconsMgrDelegate (Pour le Scan) ---
    @Override
    public void onBeaconDiscovered(KBeacon[] beacons) {
        runOnUiThread(() -> {
            for (KBeacon beacon : beacons) {
                if (beacon == null || beacon.getMac() == null) continue;
                mDiscoveredBeacons.put(beacon.getMac(), beacon);
                Log.d(TAG, "Scan: Découvert/Maj " + beacon.getMac() + " RSSI: " + beacon.getRssi());
            }
        });
    }

    @Override
    public void onCentralBleStateChang(int state) {
        Log.d(TAG, "Scan Delegate: État Bluetooth changé -> " + state);
        if (state == KBeaconsMgr.BLEStatePowerOff) {
            runOnUiThread(() -> {
                showToast("Bluetooth désactivé.");
                stopScan();
                updateStatusUi(LEFT_BRACELET_MAC, KBConnState.Disconnected, 0);
                updateStatusUi(RIGHT_BRACELET_MAC, KBConnState.Disconnected, 0);
            });
        } else if (state == KBeaconsMgr.BLEStatePowerOn) {
            runOnUiThread(() -> showToast("Bluetooth activé."));
        }
    }

    @Override
    public void onScanFailed(int errorCode) {
        runOnUiThread(() -> {
            Log.e(TAG, "Échec du scan: " + errorCode);
            showToast("Échec du scan: " + errorCode);
            btnScan.setText("Scanner");
        });
    }

    // --- Méthodes Utilitaires ---

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void setLeftButtonsEnabled(boolean enabled) {
        btnBeepLeft.setEnabled(enabled);
        btnLedLeft.setEnabled(enabled);
        btnVibrateLeft.setEnabled(enabled);
        btnLedVibrateLeft.setEnabled(enabled);
        btnStopLeft.setEnabled(enabled);
    }

    private void setRightButtonsEnabled(boolean enabled) {
        btnBeepRight.setEnabled(enabled);
        btnLedRight.setEnabled(enabled);
        btnVibrateRight.setEnabled(enabled);
        btnLedVibrateRight.setEnabled(enabled);
        btnStopRight.setEnabled(enabled);
    }
}