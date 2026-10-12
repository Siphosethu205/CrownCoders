package com.example.communitymember20;

public class AuditLogModel {
    public String id;
    public String action;       // e.g., "USER_DELETED", "ROLE_UPDATED", "NOTIFICATION_SENT"
    public String details;      // e.g., "Deleted user Thabiso Maake"
    public String performedBy;  // e.g., "admin@aquacom.com"
    public long timestamp;

    public AuditLogModel() {} // Required for Firebase

    public AuditLogModel(String id, String action, String details, String performedBy, long timestamp) {
        this.id = id;
        this.action = action;
        this.details = details;
        this.performedBy = performedBy;
        this.timestamp = timestamp;
    }
}