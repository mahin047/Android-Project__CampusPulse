package com.campuspulse.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.campuspulse.app.databinding.ActivityRegisterBinding;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    private static final Pattern BD_PHONE = Pattern.compile("^(\\+?88)?01[3-9][0-9]{8}$");

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String photoB64 = null;
    private boolean photoBusy = false;
    private String deptCode = null;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) processPhoto(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.actDept.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, Departments.labels()));
        binding.actDept.setOnItemClickListener((p, v, pos, id) -> {
            deptCode = Departments.CODES[pos];
            binding.tilDept.setError(null);
        });

        binding.ivAvatar.setOnClickListener(v -> pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.tvBack.setOnClickListener(v -> finish());

        binding.card.setTranslationY(600f);
        binding.card.setAlpha(0f);
        binding.card.animate().translationY(0f).alpha(1f).setDuration(600)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    // ---------- photo (background thread) ----------
    private void processPhoto(Uri uri) {
        photoBusy = true;
        binding.avatarLoading.setVisibility(View.VISIBLE);
        io.execute(() -> {
            try {
                byte[] data = ImageUtil.compressAvatar(getContentResolver(), uri);
                String b64 = ImageUtil.toBase64(data);
                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    photoB64 = b64;
                    photoBusy = false;
                    binding.avatarLoading.setVisibility(View.GONE);
                    binding.ivAvatar.setPadding(0, 0, 0, 0);
                    binding.ivAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    binding.ivAvatar.setImageBitmap(bmp);
                    binding.tvPhotoHint.setText("Looks good! Tap to change");
                    binding.tvPhotoHint.setTextColor(Color.parseColor("#9FE3D6"));
                });
            } catch (IOException | RuntimeException ex) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    photoBusy = false;
                    binding.avatarLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Could not read that image. Try another one.",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ---------- validate ----------
    private void attemptRegister() {
        String name = String.valueOf(binding.etName.getText()).trim();
        String sid = String.valueOf(binding.etStudentId.getText()).trim();
        String email = String.valueOf(binding.etEmail.getText()).trim();
        String phone = String.valueOf(binding.etPhone.getText()).replaceAll("[\\s-]", "");
        String pass = String.valueOf(binding.etPassword.getText());
        String confirm = String.valueOf(binding.etConfirm.getText());

        binding.tilName.setError(null);
        binding.tilStudentId.setError(null);
        binding.tilDept.setError(null);
        binding.tilPhone.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirm.setError(null);

        if (photoBusy) { Toast.makeText(this, "Photo is still processing…", Toast.LENGTH_SHORT).show(); return; }
        if (photoB64 == null) {
            binding.tvPhotoHint.setText("Profile photo is required");
            binding.tvPhotoHint.setTextColor(Color.parseColor("#FF8A95"));
            shake();
            return;
        }
        if (name.length() < 3) { fail(binding.tilName, "Enter your full name"); return; }
        if (sid.isEmpty()) { fail(binding.tilStudentId, "Student ID is required"); return; }
        if (sid.contains("/")) { fail(binding.tilStudentId, "Student ID cannot contain /"); return; }
        if (deptCode == null) { fail(binding.tilDept, "Select your department"); return; }
        if (!BD_PHONE.matcher(phone).matches()) { fail(binding.tilPhone, "Enter a valid number, e.g. 01712345678"); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { fail(binding.tilEmail, "Enter a valid email"); return; }
        if (pass.length() < 6) { fail(binding.tilPassword, "At least 6 characters"); return; }
        if (!pass.equals(confirm)) { fail(binding.tilConfirm, "Passwords do not match"); return; }

        setLoading(true);
        db.collection("idLookup").document(sid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        setLoading(false);
                        fail(binding.tilStudentId, "This student ID is already registered");
                    } else {
                        createAccount(name, sid, phone, email, pass);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    shake();
                    Toast.makeText(this, "Could not check the student ID. Check your internet.",
                            Toast.LENGTH_LONG).show();
                });
    }

    private void createAccount(String name, String sid, String phone, String email, String pass) {
        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> saveProfile(r.getUser().getUid(), name, sid, phone, email))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    shake();
                    String msg = "Registration failed. Try again.";
                    if (e instanceof FirebaseAuthUserCollisionException) msg = "This email is already registered.";
                    else if (e instanceof FirebaseNetworkException) msg = "No internet connection.";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
    }

    private void saveProfile(String uid, String name, String sid, String phone, String email) {
        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        user.put("name", name);
        user.put("studentId", sid);
        user.put("department", deptCode);
        user.put("phone", phone);
        user.put("email", email);
        user.put("photoB64", photoB64);
        user.put("role", UserModel.ROLE_USER); // সবসময় "user", admin হয় শুধু console থেকে
        user.put("createdAt", FieldValue.serverTimestamp());

        Map<String, Object> lookup = new HashMap<>();
        lookup.put("email", email);

        WriteBatch batch = db.batch();
        batch.set(db.collection("users").document(uid), user);
        batch.set(db.collection("idLookup").document(sid), lookup);

        batch.commit()
                .addOnSuccessListener(v -> {
                    getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                            .putString("my_dept", deptCode).apply(); // Study Hub এর default department
                    Intent i = new Intent(this, DashboardActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Could not save profile. Try again.", Toast.LENGTH_LONG).show();
                });
    }

    private void fail(TextInputLayout til, String msg) {
        til.setError(msg);
        shake();
    }

    private void shake() {
        ObjectAnimator.ofFloat(binding.card, "translationX", 0, 25, -25, 15, -15, 6, 0)
                .setDuration(450).start();
    }

    private void setLoading(boolean loading) {
        binding.loading.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        binding.btnRegister.setEnabled(!loading);
    }
}