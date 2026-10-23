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

import com.campuspulse.app.databinding.ActivityNoticesBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class NoticesActivity extends AppCompatActivity implements NoticeAdapter.Listener {

    private static final String[] FILTERS = {"All", "Academic", "Exam", "Event", "General"};

    private ActivityNoticesBinding binding;
    private FirebaseFirestore db;
    private NoticeAdapter adapter;
    private ListenerRegistration registration;

    private final List<NoticeModel> all = new ArrayList<>();
    private final List<TextView> chips = new ArrayList<>();
    private String filter = "All";
    private boolean isAdmin = false;

    private ConnectivityBanner banner;   // field

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNoticesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        banner = new ConnectivityBanner(this, binding.tvOffline);

        db = FirebaseFirestore.getInstance();

        adapter = new NoticeAdapter(this);
        binding.rvNotices.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotices.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddNoticeActivity.class)));

        buildChips();
        checkAdmin();
    }

    // ---------- lifecycle-aware real-time listener ----------
    @Override
    protected void onStart() {
        super.onStart();
        startListening();
        banner.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        banner.stop();
    }

    private void startListening() {
        if (all.isEmpty()) binding.loading.setVisibility(View.VISIBLE);

        registration = db.collection("notices")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    binding.loading.setVisibility(View.GONE);
                    if (e != null || snap == null) {
                        Toast.makeText(this, "Could not load notices.", Toast.LENGTH_LONG).show();
                        showEmpty("Couldn't load notices", "Check your internet connection.");
                        return;
                    }
                    all.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        NoticeModel n = new NoticeModel();
                        n.setId(d.getId());
                        n.setTitle(d.getString("title"));
                        n.setBody(d.getString("body"));
                        n.setCategory(d.getString("category"));
                        n.setPriority(d.getString("priority"));
                        n.setAuthorName(d.getString("authorName"));
                        n.setCreatedAt(d.getDate("createdAt",
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE));
                        all.add(n);
                    }
                    applyFilter();
                });
    }

    // ---------- filter chips ----------
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
        List<NoticeModel> shown = new ArrayList<>();
        for (NoticeModel n : all) {
            if (filter.equals("All") || filter.equalsIgnoreCase(n.getCategory())) shown.add(n);
        }
        adapter.submitList(shown);
        binding.tvCount.setText(shown.size() + (shown.size() == 1 ? " notice" : " notices"));

        if (shown.isEmpty()) {
            if (all.isEmpty()) showEmpty("No notices yet", "New notices from the administration will appear here.");
            else showEmpty("Nothing in " + filter, "Try another category.");
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.rvNotices.setVisibility(View.VISIBLE);
        }
    }

    private void showEmpty(String title, String sub) {
        binding.tvEmptyTitle.setText(title);
        binding.tvEmptySub.setText(sub);
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.rvNotices.setVisibility(View.INVISIBLE);
    }

    // ---------- admin ----------
    private void checkAdmin() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;
        db.collection("users").document(u.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    isAdmin = role != null && role.trim().equalsIgnoreCase("admin");
                    binding.fabAdd.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
                });
    }

    // ---------- adapter callbacks ----------
    @Override
    public void onNoticeClick(NoticeModel n) {
        Intent i = new Intent(this, NoticeDetailActivity.class); // explicit intent
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
        if (!isAdmin) return; // শুধু admin মুছতে পারবে
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