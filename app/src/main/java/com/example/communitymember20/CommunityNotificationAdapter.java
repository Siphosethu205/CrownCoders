package com.example.communitymember20;

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

public class CommunityNotificationAdapter extends RecyclerView.Adapter<CommunityNotificationAdapter.VH> {

    public interface OnNotificationClick {
        void onClick(NotificationModel model);
    }

    private final List<NotificationModel> list;
    private final OnNotificationClick clickListener;

    public CommunityNotificationAdapter(List<NotificationModel> list) {
        this(list, null);
    }

    public CommunityNotificationAdapter(List<NotificationModel> list,
                                        OnNotificationClick clickListener) {
        this.list = list;
        this.clickListener = clickListener;
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

        h.tvMessage.setText(m.message != null ? m.message : "");

        if (m.timestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
            h.tvTime.setText(sdf.format(new Date(m.timestamp)));
        } else {
            h.tvTime.setText("—");
        }

        // Unread indicator + bold message
        if (!m.isRead) {
            h.tvMessage.setTypeface(null, android.graphics.Typeface.BOLD);
            h.tvMessage.setTextColor(Color.parseColor("#0A2A33"));
            if (h.unreadDot != null) h.unreadDot.setVisibility(View.VISIBLE);
        } else {
            h.tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
            h.tvMessage.setTextColor(Color.parseColor("#5A7D8A"));
            if (h.unreadDot != null) h.unreadDot.setVisibility(View.GONE);
        }

        // Icon color by category
        if (h.ivIcon != null) {
            String cat = m.getCategory();
            int color;
            switch (cat) {
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

        // Click
        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(m);
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime;
        View unreadDot;
        ImageView ivIcon;

        public VH(@NonNull View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvNotifMessage);
            tvTime = v.findViewById(R.id.tvNotifTime);
            unreadDot = v.findViewById(R.id.unreadDot);
            ivIcon = v.findViewById(R.id.ivNotifIcon);
        }
    }
}
