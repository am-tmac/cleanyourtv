package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class JuosHomeRedirectPolicyTest {
    private static final String PKG = "com.keylab.speech.view.vidaa";
    private static final String HOME = "com.ju.secondscreen.HomeActivity";
    private static final long NOW = JuosHomeRedirectPolicy.NOW;
    private static final long NO = JuosHomeRedirectPolicy.NO;

    private static long d(JuosHomeRedirectPolicy p, long t) { return p.decide(true, false, PKG, HOME, t); }

    @Test public void exactHomeRedirectsOnlyAfterOptIn() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        assertEquals(NO, p.decide(false, false, PKG, HOME, 0));
        assertEquals(NOW, d(p, 0));
    }
    @Test public void rejectsSamePackageOtherActivity() {
        assertEquals(NO, new JuosHomeRedirectPolicy().decide(true, false, PKG, "com.ju.SettingsActivity", 0));
    }
    @Test public void rejectsOwnComponent() {
        assertEquals(NO, new JuosHomeRedirectPolicy().decide(true, false, "com.gathin.tvhome", "com.gathin.tvhome.MainActivity", 0));
    }
    @Test public void rejectsMissingFields() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        assertEquals(NO, p.decide(true, false, null, HOME, 0));
        assertEquals(NO, p.decide(true, false, PKG, null, 0));
        assertEquals(NO, p.decide(true, false, "", "", 0));
    }
    @Test public void lockedHomeDoesNotConsumeAttempt() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        assertEquals(NO, p.decide(true, true, PKG, HOME, 0));
        assertEquals(NOW, d(p, 0));
    }

    /** Regression (.5 on device): a 2nd/3rd quick Home press was dropped and left stock Home in front. */
    @Test public void quickRepeatPressIsDeferredNotDropped() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        assertEquals(NOW, d(p, 0));
        long wait = d(p, 100);
        assertEquals(JuosHomeRedirectPolicy.DEDUPE_MS - 100, wait);
        // The retry fires at the boundary while stock Home is still in front: redirect then.
        assertEquals(NOW, d(p, JuosHomeRedirectPolicy.DEDUPE_MS));
    }
    @Test public void deferredCheckDoesNotCountAsAttempt() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        assertEquals(NOW, d(p, 0));
        for (int i = 1; i < 50; i++) assertTrue(d(p, i) > 0);
        assertFalse(p.isTripped());
    }
    @Test public void pressesSpacedLikeAHumanAlwaysRedirect() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        long gap = JuosHomeRedirectPolicy.DEDUPE_MS + 100;
        assertEquals(NOW, d(p, 0));
        assertEquals(NOW, d(p, gap));
        assertEquals(NOW, d(p, 2 * gap));
        assertFalse(p.isTripped());
    }
    /** Kept on purpose: the 4th real redirect inside 10 s switches the redirect off. */
    @Test public void fourthRedirectWithinTenSecondsTrips() {
        assertEquals(3, JuosHomeRedirectPolicy.MAX_IN_BURST);
        assertEquals(10000, JuosHomeRedirectPolicy.BURST_MS);
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        long t = 0;
        for (int i = 0; i < 3; i++) {
            long r = d(p, t);
            if (r > 0) { t += r; r = d(p, t); }
            assertEquals(NOW, r);
            t += 350;
        }
        assertFalse(p.isTripped());
        long r = d(p, t);
        if (r > 0) { t += r; r = d(p, t); }
        assertEquals(NO, r);
        assertTrue(p.isTripped());
    }
    @Test public void doubleAndTriplePressStillRedirectEveryTime() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        assertEquals(NOW, d(p, 0));
        long wait = d(p, 300);
        assertTrue(wait > 0);
        assertEquals(NOW, d(p, 300 + wait));
        assertFalse(p.isTripped());
    }
    @Test public void sustainedLoopTripsUntilNewPolicy() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        long t = 0;
        for (int i = 0; i < JuosHomeRedirectPolicy.MAX_IN_BURST; i++) {
            assertEquals(NOW, d(p, t));
            t += JuosHomeRedirectPolicy.DEDUPE_MS;
        }
        assertEquals(NO, d(p, t));
        assertTrue(p.isTripped());
        assertEquals(NO, d(p, t + 60000));
    }
    @Test public void burstWindowResets() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        long t = 0;
        for (int i = 0; i < JuosHomeRedirectPolicy.MAX_IN_BURST; i++) {
            assertEquals(NOW, d(p, t));
            t += JuosHomeRedirectPolicy.DEDUPE_MS;
        }
        assertEquals(NOW, d(p, JuosHomeRedirectPolicy.BURST_MS));
        assertFalse(p.isTripped());
    }
    @Test public void clockRollbackFailsClosed() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        assertEquals(NOW, d(p, 2000));
        assertEquals(NO, d(p, 1999));
        assertEquals(NO, new JuosHomeRedirectPolicy().decide(true, false, PKG, HOME, -1));
    }
}
