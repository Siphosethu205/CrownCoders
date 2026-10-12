package com.example.communitymember20;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DriverNotifications extends BaseActivity {

    private RecyclerView rvNotifications;
    private LinearLayout layoutEmpty;
    private DatabaseReference dbRef;
    private String uid;

    private List<NotificationModel> notifList;
    private NotifAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driver_notifications);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvNotifications = findViewById(R.id.rvNotifications);
        layoutEmpty = findViewById(R.id.layoutEmpty);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid();

        dbRef = FirebaseDatabase.getInstance().getReference();

        notifList = new ArrayList<>();
        adapter = new NotifAdapter(notifList);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(adapter);

        loadNotifications();
    }

    private void loadNotifications() {
        dbRef.child("notifications").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                notifList.clear();
                for (DataSnapshot s : snapshot.getChildren()) {
                    NotificationModel m = s.getValue(NotificationModel.class);
                    if (m == null) continue;
                    if (m.targetUserId == null || !m.targetUserId.equals(uid)) continue;
                    m.id = s.getKey();
                    notifList.add(m);
                }

                Collections.sort(notifList, (a, b) -> Long.compare(b.timestamp, a.timestamp));

                layoutEmpty.setVisibility(notifList.isEmpty() ? View.VISIBLE : View.GONE);
                rvNotifications.setVisibility(notifList.isEmpty() ? View.GONE : View.VISIBLE);
                adapter.notifyDataSetChanged();

                // Mark all as read once the driver opens the screen
                markAllAsRead();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    // ============================================================
    // MARK ALL AS READ — clears the badge
    // ============================================================
    private void markAllAsRead() {
        dbRef.child("notifications").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot s : snapshot.getChildren()) {
                    String targetUser = s.child("targetUserId").getValue(String.class);
                    if (targetUser != null && targetUser.equals(uid)) {
                        Boolean isRead = s.child("isRead").getValue(Boolean.class);
                        if (isRead == null || !isRead) {
                            s.getRef().child("isRead").setValue(true);
                            s.getRef().child("readAt").setValue(System.currentTimeMillis());
                        }
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    // ============================================================
    // ADAPTER
    // ============================================================
    class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.VH> {
        private final List<NotificationModel> list;
        NotifAdapter(List<NotificationModel> list) { this.list = list; }

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
        }

        @Override
        public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvMessage, tvTime;
            VH(@NonNull View v) {
                super(v);
                tvMessage = v.findViewById(R.id.tvNotifMessage);
                tvTime = v.findViewById(R.id.tvNotifTime);
            }
        }
    }
}