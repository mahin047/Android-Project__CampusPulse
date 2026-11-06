package com.campuspulse.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StudyResource {
    public String courseCode, courseTitle, type, title, url;

    /** Nested JSON (courses → resources) কে flat Java list এ বদলায়। */
    public static List<StudyResource> parse(String json) throws JSONException {
        List<StudyResource> out = new ArrayList<>();
        JSONArray courses = new JSONObject(json).getJSONArray("courses");
        for (int i = 0; i < courses.length(); i++) {
            JSONObject c = courses.getJSONObject(i);
            String code = c.getString("code");
            String title = c.getString("title");
            JSONArray res = c.getJSONArray("resources");
            for (int j = 0; j < res.length(); j++) {
                JSONObject r = res.getJSONObject(j);
                StudyResource s = new StudyResource();
                s.courseCode = code;
                s.courseTitle = title;
                s.type = r.getString("type");
                s.title = r.getString("title");
                s.url = r.optString("url", "");
                out.add(s);
            }
        }
        return out;
    }

    public static int typeColor(String t) {
        if ("Lecture Notes".equals(t)) return 0xFF4DA3FF;
        if ("Previous Questions".equals(t)) return 0xFFFFC25A;
        if ("Lab Materials".equals(t)) return 0xFF3FE0B0;
        if ("Important Topics".equals(t)) return 0xFFFF8484;
        return 0xFF9DB2D1;
    }
}