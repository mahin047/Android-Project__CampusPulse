package com.campuspulse.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.PopupMenu;

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

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "LIFECYCLE";

    private ActivityDashboardBinding binding;
    private ListenerRegistration noticeReg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Dashboard onCreate");
        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.tvGreeting.setText(greetingForNow());
        loadUserName();
        setupModules();

        binding.noticeCard.setOnClickListener(v -> openNotices());
        binding.sosBanner.setOnClickListener(v ->
                open("Campus SOS", "The emergency alert is built on Day 9."));
        binding.tvAvatar.setOnClickListener(v -> showProfileMenu());

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
        items.add(new ModuleItem("Reports", "Reports & Feedback",
                R.drawable.ic_report, R.drawable.bg_tile_orange, "Incident reports are built on Day 7."));
        items.add(new ModuleItem("Study", "Atom Model",
                R.drawable.ic_study, R.drawable.bg_tile_teal, "Study Hub is built on Day 12."));
        items.add(new ModuleItem("Services", "Medical & Wellness",
                R.drawable.ic_services, R.drawable.bg_tile_red, "Campus Services are built on Day 12."));

        binding.rvModules.setLayoutManager(new GridLayoutManager(this, 2));
        binding.rvModules.setNestedScrollingEnabled(false);
        binding.rvModules.setAdapter(new ModuleAdapter(items, item -> {
            if ("Notices".equals(item.getTitle())) openNotices();
            else open(item.getTitle(), item.getNote());
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
                });
    }

    private void showProfileMenu() {
        PopupMenu menu = new PopupMenu(this, binding.tvAvatar);
        menu.getMenu().add(0, 1, 0, "Log out");
        menu.setOnMenuItemClickListener(item -> {
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
    }
    @Override protected void onResume()  { super.onResume();  Log.d(TAG, "Dashboard onResume"); }
    @Override protected void onPause()   { super.onPause();   Log.d(TAG, "Dashboard onPause"); }
    @Override protected void onStop() {
        super.onStop();
        Log.d(TAG, "Dashboard onStop");
        if (noticeReg != null) { noticeReg.remove(); noticeReg = null; }
    }
    @Override protected void onRestart() { super.onRestart(); Log.d(TAG, "Dashboard onRestart"); }
    @Override protected void onDestroy() { super.onDestroy(); Log.d(TAG, "Dashboard onDestroy"); }
}