package com.example.communitymember20;

public class DeliveryPointModel {
    public String pointId;
    public String routeId;
    public String location;       // e.g. "Section A Tap - Ward 7"
    public String demandLevel;    // "High", "Medium", "Low"
    public double distanceKm;     // distance from current position
    public int etaMinutes;        // ETA in minutes
    public String status;         // "Completed", "Current", "Upcoming"
    public int stopNumber;        // 1, 2, 3...
    public long completedAt;      // timestamp when delivered

    public DeliveryPointModel() {} // Required for Firebase

    public DeliveryPointModel(String pointId, String location, String demandLevel,
                              double distanceKm, int etaMinutes, String status, int stopNumber) {
        this.pointId = pointId;
        this.location = location;
        this.demandLevel = demandLevel;
        this.distanceKm = distanceKm;
        this.etaMinutes = etaMinutes;
        this.status = status;
        this.stopNumber = stopNumber;
    }
}