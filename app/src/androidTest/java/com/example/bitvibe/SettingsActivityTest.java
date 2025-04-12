package com.example.bitvibe;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityTest {

    private static final String TAG = "SettingsActivityTest";
    private SharedPreferences prefs;

    @Rule
    public ActivityScenarioRule<SettingsActivity> activityScenarioRule =
            new ActivityScenarioRule<>(SettingsActivity.class);

    @Before
    public void setUp() {
        // Initialize SharedPreferences (for cleanup)
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = appContext.getSharedPreferences("BitVibePrefs", Context.MODE_PRIVATE);
    }

    @After
    public void tearDown() {
        // Clean up SharedPreferences after each test
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        Log.d(TAG, "Tearing down");
    }

    @Test
    public void testSettingsActivityElementsDisplayed() {
        Log.d(TAG, "Début du test : testSettingsActivityElementsDisplayed");
        // Check if key elements are displayed
        onView(withId(R.id.refresh_interval_edittext)).check(matches(isDisplayed()));
        onView(withId(R.id.currency_spinner)).check(matches(isDisplayed()));
        onView(withId(R.id.notification_type_spinner)).check(matches(isDisplayed()));
        onView(withId(R.id.language_spinner)).check(matches(isDisplayed()));
        onView(withId(R.id.crypto_spinner)).check(matches(isDisplayed()));
        onView(withId(R.id.mainActivityButton)).check(matches(isDisplayed()));
        Log.d(TAG, "Fin du test : testSettingsActivityElementsDisplayed");
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