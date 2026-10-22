package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class AppearanceSettingsTest {
    @Test public void defaultsReproduceTheCurrentLook() {
        assertFalse(AppearanceSettings.GLASS_DOCK_DEFAULT);
        assertFalse(AppearanceSettings.WEATHER_DEFAULT);
        assertEquals(-1, AppearanceSettings.DIM_DEFAULT);
    }

    @Test public void dimFollowsThemeUnlessChosen() {
        assertEquals(0, AppearanceSettings.dim(0, -1, false));
        assertEquals(30, AppearanceSettings.dim(30, -1, false));
        assertEquals(20, AppearanceSettings.dim(30, 20, false));
        assertEquals(0, AppearanceSettings.dim(30, 0, true));
        // Bing pictures are bright photos: a theme without a dim still gets a light one.
        assertEquals(AppearanceSettings.BING_DIM, AppearanceSettings.dim(0, -1, true));
        assertEquals(40, AppearanceSettings.dim(40, -1, true));
        assertEquals(30, AppearanceSettings.dim(30, 999, false));   // out of range choice ignored
    }

    @Test public void dimCycle() {
        assertEquals(0, AppearanceSettings.nextDim(-1));
        assertEquals(20, AppearanceSettings.nextDim(0));
        assertEquals(35, AppearanceSettings.nextDim(20));
        assertEquals(50, AppearanceSettings.nextDim(35));
        assertEquals(-1, AppearanceSettings.nextDim(50));
        assertEquals(-1, AppearanceSettings.nextDim(7));
    }

    @Test public void bingChoiceKey() {
        assertEquals("bing", AppearanceSettings.WALLPAPER_BING);
    }
}
