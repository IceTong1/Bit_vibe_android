package com.example.bitvibe.bracelet;

import com.example.bitvibe.R;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice; // Ajout nécessaire pour getRemoteDevice
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.kkmcn.kbeaconlib2.KBConnState;
import com.kkmcn.kbeaconlib2.KBConnectionEvent;
import com.kkmcn.kbeaconlib2.KBException;
import com.kkmcn.kbeaconlib2.KBCfgPackage.KBCfgCommon;
import com.kkmcn.kbeaconlib2.KBeacon;
import com.kkmcn.kbeaconlib2.KBeaconsMgr;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BraceletConnectActivity extends AppCompatActivity implements KBeaconsMgr.KBeaconMgrDelegate, KBeacon.ConnStateDelegate {

    private static final String TAG = "KBeaconVibrationTest";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;

    // !! Adresses MAC des bracelets (mises à jour depuis l'image) !!
    private static final String LEFT_BRACELET_MAC = "BC:57:29:13:FA:DE"; // Left_551228
    private static final String RIGHT_BRACELET_MAC = "BC:57:29:13:FA:E0"; // Right_551230

    private static final String DEFAULT_PASSWORD = "0000000000000000";

    private KBeaconsMgr mBeaconMgr;
    private Button btnScan;
    private Button btnConnectLeft, btnConnectRight;
    private TextView tvStatusLeft, tvStatusRight;
    private TextView tvMacLeft, tvMacRight;

    // Nouveaux boutons de test
    private Button btnBeepLeft, btnLedLeft, btnVibrateLeft, btnLedVibrateLeft, btnStopLeft;
    private Button btnBeepRight, btnLedRight, btnVibrateRight, btnLedVibrateRight, btnStopRight;

    // Garder la map pour que getBeacon() fonctionne
    private HashMap<String, KBeacon> discoveredBeacons = new HashMap<>();

    private KBeacon leftBeacon = null;
    private KBeacon rightBeacon = null;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    // Constantes pour les types de ring
    private static final int RING_TYPE_STOP = 0;
    private static final int RING_TYPE_BEEP = 1;
    private static final int RING_TYPE_LED = 2;
    private static final int RING_TYPE_VIBRATE = 4;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bracelet_connect);

        // Initialisation UI
        btnScan = findViewById(R.id.btnScan);
        btnConnectLeft = findViewById(R.id.btnConnectLeft);
        btnConnectRight = findViewById(R.id.btnConnectRight);
        tvStatusLeft = findViewById(R.id.tvStatusLeft);
        tvStatusRight = findViewById(R.id.tvStatusRight);
        tvMacLeft = findViewById(R.id.tvMacLeft);
        tvMacRight = findViewById(R.id.tvMacRight);

        // Nouveaux boutons GAUCHE
        btnBeepLeft = findViewById(R.id.btnBeepLeft);
        btnLedLeft = findViewById(R.id.btnLedLeft);
        btnVibrateLeft = findViewById(R.id.btnVibrateLeft);
        btnLedVibrateLeft = findViewById(R.id.btnLedVibrateLeft);
        btnStopLeft = findViewById(R.id.btnStopLeft);

        // Nouveaux boutons DROIT
        btnBeepRight = findViewById(R.id.btnBeepRight);
        btnLedRight = findViewById(R.id.btnLedRight);
        btnVibrateRight = findViewById(R.id.btnVibrateRight);
        btnLedVibrateRight = findViewById(R.id.btnLedVibrateRight);
        btnStopRight = findViewById(R.id.btnStopRight);


        // Afficher les MAC cibles
        tvMacLeft.setText("MAC: " + LEFT_BRACELET_MAC);
        tvMacRight.setText("MAC: " + RIGHT_BRACELET_MAC);

        // Initialisation Beacon Manager
        mBeaconMgr = KBeaconsMgr.sharedBeaconManager(this);
        if (mBeaconMgr == null) {
            showToast("L'appareil ne supporte pas le Bluetooth LE.");
            finish();
            return;
        }
        mBeaconMgr.delegate = this;

        // Listeners des boutons principaux
        btnScan.setOnClickListener(v -> {
            if (mBeaconMgr != null && mBeaconMgr.isScanning()) {
                stopScan();
            } else {
                startScan();
            }
        });

        btnConnectLeft.setOnClickListener(v -> handleConnectButton(LEFT_BRACELET_MAC, true));
        btnConnectRight.setOnClickListener(v -> handleConnectButton(RIGHT_BRACELET_MAC, false));

        // Listeners pour les tests d'alerte GAUCHE
        btnBeepLeft.setOnClickListener(v -> triggerRingDevice(leftBeacon, "Gauche", RING_TYPE_BEEP));
        btnLedLeft.setOnClickListener(v -> triggerRingDevice(leftBeacon, "Gauche", RING_TYPE_LED));
        btnVibrateLeft.setOnClickListener(v -> triggerRingDevice(leftBeacon, "Gauche", RING_TYPE_VIBRATE));
        btnLedVibrateLeft.setOnClickListener(v -> triggerRingDevice(leftBeacon, "Gauche", RING_TYPE_LED | RING_TYPE_VIBRATE)); // Combinaison LED + Vibreur
        btnStopLeft.setOnClickListener(v -> triggerRingDevice(leftBeacon, "Gauche", RING_TYPE_STOP));


        // Listeners pour les tests d'alerte DROIT
        btnBeepRight.setOnClickListener(v -> triggerRingDevice(rightBeacon, "Droit", RING_TYPE_BEEP));
        btnLedRight.setOnClickListener(v -> triggerRingDevice(rightBeacon, "Droit", RING_TYPE_LED));
        btnVibrateRight.setOnClickListener(v -> triggerRingDevice(rightBeacon, "Droit", RING_TYPE_VIBRATE));
        btnLedVibrateRight.setOnClickListener(v -> triggerRingDevice(rightBeacon, "Droit", RING_TYPE_LED | RING_TYPE_VIBRATE)); // Combinaison LED + Vibreur
        btnStopRight.setOnClickListener(v -> triggerRingDevice(rightBeacon, "Droit", RING_TYPE_STOP));

        // Vérifier les permissions et le Bluetooth au démarrage
        checkPermissionsAndBluetooth();
    }

    // --- Gestion Bluetooth & Permissions ---

    private void checkPermissionsAndBluetooth() {
        if (mBeaconMgr != null && !mBeaconMgr.isBluetoothEnable()) {
            try {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (enableBtIntent.resolveActivity(getPackageManager()) != null) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        }

        if (permissionsRequired && !permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), REQUEST_PERMISSIONS);
        } else if (!permissionsRequired) {
            proceedAfterPermissions();
        } else { // permissionsRequired est true mais permissionsToRequest est vide => déjà accordées
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
                showToast("Certaines permissions sont nécessaires.");
                // finish(); // Optionnel: fermer l'app si les permissions sont critiques
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                requestNeededPermissions(); // Bluetooth activé, vérifier/demander les permissions
            } else {
                showToast("Le Bluetooth est nécessaire pour cette application.");
                finish();
            }
        }
    }

    private void proceedAfterPermissions() {
        Log.d(TAG, "Prêt après vérification des permissions.");
        // Aucune action automatique ici, l'utilisateur doit scanner/connecter
    }

    private boolean checkPermissionsAndBluetoothBeforeAction() {
        if (mBeaconMgr == null) return false;

        if (!mBeaconMgr.isBluetoothEnable()) {
            showToast("Veuillez activer le Bluetooth.");
            try {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (enableBtIntent.resolveActivity(getPackageManager()) != null) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsGranted = false;
            }
        }

        if (!permissionsGranted) {
            showToast("Permissions manquantes.");
            requestNeededPermissions();
            return false;
        }
        return true;
    }


    // --- Scan ---

    private void startScan() {
        if (!checkPermissionsAndBluetoothBeforeAction()) return;
        try {
            int result = mBeaconMgr.startScanning();
            if (result != 0) {
                if (result == KBeaconsMgr.SCAN_ERROR_BLE_NOT_ENABLE) {
                    showToast("Veuillez activer le Bluetooth.");
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    if (enableBtIntent.resolveActivity(getPackageManager()) != null) {
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    }
                } else if (result == KBeaconsMgr.SCAN_ERROR_NO_PERMISSION) {
                    showToast("Permissions manquantes pour le scan.");
                    requestNeededPermissions();
                } else {
                    showToast("Erreur de scan inconnue: " + result);
                }
                btnScan.setText("Scanner"); // Remettre le texte en cas d'erreur
            } else {
                showToast("Scan démarré...");
                btnScan.setText("Arrêter Scan");
                mHandler.postDelayed(this::stopScan, 10000); // Arrête après 10s
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
            if (mBeaconMgr != null && mBeaconMgr.isScanning()) {
                mBeaconMgr.stopScanning();
                showToast("Scan arrêté.");
                btnScan.setText("Scanner");
                mHandler.removeCallbacksAndMessages(null); // Annule l'arrêt auto
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException à l'arrêt du scan", e);
            showToast("Permission Bluetooth manquante pour arrêter le scan.");
        }
    }

    @Override
    public void onBeaconDiscovered(KBeacon[] beacons) {
        runOnUiThread(() -> {
            for (KBeacon beacon : beacons) {
                if (beacon == null || beacon.getMac() == null) continue;
                discoveredBeacons.put(beacon.getMac(), beacon);
                Log.d(TAG, "Discovered/Updated: " + beacon.getMac() + " Name: " + beacon.getName());
            }
        });
    }

    @Override
    public void onCentralBleStateChang(int i) {
        runOnUiThread(() -> {
            if (i == KBeaconsMgr.BLEStatePowerOff) {
                showToast("Bluetooth désactivé.");
                stopScan();
                updateStatusLeft("Déconnecté (BT off)");
                updateStatusRight("Déconnecté (BT off)");
                setLeftButtonsEnabled(false);
                setRightButtonsEnabled(false);
                btnConnectLeft.setText("Connecter Gauche");
                btnConnectRight.setText("Connecter Droit");
                btnConnectLeft.setEnabled(true); // Réactiver pour pouvoir réessayer
                btnConnectRight.setEnabled(true);
                leftBeacon = null;
                rightBeacon = null;
                discoveredBeacons.clear();

            } else if (i == KBeaconsMgr.BLEStatePowerOn) {
                showToast("Bluetooth activé.");
            }
        });
    }

    @Override
    public void onScanFailed(int errorCode) {
        runOnUiThread(() -> {
            showToast("Échec du scan: " + errorCode);
            btnScan.setText("Scanner");
        });
    }

    // --- Connexion ---

    private void handleConnectButton(String macAddress, boolean isLeftDevice) {
        if (!checkPermissionsAndBluetoothBeforeAction()) return;

        stopScan(); // Arrêter le scan avant de tenter une connexion

        KBeacon currentBeaconRef = isLeftDevice ? leftBeacon : rightBeacon;

        // Si on clique sur "Déconnecter"
        if (currentBeaconRef != null && currentBeaconRef.isConnected()) {
            try {
                currentBeaconRef.disconnect();
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException lors de la déconnexion " + (isLeftDevice ? "Gauche" : "Droit"), e);
                showToast("Permission BT manquante pour déconnecter.");
                requestNeededPermissions();
            }
            return; // Sortir après avoir demandé la déconnexion
        }

        // Si on clique sur "Connecter" et qu'il est en cours de connexion/déconnexion, ne rien faire
        if (currentBeaconRef != null && (currentBeaconRef.getState() == KBConnState.Connecting || currentBeaconRef.getState() == KBConnState.Disconnecting)) {
            showToast("Opération en cours sur " + (isLeftDevice ? "Gauche" : "Droit"));
            return;
        }

        // Tenter de récupérer l'instance KBeacon depuis le manager (après un scan)
        KBeacon targetBeacon = mBeaconMgr.getBeacon(macAddress);

        if (targetBeacon == null) {
            // Si non trouvé par le manager, essayer de créer l'objet BluetoothDevice directement
            BluetoothAdapter adapter = mBeaconMgr.getBluetoothAdapter();
            if (adapter != null && BluetoothAdapter.checkBluetoothAddress(macAddress)) {
                try {
                    BluetoothDevice device = adapter.getRemoteDevice(macAddress);
                    if (device != null) {
                        targetBeacon = new KBeacon(macAddress, this);
                        targetBeacon.attach2Device(device);
                        Log.w(TAG, "Tentative de connexion à " + macAddress + " sans découverte préalable récente.");
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException lors de getRemoteDevice", e);
                    showToast("Permission BT manquante pour obtenir l'appareil.");
                    requestNeededPermissions();
                    return;
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Adresse MAC invalide: " + macAddress, e);
                    showToast("Adresse MAC invalide pour " + (isLeftDevice ? "Gauche" : "Droit"));
                    return;
                }
            }
        }

        if (targetBeacon == null) {
            showToast("Bracelet " + (isLeftDevice ? "Gauche" : "Droit") + " (" + macAddress + ") non trouvé. Veuillez scanner.");
            return;
        }

        Log.d(TAG, "Tentative de connexion à " + (isLeftDevice ? "Gauche" : "Droit") + " - MAC: " + macAddress);
        if (isLeftDevice) {
            leftBeacon = targetBeacon; // Assigner AVANT de connecter
            updateStatusLeft("Connexion...");
            btnConnectLeft.setEnabled(false);
        } else {
            rightBeacon = targetBeacon; // Assigner AVANT de connecter
            updateStatusRight("Connexion...");
            btnConnectRight.setEnabled(false);
        }

        try {
            boolean success = targetBeacon.connect(DEFAULT_PASSWORD, 15000, this); // Timeout 15s
            if (!success) {
                showToast("Échec demande connexion " + (isLeftDevice ? "Gauche" : "Droit"));
                if (isLeftDevice) {
                    updateStatusLeft("Échec init.");
                    btnConnectLeft.setEnabled(true);
                    leftBeacon = null; // Réinitialiser car l'appel a échoué
                } else {
                    updateStatusRight("Échec init.");
                    btnConnectRight.setEnabled(true);
                    rightBeacon = null; // Réinitialiser
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException lors de la connexion à " + macAddress, e);
            showToast("Permission BT manquante pour connecter " + (isLeftDevice ? "Gauche" : "Droit"));
            if (isLeftDevice) {
                updateStatusLeft("Erreur Perm.");
                btnConnectLeft.setEnabled(true);
                leftBeacon = null;
            } else {
                updateStatusRight("Erreur Perm.");
                btnConnectRight.setEnabled(true);
                rightBeacon = null;
            }
            requestNeededPermissions();
        }
    }


    @Override
    public void onConnStateChange(KBeacon beacon, KBConnState state, int reason) {
        runOnUiThread(() -> {
            if (beacon == null || beacon.getMac() == null) return;

            String mac = beacon.getMac();
            boolean isLeft = mac.equalsIgnoreCase(LEFT_BRACELET_MAC);
            boolean isRight = mac.equalsIgnoreCase(RIGHT_BRACELET_MAC);

            // Assigner la référence correcte même si on reçoit le callback après l'avoir mise à null
            KBeacon currentRef = isLeft ? leftBeacon : rightBeacon;
            // Cas spécial : si on reçoit un callback de déconnexion pour un beacon qu'on pensait déjà null
            // ou si le callback concerne un beacon différent de celui actuellement assigné (peut arriver si on connecte/déconnecte rapidement)
            if (currentRef == null || !currentRef.getMac().equalsIgnoreCase(mac)) {
                // Mettre à jour l'UI correspondante basée sur la MAC, même si la référence est nulle/différente
                Log.d(TAG, "Mise à jour UI pour " + mac + " (ref actuelle peut être nulle/différente)");
            }


            String deviceLabel = isLeft ? "Gauche" : "Droit";
            TextView statusTv = isLeft ? tvStatusLeft : tvStatusRight;
            Button connectBtn = isLeft ? btnConnectLeft : btnConnectRight;

            switch (state) {
                case Connected:
                    // On s'assure que la référence est bien celle du beacon connecté
                    if (isLeft) leftBeacon = beacon; else rightBeacon = beacon;
                    showToast("Connecté à " + deviceLabel);
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
                    showToast(deviceLabel + ": " + reasonMsg);
                    statusTv.setText("Déconnecté");
                    connectBtn.setEnabled(true);
                    connectBtn.setText("Connecter " + deviceLabel);
                    if (isLeft) { setLeftButtonsEnabled(false); leftBeacon = null; } // Mettre à null APRES la mise à jour UI
                    else { setRightButtonsEnabled(false); rightBeacon = null; }
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
        });
    }


    // Méthodes pour activer/désactiver les groupes de boutons de test
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

    private void updateStatusLeft(String status) {
        tvStatusLeft.setText(status);
    }

    private void updateStatusRight(String status) {
        tvStatusRight.setText(status);
    }


    // --- Action : Ring/Vibration ---

    private void triggerRingDevice(KBeacon targetBeacon, String label, int ringType) {
        if (targetBeacon == null || !targetBeacon.isConnected()) {
            showToast("Bracelet " + label + " non connecté.");
            return;
        }

        KBCfgCommon commonCfg = targetBeacon.getCommonCfg();
        if (commonCfg == null) {
            Log.w(TAG, "Impossible de vérifier capacités pour " + label + " (getCommonCfg() null). Tentative 'ring'.");
        } else if (!commonCfg.isSupportBeep() && ringType != RING_TYPE_STOP) { // On vérifie 'isSupportBeep' pour toute alerte sauf STOP
            Log.w(TAG, "Bracelet " + label + " (" + targetBeacon.getMac() + ") ne reporte pas 'beep'. Tentative 'ring' (type=" + ringType + ")...");
        }
        // On ne bloque pas ici, on tente la commande.

        JSONObject cmdPara = new JSONObject();
        try {
            cmdPara.put("msg", "ring");
            cmdPara.put("ringTime", ringType == RING_TYPE_STOP ? 0 : 2000); // Durée de 2 secondes, sauf pour STOP
            cmdPara.put("ringType", ringType);

            if ((ringType & RING_TYPE_LED) > 0 || (ringType & RING_TYPE_VIBRATE) > 0) {
                cmdPara.put("ledOn", 100);
                cmdPara.put("ledOff", 100);
            }

            Log.d(TAG, "Envoi commande ring à " + label + " (type=" + ringType + "): " + cmdPara.toString());

            setLeftButtonsEnabled(false);
            setRightButtonsEnabled(false);

            targetBeacon.sendCommand(cmdPara, (success, error) -> runOnUiThread(() -> {
                if (leftBeacon != null && leftBeacon.isConnected()) setLeftButtonsEnabled(true);
                if (rightBeacon != null && rightBeacon.isConnected()) setRightButtonsEnabled(true);

                if (success) {
                    String action = ringType == RING_TYPE_STOP ? "arrêtée" : "envoyée";
                    showToast("Alerte " + label + " (type " + ringType + ") " + action + " !");
                    Log.d(TAG, "Commande ring (type=" + ringType + ") envoyée avec succès à " + label);
                } else {
                    String errorMsg = "Échec alerte " + label + ": " + (error != null ? "Code " + error.errorCode : "Inconnue");
                    showToast(errorMsg);
                    Log.e(TAG, "Échec commande ring " + label + ": " + (error != null ? error.toString() : "Inconnue"));
                }
            }));

        } catch (JSONException e) {
            showToast("Erreur JSON pour " + label);
            Log.e(TAG, "Erreur JSON commande ring " + label, e);
            if (leftBeacon != null && leftBeacon.isConnected()) setLeftButtonsEnabled(true);
            if (rightBeacon != null && rightBeacon.isConnected()) setRightButtonsEnabled(true);
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException lors de l'envoi de la commande à " + label, se);
            showToast("Permission BT manquante pour alerter " + label);
            if (leftBeacon != null && leftBeacon.isConnected()) setLeftButtonsEnabled(true);
            if (rightBeacon != null && rightBeacon.isConnected()) setRightButtonsEnabled(true);
            requestNeededPermissions();
        }
    }


    // --- Utilitaires ---

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan();
        try {
            if (leftBeacon != null && (leftBeacon.isConnected() || leftBeacon.getState() == KBConnState.Connecting)) {
                leftBeacon.disconnect();
            }
            if (rightBeacon != null && (rightBeacon.isConnected() || rightBeacon.getState() == KBConnState.Connecting)) {
                rightBeacon.disconnect();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException lors de la déconnexion onDestroy", e);
        }
        mHandler.removeCallbacksAndMessages(null);
        // Optionnel: KBeaconsMgr.clearBeaconManager();
    }
}