package com.gathin.tvhome;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DesktopAppsTest {
    private static final String SELF = "com.gathin.tvhome";
    private static final Set<String> NONE = Collections.emptySet();

    private static Set<String> set(String... items) { return new HashSet<>(Arrays.asList(items)); }
    private static DesktopApps.Choices choices(Set<String> added, Set<String> removed) {
        return new DesktopApps.Choices(added, removed);
    }
    private static final DesktopApps.Choices EMPTY = choices(NONE, NONE);

    @Test public void leanbackAppIsShownByDefault() {
        assertTrue(DesktopApps.shown("tv.app", true, NONE, EMPTY, SELF));
    }

    @Test public void launcherOnlyAppIsNotShownByDefault() {
        assertFalse(DesktopApps.shown("com.hisense.multiscreen.screenhelper", false, NONE, EMPTY, SELF));
    }

    @Test public void turningOnLauncherOnlyAppPutsItOnTheDesktop() {
        DesktopApps.Choices next = DesktopApps.toggle(EMPTY, "com.hisense.multiscreen.screenhelper", true, false, NONE, SELF);
        assertNotNull(next);
        assertTrue(next.added.contains("com.hisense.multiscreen.screenhelper"));
        assertTrue(DesktopApps.shown("com.hisense.multiscreen.screenhelper", false, NONE, next, SELF));
        assertTrue(EMPTY.added.isEmpty()); // inputs are not mutated
    }

    @Test public void turningOffHidesLeanbackAndLauncherOnlyAppsEverywhere() {
        DesktopApps.Choices on = DesktopApps.toggle(EMPTY, "launcher.only", true, false, NONE, SELF);
        DesktopApps.Choices off = DesktopApps.toggle(on, "launcher.only", false, false, NONE, SELF);
        assertFalse(DesktopApps.shown("launcher.only", false, NONE, off, SELF));
        assertFalse(off.added.contains("launcher.only"));
        DesktopApps.Choices tvOff = DesktopApps.toggle(EMPTY, "dock.tv.app", false, true, NONE, SELF);
        // One package predicate drives both the dock and the grid, so a dock favourite also disappears.
        assertFalse(DesktopApps.shown("dock.tv.app", true, NONE, tvOff, SELF));
        assertTrue(tvOff.removed.contains("dock.tv.app"));
        DesktopApps.Choices tvOn = DesktopApps.toggle(tvOff, "dock.tv.app", true, true, NONE, SELF);
        assertTrue(DesktopApps.shown("dock.tv.app", true, NONE, tvOn, SELF));
        assertFalse(tvOn.removed.contains("dock.tv.app"));
    }

    @Test public void themeHiddenAppCannotBeTurnedOnOrBypassedByAnAddition() {
        Set<String> theme = set("theme.hidden");
        assertNull(DesktopApps.toggle(EMPTY, "theme.hidden", true, true, theme, SELF));
        assertFalse(DesktopApps.shown("theme.hidden", true, theme, choices(set("theme.hidden"), NONE), SELF));
        List<DesktopApps.Row> rows = DesktopApps.rows(Collections.singletonList(
                new DesktopApps.Launchable("theme.hidden", "Theme", true)), theme, EMPTY, SELF, String::compareTo);
        assertEquals(DesktopApps.State.THEME_HIDDEN, rows.get(0).state);
        assertFalse(rows.get(0).shown());
    }

    @Test public void launcherItselfIsNeverListedShownOrToggled() {
        List<DesktopApps.Row> rows = DesktopApps.rows(Arrays.asList(
                new DesktopApps.Launchable(SELF, "CleanYourTV", true),
                new DesktopApps.Launchable("other.app", "Other", false)), NONE, EMPTY, SELF, String::compareTo);
        assertEquals(1, rows.size());
        assertEquals("other.app", rows.get(0).pkg);
        assertNull(DesktopApps.toggle(EMPTY, SELF, false, true, NONE, SELF));
        assertFalse(DesktopApps.shown(SELF, true, NONE, EMPTY, SELF));
    }

    @Test public void duplicatePackagesMergeIntoOneRowPreferringTheLeanbackEntry() {
        List<DesktopApps.Row> rows = DesktopApps.rows(Arrays.asList(
                new DesktopApps.Launchable("dual.app", "Dual phone", false),
                new DesktopApps.Launchable("dual.app", "Dual TV", true),
                new DesktopApps.Launchable("dual.app", "Dual again", false)), NONE, EMPTY, SELF, String::compareTo);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).leanback);
        assertEquals("Dual TV", rows.get(0).label);
        assertEquals(DesktopApps.State.SHOWN, rows.get(0).state);
    }

    @Test public void rowsAreSortedByLabelThenPackageAndReflectDesktopState() {
        DesktopApps.Choices state = choices(set("b.launcher"), set("c.tv"));
        List<DesktopApps.Row> rows = DesktopApps.rows(Arrays.asList(
                new DesktopApps.Launchable("c.tv", "Cinema", true),
                new DesktopApps.Launchable("z.launcher", "Atlas", false),
                new DesktopApps.Launchable("b.launcher", "Atlas", false),
                new DesktopApps.Launchable("a.tv", "Browser", true)), NONE, state, SELF, String::compareTo);
        List<String> order = new ArrayList<>();
        for (DesktopApps.Row row : rows) order.add(row.pkg);
        assertEquals(Arrays.asList("b.launcher", "z.launcher", "a.tv", "c.tv"), order);
        assertTrue(rows.get(0).shown());   // launcher-only but added
        assertFalse(rows.get(1).shown());  // launcher-only default
        assertTrue(rows.get(2).shown());   // leanback default
        assertFalse(rows.get(3).shown());  // leanback removed
    }

    @Test public void legacyHiddenAppsMigrateIntoRemovedChoicesAndStayHidden() {
        Set<String> legacy = DesktopApps.decodeLegacy("tv.hidden\nlauncher.added\n\n" + SELF + "\n");
        assertEquals(set("tv.hidden", "launcher.added", SELF), legacy);
        DesktopApps.Choices before = choices(set("launcher.added", "keep.added"), set("old.removed"));
        DesktopApps.Choices after = DesktopApps.migrate(before, legacy, SELF);
        assertEquals(set("tv.hidden", "launcher.added", "old.removed"), after.removed);
        assertEquals(set("keep.added"), after.added);
        assertFalse(DesktopApps.shown("tv.hidden", true, NONE, after, SELF));
        assertFalse(DesktopApps.shown("launcher.added", false, NONE, after, SELF));
        assertTrue(DesktopApps.shown("keep.added", false, NONE, after, SELF));
        assertEquals(set("launcher.added", "keep.added"), before.added); // not mutated
        assertSame(before, DesktopApps.migrate(before, NONE, SELF));
        assertTrue(DesktopApps.decodeLegacy(null).isEmpty());
    }
}
