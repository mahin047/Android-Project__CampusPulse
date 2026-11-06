package com.campuspulse.app;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.campuspulse.app.databinding.ActivityNoticesBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NoticesActivity extends AppCompatActivity implements NoticeAdapter.Listener {

    private static final String[] FILTERS = {"All", "Saved", "Academic", "Exam", "Event", "General"};

    private ActivityNoticesBinding binding;
    private FirebaseFirestore db;
    private CacheRepository repo;
    private NoticeAdapter adapter;
    private ConnectivityBanner banner;

    private final List<NoticeModel> all = new ArrayList<>();
    private final Set<String> saved = new HashSet<>();
    private final List<TextView> chips = new ArrayList<>();
    private String filter = "All";
    private String query = "";
    private boolean isAdmin = false;
    private long lastSyncMs = 0L;
    private String uid;

    // SQLite বদলালে (sync, bookmark) এই observer জাগে
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reloadTask = this::reload;
    private final ContentObserver observer = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) { scheduleReload(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNoticesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }
        uid = u.getUid();

        db = FirebaseFirestore.getInstance();
        repo = CacheRepository.get(this);
        banner = new ConnectivityBanner(this, binding.tvOffline);

        adapter = new NoticeAdapter(this);
        binding.rvNotices.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotices.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddNoticeActivity.class)));
        binding.tvSyncInfo.setOnClickListener(v -> showCacheInfo());
        binding.etSearch.setHint("Search notices");
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });

        binding.loading.setVisibility(View.VISIBLE);
        buildChips();
        checkAdmin();
    }

    // ---------- lifecycle: observer ও banner ----------
    @Override
    protected void onStart() {
        super.onStart();
        if (uid == null) return;
        banner.start();
        ContentResolver cr = getContentResolver();
        cr.registerContentObserver(CacheContract.Notices.CONTENT_URI, true, observer);
        cr.registerContentObserver(CacheContract.Bookmarks.CONTENT_URI, true, observer);
        cr.registerContentObserver(CacheContract.Meta.CONTENT_URI, true, observer);
        reload();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (uid == null) return;
        banner.stop();
        getContentResolver().unregisterContentObserver(observer);
        handler.removeCallbacks(reloadTask);
    }


    private void scheduleReload() {
        handler.removeCallbacks(reloadTask);
        handler.postDelayed(reloadTask, 150L); // একসাথে অনেক change এলে একবারই reload
    }

    // ---------- SQLite থেকে পড়া (background), UI update (main) ----------
    private void reload() {
        repo.loadNotices(uid, data -> {
            if (isDestroyed()) return;
            all.clear();
            all.addAll(data.items);
            saved.clear();
            saved.addAll(data.savedIds);
            lastSyncMs = data.lastSync;
            binding.loading.setVisibility(View.GONE);
            applyFilter();
            updateSyncInfo();
        });
    }

    private void updateSyncInfo() {
        boolean online = NetUtil.isOnline(this);
        String sync = lastSyncMs == 0L ? "not synced yet" : "synced " + TimeUtil.ago(new Date(lastSyncMs));
        binding.tvSyncInfo.setText((online ? "● Live  •  " : "○ Offline, showing saved data  •  ")
                + sync + "  •  tap for cache info");
        binding.tvSyncInfo.setTextColor(Color.parseColor(online ? "#2DD4BF" : "#F5B942"));
    }

    // ---------- chips ----------
    private void buildChips() {
        for (String name : FILTERS) {
            TextView chip = new TextView(this);
            chip.setText(name.equals("Saved") ? "★ Saved" : name);
            chip.setTag(name);
            chip.setTextSize(13);
            chip.setTypeface(ResourcesCompat.getFont(this, R.font.poppins_bold));
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            chip.setMinimumHeight(dp(48));
            chip.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                filter = name;
                styleChips();
                applyFilter();
            });
            chips.add(chip);
            binding.chipRow.addView(chip);
        }
        styleChips();
    }

    private void styleChips() {
        for (TextView chip : chips) {
            boolean selected = filter.equals(chip.getTag());
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chip.setTextColor(selected ? Color.parseColor("#06201E") : Color.parseColor("#C3D2EA"));
        }
    }

    private void applyFilter() {
        List<NoticeModel> shown = new ArrayList<>();
        for (NoticeModel n : all) {
            boolean match;
            if (filter.equals("All")) match = true;
            else if (filter.equals("Saved")) match = saved.contains(n.getId());
            else match = filter.equalsIgnoreCase(n.getCategory());
            if (match && !query.isEmpty()) {
                match = has(n.getTitle()) || has(n.getBody()) || has(n.getCategory());
            }
            if (match) shown.add(n);
        }
        adapter.submitList(shown);
        binding.tvCount.setText(shown.size() + (shown.size() == 1 ? " notice" : " notices"));

        if (shown.isEmpty()) {

            if (!query.isEmpty()) {
                showEmpty("No results", "Nothing matches \"" + query + "\".");
            } else if (all.isEmpty() && lastSyncMs == 0L) {
                if (NetUtil.isOnline(this)) {
                    showEmpty("Downloading notices…", "The first sync is in progress.");
                } else {
                    showEmpty("Nothing saved yet", "Connect to the internet once so notices can be saved on this phone.");
                }
            } else if (all.isEmpty()) {
                showEmpty("No notices yet", "New notices from the administration will appear here.");
            } else if (filter.equals("Saved")) {
                showEmpty("No saved notices", "Open a notice and tap the star to keep it for offline reading.");
            } else {
                showEmpty("Nothing in " + filter, "Try another category.");
            }
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.rvNotices.setVisibility(View.VISIBLE);
        }
    }
    private boolean has(String s) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(query);
    }

    private void showEmpty(String title, String sub) {
        binding.tvEmptyTitle.setText(title);
        binding.tvEmptySub.setText(sub);
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.rvNotices.setVisibility(View.INVISIBLE);
    }

    // ---------- admin ----------
    private void checkAdmin() {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    isAdmin = role != null && role.trim().equalsIgnoreCase("admin");
                    binding.fabAdd.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
                });
    }

    // ---------- cache info (Callable + Future + aggregate SQL) ----------
    private void showCacheInfo() {
        repo.loadStats(uid, s -> {
            if (isDestroyed()) return;
            if (s == null) {
                Toast.makeText(this, "Could not read the cache.", Toast.LENGTH_SHORT).show();
                return;
            }
            String msg = "Database: campus_cache.db (SQLite)\n\n"
                    + "Notices saved: " + s.notices + "\n"
                    + "By category: " + s.byCategory + "\n"
                    + "Latest notice: " + (s.latestNotice == 0 ? "-" : TimeUtil.ago(new Date(s.latestNotice))) + "\n"
                    + "Events saved: " + s.events + "\n"
                    + "Your saved (★): " + s.saved + "\n\n"
                    + "Notices synced: " + (s.lastSyncNotices == 0 ? "never" : TimeUtil.ago(new Date(s.lastSyncNotices))) + "\n"
                    + "Events synced: " + (s.lastSyncEvents == 0 ? "never" : TimeUtil.ago(new Date(s.lastSyncEvents)));
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Offline cache")
                    .setMessage(msg)
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Thread demo", (d, w) -> repo.runThreadDemo(text ->
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle("Race condition demo")
                                    .setMessage(text)
                                    .setPositiveButton("OK", null)
                                    .show()))
                    .show();
        });
    }

    // ---------- adapter callbacks ----------
    @Override
    public void onNoticeClick(NoticeModel n) {
        Intent i = new Intent(this, NoticeDetailActivity.class); // explicit intent
        i.putExtra(NoticeAlerts.EXTRA_ID, n.getId());            // star এর জন্য id লাগে
        i.putExtra(NoticeDetailActivity.EXTRA_TITLE, n.getTitle());
        i.putExtra(NoticeDetailActivity.EXTRA_BODY, n.getBody());
        i.putExtra(NoticeDetailActivity.EXTRA_CATEGORY, n.getCategory());
        i.putExtra(NoticeDetailActivity.EXTRA_PRIORITY, n.getPriority());
        i.putExtra(NoticeDetailActivity.EXTRA_AUTHOR, n.getAuthorName());
        i.putExtra(NoticeDetailActivity.EXTRA_TIME,
                n.getCreatedAt() == null ? -1L : n.getCreatedAt().getTime());
        startActivity(i);
    }

    @Override
    public void onNoticeLongClick(NoticeModel n) {
        if (!isAdmin) return; // শুধু admin মুছতে পারবে (Firestore থেকে মুছলে sync SQLite থেকেও সরাবে)
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete notice?")
                .setMessage("\"" + n.getTitle() + "\" will be removed for everyone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) ->
                        db.collection("notices").document(n.getId()).delete()
                                .addOnFailureListener(e -> Toast.makeText(this,
                                        "Could not delete.", Toast.LENGTH_LONG).show()))
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}