package com.slimene.easypark;

import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class User {
    private String uid;
    private String fullName;
    private String email;
    private String role; // "client" or "admin"
    private double balance;
    private boolean suspended;
    private String activeSpotId;

    // Default empty constructor required for Firebase Firestore
    public User() {
    }

    public User(String uid, String fullName, String email, String role, double balance, boolean suspended, String activeSpotId) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.balance = balance;
        this.suspended = suspended;
        this.activeSpotId = activeSpotId;
    }

    public User(String uid, String fullName, String email, String role, double balance, boolean suspended) {
        this(uid, fullName, email, role, balance, suspended, "");
    }

    public User(String uid, String fullName, String email, String role, double balance) {
        this(uid, fullName, email, role, balance, false, "");
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    public String getActiveSpotId() {
        return activeSpotId != null ? activeSpotId : "";
    }

    public void setActiveSpotId(String activeSpotId) {
        this.activeSpotId = activeSpotId;
    }
}
