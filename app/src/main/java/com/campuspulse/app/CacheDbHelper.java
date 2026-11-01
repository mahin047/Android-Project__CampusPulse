package com.campuspulse.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class CacheDbHelper extends SQLiteOpenHelper {

    private static final String NAME = "campus_cache.db";
    private static final int VERSION = 1;

    public CacheDbHelper(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true); // SQLite এ FK default বন্ধ থাকে, চালু করতে হয়
        db.enableWriteAheadLogging();             // লেখার সময়ও পড়া চলে
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notices ("
                + "id TEXT PRIMARY KEY, "
                + "title TEXT NOT NULL, "
                + "body TEXT, "
                + "category TEXT NOT NULL DEFAULT 'General', "
                + "priority TEXT NOT NULL DEFAULT 'Normal', "
                + "author_name TEXT, "
                + "created_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_notices_created ON notices(created_at)");

        db.execSQL("CREATE TABLE events ("
                + "id TEXT PRIMARY KEY, "
                + "title TEXT NOT NULL, "
                + "description TEXT, "
                + "venue TEXT, "
                + "category TEXT, "
                + "date_time INTEGER NOT NULL DEFAULT 0, "
                + "capacity INTEGER NOT NULL DEFAULT 0 CHECK (capacity >= 0), "
                + "registered_count INTEGER NOT NULL DEFAULT 0 CHECK (registered_count >= 0))");
        db.execSQL("CREATE INDEX idx_events_date ON events(date_time)");

        // one-to-many: একটা notice কে অনেকজন user save করতে পারে
        db.execSQL("CREATE TABLE bookmarks ("
                + "uid TEXT NOT NULL, "
                + "notice_id TEXT NOT NULL REFERENCES notices(id) ON DELETE CASCADE, "
                + "saved_at INTEGER NOT NULL, "
                + "PRIMARY KEY (uid, notice_id))");
        db.execSQL("CREATE INDEX idx_bookmarks_notice ON bookmarks(notice_id)");

        db.execSQL("CREATE TABLE meta ("
                + "meta_key TEXT PRIMARY KEY, "
                + "meta_value TEXT)");
    }

    // এটা শুধু cache, তাই version বদলালে সব মুছে নতুন করে বানানো নিরাপদ
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS bookmarks");
        db.execSQL("DROP TABLE IF EXISTS notices");
        db.execSQL("DROP TABLE IF EXISTS events");
        db.execSQL("DROP TABLE IF EXISTS meta");
        onCreate(db);
    }
}