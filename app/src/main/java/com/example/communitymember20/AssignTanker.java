package com.example.communitymember20;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AssignTanker extends BaseActivity {

    private static final String DB_TANKS = "water_tanks";
    private static final int REQUEST_PICK_TANKER = 7001;

    public static final String EXTRA_REQUEST_ID = "request_id";
    public static final String EXTRA_REQUEST_TITLE = "request_title";
    public static final String EXTRA_REQUEST_LOCATION = "request_location";
    public static final String EXTRA_REQUEST_URGENCY = "request_urgency";

    private RecyclerView rvDrivers;
    private DriverAssignmentAdapter adapter;
    private List<DriverForAssignmentModel> driverList;
    private LinearLayout layoutEmpty;
    private TextView tvEmpty, tvEmergencyTitle, tvEmergencyLocation, tvEmergencyUrgency;

    private DatabaseReference dbRef;
    private String requestId;
    private String requestTitle;
    private String requestLocation;
    private String requestUrgency;
    private DriverForAssignmentModel pendingDriver = null;

    // ✅ Track which emergencies exist (to detect orphaned driver references)
    private final Set<String> liveEmergencyIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.assign_tanker);

        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        requestTitle = getIntent().getStringExtra(EXTRA_REQUEST_TITLE);
        requestLocation = getIntent().getStringExtra(EXTRA_REQUEST_LOCATION);
        requestUrgency = getIntent().getStringExtra(EXTRA_REQUEST_URGENCY);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvDrivers = findViewById(R.id.rvDrivers);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvEmergencyTitle = findViewById(R.id.tvEmergencyTitle);
        tvEmergencyLocation = findViewById(R.id.tvEmergencyLocation);
        tvEmergencyUrgency = findViewById(R.id.tvEmergencyUrgency);

        if (requestTitle != null) tvEmergencyTitle.setText(requestTitle);
        if (requestLocation != null) tvEmergencyLocation.setText(requestLocation);
        if (requestUrgency != null) tvEmergencyUrgency.setText(requestUrgency.toUpperCase());

        driverList = new ArrayList<>();
        adapter = new DriverAssignmentAdapter(driverList, this::onAssignClicked);
        rvDrivers.setLayoutManager(new LinearLayoutManager(this));
        rvDrivers.setAdapter(adapter);

        dbRef = FirebaseDatabase.getInstance().getReference();

        // ✅ First: load the set of live emergency IDs
        loadLiveEmergenciesThenDrivers();
    }

    // ============================================================
    // Step 1: Load all live emergencies into a set
    // ============================================================
    private void loadLiveEmergenciesThenDrivers() {
        dbRef.child("emergency_requests").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                liveEmergencyIds.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    String status = snap.child("status").getValue(String.class);
                    if ("Delivered".equalsIgnoreCase(status)
                            || "Resolved".equalsIgnoreCase(status)) {
                        continue; // closed
                    }
                    liveEmergencyIds.add(snap.getKey());
                }

                // Now load drivers, cleaning up orphans in the process
                loadDrivers();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                loadDrivers(); // proceed anyway
            }
        });
    }

    // ============================================================
    // Step 2: Load drivers + cleanup orphaned assignments
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
                            String tankerId = userSnap.child("assigned_tanker").getValue(String.class);
                            String driverStatus = userSnap.child("driver_status").getValue(String.class);
                            String activeEmergencyId = userSnap.child("active_emergency_id").getValue(String.class);

                            // ✅ If the driver has an active_emergency_id but it no longer exists,
                            //    clean up the orphaned reference automatically
                            if (activeEmergencyId != null && !activeEmergencyId.isEmpty()
                                    && !liveEmergencyIds.contains(activeEmergencyId)) {
                                Log.d("AssignTanker", "Cleaning orphaned assignment for driver " + uid);
                                cleanupOrphanedDriver(uid, tankerId);
                                activeEmergencyId = null;
                                driverStatus = "Idle";
                            }

                            DriverForAssignmentModel d = new DriverForAssignmentModel();
                            d.uid = uid;
                            d.fullName = name;
                            d.phone = phone;
                            d.tankerId = tankerId;
                            d.driverStatus = driverStatus;
                            d.activeEmergencyId = activeEmergencyId;

                            // Driver is available only if they have no active emergency
                            boolean isBusy = activeEmergencyId != null && !activeEmergencyId.isEmpty();
                            d.isAvailable = !isBusy;
                            d.tankerStatus = "Idle";

                            driverList.add(d);
                        }
                        enrichWithTankerData();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        LocaleHelper.showTranslatedToast(AssignTanker.this,
                                "Failed: " + error.getMessage(), Toast.LENGTH_SHORT);
                    }
                });
    }

    // ============================================================
    // Cleanup orphaned driver assignment in the DB
    // ============================================================
    private void cleanupOrphanedDriver(String driverUid, String tankerId) {
        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS).child(driverUid)
                .child("active_emergency_id").removeValue();
        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS).child(driverUid)
                .child("active_emergency_location").removeValue();
        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS).child(driverUid)
                .child("driver_status").setValue("Idle");

        // Also clear the tanker's "In Use" status if it was tied to the orphan
        if (tankerId != null && !tankerId.isEmpty()) {
            dbRef.child(DB_TANKS).child(tankerId).child("currentEmergencyId").removeValue();
            dbRef.child(DB_TANKS).child(tankerId).child("currentStatus").setValue("Idle");
        }
    }

    // ============================================================
    // Enrich driver list with tanker details
    // ============================================================
    private void enrichWithTankerData() {
        for (DriverForAssignmentModel d : driverList) {
            if (d.tankerId != null && !d.tankerId.isEmpty()) {
                dbRef.child(DB_TANKS).child(d.tankerId)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (!snapshot.exists()) return;
                                d.tankerReg = snapshot.child("registration").getValue(String.class);
                                Integer cap = snapshot.child("capacity").getValue(Integer.class);
                                d.capacity = cap != null ? cap : 0;
                                d.tankerStatus = snapshot.child("currentStatus").getValue(String.class);
                                adapter.notifyDataSetChanged();
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) { }
                        });
            }
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
    // Tap "Assign" → open tanker picker
    // ============================================================
    private void onAssignClicked(DriverForAssignmentModel driver) {
        if (!driver.isAvailable) {
            LocaleHelper.showTranslatedToast(AssignTanker.this,
                    driver.fullName + " is already on an assignment",
                    Toast.LENGTH_SHORT);
            return;
        }

        pendingDriver = driver;

        Intent intent = new Intent(this, WaterTanksManagement.class);
        intent.putExtra(WaterTanksManagement.EXTRA_PICK_MODE, true);
        startActivityForResult(intent, REQUEST_PICK_TANKER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_TANKER && resultCode == RESULT_OK && data != null) {
            String tankerId = data.getStringExtra(WaterTanksManagement.RESULT_TANKER_ID);
            String tankerReg = data.getStringExtra(WaterTanksManagement.RESULT_TANKER_REG);
            if (tankerId != null && pendingDriver != null) {
                confirmAndAssign(pendingDriver, tankerId, tankerReg);
            }
        } else {
            pendingDriver = null;
        }
    }

    private void confirmAndAssign(DriverForAssignmentModel driver, String tankerId, String tankerReg) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Assignment")
                .setMessage("Assign " + driver.fullName + " with tanker "
                        + (tankerReg != null ? tankerReg : tankerId)
                        + " to this emergency?")
                .setPositiveButton("Assign", (d, w) -> performAssignment(driver, tankerId, tankerReg))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performAssignment(DriverForAssignmentModel driver, String tankerId, String tankerReg) {
        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS).child(driver.uid)
                .child("email")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String driverEmail = snapshot.getValue(String.class);
                        if (driverEmail == null) driverEmail = "";

                        AssignmentHelper.assign(
                                requestId,
                                requestTitle != null ? requestTitle : "Emergency Request",
                                requestLocation != null ? requestLocation : "",
                                requestUrgency != null ? requestUrgency : "Standard",
                                tankerId,
                                tankerReg != null ? tankerReg : "",
                                driver.uid,
                                driver.fullName != null ? driver.fullName : "Driver",
                                driverEmail,
                                new AssignmentHelper.Callback() {
                                    @Override
                                    public void onSuccess() {
                                        runOnUiThread(() -> {
                                            LocaleHelper.showTranslatedToast(AssignTanker.this,
                                                    "Assigned " + driver.fullName + " ✓",
                                                    Toast.LENGTH_LONG);
                                            setResult(RESULT_OK);
                                            finish();
                                        });
                                    }

                                    @Override
                                    public void onFailure(String error) {
                                        runOnUiThread(() ->
                                                LocaleHelper.showTranslatedToast(AssignTanker.this,
                                                        "Failed: " + error,
                                                        Toast.LENGTH_LONG));
                                    }
                                }
                        );
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        finish();
                    }
                });
    }
}
