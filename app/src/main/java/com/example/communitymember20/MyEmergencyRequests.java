package com.example.communitymember20;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class MyEmergencyRequests extends BaseActivity {

    private LinearLayout requestsContainer;
    private LinearLayout layoutEmptyState;
    private TextView tvEmptyState;
    private ProgressBar progressBar;
    private MaterialToolbar topAppBar;

    private FirebaseAuth mAuth;
    private DatabaseReference emergencyRef;

    private ValueEventListener liveListener;

    // Track last seen statuses so we can flicker on change
    private final Map<String, String> lastStatusMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.my_emergency_requests);

        requestsContainer = findViewById(R.id.requestsContainer);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        progressBar = findViewById(R.id.progressBar);
        topAppBar = findViewById(R.id.topAppBar);

        topAppBar.setNavigationOnClickListener(v -> finish());

        mAuth = FirebaseAuth.getInstance();
        emergencyRef = FirebaseDatabase.getInstance().getReference("emergency_requests");
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            attachLiveListener(currentUser.getUid());
        } else {
            showEmptyState();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        detachLiveListener();
    }

    private void attachLiveListener(String userId) {
        detachLiveListener();

        progressBar.setVisibility(View.VISIBLE);
        layoutEmptyState.setVisibility(View.GONE);

        Query query = emergencyRef.orderByChild("userId").equalTo(userId);

        liveListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);
                requestsContainer.removeAllViews();

                if (!snapshot.exists() || snapshot.getChildrenCount() == 0) {
                    showEmptyState();
                    return;
                }

                layoutEmptyState.setVisibility(View.GONE);
                requestsContainer.setVisibility(View.VISIBLE);

                java.util.List<DataSnapshot> requestsList = new java.util.ArrayList<>();
                for (DataSnapshot data : snapshot.getChildren()) requestsList.add(data);

                java.util.Collections.sort(requestsList, (o1, o2) -> {
                    Long t1 = o1.child("timestamp").getValue(Long.class);
                    Long t2 = o2.child("timestamp").getValue(Long.class);
                    if (t1 == null) t1 = 0L;
                    if (t2 == null) t2 = 0L;
                    return t2.compareTo(t1);
                });

                for (DataSnapshot data : requestsList) {
                    String requestId = data.getKey();
                    String status = data.child("status").getValue(String.class);
                    String date = data.child("date").getValue(String.class);
                    String location = data.child("location").getValue(String.class);
                    String urgency = data.child("urgencyLevel").getValue(String.class);

                    addRequestCard(
                            requestId,
                            status != null ? status : "Submitted",
                            date != null ? date : "N/A",
                            location != null ? location : "N/A",
                            urgency != null ? urgency : "Standard"
                    );
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Log.e("MY_EMERGENCIES", "Query failed: " + error.getMessage());
                showLoadFailureState();
            }
        };

        query.addValueEventListener(liveListener);
    }

    private void detachLiveListener() {
        if (liveListener != null && mAuth.getCurrentUser() != null) {
            emergencyRef.orderByChild("userId")
                    .equalTo(mAuth.getCurrentUser().getUid())
                    .removeEventListener(liveListener);
            liveListener = null;
        }
    }

    private void addRequestCard(String requestId, String status, String date,
                                String location, String urgency) {
        View cardView = LayoutInflater.from(this)
                .inflate(R.layout.item_my_emergency_requests, requestsContainer, false);

        ImageView ivIcon = cardView.findViewById(R.id.ivRequestIcon);
        TextView tvStatus = cardView.findViewById(R.id.tvRequestStatus);
        TextView tvDate = cardView.findViewById(R.id.tvRequestDate);
        TextView tvUrgency = cardView.findViewById(R.id.tvRequestUrgency);
        TextView tvLocation = cardView.findViewById(R.id.tvRequestLocation);
        TextView tvUrgencyLabel = cardView.findViewById(R.id.tvUrgencyLabel);
        TextView tvLocationLabel = cardView.findViewById(R.id.tvLocationLabel);

        ImageButton btnEdit = cardView.findViewById(R.id.btnEditRequest);
        ImageButton btnDelete = cardView.findViewById(R.id.btnDeleteRequest);

        // Clear translation tags
        if (tvStatus != null) tvStatus.setTag(null);
        if (tvDate != null) tvDate.setTag(null);
        if (tvUrgency != null) tvUrgency.setTag(null);
        if (tvLocation != null) tvLocation.setTag(null);
        if (tvUrgencyLabel != null) tvUrgencyLabel.setTag(null);
        if (tvLocationLabel != null) tvLocationLabel.setTag(null);

        if (tvStatus != null) tvStatus.setText("Status: " + status);
        if (tvDate != null) tvDate.setText(date);
        if (tvUrgency != null) tvUrgency.setText(urgency);
        if (tvLocation != null) tvLocation.setText(location);
        if (tvUrgencyLabel != null) tvUrgencyLabel.setText("Urgency: ");
        if (tvLocationLabel != null) tvLocationLabel.setText("Location: ");

        LocaleHelper.translateAllViews(this, cardView);

        int color;
        int iconRes;
        if (status.equalsIgnoreCase("Delivered")
                || status.equalsIgnoreCase("Completed")
                || status.equalsIgnoreCase("Confirmed")) {
            color = Color.parseColor("#00E5FF");
            iconRes = android.R.drawable.checkbox_on_background;
        } else if (status.equalsIgnoreCase("Arrived")) {
            color = Color.parseColor("#0F766E");
            iconRes = android.R.drawable.ic_menu_mylocation;
        } else if (status.equalsIgnoreCase("En Route")
                || status.equalsIgnoreCase("Tanker on the way")
                || status.equalsIgnoreCase("Dispatched")) {
            color = Color.parseColor("#0D47A1");
            iconRes = android.R.drawable.ic_menu_directions;
        } else {
            color = Color.parseColor("#F57C00");
            iconRes = android.R.drawable.ic_dialog_alert;
        }

        if (ivIcon != null) {
            ivIcon.setImageResource(iconRes);
            ivIcon.setColorFilter(color);
        }
        if (tvStatus != null) tvStatus.setTextColor(color);

        if (tvUrgency != null) {
            if (urgency.equalsIgnoreCase("Extreme")) {
                tvUrgency.setTextColor(Color.parseColor("#B91C1C"));
            } else if (urgency.equalsIgnoreCase("Critical") || urgency.equalsIgnoreCase("High")) {
                tvUrgency.setTextColor(Color.parseColor("#F57C00"));
            } else {
                tvUrgency.setTextColor(Color.parseColor("#00A3B8"));
            }
        }

        // ✅ Flicker the card if status changed since last render
        String prevStatus = lastStatusMap.get(requestId);
        if (prevStatus != null && !prevStatus.equalsIgnoreCase(status)) {
            flicker(cardView);
        }
        lastStatusMap.put(requestId, status);

        // DELETE BUTTON
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Delete Request")
                        .setMessage("Are you sure you want to delete this emergency request?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            emergencyRef.child(requestId).removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this, "Request deleted", Toast.LENGTH_SHORT).show();
                                        lastStatusMap.remove(requestId);
                                        if (requestsContainer.getChildCount() == 0) showEmptyState();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        // EDIT BUTTON
        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(MyEmergencyRequests.this, EditEmergencyRequestActivity.class);
                intent.putExtra("REQUEST_ID", requestId);
                intent.putExtra("LOCATION", location);
                intent.putExtra("URGENCY", urgency);
                intent.putExtra("DATE", date);
                startActivity(intent);
            });
        }

        requestsContainer.addView(cardView);
    }

    // ✅ Flicker animation — pulses the card's alpha
    private void flicker(View view) {
        AlphaAnimation blink = new AlphaAnimation(1.0f, 0.3f);
        blink.setDuration(280);
        blink.setRepeatMode(Animation.REVERSE);
        blink.setRepeatCount(4);
        view.startAnimation(blink);
    }

    private void showEmptyState() {
        requestsContainer.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.VISIBLE);
        tvEmptyState.setTextColor(Color.parseColor("#A0C4D0"));
        tvEmptyState.setText("You haven't submitted any emergency requests yet.");
        tvEmptyState.setTag(null);
        LocaleHelper.translateAllViews(this, tvEmptyState);
        progressBar.setVisibility(View.GONE);
    }

    private void showLoadFailureState() {
        requestsContainer.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.VISIBLE);
        tvEmptyState.setTextColor(Color.parseColor("#B91C1C"));
        tvEmptyState.setText("Couldn't load your requests. Please check your connection and try again.");
        tvEmptyState.setTag(null);
        LocaleHelper.translateAllViews(this, tvEmptyState);
        progressBar.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        detachLiveListener();
        super.onDestroy();
    }
}