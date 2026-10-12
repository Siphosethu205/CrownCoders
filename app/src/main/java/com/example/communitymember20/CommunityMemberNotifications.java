package com.example.communitymember20;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommunityMemberNotifications extends BaseActivity {

    private static final String TAG = "MemberNotifications";

    private RecyclerView rvNotifications;
    private LinearLayout layoutEmpty;
    private TextView tvEmpty, tvEmptySub;
    private BottomNavigationView bottomNav;
    private ChipGroup chipGroupFilters;

    private CommunityNotificationAdapter adapter;
    private List<NotificationModel> allNotifications;   // master list
    private List<NotificationModel> filteredList;       // what's shown
    private DatabaseReference dbRef;
    private String currentUid;

    private String activeFilter = "All"; // All / Unread / Delivery / Status

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.community_member_notifications);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        currentUid = user.getUid();

        // ----- Bottom Nav -----
        bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_notifications);
            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    startActivity(new Intent(this, CommunityMemberDashboard.class));
                    return true;
                } else if (itemId == R.id.nav_chats) {
                    startActivity(new Intent(this, CommunityMemberChats.class));
                    return true;
                } else if (itemId == R.id.nav_notifications) {
                    return true;
                } else if (itemId == R.id.nav_menu) {
                    startActivity(new Intent(this, Menu.class));
                    return true;
                }
                return false;
            });

            NotificationBadgeHelper.clearBadge(bottomNav);
        }

        // ----- Views -----
        rvNotifications = findViewById(R.id.rvNotifications);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvEmptySub = findViewById(R.id.tvEmptySub);
        chipGroupFilters = findViewById(R.id.chipGroupFilters);

        allNotifications = new ArrayList<>();
        filteredList = new ArrayList<>();
        adapter = new CommunityNotificationAdapter(filteredList);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(adapter);

        // ----- Swipe to delete -----
        attachSwipeToDelete();

        // ----- Filter chips -----
        attachFilterChips();

        // ----- Mark all read button -----
        View btnMarkAll = findViewById(R.id.btnMarkAllRead);
        if (btnMarkAll != null) {
            btnMarkAll.setOnClickListener(v -> {
                NotificationBadgeHelper.markAllAsRead(this);
                NotificationBadgeHelper.clearBadge(bottomNav);
                Toast.makeText(this, "All marked as read", Toast.LENGTH_SHORT).show();
            });
        }

        // ----- Clear all button -----
        View btnClearAll = findViewById(R.id.btnClearAll);
        if (btnClearAll != null) {
            btnClearAll.setOnClickListener(v -> confirmClearAll());
        }

        // ----- Fetch notifications -----
        dbRef = FirebaseDatabase.getInstance().getReference("notifications");
        fetchNotifications();
    }

    // ============================================================
    // FETCH
    // ============================================================
    private void fetchNotifications() {
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allNotifications.clear();

                for (DataSnapshot data : snapshot.getChildren()) {
                    String notifId = data.getKey();
                    String message = data.child("message").getValue(String.class);
                    String audience = data.child("targetAudience").getValue(String.class);
                    String targetUserId = data.child("targetUserId").getValue(String.class);
                    String targetUserName = data.child("targetUserName").getValue(String.class);
                    String type = data.child("type").getValue(String.class);
                    String relatedRequestId = data.child("relatedRequestId").getValue(String.class);
                    Long timestamp = data.child("timestamp").getValue(Long.class);
                    Boolean isRead = data.child("isRead").getValue(Boolean.class);

                    // Filter for THIS user only (not broadcasts)
                    boolean isBroadcast = audience != null && (
                            audience.equalsIgnoreCase("All Users") ||
                                    audience.equalsIgnoreCase("Community Members"));

                    boolean isPersonal = currentUid != null
                            && targetUserId != null
                            && currentUid.equals(targetUserId);

                    if (!isBroadcast && !isPersonal) continue;

                    NotificationModel model = new NotificationModel();
                    model.id = notifId;
                    model.message = message != null ? message : "";
                    model.targetAudience = audience;
                    model.targetUserId = targetUserId;
                    model.targetUserName = targetUserName;
                    model.type = type;
                    model.relatedRequestId = relatedRequestId;
                    model.timestamp = timestamp != null ? timestamp : 0L;
                    model.isRead = isRead != null && isRead;

                    allNotifications.add(model);
                }

                // Sort newest first
                Collections.sort(allNotifications, (a, b) ->
                        Long.compare(b.timestamp, a.timestamp));

                applyFilter();

                // Mark all as read after a short delay
                if (!allNotifications.isEmpty()) {
                    rvNotifications.postDelayed(() -> {
                        NotificationBadgeHelper.markAllAsRead(
                                CommunityMemberNotifications.this);
                        NotificationBadgeHelper.clearBadge(bottomNav);
                    }, 1500);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Load failed: " + error.getMessage());
                Toast.makeText(CommunityMemberNotifications.this,
                        "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ============================================================
    // FILTER
    // ============================================================
    private void attachFilterChips() {
        if (chipGroupFilters == null) return;

        chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);

            if (id == R.id.chipUnread) activeFilter = "Unread";
            else if (id == R.id.chipDelivery) activeFilter = "Delivery";
            else if (id == R.id.chipStatus) activeFilter = "Status";
            else activeFilter = "All";

            applyFilter();
        });
    }

    private void applyFilter() {
        filteredList.clear();

        for (NotificationModel m : allNotifications) {
            switch (activeFilter) {
                case "Unread":
                    if (!m.isRead) filteredList.add(m);
                    break;
                case "Delivery":
                    if ("Delivery".equals(m.getCategory())) filteredList.add(m);
                    break;
                case "Status":
                    if ("Status".equals(m.getCategory())) filteredList.add(m);
                    break;
                default:
                    filteredList.add(m);
                    break;
            }
        }

        adapter.notifyDataSetChanged();
        toggleEmptyState();
    }

    private void toggleEmptyState() {
        if (filteredList.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);

            if (tvEmpty != null) {
                switch (activeFilter) {
                    case "Unread":
                        tvEmpty.setText("No unread notifications");
                        break;
                    case "Delivery":
                        tvEmpty.setText("No delivery notifications");
                        break;
                    case "Status":
                        tvEmpty.setText("No status notifications");
                        break;
                    default:
                        tvEmpty.setText("No notifications yet");
                }
            }
            if (tvEmptySub != null) {
                tvEmptySub.setText("You're all caught up!");
            }
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvNotifications.setVisibility(View.VISIBLE);
        }
    }

    // ============================================================
    // SWIPE-TO-DELETE
    // ============================================================
    private void attachSwipeToDelete() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                if (pos < 0 || pos >= filteredList.size()) return;

                NotificationModel model = filteredList.get(pos);

                new MaterialAlertDialogBuilder(CommunityMemberNotifications.this)
                        .setTitle("Delete notification?")
                        .setMessage("This will remove it permanently.")
                        .setPositiveButton("Delete", (d, w) -> {
                            dbRef.child(model.id).removeValue()
                                    .addOnSuccessListener(aVoid -> {
                                        allNotifications.remove(model);
                                        filteredList.remove(pos);
                                        adapter.notifyItemRemoved(pos);
                                        toggleEmptyState();
                                        Toast.makeText(CommunityMemberNotifications.this,
                                                "Deleted", Toast.LENGTH_SHORT).show();
                                    });
                        })
                        .setNegativeButton("Cancel", (d, w) -> {
                            adapter.notifyItemChanged(pos);
                        })
                        .setCancelable(false)
                        .show();
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(rvNotifications);
    }

    // ============================================================
    // CLEAR ALL
    // ============================================================
    private void confirmClearAll() {
        if (allNotifications.isEmpty()) {
            Toast.makeText(this, "Nothing to clear", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear all notifications?")
                .setMessage("This will permanently delete all your notifications. This cannot be undone.")
                .setPositiveButton("Clear All", (d, w) -> clearAllNotifications())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearAllNotifications() {
        dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int deleted = 0;
                for (DataSnapshot s : snapshot.getChildren()) {
                    String targetUserId = s.child("targetUserId").getValue(String.class);
                    if (targetUserId != null && targetUserId.equals(currentUid)) {
                        s.getRef().removeValue();
                        deleted++;
                    }
                }

                allNotifications.clear();
                filteredList.clear();
                adapter.notifyDataSetChanged();
                toggleEmptyState();

                NotificationBadgeHelper.clearBadge(bottomNav);

                Toast.makeText(CommunityMemberNotifications.this,
                        "Cleared " + deleted + " notifications", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }
}