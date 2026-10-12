package com.example.communitymember20;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public class EmergencyRequestAdapter extends RecyclerView.Adapter<EmergencyRequestAdapter.VH> {

    public interface OnAssignTanker {
        void onAssign(EmergencyRequestModel request);
    }

    public interface OnUpdateStatus {
        void onUpdate(EmergencyRequestModel request);
    }

    public interface OnUnassign {
        void onUnassign(EmergencyRequestModel request);
    }

    private final List<EmergencyRequestModel> requests;
    private final OnAssignTanker assignListener;
    private final OnUpdateStatus statusListener;
    private final OnUnassign unassignListener;

    public EmergencyRequestAdapter(List<EmergencyRequestModel> requests,
                                   OnAssignTanker assignListener,
                                   OnUpdateStatus statusListener,
                                   OnUnassign unassignListener) {
        this.requests = requests;
        this.assignListener = assignListener;
        this.statusListener = statusListener;
        this.unassignListener = unassignListener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_emergency_request, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        EmergencyRequestModel r = requests.get(position);

        h.tvName.setText(r.name != null ? r.name + " Household" : "Emergency Request");
        h.tvLocation.setText(r.location != null ? r.location : "—");
        h.tvDate.setText(r.date != null ? "Requested: " + r.date : "");

        // Urgency badge
        String urgency = r.urgencyLevel != null ? r.urgencyLevel : "Standard";
        h.tvUrgency.setText(urgency.toUpperCase());
        if (urgency.equalsIgnoreCase("Extreme")) {
            h.tvUrgency.setBackgroundResource(R.drawable.bg_pill_red);
            h.tvUrgency.setTextColor(Color.parseColor("#DC2626"));
        } else if (urgency.equalsIgnoreCase("Critical")) {
            h.tvUrgency.setBackgroundResource(R.drawable.bg_pill_amber);
            h.tvUrgency.setTextColor(Color.parseColor("#C2410C"));
        } else {
            h.tvUrgency.setBackgroundResource(R.drawable.bg_pill_sync);
            h.tvUrgency.setTextColor(Color.parseColor("#0F766E"));
        }

        // Status badge
        String status = r.status != null ? r.status : "Submitted";
        h.tvStatus.setText(status.toUpperCase());
        if (status.equalsIgnoreCase("Delivered")
                || status.equalsIgnoreCase("Resolved")
                || status.equalsIgnoreCase("Confirmed")) {
            h.tvStatus.setBackgroundResource(R.drawable.bg_pill_sync);
            h.tvStatus.setTextColor(Color.parseColor("#0F766E"));
        } else if (status.equalsIgnoreCase("Tanker on the way")
                || status.equalsIgnoreCase("Received")
                || status.equalsIgnoreCase("En Route")) {
            h.tvStatus.setBackgroundResource(R.drawable.bg_pill_amber);
            h.tvStatus.setTextColor(Color.parseColor("#C2410C"));
        } else {
            h.tvStatus.setBackgroundResource(R.drawable.bg_pill_red);
            h.tvStatus.setTextColor(Color.parseColor("#DC2626"));
        }

        // Assignment info
        boolean hasAssignment = r.assignedDriverName != null && !r.assignedDriverName.isEmpty();

        if (hasAssignment) {
            h.layoutAssignmentInfo.setVisibility(View.VISIBLE);
            h.tvAssignedDriver.setText(r.assignedDriverName);
            h.tvAssignedTanker.setText(
                    r.assignedTankerReg != null && !r.assignedTankerReg.isEmpty()
                            ? r.assignedTankerReg
                            : (r.assignedTankerId != null ? r.assignedTankerId : "—"));
        } else {
            h.layoutAssignmentInfo.setVisibility(View.GONE);
        }

        // Show/hide unassign button
        if (h.btnUnassign != null) {
            h.btnUnassign.setVisibility(hasAssignment ? View.VISIBLE : View.GONE);
        }

        // Change "Assign Tanker" text when already assigned
        h.btnAssignTanker.setText(hasAssignment ? "Reassign" : "Assign Tanker");

        // Click handlers
        h.btnUpdateStatus.setOnClickListener(v -> statusListener.onUpdate(r));
        h.btnAssignTanker.setOnClickListener(v -> assignListener.onAssign(r));

        if (h.btnUnassign != null && unassignListener != null) {
            h.btnUnassign.setOnClickListener(v -> unassignListener.onUnassign(r));
        }
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvLocation, tvDate, tvUrgency, tvStatus;
        TextView tvAssignedDriver, tvAssignedTanker;
        LinearLayout layoutAssignmentInfo;
        MaterialButton btnUpdateStatus, btnAssignTanker, btnUnassign;

        public VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvRequestTitle);
            tvLocation = v.findViewById(R.id.tvRequestLocation);
            tvDate = v.findViewById(R.id.tvRequestDate);
            tvUrgency = v.findViewById(R.id.tvUrgencyBadge);
            tvStatus = v.findViewById(R.id.tvStatusBadge);
            tvAssignedDriver = v.findViewById(R.id.tvAssignedDriver);
            tvAssignedTanker = v.findViewById(R.id.tvAssignedTanker);
            layoutAssignmentInfo = v.findViewById(R.id.layoutAssignmentInfo);
            btnUpdateStatus = v.findViewById(R.id.btnUpdateStatus);
            btnAssignTanker = v.findViewById(R.id.btnAssignTanker);
            btnUnassign = v.findViewById(R.id.btnUnassign);
        }
    }
}