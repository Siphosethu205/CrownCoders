package com.example.communitymember20;

public class TankerAssignmentModel {
    public String assignmentId;
    public String emergencyRequestId;
    public String emergencyRequestTitle;
    public String emergencyRequestLocation;
    public String emergencyRequestUrgency;
    public String tankerId;
    public String tankerReg;
    public int tankerCapacity;
    public String driverId;
    public String driverName;
    public String driverPhone;
    public String assignedBy;
    public long assignedAt;
    public String assignedDate;
    public String status;               // Assigned / En Route / Delivered / Cancelled
    public long lastUpdateAt;
    public String lastUpdateBy;
    public String lastNote;
    public int litresLoaded;
    public int litresDelivered;
    public long completedAt;

    public TankerAssignmentModel() {}
}