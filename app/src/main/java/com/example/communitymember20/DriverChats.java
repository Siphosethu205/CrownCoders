package com.example.communitymember20;

import android.os.Bundle;

import com.google.android.material.appbar.MaterialToolbar;

public class DriverChats extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.driver_chats);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }
}