package com.gathin.tvhome;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class AccessibilitySettingsRouteTest {
    private static final String SERVICE = "com.gathin.tvhome/.JuosHomeRedirectService";

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    @Test public void standardActionIsTriedFirstThenHiddenJuosPage() {
        List<AccessibilitySettingsRoute.Target> c = AccessibilitySettingsRoute.candidates();
        assertEquals(2, c.size());
        assertEquals("android.settings.ACCESSIBILITY_SETTINGS", c.get(0).action);
        assertNull(c.get(0).packageName);
        assertNull(c.get(1).action);
    }

    @Test public void explicitComponentIsTheExactAospTvSettingsActivity() {
        AccessibilitySettingsRoute.Target t = AccessibilitySettingsRoute.candidates().get(1);
        assertEquals("com.android.tv.settings", t.packageName);
        assertEquals("com.android.tv.settings.system.AccessibilityActivity", t.className);
    }

    @Test public void serviceComponentMatchesManifestDeclaration() throws Exception {
        assertEquals(SERVICE, AccessibilitySettingsRoute.SERVICE_COMPONENT);
        String manifest = read("src/main/AndroidManifest.xml");
        assertTrue(manifest.contains("android:name=\".JuosHomeRedirectService\""));
        assertTrue(manifest.contains("package=\"com.gathin.tvhome\"")
                || read("build.gradle").contains("namespace 'com.gathin.tvhome'"));
    }

    @Test public void enableCommandReadsExistingListAndAppendsInsteadOfOverwriting() {
        String cmd = AccessibilitySettingsRoute.enableServiceCommand();
        assertTrue(cmd.startsWith("adb shell '"));
        int get = cmd.indexOf("settings get secure enabled_accessibility_services");
        int put = cmd.indexOf("settings put secure enabled_accessibility_services \"$new\"");
        assertTrue(get >= 0);
        assertTrue(put > get);
        assertTrue(cmd.contains("\"$cur:" + SERVICE + "\""));
        // Already present: keep the list unchanged; empty/null: just this service.
        assertTrue(cmd.contains("*" + SERVICE + "*) new=\"$cur\""));
        assertTrue(cmd.contains("\"\"|null) new=" + SERVICE));
        // Never a bare put of only our service that would wipe others.
        assertFalse(cmd.contains("enabled_accessibility_services " + SERVICE));
        assertFalse(cmd.contains("delete"));
    }

    @Test public void secondCommandTurnsAccessibilityOn() {
        assertEquals("adb shell settings put secure accessibility_enabled 1",
                AccessibilitySettingsRoute.enableAccessibilityCommand());
    }

    @Test public void commandsTextHasBothCommandsInOrder() {
        String text = AccessibilitySettingsRoute.commandsText();
        int a = text.indexOf(AccessibilitySettingsRoute.enableServiceCommand());
        int b = text.indexOf(AccessibilitySettingsRoute.enableAccessibilityCommand());
        assertTrue(a >= 0);
        assertTrue(b > a);
    }

    @Test public void buttonNoLongerFallsStraightIntoToast() throws Exception {
        String src = read("src/main/java/com/gathin/tvhome/LauncherSettingsActivity.java");
        Matcher m = Pattern.compile("R\\.id\\.juosAccessibilityChoice\\)\\.setOnClickListener\\(([^;]*);")
                .matcher(src);
        assertTrue(m.find());
        String handler = m.group(1);
        assertFalse(handler.contains("open(new Intent"));
        assertTrue(handler.contains("openAccessibilitySettings()"));
        assertTrue(src.contains("AccessibilitySettingsRoute.candidates()"));
        assertTrue(src.contains("R.string.juos_accessibility_adb_help"));
        // System settings row keeps the old behaviour.
        assertTrue(src.contains("R.id.tvSettingsChoice).setOnClickListener(v ->\n                open(new Intent(Settings.ACTION_SETTINGS)));"));
    }

    @Test public void helpStringsExistInEnglishAndChineseWithCommandPlaceholder() throws Exception {
        for (String dir : new String[] {"values", "values-zh-rCN"}) {
            String xml = read("src/main/res/" + dir + "/juos_strings.xml");
            Matcher m = Pattern.compile("<string name=\"juos_accessibility_adb_help\"[^>]*>([^<]*)</string>")
                    .matcher(xml);
            assertTrue(dir, m.find());
            assertTrue(dir, m.group(1).contains("%1$s"));
            assertTrue(dir, xml.contains("name=\"juos_accessibility_adb_title\""));
        }
    }
}
