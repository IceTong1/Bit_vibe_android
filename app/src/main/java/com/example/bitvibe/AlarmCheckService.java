package com.example.bitvibe;

import static com.example.bitvibe.MainActivity.binanceApi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences; // Assurez-vous que cet import est présent
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlarmCheckService extends Service {
    private static final String TAG = "AlarmCheckService";
    private static final String CHANNEL_ID = "BitVibeAlarmChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int ALARM_CHECK_INTERVAL = 10000; // Check every 10 seconds
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;

    // --- Constantes pour les Bracelets ---
    private static final String LEFT_BRACELET_MAC = "BC:57:29:13:FA:DE";
    private static final String RIGHT_BRACELET_MAC = "BC:57:29:13:FA:E0";

    // --- Clé et valeur par défaut pour le type de notification
    private static final String PREFS_NAME = "BitVibePrefs"; // Assurez-vous que c'est le bon nom
    private static final String PREF_NOTIFICATION_TYPE = "notification_type";
    private static final int DEFAULT_NOTIFICATION_TYPE = 6; // LED + VIB (valeur 6) par défaut


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
                    Log.e(TAG, "Erreur API : " + response.code());
                }
            }

            @Override
            public void onFailure(Call<BinancePriceResponse> call, Throwable t) {
                Log.e(TAG, "Échec de la requête API : " + t.getMessage(), t);
            }
        });
    }

    private void comparePriceWithAlarm(double currentPrice) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isAlarmOn = prefs.getBoolean("is_alarm_on", false);

        if (isAlarmOn) {
            if (prefs.contains("trigger_price") && prefs.contains("is_above")) {
                double triggerPrice = Double.parseDouble(prefs.getString("trigger_price", "0.0"));
                boolean isAbove = prefs.getBoolean("is_above", false);

                Log.d(TAG, "comparing : trigger Price = " + triggerPrice + ", is Above = " + isAbove + " currentPrice : " + currentPrice);
                if ((!isAbove && currentPrice >= triggerPrice) || (isAbove && currentPrice <= triggerPrice)) {
                    Log.d(TAG, "ALARM TRIGGERED!");
                    triggerAlarm(); // Appel de la méthode modifiée
                } else {
                    Log.d(TAG, "Not Triggered !");
                }
            }
        }
    }


    private void triggerAlarm() {
        // 1. Lire le type de notification choisi dans les préférences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int selectedNotificationType = prefs.getInt(PREF_NOTIFICATION_TYPE, DEFAULT_NOTIFICATION_TYPE);
        Log.d(TAG, "Déclenchement de l'alarme. Type de notification choisi : " + selectedNotificationType);

        // 2. Envoyer la commande pour le bracelet GAUCHE
        Intent intentLeft = new Intent(this, BluetoothConnectionService.class);
        intentLeft.setAction(BluetoothConnectionService.ACTION_RING_DEVICE);
        intentLeft.putExtra(BluetoothConnectionService.EXTRA_MAC_ADDRESS, LEFT_BRACELET_MAC);
        intentLeft.putExtra(BluetoothConnectionService.EXTRA_RING_TYPE, selectedNotificationType); // Utilisation de la valeur lue
        try {
            startService(intentLeft);
            Log.d(TAG, "Intent envoyé pour bracelet GAUCHE (Type: " + selectedNotificationType + ")");
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du démarrage du service pour bracelet GAUCHE", e);
        }

        // 3. Envoyer la commande pour le bracelet DROIT (avec petit délai)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intentRight = new Intent(this, BluetoothConnectionService.class);
            intentRight.setAction(BluetoothConnectionService.ACTION_RING_DEVICE);
            intentRight.putExtra(BluetoothConnectionService.EXTRA_MAC_ADDRESS, RIGHT_BRACELET_MAC);
            intentRight.putExtra(BluetoothConnectionService.EXTRA_RING_TYPE, selectedNotificationType); // Utilisation de la valeur lue
            try {
                startService(intentRight);
                Log.d(TAG, "Intent envoyé pour bracelet DROIT (Type: " + selectedNotificationType + ")");
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors du démarrage du service pour bracelet DROIT", e);
            }
        }, 150); // Augmentation légère du délai si nécessaire

        // 4. Afficher le Toast
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(getApplicationContext(), "PRICE ALARM TRIGGERED!", Toast.LENGTH_LONG).show();
        });
    }



    private Notification createNotification() {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BitVibe Alarm Service")
                .setContentText("Checking for price alarm...")
                .setSmallIcon(R.drawable.logo)
                .setContentIntent(pendingIntent);
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "BitVibe Alarm Channel";
            String description = "Channel for BitVibe price alarm";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}