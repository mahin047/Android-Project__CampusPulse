package com.campuspulse.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.core.graphics.ColorUtils;

import com.campuspulse.app.databinding.ViewStatCardBinding;

/** Reusable stat card: icon + animated number + label. */
public class StatCardView extends LinearLayout {

    private final ViewStatCardBinding b;
    private ValueAnimator animator;
    private int shown = 0;

    public StatCardView(Context context) {
        this(context, null);
    }

    public StatCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.bg_stat_card);
        int p = (int) (14 * getResources().getDisplayMetrics().density);
        setPadding(p, p, p, p);
        b = ViewStatCardBinding.inflate(LayoutInflater.from(context), this); // <merge> needs a parent
    }

    public void setup(String label, @DrawableRes int iconRes, int color) {
        b.tvLabel.setText(label);
        b.ivIcon.setImageResource(iconRes);
        b.ivIcon.setImageTintList(ColorStateList.valueOf(color));
        b.ivIcon.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x33)));
        setContentDescription(label);
    }

    /** Count-up animation from the number currently shown. */
    public void setValue(int value) {
        if (animator != null) animator.cancel();
        if (value == shown) {
            b.tvValue.setText(String.valueOf(value));
            return;
        }
        animator = ValueAnimator.ofInt(shown, value);
        animator.setDuration(600);
        animator.addUpdateListener(a -> {
            shown = (int) a.getAnimatedValue();
            b.tvValue.setText(String.valueOf(shown));
        });
        animator.start();
    }

    public void setValueText(String text) {
        if (animator != null) animator.cancel();
        b.tvValue.setText(text);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel(); // leak বন্ধ
    }
}