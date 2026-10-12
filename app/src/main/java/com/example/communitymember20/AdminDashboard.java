package com.example.communitymember20;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class AdminDashboard extends BaseActivity {

    private static final String TAG = "AdminDashboard";

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private TextView tvWelcome, tvTotalReports, tvWaterAvailable, tvActiveTankers, tvPendingEmergencies;
    private TextView tvHotspotLocation, tvHotspotAlerts;

    private LinearLayout actionWaterReports, actionUsers, actionNotify, actionStats;

    private DatabaseReference dbRef;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_dashboard);

        tvWelcome = findViewById(R.id.tvWelcome);
        tvTotalReports = findViewById(R.id.tvTotalReports);
        tvWaterAvailable = findViewById(R.id.tvWaterAvailable);
        tvActiveTankers = findViewById(R.id.tvActiveTankers);
        tvPendingEmergencies = findViewById(R.id.tvPendingEmergencies);
        tvHotspotLocation = findViewById(R.id.tvHotspotLocation);
        tvHotspotAlerts = findViewById(R.id.tvHotspotAlerts);

        ImageView ivMenu = findViewById(R.id.ivMenu);
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);

        // Quick Action buttons
        actionWaterReports = findViewById(R.id.actionWaterReports);
        actionUsers = findViewById(R.id.actionUsers);
        actionNotify = findViewById(R.id.actionNotify);
        actionStats = findViewById(R.id.actionStats);

        dbRef = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

        ivMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        setupDrawerNavigation();
        setupQuickActions();

        // Tapping the avatar opens admin profile settings
        View ivAvatar = findViewById(R.id.ivAvatar);
        if (ivAvatar != null) {
            ivAvatar.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(AdminDashboard.this, ProfileSettings.class));
                } catch (Exception e) {
                    Toast.makeText(this, "Profile settings coming soon", Toast.LENGTH_SHORT).show();
                }
            });
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        fetchAdminName();
        fetchDashboardMetrics();
    }

    // ============================================================
    // QUICK ACTIONS
    // ============================================================
    private void setupQuickActions() {
        // Opens Emergency Requests
        if (actionWaterReports != null) {
            actionWaterReports.setOnClickListener(v ->
                    startActivity(new Intent(AdminDashboard.this, AdminEmergencyRequests.class)));
        }

        if (actionUsers != null) {
            actionUsers.setOnClickListener(v ->
                    startActivity(new Intent(AdminDashboard.this, UserManagement.class)));
        }

        if (actionNotify != null) {
            actionNotify.setOnClickListener(v ->
                    startActivity(new Intent(AdminDashboard.this, SendNotifications.class)));
        }

        if (actionStats != null) {
            actionStats.setOnClickListener(v ->
                    startActivity(new Intent(AdminDashboard.this, ReportStatistics.class)));
        }
    }

    // ============================================================
    // DRAWER NAVIGATION
    // ============================================================
    private void setupDrawerNavigation() {
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_dashboard) {
                // Already here
            } else if (id == R.id.nav_water_reports) {
                startActivity(new Intent(AdminDashboard.this, AdminWaterStatus.class));
            } else if (id == R.id.nav_emergency) {
                startActivity(new Intent(AdminDashboard.this, AdminEmergencyRequests.class));
            } else if (id == R.id.nav_drivers) {
                startActivity(new Intent(AdminDashboard.this, DriverManagement.class));
            } else if (id == R.id.nav_waterTanks) {
                startActivity(new Intent(AdminDashboard.this, WaterTanksManagement.class));
            } else if (id == R.id.nav_users) {
                startActivity(new Intent(AdminDashboard.this, UserManagement.class));
            } else if (id == R.id.nav_notifications) {
                startActivity(new Intent(AdminDashboard.this, SendNotifications.class));
            } else if (id == R.id.nav_stats) {
                startActivity(new Intent(AdminDashboard.this, ReportStatistics.class));
            } else if (id == R.id.nav_audit) {
                startActivity(new Intent(AdminDashboard.this, AuditLogs.class));
            } else if (id == R.id.nav_infra_issues) {
                startActivity(new Intent(AdminDashboard.this, InfrastructureIssues.class));
            } else if (id == R.id.nav_logout) {
                showLogoutDialog();
            } else {
                Toast.makeText(this, "Feature coming soon!", Toast.LENGTH_SHORT).show();
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of AquaComm?")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    mAuth.signOut();
                    Intent intent = new Intent(AdminDashboard.this, LoginPage.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ============================================================
    // FETCH ADMIN NAME
    // ============================================================
    private void fetchAdminName() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        dbRef.child("users").child(UserNodeHelper.NODE_ADMINS)
                .child(currentUser.getUid()).child("full_name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists() && tvWelcome != null) {
                            String fullName = snapshot.getValue(String.class);
                            if (fullName != null && !fullName.isEmpty()) {
                                tvWelcome.setTag(null);
                                tvWelcome.setText("Welcome back, " + fullName);
                                LocaleHelper.translateAllViews(AdminDashboard.this, tvWelcome);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to fetch name: " + error.getMessage());
                    }
                });
    }

    // ============================================================
    // FETCH DASHBOARD METRICS
    // ============================================================
    private void fetchDashboardMetrics() {

        // 1. Water statuses — total + available
        dbRef.child("water_statuses").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int total = 0;
                int available = 0;
                for (DataSnapshot data : snapshot.getChildren()) {
                    total++;
                    String waterStatus = data.child("waterStatus").getValue(String.class);
                    if (waterStatus != null && waterStatus.equalsIgnoreCase("Available")) {
                        available++;
                    }
                }
                if (tvTotalReports != null) tvTotalReports.setText(String.valueOf(total));
                if (tvWaterAvailable != null) tvWaterAvailable.setText(String.valueOf(available));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        // 2. Emergency requests — total + pending + hotspot
        dbRef.child("emergency_requests").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int totalRequests = 0;
                int pending = 0;
                String hotspotLocation = "No Data";
                int maxAlerts = 0;
                Map<String, Integer> locationCounts = new HashMap<>();

                for (DataSnapshot data : snapshot.getChildren()) {
                    totalRequests++;

                    String status = data.child("status").getValue(String.class);
                    String location = data.child("location").getValue(String.class);

                    if (status != null
                            && !status.equalsIgnoreCase("Delivered")
                            && !status.equalsIgnoreCase("Resolved")) {
                        pending++;
                    }

                    if (location != null && !location.isEmpty()) {
                        int count = locationCounts.getOrDefault(location, 0) + 1;
                        locationCounts.put(location, count);
                        if (count > maxAlerts) {
                            maxAlerts = count;
                            hotspotLocation = location;
                        }
                    }
                }

                if (tvActiveTankers != null) tvActiveTankers.setText(String.valueOf(totalRequests));
                if (tvPendingEmergencies != null) tvPendingEmergencies.setText(String.valueOf(pending));
                if (tvHotspotLocation != null) tvHotspotLocation.setText(hotspotLocation);
                if (tvHotspotAlerts != null) tvHotspotAlerts.setText(maxAlerts + " red alert(s)");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        // 3. Tanker count
        dbRef.child("water_tanks").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long totalTanks = snapshot.getChildrenCount();
                Log.d(TAG, "Total tanks: " + totalTanks);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        // 4. User counts
        dbRef.child("users").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long members = snapshot.child(UserNodeHelper.NODE_MEMBERS).getChildrenCount();
                long drivers = snapshot.child(UserNodeHelper.NODE_DRIVERS).getChildrenCount();
                long admins  = snapshot.child(UserNodeHelper.NODE_ADMINS).getChildrenCount();
                Log.d(TAG, "Members: " + members
                        + " | Drivers: " + drivers
                        + " | Admins: " + admins);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }
}