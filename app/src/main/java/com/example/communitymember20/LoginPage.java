package com.example.communitymember20;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginPage extends BaseActivity {

    private EditText editTextEmail, editTextPassword;
    private Button buttonLogin;
    private TextView textViewForgotPassword, textViewRegister, tvLoginError;
    private LinearLayout layoutViewStatus, layoutSignUp;
    private ImageView ivTogglePassword;
    private ProgressBar progressBar;
    private MaterialCardView cardErrorBanner;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_page);

        sharedPreferences = getSharedPreferences("AquacommPrefs", Context.MODE_PRIVATE);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        textViewForgotPassword = findViewById(R.id.textViewForgotPassword);
        layoutViewStatus = findViewById(R.id.textViewGuest);
        layoutSignUp = findViewById(R.id.btnTopSignUp);
        textViewRegister = findViewById(R.id.textViewRegister);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        progressBar = findViewById(R.id.progressBar);
        cardErrorBanner = findViewById(R.id.cardErrorBanner);
        tvLoginError = findViewById(R.id.tvLoginError);

        // "View Area Water Status" is available to everyone (guests + registered)
        layoutViewStatus.setVisibility(View.VISIBLE);

        buttonLogin.setOnClickListener(v -> loginUser());
        textViewForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());

        ivTogglePassword.setOnClickListener(v -> togglePasswordVisibility());

        // ============================================================
        // GUEST ACCESS — Set the is_guest flag then navigate
        // ============================================================
        layoutViewStatus.setOnClickListener(v -> {
            sharedPreferences.edit()
                    .putBoolean("is_guest", true)
                    .apply();

            Intent intent = new Intent(LoginPage.this, ViewWaterStatus.class);
            startActivity(intent);
        });

        View.OnClickListener goToRegister = v -> {
            Intent intent = new Intent(LoginPage.this, RegistrationPage.class);
            startActivity(intent);
        };
        layoutSignUp.setOnClickListener(goToRegister);
        textViewRegister.setOnClickListener(goToRegister);
    }

    // ============================================================
    // PASSWORD VISIBILITY TOGGLE
    // ============================================================
    private void togglePasswordVisibility() {
        if (editTextPassword.getTransformationMethod()
                instanceof PasswordTransformationMethod) {
            editTextPassword.setTransformationMethod(
                    HideReturnsTransformationMethod.getInstance());
        } else {
            editTextPassword.setTransformationMethod(
                    PasswordTransformationMethod.getInstance());
        }
        editTextPassword.setSelection(editTextPassword.getText().length());
    }

    // ============================================================
    // NETWORK & ERROR HELPERS
    // ============================================================
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
        if (cardErrorBanner == null || tvLoginError == null) return;
        tvLoginError.setTag(null);
        tvLoginError.setText(message);
        cardErrorBanner.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        if (cardErrorBanner != null) cardErrorBanner.setVisibility(View.GONE);
    }

    // ============================================================
    // LOGIN
    // ============================================================
    private void loginUser() {
        hideError();

        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            editTextEmail.setError("Please enter a valid email");
            editTextEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.setError("Please enter a valid email");
            editTextEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            editTextPassword.setError("Password is required");
            editTextPassword.requestFocus();
            return;
        }

        if (!isNetworkAvailable()) {
            showError("No internet connection. Please check your network and try again.");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        buttonLogin.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        progressBar.setVisibility(View.GONE);
                        buttonLogin.setEnabled(true);

                        Exception e = task.getException();
                        if (e instanceof FirebaseAuthInvalidCredentialsException) {
                            showError("Wrong email or password. Please try again.");
                        } else if (e instanceof FirebaseAuthInvalidUserException) {
                            showError("No account found with this email. Please register first.");
                        } else if (e instanceof FirebaseAuthUserCollisionException) {
                            showError("This email is already registered. Try logging in instead.");
                        } else {
                            String msg = e != null ? e.getMessage() : "Login failed";
                            showError("Login failed: " + msg);
                        }
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        progressBar.setVisibility(View.GONE);
                        buttonLogin.setEnabled(true);
                        showError("Login failed. Please try again.");
                        return;
                    }

                    if (!user.isEmailVerified()) {
                        progressBar.setVisibility(View.GONE);
                        buttonLogin.setEnabled(true);

                        Intent intent = new Intent(LoginPage.this,
                                EmailVerificationActivity.class);
                        intent.putExtra("email", user.getEmail());
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                        return;
                    }

                    // Clear guest flag — user is a registered account now
                    sharedPreferences.edit().remove("is_guest").apply();

                    resolveRoleAndRoute(user.getUid());
                });
    }

    // ============================================================
    // SEARCH ALL THREE NODES TO FIND THE USER'S ROLE
    // ============================================================
    private void resolveRoleAndRoute(String uid) {
        String[] nodes = UserNodeHelper.allNodes();
        final int[] remaining = { nodes.length };
        final boolean[] found = { false };

        for (String node : nodes) {
            mDatabase.child("users").child(node).child(uid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (found[0]) return;

                            if (snapshot.exists()) {
                                found[0] = true;

                                String role = snapshot.child("role").getValue(String.class);
                                if (role == null || role.isEmpty()) {
                                    role = UserNodeHelper.roleForNode(node);
                                }

                                sharedPreferences.edit()
                                        .putString("user_node", node)
                                        .putString("user_role", role)
                                        .remove("is_guest")
                                        .apply();

                                progressBar.setVisibility(View.GONE);
                                buttonLogin.setEnabled(true);
                                redirectToDashboard(node, role);
                                return;
                            }

                            remaining[0]--;
                            if (remaining[0] == 0 && !found[0]) {
                                progressBar.setVisibility(View.GONE);
                                buttonLogin.setEnabled(true);
                                showError("Your account record is missing. Contact support.");
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            remaining[0]--;
                            if (remaining[0] == 0 && !found[0]) {
                                progressBar.setVisibility(View.GONE);
                                buttonLogin.setEnabled(true);
                                showError("Could not load your account. Please try again.");
                            }
                        }
                    });
        }
    }

    private void redirectToDashboard(String node, String role) {
        Intent intent;
        switch (node) {
            case UserNodeHelper.NODE_ADMINS:
                intent = new Intent(LoginPage.this, AdminDashboard.class);
                break;
            case UserNodeHelper.NODE_DRIVERS:
                intent = new Intent(LoginPage.this, DriverDashboard.class);
                break;
            default:
                intent = new Intent(LoginPage.this, CommunityMemberDashboard.class);
                break;
        }

        LocaleHelper.showTranslatedToast(this,
                "Login successful as " + role, Toast.LENGTH_SHORT);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ============================================================
    // FORGOT PASSWORD
    // ============================================================
    private void showForgotPasswordDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_forgot_password, null);

        TextInputLayout tilResetEmail = dialogView.findViewById(R.id.tilResetEmail);
        TextInputEditText etResetEmail = dialogView.findViewById(R.id.etResetEmail);

        String currentEmail = editTextEmail.getText() != null
                ? editTextEmail.getText().toString().trim() : "";
        if (!TextUtils.isEmpty(currentEmail)) {
            etResetEmail.setText(currentEmail);
            etResetEmail.setSelection(currentEmail.length());
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Send Link", null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String email = etResetEmail.getText() != null
                                ? etResetEmail.getText().toString().trim() : "";

                        if (TextUtils.isEmpty(email)
                                || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                            tilResetEmail.setError("Please enter a valid email");
                            etResetEmail.requestFocus();
                            return;
                        }

                        tilResetEmail.setError(null);
                        dialog.dismiss();
                        sendPasswordResetEmail(email);
                    });
        });

        dialog.show();
    }

    private void sendPasswordResetEmail(String email) {
        if (!isNetworkAvailable()) {
            showError("No internet connection. Please check your network and try again.");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        buttonLogin.setEnabled(false);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    buttonLogin.setEnabled(true);

                    LocaleHelper.showTranslatedToast(LoginPage.this,
                            "If that email is registered, a reset link has been sent. Check your inbox.",
                            Toast.LENGTH_LONG);
                });
    }
}
