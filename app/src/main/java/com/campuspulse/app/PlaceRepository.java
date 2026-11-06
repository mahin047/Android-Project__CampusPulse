package com.campuspulse.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaceRepository {

    private static volatile PlaceRepository instance;

    public static PlaceRepository get(Context c) {
        if (instance == null) {
            synchronized (PlaceRepository.class) {
                if (instance == null) instance = new PlaceRepository(c.getApplicationContext());
            }
        }
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private List<CampusPlace> cache;

    private PlaceRepository(Context app) {
        this.app = app;
    }

    /** Background এ parse (প্রথমবার), তারপর memory থেকে। Callback main thread এ আসে। */
    public void load(CacheRepository.Callback<List<CampusPlace>> cb) {
        io.execute(() -> {
            List<CampusPlace> list = loadBlocking();
            main.post(() -> cb.onResult(list));
        });
    }

    private synchronized List<CampusPlace> loadBlocking() {
        if (cache == null) {
            try (InputStream in = app.getResources().openRawResource(R.raw.campus_map)) {
                cache = PlaceXmlParser.parse(in);
                Log.d("XML", "parsed " + cache.size() + " places from campus_map.xml");
            } catch (XmlPullParserException | IOException e) {
                Log.e("XML", "campus_map.xml parse failed", e);
                return new ArrayList<>();
            }
        }
        return cache;
    }
}