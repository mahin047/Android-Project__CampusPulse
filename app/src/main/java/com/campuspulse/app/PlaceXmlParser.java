package com.campuspulse.app;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class PlaceXmlParser {

    public static List<CampusPlace> parse(InputStream in) throws XmlPullParserException, IOException {
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setInput(in, "UTF-8");

        List<CampusPlace> list = new ArrayList<>();
        CampusPlace cur = null;
        String tag = null;

        int event = p.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            switch (event) {
                case XmlPullParser.START_TAG:
                    tag = p.getName();
                    if ("place".equals(tag)) {
                        cur = new CampusPlace();
                        cur.id = attr(p, "id");
                        cur.keywords = attr(p, "keywords");
                        String cat = attr(p, "category");
                        if (!cat.isEmpty()) cur.category = cat;
                    }
                    break;
                case XmlPullParser.TEXT:
                    if (cur != null && tag != null) cur.set(tag, p.getText().trim());
                    break;
                case XmlPullParser.END_TAG:
                    if ("place".equals(p.getName()) && cur != null) {
                        list.add(cur);
                        cur = null;
                    }
                    tag = null;
                    break;
                default:
                    break;
            }
            event = p.next();
        }
        return list;
    }

    private static String attr(XmlPullParser p, String name) {
        String v = p.getAttributeValue(null, name);
        return v == null ? "" : v;
    }
}