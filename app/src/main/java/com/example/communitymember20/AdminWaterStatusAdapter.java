package com.example.communitymember20;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public class AdminWaterStatusAdapter extends RecyclerView.Adapter<AdminWaterStatusAdapter.VH> {

    public interface OnWaterStatusAction {
        void onAssignTanker(WaterStatusModel model);
        void onEdit(WaterStatusModel model);
        void onDelete(WaterStatusModel model);
    }

    private final List<WaterStatusModel> list;
    private final OnWaterStatusAction listener;

    public AdminWaterStatusAdapter(List<WaterStatusModel> list, OnWaterStatusAction listener) {
        this.list = list;
        this.listener = listener;
    }

    // Keep backward-compat 1-arg constructor for anything that still uses it
    public AdminWaterStatusAdapter(List<WaterStatusModel> list) {
        this.list = list;
        this.listener = null;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_water_status, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        WaterStatusModel m = list.get(position);
        if (m == null) return;

        String name = m.name != null ? m.name : "Unknown";
        h.tvName.setText(name);
        h.tvInitial.setText(String.valueOf(name.charAt(0)).toUpperCase());

        h.tvLocation.setText(m.location != null ? m.location : "—");
        h.tvDate.setText(m.date != null ? "Reported: " + m.date : "");

        // Water status badge
        String waterStatus = m.waterStatus != null ? m.waterStatus : "Unknown";
        h.tvWaterStatus.setText(waterStatus.toUpperCase());

        boolean isNoWater = "No Water".equalsIgnoreCase(waterStatus);

        if (isNoWater) {
            h.tvWaterStatus.setBackgroundResource(R.drawable.bg_pill_red);
            h.tvWaterStatus.setTextColor(Color.parseColor("#DC2626"));
        } else if ("Low".equalsIgnoreCase(waterStatus)) {
            h.tvWaterStatus.setBackgroundResource(R.drawable.bg_pill_amber);
            h.tvWaterStatus.setTextColor(Color.parseColor("#C2410C"));
        } else {
            h.tvWaterStatus.setBackgroundResource(R.drawable.bg_pill_sync);
            h.tvWaterStatus.setTextColor(Color.parseColor("#0F766E"));
        }

        // Admin status
        String adminStatus = m.status != null ? m.status : "Submitted";
        h.tvAdminStatus.setText(adminStatus.toUpperCase());
        if ("Resolved".equalsIgnoreCase(adminStatus)
                || "Tanker Assigned".equalsIgnoreCase(adminStatus)) {
            h.tvAdminStatus.setBackgroundResource(R.drawable.bg_pill_sync);
            h.tvAdminStatus.setTextColor(Color.parseColor("#0F766E"));
        } else if ("Reviewed".equalsIgnoreCase(adminStatus)) {
            h.tvAdminStatus.setBackgroundResource(R.drawable.bg_pill_amber);
            h.tvAdminStatus.setTextColor(Color.parseColor("#C2410C"));
        } else {
            h.tvAdminStatus.setBackgroundResource(R.drawable.bg_pill_red);
            h.tvAdminStatus.setTextColor(Color.parseColor("#DC2626"));
        }

        // ✅ URGENT notice + Assign button ONLY when No Water
        if (isNoWater) {
            h.layoutUrgentNotice.setVisibility(View.VISIBLE);
            h.btnAssignTanker.setVisibility(View.VISIBLE);

            // Make Edit take half the row
            ViewGroup.LayoutParams lp = h.btnEdit.getLayoutParams();
            if (lp instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) lp).weight = 1f;
            }
        } else {
            h.layoutUrgentNotice.setVisibility(View.GONE);
            h.btnAssignTanker.setVisibility(View.GONE);

            // Edit takes full row
            ViewGroup.LayoutParams lp = h.btnEdit.getLayoutParams();
            if (lp instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) lp).weight = 1f;
            }
        }

        // Click handlers
        h.btnAssignTanker.setOnClickListener(v -> {
            if (listener != null) listener.onAssignTanker(m);
        });
        h.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(m);
        });
        h.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(m);
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvInitial, tvName, tvLocation, tvDate, tvWaterStatus, tvAdminStatus;
        LinearLayout layoutUrgentNotice;
        MaterialButton btnAssignTanker, btnEdit;
        ImageButton btnDelete;

        public VH(@NonNull View v) {
            super(v);
            tvInitial = v.findViewById(R.id.tvInitial);
            tvName = v.findViewById(R.id.tvUserName);
            tvLocation = v.findViewById(R.id.tvLocation);
            tvDate = v.findViewById(R.id.tvDate);
            tvWaterStatus = v.findViewById(R.id.tvWaterStatus);
            tvAdminStatus = v.findViewById(R.id.tvAdminStatus);
            layoutUrgentNotice = v.findViewById(R.id.layoutUrgentNotice);
            btnAssignTanker = v.findViewById(R.id.btnAssignTanker);
            btnEdit = v.findViewById(R.id.btnEdit);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }
}