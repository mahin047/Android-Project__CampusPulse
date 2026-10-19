package com.campuspulse.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import com.campuspulse.app.databinding.ActivityEventDetailBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "ev_id";

    private ActivityEventDetailBinding b;
    private FirebaseFirestore db;
    private String eventId, uid;
    private String myName = "Student", myStudentId = "";
    private boolean isAdmin = false, registered = false, busy = false;
    private EventModel event;
    private ListenerRegistration evReg, myReg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityEventDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        db = FirebaseFirestore.getInstance();
        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID); // received data
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (eventId == null || u == null) { finish(); return; }
        uid = u.getUid();

        b.btnBack.setOnClickListener(v -> finish());
        b.btnRegister.setOnClickListener(v -> toggleRegistration());
        b.btnCalendar.setOnClickListener(v -> addToCalendar());
        b.btnShare.setOnClickListener(v -> shareEvent());
        b.btnRegistrations.setOnClickListener(v -> showRegistrations());

        loadProfile();
    }

    private void loadProfile() {
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            String n = doc.getString("name");
            String s = doc.getString("studentId");
            if (n != null && !n.trim().isEmpty()) myName = n.trim();
            if (s != null) myStudentId = s;
            String role = doc.getString("role");
            isAdmin = role != null && role.trim().equalsIgnoreCase("admin");
            render();
        });
    }

    // ---------- live listeners (lifecycle-aware) ----------
    @Override
    protected void onStart() {
        super.onStart();
        evReg = db.collection("events").document(eventId).addSnapshotListener((snap, e) -> {
            if (e != null) return;
            if (snap == null || !snap.exists()) {
                Toast.makeText(this, "This event was removed.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            event = EventModel.from(snap);
            render();
        });
        myReg = db.collection("users").document(uid).collection("myEvents").document(eventId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) return;
                    registered = snap != null && snap.exists();
                    render();
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (evReg != null) { evReg.remove(); evReg = null; }
        if (myReg != null) { myReg.remove(); myReg = null; }
    }

    // ---------- UI ----------
    private void render() {
        if (event == null) return;

        int color = EventModel.categoryColor(event.getCategory());
        b.tvBadge.setText(event.getCategory() == null ? "OTHER" : event.getCategory().toUpperCase());
        b.tvBadge.setTextColor(color);
        b.tvBadge.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x33)));

        b.tvTitle.setText(event.getTitle());
        b.tvWhen.setText(TimeUtil.eventFull(event.getDateTime()));
        b.tvVenue.setText(event.getVenue());
        b.tvDescription.setText(event.getDescription());

        if (event.getCapacity() > 0) {
            b.tvSeats.setText(event.getRegisteredCount() + " / " + event.getCapacity()
                    + " registered  •  " + event.seatsLeft() + " seats left");
            b.progressSeats.setVisibility(View.VISIBLE);
            b.progressSeats.setMax(event.getCapacity());
            b.progressSeats.setProgress(Math.min(event.getRegisteredCount(), event.getCapacity()));
            b.progressSeats.setIndicatorColor(event.isFull() ? Color.parseColor("#FF5A5F") : color);
        } else {
            b.tvSeats.setText(event.getRegisteredCount() + " registered  •  Open to all");
            b.progressSeats.setVisibility(View.GONE);
        }

        // admin sees the list button, students see the register button
        b.btnRegistrations.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        b.btnRegister.setVisibility(isAdmin ? View.GONE : View.VISIBLE);
        if (isAdmin || busy) return;

        if (registered) {
            if (event.isPast()) {
                styleRegister("You were registered", "#2DD4BF", "#06201E", false);
            } else {
                styleRegister("Cancel registration", "#D44458", "#FFFFFF", true);
            }
        } else if (event.isPast()) {
            styleRegister("Event ended", "#2DD4BF", "#06201E", false);
        } else if (event.isFull()) {
            styleRegister("Event full", "#2DD4BF", "#06201E", false);
        } else {
            styleRegister("Register now", "#2DD4BF", "#06201E", true);
        }
    }

    private void styleRegister(String text, String bg, String fg, boolean enabled) {
        b.btnRegister.setText(text);
        b.btnRegister.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(bg)));
        b.btnRegister.setTextColor(Color.parseColor(fg));
        b.btnRegister.setEnabled(enabled);
    }

    private void setBusy(boolean value) {
        busy = value;
        if (value) {
            b.btnRegister.setEnabled(false);
            b.btnRegister.setText("Please wait...");
        } else {
            render();
        }
    }

    // ---------- register / cancel (Firestore transaction) ----------
    private void toggleRegistration() {
        if (event == null) return;
        setBusy(true);

        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference regRef = eventRef.collection("registrations").document(uid);
        DocumentReference myRef = db.collection("users").document(uid)
                .collection("myEvents").document(eventId);
        final boolean wantRegister = !registered;

        db.runTransaction(tr -> {
            DocumentSnapshot ev = tr.get(eventRef);
            DocumentSnapshot reg = tr.get(regRef);
            if (!ev.exists()) {
                throw new FirebaseFirestoreException("GONE", FirebaseFirestoreException.Code.ABORTED);
            }
            Long c = ev.getLong("registeredCount");
            long count = c == null ? 0 : c;

            if (wantRegister) {
                if (reg.exists()) return null; // already registered
                Long cap = ev.getLong("capacity");
                if (cap != null && cap > 0 && count >= cap) {
                    throw new FirebaseFirestoreException("FULL", FirebaseFirestoreException.Code.ABORTED);
                }
                Map<String, Object> r = new HashMap<>();
                r.put("name", myName);
                r.put("studentId", myStudentId);
                r.put("registeredAt", FieldValue.serverTimestamp());
                Map<String, Object> m = new HashMap<>();
                m.put("eventId", eventId);
                m.put("registeredAt", FieldValue.serverTimestamp());

                tr.update(eventRef, "registeredCount", count + 1);
                tr.set(regRef, r);
                tr.set(myRef, m);
            } else {
                if (reg.exists()) {
                    if (count > 0) tr.update(eventRef, "registeredCount", count - 1);
                    tr.delete(regRef);
                }
                tr.delete(myRef);
            }
            return null;
        }).addOnSuccessListener(v -> {
            Toast.makeText(this, wantRegister ? "You're registered! 🎉" : "Registration cancelled.",
                    Toast.LENGTH_SHORT).show();
            setBusy(false);
        }).addOnFailureListener(e -> {
            String msg = "Something went wrong. Try again.";
            if ("FULL".equals(e.getMessage())) msg = "Sorry, this event just filled up.";
            else if ("GONE".equals(e.getMessage())) msg = "This event was removed.";
            else if (e instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) e).getCode()
                    == FirebaseFirestoreException.Code.UNAVAILABLE) {
                msg = "Registration needs an internet connection.";
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            setBusy(false);
        });
    }

    // ---------- admin: who registered ----------
    private void showRegistrations() {
        db.collection("events").document(eventId).collection("registrations")
                .orderBy("registeredAt").get()
                .addOnSuccessListener(snap -> {
                    List<String> rows = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        rows.add((rows.size() + 1) + ".  " + d.getString("name")
                                + "  •  " + d.getString("studentId"));
                    }
                    if (rows.isEmpty()) {
                        Toast.makeText(this, "No registrations yet.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(rows.size() + " registered")
                            .setItems(rows.toArray(new String[0]), null)
                            .setPositiveButton("Close", null)
                            .show();
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Could not load the list.", Toast.LENGTH_LONG).show());
    }

    // ---------- implicit intents ----------
    private void addToCalendar() {
        if (event == null || event.getDateTime() == null) return;
        long start = event.getDateTime().getTime();
        Intent i = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, event.getTitle())
                .putExtra(CalendarContract.Events.EVENT_LOCATION, event.getVenue())
                .putExtra(CalendarContract.Events.DESCRIPTION, event.getDescription())
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 2 * 60 * 60 * 1000L);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "No calendar app found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareEvent() {
        if (event == null) return;
        String text = "📅 " + event.getTitle()
                + "\n🕒 " + TimeUtil.eventFull(event.getDateTime())
                + "\n📍 " + event.getVenue()
                + "\n\nJoin me on CampusPulse!";
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, "Share event"));
    }
}