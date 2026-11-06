package com.campuspulse.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;

import com.campuspulse.app.CacheContract.Meta;

import org.json.JSONException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherRepository {

    // KUET এলাকা (আনুমানিক)। query parameter: latitude, longitude, current, daily, timezone, forecast_days
    public static final String URL = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=22.8997&longitude=89.5020"
            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
            + "&timezone=Asia%2FDhaka&forecast_days=4";

    private static final String KEY_JSON = "weather_json";
    private static final String KEY_AT = "weather_at";
    private static final long FRESH_MS = 20L * 60L * 1000L; // ২০ মিনিটের মধ্যে আবার network এ যাব না

    public static class Result {
        public WeatherModel weather;   // null হলে কিছুই নেই
        public long fetchedAt;
        public boolean fromCache;
        public String error;           // null হলে সব ঠিক
        public int httpCode;
        public String contentType = "";
        public long millis;
    }

    private static volatile WeatherRepository instance;

    public static WeatherRepository get(Context c) {
        if (instance == null) {
            synchronized (WeatherRepository.class) {
                if (instance == null) instance = new WeatherRepository(c.getApplicationContext());
            }
        }
        return instance;
    }

    private final Context app;
    private final ContentResolver cr;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private WeatherRepository(Context app) {
        this.app = app;
        this.cr = app.getContentResolver();
    }

    /** শুধু saved data, network নয়। কিছু না থাকলে null আসে। */
    public void loadCached(CacheRepository.Callback<Result> cb) {
        io.execute(() -> {
            Result r = readCache();
            main.post(() -> cb.onResult(r));
        });
    }

    /** force=false হলে ২০ মিনিটের মধ্যের data থাকলে network এ যায় না। Result সবসময় non-null। */
    public void refresh(boolean force, CacheRepository.Callback<Result> cb) {
        io.execute(() -> {
            Result cached = readCache();
            Result out;
            if (!force && cached != null
                    && System.currentTimeMillis() - cached.fetchedAt < FRESH_MS) {
                out = cached;
            } else {
                out = fetchNetwork(cached);
            }
            main.post(() -> cb.onResult(out));
        });
    }

    private Result fetchNetwork(Result cached) {
        if (!NetUtil.isOnline(app)) return failure(cached, "You're offline", null);

        String error = null;
        ApiClient.ApiResponse last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {   // একবার retry
            try {
                ApiClient.ApiResponse r = ApiClient.get(URL);
                last = r;
                if (r.isSuccess()) {
                    Result ok = new Result();
                    ok.weather = WeatherModel.parse(r.body);
                    ok.fetchedAt = System.currentTimeMillis();
                    ok.httpCode = r.code;
                    ok.contentType = r.contentType;
                    ok.millis = r.millis;
                    save(r.body, ok.fetchedAt);
                    return ok;
                }
                error = "Weather service error (HTTP " + r.code + ")";
            } catch (UnknownHostException e) {
                error = "Can't reach the weather server";
            } catch (SocketTimeoutException e) {
                error = "The weather server took too long";
            } catch (JSONException e) {
                error = "Unexpected response from the weather server";
                break;                                      // retry করে লাভ নেই
            } catch (IOException e) {
                error = "Network problem";
            }
            if (attempt < 2) {
                try { Thread.sleep(800); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return failure(cached, error, last);
    }

    private Result failure(Result cached, String error, ApiClient.ApiResponse last) {
        Result f = new Result();
        f.error = error;
        if (cached != null) {
            f.weather = cached.weather;
            f.fetchedAt = cached.fetchedAt;
            f.fromCache = true;
        }
        if (last != null) {
            f.httpCode = last.code;
            f.contentType = last.contentType;
            f.millis = last.millis;
        }
        return f;
    }

    // ---------- SQLite (meta table, ContentProvider এর মাধ্যমে) ----------
    private void save(String json, long at) {
        put(KEY_JSON, json);
        put(KEY_AT, String.valueOf(at));
    }

    private void put(String key, String value) {
        ContentValues v = new ContentValues();
        v.put(Meta.KEY, key);
        v.put(Meta.VALUE, value);
        try { cr.insert(Meta.CONTENT_URI, v); } catch (RuntimeException ignored) { }
    }

    private String meta(String key) {
        try (Cursor c = cr.query(Meta.CONTENT_URI, new String[]{Meta.VALUE},
                Meta.KEY + " = ?", new String[]{key}, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException ignored) { }
        return null;
    }

    private Result readCache() {
        String json = meta(KEY_JSON);
        if (json == null) return null;
        try {
            Result r = new Result();
            r.weather = WeatherModel.parse(json);
            String at = meta(KEY_AT);
            r.fetchedAt = at == null ? 0L : Long.parseLong(at);
            r.fromCache = true;
            return r;
        } catch (JSONException | NumberFormatException e) {
            return null;
        }
    }
}