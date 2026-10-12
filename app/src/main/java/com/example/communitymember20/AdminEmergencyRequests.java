package com.example.communitymember20;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminEmergencyRequests extends BaseActivity {

    private static final String DB_TANKS = "water_tanks";

    private RecyclerView rvEmergencyRequests;
    private MaterialToolbar toolbar;
    private EmergencyRequestAdapter adapter;
    private List<EmergencyRequestModel> requestList;
    private DatabaseReference dbRef;

    private final String[] statusOptions = {
            "Submitted",
            "Received",
            "Tanker on the way",
            "Delivered",
            "Resolved"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_emergency_requests);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvEmergencyRequests = findViewById(R.id.rvEmergencyRequests);
        rvEmergencyRequests.setLayoutManager(new LinearLayoutManager(this));

        requestList = new ArrayList<>();
        adapter = new EmergencyRequestAdapter(
                requestList,
                this::openAssignTanker,
                this::showStatusUpdateDialog,
                this::confirmUnassign
        );
        rvEmergencyRequests.setAdapter(adapter);

        dbRef = FirebaseDatabase.getInstance().getReference();

        fetchEmergencyRequests();
    }

    private void openAssignTanker(EmergencyRequestModel request) {
        Intent intent = new Intent(this, AssignTanker.class);
        intent.putExtra(AssignTanker.EXTRA_REQUEST_ID, request.id);
        intent.putExtra(AssignTanker.EXTRA_REQUEST_TITLE,
                (request.name != null ? request.name + " Household" : "Emergency Request"));
        intent.putExtra(AssignTanker.EXTRA_REQUEST_LOCATION, request.location);
        intent.putExtra(AssignTanker.EXTRA_REQUEST_URGENCY, request.urgencyLevel);
        startActivity(intent);
    }

    // ============================================================
    // UNASSIGN
    // ============================================================
    private void confirmUnassign(EmergencyRequestModel request) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Unassign Tanker?")
                .setMessage("This will:\n" +
                        "• Remove the driver and tanker from this request\n" +
                        "• Free the driver + tanker for other jobs\n" +
                        "• Return the request to 'Submitted'\n\n" +
                        "Continue?")
                .setPositiveButton("Unassign", (d, w) -> performUnassign(request))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performUnassign(EmergencyRequestModel request) {
        if (request.id == null) return;

        final String oldDriverId = request.assignedDriverId;
        final String oldTankerId = request.assignedTankerId;

        // Delete matching tanker_assignment(s)
        dbRef.child("tanker_assignments")
                .orderByChild("emergencyRequestId").equalTo(request.id)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot s : snapshot.getChildren()) {
                            s.getRef().removeValue();
                        }

                        // Clear emergency request fields
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("assignedDriverId", null);
                        updates.put("assignedDriverName", null);
                        updates.put("assignedTankerId", null);
                        updates.put("assignedTankerReg", null);
                        updates.put("assignedAssignmentId", null);
                        updates.put("assignedAt", null);
                        updates.put("status", "Submitted");

                        dbRef.child("emergency_requests").child(request.id)
                                .updateChildren(updates)
                                .addOnSuccessListener(a -> {

                                    if (oldDriverId != null && !oldDriverId.isEmpty()) {
                                        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS)
                                                .child(oldDriverId)
                                                .child("driver_status").setValue("Idle");
                                        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS)
                                                .child(oldDriverId)
                                                .child("active_emergency_id").removeValue();
                                        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS)
                                                .child(oldDriverId)
                                                .child("active_emergency_location").removeValue();
                                    }

                                    if (oldTankerId != null && !oldTankerId.isEmpty()) {
                                        dbRef.child(DB_TANKS).child(oldTankerId)
                                                .child("currentStatus").setValue("Idle");
                                        dbRef.child(DB_TANKS).child(oldTankerId)
                                                .child("currentEmergencyId").removeValue();
                                        dbRef.child(DB_TANKS).child(oldTankerId)
                                                .child("currentAssignmentId").removeValue();
                                    }

                                    Toast.makeText(AdminEmergencyRequests.this,
                                            "Unassigned ✓", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(AdminEmergencyRequests.this,
                                                "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    // ============================================================
    // STATUS UPDATE
    // ============================================================
    private void showStatusUpdateDialog(EmergencyRequestModel request) {
        int currentIndex = 0;
        if (request.status != null) {
            for (int i = 0; i < statusOptions.length; i++) {
                if (statusOptions[i].equalsIgnoreCase(request.status)) {
                    currentIndex = i;
                    break;
                }
            }
        }

        final int[] selectedIndex = { currentIndex };

        new MaterialAlertDialogBuilder(this)
                .setTitle("Update Request Status")
                .setSingleChoiceItems(statusOptions, currentIndex,
                        (dialog, which) -> selectedIndex[0] = which)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newStatus = statusOptions[selectedIndex[0]];
                    updateStatusInFirebase(request, newStatus);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateStatusInFirebase(EmergencyRequestModel request, String newStatus) {
        if (request.id == null) return;

        dbRef.child("emergency_requests").child(request.id)
                .child("status").setValue(newStatus)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Status: " + newStatus, Toast.LENGTH_SHORT).show();
                    sendStatusChangeNotification(request, newStatus);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void sendStatusChangeNotification(EmergencyRequestModel request, String newStatus) {
        if (request.userId == null || request.userId.isEmpty()) return;

        String notifId = dbRef.child("notifications").push().getKey();
        if (notifId == null) return;

        String message;
        switch (newStatus.toLowerCase()) {
            case "received":
                message = "📩 Your emergency water request has been received.";
                break;
            case "tanker on the way":
                message = "🚚 A water tanker is on the way to your location!";
                break;
            case "delivered":
                message = "✅ Your water has been delivered. Thank you!";
                break;
            case "resolved":
                message = "✅ Your emergency request has been resolved.";
                break;
            default:
                message = "🔄 Status updated to: " + newStatus;
                break;
        }
        if (request.location != null) message += "\n\n📍 " + request.location;

        Map<String, Object> notif = new HashMap<>();
        notif.put("id", notifId);
        notif.put("message", message);
        notif.put("targetAudience", "Specific User");
        notif.put("targetUserId", request.userId);
        notif.put("targetUserName", request.name);
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("type", "EMERGENCY_UPDATE");
        notif.put("isRead", false);

        dbRef.child("notifications").child(notifId).setValue(notif);
    }

    // ============================================================
    // LOAD REQUESTS — simple listener, no auto-cleanup
    // ============================================================
    private void fetchEmergencyRequests() {
        dbRef.child("emergency_requests").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                requestList.clear();

                for (DataSnapshot data : snapshot.getChildren()) {
                    EmergencyRequestModel model = data.getValue(EmergencyRequestModel.class);
                    if (model == null) continue;
                    model.id = data.getKey();
                    requestList.add(model);
                }

                java.util.Collections.sort(requestList, (a, b) -> {
                    long t1 = a.timestamp != null ? a.timestamp : 0;
                    long t2 = b.timestamp != null ? b.timestamp : 0;
                    return Long.compare(t2, t1);
                });

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }
}