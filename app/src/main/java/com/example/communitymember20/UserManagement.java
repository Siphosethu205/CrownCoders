package com.example.communitymember20;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UserManagement extends BaseActivity {

    private MaterialToolbar toolbar;
    private RecyclerView rvUsers;
    private LinearLayout layoutEmpty;
    private MaterialButton tabMembers, tabDrivers, tabAdmins;
    private TextInputEditText etSearch;
    private TextView tvEmpty;

    private UserManagementAdapter adapter;
    private final List<UserModel> allUsers = new ArrayList<>();
    private final List<UserModel> filteredUsers = new ArrayList<>();
    private DatabaseReference dbRef;

    private String activeNode = UserNodeHelper.NODE_MEMBERS;
    private ValueEventListener currentListener;
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_management);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvUsers = findViewById(R.id.rvUsers);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tabMembers = findViewById(R.id.tabMembers);
        tabDrivers = findViewById(R.id.tabDrivers);
        tabAdmins = findViewById(R.id.tabAdmins);
        etSearch = findViewById(R.id.etSearch);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new UserManagementAdapter(filteredUsers, this);
        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(adapter);

        dbRef = FirebaseDatabase.getInstance().getReference();

        // Search listener
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                searchQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        // Tabs
        tabMembers.setOnClickListener(v -> switchTab(UserNodeHelper.NODE_MEMBERS));
        tabDrivers.setOnClickListener(v -> switchTab(UserNodeHelper.NODE_DRIVERS));
        tabAdmins.setOnClickListener(v -> switchTab(UserNodeHelper.NODE_ADMINS));

        // Default tab
        switchTab(UserNodeHelper.NODE_MEMBERS);
    }

    private void switchTab(String node) {
        if (currentListener != null) {
            dbRef.child("users").child(activeNode).removeEventListener(currentListener);
        }

        activeNode = node;

        tabMembers.setSelected(UserNodeHelper.NODE_MEMBERS.equals(node));
        tabDrivers.setSelected(UserNodeHelper.NODE_DRIVERS.equals(node));
        tabAdmins.setSelected(UserNodeHelper.NODE_ADMINS.equals(node));

        if (tvEmpty != null) {
            switch (node) {
                case UserNodeHelper.NODE_DRIVERS:
                    tvEmpty.setText("No drivers found");
                    break;
                case UserNodeHelper.NODE_ADMINS:
                    tvEmpty.setText("No admins found");
                    break;
                default:
                    tvEmpty.setText("No members found");
                    break;
            }
        }

        loadUsers(node);
    }

    private void loadUsers(String node) {
        currentListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allUsers.clear();
                for (DataSnapshot s : snapshot.getChildren()) {
                    UserModel u = s.getValue(UserModel.class);
                    if (u != null) {
                        u.uid = s.getKey();
                        allUsers.add(u);
                    }
                }
                applyFilter();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                LocaleHelper.showTranslatedToast(UserManagement.this,
                        "Failed: " + error.getMessage(), Toast.LENGTH_SHORT);
            }
        };
        dbRef.child("users").child(node).addValueEventListener(currentListener);
    }

    private void applyFilter() {
        filteredUsers.clear();

        if (searchQuery.isEmpty()) {
            filteredUsers.addAll(allUsers);
        } else {
            for (UserModel u : allUsers) {
                // Uses the actual Firebase field names
                String name  = u.full_name != null ? u.full_name.toLowerCase(Locale.ROOT) : "";
                String phone = u.phone_number != null ? u.phone_number.toLowerCase(Locale.ROOT) : "";
                String email = u.email != null ? u.email.toLowerCase(Locale.ROOT) : "";
                String role  = u.role != null ? u.role.toLowerCase(Locale.ROOT) : "";

                if (name.contains(searchQuery)
                        || phone.contains(searchQuery)
                        || email.contains(searchQuery)
                        || role.contains(searchQuery)) {
                    filteredUsers.add(u);
                }
            }
        }

        if (filteredUsers.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvUsers.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvUsers.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentListener != null) {
            dbRef.child("users").child(activeNode).removeEventListener(currentListener);
        }
    }
}