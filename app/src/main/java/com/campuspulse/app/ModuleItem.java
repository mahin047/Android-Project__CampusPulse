package com.campuspulse.app;

public class ModuleItem {
    private final String title, subtitle, note;
    private final int iconRes, bgRes;

    public ModuleItem(String title, String subtitle, int iconRes, int bgRes, String note) {
        this.title = title;
        this.subtitle = subtitle;
        this.iconRes = iconRes;
        this.bgRes = bgRes;
        this.note = note;
    }

    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public int getIconRes() { return iconRes; }
    public int getBgRes() { return bgRes; }
    public String getNote() { return note; }
}