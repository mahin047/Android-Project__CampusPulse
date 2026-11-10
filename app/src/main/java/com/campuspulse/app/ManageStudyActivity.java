package com.campuspulse.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.campuspulse.app.databinding.ActivityDirectoryBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ManageStudyActivity extends AppCompatActivity implements ResourceAdapter.Listener {

    private ActivityDirectoryBinding b;
    private ResourceAdapter adapter;
    private ListenerRegistration reg;
    private final List<StudyResource> all = new ArrayList<>();
    private String query = "";
    private boolean loaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDirectoryBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }

        b.tvTitle.setText("Manage Study Hub");
        b.etSearch.setHint("Search course, topic or code");
        b.ivEmpty.setImageResource(R.drawable.ic_study);
        b.btnBack.setOnClickListener(v -> finish());

        adapter = new ResourceAdapter(this);
        b.rvItems.setLayoutManager(new LinearLayoutManager(this));
        b.rvItems.setAdapter(adapter);

        b.fabAdd.setText("Add resource");
        b.fabAdd.setOnClickListener(v -> startActivity(new Intent(this, EditStudyResourceActivity.class)));
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
        reg = StudyRepository.get(this).listenRaw(list -> {
            if (isDestroyed()) return;
            b.loading.setVisibility(View.GONE);
            if (list == null) {
                showEmpty("Couldn't load materials", "Check your internet connection and admin access.", false);
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
        List<StudyResource> shown = new ArrayList<>();
        for (StudyResource r : all) {
            if (!query.isEmpty() && !(r.title.toLowerCase(Locale.ROOT).contains(query)
                    || r.courseTitle.toLowerCase(Locale.ROOT).contains(query)
                    || r.courseCode.toLowerCase(Locale.ROOT).contains(query))) continue;
            shown.add(r);
        }
        adapter.submitList(shown);
        b.tvCount.setText(shown.size() + (shown.size() == 1 ? " resource" : " resources"));

        if (shown.isEmpty()) {
            if (all.isEmpty()) {
                showEmpty("No materials yet",
                        "Import the sample materials, or tap Add resource to create your own.", true);
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
                .setMessage("The bundled Study Hub materials will be added. "
                        + "You can edit or delete them afterwards.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Import", (d, w) -> {
                    b.btnEmptyAction.setEnabled(false);
                    StudyRepository.get(this).seedFromJson(n -> {
                        if (isDestroyed()) return;
                        b.btnEmptyAction.setEnabled(true);
                        Toast.makeText(this, n < 0 ? "Import failed. Check your internet and admin access."
                                : n + " resources imported.", Toast.LENGTH_LONG).show();
                    });
                })
                .show();
    }

    @Override
    public void onResourceClick(StudyResource r) {
        Intent i = new Intent(this, EditStudyResourceActivity.class);
        i.putExtra(EditStudyResourceActivity.EXTRA_ID, r.id);
        startActivity(i);
    }
}