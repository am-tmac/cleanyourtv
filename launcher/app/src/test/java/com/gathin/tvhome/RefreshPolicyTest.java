package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RefreshPolicyTest {
    private static final long MIN = 60_000L, HOUR = 60 * MIN;
    private static final long NOW = 1_800_000_000_000L;

    @Test public void weatherAtMostEveryThirtyMinutes() {
        assertTrue(RefreshPolicy.weatherDue(0, NOW));
        assertFalse(RefreshPolicy.weatherDue(NOW - 29 * MIN, NOW));
        assertTrue(RefreshPolicy.weatherDue(NOW - 30 * MIN, NOW));
        assertTrue(RefreshPolicy.weatherDue(NOW + 5 * MIN, NOW));   // clock went back: don't stall for days
    }

    @Test public void failedWeatherRetriesAfterTwoMinutes() {
        // last attempt got no reading (fetched before it): retry after 2 minutes, not 30
        assertFalse(RefreshPolicy.weatherDue(NOW - MIN, NOW - HOUR, NOW));
        assertTrue(RefreshPolicy.weatherDue(NOW - 2 * MIN, NOW - HOUR, NOW));
        assertTrue(RefreshPolicy.weatherDue(NOW - 2 * MIN, 0, NOW));
        // last attempt succeeded: the usual 30 minutes
        assertFalse(RefreshPolicy.weatherDue(NOW - 10 * MIN, NOW - 10 * MIN + 1500, NOW));
        assertTrue(RefreshPolicy.weatherDue(NOW - 30 * MIN, NOW - 30 * MIN + 1500, NOW));
        assertTrue(RefreshPolicy.weatherDue(0, 0, NOW));
    }

    @Test public void locationCachedForSixHours() {
        assertTrue(RefreshPolicy.locationDue(0, NOW));
        assertFalse(RefreshPolicy.locationDue(NOW - 5 * HOUR, NOW));
        assertTrue(RefreshPolicy.locationDue(NOW - 6 * HOUR, NOW));
    }

    @Test public void cachedWeatherShownForThreeHoursOnly() {
        assertFalse(RefreshPolicy.showCached(0, NOW));
        assertTrue(RefreshPolicy.showCached(NOW - 2 * HOUR, NOW));
        assertFalse(RefreshPolicy.showCached(NOW - 3 * HOUR - 1, NOW));
        assertFalse(RefreshPolicy.showCached(NOW + HOUR, NOW));
    }

    @Test public void bingOncePerDayWithThirtyMinuteRetry() {
        assertTrue(RefreshPolicy.bingDue("", "20261022", 0, NOW));
        assertFalse(RefreshPolicy.bingDue("20261022", "20261022", 0, NOW));
        assertTrue(RefreshPolicy.bingDue("20261021", "20261022", NOW - 31 * MIN, NOW));
        assertFalse(RefreshPolicy.bingDue("20261021", "20261022", NOW - 10 * MIN, NOW));
        assertTrue(RefreshPolicy.bingDue(null, "20261022", NOW - 10 * HOUR, NOW));
    }
}
