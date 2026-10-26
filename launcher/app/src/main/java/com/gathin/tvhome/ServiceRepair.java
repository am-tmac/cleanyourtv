package com.gathin.tvhome;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Android takes an app's accessibility services off the enabled list when the app is force-stopped (Settings, a
 * cleaner app, or am force-stop), and the remote's Home, Input and app buttons (and, on Vidda/JUOS, the Home
 * redirect, JuosHomeRedirectService) then stop working until the helper is turned on again over ADB. With restore_key_services on, a running helper records itself here and drops the
 * record when Android unbinds it (the user turned it off); a force stop kills the process before that, so the
 * record stays. Home starting again puts recorded helpers back on the list, when the installer granted
 * WRITE_SECURE_SETTINGS. With the flag off nothing is recorded and nothing is restored.
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
            List<ComponentName> enabled = new ArrayList<>();
            if (list != null) {
                for (String entry : list.split(":")) {
                    ComponentName name = ComponentName.unflattenFromString(entry);
                    if (name != null) enabled.add(name);
                }
            }
            List<ComponentName> recorded = new ArrayList<>();
            for (String service : prefs(context).getAll().keySet()) {
                recorded.add(new ComponentName(context.getPackageName(), service));
            }
            if (recorded.isEmpty()) return;
            // Vidda: the pure check (own package only, both short and full forms) decides whether anything is missing.
            if (appendMissing(list, context.getPackageName(), prefs(context).getAll().keySet()) == null) return;
            // The list without our recorded helpers, then with all of them.
            StringBuilder others = new StringBuilder();
            for (ComponentName name : enabled) {
                if (recorded.contains(name)) continue;
                if (others.length() > 0) others.append(':');
                others.append(name.flattenToShortString());
            }
            StringBuilder updated = new StringBuilder(others);
            boolean missing = false;
            for (ComponentName name : recorded) {
                if (!enabled.contains(name)) missing = true;
                if (updated.length() > 0) updated.append(':');
                updated.append(name.flattenToShortString());
            }
            if (!missing) return;
            // A killed helper also lands on Android's "Crashed services" list (dumpsys accessibility), and a
            // crashed service is never bound again, so putting it back on the list alone binds nothing and the
            // switch falls back to 0 (Hisense songshan, Android 11, whose standby kills third-party apps). Android
            // drops the crashed mark when it sees the helper leave the enabled list, so add, remove, add: after a
            // force stop the list is already empty, and a removal that changes nothing is not seen. Off the main
            // thread, so Home does not wait for the pauses. Each write first checks the list still reads what the
            // previous step left, and the cycle stops otherwise: a service turned on or off during a pause stays
            // that way instead of being overwritten by a snapshot.
            final String without = others.toString();
            final String with = updated.toString();
            new Thread(() -> {
                try {
                    if (!replaceList(cr, list, with)) return;
                    Thread.sleep(1000);
                    if (!replaceList(cr, with, without)) return;
                    Thread.sleep(1000);
                    if (!replaceList(cr, without, with)) return;
                    Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                } catch (RuntimeException | InterruptedException ignored) {
                    // The setting is the system's; a refusal leaves the helper off, as before.
                }
            }, "ServiceRepair").start();
        } catch (RuntimeException ignored) {
            // The setting is the system's; a refusal leaves the helper off, as before.
        }
    }

    /** Writes the enabled list only while it still reads {@code expected}; an absent list counts as empty. */
    private static boolean replaceList(ContentResolver cr, String expected, String updated) {
        String now = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return Objects.equals(now == null ? "" : now, expected == null ? "" : expected)
                && Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated);
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
