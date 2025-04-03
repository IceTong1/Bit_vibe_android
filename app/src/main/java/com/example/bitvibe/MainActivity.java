package com.example.bitvibe;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import android.content.SharedPreferences;
import android.os.Handler;

// Activité principale de l'application BitVibe
public class MainActivity extends AppCompatActivity {
    private SharedPreferences prefs;  //pour les settings
    // Constante pour les logs
    private static final String TAG = "MainActivity";
    // Code de demande pour les permissions Bluetooth
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    // Interface pour appeler l'API de Binance
    private BinanceApi binanceApi;
    // TextView pour afficher le prix du Bitcoin
    private TextView bitcoinPriceTextView;
    // Handler pour exécuter des tâches sur le thread principal
    private final Handler handler = new Handler();
    // Runnable pour récupérer le prix périodiquement
    private Runnable runnable;
    // Dernier prix connu du Bitcoin (-1 au départ)
    private double lastPrice = -1;
    // Intervalle minimum entre les mises à jour du prix (en secondes)
    private int minInterval;
    // Variable pour vérifier si le Runnable a été initialisé
    private boolean asBeenInitialized = false;

    // Méthode appelée lors de la création de l'activité
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Charge le layout de l'activité depuis activity_main.xml
        setContentView(R.layout.activity_main);

        // Associer le bouton au code
        Button settingsButton = findViewById(R.id.settingsButton);

        // Ajouter le listener pour ouvrir SettingsActivity
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // Créer ou charger les préférences partagées
        prefs = getSharedPreferences("BitVibePrefs", MODE_PRIVATE); // Sauvegarder dans un fichier nommé "BitVibePrefs"
        SharedPreferences.Editor editor = prefs.edit();
        // Définir les valeurs par défaut seulement si elles n'existent pas
        if (!prefs.contains("refresh_interval")) {
            editor.putInt("refresh_interval", 5);           // Intervalle minimum de vibration : 5000ms
        }
        if (!prefs.contains("vibration_intensity")) {
            editor.putInt("vibration_intensity", 2);        // Intensité de vibration : niveau 2
        }
        if (!prefs.contains("currency")) {
            editor.putString("currency", "EUR");            // Devise : Euros
        }
        if (!prefs.contains("language")) {
            editor.putString("language", "fr");             // Langue : Français
        }
        if (!prefs.contains("tolerance_percentage")) {
            editor.putFloat("tolerance_percentage", (float) 0.01);
        }
        editor.apply(); // Sauvegarde les modifications


        /////////////////////////// Lire les valeurs des paramètres pour debug //////////////////////////////////
        minInterval = prefs.getInt("refresh_interval", -1);    // -1 si pas de valeur sauvegardée
        int intensity = prefs.getInt("vibration_intensity", -1);
        String currency = prefs.getString("currency", "null");   //null si pas de valeur sauvegardée
        String language = prefs.getString("language", "null");
        float tolerancePercentage = prefs.getFloat("tolerance_percentage", -1);

        Log.d("MainActivity", "VALEURS SAUVEGARDEES : refresh_interval="
                + minInterval
                + ", vibration_intensity=" + intensity
                + ", currency=" + currency
                + ", language=" + language
                + ", tolerance_percentage=" + tolerancePercentage);
        ///////////////////////////////////////////////////////////////////////////////////////////////////////


        // Associe les vues aux éléments du layout
        bitcoinPriceTextView = findViewById(R.id.bitcoinPriceTextView);


        // Configure Retrofit pour communiquer avec l'API de Binance
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.binance.com/") // URL de base de l'API
                .addConverterFactory(GsonConverterFactory.create()) // Utilise Gson pour parser le JSON
                .build();

        // Crée une instance de l'interface BinanceApi
        binanceApi = retrofit.create(BinanceApi.class);


        initializeLoop(); // Lance la boucle de récupération du prix
    }

    private void initializeLoop() {
        // Check if the Runnable has already been initialized
        if (this.asBeenInitialized == false) {
            this.asBeenInitialized = true; // Set the flag to true to indicate it has been initialized
            Log.d(TAG, "FIRST TIME AND LAST");
            runnable = new Runnable() {
                @Override
                public void run() {
                    fetchBitcoinPrice(); // Fetch the price
                    handler.postDelayed(this, minInterval * 1000L); // Schedule the next fetch
                }
            };
            handler.post(runnable);
        }
    }

    // onResume is called when the activity becomes visible to the user
    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(runnable); // Stop the loop when the activity is paused

        minInterval = prefs.getInt("refresh_interval", 5); // Get the new interval from preferences

        handler.post(runnable); // Restart the loop with the new interval
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(runnable);
    }

    // Méthode pour récupérer le prix du Bitcoin depuis l'API de Binance et afficher la variation
    private void fetchBitcoinPrice() {
        // Crée une requête pour obtenir le prix
        Call<BinancePriceResponse> call = binanceApi.getBitcoinPrice();
        // Exécute la requête de manière asynchrone
        call.enqueue(new Callback<BinancePriceResponse>() {
            @Override
            public void onResponse(Call<BinancePriceResponse> call, Response<BinancePriceResponse> response) {
                // Vérifie si la réponse est valide
                if (response.isSuccessful() && response.body() != null) {
                    double currentPrice = response.body().getPrice(); // Récupère le prix actuel
                    Log.d(TAG, "Prix du Bitcoin (" + response.body().getSymbol() + ") : currentPrice = " + currentPrice + ", lastPrice = " + lastPrice);
                    bitcoinPriceTextView.setText(response.body().getSymbol()+" : $" + currentPrice); // Met à jour l'affichage

                    // Si un dernier prix existe, calcule la variation
                    float tolerancePercentage = prefs.getFloat("tolerance_percentage", -1); // Récupère la tolérance
                    if (lastPrice != -1) {
                        double percentageChange = ((currentPrice - lastPrice) / lastPrice) * 100; // Variation en %
                        if (percentageChange > tolerancePercentage) { // Si hausse significative
                            lastPrice = currentPrice; // Met à jour le dernier prix
                            Log.d(TAG, "Hausse détectée (+" + String.format("%.2f", percentageChange) + "%)");
                            Toast.makeText(MainActivity.this, "Hausse détectée (+" + String.format("%.2f", percentageChange) + "%)", Toast.LENGTH_SHORT).show();
                        } else if (percentageChange < -tolerancePercentage) { // Si baisse significative
                            lastPrice = currentPrice; // Met à jour le dernier prix
                            Log.d(TAG, "Baisse détectée (" + String.format("%.2f", percentageChange) + "%)");
                            Toast.makeText(MainActivity.this, "Baisse détectée (" + String.format("%.2f", percentageChange) + "%)", Toast.LENGTH_SHORT).show();
                        } else { // Si stable
                            Log.d(TAG, "Prix stable (" + String.format("%.2f", percentageChange) + "%)");
                        }
                    } else {
                        lastPrice = currentPrice; // Met à jour le dernier prix si c'est le premier prix récupéré
                    }
                    // Le dernier prix n'est pas mis tant qu'il n'y a pas eu de variation significative

                } else { // En cas d'erreur dans la réponse
                    Log.e(TAG, "Erreur API : " + response.code());
                    bitcoinPriceTextView.setText("Erreur API : " + response.code());
                }
            }

            @Override
            public void onFailure(Call<BinancePriceResponse> call, Throwable t) { // En cas d'échec réseau
                Log.e(TAG, "Échec de la requête : " + t.getMessage());
                bitcoinPriceTextView.setText("Échec de la connexion");
            }
        });
    }
}