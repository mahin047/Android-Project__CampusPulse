package com.campuspulse.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.campuspulse.app.databinding.ActivityLostFoundBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LostFoundActivity extends AppCompatActivity implements LostFoundAdapter.Listener {

    private static final String[] FILTERS = {"All", "Lost", "Found", "Resolved", "My posts"};

    private ActivityLostFoundBinding binding;
    private FirebaseFirestore db;
    private LostFoundAdapter adapter;
    private ListenerRegistration reg;

    private final List<LostFoundModel> all = new ArrayList<>();
    private final List<TextView> chips = new ArrayList<>();
    private String filter = "All";
    private String query = "";
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLostFoundBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }
        uid = u.getUid();

        adapter = new LostFoundAdapter(this);
        binding.rvItems.setLayoutManager(new LinearLayoutManager(this));
        binding.rvItems.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, PostItemActivity.class)));

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });

        buildChips();
    }

    // ---------- lifecycle-aware listener ----------
    @Override
    protected void onStart() {
        super.onStart();
        if (all.isEmpty()) binding.loading.setVisibility(View.VISIBLE);

        reg = db.collection("lostfound")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    binding.loading.setVisibility(View.GONE);
                    if (e != null || snap == null) {
                        Toast.makeText(this, "Could not load items.", Toast.LENGTH_LONG).show();
                        showEmpty("Couldn't load items", "Check your internet connection.");
                        return;
                    }
                    all.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) all.add(LostFoundModel.from(d));
                    applyFilter();
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (reg != null) { reg.remove(); reg = null; }
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

    // ---------- filter + search ----------
    private void applyFilter() {
        List<LostFoundModel> shown = new ArrayList<>();
        for (LostFoundModel m : all) {
            boolean mine = uid.equals(m.getPosterUid());
            switch (filter) {
                case "My posts": if (!mine) continue; break;
                case "Lost": if (!m.isLost() || m.isResolved()) continue; break;
                case "Found": if (m.isLost() || m.isResolved()) continue; break;
                case "Resolved": if (!m.isResolved()) continue; break;
                default: if (m.isResolved()) continue; // "All" shows open items only
            }
            if (!query.isEmpty() && !matches(m, query)) continue;
            shown.add(m);
        }

        adapter.submitList(shown);
        binding.tvCount.setText(shown.size() + (shown.size() == 1 ? " item" : " items"));

        if (shown.isEmpty()) {
            if (!query.isEmpty()) {
                showEmpty("No results", "Nothing matches \"" + query + "\".");
            } else if (filter.equals("My posts")) {
                showEmpty("You haven't posted anything", "Tap Post item to report a lost or found item.");
            } else if (filter.equals("Resolved")) {
                showEmpty("No resolved posts", "Items that were returned will be listed here.");
            } else if (filter.equals("All")) {
                showEmpty("Nothing here yet", "Be the first to post a lost or found item.");
            } else {
                showEmpty("Nothing here yet", "No open " + filter.toLowerCase(Locale.ROOT) + " items right now.");
            }
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.rvItems.setVisibility(View.VISIBLE);
        }
    }

    private boolean matches(LostFoundModel m, String q) {
        return has(m.getItemName(), q) || has(m.getLocation(), q) || has(m.getCategory(), q);
    }

    private boolean has(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    private void showEmpty(String title, String sub) {
        binding.tvEmptyTitle.setText(title);
        binding.tvEmptySub.setText(sub);
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.rvItems.setVisibility(View.INVISIBLE);
    }

    // ---------- adapter callback ----------
    @Override
    public void onItemClick(LostFoundModel item) {
        Intent i = new Intent(this, LostFoundDetailActivity.class); // explicit intent
        i.putExtra(LostFoundDetailActivity.EXTRA_ID, item.getId()); // data to next Activity
        startActivity(i);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}