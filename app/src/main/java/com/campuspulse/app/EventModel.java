package com.campuspulse.app;

import android.graphics.Color;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Date;

public class EventModel {
    public static final String[] CATEGORIES =
            {"Workshop", "Contest", "Cultural", "Sports", "Seminar", "Other"};

    private String id, title, description, venue, category, authorName;
    private Date dateTime;
    private int capacity, registeredCount;

    public static EventModel from(DocumentSnapshot d) {
        EventModel e = new EventModel();
        e.id = d.getId();
        e.title = d.getString("title");
        e.description = d.getString("description");
        e.venue = d.getString("venue");
        e.category = d.getString("category");
        e.authorName = d.getString("authorName");
        e.dateTime = d.getDate("dateTime");
        Long cap = d.getLong("capacity");
        Long cnt = d.getLong("registeredCount");
        e.capacity = cap == null ? 0 : cap.intValue();
        e.registeredCount = cnt == null ? 0 : cnt.intValue();
        return e;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getVenue() { return venue; }
    public String getCategory() { return category; }
    public String getAuthorName() { return authorName; }
    public Date getDateTime() { return dateTime; }
    public int getCapacity() { return capacity; }
    public int getRegisteredCount() { return registeredCount; }

    public boolean isPast() { return dateTime != null && dateTime.before(new Date()); }
    public boolean isFull() { return capacity > 0 && registeredCount >= capacity; }
    public int seatsLeft() { return Math.max(0, capacity - registeredCount); }

    public static int categoryColor(String c) {
        if ("Workshop".equalsIgnoreCase(c)) return Color.parseColor("#4DA3FF");
        if ("Contest".equalsIgnoreCase(c)) return Color.parseColor("#9A8BFF");
        if ("Cultural".equalsIgnoreCase(c)) return Color.parseColor("#FF8484");
        if ("Sports".equalsIgnoreCase(c)) return Color.parseColor("#FFC25A");
        if ("Seminar".equalsIgnoreCase(c)) return Color.parseColor("#3FE0B0");
        return Color.parseColor("#2DD4BF");
    }
}