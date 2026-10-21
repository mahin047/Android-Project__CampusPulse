package com.campuspulse.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.content.res.ResourcesCompat;

/** Hold-to-send SOS button. Draws everything on a Canvas. */
public class SosButtonView extends View {

    public interface OnSosListener {
        void onSosTriggered();
        void onReleasedEarly();
    }

    private static final long HOLD_MS = 1500L;
    private static final int RED = 0xFFFF4D5E;

    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float density;

    private ValueAnimator pulse, hold, release;
    private float pulseT = 0f, holdT = 0f;
    private boolean cancelledHold = false;
    private String hint = "HOLD";
    private OnSosListener listener;

    public SosButtonView(Context context) {
        this(context, null);
    }

    public SosButtonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3 * density);
        ringPaint.setColor(RED);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(8 * density);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(Color.WHITE);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setColor(0xCCFFFFFF);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        try {
            textPaint.setTypeface(ResourcesCompat.getFont(context, R.font.poppins_bold));
            hintPaint.setTypeface(ResourcesCompat.getFont(context, R.font.poppins_regular));
        } catch (RuntimeException ignored) { }
    }

    public void setOnSosListener(OnSosListener l) { listener = l; }

    public void setHint(String text) {
        hint = text;
        invalidate();
    }

    // ---------- drawing ----------
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(cx, cy) * 0.58f;
        corePaint.setShader(new RadialGradient(cx, cy - r * 0.3f, r * 1.3f,
                new int[]{0xFFFF8A95, 0xFFE63946, 0xFFB3202F},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float maxR = Math.min(cx, cy) - 2 * density;
        float coreR = Math.min(cx, cy) * 0.58f * (1f - 0.05f * holdT);

        // two expanding pulse rings
        for (int i = 0; i < 2; i++) {
            float t = (pulseT + i * 0.5f) % 1f;
            ringPaint.setAlpha((int) (130 * (1f - t)));
            canvas.drawCircle(cx, cy, coreR + (maxR - coreR) * t, ringPaint);
        }

        canvas.drawCircle(cx, cy, coreR, corePaint);

        // hold progress: faint track + white arc
        float ar = coreR + 9 * density;
        oval.set(cx - ar, cy - ar, cx + ar, cy + ar);
        if (holdT > 0f) {
            arcPaint.setAlpha(60);
            canvas.drawCircle(cx, cy, ar, arcPaint);
            arcPaint.setAlpha(255);
            canvas.drawArc(oval, -90f, 360f * holdT, false, arcPaint);
        }

        textPaint.setTextSize(coreR * 0.62f);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        canvas.drawText("SOS", cx, cy - (fm.ascent + fm.descent) / 2f - coreR * 0.10f, textPaint);
        hintPaint.setTextSize(coreR * 0.17f);
        canvas.drawText(hint, cx, cy + coreR * 0.50f, hintPaint);
    }

    // ---------- touch: hold to trigger ----------
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float dx = e.getX() - getWidth() / 2f, dy = e.getY() - getHeight() / 2f;
                float limit = Math.min(getWidth(), getHeight()) / 2f * 0.62f;
                if (Math.hypot(dx, dy) > limit) return false; // শুধু red circle এর ভেতরে
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                startHold();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelHold();
                return true;
            default:
                return true;
        }
    }

    private void startHold() {
        if (release != null) release.cancel();
        cancelledHold = false;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        hold = ValueAnimator.ofFloat(holdT, 1f);
        hold.setDuration((long) (HOLD_MS * (1f - holdT)));
        hold.setInterpolator(new LinearInterpolator());
        hold.addUpdateListener(a -> {
            holdT = (float) a.getAnimatedValue();
            invalidate();
        });
        hold.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationCancel(Animator a) { cancelledHold = true; }

            @Override public void onAnimationEnd(Animator a) {
                if (cancelledHold) return;
                holdT = 0f;
                invalidate();
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (listener != null) listener.onSosTriggered();
            }
        });
        hold.start();
    }

    private void cancelHold() {
        if (hold != null && hold.isRunning()) {
            hold.cancel();
            if (listener != null && holdT > 0.05f) listener.onReleasedEarly();
            release = ValueAnimator.ofFloat(holdT, 0f);
            release.setDuration(200);
            release.addUpdateListener(a -> {
                holdT = (float) a.getAnimatedValue();
                invalidate();
            });
            release.start();
        }
    }

    // ---------- lifecycle of the animators ----------
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(2000);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new LinearInterpolator());
        pulse.addUpdateListener(a -> {
            pulseT = (float) a.getAnimatedValue();
            invalidate();
        });
        pulse.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulse != null) pulse.cancel(); // leak বন্ধ
        if (hold != null) hold.cancel();
        if (release != null) release.cancel();
    }
}