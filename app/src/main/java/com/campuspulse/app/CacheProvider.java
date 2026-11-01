package com.campuspulse.app;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class CacheProvider extends ContentProvider {

    private static final int NOTICES = 1, NOTICE_ID = 2, NOTICE_STATS = 3,
            EVENTS = 4, EVENT_ID = 5, BOOKMARKS = 6, META = 7;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        String a = CacheContract.AUTHORITY;
        MATCHER.addURI(a, "notices/stats", NOTICE_STATS); // অবশ্যই "notices/*" এর আগে
        MATCHER.addURI(a, "notices", NOTICES);
        MATCHER.addURI(a, "notices/*", NOTICE_ID);        // id Firestore এর string, তাই * (# নয়)
        MATCHER.addURI(a, "events", EVENTS);
        MATCHER.addURI(a, "events/*", EVENT_ID);
        MATCHER.addURI(a, "bookmarks", BOOKMARKS);
        MATCHER.addURI(a, "meta", META);
    }

    private CacheDbHelper helper;
    // applyBatch চলার সময় কোন URI বদলাল তা জমিয়ে রাখে, শেষে একবার notify
    private final ThreadLocal<Set<Uri>> pending = new ThreadLocal<>();

    @Override
    public boolean onCreate() {
        helper = new CacheDbHelper(getContext());
        return true;
    }

    private Context ctx() {
        Context c = getContext();
        if (c == null) throw new IllegalStateException("No context");
        return c;
    }

    private static String tableOf(int code) {
        switch (code) {
            case NOTICES: case NOTICE_ID: return CacheContract.Notices.TABLE;
            case EVENTS: case EVENT_ID: return CacheContract.Events.TABLE;
            case BOOKMARKS: return CacheContract.Bookmarks.TABLE;
            case META: return CacheContract.Meta.TABLE;
            default: return null;
        }
    }

    private static Uri collectionUri(int code) {
        switch (code) {
            case NOTICES: case NOTICE_ID: return CacheContract.Notices.CONTENT_URI;
            case EVENTS: case EVENT_ID: return CacheContract.Events.CONTENT_URI;
            case BOOKMARKS: return CacheContract.Bookmarks.CONTENT_URI;
            default: return CacheContract.Meta.CONTENT_URI;
        }
    }

    private static boolean isItem(int code) {
        return code == NOTICE_ID || code == EVENT_ID;
    }

    private void changed(Uri collection) {
        Set<Uri> batch = pending.get();
        if (batch != null) batch.add(collection);
        else ctx().getContentResolver().notifyChange(collection, null);
    }

    // ---------- READ ----------
    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        int code = MATCHER.match(uri);
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c;
        if (code == NOTICE_STATS) {
            // aggregate: category অনুযায়ী notice এর সংখ্যা
            c = db.rawQuery("SELECT category, COUNT(*) AS n FROM notices "
                    + "GROUP BY category ORDER BY n DESC", null);
        } else {
            String table = tableOf(code);
            if (table == null) throw new IllegalArgumentException("Unknown URI: " + uri);
            if (isItem(code)) {
                selection = "id = ?";
                selectionArgs = new String[]{uri.getLastPathSegment()};
            }
            c = db.query(table, projection, selection, selectionArgs, null, null, sortOrder);
        }
        c.setNotificationUri(ctx().getContentResolver(), uri);
        return c;
    }

    // ---------- MIME ----------
    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case NOTICES: return CacheContract.Notices.TYPE_DIR;
            case NOTICE_ID: return CacheContract.Notices.TYPE_ITEM;
            case NOTICE_STATS: return "vnd.android.cursor.dir/vnd.com.campuspulse.app.notice-stats";
            case EVENTS: return CacheContract.Events.TYPE_DIR;
            case EVENT_ID: return CacheContract.Events.TYPE_ITEM;
            case BOOKMARKS: return CacheContract.Bookmarks.TYPE_DIR;
            case META: return CacheContract.Meta.TYPE_DIR;
            default: return null;
        }
    }

    // ---------- CREATE (notices/events = upsert) ----------
    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        int code = MATCHER.match(uri);
        boolean ok = code == NOTICES || code == EVENTS || code == BOOKMARKS || code == META;
        if (values == null || !ok) throw new IllegalArgumentException("Cannot insert into " + uri);

        SQLiteDatabase db = helper.getWritableDatabase();
        String table = tableOf(code);
        Uri result = uri;

        if (code == NOTICES || code == EVENTS) {
            String id = values.getAsString("id");
            if (id == null || id.isEmpty()) throw new IllegalArgumentException("id is required");
            ContentValues changes = new ContentValues(values);
            changes.remove("id");
            int n = db.update(table, changes, "id = ?", new String[]{id});
            if (n == 0) db.insertOrThrow(table, null, values);
            result = uri.buildUpon().appendPath(id).build();
        } else if (code == BOOKMARKS) {
            db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        } else {
            db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE); // meta তে FK নেই
        }
        changed(collectionUri(code));
        return result;
    }

    // ---------- UPDATE ----------
    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        int code = MATCHER.match(uri);
        String table = tableOf(code);
        if (table == null) throw new IllegalArgumentException("Unknown URI: " + uri);
        if (values == null || values.size() == 0) return 0;
        if (isItem(code)) {
            selection = "id = ?";
            selectionArgs = new String[]{uri.getLastPathSegment()};
        }
        int n = helper.getWritableDatabase().update(table, values, selection, selectionArgs);
        if (n > 0) changed(collectionUri(code));
        return n;
    }

    // ---------- DELETE ----------
    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        int code = MATCHER.match(uri);
        String table = tableOf(code);
        if (table == null) throw new IllegalArgumentException("Unknown URI: " + uri);
        if (isItem(code)) {
            selection = "id = ?";
            selectionArgs = new String[]{uri.getLastPathSegment()};
        }
        int n = helper.getWritableDatabase().delete(table, selection, selectionArgs);
        if (n > 0) changed(collectionUri(code));
        return n;
    }

    // ---------- BATCH: সব operation এক transaction এ, শেষে একবার notify ----------
    @NonNull
    @Override
    public ContentProviderResult[] applyBatch(@NonNull ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        SQLiteDatabase db = helper.getWritableDatabase();
        pending.set(new HashSet<>());
        db.beginTransaction();
        try {
            ContentProviderResult[] results = super.applyBatch(operations);
            db.setTransactionSuccessful();
            return results;
        } finally {
            db.endTransaction(); // success না হলে rollback
            Set<Uri> touched = pending.get();
            pending.remove();
            if (touched != null) {
                for (Uri u : touched) ctx().getContentResolver().notifyChange(u, null);
            }
        }
    }
}