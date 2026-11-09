package com.campuspulse.app;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.campuspulse.app.databinding.ActivityEditServiceBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class EditServiceActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "svc_id";

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{3,15}$");

    private ActivityEditServiceBinding b;
    private FirebaseFirestore db;
    private String editId;          // null হলে নতুন জায়গা
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityEditServiceBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }
        db = FirebaseFirestore.getInstance();
        editId = getIntent().getStringExtra(EXTRA_ID);

        b.btnBack.setOnClickListener(v -> finish());
        b.btnSave.setOnClickListener(v -> save());
        b.btnDelete.setOnClickListener(v -> confirmDelete());

        if (editId != null) {
            b.tvHeader.setText("Edit place");
            b.btnDelete.setVisibility(View.VISIBLE);
            loadExisting();
        }
        loadCategories();

        db.collection("users").document(u.getUid()).get().addOnSuccessListener(doc -> {
            String role = doc.getString("role");
            if (role == null || !role.trim().equalsIgnoreCase("admin")) {
                Toast.makeText(this, "Admins only.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    // default category + এখন পর্যন্ত admin এর বানানো নতুন category সব একসাথে
    private void loadCategories() {
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Collections.addAll(set, CampusPlace.DEFAULT_CATEGORIES);
        setCategoryAdapter(set);
        db.collection("services").get().addOnSuccessListener(snap -> {
            for (DocumentSnapshot d : snap.getDocuments()) {
                String c = d.getString("category");
                if (c != null && !c.trim().isEmpty()) set.add(c.trim());
            }
            if (!isDestroyed()) setCategoryAdapter(set);
        });
    }

    private void setCategoryAdapter(TreeSet<String> set) {
        b.actCategory.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, new ArrayList<>(set)));
    }

    private void loadExisting() {
        db.collection("services").document(editId).get()
                .addOnSuccessListener(doc -> {
                    if (isDestroyed()) return;
                    if (!doc.exists()) {
                        Toast.makeText(this, "This place was removed.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    CampusPlace p = CampusPlace.from(doc);
                    b.etName.setText(p.name);
                    b.actCategory.setText(p.category, false);
                    b.etWhere.setText(p.where);
                    b.etHours.setText(p.hours);
                    b.etPhone.setText(p.phone);
                    b.etWebsite.setText(p.website);
                    if (p.hasLocation()) {
                        b.etLat.setText(String.valueOf(p.lat));
                        b.etLng.setText(String.valueOf(p.lng));
                    }
                    b.etKeywords.setText(p.keywords);
                    b.etNote.setText(p.note);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Could not load this place.", Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    // ---------- validate + save ----------
    private void save() {
        if (busy) return;
        String name = text(b.etName);
        String category = text(b.actCategory);
        String where = text(b.etWhere);
        String hours = text(b.etHours);
        String phone = text(b.etPhone).replaceAll("[\\s-]", "");
        String website = text(b.etWebsite);
        String latS = text(b.etLat), lngS = text(b.etLng);
        String keywords = text(b.etKeywords).toLowerCase(Locale.ROOT);
        String note = text(b.etNote);

        b.tilName.setError(null);
        b.tilCategory.setError(null);
        b.tilWhere.setError(null);
        b.tilPhone.setError(null);
        b.tilWebsite.setError(null);
        b.tilLat.setError(null);
        b.tilLng.setError(null);

        if (name.length() < 2) { b.tilName.setError("Enter the name"); return; }
        if (category.isEmpty()) { b.tilCategory.setError("Pick or type a category"); return; }
        if (where.isEmpty()) { b.tilWhere.setError("Tell students where it is"); return; }
        if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
            b.tilPhone.setError("Digits only, e.g. 01712345678 or 999");
            return;
        }
        if (!website.isEmpty()) {
            if (!website.toLowerCase(Locale.ROOT).startsWith("http")) website = "https://" + website;
            if (!Patterns.WEB_URL.matcher(website).matches()) {
                b.tilWebsite.setError("Enter a valid website address");
                return;
            }
        }

        Double lat = null, lng = null;
        if (!latS.isEmpty() || !lngS.isEmpty()) {
            try {
                lat = Double.valueOf(latS);
                lng = Double.valueOf(lngS);
            } catch (NumberFormatException ex) {
                b.tilLat.setError("Enter both numbers, or leave both empty");
                return;
            }
            if (lat < -90 || lat > 90) { b.tilLat.setError("Must be between -90 and 90"); return; }
            if (lng < -180 || lng > 180) { b.tilLng.setError("Must be between -180 and 180"); return; }
        }

        if (!NetUtil.isOnline(this)) {
            Toast.makeText(this, "Saving needs an internet connection.", Toast.LENGTH_LONG).show();
            return;
        }

        CampusPlace p = new CampusPlace();
        p.name = name;
        p.category = category;
        p.where = where;
        p.hours = hours;
        p.phone = phone;
        p.website = website;
        p.note = note;
        p.keywords = keywords;
        p.lat = lat;
        p.lng = lng;

        setBusy(true);
        String docId = editId != null ? editId : db.collection("services").document().getId();
        db.collection("services").document(docId).set(p.toMap())
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, editId != null ? "Place updated." : "Place added.",
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    setBusy(false);
                    Toast.makeText(this, "Could not save. Check your internet and admin access.",
                            Toast.LENGTH_LONG).show();
                });
    }

    // ---------- delete ----------
    private void confirmDelete() {
        if (editId == null || busy) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this place?")
                .setMessage("It will disappear from every student's Services list. This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    setBusy(true);
                    db.collection("services").document(editId).delete()
                            .addOnSuccessListener(v -> {
                                Toast.makeText(this, "Place deleted.", Toast.LENGTH_SHORT).show();
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