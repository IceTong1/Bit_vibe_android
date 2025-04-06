package com.example.bitvibe;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
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
import android.os.Looper;

// Import de la nouvelle activité
import com.example.bitvibe.bracelet.BraceletConnectActivity;

// Activité principale de l'application BitVibe
public class MainActivity extends AppCompatActivity {
    private SharedPreferences prefs;   //pour les settings
    // Constante pour les logs
    private static final String TAG = "MainActivity";
    // Code de demande pour les permissions Bluetooth
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    // Interface pour appeler l'API de Binance
    public static BinanceApi binanceApi;
    // TextView pour afficher le prix du Bitcoin
    private TextView bitcoinPriceTextView;
    // EditText pour entrer la tolérance de variation (Note : nom de variable "tolassociatedérance" semble incorrect)
    private EditText toleranceEditText;
    // Handler pour exécuter des tâches sur le thread principal
    private Handler handler = new Handler(Looper.getMainLooper()); // Préciser Looper.getMainLooper() est une bonne pratique
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

        // --- Bouton Paramètres ---
        // Associer le bouton au code
        Button settingsButton = findViewById(R.id.settingsButton);
        // Ajouter le listener pour ouvrir SettingsActivity
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // --- NOUVEAU : Bouton Connexion Bracelets ---
        // Associer le nouveau bouton (suppose que son ID dans activity_main.xml est "braceletConnectButton")
        Button braceletConnectButton = findViewById(R.id.braceletConnectButton); // Assurez-vous que cet ID existe dans votre XML
        // Ajouter le listener pour ouvrir BraceletConnectActivity
        braceletConnectButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BraceletConnectActivity.class);
            startActivity(intent);
        });
        // --- Fin NOUVEAU ---

        // --- Bouton Alarm ---
        // Associer le bouton au code
        Button alarmButton = findViewById(R.id.alarmButton);
        // Ajouter le listener pour ouvrir SettingsActivity
        alarmButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AlarmActivity.class);
            startActivity(intent);
        });



        // Créer ou charger les préférences partagées
        prefs = getSharedPreferences("BitVibePrefs", MODE_PRIVATE); // Sauvegarder dans un fichier nommé "BitVibePrefs"
        SharedPreferences.Editor editor = prefs.edit();
        // Définir les valeurs par défaut seulement si elles n'existent pas
        if (!prefs.contains("refresh_interval")) {
            editor.putInt("refresh_interval", 5);          // Intervalle minimum de vibration : 5s
        }
        if (!prefs.contains("vibration_intensity")) {
            editor.putInt("vibration_intensity", 2);       // Intensité de vibration : niveau 2
        }
        if (!prefs.contains("currency")) {
            editor.putString("currency", "EUR");           // Devise : Euros
        }
        if (!prefs.contains("language")) {
            editor.putString("language", "fr");            // Langue : Français
        }
        if (!prefs.contains("tolerance_percentage")) {
            editor.putFloat("tolerance_percentage", (float) 0.01); // Tolérance 0.01% ? Ou 1% ? (0.01 = 1%)
        }
        editor.apply(); // Sauvegarde les modifications


        /////////////////////////// Lire les valeurs des paramètres pour debug //////////////////////////////////
        minInterval = prefs.getInt("refresh_interval", 5);   // Valeur par défaut 5 si pas trouvée
        int intensity = prefs.getInt("vibration_intensity", -1);
        String currency = prefs.getString("currency", "EUR"); // Valeur par défaut EUR si pas trouvée
        String language = prefs.getString("language", "fr");  // Valeur par défaut fr si pas trouvée
        float tolerancePercentage = prefs.getFloat("tolerance_percentage", 0.01f); // Valeur par défaut 0.01f si pas trouvée

        Log.d(TAG, "VALEURS CHARGEES : refresh_interval="
                + minInterval
                + ", vibration_intensity=" + intensity
                + ", currency=" + currency
                + ", language=" + language
                + ", tolerance_percentage=" + tolerancePercentage);
        ///////////////////////////////////////////////////////////////////////////////////////////////////////


        // Associe les vues aux éléments du layout
        bitcoinPriceTextView = findViewById(R.id.bitcoinPriceTextView);
        // Note: toleranceEditText n'est pas initialisé ici via findViewById. S'il existe dans le layout, il faudrait l'ajouter.
        // toleranceEditText = findViewById(R.id.toleranceEditText); // Exemple si l'ID existe


        /// ///////////////////////////BLUETOOTH////////////////////////////////////////////////////
        // Vérifie si les permissions Bluetooth sont accordées, sinon les demande
        // AJOUT: Vérifier aussi les permissions de localisation si nécessaire pour le scan pré-Android 12
        // (Bien que la demande soit faite ici, la logique de scan dans BraceletConnectActivity devra aussi gérer ces permissions)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { // Ajout vérification localisation
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.ACCESS_FINE_LOCATION // Ajout demande localisation
                    },
                    REQUEST_BLUETOOTH_PERMISSIONS);
        }
        ////////////////////////////////////////////////////////////////////////////////////////////


        // Configure Retrofit pour communiquer avec l'API de Binance
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.binance.com/") // URL de base de l'API
                .addConverterFactory(GsonConverterFactory.create()) // Utilise Gson pour parser le JSON
                .build();

        // Crée une instance de l'interface BinanceApi
        binanceApi = retrofit.create(BinanceApi.class);


        //////////////////////////////// A AMELIORER ///////////////////////////////////////////
        // L'initialisation de la boucle ici pourrait être problématique si les permissions ne sont pas encore accordées.
        // Idéalement, démarrer la boucle seulement après confirmation des permissions nécessaires.
        ////////////////////////////////////////////////////////////////////////////////////////
        initializeLoop(); // Initialise la boucle de récupération du prix
    }


    private void initializeLoop() {
        // Vérifie si le Runnable a déjà été initialisé
        if (!this.asBeenInitialized) { // Simplification de la condition
            this.asBeenInitialized = true; // Marque comme initialisé
            Log.d(TAG, "Initialisation de la boucle de récupération du prix");
            runnable = new Runnable() {
                @Override
                public void run() {
                    fetchBitcoinPrice(); // Récupère le prix
                    // Reprogramme l'exécution après l'intervalle défini (converti en millisecondes)
                    handler.postDelayed(this, minInterval * 1000L);
                }
            };
            handler.post(runnable); // Lance la première exécution immédiatement
        }
    }

    // onResume est appelée lorsque l'activité redevient visible
    @Override
    protected void onResume() {
        super.onResume();
        // Assurez-vous que le runnable existe avant de tenter de le supprimer/reposter
        if (runnable != null) {
             handler.removeCallbacks(runnable); // Arrête les exécutions programmées précédentes

             // Recharge l'intervalle depuis les préférences (au cas où il aurait changé dans SettingsActivity)
             minInterval = prefs.getInt("refresh_interval", 5);
             Log.d(TAG, "onResume: Reprise de la boucle avec intervalle = " + minInterval + "s");

             handler.post(runnable); // Redémarre la boucle immédiatement
        } else if (!asBeenInitialized) {
             // Si le runnable n'a jamais été initialisé (par exemple si onCreate n'a pas pu le faire
             // à cause des permissions manquantes initialement), on pourrait tenter de l'initialiser ici
             // après avoir vérifié à nouveau les permissions. Pour l'instant, on log seulement.
             Log.w(TAG, "onResume: Runnable non initialisé.");
        }

    }

    // onPause est appelée lorsque l'activité n'est plus au premier plan
    //TODO verifier si marche toujour en arriere plan sinon ne jamais arrêter la boucle et suppr onPause
    @Override
    protected void onPause() {
        super.onPause();
         // Arrête la boucle lorsque l'activité est mise en pause pour économiser les ressources
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
             Log.d(TAG, "onPause: Boucle de récupération du prix mise en pause.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // S'assure d'arrêter la boucle lorsque l'activité est détruite
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
             Log.d(TAG, "onDestroy: Boucle de récupération du prix arrêtée.");
        }
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
                    Log.d(TAG, "Prix du Bitcoin : " + currentPrice + "lastPrice : " + lastPrice);
                    bitcoinPriceTextView.setText("Prix du Bitcoin : $" + currentPrice); // Met à jour l'affichage

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
            public void onFailure(Call<BinancePriceResponse> call, Throwable t) { // En cas d'échec réseau ou autre problème
                Log.e(TAG, "Échec de la requête API : " + t.getMessage(), t); // Logger l'exception complète est utile
                bitcoinPriceTextView.setText("Échec de la connexion");
            }
        });
    }

    // Méthode pour gérer les résultats des demandes de permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) { // Vérifie si c'est une réponse pour nos permissions
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) { // Toutes les permissions (Bluetooth Connect, Scan, Fine Location) ont été accordées
                Log.i(TAG, "Toutes les permissions nécessaires (Bluetooth/Localisation) ont été accordées.");
                // On pourrait relancer l'initialisation de la boucle ici si elle n'a pas pu se faire dans onCreate
                if (!asBeenInitialized) {
                    initializeLoop();
                }
            } else { // Au moins une permission a été refusée
                Log.e(TAG, "Au moins une permission Bluetooth ou Localisation a été refusée.");
                // Afficher un message plus clair à l'utilisateur
                Toast.makeText(this, "Les permissions Bluetooth et Localisation sont nécessaires pour la connexion aux bracelets.", Toast.LENGTH_LONG).show();
                bitcoinPriceTextView.setText("Permissions requises"); // Mettre à jour l'UI pour indiquer le problème
                // On pourrait désactiver le bouton de connexion aux bracelets ici
                 Button braceletConnectButton = findViewById(R.id.braceletConnectButton);
                 if (braceletConnectButton != null) {
                     braceletConnectButton.setEnabled(false);
                 }
            }
        }
    }
}