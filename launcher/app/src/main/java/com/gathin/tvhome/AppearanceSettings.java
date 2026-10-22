package com.gathin.tvhome;

/**
 * On-TV appearance choices (glass dock, weather line, wallpaper dim, Bing wallpaper). Pure Java.
 * Every default reproduces the look from before these options existed.
 */
final class AppearanceSettings {
    static final String GLASS_DOCK_KEY = "glass_dock";
    static final String WEATHER_KEY = "weather_line";
    static final String DIM_KEY = "wallpaper_dim_choice";
    /** Value of Config.WALLPAPER_CHOICE_KEY for Bing's daily image. */
    static final String WALLPAPER_BING = "bing";

    static final boolean GLASS_DOCK_DEFAULT = false;
    static final boolean WEATHER_DEFAULT = false;
    /** -1: the theme's wallpaper_dim decides. */
    static final int DIM_DEFAULT = -1;
    /** Bing's photos are bright; with no dim from the theme or the TV, they get this much. */
    static final int BING_DIM = 25;
    private static final int[] DIM_STEPS = {-1, 0, 20, 35, 50};

    private AppearanceSettings() { }

    static int dim(int themeDim, int chosen, boolean bing) {
        if (chosen >= 0 && chosen <= 90) return chosen;
        if (bing && themeDim <= 0) return BING_DIM;
        return themeDim;
    }

    /** OK on the dim row: theme, 0, 20, 35, 50, theme again. */
    static int nextDim(int current) {
        for (int i = 0; i < DIM_STEPS.length - 1; i++) if (DIM_STEPS[i] == current) return DIM_STEPS[i + 1];
        return DIM_STEPS[0];
    }
}
