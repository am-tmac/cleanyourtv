package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Single source of truth for "is this app on the desktop": the library's added/removed choices
 * plus the theme's hidden_apps veto. Pure Java so the rules are tested on the JVM.
 * Leanback apps are shown unless removed; launcher-only apps only once added; theme hidden_apps
 * and the launcher itself always lose. The same package predicate filters the dock and the grid.
 */
final class DesktopApps {
    private DesktopApps() { }

    enum State { SHOWN, NOT_SHOWN, THEME_HIDDEN }

    /** Immutable snapshot of the owner's explicit additions and removals. */
    static final class Choices {
        final Set<String> added, removed;
        Choices(Set<String> added, Set<String> removed) {
            this.added = Collections.unmodifiableSet(new HashSet<>(added));
            this.removed = Collections.unmodifiableSet(new HashSet<>(removed));
        }
    }

    /** One launch entry from PackageManager (LEANBACK_LAUNCHER or LAUNCHER). */
    static final class Launchable {
        final String pkg, label;
        final boolean leanback;
        Launchable(String pkg, String label, boolean leanback) {
            this.pkg = pkg;
            this.label = label;
            this.leanback = leanback;
        }
    }

    static final class Row {
        final String pkg, label;
        final boolean leanback;
        final State state;
        Row(String pkg, String label, boolean leanback, State state) {
            this.pkg = pkg;
            this.label = label;
            this.leanback = leanback;
            this.state = state;
        }
        boolean shown() { return state == State.SHOWN; }
    }

    static boolean shown(String pkg, boolean leanback, Collection<String> theme, Choices choices, String self) {
        if (pkg == null || pkg.equals(self) || theme.contains(pkg) || choices.removed.contains(pkg)) return false;
        return leanback || choices.added.contains(pkg);
    }

    /** New choices after the owner's switch, or null when the switch is locked (theme or self). */
    static Choices toggle(Choices current, String pkg, boolean show, boolean leanback,
                          Collection<String> theme, String self) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(self) || theme.contains(pkg)) return null;
        Set<String> added = new HashSet<>(current.added);
        Set<String> removed = new HashSet<>(current.removed);
        if (show) {
            removed.remove(pkg);
            if (leanback) added.remove(pkg); else added.add(pkg);
        } else {
            added.remove(pkg);
            removed.add(pkg);
        }
        return new Choices(added, removed);
    }

    /** One row per package (leanback entry preferred), self excluded, sorted by label then package. */
    static List<Row> rows(List<Launchable> apps, Collection<String> theme, Choices choices, String self,
                          Comparator<String> labelOrder) {
        Map<String, Launchable> unique = new LinkedHashMap<>();
        for (Launchable app : apps) {
            if (app.pkg == null || app.pkg.equals(self)) continue;
            Launchable known = unique.get(app.pkg);
            if (known == null || (app.leanback && !known.leanback)) unique.put(app.pkg, app);
        }
        List<Row> rows = new ArrayList<>();
        for (Launchable app : unique.values()) {
            State state = theme.contains(app.pkg) ? State.THEME_HIDDEN
                    : shown(app.pkg, app.leanback, theme, choices, self) ? State.SHOWN : State.NOT_SHOWN;
            rows.add(new Row(app.pkg, app.label == null ? app.pkg : app.label, app.leanback, state));
        }
        rows.sort((a, b) -> {
            int byLabel = labelOrder.compare(a.label, b.label);
            return byLabel != 0 ? byLabel : a.pkg.compareTo(b.pkg);
        });
        return rows;
    }

    /** The previous build's "Show / hide apps" preference: newline-separated packages. */
    static Set<String> decodeLegacy(String value) {
        Set<String> result = new TreeSet<>();
        if (value != null) for (String pkg : value.split("\n")) if (!pkg.isEmpty()) result.add(pkg);
        return result;
    }

    /** Folds legacy user-hidden packages into removals so they stay hidden after the upgrade. */
    static Choices migrate(Choices current, Set<String> legacy, String self) {
        if (legacy.isEmpty()) return current;
        Set<String> added = new HashSet<>(current.added);
        Set<String> removed = new HashSet<>(current.removed);
        for (String pkg : legacy) {
            if (pkg.equals(self)) continue;
            added.remove(pkg);
            removed.add(pkg);
        }
        return new Choices(added, removed);
    }
}
