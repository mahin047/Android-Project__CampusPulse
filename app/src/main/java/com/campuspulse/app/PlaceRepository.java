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

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
    private List<CampusPlace> xmlCache;

    private PlaceRepository(Context app) {
        this.app = app;
    }

    private CollectionReference col() {
        return FirebaseFirestore.getInstance().collection("services");
    }

    private static List<CampusPlace> fromSnapshot(QuerySnapshot snap) {
        List<CampusPlace> list = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) list.add(CampusPlace.from(d));
        Collections.sort(list, (a, b) -> a.name.toLowerCase(Locale.ROOT)
                .compareTo(b.name.toLowerCase(Locale.ROOT)));
        return list;
    }

    /** Student: live list। Firestore খালি বা ব্যর্থ হলে bundled XML দেখায়। */
    public ListenerRegistration listen(CacheRepository.Callback<List<CampusPlace>> cb) {
        return col().addSnapshotListener((snap, e) -> {
            if (e != null || snap == null || snap.isEmpty()) {
                loadXml(cb);
                return;
            }
            cb.onResult(fromSnapshot(snap));
        });
    }

    /** Admin: শুধু Firestore (edit করার জন্য আসল document লাগে)। ব্যর্থ হলে null আসে। */
    public ListenerRegistration listenRaw(CacheRepository.Callback<List<CampusPlace>> cb) {
        return col().addSnapshotListener((snap, e) -> {
            if (e != null || snap == null) {
                cb.onResult(null);
                return;
            }
            cb.onResult(fromSnapshot(snap));
        });
    }

    /** একবার পড়া (Assistant এর জন্য)। */
    public void load(CacheRepository.Callback<List<CampusPlace>> cb) {
        col().get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) loadXml(cb);
                    else cb.onResult(fromSnapshot(snap));
                })
                .addOnFailureListener(e -> loadXml(cb));
    }

    public void loadXml(CacheRepository.Callback<List<CampusPlace>> cb) {
        io.execute(() -> {
            List<CampusPlace> list = loadXmlBlocking();
            main.post(() -> cb.onResult(list));
        });
    }

    /** Admin: bundled XML এর সব জায়গা Firestore এ ঢোকায়। একই id থাকলে overwrite হয়। -1 মানে ব্যর্থ। */
    public void seedFromXml(CacheRepository.Callback<Integer> cb) {
        io.execute(() -> {
            List<CampusPlace> list = loadXmlBlocking();
            if (list.isEmpty()) {
                main.post(() -> cb.onResult(-1));
                return;
            }
            WriteBatch batch = FirebaseFirestore.getInstance().batch();
            for (CampusPlace p : list) batch.set(col().document(p.id), p.toMap());
            batch.commit()
                    .addOnSuccessListener(v -> cb.onResult(list.size()))
                    .addOnFailureListener(e -> {
                        Log.e("SERVICES", "seed failed", e);
                        cb.onResult(-1);
                    });
        });
    }

    private synchronized List<CampusPlace> loadXmlBlocking() {
        if (xmlCache == null) {
            try (InputStream in = app.getResources().openRawResource(R.raw.campus_map)) {
                xmlCache = PlaceXmlParser.parse(in);
            } catch (XmlPullParserException | IOException e) {
                Log.e("XML", "campus_map.xml parse failed", e);
                return new ArrayList<>();
            }
        }
        return xmlCache;
    }
}