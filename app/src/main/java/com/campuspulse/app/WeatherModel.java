package com.campuspulse.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WeatherModel {

    public static class Day {
        public String date;
        public double max, min;
        public int code;
    }

    public double temp, feels, wind;
    public int humidity, code;
    public final List<Day> days = new ArrayList<>();

    /** JSON → Java object (nested object + arrays) */
    public static WeatherModel parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject cur = root.getJSONObject("current");

        WeatherModel w = new WeatherModel();
        w.temp = cur.getDouble("temperature_2m");
        w.feels = cur.optDouble("apparent_temperature", w.temp);
        w.humidity = cur.optInt("relative_humidity_2m", -1);
        w.wind = cur.optDouble("wind_speed_10m", 0);
        w.code = cur.optInt("weather_code", 0);

        JSONObject daily = root.optJSONObject("daily");
        if (daily != null) {
            JSONArray time = daily.getJSONArray("time");
            JSONArray max = daily.getJSONArray("temperature_2m_max");
            JSONArray min = daily.getJSONArray("temperature_2m_min");
            JSONArray codes = daily.getJSONArray("weather_code");
            for (int i = 0; i < time.length(); i++) {
                Day d = new Day();
                d.date = time.getString(i);
                d.max = max.getDouble(i);
                d.min = min.getDouble(i);
                d.code = codes.getInt(i);
                w.days.add(d);
            }
        }
        return w;
    }

    public static String dayLabel(String iso, int index) {
        if (index == 0) return "Today";
        if (index == 1) return "Tomorrow";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            return new SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(d);
        } catch (ParseException e) {
            return iso;
        }
    }

    // WMO weather code → text/emoji
    public static String describe(int c) {
        if (c == 0) return "Clear sky";
        if (c == 1 || c == 2) return "Partly cloudy";
        if (c == 3) return "Overcast";
        if (c == 45 || c == 48) return "Foggy";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Rain showers";
        if (c >= 85 && c <= 86) return "Snow showers";
        if (c >= 95) return "Thunderstorm";
        return "Unknown";
    }

    public static String emoji(int c) {
        if (c == 0) return "☀️";
        if (c == 1 || c == 2) return "🌤️";
        if (c == 3) return "☁️";
        if (c == 45 || c == 48) return "🌫️";
        if (c >= 51 && c <= 57) return "🌦️";
        if (c >= 61 && c <= 67) return "🌧️";
        if (c >= 71 && c <= 77) return "❄️";
        if (c >= 80 && c <= 82) return "🌧️";
        if (c >= 85 && c <= 86) return "❄️";
        if (c >= 95) return "⛈️";
        return "🌡️";
    }
}