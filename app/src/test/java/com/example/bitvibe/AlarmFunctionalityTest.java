package com.example.bitvibe;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
//import androidx.test.espresso.contrib.ToastMatchers;
//import androidx.test.ext.junit.rules.ActivityScenarioRule;
//import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.atomic.AtomicReference;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

//@RunWith(AndroidJUnit4.class)
public class AlarmFunctionalityTest {

//    @Rule
//    public androidx.test.ext.junit.rules.ActivityScenarioRule<AlarmActivity> alarmActivityScenarioRule = new androidx.test.ext.junit.rules.ActivityScenarioRule<>(AlarmActivity.class);
//
//    @Rule
//    public androidx.test.ext.junit.rules.ActivityScenarioRule<MainActivity> mainActivityScenarioRule = new androidx.test.ext.junit.rules.ActivityScenarioRule<>(MainActivity.class);

    @Mock
    private BinanceApi mockBinanceApi;

//    private ToastIdlingResource toastIdlingResource;

//    @Before
//    public void setUp() {
//        MockitoAnnotations.openMocks(this);
//        MainActivity.binanceApi = mockBinanceApi;
//        toastIdlingResource = new ToastIdlingResource();
//        IdlingRegistry.getInstance().register(toastIdlingResource);
//    }

//    @After
//    public void tearDown() {
//        IdlingRegistry.getInstance().unregister(toastIdlingResource);
//    }

//    // Teste si l'alarme est déclenchée lorsque le prix est supérieur au prix cible (is_above = true)
//    @Test
//    public void alarmTriggers_whenPriceIsCorrect_above() throws InterruptedException {
//        // Récupère les préférences partagées depuis le contexte
//        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
//        SharedPreferences prefs = context.getSharedPreferences("BitVibePrefs", Context.MODE_PRIVATE);
//        //1. parametre l'alarme dans les préférences partagées
//        SharedPreferences.Editor editor = prefs.edit();
//        editor.putString("trigger_price", "20000.0");
//        editor.putBoolean("is_above", true);
//        editor.apply();
//        // 2. Mock l'API
//        // Crée une réponse simulée de BinancePriceResponse avec un prix qui déclenche l'alarme (supérieur à 20000)
//        BinancePriceResponse mockResponse = new BinancePriceResponse("BTCUSDT", 25000.0);
//        // Simule l'appel à getBitcoinPrice pour retourner une réponse réussie
//        Call<BinancePriceResponse> mockCall = mock(Call.class);
//        when(mockBinanceApi.getBitcoinPrice()).thenReturn(mockCall);
//        doAnswer(invocation -> {
//            Callback<BinancePriceResponse> callback = invocation.getArgument(0);
//            callback.onResponse(mockCall, Response.success(mockResponse));
//            return null;
//        }).when(mockCall).enqueue(any()); // Configure le comportement de l'appel en attente
//
//        // 3. demarre le service (pour que l'alarme soit vérifiée)
//        mainActivityScenarioRule.getScenario().onActivity(activity -> {
//            // Start the service
//            if (prefs.getBoolean("is_alarm_on", false)) {
//                activity.startService(new Intent(activity, AlarmCheckService.class));
//            }
//        });
//        // 4. Vérifie le Toast
//        // Attend l'apparition du Toast (en utilisant notre IdlingResource)
//        toastIdlingResource.waitForToast();
//        androidx.test.espresso.Espresso.onView(withText("Alarm triggered: price increase!")).inRoot(ToastMatchers.isToast()).check(matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
//    }

//    @Test
//    public void alarmTriggers_whenPriceIsCorrect_below() throws InterruptedException {
//        // Get the shared preferences from the context
//        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
//        SharedPreferences prefs = context.getSharedPreferences("BitVibePrefs", Context.MODE_PRIVATE);
//        //1. set the alarm in shared preference
//        SharedPreferences.Editor editor = prefs.edit();
//        editor.putString("trigger_price", "20000.0");
//        editor.putBoolean("is_above", false);
//        editor.apply();
//        //2. Mock the API
//        // Create a mock BinancePriceResponse with a price that triggers the alarm (below 20000)
//        BinancePriceResponse mockResponse = new BinancePriceResponse("BTCUSDT", 15000.0);
//        // Mock the call to getBitcoinPrice to return a successful response
//        Call<BinancePriceResponse> mockCall = mock(Call.class);
//        when(mockBinanceApi.getBitcoinPrice()).thenReturn(mockCall);
//        doAnswer(invocation -> {
//            Callback<BinancePriceResponse> callback = invocation.getArgument(0);
//            callback.onResponse(mockCall, Response.success(mockResponse));
//            return null;
//        }).when(mockCall).enqueue(any());
//
//        // 3. Start the service (for the alarm to be checked)
//        mainActivityScenarioRule.getScenario().onActivity(activity -> {
//            // Start the service
//            if (prefs.getBoolean("is_alarm_on", false)) {
//                activity.startService(new Intent(activity, AlarmCheckService.class));
//            }
//        });
//        //4. Check for the toast
//        // Wait for the Toast to appear (using our IdlingResource)
//        toastIdlingResource.waitForToast();
//        androidx.test.espresso.Espresso.onView(withText("Alarm triggered: price drop!")).inRoot(ToastMatchers.isToast()).check(matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
//
//    }
}