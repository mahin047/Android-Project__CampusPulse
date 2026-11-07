package com.campuspulse.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StudyRepository {

    private static volatile StudyRepository instance;

    public static StudyRepository get(Context c) {
        if (instance == null) {
            synchronized (StudyRepository.class) {
                if (instance == null) instance = new StudyRepository(c.getApplicationContext());
            }
        }
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private StudyResource.Data cache;

    private StudyRepository(Context app) {
        this.app = app;
    }

    public void load(CacheRepository.Callback<StudyResource.Data> cb) {
        io.execute(() -> {
            StudyResource.Data data = loadBlocking();
            main.post(() -> cb.onResult(data));
        });
    }

    private synchronized StudyResource.Data loadBlocking() {
        if (cache == null) {
            try (InputStream in = app.getResources().openRawResource(R.raw.study_hub);
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                cache = StudyResource.parse(sb.toString());
            } catch (IOException | JSONException e) {
                Log.e("STUDY", "study_hub.json failed", e);
                return new StudyResource.Data();
            }
        }
        return cache;
    }
}