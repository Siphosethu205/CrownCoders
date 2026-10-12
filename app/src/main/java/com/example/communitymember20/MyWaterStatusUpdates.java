package com.example.communitymember20;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
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

public class MyWaterStatusUpdates extends BaseActivity {

    private LinearLayout reportsContainer;
    private LinearLayout layoutEmptyState;
    private TextView tvEmptyState;
    private ProgressBar progressBar;
    private MaterialToolbar topAppBar;

    private FirebaseAuth mAuth;
    private DatabaseReference waterRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.my_water_status_updates);

        reportsContainer = findViewById(R.id.reportsContainer);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        progressBar = findViewById(R.id.progressBar);
        topAppBar = findViewById(R.id.topAppBar);

        topAppBar.setTitle("My Water Status Updates");
        topAppBar.setTitleTextColor(Color.parseColor("#0D47A1"));
        topAppBar.setNavigationOnClickListener(v -> finish());

        mAuth = FirebaseAuth.getInstance();
        waterRef = FirebaseDatabase.getInstance().getReference("water_statuses");
    }

    // ==========================================================
    // NEW: Fetch fresh data every time the user returns to this screen
    // ==========================================================
    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            fetchUserReports(currentUser.getUid());
        } else {
            showEmptyState();
        }
    }

    private void fetchUserReports(String userId) {
        progressBar.setVisibility(View.VISIBLE);
        layoutEmptyState.setVisibility(View.GONE);
        reportsContainer.setVisibility(View.GONE);

        // Clear old views so they don't duplicate when refreshed
        reportsContainer.removeAllViews();

        Query query = waterRef.orderByChild("userId").equalTo(userId);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);

                if (!snapshot.exists() || snapshot.getChildrenCount() == 0) {
                    showEmptyState();
                    return;
                }

                layoutEmptyState.setVisibility(View.GONE);
                reportsContainer.setVisibility(View.VISIBLE);

                java.util.List<DataSnapshot> reportsList = new java.util.ArrayList<>();
                for (DataSnapshot data : snapshot.getChildren()) {
                    reportsList.add(data);
                }

                java.util.Collections.sort(reportsList, (o1, o2) -> {
                    Long t1 = o1.child("timestamp").getValue(Long.class);
                    Long t2 = o2.child("timestamp").getValue(Long.class);
                    if (t1 == null) t1 = 0L;
                    if (t2 == null) t2 = 0L;
                    return t2.compareTo(t1);
                });

                for (DataSnapshot data : reportsList) {
                    String reportId = data.getKey();
                    String status = data.child("status").getValue(String.class);
                    String date = data.child("date").getValue(String.class);
                    String location = data.child("location").getValue(String.class);
                    String waterLevel = data.child("waterStatus").getValue(String.class);
                    String reference = data.child("reference").getValue(String.class);

                    addReportCard(
                            reportId,
                            status != null ? status : "Received",
                            date != null ? date : "N/A",
                            location != null ? location : "N/A",
                            waterLevel != null ? waterLevel : "N/A",
                            reference
                    );
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Log.e("MY_REPORTS", "Query failed: " + error.getMessage());
                showLoadFailureState();
            }
        });
    }

    private void addReportCard(String reportId, String status, String date, String location,
                               String waterLevel, String reference) {
        View cardView = LayoutInflater.from(this)
                .inflate(R.layout.item_my_water_status_updates, reportsContainer, false);

        ImageView ivIcon = cardView.findViewById(R.id.ivReportIcon);
        TextView tvStatus = cardView.findViewById(R.id.tvReportStatus);
        TextView tvDate = cardView.findViewById(R.id.tvReportDate);
        TextView tvLocation = cardView.findViewById(R.id.tvReportLocation);
        TextView tvWaterLevel = cardView.findViewById(R.id.tvReportWaterLevel);
        TextView tvLocationLabel = cardView.findViewById(R.id.tvLocationLabel);
        TextView tvWaterLevelLabel = cardView.findViewById(R.id.tvWaterLevelLabel);

        ImageButton btnEdit = cardView.findViewById(R.id.btnEditReport);
        ImageButton btnDelete = cardView.findViewById(R.id.btnDeleteReport);

        if (tvStatus != null) tvStatus.setTag(null);
        if (tvDate != null) tvDate.setTag(null);
        if (tvLocation != null) tvLocation.setTag(null);
        if (tvWaterLevel != null) tvWaterLevel.setTag(null);
        if (tvLocationLabel != null) tvLocationLabel.setTag(null);
        if (tvWaterLevelLabel != null) tvWaterLevelLabel.setTag(null);

        if (tvStatus != null) {
            String statusText = "Status: " + status;
            if (reference != null && !reference.isEmpty()) {
                statusText += " • " + reference;
            }
            tvStatus.setText(statusText);
        }
        if (tvDate != null) tvDate.setText(date);
        if (tvLocation != null) tvLocation.setText(location);
        if (tvWaterLevel != null) tvWaterLevel.setText(waterLevel);
        if (tvLocationLabel != null) tvLocationLabel.setText("Location: ");
        if (tvWaterLevelLabel != null) tvWaterLevelLabel.setText("Water Level: ");

        LocaleHelper.translateAllViews(this, cardView);

        int color;
        int iconRes;
        if (status.equalsIgnoreCase("Resolved") || status.equalsIgnoreCase("Completed")) {
            color = Color.parseColor("#00E5FF");
            iconRes = android.R.drawable.checkbox_on_background;
        } else if (status.equalsIgnoreCase("Reviewed") || status.equalsIgnoreCase("Acknowledged")) {
            color = Color.parseColor("#0D47A1");
            iconRes = android.R.drawable.ic_menu_view;
        } else {
            color = Color.parseColor("#F57C00");
            iconRes = android.R.drawable.ic_menu_recent_history;
        }

        if (ivIcon != null) {
            ivIcon.setImageResource(iconRes);
            ivIcon.setColorFilter(color);
        }
        if (tvStatus != null) {
            tvStatus.setTextColor(color);
        }

        if (tvWaterLevel != null) {
            if (waterLevel.equalsIgnoreCase("No Water") || waterLevel.equalsIgnoreCase("None")) {
                tvWaterLevel.setTextColor(Color.parseColor("#B91C1C"));
            } else if (waterLevel.equalsIgnoreCase("Low") || waterLevel.equalsIgnoreCase("Low Pressure")) {
                tvWaterLevel.setTextColor(Color.parseColor("#F57C00"));
            } else if (waterLevel.equalsIgnoreCase("Available") || waterLevel.equalsIgnoreCase("Normal")) {
                tvWaterLevel.setTextColor(Color.parseColor("#00A3B8"));
            }
        }

        // DELETE BUTTON LOGIC
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Delete Report")
                        .setMessage("Are you sure you want to delete this water report? This cannot be undone.")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            waterRef.child(reportId).removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this, "Report deleted", Toast.LENGTH_SHORT).show();
                                        reportsContainer.removeView(cardView);
                                        if (reportsContainer.getChildCount() == 0) {
                                            showEmptyState();
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    });
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        // EDIT BUTTON LOGIC
        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(MyWaterStatusUpdates.this, EditWaterStatusActivity.class);
                intent.putExtra("REPORT_ID", reportId);
                intent.putExtra("LOCATION", location);
                intent.putExtra("WATER_LEVEL", waterLevel);
                intent.putExtra("DATE", date);
                startActivity(intent);
            });
        }

        reportsContainer.addView(cardView);
    }

    private void showEmptyState() {
        if (layoutEmptyState == null) return;
        reportsContainer.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.VISIBLE);
        if (tvEmptyState != null) {
            tvEmptyState.setTextColor(Color.parseColor("#A0C4D0"));
            tvEmptyState.setText("You haven't submitted any reports yet.");
            tvEmptyState.setTag(null);
            LocaleHelper.translateAllViews(this, tvEmptyState);
        }
        progressBar.setVisibility(View.GONE);
    }

    private void showLoadFailureState() {
        if (layoutEmptyState == null) return;
        reportsContainer.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.VISIBLE);
        if (tvEmptyState != null) {
            tvEmptyState.setTextColor(Color.parseColor("#B91C1C"));
            tvEmptyState.setText("Couldn't load your reports. Please check your connection and try again.");
            tvEmptyState.setTag(null);
            LocaleHelper.translateAllViews(this, tvEmptyState);
        }
        progressBar.setVisibility(View.GONE);
    }
}