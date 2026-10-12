package com.example.communitymember20;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ViewWaterStatus extends BaseActivity {

    private static final String TAG = "VIEW_WATER_STATUS";
    private static final long TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L;

    private MaterialToolbar topAppBar;
    private Spinner spinnerArea;
    private ProgressBar progressBar;

    private MaterialCardView cardAreaSelector, cardStatusResult, cardNoData, cardNoAreas;
    private TextView tvStatusTitle, tvStatusMessage, tvReportCount, tvNoDataMessage, tvNoAreasTitle, tvNoAreasMessage;
    private View statusDot;

    private MaterialButton btnGoToLogin, btnNoAreasLogin, btnNoDataLogin;

    private DatabaseReference waterStatusesRef;

    private List<String> reportedAreas = new ArrayList<>();
    private ArrayAdapter<String> areaAdapter;

    private DataSnapshot cachedSnapshot;
    private boolean suppressSelectionCallbacks = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_water_status);

        // Bind UI
        topAppBar = findViewById(R.id.topAppBar);
        spinnerArea = findViewById(R.id.spinnerArea);
        progressBar = findViewById(R.id.progressBar);
        cardAreaSelector = findViewById(R.id.cardAreaSelector);
        cardStatusResult = findViewById(R.id.cardStatusResult);
        cardNoData = findViewById(R.id.cardNoData);
        cardNoAreas = findViewById(R.id.cardNoAreas);
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvStatusMessage = findViewById(R.id.tvStatusMessage);
        tvReportCount = findViewById(R.id.tvReportCount);
        tvNoDataMessage = findViewById(R.id.tvNoDataMessage);
        statusDot = findViewById(R.id.statusDot);
        btnGoToLogin = findViewById(R.id.btnGoToLogin);
        btnNoAreasLogin = findViewById(R.id.btnNoAreasLogin);
        btnNoDataLogin = findViewById(R.id.btnNoDataLogin);

        tvNoAreasTitle = findViewById(R.id.tvNoAreasTitle);
        tvNoAreasMessage = findViewById(R.id.tvNoAreasMessage);

        waterStatusesRef = FirebaseDatabase.getInstance().getReference("water_statuses");

        topAppBar.setNavigationOnClickListener(v -> finish());

        areaAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, reportedAreas);
        areaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerArea.setAdapter(areaAdapter);

        spinnerArea.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressSelectionCallbacks) return;
                if (position >= 0 && position < reportedAreas.size()) {
                    fetchAreaStatus(reportedAreas.get(position));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnGoToLogin.setOnClickListener(v -> goToLogin());
        btnNoAreasLogin.setOnClickListener(v -> goToLogin());
        btnNoDataLogin.setOnClickListener(v -> goToLogin());

        loadReportedAreas();
    }

    private void goToLogin() {
        Intent intent = new Intent(ViewWaterStatus.this, LoginPage.class);
        startActivity(intent);
        finish();
    }

    // ============================================================
    // STEP 1 — Load the list of reported areas from Firebase
    // ============================================================
    private void loadReportedAreas() {
        progressBar.setVisibility(View.VISIBLE);
        hideAllCards();

        waterStatusesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);
                cachedSnapshot = snapshot;

                Set<String> uniqueAreas = new LinkedHashSet<>();
                for (DataSnapshot data : snapshot.getChildren()) {
                    String location = data.child("location").getValue(String.class);
                    String area = extractArea(location);
                    if (area != null && !area.isEmpty()) uniqueAreas.add(area);
                }

                reportedAreas.clear();
                reportedAreas.addAll(uniqueAreas);

                if (reportedAreas.isEmpty()) {
                    showNoAreasState(false);
                } else {
                    suppressSelectionCallbacks = true;
                    areaAdapter.notifyDataSetChanged();
                    spinnerArea.setSelection(0);
                    suppressSelectionCallbacks = false;

                    cardAreaSelector.setVisibility(View.VISIBLE);

                    fetchAreaStatus(reportedAreas.get(0));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Failed to load areas: " + error.getMessage());
                showNoAreasState(true);
            }
        });
    }

    // ============================================================
    // STEP 2 — Aggregate reports for the chosen area
    // ============================================================
    private void fetchAreaStatus(String areaName) {
        progressBar.setVisibility(View.VISIBLE);
        cardStatusResult.setVisibility(View.GONE);
        cardNoData.setVisibility(View.GONE);

        if (cachedSnapshot == null) {
            loadReportedAreas();
            return;
        }

        long cutoff = System.currentTimeMillis() - TWENTY_FOUR_HOURS_MS;
        int availableCount = 0, lowCount = 0, noWaterCount = 0, totalMatching = 0;

        for (DataSnapshot data : cachedSnapshot.getChildren()) {
            String location = data.child("location").getValue(String.class);
            String status = data.child("waterStatus").getValue(String.class);
            Long timestamp = data.child("timestamp").getValue(Long.class);
            if (location == null || status == null || timestamp == null) continue;

            String area = extractArea(location);
            if (area == null || !area.equalsIgnoreCase(areaName)) continue;
            if (timestamp < cutoff) continue;

            totalMatching++;
            if ("Available".equalsIgnoreCase(status)) availableCount++;
            else if ("Low".equalsIgnoreCase(status)) lowCount++;
            else if ("No water".equalsIgnoreCase(status)) noWaterCount++;
        }

        progressBar.setVisibility(View.GONE);

        if (totalMatching == 0) {
            showNoDataState(areaName);
            return;
        }

        int max = Math.max(availableCount, Math.max(lowCount, noWaterCount));
        String overallStatus, message;
        int color;
        if (max == availableCount) {
            overallStatus = "NORMAL";
            message = "Water supply is stable in this area.";
            color = Color.parseColor("#16A34A");
        } else if (max == lowCount) {
            overallStatus = "LOW / PARTIAL";
            message = "Water supply is limited. Tankers may arrive soon.";
            color = Color.parseColor("#CA8A04");
        } else {
            overallStatus = "SHORTAGE";
            message = "Water shortage reported. Emergency services notified.";
            color = Color.parseColor("#B91C1C");
        }

        if (tvStatusTitle != null) tvStatusTitle.setTag(null);
        if (tvStatusMessage != null) tvStatusMessage.setTag(null);
        if (tvReportCount != null) tvReportCount.setTag(null);

        if (tvStatusTitle != null) tvStatusTitle.setText("AREA WATER STATUS: " + overallStatus);
        if (tvStatusMessage != null) tvStatusMessage.setText(message);
        if (tvReportCount != null) tvReportCount.setText("Based on " + totalMatching + " report(s) in the last 24 hours.");

        if (cardStatusResult != null) {
            cardStatusResult.setCardBackgroundColor(color);
        }

        try {
            if (statusDot != null) {
                Drawable dotBg = statusDot.getBackground();
                if (dotBg != null) {
                    Drawable wrapped = DrawableCompat.wrap(dotBg.mutate());
                    DrawableCompat.setTint(wrapped, Color.WHITE);
                    statusDot.setBackground(wrapped);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to tint status dot: " + e.getMessage());
        }

        if (cardStatusResult != null) {
            cardStatusResult.setVisibility(View.VISIBLE);
            LocaleHelper.translateAllViews(ViewWaterStatus.this, cardStatusResult);
        }
    }

    // ============================================================
    // STATE HELPERS
    // ============================================================

    private void hideAllCards() {
        if (cardAreaSelector != null) cardAreaSelector.setVisibility(View.GONE);
        if (cardNoAreas != null) cardNoAreas.setVisibility(View.GONE);
        if (cardStatusResult != null) cardStatusResult.setVisibility(View.GONE);
        if (cardNoData != null) cardNoData.setVisibility(View.GONE);
    }

    private void showNoAreasState(boolean isLoadFailure) {
        if (cardNoAreas == null) return;

        cardNoAreas.setVisibility(View.VISIBLE);
        if (cardAreaSelector != null) cardAreaSelector.setVisibility(View.GONE);

        if (isLoadFailure) {
            if (tvNoAreasTitle != null) {
                tvNoAreasTitle.setTag(null);
                tvNoAreasTitle.setText("Couldn't Load Reports");
            }
            if (tvNoAreasMessage != null) {
                tvNoAreasMessage.setTag(null);
                tvNoAreasMessage.setText("Please check your internet connection and try again.");
            }
        } else {
            if (tvNoAreasTitle != null) {
                tvNoAreasTitle.setTag(null);
                tvNoAreasTitle.setText("No Reports Yet");
            }
            if (tvNoAreasMessage != null) {
                tvNoAreasMessage.setTag(null);
                tvNoAreasMessage.setText("No water status reports have been submitted yet. Log in to be the first to help your community.");
            }
        }

        LocaleHelper.translateAllViews(ViewWaterStatus.this, cardNoAreas);
    }

    private void showNoDataState(String areaName) {
        if (cardNoData == null) return;

        cardNoData.setVisibility(View.VISIBLE);

        if (tvNoDataMessage != null) {
            tvNoDataMessage.setTag(null);
            tvNoDataMessage.setText("No reports have been submitted for " + areaName
                    + " in the last 24 hours. Log in to be the first to update your community.");
        }

        LocaleHelper.translateAllViews(ViewWaterStatus.this, cardNoData);
    }

    private String extractArea(String location) {
        if (location == null) return null;
        String trimmed = location.trim();
        if (trimmed.isEmpty()) return null;
        if (!trimmed.contains(",")) return trimmed;

        String[] parts = trimmed.split(",");
        for (int i = 1; i < parts.length; i++) {
            String segment = parts[i].trim();
            if (segment.isEmpty()) continue;
            if (segment.matches("^\\d+.*")) continue;
            return segment;
        }
        for (String part : parts) {
            String p = part.trim();
            if (!p.isEmpty()) return p;
        }
        return null;
    }
}