package com.example.communitymember20;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationHistoryAdapter extends RecyclerView.Adapter<NotificationHistoryAdapter.VH> {

    private final List<NotificationModel> list;
    private final Context context; // optional, some callers pass it

    // ✅ Constructor A — 1 argument (community member notifications)
    public NotificationHistoryAdapter(List<NotificationModel> list) {
        this.list = list;
        this.context = null;
    }

    // ✅ Constructor B — 2 arguments (admin SendNotifications screen)
    public NotificationHistoryAdapter(Context context, List<NotificationModel> list) {
        this.list = list;
        this.context = context;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        NotificationModel m = list.get(position);
        if (m == null) return;

        // Message
        if (h.tvMessage != null) {
            h.tvMessage.setText(m.message != null ? m.message : "");
        }

        // Time
        if (h.tvTime != null) {
            if (m.timestamp > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, HH:mm",
                        Locale.getDefault());
                h.tvTime.setText(sdf.format(new Date(m.timestamp)));
            } else {
                h.tvTime.setText("—");
            }
        }

        // Recipient line
        String recipientLine = buildRecipientLine(m);
        if (h.tvRecipient != null) {
            if (recipientLine.isEmpty()) {
                h.tvRecipient.setVisibility(View.GONE);
            } else {
                h.tvRecipient.setVisibility(View.VISIBLE);
                h.tvRecipient.setText(recipientLine);
            }
        }

        // Audience badge
        if (h.tvAudienceBadge != null) {
            String audience = m.targetAudience != null ? m.targetAudience : "All Users";
            h.tvAudienceBadge.setText(audience.toUpperCase());
            h.tvAudienceBadge.setVisibility(View.VISIBLE);

            switch (audience.toLowerCase()) {
                case "specific user":
                    h.tvAudienceBadge.setBackgroundResource(R.drawable.bg_pill_sync);
                    h.tvAudienceBadge.setTextColor(Color.parseColor("#0F766E"));
                    break;
                case "admins":
                    h.tvAudienceBadge.setBackgroundResource(R.drawable.bg_pill_red);
                    h.tvAudienceBadge.setTextColor(Color.parseColor("#DC2626"));
                    break;
                default:
                    h.tvAudienceBadge.setBackgroundResource(R.drawable.bg_pill_amber);
                    h.tvAudienceBadge.setTextColor(Color.parseColor("#C2410C"));
                    break;
            }
        }

        // Icon color by category
        if (h.ivIcon != null) {
            String category = m.getCategory();
            int color;
            switch (category) {
                case "Delivery":
                    color = Color.parseColor("#0F766E");
                    break;
                case "Status":
                    color = Color.parseColor("#0288D1");
                    break;
                default:
                    color = Color.parseColor("#F57C00");
                    break;
            }
            h.ivIcon.setColorFilter(color);
        }
    }

    private String buildRecipientLine(NotificationModel m) {
        StringBuilder sb = new StringBuilder();
        if (m.targetUserName != null && !m.targetUserName.isEmpty()) {
            sb.append(m.targetUserName);
        }
        if (m.targetUserRole != null && !m.targetUserRole.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(m.targetUserRole);
        }
        return sb.toString();
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime, tvRecipient, tvAudienceBadge;
        ImageView ivIcon;

        public VH(@NonNull View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvNotifMessage);
            tvTime = v.findViewById(R.id.tvNotifTime);
            tvRecipient = v.findViewById(R.id.tvNotifRecipient);
            tvAudienceBadge = v.findViewById(R.id.tvNotifAudience);
            ivIcon = v.findViewById(R.id.ivNotifIcon);
        }
    }
}