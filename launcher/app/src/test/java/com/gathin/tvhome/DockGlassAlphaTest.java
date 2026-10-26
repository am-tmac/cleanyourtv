package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Launcher settings' dock opacity also applies to the Vidda glass dock. */
public class DockGlassAlphaTest {
    @Test public void settingsChoicesMapToLayerAlpha() {
        assertEquals(255, DockDecoration.glassAlpha(100));
        assertEquals(179, DockDecoration.glassAlpha(70));
        assertEquals(128, DockDecoration.glassAlpha(50));
        assertEquals(77, DockDecoration.glassAlpha(30));
    }

    @Test public void outOfRangeDrawsOpaque() {
        assertEquals(255, DockDecoration.glassAlpha(0));
        assertEquals(255, DockDecoration.glassAlpha(150));
    }
}
