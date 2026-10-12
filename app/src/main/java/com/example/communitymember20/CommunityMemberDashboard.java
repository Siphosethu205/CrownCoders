package com.example.communitymember20;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CommunityMemberDashboard extends BaseActivity {

    private static final String TAG = "MemberDashboard";

    private View btnNavigateReport, btnNavigateTrack, btnNavigateEmergency,
            btnNavigateReportIssue;
    private View btnNotifications, btnUpdateProfile, btnLogout;

    private DrawerLayout drawerLayout;
    private ImageView btnMenu;
    private NavigationView navigationView;
    private BottomNavigationView bottomNav;

    private TextView tvWelcomeUser;
    private LinearLayout recentActivityContainer;

    private MaterialCardView cardActiveEmergency;
    private TextView tvActiveEmergencyZone;

    private MaterialCardView cardDeliveryFeedback;

    // OTP entry card
    private MaterialCardView cardOtpEntry;
    private TextInputEditText etOtpEntry;
    private TextInputLayout tilOtpEntry;
    private MaterialButton btnConfirmDeliveryOtp;

    private TextView tvWaterTip;
    private MaterialCardView cardWaterTip;
    private SharedPreferences tipPrefs;
    private final Random random = new Random();

    // ✅ Live listeners
    private ValueEventListener waterListener;
    private ValueEventListener emergencyListener;
    private ValueEventListener waterStatusListener;
    private ValueEventListener emergencyStatusListener;

    // ✅ Keep references to the actual Query objects so we can detach cleanly
    private Query attachedWaterQuery;
    private Query attachedEmergencyQuery;
    private Query attachedEmergencyStatusQuery;
    private Query attachedFeedbackQuery;

    private String currentUserId = null;
    private String previousEmergencyStatus = null;

    // OTP state
    private String activeAssignmentId = null;
    private String activeEmergencyId = null;
    private String activeOtp = null;
    private boolean otpCardShown = false;

    // ✅ Cached activity data
    private final List<Map<String, Object>> waterActivities = new ArrayList<>();
    private final List<Map<String, Object>> emergencyActivities = new ArrayList<>();

    private final String[] waterTips = {
            "Fix a dripping tap — it can save up to 20 litres a day.",
            "Take shorter showers — every minute saves up to 10 litres.",
            "Water your garden early morning or late evening to reduce evaporation.",
            "Turn off the tap while brushing your teeth.",
            "Run washing machines and dishwashers only with full loads.",
            "Collect rainwater for watering your garden.",
            "Use a broom instead of a hose to clean driveways.",
            "Check for leaks in your toilet tank — add food colouring to test.",
            "Install a low-flow showerhead to save water.",
            "Don't use running water to thaw frozen food.",
            "Reuse water from rinsing vegetables to water plants.",
            "Only water your lawn when it actually needs it.",
            "Cover your pool to reduce evaporation.",
            "Use a bucket when washing your car, not a running hose.",
            "Insulate your water pipes so you don't waste water waiting for it to heat up.",
            "Plant drought-resistant plants in your garden.",
            "Report leaks to your municipality immediately.",
            "Teach children to close taps tightly after use.",
            "Install a water-efficient toilet to save thousands of litres a year.",
            "Wash fruits and vegetables in a bowl, not under running water."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.community_member_dashboard);

        btnNavigateReport = findViewById(R.id.btnNavigateReport);
        btnNavigateTrack = findViewById(R.id.btnNavigateTrack);
        btnNavigateEmergency = findViewById(R.id.btnNavigateEmergency);
        btnNavigateReportIssue = findViewById(R.id.btnNavigateReportIssue);
        btnNotifications = findViewById(R.id.btnNotifications);
        btnUpdateProfile = findViewById(R.id.btnUpdateProfile);
        btnLogout = findViewById(R.id.btnLogout);

        tvWelcomeUser = findViewById(R.id.tvWelcomeUser);
        recentActivityContainer = findViewById(R.id.recentActivityContainer);

        cardActiveEmergency = findViewById(R.id.cardActiveEmergency);
        tvActiveEmergencyZone = findViewById(R.id.tvActiveEmergencyZone);
        cardDeliveryFeedback = findViewById(R.id.cardDeliveryFeedback);

        cardOtpEntry = findViewById(R.id.cardOtpEntry);
        etOtpEntry = findViewById(R.id.etOtpEntry);
        tilOtpEntry = findViewById(R.id.tilOtpEntry);
        btnConfirmDeliveryOtp = findViewById(R.id.btnConfirmDeliveryOtp);

        tvWaterTip = findViewById(R.id.tvWaterTip);
        cardWaterTip = findViewById(R.id.cardWaterTip);
        tipPrefs = getSharedPreferences("TipPrefs", MODE_PRIVATE);

        if (cardWaterTip != null) cardWaterTip.setOnClickListener(v -> showRandomTip());

        if (cardActiveEmergency != null) {
            cardActiveEmergency.setOnClickListener(v ->
                    startActivity(new Intent(this, MyEmergencyRequests.class)));
        }

        if (btnConfirmDeliveryOtp != null) {
            btnConfirmDeliveryOtp.setOnClickListener(v -> verifyOtp());
        }

        drawerLayout = findViewById(R.id.drawerLayout);
        btnMenu = findViewById(R.id.btnMenu);
        navigationView = findViewById(R.id.navigationView);
        bottomNav = findViewById(R.id.bottomNav);

        if (btnMenu != null && drawerLayout != null) {
            btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) return true;
                else if (itemId == R.id.nav_chats) { navigateToBottomTab(CommunityMemberChats.class); return true; }
                else if (itemId == R.id.nav_notifications) { navigateToBottomTab(CommunityMemberNotifications.class); return true; }
                else if (itemId == R.id.nav_menu) { navigateToBottomTab(Menu.class); return true; }
                return false;
            });
            NotificationBadgeHelper.refresh(this, bottomNav);
        }

        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_drawer_profile) startActivity(new Intent(this, ProfileSettings.class));
                else if (id == R.id.nav_drawer_my_reports) startActivity(new Intent(this, MyWaterStatusUpdates.class));
                else if (id == R.id.nav_drawer_my_emergency) startActivity(new Intent(this, MyEmergencyRequests.class));
                else if (id == R.id.nav_drawer_my_issues) startActivity(new Intent(this, MyReportedIssues.class));
                else if (id == R.id.nav_drawer_about) startActivity(new Intent(this, AboutUs.class));
                else if (id == R.id.nav_drawer_logout) showLogoutDialog();
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        btnNavigateReport.setOnClickListener(v -> startActivity(new Intent(this, WaterStatus.class)));
        btnNavigateTrack.setOnClickListener(v -> startActivity(new Intent(this, DriverTracker.class)));
        btnNavigateEmergency.setOnClickListener(v -> startActivity(new Intent(this, EmergencyWaterRequest.class)));
        if (btnNavigateReportIssue != null) btnNavigateReportIssue.setOnClickListener(v -> startActivity(new Intent(this, ReportIssue.class)));
        btnUpdateProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileSettings.class)));
        if (btnLogout != null) btnLogout.setOnClickListener(v -> showLogoutDialog());
    }

    // ============================================================
    // ✅ ATTACH LISTENERS IN onResume — ensures fresh data every time
    // ============================================================
    @Override
    protected void onResume() {
        super.onResume();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        currentUserId = user.getUid();
        Log.d(TAG, "onResume → attaching listeners for uid=" + currentUserId);

        // Detach any stale listeners first to avoid duplicates
        detachAllListeners();

        // Attach fresh
        fetchUserName(currentUserId);
        attachActiveEmergencyListener(currentUserId);
        attachRecentActivityListeners(currentUserId);
        attachDeliveryFeedbackListener(currentUserId);

        showRandomTip();
        if (bottomNav != null) NotificationBadgeHelper.refresh(this, bottomNav);
    }

    // ============================================================
    // ✅ DETACH LISTENERS IN onPause — prevents stale listener leaks
    // ============================================================
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause → detaching listeners");
        detachAllListeners();
    }

    private void detachAllListeners() {
        try {
            if (attachedWaterQuery != null && waterListener != null) {
                attachedWaterQuery.removeEventListener(waterListener);
            }
            if (attachedEmergencyQuery != null && emergencyListener != null) {
                attachedEmergencyQuery.removeEventListener(emergencyListener);
            }
            if (attachedEmergencyStatusQuery != null && emergencyStatusListener != null) {
                attachedEmergencyStatusQuery.removeEventListener(emergencyStatusListener);
            }
            if (attachedFeedbackQuery != null && waterStatusListener != null) {
                attachedFeedbackQuery.removeEventListener(waterStatusListener);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to detach listeners: " + e.getMessage());
        }

        attachedWaterQuery = null;
        attachedEmergencyQuery = null;
        attachedEmergencyStatusQuery = null;
        attachedFeedbackQuery = null;
        waterListener = null;
        emergencyListener = null;
        waterStatusListener = null;
        emergencyStatusListener = null;

        // Clear cached data so we don't show stale items
        waterActivities.clear();
        emergencyActivities.clear();
        if (recentActivityContainer != null) recentActivityContainer.removeAllViews();
    }

    private void navigateToBottomTab(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of AquaComm?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log Out", (dialog, which) -> performLogout())
                .show();
    }

    private void performLogout() {
        getSharedPreferences("AquacommPrefs", MODE_PRIVATE).edit()
                .remove("user_role").remove("user_node").remove("is_guest").apply();
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginPage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showRandomTip() {
        if (tvWaterTip == null) return;
        int lastIndex = tipPrefs.getInt("last_tip_index", -1);
        int tempIndex;
        do { tempIndex = random.nextInt(waterTips.length); }
        while (waterTips.length > 1 && tempIndex == lastIndex);
        final int newIndex = tempIndex;
        tipPrefs.edit().putInt("last_tip_index", newIndex).apply();
        tvWaterTip.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            tvWaterTip.setTag(null);
            tvWaterTip.setText(waterTips[newIndex]);
            LocaleHelper.translateAllViews(CommunityMemberDashboard.this, tvWaterTip);
            tvWaterTip.animate().alpha(1f).setDuration(200).start();
        }).start();
    }

    private void fetchUserName(String userId) {
        SharedPreferences prefs = getSharedPreferences("AquacommPrefs", MODE_PRIVATE);
        String userNode = prefs.getString("user_node", null);
        if (userNode != null) loadNameFromNode(userNode, userId, false);
        else for (String node : UserNodeHelper.allNodes()) loadNameFromNode(node, userId, true);
    }

    private void loadNameFromNode(String node, String userId, boolean silentIfMissing) {
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("users").child(node).child(userId).child("full_name");
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                String fullName = snapshot.getValue(String.class);
                if (fullName != null && !fullName.isEmpty() && tvWelcomeUser != null) {
                    String firstName = fullName.split(" ")[0];
                    tvWelcomeUser.setTag(null);
                    tvWelcomeUser.setText("Welcome, " + firstName);
                    LocaleHelper.translateAllViews(CommunityMemberDashboard.this, tvWelcomeUser);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    // ============================================================
    // ACTIVE EMERGENCY + OTP card
    // ============================================================
    private void attachActiveEmergencyListener(String userId) {
        if (cardActiveEmergency == null) return;

        DatabaseReference emergencyRef = FirebaseDatabase.getInstance()
                .getReference("emergency_requests");
        attachedEmergencyStatusQuery = emergencyRef.orderByChild("userId").equalTo(userId).limitToLast(1);

        emergencyStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Emergency status listener fired, children=" + snapshot.getChildrenCount());

                if (!snapshot.exists() || snapshot.getChildrenCount() == 0) {
                    cardActiveEmergency.setVisibility(View.GONE);
                    if (cardOtpEntry != null) cardOtpEntry.setVisibility(View.GONE);
                    previousEmergencyStatus = null;
                    return;
                }

                for (DataSnapshot data : snapshot.getChildren()) {
                    String status = data.child("status").getValue(String.class);
                    String location = data.child("location").getValue(String.class);
                    String assignmentId = data.child("assignedAssignmentId").getValue(String.class);
                    String emergencyId = data.getKey();

                    boolean statusChanged = previousEmergencyStatus != null && status != null
                            && !previousEmergencyStatus.equalsIgnoreCase(status);

                    boolean isActive = status != null
                            && !status.equalsIgnoreCase("Delivered")
                            && !status.equalsIgnoreCase("Confirmed")
                            && !status.equalsIgnoreCase("Disputed");

                    if (isActive) {
                        if (tvActiveEmergencyZone != null) {
                            String zoneText = (location != null ? location : "Your area")
                                    + " — " + status + " (tap to see details)";
                            tvActiveEmergencyZone.setTag(null);
                            tvActiveEmergencyZone.setText(zoneText);
                        }
                        cardActiveEmergency.setVisibility(View.VISIBLE);
                        LocaleHelper.translateAllViews(CommunityMemberDashboard.this, cardActiveEmergency);

                        if (statusChanged) {
                            cardActiveEmergency.post(() -> flicker(cardActiveEmergency));
                        }
                    } else {
                        cardActiveEmergency.setVisibility(View.GONE);
                    }

                    if ("Arrived".equalsIgnoreCase(status)
                            && assignmentId != null
                            && !assignmentId.isEmpty()) {
                        activeAssignmentId = assignmentId;
                        activeEmergencyId = emergencyId;
                        loadOtpForAssignment(assignmentId);
                    } else {
                        if (cardOtpEntry != null) cardOtpEntry.setVisibility(View.GONE);
                        otpCardShown = false;
                    }

                    previousEmergencyStatus = status;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Emergency status listener cancelled: " + error.getMessage());
                cardActiveEmergency.setVisibility(View.GONE);
                if (cardOtpEntry != null) cardOtpEntry.setVisibility(View.GONE);
            }
        };

        attachedEmergencyStatusQuery.addValueEventListener(emergencyStatusListener);
    }

    private void loadOtpForAssignment(String assignmentId) {
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("tanker_assignments").child(assignmentId);

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) {
                    if (cardOtpEntry != null) cardOtpEntry.setVisibility(View.GONE);
                    return;
                }

                Boolean verified = snap.child("otpVerified").getValue(Boolean.class);
                String otp = snap.child("deliveryOtp").getValue(String.class);

                if (otp == null || (verified != null && verified)) {
                    if (cardOtpEntry != null) cardOtpEntry.setVisibility(View.GONE);
                    otpCardShown = false;
                    return;
                }

                activeOtp = otp;
                if (cardOtpEntry != null) {
                    boolean wasHidden = cardOtpEntry.getVisibility() != View.VISIBLE;
                    cardOtpEntry.setVisibility(View.VISIBLE);

                    if (!otpCardShown && wasHidden) {
                        cardOtpEntry.post(() -> flicker(cardOtpEntry));
                        otpCardShown = true;
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void verifyOtp() {
        if (etOtpEntry == null || etOtpEntry.getText() == null) return;

        String entered = etOtpEntry.getText().toString().trim();
        if (entered.length() != 6) {
            if (tilOtpEntry != null) tilOtpEntry.setError("Enter the full 6-digit code");
            return;
        }
        if (tilOtpEntry != null) tilOtpEntry.setError(null);

        if (activeOtp == null) {
            Toast.makeText(this, "No pending delivery to verify", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!entered.equals(activeOtp)) {
            if (tilOtpEntry != null) tilOtpEntry.setError("Incorrect code. Ask the driver again.");
            Toast.makeText(this, "❌ Invalid code", Toast.LENGTH_SHORT).show();
            return;
        }

        confirmDeliveryOtp();
    }

    private void confirmDeliveryOtp() {
        if (activeAssignmentId == null || activeEmergencyId == null) return;

        long now = System.currentTimeMillis();

        Map<String, Object> assignmentUpdates = new HashMap<>();
        assignmentUpdates.put("otpVerified", true);
        assignmentUpdates.put("otpVerifiedAt", now);
        assignmentUpdates.put("status", "Confirmed");
        assignmentUpdates.put("completedAt", now);
        assignmentUpdates.put("lastUpdateAt", now);
        assignmentUpdates.put("lastNote", "Delivery confirmed by member via OTP");

        FirebaseDatabase.getInstance().getReference("tanker_assignments")
                .child(activeAssignmentId).updateChildren(assignmentUpdates)
                .addOnSuccessListener(aVoid -> {
                    Map<String, Object> emergencyUpdates = new HashMap<>();
                    emergencyUpdates.put("status", "Confirmed");
                    emergencyUpdates.put("deliveredAt", now);
                    emergencyUpdates.put("memberFeedback", "RECEIVED");
                    emergencyUpdates.put("memberFeedbackAt", now);

                    FirebaseDatabase.getInstance().getReference("emergency_requests")
                            .child(activeEmergencyId).updateChildren(emergencyUpdates)
                            .addOnSuccessListener(a -> {
                                notifyDriverOfConfirmation(activeEmergencyId);
                                freeTankerAndDriver(activeEmergencyId);
                                if (cardOtpEntry != null) cardOtpEntry.setVisibility(View.GONE);
                                if (etOtpEntry != null) etOtpEntry.setText("");
                                if (tilOtpEntry != null) tilOtpEntry.setError(null);
                                otpCardShown = false;
                                Toast.makeText(CommunityMemberDashboard.this,
                                        "✅ Delivery confirmed!", Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void freeTankerAndDriver(String emergencyId) {
        FirebaseDatabase.getInstance().getReference("emergency_requests").child(emergencyId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        String driverId = snap.child("assignedDriverId").getValue(String.class);
                        String tankerId = snap.child("assignedTankerId").getValue(String.class);

                        if (driverId != null && !driverId.isEmpty()) {
                            DatabaseReference driverRef = FirebaseDatabase.getInstance()
                                    .getReference("users").child(UserNodeHelper.NODE_DRIVERS).child(driverId);
                            driverRef.child("driver_status").setValue("Idle");
                            driverRef.child("active_emergency_id").removeValue();
                            driverRef.child("active_emergency_location").removeValue();
                        }

                        if (tankerId != null && !tankerId.isEmpty()) {
                            DatabaseReference tankRef = FirebaseDatabase.getInstance()
                                    .getReference("water_tanks").child(tankerId);
                            tankRef.child("currentStatus").setValue("Idle");
                            tankRef.child("currentEmergencyId").removeValue();
                            tankRef.child("currentAssignmentId").removeValue();
                            tankRef.child("currentLoad").setValue(0);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    private void notifyDriverOfConfirmation(String emergencyId) {
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        db.getReference("emergency_requests").child(emergencyId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        String driverId = snap.child("assignedDriverId").getValue(String.class);
                        String driverName = snap.child("assignedDriverName").getValue(String.class);
                        if (driverId == null || driverId.isEmpty()) return;

                        String notifId = db.getReference("notifications").push().getKey();
                        if (notifId == null) return;

                        Map<String, Object> notif = new HashMap<>();
                        notif.put("id", notifId);
                        notif.put("message", "✅ The member confirmed delivery with your code. Nice work!");
                        notif.put("targetAudience", "Specific User");
                        notif.put("targetUserId", driverId);
                        notif.put("targetUserName", driverName);
                        notif.put("timestamp", System.currentTimeMillis());
                        notif.put("type", "DELIVERY_CONFIRMED");
                        notif.put("relatedRequestId", emergencyId);
                        notif.put("isRead", false);

                        db.getReference("notifications").child(notifId).setValue(notif);
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    // ============================================================
    // RECENT ACTIVITY — combined, sorted, real-time
    // ============================================================
    private void attachRecentActivityListeners(String userId) {
        if (recentActivityContainer == null) return;

        DatabaseReference waterRef = FirebaseDatabase.getInstance()
                .getReference("water_statuses");
        DatabaseReference emergencyRef = FirebaseDatabase.getInstance()
                .getReference("emergency_requests");

        // ✅ Water listener
        attachedWaterQuery = waterRef.orderByChild("userId").equalTo(userId);
        waterListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Water activity listener fired, children=" + snapshot.getChildrenCount());

                waterActivities.clear();
                for (DataSnapshot d : snapshot.getChildren()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("status", d.child("status").getValue(String.class));
                    item.put("date", d.child("date").getValue(String.class));
                    item.put("timestamp", d.child("timestamp").getValue(Long.class));
                    waterActivities.add(item);
                }
                renderRecentActivity();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Water activity listener cancelled: " + error.getMessage());
            }
        };
        attachedWaterQuery.addValueEventListener(waterListener);

        // ✅ Emergency listener
        attachedEmergencyQuery = emergencyRef.orderByChild("userId").equalTo(userId);
        emergencyListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Emergency activity listener fired, children=" + snapshot.getChildrenCount());

                emergencyActivities.clear();
                for (DataSnapshot d : snapshot.getChildren()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("status", d.child("status").getValue(String.class));
                    item.put("date", d.child("date").getValue(String.class));
                    item.put("timestamp", d.child("timestamp").getValue(Long.class));
                    emergencyActivities.add(item);
                }
                renderRecentActivity();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Emergency activity listener cancelled: " + error.getMessage());
            }
        };
        attachedEmergencyQuery.addValueEventListener(emergencyListener);
    }

    private void renderRecentActivity() {
        if (recentActivityContainer == null) return;

        List<Map<String, Object>> combined = new ArrayList<>();
        for (Map<String, Object> w : waterActivities) {
            Map<String, Object> copy = new HashMap<>(w);
            copy.put("type", "water");
            combined.add(copy);
        }
        for (Map<String, Object> e : emergencyActivities) {
            Map<String, Object> copy = new HashMap<>(e);
            copy.put("type", "emergency");
            combined.add(copy);
        }

        Collections.sort(combined, (a, b) -> {
            Long t1 = (Long) a.get("timestamp");
            Long t2 = (Long) b.get("timestamp");
            return Long.compare(t2 != null ? t2 : 0, t1 != null ? t1 : 0);
        });

        recentActivityContainer.removeAllViews();

        if (combined.isEmpty()) {
            // Show a subtle empty hint
            TextView empty = new TextView(this);
            empty.setText("No recent activity yet.");
            empty.setTextColor(Color.parseColor("#A0C4D0"));
            empty.setTextSize(13f);
            empty.setPadding(8, 24, 8, 24);
            recentActivityContainer.addView(empty);
            return;
        }

        for (Map<String, Object> item : combined) {
            String type = (String) item.get("type");
            String status = (String) item.get("status");
            String date = (String) item.get("date");

            String title;
            String colorHex;
            if ("water".equals(type)) {
                title = "Water Status Update: " + (status != null ? status : "Submitted");
                colorHex = "#16A34A";
            } else {
                title = "Emergency Water Request: " + (status != null ? status : "Submitted");
                colorHex = "#CA8A04";
            }

            addActivityCard(title, "Date: " + (date != null ? date : "N/A"), colorHex);
        }
    }

    private void addActivityCard(String title, String date, String colorHex) {
        if (recentActivityContainer == null) return;

        View cardView = LayoutInflater.from(this)
                .inflate(R.layout.item_recent_activity, recentActivityContainer, false);

        TextView tvTitle = cardView.findViewById(R.id.tvActivityTitle);
        TextView tvDate = cardView.findViewById(R.id.tvActivityDate);
        ImageView ivIcon = cardView.findViewById(R.id.ivActivityIcon);

        if (tvTitle != null) tvTitle.setTag(null);
        if (tvDate != null) tvDate.setTag(null);

        if (tvTitle != null) tvTitle.setText(title);
        if (tvDate != null) tvDate.setText(date);

        try {
            if (ivIcon != null) ivIcon.setColorFilter(Color.parseColor(colorHex));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid color hex: " + colorHex);
        }

        LocaleHelper.translateAllViews(this, cardView);
        recentActivityContainer.addView(cardView);
    }

    // ============================================================
    // Delivery Feedback Card
    // ============================================================
    private void attachDeliveryFeedbackListener(String userId) {
        if (cardDeliveryFeedback == null) return;

        DatabaseReference emergencyRef = FirebaseDatabase.getInstance()
                .getReference("emergency_requests");
        attachedFeedbackQuery = emergencyRef.orderByChild("userId").equalTo(userId);

        waterStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DataSnapshot candidate = null;
                for (DataSnapshot req : snapshot.getChildren()) {
                    String status = req.child("status").getValue(String.class);
                    String feedback = req.child("memberFeedback").getValue(String.class);
                    if ("Delivered".equalsIgnoreCase(status) && feedback == null) {
                        candidate = req;
                        break;
                    }
                }
                if (candidate != null) showFeedbackCard(candidate);
                else cardDeliveryFeedback.setVisibility(View.GONE);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (cardDeliveryFeedback != null) cardDeliveryFeedback.setVisibility(View.GONE);
            }
        };

        attachedFeedbackQuery.addValueEventListener(waterStatusListener);
    }

    private void showFeedbackCard(DataSnapshot request) {
        if (cardDeliveryFeedback == null) return;

        Long deliveredAt = request.child("deliveredAt").getValue(Long.class);
        if (deliveredAt != null
                && System.currentTimeMillis() - deliveredAt > 7L * 24 * 60 * 60 * 1000) {
            cardDeliveryFeedback.setVisibility(View.GONE);
            return;
        }

        cardDeliveryFeedback.setVisibility(View.VISIBLE);

        String emergencyId = request.getKey();
        String location = request.child("location").getValue(String.class);
        Integer litres = request.child("litresDelivered").getValue(Integer.class);

        TextView tvLoc = findViewById(R.id.tvFeedbackLocation);
        TextView tvL = findViewById(R.id.tvFeedbackLitres);
        if (tvLoc != null && location != null) tvLoc.setText(location);
        if (tvL != null && litres != null)
            tvL.setText(String.format(java.util.Locale.getDefault(), "%,d L", litres));

        View btnReceived = findViewById(R.id.btnFeedbackReceived);
        View btnProblem = findViewById(R.id.btnFeedbackProblem);

        if (btnReceived != null)
            btnReceived.setOnClickListener(v -> submitMemberFeedback(emergencyId, "RECEIVED", ""));
        if (btnProblem != null)
            btnProblem.setOnClickListener(v -> showProblemDialog(emergencyId));
    }

    private void submitMemberFeedback(String emergencyId, String feedbackType, String note) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        long now = System.currentTimeMillis();
        Map<String, Object> updates = new HashMap<>();
        updates.put("memberFeedback", feedbackType);
        updates.put("memberFeedbackAt", now);
        updates.put("memberFeedbackBy", user.getUid());
        if (note != null && !note.isEmpty()) updates.put("memberFeedbackNote", note);

        if ("PROBLEM".equals(feedbackType)) updates.put("status", "Disputed");

        FirebaseDatabase.getInstance().getReference("emergency_requests")
                .child(emergencyId).updateChildren(updates)
                .addOnSuccessListener(a -> {
                    Toast.makeText(this,
                            "RECEIVED".equals(feedbackType)
                                    ? "Thank you for confirming! ✓"
                                    : "Problem reported — admin notified",
                            Toast.LENGTH_LONG).show();
                    if (cardDeliveryFeedback != null) cardDeliveryFeedback.setVisibility(View.GONE);
                    if ("PROBLEM".equals(feedbackType)) notifyAdminOfProblem(emergencyId, note);
                    else notifyDriverOfThanks(emergencyId);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showProblemDialog(String emergencyId) {
        final EditText input = new EditText(this);
        input.setHint("Describe the problem (optional)");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);
        input.setMaxLines(4);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 40; params.rightMargin = 40; params.topMargin = 20;
        input.setLayoutParams(params);
        container.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Report a Problem")
                .setMessage("The admin will be notified to investigate.")
                .setView(container)
                .setPositiveButton("Submit", (d, w) ->
                        submitMemberFeedback(emergencyId, "PROBLEM",
                                input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void notifyAdminOfProblem(String emergencyId, String note) {
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        String notifId = db.getReference("notifications").push().getKey();
        if (notifId == null) return;

        String msg = "🚨 Delivery problem reported by member\n\n" +
                "Emergency ID: " + emergencyId + "\n" +
                (note != null && !note.isEmpty() ? "Note: " + note : "No note provided");

        Map<String, Object> notif = new HashMap<>();
        notif.put("id", notifId);
        notif.put("message", msg);
        notif.put("targetAudience", "Admins");
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("type", "DELIVERY_DISPUTED");
        notif.put("relatedRequestId", emergencyId);
        notif.put("isRead", false);

        db.getReference("notifications").child(notifId).setValue(notif);
    }

    private void notifyDriverOfThanks(String emergencyId) {
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        db.getReference("emergency_requests").child(emergencyId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        String driverId = snap.child("assignedDriverId").getValue(String.class);
                        String driverName = snap.child("assignedDriverName").getValue(String.class);
                        if (driverId == null || driverId.isEmpty()) return;

                        String notifId = db.getReference("notifications").push().getKey();
                        if (notifId == null) return;

                        Map<String, Object> notif = new HashMap<>();
                        notif.put("id", notifId);
                        notif.put("message", "✅ The member confirmed they received the water. Nice work!");
                        notif.put("targetAudience", "Specific User");
                        notif.put("targetUserId", driverId);
                        notif.put("targetUserName", driverName);
                        notif.put("timestamp", System.currentTimeMillis());
                        notif.put("type", "DELIVERY_CONFIRMED");
                        notif.put("relatedRequestId", emergencyId);
                        notif.put("isRead", false);

                        db.getReference("notifications").child(notifId).setValue(notif);
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    private void flicker(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        AlphaAnimation blink = new AlphaAnimation(1.0f, 0.35f);
        blink.setDuration(300);
        blink.setRepeatMode(Animation.REVERSE);
        blink.setRepeatCount(5);
        view.startAnimation(blink);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachAllListeners();
        NotificationBadgeHelper.detach();
    }
}