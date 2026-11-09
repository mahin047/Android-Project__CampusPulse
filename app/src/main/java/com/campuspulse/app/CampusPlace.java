package com.campuspulse.app;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class CampusPlace {

    public static final String[] DEFAULT_CATEGORIES =
            {"Academic", "Admin", "Health", "Food", "Transport", "Safety", "Sports", "Campus"};

    public String id = "", name = "", category = "Campus", keywords = "";
    public String where = "", hours = "", phone = "", website = "", note = "";
    public Double lat, lng;

    /** XML parser প্রতিটা tag এর text এখানে পাঠায়। */
    void set(String tag, String v) {
        switch (tag) {
            case "name": name = v; break;
            case "where": where = v; break;
            case "hours": hours = v; break;
            case "phone": phone = v; break;
            case "website": website = v; break;
            case "note": note = v; break;
            case "lat": lat = toDouble(v); break;
            case "lng": lng = toDouble(v); break;
            default: break;
        }
    }

    private static Double toDouble(String s) {
        try { return Double.valueOf(s); } catch (NumberFormatException e) { return null; }
    }

    // ---------- Firestore ----------
    public static CampusPlace from(DocumentSnapshot d) {
        CampusPlace p = new CampusPlace();
        p.id = d.getId();
        p.name = nz(d.getString("name"));
        String c = d.getString("category");
        p.category = c == null || c.trim().isEmpty() ? "Campus" : c.trim();
        p.where = nz(d.getString("where"));
        p.hours = nz(d.getString("hours"));
        p.phone = nz(d.getString("phone"));
        p.website = nz(d.getString("website"));
        p.note = nz(d.getString("note"));
        p.keywords = nz(d.getString("keywords"));
        p.lat = d.getDouble("lat");
        p.lng = d.getDouble("lng");
        return p;
    }

    /** set() দিয়ে পুরো document বদলানো হয়, তাই lat/lng না থাকলে আগেরটা মুছে যায়। */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("category", category);
        m.put("where", where);
        m.put("hours", hours);
        m.put("phone", phone);
        m.put("website", website);
        m.put("note", note);
        m.put("keywords", keywords);
        if (lat != null && lng != null) {
            m.put("lat", lat);
            m.put("lng", lng);
        }
        return m;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    public boolean hasLocation() { return lat != null && lng != null; }
    public boolean hasPhone() { return !phone.isEmpty(); }
    public boolean hasWebsite() { return !website.isEmpty(); }

    private static final int[] PALETTE = {0xFF4DA3FF, 0xFF9A8BFF, 0xFFFF8484, 0xFFFFC25A,
            0xFF2DD4BF, 0xFF3FE0B0, 0xFFC084FC, 0xFFFFA45A};

    public static int categoryColor(String c) {
        if ("Academic".equals(c)) return 0xFF4DA3FF;
        if ("Admin".equals(c)) return 0xFF9A8BFF;
        if ("Health".equals(c)) return 0xFFFF8484;
        if ("Food".equals(c)) return 0xFFFFC25A;
        if ("Transport".equals(c)) return 0xFF2DD4BF;
        if ("Safety".equals(c)) return 0xFFFF5A5F;
        if ("Sports".equals(c)) return 0xFF3FE0B0;
        if ("Campus".equals(c) || c == null) return 0xFF9DB2D1;
        // admin এর বানানো নতুন category: নাম থেকে একটা স্থির রং
        return PALETTE[Math.abs(c.hashCode()) % PALETTE.length];
    }
}