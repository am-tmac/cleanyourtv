package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/** User-authorized, exact JUOS Home window redirect. Does not inspect view nodes or keys. */
public final class JuosHomeRedirectService extends AccessibilityService {
    static final String PREFS = "juos_home_redirect";
    static final String ENABLED = "enabled";
    static final String STOPPED = "safety_stopped";
    private static final String HOME_PKG = "com.keylab.speech.view.vidaa";
    private static final String HOME_ACTIVITY = "com.ju.secondscreen.HomeActivity";
    private JuosHomeRedirectPolicy policy = new JuosHomeRedirectPolicy();
    private SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Last window-state change we saw; a deferred re-check only acts if stock Home is still on top. */
    private String foregroundPkg, foregroundActivity;
    private final Runnable recheck = () -> evaluate(foregroundPkg, foregroundActivity);
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (ENABLED.equals(key)) {
            handler.removeCallbacks(recheck);
            policy = new JuosHomeRedirectPolicy();
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || preferences == null) return;
        CharSequence pkg = event.getPackageName();
        CharSequence activity = event.getClassName();
        foregroundPkg = pkg == null ? null : pkg.toString();
        foregroundActivity = activity == null ? null : activity.toString();
        evaluate(foregroundPkg, foregroundActivity);
    }

    private void evaluate(String pkg, String activity) {
        if (preferences == null) return;
        handler.removeCallbacks(recheck);
        // A re-check fires after the user may have moved on; only act if stock Home is still in front.
        if (!HOME_PKG.equals(pkg) || !HOME_ACTIVITY.equals(activity)) return;
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        // Unknown lock state is not permission to move the foreground.
        boolean locked = keyguard == null || keyguard.isKeyguardLocked() || keyguard.isDeviceLocked();
        long decision = policy.decide(preferences.getBoolean(ENABLED, false), locked, pkg, activity,
                SystemClock.elapsedRealtime());
        if (policy.isTripped()) {
            stopForSafety();
            return;
        }
        if (decision > 0) {
            handler.postDelayed(recheck, decision);
            return;
        }
        if (decision != JuosHomeRedirectPolicy.NOW) return;
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (ActivityNotFoundException | SecurityException failure) {
            stopForSafety();
        }
    }

    private void stopForSafety() {
        handler.removeCallbacks(recheck);
        // Persist off across process restarts. Only a deliberate settings toggle can re-arm it.
        preferences.edit().putBoolean(ENABLED, false).putBoolean(STOPPED, true).apply();
        Toast.makeText(this, R.string.juos_redirect_stopped, Toast.LENGTH_LONG).show();
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        handler.removeCallbacks(recheck);
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(listener);
        super.onDestroy();
    }
}
