package com.gathin.tvhome;

/**
 * Geometry and colours of the shared on/off switch ({@link PillSwitchView}), pure Java for JVM tests.
 * A capsule track about 52×31dp; a round knob about 80% of the track height sits fully inside it with
 * about 3dp all round. Off: dark translucent track, light grey outline, grey knob on the left.
 * On: solid accent track without outline, white knob on the right. The knob slides in about 150ms.
 */
final class PillSwitch {
    static final float TRACK_W_DP = 52f, TRACK_H_DP = 31f, STROKE_DP = 1.5f;
    static final float KNOB_FRACTION = 0.8f;
    /** #3A3A3A at 35% opacity. */
    static final int OFF_TRACK = 0x593A3A3A;
    static final int OFF_STROKE = 0xFFBDBDBD;
    static final int OFF_KNOB = 0xFFC4C4C4;
    /** One accent for every switch in the launcher. */
    static final int ON_TRACK = 0xFF8AB4F8;
    static final int ON_KNOB = 0xFFFFFFFF;
    static final long ANIM_MS = 150L;

    private PillSwitch() { }

    static float cornerRadius(float trackHeight) { return trackHeight / 2f; }

    static float knobRadius(float trackHeight, float density) { return trackHeight * KNOB_FRACTION / 2f; }

    /** Gap between the knob and the track edge. */
    static float inset(float trackHeight, float density) {
        return trackHeight / 2f - knobRadius(trackHeight, density);
    }

    /** Knob centre: h/2 when off (progress 0), w - h/2 when on (progress 1). */
    static float knobCenterX(float trackWidth, float trackHeight, float density, float progress) {
        float p = clamp(progress);
        float left = trackHeight / 2f, right = trackWidth - trackHeight / 2f;
        return left + (right - left) * p;
    }

    /** The outline fades out as the switch turns on. */
    static float strokeAlpha(float progress) { return 1f - clamp(progress); }

    /** ARGB blend, channel by channel. */
    static int blend(int from, int to, float progress) {
        float p = clamp(progress);
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (from >>> shift) & 0xFF, b = (to >>> shift) & 0xFF;
            result |= (Math.round(a + (b - a) * p) & 0xFF) << shift;
        }
        return result;
    }

    private static float clamp(float p) { return p < 0f ? 0f : p > 1f ? 1f : p; }
}
