package com.campuspulse.app;

import android.content.Context;

public class SosPrefs {
    private static final String FILE = "sos_prefs";
    private static final String KEY = "active_alert";

    public static void set(Context c, String uid, String alertId) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(KEY, uid + "|" + alertId).apply();
    }

    /** আগের alert এর id, শুধু যদি সেটা এই user এর হয়। */
    public static String get(Context c, String uid) {
        String v = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null);
        if (v == null || uid == null) return null;
        String prefix = uid + "|";
        return v.startsWith(prefix) ? v.substring(prefix.length()) : null;
    }

    /** Service এ uid হাতে থাকে না, তাই raw id এর জন্য। */
    public static String getAnyId(Context c) {
        String v = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null);
        if (v == null) return null;
        int i = v.indexOf('|');
        return i < 0 ? null : v.substring(i + 1);
    }

    public static void clear(Context c) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }
}