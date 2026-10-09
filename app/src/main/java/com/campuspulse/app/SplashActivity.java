package com.campuspulse.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.campuspulse.app.databinding.ActivitySplashBinding;

public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private ObjectAnimator progressAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);   // draw behind status bar
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        playIntro();
    }

    private void playIntro() {
        // 1) Logo: scale + fade (0 ms)
        binding.ivLogo.setScaleX(0.6f);
        binding.ivLogo.setScaleY(0.6f);
        binding.ivLogo.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(800).setInterpolator(new OvershootInterpolator()).start();

        // 2) Title: slide up + fade (500 ms)
        binding.titleRow.setTranslationY(40f);
        binding.titleRow.animate().alpha(1f).translationY(0f)
                .setStartDelay(500).setDuration(600).start();

        // 3) Tagline (900 ms)
        binding.tvTagline.animate().alpha(1f).setStartDelay(900).setDuration(600).start();

        // 4) Icons + ECG line (1100 ms), then the line draws itself
        binding.pulseContainer.animate().alpha(1f).setStartDelay(1100).setDuration(400)
                .withEndAction(() -> binding.ecgView.start(2000)).start();

        // 5) Footer (1300 ms)
        binding.tvUniversity.animate().alpha(1f).setStartDelay(1300).setDuration(600).start();
        binding.tvPowered.animate().alpha(1f).setStartDelay(1500).setDuration(600).start();

        // 6) Progress bar, then go to Login
        progressAnim = ObjectAnimator.ofInt(binding.progressBar, "progress", 0, 100);
        progressAnim.setStartDelay(1200);
        progressAnim.setDuration(2200);
        progressAnim.setInterpolator(new LinearInterpolator());
        progressAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                goNext();
            }
        });
        progressAnim.start();
    }

    private void goNext() {
        // Later: if (FirebaseAuth.getInstance().getCurrentUser() != null) -> DashboardActivity
        startActivity(new Intent(this, LoginActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        if (progressAnim != null) {
            progressAnim.removeAllListeners();
            progressAnim.cancel();
        }
        super.onDestroy();
    }
}