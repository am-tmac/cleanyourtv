package com.gathin.tvhome;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-app "Crop to fill" choice (tile long-press panel). Pure Java so the rules run on the JVM.
 * Defaults reproduce the previous look exactly: an app with a TV banner was a centerCrop fill (on),
 * an app without one was a small icon plus its name (off). Only explicit choices are saved.
 */
final class TileFill {
    static final int MAX_ENTRIES = 256;
    static final int MAX_CHARS = 64 * 1024;

    private TileFill() { }

    enum Mode {
        /** Banner, or failing that the app icon, centerCrop across the whole card; no in-card name. */
        CROP_FILL(false),
        /** The whole banner, fitCenter, nothing cropped; no in-card name. */
        FIT_BANNER(false),
        /** The previous look of an app without a banner: small icon and its name. */
        ICON_LABEL(true);

        final boolean inCardLabel;
        Mode(boolean inCardLabel) { this.inCardLabel = inCardLabel; }
    }

    static boolean defaultOn(boolean hasBanner) { return hasBanner; }

    static boolean enabled(Map<String, Boolean> saved, String pkg, boolean hasBanner) {
        Boolean choice = saved == null || pkg == null ? null : saved.get(pkg);
        return choice != null ? choice : defaultOn(hasBanner);
    }

    static Mode mode(boolean on, boolean hasBanner) {
        if (on) return Mode.CROP_FILL;
        return hasBanner ? Mode.FIT_BANNER : Mode.ICON_LABEL;
    }

    /** Whether the card shows the banner (otherwise the app icon). */
    static boolean usesBanner(boolean on, boolean hasBanner) { return hasBanner; }

    /** A copy with this package's choice; the newest choices survive the size bound. */
    static Map<String, Boolean> with(Map<String, Boolean> saved, String pkg, boolean on) {
        if (!LibraryApps.validPackage(pkg)) throw new IllegalArgumentException("Invalid package");
        LinkedHashMap<String, Boolean> next = new LinkedHashMap<>();
        if (saved != null) next.putAll(saved);
        next.remove(pkg);
        next.put(pkg, on);
        while (next.size() > MAX_ENTRIES) next.remove(next.keySet().iterator().next());
        return Collections.unmodifiableMap(next);
    }

    static String encode(Map<String, Boolean> saved) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Boolean> e : new TreeMap<>(saved).entrySet()) {
            if (!LibraryApps.validPackage(e.getKey())) continue;
            text.append(e.getKey()).append('=').append(Boolean.TRUE.equals(e.getValue()) ? '1' : '0').append('\n');
        }
        return text.toString();
    }

    /** Lenient: corrupt lines are skipped, an oversized file is ignored; never throws. */
    static Map<String, Boolean> decode(String text) {
        LinkedHashMap<String, Boolean> result = new LinkedHashMap<>();
        if (text == null || text.isEmpty() || text.length() > MAX_CHARS) return result;
        for (String line : text.split("\n")) {
            int eq = line.lastIndexOf('=');
            if (eq <= 0 || eq != line.length() - 2) continue;
            String pkg = line.substring(0, eq);
            char v = line.charAt(eq + 1);
            if (!LibraryApps.validPackage(pkg) || (v != '0' && v != '1')) continue;
            result.remove(pkg);
            result.put(pkg, v == '1');
            if (result.size() > MAX_ENTRIES) result.remove(result.keySet().iterator().next());
        }
        return result;
    }
}
