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

public class WaterTankAdapter extends RecyclerView.Adapter<WaterTankAdapter.VH> {

    public interface OnTankAction {
        void onEdit(TankerModel t);
        void onAssignDriver(TankerModel t);
        void onDelete(TankerModel t);
    }

    private final List<TankerModel> tanks;
    private final OnTankAction listener;
    private final boolean pickMode;

    public WaterTankAdapter(List<TankerModel> tanks, OnTankAction listener) {
        this(tanks, listener, false);
    }

    public WaterTankAdapter(List<TankerModel> tanks, OnTankAction listener, boolean pickMode) {
        this.tanks = tanks;
        this.listener = listener;
        this.pickMode = pickMode;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_water_tank, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        TankerModel t = tanks.get(position);

        h.tvRegistration.setText(t.registration != null ? t.registration : "—");
        h.tvDriverName.setText(t.assignedDriverName != null && !t.assignedDriverName.isEmpty()
                ? t.assignedDriverName : "Unassigned");

        String status = t.currentStatus != null ? t.currentStatus : "Idle";
        h.tvStatusBadge.setText(status.toUpperCase());
        if ("Idle".equalsIgnoreCase(status) || "Available".equalsIgnoreCase(status)) {
            h.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_sync);
            h.tvStatusBadge.setTextColor(Color.parseColor("#0F766E"));
        } else if ("In Use".equalsIgnoreCase(status) || "En Route".equalsIgnoreCase(status)) {
            h.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_amber);
            h.tvStatusBadge.setTextColor(Color.parseColor("#C2410C"));
        } else {
            h.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_red);
            h.tvStatusBadge.setTextColor(Color.parseColor("#DC2626"));
        }

        int cap = t.capacity;
        int load = t.currentLoad;
        h.tvLoadValue.setText(String.format(Locale.getDefault(), "%,d / %,d L", load, cap));
        h.tvCapacity.setText(cap > 0 ? String.format(Locale.getDefault(), "%,d L", cap) : "—");
        h.tvSource.setText(t.sourceName != null && !t.sourceName.isEmpty() ? t.sourceName : "—");
        h.tvLastUpdated.setText("—");

        if (cap > 0) {
            int pct = (int) (((float) load / cap) * 100);
            h.pbLoad.setProgress(Math.max(0, Math.min(100, pct)));
        } else {
            h.pbLoad.setProgress(0);
        }

        if (pickMode) {
            // Whole card is clickable to pick
            h.btnEdit.setVisibility(View.GONE);
            h.btnAssignDriver.setVisibility(View.GONE);
            h.btnDelete.setVisibility(View.GONE);
            h.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onEdit(t);
            });
        } else {
            h.btnEdit.setVisibility(View.VISIBLE);
            h.btnAssignDriver.setVisibility(View.VISIBLE);
            h.btnDelete.setVisibility(View.VISIBLE);
            h.itemView.setOnClickListener(null);
            h.btnEdit.setOnClickListener(v -> listener.onEdit(t));
            h.btnAssignDriver.setOnClickListener(v -> listener.onAssignDriver(t));
            h.btnDelete.setOnClickListener(v -> listener.onDelete(t));
        }
    }

    @Override
    public int getItemCount() { return tanks.size(); }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvRegistration, tvDriverName, tvStatusBadge;
        TextView tvLoadValue, tvCapacity, tvSource, tvLastUpdated;
        ProgressBar pbLoad;
        MaterialButton btnEdit, btnAssignDriver;
        ImageButton btnDelete;

        public VH(@NonNull View v) {
            super(v);
            tvRegistration = v.findViewById(R.id.tvRegistration);
            tvDriverName = v.findViewById(R.id.tvDriverName);
            tvStatusBadge = v.findViewById(R.id.tvStatusBadge);
            tvLoadValue = v.findViewById(R.id.tvLoadValue);
            tvCapacity = v.findViewById(R.id.tvCapacity);
            tvSource = v.findViewById(R.id.tvSource);
            tvLastUpdated = v.findViewById(R.id.tvLastUpdated);
            pbLoad = v.findViewById(R.id.pbLoad);
            btnEdit = v.findViewById(R.id.btnEdit);
            btnAssignDriver = v.findViewById(R.id.btnAssignDriver);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }
}