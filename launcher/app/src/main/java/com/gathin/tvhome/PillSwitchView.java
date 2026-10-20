package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The launcher's one on/off switch (App management rows, tile options). Display only: the row
 * around it takes focus and OK, so it has no focus ring of its own. Geometry and colours: {@link PillSwitch}.
 */
public class PillSwitchView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final float density;
    private boolean checked;
    private float progress;
    private ValueAnimator animator;

    public PillSwitchView(Context context) { this(context, null); }

    public PillSwitchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        fill.setStyle(Paint.Style.FILL);
        knob.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(PillSwitch.STROKE_DP * density);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public boolean isChecked() { return checked; }

    /** Sets the state; animate slides the knob (about 150ms), otherwise it jumps (used when binding rows). */
    public void setChecked(boolean on, boolean animate) {
        if (animator != null) animator.cancel();
        checked = on;
        float target = on ? 1f : 0f;
        if (!animate || !isAttachedToWindow() || progress == target) {
            progress = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(PillSwitch.ANIM_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.4f);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        progress = checked ? 1f : 0f;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize(Math.round(PillSwitch.TRACK_W_DP * density) + getPaddingLeft() + getPaddingRight(), widthMeasureSpec);
        int h = resolveSize(Math.round(PillSwitch.TRACK_H_DP * density) + getPaddingTop() + getPaddingBottom(), heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        float h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (w <= 0 || h <= 0) return;
        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());
        float r = PillSwitch.cornerRadius(h);
        float half = stroke.getStrokeWidth() / 2f;   // keep the outline inside the view
        track.set(half, half, w - half, h - half);
        fill.setColor(PillSwitch.blend(PillSwitch.OFF_TRACK, PillSwitch.ON_TRACK, progress));
        canvas.drawRoundRect(track, r - half, r - half, fill);
        float strokeAlpha = PillSwitch.strokeAlpha(progress);
        if (strokeAlpha > 0f) {
            int c = PillSwitch.OFF_STROKE;
            stroke.setColor(c);
            stroke.setAlpha(Math.round(((c >>> 24) & 0xFF) * strokeAlpha));
            canvas.drawRoundRect(track, r - half, r - half, stroke);
        }
        knob.setColor(PillSwitch.blend(PillSwitch.OFF_KNOB, PillSwitch.ON_KNOB, progress));
        canvas.drawCircle(PillSwitch.knobCenterX(w, h, density, progress), h / 2f,
                PillSwitch.knobRadius(h, density), knob);
        canvas.restore();
    }
}
