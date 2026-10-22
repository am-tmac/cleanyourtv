package com.gathin.tvhome;

/**
 * CPU blur and tone for the glass dock's backdrop: run once per wallpaper on a small (1/8 screen) copy,
 * never per frame. Repeated box blurs approximate a Gaussian. Pixels are opaque ARGB. Pure Java.
 */
final class BackdropBlur {
    private BackdropBlur() { }

    static void blur(int[] px, int w, int h, int radius, int passes) {
        if (radius <= 0 || w <= 0 || h <= 0 || px.length < w * h) return;
        int[] tmp = new int[w * h];
        for (int p = 0; p < passes; p++) {
            box(px, tmp, w, h, radius, true);
            box(tmp, px, w, h, radius, false);
        }
    }

    /** One box pass along rows (horizontal) or columns, edges clamped. */
    private static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int len = horizontal ? w : h, lines = horizontal ? h : w;
        int step = horizontal ? 1 : w, lineStep = horizontal ? w : 1;
        int div = 2 * r + 1, half = div / 2;
        for (int line = 0; line < lines; line++) {
            int base = line * lineStep;
            int sr = 0, sg = 0, sb = 0;
            for (int k = -r; k <= r; k++) {
                int c = in[base + clamp(k, len) * step];
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
            }
            for (int x = 0; x < len; x++) {
                out[base + x * step] = 0xFF000000 | (((sr + half) / div) << 16) | (((sg + half) / div) << 8)
                        | ((sb + half) / div);
                int add = in[base + clamp(x + r + 1, len) * step];
                int sub = in[base + clamp(x - r, len) * step];
                sr += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                sb += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    private static int clamp(int i, int len) {
        return i < 0 ? 0 : i >= len ? len - 1 : i;
    }

    /** Saturation (1 = unchanged), brightness factor, then the wallpaper's dim in percent. */
    static void tone(int[] px, float saturation, float brightness, int dimPercent) {
        float keep = (100 - Math.max(0, Math.min(100, dimPercent))) / 100f;
        float f = brightness * keep;
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            float r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            float lum = 0.299f * r + 0.587f * g + 0.114f * b;
            px[i] = 0xFF000000 | (channel((lum + (r - lum) * saturation) * f) << 16)
                    | (channel((lum + (g - lum) * saturation) * f) << 8) | channel((lum + (b - lum) * saturation) * f);
        }
    }

    private static int channel(float v) {
        int i = Math.round(v);
        return i < 0 ? 0 : i > 255 ? 255 : i;
    }
}
