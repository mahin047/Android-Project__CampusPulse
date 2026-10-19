package com.campuspulse.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.campuspulse.app.databinding.ActivityIncidentsBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class IncidentsActivity extends AppCompatActivity implements IncidentAdapter.Listener {

    private static final String[] FILTERS = {"All", "Pending", "Investigating", "Resolved"};

    private ActivityIncidentsBinding binding;
    private FirebaseFirestore db;
    private IncidentAdapter adapter;
    private ListenerRegistration reg;

    private final List<IncidentModel> all = new ArrayList<>();
    private final List<TextView> chips = new ArrayList<>();
    private String filter = "All";
    private String uid;
    private boolean roleKnown = false, isAdmin = false, started = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityIncidentsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }
        uid = u.getUid();

        adapter = new IncidentAdapter(this);
        binding.rvIncidents.setLayoutManager(new LinearLayoutManager(this));
        binding.rvIncidents.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, ReportIncidentActivity.class)));

        buildChips();
        loadRole();
    }

    // The query depends on the role, so we learn the role first.
    private void loadRole() {
        binding.loading.setVisibility(View.VISIBLE);
        db.collection("users").document(uid).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                String role = task.getResult().getString("role");
                isAdmin = role != null && role.trim().equalsIgnoreCase("admin");
            }
            roleKnown = true;
            binding.tvTitle.setText(isAdmin ? "Incident queue" : "My reports");
            binding.fabAdd.setVisibility(isAdmin ? View.GONE : View.VISIBLE);
            adapter.setShowReporter(isAdmin);
            maybeListen();
        });
    }

    // ---------- lifecycle-aware listener ----------
    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        maybeListen();
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        if (reg != null) { reg.remove(); reg = null; }
    }

    private void maybeListen() {
        if (!roleKnown || !started || reg != null) return;
        if (all.isEmpty()) binding.loading.setVisibility(View.VISIBLE);

        // Students: only their own reports. Admin: everything.
        // No orderBy, so no composite index is needed; we sort in Java below.
        Query q = isAdmin
                ? db.collection("incidents")
                : db.collection("incidents").whereEqualTo("reporterUid", uid);

        reg = q.addSnapshotListener((snap, e) -> {
            binding.loading.setVisibility(View.GONE);
            if (e != null || snap == null) {
                Toast.makeText(this, "Could not load reports.", Toast.LENGTH_LONG).show();
                showEmpty("Couldn't load reports", "Check your internet connection.");
                return;
            }
            all.clear();
            for (DocumentSnapshot d : snap.getDocuments()) all.add(IncidentModel.from(d));
            Collections.sort(all, (a, b) -> {
                Date x = a.getCreatedAt(), y = b.getCreatedAt();
                if (x == null && y == null) return 0;
                if (x == null) return -1;   // a just-created (not yet synced) report goes first
                if (y == null) return 1;
                return y.compareTo(x);      // newest first
            });
            applyFilter();
        });
    }

    // ---------- chips ----------
    private void buildChips() {
        for (String name : FILTERS) {
            TextView chip = new TextView(this);
            chip.setText(name);
            chip.setTextSize(13);
            chip.setTypeface(ResourcesCompat.getFont(this, R.font.poppins_bold));
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
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
            boolean selected = chip.getText().toString().equals(filter);
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chip.setTextColor(selected ? Color.parseColor("#06201E") : Color.parseColor("#C3D2EA"));
        }
    }

    private void applyFilter() {
        List<IncidentModel> shown = new ArrayList<>();
        for (IncidentModel m : all) {
            if (filter.equals("All") || filter.equalsIgnoreCase(m.getStatus())) shown.add(m);
        }
        adapter.submitList(shown);
        binding.tvCount.setText(shown.size() + (shown.size() == 1 ? " report" : " reports"));

        if (shown.isEmpty()) {
            if (all.isEmpty()) {
                if (isAdmin) showEmpty("Queue is clear", "New incident reports from students will appear here.");
                else showEmpty("No reports yet", "Spotted a problem on campus? Tap Report issue.");
            } else {
                showEmpty("Nothing in " + filter, "Try another status.");
            }
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.rvIncidents.setVisibility(View.VISIBLE);
        }
    }

    private void showEmpty(String title, String sub) {
        binding.tvEmptyTitle.setText(title);
        binding.tvEmptySub.setText(sub);
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.rvIncidents.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onIncidentClick(IncidentModel m) {
        Intent i = new Intent(this, IncidentDetailActivity.class); // explicit intent
        i.putExtra(IncidentDetailActivity.EXTRA_ID, m.getId());    // data to next Activity
        startActivity(i);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}