package com.example.communitymember20;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

// EMAILJS IMPORTS
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DriverManagement extends BaseActivity {

    private static final String TAG = "DriverManagement";
    private static final String DB_TANKS = "water_tanks";

    // ============================================================
    // EMAILJS CREDENTIALS
    // ============================================================
    private static final String EMAILJS_SERVICE_ID = "service_j8ke2u4";
    private static final String EMAILJS_TEMPLATE_ID = "template_t7pzk1j";
    private static final String EMAILJS_PUBLIC_KEY = "Zxw3AfQDypb2xP6nd";
    // ============================================================

    private RecyclerView rvDrivers;
    private LinearLayout layoutEmpty;
    private MaterialToolbar toolbar;
    private ExtendedFloatingActionButton fabAddDriver;
    private DriverManagementAdapter adapter;
    private List<DriverForAssignmentModel> driverList;
    private DatabaseReference dbRef;

    private Map<String, TankerModel> tankByDriver = new HashMap<>();

    private final Set<String> liveEmergencyIds = new HashSet<>();

    private String lastProvisionedName;
    private String lastProvisionedEmail;
    private String lastProvisionedPhone;
    private String lastProvisionedPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driver_management);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvDrivers = findViewById(R.id.rvDrivers);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        fabAddDriver = findViewById(R.id.fabAddDriver);

        driverList = new ArrayList<>();
        adapter = new DriverManagementAdapter(driverList, new DriverManagementAdapter.OnDriverAction() {
            @Override public void onEdit(DriverForAssignmentModel d) {
                LocaleHelper.showTranslatedToast(DriverManagement.this,
                        "Edit " + d.fullName, Toast.LENGTH_SHORT);
            }
            @Override public void onReassign(DriverForAssignmentModel d) {
                openTankerPickerForDriver(d);
            }
            @Override public void onDelete(DriverForAssignmentModel d) {
                deleteDriver(d);
            }
        });
        rvDrivers.setLayoutManager(new LinearLayoutManager(this));
        rvDrivers.setAdapter(adapter);

        dbRef = FirebaseDatabase.getInstance().getReference();
        fabAddDriver.setOnClickListener(v -> showAddDriverDialog());

        loadTanksForLookup();
        loadLiveEmergenciesThenDrivers();
    }

    // ============================================================
    // Load live emergency IDs first, then load drivers
    // ============================================================
    private void loadLiveEmergenciesThenDrivers() {
        dbRef.child("emergency_requests").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                liveEmergencyIds.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    String status = snap.child("status").getValue(String.class);
                    if ("Delivered".equalsIgnoreCase(status)
                            || "Resolved".equalsIgnoreCase(status)) {
                        continue;
                    }
                    liveEmergencyIds.add(snap.getKey());
                }
                loadDrivers();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                loadDrivers();
            }
        });
    }

    private void openTankerPickerForDriver(DriverForAssignmentModel driver) {
        Intent intent = new Intent(this, WaterTanksManagement.class);
        intent.putExtra(WaterTanksManagement.EXTRA_PICK_MODE, true);
        intent.putExtra("assign_driver_uid", driver.uid);
        intent.putExtra("assign_driver_name", driver.fullName);
        startActivity(intent);
    }

    // ============================================================
    // ADD DRIVER DIALOG
    // ============================================================
    private void showAddDriverDialog() {
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_driver, null);

        TextInputEditText etName = view.findViewById(R.id.etDriverName);
        TextInputEditText etEmail = view.findViewById(R.id.etDriverEmail);
        TextInputEditText etPhone = view.findViewById(R.id.etDriverPhone);
        TextInputEditText etTempPw = view.findViewById(R.id.etTempPassword);

        new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setPositiveButton("Create Driver", (dialog, which) -> {
                    String name = etName.getText() != null
                            ? etName.getText().toString().trim() : "";
                    String email = etEmail.getText() != null
                            ? etEmail.getText().toString().trim() : "";
                    String phone = etPhone.getText() != null
                            ? etPhone.getText().toString().trim() : "";
                    String tempPw = etTempPw.getText() != null
                            ? etTempPw.getText().toString().trim() : "";

                    if (TextUtils.isEmpty(name)) {
                        LocaleHelper.showTranslatedToast(this,
                                "Please enter the driver's name", Toast.LENGTH_SHORT);
                        return;
                    }
                    if (TextUtils.isEmpty(email) || !email.contains("@")) {
                        LocaleHelper.showTranslatedToast(this,
                                "Please enter a valid email", Toast.LENGTH_SHORT);
                        return;
                    }
                    if (TextUtils.isEmpty(phone)) {
                        LocaleHelper.showTranslatedToast(this,
                                "Please enter a phone number", Toast.LENGTH_SHORT);
                        return;
                    }
                    if (tempPw.length() < 6) {
                        LocaleHelper.showTranslatedToast(this,
                                "Password must be at least 6 characters", Toast.LENGTH_SHORT);
                        return;
                    }

                    provisionNewDriver(name, email, phone, tempPw);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ============================================================
    // PROVISION NEW DRIVER (SEQUENTIAL: create → verify → save → email)
    // ============================================================
    private void provisionNewDriver(String name, String email,
                                    String phone, String tempPw) {

        FirebaseApp secondaryApp;
        try {
            secondaryApp = FirebaseApp.getInstance("secondary");
        } catch (IllegalStateException e) {
            FirebaseOptions primaryOptions = FirebaseApp.getInstance().getOptions();
            secondaryApp = FirebaseApp.initializeApp(this, primaryOptions, "secondary");
        }

        final FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);
        secondaryAuth.signOut();

        secondaryAuth.createUserWithEmailAndPassword(email, tempPw)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        String msg = task.getException() != null
                                ? task.getException().getMessage() : "unknown";
                        Log.e(TAG, "✗ Account creation failed: " + msg);
                        LocaleHelper.showTranslatedToast(this,
                                "Failed to create account: " + msg, Toast.LENGTH_LONG);
                        return;
                    }

                    FirebaseUser newUser = task.getResult().getUser();
                    if (newUser == null) {
                        LocaleHelper.showTranslatedToast(this,
                                "Failed to get user object", Toast.LENGTH_LONG);
                        return;
                    }

                    final String newUid = newUser.getUid();

                    // ---------------------------------------------------------
                    // ✅ STEP 1: Send verification email AND WAIT for completion
                    // ---------------------------------------------------------
                    Log.d(TAG, "Sending Firebase verification email to " + email);
                    newUser.sendEmailVerification()
                            .addOnCompleteListener(verifyTask -> {
                                if (verifyTask.isSuccessful()) {
                                    Log.d(TAG, "✓ Verification email sent successfully to " + email);
                                    runOnUiThread(() -> LocaleHelper.showTranslatedToast(
                                            DriverManagement.this,
                                            "Verification email sent to " + email,
                                            Toast.LENGTH_LONG));
                                } else {
                                    String err = verifyTask.getException() != null
                                            ? verifyTask.getException().getMessage() : "unknown";
                                    Log.e(TAG, "✗ Verification email failed: " + err);
                                    runOnUiThread(() -> LocaleHelper.showTranslatedToast(
                                            DriverManagement.this,
                                            "Could not send verification email: " + err,
                                            Toast.LENGTH_LONG));
                                }

                                // ---------------------------------------------------------
                                // ✅ STEP 2: Save the driver profile
                                // ---------------------------------------------------------
                                Map<String, Object> driverData = new HashMap<>();
                                driverData.put("full_name", name);
                                driverData.put("email", email);
                                driverData.put("phone_number", phone);
                                driverData.put("role", "Driver");
                                driverData.put("profile_complete", false);
                                driverData.put("license_no", null);
                                driverData.put("assigned_tanker", null);
                                driverData.put("preferred_lang", "English");
                                // Verified=true since admin created this account (still receives the verification email)
                                driverData.put("email_verified", true);
                                driverData.put("createdAt", System.currentTimeMillis());
                                driverData.put("createdBy", FirebaseAuth.getInstance().getUid());
                                driverData.put("driver_status", "Idle");
                                driverData.put("active_emergency_id", null);
                                driverData.put("active_emergency_location", null);

                                dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS)
                                        .child(newUid)
                                        .setValue(driverData)
                                        .addOnCompleteListener(dbTask -> {

                                            // ---------------------------------------------------------
                                            // ✅ STEP 3: Sign out the secondary auth (only after everything)
                                            // ---------------------------------------------------------
                                            try {
                                                secondaryAuth.signOut();
                                            } catch (Exception ignored) { }

                                            if (!dbTask.isSuccessful()) {
                                                String err = dbTask.getException() != null
                                                        ? dbTask.getException().getMessage() : "unknown";
                                                Log.e(TAG, "✗ Profile save failed: " + err);
                                                runOnUiThread(() -> LocaleHelper.showTranslatedToast(
                                                        DriverManagement.this,
                                                        "Profile save failed: " + err,
                                                        Toast.LENGTH_LONG));
                                                return;
                                            }

                                            Log.d(TAG, "✓ Driver profile saved: " + name);

                                            lastProvisionedName = name;
                                            lastProvisionedEmail = email;
                                            lastProvisionedPhone = phone;
                                            lastProvisionedPassword = tempPw;

                                            // ---------------------------------------------------------
                                            // ✅ STEP 4: Send EmailJS credentials email
                                            // ---------------------------------------------------------
                                            sendEmailViaEmailJS(email, name, tempPw);

                                            // ---------------------------------------------------------
                                            // ✅ STEP 5: Show credentials dialog to admin
                                            // ---------------------------------------------------------
                                            runOnUiThread(() -> showPlainCredentials(name, email, tempPw));

                                            writeAuditLog(name, email);
                                        });
                            });
                });
    }

    // ============================================================
    // AUTOMATIC EMAIL SENDING VIA EMAILJS REST API
    // ============================================================
    private void sendEmailViaEmailJS(String driverEmail, String driverName, String tempPw) {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.emailjs.com/api/v1.0/email/send");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("origin", "http://localhost");
                conn.setDoOutput(true);

                JSONObject templateParams = new JSONObject();
                templateParams.put("to_email", driverEmail);
                templateParams.put("to_name", driverName);
                templateParams.put("temp_password", tempPw);

                JSONObject payload = new JSONObject();
                payload.put("service_id", EMAILJS_SERVICE_ID);
                payload.put("template_id", EMAILJS_TEMPLATE_ID);
                payload.put("user_id", EMAILJS_PUBLIC_KEY);
                payload.put("template_params", templateParams);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();

                String responseBody = "";
                try {
                    InputStream is = (responseCode == 200)
                            ? conn.getInputStream() : conn.getErrorStream();
                    if (is != null) {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(is, StandardCharsets.UTF_8))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) sb.append(line);
                            responseBody = sb.toString();
                        }
                    }
                } catch (Exception ignored) { }

                final String finalResponseBody = responseBody;
                final int finalCode = responseCode;

                Log.d(TAG, "EmailJS response: " + finalCode + " | " + finalResponseBody);

                runOnUiThread(() -> {
                    if (finalCode == 200) {
                        LocaleHelper.showTranslatedToast(DriverManagement.this,
                                "Credentials emailed to " + driverEmail, Toast.LENGTH_LONG);
                    } else {
                        Log.e(TAG, "EmailJS error: " + finalCode + " " + finalResponseBody);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "EmailJS exception: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    // ============================================================
    // SHOW CREDENTIALS DIALOG (Backup for Admin)
    // ============================================================
    private void showPlainCredentials(String name, String email, String tempPw) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Driver Account Created ✓")
                .setMessage("The following have been sent to " + name + ":\n\n" +
                        "1️⃣  Firebase verification email\n" +
                        "2️⃣  Login credentials via EmailJS\n\n" +
                        "Backup Copy:\n" +
                        "📧  Email:  " + email + "\n" +
                        "🔑  Password:  " + tempPw + "\n\n" +
                        "They should change the password after their first login.")
                .setPositiveButton("Got it", null)
                .show();
    }

    // ============================================================
    // AUDIT LOG
    // ============================================================
    private void writeAuditLog(String driverName, String driverEmail) {
        String logId = dbRef.child("audit_logs").push().getKey();
        if (logId == null) return;

        Map<String, Object> log = new HashMap<>();
        log.put("action", "DRIVER_PROVISIONED");
        log.put("details", "Created new driver: " + driverName + " (" + driverEmail + ")");
        log.put("performedBy", FirebaseAuth.getInstance().getUid() != null
                ? FirebaseAuth.getInstance().getUid() : "admin");
        log.put("timestamp", System.currentTimeMillis());

        dbRef.child("audit_logs").child(logId).setValue(log);
    }

    // ============================================================
    // LOAD TANKERS LOOKUP
    // ============================================================
    private void loadTanksForLookup() {
        dbRef.child(DB_TANKS).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                tankByDriver.clear();
                for (DataSnapshot t : snapshot.getChildren()) {
                    TankerModel model = t.getValue(TankerModel.class);
                    if (model != null) {
                        model.tankerId = t.getKey();
                        if (model.assignedDriverId != null && !model.assignedDriverId.isEmpty()) {
                            tankByDriver.put(model.assignedDriverId, model);
                        }
                    }
                }
                applyTanksToDrivers();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    // ============================================================
    // LOAD DRIVERS + ORPHAN CLEANUP
    // ============================================================
    private void loadDrivers() {
        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        driverList.clear();

                        for (DataSnapshot userSnap : snapshot.getChildren()) {
                            String uid = userSnap.getKey();
                            String name = userSnap.child("full_name").getValue(String.class);
                            String phone = userSnap.child("phone_number").getValue(String.class);
                            String assignedTankerId = userSnap.child("assigned_tanker").getValue(String.class);
                            String driverStatus = userSnap.child("driver_status").getValue(String.class);
                            String activeEmergencyId = userSnap.child("active_emergency_id").getValue(String.class);
                            String activeEmergencyLocation = userSnap.child("active_emergency_location").getValue(String.class);

                            // Orphan cleanup
                            if (activeEmergencyId != null && !activeEmergencyId.isEmpty()
                                    && !liveEmergencyIds.contains(activeEmergencyId)) {
                                Log.d(TAG, "Cleaning orphan for driver " + uid);
                                dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS).child(uid)
                                        .child("active_emergency_id").removeValue();
                                dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS).child(uid)
                                        .child("active_emergency_location").removeValue();
                                dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS).child(uid)
                                        .child("driver_status").setValue("Idle");

                                if (assignedTankerId != null && !assignedTankerId.isEmpty()) {
                                    dbRef.child(DB_TANKS).child(assignedTankerId)
                                            .child("currentEmergencyId").removeValue();
                                    dbRef.child(DB_TANKS).child(assignedTankerId)
                                            .child("currentStatus").setValue("Idle");
                                }
                                activeEmergencyId = null;
                                driverStatus = "Idle";
                            }

                            DriverForAssignmentModel d = new DriverForAssignmentModel();
                            d.uid = uid;
                            d.fullName = name;
                            d.phone = phone;
                            d.tankerStatus = "Idle";
                            d.isAvailable = true;
                            d.driverStatus = driverStatus;
                            d.activeEmergencyId = activeEmergencyId;
                            d.activeEmergencyLocation = activeEmergencyLocation;

                            if (assignedTankerId != null && !assignedTankerId.isEmpty()) {
                                d.tankerId = assignedTankerId;
                            } else if (tankByDriver.containsKey(uid)) {
                                TankerModel t = tankByDriver.get(uid);
                                d.tankerId = t.tankerId;
                                d.tankerReg = t.registration;
                                d.capacity = t.capacity;
                                d.currentLoad = t.currentLoad;
                                d.tankerStatus = t.currentStatus;
                            }

                            driverList.add(d);
                        }

                        enrichWithTankerData();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        LocaleHelper.showTranslatedToast(DriverManagement.this,
                                "Failed: " + error.getMessage(), Toast.LENGTH_SHORT);
                    }
                });
    }

    private void applyTanksToDrivers() {
        for (DriverForAssignmentModel d : driverList) {
            if (d.tankerId == null || d.tankerId.isEmpty()) {
                if (tankByDriver.containsKey(d.uid)) {
                    TankerModel t = tankByDriver.get(d.uid);
                    d.tankerId = t.tankerId;
                    d.tankerReg = t.registration;
                    d.capacity = t.capacity;
                    d.currentLoad = t.currentLoad;
                    d.tankerStatus = t.currentStatus;
                }
            } else if (d.tankerReg == null) {
                TankerModel t = tankByDriver.get(d.uid);
                if (t != null) {
                    d.tankerReg = t.registration;
                    d.capacity = t.capacity;
                    d.currentLoad = t.currentLoad;
                    d.tankerStatus = t.currentStatus;
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void enrichWithTankerData() {
        for (DriverForAssignmentModel d : driverList) {
            if (d.tankerId == null || d.tankerId.isEmpty()) continue;
            if (d.tankerReg != null) continue;

            dbRef.child(DB_TANKS).child(d.tankerId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snap) {
                            TankerModel t = snap.getValue(TankerModel.class);
                            if (t == null) return;

                            d.tankerReg = t.registration;
                            d.capacity = t.capacity;
                            d.currentLoad = t.currentLoad;
                            d.tankerStatus = t.currentStatus;

                            adapter.notifyDataSetChanged();
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) { }
                    });
        }

        if (driverList.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvDrivers.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvDrivers.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    // ============================================================
    // DELETE DRIVER
    // ============================================================
    private void deleteDriver(DriverForAssignmentModel d) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Driver")
                .setMessage("Remove " + d.fullName + " from drivers?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    if (d.tankerId != null && !d.tankerId.isEmpty()) {
                        dbRef.child(DB_TANKS).child(d.tankerId)
                                .child("assignedDriverId").removeValue();
                        dbRef.child(DB_TANKS).child(d.tankerId)
                                .child("assignedDriverName").removeValue();
                    }
                    dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS)
                            .child(d.uid).removeValue();
                    LocaleHelper.showTranslatedToast(this, "Removed", Toast.LENGTH_SHORT);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}