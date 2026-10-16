package com.campuspulse.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.campuspulse.app.databinding.ActivityLoginBinding;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // underlined footer links
        binding.tvNewCampus.setPaintFlags(binding.tvNewCampus.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        binding.tvSecurityKey.setPaintFlags(binding.tvSecurityKey.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
        binding.tvNewCampus.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class))); // explicit intent
        binding.tvSecurityKey.setOnClickListener(v ->
                Toast.makeText(this, "Security Key Access is not available yet.", Toast.LENGTH_SHORT).show());

        playIntro();
    }

    // ---------- Step 1: validate ----------
    private void attemptLogin() {
        String id = String.valueOf(binding.etId.getText()).trim();
        String pass = String.valueOf(binding.etPassword.getText());

        binding.tilId.setError(null);
        binding.tilPassword.setError(null);

        if (id.isEmpty()) {
            binding.tilId.setError("Enter your email or student ID");
            shake();
            return;
        }
        if (pass.isEmpty()) {
            binding.tilPassword.setError("Enter your password");
            shake();
            return;
        }

        if (id.contains("@")) {
            if (!Patterns.EMAIL_ADDRESS.matcher(id).matches()) {
                binding.tilId.setError("Enter a valid email");
                shake();
                return;
            }
            setLoading(true);
            signIn(id, pass);
        } else {
            if (id.contains("/")) {
                binding.tilId.setError("Invalid student ID");
                shake();
                return;
            }
            setLoading(true);
            resolveStudentId(id, pass);
        }
    }

    // ---------- Step 2: Student ID -> email ----------
    private void resolveStudentId(String studentId, String pass) {
        db.collection("idLookup").document(studentId).get()
                .addOnSuccessListener(doc -> {
                    String email = doc.exists() ? doc.getString("email") : null;
                    if (email == null) {
                        setLoading(false);
                        binding.tilId.setError("No account found for this ID");
                        shake();
                    } else {
                        signIn(email, pass);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    shake();
                    Toast.makeText(this, "Could not look up this ID. Check your internet.",
                            Toast.LENGTH_LONG).show();
                });
    }

    // ---------- Step 3: Firebase sign-in ----------
    private void signIn(String email, String pass) {
        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> verifyRole(r.getUser().getUid()))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    shake();
                    showAuthError(e);
                });
    }

    // ---------- Step 4: role decides the destination ----------
    private void verifyRole(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.exists() ? doc.getString("role") : null;
                    goNext(UserModel.ROLE_ADMIN.equals(role));
                })
                .addOnFailureListener(e -> {
                    auth.signOut();
                    setLoading(false);
                    shake();
                    Toast.makeText(this, "Could not verify your account. Check your internet.",
                            Toast.LENGTH_LONG).show();
                });
    }

    private void goNext(boolean isAdmin) {
        Class<?> target = isAdmin ? AdminDashboardActivity.class : DashboardActivity.class;
        Intent i = new Intent(this, target);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }

    private void showAuthError(Exception e) {
        String msg;
        if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            msg = "Wrong credentials. Please try again.";
        } else if (e instanceof FirebaseNetworkException) {
            msg = "No internet connection.";
        } else {
            msg = "Authentication failed. Please try again.";
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.btnLogin.setText(loading ? "Authenticating..." : "Authenticate");
    }

    // ---------- Animations ----------
    private void shake() {
        ObjectAnimator.ofFloat(binding.card, "translationX", 0, 25, -25, 15, -15, 6, 0)
                .setDuration(450).start();
    }

    private void playIntro() {
        fadeUp(binding.tvBrand, 100, 500, 30f);
        fadeUp(binding.tvTitle, 250, 500, 30f);
        fadeUp(binding.tvSubtitle, 400, 500, 30f);
        fadeUp(binding.card, 550, 650, 120f);
        fadeUp(binding.bottomNotice, 900, 500, 30f);
        binding.bottomIcons.setAlpha(0f);
        binding.bottomIcons.animate().alpha(0.7f).setStartDelay(1100).setDuration(500).start();
    }

    private void fadeUp(View v, long delay, long duration, float distance) {
        v.setAlpha(0f);
        v.setTranslationY(distance);
        v.animate().alpha(1f).translationY(0f)
                .setStartDelay(delay).setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }
}