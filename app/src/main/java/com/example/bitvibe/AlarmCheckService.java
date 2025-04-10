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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlarmCheckService extends Service {
    private static final String TAG = "AlarmCheckService"; // Tag for logging
    private static final String CHANNEL_ID = "BitVibeAlarmChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int ALARM_CHECK_INTERVAL = 10000; // Check every 10 seconds (adjust as needed)
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;

    // this function is called when the service is created
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
    }

    // this function is called when the service is started
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

    // this function is called when the service is destroyed
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    /**
     * This service does not support client binding.
     * This method is called when a client attempts to bind to the service,
     * but since this is a started, unbound service, we return null.
     * This indicates that there is no communication channel available for clients.
     */    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Method to check the alarm
    private void checkAlarm() {
        // Crée une requête pour obtenir le prix
        // Retrieve the selected cryptocurrency symbol from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("BitVibePrefs", Context.MODE_PRIVATE);
        String selectedCrypto = prefs.getString("crypto", "DOGEUSDT"); // Default to DOGEUSDT if not set
        Call<BinancePriceResponse> call = binanceApi.getBitcoinPrice(selectedCrypto); // Pass the symbol to the API call
        // Exécute la requête de manière asynchrone
        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(Call<BinancePriceResponse> call, Response<BinancePriceResponse> response) {
                // Vérifie si la réponse est valide
                if (response.isSuccessful() && response.body() != null) {
                    double currentPrice = response.body().getPrice(); // Récupère le prix actuel
                    comparePriceWithAlarm(currentPrice);
                } else { // En cas d'erreur dans la réponse
                    Log.e(TAG, "Erreur API : " + response.code());
                }
            }

            @Override
            public void onFailure(Call<BinancePriceResponse> call, Throwable t) { // En cas d'échec réseau ou autre problème
                Log.e(TAG, "Échec de la requête API : " + t.getMessage(), t); // Logger l'exception complète est utile
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
                    triggerAlarm();

                } else {
                    Log.d(TAG, "Not Triggered !");
                }
            }
        }
    }

    // Method to trigger the alarm (show a Toast)
    private void triggerAlarm() {
         // TODO : Faire vibrer les deux bracelets
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