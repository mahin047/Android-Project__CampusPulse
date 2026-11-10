package com.campuspulse.app;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContactsHelper {

    public static class Contact {
        public final String name, phone;

        Contact(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Phone-er contact gulo background-e pore. Callback main thread-e ashe. READ_CONTACTS agei thakte hobe. */
    public static void load(Context c, CacheRepository.Callback<List<Contact>> cb) {
        Context app = c.getApplicationContext();
        IO.execute(() -> {
            List<Contact> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            String[] projection = {Phone.DISPLAY_NAME, Phone.NUMBER};
            // Content Provider query: URI + projection + sort order
            try (Cursor cur = app.getContentResolver().query(Phone.CONTENT_URI, projection,
                    null, null, Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")) {
                if (cur != null) {
                    int iName = cur.getColumnIndexOrThrow(Phone.DISPLAY_NAME);
                    int iNum = cur.getColumnIndexOrThrow(Phone.NUMBER);
                    while (cur.moveToNext() && out.size() < 3000) {
                        String name = cur.getString(iName);
                        String num = clean(cur.getString(iNum));
                        if (num.isEmpty() || !seen.add(num)) continue;   // ekoi number duibar na
                        out.add(new Contact(name == null || name.trim().isEmpty() ? num : name.trim(), num));
                    }
                }
            } catch (RuntimeException e) {
                Log.e("CONTACTS", "query failed", e);
            }
            Log.d("CONTACTS", "loaded " + out.size() + " contacts");
            MAIN.post(() -> cb.onResult(out));
        });
    }

    private static String clean(String raw) {
        return raw == null ? "" : raw.replaceAll("[^0-9+]", "");
    }
}