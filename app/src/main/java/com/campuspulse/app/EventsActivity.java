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
import androidx.recyclerview.widget.GridLayoutManager;

import com.campuspulse.app.databinding.ActivityEventsBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EventsActivity extends AppCompatActivity implements EventAdapter.Listener {

    private static final String[] FILTERS = {"Upcoming", "My events", "Past"};

    private ActivityEventsBinding binding;
    private FirebaseFirestore db;
    private EventAdapter adapter;
    private ListenerRegistration eventsReg, myReg;

    private final List<EventModel> all = new ArrayList<>();
    private final Set<String> myIds = new HashSet<>();
    private final List<TextView> chips = new ArrayList<>();
    private String filter = "Upcoming";
    private boolean isAdmin = false;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }
        uid = u.getUid();

        adapter = new EventAdapter(this);
        binding.rvEvents.setLayoutManager(new GridLayoutManager(this, 2)); // 2-column grid
        binding.rvEvents.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddEventActivity.class)));

        buildChips();
        checkAdmin();
    }

    // ---------- listeners follow the lifecycle ----------
    @Override
    protected void onStart() {
        super.onStart();
        if (all.isEmpty()) binding.loading.setVisibility(View.VISIBLE);

        eventsReg = db.collection("events")
                .orderBy("dateTime", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    binding.loading.setVisibility(View.GONE);
                    if (e != null || snap == null) {
                        Toast.makeText(this, "Could not load events.", Toast.LENGTH_LONG).show();
                        showEmpty("Couldn't load events", "Check your internet connection.");
                        return;
                    }
                    all.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) all.add(EventModel.from(d));
                    applyFilter();
                });

        myReg = db.collection("users").document(uid).collection("myEvents")
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;
                    myIds.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) myIds.add(d.getId());
                    adapter.setRegistered(myIds);
                    applyFilter();
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (eventsReg != null) { eventsReg.remove(); eventsReg = null; }
        if (myReg != null) { myReg.remove(); myReg = null; }
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
        List<EventModel> shown = new ArrayList<>();
        for (EventModel e : all) {
            switch (filter) {
                case "Upcoming": if (!e.isPast()) shown.add(e); break;
                case "My events": if (myIds.contains(e.getId())) shown.add(e); break;
                default: if (e.isPast()) shown.add(e); break;
            }
        }
        if (filter.equals("Past")) Collections.reverse(shown); // newest first

        adapter.submitList(shown);
        binding.tvCount.setText(shown.size() + (shown.size() == 1 ? " event" : " events"));

        if (shown.isEmpty()) {
            switch (filter) {
                case "Upcoming":
                    showEmpty("No upcoming events", "New events from the administration will appear here.");
                    break;
                case "My events":
                    showEmpty("You haven't registered yet", "Open an event and tap Register now.");
                    break;
                default:
                    showEmpty("No past events", "Finished events will be listed here.");
            }
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.rvEvents.setVisibility(View.VISIBLE);
        }
    }

    private void showEmpty(String title, String sub) {
        binding.tvEmptyTitle.setText(title);
        binding.tvEmptySub.setText(sub);
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.rvEvents.setVisibility(View.INVISIBLE);
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

    // ---------- adapter callbacks ----------
    @Override
    public void onEventClick(EventModel e) {
        Intent i = new Intent(this, EventDetailActivity.class); // explicit intent
        i.putExtra(EventDetailActivity.EXTRA_EVENT_ID, e.getId()); // data to next Activity
        startActivity(i);
    }

    @Override
    public void onEventLongClick(EventModel e) {
        if (!isAdmin) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete event?")
                .setMessage("\"" + e.getTitle() + "\" will be removed for everyone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) ->
                        db.collection("events").document(e.getId()).delete()
                                .addOnFailureListener(err -> Toast.makeText(this,
                                        "Could not delete.", Toast.LENGTH_LONG).show()))
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}