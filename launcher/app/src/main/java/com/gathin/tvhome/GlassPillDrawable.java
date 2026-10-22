package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

/** A top-bar pill in light glass while idle; focused or activated it draws the usual pill_bg states. */
final class GlassPillDrawable extends Drawable {
    private final View host;
    private final GlassPainter painter;
    private final Drawable states;
    private final int[] location = new int[2];
    private final RectF rect = new RectF();

    GlassPillDrawable(View host, GlassPainter painter, Drawable states) {
        this.host = host;
        this.painter = painter;
        this.states = states.mutate();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int[] state = getState();
        for (int s : state) {
            if (s == android.R.attr.state_focused || s == android.R.attr.state_activated) {
                states.draw(canvas);
                return;
            }
        }
        Rect b = getBounds();
        rect.set(b);
        host.getLocationInWindow(location);
        painter.draw(canvas, rect, rect.height() / 2f, location[0], location[1], 0);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        states.setBounds(bounds);
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(@NonNull int[] state) {
        states.setState(state);
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
        states.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        states.setColorFilter(colorFilter);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
