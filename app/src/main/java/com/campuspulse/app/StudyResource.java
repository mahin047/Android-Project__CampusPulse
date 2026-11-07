package com.campuspulse.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StudyResource {
    public String courseCode, courseTitle, dept, type, title, url;

    public static class Dept {
        public String code, name;
    }

    public static class Data {
        public final List<Dept> departments = new ArrayList<>();
        public final List<StudyResource> resources = new ArrayList<>();
    }

    public static Data parse(String json) throws JSONException {
        Data data = new Data();
        JSONObject root = new JSONObject(json);

        JSONArray deps = root.optJSONArray("departments");
        if (deps != null) {
            for (int i = 0; i < deps.length(); i++) {
                JSONObject d = deps.getJSONObject(i);
                Dept x = new Dept();
                x.code = d.getString("code");
                x.name = d.optString("name", x.code);
                data.departments.add(x);
            }
        }

        JSONArray courses = root.getJSONArray("courses");
        for (int i = 0; i < courses.length(); i++) {
            JSONObject c = courses.getJSONObject(i);
            String code = c.getString("code");
            String title = c.getString("title");
            String dept = c.optString("dept", "");
            JSONArray res = c.getJSONArray("resources");
            for (int j = 0; j < res.length(); j++) {
                JSONObject r = res.getJSONObject(j);
                StudyResource s = new StudyResource();
                s.courseCode = code;
                s.courseTitle = title;
                s.dept = dept;
                s.type = r.getString("type");
                s.title = r.getString("title");
                s.url = r.optString("url", "");
                data.resources.add(s);
            }
        }
        return data;
    }

    public static int typeColor(String t) {
        if ("Lecture Notes".equals(t)) return 0xFF4DA3FF;
        if ("Previous Questions".equals(t)) return 0xFFFFC25A;
        if ("Lab Materials".equals(t)) return 0xFF3FE0B0;
        if ("Important Topics".equals(t)) return 0xFFFF8484;
        return 0xFF9DB2D1;
    }
}