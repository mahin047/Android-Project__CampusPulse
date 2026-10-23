package com.campuspulse.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/** Shows "offline / back online" on any screen. Call start() in onStart, stop() in onStop. */
public class ConnectivityBanner {

    private static final long DEBOUNCE_MS = 700L;
    private static final long BACK_ONLINE_MS = 2500L;

    private final Context ctx;
    private final TextView view;
    private final ConnectivityManager cm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean started = false;
    private Boolean online = null; // null = এখনো জানি না

    private final Runnable evaluateTask = this::evaluate;
    private final Runnable hideTask = this::hideBanner;

    // dynamic receiver: system broadcast আসলে একটু অপেক্ষা করে আসল অবস্থা মেপে নেয়
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            scheduleCheck();
        }
    };

    // backup: NetworkCallback অন্য thread এ চলে, তাই শুধু handler এ post করা হয়
    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(@NonNull Network n) { scheduleCheck(); }
        @Override public void onLost(@NonNull Network n) { scheduleCheck(); }
    };

    public ConnectivityBanner(Context ctx, TextView view) {
        this.ctx = ctx;
        this.view = view;
        this.cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @SuppressWarnings("deprecation") // CONNECTIVITY_ACTION deprecated, কিন্তু dynamic receiver এ এখনো আসে
    public void start() {
        if (started) return;
        started = true;
        online = null;

        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        // sender হলো system। receiver intent এর কোনো data পড়ে না, শুধু নিজে network মাপে, তাই EXPORTED নিরাপদ
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED);

        if (cm != null) {
            try { cm.registerDefaultNetworkCallback(callback); } catch (RuntimeException ignored) { }
        }
        evaluate(); // প্রথম অবস্থা সাথে সাথে
    }

    public void stop() {
        if (!started) return;
        started = false;
        handler.removeCallbacksAndMessages(null);
        try { ctx.unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) { }
        if (cm != null) {
            try { cm.unregisterNetworkCallback(callback); } catch (RuntimeException ignored) { }
        }
        view.animate().cancel();
    }

    private void scheduleCheck() {
        handler.removeCallbacks(evaluateTask);
        handler.postDelayed(evaluateTask, DEBOUNCE_MS);
    }

    private void evaluate() {
        boolean now = NetUtil.isOnline(ctx);
        if (online != null && online == now) return; // কিছু বদলায়নি

        boolean wasOffline = online != null && !online;
        online = now;
        handler.removeCallbacks(hideTask);

        if (!now) {
            show("📡  You're offline. Saved data is shown. SOS needs internet, call 999 in an emergency.",
                    "#F5B942");
        } else if (wasOffline) {
            show("✓  Back online. Syncing latest data…", "#34D399");
            handler.postDelayed(hideTask, BACK_ONLINE_MS);
        } else {
            hideBanner(); // শুরুতেই online, কিছু দেখানোর নেই
        }
    }

    private void show(String text, String color) {
        view.setText(text);
        view.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(color)));
        view.setTextColor(Color.parseColor("#06201E"));
        view.animate().cancel();
        if (view.getVisibility() != View.VISIBLE) {
            view.setAlpha(0f);
            view.setTranslationY(-24f);
            view.setVisibility(View.VISIBLE);
        }
        view.animate().alpha(1f).translationY(0f).setDuration(250).start();
    }

    private void hideBanner() {
        if (view.getVisibility() != View.VISIBLE) return;
        view.animate().cancel();
        view.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> view.setVisibility(View.GONE)).start();
    }
}