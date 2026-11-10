package com.campuspulse.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class StudyHubActivity extends AppCompatActivity implements ResourceAdapter.Listener {

    private static final String PREFS = "app_prefs";
    private static final String KEY_DEPT = "my_dept";
    private static final String ALL = "All";

    private ActivityDirectoryBinding b;
    private ResourceAdapter adapter;
    private SharedPreferences prefs;

    private final List<StudyResource> all = new ArrayList<>();
    private final List<StudyResource.Dept> depts = new ArrayList<>();
    private final List<TextView> deptChips = new ArrayList<>();
    private final List<TextView> typeChips = new ArrayList<>();

    private String deptFilter = ALL;   // "All" ba department code
    private String typeFilter = ALL;
    private String query = "";
    private String myDept = null;
    private ListenerRegistration reg;
    private boolean firstData = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDirectoryBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        myDept = prefs.getString(KEY_DEPT, null);

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
    }

    // ---------- live listen: admin panel theke change hole sathe sathe update ----------
    @Override
    protected void onStart() {
        super.onStart();
        if (firstData) b.loading.setVisibility(View.VISIBLE);
        reg = StudyRepository.get(this).listen(data -> {
            if (isDestroyed()) return;
            b.loading.setVisibility(View.GONE);
            all.clear();
            all.addAll(data.resources);

            if (firstData) {
                depts.clear();
                depts.addAll(data.departments);
                buildDeptChips();
            }
            buildTypeChips();

            if (firstData) {
                firstData = false;
                if (myDept == null) {
                    askMyDepartment();               // prothom-bar
                    return;
                }
                deptFilter = myDept;
            }
            styleChips();
            applyFilter();
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (reg != null) { reg.remove(); reg = null; }
    }

    // ---------- prothom-bar department bechhe neoa ----------
    private void askMyDepartment() {
        if (depts.isEmpty()) {
            applyFilter();
            return;
        }
        String[] names = new String[depts.size()];
        for (int i = 0; i < depts.size(); i++) {
            names[i] = depts.get(i).code + "  •  " + depts.get(i).name;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select your department")
                .setItems(names, (d, which) -> {
                    myDept = depts.get(which).code;
                    prefs.edit().putString(KEY_DEPT, myDept).apply();
                    deptFilter = myDept;
                    styleChips();
                    applyFilter();
                })
                .setNegativeButton("Skip", (d, w) -> applyFilter())
                .show();
    }

    // ---------- department chips (16 ta) ----------
    private void buildDeptChips() {
        b.deptScroll.setVisibility(View.VISIBLE);
        List<String> labels = new ArrayList<>();
        labels.add(ALL);
        for (StudyResource.Dept d : depts) labels.add(d.code);

        for (String code : labels) {
            TextView chip = makeChip(code.equals(myDept) ? "★ " + code : code, code);
            chip.setOnClickListener(v -> {
                deptFilter = code;
                styleChips();
                applyFilter();
            });
            deptChips.add(chip);
            b.deptRow.addView(chip);
        }
        // "Change my department"
        TextView change = makeChip("⚙ Change mine", "__change");
        change.setOnClickListener(v -> askMyDepartment());
        b.deptRow.addView(change);
    }

    private void buildTypeChips() {
        b.chipRow.removeAllViews();
        typeChips.clear();
        Set<String> types = new LinkedHashSet<>();
        types.add(ALL);
        for (StudyResource r : all) if (!r.type.isEmpty()) types.add(r.type);
        if (!types.contains(typeFilter)) typeFilter = ALL;
        for (String name : types) {
            TextView chip = makeChip(name, name);
            chip.setOnClickListener(v -> {
                typeFilter = name;
                styleChips();
                applyFilter();
            });
            typeChips.add(chip);
            b.chipRow.addView(chip);
        }
        styleChips();
    }

    private TextView makeChip(String label, String tag) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTag(tag);
        chip.setTextSize(13);
        chip.setTypeface(ResourcesCompat.getFont(this, R.font.poppins_bold));
        chip.setPadding(dp(16), dp(8), dp(16), dp(8));
        chip.setMinimumHeight(dp(48));
        chip.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        chip.setLayoutParams(lp);
        return chip;
    }

    private void styleChips() {
        for (TextView c : deptChips) paint(c, deptFilter.equals(c.getTag()));
        for (TextView c : typeChips) paint(c, typeFilter.equals(c.getTag()));
    }

    private void paint(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        chip.setTextColor(selected ? Color.parseColor("#06201E") : Color.parseColor("#C3D2EA"));
    }

    // ---------- filter ----------
    private void applyFilter() {
        List<StudyResource> shown = new ArrayList<>();
        for (StudyResource r : all) {
            if (!deptFilter.equals(ALL) && !deptFilter.equals(r.dept)) continue;
            if (!typeFilter.equals(ALL) && !typeFilter.equals(r.type)) continue;
            if (!query.isEmpty() && !matches(r, query)) continue;
            shown.add(r);
        }
        adapter.submitList(shown);
        b.tvCount.setText(shown.size() + (shown.size() == 1 ? " resource" : " resources"));

        if (shown.isEmpty()) {
            if (all.isEmpty()) {
                b.tvEmptyTitle.setText("No materials yet");
                b.tvEmptySub.setText("Study materials will appear here once they are added.");
            } else if (!query.isEmpty()) {
                b.tvEmptyTitle.setText("No results");
                b.tvEmptySub.setText("Nothing matches \"" + query + "\".");
            } else {
                b.tvEmptyTitle.setText("No resources yet");
                b.tvEmptySub.setText(deptFilter.equals(ALL)
                        ? "Try another category."
                        : deptFilter + " materials haven't been added yet. Check All departments.");
            }
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
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.url)));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "No browser found.", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}