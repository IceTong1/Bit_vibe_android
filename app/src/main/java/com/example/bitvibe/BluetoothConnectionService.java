package com.example.bitvibe; // Assurez-vous que le package est correct

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager; // Utilisation de LocalBroadcastManager

import com.kkmcn.kbeaconlib2.KBConnState;
import com.kkmcn.kbeaconlib2.KBConnectionEvent;
import com.kkmcn.kbeaconlib2.KBException;
import com.kkmcn.kbeaconlib2.KBCfgPackage.KBCfgCommon;
import com.kkmcn.kbeaconlib2.KBeacon;
import com.kkmcn.kbeaconlib2.KBeaconsMgr;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class BluetoothConnectionService extends Service implements KBeacon.ConnStateDelegate {

    private static final String TAG = "BluetoothConnService";
    private static final String CHANNEL_ID = "BluetoothConnectionServiceChannel";
    private static final int NOTIFICATION_ID = 2; // ID différent de AlarmCheckService
    private static final String DEFAULT_PASSWORD = "0000000000000000"; // Mot de passe par défaut (à adapter si nécessaire)
    private static final String PREFS_NAME = "BitVibePrefs"; // Nom des SharedPreferences

    // Actions pour les Intents et Broadcasts
    public static final String ACTION_CONNECT = "com.example.bitvibe.CONNECT";
    public static final String ACTION_DISCONNECT = "com.example.bitvibe.DISCONNECT";
    public static final String ACTION_RING_DEVICE = "com.example.bitvibe.RING_DEVICE";
    public static final String ACTION_GET_STATE = "com.example.bitvibe.GET_STATE"; // Optionnel: pour demander l'état actuel

    public static final String EXTRA_MAC_ADDRESS = "com.example.bitvibe.MAC_ADDRESS";
    public static final String EXTRA_RING_TYPE = "com.example.bitvibe.RING_TYPE";
    public static final String EXTRA_PASSWORD = "com.example.bitvibe.PASSWORD"; // Pour passer le mot de passe si différent

    public static final String ACTION_CONN_STATE_CHANGED = "com.example.bitvibe.CONN_STATE_CHANGED";
    public static final String EXTRA_CONN_STATE_MAC = "com.example.bitvibe.CONN_STATE_MAC";
    public static final String EXTRA_CONN_STATE = "com.example.bitvibe.CONN_STATE"; // Utiliser un int (ordinal() de l'enum)
    public static final String EXTRA_CONN_REASON = "com.example.bitvibe.CONN_REASON";

    private final IBinder mBinder = new LocalBinder();
    private KBeaconsMgr mBeaconMgr;
    private final HashMap<String, KBeacon> mConnectedBeacons = new HashMap<>();
    private Handler mHandler;
    private SharedPreferences sharedPreferences;

    // Binder pour la communication locale
    public class LocalBinder extends Binder {
        // *** CORRECTION : Ajout de 'public' ici ***
        public BluetoothConnectionService getService() {
            // Retourne cette instance de LocalService pour que les clients puissent appeler les méthodes publiques
            return BluetoothConnectionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        mHandler = new Handler(Looper.getMainLooper());
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE); // Initialiser SharedPreferences

        // Initialiser KBeaconsMgr avec le contexte de l'application
        mBeaconMgr = KBeaconsMgr.sharedBeaconManager(getApplicationContext());
        if (mBeaconMgr == null) {
            Log.e(TAG, "Impossible d'initialiser KBeaconsMgr");
            stopSelf(); // Arrêter le service si KBeaconsMgr n'est pas disponible
            return;
        }

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");

        // Démarrer en mode foreground
        Notification notification = buildNotification("Maintien des connexions Bluetooth...");
        startForeground(NOTIFICATION_ID, notification);

        // Traiter la commande reçue via l'Intent
        if (intent != null) {
            final String action = intent.getAction();
            final String macAddress = intent.getStringExtra(EXTRA_MAC_ADDRESS);
            final String password = intent.getStringExtra(EXTRA_PASSWORD) != null ? intent.getStringExtra(EXTRA_PASSWORD) : DEFAULT_PASSWORD; // Utiliser le mdp passé ou défaut

            if (macAddress != null) {
                if (ACTION_CONNECT.equals(action)) {
                    Log.d(TAG, "Commande reçue : CONNECT pour " + macAddress);
                    connectBeaconInternal(macAddress, password);
                } else if (ACTION_DISCONNECT.equals(action)) {
                    Log.d(TAG, "Commande reçue : DISCONNECT pour " + macAddress);
                    disconnectBeaconInternal(macAddress);
                } else if (ACTION_RING_DEVICE.equals(action)) {
                    int ringType = intent.getIntExtra(EXTRA_RING_TYPE, 0); // 0 = STOP par défaut
                    Log.d(TAG, "Commande reçue : RING pour " + macAddress + " type " + ringType);
                    ringDeviceInternal(macAddress, ringType);
                }
            }
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Service onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");
        // Nettoyer : déconnecter tous les beacons
        for (KBeacon beacon : mConnectedBeacons.values()) {
            if (beacon != null && (beacon.getState() == KBConnState.Connected || beacon.getState() == KBConnState.Connecting)) {
                try {
                    Log.d(TAG, "Déconnexion de " + beacon.getMac() + " dans onDestroy");
                    beacon.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Erreur lors de la déconnexion dans onDestroy", e);
                }
            }
        }
        mConnectedBeacons.clear();
        mHandler.removeCallbacksAndMessages(null); // Nettoyer le handler
        stopForeground(true); // Arrêter le mode foreground
        super.onDestroy();
    }

    // --- Méthodes de connexion/déconnexion internes ---

    private void connectBeaconInternal(String macAddress, String password) {
        if (mBeaconMgr == null) {
            Log.e(TAG, "KBeaconsMgr non initialisé.");
            broadcastConnState(macAddress, KBConnState.Disconnected, KBConnectionEvent.ConnException);
            return;
        }

        // Vérifier si déjà connecté ou en cours de connexion
        KBeacon existingBeacon = mConnectedBeacons.get(macAddress);
        if (existingBeacon != null && (existingBeacon.getState() == KBConnState.Connected || existingBeacon.getState() == KBConnState.Connecting)) {
            Log.w(TAG, "Tentative de connexion à un beacon déjà connecté/en cours : " + macAddress);
            broadcastConnState(macAddress, existingBeacon.getState(), 0);
            return;
        }

        KBeacon beacon = mBeaconMgr.getBeacon(macAddress);
        if (beacon == null) {
            if (mBeaconMgr.getBluetoothAdapter() != null && android.bluetooth.BluetoothAdapter.checkBluetoothAddress(macAddress)) {
                try {
                    android.bluetooth.BluetoothDevice device = mBeaconMgr.getBluetoothAdapter().getRemoteDevice(macAddress);
                    if (device != null) {
                        beacon = new KBeacon(macAddress, getApplicationContext()); // Utiliser le contexte de l'app
                        beacon.attach2Device(device); // Attacher l'objet BluetoothDevice
                        Log.w(TAG, "KBeacon instance créée pour " + macAddress + " sans scan récent.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erreur lors de getRemoteDevice ou création KBeacon pour " + macAddress, e);
                    broadcastConnState(macAddress, KBConnState.Disconnected, KBConnectionEvent.ConnException);
                    return;
                }
            }
        }


        if (beacon == null) {
            Log.e(TAG, "Beacon non trouvé pour la connexion : " + macAddress);
            broadcastConnState(macAddress, KBConnState.Disconnected, KBConnectionEvent.ConnException); // Ou une raison spécifique
            return;
        }

        Log.i(TAG, "Tentative de connexion à : " + macAddress);
        broadcastConnState(macAddress, KBConnState.Connecting, 0); // Notifier le début de la connexion

        final KBeacon finalBeacon = beacon; // Pour utilisation dans le callback
        boolean success = finalBeacon.connect(password, 15000, this);
        if (!success) {
            Log.e(TAG, "L'appel KBeacon.connect() a échoué immédiatement pour " + macAddress);
            broadcastConnState(macAddress, KBConnState.Disconnected, KBConnectionEvent.ConnException); // Indiquer un échec
        }
    }

    private void disconnectBeaconInternal(String macAddress) {
        KBeacon beacon = mConnectedBeacons.get(macAddress);
        if (beacon != null && (beacon.getState() == KBConnState.Connected || beacon.getState() == KBConnState.Connecting)) {
            Log.i(TAG, "Tentative de déconnexion de : " + macAddress);
            broadcastConnState(macAddress, KBConnState.Disconnecting, 0); // Notifier le début de la déconnexion
            try {
                beacon.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de la déconnexion de " + macAddress, e);
            }
        } else {
            Log.w(TAG, "Tentative de déconnexion d'un beacon non connecté : " + macAddress);
            if (beacon == null || beacon.getState() == KBConnState.Disconnected) {
                broadcastConnState(macAddress, KBConnState.Disconnected, KBConnectionEvent.ConnManualDisconnecting); // Confirmer déconnecté
            }
        }
    }

    // --- Implémentation KBeacon.ConnStateDelegate ---

    @Override
    public void onConnStateChange(KBeacon beacon, KBConnState state, int reason) {
        if (beacon == null || beacon.getMac() == null) return;
        String mac = beacon.getMac();
        Log.d(TAG, "onConnStateChange: MAC=" + mac + ", State=" + state + ", Reason=" + reason);

        mHandler.post(() -> {
            if (state == KBConnState.Connected) {
                mConnectedBeacons.put(mac, beacon);
            } else if (state == KBConnState.Disconnected) {
                mConnectedBeacons.remove(mac);
            }
            broadcastConnState(mac, state, reason);
        });
    }

    // --- Méthode pour envoyer des broadcasts ---

    private void broadcastConnState(String mac, KBConnState state, int reason) {
        Intent intent = new Intent(ACTION_CONN_STATE_CHANGED);
        intent.putExtra(EXTRA_CONN_STATE_MAC, mac);
        intent.putExtra(EXTRA_CONN_STATE, state.ordinal()); // Envoyer l'ordinal de l'enum
        intent.putExtra(EXTRA_CONN_REASON, reason);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "Broadcast sent: MAC=" + mac + ", State=" + state);
    }

    // --- Méthodes publiques pour le Binder ---

    public void connectToBeacon(String mac, String password) {
        Intent intent = new Intent(this, BluetoothConnectionService.class);
        intent.setAction(ACTION_CONNECT);
        intent.putExtra(EXTRA_MAC_ADDRESS, mac);
        intent.putExtra(EXTRA_PASSWORD, password);
        startService(intent);
    }

    public void disconnectBeacon(String mac) {
        Intent intent = new Intent(this, BluetoothConnectionService.class);
        intent.setAction(ACTION_DISCONNECT);
        intent.putExtra(EXTRA_MAC_ADDRESS, mac);
        startService(intent);
    }

    public KBConnState getBeaconState(String mac) {
        KBeacon beacon = mConnectedBeacons.get(mac);
        return (beacon != null) ? beacon.getState() : KBConnState.Disconnected;
    }

    public Map<String, KBeacon> getConnectedBeaconsMap() {
        return new HashMap<>(mConnectedBeacons);
    }

    public void ringBeacon(String mac, int ringType) {
        Intent intent = new Intent(this, BluetoothConnectionService.class);
        intent.setAction(ACTION_RING_DEVICE);
        intent.putExtra(EXTRA_MAC_ADDRESS, mac);
        intent.putExtra(EXTRA_RING_TYPE, ringType);
        startService(intent);
    }


    // --- Logique interne pour RingDevice ---
    private void ringDeviceInternal(String mac, int ringType) {
        KBeacon beacon = mConnectedBeacons.get(mac);
        if (beacon == null || beacon.getState() != KBConnState.Connected) {
            Log.w(TAG, "Tentative de ring sur beacon non connecté: " + mac);
            return;
        }

        KBCfgCommon commonCfg = beacon.getCommonCfg();
        if (commonCfg != null && !commonCfg.isSupportBeep() && ringType != 0 ) {
            Log.w(TAG, "Bracelet " + mac + " ne reporte pas explicitement 'beep', mais tentative de commande 'ring' (type=" + ringType + ")...");
        }

        JSONObject cmdPara = new JSONObject();
        try {
            cmdPara.put("msg", "ring");
            cmdPara.put("ringTime", ringType == 0 ? 0 : 2000);
            cmdPara.put("ringType", ringType);
            if ((ringType & 2) > 0 || (ringType & 4) > 0) {
                cmdPara.put("ledOn", 100);
                cmdPara.put("ledOff", 100);
            }

            Log.d(TAG, "Envoi commande ring à " + mac + " (type=" + ringType + "): " + cmdPara.toString());

            beacon.sendCommand(cmdPara, (success, error) -> {
                mHandler.post(() -> {
                    if (success) {
                        Log.d(TAG, "Commande ring (type=" + ringType + ") envoyée avec succès à " + mac);
                    } else {
                        String errorMsg = "Échec commande ring " + mac + ": " + (error != null ? "Code " + error.errorCode + " - " + error.getMessage() : "Inconnue");
                        Log.e(TAG, errorMsg);
                    }
                });
            });

        } catch (JSONException e) {
            Log.e(TAG, "Erreur JSON pour commande ring " + mac, e);
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'envoi de la commande ring à " + mac, e);
        }
    }


    // --- Gestion Notification Foreground ---

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name) + " Connexion BT";
            String description = "Maintient les connexions Bluetooth actives";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel créé.");
            } else {
                Log.e(TAG, "NotificationManager non obtenu.");
            }
        }
    }

    private Notification buildNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name) + " actif")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.logo)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
}