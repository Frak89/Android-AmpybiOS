package dev.ampybios.core;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.View;

/** Actual Android Canvas preview: independent corner radii and moving radar highlights. */
public final class RadarPreview extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path border = new Path();
    private final RectF rect = new RectF();
    private final Matrix rotation = new Matrix();
    private ThemeProfile profile;
    private boolean radar = true;
    private boolean reducedMotion;
    private boolean animate = true;
    private float glow = 0.4f;
    private int speed = 100;
    private long epoch = SystemClock.uptimeMillis();

    public RadarPreview(Context context) { super(context); }

    public void setProfile(ThemeProfile data, boolean radarEnabled, boolean reduce,
                           float glowAlpha, int angularSpeed) {
        this.profile = data;
        this.radar = radarEnabled;
        this.reducedMotion = reduce;
        this.glow = Math.max(0f, Math.min(glowAlpha, 1f));
        this.speed = Math.max(0, Math.min(angularSpeed, 360));
        epoch = SystemClock.uptimeMillis();
        invalidate();
    }
    public void setVisibleAnimation(boolean enabled) {
        animate = enabled;
        if (enabled) invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (profile == null) return;
        float density = getResources().getDisplayMetrics().density;
        float inset = 22f * density;
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        if (rect.width() <= 0 || rect.height() <= 0) return;
        border.reset();
        border.addRoundRect(rect, profile.radii, Path.Direction.CW);
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(profile.surface);
        canvas.drawPath(border, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(12f * density);
        paint.setColor(profile.radar);
        paint.setAlpha(Math.round(glow * 95));
        canvas.drawPath(border, paint);

        paint.setStrokeWidth(3f * density);
        paint.setAlpha(255);
        if (radar) {
            float cx = rect.centerX();
            float cy = rect.centerY();
            Shader gradient = new SweepGradient(cx, cy,
                    new int[] {profile.secondary, profile.radar, profile.accent,
                            Color.WHITE, profile.secondary},
                    new float[] {0f, .45f, .78f, .82f, 1f});
            float elapsedSeconds = (SystemClock.uptimeMillis() - epoch) / 1000f;
            float angle = (animate && !reducedMotion) ? elapsedSeconds * speed % 360f : 0;
            rotation.setRotate(angle, cx, cy);
            gradient.setLocalMatrix(rotation);
            paint.setShader(gradient);
        } else {
            paint.setColor(profile.accent);
        }
        canvas.drawPath(border, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 1));
        paint.setTextSize(22f * density);
        paint.setColor(profile.foreground);
        canvas.drawText("AMPYBIOS", rect.centerX(), rect.centerY(), paint);
        paint.setTextSize(11f * density);
        paint.setColor(profile.accent);
        canvas.drawText("ORGANIC FUSION", rect.centerX(), rect.centerY() + 25f * density, paint);
        if (animate && radar && !reducedMotion && speed > 0 && getWindowVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }
}
