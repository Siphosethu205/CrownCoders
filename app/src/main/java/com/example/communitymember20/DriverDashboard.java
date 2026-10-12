package com.example.communitymember20;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DriverDashboard extends BaseActivity {

    private static final String TAG = "DriverDashboard";
    private static final int LOCATION_PERMISSION_REQUEST = 101;
    private static final int ACTIVITY_RECOGNITION_REQUEST = 9001;
    private static final String DB_TANKS = "water_tanks";

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView ivMenu;
    private BottomNavigationView bottomNav;

    private TextView tvDriverWelcome;
    private View syncDot;
    private TextView tvSyncStatus;

    private MaterialCardView cardProfileAlert, cardEmptyAssignment;
    private TextView tvProfileAlertMessage;

    private LinearLayout layoutActionButtons;
    private MaterialButton btnChangeStatus, btnReportIssue;

    private MaterialCardView cardHero, cardStats;
    private TextView tvTankerReg, tvTankerSource, tvTankerStatusBadge;

    private LinearLayout sectionIdle, sectionJob, sectionArrived, sectionConfirmed;
    private LinearLayout tripProgressSection, otpSection;

    private TextView tvJobLabel, tvJobAddress, tvJobPriorityBadge, tvJobEta, tvJobDistance;
    private TextView tvTripDistance, tvTripSteps, tvOtpCode, tvOtpHint;
    private TextView tvArrivedCode, tvConfirmedLitres;
    private ProgressBar pbTripProgress;
    private MaterialButton btnHeroPrimary, btnHeroNavigate;

    private TextView tvStopsDone, tvWaterDelivered, tvDistanceCovered;

    private StepTracker stepTracker;
    private ValueEventListener otpListener;

    private DatabaseReference dbRef;
    private FirebaseAuth mAuth;
    private String uid;

    private TankerModel currentTanker;
    private String tankerId;
    private String activeAssignmentId;
    private String activeEmergencyRequestId;

    private boolean profileComplete = false;
    private boolean hasLicense = false;
    private boolean hasAssignedTanker = false;
    private boolean hasActiveAssignment = false;
    private boolean tripInProgress = false;
    private boolean arrivedAtDestination = false;
    private boolean otpVerified = false;

    private Double destLat = null, destLng = null;
    private Double driverLat = null, driverLng = null;
    private double destinationKm = 5.0;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean isTrackingGps = false;

    private ValueEventListener profileListener;
    private ValueEventListener assignmentsListener;
    private ValueEventListener tankerListener;
    private ValueEventListener driverLocationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driver_dashboard);

        dbRef = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            LocaleHelper.showTranslatedToast(this, "Not logged in", Toast.LENGTH_SHORT);
            finish();
            return;
        }
        uid = user.getUid();

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        ivMenu = findViewById(R.id.ivMenu);
        bottomNav = findViewById(R.id.driverBottomNav);

        tvDriverWelcome = findViewById(R.id.tvDriverWelcome);
        syncDot = findViewById(R.id.syncDot);
        tvSyncStatus = findViewById(R.id.tvSyncStatus);

        cardProfileAlert = findViewById(R.id.cardProfileAlert);
        tvProfileAlertMessage = findViewById(R.id.tvProfileAlertMessage);
        cardEmptyAssignment = findViewById(R.id.cardEmptyAssignment);

        layoutActionButtons = findViewById(R.id.layoutActionButtons);
        btnChangeStatus = findViewById(R.id.btnChangeStatus);
        btnReportIssue = findViewById(R.id.btnReportIssue);

        cardHero = findViewById(R.id.cardHero);
        cardStats = findViewById(R.id.cardStats);
        tvTankerReg = findViewById(R.id.tvTankerReg);
        tvTankerSource = findViewById(R.id.tvTankerSource);
        tvTankerStatusBadge = findViewById(R.id.tvTankerStatusBadge);

        sectionIdle = findViewById(R.id.sectionIdle);
        sectionJob = findViewById(R.id.sectionJob);
        sectionArrived = findViewById(R.id.sectionArrived);
        sectionConfirmed = findViewById(R.id.sectionConfirmed);

        tripProgressSection = findViewById(R.id.tripProgressSection);
        otpSection = findViewById(R.id.otpSection);

        tvJobLabel = findViewById(R.id.tvJobLabel);
        tvJobAddress = findViewById(R.id.tvJobAddress);
        tvJobPriorityBadge = findViewById(R.id.tvJobPriorityBadge);
        tvJobEta = findViewById(R.id.tvJobEta);
        tvJobDistance = findViewById(R.id.tvJobDistance);
        tvTripDistance = findViewById(R.id.tvTripDistance);
        tvTripSteps = findViewById(R.id.tvTripSteps);
        tvOtpCode = findViewById(R.id.tvOtpCode);
        tvOtpHint = findViewById(R.id.tvOtpHint);
        tvArrivedCode = findViewById(R.id.tvArrivedCode);
        tvConfirmedLitres = findViewById(R.id.tvConfirmedLitres);
        pbTripProgress = findViewById(R.id.pbTripProgress);

        btnHeroPrimary = findViewById(R.id.btnHeroPrimary);
        btnHeroNavigate = findViewById(R.id.btnHeroNavigate);

        tvStopsDone = findViewById(R.id.tvStopsDone);
        tvWaterDelivered = findViewById(R.id.tvWaterDelivered);
        tvDistanceCovered = findViewById(R.id.tvDistanceCovered);

        setupDrawerNavigation();
        setupBottomNav();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START);
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });

        ivMenu.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START);
            else drawerLayout.openDrawer(GravityCompat.START);
        });

        stepTracker = new StepTracker(this, new StepTracker.OnProgress() {
            @Override
            public void onStep(int totalSteps, double travelledKm, double totalKm, boolean arrived) {
                if (tvTripSteps != null) tvTripSteps.setText(totalSteps + " steps");
                if (tvTripDistance != null)
                    tvTripDistance.setText(String.format(Locale.getDefault(), "%.1f / %.1f km", travelledKm, totalKm));
                if (pbTripProgress != null) {
                    int pct = totalKm > 0 ? (int) Math.min(100, (travelledKm / totalKm) * 100) : 0;
                    pbTripProgress.setProgress(pct);
                }
            }
            @Override
            public void onArrived() {
                tripInProgress = false;
                arrivedAtDestination = true;

                // ✅ Update assignment status
                if (activeAssignmentId != null) {
                    dbRef.child("tanker_assignments").child(activeAssignmentId)
                            .child("status").setValue("Arrived");
                    dbRef.child("tanker_assignments").child(activeAssignmentId)
                            .child("arrivedAt").setValue(System.currentTimeMillis());
                }
                if (tankerId != null && !tankerId.isEmpty()) {
                    dbRef.child(DB_TANKS).child(tankerId).child("currentStatus").setValue("Arrived");
                }

                // ✅ Update the EMERGENCY REQUEST status too — this is what triggers the member's OTP card
                if (activeEmergencyRequestId != null) {
                    dbRef.child("emergency_requests").child(activeEmergencyRequestId)
                            .child("status").setValue("Arrived");
                }

                // ✅ Notify member (fetches fresh from Firebase — no race condition)
                sendMemberNotification("Arrived",
                        "🚚 Your water tanker has arrived!\n\n" +
                                "Ask the driver for their 6-digit delivery code, then enter it in the app to confirm your delivery.");

                updateHeroCard();
                LocaleHelper.showTranslatedToast(DriverDashboard.this,
                        "🚚 You've arrived! Show the code to the member.", Toast.LENGTH_LONG);
            }
        });

        btnHeroPrimary.setOnClickListener(v -> onPrimaryButtonClicked());
        btnHeroNavigate.setOnClickListener(v -> openNavigation());
        btnChangeStatus.setOnClickListener(v -> showStatusDialog());
        btnReportIssue.setOnClickListener(v -> reportIssue());
        cardProfileAlert.setOnClickListener(v -> startActivity(new Intent(this, DriverProfileSettings.class)));

        loadDriverName();
        startProfileListener();
        startAssignmentsListener();
        startDriverLocationListener();
        loadTodayStats();
        setSyncOnline();
    }

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_driver_home) return true;
            else if (id == R.id.nav_driver_alerts) { startActivity(new Intent(this, DriverNotifications.class)); return true; }
            else if (id == R.id.nav_driver_chat) { startActivity(new Intent(this, DriverChats.class)); return true; }
            else if (id == R.id.nav_driver_profile) { startActivity(new Intent(this, DriverProfileSettings.class)); return true; }
            return false;
        });
        bottomNav.setSelectedItemId(R.id.nav_driver_home);
        NotificationBadgeHelper.refresh(this, bottomNav);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav != null) NotificationBadgeHelper.refresh(this, bottomNav);
    }

    private void loadDriverName() {
        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS).child(uid).child("full_name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String name = snapshot.getValue(String.class);
                        if (name == null || name.isEmpty()) name = "Driver";
                        if (tvDriverWelcome != null) tvDriverWelcome.setText("Welcome back, " + name);
                        TextView tvName = findViewById(R.id.tvDriverName);
                        TextView tvTankerLine = findViewById(R.id.tvDriverTanker);
                        if (tvName != null) tvName.setText(name);
                        if (tvTankerLine != null) tvTankerLine.setText("Tanker Driver");
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    private void startProfileListener() {
        profileListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean complete = snapshot.child("profile_complete").getValue(Boolean.class);
                String license = snapshot.child("license_no").getValue(String.class);
                hasLicense = license != null && !license.trim().isEmpty();
                profileComplete = (complete != null && complete) && hasLicense;

                String assigned = snapshot.child("assigned_tanker").getValue(String.class);
                hasAssignedTanker = assigned != null && !assigned.isEmpty();

                if (hasAssignedTanker) {
                    tankerId = assigned;
                    loadTankerInfo(tankerId);
                }
                updateProfileAlert();
                if (!hasAssignedTanker) reverseLookupTanker();
                updateDashboard();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS).child(uid)
                .addValueEventListener(profileListener);
    }

    private void updateProfileAlert() {
        if (cardProfileAlert == null) return;
        if (!hasLicense && !hasAssignedTanker) {
            cardProfileAlert.setVisibility(View.VISIBLE);
            if (tvProfileAlertMessage != null)
                tvProfileAlertMessage.setText("Add your driver's license to start receiving assignments.");
        } else {
            cardProfileAlert.setVisibility(View.GONE);
        }
    }

    private void startAssignmentsListener() {
        if (assignmentsListener == null) {
            assignmentsListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String previousAssignmentId = activeAssignmentId;

                    hasActiveAssignment = false;
                    activeAssignmentId = null;
                    activeEmergencyRequestId = null;
                    destLat = null;
                    destLng = null;

                    String bestId = null;
                    long latest = 0;
                    String bestEmergency = null;
                    String bestTanker = null;
                    String bestAddress = null;
                    String bestUrgency = null;
                    String bestStatus = null;

                    for (DataSnapshot a : snapshot.getChildren()) {
                        String driverId = a.child("driverId").getValue(String.class);
                        if (driverId == null || !driverId.equals(uid)) continue;

                        String status = a.child("status").getValue(String.class);
                        if ("Delivered".equalsIgnoreCase(status)
                                || "Confirmed".equalsIgnoreCase(status)
                                || "Cancelled".equalsIgnoreCase(status)) continue;

                        Long at = a.child("assignedAt").getValue(Long.class);
                        long t = at != null ? at : 0;
                        if (t > latest) {
                            latest = t;
                            bestId = a.getKey();
                            bestEmergency = a.child("emergencyRequestId").getValue(String.class);
                            bestTanker = a.child("tankerId").getValue(String.class);
                            bestAddress = a.child("emergencyRequestLocation").getValue(String.class);
                            bestUrgency = a.child("emergencyRequestUrgency").getValue(String.class);
                            bestStatus = status;
                        }
                    }

                    if (bestId != null) {
                        hasActiveAssignment = true;
                        activeAssignmentId = bestId;
                        activeEmergencyRequestId = bestEmergency;

                        if (!bestId.equals(previousAssignmentId)) {
                            tripInProgress = false;
                            arrivedAtDestination = false;
                            otpVerified = false;
                        }

                        if (bestStatus != null) {
                            if ("En Route".equalsIgnoreCase(bestStatus)) { tripInProgress = true; arrivedAtDestination = false; }
                            else if ("Arrived".equalsIgnoreCase(bestStatus)) { tripInProgress = false; arrivedAtDestination = true; }
                            else if ("Assigned".equalsIgnoreCase(bestStatus)) { tripInProgress = false; arrivedAtDestination = false; }
                        }

                        if ((!hasAssignedTanker || tankerId == null)
                                && bestTanker != null && !bestTanker.isEmpty()) {
                            tankerId = bestTanker;
                            hasAssignedTanker = true;
                            updateProfileAlert();
                        }

                        if (bestAddress != null) tvJobAddress.setText(bestAddress);
                        if (bestUrgency != null) {
                            tvJobPriorityBadge.setText(bestUrgency.toUpperCase() + " PRIORITY");
                            if ("Extreme".equalsIgnoreCase(bestUrgency)) {
                                tvJobPriorityBadge.setBackgroundResource(R.drawable.bg_pill_red);
                                tvJobPriorityBadge.setTextColor(0xFFDC2626);
                            } else if ("Critical".equalsIgnoreCase(bestUrgency)) {
                                tvJobPriorityBadge.setBackgroundResource(R.drawable.bg_pill_amber);
                                tvJobPriorityBadge.setTextColor(0xFFC2410C);
                            } else {
                                tvJobPriorityBadge.setBackgroundResource(R.drawable.bg_pill_sync);
                                tvJobPriorityBadge.setTextColor(0xFF0F766E);
                            }
                        }

                        if (bestEmergency != null) loadEmergencyDetails(bestEmergency);
                        if (tankerId != null && !tankerId.isEmpty()) loadTankerInfo(tankerId);
                        attachOtpListener(bestId);
                    } else {
                        tripInProgress = false;
                        arrivedAtDestination = false;
                        otpVerified = false;
                    }
                    updateDashboard();
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) { }
            };
        }
        dbRef.child("tanker_assignments").addValueEventListener(assignmentsListener);
    }

    private void loadEmergencyDetails(String emergencyId) {
        dbRef.child("emergency_requests").child(emergencyId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        destLat = snap.child("latitude").getValue(Double.class);
                        destLng = snap.child("longitude").getValue(Double.class);

                        if (destLat != null && destLng != null && driverLat != null && driverLng != null) {
                            destinationKm = haversine(driverLat, driverLng, destLat, destLng);
                            if (destinationKm < 1.0) destinationKm = 1.0;
                        }
                        updateDistanceToDestination();
                        updateHeroCard();
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    private void attachOtpListener(String assignmentId) {
        if (otpListener != null && activeAssignmentId != null) {
            dbRef.child("tanker_assignments").child(activeAssignmentId).removeEventListener(otpListener);
        }

        otpListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) return;

                String otp = snap.child("deliveryOtp").getValue(String.class);
                Boolean verified = snap.child("otpVerified").getValue(Boolean.class);
                String status = snap.child("status").getValue(String.class);
                otpVerified = verified != null && verified;

                if (otp != null) {
                    StringBuilder f = new StringBuilder();
                    for (int i = 0; i < otp.length(); i++) {
                        if (i > 0) f.append(" ");
                        f.append(otp.charAt(i));
                    }
                    String formatted = f.toString();
                    if (tvOtpCode != null) tvOtpCode.setText(formatted);
                    if (tvArrivedCode != null) tvArrivedCode.setText(formatted);
                }

                if (status != null) {
                    if ("En Route".equalsIgnoreCase(status)) { tripInProgress = true; arrivedAtDestination = false; }
                    else if ("Arrived".equalsIgnoreCase(status)) { tripInProgress = false; arrivedAtDestination = true; }
                }

                if (otpVerified) {
                    if (stepTracker != null) stepTracker.stop();
                    tripInProgress = false;
                    stopGpsTracking();
                }
                updateHeroCard();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };

        dbRef.child("tanker_assignments").child(assignmentId).addValueEventListener(otpListener);
    }

    private void startDriverLocationListener() {
        if (driverLocationListener != null) return;
        driverLocationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                Double lat = snapshot.child("latitude").getValue(Double.class);
                Double lng = snapshot.child("longitude").getValue(Double.class);
                if (lat == null || lng == null) return;
                driverLat = lat;
                driverLng = lng;
                updateDistanceToDestination();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        dbRef.child("tanker_locations").child(uid).addValueEventListener(driverLocationListener);
    }

    private void updateDistanceToDestination() {
        if (destLat == null || destLng == null) {
            if (tvJobEta != null) tvJobEta.setText("— ETA");
            if (tvJobDistance != null) tvJobDistance.setText("— away");
            return;
        }
        if (driverLat == null || driverLng == null) return;

        double km = haversine(driverLat, driverLng, destLat, destLng);
        if (tvJobDistance != null)
            tvJobDistance.setText(String.format(Locale.getDefault(), "%.1f km away", km));

        double etaMin = (km / 40.0) * 60.0;
        if (tvJobEta != null) {
            if (etaMin < 1) tvJobEta.setText("Arriving");
            else if (etaMin < 60) tvJobEta.setText(String.format(Locale.getDefault(), "%d min ETA", (int) etaMin));
            else tvJobEta.setText(String.format(Locale.getDefault(), "%dh %dm ETA",
                        (int)(etaMin/60), (int)(etaMin%60)));
        }
    }

    private void reverseLookupTanker() {
        dbRef.child(DB_TANKS).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot t : snapshot.getChildren()) {
                    String driverId = t.child("assignedDriverId").getValue(String.class);
                    if (driverId != null && driverId.equals(uid)) {
                        tankerId = t.getKey();
                        hasAssignedTanker = true;
                        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS)
                                .child(uid).child("assigned_tanker").setValue(tankerId);
                        updateProfileAlert();
                        loadTankerInfo(tankerId);
                        updateDashboard();
                        return;
                    }
                }
                updateDashboard();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { updateDashboard(); }
        });
    }

    private void loadTodayStats() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();

        dbRef.child("tanker_assignments").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int stops = 0, litres = 0, active = 0;
                for (DataSnapshot a : snapshot.getChildren()) {
                    String driverId = a.child("driverId").getValue(String.class);
                    if (driverId == null || !driverId.equals(uid)) continue;
                    String status = a.child("status").getValue(String.class);

                    if ("Delivered".equalsIgnoreCase(status) || "Confirmed".equalsIgnoreCase(status)) {
                        Long completedAt = a.child("completedAt").getValue(Long.class);
                        long completed = completedAt != null ? completedAt : 0;
                        if (completed >= startOfDay) {
                            stops++;
                            Integer l = a.child("litresDelivered").getValue(Integer.class);
                            if (l != null) litres += l;
                        }
                    } else if (!"Cancelled".equalsIgnoreCase(status) && !"Disputed".equalsIgnoreCase(status)) {
                        active++;
                    }
                }
                tvStopsDone.setText(String.format(Locale.getDefault(), "%d/%d", stops, stops + active));
                tvWaterDelivered.setText(String.format(Locale.getDefault(), "%,d L", litres));
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void updateDashboard() {
        if (hasAssignedTanker) {
            cardHero.setVisibility(View.VISIBLE);
            cardStats.setVisibility(View.VISIBLE);
            cardEmptyAssignment.setVisibility(View.GONE);
            layoutActionButtons.setVisibility(View.VISIBLE);
            updateHeroCard();
        } else {
            cardHero.setVisibility(View.GONE);
            cardStats.setVisibility(View.GONE);
            cardEmptyAssignment.setVisibility(View.VISIBLE);
            layoutActionButtons.setVisibility(View.GONE);
        }
    }

    private void updateHeroCard() {
        if (cardHero == null || cardHero.getVisibility() != View.VISIBLE) return;

        sectionIdle.setVisibility(View.GONE);
        sectionJob.setVisibility(View.GONE);
        sectionArrived.setVisibility(View.GONE);
        sectionConfirmed.setVisibility(View.GONE);

        if (hasActiveAssignment && otpVerified) {
            sectionConfirmed.setVisibility(View.VISIBLE);
            if (tvConfirmedLitres != null) tvConfirmedLitres.setText("Ready for the next assignment");
            return;
        }

        if (hasActiveAssignment && arrivedAtDestination && !otpVerified) {
            sectionArrived.setVisibility(View.VISIBLE);
            return;
        }

        if (hasActiveAssignment) {
            sectionJob.setVisibility(View.VISIBLE);
            if (tripInProgress) {
                if (tvJobLabel != null) tvJobLabel.setText("ON THE WAY");
                tripProgressSection.setVisibility(View.VISIBLE);
                otpSection.setVisibility(View.VISIBLE);
                if (btnHeroPrimary != null) { btnHeroPrimary.setText("Stop Trip"); btnHeroPrimary.setIconResource(android.R.drawable.ic_media_pause); }
                if (btnHeroNavigate != null) btnHeroNavigate.setVisibility(View.VISIBLE);
            } else {
                if (tvJobLabel != null) tvJobLabel.setText("NEXT DELIVERY");
                tripProgressSection.setVisibility(View.GONE);
                otpSection.setVisibility(View.VISIBLE);
                if (btnHeroPrimary != null) { btnHeroPrimary.setText("Start Trip"); btnHeroPrimary.setIconResource(android.R.drawable.ic_media_play); }
                if (btnHeroNavigate != null) btnHeroNavigate.setVisibility(View.VISIBLE);
            }
            return;
        }
        sectionIdle.setVisibility(View.VISIBLE);
    }

    private void onPrimaryButtonClicked() {
        if (!hasActiveAssignment) return;
        if (tripInProgress) {
            if (stepTracker != null) stepTracker.stop();
            tripInProgress = false;
            if (activeAssignmentId != null) dbRef.child("tanker_assignments").child(activeAssignmentId).child("status").setValue("Assigned");
            updateHeroCard();
            LocaleHelper.showTranslatedToast(this, "Trip paused", Toast.LENGTH_SHORT);
        } else {
            promptLitresThenStart();
        }
    }

    private void promptLitresThenStart() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("e.g. 10000");
        if (currentTanker != null && currentTanker.capacity > 0) {
            input.setText(String.valueOf(currentTanker.capacity));
        }
        input.setSelectAllOnFocus(true);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 60; params.rightMargin = 60; params.topMargin = 20;
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("How many litres are you loading?")
                .setMessage("Enter the amount of water you're carrying for this delivery.")
                .setView(container)
                .setPositiveButton("Start Trip", (d, w) -> {
                    String s = input.getText().toString().trim();
                    if (s.isEmpty()) s = "0";
                    int litres;
                    try { litres = Integer.parseInt(s); } catch (Exception e) { litres = 0; }
                    saveLitresAndStart(litres);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveLitresAndStart(int litres) {
        if (activeAssignmentId != null) {
            dbRef.child("tanker_assignments").child(activeAssignmentId)
                    .child("litresLoaded").setValue(litres);
        }
        if (tankerId != null && !tankerId.isEmpty()) {
            dbRef.child(DB_TANKS).child(tankerId).child("currentLoad").setValue(litres);
        }
        startTrip(litres);
    }

    private void startTrip(int litres) {
        if (activeAssignmentId == null) return;

        if (stepTracker.isSensorAvailable() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                        ACTIVITY_RECOGNITION_REQUEST);
                return;
            }
        }

        double effectiveDestLat = destLat != null ? destLat : 0.0;
        double effectiveDestLng = destLng != null ? destLng : 0.0;

        double startLat, startLng;
        if (driverLat != null && driverLng != null && destLat != null && destLng != null
                && haversine(driverLat, driverLng, destLat, destLng) > 0.5) {
            startLat = driverLat; startLng = driverLng;
        } else {
            startLat = effectiveDestLat - 0.045;
            startLng = effectiveDestLng - 0.030;
        }

        tripInProgress = true;
        arrivedAtDestination = false;
        updateHeroCard();

        // ✅ Update tanker + assignment + emergency request status
        if (tankerId != null && !tankerId.isEmpty()) {
            dbRef.child(DB_TANKS).child(tankerId).child("currentStatus").setValue("En Route");
        }
        dbRef.child("tanker_assignments").child(activeAssignmentId).child("status").setValue("En Route");

        if (activeEmergencyRequestId != null) {
            dbRef.child("emergency_requests").child(activeEmergencyRequestId)
                    .child("status").setValue("En Route");
        }

        stepTracker.start(uid, tankerId, activeAssignmentId, startLat, startLng, effectiveDestLat, effectiveDestLng);

        // ✅ Notify member (fetches fresh from Firebase)
        sendMemberNotification("En Route",
                "🚚 Your water tanker is on the way!\n\n" +
                        "Litres loaded: " + String.format(Locale.getDefault(), "%,d L", litres) + "\n\n" +
                        "Track the driver in the app.");

        LocaleHelper.showTranslatedToast(this, "Trip started!", Toast.LENGTH_SHORT);
    }

    // ✅ Fetch member info FRESH from Firebase to avoid race conditions
    private void sendMemberNotification(String type, String message) {
        if (activeEmergencyRequestId == null || activeEmergencyRequestId.isEmpty()) {
            Log.w(TAG, "Cannot notify — no active emergency request ID");
            return;
        }

        dbRef.child("emergency_requests").child(activeEmergencyRequestId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        String userId = snap.child("userId").getValue(String.class);
                        String userName = snap.child("name").getValue(String.class);
                        if (userId == null || userId.isEmpty()) {
                            Log.w(TAG, "Cannot notify — emergency has no userId");
                            return;
                        }

                        String notifId = dbRef.child("notifications").push().getKey();
                        if (notifId == null) return;

                        Map<String, Object> notif = new HashMap<>();
                        notif.put("id", notifId);
                        notif.put("message", message);
                        notif.put("targetAudience", "Specific User");
                        notif.put("targetUserId", userId);
                        notif.put("targetUserName", userName);
                        notif.put("timestamp", System.currentTimeMillis());
                        notif.put("type", "EMERGENCY_UPDATE");
                        notif.put("relatedRequestId", activeEmergencyRequestId);
                        notif.put("isRead", false);

                        dbRef.child("notifications").child(notifId).setValue(notif)
                                .addOnSuccessListener(a -> Log.d(TAG, "✓ Member notified (" + type + ")"))
                                .addOnFailureListener(e -> Log.e(TAG, "✗ Notify failed: " + e.getMessage()));
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    private void openNavigation() {
        String target = tvJobAddress.getText() != null ? tvJobAddress.getText().toString().trim() : "";
        if (target.isEmpty()) { LocaleHelper.showTranslatedToast(this, "No destination available", Toast.LENGTH_SHORT); return; }

        if (tankerId != null && !tankerId.isEmpty())
            dbRef.child(DB_TANKS).child(tankerId).child("currentStatus").setValue("En Route");
        if (activeAssignmentId != null)
            dbRef.child("tanker_assignments").child(activeAssignmentId).child("status").setValue("En Route");

        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(target));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) startActivity(mapIntent);
        else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(target))));
    }

    private void loadTankerInfo(String tid) {
        if (tankerListener != null && tankerId != null)
            dbRef.child(DB_TANKS).child(tankerId).removeEventListener(tankerListener);
        tankerId = tid;

        tankerListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentTanker = snapshot.getValue(TankerModel.class);
                if (currentTanker == null) return;

                tvTankerReg.setText(currentTanker.registration != null ? currentTanker.registration : "—");
                tvTankerSource.setText(currentTanker.sourceName != null ? "Filling at " + currentTanker.sourceName : "—");

                String status = currentTanker.currentStatus != null ? currentTanker.currentStatus.toUpperCase() : "IDLE";
                tvTankerStatusBadge.setText(status);

                switch (status) {
                    case "EN ROUTE": tvTankerStatusBadge.setBackgroundResource(R.drawable.bg_pill_amber); tvTankerStatusBadge.setTextColor(0xFFC2410C); break;
                    default: tvTankerStatusBadge.setBackgroundResource(R.drawable.bg_pill_sync); tvTankerStatusBadge.setTextColor(0xFF0F766E);
                }
                startGpsTracking();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        dbRef.child(DB_TANKS).child(tid).addValueEventListener(tankerListener);
    }

    private void showStatusDialog() {
        if (currentTanker == null || tankerId == null) { LocaleHelper.showTranslatedToast(this, "No tanker assigned", Toast.LENGTH_SHORT); return; }
        final String[] statuses = {"Filling", "En Route", "Idle", "Arrived"};
        int checked = 0;
        for (int i = 0; i < statuses.length; i++) if (statuses[i].equalsIgnoreCase(currentTanker.currentStatus)) { checked = i; break; }
        new AlertDialog.Builder(this)
                .setTitle("Update Tanker Status")
                .setSingleChoiceItems(statuses, checked, (dialog, which) -> {
                    String newStatus = statuses[which];
                    dbRef.child(DB_TANKS).child(tankerId).child("currentStatus").setValue(newStatus)
                            .addOnSuccessListener(a -> { LocaleHelper.showTranslatedToast(this, "Status: " + newStatus, Toast.LENGTH_SHORT); dialog.dismiss(); });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void reportIssue() {
        final String[] issues = {"Road blocked", "Tanker empty", "Mechanical fault", "Other"};
        new AlertDialog.Builder(this)
                .setTitle("Report Issue")
                .setItems(issues, (d, which) -> {
                    String id = dbRef.child("driver_issues").push().getKey();
                    if (id != null) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("issue", issues[which]);
                        map.put("tankerId", tankerId);
                        map.put("driverId", uid);
                        map.put("assignmentId", activeAssignmentId);
                        map.put("timestamp", System.currentTimeMillis());
                        dbRef.child("driver_issues").child(id).setValue(map)
                                .addOnSuccessListener(a -> LocaleHelper.showTranslatedToast(this, "Issue reported", Toast.LENGTH_SHORT));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupDrawerNavigation() {
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_driver_issue) reportIssue();
            else if (id == R.id.nav_driver_profile) startActivity(new Intent(DriverDashboard.this, DriverProfileSettings.class));
            else if (id == R.id.nav_driver_logout) showLogoutDialog();
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out").setMessage("Log out of AquaComm?")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    if (stepTracker != null) stepTracker.stop();
                    stopGpsTracking();
                    mAuth.signOut();
                    Intent i = new Intent(this, LoginPage.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i); finish();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void setSyncOnline() {
        if (syncDot != null) syncDot.setBackgroundResource(R.drawable.bg_dot_green);
        if (tvSyncStatus != null) { tvSyncStatus.setText("Online"); tvSyncStatus.setTextColor(0xFF00A3B8); }
    }

    private void startGpsTracking() {
        if (isTrackingGps) return;
        if (tankerId == null || tankerId.isEmpty()) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15000).setMinUpdateIntervalMillis(5000).build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location loc = locationResult.getLastLocation();
                if (loc != null) {
                    driverLat = loc.getLatitude(); driverLng = loc.getLongitude();
                    if (stepTracker == null || !stepTracker.isRunning()) pushGpsToFirebase(loc);
                    updateDistanceToDestination();
                }
            }
        };
        try { fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper()); isTrackingGps = true; }
        catch (SecurityException e) { Log.e(TAG, "GPS error: " + e.getMessage()); }
    }

    private void stopGpsTracking() {
        if (!isTrackingGps || fusedLocationClient == null || locationCallback == null) return;
        fusedLocationClient.removeLocationUpdates(locationCallback);
        isTrackingGps = false;
    }

    private void pushGpsToFirebase(Location loc) {
        if (uid == null) return;
        Map<String, Object> d = new HashMap<>();
        d.put("driverId", uid); d.put("tankerId", tankerId);
        d.put("latitude", loc.getLatitude()); d.put("longitude", loc.getLongitude());
        d.put("speed", loc.getSpeed());
        d.put("lastUpdated", System.currentTimeMillis());
        if (activeAssignmentId != null) d.put("assignmentId", activeAssignmentId);
        dbRef.child("tanker_locations").child(uid).setValue(d);
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c;
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(rc, p, g);
        if (rc == ACTIVITY_RECOGNITION_REQUEST) {
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) promptLitresThenStart();
            else LocaleHelper.showTranslatedToast(this, "Activity recognition needed.", Toast.LENGTH_LONG);
            return;
        }
        if (rc == LOCATION_PERMISSION_REQUEST && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) startGpsTracking();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stepTracker != null) stepTracker.stop();
        stopGpsTracking();
        if (profileListener != null) dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS).child(uid).removeEventListener(profileListener);
        if (assignmentsListener != null) dbRef.child("tanker_assignments").removeEventListener(assignmentsListener);
        if (tankerListener != null && tankerId != null) dbRef.child(DB_TANKS).child(tankerId).removeEventListener(tankerListener);
        if (driverLocationListener != null) dbRef.child("tanker_locations").child(uid).removeEventListener(driverLocationListener);
        if (otpListener != null && activeAssignmentId != null) dbRef.child("tanker_assignments").child(activeAssignmentId).removeEventListener(otpListener);
    }
}