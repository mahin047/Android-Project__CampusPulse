package com.campuspulse.app;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.campuspulse.app.databinding.ActivityPostItemBinding;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
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
import java.util.regex.Pattern;

public class PostItemActivity extends AppCompatActivity {

    // 01XXXXXXXXX, optionally prefixed with +88 or 88
    private static final Pattern BD_PHONE = Pattern.compile("^(\\+?88)?01[3-9][0-9]{8}$");

    private ActivityPostItemBinding binding;
    private FirebaseFirestore db;
    private String uid, myName = "Student", myStudentId = "";
    private boolean lost = true;
    private final Calendar picked = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPostItemBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }
        uid = u.getUid();

        binding.actCategory.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, LostFoundModel.CATEGORIES));
        binding.actCategory.setText(LostFoundModel.CATEGORIES[0], false);

        // default date = today
        binding.etDate.setText(formatDate());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.tvTypeLost.setOnClickListener(v -> setType(true));
        binding.tvTypeFound.setOnClickListener(v -> setType(false));
        binding.etDate.setOnClickListener(v -> pickDate());
        binding.btnPost.setOnClickListener(v -> post());

        setType(true);
        loadProfile();
    }

    private void loadProfile() {
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            String n = doc.getString("name");
            String s = doc.getString("studentId");
            if (n != null && !n.trim().isEmpty()) myName = n.trim();
            if (s != null) myStudentId = s;
        });
    }

    // ---------- Lost / Found toggle ----------
    private void setType(boolean isLost) {
        lost = isLost;
        styleToggle(binding.tvTypeLost, isLost, "#FF8484");
        styleToggle(binding.tvTypeFound, !isLost, "#34D399");

        binding.tilName.setHint(isLost ? "What did you lose?" : "What did you find?");
        binding.tilDate.setHint(isLost ? "Date lost" : "Date found");
        binding.tilLocation.setHint(isLost ? "Where did you lose it?" : "Where did you find it?");
        binding.btnPost.setText(isLost ? "Post lost item" : "Post found item");
        binding.btnPost.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor(isLost ? "#FF8484" : "#34D399")));
    }

    private void styleToggle(TextView tv, boolean selected, String color) {
        tv.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        tv.setBackgroundTintList(selected ? ColorStateList.valueOf(Color.parseColor(color)) : null);
        tv.setTextColor(selected ? Color.parseColor("#06201E") : Color.parseColor("#C3D2EA"));
    }

    // ---------- date picker (no future dates) ----------
    private void pickDate() {
        CalendarConstraints limit = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now()).build();
        MaterialDatePicker<Long> p = MaterialDatePicker.Builder.datePicker()
                .setTitleText(lost ? "Date lost" : "Date found")
                .setCalendarConstraints(limit)
                .build();
        p.addOnPositiveButtonClickListener(sel -> {
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.setTimeInMillis(sel);
            picked.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
            binding.etDate.setText(formatDate());
        });
        p.show(getSupportFragmentManager(), "date");
    }

    private String formatDate() {
        return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(picked.getTime());
    }

    // ---------- validate + save ----------
    private void post() {
        String name = String.valueOf(binding.etName.getText()).trim();
        String category = String.valueOf(binding.actCategory.getText()).trim();
        String location = String.valueOf(binding.etLocation.getText()).trim();
        String phone = String.valueOf(binding.etPhone.getText()).replaceAll("[\\s-]", "");
        String desc = String.valueOf(binding.etDesc.getText()).trim();

        binding.tilName.setError(null);
        binding.tilLocation.setError(null);
        binding.tilPhone.setError(null);
        binding.tilDesc.setError(null);

        if (name.length() < 2) { binding.tilName.setError("Enter the item name"); return; }
        if (location.length() < 2) { binding.tilLocation.setError("Enter the location"); return; }
        if (!BD_PHONE.matcher(phone).matches()) {
            binding.tilPhone.setError("Enter a valid number, e.g. 01712345678");
            return;
        }
        if (desc.length() < 5) { binding.tilDesc.setError("Add a short description"); return; }

        binding.btnPost.setEnabled(false);
        binding.btnPost.setText("Posting...");

        Map<String, Object> item = new HashMap<>();
        item.put("type", lost ? LostFoundModel.TYPE_LOST : LostFoundModel.TYPE_FOUND);
        item.put("itemName", name);
        item.put("category", category);
        item.put("location", location);
        item.put("description", desc);
        item.put("phone", phone);
        item.put("eventDate", picked.getTime());
        item.put("posterUid", uid);
        item.put("posterName", myName);
        item.put("posterStudentId", myStudentId);
        item.put("status", LostFoundModel.STATUS_OPEN);
        item.put("createdAt", FieldValue.serverTimestamp());

        db.collection("lostfound").add(item)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Posted. Good luck!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    binding.btnPost.setEnabled(true);
                    binding.btnPost.setText(lost ? "Post lost item" : "Post found item");
                    Toast.makeText(this, "Could not post. Check your internet.",
                            Toast.LENGTH_LONG).show();
                });
    }
}