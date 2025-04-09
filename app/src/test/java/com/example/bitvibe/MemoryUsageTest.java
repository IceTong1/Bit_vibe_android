package com.example.bitvibe;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Debug;
import android.os.MemoryFile;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class MemoryUsageTest {

    @Rule
    public androidx.test.ext.junit.rules.ActivityScenarioRule<MainActivity> activityScenarioRule = new androidx.test.ext.junit.rules.ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void memoryUsage_alarmRunning() {
        activityScenarioRule.getScenario().onActivity(activity -> {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            // Start the service
            Intent intent = new Intent(context, AlarmCheckService.class);
            context.startService(intent);

            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);

            // Check available memory (just an example, adjust the threshold)
            assertTrue(memoryInfo.availMem > 100 * 1024 * 1024); // More than 100MB available

            // OR: Check memory used by the process (more complex, requires PID)
            // ... Code to get the process PID ...
            // int[] pids = {pid};
            // Debug.MemoryInfo[] memoryInfos = activityManager.getProcessMemoryInfo(pids);
            // Check memory used by the process (just an example)
            // assertTrue(memoryInfos[0].getTotalPss() < 200 * 1024); // Less than 200MB used
            context.stopService(intent);
        });
    }
}