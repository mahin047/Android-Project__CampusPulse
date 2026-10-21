package com.campuspulse.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.PopupMenu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.campuspulse.app.databinding.ActivityAdminDashboardBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminDashboardActivity extends AppCompatActivity implements IncidentAdapter.Listener {

    private ActivityAdminDashboardBinding binding;
    private FirebaseFirestore db;
    private IncidentAdapter attentionAdapter;
    private final List<ListenerRegistration> regs = new ArrayList<>();
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { goLogin(); return; }
        uid = u.getUid();

        binding.tvGreeting.setText(greetingForNow());
        binding.tvDate.setText(new SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())
                .format(new Date()));

        setupCards();
        setupAttentionList();
        setupQuickActions();

        binding.tvSeeAll.setOnClickListener(v ->
                startActivity(new Intent(this, IncidentsActivity.class)));
        binding.tvAvatar.setOnClickListener(v -> showMenu());

        verifyAdminAndLoadName();
        playIntro();
    }

    // ---------- role guard + name ----------
    private void verifyAdminAndLoadName() {
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            String role = doc.getString("role");
            if (role == null || !role.trim().equalsIgnoreCase("admin")) {
                // admin না হলে এই screen এ থাকার অধিকার নেই
                startActivity(new Intent(this, DashboardActivity.class));
                finish();
                return;
            }
            String full = doc.getString("name");
            if (full != null && !full.trim().isEmpty()) {
                String first = full.trim().split("\\s+")[0];
                binding.tvName.setText(first);
                binding.tvAvatar.setText(first.substring(0, 1).toUpperCase());
            }
        });
    }

    private String greetingForNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good morning,";
        if (hour < 17) return "Good afternoon,";
        if (hour < 21) return "Good evening,";
        return "Good night,";
    }

    // ---------- stat cards ----------
    private void setupCards() {
        binding.cardPending.setup("Pending", R.drawable.ic_report, Color.parseColor("#FF5A5F"));
        binding.cardInvestigating.setup("Investigating", R.drawable.ic_clock, Color.parseColor("#F5B942"));
        binding.cardNotices.setup("Notices", R.drawable.ic_bell, Color.parseColor("#4DA3FF"));
        binding.cardEvents.setup("Upcoming events", R.drawable.ic_event, Color.parseColor("#3FE0B0"));
        binding.cardUsers.setup("Students", R.drawable.ic_people, Color.parseColor("#9A8BFF"));
        binding.cardSos.setup("SOS alerts", R.drawable.ic_sos, Color.parseColor("#FF8484"));
        binding.cardSos.setValueText("Day 9"); // SOS কাল বানাব
    }

    // ---------- needs attention (reuse IncidentAdapter) ----------
    private void setupAttentionList() {
        attentionAdapter = new IncidentAdapter(this);
        attentionAdapter.setShowReporter(true);
        binding.rvAttention.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAttention.setNestedScrollingEnabled(false);
        binding.rvAttention.setAdapter(attentionAdapter);
    }

    @Override
    public void onIncidentClick(IncidentModel m) {
        Intent i = new Intent(this, IncidentDetailActivity.class); // explicit intent
        i.putExtra(IncidentDetailActivity.EXTRA_ID, m.getId());    // data to next Activity
        startActivity(i);
    }

    // ---------- quick actions (reuse ModuleAdapter) ----------
    private void setupQuickActions() {
        List<ModuleItem> items = new ArrayList<>();
        items.add(new ModuleItem("Notices", "Publish & manage",
                R.drawable.ic_notice, R.drawable.bg_tile_blue, ""));
        items.add(new ModuleItem("Events", "Create & track",
                R.drawable.ic_event, R.drawable.bg_tile_green, ""));
        items.add(new ModuleItem("Lost & Found", "Moderate posts",
                R.drawable.ic_lost_found, R.drawable.bg_tile_purple, ""));
        items.add(new ModuleItem("Incident queue", "Handle reports",
                R.drawable.ic_report, R.drawable.bg_tile_orange, ""));

        binding.rvActions.setLayoutManager(new GridLayoutManager(this, 2));
        binding.rvActions.setNestedScrollingEnabled(false);
        binding.rvActions.setAdapter(new ModuleAdapter(items, item -> {
            switch (item.getTitle()) {
                case "Notices": startActivity(new Intent(this, NoticesActivity.class)); break;
                case "Events": startActivity(new Intent(this, EventsActivity.class)); break;
                case "Lost & Found": startActivity(new Intent(this, LostFoundActivity.class)); break;
                default: startActivity(new Intent(this, IncidentsActivity.class));
            }
        }));
    }

    // ---------- live listeners (lifecycle-aware) ----------
    @Override
    protected void onStart() {
        super.onStart();
        if (uid == null) return;

        // 1) incidents: pending / investigating count + attention list
        regs.add(db.collection("incidents").addSnapshotListener((snap, e) -> {
            if (e != null || snap == null) return;
            int pending = 0, investigating = 0;
            List<IncidentModel> waiting = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                IncidentModel m = IncidentModel.from(d);
                if (m.step() == 0) { pending++; waiting.add(m); }
                else if (m.step() == 1) investigating++;
            }
            binding.cardPending.setValue(pending);
            binding.cardInvestigating.setValue(investigating);

            // সবচেয়ে পুরোনো pending আগে (যে সবচেয়ে বেশি অপেক্ষা করছে)
            Collections.sort(waiting, (a, b) -> {
                Date x = a.getCreatedAt(), y = b.getCreatedAt();
                if (x == null && y == null) return 0;
                if (x == null) return 1;
                if (y == null) return -1;
                return x.compareTo(y);
            });
            List<IncidentModel> top = waiting.size() > 3
                    ? new ArrayList<>(waiting.subList(0, 3)) : waiting;
            attentionAdapter.submitList(top);
            binding.tvAllClear.setVisibility(top.isEmpty() ? View.VISIBLE : View.GONE);
            binding.rvAttention.setVisibility(top.isEmpty() ? View.GONE : View.VISIBLE);
        }));

        // 2) notices count
        regs.add(db.collection("notices").addSnapshotListener((snap, e) -> {
            if (e != null || snap == null) return;
            binding.cardNotices.setValue(snap.size());
        }));

        // 3) upcoming events count
        regs.add(db.collection("events").addSnapshotListener((snap, e) -> {
            if (e != null || snap == null) return;
            int upcoming = 0;
            for (DocumentSnapshot d : snap.getDocuments()) {
                if (!EventModel.from(d).isPast()) upcoming++;
            }
            binding.cardEvents.setValue(upcoming);
        }));

        // 4) students count: aggregation query, document download হয় না
        db.collection("users").whereEqualTo("role", "user").count().get(AggregateSource.SERVER)
                .addOnSuccessListener(r -> binding.cardUsers.setValue((int) r.getCount()))
                .addOnFailureListener(e -> binding.cardUsers.setValueText("–"));
    }

    @Override
    protected void onStop() {
        super.onStop();
        for (ListenerRegistration r : regs) r.remove();
        regs.clear();
    }

    // ---------- menu ----------
    private void showMenu() {
        PopupMenu menu = new PopupMenu(this, binding.tvAvatar);
        menu.getMenu().add(0, 1, 0, "Log out");
        menu.setOnMenuItemClickListener(item -> {
            FirebaseAuth.getInstance().signOut();
            goLogin();
            return true;
        });
        menu.show();
    }

    private void goLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }

    // ---------- intro animation ----------
    private void playIntro() {
        fadeUp(binding.headerRow, 0);
        fadeUp(binding.rowStats1, 150);
        fadeUp(binding.rowStats2, 280);
        fadeUp(binding.rowStats3, 410);
    }

    private void fadeUp(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(50f);
        v.animate().alpha(1f).translationY(0f)
                .setStartDelay(delay).setDuration(500)
                .setInterpolator(new DecelerateInterpolator()).start();
    }
}