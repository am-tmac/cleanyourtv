package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/** Small drawn weather icon for the weather line: sun, cloud, fog, rain, snow or thunder (WeatherCodes kinds). */
final class WeatherIconDrawable extends Drawable {
    private static final int SUN = 0xFFFFD54A, SUN_GLOW = 0x55FFC400, CLOUD = 0xFFE3E6EA, RAIN = 0xFF7FB7FF;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bolt = new Path();
    private final int size;
    private int kind;

    WeatherIconDrawable(int sizePx) {
        size = sizePx;
        setBounds(0, 0, sizePx, sizePx);
    }

    void setKind(int kind) {
        if (this.kind == kind) return;
        this.kind = kind;
        invalidateSelf();
    }

    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }

    @Override
    public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        float s = b.width(), x = b.left, y = b.top;
        switch (kind) {
            case WeatherCodes.SUN:
                sun(c, x + s / 2f, y + s / 2f, s * 0.36f);
                break;
            case WeatherCodes.PARTLY:
                sun(c, x + s * 0.62f, y + s * 0.36f, s * 0.24f);
                cloud(c, x, y + s * 0.12f, s * 0.85f);
                break;
            case WeatherCodes.FOG:
                paint.setColor(CLOUD);
                for (int i = 0; i < 3; i++) {
                    float ly = y + s * (0.3f + 0.2f * i);
                    c.drawRoundRect(x + s * 0.1f, ly - s * 0.05f, x + s * 0.9f, ly + s * 0.05f, s * 0.05f, s * 0.05f, paint);
                }
                break;
            case WeatherCodes.RAIN:
            case WeatherCodes.SNOW:
            case WeatherCodes.THUNDER:
                cloud(c, x, y - s * 0.08f, s);
                if (kind == WeatherCodes.THUNDER) {
                    paint.setColor(SUN);
                    bolt.reset();
                    bolt.moveTo(x + s * 0.55f, y + s * 0.55f);
                    bolt.lineTo(x + s * 0.4f, y + s * 0.8f);
                    bolt.lineTo(x + s * 0.52f, y + s * 0.8f);
                    bolt.lineTo(x + s * 0.45f, y + s);
                    bolt.lineTo(x + s * 0.68f, y + s * 0.72f);
                    bolt.lineTo(x + s * 0.55f, y + s * 0.72f);
                    bolt.close();
                    c.drawPath(bolt, paint);
                } else {
                    paint.setColor(kind == WeatherCodes.SNOW ? 0xFFFFFFFF : RAIN);
                    for (int i = 0; i < 3; i++) {
                        c.drawCircle(x + s * (0.3f + 0.2f * i), y + s * (0.8f + (i % 2) * 0.1f), s * 0.06f, paint);
                    }
                }
                break;
            case WeatherCodes.CLOUD:
            default:
                cloud(c, x, y, s);
                break;
        }
    }

    private void sun(Canvas c, float cx, float cy, float r) {
        paint.setColor(SUN_GLOW);
        c.drawCircle(cx, cy, r * 1.35f, paint);
        paint.setColor(SUN);
        c.drawCircle(cx, cy, r, paint);
    }

    private void cloud(Canvas c, float x, float y, float s) {
        paint.setColor(CLOUD);
        c.drawCircle(x + s * 0.36f, y + s * 0.55f, s * 0.2f, paint);
        c.drawCircle(x + s * 0.56f, y + s * 0.46f, s * 0.24f, paint);
        c.drawRoundRect(x + s * 0.14f, y + s * 0.52f, x + s * 0.88f, y + s * 0.74f, s * 0.11f, s * 0.11f, paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override @SuppressWarnings("deprecation") public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
