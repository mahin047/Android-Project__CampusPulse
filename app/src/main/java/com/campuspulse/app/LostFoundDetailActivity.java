package com.campuspulse.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import com.campuspulse.app.databinding.ActivityLostFoundDetailBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class LostFoundDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "lf_id";

    private ActivityLostFoundDetailBinding b;
    private FirebaseFirestore db;
    private String itemId, uid;
    private boolean isAdmin = false;
    private LostFoundModel item;
    private ListenerRegistration reg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLostFoundDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        db = FirebaseFirestore.getInstance();
        itemId = getIntent().getStringExtra(EXTRA_ID); // received data
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (itemId == null || u == null) { finish(); return; }
        uid = u.getUid();

        b.btnBack.setOnClickListener(v -> finish());
        b.btnCall.setOnClickListener(v -> callPoster());
        b.btnSms.setOnClickListener(v -> messagePoster());
        b.btnShare.setOnClickListener(v -> sharePost());
        b.btnResolve.setOnClickListener(v -> confirmResolve());
        b.btnDelete.setOnClickListener(v -> confirmDelete());

        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            String role = doc.getString("role");
            isAdmin = role != null && role.trim().equalsIgnoreCase("admin");
            render();
        });
    }

    // ---------- live listener ----------
    @Override
    protected void onStart() {
        super.onStart();
        reg = db.collection("lostfound").document(itemId).addSnapshotListener((snap, e) -> {
            if (e != null) return;
            if (snap == null || !snap.exists()) {
                Toast.makeText(this, "This post was removed.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            item = LostFoundModel.from(snap);
            render();
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (reg != null) { reg.remove(); reg = null; }
    }

    // ---------- UI ----------
    private void render() {
        if (item == null) return;

        int color = LostFoundModel.typeColor(item.getType());
        b.tvBadge.setText(item.isLost() ? "LOST" : "FOUND");
        b.tvBadge.setTextColor(color);
        b.tvBadge.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x33)));
        b.tvResolved.setVisibility(item.isResolved() ? View.VISIBLE : View.GONE);

        b.tvTitle.setText(item.getItemName());
        b.tvCategory.setText(item.getCategory());
        b.tvLocation.setText(item.getLocation());
        b.tvDate.setText((item.isLost() ? "Lost on " : "Found on ") + TimeUtil.dateOnly(item.getEventDate()));
        String sid = item.getPosterStudentId();
        b.tvPoster.setText("Posted by " + item.getPosterName()
                + (sid == null || sid.isEmpty() ? "" : "  •  " + sid)
                + "  •  " + TimeUtil.ago(item.getCreatedAt()));
        b.tvDescription.setText(item.getDescription());

        boolean owner = uid.equals(item.getPosterUid());

        // someone else's open post -> contact buttons
        b.contactGroup.setVisibility(!owner && !item.isResolved() ? View.VISIBLE : View.GONE);
        b.btnCall.setText(item.isLost() ? "Call the owner" : "Call the finder");

        // poster's own open post -> mark resolved
        b.btnResolve.setVisibility(owner && !item.isResolved() ? View.VISIBLE : View.GONE);
        b.btnResolve.setText(item.isLost() ? "I got it back" : "Item returned to owner");

        // poster or admin -> delete
        b.btnDelete.setVisibility(owner || isAdmin ? View.VISIBLE : View.GONE);
    }

    // ---------- implicit intents ----------
    private void callPoster() {
        if (item == null || item.getPhone() == null) return;
        // ACTION_DIAL opens the dialer, so no CALL_PHONE permission is needed
        Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(item.getPhone())));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "No dialer app found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void messagePoster() {
        if (item == null || item.getPhone() == null) return;
        String body = item.isLost()
                ? "Hi, I think I found your lost " + item.getItemName()
                + " (" + item.getLocation() + "). Saw your post on CampusPulse."
                : "Hi, the " + item.getItemName() + " you found at " + item.getLocation()
                + " may be mine. Saw your post on CampusPulse.";
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(item.getPhone())));
        i.putExtra("sms_body", body);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "No messaging app found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void sharePost() {
        if (item == null) return;
        String text = (item.isLost() ? "🔎 LOST: " : "✅ FOUND: ") + item.getItemName()
                + "\n📍 " + item.getLocation()
                + "\n📅 " + TimeUtil.dateOnly(item.getEventDate())
                + "\n\nSeen on CampusPulse. Please help!";
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, "Share post"));
    }

    // ---------- owner / admin actions ----------
    private void confirmResolve() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Mark as resolved?")
                .setMessage("The post will be greyed out and hidden from the main list.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Yes", (d, w) ->
                        db.collection("lostfound").document(itemId)
                                .update("status", LostFoundModel.STATUS_RESOLVED,
                                        "resolvedAt", FieldValue.serverTimestamp())
                                .addOnSuccessListener(v -> Toast.makeText(this,
                                        "Marked as resolved.", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(this,
                                        "Could not update. Try again.", Toast.LENGTH_LONG).show()))
                .show();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this post?")
                .setMessage("This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) ->
                        db.collection("lostfound").document(itemId).delete()
                                .addOnFailureListener(e -> Toast.makeText(this,
                                        "Could not delete.", Toast.LENGTH_LONG).show()))
                .show();
    }
}