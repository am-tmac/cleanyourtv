package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class JuosHomeRedirectPolicyTest {
    private static final String PKG = "com.keylab.speech.view.vidaa";
    private static final String HOME = "com.ju.secondscreen.HomeActivity";

    @Test public void exactHomeRedirectsOnlyAfterOptIn() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        assertFalse(p.shouldRedirect(false, false, PKG, HOME, 0));
        assertTrue(p.shouldRedirect(true, false, PKG, HOME, 0));
    }
    @Test public void rejectsSamePackageOtherActivity() {
        assertFalse(new JuosHomeRedirectPolicy().shouldRedirect(true, false, PKG, "com.ju.SettingsActivity", 0));
    }
    @Test public void rejectsOwnComponent() {
        assertFalse(new JuosHomeRedirectPolicy().shouldRedirect(true, false, "com.gathin.tvhome", "com.gathin.tvhome.MainActivity", 0));
    }
    @Test public void rejectsMissingFields() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        assertFalse(p.shouldRedirect(true, false, null, HOME, 0));
        assertFalse(p.shouldRedirect(true, false, PKG, null, 0));
        assertFalse(p.shouldRedirect(true, false, "", "", 0));
    }
    @Test public void lockedHomeDoesNotConsumeAttempt() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        assertFalse(p.shouldRedirect(true, true, PKG, HOME, 0));
        assertTrue(p.shouldRedirect(true, false, PKG, HOME, 0));
    }
    @Test public void duplicateIsBlockedUntilExactCooldownBoundary() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        assertTrue(p.shouldRedirect(true, false, PKG, HOME, 0));
        assertFalse(p.shouldRedirect(true, false, PKG, HOME, 0));
        assertFalse(p.shouldRedirect(true, false, PKG, HOME, 1499));
        assertTrue(p.shouldRedirect(true, false, PKG, HOME, 1500));
    }
    @Test public void fourthRapidAttemptTripsUntilNewPolicy() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        assertTrue(p.shouldRedirect(true, false, PKG, HOME, 0));
        assertTrue(p.shouldRedirect(true, false, PKG, HOME, 1500));
        assertTrue(p.shouldRedirect(true, false, PKG, HOME, 3000));
        assertFalse(p.shouldRedirect(true, false, PKG, HOME, 4500));
        assertFalse(p.shouldRedirect(true, false, PKG, HOME, 60000));
    }
    @Test public void burstWindowResetsAtExactBoundary() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        assertTrue(p.shouldRedirect(true, false, PKG, HOME, 0));
        assertTrue(p.shouldRedirect(true, false, PKG, HOME, 1500));
        assertTrue(p.shouldRedirect(true, false, PKG, HOME, 3000));
        assertTrue(p.shouldRedirect(true, false, PKG, HOME, 10000));
    }
    @Test public void clockRollbackFailsClosed() {
        JuosHomeRedirectPolicy p = new JuosHomeRedirectPolicy();
        assertTrue(p.shouldRedirect(true, false, PKG, HOME, 2000));
        assertFalse(p.shouldRedirect(true, false, PKG, HOME, 1999));
        assertFalse(new JuosHomeRedirectPolicy().shouldRedirect(true, false, PKG, HOME, -1));
    }
}
