package com.example.communitymember20;

public class NotificationModel {
    public String id;
    public String message;
    public String targetAudience;
    public String targetUserId;
    public String targetUserName;
    public String targetUserRole;     // ✅ Used by NotificationHistoryAdapter + SendNotifications
    public String type;               // EMERGENCY_ASSIGNMENT, DELIVERY_CONFIRMED, etc.
    public String relatedRequestId;
    public String relatedLocation;
    public Integer litresDelivered;
    public boolean isRead;
    public long timestamp;
    public Long readAt;

    public NotificationModel() {}

    // Helper for notification categorization (used by filters)
    public String getCategory() {
        if (type == null) return "General";
        switch (type) {
            case "EMERGENCY_ASSIGNMENT":
            case "DELIVERY_FEEDBACK_REQUEST":
            case "DELIVERY_DISPUTED":
            case "DELIVERY_CONFIRMED":
                return "Delivery";
            case "WATER_STATUS":
            case "EMERGENCY_UPDATE":
                return "Status";
            default:
                return "General";
        }
    }
}