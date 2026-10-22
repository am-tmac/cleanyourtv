package com.gathin.tvhome;

/** How often Home may call the network for the weather line and the Bing wallpaper. Pure Java. */
final class RefreshPolicy {
    static final long MINUTE = 60_000L;
    static final long WEATHER_INTERVAL = 30 * MINUTE;
    static final long LOCATION_INTERVAL = 6 * 60 * MINUTE;
    static final long CACHE_MAX_AGE = 3 * 60 * MINUTE;
    static final long BING_RETRY = 30 * MINUTE;

    private RefreshPolicy() { }

    private static boolean elapsed(long last, long now, long interval) {
        return last <= 0 || now < last || now - last >= interval;
    }

    static boolean weatherDue(long lastAttempt, long now) {
        return elapsed(lastAttempt, now, WEATHER_INTERVAL);
    }

    static boolean locationDue(long fetched, long now) {
        return elapsed(fetched, now, LOCATION_INTERVAL);
    }

    /** A cached reading is shown only while it is at most three hours old; older, the line hides. */
    static boolean showCached(long fetched, long now) {
        return fetched > 0 && now >= fetched && now - fetched <= CACHE_MAX_AGE;
    }

    /** Once per local day; after a failed attempt, retry at most every 30 minutes. */
    static boolean bingDue(String lastDay, String today, long lastAttempt, long now) {
        if (today != null && today.equals(lastDay)) return false;
        return elapsed(lastAttempt, now, BING_RETRY);
    }
}
