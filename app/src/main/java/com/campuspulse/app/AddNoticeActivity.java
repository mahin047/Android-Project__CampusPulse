package com.campuspulse.app;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.campuspulse.app.databinding.ActivityAddNoticeBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AddNoticeActivity extends AppCompatActivity {

    private ActivityAddNoticeBinding binding;
    private FirebaseFirestore db;
    private String authorName = "Administration";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddNoticeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();

        binding.actCategory.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, NoticeModel.CATEGORIES));
        binding.actPriority.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, NoticeModel.PRIORITIES));
        binding.actCategory.setText("General", false);
        binding.actPriority.setText("Normal", false);

        binding.btnBack.setOnClickListener(v -> finish());
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

    private void publish() {
        String title = String.valueOf(binding.etTitle.getText()).trim();
        String body = String.valueOf(binding.etBody.getText()).trim();
        String category = String.valueOf(binding.actCategory.getText()).trim();
        String priority = String.valueOf(binding.actPriority.getText()).trim();

        binding.tilTitle.setError(null);
        binding.tilBody.setError(null);

        if (title.length() < 3) { binding.tilTitle.setError("Enter a title"); return; }
        if (body.length() < 5) { binding.tilBody.setError("Write the details"); return; }

        binding.btnPublish.setEnabled(false);
        binding.btnPublish.setText("Publishing...");

        Map<String, Object> notice = new HashMap<>();
        notice.put("title", title);
        notice.put("body", body);
        notice.put("category", category);
        notice.put("priority", priority);
        notice.put("authorName", authorName);
        notice.put("createdAt", FieldValue.serverTimestamp());

        db.collection("notices").add(notice)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Notice published.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    binding.btnPublish.setEnabled(true);
                    binding.btnPublish.setText("Publish notice");
                    Toast.makeText(this, "Could not publish. Check your internet.",
                            Toast.LENGTH_LONG).show();
                });
    }
}