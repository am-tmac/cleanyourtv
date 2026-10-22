package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.RuntimeShader;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

/**
 * Light glass (launcher settings, Glass dock): a pre-blurred copy of the wallpaper behind the shape,
 * an adaptive 10–24% white tint and GPU Tigris edges on API 33+. The exact Plan A edge treatment is retained
 * as fallback. The blur is computed once per
 * wallpaper (WallpaperPlayer, 1/8 size); drawing is a scaled bitmap shader, nothing is allocated per frame.
 * Without a wallpaper API 33+ uses a neutral blurred glow mask with animated colour uniforms.
 */
final class GlassPainter {
    static final int TINT = LightGlassTone.TINT;
    /** Light translucent fill, used when there is no wallpaper to blur. */
    static final int FALLBACK = LightGlassTone.FALLBACK;

    private final float density;
    private final Paint backdrop = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix backdropMatrix = new Matrix();
    private final Matrix highlightMatrix = new Matrix();
    private final RectF tmp = new RectF();
    private BitmapShader backdropShader;
    private RuntimeGlass runtime;
    private boolean inputChanged = true;
    private boolean synthetic;
    private float tint = .24f;
    private int backgroundColor, glowColor;
    private float glowAlpha = -1;
    private float maskDockMean;
    private float scaleX = 1f, scaleY = 1f;
    private float lastOriginX = Float.NaN, lastOriginY = Float.NaN, lastTop = Float.NaN, lastSpan = Float.NaN;

    GlassPainter(float density) {
        this.density = density;
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                runtime = new RuntimeGlass();
            } catch (RuntimeException failure) {
                Log.w("TvHome.Glass", "RuntimeShader unavailable; using Plan A", failure);
            }
        }
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(2 * density);
        highlight.setStyle(Paint.Style.STROKE);
        highlight.setStrokeWidth(2 * density);
        // Unit gradient, mapped onto each shape's top edge by highlightMatrix: rgba(255,255,255,.6) fading out.
        highlight.setShader(new LinearGradient(0, 0, 0, 1, 0x99FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(0x52FFFFFF);
        shadow.setStyle(Paint.Style.STROKE);
        shadow.setStrokeWidth(8 * density);
    }

    /** The blurred wallpaper covering a screen of screenW x screenH; null for the plain translucent fill. */
    void setBackdrop(Bitmap blurred, int screenW, int screenH) {
        if (blurred == null || blurred.getWidth() <= 0 || blurred.getHeight() <= 0) {
            backdropShader = null;
            backdrop.setShader(null);
            return;
        }
        synthetic = false;
        int[] pixels = new int[blurred.getWidth() * blurred.getHeight()];
        blurred.getPixels(pixels, 0, blurred.getWidth(), 0, 0, blurred.getWidth(), blurred.getHeight());
        tint = LightGlassTone.adaptiveTint(LightGlassTone.dockLuminance(pixels, blurred.getWidth(), blurred.getHeight()));
        inputChanged = true;
        backdropShader = new BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        backdrop.setShader(backdropShader);
        scaleX = screenW / (float) blurred.getWidth();
        scaleY = screenH / (float) blurred.getHeight();
        lastOriginX = Float.NaN;
    }

    /** Build only the neutral blurred mask. Animated colour/alpha stay in uniforms (no bitmap uploads). */
    void setSyntheticBackdrop(int bg, AmbientGlow geometry, int screenW, int screenH) {
        if (Build.VERSION.SDK_INT < 33 || runtime == null) return;
        int w = Math.max(16, screenW / 8), h = Math.max(9, screenH / 8);
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int a = Math.round(255 * geometry.mask((x + .5f) / w, (y + .5f) / h, screenW, screenH));
            pixels[y*w+x] = 0xFF000000 | a * 0x010101;
        }
        BackdropBlur.blur(pixels, w, h, 5, 3);
        maskDockMean = LightGlassTone.dockLuminance(pixels, w, h);
        // Encode prepare's stable dither in a separate channel; red remains the neutral mask.
        int[] dither = new int[pixels.length];
        java.util.Arrays.fill(dither, 0xFF646464);
        LightGlassTone.prepare(dither);
        for (int i = 0; i < pixels.length; i++)
            pixels[i] = (pixels[i] & 0xFFFF00FF) | (dither[i] & 0x0000FF00);
        Bitmap mask = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
        setBackdrop(mask, screenW, screenH);
        backgroundColor = bg;
        synthetic = true;
    }

    /** Called before drawing, reading the ambient view's actual animated alpha. */
    boolean updateSynthetic(int color, float alpha) {
        if (!synthetic || (color == glowColor && alpha == glowAlpha)) return false;
        glowColor = color; glowAlpha = alpha;
        // Cheap colour approximation: composition at the blurred dock's mean mask coverage.
        tint = LightGlassTone.adaptiveTint(LightGlassTone.luminance(AmbientGlow.prepared(
                AmbientGlow.composite(backgroundColor, color, maskDockMean, alpha))));
        return true;
    }

    /**
     * @param originX where the canvas's 0,0 sits on screen (view's location in the window)
     * @param shadowWidth the dock's soft drop shadow, rgba(0,0,0,.25) about 10dp down, kept inside 0..shadowWidth
     *                    so the grid's clip never cuts it with a hard edge; 0 for no shadow
     */
    void draw(Canvas c, RectF r, float radius, float originX, float originY, float shadowWidth) {
        if (shadowWidth > 0) {
            for (int i = 0; i < 3; i++) {
                float grow = (4 + 8 * i) * density;
                tmp.set(Math.max(0, r.left - grow), r.top - grow + 10 * density,
                        Math.min(shadowWidth, r.right + grow), r.bottom + grow + 10 * density);
                shadow.setColor((0x16 - 6 * i) << 24);
                c.drawRoundRect(tmp, radius + grow, radius + grow, shadow);
            }
        }
        if (backdropShader != null) {
            if (originX != lastOriginX || originY != lastOriginY) {
                backdropMatrix.setScale(scaleX, scaleY);
                backdropMatrix.postTranslate(-originX, -originY);
                backdropShader.setLocalMatrix(backdropMatrix);
                inputChanged = true;
                lastOriginX = originX;
                lastOriginY = originY;
            }
            if (Build.VERSION.SDK_INT >= 33 && runtime != null && c.isHardwareAccelerated()) {
                try {
                    // setInputShader snapshots its child: rebind after any origin/matrix change.
                    // Dock and pills share this painter, so both geometry and origin are checked per draw.
                    runtime.draw(c, r, radius, backdropShader, inputChanged, tint, synthetic,
                            backgroundColor, glowColor, glowAlpha);
                    inputChanged = false;
                    return;
                } catch (RuntimeException failure) {
                    runtime = null;
                    Log.w("TvHome.Glass", "RuntimeShader draw failed; using Plan A", failure);
                }
            }
            if (synthetic) {
                // The mask is data, not a colour image. Software/failed GPU draws retain the old fallback.
                fill.setColor(FALLBACK);
            } else {
                c.drawRoundRect(r, radius, radius, backdrop);
                fill.setColor((Math.round(tint * 255) << 24) | 0xFFFFFF);
            }
        } else {
            fill.setColor(FALLBACK);
        }
        c.drawRoundRect(r, radius, radius, fill);
        // Soft inner glow: inset 0 0 30px rgba(255,255,255,.08).
        // Several faint strokes fade inward instead of making a second solid ring on the pills.
        for (int i = 0; i < 5; i++) {
            float g = (2 + 2 * i) * density;
            glow.setColor(((20 - 4 * i) << 24) | 0x00FFFFFF);
            tmp.set(r.left + g, r.top + g, r.right - g, r.bottom - g);
            c.drawRoundRect(tmp, Math.max(0, radius - g), Math.max(0, radius - g), glow);
        }
        // Top highlight: inset 0 2px 0 rgba(255,255,255,.6), fading down the rounded corners.
        float span = Math.max(4 * density, Math.min(radius, r.height() / 2f));
        if (r.top != lastTop || span != lastSpan) {
            highlightMatrix.setScale(1f, span);
            highlightMatrix.postTranslate(0, r.top);
            highlight.getShader().setLocalMatrix(highlightMatrix);
            lastTop = r.top;
            lastSpan = span;
        }
        float rh = rim.getStrokeWidth() / 2f;
        tmp.set(r.left + rh, r.top + rh, r.right - rh, r.bottom - rh);
        c.drawRoundRect(tmp, radius - rh, radius - rh, rim);
        // Use the same contour as the hairline rim: one bright top edge, no double outline.
        c.drawRoundRect(tmp, radius - rh, radius - rh, highlight);
    }

    /** API isolation also keeps Android 26-32 class loading on the unchanged Plan A path. */
    @RequiresApi(33)
    private static final class RuntimeGlass {
        private final RuntimeShader shader = new RuntimeShader(GlassEdge.AGSL);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF lastRect = new RectF();
        private float lastRadius = Float.NaN;

        RuntimeGlass() {
            paint.setShader(shader);
            shader.setFloatUniform("tint", ((TINT >>> 24) & 255) / 255f);
        }

        void draw(Canvas canvas, RectF r, float radius, BitmapShader input, boolean inputChanged,
                  float tint, boolean synthetic, int bg, int glow, float alpha) {
            shader.setFloatUniform("tint", tint);
            shader.setFloatUniform("synthetic", synthetic ? 1f : 0f);
            shader.setFloatUniform("bgColor", ((bg >> 16) & 255) / 255f, ((bg >> 8) & 255) / 255f, (bg & 255) / 255f);
            shader.setFloatUniform("glowColor", ((glow >> 16) & 255) / 255f, ((glow >> 8) & 255) / 255f, (glow & 255) / 255f);
            shader.setFloatUniform("glowAlpha", Math.max(0, alpha));
            if (inputChanged) shader.setInputShader("backdrop", input);
            if (!lastRect.equals(r) || radius != lastRadius) {
                // Fixed-arity overloads: no varargs arrays or other Java allocations per frame.
                shader.setFloatUniform("rect", r.left, r.top, r.right, r.bottom);
                shader.setFloatUniform("radius", radius);
                lastRect.set(r);
                lastRadius = radius;
            }
            // SDF supplies the contour and 1px AA; include the outside half of the coverage band.
            canvas.drawRect(r.left - 1, r.top - 1, r.right + 1, r.bottom + 1, paint);
        }
    }

    /**
     * The backdrop for a wallpaper bitmap shown CENTER_CROP on a screenW x screenH screen: 1/8 size, blurred
     * (about 40px at full size), saturation 140%, brightness 108%, and subtle static dither.
     * The wallpaper dim is deliberately excluded: glass stays light even over a dimmed image.
     */
    static Bitmap makeBackdrop(Bitmap source, int screenW, int screenH) {
        if (source == null || source.isRecycled() || screenW <= 0 || screenH <= 0) return null;
        try {
            int w = Math.max(16, screenW / 8), h = Math.max(9, screenH / 8);
            Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(small);
            float scale = Math.max(w / (float) source.getWidth(), h / (float) source.getHeight());
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            m.postTranslate((w - source.getWidth() * scale) / 2f, (h - source.getHeight() * scale) / 2f);
            canvas.drawBitmap(source, m, new Paint(Paint.FILTER_BITMAP_FLAG));
            int[] px = new int[w * h];
            small.getPixels(px, 0, w, 0, 0, w, h);
            BackdropBlur.blur(px, w, h, 5, 3);
            LightGlassTone.prepare(px);
            small.setPixels(px, 0, w, 0, 0, w, h);
            return small;
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }
}
