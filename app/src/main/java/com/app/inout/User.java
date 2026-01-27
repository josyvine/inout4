package com.inout.app.models;

import com.google.firebase.firestore.IgnoreExtraProperties;

/**
 * Model class representing a user in the 'users' Firestore collection.
 * This is the bridge between Firestore and the app memory.
 */
@IgnoreExtraProperties
public class User {

    private String uid;
    private String name;
    private String email;
    private String phone;
    private String role; // "admin" or "employee"
    private boolean approved;
    private String employeeId; // Assigned by Admin (e.g., EMP001)
    private String photoUrl;
    
    // CRITICAL FIELD: This must match the Firestore key exactly
    private String assignedLocationId; 

    public User() {
        // Default constructor required for Firestore
    }

    public User(String uid, String email, String role) {
        this.uid = uid;
        this.email = email;
        this.role = role;
        this.approved = false;
    }

    // Getters and Setters

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getAssignedLocationId() {
        return assignedLocationId;
    }

    public void setAssignedLocationId(String assignedLocationId) {
        this.assignedLocationId = assignedLocationId;
    }
}