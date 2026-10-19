package com.campuspulse.app;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class TimeUtil {

    public static String listTime(Date d) {
        if (d == null) return "Just now";
        return new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(d);
    }

    public static String dashboardTime(Date d) {
        if (d == null) return "Just now";
        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(d);
        return isToday(d) ? "Today, " + time : listTime(d);
    }

    public static String fullDate(Date d) {
        if (d == null) return "Just now";
        return new SimpleDateFormat("EEEE, dd MMMM yyyy  •  hh:mm a", Locale.getDefault()).format(d);
    }

    private static boolean isToday(Date d) {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTime(d);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
    public static String day(Date d) {
        return d == null ? "--" : new SimpleDateFormat("dd", Locale.getDefault()).format(d);
    }

    public static String month(Date d) {
        return d == null ? "---" : new SimpleDateFormat("MMM", Locale.getDefault()).format(d).toUpperCase();
    }

    public static String clock(Date d) {
        return d == null ? "--" : new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(d);
    }

    public static String eventFull(Date d) {
        return d == null ? "--" : new SimpleDateFormat("EEE, dd MMM yyyy  •  hh:mm a", Locale.getDefault()).format(d);
    }
}