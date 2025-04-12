package com.example.bitvibe;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.util.Log;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AlarmActivityTest {

    private static final String TAG = "AlarmActivityTest";

    @Rule
    public ActivityScenarioRule<AlarmActivity> activityScenarioRule =
            new ActivityScenarioRule<>(AlarmActivity.class);

    @Test
    public void testAlarmActivityElementsDisplayed() {
        Log.d(TAG, "Début du test : testAlarmActivityElementsDisplayed");
        // Check if key elements are displayed
        onView(withId(R.id.highTriggerPriceEditText)).check(matches(isDisplayed()));
        onView(withId(R.id.lowTriggerPriceEditText)).check(matches(isDisplayed()));
        onView(withId(R.id.highAlarmSwitch)).check(matches(isDisplayed()));
        onView(withId(R.id.lowAlarmSwitch)).check(matches(isDisplayed()));
        onView(withId(R.id.currentPriceTextView)).check(matches(isDisplayed()));
        onView(withId(R.id.currentCryptoSymbolTextView)).check(matches(isDisplayed()));
        onView(withId(R.id.volatilityThresholdEditText)).check(matches(isDisplayed()));
        onView(withId(R.id.volatilityAlarmSwitch)).check(matches(isDisplayed()));
        onView(withId(R.id.setVolatilityReferenceButton)).check(matches(isDisplayed()));
        onView(withId(R.id.mainActivityButton)).check(matches(isDisplayed()));
        Log.d(TAG, "Fin du test : testAlarmActivityElementsDisplayed");
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