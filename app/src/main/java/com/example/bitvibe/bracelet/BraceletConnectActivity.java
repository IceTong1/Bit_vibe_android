package com.example.bitvibe.bracelet;

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

    private static final String LEFT_BRACELET_MAC = "BC:57:29:13:FA:DE";
    private static final String RIGHT_BRACELET_MAC = "BC:57:29:13:FA:E0";
    private static final String DEFAULT_PASSWORD = "0000000000000000";

    // --- Service ---
    private BluetoothConnectionService mService;
    private boolean mBound = false;

    // --- Scan ---
    private KBeaconsMgr mBeaconMgrForScan;
    private HashMap<String, KBeacon> mDiscoveredBeacons = new HashMap<>();

    // --- UI Elements ---
    private Button btnScan;
    private Button btnConnectLeft, btnConnectRight;
    private TextView tvStatusLeft, tvStatusRight;
    private TextView tvMacLeft, tvMacRight;
    private Button btnBeepLeft, btnLedLeft, btnVibrateLeft, btnLedVibrateLeft, btnStopLeft;
    private Button btnBeepRight, btnLedRight, btnVibrateRight, btnLedVibrateRight, btnStopRight;

    // Ring Types
    private static final int RING_TYPE_STOP = 0;
    private static final int RING_TYPE_BEEP = 1;
    private static final int RING_TYPE_LED = 2;
    private static final int RING_TYPE_VIBRATE = 4;

    // --- ServiceConnection ---
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            BluetoothConnectionService.LocalBinder binder = (BluetoothConnectionService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            Log.d(TAG, "Service connected");
            updateUiWithCurrentState();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "Service disconnected");
            mBound = false;
            mService = null;
            setLeftButtonsEnabled(false);
            setRightButtonsEnabled(false);
            updateStatusUi(LEFT_BRACELET_MAC, KBConnState.Disconnected, 0);
            updateStatusUi(RIGHT_BRACELET_MAC, KBConnState.Disconnected, 0);
        }
    };

    // --- BroadcastReceiver ---
    private final BroadcastReceiver mConnStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothConnectionService.ACTION_CONN_STATE_CHANGED.equals(action)) {
                String mac = intent.getStringExtra(BluetoothConnectionService.EXTRA_CONN_STATE_MAC);
                int stateOrdinal = intent.getIntExtra(BluetoothConnectionService.EXTRA_CONN_STATE, KBConnState.Disconnected.ordinal());
                KBConnState state = KBConnState.values()[stateOrdinal];
                int reason = intent.getIntExtra(BluetoothConnectionService.EXTRA_CONN_REASON, 0);
                Log.d(TAG, "Broadcast received: Update state for " + mac + " -> " + state);
                updateStatusUi(mac, state, reason);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bracelet_connect);

        bindUiElements();
        setupButtonClickListeners();

        tvMacLeft.setText("MAC: " + LEFT_BRACELET_MAC);
        tvMacRight.setText("MAC: " + RIGHT_BRACELET_MAC);

        mBeaconMgrForScan = KBeaconsMgr.sharedBeaconManager(this);
        if (mBeaconMgrForScan == null) {
            showToast("Device does not support Bluetooth LE."); // Modifié
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

        btnBeepLeft.setOnClickListener(v -> triggerRingCommand(LEFT_BRACELET_MAC, "Left", RING_TYPE_BEEP)); // Modifié
        btnLedLeft.setOnClickListener(v -> triggerRingCommand(LEFT_BRACELET_MAC, "Left", RING_TYPE_LED)); // Modifié
        btnVibrateLeft.setOnClickListener(v -> triggerRingCommand(LEFT_BRACELET_MAC, "Left", RING_TYPE_VIBRATE)); // Modifié
        btnLedVibrateLeft.setOnClickListener(v -> triggerRingCommand(LEFT_BRACELET_MAC, "Left", RING_TYPE_LED | RING_TYPE_VIBRATE)); // Modifié
        btnStopLeft.setOnClickListener(v -> triggerRingCommand(LEFT_BRACELET_MAC, "Left", RING_TYPE_STOP)); // Modifié

        btnBeepRight.setOnClickListener(v -> triggerRingCommand(RIGHT_BRACELET_MAC, "Right", RING_TYPE_BEEP)); // Modifié
        btnLedRight.setOnClickListener(v -> triggerRingCommand(RIGHT_BRACELET_MAC, "Right", RING_TYPE_LED)); // Modifié
        btnVibrateRight.setOnClickListener(v -> triggerRingCommand(RIGHT_BRACELET_MAC, "Right", RING_TYPE_VIBRATE)); // Modifié
        btnLedVibrateRight.setOnClickListener(v -> triggerRingCommand(RIGHT_BRACELET_MAC, "Right", RING_TYPE_LED | RING_TYPE_VIBRATE)); // Modifié
        btnStopRight.setOnClickListener(v -> triggerRingCommand(RIGHT_BRACELET_MAC, "Right", RING_TYPE_STOP)); // Modifié
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, BluetoothConnectionService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "Binding to service...");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
            mService = null;
            Log.d(TAG, "Unbinding from service...");
        }
        stopScan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(BluetoothConnectionService.ACTION_CONN_STATE_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mConnStateReceiver, filter);
        Log.d(TAG, "BroadcastReceiver registered.");
        if (mBound) {
            updateUiWithCurrentState();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mConnStateReceiver);
        Log.d(TAG, "BroadcastReceiver unregistered.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Activity destroyed.");
        stopScan();
    }

    // --- UI Update based on received state ---
    private void updateUiWithCurrentState() {
        if (!mBound || mService == null) return;
        Log.d(TAG, "Updating UI with current service state");
        updateStatusUi(LEFT_BRACELET_MAC, mService.getBeaconState(LEFT_BRACELET_MAC), 0);
        updateStatusUi(RIGHT_BRACELET_MAC, mService.getBeaconState(RIGHT_BRACELET_MAC), 0);
    }

    private void updateStatusUi(String mac, KBConnState state, int reason) {
        boolean isLeft = LEFT_BRACELET_MAC.equalsIgnoreCase(mac);
        boolean isRight = RIGHT_BRACELET_MAC.equalsIgnoreCase(mac);

        if (!isLeft && !isRight) return;

        TextView statusTv = isLeft ? tvStatusLeft : tvStatusRight;
        Button connectBtn = isLeft ? btnConnectLeft : btnConnectRight;
        String deviceLabel = isLeft ? "Left" : "Right"; // Modifié

        switch (state) {
            case Connected:
                statusTv.setText("Connected"); // Modifié
                connectBtn.setEnabled(true);
                connectBtn.setText("Disconnect " + deviceLabel); // Modifié
                if (isLeft) setLeftButtonsEnabled(true); else setRightButtonsEnabled(true);
                break;
            case Disconnected:
                String reasonMsg;
                if (reason == KBConnectionEvent.ConnManualDisconnecting) { reasonMsg = "Manual Disconn."; } // Modifié
                else if (reason == KBConnectionEvent.ConnTimeout) { reasonMsg = "Fail: Timeout"; } // Modifié
                else if (reason == KBConnectionEvent.ConnAuthFail) { reasonMsg = "Fail: Auth"; } // Modifié
                else if (reason == KBConnectionEvent.ConnServiceNotSupport) { reasonMsg = "Fail: KKM Service not found";} // Modifié
                else if (reason == KBConnectionEvent.ConnException) { reasonMsg = "Fail: BT Exception (" + reason + ")";} // Modifié
                else { reasonMsg = "Disconnected (" + reason + ")"; } // Modifié
                statusTv.setText(reasonMsg);
                connectBtn.setEnabled(true);
                connectBtn.setText("Connect " + deviceLabel); // Modifié
                if (isLeft) setLeftButtonsEnabled(false); else setRightButtonsEnabled(false);
                break;
            case Connecting:
                statusTv.setText("Connecting..."); // Modifié
                connectBtn.setEnabled(false);
                if (isLeft) setLeftButtonsEnabled(false); else setRightButtonsEnabled(false);
                break;
            case Disconnecting:
                statusTv.setText("Disconnecting..."); // Modifié
                connectBtn.setEnabled(false);
                if (isLeft) setLeftButtonsEnabled(false); else setRightButtonsEnabled(false);
                break;
        }
    }

    // --- Connection/Action Logic via Service ---
    private void handleConnectButton(String macAddress) {
        if (!checkPermissionsAndBluetoothBeforeAction()) return;
        if (!mBound || mService == null) {
            showToast("Service not connected."); // Modifié
            return;
        }

        stopScan(); // Stop scanning before connect/disconnect attempt

        KBConnState currentState = mService.getBeaconState(macAddress);
        if (currentState == KBConnState.Connected) {
            Log.d(TAG, "Requesting disconnect for " + macAddress);
            mService.disconnectBeacon(macAddress);
        } else if (currentState == KBConnState.Disconnected) {
            Log.d(TAG, "Requesting connect for " + macAddress);
            mService.connectToBeacon(macAddress, DEFAULT_PASSWORD);
        } else {
            showToast("Operation already in progress for " + macAddress); // Modifié
        }
    }

    private void triggerRingCommand(String macAddress, String label, int ringType) {
        if (!mBound || mService == null) {
            showToast("Service not connected."); // Modifié
            return;
        }

        KBConnState currentState = mService.getBeaconState(macAddress);
        if (currentState != KBConnState.Connected) {
            showToast("Bracelet " + label + " not connected."); // Modifié
            return;
        }

        Log.d(TAG, "Requesting Ring type " + ringType + " for " + macAddress);
        mService.ringBeacon(macAddress, ringType);
    }


    // --- Bluetooth & Permissions Management ---
    private void checkPermissionsAndBluetooth() {
        if (mBeaconMgrForScan != null && !mBeaconMgrForScan.isBluetoothEnable()) {
            try {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (enableBtIntent.resolveActivity(getPackageManager()) != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        showToast("BLUETOOTH_CONNECT permission missing to enable BT."); // Modifié
                        requestNeededPermissions();
                        return;
                    }
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    showToast("Could not request Bluetooth activation."); // Modifié
                    finish();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException during BT enable request", e);
                showToast("Bluetooth permission missing to enable."); // Modifié
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
            // ACCESS_FINE_LOCATION might still be needed depending on use case, keep it for safety
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
                showToast("Permissions granted."); // Modifié
                proceedAfterPermissions();
            } else {
                showToast("Some necessary permissions for scan/connection are missing."); // Modifié
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
                showToast("Bluetooth is required for this application."); // Modifié
                finish();
            }
        }
    }

    private void proceedAfterPermissions() {
        Log.d(TAG, "Ready after checking permissions and Bluetooth.");
    }

    private boolean checkPermissionsAndBluetoothBeforeAction() {
        if (mBeaconMgrForScan == null) return false;
        if (!mBeaconMgrForScan.isBluetoothEnable()) {
            showToast("Please enable Bluetooth."); // Modifié
            try {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (enableBtIntent.resolveActivity(getPackageManager()) != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        showToast("BLUETOOTH_CONNECT permission missing."); // Modifié
                        requestNeededPermissions();
                        return false;
                    }
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    showToast("Could not request Bluetooth activation."); // Modifié
                }
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException checking BT before action", e);
                showToast("Bluetooth permission missing."); // Modifié
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
            showToast("Missing required permissions."); // Modifié
            requestNeededPermissions();
            return false;
        }

        return true;
    }


    // --- Scan ---
    private void startScan() {
        if (!checkPermissionsAndBluetoothBeforeAction()) return;
        mDiscoveredBeacons.clear(); // Clear previous results before starting

        try {
            int result = mBeaconMgrForScan.startScanning();
            if (result != 0) {
                handleScanError(result);
                btnScan.setText("Scan"); // Modifié
            } else {
                showToast("Scan started..."); // Modifié
                btnScan.setText("Stop Scan"); // Modifié
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException starting scan", e);
            showToast("Bluetooth permission missing for scan."); // Modifié
            btnScan.setText("Scan"); // Modifié
            requestNeededPermissions();
        }
    }

    private void stopScan() {
        try {
            if (mBeaconMgrForScan != null && mBeaconMgrForScan.isScanning()) {
                mBeaconMgrForScan.stopScanning();
                showToast("Scan stopped."); // Modifié
                btnScan.setText("Scan"); // Modifié
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException stopping scan", e);
            showToast("Bluetooth permission missing to stop scan."); // Modifié
        } catch (Exception e) { // Catch generic exception just in case
            Log.e(TAG, "Unknown error stopping scan", e);
        }
    }

    private void handleScanError(int result) {
        if (result == KBeaconsMgr.SCAN_ERROR_BLE_NOT_ENABLE) {
            showToast("Please enable Bluetooth."); // Modifié
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (enableBtIntent.resolveActivity(getPackageManager()) != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requestNeededPermissions(); // Request permissions if needed
                } else {
                    try { // Add try-catch for potential SecurityException here too
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    } catch (SecurityException e) {
                        showToast("Bluetooth permission missing."); // Modifié
                        requestNeededPermissions();
                    }
                }
            }
        } else if (result == KBeaconsMgr.SCAN_ERROR_NO_PERMISSION) {
            showToast("Missing permissions for scan."); // Modifié
            requestNeededPermissions();
        } else {
            showToast("Unknown scan error: " + result); // Modifié
        }
    }

    // --- KBeaconsMgrDelegate Callbacks (For Scan) ---
    @Override
    public void onBeaconDiscovered(KBeacon[] beacons) {
        runOnUiThread(() -> {
            for (KBeacon beacon : beacons) {
                if (beacon == null || beacon.getMac() == null) continue;
                mDiscoveredBeacons.put(beacon.getMac(), beacon);
                Log.d(TAG, "Scan: Discovered/Updated " + beacon.getMac() + " RSSI: " + beacon.getRssi());
            }
            // Optionally update a list adapter here if you display scanned devices
        });
    }

    @Override
    public void onCentralBleStateChang(int state) {
        Log.d(TAG, "Scan Delegate: Bluetooth state changed -> " + state);
        if (state == KBeaconsMgr.BLEStatePowerOff) {
            runOnUiThread(() -> {
                showToast("Bluetooth disabled."); // Modifié
                stopScan(); // Stop scan if BT is turned off
                // Ensure UI reflects disconnected state if scan stops
                updateStatusUi(LEFT_BRACELET_MAC, KBConnState.Disconnected, 0);
                updateStatusUi(RIGHT_BRACELET_MAC, KBConnState.Disconnected, 0);
            });
        } else if (state == KBeaconsMgr.BLEStatePowerOn) {
            runOnUiThread(() -> showToast("Bluetooth enabled.")); // Modifié
        }
    }

    @Override
    public void onScanFailed(int errorCode) {
        runOnUiThread(() -> {
            Log.e(TAG, "Scan failed: " + errorCode);
            showToast("Scan failed: " + errorCode); // Modifié
            btnScan.setText("Scan"); // Modifié
        });
    }

    // --- Utility Methods ---
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