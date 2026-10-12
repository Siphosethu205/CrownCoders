package com.example.communitymember20;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SendNotifications extends BaseActivity {

    private Spinner spinnerAudience;
    private LinearLayout layoutSpecificRecipient, rowPickRecipient;
    private TextView tvRecipientName, tvRecipientSub, tvRecipientInitial;
    private TextInputEditText etMessage;
    private MaterialButton btnSend;
    private RecyclerView rvHistory;
    private NotificationHistoryAdapter adapter;
    private List<NotificationModel> historyList;
    private DatabaseReference dbRef;

    private String selectedUserId = null;
    private String selectedUserName = null;
    private String selectedUserRole = null;

    private final String[] audiences = {
            "All Users",
            "Community Members",
            "Tanker Drivers",
            "Specific User"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.send_notifications);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        spinnerAudience = findViewById(R.id.spinnerAudience);
        layoutSpecificRecipient = findViewById(R.id.layoutSpecificRecipient);
        rowPickRecipient = findViewById(R.id.rowPickRecipient);
        tvRecipientName = findViewById(R.id.tvRecipientName);
        tvRecipientSub = findViewById(R.id.tvRecipientSub);
        tvRecipientInitial = findViewById(R.id.tvRecipientInitial);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        rvHistory = findViewById(R.id.rvHistory);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, audiences);
        spinnerAudience.setAdapter(spinnerAdapter);

        spinnerAudience.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateRecipientSectionVisibility();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        rowPickRecipient.setOnClickListener(v -> openUserPicker());

        historyList = new ArrayList<>();
        adapter = new NotificationHistoryAdapter(this, historyList);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(adapter);

        dbRef = FirebaseDatabase.getInstance().getReference("notifications");

        fetchNotificationHistory();
        btnSend.setOnClickListener(v -> sendNotification());
    }

    private void updateRecipientSectionVisibility() {
        String selected = spinnerAudience.getSelectedItem().toString();
        if ("Specific User".equals(selected)) {
            layoutSpecificRecipient.setVisibility(View.VISIBLE);
        } else {
            layoutSpecificRecipient.setVisibility(View.GONE);
            selectedUserId = null;
            selectedUserName = null;
            selectedUserRole = null;
        }
    }

    private void openUserPicker() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pick_recipient, null);
        TextInputEditText etSearch = dialogView.findViewById(R.id.etSearchUser);
        ChipGroup chipGroupRoles = dialogView.findViewById(R.id.chipGroupRoles);
        RecyclerView rvUsers = dialogView.findViewById(R.id.rvUsers);
        TextView tvNoUsers = dialogView.findViewById(R.id.tvNoUsers);

        final SendNotifications activity = this;

        // Walk all three nodes
        dbRef.getRoot().child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<UserPickModel> allUsers = new ArrayList<>();

                for (String node : UserNodeHelper.allNodes()) {
                    DataSnapshot nodeSnap = snapshot.child(node);
                    for (DataSnapshot u : nodeSnap.getChildren()) {
                        UserPickModel model = new UserPickModel();
                        model.uid = u.getKey();
                        model.fullName = u.child("full_name").getValue(String.class);
                        model.email = u.child("email").getValue(String.class);
                        model.phone = u.child("phone_number").getValue(String.class);
                        model.role = u.child("role").getValue(String.class);
                        allUsers.add(model);
                    }
                }

                Collections.sort(allUsers, (a, b) -> {
                    String n1 = a.fullName != null ? a.fullName : "";
                    String n2 = b.fullName != null ? b.fullName : "";
                    return n1.compareToIgnoreCase(n2);
                });

                List<UserPickModel> filtered = new ArrayList<>(allUsers);

                final AlertDialog dialog = new AlertDialog.Builder(activity)
                        .setTitle("Select Recipient")
                        .setView(dialogView)
                        .setNegativeButton("Cancel", null)
                        .create();

                UserPickAdapter pickAdapter = new UserPickAdapter(filtered, user -> {
                    selectedUserId = user.uid;
                    selectedUserName = user.fullName;
                    selectedUserRole = user.role;

                    tvRecipientName.setText(user.fullName != null ? user.fullName : "Unknown");
                    tvRecipientSub.setText((user.role != null ? user.role : "User")
                            + (user.email != null ? " · " + user.email : ""));
                    tvRecipientInitial.setText(user.fullName != null && !user.fullName.isEmpty()
                            ? String.valueOf(user.fullName.charAt(0)).toUpperCase() : "?");

                    dialog.dismiss();
                });

                rvUsers.setLayoutManager(new LinearLayoutManager(activity));
                rvUsers.setAdapter(pickAdapter);

                final String[] activeRoleFilter = { null };
                final String[] searchQuery = { "" };

                Runnable applyFilters = () -> {
                    filtered.clear();
                    for (UserPickModel u : allUsers) {
                        if (activeRoleFilter[0] != null) {
                            if (u.role == null) continue;
                            if (!u.role.toLowerCase(Locale.ROOT).contains(activeRoleFilter[0])) continue;
                        }
                        if (!searchQuery[0].isEmpty()) {
                            String name = u.fullName != null ? u.fullName.toLowerCase(Locale.ROOT) : "";
                            String email = u.email != null ? u.email.toLowerCase(Locale.ROOT) : "";
                            String phone = u.phone != null ? u.phone.toLowerCase(Locale.ROOT) : "";
                            if (!name.contains(searchQuery[0])
                                    && !email.contains(searchQuery[0])
                                    && !phone.contains(searchQuery[0])) {
                                continue;
                            }
                        }
                        filtered.add(u);
                    }
                    pickAdapter.notifyDataSetChanged();
                    tvNoUsers.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                };

                etSearch.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
                    @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                        searchQuery[0] = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                        applyFilters.run();
                    }
                    @Override public void afterTextChanged(Editable s) { }
                });

                chipGroupRoles.setOnCheckedStateChangeListener((group, checkedIds) -> {
                    if (checkedIds.isEmpty()) {
                        activeRoleFilter[0] = null;
                    } else {
                        int id = checkedIds.get(0);
                        if (id == R.id.chipRoleAll) activeRoleFilter[0] = null;
                        else if (id == R.id.chipRoleDriver) activeRoleFilter[0] = "driver";
                        else if (id == R.id.chipRoleMember) activeRoleFilter[0] = "member";
                        else if (id == R.id.chipRoleAdmin) activeRoleFilter[0] = "admin";
                        else activeRoleFilter[0] = null;
                    }
                    applyFilters.run();
                });

                dialog.show();
                tvNoUsers.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                LocaleHelper.showTranslatedToast(activity,
                        "Failed to load users: " + error.getMessage(),
                        Toast.LENGTH_SHORT);
            }
        });
    }

    private void sendNotification() {
        String message = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        String audience = spinnerAudience.getSelectedItem().toString();

        if (TextUtils.isEmpty(message)) {
            etMessage.setError("Please enter a message");
            etMessage.requestFocus();
            return;
        }

        if ("Specific User".equals(audience) && TextUtils.isEmpty(selectedUserId)) {
            LocaleHelper.showTranslatedToast(this,
                    "Please pick a recipient first", Toast.LENGTH_SHORT);
            return;
        }

        String id = dbRef.push().getKey();
        if (id == null) return;

        NotificationModel notification = new NotificationModel();
        notification.id = id;
        notification.message = message;
        notification.targetAudience = audience;
        notification.timestamp = System.currentTimeMillis();

        if ("Specific User".equals(audience)) {
            notification.targetUserId = selectedUserId;
            notification.targetUserName = selectedUserName;
            notification.targetUserRole = selectedUserRole;
        }

        dbRef.child(id).setValue(notification)
                .addOnSuccessListener(aVoid -> {
                    String sentTo = "Specific User".equals(audience)
                            ? (selectedUserName != null ? selectedUserName : "user")
                            : audience;

                    etMessage.setText("");
                    selectedUserId = null;
                    selectedUserName = null;
                    selectedUserRole = null;
                    tvRecipientName.setText("Tap to select a recipient");
                    tvRecipientSub.setText("Choose one user");
                    tvRecipientInitial.setText("?");

                    LocaleHelper.showTranslatedToast(this,
                            "Sent to " + sentTo, Toast.LENGTH_SHORT);
                })
                .addOnFailureListener(e ->
                        LocaleHelper.showTranslatedToast(this,
                                "Failed: " + e.getMessage(), Toast.LENGTH_SHORT));
    }

    private void fetchNotificationHistory() {
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                historyList.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    NotificationModel model = data.getValue(NotificationModel.class);
                    if (model != null) {
                        model.id = data.getKey();
                        historyList.add(model);
                    }
                }
                Collections.sort(historyList, (a, b) -> Long.compare(b.timestamp, a.timestamp));
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                LocaleHelper.showTranslatedToast(SendNotifications.this,
                        "DB Error: " + error.getMessage(), Toast.LENGTH_SHORT);
            }
        });
    }
}