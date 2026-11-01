package com.campuspulse.app;

import android.net.Uri;

public final class CacheContract {
    private CacheContract() { }

    public static final String AUTHORITY = "com.campuspulse.app.provider";
    public static final Uri BASE_URI = Uri.parse("content://" + AUTHORITY);

    public static final class Notices {
        public static final String TABLE = "notices";
        public static final Uri CONTENT_URI = BASE_URI.buildUpon().appendPath(TABLE).build();
        public static final Uri STATS_URI = CONTENT_URI.buildUpon().appendPath("stats").build();
        public static final String ID = "id";
        public static final String TITLE = "title";
        public static final String BODY = "body";
        public static final String CATEGORY = "category";
        public static final String PRIORITY = "priority";
        public static final String AUTHOR = "author_name";
        public static final String CREATED_AT = "created_at";
        public static final String TYPE_DIR = "vnd.android.cursor.dir/vnd.com.campuspulse.app.notice";
        public static final String TYPE_ITEM = "vnd.android.cursor.item/vnd.com.campuspulse.app.notice";
    }

    public static final class Events {
        public static final String TABLE = "events";
        public static final Uri CONTENT_URI = BASE_URI.buildUpon().appendPath(TABLE).build();
        public static final String ID = "id";
        public static final String TITLE = "title";
        public static final String DESCRIPTION = "description";
        public static final String VENUE = "venue";
        public static final String CATEGORY = "category";
        public static final String DATE_TIME = "date_time";
        public static final String CAPACITY = "capacity";
        public static final String REGISTERED = "registered_count";
        public static final String TYPE_DIR = "vnd.android.cursor.dir/vnd.com.campuspulse.app.event";
        public static final String TYPE_ITEM = "vnd.android.cursor.item/vnd.com.campuspulse.app.event";
    }

    public static final class Bookmarks {
        public static final String TABLE = "bookmarks";
        public static final Uri CONTENT_URI = BASE_URI.buildUpon().appendPath(TABLE).build();
        public static final String UID = "uid";
        public static final String NOTICE_ID = "notice_id";
        public static final String SAVED_AT = "saved_at";
        public static final String TYPE_DIR = "vnd.android.cursor.dir/vnd.com.campuspulse.app.bookmark";
    }

    public static final class Meta {
        public static final String TABLE = "meta";
        public static final Uri CONTENT_URI = BASE_URI.buildUpon().appendPath(TABLE).build();
        public static final String KEY = "meta_key";
        public static final String VALUE = "meta_value";
        public static final String KEY_NOTICES_SYNC = "last_sync_notices";
        public static final String KEY_EVENTS_SYNC = "last_sync_events";
        public static final String TYPE_DIR = "vnd.android.cursor.dir/vnd.com.campuspulse.app.meta";
    }
}