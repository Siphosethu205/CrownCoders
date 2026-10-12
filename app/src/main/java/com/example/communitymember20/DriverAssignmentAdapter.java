package com.example.communitymember20;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public class DriverAssignmentAdapter extends RecyclerView.Adapter<DriverAssignmentAdapter.ViewHolder> {

    public interface OnAssignClickListener {
        void onAssign(DriverForAssignmentModel driver);
    }

    private List<DriverForAssignmentModel> drivers;
    private OnAssignClickListener listener;

    public DriverAssignmentAdapter(List<DriverForAssignmentModel> drivers, OnAssignClickListener listener) {
        this.drivers = drivers;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_driver_for_assignment, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DriverForAssignmentModel d = drivers.get(position);

        holder.tvInitial.setText(d.fullName != null && !d.fullName.isEmpty()
                ? String.valueOf(d.fullName.charAt(0)).toUpperCase() : "?");
        holder.tvDriverName.setText(d.fullName != null ? d.fullName : "Unknown");
        holder.tvDriverPhone.setText(d.phone != null ? d.phone : "—");
        holder.tvTankerReg.setText(d.tankerReg != null ? d.tankerReg : "—");
        holder.tvCapacity.setText(d.capacity > 0 ? String.format("%,d L", d.capacity) : "—");
        holder.tvProgress.setText(d.stopsCompleted + "/" + d.stopsTotal + " stops");

        // ✅ Availability badge + BUTTON BLUR logic
        if (d.isAvailable) {
            holder.tvAvailability.setText("AVAILABLE");
            holder.tvAvailability.setTextColor(Color.parseColor("#0F766E"));
            holder.tvAvailability.setBackgroundResource(R.drawable.bg_pill_sync);

            // Full color, enabled
            holder.btnAssign.setEnabled(true);
            holder.btnAssign.setAlpha(1.0f);
            holder.btnAssign.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#0F766E")));
            holder.btnAssign.setText("Assign to this Emergency");
        } else {
            holder.tvAvailability.setText("BUSY");
            holder.tvAvailability.setTextColor(Color.parseColor("#C2410C"));
            holder.tvAvailability.setBackgroundResource(R.drawable.bg_pill_amber);

            // ✅ Blurred + disabled
            holder.btnAssign.setEnabled(false);
            holder.btnAssign.setAlpha(0.35f);
            holder.btnAssign.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#94A3B8")));
            holder.btnAssign.setText("Already on Assignment");
        }

        holder.btnAssign.setOnClickListener(v -> {
            if (listener != null && d.isAvailable) {
                listener.onAssign(d);
            }
        });
    }

    @Override
    public int getItemCount() {
        return drivers.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvInitial, tvDriverName, tvDriverPhone, tvAvailability;
        TextView tvTankerReg, tvCapacity, tvProgress;
        MaterialButton btnAssign;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInitial = itemView.findViewById(R.id.tvInitial);
            tvDriverName = itemView.findViewById(R.id.tvDriverName);
            tvDriverPhone = itemView.findViewById(R.id.tvDriverPhone);
            tvAvailability = itemView.findViewById(R.id.tvAvailability);
            tvTankerReg = itemView.findViewById(R.id.tvTankerReg);
            tvCapacity = itemView.findViewById(R.id.tvCapacity);
            tvProgress = itemView.findViewById(R.id.tvProgress);
            btnAssign = itemView.findViewById(R.id.btnAssign);
        }
    }
}