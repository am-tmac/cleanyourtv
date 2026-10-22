package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class BackdropBlurTest {
    private static int r(int c) { return (c >> 16) & 0xFF; }
    private static int g(int c) { return (c >> 8) & 0xFF; }
    private static int b(int c) { return c & 0xFF; }

    @Test public void uniformImageStaysUniform() {
        int[] px = new int[20 * 10];
        Arrays.fill(px, 0xFF336699);
        BackdropBlur.blur(px, 20, 10, 3, 3);
        for (int c : px) assertEquals(0xFF336699, c);
    }

    @Test public void brightDotSpreadsAndKeepsEnergy() {
        int w = 21, h = 21;
        int[] px = new int[w * h];
        Arrays.fill(px, 0xFF000000);
        px[10 * w + 10] = 0xFFFFFFFF;
        BackdropBlur.blur(px, w, h, 2, 3);
        assertTrue(r(px[10 * w + 10]) < 255);
        assertTrue(r(px[10 * w + 13]) > 0);
        assertTrue(r(px[13 * w + 10]) > 0);
        assertEquals(0, r(px[0]));
        long sum = 0;
        for (int c : px) sum += r(c);
        assertTrue("sum " + sum, Math.abs(sum - 255) < 40);
        for (int c : px) assertEquals(0xFF, c >>> 24);
    }

    @Test public void tinyImagesAndZeroRadiusAreSafe() {
        int[] one = {0xFF102030};
        BackdropBlur.blur(one, 1, 1, 4, 3);
        assertEquals(0xFF102030, one[0]);
        int[] px = {0xFF000000, 0xFFFFFFFF};
        BackdropBlur.blur(px, 2, 1, 0, 3);
        assertEquals(0xFFFFFFFF, px[1]);
    }

    @Test public void toneKeepsGreyAndBoostsColour() {
        int[] px = {0xFF808080, 0xFF806060};
        BackdropBlur.tone(px, 1.4f, 1.0f, 0);
        assertEquals(0xFF808080, px[0]);
        assertTrue(r(px[1]) > 0x80);
        assertTrue(g(px[1]) < 0x60);
    }

    @Test public void toneBrightnessAndDim() {
        int[] px = {0xFF646464};
        BackdropBlur.tone(px, 1f, 1.08f, 0);
        assertEquals(108, r(px[0]));
        int[] d = {0xFFC8C8C8};
        BackdropBlur.tone(d, 1f, 1f, 50);
        assertEquals(100, b(d[0]));
        int[] clip = {0xFFFF0000};
        BackdropBlur.tone(clip, 1.4f, 1.08f, 0);
        assertEquals(255, r(clip[0]));
        assertEquals(0, g(clip[0]));
    }
}
