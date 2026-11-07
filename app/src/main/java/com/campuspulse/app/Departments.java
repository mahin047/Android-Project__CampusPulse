package com.campuspulse.app;

public class Departments {
    public static final String[] CODES = {"CSE", "EEE", "ME", "CE", "ECE", "IPE", "ESE", "BME",
            "URP", "BECM", "MSE", "CHE", "LE", "TE", "MATH", "PHY"};
    public static final String[] NAMES = {
            "Computer Science & Engineering", "Electrical & Electronic Engineering",
            "Mechanical Engineering", "Civil Engineering",
            "Electronics & Communication Engineering", "Industrial Engineering & Management",
            "Energy Science & Engineering", "Biomedical Engineering",
            "Urban & Regional Planning", "Building Engineering & Construction Management",
            "Materials Science & Engineering", "Chemical Engineering",
            "Leather Engineering", "Textile Engineering", "Mathematics", "Physics"};

    public static String[] labels() {
        String[] out = new String[CODES.length];
        for (int i = 0; i < CODES.length; i++) out[i] = CODES[i] + "  •  " + NAMES[i];
        return out;
    }

    public static String nameOf(String code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return NAMES[i];
        return code;
    }
}