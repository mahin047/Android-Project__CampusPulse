package com.campuspulse.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.campuspulse.app.databinding.ActivitySosAlertsBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SosAlertsActivity extends AppCompatActivity implements SosAlertAdapter.Listener {

    private static final String[] FILTERS = {"Active", "History"};

    private ActivitySosAlertsBinding b;
    private DatabaseReference ref;
    private ValueEventListener listener;
    private SosAlertAdapter adapter;
    private final List<SosAlertModel> all = new ArrayList<>();
    private final List<TextView> chips = new ArrayList<>();
    private String filter = "Active";
    private String myName = "Administration";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySosAlertsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }

        adapter = new SosAlertAdapter(this);
        b.rvAlerts.setLayoutManager(new LinearLayoutManager(this));
        b.rvAlerts.setAdapter(adapter);
        b.btnBack.setOnClickListener(v -> finish());
        buildChips();

        // admin guard + নাম
        FirebaseFirestore.getInstance().collection("users").document(u.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    if (role == null || !role.trim().equalsIgnoreCase("admin")) {
                        Toast.makeText(this, "Admins only.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    String n = doc.getString("name");
                    if (n != null && !n.trim().isEmpty()) myName = n.trim();
                });
    }

    // ---------- lifecycle-aware real-time listener ----------
    @Override
    protected void onStart() {
        super.onStart();
        if (all.isEmpty()) b.loading.setVisibility(View.VISIBLE);
        ref = FirebaseDatabase.getInstance().getReference("sos_alerts");
        listener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                b.loading.setVisibility(View.GONE);
                all.clear();
                for (DataSnapshot c : snap.getChildren()) all.add(SosAlertModel.from(c));
                Collections.sort(all, (x, y) -> {
                    long a = x.getCreatedAtRaw() == null ? Long.MAX_VALUE : x.getCreatedAtRaw();
                    long z = y.getCreatedAtRaw() == null ? Long.MAX_VALUE : y.getCreatedAtRaw();
                    return Long.compare(z, a); // newest first
                });
                applyFilter();
            }

            @Override public void onCancelled(@NonNull DatabaseError e) {
                b.loading.setVisibility(View.GONE);
                Log.e("SOS", "feed denied: " + e.getMessage());
                showEmpty("No access to the SOS feed",
                        "Add your UID under /admins in Realtime Database, then reopen this screen.");
            }
        };
        ref.addValueEventListener(listener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (ref != null && listener != null) ref.removeEventListener(listener);
        ref = null;
        listener = null;
    }

    // ---------- chips + filter ----------
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
        boolean wantActive = filter.equals("Active");
        List<SosAlertModel> shown = new ArrayList<>();
        for (SosAlertModel m : all) {
            if (m.isActive() == wantActive) shown.add(m);
        }
        adapter.submitList(shown);
        b.tvCount.setText(shown.size() + (shown.size() == 1 ? " alert" : " alerts"));

        if (shown.isEmpty()) {
            if (wantActive) showEmpty("No active alerts", "Everything is calm. New SOS alerts appear here instantly.");
            else showEmpty("No history yet", "Resolved and cancelled alerts will be listed here.");
        } else {
            b.emptyState.setVisibility(View.GONE);
            b.rvAlerts.setVisibility(View.VISIBLE);
        }
    }

    private void showEmpty(String title, String sub) {
        b.tvEmptyTitle.setText(title);
        b.tvEmptySub.setText(sub);
        b.emptyState.setVisibility(View.VISIBLE);
        b.rvAlerts.setVisibility(View.INVISIBLE);
    }

    // ---------- adapter callbacks ----------
    @Override
    public void onRespond(SosAlertModel m) {
        setStatus(m, SosAlertModel.ACK);
    }

    @Override
    public void onResolve(SosAlertModel m) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Mark as resolved?")
                .setMessage("The student will see that the alert is resolved and live tracking will stop.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Resolve", (d, w) -> setStatus(m, SosAlertModel.RESOLVED))
                .show();
    }

    @Override
    public void onMap(SosAlertModel m) {
        if (!m.hasLocation()) return;
        String label = Uri.encode("SOS: " + m.getName());
        Uri uri = Uri.parse("geo:" + m.getLat() + "," + m.getLng()
                + "?q=" + m.getLat() + "," + m.getLng() + "(" + label + ")");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri)); // implicit intent
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "No maps app found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void setStatus(SosAlertModel m, String status) {
        Map<String, Object> up = new HashMap<>();
        up.put("status", status);
        up.put("handledBy", myName);
        up.put("handledAt", ServerValue.TIMESTAMP);
        FirebaseDatabase.getInstance().getReference("sos_alerts").child(m.getId())
                .updateChildren(up)
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Could not update. Check your internet and admin access.", Toast.LENGTH_LONG).show());
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}