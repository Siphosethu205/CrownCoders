package com.example.communitymember20;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.google.firebase.auth.FirebaseAuth;

public class WelcomePage extends BaseActivity {

    private Spinner spinnerLanguage;
    private Button btnContinue;
    private SharedPreferences sharedPreferences;

    private FirebaseAuth mAuth;

    private final String[] displayLanguages = {"English", "Setswana", "isiZulu", "Afrikaans"};

    // ============================================================
    // Runs BEFORE onCreate and BEFORE BaseActivity.attachBaseContext.
    //
    // We reset the saved language to English here, so by the time
    // BaseActivity reads it, it sees "English" and does NOT wrap
    // the context in the previous user's locale.
    //
    // Result: the Welcome Page always renders in English.
    // ============================================================
    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("AquacommPrefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("preferred_lang", "English")
                .remove("user_role")
                .remove("is_guest")
                .apply();

        // Now call super — BaseActivity will read "English" and skip its locale wrap
        super.attachBaseContext(newBase);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome_page);

        sharedPreferences = getSharedPreferences("AquacommPrefs", Context.MODE_PRIVATE);
        mAuth = FirebaseAuth.getInstance();

        // Sign out any lingering Firebase session
        if (mAuth.getCurrentUser() != null) {
            mAuth.signOut();
        }

        // Bind UI
        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        btnContinue = findViewById(R.id.btnContinue);

        // Language spinner — always default to English
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, displayLanguages);
        spinnerLanguage.setAdapter(langAdapter);
        spinnerLanguage.setSelection(0); // English

        // Continue button
        btnContinue.setOnClickListener(v -> {
            if (spinnerLanguage.getSelectedItem() == null) {
                return;
            }

            String selectedDisplayLang = spinnerLanguage.getSelectedItem().toString();

            // Save the user's new choice — this becomes the app language
            // for every subsequent screen (Login, Dashboard, etc.)
            sharedPreferences.edit()
                    .putString("preferred_lang", selectedDisplayLang)
                    .apply();

            Intent intent = new Intent(WelcomePage.this, LoginPage.class);
            startActivity(intent);
            finish();
        });
    }
}