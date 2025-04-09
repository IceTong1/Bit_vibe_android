package com.example.bitvibe;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.telecom.Call;
import android.widget.TextView;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE)
public class MainActivityUnitTest {
//
//    @Mock
//    private BinanceApi mockBinanceApi;
//
//    @Mock
//    private TextView mockBitcoinPriceTextView;
//
//    private MainActivity mainActivity;
//
//    @Before
//    public void setUp() {
//        MockitoAnnotations.openMocks(this);
//        mainActivity = new MainActivity(); // You will need to adjust how you create the activity for testing purposes
//        MainActivity.binanceApi = mockBinanceApi;
//        mainActivity.bitcoinPriceTextView = mockBitcoinPriceTextView;
//    }
//
//    // Teste de la methode fetchBitcoinPrice qui appelle l'API Binance pour récupérer le prix du Bitcoin et mettre à jour le TextView
//    @Test
//    public void fetchBitcoinPrice_onResponse_success() {
//        // Cree un mock BinancePriceResponse avec un prix de Bitcoin
//        BinancePriceResponse mockResponse = new BinancePriceResponse("BTCUSDT", 30000.0);
//
//        // Mock la methode getBitcoinPrice pour retourner une reponse réussie
//        Call<BinancePriceResponse> mockCall = mock(Call.class);
//        when(mockBinanceApi.getBitcoinPrice()).thenReturn(mockCall);
//        doAnswer(invocation -> {
//            org.chromium.base.Callback<BinancePriceResponse> callback = invocation.getArgument(0);
//            callback.onResponse(mockCall, androidx.tracing.perfetto.handshake.protocol.Response.success(mockResponse));
//            return null;
//        }).when(mockCall).enqueue(any());
//
//        // Appele la methode fetchBitcoinPrice
//        mainActivity.fetchBitcoinPrice();
//
//        // Verifie que le TextView a été mis à jour avec le prix du Bitcoin
//        verify(mockBitcoinPriceTextView).setText("BTCUSDT : $30000.0");
//    }
}