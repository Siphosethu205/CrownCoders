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

public class AuditLogAdapter extends RecyclerView.Adapter<AuditLogAdapter.ViewHolder> {

    private List<AuditLogModel> logList;

    public AuditLogAdapter(List<AuditLogModel> logList) {
        this.logList = logList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_audit_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AuditLogModel item = logList.get(position);

        holder.tvAction.setText(item.action);
        holder.tvDetails.setText(item.details);
        holder.tvPerformedBy.setText("By: " + item.performedBy);

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        holder.tvTimestamp.setText(sdf.format(new Date(item.timestamp)));

        // Dynamic Color Coding based on the Action
        if (item.action.equals("EMERGENCY_REQUEST")) {
            holder.colorStrip.setBackgroundColor(Color.parseColor("#D32F2F")); // Red
            holder.tvAction.setTextColor(Color.parseColor("#D32F2F"));
        } else if (item.action.equals("NOTIFICATION_SENT")) {
            holder.colorStrip.setBackgroundColor(Color.parseColor("#388E3C")); // Green
            holder.tvAction.setTextColor(Color.parseColor("#388E3C"));
        } else if (item.action.equals("WATER_REPORT")) {
            holder.colorStrip.setBackgroundColor(Color.parseColor("#1976D2")); // Blue
            holder.tvAction.setTextColor(Color.parseColor("#1976D2"));
        } else {
            holder.colorStrip.setBackgroundColor(Color.parseColor("#757575")); // Grey fallback
            holder.tvAction.setTextColor(Color.parseColor("#757575"));
        }
    }

    @Override
    public int getItemCount() {
        return logList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAction, tvDetails, tvPerformedBy, tvTimestamp;
        View colorStrip;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAction = itemView.findViewById(R.id.tvAction);
            tvDetails = itemView.findViewById(R.id.tvDetails);
            tvPerformedBy = itemView.findViewById(R.id.tvPerformedBy);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            colorStrip = itemView.findViewById(R.id.colorStrip);
        }
    }
}