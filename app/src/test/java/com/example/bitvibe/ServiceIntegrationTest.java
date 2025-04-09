package com.example.bitvibe;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ActivityScenario;
//import androidx.test.ext.junit.rules.ActivityScenarioRule;
//import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

//@RunWith(AndroidJUnit4.class)
public class ServiceIntegrationTest {

//    @Rule
//    public androidx.test.ext.junit.rules.ActivityScenarioRule<MainActivity> activityScenarioRule = new androidx.test.ext.junit.rules.ActivityScenarioRule<>(MainActivity.class);
//    private Context context;
//    private SharedPreferences prefs;
//    @Before
//    public void setUp() {
//        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
//        prefs = context.getSharedPreferences("BitVibePrefs", Context.MODE_PRIVATE);
//        SharedPreferences.Editor editor = prefs.edit();
//        editor.putBoolean("is_alarm_on", true);
//        editor.apply();
//
//    }
//    @Test
//    public void startServiceAndSaveAlarmPref() {
//        activityScenarioRule.getScenario().onActivity(activity -> {
//            // Set the alarm to ON
//            assertTrue(prefs.getBoolean("is_alarm_on", false));
//
//            // Check if the necessary permissions are granted
//            boolean permissionsGranted = ContextCompat.checkSelfPermission(activity, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
//                    ContextCompat.checkSelfPermission(activity, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
//                    ContextCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
//
//            // If permissions are granted, check that the service is running
//            if (permissionsGranted) {
//                // Check if the service is running
//                assertTrue(isServiceRunning(activity, AlarmCheckService.class));
//            }
//        });
//    }


    // Verifie si un service pour l'alarme est en cours d'exécution
    private boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE); // Récupère le gestionnaire d'activités

        // boucle sur les services en cours d'exécution pour vérifier si le service spécifié est en cours d'exécution
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}