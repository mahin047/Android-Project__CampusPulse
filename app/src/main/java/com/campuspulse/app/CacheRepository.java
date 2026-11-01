package com.campuspulse.app;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.campuspulse.app.CacheContract.Bookmarks;
import com.campuspulse.app.CacheContract.Events;
import com.campuspulse.app.CacheContract.Meta;
import com.campuspulse.app.CacheContract.Notices;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CacheRepository {

    private static final String TAG = "CACHE";

    public interface Callback<T> { void onResult(T result); }

    public static class NoticeData {
        public final List<NoticeModel> items = new ArrayList<>();
        public final Set<String> savedIds = new HashSet<>();
        public long lastSync = 0L;
    }

    public static class Stats {
        public int notices, events, saved;
        public long latestNotice, lastSyncNotices, lastSyncEvents;
        public String byCategory = "-";
    }

    // ---------- singleton ----------
    private static volatile CacheRepository instance;

    public static CacheRepository get(Context c) {
        if (instance == null) {
            synchronized (CacheRepository.class) {
                if (instance == null) instance = new CacheRepository(c.getApplicationContext());
            }
        }
        return instance;
    }

    private final ContentResolver cr;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final ExecutorService readers = Executors.newFixedThreadPool(2);
    private final ExecutorService waiter = Executors.newCachedThreadPool();

    private CacheRepository(Context appContext) {
        cr = appContext.getContentResolver();
    }

    private static String nz(String s, String fallback) {
        return s == null ? fallback : s;
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(i == 0 ? "?" : ",?");
        return sb.toString();
    }

    // =====================================================================
    //  SYNC: Firestore → SQLite (background, এক transaction)
    // =====================================================================
    public void syncNotices(List<NoticeModel> list) {
        final List<NoticeModel> rows = new ArrayList<>(list);
        writer.execute(() -> {
            List<ContentValues> values = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (NoticeModel n : rows) {
                ContentValues v = new ContentValues();
                v.put(Notices.ID, n.getId());
                v.put(Notices.TITLE, nz(n.getTitle(), ""));
                v.put(Notices.BODY, nz(n.getBody(), ""));
                v.put(Notices.CATEGORY, nz(n.getCategory(), "General"));
                v.put(Notices.PRIORITY, nz(n.getPriority(), "Normal"));
                v.put(Notices.AUTHOR, nz(n.getAuthorName(), ""));
                v.put(Notices.CREATED_AT, n.getCreatedAt() == null ? 0L : n.getCreatedAt().getTime());
                values.add(v);
                ids.add(n.getId());
            }
            applySync(Notices.CONTENT_URI, Notices.ID, values, ids, Meta.KEY_NOTICES_SYNC);
        });
    }

    public void syncEvents(List<EventModel> list) {
        final List<EventModel> rows = new ArrayList<>(list);
        writer.execute(() -> {
            List<ContentValues> values = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (EventModel e : rows) {
                ContentValues v = new ContentValues();
                v.put(Events.ID, e.getId());
                v.put(Events.TITLE, nz(e.getTitle(), ""));
                v.put(Events.DESCRIPTION, nz(e.getDescription(), ""));
                v.put(Events.VENUE, nz(e.getVenue(), ""));
                v.put(Events.CATEGORY, nz(e.getCategory(), "Other"));
                v.put(Events.DATE_TIME, e.getDateTime() == null ? 0L : e.getDateTime().getTime());
                v.put(Events.CAPACITY, Math.max(0, e.getCapacity()));
                v.put(Events.REGISTERED, Math.max(0, e.getRegisteredCount()));
                values.add(v);
                ids.add(e.getId());
            }
            applySync(Events.CONTENT_URI, Events.ID, values, ids, Meta.KEY_EVENTS_SYNC);
        });
    }

    /** upsert সব row, তারপর server এ নেই এমন row মুছে ফেলে, আর sync সময় লেখে। সবই এক transaction এ। */
    private void applySync(Uri table, String idColumn, List<ContentValues> rows,
                           List<String> ids, String metaKey) {
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        for (ContentValues v : rows) {
            ops.add(ContentProviderOperation.newInsert(table).withValues(v).build());
        }
        if (ids.isEmpty()) {
            ops.add(ContentProviderOperation.newDelete(table).build());
        } else {
            // এক query তে SQLite এর ~999 variable সীমা আছে। আমাদের data ছোট, তাই ঠিক আছে
            ops.add(ContentProviderOperation.newDelete(table)
                    .withSelection(idColumn + " NOT IN (" + placeholders(ids.size()) + ")",
                            ids.toArray(new String[0]))
                    .build());
        }
        ContentValues meta = new ContentValues();
        meta.put(Meta.KEY, metaKey);
        meta.put(Meta.VALUE, String.valueOf(System.currentTimeMillis()));
        ops.add(ContentProviderOperation.newInsert(Meta.CONTENT_URI).withValues(meta).build());

        try {
            cr.applyBatch(CacheContract.AUTHORITY, ops);
            Log.d(TAG, "synced " + rows.size() + " rows into " + table.getLastPathSegment());
        } catch (RemoteException | OperationApplicationException | RuntimeException e) {
            Log.e(TAG, "sync failed for " + table, e);
        }
    }

    // =====================================================================
    //  READ (readers pool)
    // =====================================================================
    public void loadNotices(String uid, Callback<NoticeData> cb) {
        readers.execute(() -> {
            NoticeData data = new NoticeData();
            try (Cursor c = cr.query(Notices.CONTENT_URI, null, null, null, Notices.CREATED_AT + " DESC")) {
                if (c != null) {
                    int iId = c.getColumnIndexOrThrow(Notices.ID);
                    int iTitle = c.getColumnIndexOrThrow(Notices.TITLE);
                    int iBody = c.getColumnIndexOrThrow(Notices.BODY);
                    int iCat = c.getColumnIndexOrThrow(Notices.CATEGORY);
                    int iPri = c.getColumnIndexOrThrow(Notices.PRIORITY);
                    int iAuthor = c.getColumnIndexOrThrow(Notices.AUTHOR);
                    int iTime = c.getColumnIndexOrThrow(Notices.CREATED_AT);
                    while (c.moveToNext()) {
                        NoticeModel n = new NoticeModel();
                        n.setId(c.getString(iId));
                        n.setTitle(c.getString(iTitle));
                        n.setBody(c.getString(iBody));
                        n.setCategory(c.getString(iCat));
                        n.setPriority(c.getString(iPri));
                        n.setAuthorName(c.getString(iAuthor));
                        long t = c.getLong(iTime);
                        n.setCreatedAt(t > 0 ? new Date(t) : null);
                        data.items.add(n);
                    }
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "loadNotices failed", e);
            }

            try (Cursor c = cr.query(Bookmarks.CONTENT_URI, new String[]{Bookmarks.NOTICE_ID},
                    Bookmarks.UID + " = ?", new String[]{uid}, null)) {
                while (c != null && c.moveToNext()) data.savedIds.add(c.getString(0));
            } catch (RuntimeException e) {
                Log.e(TAG, "load bookmarks failed", e);
            }

            data.lastSync = readLastSync(Meta.KEY_NOTICES_SYNC);
            main.post(() -> cb.onResult(data));
        });
    }

    public void loadEvents(Callback<List<EventModel>> cb) {
        readers.execute(() -> {
            List<EventModel> list = new ArrayList<>();
            try (Cursor c = cr.query(Events.CONTENT_URI, null, null, null, Events.DATE_TIME + " ASC")) {
                if (c != null) {
                    int iId = c.getColumnIndexOrThrow(Events.ID);
                    int iTitle = c.getColumnIndexOrThrow(Events.TITLE);
                    int iDesc = c.getColumnIndexOrThrow(Events.DESCRIPTION);
                    int iVenue = c.getColumnIndexOrThrow(Events.VENUE);
                    int iCat = c.getColumnIndexOrThrow(Events.CATEGORY);
                    int iTime = c.getColumnIndexOrThrow(Events.DATE_TIME);
                    int iCap = c.getColumnIndexOrThrow(Events.CAPACITY);
                    int iReg = c.getColumnIndexOrThrow(Events.REGISTERED);
                    while (c.moveToNext()) {
                        list.add(EventModel.fromCache(c.getString(iId), c.getString(iTitle),
                                c.getString(iDesc), c.getString(iVenue), c.getString(iCat),
                                c.getLong(iTime), c.getInt(iCap), c.getInt(iReg)));
                    }
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "loadEvents failed", e);
            }
            main.post(() -> cb.onResult(list));
        });
    }

    private long readLastSync(String key) {
        try (Cursor c = cr.query(Meta.CONTENT_URI, new String[]{Meta.VALUE},
                Meta.KEY + " = ?", new String[]{key}, null)) {
            if (c != null && c.moveToFirst()) return Long.parseLong(c.getString(0));
        } catch (RuntimeException e) {
            Log.e(TAG, "readLastSync failed", e);
        }
        return 0L;
    }

    // =====================================================================
    //  BOOKMARKS (writer thread, তাই লেখা লাইন ধরে চলে)
    // =====================================================================
    public void isBookmarked(String uid, String noticeId, Callback<Boolean> cb) {
        writer.execute(() -> {
            boolean yes = bookmarkExists(uid, noticeId);
            main.post(() -> cb.onResult(yes));
        });
    }

    /** true = এখন saved, false = এখন unsaved, null = সংরক্ষণ করা গেল না (notice এখনো cache এ নেই) */
    public void toggleBookmark(String uid, String noticeId, Callback<Boolean> cb) {
        writer.execute(() -> {
            Boolean result;
            try {
                String sel = Bookmarks.UID + " = ? AND " + Bookmarks.NOTICE_ID + " = ?";
                String[] args = {uid, noticeId};
                if (bookmarkExists(uid, noticeId)) {
                    cr.delete(Bookmarks.CONTENT_URI, sel, args);
                    result = Boolean.FALSE;
                } else {
                    ContentValues v = new ContentValues();
                    v.put(Bookmarks.UID, uid);
                    v.put(Bookmarks.NOTICE_ID, noticeId);
                    v.put(Bookmarks.SAVED_AT, System.currentTimeMillis());
                    cr.insert(Bookmarks.CONTENT_URI, v); // FK fail করলে exception
                    result = bookmarkExists(uid, noticeId) ? Boolean.TRUE : null;
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "toggleBookmark failed", e);
                result = null;
            }
            final Boolean r = result;
            main.post(() -> cb.onResult(r));
        });
    }

    private boolean bookmarkExists(String uid, String noticeId) {
        try (Cursor c = cr.query(Bookmarks.CONTENT_URI, new String[]{"COUNT(*)"},
                Bookmarks.UID + " = ? AND " + Bookmarks.NOTICE_ID + " = ?",
                new String[]{uid, noticeId}, null)) {
            return c != null && c.moveToFirst() && c.getInt(0) > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // =====================================================================
    //  STATS: Callable + Future + aggregate SQL
    // =====================================================================
    public void loadStats(String uid, Callback<Stats> cb) {
        // Callable: ফলাফল ফেরত দেয়, readers pool এ চলে
        Future<Stats> future = readers.submit(() -> {
            Stats s = new Stats();
            try (Cursor c = cr.query(Notices.CONTENT_URI,
                    new String[]{"COUNT(*) AS n", "MAX(created_at) AS latest"}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    s.notices = c.getInt(0);
                    s.latestNotice = c.getLong(1);
                }
            }
            try (Cursor c = cr.query(Notices.STATS_URI, null, null, null, null)) { // GROUP BY
                StringBuilder sb = new StringBuilder();
                while (c != null && c.moveToNext()) {
                    if (sb.length() > 0) sb.append("  •  ");
                    sb.append(c.getString(0)).append(" ").append(c.getInt(1));
                }
                if (sb.length() > 0) s.byCategory = sb.toString();
            }
            try (Cursor c = cr.query(Events.CONTENT_URI, new String[]{"COUNT(*)"}, null, null, null)) {
                if (c != null && c.moveToFirst()) s.events = c.getInt(0);
            }
            try (Cursor c = cr.query(Bookmarks.CONTENT_URI, new String[]{"COUNT(*)"},
                    Bookmarks.UID + " = ?", new String[]{uid}, null)) {
                if (c != null && c.moveToFirst()) s.saved = c.getInt(0);
            }
            s.lastSyncNotices = readLastSync(Meta.KEY_NOTICES_SYNC);
            s.lastSyncEvents = readLastSync(Meta.KEY_EVENTS_SYNC);
            return s;
        });

        // অন্য thread এ Future.get(timeout), main thread আটকায় না
        waiter.execute(() -> {
            Stats result;
            try {
                result = future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                Log.e(TAG, "stats failed", e);
                result = null;
            }
            final Stats r = result;
            main.post(() -> cb.onResult(r));
        });
    }

    // =====================================================================
    //  Race condition demo
    // =====================================================================
    public void runThreadDemo(Callback<String> cb) {
        waiter.execute(() -> {
            String text;
            try {
                text = ThreadDemo.run();
            } catch (InterruptedException e) {
                text = "interrupted";
            }
            Log.d("THREADS", text);
            final String r = text;
            main.post(() -> cb.onResult(r));
        });
    }
}