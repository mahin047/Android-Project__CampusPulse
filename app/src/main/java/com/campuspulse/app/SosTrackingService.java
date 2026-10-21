package com.campuspulse.app;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class SosTrackingService extends Service implements LocationListener {

    public static final String ACTION_START = "com.campuspulse.app.action.SOS_START";
    public static final String ACTION_CANCEL = "com.campuspulse.app.action.SOS_CANCEL";
    public static final String ACTION_ENDED = "com.campuspulse.app.action.SOS_ENDED"; // broadcast
    public static final String EXTRA_ALERT_ID = "alert_id";

    private static final String CHANNEL_ID = "sos_live";
    private static final int NOTIF_ID = 9001;
    private static final long MAX_DURATION_MS = 60L * 60L * 1000L; // safety: ১ ঘণ্টা পর নিজে বন্ধ

    /** Activity এই callback দিয়ে live location দেখে (bound service) */
    public interface UpdateListener {
        void onLocation(Location location, int sentCount);
    }

    public class LocalBinder extends Binder {
        SosTrackingService getService() { return SosTrackingService.this; }
    }

    private final IBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LocationManager lm;
    private DatabaseReference alertRef;
    private ValueEventListener statusListener;
    private String alertId;
    private Location lastLocation;
    private int sent = 0;
    private boolean ending = false;
    private UpdateListener updateListener;

    // ---------- lifecycle ----------
    @Override
    public void onCreate() {
        super.onCreate();
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Live SOS tracking", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Shown while your SOS location is being shared");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        String action = intent.getAction();

        if (ACTION_CANCEL.equals(action)) {            // notification এর "I'm safe" button
            cancelFromNotification();
        } else if (ACTION_START.equals(action)) {
            beginTracking(intent.getStringExtra(EXTRA_ALERT_ID));
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        try { lm.removeUpdates(this); } catch (RuntimeException ignored) { }
        if (alertRef != null && statusListener != null) alertRef.removeEventListener(statusListener);
    }

    // ---------- tracking ----------
    private void beginTracking(String id) {
        if (id == null) { stopSelf(); return; }

        // startForegroundService() এর পর ৫ সেকেন্ডের মধ্যে এটা ডাকতেই হবে
        ServiceCompat.startForeground(this, NOTIF_ID,
                buildNotification("Sharing your live location with campus security."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);

        if (id.equals(alertId)) return; // আগেই চলছে, শুধু notification নতুন করা হলো
        alertId = id;
        ending = false;

        alertRef = FirebaseDatabase.getInstance().getReference("sos_alerts").child(id);
        watchStatus();
        startLocationUpdates();
        handler.postDelayed(this::endSelf, MAX_DURATION_MS);
    }

    private void watchStatus() {
        statusListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                String st = snap.child("status").getValue(String.class);
                if (!snap.exists() || SosAlertModel.RESOLVED.equals(st)
                        || SosAlertModel.CANCELLED.equals(st)) {
                    endSelf();
                } else if (SosAlertModel.ACK.equals(st)) {
                    String who = snap.child("handledBy").getValue(String.class);
                    notifyText("Help is on the way. " + (who == null ? "Campus security" : who)
                            + " is responding.");
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) { endSelf(); }
        };
        alertRef.addValueEventListener(statusListener);
    }

    @SuppressLint("MissingPermission") // permission Activity তে আগেই check করা হয়
    private void startLocationUpdates() {
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (!lm.isProviderEnabled(provider)) continue;
                lm.requestLocationUpdates(provider, 5000L, 0f, this, Looper.getMainLooper());
                Location last = lm.getLastKnownLocation(provider);
                if (last != null) onLocationChanged(last);
            } catch (SecurityException | IllegalArgumentException ignored) {
                // coarse permission থাকলে GPS provider নেওয়া যায় না, network provider কাজ করবে
            }
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location l) {
        if (!isBetter(l, lastLocation)) return;
        lastLocation = l;
        push(l);
    }

    // API 30 এর আগের ফোনে এই তিনটা না থাকলে crash হয়
    @Override public void onProviderEnabled(@NonNull String provider) { }
    @Override public void onProviderDisabled(@NonNull String provider) { }
    @SuppressWarnings("deprecation")
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    private boolean isBetter(Location n, Location cur) {
        if (cur == null) return true;
        long dt = n.getTime() - cur.getTime();
        if (dt > 30_000) return true;
        if (dt < -30_000) return false;
        return n.hasAccuracy() && (!cur.hasAccuracy() || n.getAccuracy() < cur.getAccuracy());
    }

    private void push(Location l) {
        if (alertRef == null) return;
        Map<String, Object> m = new HashMap<>();
        m.put("lat", Math.round(l.getLatitude() * 1e5) / 1e5);   // ~১ মিটার precision
        m.put("lng", Math.round(l.getLongitude() * 1e5) / 1e5);
        m.put("accuracy", Math.round(l.getAccuracy()));
        m.put("updatedAt", ServerValue.TIMESTAMP);
        alertRef.updateChildren(m);
        sent++;
        if (updateListener != null) updateListener.onLocation(l, sent);
    }

    // ---------- stop ----------
    private void cancelFromNotification() {
        String id = alertId != null ? alertId : SosPrefs.getAnyId(this);
        if (id != null) {
            FirebaseDatabase.getInstance().getReference("sos_alerts").child(id)
                    .child("status").setValue(SosAlertModel.CANCELLED);
        }
        endSelf();
    }

    private void endSelf() {
        if (ending) return;
        ending = true;
        SosPrefs.clear(this);
        // component communication: Activity কে জানাও (শুধু নিজের app এ যাবে)
        sendBroadcast(new Intent(ACTION_ENDED).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // ---------- notification ----------
    private Notification buildNotification(String text) {
        Intent open = new Intent(this, SosActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent cancel = new Intent(this, SosTrackingService.class).setAction(ACTION_CANCEL);
        PendingIntent cancelPi = PendingIntent.getService(this, 1, cancel,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sos)
                .setContentTitle("SOS active")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setColor(0xFFFF4D5E)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openPi)
                .addAction(0, "I'm safe — cancel", cancelPi)
                .build();
    }

    private void notifyText(String text) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIF_ID, buildNotification(text));
    }

    // ---------- bound-service API ----------
    public void setUpdateListener(UpdateListener l) { updateListener = l; }
    public Location getLastLocation() { return lastLocation; }
    public int getSentCount() { return sent; }
}