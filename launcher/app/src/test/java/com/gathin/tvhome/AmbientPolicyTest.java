package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class AmbientPolicyTest {
    // Failure cases: a selected but not loaded image must not disable the original glow;
    // any displayed image must disable it, independent of source, glass or Android version.
    @Test public void noImageKeepsOriginalAmbient() {
        assertTrue(AmbientPolicy.allowed(false));
    }

    @Test public void displayedImageDisablesAmbient() {
        assertFalse(AmbientPolicy.allowed(true));
    }

    @Test public void arrivalAndRemovalFollowActualImageState() {
        for (boolean showing : new boolean[]{false, true, true, false}) {
            assertEquals(!showing, AmbientPolicy.allowed(showing));
        }
    }
}
