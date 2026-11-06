package com.campuspulse.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
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
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.GridLayoutManager;

import com.campuspulse.app.databinding.ActivityDirectoryBinding;
import com.campuspulse.app.databinding.SheetPlaceBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ServicesActivity extends AppCompatActivity implements PlaceAdapter.Listener {

    private ActivityDirectoryBinding b;
    private PlaceAdapter adapter;
    private final List<CampusPlace> all = new ArrayList<>();
    private final List<TextView> chips = new ArrayList<>();
    private String filter = "All";
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDirectoryBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.tvTitle.setText("Campus services");
        b.etSearch.setHint("Search places or services");
        b.ivEmpty.setImageResource(R.drawable.ic_services);
        b.btnBack.setOnClickListener(v -> finish());

        adapter = new PlaceAdapter(this);
        b.rvItems.setLayoutManager(new GridLayoutManager(this, 2));
        b.rvItems.setAdapter(adapter);

        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });

        // XML parsing background এ, তাই spinner
        b.loading.setVisibility(View.VISIBLE);
        PlaceRepository.get(this).load(list -> {
            if (isDestroyed()) return;
            b.loading.setVisibility(View.GONE);
            all.addAll(list);
            buildChips();
            applyFilter();
        });
    }

    // ---------- chips: category গুলো XML data থেকেই আসে ----------
    private void buildChips() {
        Set<String> cats = new LinkedHashSet<>();
        cats.add("All");
        for (CampusPlace p : all) cats.add(p.category);
        for (String name : cats) {
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
        List<CampusPlace> shown = new ArrayList<>();
        for (CampusPlace p : all) {
            if (!filter.equals("All") && !filter.equals(p.category)) continue;
            if (!query.isEmpty() && !matches(p, query)) continue;
            shown.add(p);
        }
        adapter.submitList(shown);
        b.tvCount.setText(shown.size() + (shown.size() == 1 ? " place" : " places"));

        if (shown.isEmpty()) {
            b.tvEmptyTitle.setText(all.isEmpty() ? "No data" : "No results");
            b.tvEmptySub.setText(all.isEmpty()
                    ? "Could not read campus_map.xml."
                    : "Nothing matches your search.");
            b.emptyState.setVisibility(View.VISIBLE);
            b.rvItems.setVisibility(View.INVISIBLE);
        } else {
            b.emptyState.setVisibility(View.GONE);
            b.rvItems.setVisibility(View.VISIBLE);
        }
    }

    private boolean matches(CampusPlace p, String q) {
        return p.name.toLowerCase(Locale.ROOT).contains(q)
                || p.where.toLowerCase(Locale.ROOT).contains(q)
                || p.keywords.toLowerCase(Locale.ROOT).contains(q)
                || p.category.toLowerCase(Locale.ROOT).contains(q);
    }

    // ---------- details sheet + implicit intents ----------
    @Override
    public void onPlaceClick(CampusPlace p) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        SheetPlaceBinding s = SheetPlaceBinding.inflate(getLayoutInflater());

        int color = CampusPlace.categoryColor(p.category);
        s.tvBadge.setText(p.category.toUpperCase());
        s.tvBadge.setTextColor(color);
        s.tvBadge.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x33)));
        s.tvName.setText(p.name);

        show(s.tvWhere, p.where.isEmpty() ? "" : "📍 " + p.where);
        show(s.tvHours, p.hours.isEmpty() ? "" : "🕒 " + p.hours);
        show(s.tvPhone, p.hasPhone() ? "📞 " + p.phone : "");
        show(s.tvNote, p.note);

        s.btnCall.setVisibility(p.hasPhone() ? View.VISIBLE : View.GONE);
        s.btnMap.setVisibility(p.hasLocation() ? View.VISIBLE : View.GONE);
        s.btnWeb.setVisibility(p.hasWebsite() ? View.VISIBLE : View.GONE);

        // implicit intents
        s.btnCall.setOnClickListener(v -> launch(new Intent(Intent.ACTION_DIAL,
                Uri.parse("tel:" + Uri.encode(p.phone))), "No dialer app found."));
        s.btnMap.setOnClickListener(v -> launch(new Intent(Intent.ACTION_VIEW,
                Uri.parse("geo:" + p.lat + "," + p.lng + "?q=" + p.lat + "," + p.lng
                        + "(" + Uri.encode(p.name) + ")")), "No maps app found."));
        s.btnWeb.setOnClickListener(v -> launch(new Intent(Intent.ACTION_VIEW,
                Uri.parse(p.website)), "No browser found."));
        s.btnShare.setOnClickListener(v -> {
            String text = "📍 " + p.name + "\n" + p.where
                    + (p.hasLocation() ? "\nhttps://maps.google.com/?q=" + p.lat + "," + p.lng : "")
                    + "\n\nShared from CampusPulse";
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(send, "Share place"));
        });

        dialog.setContentView(s.getRoot());
        dialog.show();
    }

    private void show(TextView tv, String text) {
        tv.setText(text);
        tv.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void launch(Intent i, String errorMessage) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}