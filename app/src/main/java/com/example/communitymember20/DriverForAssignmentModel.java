package com.example.communitymember20;

public class DriverForAssignmentModel {
    public String uid;
    public String fullName;
    public String phone;
    public String tankerId;
    public String tankerReg;
    public int capacity;
    public int currentLoad;
    public String tankerStatus;
    public int stopsCompleted;
    public int stopsTotal;
    public boolean isAvailable;

    // NEW: for tracking assignment status
    public String driverStatus;              // Available / Not Available / Idle
    public String activeEmergencyId;
    public String activeEmergencyLocation;

    public DriverForAssignmentModel() {}
}