package com.example.communitymember20;

public class WaterStatusModel {

    // Firebase key (set after reading from DB)
    public String id;

    // Report content
    public String userId;
    public String name;
    public String contact;
    public String location;
    public String waterStatus;
    public String status;
    public String reference;        // ← ADD THIS (was missing)
    public String date;
    public Long timestamp;
    public Long expiresAt;          // ← ADD THIS (for 24h expiry tracking)
    public Long lastUpdated;        // ← ADD THIS (for admin status changes)
    public Long resolvedAt;         // ← ADD THIS (set when status = Resolved)

    // Empty constructor required by Firebase
    public WaterStatusModel() { }
}