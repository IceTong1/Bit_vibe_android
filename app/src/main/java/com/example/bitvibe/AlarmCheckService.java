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
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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

    private static final String PREF_HIGH_TRIGGER_PRICE = "high_trigger_price";
    private static final String PREF_IS_HIGH_ALARM_ON = "is_high_alarm_on";
    private static final String PREF_LOW_TRIGGER_PRICE = "low_trigger_price";
    private static final String PREF_IS_LOW_ALARM_ON = "is_low_alarm_on";

    private static final String PREF_VOLATILITY_THRESHOLD = "volatility_threshold";
    private static final String PREF_IS_VOLATILITY_ALARM_ON = "is_volatility_alarm_on";
    private static final String PREF_VOLATILITY_REFERENCE_PRICE = "volatility_reference_price";

    public static final String ACTION_ALARM_STATE_CHANGED = "com.example.bitvibe.ALARM_STATE_CHANGED";
    public static final String EXTRA_ALARM_TYPE = "com.example.bitvibe.EXTRA_ALARM_TYPE";
    public static final String EXTRA_ALARM_STATE = "com.example.bitvibe.EXTRA_ALARM_STATE";


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
                checkAlarms();
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

    private void checkAlarms() {
        if (binanceApi == null) {
            Log.e(TAG, "checkAlarms: binanceApi is null.");
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
                    evaluateThresholdAlarmConditions(currentPrice, prefs);
                    evaluateVolatilityAlarmCondition(currentPrice, prefs);
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

    private void evaluateThresholdAlarmConditions(double currentPrice, SharedPreferences prefs) {
        boolean highAlarmTriggered = false;
        boolean lowAlarmTriggered = false;

        boolean isHighAlarmOn = prefs.getBoolean(PREF_IS_HIGH_ALARM_ON, false);
        String highPriceStr = prefs.getString(PREF_HIGH_TRIGGER_PRICE, "");
        boolean isLowAlarmOn = prefs.getBoolean(PREF_IS_LOW_ALARM_ON, false);
        String lowPriceStr = prefs.getString(PREF_LOW_TRIGGER_PRICE, "");
        int selectedNotificationType = prefs.getInt(PREF_NOTIFICATION_TYPE, DEFAULT_NOTIFICATION_TYPE);

        SharedPreferences.Editor editor = prefs.edit();

        if (isHighAlarmOn && !highPriceStr.isEmpty()) {
            try {
                double highTriggerPrice = Double.parseDouble(highPriceStr);
                if (currentPrice > highTriggerPrice) {
                    Log.d(TAG, "HIGH ALARM TRIGGERED! Price=" + currentPrice + " > Threshold=" + highTriggerPrice);
                    sendBraceletNotification(LEFT_BRACELET_MAC, selectedNotificationType);
                    showToastNotification("High Alarm: Price above " + highTriggerPrice);
                    editor.putBoolean(PREF_IS_HIGH_ALARM_ON, false);
                    highAlarmTriggered = true;
                    sendAlarmStateBroadcast("high", false);
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error parsing high trigger price: " + highPriceStr, e);
                editor.putBoolean(PREF_IS_HIGH_ALARM_ON, false);
                highAlarmTriggered = true;
                sendAlarmStateBroadcast("high", false);
            }
        }

        if (isLowAlarmOn && !lowPriceStr.isEmpty()) {
            try {
                double lowTriggerPrice = Double.parseDouble(lowPriceStr);
                if (currentPrice < lowTriggerPrice) {
                    Log.d(TAG, "LOW ALARM TRIGGERED! Price=" + currentPrice + " < Threshold=" + lowTriggerPrice);
                    sendBraceletNotification(RIGHT_BRACELET_MAC, selectedNotificationType);
                    showToastNotification("Low Alarm: Price below " + lowTriggerPrice);
                    editor.putBoolean(PREF_IS_LOW_ALARM_ON, false);
                    lowAlarmTriggered = true;
                    sendAlarmStateBroadcast("low", false);
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error parsing low trigger price: " + lowPriceStr, e);
                editor.putBoolean(PREF_IS_LOW_ALARM_ON, false);
                lowAlarmTriggered = true;
                sendAlarmStateBroadcast("low", false);
            }
        }

        if (highAlarmTriggered || lowAlarmTriggered) {
            editor.apply();
            Log.d(TAG, "Threshold alarm states updated in preferences after trigger.");
        }
    }

    private void evaluateVolatilityAlarmCondition(double currentPrice, SharedPreferences prefs) {
        boolean isVolatilityAlarmOn = prefs.getBoolean(PREF_IS_VOLATILITY_ALARM_ON, false);

        if (!isVolatilityAlarmOn) {

            return;
        }

        float volatilityThreshold = prefs.getFloat(PREF_VOLATILITY_THRESHOLD, -1.0f);
        String referencePriceStr = prefs.getString(PREF_VOLATILITY_REFERENCE_PRICE, null);
        int selectedNotificationType = prefs.getInt(PREF_NOTIFICATION_TYPE, DEFAULT_NOTIFICATION_TYPE);


        if (volatilityThreshold <= 0 || referencePriceStr == null) {
            Log.w(TAG, "Volatility check skipped: Invalid threshold ("+volatilityThreshold+") or missing reference price.");
            return;
        }

        try {
            double referencePrice = Double.parseDouble(referencePriceStr);
            if (referencePrice <= 0) {
                Log.w(TAG, "Volatility check skipped: Invalid reference price ("+referencePrice+").");
                return;
            }

            double percentageChange = ((currentPrice - referencePrice) / referencePrice) * 100.0;

            Log.d(TAG, "Volatility Check: Current=" + currentPrice + ", Ref=" + referencePrice + ", Change=" + String.format("%.2f", percentageChange) + "%, Threshold=" + volatilityThreshold + "%");

            if (Math.abs(percentageChange) > volatilityThreshold) {
                Log.i(TAG, "VOLATILITY ALARM TRIGGERED! Change=" + String.format("%.2f", percentageChange) + "% > Threshold=" + volatilityThreshold + "%");

                if (percentageChange > 0) {
                    Log.d(TAG, "Volatility UP -> Notifying LEFT bracelet.");
                    sendBraceletNotification(LEFT_BRACELET_MAC, selectedNotificationType);
                    showToastNotification("Volatility Alarm: Price UP " + String.format("%.2f", percentageChange) + "%");
                } else {
                    Log.d(TAG, "Volatility DOWN -> Notifying RIGHT bracelet.");
                    sendBraceletNotification(RIGHT_BRACELET_MAC, selectedNotificationType);
                    showToastNotification("Volatility Alarm: Price DOWN " + String.format("%.2f", percentageChange) + "%");
                }




            }

        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing volatility reference price: " + referencePriceStr, e);




        }
    }


    private void sendAlarmStateBroadcast(String alarmType, boolean newState) {
        Intent intent = new Intent(ACTION_ALARM_STATE_CHANGED);
        intent.putExtra(EXTRA_ALARM_TYPE, alarmType);
        intent.putExtra(EXTRA_ALARM_STATE, newState);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "Sent broadcast: Alarm " + alarmType + " state changed to " + newState);
    }

    private void sendBraceletNotification(String macAddress, int ringType) {
        if (macAddress == null || macAddress.isEmpty()) {
            Log.w(TAG, "Attempting to send notification without MAC address.");
            return;
        }
        Intent intent = new Intent(this, BluetoothConnectionService.class);
        intent.setAction(BluetoothConnectionService.ACTION_RING_DEVICE);
        intent.putExtra(BluetoothConnectionService.EXTRA_MAC_ADDRESS, macAddress);
        intent.putExtra(BluetoothConnectionService.EXTRA_RING_TYPE, ringType);
        try {
            startService(intent);
            Log.d(TAG, "Intent sent to notify bracelet " + macAddress + " (Type: " + ringType + ")");
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error (IllegalStateException) starting BT service for " + macAddress, e);
            try {
                ContextCompat.startForegroundService(this, intent);
                Log.d(TAG, "Attempting foreground start for BT service for " + macAddress);
            } catch (Exception e2) {
                Log.e(TAG, "Failed foreground start attempt for " + macAddress, e2);
            }
        } catch (Exception e) {
            Log.e(TAG, "General error starting BT service for " + macAddress, e);
        }
    }

    private void showToastNotification(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
        });
    }

    private Notification createNotification() {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BitVibe Alarms Active")
                .setContentText("Monitoring cryptocurrency price...")
                .setSmallIcon(R.drawable.logo)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "BitVibe Alarm Channel";
            String description = "Notifications for BitVibe price alarms";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            } else {
                Log.e(TAG, "Cannot get NotificationManager to create channel.");
            }
        }
    }
}