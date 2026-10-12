package com.example.communitymember20;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class AuditLogger {

    public static void log(String action, String details) {
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("audit_logs");
        String id = dbRef.push().getKey();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String adminEmail = (currentUser != null && currentUser.getEmail() != null)
                ? currentUser.getEmail()
                : "Unknown Admin";

        AuditLogModel logEntry = new AuditLogModel(
                id,
                action,
                details,
                adminEmail,
                System.currentTimeMillis()
        );

        if (id != null) {
            dbRef.child(id).setValue(logEntry);
        }
    }
}
