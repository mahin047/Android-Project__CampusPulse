package com.campuspulse.app;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.Date;

public class CampusPulseApp extends Application {

    // কয়টা Activity এখন "started" অবস্থায় আছে। > 0 মানে app সামনে আছে
    private static int startedCount = 0;

    public static boolean isForeground() {
        return startedCount > 0;
    }

    private ListenerRegistration noticeReg;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(@NonNull Activity a) { startedCount++; }
            @Override public void onActivityStopped(@NonNull Activity a) { startedCount = Math.max(0, startedCount - 1); }
            @Override public void onActivityCreated(@NonNull Activity a, Bundle b) { }
            @Override public void onActivityResumed(@NonNull Activity a) { }
            @Override public void onActivityPaused(@NonNull Activity a) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
            @Override public void onActivityDestroyed(@NonNull Activity a) { }
        });

        // login/logout হলেই listener নতুন করে বসে বা বন্ধ হয়
        FirebaseAuth.getInstance().addAuthStateListener(auth -> onUserChanged(auth.getCurrentUser()));
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);

        NotificationChannel normal = new NotificationChannel(
                NoticeAlerts.CH_NORMAL, "Campus notices", NotificationManager.IMPORTANCE_DEFAULT);
        normal.setDescription("New notices from the administration");

        NotificationChannel urgent = new NotificationChannel(
                NoticeAlerts.CH_URGENT, "Urgent notices", NotificationManager.IMPORTANCE_HIGH);
        urgent.setDescription("Notices marked Urgent");

        nm.createNotificationChannel(normal);
        nm.createNotificationChannel(urgent);
    }

    // ---------- who is signed in ----------
    private void onUserChanged(FirebaseUser user) {
        stopWatching();
        if (user == null) return;
        final String uid = user.getUid();

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    boolean admin = role != null && role.trim().equalsIgnoreCase("admin");
                    FirebaseUser cur = FirebaseAuth.getInstance().getCurrentUser();
                    // admin এর notification লাগে না, আর এর মধ্যে user বদলে গেলে বাদ
                    if (!admin && cur != null && cur.getUid().equals(uid)) startWatching(uid);
                });
    }

    private void stopWatching() {
        if (noticeReg != null) {
            noticeReg.remove();
            noticeReg = null;
        }
    }

    // ---------- watch the newest notice ----------
    private void startWatching(String uid) {
        stopWatching();
        noticeReg = FirebaseFirestore.getInstance().collection("notices")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null || snap.isEmpty()) return;
                    DocumentSnapshot d = snap.getDocuments().get(0);
                    if (d.getMetadata().hasPendingWrites()) return; // server timestamp এখনো চূড়ান্ত না

                    Date created = d.getDate("createdAt");
                    if (created == null) return;
                    long ts = created.getTime();

                    SharedPreferences sp = getSharedPreferences("notice_prefs", MODE_PRIVATE);
                    String key = "last_ts_" + uid;
                    long last = sp.getLong(key, -1L);

                    if (last == -1L) {                      // প্রথমবার: পুরোনো notice নিয়ে notification নয়
                        sp.edit().putLong(key, ts).apply();
                        return;
                    }
                    if (ts <= last) return;                 // নতুন না
                    sp.edit().putLong(key, ts).apply();
                    if (uid.equals(d.getString("authorUid"))) return;

                    sendNoticeBroadcast(d, ts);
                });
    }

    /** Custom broadcast: setPackage() দিলে শুধু আমাদের app এর receiver পায়। */
    private void sendNoticeBroadcast(DocumentSnapshot d, long ts) {
        Intent i = new Intent(NoticeAlerts.ACTION_NEW_NOTICE).setPackage(getPackageName());
        i.putExtra(NoticeAlerts.EXTRA_ID, d.getId());
        i.putExtra(NoticeDetailActivity.EXTRA_TITLE, d.getString("title"));
        i.putExtra(NoticeDetailActivity.EXTRA_BODY, d.getString("body"));
        i.putExtra(NoticeDetailActivity.EXTRA_CATEGORY, d.getString("category"));
        i.putExtra(NoticeDetailActivity.EXTRA_PRIORITY, d.getString("priority"));
        i.putExtra(NoticeDetailActivity.EXTRA_AUTHOR, d.getString("authorName"));
        i.putExtra(NoticeDetailActivity.EXTRA_TIME, ts);
        sendBroadcast(i);
    }
}