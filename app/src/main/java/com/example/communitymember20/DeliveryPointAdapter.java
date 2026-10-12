package com.example.communitymember20;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class DeliveryPointAdapter extends RecyclerView.Adapter<DeliveryPointAdapter.ViewHolder> {

    private List<DeliveryPointModel> points;

    public DeliveryPointAdapter(List<DeliveryPointModel> points) {
        this.points = points;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_delivery_point, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeliveryPointModel item = points.get(position);

        holder.tvStopNumber.setText(String.valueOf(item.stopNumber));
        holder.tvLocation.setText(item.location);
        holder.tvDistance.setText(String.format("%.1f km", item.distanceKm));
        holder.tvEta.setText(item.etaMinutes + " min");

        // Demand level
        if (item.demandLevel != null) {
            switch (item.demandLevel.toLowerCase()) {
                case "high":
                    holder.tvDemand.setText("High demand");
                    holder.demandDot.setBackgroundResource(R.drawable.bg_dot_red);
                    break;
                case "medium":
                    holder.tvDemand.setText("Medium demand");
                    holder.demandDot.setBackgroundResource(R.drawable.bg_dot_orange);
                    break;
                default:
                    holder.tvDemand.setText("Low demand");
                    holder.demandDot.setBackgroundResource(R.drawable.bg_dot_green);
                    break;
            }
        }

        // Status — Completed / Current / Upcoming
        if (item.status != null) {
            switch (item.status.toLowerCase()) {
                case "completed":
                    holder.statusCircle.setBackgroundResource(R.drawable.bg_stop_completed);
                    holder.tvStopNumber.setText("✓");
                    holder.tvStopNumber.setTextColor(Color.WHITE);
                    holder.tvLocation.setTextColor(Color.parseColor("#94A3B8"));
                    break;
                case "current":
                    holder.statusCircle.setBackgroundResource(R.drawable.bg_stop_current);
                    holder.tvStopNumber.setTextColor(Color.WHITE);
                    holder.tvLocation.setTextColor(Color.parseColor("#0F172A"));
                    break;
                default: // upcoming
                    holder.statusCircle.setBackgroundResource(R.drawable.bg_stop_number);
                    holder.tvStopNumber.setTextColor(Color.parseColor("#0F766E"));
                    holder.tvLocation.setTextColor(Color.parseColor("#0F172A"));
                    break;
            }
        }
    }

    @Override
    public int getItemCount() {
        return points.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        FrameLayout statusCircle;
        TextView tvStopNumber, tvLocation, tvDemand, tvDistance, tvEta;
        View demandDot;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            statusCircle = itemView.findViewById(R.id.statusCircle);
            tvStopNumber = itemView.findViewById(R.id.tvStopNumber);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvDemand = itemView.findViewById(R.id.tvDemand);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            tvEta = itemView.findViewById(R.id.tvEta);
            demandDot = itemView.findViewById(R.id.demandDot);
        }
    }
}