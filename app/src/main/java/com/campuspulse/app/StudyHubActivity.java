package com.campuspulse.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
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

import com.campuspulse.app.databinding.ActivityDirectoryBinding;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class StudyHubActivity extends AppCompatActivity implements ResourceAdapter.Listener {

    private ActivityDirectoryBinding b;
    private ResourceAdapter adapter;
    private final List<StudyResource> all = new ArrayList<>();
    private final List<TextView> chips = new ArrayList<>();
    private String filter = "All";
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDirectoryBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.tvTitle.setText("Study Hub");
        b.etSearch.setHint("Search course, topic or code");
        b.ivEmpty.setImageResource(R.drawable.ic_study);
        b.btnBack.setOnClickListener(v -> finish());

        adapter = new ResourceAdapter(this);
        b.rvItems.setLayoutManager(new LinearLayoutManager(this));
        b.rvItems.setAdapter(adapter);

        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });

        b.loading.setVisibility(View.VISIBLE);
        StudyRepository.get(this).load(list -> {
            if (isDestroyed()) return;
            b.loading.setVisibility(View.GONE);
            all.addAll(list);
            buildChips();
            applyFilter();
        });
    }

    private void buildChips() {
        Set<String> types = new LinkedHashSet<>();
        types.add("All");
        for (StudyResource r : all) types.add(r.type);
        for (String name : types) {
            TextView chip = new TextView(this);
            chip.setText(name);
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
            b.chipRow.addView(chip);
        }
        styleChips();
    }

    private void styleChips() {
        for (TextView chip : chips) {
            boolean sel = chip.getText().toString().equals(filter);
            chip.setBackgroundResource(sel ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chip.setTextColor(sel ? Color.parseColor("#06201E") : Color.parseColor("#C3D2EA"));
        }
    }

    private void applyFilter() {
        List<StudyResource> shown = new ArrayList<>();
        for (StudyResource r : all) {
            if (!filter.equals("All") && !filter.equals(r.type)) continue;
            if (!query.isEmpty() && !matches(r, query)) continue;
            shown.add(r);
        }
        adapter.submitList(shown);
        b.tvCount.setText(shown.size() + (shown.size() == 1 ? " resource" : " resources"));

        if (shown.isEmpty()) {
            b.tvEmptyTitle.setText(all.isEmpty() ? "No data" : "No results");
            b.tvEmptySub.setText(all.isEmpty()
                    ? "Could not read study_hub.json."
                    : "Try another keyword or category.");
            b.emptyState.setVisibility(View.VISIBLE);
            b.rvItems.setVisibility(View.INVISIBLE);
        } else {
            b.emptyState.setVisibility(View.GONE);
            b.rvItems.setVisibility(View.VISIBLE);
        }
    }

    private boolean matches(StudyResource r, String q) {
        return r.title.toLowerCase(Locale.ROOT).contains(q)
                || r.courseTitle.toLowerCase(Locale.ROOT).contains(q)
                || r.courseCode.toLowerCase(Locale.ROOT).contains(q);
    }

    @Override
    public void onResourceClick(StudyResource r) {
        if (r.url.isEmpty()) {
            Toast.makeText(this, "No link for this resource yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.url))); // implicit intent
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "No browser found.", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}