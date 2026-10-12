package com.example.communitymember20;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class EmailVerificationActivity extends BaseActivity {

    private static final String TAG = "EmailVerification";
    private static final long POLL_INTERVAL_MS = 3000; // 3 seconds
    private static final long MAX_POLL_DURATION_MS = 10 * 60 * 1000; // 10 minutes

    private TextView tvVerifyEmail, tvVerifyStatus, tvVerifyError;
    private MaterialCardView cardVerifyError;
    private MaterialButton btnResendEmail;
    private ProgressBar progressVerify;

    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;
    private SharedPreferences prefs;

    private Handler pollHandler;
    private Runnable pollRunnable;
    private long pollStartTime = 0;
    private boolean hasAdvanced = false;
    private long lastResendTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.email_verification);

        mAuth = FirebaseAuth.getInstance();
        dbRef = FirebaseDatabase.getInstance().getReference();
        prefs = getSharedPreferences("AquacommPrefs", Context.MODE_PRIVATE);

        tvVerifyEmail = findViewById(R.id.tvVerifyEmail);
        tvVerifyStatus = findViewById(R.id.tvVerifyStatus);
        tvVerifyError = findViewById(R.id.tvVerifyError);
        cardVerifyError = findViewById(R.id.cardVerifyError);
        btnResendEmail = findViewById(R.id.btnResendEmail);
        progressVerify = findViewById(R.id.progressVerify);

        // Resolve the email from intent, current user, or cache
        String email = getIntent().getStringExtra("email");
        if (email == null && mAuth.getCurrentUser() != null) {
            email = mAuth.getCurrentUser().getEmail();
        }
        if (email == null) {
            email = prefs.getString("pending_verification_email", "");
        }
        if (email != null && !email.isEmpty()) {
            tvVerifyEmail.setText(email);
        }

        btnResendEmail.setOnClickListener(v -> resendVerification());

        // Prevent back navigation — user must complete or go to Login
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                stopPolling();
                Intent intent = new Intent(EmailVerificationActivity.this, LoginPage.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });

        // Start polling immediately
        startPolling();
    }

    // ============================================================
    // AUTO-DETECT: Poll Firebase every 3 seconds
    // ============================================================
    private void startPolling() {
        pollHandler = new Handler(Looper.getMainLooper());
        pollStartTime = System.currentTimeMillis();

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (hasAdvanced) return;

                // Stop after 10 minutes
                if (System.currentTimeMillis() - pollStartTime > MAX_POLL_DURATION_MS) {
                    Log.d(TAG, "Polling timeout reached");
                    if (tvVerifyStatus != null) {
                        tvVerifyStatus.setText("Still waiting? Tap Resend to send a new link.");
                    }
                    return;
                }

                FirebaseUser user = mAuth.getCurrentUser();
                if (user == null) {
                    pollHandler.postDelayed(this, POLL_INTERVAL_MS);
                    return;
                }

                user.reload().addOnCompleteListener(task -> {
                    if (hasAdvanced) return;

                    FirebaseUser refreshed = mAuth.getCurrentUser();
                    if (refreshed != null && refreshed.isEmailVerified()) {
                        Log.d(TAG, "✓ Email verified detected by poll");
                        markVerifiedAndContinue(refreshed);
                        return;
                    }

                    // Still not verified — schedule next check
                    pollHandler.postDelayed(this, POLL_INTERVAL_MS);
                });
            }
        };

        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    private void stopPolling() {
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }

    // ============================================================
    // Auto-check also fires when user returns to the app
    // ============================================================
    @Override
    protected void onResume() {
        super.onResume();
        if (hasAdvanced) return;

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        user.reload().addOnCompleteListener(task -> {
            if (hasAdvanced) return;
            FirebaseUser refreshed = mAuth.getCurrentUser();
            if (refreshed != null && refreshed.isEmailVerified()) {
                Log.d(TAG, "✓ Email verified detected on resume");
                markVerifiedAndContinue(refreshed);
            }
        });
    }

    // ============================================================
    // Mark verified in DB and route to LoginPage
    // ============================================================
    private void markVerifiedAndContinue(FirebaseUser user) {
        if (hasAdvanced) return;
        hasAdvanced = true;
        stopPolling();

        String userNode = prefs.getString("user_node", UserNodeHelper.NODE_MEMBERS);
        Log.d(TAG, "Marking email verified for uid=" + user.getUid() + " node=" + userNode);

        dbRef.child("users")
                .child(userNode)
                .child(user.getUid())
                .child("email_verified")
                .setValue(true)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "✓ email_verified set to true in DB");

                        LocaleHelper.showTranslatedToast(this,
                                "Email verified! You can now log in.",
                                Toast.LENGTH_LONG);

                        Intent intent = new Intent(this, LoginPage.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        String err = task.getException() != null
                                ? task.getException().getMessage() : "unknown";
                        Log.e(TAG, "✗ DB update failed: " + err);
                        hasAdvanced = false;
                        showError("Could not save verification: " + err);
                        startPolling();
                    }
                });
    }

    // ============================================================
    // Resend the verification link
    // ============================================================
    private void resendVerification() {
        hideError();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showError("You're not signed in. Please register again.");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastResendTime < 60_000) {
            long secondsLeft = (60_000 - (now - lastResendTime)) / 1000;
            LocaleHelper.showTranslatedToast(this,
                    "Please wait " + secondsLeft + "s before resending.",
                    Toast.LENGTH_SHORT);
            return;
        }

        progressVerify.setVisibility(View.VISIBLE);
        btnResendEmail.setEnabled(false);

        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    progressVerify.setVisibility(View.GONE);
                    btnResendEmail.setEnabled(true);

                    if (task.isSuccessful()) {
                        lastResendTime = System.currentTimeMillis();
                        LocaleHelper.showTranslatedToast(this,
                                "Verification email sent again. Check your inbox.",
                                Toast.LENGTH_LONG);

                        // Reset the poll timer
                        stopPolling();
                        hasAdvanced = false;
                        startPolling();
                    } else {
                        showError("Couldn't resend: " +
                                (task.getException() != null
                                        ? task.getException().getMessage()
                                        : "unknown"));
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }

    private void showError(String message) {
        if (cardVerifyError == null || tvVerifyError == null) return;
        tvVerifyError.setText(message);
        cardVerifyError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        if (cardVerifyError != null) cardVerifyError.setVisibility(View.GONE);
    }
}