package com.example.bitvibe;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;

import static org.junit.Assert.assertFalse;

@RunWith(AndroidJUnit4.class)
public class SecurityTest {

    // Teste si les préférences partagées sont sécurisées (Test if shared preferences are secure)
    @Test
    public void checkSharedPreferencesSecure() {
        // Récupérer le contexte de l'application et les préférences partagées (shared preferences)
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("BitVibePrefs", Context.MODE_PRIVATE);

        // Ajouter des données dans SharedPreferences
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("api_key", "testkey");
        editor.apply();

        // Obtenir le chemin du fichier
        File sharedPrefsFile = new File(context.getApplicationInfo().dataDir, "shared_prefs/BitVibePrefs.xml");

        // Vérifier si le fichier est lisible ou accessible en écriture par d'autres
        assertFalse("SharedPreferences file is world-readable", sharedPrefsFile.canRead());
        assertFalse("SharedPreferences file is world-writable", sharedPrefsFile.canWrite());
    }
}