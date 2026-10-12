package com.example.communitymember20;

public class EmergencyRequestModel {

    public String id;

    // Core fields
    public String userId;
    public String name;
    public String contact;
    public String location;
    public String urgencyLevel;
    public String status;
    public String reference;
    public String date;
    public Long timestamp;

    // GPS coordinates
    public Double latitude;
    public Double longitude;

    // Assignment tracking fields
    public String assignedDriverId;
    public String assignedDriverName;
    public String assignedTankerId;
    public String assignedTankerReg;
    public String assignedAssignmentId;
    public Long assignedAt;

    // Delivery tracking
    public Long deliveredAt;
    public Integer litresDelivered;
    public String memberFeedback;
    public Long memberFeedbackAt;
    public String memberFeedbackBy;
    public String memberFeedbackNote;

    public EmergencyRequestModel() { }
}