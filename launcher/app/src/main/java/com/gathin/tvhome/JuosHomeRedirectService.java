package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/** User-authorized, exact JUOS Home window redirect. Does not inspect view nodes or keys. */
public final class JuosHomeRedirectService extends AccessibilityService {
    static final String PREFS = "juos_home_redirect";
    static final String ENABLED = "enabled";
    static final String STOPPED = "safety_stopped";
    private JuosHomeRedirectPolicy policy = new JuosHomeRedirectPolicy();
    private SharedPreferences preferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (ENABLED.equals(key)) policy = new JuosHomeRedirectPolicy();
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
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        // Unknown lock state is not permission to move the foreground.
        boolean locked = keyguard == null || keyguard.isKeyguardLocked() || keyguard.isDeviceLocked();
        boolean redirect = policy.shouldRedirect(preferences.getBoolean(ENABLED, false), locked,
                pkg == null ? null : pkg.toString(), activity == null ? null : activity.toString(),
                SystemClock.elapsedRealtime());
        if (policy.isTripped()) {
            stopForSafety();
            return;
        }
        if (!redirect) return;
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (ActivityNotFoundException | SecurityException failure) {
            stopForSafety();
        }
    }

    private void stopForSafety() {
        // Persist off across process restarts. Only a deliberate settings toggle can re-arm it.
        preferences.edit().putBoolean(ENABLED, false).putBoolean(STOPPED, true).apply();
        Toast.makeText(this, R.string.juos_redirect_stopped, Toast.LENGTH_LONG).show();
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(listener);
        super.onDestroy();
    }
}
