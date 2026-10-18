package com.campuspulse.app;

import android.graphics.Color;

import java.util.Date;

public class NoticeModel {
    public static final String[] CATEGORIES = {"Academic", "Exam", "Event", "General"};
    public static final String[] PRIORITIES = {"Normal", "Important", "Urgent"};

    private String id, title, body, category, priority, authorName;
    private Date createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public static int priorityColor(String priority) {
        if ("urgent".equalsIgnoreCase(priority)) return Color.parseColor("#FF5A5F");
        if ("important".equalsIgnoreCase(priority)) return Color.parseColor("#F5B942");
        return Color.parseColor("#2DD4BF");
    }

    public static String badgeText(NoticeModel n) {
        String cat = n.getCategory() == null ? "GENERAL" : n.getCategory().toUpperCase();
        if ("urgent".equalsIgnoreCase(n.getPriority())) return cat + "  •  URGENT";
        if ("important".equalsIgnoreCase(n.getPriority())) return cat + "  •  IMPORTANT";
        return cat;
    }
}