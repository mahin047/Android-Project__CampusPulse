package com.campuspulse.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.campuspulse.app.databinding.ActivityDirectoryBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ManageServicesActivity extends AppCompatActivity implements PlaceAdapter.Listener {

    private ActivityDirectoryBinding b;
    private PlaceAdapter adapter;
    private ListenerRegistration reg;
    private final List<CampusPlace> all = new ArrayList<>();
    private String query = "";
    private boolean loaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDirectoryBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }

        b.tvTitle.setText("Manage services");
        b.etSearch.setHint("Search places");
        b.ivEmpty.setImageResource(R.drawable.ic_services);
        b.btnBack.setOnClickListener(v -> finish());

        adapter = new PlaceAdapter(this);
        b.rvItems.setLayoutManager(new GridLayoutManager(this,
                getResources().getInteger(R.integer.grid_columns)));
        b.rvItems.setAdapter(adapter);

        b.fabAdd.setText("Add place");
        b.fabAdd.setOnClickListener(v -> startActivity(new Intent(this, EditServiceActivity.class)));
        b.btnEmptyAction.setText("Import sample data");
        b.btnEmptyAction.setOnClickListener(v -> confirmSeed());

        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });

        // শুধু admin এই screen এ থাকতে পারবে
        FirebaseFirestore.getInstance().collection("users").document(u.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    if (role == null || !role.trim().equalsIgnoreCase("admin")) {
                        Toast.makeText(this, "Admins only.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    b.fabAdd.setVisibility(View.VISIBLE);
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!loaded) b.loading.setVisibility(View.VISIBLE);
        reg = PlaceRepository.get(this).listenRaw(list -> {
            if (isDestroyed()) return;
            b.loading.setVisibility(View.GONE);
            if (list == null) {
                showEmpty("Couldn't load places", "Check your internet connection.", false);
                return;
            }
            loaded = true;
            all.clear();
            all.addAll(list);
            applyFilter();
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (reg != null) { reg.remove(); reg = null; }
    }

    private void applyFilter() {
        List<CampusPlace> shown = new ArrayList<>();
        for (CampusPlace p : all) {
            if (!query.isEmpty() && !(p.name.toLowerCase(Locale.ROOT).contains(query)
                    || p.category.toLowerCase(Locale.ROOT).contains(query)
                    || p.where.toLowerCase(Locale.ROOT).contains(query))) continue;
            shown.add(p);
        }
        adapter.submitList(shown);
        b.tvCount.setText(shown.size() + (shown.size() == 1 ? " place" : " places"));

        if (shown.isEmpty()) {
            if (all.isEmpty()) {
                showEmpty("No places yet",
                        "Import the sample campus places, or tap Add place to create your own.", true);
            } else {
                showEmpty("No results", "Nothing matches your search.", false);
            }
        } else {
            b.emptyState.setVisibility(View.GONE);
            b.rvItems.setVisibility(View.VISIBLE);
        }
    }

    private void showEmpty(String title, String sub, boolean showSeed) {
        b.tvEmptyTitle.setText(title);
        b.tvEmptySub.setText(sub);
        b.btnEmptyAction.setVisibility(showSeed ? View.VISIBLE : View.GONE);
        b.emptyState.setVisibility(View.VISIBLE);
        b.rvItems.setVisibility(View.INVISIBLE);
    }

    private void confirmSeed() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Import sample data?")
                .setMessage("The 11 sample places (library, medical center, cafeteria and more) will be "
                        + "added. You can edit or delete them afterwards.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Import", (d, w) -> {
                    b.btnEmptyAction.setEnabled(false);
                    PlaceRepository.get(this).seedFromXml(n -> {
                        if (isDestroyed()) return;
                        b.btnEmptyAction.setEnabled(true);
                        Toast.makeText(this, n < 0 ? "Import failed. Check your internet and admin access."
                                : n + " places imported.", Toast.LENGTH_LONG).show();
                    });
                })
                .show();
    }

    @Override
    public void onPlaceClick(CampusPlace p) {
        Intent i = new Intent(this, EditServiceActivity.class);
        i.putExtra(EditServiceActivity.EXTRA_ID, p.id);
        startActivity(i);
    }
}