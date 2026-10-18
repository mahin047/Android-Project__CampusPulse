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
}