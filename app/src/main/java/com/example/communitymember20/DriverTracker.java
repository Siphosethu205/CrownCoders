package com.example.communitymember20;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.Locale;

public class DriverTracker extends BaseActivity {

    private static final String TAG = "DriverTracker";

    private LinearLayout layoutEmpty, layoutContent;
    private TextView tvEtaCountdown, tvEtaSubtitle, tvDistance, tvSpeed;
    private TextView tvDriverInitial, tvDriverName, tvTankerReg, tvCapacity, tvLoaded, tvStatus;
    private TextView tvLastUpdate;
    private CardView btnRecenterMap;
    private MaterialButton btnCallDriver, btnNotifyClose, btnRefreshStatus;

    private MapView mapView;
    private Marker tankerMarker;
    private Marker destinationMarker;
    private boolean hasCenteredOnce = false;

    private DatabaseReference dbRef;
    private String uid;

    private String emergencyRequestId;
    private String activeAssignmentId;
    private String activeDriverId;
    private String activeTankerId;
    private String activeDriverPhone;
    private Double destLat;
    private Double destLng;

    private ValueEventListener requestListener;
    private ValueEventListener assignmentListener;
    private ValueEventListener locationListener;
    private ValueEventListener tankerListener;

    private boolean notifyWhenClose = false;
    private boolean closeNotified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(this,
                android.preference.PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(
                "AquaComm/1.0 (student.project@aquacom.co.za)");
        Configuration.getInstance().setOsmdroidTileCache(getCacheDir());

        setContentView(R.layout.driver_tracker);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        layoutEmpty = findViewById(R.id.layoutEmpty);
        layoutContent = findViewById(R.id.layoutContent);

        tvEtaCountdown = findViewById(R.id.tvEtaCountdown);
        tvEtaSubtitle = findViewById(R.id.tvEtaSubtitle);
        tvDistance = findViewById(R.id.tvDistance);
        tvSpeed = findViewById(R.id.tvSpeed);

        tvDriverInitial = findViewById(R.id.tvDriverInitial);
        tvDriverName = findViewById(R.id.tvDriverName);
        tvTankerReg = findViewById(R.id.tvTankerReg);
        tvCapacity = findViewById(R.id.tvCapacity);
        tvLoaded = findViewById(R.id.tvLoaded);
        tvStatus = findViewById(R.id.tvStatus);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);

        btnRecenterMap = findViewById(R.id.btnRecenterMap);
        btnCallDriver = findViewById(R.id.btnCallDriver);
        btnNotifyClose = findViewById(R.id.btnNotifyClose);
        btnRefreshStatus = findViewById(R.id.btnRefreshStatus);

        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(new XYTileSource(
                "ArcGIS",
                1, 19, 256, ".png",
                new String[]{
                        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/"
                }
        ));
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(13.0);
        mapView.getController().setCenter(new GeoPoint(-26.2041, 28.0473));

        dbRef = FirebaseDatabase.getInstance().getReference();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        uid = user.getUid();

        btnRecenterMap.setOnClickListener(v -> {
            if (tankerMarker != null && tankerMarker.getPosition() != null) {
                mapView.getController().animateTo(tankerMarker.getPosition());
            } else if (destinationMarker != null && destinationMarker.getPosition() != null) {
                mapView.getController().animateTo(destinationMarker.getPosition());
            } else {
                Toast.makeText(this, "Waiting for GPS signal", Toast.LENGTH_SHORT).show();
            }
        });

        btnCallDriver.setOnClickListener(v -> {
            if (activeDriverPhone != null && !activeDriverPhone.isEmpty()) {
                startActivity(new Intent(Intent.ACTION_DIAL,
                        Uri.parse("tel:" + activeDriverPhone)));
            } else {
                Toast.makeText(this, "Driver phone not available", Toast.LENGTH_SHORT).show();
            }
        });

        btnNotifyClose.setOnClickListener(v -> {
            notifyWhenClose = !notifyWhenClose;
            closeNotified = false;
            btnNotifyClose.setText(notifyWhenClose ? "Alert On ✓" : "Notify When Close");
            Toast.makeText(this,
                    notifyWhenClose
                            ? "You'll be alerted when the tanker is under 500m away"
                            : "Alert turned off",
                    Toast.LENGTH_SHORT).show();
        });

        btnRefreshStatus.setOnClickListener(v -> {
            if (tankerMarker != null && tankerMarker.getPosition() != null) {
                mapView.getController().animateTo(tankerMarker.getPosition());
                Toast.makeText(this, "Recentred on tanker", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No GPS data yet", Toast.LENGTH_SHORT).show();
            }
        });

        findMyActiveRequest();
    }

    private void findMyActiveRequest() {
        requestListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                EmergencyRequestModel best = null;
                long bestTs = 0;
                String bestId = null;

                for (DataSnapshot req : snapshot.getChildren()) {
                    String status = req.child("status").getValue(String.class);
                    if ("Delivered".equalsIgnoreCase(status)
                            || "Resolved".equalsIgnoreCase(status)) {
                        continue;
                    }
                    Long ts = req.child("timestamp").getValue(Long.class);
                    long t = ts != null ? ts : 0;
                    if (t > bestTs) {
                        bestTs = t;
                        bestId = req.getKey();
                        best = req.getValue(EmergencyRequestModel.class);
                    }
                }

                if (best == null || bestId == null) {
                    showEmptyState();
                    return;
                }

                emergencyRequestId = bestId;
                destLat = best.latitude;
                destLng = best.longitude;

                showContent();

                String driverId = best.assignedDriverId;
                String assignmentId = best.assignedAssignmentId;
                String driverName = best.assignedDriverName;

                if (driverId == null || driverId.isEmpty()) {
                    tvEtaCountdown.setText("Awaiting driver");
                    tvEtaSubtitle.setText("The admin will assign a water tanker shortly");
                    tvDriverName.setText("Not assigned yet");
                    tvTankerReg.setText("—");
                    tvStatus.setText("PENDING");
                    return;
                }

                activeDriverId = driverId;
                activeAssignmentId = assignmentId;

                if (driverName != null && !driverName.isEmpty()) {
                    tvDriverName.setText(driverName);
                    tvDriverInitial.setText(
                            String.valueOf(driverName.charAt(0)).toUpperCase());
                }

                loadDriverDetails(driverId);
                if (assignmentId != null && !assignmentId.isEmpty()) {
                    loadAssignment(assignmentId);
                }
                startLocationTracking(driverId);

                if (destLat != null && destLng != null) {
                    drawDestinationPin();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showEmptyState();
            }
        };

        dbRef.child("emergency_requests")
                .orderByChild("userId").equalTo(uid)
                .addValueEventListener(requestListener);
    }

    // ✅ FIXED: read from users/members/{driverId}
    private void loadDriverDetails(String driverId) {
        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS).child(driverId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        activeDriverPhone = snap.child("phone_number").getValue(String.class);
                        String tankerId = snap.child("assigned_tanker").getValue(String.class);
                        activeTankerId = tankerId;
                        if (tankerId != null && !tankerId.isEmpty()) {
                            attachTankerListener(tankerId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    private void loadAssignment(String assignmentId) {
        if (assignmentListener != null) return;

        assignmentListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) return;

                String tankerReg = snap.child("tankerReg").getValue(String.class);
                String status = snap.child("status").getValue(String.class);
                String driverName = snap.child("driverName").getValue(String.class);
                Integer cap = snap.child("tankerCapacity").getValue(Integer.class);
                Integer loaded = snap.child("litresLoaded").getValue(Integer.class);
                Long lastUpd = snap.child("lastUpdateAt").getValue(Long.class);

                if (tankerReg != null) tvTankerReg.setText(tankerReg);
                if (status != null) tvStatus.setText(status.toUpperCase());
                if (driverName != null && !driverName.isEmpty()) {
                    tvDriverName.setText(driverName);
                    tvDriverInitial.setText(
                            String.valueOf(driverName.charAt(0)).toUpperCase());
                }
                if (cap != null && cap > 0) {
                    tvCapacity.setText(String.format(Locale.getDefault(), "%,d L", cap));
                }
                if (loaded != null) {
                    tvLoaded.setText(String.format(Locale.getDefault(), "%,d L", loaded));
                }
                if (lastUpd != null && lastUpd > 0) {
                    tvLastUpdate.setText("Updated " + getTimeAgo(lastUpd));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };

        dbRef.child("tanker_assignments").child(assignmentId)
                .addValueEventListener(assignmentListener);
    }

    private void startLocationTracking(String driverId) {
        if (locationListener != null) return;

        locationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) return;

                Double lat = snap.child("latitude").getValue(Double.class);
                Double lng = snap.child("longitude").getValue(Double.class);
                Float speed = snap.child("speed").getValue(Float.class);
                Long lastUpd = snap.child("lastUpdated").getValue(Long.class);

                if (lat == null || lng == null) return;

                GeoPoint pos = new GeoPoint(lat, lng);

                if (tankerMarker == null) {
                    tankerMarker = new Marker(mapView);
                    tankerMarker.setPosition(pos);
                    tankerMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    tankerMarker.setTitle("Water Tanker");
                    tankerMarker.setIcon(
                            getDrawable(android.R.drawable.ic_menu_directions));
                    mapView.getOverlays().add(tankerMarker);
                } else {
                    tankerMarker.setPosition(pos);
                }

                mapView.invalidate();

                if (!hasCenteredOnce) {
                    mapView.getController().animateTo(pos);
                    mapView.getController().setZoom(15.0);
                    hasCenteredOnce = true;
                }

                if (speed != null && speed > 0.5) {
                    tvSpeed.setText(String.format(Locale.getDefault(),
                            "%.0f km/h", speed * 3.6));
                } else {
                    tvSpeed.setText("Stationary");
                }

                if (destLat != null && destLng != null) {
                    double km = haversine(pos.getLatitude(), pos.getLongitude(),
                            destLat, destLng);
                    tvDistance.setText(String.format(Locale.getDefault(), "%.1f km", km));

                    if (notifyWhenClose && !closeNotified && km <= 0.5) {
                        closeNotified = true;
                        notifyWhenClose = false;
                        btnNotifyClose.setText("Notify When Close");
                        Toast.makeText(DriverTracker.this,
                                "The water tanker is within 500m!",
                                Toast.LENGTH_LONG).show();
                    }

                    double etaMin = (km / 40.0) * 60.0;
                    if (etaMin < 1) {
                        tvEtaCountdown.setText("Arriving now");
                        tvEtaSubtitle.setText("The tanker is at your location");
                    } else if (etaMin < 60) {
                        tvEtaCountdown.setText(String.format(Locale.getDefault(),
                                "%d min", (int) etaMin));
                        tvEtaSubtitle.setText("Estimated arrival time");
                    } else {
                        int h = (int) (etaMin / 60);
                        int m = (int) (etaMin % 60);
                        tvEtaCountdown.setText(String.format(Locale.getDefault(),
                                "%dh %dm", h, m));
                        tvEtaSubtitle.setText("Estimated arrival time");
                    }
                } else {
                    tvEtaCountdown.setText("Live");
                    tvEtaSubtitle.setText("Driver is moving — position updating live");
                    tvDistance.setText("—");
                }

                if (lastUpd != null && lastUpd > 0) {
                    tvLastUpdate.setText("Updated " + getTimeAgo(lastUpd));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };

        dbRef.child("tanker_locations").child(driverId)
                .addValueEventListener(locationListener);
    }

    private void attachTankerListener(String tankerId) {
        if (tankerListener != null) return;

        tankerListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                Integer cap = snap.child("capacity").getValue(Integer.class);
                Integer load = snap.child("currentLoad").getValue(Integer.class);
                String status = snap.child("currentStatus").getValue(String.class);
                String reg = snap.child("registration").getValue(String.class);

                if (cap != null && cap > 0) {
                    tvCapacity.setText(String.format(Locale.getDefault(), "%,d L", cap));
                }
                if (load != null) {
                    tvLoaded.setText(String.format(Locale.getDefault(), "%,d L", load));
                }
                if (status != null) tvStatus.setText(status.toUpperCase());
                if (reg != null) tvTankerReg.setText(reg);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };

        dbRef.child("water_tanks").child(tankerId)
                .addValueEventListener(tankerListener);
    }

    private void drawDestinationPin() {
        if (destLat == null || destLng == null) return;
        if (destinationMarker != null) return;

        destinationMarker = new Marker(mapView);
        destinationMarker.setPosition(new GeoPoint(destLat, destLng));
        destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        destinationMarker.setTitle("Your location");
        destinationMarker.setIcon(
                getDrawable(android.R.drawable.ic_menu_myplaces));
        mapView.getOverlays().add(destinationMarker);
        mapView.invalidate();
    }

    private void showEmptyState() {
        layoutEmpty.setVisibility(View.VISIBLE);
        layoutContent.setVisibility(View.GONE);
    }

    private void showContent() {
        layoutEmpty.setVisibility(View.GONE);
        layoutContent.setVisibility(View.VISIBLE);
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c;
    }

    private String getTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long secs = diff / 1000;
        if (secs < 10) return "just now";
        if (secs < 60) return secs + "s ago";
        long mins = secs / 60;
        if (mins < 60) return mins + "m ago";
        long hours = mins / 60;
        if (hours < 24) return hours + "h ago";
        return (hours / 24) + "d ago";
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mapView != null) mapView.onDetach();

        if (requestListener != null && uid != null) {
            dbRef.child("emergency_requests")
                    .orderByChild("userId").equalTo(uid)
                    .removeEventListener(requestListener);
        }
        if (assignmentListener != null && activeAssignmentId != null) {
            dbRef.child("tanker_assignments").child(activeAssignmentId)
                    .removeEventListener(assignmentListener);
        }
        if (locationListener != null && activeDriverId != null) {
            dbRef.child("tanker_locations").child(activeDriverId)
                    .removeEventListener(locationListener);
        }
        if (tankerListener != null && activeTankerId != null) {
            dbRef.child("water_tanks").child(activeTankerId)
                    .removeEventListener(tankerListener);
        }
    }
}