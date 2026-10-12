package com.example.communitymember20;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class NotificationBadgeHelper {

    // Cache listeners so we don't double-register
    private static ValueEventListener cachedListener;
    private static DatabaseReference cachedRef;

    /**
     * Refreshes the notification badge on the given BottomNavigationView.
     * Auto-detects whether it's the driver or member menu by checking which menu item exists.
     */
    public static void refresh(Context context, BottomNavigationView bottomNav) {
        if (bottomNav == null || context == null) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();

        // Detach previous listener to avoid stacking
        if (cachedListener != null && cachedRef != null) {
            cachedRef.removeEventListener(cachedListener);
        }

        cachedRef = FirebaseDatabase.getInstance().getReference("notifications");

        cachedListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int unreadCount = 0;

                for (DataSnapshot s : snapshot.getChildren()) {
                    String targetUser = s.child("targetUserId").getValue(String.class);
                    String targetAudience = s.child("targetAudience").getValue(String.class);

                    // Count for specific user
                    boolean isForMe = targetUser != null && targetUser.equals(uid);

                    // Also count admin-wide notifications (targetAudience = "Admins")
                    boolean isForAdmins = "Admins".equalsIgnoreCase(targetAudience);

                    // Skip broadcast notifications (those aren't personal alerts)
                    boolean isBroadcast = "All Users".equalsIgnoreCase(targetAudience)
                            || "Community Members".equalsIgnoreCase(targetAudience)
                            || "Tanker Drivers".equalsIgnoreCase(targetAudience);

                    if (!isForMe && !isForAdmins) continue;

                    // Skip if already read
                    Boolean isRead = s.child("isRead").getValue(Boolean.class);
                    if (isRead != null && isRead) continue;

                    unreadCount++;
                }

                final int finalCount = unreadCount;

                // Find the correct menu item to badge
                int badgeItemId = -1;
                if (bottomNav.getMenu().findItem(R.id.nav_driver_alerts) != null) {
                    badgeItemId = R.id.nav_driver_alerts;
                } else if (bottomNav.getMenu().findItem(R.id.nav_notifications) != null) {
                    badgeItemId = R.id.nav_notifications;
                }

                if (badgeItemId == -1) return;
                final int itemId = badgeItemId;

                bottomNav.post(() -> {
                    if (finalCount > 0) {
                        BadgeDrawable badge = bottomNav.getOrCreateBadge(itemId);
                        badge.setVisible(true);
                        badge.setNumber(finalCount);
                        badge.setBackgroundColor(0xFFDC2626);
                        badge.setBadgeTextColor(0xFFFFFFFF);
                    } else {
                        bottomNav.removeBadge(itemId);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };

        cachedRef.addValueEventListener(cachedListener);
    }

    /**
     * Mark all of the current user's notifications as read.
     * Also clears the badge.
     * Called from the Notifications screen after the user has seen them.
     */
    public static void markAllAsRead(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("notifications");

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot s : snapshot.getChildren()) {
                    String targetUser = s.child("targetUserId").getValue(String.class);

                    // Only mark notifications targeted directly at this user
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

    /**
     * Clear the badge immediately (e.g. when the user taps the Notifications tab).
     * The DB write is handled by markAllAsRead().
     */
    public static void clearBadge(BottomNavigationView bottomNav) {
        if (bottomNav == null) return;

        int badgeItemId = -1;
        if (bottomNav.getMenu().findItem(R.id.nav_driver_alerts) != null) {
            badgeItemId = R.id.nav_driver_alerts;
        } else if (bottomNav.getMenu().findItem(R.id.nav_notifications) != null) {
            badgeItemId = R.id.nav_notifications;
        }

        if (badgeItemId != -1) {
            final int itemId = badgeItemId;
            bottomNav.post(() -> bottomNav.removeBadge(itemId));
        }
    }

    /**
     * Optional: call this in onDestroy to clean up.
     */
    public static void detach() {
        if (cachedListener != null && cachedRef != null) {
            cachedRef.removeEventListener(cachedListener);
        }
        cachedListener = null;
        cachedRef = null;
    }
}