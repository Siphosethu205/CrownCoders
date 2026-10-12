package com.example.communitymember20;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class Menu extends BaseActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private BottomNavigationView bottomNav;
    private MaterialToolbar topAppBar;

    // UPDATED: Changed from LinearLayout to MaterialCardView to match XML
    private MaterialCardView btnProfileSettings, btnMyReports, btnMyEmergencyRequests, btnAboutUs, btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);

        // 1. Initialize Main UI Elements
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        bottomNav = findViewById(R.id.bottomNav);
        topAppBar = findViewById(R.id.topAppBar);

        // 2. Initialize Body Cards (UPDATED: now MaterialCardView)
        btnProfileSettings = findViewById(R.id.btnProfileSettings);
        btnMyReports = findViewById(R.id.btnMyReports);
        btnMyEmergencyRequests = findViewById(R.id.btnMyEmergencyRequests);
        btnAboutUs = findViewById(R.id.btnAboutUs);
        btnLogout = findViewById(R.id.btnLogout);

        // 3. Top App Bar — hamburger opens drawer
        topAppBar.setNavigationOnClickListener(v -> {
            if (drawerLayout != null) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        // 4. Translate the drawer menu items (they aren't in the view hierarchy
        //    so BaseActivity's translateAllViews can't reach them automatically)
        translateDrawerMenu();

        // 5. Side Navigation Drawer clicks
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_drawer_profile) {
                startActivity(new Intent(Menu.this, ProfileSettings.class));
            } else if (id == R.id.nav_drawer_my_reports) {
                startActivity(new Intent(Menu.this, MyWaterStatusUpdates.class));
            } else if (id == R.id.nav_drawer_my_emergency) {
                startActivity(new Intent(Menu.this, MyEmergencyRequests.class));
            } else if (id == R.id.nav_drawer_about) {
                startActivity(new Intent(Menu.this, AboutUs.class));
            } else if (id == R.id.nav_drawer_logout) {
                showLogoutDialog();
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // 6. Bottom Navigation
        bottomNav.setSelectedItemId(R.id.nav_menu);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                Intent intent = new Intent(Menu.this, CommunityMemberDashboard.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.nav_chats) {
                startActivity(new Intent(Menu.this, CommunityMemberChats.class));
                return true;
            } else if (itemId == R.id.nav_notifications) {
                startActivity(new Intent(Menu.this, CommunityMemberNotifications.class));
                return true;
            } else if (itemId == R.id.nav_menu) {
                return true;
            }
            return false;
        });

        // 7. Visible Body Card Clicks
        btnProfileSettings.setOnClickListener(v -> startActivity(new Intent(Menu.this, ProfileSettings.class)));
        btnMyReports.setOnClickListener(v -> startActivity(new Intent(Menu.this, MyWaterStatusUpdates.class)));
        btnMyEmergencyRequests.setOnClickListener(v -> startActivity(new Intent(Menu.this, MyEmergencyRequests.class)));
        btnAboutUs.setOnClickListener(v -> startActivity(new Intent(Menu.this, AboutUs.class)));
        btnLogout.setOnClickListener(v -> showLogoutDialog());

        // 8. Back Press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    // ============================================================
    // TRANSLATE DRAWER MENU
    // ============================================================
    /**
     * NavigationView menu items live in a menu resource, not the view hierarchy.
     * BaseActivity's translateAllViews can't reach them, so we walk each item
     * and translate its title via LocaleHelper.
     */
    private void translateDrawerMenu() {
        if (navigationView == null) return;
        android.view.Menu menu = navigationView.getMenu();
        if (menu == null) return;

        // Collect the original English titles first
        int[] itemIds = {
                R.id.nav_drawer_profile,
                R.id.nav_drawer_my_reports,
                R.id.nav_drawer_my_emergency,
                R.id.nav_drawer_about,
                R.id.nav_drawer_logout
        };

        final String[] englishTitles = new String[itemIds.length];
        final String[] translatedTitles = new String[itemIds.length];
        final int[] remaining = {itemIds.length};

        for (int i = 0; i < itemIds.length; i++) {
            final int index = i;
            MenuItem item = menu.findItem(itemIds[i]);
            if (item == null) {
                remaining[0]--;
                continue;
            }
            String title = item.getTitle() != null ? item.getTitle().toString() : "";
            englishTitles[index] = title;

            LocaleHelper.translateStringApi(this, title, result -> {
                translatedTitles[index] = result;
                remaining[0]--;
                if (remaining[0] == 0) {
                    runOnUiThread(() -> applyTranslatedDrawerTitles(menu, itemIds, translatedTitles));
                }
            });
        }

        // Handle the case where all items were missing
        if (remaining[0] == 0) {
            runOnUiThread(() -> applyTranslatedDrawerTitles(menu, itemIds, translatedTitles));
        }
    }

    private void applyTranslatedDrawerTitles(android.view.Menu menu, int[] itemIds, String[] titles) {
        for (int i = 0; i < itemIds.length; i++) {
            MenuItem item = menu.findItem(itemIds[i]);
            if (item != null && titles[i] != null && !titles[i].isEmpty()) {
                item.setTitle(titles[i]);
            }
        }
    }

    // ============================================================
    // LOGOUT DIALOG (translated)
    // ============================================================
    private void showLogoutDialog() {
        final String[] englishStrings = {
                "Log Out",                                              // 0 — title
                "Are you sure you want to log out of AquaComm?",        // 1 — message
                "Cancel",                                               // 2 — cancel button
                "Log Out"                                               // 3 — positive button
        };

        final String[] translated = new String[englishStrings.length];
        final int[] remaining = {englishStrings.length};

        for (int i = 0; i < englishStrings.length; i++) {
            final int index = i;
            LocaleHelper.translateStringApi(this, englishStrings[i], result -> {
                translated[index] = result;
                remaining[0]--;
                if (remaining[0] == 0) {
                    runOnUiThread(() -> buildLogoutDialog(
                            translated[0], translated[1],
                            translated[2], translated[3]));
                }
            });
        }
    }

    private void buildLogoutDialog(String title, String message,
                                   String cancelText, String logoutText) {
        new MaterialAlertDialogBuilder(Menu.this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(cancelText, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(logoutText, (dialog, which) -> {
                    dialog.dismiss();
                    performLogout();
                })
                .show();
    }

    // ============================================================
    // LOGOUT
    // ============================================================
    private void performLogout() {
        // Clear the user's session + saved preferences so the next
        // launch starts fresh
        getSharedPreferences("AquacommPrefs", MODE_PRIVATE).edit()
                .remove("user_role")
                .remove("is_guest")
                .apply();

        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(Menu.this, LoginPage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}