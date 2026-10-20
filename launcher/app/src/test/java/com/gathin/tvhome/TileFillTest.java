package com.gathin.tvhome;

import org.junit.Test;
import java.util.Collections;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * "Crop to fill" per app. Failure modes enumerated before implementation:
 * an upgrade changes an existing tile (wrong default); "on" leaves the small icon or the in-card name;
 * "off" still crops a banner; an unknown/new package inherits another app's choice; a corrupt
 * or oversized saved file crashes the launcher or wipes valid choices; an invalid package is written.
 */
public class TileFillTest {
    private static final Map<String, Boolean> NONE = Collections.emptyMap();

    // ---- defaults must reproduce the previous appearance -------------------------------------

    @Test public void appWithBannerDefaultsToOn() {
        assertTrue(TileFill.defaultOn(true));
    }

    @Test public void appWithoutBannerDefaultsToOff() {
        assertFalse(TileFill.defaultOn(false));
    }

    @Test public void defaultsGiveExactlyThePreviousLook() {
        // Before this feature: banner -> centerCrop fill; no banner -> small icon + name.
        assertEquals(TileFill.Mode.CROP_FILL, TileFill.mode(TileFill.enabled(NONE, "tv.app", true), true));
        assertEquals(TileFill.Mode.ICON_LABEL, TileFill.mode(TileFill.enabled(NONE, "com.hisense.multiscreen.screenhelper", false), false));
    }

    // ---- display mode for on / off ------------------------------------------------------------

    @Test public void onWithBannerCropsTheBanner() {
        assertEquals(TileFill.Mode.CROP_FILL, TileFill.mode(true, true));
        assertTrue(TileFill.usesBanner(true, true));
    }

    @Test public void onWithoutBannerCropsTheAppIconAcrossTheCard() {
        assertEquals(TileFill.Mode.CROP_FILL, TileFill.mode(true, false));
        assertFalse(TileFill.usesBanner(true, false));
    }

    @Test public void onNeverShowsTheInCardName() {
        assertFalse(TileFill.Mode.CROP_FILL.inCardLabel);
    }

    @Test public void offWithBannerShowsTheWholeBannerUncropped() {
        assertEquals(TileFill.Mode.FIT_BANNER, TileFill.mode(false, true));
        assertFalse(TileFill.Mode.FIT_BANNER.inCardLabel);
    }

    @Test public void offWithoutBannerKeepsSmallIconAndName() {
        assertEquals(TileFill.Mode.ICON_LABEL, TileFill.mode(false, false));
        assertTrue(TileFill.Mode.ICON_LABEL.inCardLabel);
    }

    // ---- saved choices --------------------------------------------------------------------------

    @Test public void savedChoiceOverridesTheDefaultForThatPackageOnly() {
        Map<String, Boolean> saved = TileFill.with(NONE, "launcher.only", true);
        assertTrue(TileFill.enabled(saved, "launcher.only", false));
        assertFalse(TileFill.enabled(saved, "other.launcher.only", false));
        assertTrue(TileFill.enabled(saved, "other.tv.app", true));
        assertTrue(NONE.isEmpty());   // input not mutated
    }

    @Test public void turningBannerAppOffIsRemembered() {
        Map<String, Boolean> saved = TileFill.with(NONE, "tv.app", false);
        assertFalse(TileFill.enabled(saved, "tv.app", true));
        assertEquals(TileFill.Mode.FIT_BANNER, TileFill.mode(TileFill.enabled(saved, "tv.app", true), true));
    }

    @Test public void unknownOrNullPackageUsesTheDefault() {
        Map<String, Boolean> saved = TileFill.with(NONE, "tv.app", false);
        assertTrue(TileFill.enabled(saved, "never.seen", true));
        assertFalse(TileFill.enabled(saved, "never.seen", false));
        assertTrue(TileFill.enabled(saved, null, true));
        assertFalse(TileFill.enabled(null, "tv.app", false));
    }

    @Test public void encodeDecodeRoundTrip() {
        Map<String, Boolean> saved = TileFill.with(TileFill.with(NONE, "b.app", true), "a.app", false);
        String text = TileFill.encode(saved);
        assertEquals("a.app=0\nb.app=1\n", text);
        assertEquals(saved, TileFill.decode(text));
    }

    @Test public void decodeOfNothingIsEmpty() {
        assertTrue(TileFill.decode(null).isEmpty());
        assertTrue(TileFill.decode("").isEmpty());
    }

    @Test public void decodeSkipsCorruptLinesButKeepsValidOnes() {
        Map<String, Boolean> saved = TileFill.decode("good.app=1\n=1\nbad line\n..x=0\nx.y=7\nok.app=0\n");
        assertEquals(2, saved.size());
        assertTrue(saved.get("good.app"));
        assertFalse(saved.get("ok.app"));
    }

    @Test public void decodeIgnoresOversizedInput() {
        StringBuilder big = new StringBuilder();
        while (big.length() <= TileFill.MAX_CHARS) big.append("a.b=1\n");
        assertTrue(TileFill.decode(big.toString()).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidPackageIsNeverSaved() {
        TileFill.with(NONE, "not a package", true);
    }

    @Test public void choicesAreBounded() {
        Map<String, Boolean> saved = NONE;
        for (int i = 0; i < TileFill.MAX_ENTRIES + 20; i++) saved = TileFill.with(saved, "p.a" + i, true);
        assertTrue(saved.size() <= TileFill.MAX_ENTRIES);
        assertTrue(saved.containsKey("p.a" + (TileFill.MAX_ENTRIES + 19)));   // the newest choice is kept
    }
}
