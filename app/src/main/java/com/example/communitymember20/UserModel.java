package com.example.communitymember20;

public class UserModel {

    // Firebase keys (must match exactly what's in the database)
    public String full_name;
    public String email;
    public String phone_number;
    public String role;
    public String preferred_lang;
    public String license_no;
    public String assigned_tanker;
    public Boolean email_verified;
    public Boolean phone_verified;
    public Boolean profile_complete;
    public Long createdAt;
    public String status;

    // Local-only field (the Firebase key for this record, added after reading)
    public String uid;

    // Empty constructor required by Firebase
    public UserModel() { }

    // Optional convenience getters (used by some parts of the app)
    public String getUid() { return uid; }
    public String getFullName() { return full_name; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phone_number; }
    public String getRole() { return role; }
}
