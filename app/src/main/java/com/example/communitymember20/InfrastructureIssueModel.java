package com.example.communitymember20;

public class InfrastructureIssueModel {
    public String issueId;
    public String memberId;
    public String memberName;
    public String memberPhone;

    public String issueType;        // "Broken Pipe", "Leak", "Damaged Tank", etc.
    public String description;      // user's narrative
    public String urgency;          // "Low", "Medium", "High", "Critical"
    public String location;         // typed or GPS-derived address
    public double latitude;
    public double longitude;
    public String voiceNotePath; // — stores path to recorded audio

    public String photoUrl;         // Firebase Storage URL (or empty)
    public String status;           // "Pending", "In Progress", "Resolved"
    public String assignedStaffId;  // set when admin assigns
    public String resolutionNote;   // set on resolve

    public long timestamp;
    public long resolvedAt;

    public InfrastructureIssueModel() {}
}