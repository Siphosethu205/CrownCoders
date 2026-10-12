package com.example.communitymember20;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserManagementAdapter
        extends RecyclerView.Adapter<UserManagementAdapter.VH> {

    private final List<UserModel> items;
    private final Context context;

    // Constructor order: (List, Context) — matches UserManagement.java
    public UserManagementAdapter(List<UserModel> items, Context context) {
        this.items = items;
        this.context = context;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_management, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        UserModel u = items.get(position);

        h.tvUserName.setText(u.full_name != null ? u.full_name : "Unknown");
        h.tvUserEmail.setText(u.email != null ? u.email : "—");
        h.tvUserPhone.setText(u.phone_number != null ? u.phone_number : "—");

        String role = u.role != null ? u.role : "Community Member";
        h.tvUserRole.setText(role.toUpperCase());

        boolean emailVerified = u.email_verified != null && u.email_verified;
        boolean phoneVerified = u.phone_verified != null && u.phone_verified;
        if (emailVerified && phoneVerified) {
            h.tvUserVerified.setText("✓ Fully Verified");
            h.tvUserVerified.setTextColor(0xFF00A3B8);
        } else if (emailVerified || phoneVerified) {
            h.tvUserVerified.setText("◐ Partially Verified");
            h.tvUserVerified.setTextColor(0xFFF57C00);
        } else {
            h.tvUserVerified.setText("◌ Unverified");
            h.tvUserVerified.setTextColor(0xFFB91C1C);
        }

        // Role-based actions
        if ("Driver".equalsIgnoreCase(role)) {
            h.btnPromote.setText("Demote");
            h.btnPromote.setOnClickListener(v -> demoteToMember(u));
        } else if ("Admin".equalsIgnoreCase(role)) {
            h.btnPromote.setVisibility(View.GONE);
        } else {
            h.btnPromote.setText("Promote");
            h.btnPromote.setVisibility(View.VISIBLE);
            h.btnPromote.setOnClickListener(v -> promoteToDriver(u));
        }

        h.btnDelete.setOnClickListener(v -> confirmDelete(u));
    }

    // ============================================================
    // PROMOTE: Community Member → Driver
    // ============================================================
    private void promoteToDriver(UserModel user) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Promote to Driver")
                .setMessage("Promote " + user.full_name + " to a Tanker Driver?\n\n" +
                        "They will need to complete their driver profile (license) " +
                        "before receiving tanker assignments.")
                .setPositiveButton("Promote", (d, w) -> {
                    DatabaseReference db = FirebaseDatabase.getInstance().getReference();

                    db.child("users").child(UserNodeHelper.NODE_MEMBERS).child(user.uid)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    if (!snapshot.exists()) {
                                        Toast.makeText(context,
                                                "Member record not found.",
                                                Toast.LENGTH_SHORT).show();
                                        return;
                                    }

                                    Map<String, Object> driverData = new HashMap<>();
                                    for (DataSnapshot field : snapshot.getChildren()) {
                                        driverData.put(field.getKey(), field.getValue());
                                    }
                                    driverData.put("role", "Driver");
                                    driverData.put("profile_complete", false);
                                    driverData.put("license_no", null);
                                    driverData.put("assigned_tanker", null);
                                    driverData.put("promotedAt", System.currentTimeMillis());
                                    driverData.put("promotedBy", FirebaseAuth.getInstance().getUid());

                                    db.child("users").child(UserNodeHelper.NODE_DRIVERS)
                                            .child(user.uid)
                                            .setValue(driverData)
                                            .addOnSuccessListener(aVoid ->
                                                    db.child("users").child(UserNodeHelper.NODE_MEMBERS)
                                                            .child(user.uid).removeValue()
                                                            .addOnSuccessListener(v2 -> {
                                                                Toast.makeText(context,
                                                                        user.full_name + " is now a Driver",
                                                                        Toast.LENGTH_SHORT).show();
                                                                writeAuditLog("PROMOTE_TO_DRIVER",
                                                                        user.full_name + " promoted to Driver");
                                                            }))
                                            .addOnFailureListener(e ->
                                                    Toast.makeText(context,
                                                            "Failed: " + e.getMessage(),
                                                            Toast.LENGTH_SHORT).show());
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) { }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ============================================================
    // DEMOTE: Driver → Community Member
    // ============================================================
    private void demoteToMember(UserModel user) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Demote to Member")
                .setMessage("Demote " + user.full_name + " back to a Community Member?")
                .setPositiveButton("Demote", (d, w) -> {
                    DatabaseReference db = FirebaseDatabase.getInstance().getReference();

                    db.child("users").child(UserNodeHelper.NODE_DRIVERS).child(user.uid)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    if (!snapshot.exists()) return;

                                    Map<String, Object> memberData = new HashMap<>();
                                    for (DataSnapshot field : snapshot.getChildren()) {
                                        memberData.put(field.getKey(), field.getValue());
                                    }
                                    memberData.put("role", "Community Member");
                                    memberData.remove("license_no");
                                    memberData.remove("assigned_tanker");
                                    memberData.put("demotedAt", System.currentTimeMillis());
                                    memberData.put("demotedBy", FirebaseAuth.getInstance().getUid());

                                    db.child("users").child(UserNodeHelper.NODE_MEMBERS)
                                            .child(user.uid).setValue(memberData)
                                            .addOnSuccessListener(aVoid ->
                                                    db.child("users").child(UserNodeHelper.NODE_DRIVERS)
                                                            .child(user.uid).removeValue()
                                                            .addOnSuccessListener(v2 -> {
                                                                Toast.makeText(context,
                                                                        user.full_name + " is now a Member",
                                                                        Toast.LENGTH_SHORT).show();
                                                                writeAuditLog("DEMOTE_TO_MEMBER",
                                                                        user.full_name + " demoted to Member");
                                                            }))
                                            .addOnFailureListener(e ->
                                                    Toast.makeText(context,
                                                            "Failed: " + e.getMessage(),
                                                            Toast.LENGTH_SHORT).show());
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) { }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ============================================================
    // DELETE USER
    // ============================================================
    private void confirmDelete(UserModel user) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Delete User")
                .setMessage("Permanently delete " + user.full_name + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    DatabaseReference db = FirebaseDatabase.getInstance().getReference();
                    for (String node : UserNodeHelper.allNodes()) {
                        db.child("users").child(node).child(user.uid).removeValue();
                    }
                    Toast.makeText(context, "User deleted", Toast.LENGTH_SHORT).show();
                    writeAuditLog("DELETE_USER", "Deleted user " + user.full_name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void writeAuditLog(String action, String details) {
        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        String logId = db.child("audit_logs").push().getKey();
        if (logId == null) return;

        Map<String, Object> log = new HashMap<>();
        log.put("action", action);
        log.put("details", details);
        log.put("performedBy", FirebaseAuth.getInstance().getUid() != null
                ? FirebaseAuth.getInstance().getUid() : "admin");
        log.put("timestamp", System.currentTimeMillis());

        db.child("audit_logs").child(logId).setValue(log);
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ============================================================
    // VIEW HOLDER
    // ============================================================
    public static class VH extends RecyclerView.ViewHolder {
        TextView tvUserName, tvUserRole, tvUserEmail, tvUserPhone, tvUserVerified;
        MaterialButton btnPromote, btnDelete;

        public VH(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvUserRole = itemView.findViewById(R.id.tvUserRole);
            tvUserEmail = itemView.findViewById(R.id.tvUserEmail);
            tvUserPhone = itemView.findViewById(R.id.tvUserPhone);
            tvUserVerified = itemView.findViewById(R.id.tvUserVerified);
            btnPromote = itemView.findViewById(R.id.btnPromote);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}