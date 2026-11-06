package com.campuspulse.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Loading placeholder: কয়েকটা ধূসর কাঠামো, তার উপর দিয়ে আলোর রেখা বয়ে যায়। */
public class SkeletonView extends FrameLayout {

    private final Paint paint = new Paint();
    private final Matrix matrix = new Matrix();
    private LinearGradient gradient;
    private ValueAnimator animator;
    private float progress = 0f;

    public SkeletonView(Context context) {
        this(context, null);
    }

    public SkeletonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        // SRC_ATOP: আলো শুধু সেখানেই আঁকা হয় যেখানে কাঠামো (bar) আছে
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));

        float d = getResources().getDisplayMetrics().density;
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) (16 * d), (int) (14 * d), (int) (16 * d), 0);
        for (int i = 0; i < 6; i++) {
            LayoutInflater.from(context).inflate(R.layout.item_skeleton_row, col, true);
        }
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        setContentDescription("Loading");
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        gradient = new LinearGradient(0, 0, Math.max(1, w * 0.6f), 0,
                new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        int layer = canvas.saveLayer(0, 0, w, h, null);
        super.dispatchDraw(canvas);                       // কাঠামোগুলো আঁকো
        if (gradient != null) {
            matrix.setTranslate(-0.6f * w + progress * 1.6f * w, 0);
            gradient.setLocalMatrix(matrix);
            canvas.drawRect(0, 0, w, h, paint);           // তার উপর আলোর রেখা
        }
        canvas.restoreToCount(layer);
    }

    // ---------- animation শুধু দেখা গেলে চলে (ব্যাটারি বাঁচে) ----------
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        updateAnimation();
    }

    private void updateAnimation() {
        if (getVisibility() == VISIBLE && isAttachedToWindow()) startAnimation2();
        else stopAnimation();
    }

    private void startAnimation2() {
        if (animator != null) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1300);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}