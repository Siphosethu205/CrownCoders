package com.example.communitymember20;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
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

public class EmergencyWaterRequest extends BaseActivity {

    private static final String TAG = "EmergencyRequest";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private MaterialToolbar topAppBar;
    private TextInputEditText etZoneName;
    private TextInputLayout tilZoneName;
    private MaterialButton btnUrgencyLow, btnUrgencyMedium, btnUrgencyHigh, btnSubmitEmergency;
    private TextView tvUrgencyError, tvUrgencyHint;
    private ProgressBar progressBar;
    private String selectedUrgency = "";

    private Double detectedLat = null;
    private Double detectedLng = null;

    private String errorLocationRequired = "Please enter or detect your location";
    private String errorUrgencyRequired = "Please select an urgency level.";

    private DatabaseReference databaseReference;
    private DatabaseReference notificationsRef;
    private FirebaseAuth mAuth;
    private FusedLocationProviderClient fusedLocationClient;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.emergency_water_request);

        databaseReference = FirebaseDatabase.getInstance().getReference("emergency_requests");
        notificationsRef = FirebaseDatabase.getInstance().getReference("notifications");
        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        prefs = getSharedPreferences("AquacommPrefs", MODE_PRIVATE);

        topAppBar = findViewById(R.id.topAppBar);
        etZoneName = findViewById(R.id.etZoneName);
        tilZoneName = findViewById(R.id.tilZoneName);
        btnUrgencyLow = findViewById(R.id.btnUrgencyLow);
        btnUrgencyMedium = findViewById(R.id.btnUrgencyMedium);
        btnUrgencyHigh = findViewById(R.id.btnUrgencyHigh);
        btnSubmitEmergency = findViewById(R.id.btnSubmitEmergency);
        tvUrgencyError = findViewById(R.id.tvUrgencyError);
        tvUrgencyHint = findViewById(R.id.tvUrgencyHint);
        progressBar = findViewById(R.id.progressBar);

        topAppBar.setNavigationOnClickListener(v -> finish());

        LocaleHelper.translateStringApi(this, "Please enter or detect your location", translated -> errorLocationRequired = translated);
        LocaleHelper.translateStringApi(this, "Please select an urgency level.", translated -> errorUrgencyRequired = translated);

        tilZoneName.setEndIconOnClickListener(v -> checkLocationPermissionAndFetch());

        btnUrgencyLow.setOnClickListener(v -> {
            selectedUrgency = "Standard";
            updateUrgencySelectionUI(btnUrgencyLow);
        });
        btnUrgencyMedium.setOnClickListener(v -> {
            selectedUrgency = "Critical";
            updateUrgencySelectionUI(btnUrgencyMedium);
        });
        btnUrgencyHigh.setOnClickListener(v -> {
            selectedUrgency = "Extreme";
            updateUrgencySelectionUI(btnUrgencyHigh);
        });

        btnSubmitEmergency.setOnClickListener(v -> {
            if (validateInputs()) showConfirmationDialog();
        });
    }

    private boolean validateInputs() {
        boolean valid = true;
        String location = etZoneName.getText() != null ? etZoneName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(location)) {
            tilZoneName.setError(errorLocationRequired);
            valid = false;
        } else {
            tilZoneName.setError(null);
        }
        if (TextUtils.isEmpty(selectedUrgency)) {
            tvUrgencyError.setVisibility(View.VISIBLE);
            tvUrgencyError.setText(errorUrgencyRequired);
            valid = false;
        } else {
            tvUrgencyError.setVisibility(View.GONE);
        }
        if (!valid) LocaleHelper.showTranslatedToast(this, "Please fill in all required fields", Toast.LENGTH_SHORT);
        return valid;
    }

    private void showConfirmationDialog() {
        String location = etZoneName.getText() != null ? etZoneName.getText().toString().trim() : "";
        final String titleEn;
        final String messageEn;

        if ("Extreme".equals(selectedUrgency)) {
            titleEn = "Submit EXTREME Emergency Request?";
            messageEn = "You are about to send an EXTREME priority request.\n\n• Urgency: " + selectedUrgency + "\n• Location: " + location + "\n\nThis will alert local authorities immediately.";
        } else {
            titleEn = "Submit Emergency Request?";
            messageEn = "You are about to submit:\n\n• Urgency: " + selectedUrgency + "\n• Location: " + location + "\n\nA dispatcher will be notified. Continue?";
        }
        final String cancelEn = "Cancel";
        final String submitEn = "Submit";

        LocaleHelper.translateStringApi(this, titleEn, tTitle ->
                LocaleHelper.translateStringApi(this, messageEn, tMessage ->
                        LocaleHelper.translateStringApi(this, cancelEn, tCancel ->
                                LocaleHelper.translateStringApi(this, submitEn, tSubmit ->
                                        runOnUiThread(() -> new MaterialAlertDialogBuilder(EmergencyWaterRequest.this)
                                                .setTitle(tTitle)
                                                .setMessage(tMessage)
                                                .setNegativeButton(tCancel, (dialog, which) -> dialog.dismiss())
                                                .setPositiveButton(tSubmit, (dialog, which) -> {
                                                    dialog.dismiss();
                                                    fetchUserNameAndSubmit(location, selectedUrgency);
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
                    if (location != null) handleLocationResult(location);
                    else tryLastKnownLocation();
                })
                .addOnFailureListener(e -> tryLastKnownLocation());
    }

    private void tryLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) handleLocationResult(location);
            else LocaleHelper.showTranslatedToast(this,
                    "Couldn't get GPS. Please type your location manually.", Toast.LENGTH_LONG);
        });
    }

    private void handleLocationResult(Location location) {
        detectedLat = location.getLatitude();
        detectedLng = location.getLongitude();

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(detectedLat, detectedLng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                etZoneName.setText(addresses.get(0).getAddressLine(0));
                tilZoneName.setError(null);
                LocaleHelper.showTranslatedToast(this, "Location acquired successfully!", Toast.LENGTH_SHORT);
            } else {
                etZoneName.setText(String.format(Locale.getDefault(), "%.5f, %.5f", detectedLat, detectedLng));
            }
        } catch (IOException e) {
            etZoneName.setText(String.format(Locale.getDefault(), "%.5f, %.5f", detectedLat, detectedLng));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) fetchCurrentLocation();
            else LocaleHelper.showTranslatedToast(this, "Location permission is required.", Toast.LENGTH_LONG);
        }
    }

    private void updateUrgencySelectionUI(MaterialButton selectedButton) {
        btnUrgencyLow.setStrokeWidth(2);
        btnUrgencyMedium.setStrokeWidth(2);
        btnUrgencyHigh.setStrokeWidth(2);
        selectedButton.setStrokeWidth(6);
        tvUrgencyError.setVisibility(View.GONE);
        tvUrgencyHint.setText("Selected: " + selectedUrgency);
    }

    private void fetchUserNameAndSubmit(String location, String urgency) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String userId = currentUser != null ? currentUser.getUid() : "Anonymous";

        progressBar.setVisibility(View.VISIBLE);
        btnSubmitEmergency.setEnabled(false);
        btnSubmitEmergency.setText("Submitting…");

        if (currentUser == null) {
            pushEmergency("Anonymous", "Community Member", location, urgency);
            return;
        }

        String userNode = prefs.getString("user_node", UserNodeHelper.NODE_MEMBERS);
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users").child(userNode).child(userId).child("full_name");

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = null;
                if (snapshot.exists()) name = snapshot.getValue(String.class);
                if (name == null || name.isEmpty()) tryFallbackName(userId, location, urgency);
                else pushEmergency(userId, name, location, urgency);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tryFallbackName(userId, location, urgency);
            }
        });
    }

    private void tryFallbackName(String userId, String location, String urgency) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String foundName = null;
                String[] nodes = {UserNodeHelper.NODE_MEMBERS, UserNodeHelper.NODE_DRIVERS, UserNodeHelper.NODE_ADMINS};
                for (String node : nodes) {
                    DataSnapshot userSnap = snapshot.child(node).child(userId);
                    if (userSnap.exists()) {
                        String n = userSnap.child("full_name").getValue(String.class);
                        if (n != null && !n.isEmpty()) { foundName = n; break; }
                    }
                }
                if (foundName == null) foundName = "Community Member";
                pushEmergency(userId, foundName, location, urgency);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                pushEmergency(userId, "Community Member", location, urgency);
            }
        });
    }

    private void pushEmergency(String userId, String name, String location, String urgency) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String contact = currentUser != null && currentUser.getEmail() != null ? currentUser.getEmail() : "Not Provided";

        Date now = new Date();
        String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(now);
        long timestampMillis = System.currentTimeMillis();
        String refDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now);
        String referenceNumber = "ER-" + refDate + "-" + (1000 + (int) (Math.random() * 9000));

        Map<String, Object> emergencyMap = new HashMap<>();
        emergencyMap.put("userId", userId);
        emergencyMap.put("name", name);
        emergencyMap.put("contact", contact);
        emergencyMap.put("location", location);
        emergencyMap.put("urgencyLevel", urgency);
        emergencyMap.put("status", "Submitted");
        emergencyMap.put("reference", referenceNumber);
        emergencyMap.put("date", currentDate);
        emergencyMap.put("timestamp", timestampMillis);

        if (detectedLat != null && detectedLng != null) {
            emergencyMap.put("latitude", detectedLat);
            emergencyMap.put("longitude", detectedLng);
        }

        String requestId = databaseReference.push().getKey();
        if (requestId == null) { onSubmissionFinished(); return; }

        databaseReference.child(requestId).setValue(emergencyMap)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✓ Emergency request saved");
                    // Notify the member (self-confirmation)
                    sendSubmissionNotification(userId, name, referenceNumber, location, urgency);
                    // ✅ NEW: Notify ALL admins of the new request
                    sendAdminNotification(name, location, urgency, referenceNumber);
                    onSubmissionFinished();
                    LocaleHelper.showTranslatedToast(EmergencyWaterRequest.this,
                            "Request submitted! Reference: " + referenceNumber, Toast.LENGTH_LONG);
                    finish();
                })
                .addOnFailureListener(e -> {
                    onSubmissionFinished();
                    LocaleHelper.showTranslatedToast(EmergencyWaterRequest.this,
                            "Failed to submit request. Please try again.", Toast.LENGTH_LONG);
                });
    }

    private void sendSubmissionNotification(String targetUid, String targetName,
                                            String reference, String location, String urgency) {
        if (targetUid == null || targetUid.isEmpty() || "Anonymous".equals(targetUid)) return;

        String notifId = notificationsRef.push().getKey();
        if (notifId == null) return;

        String message = "🚨 Your emergency water request was submitted successfully!\n\n" +
                "Reference: " + reference + "\n" +
                "Urgency: " + urgency + "\n" +
                "Location: " + location + "\n\n" +
                "A dispatcher will review it shortly.";

        Map<String, Object> notif = new HashMap<>();
        notif.put("id", notifId);
        notif.put("message", message);
        notif.put("targetAudience", "Specific User");
        notif.put("targetUserId", targetUid);
        notif.put("targetUserName", targetName);
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("type", "WATER_STATUS");
        notif.put("relatedRequestId", reference);
        notif.put("isRead", false);

        notificationsRef.child(notifId).setValue(notif);
    }

    // ✅ NEW: Notify admins about the new emergency request
    private void sendAdminNotification(String memberName, String location,
                                       String urgency, String reference) {
        String notifId = notificationsRef.push().getKey();
        if (notifId == null) return;

        String message = "🚨 New emergency request from " + memberName + "\n\n" +
                "Urgency: " + urgency + "\n" +
                "Location: " + location + "\n" +
                "Reference: " + reference + "\n\n" +
                "Open Emergency Requests to assign a tanker.";

        Map<String, Object> notif = new HashMap<>();
        notif.put("id", notifId);
        notif.put("message", message);
        notif.put("targetAudience", "Admins");
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("type", "EMERGENCY_ASSIGNMENT");
        notif.put("relatedRequestId", reference);
        notif.put("isRead", false);

        notificationsRef.child(notifId).setValue(notif);
    }

    private void onSubmissionFinished() {
        progressBar.setVisibility(View.GONE);
        btnSubmitEmergency.setEnabled(true);
        btnSubmitEmergency.setText("Submit Emergency Request");
    }
}