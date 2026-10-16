package com.campuspulse.app;

public class UserModel {
    public static final String ROLE_USER = "user";
    public static final String ROLE_ADMIN = "admin";

    private String uid, name, email, studentId, role;

    public UserModel() { } // Firestore-এর জন্য ফাঁকা constructor লাগে

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}