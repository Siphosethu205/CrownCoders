package com.example.communitymember20;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InfrastructureIssues extends BaseActivity {

    private RecyclerView rvIssues;
    private LinearLayout layoutEmpty; // Bound the empty state container
    private TextView tvEmpty;
    private MaterialToolbar toolbar;  // MaterialToolbar matching the new XML
    private IssuesAdapter adapter;
    private List<InfrastructureIssueModel> issueList;
    private DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.infrastructure_issues);

        // Setup Toolbar (custom MaterialToolbar - title handled in XML)
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvIssues = findViewById(R.id.rvIssues);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tvEmpty = findViewById(R.id.tvEmpty);

        issueList = new ArrayList<>();
        adapter = new IssuesAdapter(issueList);
        rvIssues.setLayoutManager(new LinearLayoutManager(this));
        rvIssues.setAdapter(adapter);

        dbRef = FirebaseDatabase.getInstance().getReference();

        dbRef.child("infrastructure_issues")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        issueList.clear();
                        for (DataSnapshot data : snapshot.getChildren()) {
                            InfrastructureIssueModel m = data.getValue(InfrastructureIssueModel.class);
                            if (m != null) {
                                m.issueId = data.getKey();
                                issueList.add(m);
                            }
                        }
                        Collections.sort(issueList, (a, b) ->
                                Long.compare(b.timestamp, a.timestamp));

                        // Toggle empty state container (icon + text together)
                        if (issueList.isEmpty()) {
                            layoutEmpty.setVisibility(View.VISIBLE);
                            rvIssues.setVisibility(View.GONE);
                        } else {
                            layoutEmpty.setVisibility(View.GONE);
                            rvIssues.setVisibility(View.VISIBLE);
                        }
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    // ---------------------------------------------------------------
    // Adapter
    // ---------------------------------------------------------------
    class IssuesAdapter extends RecyclerView.Adapter<IssuesAdapter.VH> {
        private List<InfrastructureIssueModel> items;

        IssuesAdapter(List<InfrastructureIssueModel> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_infrastructure_issue, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            InfrastructureIssueModel m = items.get(position);

            h.tvIssueType.setText(m.issueType != null ? m.issueType : "Issue");
            h.tvReporter.setText(m.memberName != null ? m.memberName : "—");
            h.tvDescription.setText(m.description != null ? m.description : "");
            h.tvLocation.setText("📍 " + (m.location != null ? m.location : "—"));

            // Status styling (aquatic theme colors)
            String status = m.status != null ? m.status : "Pending";
            h.tvStatus.setText(status.toUpperCase());
            switch (status.toLowerCase()) {
                case "resolved":
                    h.tvStatus.setBackgroundResource(R.drawable.bg_pill_sync);
                    h.tvStatus.setTextColor(0xFF00A3B8); // Cyan/Teal
                    break;
                case "in progress":
                    h.tvStatus.setBackgroundResource(R.drawable.bg_pill_amber);
                    h.tvStatus.setTextColor(0xFFF57C00); // Orange
                    break;
                default: // Pending
                    h.tvStatus.setBackgroundResource(R.drawable.bg_pill_red);
                    h.tvStatus.setTextColor(0xFFB91C1C); // Red
                    break;
            }

            // Urgency styling (aquatic theme colors)
            String urgency = m.urgency != null ? m.urgency : "Medium";
            h.tvUrgency.setText(urgency.toUpperCase());
            switch (urgency.toLowerCase()) {
                case "critical":
                case "high":
                    h.tvUrgency.setTextColor(0xFFB91C1C); // Red
                    break;
                case "medium":
                    h.tvUrgency.setTextColor(0xFFF57C00); // Orange
                    break;
                default:
                    h.tvUrgency.setTextColor(0xFF00A3B8); // Cyan/Teal
                    break;
            }

            SimpleDateFormat sdf = new SimpleDateFormat(
                    "dd MMM, HH:mm", Locale.getDefault());
            h.tvTime.setText(sdf.format(new Date(m.timestamp)));

            h.btnUpdateStatus.setOnClickListener(v -> showStatusDialog(m));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvIssueType, tvReporter, tvDescription, tvLocation,
                    tvStatus, tvUrgency, tvTime;
            MaterialButton btnUpdateStatus;

            VH(@NonNull View v) {
                super(v);
                tvIssueType = v.findViewById(R.id.tvIssueType);
                tvReporter = v.findViewById(R.id.tvReporter);
                tvDescription = v.findViewById(R.id.tvDescription);
                tvLocation = v.findViewById(R.id.tvLocation);
                tvStatus = v.findViewById(R.id.tvStatus);
                tvUrgency = v.findViewById(R.id.tvUrgency);
                tvTime = v.findViewById(R.id.tvTime);
                btnUpdateStatus = v.findViewById(R.id.btnUpdateStatus);
            }
        }
    }

    private void showStatusDialog(InfrastructureIssueModel issue) {
        String[] options = {"Pending", "In Progress", "Resolved"};
        new AlertDialog.Builder(this)
                .setTitle("Update Status")
                .setItems(options, (d, which) -> {
                    String newStatus = options[which];
                    java.util.Map<String, Object> updates = new java.util.HashMap<>();
                    updates.put("status", newStatus);
                    if ("Resolved".equals(newStatus)) {
                        updates.put("resolvedAt", System.currentTimeMillis());
                    }
                    dbRef.child("infrastructure_issues").child(issue.issueId)
                            .updateChildren(updates)
                            .addOnSuccessListener(a ->
                                    LocaleHelper.showTranslatedToast(this,
                                            "Status: " + newStatus, Toast.LENGTH_SHORT));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}