package com.campuspulse.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import com.campuspulse.app.databinding.ActivitySplashBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 5000L;

    private ActivitySplashBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playIntroAnimation();
        handler.postDelayed(this::routeNext, SPLASH_DELAY_MS);
    }

    private void playIntroAnimation() {
        // 1. Logo: bounce in
        binding.ivLogo.setScaleX(0.4f);
        binding.ivLogo.setScaleY(0.4f);
        binding.ivLogo.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(800)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();

        // 2. Title: slide up
        fadeUp(binding.titleRow, 450, 600);

        // 3. Tagline
        fadeUp(binding.tvTagline, 800, 500);

        // 4. Pulse (icons + ECG + labels)
        fadeUp(binding.pulseContainer, 1100, 600);
        binding.ecgView.postDelayed(() -> binding.ecgView.start(2200), 1400);
        // 5. Progress bar fills 0 -> 100
        ObjectAnimator progress = ObjectAnimator.ofInt(binding.progressBar, "progress", 0, 100);
        progress.setDuration(3500);
        progress.setStartDelay(1100);
        progress.setInterpolator(new LinearInterpolator());
        progress.start();

        // 6. Footer
        fadeUp(binding.tvUniversity, 1800, 500);
        fadeUp(binding.tvPowered, 2000, 500);
    }

    private void fadeUp(View v, long delay, long duration) {
        v.setTranslationY(40f);
        v.animate().alpha(1f).translationY(0f)
                .setStartDelay(delay).setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ---------- Day 2: role-based routing ----------
    private void routeNext() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            go(LoginActivity.class);
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> go(
                        "admin".equals(doc.getString("role"))
                                ? AdminDashboardActivity.class
                                : DashboardActivity.class))
                .addOnFailureListener(e -> go(DashboardActivity.class));
    }

    private void go(Class<?> target) {
        startActivity(new Intent(this, target)); // explicit intent
        finish(); // remove Splash from the back stack
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}