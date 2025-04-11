package com.example.bitvibe;

import static com.example.bitvibe.MainActivity.binanceApi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent; // Ajout de l'import Intent
import android.content.SharedPreferences;
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

    // --- Constantes pour les Bracelets et Vibration --- AJOUTÉES ---
    private static final String LEFT_BRACELET_MAC = "BC:57:29:13:FA:DE";
    private static final String RIGHT_BRACELET_MAC = "BC:57:29:13:FA:E0";
    private static final int RING_TYPE_VIBRATE = 4; // Type pour la vibration
    // --- FIN AJOUTS CONSTANTES ---

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        startForeground(NOTIFICATION_ID, createNotification());

        // Initialize the runnable for periodic alarm checks
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
        // Ce service ne supporte pas le binding
        return null;
    }

    // Method to check the alarm
    private void checkAlarm() {
        SharedPreferences prefs = getSharedPreferences("BitVibePrefs", Context.MODE_PRIVATE);
        String selectedCrypto = prefs.getString("crypto", "DOGEUSDT"); // Default to DOGEUSDT if not set
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

    // Method to compare the current price with the alarm settings
    private void comparePriceWithAlarm(double currentPrice) {
        SharedPreferences prefs = getSharedPreferences("BitVibePrefs", Context.MODE_PRIVATE);
        boolean isAlarmOn = prefs.getBoolean("is_alarm_on", false); // Load alarm state

        if (isAlarmOn) { // Only proceed if alarm is ON
            if (prefs.contains("trigger_price") && prefs.contains("is_above")) {
                double triggerPrice = Double.parseDouble(prefs.getString("trigger_price", "0.0"));
                boolean isAbove = prefs.getBoolean("is_above", false);

                Log.d(TAG, "comparing : trigger Price = " + triggerPrice + ", is Above = " + isAbove + " currentPrice : " + currentPrice);
                if ((!isAbove && currentPrice >= triggerPrice) || (isAbove && currentPrice <= triggerPrice)) {
                    // Trigger alarm!
                    Log.d(TAG, "ALARM TRIGGERED!");
                    triggerAlarm(); // Appel de la méthode modifiée

                } else {
                    Log.d(TAG, "Not Triggered !");
                }
            }
        }
    }

    // Method to trigger the alarm (show a Toast and vibrate bracelets)
    private void triggerAlarm() {
        Log.d(TAG, "Déclenchement de l'alarme et des vibrations...");

        // --- VIBRATION BRACELET GAUCHE ---
        Intent intentLeft = new Intent(this, BluetoothConnectionService.class);
        intentLeft.setAction(BluetoothConnectionService.ACTION_RING_DEVICE);
        intentLeft.putExtra(BluetoothConnectionService.EXTRA_MAC_ADDRESS, LEFT_BRACELET_MAC);
        intentLeft.putExtra(BluetoothConnectionService.EXTRA_RING_TYPE, RING_TYPE_VIBRATE);
        try {
            startService(intentLeft);
            Log.d(TAG, "Intent envoyé pour faire vibrer le bracelet GAUCHE.");
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du démarrage du service pour bracelet GAUCHE", e);
        }

        // Petite pause pour éviter de surcharger le service ou le BT (optionnel mais peut aider)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intentRight = new Intent(this, BluetoothConnectionService.class);
            intentRight.setAction(BluetoothConnectionService.ACTION_RING_DEVICE);
            intentRight.putExtra(BluetoothConnectionService.EXTRA_MAC_ADDRESS, RIGHT_BRACELET_MAC);
            intentRight.putExtra(BluetoothConnectionService.EXTRA_RING_TYPE, RING_TYPE_VIBRATE);
            try {
                startService(intentRight);
                Log.d(TAG, "Intent envoyé pour faire vibrer le bracelet DROIT.");
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors du démarrage du service pour bracelet DROIT", e);
            }
        }, 100); // Délai de 100ms entre les deux commandes

        // --- AFFICHAGE DU TOAST ---
        // Utiliser le Handler pour afficher le Toast sur le thread UI
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(getApplicationContext(), "PRICE ALARM TRIGGERED!", Toast.LENGTH_LONG).show();
        });
    }

    // Method to create the notification for the foreground service
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

    // Method to create the notification channel (required for Android Oreo and above)
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "BitVibe Alarm Channel";
            String description = "Channel for BitVibe price alarm";
            int importance = NotificationManager.IMPORTANCE_LOW; // Use a lower importance for the service notification
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}