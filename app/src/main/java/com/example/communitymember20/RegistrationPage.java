package com.example.communitymember20;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

public class RegistrationPage extends BaseActivity {

    private static final String TAG = "RegistrationPage";

    private EditText editTextFullName, editTextEmail, editTextPhone,
            editTextPassword, editTextConfirmPassword;
    private Button buttonRegister;
    private TextView textViewLogin, tvRegisterError, tvPasswordStrength, tvTermsLink;
    private LinearLayout btnTopLogin;
    private ImageView ivTogglePassword, ivToggleConfirmPassword;
    private CheckBox cbTerms;
    private ProgressBar progressBar;
    private MaterialCardView cardErrorBanner;
    private View strengthBar1, strengthBar2, strengthBar3, strengthBar4;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private SharedPreferences sharedPreferences;

    private boolean passwordVisible = false;
    private boolean confirmPasswordVisible = false;

    private static final String[] DISPOSABLE_DOMAINS = {
            "tempmail.com", "10minutemail.com", "guerrillamail.com",
            "mailinator.com", "throwawaymail.com", "yopmail.com",
            "trashmail.com", "fakeinbox.com", "sharklasers.com"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registration_page);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        sharedPreferences = getSharedPreferences("AquacommPrefs", Context.MODE_PRIVATE);

        editTextFullName = findViewById(R.id.editTextFullName);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPhone = findViewById(R.id.editTextPhone);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword);
        buttonRegister = findViewById(R.id.buttonRegister);
        textViewLogin = findViewById(R.id.textViewLogin);
        btnTopLogin = findViewById(R.id.btnTopLogin);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        ivToggleConfirmPassword = findViewById(R.id.ivToggleConfirmPassword);
        cbTerms = findViewById(R.id.cbTerms);
        tvTermsLink = findViewById(R.id.tvTermsLink);
        tvPasswordStrength = findViewById(R.id.tvPasswordStrength);
        strengthBar1 = findViewById(R.id.strengthBar1);
        strengthBar2 = findViewById(R.id.strengthBar2);
        strengthBar3 = findViewById(R.id.strengthBar3);
        strengthBar4 = findViewById(R.id.strengthBar4);
        progressBar = findViewById(R.id.progressBar);
        cardErrorBanner = findViewById(R.id.cardErrorBanner);
        tvRegisterError = findViewById(R.id.tvRegisterError);

        buttonRegister.setOnClickListener(v -> registerUser());

        // Password visibility toggles
        ivTogglePassword.setOnClickListener(v -> togglePassword(editTextPassword, true));
        ivToggleConfirmPassword.setOnClickListener(v ->
                togglePassword(editTextConfirmPassword, false));

        // Terms link
        tvTermsLink.setOnClickListener(v -> showTermsDialog());

        // Live password strength meter
        editTextPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                updatePasswordStrength(s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        // Real-time confirm-password match check
        editTextConfirmPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                String pw = editTextPassword.getText().toString();
                String confirm = s.toString();
                if (!confirm.isEmpty() && !confirm.equals(pw)) {
                    editTextConfirmPassword.setError("Passwords do not match");
                } else {
                    editTextConfirmPassword.setError(null);
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        View.OnClickListener goToLogin = v -> {
            startActivity(new Intent(RegistrationPage.this, LoginPage.class));
            finish();
        };
        textViewLogin.setOnClickListener(goToLogin);
        btnTopLogin.setOnClickListener(goToLogin);
    }

    // ============================================================
    // PASSWORD VISIBILITY
    // ============================================================
    private void togglePassword(EditText field, boolean isPrimary) {
        boolean currentlyVisible = isPrimary ? passwordVisible : confirmPasswordVisible;

        if (currentlyVisible) {
            field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        } else {
            field.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
        }
        field.setSelection(field.getText().length());

        if (isPrimary) passwordVisible = !passwordVisible;
        else confirmPasswordVisible = !confirmPasswordVisible;
    }

    // ============================================================
    // PASSWORD STRENGTH METER
    // ============================================================
    private void updatePasswordStrength(String pw) {
        int score = 0;
        if (pw.length() >= 8) score++;
        if (pw.matches(".*[A-Z].*")) score++;
        if (pw.matches(".*[a-z].*") && pw.matches(".*[0-9].*")) score++;
        if (pw.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) score++;

        // Reset all bars to default
        int defaultColor = 0xFFE6F8FA;
        strengthBar1.setBackgroundColor(defaultColor);
        strengthBar2.setBackgroundColor(defaultColor);
        strengthBar3.setBackgroundColor(defaultColor);
        strengthBar4.setBackgroundColor(defaultColor);

        if (pw.isEmpty()) {
            tvPasswordStrength.setText("");
            return;
        }

        int red = 0xFFDC2626;
        int orange = 0xFFF57C00;
        int yellow = 0xFFFBC02D;
        int green = 0xFF00A3B8;

        String label;
        int color;

        switch (score) {
            case 1:
                strengthBar1.setBackgroundColor(red);
                label = "Weak";
                color = red;
                break;
            case 2:
                strengthBar1.setBackgroundColor(orange);
                strengthBar2.setBackgroundColor(orange);
                label = "Fair";
                color = orange;
                break;
            case 3:
                strengthBar1.setBackgroundColor(yellow);
                strengthBar2.setBackgroundColor(yellow);
                strengthBar3.setBackgroundColor(yellow);
                label = "Good";
                color = yellow;
                break;
            case 4:
                strengthBar1.setBackgroundColor(green);
                strengthBar2.setBackgroundColor(green);
                strengthBar3.setBackgroundColor(green);
                strengthBar4.setBackgroundColor(green);
                label = "Strong";
                color = green;
                break;
            default:
                strengthBar1.setBackgroundColor(red);
                label = "Too short";
                color = red;
                break;
        }

        tvPasswordStrength.setText(label);
        tvPasswordStrength.setTextColor(color);
    }

    // ============================================================
    // VALIDATION
    // ============================================================
    private boolean isValidEmail(String email) {
        String regex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (!email.matches(regex)) return false;
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
        for (String blocked : DISPOSABLE_DOMAINS) {
            if (domain.equals(blocked) || domain.endsWith("." + blocked)) return false;
        }
        return true;
    }

    private boolean isSAPhone(String phone) {
        return phone.matches("^0[6-8][0-9]{8}$");
    }

    private boolean isStrongPassword(String pw) {
        if (pw.length() < 8) return false;
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : pw.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void showError(String message) {
        if (cardErrorBanner == null || tvRegisterError == null) return;
        tvRegisterError.setText(message);
        cardErrorBanner.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        if (cardErrorBanner != null) cardErrorBanner.setVisibility(View.GONE);
    }

    // ============================================================
    // TERMS OF SERVICE DIALOG
    // ============================================================
    private void showTermsDialog() {
        String terms =
                "AquaComm Terms of Service\n\n" +
                        "1. You agree to provide accurate information.\n\n" +
                        "2. Reports you submit are visible to your community.\n\n" +
                        "3. False emergency reports may result in account suspension.\n\n" +
                        "4. We collect your name, email, and phone number to provide " +
                        "our service. Your data is not shared with third parties.\n\n" +
                        "5. You may delete your account at any time.";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Terms & Privacy")
                .setMessage(terms)
                .setPositiveButton("OK", null)
                .show();
    }

    // ============================================================
    // REGISTER
    // ============================================================
    private void registerUser() {
        hideError();

        String fullName = editTextFullName.getText().toString().trim();
        String email = editTextEmail.getText().toString().trim();
        String phone = editTextPhone.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        String confirmPassword = editTextConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(fullName)) {
            editTextFullName.setError("Full Name is required");
            editTextFullName.requestFocus();
            return;
        }
        if (!isValidEmail(email)) {
            editTextEmail.setError("Enter a valid email");
            editTextEmail.requestFocus();
            return;
        }
        if (!isSAPhone(phone)) {
            editTextPhone.setError("Enter a valid SA mobile (e.g. 0712345678)");
            editTextPhone.requestFocus();
            return;
        }
        if (!isStrongPassword(password)) {
            editTextPassword.setError("Password needs 8+ chars, mixed case, number, symbol");
            editTextPassword.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            editTextConfirmPassword.setError("Passwords do not match");
            editTextConfirmPassword.requestFocus();
            return;
        }
        if (!cbTerms.isChecked()) {
            showError("Please accept the Terms of Service and Privacy Policy to continue.");
            return;
        }
        if (!isNetworkAvailable()) {
            showError("No internet connection. Please try again.");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        buttonRegister.setEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        progressBar.setVisibility(View.GONE);
                        buttonRegister.setEnabled(true);
                        Exception e = task.getException();
                        if (e instanceof FirebaseAuthUserCollisionException) {
                            showError("This email is already registered. Try logging in.");
                        } else if (e instanceof FirebaseAuthWeakPasswordException) {
                            showError("Your password is too weak.");
                        } else {
                            showError("Registration failed: " +
                                    (e != null ? e.getMessage() : "unknown"));
                        }
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        progressBar.setVisibility(View.GONE);
                        buttonRegister.setEnabled(true);
                        showError("Registration failed.");
                        return;
                    }

                    String userId = user.getUid();
                    String selectedLang = sharedPreferences.getString("preferred_lang", "English");

                    HashMap<String, Object> userMap = new HashMap<>();
                    userMap.put("full_name", fullName);
                    userMap.put("email", email);
                    userMap.put("phone_number", phone);
                    userMap.put("role", "Community Member");
                    userMap.put("preferred_lang", selectedLang);
                    userMap.put("email_verified", false);
                    userMap.put("profile_complete", true);
                    userMap.put("terms_accepted", true);
                    userMap.put("createdAt", System.currentTimeMillis());

                    mDatabase.child("users")
                            .child(UserNodeHelper.NODE_MEMBERS)
                            .child(userId)
                            .setValue(userMap)
                            .addOnCompleteListener(dbTask -> {
                                if (!dbTask.isSuccessful()) {
                                    user.delete();
                                    progressBar.setVisibility(View.GONE);
                                    buttonRegister.setEnabled(true);
                                    showError("Could not save profile. Please try again.");
                                    return;
                                }

                                sharedPreferences.edit()
                                        .putString("user_node", UserNodeHelper.NODE_MEMBERS)
                                        .putString("user_role", "Community Member")
                                        .putString("pending_verification_email", email)
                                        .apply();

                                sendEmailVerification(user, email);
                            });
                });
    }

    // ============================================================
    // SEND EMAIL VERIFICATION → NAVIGATE
    // ============================================================
    private void sendEmailVerification(FirebaseUser user, String email) {
        user.sendEmailVerification()
                .addOnCompleteListener(emailTask -> {
                    progressBar.setVisibility(View.GONE);
                    buttonRegister.setEnabled(true);

                    if (emailTask.isSuccessful()) {
                        Log.d(TAG, "✓ Verification email sent to " + email);
                    } else {
                        String err = emailTask.getException() != null
                                ? emailTask.getException().getMessage() : "unknown";
                        Log.e(TAG, "✗ Email verification failed: " + err);
                    }

                    Intent intent = new Intent(RegistrationPage.this,
                            EmailVerificationActivity.class);
                    intent.putExtra("email", email);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }
}