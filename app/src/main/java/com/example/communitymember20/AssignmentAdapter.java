package com.example.communitymember20;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AssignmentAdapter extends RecyclerView.Adapter<AssignmentAdapter.ViewHolder> {

    private List<TankerAssignmentModel> list;

    public AssignmentAdapter(List<TankerAssignmentModel> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_assignment, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        TankerAssignmentModel m = list.get(position);

        h.tvEmergencyTitle.setText(m.emergencyRequestTitle != null ? m.emergencyRequestTitle : "—");
        h.tvEmergencyLocation.setText(m.emergencyRequestLocation != null ? m.emergencyRequestLocation : "—");
        h.tvDriverName.setText(m.driverName != null ? m.driverName : "—");
        h.tvTankerReg.setText(m.tankerReg != null ? m.tankerReg : "—");

        h.tvLitres.setText(m.litresLoaded > 0 ? String.format("%,d L", m.litresLoaded) : "—");

        String assignedStr = m.assignedDate;
        if (assignedStr == null && m.assignedAt > 0) {
            assignedStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(new Date(m.assignedAt));
        }
        h.tvAssignedAt.setText(assignedStr != null ? assignedStr : "—");

        if (m.lastUpdateAt > 0) {
            long diff = System.currentTimeMillis() - m.lastUpdateAt;
            long mins = diff / 60000;
            if (mins < 1) h.tvLastUpdate.setText("just now");
            else if (mins < 60) h.tvLastUpdate.setText(mins + " min ago");
            else if (mins < 1440) h.tvLastUpdate.setText((mins / 60) + " h ago");
            else h.tvLastUpdate.setText((mins / 1440) + " d ago");
        } else {
            h.tvLastUpdate.setText("—");
        }

        if (m.lastNote != null && !m.lastNote.isEmpty()) {
            h.tvNote.setVisibility(View.VISIBLE);
            h.tvNote.setText("Note: " + m.lastNote);
        } else {
            h.tvNote.setVisibility(View.GONE);
        }

        String status = m.status != null ? m.status.toUpperCase() : "ASSIGNED";
        h.tvStatus.setText(status);
        switch (status) {
            case "DELIVERED":
                h.tvStatus.setBackgroundResource(R.drawable.bg_pill_sync);
                h.tvStatus.setTextColor(Color.parseColor("#0F766E"));
                break;
            case "EN ROUTE":
                h.tvStatus.setBackgroundResource(R.drawable.bg_pill_amber);
                h.tvStatus.setTextColor(Color.parseColor("#C2410C"));
                break;
            case "CANCELLED":
                h.tvStatus.setBackgroundResource(R.drawable.bg_pill_red);
                h.tvStatus.setTextColor(Color.parseColor("#DC2626"));
                break;
            default:
                h.tvStatus.setBackgroundResource(R.drawable.bg_pill_amber);
                h.tvStatus.setTextColor(Color.parseColor("#C2410C"));
                break;
        }
    }

    @Override
    public int getItemCount() { return list.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEmergencyTitle, tvEmergencyLocation, tvStatus;
        TextView tvDriverName, tvTankerReg, tvLitres;
        TextView tvAssignedAt, tvLastUpdate, tvNote;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmergencyTitle = itemView.findViewById(R.id.tvEmergencyTitle);
            tvEmergencyLocation = itemView.findViewById(R.id.tvEmergencyLocation);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvDriverName = itemView.findViewById(R.id.tvDriverName);
            tvTankerReg = itemView.findViewById(R.id.tvTankerReg);
            tvLitres = itemView.findViewById(R.id.tvLitres);
            tvAssignedAt = itemView.findViewById(R.id.tvAssignedAt);
            tvLastUpdate = itemView.findViewById(R.id.tvLastUpdate);
            tvNote = itemView.findViewById(R.id.tvNote);
        }
    }
}
