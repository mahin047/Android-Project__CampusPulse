package com.campuspulse.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.PopupMenu;
import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.view.ViewCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.Date;

import com.campuspulse.app.databinding.ActivityDashboardBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import androidx.appcompat.app.AlertDialog;
import com.campuspulse.app.databinding.DialogFeedbackBinding;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "LIFECYCLE";

    private ActivityDashboardBinding binding;
    private ListenerRegistration noticeReg;

    private ConnectivityBanner banner;
    private boolean noticeReceiverOn = false;

    // dynamic receiver: app সামনে থাকলে নতুন notice এ Snackbar
    private final BroadcastReceiver noticeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String title = i.getStringExtra(NoticeDetailActivity.EXTRA_TITLE);
            Snackbar.make(binding.getRoot(), "New notice: " + (title == null ? "" : title),
                            Snackbar.LENGTH_LONG)
                    .setAction("View", v -> openNotices())
                    .show();
        }
    };

    // runtime permission launcher (Android 13+)
    private final ActivityResultLauncher<String> notifLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> Toast.makeText(this,
                    granted ? "Notifications enabled ✓"
                            : "No problem. You can turn them on later from the profile menu.",
                    Toast.LENGTH_LONG).show());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Dashboard onCreate");
        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        banner = new ConnectivityBanner(this, binding.tvOffline);

        binding.tvGreeting.setText(greetingForNow());
        loadUserName();
        setupModules();

        binding.noticeCard.setOnClickListener(v -> openNotices());
        binding.sosBanner.setOnClickListener(v ->
                startActivity(new Intent(this, SosActivity.class)));
        binding.tvAvatar.setOnClickListener(v -> showProfileMenu());
        binding.assistantCard.setOnClickListener(v ->
                startActivity(new Intent(this, AssistantActivity.class)));
        binding.tvWeather.setOnClickListener(v ->
                startActivity(new Intent(this, WeatherActivity.class)));
        ViewCompat.setAccessibilityHeading(binding.tvModulesTitle, true);
        playIntro();
    }

    // ---------- Greeting & user ----------
    private String greetingForNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good morning,";
        if (hour < 17) return "Good afternoon,";
        if (hour < 21) return "Good evening,";
        return "Good night,";
    }

    private void loadUserName() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;
        FirebaseFirestore.getInstance().collection("users").document(u.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String full = doc.getString("name");
                    if (full == null || full.trim().isEmpty()) return;
                    String first = full.trim().split("\\s+")[0];
                    binding.tvName.setText(first);
                    binding.tvAvatar.setText(first.substring(0, 1).toUpperCase());
                });
    }

    // ---------- Module grid (RecyclerView + GridLayoutManager) ----------
    private void setupModules() {
        List<ModuleItem> items = new ArrayList<>();
        items.add(new ModuleItem("Notices", "Recent Updates",
                R.drawable.ic_notice, R.drawable.bg_tile_blue, "Notices are built on Day 4."));
        items.add(new ModuleItem("Events", "Upcoming Calendar",
                R.drawable.ic_event, R.drawable.bg_tile_green, "Events are built on Day 5."));
        items.add(new ModuleItem("Lost & Found", "Classic Key/Altes",
                R.drawable.ic_lost_found, R.drawable.bg_tile_purple, "Lost & Found is built on Day 6."));
        items.add(new ModuleItem("Reports", "TEST123",
                R.drawable.ic_report, R.drawable.bg_tile_orange, "Incident reports are built on Day 7."));
        items.add(new ModuleItem("Study", "Notes & Questions",
                R.drawable.ic_study, R.drawable.bg_tile_teal, "Study Hub is built on Day 12."));
        items.add(new ModuleItem("Services", "Medical & Wellness",
                R.drawable.ic_services, R.drawable.bg_tile_red, "Campus Services are built on Day 12."));

        binding.rvModules.setLayoutManager(new GridLayoutManager(this, getResources().getInteger(R.integer.grid_columns)));
        binding.rvModules.setNestedScrollingEnabled(false);
        binding.rvModules.setAdapter(new ModuleAdapter(items, item -> {
            switch (item.getTitle()) {
                case "Notices": openNotices(); break;
                case "Events": startActivity(new Intent(this, EventsActivity.class)); break;
                case "Lost & Found": startActivity(new Intent(this, LostFoundActivity.class)); break;
                case "Reports": startActivity(new Intent(this, IncidentsActivity.class)); break;
                case "Study": startActivity(new Intent(this, StudyHubActivity.class)); break;
                case "Services": startActivity(new Intent(this, ServicesActivity.class)); break;
                default: open(item.getTitle(), item.getNote());
            }
        }));
    }

    // ---------- Navigation ----------
    private void open(String title, String note) {
        Intent i = new Intent(this, ModuleActivity.class); // explicit intent
        i.putExtra(ModuleActivity.EXTRA_TITLE, title);     // data passed to next Activity
        i.putExtra(ModuleActivity.EXTRA_NOTE, note);
        startActivity(i);
    }
    private void openNotices() {
        startActivity(new Intent(this, NoticesActivity.class));
    }

    private void listenLatestNotice() {
        noticeReg = FirebaseFirestore.getInstance().collection("notices")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;
                    if (snap.isEmpty()) {
                        binding.tvNoticeText.setText("No notices yet. Check back soon.");
                        binding.tvNoticeTime.setText("—");
                        return;
                    }
                    DocumentSnapshot d = snap.getDocuments().get(0);
                    binding.tvNoticeText.setText(d.getString("title"));
                    Date t = d.getDate("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
                    binding.tvNoticeTime.setText(TimeUtil.dashboardTime(t));
                    binding.tvNoticeText.setText(d.getString("title"));
                    binding.noticeCard.setContentDescription("Latest notice: " + d.getString("title")
                            + ". Double tap to open all notices.");
                });
    }

    private void showProfileMenu() {
        PopupMenu menu = new PopupMenu(this, binding.tvAvatar);
        menu.getMenu().add(0, 2, 0, "Notifications");
        menu.getMenu().add(0, 3, 1, "Send feedback");
        menu.getMenu().add(0, 1, 2, "Log out");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 2) {
                onNotificationsMenu();
                return true;
            }
            if (item.getItemId() == 3) {
                showFeedbackDialog();
                return true;
            }
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            return true;
        });
        menu.show();
    }

    // ---------- Intro animation ----------
    private void playIntro() {
        binding.headerRow.setAlpha(0f);
        binding.headerRow.animate().alpha(1f).setDuration(500).start();

        binding.noticeCard.setAlpha(0f);
        binding.noticeCard.setTranslationY(60f);
        binding.noticeCard.animate().alpha(1f).translationY(0f)
                .setStartDelay(200).setDuration(500).start();

        binding.sosBanner.setAlpha(0f);
        binding.sosBanner.setTranslationY(60f);
        binding.sosBanner.animate().alpha(1f).translationY(0f)
                .setStartDelay(1000).setDuration(500).start();
    }

    // ---------- Lifecycle logging (Logcat-এ "LIFECYCLE" খোঁজো) ----------
    @Override protected void onStart() {
        super.onStart();
        Log.d(TAG, "Dashboard onStart");
        listenLatestNotice();
        loadWeather();

        banner.start();
        ContextCompat.registerReceiver(this, noticeReceiver,
                new IntentFilter(NoticeAlerts.ACTION_NEW_NOTICE), ContextCompat.RECEIVER_NOT_EXPORTED);
        noticeReceiverOn = true;
        maybeAskNotifications();
    }
    @Override protected void onResume()  { super.onResume();  Log.d(TAG, "Dashboard onResume"); }
    @Override protected void onPause()   { super.onPause();   Log.d(TAG, "Dashboard onPause"); }
    @Override protected void onStop() {
        super.onStop();
        Log.d(TAG, "Dashboard onStop");
        if (noticeReg != null) { noticeReg.remove(); noticeReg = null; }
        banner.stop();
        if (noticeReceiverOn) {
            unregisterReceiver(noticeReceiver);
            noticeReceiverOn = false;
        }
    }
    @Override protected void onRestart() { super.onRestart(); Log.d(TAG, "Dashboard onRestart"); }
    @Override protected void onDestroy() { super.onDestroy(); Log.d(TAG, "Dashboard onDestroy"); }

    // ---------- notification permission flow ----------
    private boolean needsNotifPermission() {
        return Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    /** প্রথমবার: আগে নিজের ভাষায় কারণ বুঝিয়ে, তারপর system dialog। */
    private void maybeAskNotifications() {
        if (!needsNotifPermission()) return;
        SharedPreferences sp = getSharedPreferences("app_prefs", MODE_PRIVATE);
        if (sp.getBoolean("notif_prompted", false)) return;   // বারবার বিরক্ত নয়
        sp.edit().putBoolean("notif_prompted", true).apply();

        new MaterialAlertDialogBuilder(this)
                .setTitle("Stay informed")
                .setMessage("Allow notifications to hear about new notices and SOS updates, "
                        + "even when the app is in the background.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Allow", (d, w) ->
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS))
                .show();
    }

    /** Profile menu → Notifications: অবস্থা অনুযায়ী ঠিক কাজটা করে। */
    private void onNotificationsMenu() {
        if (needsNotifPermission()) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.POST_NOTIFICATIONS)) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);  // একবার deny করেছে, আবার চাওয়া যায়
            } else {
                openNotificationSettings();                                    // স্থায়ীভাবে deny, শুধু Settings
            }
        } else if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            openNotificationSettings();                                        // system settings এ বন্ধ করা
        } else {
            Toast.makeText(this, "Notifications are on ✓", Toast.LENGTH_SHORT).show();
        }
    }

    private void openNotificationSettings() {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS) // implicit intent
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "Open Settings → Apps → CampusPulse → Notifications.",
                    Toast.LENGTH_LONG).show();
        }
    }

    // ---------- live weather (REST + SQLite cache) ----------
    private void loadWeather() {
        WeatherRepository repo = WeatherRepository.get(this);
        repo.loadCached(r -> showWeather(r));
        repo.refresh(false, r -> showWeather(r));
    }

    private void showWeather(WeatherRepository.Result r) {
        if (isDestroyed() || r == null || r.weather == null) return;
        binding.tvWeather.setText("Khulna  " + Math.round(r.weather.temp) + "°C");
    }

    // ---------- feedback (HTTP POST) ----------
    private void showFeedbackDialog() {
        DialogFeedbackBinding fb = DialogFeedbackBinding.inflate(getLayoutInflater());
        AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle("Send feedback")
                .setView(fb.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", null)   // নিচে নিজে handle করব, যাতে error এ dialog বন্ধ না হয়
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String msg = String.valueOf(fb.etMessage.getText()).trim();
            fb.til.setError(null);
            if (msg.length() < 5) {
                fb.til.setError("Write at least 5 characters");
                return;
            }
            v.setEnabled(false);
            FeedbackApi.send(this, msg, result -> {
                if (isDestroyed()) return;
                d.dismiss();
                Toast.makeText(this, result, Toast.LENGTH_LONG).show();
            });
        }));
        d.show();
    }

}