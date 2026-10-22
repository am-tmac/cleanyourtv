package com.gathin.tvhome;

/** Glass backdrop colour preparation, performed once per wallpaper. Pure Java. */
final class LightGlassTone {
    /** rgba(255,255,255,.24), composited by GlassPainter over this backdrop. */
    static final int TINT = 0x3DFFFFFF;
    /** rgba(255,255,255,.12), for Home without a wallpaper or on older Android versions. */
    static final int FALLBACK = 0x1FFFFFFF;

    private LightGlassTone() { }

    static float adaptiveTint(float luminance) {
        float t = Math.max(0, Math.min(1, (luminance - .15f) / .40f));
        return .10f + .14f * t * t * (3 - 2 * t);
    }

    static float luminance(int color) {
        return (.299f * ((color >> 16) & 255) + .587f * ((color >> 8) & 255)
                + .114f * (color & 255)) / 255f;
    }

    static float luminance(int[] pixels) {
        double sum = 0;
        for (int color : pixels) sum += luminance(color);
        return pixels.length == 0 ? 0 : (float) (sum / pixels.length);
    }

    // Dock region at the reference layout; pills deliberately share this luminance.
    static float dockLuminance(int[] pixels, int w, int h) {
        double sum = 0; int count = 0;
        for (int y = h * 692 / 1080; y < h * 950 / 1080; y++) {
            for (int x = w * 117 / 1920; x < w * 1803 / 1920; x++) {
                sum += luminance(pixels[y*w+x]); count++;
            }
        }
        return count == 0 ? 0 : (float) (sum / count);
    }

    static void prepare(int[] pixels) {
        // Wallpaper dim belongs to the image behind the glass, never to its bright backdrop.
        BackdropBlur.tone(pixels, 1.4f, 1.08f, 0);
        for (int i = 0; i < pixels.length; i++) {
            // Stable, allocation-free monochrome dither, +/-2 levels (1.6% peak-to-peak).
            // Hash the pixel index rather than using frame-time randomness: no shimmer on redraw.
            int hash = i * 0x45d9f3b;
            hash = (hash ^ (hash >>> 16)) * 0x45d9f3b;
            int noise = ((hash ^ (hash >>> 16)) & 0x7fffffff) % 5 - 2;
            int c = pixels[i];
            pixels[i] = 0xFF000000 | (clamp(((c >> 16) & 255) + noise) << 16)
                    | (clamp(((c >> 8) & 255) + noise) << 8) | clamp((c & 255) + noise);
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
