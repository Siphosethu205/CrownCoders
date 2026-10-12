package com.example.communitymember20;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminWaterStatus extends BaseActivity {

    private static final String TAG = "AdminWaterStatus";

    private RecyclerView rvWaterReports;
    private AdminWaterStatusAdapter adapter;
    private List<WaterStatusModel> originalList;
    private List<WaterStatusModel> filteredList;
    private DatabaseReference dbRef;

    private MaterialToolbar toolbar;
    private MaterialButton btnFilterAll, btnFilterAvailable, btnFilterNoWater, btnFilterLow;

    // Insights
    private android.widget.TextView tvInsightTotal, tvInsightNoWater, tvInsightHotspot;

    private String activeFilter = "All";

    // ✅ Cached CSV content for the export flow
    private String lastCsvContent = "";
    private String lastCsvFileName = "";

    // ✅ File picker for export
    private final ActivityResultLauncher<Intent> exportFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) writeCsvToUri(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_water_status);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // ✅ Add "Export" action to the toolbar
        toolbar.inflateMenu(R.menu.menu_admin_water_status);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_export) {
                exportReports();
                return true;
            }
            return false;
        });

        rvWaterReports = findViewById(R.id.rvWaterReports);
        rvWaterReports.setLayoutManager(new LinearLayoutManager(this));

        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterAvailable = findViewById(R.id.btnFilterAvailable);
        btnFilterNoWater = findViewById(R.id.btnFilterNoWater);
        btnFilterLow = findViewById(R.id.btnFilterLow);

        // Insights views
        tvInsightTotal = findViewById(R.id.tvInsightTotal);
        tvInsightNoWater = findViewById(R.id.tvInsightNoWater);
        tvInsightHotspot = findViewById(R.id.tvInsightHotspot);

        originalList = new ArrayList<>();
        filteredList = new ArrayList<>();

        adapter = new AdminWaterStatusAdapter(filteredList, new AdminWaterStatusAdapter.OnWaterStatusAction() {
            @Override
            public void onAssignTanker(WaterStatusModel model) {
                showAssignTankerDialog(model);
            }
            @Override
            public void onEdit(WaterStatusModel model) {
                Toast.makeText(AdminWaterStatus.this, "Edit: " + model.name, Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onDelete(WaterStatusModel model) {
                new MaterialAlertDialogBuilder(AdminWaterStatus.this)
                        .setTitle("Delete Report")
                        .setMessage("Delete " + (model.name != null ? model.name : "this report") + "'s report?")
                        .setPositiveButton("Delete", (d, w) -> {
                            if (model.id != null) {
                                dbRef.child(model.id).removeValue()
                                        .addOnSuccessListener(a -> Toast.makeText(AdminWaterStatus.this,
                                                "Deleted", Toast.LENGTH_SHORT).show());
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        rvWaterReports.setAdapter(adapter);

        dbRef = FirebaseDatabase.getInstance().getReference("water_statuses");

        fetchWaterReports();

        btnFilterAll.setOnClickListener(v -> filterList("All"));
        btnFilterAvailable.setOnClickListener(v -> filterList("Available"));
        btnFilterNoWater.setOnClickListener(v -> filterList("No Water"));
        btnFilterLow.setOnClickListener(v -> filterList("Low"));

        btnFilterAll.setSelected(true);
    }

    // ============================================================
    // FETCH + LIVE INSIGHTS
    // ============================================================
    private void fetchWaterReports() {
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                originalList.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    WaterStatusModel model = data.getValue(WaterStatusModel.class);
                    if (model != null) {
                        model.id = data.getKey();
                        originalList.add(model);
                    }
                }
                filterList(activeFilter);
                updateInsights();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                LocaleHelper.showTranslatedToast(AdminWaterStatus.this,
                        "Failed to load data.", Toast.LENGTH_SHORT);
            }
        });
    }

    private void updateInsights() {
        int total = originalList.size();
        int noWaterCount = 0;
        Map<String, Integer> locationCounts = new HashMap<>();
        String hotspot = "—";
        int maxCount = 0;

        for (WaterStatusModel m : originalList) {
            if ("No Water".equalsIgnoreCase(m.waterStatus)) noWaterCount++;
            if (m.location != null && !m.location.isEmpty()) {
                int c = locationCounts.getOrDefault(m.location, 0) + 1;
                locationCounts.put(m.location, c);
                if (c > maxCount) {
                    maxCount = c;
                    hotspot = m.location;
                }
            }
        }

        if (tvInsightTotal != null) tvInsightTotal.setText(String.valueOf(total));
        if (tvInsightNoWater != null) tvInsightNoWater.setText(String.valueOf(noWaterCount));
        if (tvInsightHotspot != null) {
            tvInsightHotspot.setText(maxCount > 1 ? hotspot : "—");
        }
    }

    private void filterList(String waterStatusFilter) {
        activeFilter = waterStatusFilter;
        filteredList.clear();

        if ("All".equals(waterStatusFilter)) {
            filteredList.addAll(originalList);
        } else {
            for (WaterStatusModel item : originalList) {
                String ws = item.waterStatus != null ? item.waterStatus : "Unknown";
                if (ws.equalsIgnoreCase(waterStatusFilter)) filteredList.add(item);
            }
        }

        btnFilterAll.setSelected("All".equals(waterStatusFilter));
        btnFilterAvailable.setSelected("Available".equals(waterStatusFilter));
        btnFilterLow.setSelected("Low".equals(waterStatusFilter));
        btnFilterNoWater.setSelected("No Water".equals(waterStatusFilter));

        adapter.notifyDataSetChanged();
    }

    // ============================================================
    // ASSIGN TANKER — pick a driver from a list
    // ============================================================
    private void showAssignTankerDialog(WaterStatusModel model) {
        if (model == null || model.location == null) return;

        DatabaseReference usersRef = FirebaseDatabase.getInstance()
                .getReference("users").child(UserNodeHelper.NODE_DRIVERS);

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> names = new ArrayList<>();
                List<String> ids = new ArrayList<>();

                for (DataSnapshot u : snapshot.getChildren()) {
                    String name = u.child("full_name").getValue(String.class);
                    String driverStatus = u.child("driver_status").getValue(String.class);

                    // Only offer drivers who aren't on another job
                    if (name != null && (driverStatus == null
                            || !"Not Available".equalsIgnoreCase(driverStatus))) {
                        names.add(name);
                        ids.add(u.getKey());
                    }
                }

                if (names.isEmpty()) {
                    Toast.makeText(AdminWaterStatus.this,
                            "No available drivers right now", Toast.LENGTH_LONG).show();
                    return;
                }

                new MaterialAlertDialogBuilder(AdminWaterStatus.this)
                        .setTitle("Assign Tanker to " + model.location)
                        .setItems(names.toArray(new String[0]), (d, which) -> {
                            String driverId = ids.get(which);
                            String driverName = names.get(which);
                            performAssignTanker(model, driverId, driverName);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AdminWaterStatus.this,
                        "Failed to load drivers", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performAssignTanker(WaterStatusModel model, String driverId, String driverName) {
        // 1. Update the water status report — mark as handled
        if (model.id != null) {
            Map<String, Object> reportUpdate = new HashMap<>();
            reportUpdate.put("status", "Tanker Assigned");
            reportUpdate.put("assignedDriverId", driverId);
            reportUpdate.put("assignedDriverName", driverName);
            reportUpdate.put("assignedAt", System.currentTimeMillis());
            dbRef.child(model.id).updateChildren(reportUpdate);
        }

        // 2. Notify the driver
        DatabaseReference notificationsRef = FirebaseDatabase.getInstance()
                .getReference("notifications");
        String notifId = notificationsRef.push().getKey();
        if (notifId != null) {
            Map<String, Object> notif = new HashMap<>();
            notif.put("id", notifId);
            notif.put("message",
                    "🚚 You have been assigned a tanker delivery!\n\n" +
                            "📍 Location: " + model.location + "\n" +
                            "📋 Reason: No water reported by " + (model.name != null ? model.name : "community member") + "\n\n" +
                            "Please open your dashboard to proceed.");
            notif.put("targetAudience", "Specific User");
            notif.put("targetUserId", driverId);
            notif.put("targetUserName", driverName);
            notif.put("timestamp", System.currentTimeMillis());
            notif.put("type", "EMERGENCY_ASSIGNMENT");
            notif.put("isRead", false);
            notificationsRef.child(notifId).setValue(notif);
        }

        // 3. Notify the member who reported it
        if (model.userId != null && !model.userId.isEmpty()) {
            String memberNotifId = notificationsRef.push().getKey();
            if (memberNotifId != null) {
                Map<String, Object> memberNotif = new HashMap<>();
                memberNotif.put("id", memberNotifId);
                memberNotif.put("message",
                        "🚚 A water tanker has been assigned to your area.\n\n" +
                                "📍 Location: " + model.location + "\n\n" +
                                "You'll be notified when the driver is on the way.");
                memberNotif.put("targetAudience", "Specific User");
                memberNotif.put("targetUserId", model.userId);
                memberNotif.put("targetUserName", model.name);
                memberNotif.put("timestamp", System.currentTimeMillis());
                memberNotif.put("type", "EMERGENCY_UPDATE");
                memberNotif.put("isRead", false);
                notificationsRef.child(memberNotifId).setValue(memberNotif);
            }
        }

        Toast.makeText(this,
                "✓ Tanker assigned to " + driverName,
                Toast.LENGTH_LONG).show();
    }

    // ============================================================
    // EXPORT TO CSV
    // ============================================================
    private void exportReports() {
        if (filteredList.isEmpty()) {
            Toast.makeText(this, "No reports to export", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csv = new StringBuilder();

        // Header
        csv.append("Reference,Date,Member Name,Contact,Location,Water Status,Admin Status\n");

        // Rows
        for (WaterStatusModel m : filteredList) {
            csv.append(csvCell(m.reference)).append(",");
            csv.append(csvCell(m.date)).append(",");
            csv.append(csvCell(m.name)).append(",");
            csv.append(csvCell(m.contact)).append(",");
            csv.append(csvCell(m.location)).append(",");
            csv.append(csvCell(m.waterStatus)).append(",");
            csv.append(csvCell(m.status)).append("\n");
        }

        lastCsvContent = csv.toString();
        String dateStr = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
        lastCsvFileName = "AquaComm_WaterReports_" + dateStr + ".csv";

        // Launch the file picker
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, lastCsvFileName);
        exportFileLauncher.launch(intent);
    }

    private void writeCsvToUri(Uri uri) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) {
                Toast.makeText(this, "Could not open file", Toast.LENGTH_SHORT).show();
                return;
            }
            os.write(lastCsvContent.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            Toast.makeText(this,
                    "✓ Exported " + filteredList.size() + " reports to " + lastCsvFileName,
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Log.e(TAG, "Export failed: " + e.getMessage());
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // CSV cell escaper — wraps in quotes if contains comma/quote/newline
    private String csvCell(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}