package com.campuspulse.app;

public class CampusPlace {
    public String id = "", name = "", category = "Campus", keywords = "";
    public String where = "", hours = "", phone = "", website = "", note = "";
    public Double lat, lng;

    /** Parser প্রতিটা tag এর text এখানে পাঠায়। */
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

    public boolean hasLocation() { return lat != null && lng != null; }
    public boolean hasPhone() { return !phone.isEmpty(); }
    public boolean hasWebsite() { return !website.isEmpty(); }

    public static int categoryColor(String c) {
        if ("Academic".equals(c)) return 0xFF4DA3FF;
        if ("Admin".equals(c)) return 0xFF9A8BFF;
        if ("Health".equals(c)) return 0xFFFF8484;
        if ("Food".equals(c)) return 0xFFFFC25A;
        if ("Transport".equals(c)) return 0xFF2DD4BF;
        if ("Safety".equals(c)) return 0xFFFF5A5F;
        if ("Sports".equals(c)) return 0xFF3FE0B0;
        return 0xFF9DB2D1;
    }
}