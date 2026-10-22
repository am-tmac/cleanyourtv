package com.gathin.tvhome;

/** Shared ambient geometry and synthetic backdrop mirror. Coordinates are screen fractions. */
final class AmbientGlow {
    final float centerX, centerY, radius, middle;
    final int innerAlpha, middleAlpha;

    AmbientGlow(boolean cinematic, boolean rtl) {
        centerX = cinematic ? (rtl ? .20f : .80f) : .32f;
        centerY = cinematic ? .06f : .42f;
        radius = cinematic ? .78f : .62f;
        middle = cinematic ? .5f : .45f;
        innerAlpha = cinematic ? 0xA6 : 0x5C;
        middleAlpha = cinematic ? 0x47 : 0x26;
    }

    float mask(float x, float y, int w, int h) {
        float t = (float) Math.hypot(x - centerX, (y - centerY) * h / w) / radius;
        return t < middle ? (innerAlpha + (middleAlpha - innerAlpha) * t / middle) / 255f
                : Math.max(0, middleAlpha * (1 - t) / (1 - middle)) / 255f;
    }

    static int composite(int bg, int glow, float mask, float alpha) {
        float a = Math.max(0, Math.min(1, mask * alpha));
        int out = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            float b = (bg >> shift) & 255, g = (glow >> shift) & 255;
            out |= Math.round(b + (g - b) * a) << shift;
        }
        return out;
    }

    /** Same saturation/brightness as LightGlassTone.prepare, evaluated after mask composition on GPU. */
    static int prepared(int color) {
        float r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        float l = .299f * r + .587f * g + .114f * b;
        return 0xFF000000 | (channel((l + (r-l)*1.4f)*1.08f) << 16)
                | (channel((l + (g-l)*1.4f)*1.08f) << 8) | channel((l + (b-l)*1.4f)*1.08f);
    }
    private static int channel(float v) { return Math.max(0, Math.min(255, Math.round(v))); }
}
