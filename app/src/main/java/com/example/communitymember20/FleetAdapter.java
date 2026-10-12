package com.example.communitymember20;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class FleetAdapter extends RecyclerView.Adapter<FleetAdapter.ViewHolder> {

    private List<FleetModel> fleetList;

    public FleetAdapter(List<FleetModel> fleetList) {
        this.fleetList = fleetList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fleet_snapshot, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FleetModel item = fleetList.get(position);

        holder.tvTruckId.setText(item.id);
        holder.tvLocation.setText(item.location);
        holder.tvStatusBadge.setText(item.status);

        // Dynamically set badge color based on status
        if (item.status.equalsIgnoreCase("IDLE")) {
            holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_status_pending); // Using orange for idle as per design
        } else if (item.status.equalsIgnoreCase("EN ROUTE") || item.status.equalsIgnoreCase("FILLING")) {
            holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_status_resolved); // Using green for active
        } else {
            holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_status_pending);
        }
    }

    @Override
    public int getItemCount() {
        return fleetList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTruckId, tvLocation, tvStatusBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTruckId = itemView.findViewById(R.id.tvTruckId);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
        }
    }
}
