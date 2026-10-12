package com.example.communitymember20;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AuditLogs extends BaseActivity {

    private RecyclerView rvAuditLogs;
    private LinearLayout layoutEmpty;
    private TextView tvEmptyTitle, tvEmptySubtitle;
    private TextInputEditText etSearch;
    private MaterialButton btnDateRange;
    private ChipGroup chipGroupFilters;
    private AuditLogAdapter adapter;

    // Data
    private final List<AuditLogModel> allLogs = new ArrayList<>();
    private final List<AuditLogModel> filteredLogs = new ArrayList<>();

    // Filters
    private String activeCategory = null;    // null = All
    private String searchQuery = "";          // lowercased
    private long dateFrom = 0L;               // 0 = no lower bound
    private long dateTo = Long.MAX_VALUE;    // max = no upper bound
    private String dateLabel = "All";         // shown on button

    // Firebase
    private DatabaseReference dbWater, dbEmergency, dbNotifications, dbUsers,
            dbAssignments, dbTanks, dbIssues;
    private ValueEventListener waterListener, emergencyListener, notificationListener,
            usersListener, assignmentsListener, tanksListener, issuesListener;

    private final List<AuditLogModel> waterLogs = new ArrayList<>();
    private final List<AuditLogModel> emergencyLogs = new ArrayList<>();
    private final List<AuditLogModel> notificationLogs = new ArrayList<>();
    private final List<AuditLogModel> userLogs = new ArrayList<>();
    private final List<AuditLogModel> assignmentLogs = new ArrayList<>();
    private final List<AuditLogModel> tankLogs = new ArrayList<>();
    private final List<AuditLogModel> issueLogs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audit_logs);

        // Setup Toolbar (custom MaterialToolbar - title handled in XML)
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvAuditLogs = findViewById(R.id.rvAuditLogs);
        layoutEmpty = findViewById(R.id.tvEmptyLogs); // The outer LinearLayout container
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);
        etSearch = findViewById(R.id.etSearch);
        btnDateRange = findViewById(R.id.btnDateRange);
        chipGroupFilters = findViewById(R.id.chipGroupFilters);

        adapter = new AuditLogAdapter(filteredLogs);
        rvAuditLogs.setLayoutManager(new LinearLayoutManager(this));
        rvAuditLogs.setAdapter(adapter);

        setupSearch();
        setupDateButton();
        setupCategoryChips();

        FirebaseDatabase db = FirebaseDatabase.getInstance();
        dbWater = db.getReference("water_statuses");
        dbEmergency = db.getReference("emergency_requests");
        dbNotifications = db.getReference("notifications");
        dbUsers = db.getReference("users");
        dbAssignments = db.getReference("tanker_assignments");
        dbTanks = db.getReference("water_tanks");
        dbIssues = db.getReference("driver_issues");

        attachWaterListener();
        attachEmergencyListener();
        attachNotificationListener();
        attachUsersListener();
        attachAssignmentsListener();
        attachTankListener();
        attachIssueListener();
    }

    // ===============================================================
    // SEARCH
    // ===============================================================
    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                searchQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                rebuildAndShow();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    // ===============================================================
    // DATE RANGE
    // ===============================================================
    private void setupDateButton() {
        btnDateRange.setOnClickListener(v -> showDateRangeDialog());
    }

    private void showDateRangeDialog() {
        String[] options = {
                "All Time",
                "Today",
                "Yesterday",
                "Last 7 Days",
                "Last 30 Days",
                "This Month",
                "Custom Range…"
        };

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Filter by Date")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: setDateAllTime(); break;
                        case 1: setDateToday(); break;
                        case 2: setDateYesterday(); break;
                        case 3: setDateLast7Days(); break;
                        case 4: setDateLast30Days(); break;
                        case 5: setDateThisMonth(); break;
                        case 6: showCustomDateRangePicker(); break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setDateAllTime() {
        dateFrom = 0L;
        dateTo = Long.MAX_VALUE;
        dateLabel = "All";
        btnDateRange.setText(dateLabel);
        rebuildAndShow();
    }

    private void setDateToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        dateFrom = c.getTimeInMillis();
        dateTo = Long.MAX_VALUE;
        dateLabel = "Today";
        btnDateRange.setText(dateLabel);
        rebuildAndShow();
    }

    private void setDateYesterday() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();

        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, 0);
        end.set(Calendar.MINUTE, 0);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);
        long endMs = end.getTimeInMillis();

        dateFrom = start;
        dateTo = endMs;
        dateLabel = "Yesterday";
        btnDateRange.setText(dateLabel);
        rebuildAndShow();
    }

    private void setDateLast7Days() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -7);
        dateFrom = c.getTimeInMillis();
        dateTo = Long.MAX_VALUE;
        dateLabel = "7 Days";
        btnDateRange.setText(dateLabel);
        rebuildAndShow();
    }

    private void setDateLast30Days() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -30);
        dateFrom = c.getTimeInMillis();
        dateTo = Long.MAX_VALUE;
        dateLabel = "30 Days";
        btnDateRange.setText(dateLabel);
        rebuildAndShow();
    }

    private void setDateThisMonth() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        dateFrom = c.getTimeInMillis();
        dateTo = Long.MAX_VALUE;
        dateLabel = "This Month";
        btnDateRange.setText(dateLabel);
        rebuildAndShow();
    }

    private void showCustomDateRangePicker() {
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText("Select date range")
                        .setTheme(com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialCalendar)
                        .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null && selection.first != null && selection.second != null) {
                Calendar start = Calendar.getInstance();
                start.setTimeInMillis(selection.first);
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                dateFrom = start.getTimeInMillis();

                Calendar end = Calendar.getInstance();
                end.setTimeInMillis(selection.second);
                end.set(Calendar.HOUR_OF_DAY, 23);
                end.set(Calendar.MINUTE, 59);
                end.set(Calendar.SECOND, 59);
                end.set(Calendar.MILLISECOND, 999);
                dateTo = end.getTimeInMillis();

                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", Locale.getDefault());
                dateLabel = sdf.format(start.getTime()) + " – " + sdf.format(end.getTime());
                btnDateRange.setText(dateLabel);
                rebuildAndShow();
            }
        });

        picker.show(getSupportFragmentManager(), "date_range_picker");
    }

    // ===============================================================
    // CATEGORY CHIPS
    // ===============================================================
    private void setupCategoryChips() {
        chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                activeCategory = null;
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.chipAll) activeCategory = null;
                else if (id == R.id.chipWater) activeCategory = "WATER_REPORT";
                else if (id == R.id.chipEmergency) activeCategory = "EMERGENCY_REQUEST";
                else if (id == R.id.chipNotifications) activeCategory = "NOTIFICATION_SENT";
                else if (id == R.id.chipAssignments) activeCategory = "TANKER_ASSIGNED";
                else if (id == R.id.chipTanks) activeCategory = "TANK_UPDATED";
                else if (id == R.id.chipUsers) activeCategory = "USER_ACTIVITY";
                else if (id == R.id.chipIssues) activeCategory = "DRIVER_ISSUE";
                else activeCategory = null;
            }
            rebuildAndShow();
        });
    }

    // ===============================================================
    // APPLY ALL FILTERS
    // ===============================================================
    private void rebuildAndShow() {
        allLogs.clear();
        allLogs.addAll(waterLogs);
        allLogs.addAll(emergencyLogs);
        allLogs.addAll(notificationLogs);
        allLogs.addAll(userLogs);
        allLogs.addAll(assignmentLogs);
        allLogs.addAll(tankLogs);
        allLogs.addAll(issueLogs);

        Collections.sort(allLogs, (a, b) -> Long.compare(b.timestamp, a.timestamp));

        filteredLogs.clear();
        for (AuditLogModel m : allLogs) {
            if (activeCategory != null && !activeCategory.equals(m.action)) continue;
            if (m.timestamp < dateFrom || m.timestamp > dateTo) continue;
            if (!searchQuery.isEmpty() && !matchesSearch(m, searchQuery)) continue;
            filteredLogs.add(m);
        }

        if (filteredLogs.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvAuditLogs.setVisibility(View.GONE);
            tvEmptyTitle.setText("No matching activity");
            tvEmptySubtitle.setText("Try adjusting your search, date, or category filter.");
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvAuditLogs.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    private boolean matchesSearch(AuditLogModel m, String q) {
        if (m.details != null && m.details.toLowerCase(Locale.ROOT).contains(q)) return true;
        if (m.performedBy != null && m.performedBy.toLowerCase(Locale.ROOT).contains(q)) return true;
        if (m.action != null && m.action.toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }

    // ===============================================================
    // REAL-TIME LISTENERS
    // ===============================================================

    private void attachWaterListener() {
        waterListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                waterLogs.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    String name = data.child("name").getValue(String.class);
                    String waterStatus = data.child("waterStatus").getValue(String.class);
                    String status = data.child("status").getValue(String.class);
                    Long timestamp = data.child("timestamp").getValue(Long.class);

                    String details = (name != null ? name : "User") + " reported " +
                            (waterStatus != null ? waterStatus : "water status") +
                            " (Report is " + (status != null ? status : "Received") + ")";

                    waterLogs.add(new AuditLogModel(
                            data.getKey(), "WATER_REPORT", details,
                            name != null ? name : "User",
                            timestamp != null ? timestamp : 0));
                }
                rebuildAndShow();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };
        dbWater.addValueEventListener(waterListener);
    }

    private void attachEmergencyListener() {
        emergencyListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                emergencyLogs.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    String name = data.child("name").getValue(String.class);
                    String status = data.child("status").getValue(String.class);
                    String urgency = data.child("urgencyLevel").getValue(String.class);
                    Long timestamp = data.child("timestamp").getValue(Long.class);

                    String details = (name != null ? name : "Household") +
                            " · Emergency water request" +
                            (urgency != null ? " · " + urgency + " priority" : "") +
                            " · Status: " + (status != null ? status : "Pending");

                    emergencyLogs.add(new AuditLogModel(
                            data.getKey(), "EMERGENCY_REQUEST", details,
                            name != null ? name : "Community Member",
                            timestamp != null ? timestamp : 0));
                }
                rebuildAndShow();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };
        dbEmergency.addValueEventListener(emergencyListener);
    }

    private void attachNotificationListener() {
        notificationListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                notificationLogs.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    String message = data.child("message").getValue(String.class);
                    String target = data.child("targetAudience").getValue(String.class);
                    Long timestamp = data.child("timestamp").getValue(Long.class);

                    String details = "Sent to " + (target != null ? target : "All") +
                            ": \"" + (message != null ? message : "") + "\"";

                    notificationLogs.add(new AuditLogModel(
                            data.getKey(), "NOTIFICATION_SENT", details,
                            "Admin",
                            timestamp != null ? timestamp : 0));
                }
                rebuildAndShow();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };
        dbNotifications.addValueEventListener(notificationListener);
    }

    private void attachUsersListener() {
        usersListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                userLogs.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    String name = data.child("full_name").getValue(String.class);
                    String role = data.child("role").getValue(String.class);
                    String status = data.child("status").getValue(String.class);
                    String email = data.child("email").getValue(String.class);
                    Long ts = data.child("updatedAt").getValue(Long.class);
                    if (ts == null) ts = System.currentTimeMillis();

                    String details = (name != null ? name : "User") +
                            " · Role: " + (role != null ? role : "—") +
                            (status != null ? " · Status: " + status : "");

                    userLogs.add(new AuditLogModel(
                            data.getKey(), "USER_ACTIVITY", details,
                            email != null ? email : "System",
                            ts));
                }
                rebuildAndShow();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };
        dbUsers.addValueEventListener(usersListener);
    }

    private void attachAssignmentsListener() {
        assignmentsListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                assignmentLogs.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    String driverName = data.child("driverName").getValue(String.class);
                    String tankerReg = data.child("tankerReg").getValue(String.class);
                    String emergencyTitle = data.child("emergencyRequestTitle").getValue(String.class);
                    String status = data.child("status").getValue(String.class);
                    Long ts = data.child("lastUpdateAt").getValue(Long.class);
                    if (ts == null) ts = data.child("assignedAt").getValue(Long.class);
                    if (ts == null) ts = 0L;

                    String details = "Assigned " + (driverName != null ? driverName : "driver") +
                            " (" + (tankerReg != null ? tankerReg : "no tanker") + ") to " +
                            (emergencyTitle != null ? emergencyTitle : "emergency") +
                            " · " + (status != null ? status : "Assigned");

                    assignmentLogs.add(new AuditLogModel(
                            data.getKey(), "TANKER_ASSIGNED", details,
                            "Admin",
                            ts));
                }
                rebuildAndShow();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };
        dbAssignments.addValueEventListener(assignmentsListener);
    }

    private void attachTankListener() {
        tanksListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                tankLogs.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    String reg = data.child("registration").getValue(String.class);
                    String status = data.child("currentStatus").getValue(String.class);
                    Integer load = data.child("currentLoad").getValue(Integer.class);
                    String driver = data.child("assignedDriverName").getValue(String.class);
                    Long ts = data.child("lastUpdated").getValue(Long.class);
                    if (ts == null) ts = 0L;

                    String details = (reg != null ? reg : "Water tank") +
                            " · Status: " + (status != null ? status : "Idle") +
                            (load != null && load > 0 ? " · " + String.format("%,d", load) + " L" : "") +
                            (driver != null ? " · Driver: " + driver : "");

                    tankLogs.add(new AuditLogModel(
                            data.getKey(), "TANK_UPDATED", details,
                            driver != null ? driver : "System",
                            ts));
                }
                rebuildAndShow();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };
        dbTanks.addValueEventListener(tanksListener);
    }

    private void attachIssueListener() {
        issuesListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                issueLogs.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    String issue = data.child("issue").getValue(String.class);
                    String driverId = data.child("driverId").getValue(String.class);
                    Long ts = data.child("timestamp").getValue(Long.class);
                    if (ts == null) ts = 0L;

                    String details = "Driver reported: " + (issue != null ? issue : "issue");

                    issueLogs.add(new AuditLogModel(
                            data.getKey(), "DRIVER_ISSUE", details,
                            driverId != null ? driverId : "Driver",
                            ts));
                }
                rebuildAndShow();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };
        dbIssues.addValueEventListener(issuesListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (waterListener != null) dbWater.removeEventListener(waterListener);
        if (emergencyListener != null) dbEmergency.removeEventListener(emergencyListener);
        if (notificationListener != null) dbNotifications.removeEventListener(notificationListener);
        if (usersListener != null) dbUsers.removeEventListener(usersListener);
        if (assignmentsListener != null) dbAssignments.removeEventListener(assignmentsListener);
        if (tanksListener != null) dbTanks.removeEventListener(tanksListener);
        if (issuesListener != null) dbIssues.removeEventListener(issuesListener);
    }
}