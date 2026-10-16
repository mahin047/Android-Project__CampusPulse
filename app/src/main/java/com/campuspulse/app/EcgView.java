package com.campuspulse.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class EcgView extends View {

    private final float density = getResources().getDisplayMetrics().density;
    private final int mint = Color.parseColor("#2EF2B0");
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Normalized points (x: 0..1, y: 0..1). Spike sits between node 3 and node 4.
    private final float[][] points = {
            {1f / 12f, .50f}, {.43f, .50f}, {.45f, .40f}, {.47f, .50f},
            {.49f, .50f}, {.51f, .06f}, {.54f, .94f}, {.56f, .50f}, {11f / 12f, .50f}
    };
    private final float[] nodeX = new float[6];

    private final Path path = new Path();
    private final Path segment = new Path();
    private final float[] tip = new float[2];
    private PathMeasure measure;
    private float progress = 0f;
    private ValueAnimator animator;

    public EcgView(Context context) {
        this(context, null);
    }

    public EcgView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // needed for glow

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.5f * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(mint);
        linePaint.setShadowLayer(8f * density, 0f, 0f, mint);   // glow

        dotPaint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < nodeX.length; i++) {
            nodeX[i] = (i + 0.5f) / 6f;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        path.reset();
        for (int i = 0; i < points.length; i++) {
            float x = points[i][0] * w;
            float y = points[i][1] * h;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        measure = new PathMeasure(path, false);
    }

    public void start() {
        start(2000L);
    }

    public void start(long duration) {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
       // android.util.Log.d("ECG", "onDraw progress=" + progress + " measure=" + (measure != null));
        if (measure == null) return;

        float len = measure.getLength() * progress;
        segment.reset();
        measure.getSegment(0f, len, segment, true);
        canvas.drawPath(segment, linePaint);

        measure.getPosTan(len, tip, null);

        float cy = getHeight() * 0.5f;
        for (float nx : nodeX) {
            float cx = nx * getWidth();
            boolean lit = tip[0] >= cx;
            dotPaint.setColor(lit ? mint : Color.parseColor("#3D6FA8"));
            dotPaint.setShadowLayer(lit ? 10f * density : 0f, 0f, 0f, mint);
            canvas.drawCircle(cx, cy, 4.5f * density, dotPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}