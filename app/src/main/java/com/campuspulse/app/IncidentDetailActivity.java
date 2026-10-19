package com.campuspulse.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import com.campuspulse.app.databinding.ActivityIncidentDetailBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IncidentDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "inc_id";

    private static final int OFF = 0x33FFFFFF;
    private static final int MUTED = Color.parseColor("#7C8FB0");

    private ActivityIncidentDetailBinding b;
    private FirebaseFirestore db;
    private String incId, uid, myName = "Administration";
    private boolean isAdmin = false, noteFilled = false, photoRequested = false, busy = false;
    private IncidentModel item;
    private ListenerRegistration reg;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityIncidentDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        db = FirebaseFirestore.getInstance();
        incId = getIntent().getStringExtra(EXTRA_ID); // received data
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (incId == null || u == null) { finish(); return; }
        uid = u.getUid();

        b.btnBack.setOnClickListener(v -> finish());
        b.btnMap.setOnClickListener(v -> openMap());
        b.btnPrimary.setOnClickListener(v -> advanceStatus());
        b.btnSaveNote.setOnClickListener(v -> saveNoteOnly());

        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            String role = doc.getString("role");
            isAdmin = role != null && role.trim().equalsIgnoreCase("admin");
            String n = doc.getString("name");
            if (n != null && !n.trim().isEmpty()) myName = n.trim();
            render();
        });
    }

    // ---------- live listener: status changes appear instantly ----------
    @Override
    protected void onStart() {
        super.onStart();
        reg = db.collection("incidents").document(incId).addSnapshotListener((snap, e) -> {
            if (e != null) return;
            if (snap == null || !snap.exists()) {
                Toast.makeText(this, "This report was removed.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            item = IncidentModel.from(snap);
            render();
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (reg != null) { reg.remove(); reg = null; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    // ---------- UI ----------
    private void render() {
        if (item == null) return;

        int cc = IncidentModel.categoryColor(item.getCategory());
        int sc = IncidentModel.statusColor(item.getStatus());

        b.tvBadge.setText(item.getCategory() == null ? "OTHER" : item.getCategory().toUpperCase());
        b.tvBadge.setTextColor(cc);
        b.tvBadge.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(cc, 0x33)));

        b.tvStatus.setText(IncidentModel.statusLabel(item.getStatus()));
        b.tvStatus.setTextColor(sc);
        b.tvStatus.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(sc, 0x33)));

        b.tvTitle.setText(item.getCategory());
        b.tvMeta.setText("Reported " + TimeUtil.ago(item.getCreatedAt()));
        b.tvLocation.setText(item.getLocation());
        String sid = item.getReporterStudentId();
        b.tvReporter.setText(item.getReporterName()
                + (sid == null || sid.isEmpty() ? "" : "  •  " + sid));
        b.tvDescription.setText(item.getDescription());

        renderTimeline(sc);

        // map button only when a location was attached
        b.btnMap.setVisibility(item.getLat() != null && item.getLng() != null ? View.VISIBLE : View.GONE);

        // reply from the administration
        String note = item.getAdminNote();
        boolean hasNote = note != null && !note.trim().isEmpty();
        b.cardResponse.setVisibility(hasNote && !isAdmin ? View.VISIBLE : View.GONE);
        if (hasNote) b.tvResponse.setText(note);

        loadPhotoOnce();
        renderAdminPanel();
    }

    private void renderTimeline(int color) {
        int step = item.step();
        tint(b.dot1, step >= 0 ? color : OFF);
        tint(b.dot2, step >= 1 ? color : OFF);
        tint(b.dot3, step >= 2 ? color : OFF);
        tint(b.line1, step >= 1 ? color : OFF);
        tint(b.line2, step >= 2 ? color : OFF);
        b.lbl1.setTextColor(step >= 0 ? Color.WHITE : MUTED);
        b.lbl2.setTextColor(step >= 1 ? Color.WHITE : MUTED);
        b.lbl3.setTextColor(step >= 2 ? Color.WHITE : MUTED);
    }

    private void tint(View v, int color) {
        v.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private void renderAdminPanel() {
        b.adminPanel.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        if (!isAdmin) return;

        if (!noteFilled) { // fill once, so we never overwrite what the admin is typing
            b.etNote.setText(item.getAdminNote());
            noteFilled = true;
        }
        if (busy) return;

        String label;
        String color;
        switch (item.step()) {
            case 0: label = "Start investigating"; color = "#F5B942"; break;
            case 1: label = "Mark as resolved"; color = "#34D399"; break;
            default: label = "Reopen investigation"; color = "#F5B942";
        }
        b.btnPrimary.setText(label);
        b.btnPrimary.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(color)));
        b.btnPrimary.setEnabled(true);
        b.btnSaveNote.setEnabled(true);
    }

    // ---------- photo: fetched once, decoded on a background thread ----------
    private void loadPhotoOnce() {
        if (!item.hasPhoto()) { b.photoBox.setVisibility(View.GONE); return; }
        b.photoBox.setVisibility(View.VISIBLE);
        if (photoRequested) return;
        photoRequested = true;

        db.collection("incidents").document(incId).collection("attachments").document("photo")
                .get()
                .addOnSuccessListener(doc -> {
                    String data = doc.getString("data");
                    if (data == null) { photoFailed(); return; }
                    io.execute(() -> {
                        try {
                            Bitmap bmp = ImageUtil.fromBase64(data);
                            runOnUiThread(() -> {
                                if (isDestroyed()) return;
                                b.ivPhoto.setImageBitmap(bmp);
                                b.photoLoading.setVisibility(View.GONE);
                            });
                        } catch (RuntimeException ex) {
                            runOnUiThread(this::photoFailed);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    photoRequested = false; // try again next time the screen refreshes
                    photoFailed();
                });
    }

    private void photoFailed() {
        if (isDestroyed()) return;
        b.photoLoading.setVisibility(View.GONE);
        b.photoBox.setVisibility(View.GONE);
        Toast.makeText(this, "Photo not available right now.", Toast.LENGTH_SHORT).show();
    }

    // ---------- implicit intent: open the location in any Maps app ----------
    private void openMap() {
        if (item == null || item.getLat() == null || item.getLng() == null) return;
        String label = Uri.encode(item.getLocation() == null ? "Incident" : item.getLocation());
        Uri uri = Uri.parse("geo:" + item.getLat() + "," + item.getLng()
                + "?q=" + item.getLat() + "," + item.getLng() + "(" + label + ")");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "No maps app found.", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- admin: Pending -> Investigating -> Resolved ----------
    private void advanceStatus() {
        if (item == null || !isAdmin) return;
        String next;
        switch (item.step()) {
            case 0: next = IncidentModel.INVESTIGATING; break;
            case 1: next = IncidentModel.RESOLVED; break;
            default: next = IncidentModel.INVESTIGATING; // reopen
        }
        setBusy(true);
        db.collection("incidents").document(incId)
                .update("status", next,
                        "adminNote", noteText(),
                        "handledBy", myName,
                        "updatedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Status: " + IncidentModel.statusLabel(next), Toast.LENGTH_SHORT).show();
                    setBusy(false);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Could not update. Check your internet.", Toast.LENGTH_LONG).show();
                    setBusy(false);
                });
    }

    private void saveNoteOnly() {
        if (item == null || !isAdmin) return;
        setBusy(true);
        db.collection("incidents").document(incId)
                .update("adminNote", noteText(),
                        "handledBy", myName,
                        "updatedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Note saved.", Toast.LENGTH_SHORT).show();
                    setBusy(false);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Could not save. Check your internet.", Toast.LENGTH_LONG).show();
                    setBusy(false);
                });
    }

    private String noteText() {
        return String.valueOf(b.etNote.getText()).trim();
    }

    private void setBusy(boolean value) {
        busy = value;
        if (value) {
            b.btnPrimary.setEnabled(false);
            b.btnSaveNote.setEnabled(false);
            b.btnPrimary.setText("Please wait...");
        } else {
            renderAdminPanel();
        }
    }
}