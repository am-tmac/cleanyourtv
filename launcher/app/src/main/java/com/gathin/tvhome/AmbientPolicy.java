package com.gathin.tvhome;

/** Whether focus colour may be painted over the current background. Pure Java. */
final class AmbientPolicy {
    private AmbientPolicy() { }

    static boolean allowed(boolean wallpaperShowing) {
        return !wallpaperShowing;
    }
}
