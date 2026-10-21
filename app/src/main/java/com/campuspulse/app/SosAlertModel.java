package com.campuspulse.app;

import android.graphics.Color;

import com.google.firebase.database.DataSnapshot;

import java.util.Date;

public class SosAlertModel {
    public static final String ACTIVE = "active";
    public static final String ACK = "acknowledged";
    public static final String RESOLVED = "resolved";
    public static final String CANCELLED = "cancelled";

    public static final String[] TYPES =
            {"Not sure", "Medical", "Fire", "Security", "Harassment", "Accident"};

    private String id, uid, name, studentId, type, status, handledBy;
    private Double lat, lng;
    private Long accuracy, createdAt, updatedAt, handledAt;

    public static SosAlertModel from(DataSnapshot s) {
        SosAlertModel m = new SosAlertModel();
        m.id = s.getKey();
        m.uid = s.child("uid").getValue(String.class);
        m.name = s.child("name").getValue(String.class);
        m.studentId = s.child("studentId").getValue(String.class);
        m.type = s.child("type").getValue(String.class);
        m.status = s.child("status").getValue(String.class);
        m.handledBy = s.child("handledBy").getValue(String.class);
        m.lat = s.child("lat").getValue(Double.class);
        m.lng = s.child("lng").getValue(Double.class);
        m.accuracy = s.child("accuracy").getValue(Long.class);
        m.createdAt = s.child("createdAt").getValue(Long.class);
        m.updatedAt = s.child("updatedAt").getValue(Long.class);
        m.handledAt = s.child("handledAt").getValue(Long.class);
        return m;
    }

    public String getId() { return id; }
    public String getUid() { return uid; }
    public String getName() { return name; }
    public String getStudentId() { return studentId; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public String getHandledBy() { return handledBy; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public Long getAccuracy() { return accuracy; }
    public Date getCreatedDate() { return createdAt == null ? null : new Date(createdAt); }
    public Date getUpdatedDate() { return updatedAt == null ? null : new Date(updatedAt); }
    public Long getCreatedAtRaw() { return createdAt; }
    public Long getUpdatedAtRaw() { return updatedAt; }

    public boolean isActive() { return ACTIVE.equals(status) || ACK.equals(status); }
    public boolean hasLocation() { return lat != null && lng != null; }

    public static int statusColor(String s) {
        if (ACK.equals(s)) return Color.parseColor("#F5B942");
        if (RESOLVED.equals(s)) return Color.parseColor("#34D399");
        if (CANCELLED.equals(s)) return Color.parseColor("#9DB2D1");
        return Color.parseColor("#FF5A5F");
    }

    public static String statusLabel(String s) {
        if (ACK.equals(s)) return "RESPONDING";
        if (RESOLVED.equals(s)) return "RESOLVED";
        if (CANCELLED.equals(s)) return "CANCELLED";
        return "ACTIVE";
    }

    public static int typeColor(String t) {
        if ("Medical".equals(t)) return Color.parseColor("#FF8484");
        if ("Fire".equals(t)) return Color.parseColor("#FF5A5F");
        if ("Security".equals(t)) return Color.parseColor("#9A8BFF");
        if ("Harassment".equals(t)) return Color.parseColor("#C084FC");
        if ("Accident".equals(t)) return Color.parseColor("#FFA45A");
        return Color.parseColor("#2DD4BF");
    }
}