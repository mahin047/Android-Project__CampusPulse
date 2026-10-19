package com.campuspulse.app;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.campuspulse.app.databinding.ActivityAddEventBinding;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class AddEventActivity extends AppCompatActivity {

    private ActivityAddEventBinding binding;
    private FirebaseFirestore db;
    private String authorName = "Administration";
    private final Calendar picked = Calendar.getInstance();
    private boolean dateSet = false, timeSet = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddEventBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();

        binding.actCategory.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, EventModel.CATEGORIES));
        binding.actCategory.setText("Workshop", false);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.etDate.setOnClickListener(v -> pickDate());
        binding.etTime.setOnClickListener(v -> pickTime());
        binding.btnPublish.setOnClickListener(v -> publish());

        verifyAdmin();
    }

    private void verifyAdmin() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }
        db.collection("users").document(u.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    if (role == null || !role.trim().equalsIgnoreCase("admin")) {
                        Toast.makeText(this, "Admins only.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    String name = doc.getString("name");
                    if (name != null && !name.trim().isEmpty()) authorName = name.trim();
                });
    }

    private void pickDate() {
        MaterialDatePicker<Long> p = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Event date").build();
        p.addOnPositiveButtonClickListener(sel -> {
            // the picker returns UTC midnight; read Y/M/D in UTC, apply in local time
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.setTimeInMillis(sel);
            picked.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
            dateSet = true;
            binding.etDate.setText(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    .format(picked.getTime()));
            binding.tilDate.setError(null);
        });
        p.show(getSupportFragmentManager(), "date");
    }

    private void pickTime() {
        MaterialTimePicker t = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(10).setMinute(0)
                .setTitleText("Event time").build();
        t.addOnPositiveButtonClickListener(v -> {
            picked.set(Calendar.HOUR_OF_DAY, t.getHour());
            picked.set(Calendar.MINUTE, t.getMinute());
            picked.set(Calendar.SECOND, 0);
            picked.set(Calendar.MILLISECOND, 0);
            timeSet = true;
            binding.etTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault())
                    .format(picked.getTime()));
            binding.tilTime.setError(null);
        });
        t.show(getSupportFragmentManager(), "time");
    }

    private void publish() {
        String title = String.valueOf(binding.etTitle.getText()).trim();
        String venue = String.valueOf(binding.etVenue.getText()).trim();
        String desc = String.valueOf(binding.etDesc.getText()).trim();
        String category = String.valueOf(binding.actCategory.getText()).trim();
        String capText = String.valueOf(binding.etCapacity.getText()).trim();

        binding.tilTitle.setError(null);
        binding.tilVenue.setError(null);
        binding.tilDate.setError(null);
        binding.tilTime.setError(null);
        binding.tilCapacity.setError(null);
        binding.tilDesc.setError(null);

        if (title.length() < 3) { binding.tilTitle.setError("Enter a title"); return; }
        if (venue.length() < 2) { binding.tilVenue.setError("Enter the venue"); return; }
        if (!dateSet) { binding.tilDate.setError("Pick a date"); return; }
        if (!timeSet) { binding.tilTime.setError("Pick a time"); return; }

        int capacity = 0; // 0 = unlimited
        if (!capText.isEmpty()) {
            try {
                capacity = Integer.parseInt(capText);
            } catch (NumberFormatException ex) {
                binding.tilCapacity.setError("Enter a valid number");
                return;
            }
            if (capacity < 0) { binding.tilCapacity.setError("Cannot be negative"); return; }
        }
        if (desc.length() < 5) { binding.tilDesc.setError("Write a short description"); return; }

        binding.btnPublish.setEnabled(false);
        binding.btnPublish.setText("Publishing...");

        Map<String, Object> event = new HashMap<>();
        event.put("title", title);
        event.put("venue", venue);
        event.put("category", category);
        event.put("description", desc);
        event.put("dateTime", picked.getTime());
        event.put("capacity", capacity);
        event.put("registeredCount", 0);
        event.put("authorName", authorName);
        event.put("createdAt", FieldValue.serverTimestamp());

        db.collection("events").add(event)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Event published.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    binding.btnPublish.setEnabled(true);
                    binding.btnPublish.setText("Publish event");
                    Toast.makeText(this, "Could not publish. Check your internet.",
                            Toast.LENGTH_LONG).show();
                });
    }
}