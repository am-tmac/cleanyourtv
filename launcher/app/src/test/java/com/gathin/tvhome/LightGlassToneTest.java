package com.gathin.tvhome;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class LightGlassToneTest {
    // Failure cases: smoked tint returns; wallpaper dim leaks into glass; noise flickers,
    // changes alpha, clips channels incorrectly, or exceeds the subtle 1–2% range.
    @Test public void fillIsWhiteAndFallbackIsLight() {
        assertEquals(0x3DFFFFFF, LightGlassTone.TINT);
        assertEquals(0x1FFFFFFF, LightGlassTone.FALLBACK);
    }

    @Test public void compositedGlassIsBrighterThanSampleWallpaper() {
        int[] samples = {0xFF000000, 0xFF202020, 0xFF808080, 0xFFC8C8C8,
                0xFFF0F0F0, 0xFF806040, 0xFF205040, 0xFF204080, 0xFFFF0000};
        int[] glass = samples.clone();
        LightGlassTone.prepare(glass);
        float alpha = (LightGlassTone.TINT >>> 24) / 255f;
        for (int i = 0; i < glass.length; i++) {
            double composite = luminance(glass[i]) * (1 - alpha) + 255 * alpha;
            assertTrue("sample " + i, composite > luminance(samples[i]));
        }
    }

    @Test public void backdropUsesUndimmedBrightnessAndSaturation() {
        int[] pixels = {0xFF646464, 0xFF806060};
        LightGlassTone.prepare(pixels);
        assertTrue(Math.abs(red(pixels[0]) - 108) <= 2);
        assertTrue(red(pixels[1]) > 0x80);
        assertTrue(((pixels[1] >> 8) & 255) < 0x68);
    }

    @Test public void noiseIsDeterministicBoundedAndOpaque() {
        int[] a = new int[1024];
        Arrays.fill(a, 0xFF646464);
        int[] b = a.clone();
        LightGlassTone.prepare(a);
        LightGlassTone.prepare(b);
        assertArrayEquals(a, b);
        int min = 255, max = 0;
        for (int pixel : a) {
            assertEquals(255, pixel >>> 24);
            assertEquals(red(pixel), pixel & 255);
            min = Math.min(min, red(pixel));
            max = Math.max(max, red(pixel));
        }
        assertEquals(106, min);
        assertEquals(110, max);
    }

    @Test public void blackAndWhiteStayWithinChannelBounds() {
        int[] pixels = {0xFF000000, 0xFFFFFFFF};
        LightGlassTone.prepare(pixels);
        assertTrue(red(pixels[0]) <= 2);
        assertTrue(red(pixels[1]) >= 253);
    }

    // Failure cases: wrong endpoints, non-monotonic transition, empty arrays, wrong region/weights.
    @Test public void adaptiveTintEndpointsAndMonotonicity() {
        assertEquals(.10f, LightGlassTone.adaptiveTint(-1), .00001f);
        assertEquals(.10f, LightGlassTone.adaptiveTint(.15f), .00001f);
        assertEquals(.24f, LightGlassTone.adaptiveTint(.55f), .00001f);
        assertEquals(.24f, LightGlassTone.adaptiveTint(2), .00001f);
        assertEquals(.17f, LightGlassTone.adaptiveTint(.35f), .00001f);
        float previous = 0;
        for (int i = 0; i <= 100; i++) {
            float tint = LightGlassTone.adaptiveTint(i / 100f);
            assertTrue(tint >= previous); previous = tint;
        }
    }

    @Test public void pixelArrayLuminanceAndDockRegion() {
        assertEquals(0, LightGlassTone.luminance(new int[0]), .00001f);
        assertEquals(.5f, LightGlassTone.luminance(new int[]{0xFF000000, 0xFFFFFFFF}), .00001f);
        assertEquals(.299f, LightGlassTone.luminance(new int[]{0xFFFF0000}), .00001f);
        int[] pixels = new int[100];
        Arrays.fill(pixels, 0xFF000000);
        for (int y = 6; y < 9; y++) for (int x = 0; x < 10; x++) pixels[y*10+x] = 0xFFFFFFFF;
        assertEquals(1, LightGlassTone.dockLuminance(pixels, 10, 10), .00001f);
    }

    private static int red(int c) { return (c >> 16) & 255; }
    private static double luminance(int c) {
        return .299 * red(c) + .587 * ((c >> 8) & 255) + .114 * (c & 255);
    }
}
