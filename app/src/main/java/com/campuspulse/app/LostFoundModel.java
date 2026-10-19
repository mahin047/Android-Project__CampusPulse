package com.campuspulse.app;

import android.graphics.Color;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Date;

public class LostFoundModel {
    public static final String TYPE_LOST = "lost";
    public static final String TYPE_FOUND = "found";
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_RESOLVED = "resolved";

    public static final String[] CATEGORIES = {
            "ID / Cards", "Wallet / Bag", "Electronics", "Keys",
            "Books / Notes", "Clothing", "Other"};

    private String id, type, itemName, category, location, description, phone;
    private String posterUid, posterName, posterStudentId, status;
    private Date eventDate, createdAt;

    public static LostFoundModel from(DocumentSnapshot d) {
        LostFoundModel m = new LostFoundModel();
        m.id = d.getId();
        m.type = d.getString("type");
        m.itemName = d.getString("itemName");
        m.category = d.getString("category");
        m.location = d.getString("location");
        m.description = d.getString("description");
        m.phone = d.getString("phone");
        m.posterUid = d.getString("posterUid");
        m.posterName = d.getString("posterName");
        m.posterStudentId = d.getString("posterStudentId");
        m.status = d.getString("status");
        m.eventDate = d.getDate("eventDate");
        m.createdAt = d.getDate("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        return m;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getItemName() { return itemName; }
    public String getCategory() { return category; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public String getPhone() { return phone; }
    public String getPosterUid() { return posterUid; }
    public String getPosterName() { return posterName; }
    public String getPosterStudentId() { return posterStudentId; }
    public Date getEventDate() { return eventDate; }
    public Date getCreatedAt() { return createdAt; }

    public boolean isLost() { return TYPE_LOST.equalsIgnoreCase(type); }
    public boolean isResolved() { return STATUS_RESOLVED.equalsIgnoreCase(status); }

    public static int typeColor(String type) {
        return TYPE_LOST.equalsIgnoreCase(type)
                ? Color.parseColor("#FF8484")   // lost = coral
                : Color.parseColor("#34D399");  // found = mint
    }
}