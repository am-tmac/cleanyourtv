package com.gathin.tvhome;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Android takes an app's accessibility services off the enabled list when the app is force-stopped (Settings, a
 * cleaner app, or am force-stop), and the remote's Home, Input and app buttons (and, on Vidda/JUOS, the Home
 * redirect) then stop working until the helper is turned on again over ADB. With restore_key_services on, a running
 * helper records itself here and drops the record when Android unbinds it (the user turned it off); a force stop
 * kills the process before that, so the record stays. Home starting again appends recorded helpers that are missing
 * to the list, when the installer granted WRITE_SECURE_SETTINGS. Other entries keep their order and are never
 * removed. With the flag off nothing is recorded and nothing is restored.
 */
final class ServiceRepair {
    private static final String PREFS = "accessibility_helpers";

    private ServiceRepair() {
    }

    static void connected(Context context, Class<?> service) {
        prefs(context).edit().putBoolean(service.getName(), true).apply();
    }

    static void disconnected(Context context, Class<?> service) {
        prefs(context).edit().remove(service.getName()).apply();
    }

    static void restore(Context context) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) return;
        try {
            ContentResolver cr = context.getContentResolver();
            String list = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String updated = appendMissing(list, context.getPackageName(), prefs(context).getAll().keySet());
            if (updated == null) return;
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated);
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (RuntimeException ignored) {
            // The setting is the system's; a refusal leaves the helper off, as before.
        }
    }

    /**
     * The enabled list with every recorded helper of {@code pkg} that is not on it appended at the end, or null when
     * nothing is missing. Existing entries keep their text and order. A record is a full class name inside
     * {@code pkg}; anything else is ignored.
     */
    static String appendMissing(String list, String pkg, Collection<String> recorded) {
        String current = list == null || list.equals("null") ? "" : list;
        Set<String> enabled = new HashSet<>();
        for (String entry : current.split(":")) {
            String full = fullName(entry.trim());
            if (full != null) enabled.add(full);
        }
        List<String> missing = new ArrayList<>();
        for (String service : new TreeSet<>(recorded)) {
            if (service == null || !service.startsWith(pkg + ".")) continue;
            if (enabled.contains(pkg + "/" + service)) continue;
            missing.add(pkg + "/" + service.substring(pkg.length()));
        }
        if (missing.isEmpty()) return null;
        StringBuilder updated = new StringBuilder(current);
        while (updated.length() > 0 && updated.charAt(updated.length() - 1) == ':') updated.setLength(updated.length() - 1);
        for (String name : missing) {
            if (updated.length() > 0) updated.append(':');
            updated.append(name);
        }
        return updated.toString();
    }

    /** "pkg/.Cls" or "pkg/pkg.Cls" as "pkg/pkg.Cls"; null when the entry is not a component. */
    private static String fullName(String entry) {
        int slash = entry.indexOf('/');
        if (slash <= 0 || slash == entry.length() - 1) return null;
        String pkg = entry.substring(0, slash);
        String cls = entry.substring(slash + 1);
        return pkg + "/" + (cls.startsWith(".") ? pkg + cls : cls);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
