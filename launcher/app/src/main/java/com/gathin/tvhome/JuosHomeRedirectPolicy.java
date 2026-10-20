package com.gathin.tvhome;

/** Exact allow-list; monotonic milliseconds, no Android or node-content dependency. */
final class JuosHomeRedirectPolicy {
    private long lastAttempt = -1;
    private long burstStart;
    private int attempts;
    private boolean tripped;

    boolean shouldRedirect(boolean enabled, boolean locked, String pkg, String activity, long now) {
        if (!enabled || locked || tripped || now < 0
                || !"com.keylab.speech.view.vidaa".equals(pkg)
                || !"com.ju.secondscreen.HomeActivity".equals(activity)) return false;
        if (lastAttempt >= 0 && (now < lastAttempt || now - lastAttempt < 1500)) return false;
        if (lastAttempt < 0 || now - burstStart >= 10000) {
            burstStart = now;
            attempts = 0;
        }
        if (attempts >= 3) {
            tripped = true;
            return false;
        }
        attempts++;
        lastAttempt = now;
        return true;
    }

    boolean isTripped() { return tripped; }
}
