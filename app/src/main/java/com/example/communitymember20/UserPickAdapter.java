package com.example.communitymember20;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class UserPickAdapter extends RecyclerView.Adapter<UserPickAdapter.ViewHolder> {

    public interface OnUserPick {
        void onPick(UserPickModel user);
    }

    private List<UserPickModel> users;
    private OnUserPick listener;

    public UserPickAdapter(List<UserPickModel> users, OnUserPick listener) {
        this.users = users;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_pick, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        UserPickModel u = users.get(position);

        String name = u.fullName != null ? u.fullName : "Unknown";
        h.tvName.setText(name);
        h.tvEmail.setText(u.email != null ? u.email : u.phone != null ? u.phone : "—");
        h.tvInitial.setText(!name.isEmpty()
                ? String.valueOf(name.charAt(0)).toUpperCase() : "?");

        String role = u.role != null ? u.role : "—";
        h.tvRole.setText(role.toUpperCase());

        // Color the role pill differently per role
        switch (role.toLowerCase()) {
            case "driver":
                h.tvRole.setBackgroundResource(R.drawable.bg_pill_amber);
                h.tvRole.setTextColor(Color.parseColor("#C2410C"));
                break;
            case "admin":
                h.tvRole.setBackgroundResource(R.drawable.bg_pill_red);
                h.tvRole.setTextColor(Color.parseColor("#DC2626"));
                break;
            case "authority":
                h.tvRole.setBackgroundResource(R.drawable.bg_pill_red);
                h.tvRole.setTextColor(Color.parseColor("#DC2626"));
                break;
            default:
                h.tvRole.setBackgroundResource(R.drawable.bg_pill_sync);
                h.tvRole.setTextColor(Color.parseColor("#0F766E"));
                break;
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPick(u);
        });
    }

    @Override
    public int getItemCount() { return users.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvInitial, tvName, tvEmail, tvRole;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInitial = itemView.findViewById(R.id.tvInitial);
            tvName = itemView.findViewById(R.id.tvName);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvRole = itemView.findViewById(R.id.tvRole);
        }
    }
}