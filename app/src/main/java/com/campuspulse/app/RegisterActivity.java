package com.campuspulse.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;
import com.google.firebase.firestore.WriteBatch;

import androidx.appcompat.app.AppCompatActivity;

import com.campuspulse.app.databinding.ActivityRegisterBinding;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.tvBack.setOnClickListener(v -> finish());

        binding.card.setTranslationY(600f);
        binding.card.setAlpha(0f);
        binding.card.animate().translationY(0f).alpha(1f).setDuration(600)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void attemptRegister() {
        String name = String.valueOf(binding.etName.getText()).trim();
        String sid = String.valueOf(binding.etStudentId.getText()).trim();
        String email = String.valueOf(binding.etEmail.getText()).trim();
        String pass = String.valueOf(binding.etPassword.getText());
        String confirm = String.valueOf(binding.etConfirm.getText());

        binding.tilName.setError(null);
        binding.tilStudentId.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirm.setError(null);

        if (name.length() < 3) { fail(binding.tilName, "Enter your full name"); return; }
        if (sid.isEmpty()) { fail(binding.tilStudentId, "Student ID is required"); return; }
        if (sid.contains("/")) { fail(binding.tilStudentId, "Student ID cannot contain /"); return; }
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
                        createAccount(name, sid, email, pass);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    shake();
                    Toast.makeText(this, "Could not check the student ID. Check your internet.",
                            Toast.LENGTH_LONG).show();
                });
    }

    private void createAccount(String name, String sid, String email, String pass) {
        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> saveProfile(r.getUser().getUid(), name, sid, email))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    shake();
                    String msg = "Registration failed. Try again.";
                    if (e instanceof FirebaseAuthUserCollisionException) msg = "This email is already registered.";
                    else if (e instanceof FirebaseNetworkException) msg = "No internet connection.";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
    }

    private void saveProfile(String uid, String name, String sid, String email) {
        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        user.put("name", name);
        user.put("studentId", sid);
        user.put("email", email);
        user.put("role", UserModel.ROLE_USER); // সবসময় "user", admin হয় শুধু console থেকে
        user.put("createdAt", FieldValue.serverTimestamp());

        Map<String, Object> lookup = new HashMap<>();
        lookup.put("email", email);

        WriteBatch batch = db.batch();
        batch.set(db.collection("users").document(uid), user);
        batch.set(db.collection("idLookup").document(sid), lookup);

        batch.commit()
                .addOnSuccessListener(v -> {
                    Intent i = new Intent(this, DashboardActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Could not save profile. Try again.", Toast.LENGTH_LONG).show();
                });
    }

    private void fail(com.google.android.material.textfield.TextInputLayout til, String msg) {
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