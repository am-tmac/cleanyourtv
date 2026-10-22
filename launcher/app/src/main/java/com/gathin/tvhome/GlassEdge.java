package com.gathin.tvhome;

/**
 * Tigris edge refraction, specular rim and inner glow, adapted from Glass.kt.
 * Copyright (c) 2026 lesnolie. MIT License; see THIRD_PARTY_NOTICES.md.
 * The interior is Plan A (prepared wallpaper + 24% white), not Tigris's milky tone.
 * Pure Java mirror and AGSL share shape-local pixel coordinates and screen-pixel distances.
 */
final class GlassEdge {
    private GlassEdge() { }

    static final String AGSL = """
            uniform shader backdrop;
            uniform float4 rect;
            uniform float radius;
            uniform half tint;
            uniform half synthetic;
            uniform half3 bgColor;
            uniform half3 glowColor;
            uniform half glowAlpha;
            half4 main(float2 p) {
                float2 size = rect.zw - rect.xy;
                float2 delta = p - (rect.xy + rect.zw) * 0.5;
                float2 q = abs(delta) - (size * 0.5 - radius);
                float2 o = max(q, 0.0);
                float sdf = length(o) + min(max(q.x, q.y), 0.0) - radius;
                float a = clamp(0.5 - sdf, 0.0, 1.0);
                if (a <= 0.0) return half4(0.0);
                float zone = min(size.y * 0.26, radius * 1.4);
                // Normal of the same box with a wider corner (radius + zone): inside the edge band it turns
                // smoothly round the corner instead of flipping axis on the diagonal (no mitred seam).
                float soft = min(radius + zone, min(size.x, size.y) * 0.5);
                float2 qs = max(abs(delta) - (size * 0.5 - soft), 0.0);
                float2 n;
                if (qs.x > 0.0 && qs.y > 0.0) n = qs / max(length(qs), 0.001);
                else if (q.x > q.y) n = float2(1.0, 0.0);
                else n = float2(0.0, 1.0);
                if (delta.x < 0.0) n.x = -n.x;
                if (delta.y < 0.0) n.y = -n.y;
                float depth = -sdf;
                float t = clamp(1.0 - depth / zone, 0.0, 1.0);
                float off = t * t * zone * 0.9;
                half4 color = backdrop.eval(p + n * off);
                if (synthetic > 0.5) {
                    half noise = color.g - half(108.0 / 255.0);
                    color.rgb = mix(bgColor, glowColor, color.r * glowAlpha);
                    half lum = dot(color.rgb, half3(0.299, 0.587, 0.114));
                    color.rgb = clamp((half3(lum) + (color.rgb - half3(lum)) * 1.4) * 1.08, 0.0, 1.0);
                    color.rgb = clamp(color.rgb + noise, 0.0, 1.0);
                }
                color.rgb = mix(color.rgb, half3(1.0), tint);
                float facing = -(n.x * 0.45 + n.y * 0.9);
                float light = 0.3 + 0.7 * max(facing, 0.0) + 0.35 * max(-facing, 0.0);
                float rim = clamp(1.0 - depth / 2.2, 0.0, 1.0) * (0.35 + 0.6 * light);
                color.rgb = mix(color.rgb, half3(1.0), half(rim));
                float glow = max(1.0 - depth / zone, 0.0) * 0.16 * max(facing, 0.0);
                color.rgb = mix(color.rgb, half3(1.0), half(glow));
                return half4(color.rgb * half(a), half(a));
            }
            """;

    static float sdf(float x, float y, float w, float h, float radius) {
        float qx = Math.abs(x - w / 2) - (w / 2 - radius);
        float qy = Math.abs(y - h / 2) - (h / 2 - radius);
        return (float) Math.hypot(Math.max(qx, 0), Math.max(qy, 0))
                + Math.min(Math.max(qx, qy), 0) - radius;
    }

    /** Writes the analytic outward normal without allocating. */
    static void normal(float x, float y, float w, float h, float radius, float[] out) {
        float dx = x - w / 2, dy = y - h / 2;
        float qx = Math.abs(dx) - (w / 2 - radius);
        float qy = Math.abs(dy) - (h / 2 - radius);
        float soft = Math.min(radius + zone(h, radius), Math.min(w, h) / 2);
        float sx = Math.max(Math.abs(dx) - (w / 2 - soft), 0);
        float sy = Math.max(Math.abs(dy) - (h / 2 - soft), 0);
        if (sx > 0 && sy > 0) {
            float len = Math.max((float) Math.hypot(sx, sy), .001f);
            out[0] = sx / len;
            out[1] = sy / len;
        } else if (qx > qy) {
            out[0] = 1; out[1] = 0;
        } else {
            out[0] = 0; out[1] = 1;
        }
        if (dx < 0) out[0] = -out[0];
        if (dy < 0) out[1] = -out[1];
    }

    static float zone(float height, float radius) { return Math.min(height * .26f, radius * 1.4f); }
    static float offset(float depth, float zone) {
        float t = clamp(1 - depth / zone);
        return t * t * zone * .9f;
    }
    static float facing(float nx, float ny) { return -(nx * .45f + ny * .9f); }
    static float rim(float depth, float facing) {
        float light = .3f + .7f * Math.max(facing, 0) + .35f * Math.max(-facing, 0);
        return clamp(1 - depth / 2.2f) * (.35f + .6f * light);
    }
    static float glow(float depth, float zone, float facing) {
        return Math.max(1 - depth / zone, 0) * .16f * Math.max(facing, 0);
    }
    static float alpha(float sdf) { return clamp(.5f - sdf); }
    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
}
