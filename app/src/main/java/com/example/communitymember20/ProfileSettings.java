package com.example.communitymember20;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class ProfileSettings extends BaseActivity {

    private static final String TAG = "PROFILE_SETTINGS";

    private MaterialToolbar topAppBar;
    private TextView tvProfileName, tvProfileRoleBadge, tvProfileError;
    private TextInputEditText etProfileFullName, etProfilePhone, etProfileEmail;
    private TextInputLayout tilFullName, tilPhone;
    private AutoCompleteTextView dropdownLanguage;
    private MaterialButton btnSaveProfile, btnChangePassword;
    private MaterialCardView cardProfileError;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private SharedPreferences sharedPreferences;

    private final String[] displayLanguages = {"English", "Setswana", "isiZulu", "Afrikaans"};

    private String originalName = "";
    private String originalPhone = "";
    private String originalLanguage = "English";
    private String selectedLanguage = "English";
    private String userNode = UserNodeHelper.NODE_MEMBERS;

    private String errorNameRequired = "Full Name is required";
    private String errorPhoneRequired = "Phone Number is required";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_settings);

        sharedPreferences = getSharedPreferences("AquacommPrefs", Context.MODE_PRIVATE);
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        userNode = sharedPreferences.getString("user_node", UserNodeHelper.NODE_MEMBERS);

        topAppBar = findViewById(R.id.topAppBar);
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileRoleBadge = findViewById(R.id.tvProfileRoleBadge);
        tvProfileError = findViewById(R.id.tvProfileError);
        cardProfileError = findViewById(R.id.cardProfileError);
        etProfileFullName = findViewById(R.id.etProfileFullName);
        etProfilePhone = findViewById(R.id.etProfilePhone);
        etProfileEmail = findViewById(R.id.etProfileEmail);
        tilFullName = findViewById(R.id.tilFullName);
        tilPhone = findViewById(R.id.tilPhone);
        dropdownLanguage = findViewById(R.id.dropdownLanguage);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        progressBar = findViewById(R.id.progressBar);

        topAppBar.setNavigationOnClickListener(v -> finish());

        LocaleHelper.translateStringApi(this, "Full Name is required",
                t -> errorNameRequired = t);
        LocaleHelper.translateStringApi(this, "Phone Number is required",
                t -> errorPhoneRequired = t);

        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(
                this, R.layout.item_dropdown, displayLanguages);
        dropdownLanguage.setAdapter(langAdapter);

        dropdownLanguage.setOnItemClickListener((parent, view, position, id) -> {
            selectedLanguage = (String) parent.getItemAtPosition(position);
        });

        btnSaveProfile.setOnClickListener(v -> saveProfile());
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            loadProfile(currentUser.getUid(), currentUser.getEmail());
        } else {
            showError("You're not signed in.");
        }
    }

    private void loadProfile(String userId, String fallbackEmail) {
        progressBar.setVisibility(View.VISIBLE);

        mDatabase.child("users").child(userNode).child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progressBar.setVisibility(View.GONE);

                        if (!snapshot.exists()) {
                            showError("Couldn't load your profile.");
                            return;
                        }

                        String fullName = snapshot.child("full_name").getValue(String.class);
                        String phone = snapshot.child("phone_number").getValue(String.class);
                        String email = snapshot.child("email").getValue(String.class);
                        String role = snapshot.child("role").getValue(String.class);
                        String lang = snapshot.child("preferred_lang").getValue(String.class);

                        originalName = fullName != null ? fullName : "";
                        originalPhone = phone != null ? phone : "";
                        originalLanguage = lang != null ? lang : "English";
                        selectedLanguage = originalLanguage;

                        if (fullName != null && !fullName.isEmpty()) {
                            tvProfileName.setTag(null);
                            tvProfileName.setText(fullName);
                        }

                        if (role != null) {
                            tvProfileRoleBadge.setTag(null);
                            tvProfileRoleBadge.setText(role);
                        }

                        if (etProfileFullName != null) etProfileFullName.setText(originalName);
                        if (etProfilePhone != null) etProfilePhone.setText(originalPhone);
                        if (etProfileEmail != null) {
                            String emailToShow = (email != null && !email.isEmpty())
                                    ? email
                                    : (fallbackEmail != null ? fallbackEmail : "");
                            etProfileEmail.setText(emailToShow);
                        }

                        dropdownLanguage.setText(originalLanguage, false);

                        LocaleHelper.translateAllViews(ProfileSettings.this, tvProfileName);
                        LocaleHelper.translateAllViews(ProfileSettings.this, tvProfileRoleBadge);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.GONE);
                        Log.e(TAG, "Load failed: " + error.getMessage());
                        showError("Couldn't load your profile. Check your connection.");
                    }
                });
    }

    private void saveProfile() {
        hideError();

        String newName = etProfileFullName.getText() != null
                ? etProfileFullName.getText().toString().trim() : "";
        String newPhone = etProfilePhone.getText() != null
                ? etProfilePhone.getText().toString().trim() : "";
        String newLanguage = !TextUtils.isEmpty(selectedLanguage)
                ? selectedLanguage
                : dropdownLanguage.getText().toString().trim();

        boolean valid = true;
        if (TextUtils.isEmpty(newName)) {
            tilFullName.setError(errorNameRequired);
            valid = false;
        } else {
            tilFullName.setError(null);
        }
        if (TextUtils.isEmpty(newPhone)) {
            tilPhone.setError(errorPhoneRequired);
            valid = false;
        } else {
            tilPhone.setError(null);
        }
        if (!valid) return;

        boolean nameChanged = !newName.equals(originalName);
        boolean phoneChanged = !newPhone.equals(originalPhone);
        boolean languageChanged = !newLanguage.equalsIgnoreCase(originalLanguage);

        if (!nameChanged && !phoneChanged && !languageChanged) {
            LocaleHelper.showTranslatedToast(this, "No changes to save.", Toast.LENGTH_SHORT);
            return;
        }

        if (!isNetworkAvailable()) {
            showError("No internet connection. Please try again.");
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            showError("You're not signed in.");
            return;
        }
        String userId = currentUser.getUid();

        Map<String, Object> updates = new HashMap<>();
        if (nameChanged) updates.put("full_name", newName);
        if (phoneChanged) updates.put("phone_number", newPhone);
        if (languageChanged) updates.put("preferred_lang", newLanguage);

        progressBar.setVisibility(View.VISIBLE);
        btnSaveProfile.setEnabled(false);
        btnSaveProfile.setText("Saving…");

        mDatabase.child("users").child(userNode).child(userId).updateChildren(updates)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    btnSaveProfile.setEnabled(true);
                    btnSaveProfile.setText("Save Changes");

                    if (task.isSuccessful()) {
                        if (languageChanged) {
                            sharedPreferences.edit()
                                    .putString("preferred_lang", newLanguage)
                                    .apply();
                        }

                        originalName = newName;
                        originalPhone = newPhone;
                        originalLanguage = newLanguage;

                        if (nameChanged && !newName.isEmpty()) {
                            tvProfileName.setTag(null);
                            tvProfileName.setText(newName);
                            LocaleHelper.translateAllViews(ProfileSettings.this, tvProfileName);
                        }

                        LocaleHelper.showTranslatedToast(ProfileSettings.this,
                                "Profile updated successfully!", Toast.LENGTH_LONG);
                    } else {
                        String msg = task.getException() != null
                                ? task.getException().getMessage() : "Unknown error";
                        showError("Failed to save changes: " + msg);
                    }
                });
    }

    private void showChangePasswordDialog() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) {
            showError("You're not signed in.");
            return;
        }

        final String email = currentUser.getEmail();

        new MaterialAlertDialogBuilder(this)
                .setTitle("Change Password")
                .setMessage("We'll send a password reset link to:\n\n" + email)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send Link", (dialog, which) ->
                        sendPasswordResetEmail(email))
                .show();
    }

    private void sendPasswordResetEmail(String email) {
        if (!isNetworkAvailable()) {
            showError("No internet connection. Please try again.");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnChangePassword.setEnabled(false);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    btnChangePassword.setEnabled(true);

                    if (task.isSuccessful()) {
                        LocaleHelper.showTranslatedToast(ProfileSettings.this,
                                "Reset link sent. Check your inbox.",
                                Toast.LENGTH_LONG);
                    } else {
                        showError("Couldn't send reset email. Please try again.");
                    }
                });
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } catch (Exception e) {
            return false;
        }
    }

    private void showError(String message) {
        if (cardProfileError == null || tvProfileError == null) return;
        tvProfileError.setTag(null);
        tvProfileError.setText(message);
        cardProfileError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        if (cardProfileError != null) cardProfileError.setVisibility(View.GONE);
    }
}