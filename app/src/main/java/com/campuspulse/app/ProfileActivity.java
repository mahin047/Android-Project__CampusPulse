package com.campuspulse.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.campuspulse.app.databinding.ActivityProfileBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileActivity extends AppCompatActivity {

    public static final String EXTRA_ACTION = "action";
    public static final String ACTION_NOTIFICATIONS = "notifications";
    public static final String ACTION_FEEDBACK = "feedback";

    private ActivityProfileBinding b;
    private FirebaseFirestore db;
    private String uid;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) changePhoto(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) { finish(); return; }
        uid = u.getUid();
        db = FirebaseFirestore.getInstance();

        b.btnBack.setOnClickListener(v -> finish());
        View.OnClickListener pick = v -> pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
        b.ivAvatar.setOnClickListener(pick);
        b.tvChangePhoto.setOnClickListener(pick);

        b.btnNotifications.setOnClickListener(v -> returnAction(ACTION_NOTIFICATIONS));
        b.btnFeedback.setOnClickListener(v -> returnAction(ACTION_FEEDBACK));
        b.btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
        View glow = ((android.view.ViewGroup) b.ivAvatar.getParent()).getChildAt(0);
        android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(glow,
                android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.08f),
                android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.08f),
                android.animation.PropertyValuesHolder.ofFloat("alpha", 1f, 0.5f));
        pulse.setDuration(1400);
        pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulse.start();

        load();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void returnAction(String action) {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_ACTION, action));
        finish();
    }

    private void load() {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(this::render)
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Could not load your profile.", Toast.LENGTH_SHORT).show());
    }

    private void render(DocumentSnapshot d) {
        if (isDestroyed() || !d.exists()) return;
        String name = d.getString("name");
        b.tvName.setText(name == null ? "Student" : name);
        b.tvAvatarLetter.setText(name == null || name.isEmpty() ? "U" : name.substring(0, 1).toUpperCase());
        b.tvStudentId.setText(orDash(d.getString("studentId")));
        String dept = d.getString("department");
        b.tvDept.setText(dept == null ? "Not added" : dept + "  •  " + Departments.nameOf(dept));
        b.tvEmail.setText(orDash(d.getString("email")));
        String phone = d.getString("phone");
        b.tvPhone.setText(phone == null ? "Not added" : phone);
        Date joined = d.getDate("createdAt");
        b.tvJoined.setText(joined == null ? "-" : TimeUtil.dateOnly(joined));

        String b64 = d.getString("photoB64");
        if (b64 == null || b64.isEmpty()) {
            b.ivAvatar.setVisibility(View.INVISIBLE);
            b.tvChangePhoto.setText("Add photo");
        } else {
            b.tvChangePhoto.setText("Change photo");
            ImageUtil.decodeAsync(b64, bmp -> showPhoto(bmp));
        }
    }

    private void showPhoto(Bitmap bmp) {
        if (isDestroyed() || bmp == null) return;
        b.ivAvatar.setImageBitmap(bmp);
        b.ivAvatar.setVisibility(View.VISIBLE);
        b.tvChangePhoto.setText("Change photo");
    }

    private String orDash(String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }

    // ---------- photo বদলানো ----------
    private void changePhoto(Uri uri) {
        b.avatarLoading.setVisibility(View.VISIBLE);
        io.execute(() -> {
            try {
                byte[] data = ImageUtil.compressAvatar(getContentResolver(), uri);
                String b64 = ImageUtil.toBase64(data);
                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                db.collection("users").document(uid).update("photoB64", b64)
                        .addOnSuccessListener(v -> runOnUiThread(() -> {
                            if (isDestroyed()) return;
                            b.avatarLoading.setVisibility(View.GONE);
                            showPhoto(bmp);
                            Toast.makeText(this, "Photo updated.", Toast.LENGTH_SHORT).show();
                        }))
                        .addOnFailureListener(e -> runOnUiThread(() -> {
                            if (isDestroyed()) return;
                            b.avatarLoading.setVisibility(View.GONE);
                            Toast.makeText(this, "Could not save. Check your internet.",
                                    Toast.LENGTH_LONG).show();
                        }));
            } catch (IOException | RuntimeException ex) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    b.avatarLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Could not read that image.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}