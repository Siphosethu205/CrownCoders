package com.example.communitymember20;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MyReportedIssues extends BaseActivity {

    private RecyclerView rvMyIssues;
    private LinearLayout layoutEmpty;
    private MyIssuesAdapter adapter;
    private List<InfrastructureIssueModel> issueList;
    private DatabaseReference dbRef;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.my_reported_issues);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // FIX: Removed the line that hides the title
        toolbar.setNavigationOnClickListener(v -> finish());

        rvMyIssues = findViewById(R.id.rvMyIssues);
        layoutEmpty = findViewById(R.id.layoutEmpty);

        issueList = new ArrayList<>();
        adapter = new MyIssuesAdapter(issueList);
        rvMyIssues.setLayoutManager(new LinearLayoutManager(this));
        rvMyIssues.setAdapter(adapter);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid();

        dbRef = FirebaseDatabase.getInstance().getReference();
    }

    // Refresh list every time user returns from Edit screen
    @Override
    protected void onResume() {
        super.onResume();
        fetchMyIssues();
    }

    private void fetchMyIssues() {
        Query q = dbRef.child("infrastructure_issues")
                .orderByChild("memberId").equalTo(uid);

        q.addListenerForSingleValueEvent(new ValueEventListener() {
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
                Collections.sort(issueList, (a, b) -> Long.compare(b.timestamp, a.timestamp));

                if (issueList.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    rvMyIssues.setVisibility(View.GONE);
                } else {
                    layoutEmpty.setVisibility(View.GONE);
                    rvMyIssues.setVisibility(View.VISIBLE);
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
    class MyIssuesAdapter extends RecyclerView.Adapter<MyIssuesAdapter.VH> {
        private List<InfrastructureIssueModel> items;

        MyIssuesAdapter(List<InfrastructureIssueModel> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_my_issue, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            InfrastructureIssueModel m = items.get(position);

            h.tvIssueType.setText(m.issueType != null ? m.issueType : "Issue");
            h.tvDescription.setText(m.description != null ? m.description : "");
            h.tvLocation.setText("📍 " + (m.location != null ? m.location : "—"));

            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
            h.tvTime.setText(sdf.format(new Date(m.timestamp)));

            // FIX: Load the image if it exists
            if (m.photoUrl != null && !m.photoUrl.isEmpty()) {
                h.ivIssueImage.setVisibility(View.VISIBLE);
                try {
                    h.ivIssueImage.setImageURI(Uri.parse(m.photoUrl));
                } catch (Exception e) {
                    Log.e("MyIssues", "Failed to load image: " + e.getMessage());
                    h.ivIssueImage.setVisibility(View.GONE);
                }
            } else {
                h.ivIssueImage.setVisibility(View.GONE);
            }

            // Status
            String status = m.status != null ? m.status : "Pending";
            h.tvStatus.setText(status.toUpperCase());
            switch (status.toLowerCase()) {
                case "resolved":
                    h.tvStatus.setBackgroundResource(R.drawable.bg_pill_sync);
                    h.tvStatus.setTextColor(0xFF0F766E);
                    break;
                case "in progress":
                    h.tvStatus.setBackgroundResource(R.drawable.bg_pill_amber);
                    h.tvStatus.setTextColor(0xFFC2410C);
                    break;
                default:
                    h.tvStatus.setBackgroundResource(R.drawable.bg_pill_red);
                    h.tvStatus.setTextColor(0xFFDC2626);
                    break;
            }

            // Urgency
            String urgency = m.urgency != null ? m.urgency : "Medium";
            h.tvUrgency.setText(urgency.toUpperCase());
            switch (urgency.toLowerCase()) {
                case "critical":
                case "high":
                    h.tvUrgency.setTextColor(0xFFDC2626);
                    break;
                case "medium":
                    h.tvUrgency.setTextColor(0xFFC2410C);
                    break;
                default:
                    h.tvUrgency.setTextColor(0xFF0F766E);
                    break;
            }

            if ("resolved".equalsIgnoreCase(status) && m.resolvedAt > 0) {
                SimpleDateFormat rsdf = new SimpleDateFormat("dd MMM", Locale.getDefault());
                h.tvResolvedInfo.setVisibility(View.VISIBLE);
                h.tvResolvedInfo.setText("Resolved " + rsdf.format(new Date(m.resolvedAt)));
            } else {
                h.tvResolvedInfo.setVisibility(View.GONE);
            }

            // DELETE BUTTON
            h.btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(h.itemView.getContext())
                        .setTitle("Delete Issue")
                        .setMessage("Are you sure you want to delete this reported issue? This cannot be undone.")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            dbRef.child("infrastructure_issues").child(m.issueId).removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(h.itemView.getContext(), "Issue deleted", Toast.LENGTH_SHORT).show();
                                        issueList.remove(position);
                                        notifyDataSetChanged();
                                        if (issueList.isEmpty()) {
                                            layoutEmpty.setVisibility(View.VISIBLE);
                                            rvMyIssues.setVisibility(View.GONE);
                                        }
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(h.itemView.getContext(), "Failed to delete: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            // EDIT BUTTON
            h.btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(h.itemView.getContext(), EditReportedIssueActivity.class);
                intent.putExtra("ISSUE_ID", m.issueId);
                intent.putExtra("ISSUE_TYPE", m.issueType);
                intent.putExtra("DESCRIPTION", m.description);
                intent.putExtra("LOCATION", m.location);
                intent.putExtra("URGENCY", m.urgency);
                h.itemView.getContext().startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvIssueType, tvTime, tvStatus, tvDescription, tvLocation, tvUrgency, tvResolvedInfo;
            ImageButton btnEdit, btnDelete;
            ImageView ivIssueImage; // NEW

            VH(@NonNull View v) {
                super(v);
                tvIssueType = v.findViewById(R.id.tvIssueType);
                tvTime = v.findViewById(R.id.tvTime);
                tvStatus = v.findViewById(R.id.tvStatus);
                tvDescription = v.findViewById(R.id.tvDescription);
                tvLocation = v.findViewById(R.id.tvLocation);
                tvUrgency = v.findViewById(R.id.tvUrgency);
                tvResolvedInfo = v.findViewById(R.id.tvResolvedInfo);
                btnEdit = v.findViewById(R.id.btnEditIssue);
                btnDelete = v.findViewById(R.id.btnDeleteIssue);
                ivIssueImage = v.findViewById(R.id.ivIssueImage); // NEW
            }
        }
    }
}