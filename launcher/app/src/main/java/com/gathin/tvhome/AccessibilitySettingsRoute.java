package com.gathin.tvhome;

import java.util.Arrays;
import java.util.List;

/**
 * Where the "open system accessibility settings" row tries to go, in order, and the one-time
 * ADB fallback shown when this TV exposes neither. Pure Java so the order and commands are testable.
 */
final class AccessibilitySettingsRoute {
    static final String SERVICE_COMPONENT = "com.gathin.tvhome/.JuosHomeRedirectService";
    private static final String SERVICE_FULL = "com.gathin.tvhome/com.gathin.tvhome.JuosHomeRedirectService";

    static final class Target {
        final String action, packageName, className;
        Target(String action, String packageName, String className) {
            this.action = action; this.packageName = packageName; this.className = className;
        }
    }

    private AccessibilitySettingsRoute() {}

    /** Standard action first (other TVs); then JUOS's hidden AOSP page, exported without a filter. */
    static List<Target> candidates() {
        return Arrays.asList(
                new Target("android.settings.ACCESSIBILITY_SETTINGS", null, null),
                new Target(null, "com.android.tv.settings",
                        "com.android.tv.settings.system.AccessibilityActivity"));
    }

    /** Appends our service to whatever is already enabled; never replaces the existing list. */
    static String enableServiceCommand() {
        return "adb shell 'cur=$(settings get secure enabled_accessibility_services); case \"$cur\" in "
                + "*" + SERVICE_FULL + "*|*" + SERVICE_COMPONENT + "*) new=\"$cur\";; "
                + "\"\"|null) new=" + SERVICE_COMPONENT + ";; "
                + "*) new=\"$cur:" + SERVICE_COMPONENT + "\";; esac; "
                + "settings put secure enabled_accessibility_services \"$new\"'";
    }

    static String enableAccessibilityCommand() {
        return "adb shell settings put secure accessibility_enabled 1";
    }

    static String commandsText() {
        return "1) " + enableServiceCommand() + "\n\n2) " + enableAccessibilityCommand();
    }
}
