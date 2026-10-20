package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Shared on/off switch (App management rows, tile options). Failure modes enumerated first:
 * knob spills outside the track; track not a capsule; wrong colours make "off" invisible on a light
 * focus or "on" look like "off"; knob on the wrong side; animation too slow/instant; size drifts.
 */
public class PillSwitchTest {
    private static final float D = 1.5f;   // a typical TV density

    @Test public void trackIsAbout52By31dpCapsule() {
        assertEquals(52f, PillSwitch.TRACK_W_DP, 0f);
        assertEquals(31f, PillSwitch.TRACK_H_DP, 0f);
        float ratio = PillSwitch.TRACK_W_DP / PillSwitch.TRACK_H_DP;
        assertTrue(ratio > 1.6f && ratio < 1.7f);
        assertEquals(31f * D / 2f, PillSwitch.cornerRadius(31f * D), 1e-4f);
    }

    @Test public void knobIsAbout80PercentOfTrackHeightWithAbout3dpInset() {
        float h = 31f * D;
        float r = PillSwitch.knobRadius(h, D);
        assertEquals(24f * D, 2 * r, 1f * D);
        assertTrue(2 * r / h > 0.75f && 2 * r / h < 0.85f);
        assertEquals(3f * D, PillSwitch.inset(h, D), 0.6f * D);
    }

    @Test public void knobStaysInsideTheTrackAtEveryProgress() {
        float w = 52f * D, h = 31f * D, r = PillSwitch.knobRadius(h, D);
        for (int i = 0; i <= 20; i++) {
            float p = i / 20f;
            float cx = PillSwitch.knobCenterX(w, h, D, p);
            assertTrue("left edge at " + p, cx - r >= 0f);
            assertTrue("right edge at " + p, cx + r <= w);
            assertTrue(h / 2f - r >= 0f && h / 2f + r <= h);
        }
    }

    @Test public void offIsLeftAndOnIsRight() {
        float w = 52f * D, h = 31f * D;
        float left = PillSwitch.knobCenterX(w, h, D, 0f), right = PillSwitch.knobCenterX(w, h, D, 1f);
        assertEquals(h / 2f, left, 1e-3f);
        assertEquals(w - h / 2f, right, 1e-3f);
        assertTrue(left < PillSwitch.knobCenterX(w, h, D, 0.5f) && PillSwitch.knobCenterX(w, h, D, 0.5f) < right);
    }

    @Test public void progressIsClamped() {
        float w = 52f * D, h = 31f * D;
        assertEquals(PillSwitch.knobCenterX(w, h, D, 0f), PillSwitch.knobCenterX(w, h, D, -1f), 0f);
        assertEquals(PillSwitch.knobCenterX(w, h, D, 1f), PillSwitch.knobCenterX(w, h, D, 2f), 0f);
    }

    @Test public void offColoursAreDarkTrackLightOutlineGreyKnob() {
        assertEquals(0x593A3A3A, PillSwitch.OFF_TRACK);
        assertEquals(0xFFBDBDBD, PillSwitch.OFF_STROKE);
        assertEquals(0xFFC4C4C4, PillSwitch.OFF_KNOB);
        assertEquals(1.5f, PillSwitch.STROKE_DP, 0f);
    }

    @Test public void onColoursAreSolidAccentWhiteKnobNoOutline() {
        assertEquals(0xFF8AB4F8, PillSwitch.ON_TRACK);
        assertEquals(0xFFFFFFFF, PillSwitch.ON_KNOB);
        assertEquals(0f, PillSwitch.strokeAlpha(1f), 0f);
        assertEquals(1f, PillSwitch.strokeAlpha(0f), 0f);
    }

    @Test public void coloursBlendWithProgress() {
        assertEquals(PillSwitch.OFF_KNOB, PillSwitch.blend(PillSwitch.OFF_KNOB, PillSwitch.ON_KNOB, 0f));
        assertEquals(PillSwitch.ON_KNOB, PillSwitch.blend(PillSwitch.OFF_KNOB, PillSwitch.ON_KNOB, 1f));
        assertEquals(PillSwitch.ON_TRACK, PillSwitch.blend(PillSwitch.OFF_TRACK, PillSwitch.ON_TRACK, 1f));
    }

    @Test public void slideTakesAbout150ms() {
        assertEquals(150L, PillSwitch.ANIM_MS);
    }
}
