package com.example.communitymember20;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
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

public class DriverProfileSettings extends BaseActivity {

    private MaterialToolbar toolbar;
    private TextView tvProfileName, tvProfileRoleBadge, tvProfileStatus, tvProfileError;
    private MaterialCardView cardProfileStatus, cardProfileError;
    private TextInputEditText etProfileFullName, etProfilePhone, etProfileEmail, etLicense;
    private TextInputLayout tilFullName, tilPhone, tilLicense, tilLanguage;
    private AutoCompleteTextView dropdownLanguage;
    private MaterialButton btnSave, btnChangePassword;
    private ProgressBar progressBar;

    private DatabaseReference dbRef;
    private FirebaseAuth mAuth;
    private String uid;

    private String originalName = "";
    private String originalPhone = "";
    private String originalLicense = "";
    private String originalLanguage = "English";
    private String selectedLanguage = "English";

    private final String[] displayLanguages = {"English", "Setswana", "isiZulu", "Afrikaans"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driver_profile_settings);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        dbRef = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            finish();
            return;
        }
        uid = user.getUid();

        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileRoleBadge = findViewById(R.id.tvProfileRoleBadge);
        tvProfileStatus = findViewById(R.id.tvProfileStatus);
        tvProfileError = findViewById(R.id.tvProfileError);
        cardProfileStatus = findViewById(R.id.cardProfileStatus);
        cardProfileError = findViewById(R.id.cardProfileError);
        etProfileFullName = findViewById(R.id.etProfileFullName);
        etProfilePhone = findViewById(R.id.etProfilePhone);
        etProfileEmail = findViewById(R.id.etProfileEmail);
        etLicense = findViewById(R.id.etLicense);
        tilFullName = findViewById(R.id.tilFullName);
        tilPhone = findViewById(R.id.tilPhone);
        tilLicense = findViewById(R.id.tilLicense);
        tilLanguage = findViewById(R.id.tilLanguage);
        dropdownLanguage = findViewById(R.id.dropdownLanguage);
        btnSave = findViewById(R.id.btnSave);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        progressBar = findViewById(R.id.progressBar);

        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(
                this, R.layout.item_dropdown, displayLanguages);
        dropdownLanguage.setAdapter(langAdapter);
        dropdownLanguage.setOnItemClickListener((parent, view, position, id) -> {
            selectedLanguage = (String) parent.getItemAtPosition(position);
        });

        btnSave.setOnClickListener(v -> saveProfile());
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        loadProfile();
    }

    private void loadProfile() {
        progressBar.setVisibility(View.VISIBLE);

        // Drivers live in users/drivers/
        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS).child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progressBar.setVisibility(View.GONE);
                        if (!snapshot.exists()) return;

                        String name = snapshot.child("full_name").getValue(String.class);
                        String email = snapshot.child("email").getValue(String.class);
                        String phone = snapshot.child("phone_number").getValue(String.class);
                        String license = snapshot.child("license_no").getValue(String.class);
                        String lang = snapshot.child("preferred_lang").getValue(String.class);
                        Boolean complete = snapshot.child("profile_complete").getValue(Boolean.class);

                        originalName = name != null ? name : "";
                        originalPhone = phone != null ? phone : "";
                        originalLicense = license != null ? license : "";
                        originalLanguage = lang != null ? lang : "English";
                        selectedLanguage = originalLanguage;

                        if (tvProfileName != null) tvProfileName.setText(
                                originalName.isEmpty() ? "Driver" : originalName);
                        if (tvProfileRoleBadge != null) tvProfileRoleBadge.setText("Driver");

                        if (complete != null && complete) {
                            if (tvProfileStatus != null) tvProfileStatus.setText("✓ Profile Verified");
                            if (cardProfileStatus != null) {
                                cardProfileStatus.setCardBackgroundColor(0xFFE6F8FA);
                                cardProfileStatus.setStrokeColor(0xFFD3EFF5);
                            }
                            if (tvProfileStatus != null) tvProfileStatus.setTextColor(0xFF00A3B8);
                        } else {
                            if (tvProfileStatus != null) tvProfileStatus.setText("⚠ Profile Incomplete");
                            if (tvProfileStatus != null) tvProfileStatus.setTextColor(0xFFC2410C);
                        }

                        if (etProfileFullName != null) etProfileFullName.setText(originalName);
                        if (etProfilePhone != null) etProfilePhone.setText(originalPhone);
                        if (etProfileEmail != null) etProfileEmail.setText(email != null ? email : "");
                        if (etLicense != null) etLicense.setText(originalLicense);

                        dropdownLanguage.setText(originalLanguage, false);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.GONE);
                        showError("Failed to load profile: " + error.getMessage());
                    }
                });
    }

    private void saveProfile() {
        hideError();

        String name = etProfileFullName.getText() != null
                ? etProfileFullName.getText().toString().trim() : "";
        String phone = etProfilePhone.getText() != null
                ? etProfilePhone.getText().toString().trim() : "";
        String license = etLicense.getText() != null
                ? etLicense.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            tilFullName.setError("Full Name is required");
            etProfileFullName.requestFocus();
            return;
        } else {
            tilFullName.setError(null);
        }

        if (TextUtils.isEmpty(phone)) {
            tilPhone.setError("Phone Number is required");
            etProfilePhone.requestFocus();
            return;
        } else {
            tilPhone.setError(null);
        }

        if (TextUtils.isEmpty(license)) {
            tilLicense.setError("License number is required");
            etLicense.requestFocus();
            return;
        } else if (license.length() < 5) {
            tilLicense.setError("Please enter a valid license number");
            etLicense.requestFocus();
            return;
        } else {
            tilLicense.setError(null);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("full_name", name);
        updates.put("phone_number", phone);
        updates.put("license_no", license);
        updates.put("profile_complete", true);
        if (!TextUtils.isEmpty(selectedLanguage)) {
            updates.put("preferred_lang", selectedLanguage);
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);
        btnSave.setText("Saving…");

        // Drivers live in users/drivers/
        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS).child(uid)
                .updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);
                    btnSave.setText("Save Changes");

                    originalName = name;
                    originalPhone = phone;
                    originalLicense = license;
                    originalLanguage = selectedLanguage;

                    if (tvProfileName != null) tvProfileName.setText(name);
                    if (tvProfileStatus != null) {
                        tvProfileStatus.setText("✓ Profile Verified");
                        tvProfileStatus.setTextColor(0xFF00A3B8);
                    }
                    if (cardProfileStatus != null) {
                        cardProfileStatus.setCardBackgroundColor(0xFFE6F8FA);
                        cardProfileStatus.setStrokeColor(0xFFD3EFF5);
                    }

                    LocaleHelper.showTranslatedToast(DriverProfileSettings.this,
                            "Profile updated successfully ✓", Toast.LENGTH_SHORT);

                    Intent intent = new Intent(DriverProfileSettings.this, DriverDashboard.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);
                    btnSave.setText("Save Changes");
                    showError("Failed: " + e.getMessage());
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
        progressBar.setVisibility(View.VISIBLE);
        btnChangePassword.setEnabled(false);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    btnChangePassword.setEnabled(true);

                    if (task.isSuccessful()) {
                        LocaleHelper.showTranslatedToast(DriverProfileSettings.this,
                                "Reset link sent. Check your inbox.",
                                Toast.LENGTH_LONG);
                    } else {
                        showError("Couldn't send reset email. Please try again.");
                    }
                });
    }

    private void showError(String message) {
        if (cardProfileError == null || tvProfileError == null) return;
        tvProfileError.setText(message);
        cardProfileError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        if (cardProfileError != null) cardProfileError.setVisibility(View.GONE);
    }
}