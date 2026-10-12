package com.example.communitymember20;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

public class DriverManagementAdapter extends RecyclerView.Adapter<DriverManagementAdapter.VH> {

    public interface OnDriverAction {
        void onEdit(DriverForAssignmentModel d);
        void onReassign(DriverForAssignmentModel d);
        void onDelete(DriverForAssignmentModel d);
    }

    private final List<DriverForAssignmentModel> drivers;
    private final OnDriverAction listener;

    public DriverManagementAdapter(List<DriverForAssignmentModel> drivers, OnDriverAction listener) {
        this.drivers = drivers;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_driver_management, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DriverForAssignmentModel d = drivers.get(position);

        // Avatar
        if (d.fullName != null && !d.fullName.isEmpty()) {
            h.tvInitial.setText(String.valueOf(d.fullName.charAt(0)).toUpperCase());
        } else {
            h.tvInitial.setText("?");
        }

        h.tvDriverName.setText(d.fullName != null ? d.fullName : "Unnamed Driver");
        h.tvDriverPhone.setText(d.phone != null && !d.phone.isEmpty() ? d.phone : "—");

        // ---- Status badge ----
        boolean isOnAssignment = d.activeEmergencyId != null && !d.activeEmergencyId.isEmpty();
        boolean hasTanker = d.tankerId != null && !d.tankerId.isEmpty();

        if (isOnAssignment) {
            h.tvStatusBadge.setText("NOT AVAILABLE");
            h.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_red);
            h.tvStatusBadge.setTextColor(Color.parseColor("#DC2626"));
        } else if (hasTanker) {
            h.tvStatusBadge.setText("AVAILABLE");
            h.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_sync);
            h.tvStatusBadge.setTextColor(Color.parseColor("#0F766E"));
        } else {
            h.tvStatusBadge.setText("IDLE");
            h.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_amber);
            h.tvStatusBadge.setTextColor(Color.parseColor("#C2410C"));
        }

        // ---- Assignment info ----
        if (isOnAssignment && d.activeEmergencyLocation != null) {
            h.tvAssignment.setVisibility(View.VISIBLE);
            h.tvAssignment.setText("🚨 On assignment: " + d.activeEmergencyLocation);
        } else {
            h.tvAssignment.setVisibility(View.GONE);
        }

        // ---- Tanker info grid ----
        if (hasTanker) {
            h.tvTankerReg.setText(d.tankerReg != null ? d.tankerReg : d.tankerId);
            h.tvCapacity.setText(d.capacity > 0
                    ? String.format(Locale.getDefault(), "%,d L", d.capacity) : "—");
        } else {
            h.tvTankerReg.setText("No Tanker");
            h.tvCapacity.setText("—");
        }

        // ---- Load bar ----
        int cap = d.capacity;
        int load = d.currentLoad;
        if (hasTanker && cap > 0) {
            h.tvLoadValue.setText(String.format(Locale.getDefault(), "%,d / %,d L", load, cap));
            int pct = (int) (((float) load / cap) * 100);
            h.pbLoad.setProgress(Math.max(0, Math.min(100, pct)));
        } else {
            h.tvLoadValue.setText("0 / 0 L");
            h.pbLoad.setProgress(0);
        }

        h.tvLastUpdated.setText(hasTanker ? "Just now" : "—");

        // ---- Reassign button text ----
        if (hasTanker) {
            h.btnReassign.setText("REASSIGN");
        } else {
            h.btnReassign.setText("ASSIGN");
        }

        // ---- Click handlers ----
        h.btnEdit.setOnClickListener(v -> listener.onEdit(d));
        h.btnReassign.setOnClickListener(v -> listener.onReassign(d));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(d));
    }

    @Override
    public int getItemCount() { return drivers.size(); }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvInitial, tvDriverName, tvDriverPhone, tvStatusBadge;
        TextView tvTankerReg, tvCapacity, tvLastUpdated, tvLoadValue;
        TextView tvAssignment;
        ProgressBar pbLoad;
        MaterialButton btnEdit, btnReassign;
        ImageButton btnDelete;

        public VH(@NonNull View v) {
            super(v);
            tvInitial = v.findViewById(R.id.tvInitial);
            tvDriverName = v.findViewById(R.id.tvDriverName);
            tvDriverPhone = v.findViewById(R.id.tvDriverPhone);
            tvStatusBadge = v.findViewById(R.id.tvStatusBadge);
            tvTankerReg = v.findViewById(R.id.tvTankerReg);
            tvCapacity = v.findViewById(R.id.tvCapacity);
            tvLastUpdated = v.findViewById(R.id.tvLastUpdated);
            tvLoadValue = v.findViewById(R.id.tvLoadValue);
            tvAssignment = v.findViewById(R.id.tvAssignment);
            pbLoad = v.findViewById(R.id.pbLoad);
            btnEdit = v.findViewById(R.id.btnEdit);
            btnReassign = v.findViewById(R.id.btnReassign);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }
}
