package com.campuspulse.app;

import android.graphics.Color;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Date;

public class IncidentModel {
    public static final String PENDING = "pending";
    public static final String INVESTIGATING = "investigating";
    public static final String RESOLVED = "resolved";

    public static final String[] CATEGORIES = {
            "Electrical Hazard", "Fire / Smoke", "Medical", "Security",
            "Harassment", "Accident", "Broken Equipment", "Other"};

    private String id, category, location, description, status;
    private String reporterUid, reporterName, reporterStudentId, adminNote, handledBy;
    private boolean hasPhoto;
    private Double lat, lng;
    private Date createdAt, updatedAt;

    public static IncidentModel from(DocumentSnapshot d) {
        IncidentModel m = new IncidentModel();
        m.id = d.getId();
        m.category = d.getString("category");
        m.location = d.getString("location");
        m.description = d.getString("description");
        m.status = d.getString("status");
        m.reporterUid = d.getString("reporterUid");
        m.reporterName = d.getString("reporterName");
        m.reporterStudentId = d.getString("reporterStudentId");
        m.adminNote = d.getString("adminNote");
        m.handledBy = d.getString("handledBy");
        Boolean hp = d.getBoolean("hasPhoto");
        m.hasPhoto = hp != null && hp;
        m.lat = d.getDouble("lat");
        m.lng = d.getDouble("lng");
        m.createdAt = d.getDate("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        m.updatedAt = d.getDate("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        return m;
    }

    public String getId() { return id; }
    public String getCategory() { return category; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getReporterUid() { return reporterUid; }
    public String getReporterName() { return reporterName; }
    public String getReporterStudentId() { return reporterStudentId; }
    public String getAdminNote() { return adminNote; }
    public String getHandledBy() { return handledBy; }
    public boolean hasPhoto() { return hasPhoto; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public Date getCreatedAt() { return createdAt; }
    public Date getUpdatedAt() { return updatedAt; }

    /** 0 = pending, 1 = investigating, 2 = resolved */
    public int step() {
        if (RESOLVED.equalsIgnoreCase(status)) return 2;
        if (INVESTIGATING.equalsIgnoreCase(status)) return 1;
        return 0;
    }

    public static int statusColor(String s) {
        if (RESOLVED.equalsIgnoreCase(s)) return Color.parseColor("#34D399");
        if (INVESTIGATING.equalsIgnoreCase(s)) return Color.parseColor("#F5B942");
        return Color.parseColor("#FF5A5F");
    }

    public static String statusLabel(String s) {
        if (RESOLVED.equalsIgnoreCase(s)) return "RESOLVED";
        if (INVESTIGATING.equalsIgnoreCase(s)) return "INVESTIGATING";
        return "PENDING";
    }

    public static int categoryColor(String c) {
        if ("Electrical Hazard".equalsIgnoreCase(c)) return Color.parseColor("#F5B942");
        if ("Fire / Smoke".equalsIgnoreCase(c)) return Color.parseColor("#FF5A5F");
        if ("Medical".equalsIgnoreCase(c)) return Color.parseColor("#FF8484");
        if ("Security".equalsIgnoreCase(c)) return Color.parseColor("#9A8BFF");
        if ("Harassment".equalsIgnoreCase(c)) return Color.parseColor("#C084FC");
        if ("Accident".equalsIgnoreCase(c)) return Color.parseColor("#FFA45A");
        if ("Broken Equipment".equalsIgnoreCase(c)) return Color.parseColor("#4DA3FF");
        return Color.parseColor("#2DD4BF");
    }
}