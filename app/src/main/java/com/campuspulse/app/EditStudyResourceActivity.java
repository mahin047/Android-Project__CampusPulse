package com.campuspulse.app;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.campuspulse.app.databinding.ActivityEditResourceBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class EditStudyResourceActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "study_id";

    private ActivityEditResourceBinding b;
    private FirebaseFirestore db;
    private String editId;          // null hole notun material
    private boolean busy = false;
    private final List<String> deptLabels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityEditResourceBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }
        db = FirebaseFirestore.getInstance();
        editId = getIntent().getStringExtra(EXTRA_ID);

        b.btnBack.setOnClickListener(v -> finish());
        b.btnSave.setOnClickListener(v -> save());
        b.btnDelete.setOnClickListener(v -> confirmDelete());

        deptLabels.addAll(Arrays.asList(Departments.labels()));
        b.actDept.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, deptLabels));

        if (editId != null) {
            b.tvHeader.setText("Edit resource");
            b.btnDelete.setVisibility(View.VISIBLE);
            loadExisting();
        }
        loadTypes();

        db.collection("users").document(u.getUid()).get().addOnSuccessListener(doc -> {
            String role = doc.getString("role");
            if (role == null || !role.trim().equalsIgnoreCase("admin")) {
                Toast.makeText(this, "Admins only.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void loadTypes() {
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Collections.addAll(set, StudyResource.TYPES);
        setTypeAdapter(set);
        db.collection("study_resources").get().addOnSuccessListener(snap -> {
            for (DocumentSnapshot d : snap.getDocuments()) {
                String t = d.getString("type");
                if (t != null && !t.trim().isEmpty()) set.add(t.trim());
            }
            if (!isDestroyed()) setTypeAdapter(set);
        });
    }

    private void setTypeAdapter(TreeSet<String> set) {
        b.actType.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, new ArrayList<>(set)));
    }

    private void loadExisting() {
        db.collection("study_resources").document(editId).get()
                .addOnSuccessListener(doc -> {
                    if (isDestroyed()) return;
                    if (!doc.exists()) {
                        Toast.makeText(this, "This resource was removed.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    StudyResource r = StudyResource.from(doc);
                    int idx = Arrays.asList(Departments.CODES).indexOf(r.dept);
                    if (idx >= 0) b.actDept.setText(deptLabels.get(idx), false);
                    b.etCode.setText(r.courseCode);
                    b.etCourse.setText(r.courseTitle);
                    b.actType.setText(r.type, false);
                    b.etTitle.setText(r.title);
                    b.etUrl.setText(r.url);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Could not load this resource.", Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void save() {
        if (busy) return;
        String deptLabel = text(b.actDept);
        String code = text(b.etCode).toUpperCase(Locale.ROOT);
        String course = text(b.etCourse);
        String type = text(b.actType);
        String title = text(b.etTitle);
        String url = text(b.etUrl);

        b.tilDept.setError(null);
        b.tilCode.setError(null);
        b.tilCourse.setError(null);
        b.tilType.setError(null);
        b.tilTitle.setError(null);
        b.tilUrl.setError(null);

        int deptIdx = deptLabels.indexOf(deptLabel);
        if (deptIdx < 0) { b.tilDept.setError("Pick a department"); return; }
        if (code.length() < 3) { b.tilCode.setError("Enter the course code"); return; }
        if (course.length() < 2) { b.tilCourse.setError("Enter the course title"); return; }
        if (type.isEmpty()) { b.tilType.setError("Pick or type a type"); return; }
        if (title.length() < 2) { b.tilTitle.setError("Enter a title"); return; }
        if (url.isEmpty()) { b.tilUrl.setError("Paste the resource link"); return; }
        if (!url.toLowerCase(Locale.ROOT).startsWith("http")) url = "https://" + url;
        if (!Patterns.WEB_URL.matcher(url).matches()) {
            b.tilUrl.setError("Enter a valid link");
            return;
        }

        if (!NetUtil.isOnline(this)) {
            Toast.makeText(this, "Saving needs an internet connection.", Toast.LENGTH_LONG).show();
            return;
        }

        StudyResource r = new StudyResource();
        r.dept = Departments.CODES[deptIdx];
        r.courseCode = code;
        r.courseTitle = course;
        r.type = type;
        r.title = title;
        r.url = url;

        setBusy(true);
        String docId = editId != null ? editId : db.collection("study_resources").document().getId();
        db.collection("study_resources").document(docId).set(r.toMap())
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, editId != null ? "Resource updated." : "Resource added.",
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    setBusy(false);
                    Toast.makeText(this, "Could not save. Check your internet and admin access.",
                            Toast.LENGTH_LONG).show();
                });
    }

    private void confirmDelete() {
        if (editId == null || busy) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this resource?")
                .setMessage("It will disappear from every student's Study Hub. This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    setBusy(true);
                    db.collection("study_resources").document(editId).delete()
                            .addOnSuccessListener(v -> {
                                Toast.makeText(this, "Resource deleted.", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                setBusy(false);
                                Toast.makeText(this, "Could not delete. Check your internet.",
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .show();
    }

    private void setBusy(boolean value) {
        busy = value;
        b.btnSave.setEnabled(!value);
        b.btnDelete.setEnabled(!value);
        b.btnSave.setText(value ? "Please wait..." : "Save");
    }

    private String text(android.widget.TextView tv) {
        return String.valueOf(tv.getText()).trim();
    }
}