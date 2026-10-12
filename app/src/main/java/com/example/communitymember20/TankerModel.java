package com.example.communitymember20;

public class TankerModel {
    public String tankerId;
    public String registration;         // CA 123-456
    public String sourceName;           // Sandton Reservoir
    public int capacity;                // 10000
    public int currentLoad;             // driver updates
    public String currentStatus;        // Filling / En Route / Idle
    public String assignedDriverId;
    public String assignedDriverName;
    public long lastUpdated;
    public String updatedBy;
    public String lastNote;
    public String currentAssignmentId;  // active emergency assignment
    public String currentEmergencyId;   // which emergency this tanker is serving
    public boolean active;

    public TankerModel() {}
}