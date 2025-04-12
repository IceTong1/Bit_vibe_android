package com.example.bitvibe;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.contrib.RecyclerViewActions;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    private static final String TAG = "MainActivityTest";

    @Rule
    public androidx.test.ext.junit.rules.ActivityScenarioRule<MainActivity> activityScenarioRule =
            new androidx.test.ext.junit.rules.ActivityScenarioRule<>(MainActivity.class);

    @Before
    public void setUp() {
        Intents.init(); // Initialize Espresso-Intents before each test
    }

    @After
    public void tearDown() {
        Intents.release(); // Release Espresso-Intents after each test
    }

    @Test
    public void testBitcoinPriceTextViewIsDisplayed() {
        Log.d(TAG, "Début du test : testBitcoinPriceTextViewIsDisplayed");
        // Check if bitcoinPriceTextView is displayed
        onView(withId(R.id.bitcoinPriceTextView)).check(matches(isDisplayed()));
        Log.d(TAG, "Fin du test : testBitcoinPriceTextViewIsDisplayed");
    }

    @Test
    public void testSettingsButtonNavigation() {
        Log.d(TAG, "Début du test : testSettingsButtonNavigation");
        // Check if clicking the settingsButton navigates to SettingsActivity
        onView(withId(R.id.settingsButton)).perform(click());
        intended(hasComponent(SettingsActivity.class.getName()));
        Log.d(TAG, "Fin du test : testSettingsButtonNavigation");
    }

    @Test
    public void testBraceletConnectButtonNavigation() {
        Log.d(TAG, "Début du test : testBraceletConnectButtonNavigation");
        // Check if clicking the braceletConnectButton navigates to BraceletConnectActivity
        onView(withId(R.id.braceletConnectButton)).perform(click());
        intended(hasComponent(BraceletConnectActivity.class.getName()));
        Log.d(TAG, "Fin du test : testBraceletConnectButtonNavigation");
    }

    @Test
    public void testAlarmButtonNavigation() {
        Log.d(TAG, "Début du test : testAlarmButtonNavigation");
        // Check if clicking the alarmButton navigates to AlarmActivity
        onView(withId(R.id.alarmButton)).perform(click());
        intended(hasComponent(AlarmActivity.class.getName()));
        Log.d(TAG, "Fin du test : testAlarmButtonNavigation");
    }
}