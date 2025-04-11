

package com.example.bitvibe;

import static com.example.bitvibe.MainActivity.binanceApi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat; // Ajout de cet import


import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlarmCheckService extends Service {
    private static final String TAG = "AlarmCheckService";
    private static final String CHANNEL_ID = "BitVibeAlarmChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int ALARM_CHECK_INTERVAL = 10000;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;


    private static final String LEFT_BRACELET_MAC = "BC:57:29:13:FA:DE";
    private static final String RIGHT_BRACELET_MAC = "BC:57:29:13:FA:E0";


    private static final String PREFS_NAME = "BitVibePrefs";
    private static final String PREF_NOTIFICATION_TYPE = "notification_type";
    private static final int DEFAULT_NOTIFICATION_TYPE = 6;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        startForeground(NOTIFICATION_ID, createNotification());

        runnable = new Runnable() {
            @Override
            public void run() {
                checkAlarm();
                handler.postDelayed(this, ALARM_CHECK_INTERVAL);
            }
        };
        handler.post(runnable);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void checkAlarm() {
        if (binanceApi == null) {
            Log.e(TAG, "checkAlarm: binanceApi is null. Cannot check price.");
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String selectedCrypto = prefs.getString("crypto", "DOGEUSDT");
        Call<BinancePriceResponse> call = binanceApi.getBitcoinPrice(selectedCrypto);

        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(Call<BinancePriceResponse> call, Response<BinancePriceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    double currentPrice = response.body().getPrice();
                    comparePriceWithAlarm(currentPrice);
                } else {
                    Log.e(TAG, "Erreur API lors de la récupération du prix: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<BinancePriceResponse> call, Throwable t) {
                Log.e(TAG, "Échec de la requête API pour le prix: " + t.getMessage(), t);
            }
        });
    }

    private void comparePriceWithAlarm(double currentPrice) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isAlarmOn = prefs.getBoolean("is_alarm_on", false);

        if (isAlarmOn) {
            if (prefs.contains("trigger_price") && prefs.contains("is_above")) {
                double triggerPrice = 0.0;
                try {
                    triggerPrice = Double.parseDouble(prefs.getString("trigger_price", "0.0"));
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Erreur de parsing du trigger_price depuis SharedPreferences", e);
                    return;
                }

                boolean wasAboveWhenSet = prefs.getBoolean("is_above", false);

                Log.d(TAG, "Vérification: Prix Actuel=" + currentPrice + ", Seuil=" + triggerPrice + ", Était Au-dessus Lors du Set=" + wasAboveWhenSet);

                String targetMacAddress = null;
                String triggerReason = "";


                if (!wasAboveWhenSet && currentPrice >= triggerPrice) {
                    Log.d(TAG, "ALARM TRIGGERED! Condition: Montée au-dessus du seuil.");
                    targetMacAddress = LEFT_BRACELET_MAC;
                    triggerReason = "Hausse du prix au-dessus de " + triggerPrice;
                }

                else if (wasAboveWhenSet && currentPrice <= triggerPrice) {
                    Log.d(TAG, "ALARM TRIGGERED! Condition: Descente en dessous du seuil.");
                    targetMacAddress = RIGHT_BRACELET_MAC;
                    triggerReason = "Baisse du prix en dessous de " + triggerPrice;
                } else {
                    Log.d(TAG, "Condition d'alarme non remplie.");
                }


                if (targetMacAddress != null) {

                    int selectedNotificationType = prefs.getInt(PREF_NOTIFICATION_TYPE, DEFAULT_NOTIFICATION_TYPE);
                    Log.d(TAG, "Type de notification choisi: " + selectedNotificationType);


                    sendBraceletNotification(targetMacAddress, selectedNotificationType);


                    final String finalTriggerReason = triggerReason;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(getApplicationContext(), "ALARM TRIGGERED!\n" + finalTriggerReason, Toast.LENGTH_LONG).show();
                    });


                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("is_alarm_on", false);
                    editor.apply();
                    Log.d(TAG, "Alarme désactivée après déclenchement pour MAC: " + targetMacAddress);


                }
            } else {
                Log.w(TAG, "Vérification ignorée: trigger_price ou is_above manquant dans SharedPreferences.");
            }
        } else {
            Log.d(TAG, "Vérification ignorée: Alarme désactivée (is_alarm_on=false).");
        }
    }


    private void sendBraceletNotification(String macAddress, int ringType) {
        if (macAddress == null || macAddress.isEmpty()) {
            Log.w(TAG, "Tentative d'envoi de notification sans adresse MAC.");
            return;
        }

        Intent intent = new Intent(this, BluetoothConnectionService.class);
        intent.setAction(BluetoothConnectionService.ACTION_RING_DEVICE);
        intent.putExtra(BluetoothConnectionService.EXTRA_MAC_ADDRESS, macAddress);
        intent.putExtra(BluetoothConnectionService.EXTRA_RING_TYPE, ringType);

        try {

            startService(intent);
            Log.d(TAG, "Intent envoyé pour faire sonner/vibrer le bracelet " + macAddress + " (Type: " + ringType + ")");
        } catch (IllegalStateException e) {

            Log.e(TAG, "Erreur (IllegalStateException) lors du démarrage du service pour bracelet " + macAddress + ". Le service BT tourne-t-il en foreground?", e);

            try {
                ContextCompat.startForegroundService(this, intent);
                Log.d(TAG, "Tentative de démarrage en foreground du service pour bracelet " + macAddress);
            } catch (Exception e2) {
                Log.e(TAG, "Échec de la tentative de démarrage en foreground du service pour bracelet " + macAddress, e2);
            }

        } catch (Exception e) {
            Log.e(TAG, "Erreur générale lors du démarrage du service pour bracelet " + macAddress, e);
        }
    }


    private Notification createNotification() {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BitVibe Alarme Active")
                .setContentText("Surveillance du prix de la cryptomonnaie...")
                .setSmallIcon(R.drawable.logo)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "BitVibe Alarm Channel";
            String description = "Notifications pour les alarmes de prix BitVibe";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            } else {
                Log.e(TAG, "Impossible d'obtenir NotificationManager pour créer le canal.");
            }
        }
    }
}
