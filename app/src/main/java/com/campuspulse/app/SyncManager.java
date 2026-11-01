package com.campuspulse.app;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/** Login থাকলে Firestore এর notices ও events শোনে এবং SQLite এ লেখে। */
public class SyncManager {

    private final CacheRepository repo;
    private ListenerRegistration noticesReg, eventsReg;

    public SyncManager(Context ctx) {
        repo = CacheRepository.get(ctx);
    }

    public void start() {
        stop();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        noticesReg = db.collection("notices").addSnapshotListener((snap, e) -> {
            if (e != null || snap == null) return;
            // offline এ cache ফাঁকা থাকলে Firestore ফাঁকা snapshot দেয়। এটা দিয়ে আমাদের SQLite মুছে ফেলা চলবে না
            if (snap.isEmpty() && snap.getMetadata().isFromCache()) return;

            List<NoticeModel> list = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                NoticeModel n = new NoticeModel();
                n.setId(d.getId());
                n.setTitle(d.getString("title"));
                n.setBody(d.getString("body"));
                n.setCategory(d.getString("category"));
                n.setPriority(d.getString("priority"));
                n.setAuthorName(d.getString("authorName"));
                n.setCreatedAt(d.getDate("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE));
                list.add(n);
            }
            Log.d("SYNC", "notices snapshot: " + list.size() + " (fromCache=" + snap.getMetadata().isFromCache() + ")");
            repo.syncNotices(list);
        });

        eventsReg = db.collection("events").addSnapshotListener((snap, e) -> {
            if (e != null || snap == null) return;
            if (snap.isEmpty() && snap.getMetadata().isFromCache()) return;

            List<EventModel> list = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) list.add(EventModel.from(d));
            Log.d("SYNC", "events snapshot: " + list.size());
            repo.syncEvents(list);
        });
    }

    public void stop() {
        if (noticesReg != null) { noticesReg.remove(); noticesReg = null; }
        if (eventsReg != null) { eventsReg.remove(); eventsReg = null; }
    }
}