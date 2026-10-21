package com.gathin.tvhome;

/**
 * Exact allow-list; monotonic milliseconds, no Android or node-content dependency.
 *
 * A stock Home window seen shortly after our last redirect is a repeat Home press, not a loop:
 * {@link #decide} answers "check again in N ms" instead of dropping it, so stock Home is never
 * left in front. Deferred re-checks are not attempts: the circuit breaker trips on the 4th real
 * redirect inside {@link #BURST_MS} (more than {@link #MAX_IN_BURST}), as before.
 */
final class JuosHomeRedirectPolicy {
    static final long NOW = 0;
    static final long NO = -1;
    static final long DEDUPE_MS = 600;
    static final long BURST_MS = 10000;
    static final int MAX_IN_BURST = 3;

    private long lastAttempt = -1;
    private long burstStart;
    private int attempts;
    private boolean tripped;

    /** {@link #NOW} = redirect now, {@link #NO} = ignore, positive = re-check after that many ms. */
    long decide(boolean enabled, boolean locked, String pkg, String activity, long now) {
        if (!enabled || locked || tripped || now < 0
                || !"com.keylab.speech.view.vidaa".equals(pkg)
                || !"com.ju.secondscreen.HomeActivity".equals(activity)) return NO;
        if (lastAttempt >= 0 && now < lastAttempt) return NO;
        if (lastAttempt >= 0 && now - lastAttempt < DEDUPE_MS) return DEDUPE_MS - (now - lastAttempt);
        if (lastAttempt < 0 || now - burstStart >= BURST_MS) {
            burstStart = now;
            attempts = 0;
        }
        if (attempts >= MAX_IN_BURST) {
            tripped = true;
            return NO;
        }
        attempts++;
        lastAttempt = now;
        return NOW;
    }

    boolean isTripped() { return tripped; }
}
