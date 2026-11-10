package com.campuspulse.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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

    private CollectionReference col() {
        return FirebaseFirestore.getInstance().collection("study_resources");
    }

    private static List<StudyResource> fromSnapshot(QuerySnapshot snap) {
        List<StudyResource> list = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) list.add(StudyResource.from(d));
        Collections.sort(list, (a, b) -> {
            int c = a.courseCode.toLowerCase(Locale.ROOT).compareTo(b.courseCode.toLowerCase(Locale.ROOT));
            return c != 0 ? c : a.title.toLowerCase(Locale.ROOT).compareTo(b.title.toLowerCase(Locale.ROOT));
        });
        return list;
    }

    private static StudyResource.Data wrap(List<StudyResource> list) {
        StudyResource.Data data = new StudyResource.Data();
        for (int i = 0; i < Departments.CODES.length; i++) {
            StudyResource.Dept d = new StudyResource.Dept();
            d.code = Departments.CODES[i];
            d.name = Departments.NAMES[i];
            data.departments.add(d);
        }
        data.resources.addAll(list);
        return data;
    }

    /** Student: live list. Error hole ba cache theke khali ashle bundled JSON dekhay. */
    public ListenerRegistration listen(CacheRepository.Callback<StudyResource.Data> cb) {
        return col().addSnapshotListener((snap, e) -> {
            if (e != null || snap == null || (snap.isEmpty() && snap.getMetadata().isFromCache())) {
                load(data -> cb.onResult(wrap(data.resources)));
                return;
            }
            cb.onResult(wrap(fromSnapshot(snap)));
        });
    }

    /** Admin: shudhu Firestore. Fail hole null ashe. */
    public ListenerRegistration listenRaw(CacheRepository.Callback<List<StudyResource>> cb) {
        return col().addSnapshotListener((snap, e) -> {
            if (e != null || snap == null) {
                cb.onResult(null);
                return;
            }
            cb.onResult(fromSnapshot(snap));
        });
    }

    /** Admin: bundled study_hub.json er sob resource Firestore e dhokay. -1 mane fail. */
    public void seedFromJson(CacheRepository.Callback<Integer> cb) {
        io.execute(() -> {
            List<StudyResource> list = loadBlocking().resources;
            if (list.isEmpty()) {
                main.post(() -> cb.onResult(-1));
                return;
            }
            WriteBatch batch = FirebaseFirestore.getInstance().batch();
            for (StudyResource r : list) batch.set(col().document(r.id), r.toMap());
            batch.commit()
                    .addOnSuccessListener(v -> cb.onResult(list.size()))
                    .addOnFailureListener(e -> {
                        Log.e("STUDY", "seed failed", e);
                        cb.onResult(-1);
                    });
        });
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