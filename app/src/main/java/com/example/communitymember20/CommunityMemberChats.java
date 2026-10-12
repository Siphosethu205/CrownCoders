package com.example.communitymember20;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class CommunityMemberChats extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.community_member_chats);

        // Setup Bottom Navigation so the user can navigate away from this screen
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_chats); // Highlight the Chats tab

            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    startActivity(new Intent(this, CommunityMemberDashboard.class));
                    return true;
                } else if (itemId == R.id.nav_chats) {
                    return true; // Already here
                } else if (itemId == R.id.nav_notifications) {
                    startActivity(new Intent(this, CommunityMemberNotifications.class));
                    return true;
                } else if (itemId == R.id.nav_menu) {
                    startActivity(new Intent(this, Menu.class));
                    return true;
                }
                return false;
            });
        }
    }
}