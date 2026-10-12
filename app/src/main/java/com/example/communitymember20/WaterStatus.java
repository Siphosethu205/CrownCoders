package com.example.communitymember20;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WaterStatus extends BaseActivity {

    private static final String TAG = "WaterStatus";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1002;
    private static final long DUPLICATE_WINDOW_MS = 5 * 60 * 1000;
    private static final long REPORT_EXPIRY_MS = 24 * 60 * 60 * 1000;

    private MaterialToolbar topAppBar;
    private TextInputEditText etZoneName;
    private TextInputLayout tilZoneName;
    private MaterialButton btnStatusRed, btnStatusYellow, btnStatusGreen, btnSubmitReport;
    private TextView tvStatusError, tvStatusHint;
    private ProgressBar progressBar;
    private String selectedStatus = "";

    private String errorLocationRequired = "Please enter or detect your location";
    private String errorStatusRequired = "Please select a water status level.";

    private DatabaseReference databaseReference;
    private DatabaseReference notificationsRef;
    private FirebaseAuth mAuth;
    private FusedLocationProviderClient fusedLocationClient;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.water_status);

        databaseReference = FirebaseDatabase.getInstance().getReference("water_statuses");
        notificationsRef = FirebaseDatabase.getInstance().getReference("notifications");
        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        prefs = getSharedPreferences("AquacommPrefs", MODE_PRIVATE);

        topAppBar = findViewById(R.id.topAppBar);
        etZoneName = findViewById(R.id.etZoneName);
        tilZoneName = findViewById(R.id.tilZoneName);
        btnStatusRed = findViewById(R.id.btnStatusRed);
        btnStatusYellow = findViewById(R.id.btnStatusYellow);
        btnStatusGreen = findViewById(R.id.btnStatusGreen);
        btnSubmitReport = findViewById(R.id.btnSubmitReport);
        tvStatusError = findViewById(R.id.tvStatusError);
        tvStatusHint = findViewById(R.id.tvStatusHint);
        progressBar = findViewById(R.id.progressBar);

        topAppBar.setNavigationOnClickListener(v -> finish());

        LocaleHelper.translateStringApi(this, "Please enter or detect your location",
                translated -> errorLocationRequired = translated);
        LocaleHelper.translateStringApi(this, "Please select a water status level.",
                translated -> errorStatusRequired = translated);

        tilZoneName.setEndIconOnClickListener(v -> checkLocationPermissionAndFetch());

        btnStatusRed.setOnClickListener(v -> {
            selectedStatus = "No water";
            updateStatusSelectionUI(btnStatusRed);
        });
        btnStatusYellow.setOnClickListener(v -> {
            selectedStatus = "Low";
            updateStatusSelectionUI(btnStatusYellow);
        });
        btnStatusGreen.setOnClickListener(v -> {
            selectedStatus = "Available";
            updateStatusSelectionUI(btnStatusGreen);
        });

        btnSubmitReport.setOnClickListener(v -> {
            if (validateInputs()) {
                checkDuplicateAndConfirm();
            }
        });
    }

    private boolean validateInputs() {
        boolean valid = true;

        String location = etZoneName.getText() != null
                ? etZoneName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(location)) {
            tilZoneName.setError(errorLocationRequired);
            valid = false;
        } else {
            tilZoneName.setError(null);
        }

        if (TextUtils.isEmpty(selectedStatus)) {
            tvStatusError.setVisibility(View.VISIBLE);
            tvStatusError.setText(errorStatusRequired);
            valid = false;
        } else {
            tvStatusError.setVisibility(View.GONE);
        }

        if (!valid) {
            LocaleHelper.showTranslatedToast(this,
                    "Please fill in all required fields", Toast.LENGTH_SHORT);
        }

        return valid;
    }

    private void checkDuplicateAndConfirm() {
        long lastSubmit = prefs.getLong("last_water_status_submit", 0);
        long now = System.currentTimeMillis();

        if (now - lastSubmit < DUPLICATE_WINDOW_MS) {
            long secondsAgo = (now - lastSubmit) / 1000;
            String timeAgo = secondsAgo < 60
                    ? secondsAgo + " seconds"
                    : (secondsAgo / 60) + " minute(s)";

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Recent Report Detected")
                    .setMessage("You already submitted a report " + timeAgo +
                            " ago.\n\nSubmit another?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Submit Anyway", (d, w) -> showConfirmationDialog())
                    .show();
        } else {
            showConfirmationDialog();
        }
    }

    private void showConfirmationDialog() {
        String location = etZoneName.getText() != null
                ? etZoneName.getText().toString().trim() : "";

        String titleEn = "Submit Water Status Report?";
        String messageEn = "You are about to report:\n\n" +
                "• Status: " + selectedStatus + "\n" +
                "• Location: " + location + "\n\n" +
                "This will be visible to your community. Continue?";
        String cancelEn = "Cancel";
        String submitEn = "Submit";

        LocaleHelper.translateStringApi(this, titleEn, tTitle ->
                LocaleHelper.translateStringApi(this, messageEn, tMessage ->
                        LocaleHelper.translateStringApi(this, cancelEn, tCancel ->
                                LocaleHelper.translateStringApi(this, submitEn, tSubmit ->
                                        runOnUiThread(() -> new MaterialAlertDialogBuilder(WaterStatus.this)
                                                .setTitle(tTitle)
                                                .setMessage(tMessage)
                                                .setNegativeButton(tCancel, (dialog, which) -> dialog.dismiss())
                                                .setPositiveButton(tSubmit, (dialog, which) -> {
                                                    dialog.dismiss();
                                                    fetchUserNameAndSubmit(location, selectedStatus);
                                                })
                                                .show())
                                )
                        )
                )
        );
    }

    private void checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            fetchCurrentLocation();
        }
    }

    private void fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocaleHelper.showTranslatedToast(this, "Fetching live GPS location...", Toast.LENGTH_SHORT);

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        double latitude = location.getLatitude();
                        double longitude = location.getLongitude();

                        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                        try {
                            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                            if (addresses != null && !addresses.isEmpty()) {
                                etZoneName.setText(addresses.get(0).getAddressLine(0));
                                tilZoneName.setError(null);
                                LocaleHelper.showTranslatedToast(this,
                                        "Location acquired successfully!", Toast.LENGTH_SHORT);
                            } else {
                                etZoneName.setText(latitude + ", " + longitude);
                            }
                        } catch (IOException e) {
                            etZoneName.setText(latitude + ", " + longitude);
                        }
                    } else {
                        tryLastKnownLocation();
                    }
                })
                .addOnFailureListener(e -> tryLastKnownLocation());
    }

    private void tryLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                try {
                    List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        etZoneName.setText(addresses.get(0).getAddressLine(0));
                        tilZoneName.setError(null);
                    } else {
                        etZoneName.setText(latitude + ", " + longitude);
                    }
                } catch (IOException e) {
                    etZoneName.setText(latitude + ", " + longitude);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchCurrentLocation();
            } else {
                LocaleHelper.showTranslatedToast(this,
                        "Location permission is required to detect GPS.",
                        Toast.LENGTH_LONG);
            }
        }
    }

    private void updateStatusSelectionUI(MaterialButton selectedButton) {
        btnStatusRed.setSelected(false);
        btnStatusYellow.setSelected(false);
        btnStatusGreen.setSelected(false);
        selectedButton.setSelected(true);

        tvStatusError.setVisibility(View.GONE);
        tvStatusHint.setText("Selected: " + selectedStatus);
        tvStatusHint.setTextColor(0xFF00A3B8);
    }

    private void fetchUserNameAndSubmit(String location, String status) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String userId = currentUser != null ? currentUser.getUid() : "Anonymous";

        progressBar.setVisibility(View.VISIBLE);
        btnSubmitReport.setEnabled(false);
        btnSubmitReport.setText("Submitting…");

        if (currentUser == null) {
            pushReport("Anonymous", "Community Member", location, status);
            return;
        }

        String userNode = prefs.getString("user_node", UserNodeHelper.NODE_MEMBERS);
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users").child(userNode).child(userId).child("full_name");

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = "Community Member";
                if (snapshot.exists()) {
                    String fullName = snapshot.getValue(String.class);
                    if (fullName != null && !fullName.isEmpty()) name = fullName;
                }
                pushReport(userId, name, location, status);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                pushReport(userId, "Community Member", location, status);
            }
        });
    }

    private void pushReport(String userId, String name, String location, String status) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String contact = currentUser != null && currentUser.getEmail() != null
                ? currentUser.getEmail() : "Not Provided";

        Date now = new Date();
        String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(now);
        long timestampMillis = System.currentTimeMillis();
        long expiresAt = timestampMillis + REPORT_EXPIRY_MS;

        String refDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now);
        String referenceNumber = "WR-" + refDate + "-" + (1000 + (int) (Math.random() * 9000));

        Map<String, Object> reportMap = new HashMap<>();
        reportMap.put("userId", userId);
        reportMap.put("name", name);
        reportMap.put("contact", contact);
        reportMap.put("location", location);
        reportMap.put("waterStatus", status);
        // ✅ DEFAULT STATUS IS "Submitted" — admin must change it
        reportMap.put("status", "Submitted");
        reportMap.put("reference", referenceNumber);
        reportMap.put("date", currentDate);
        reportMap.put("timestamp", timestampMillis);
        reportMap.put("expiresAt", expiresAt);

        String reportId = databaseReference.push().getKey();
        if (reportId == null) {
            onSubmissionFinished();
            return;
        }

        Log.d(TAG, "Submitting report with status=Submitted, ref=" + referenceNumber);

        databaseReference.child(reportId).setValue(reportMap)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✓ Report saved to DB");

                    prefs.edit()
                            .putLong("last_water_status_submit", System.currentTimeMillis())
                            .apply();

                    // ✅ SEND A RICH PERSONAL NOTIFICATION WITH ALL DETAILS
                    sendSubmissionNotification(userId, name, referenceNumber, location, status);

                    onSubmissionFinished();
                    showSuccessDialog(name, location, status, referenceNumber, currentDate);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "✗ Report save failed: " + e.getMessage());
                    onSubmissionFinished();
                    LocaleHelper.showTranslatedToast(WaterStatus.this,
                            "Failed to submit report. Please try again.",
                            Toast.LENGTH_LONG);
                });
    }

    // ============================================================
    // SEND PERSONAL NOTIFICATION WITH FULL DETAILS
    // ============================================================
    private void sendSubmissionNotification(String targetUid, String targetName,
                                            String reference, String location,
                                            String waterStatus) {
        if (targetUid == null || targetUid.isEmpty() || "Anonymous".equals(targetUid)) {
            Log.w(TAG, "Skipping notification — no valid user UID");
            return;
        }

        String notifId = notificationsRef.push().getKey();
        if (notifId == null) {
            Log.e(TAG, "Failed to generate notification ID");
            return;
        }

        // Build a rich, informative message
        String message = "✅ Your water status report was submitted successfully!\n\n" +
                "Reference: " + reference + "\n" +
                "Status reported: " + waterStatus + "\n" +
                "Location: " + location + "\n\n" +
                "An admin will review it shortly. You'll be notified when the status changes.";

        Map<String, Object> notif = new HashMap<>();
        notif.put("id", notifId);
        notif.put("message", message);
        notif.put("targetAudience", "Specific User");
        notif.put("targetUserId", targetUid);
        notif.put("targetUserName", targetName);
        notif.put("timestamp", System.currentTimeMillis());
        // Optional metadata
        notif.put("relatedReportId", reference);
        notif.put("relatedLocation", location);

        notificationsRef.child(notifId).setValue(notif)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "✓ Notification sent to user " + targetUid))
                .addOnFailureListener(e ->
                        Log.e(TAG, "✗ Notification failed: " + e.getMessage()));
    }

    private void onSubmissionFinished() {
        progressBar.setVisibility(View.GONE);
        btnSubmitReport.setEnabled(true);
        btnSubmitReport.setText("Submit Report");
    }

    private void showSuccessDialog(String name, String location, String status,
                                   String reference, String date) {
        View dialogView = LayoutInflater.from(this).inflate(
                R.layout.dialog_report_success, null);

        TextView tvRef = dialogView.findViewById(R.id.tvSuccessRef);
        TextView tvStatus = dialogView.findViewById(R.id.tvSuccessStatus);
        TextView tvLocation = dialogView.findViewById(R.id.tvSuccessLocation);
        TextView tvDate = dialogView.findViewById(R.id.tvSuccessDate);
        ImageView ivIcon = dialogView.findViewById(R.id.ivSuccessIcon);

        if (tvRef != null) tvRef.setText(reference);
        if (tvStatus != null) tvStatus.setText(status);
        if (tvLocation != null) tvLocation.setText(location);
        if (tvDate != null) tvDate.setText(date);

        if (ivIcon != null) {
            int color;
            if ("No water".equalsIgnoreCase(status)) color = 0xFFDC2626;
            else if ("Low".equalsIgnoreCase(status)) color = 0xFFF57C00;
            else color = 0xFF00A3B8;
            ivIcon.setColorFilter(color);
        }

        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("Done", (d, w) -> {
                    d.dismiss();
                    finish();
                })
                .setCancelable(false)
                .show();
    }
}