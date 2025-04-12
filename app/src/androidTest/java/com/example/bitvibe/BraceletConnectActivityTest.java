package com.example.bitvibe;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.Manifest;
import android.util.Log;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BraceletConnectActivityTest {

    private static final String TAG = "BraceletConnectActTest";

    @Rule
    public ActivityScenarioRule<BraceletConnectActivity> activityScenarioRule =
            new ActivityScenarioRule<>(BraceletConnectActivity.class);

    @Rule
    public GrantPermissionRule grantPermissionRule = GrantPermissionRule.grant(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE,
            Manifest.permission.ACCESS_FINE_LOCATION
    );

    @Test
    public void testBraceletConnectActivityElementsDisplayed() {
        Log.d(TAG, "Début du test : testBraceletConnectActivityElementsDisplayed");
        // Check if key elements are displayed
        onView(withId(R.id.btnScan)).check(matches(isDisplayed()));
        onView(withId(R.id.btnConnectLeft)).check(matches(isDisplayed()));
        onView(withId(R.id.btnConnectRight)).check(matches(isDisplayed()));
        onView(withId(R.id.tvStatusLeft)).check(matches(isDisplayed()));
        onView(withId(R.id.tvStatusRight)).check(matches(isDisplayed()));
        onView(withId(R.id.tvMacLeft)).check(matches(isDisplayed()));
        onView(withId(R.id.tvMacRight)).check(matches(isDisplayed()));
        onView(withId(R.id.btnBeepLeft)).check(matches(isDisplayed()));
        onView(withId(R.id.btnLedLeft)).check(matches(isDisplayed()));
        onView(withId(R.id.btnVibrateLeft)).check(matches(isDisplayed()));
        onView(withId(R.id.btnLedVibrateLeft)).check(matches(isDisplayed()));
        onView(withId(R.id.btnStopLeft)).check(matches(isDisplayed()));
        onView(withId(R.id.btnBeepRight)).check(matches(isDisplayed()));
        onView(withId(R.id.btnLedRight)).check(matches(isDisplayed()));
        onView(withId(R.id.btnVibrateRight)).check(matches(isDisplayed()));
        onView(withId(R.id.btnLedVibrateRight)).check(matches(isDisplayed()));
        onView(withId(R.id.btnStopRight)).check(matches(isDisplayed()));
        onView(withId(R.id.mainActivityButton)).check(matches(isDisplayed()));
        Log.d(TAG, "Fin du test : testBraceletConnectActivityElementsDisplayed");
    }

    @Test
    public void testBackButtonWorks() {
        Log.d(TAG, "Début du test : testBackButtonWorks");
        // Click the back button
        onView(withId(R.id.mainActivityButton)).perform(click());
        // Test passed if the app does not crash
        Log.d(TAG, "Fin du test : testBackButtonWorks");
    }
}