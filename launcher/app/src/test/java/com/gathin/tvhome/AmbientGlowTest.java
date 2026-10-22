package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
/** Failure cases: ambient/synthetic geometry drift, cinematic RTL reversed, alpha lost, shader tone drift. */
public class AmbientGlowTest {
    @Test public void sharedDefaultAndCinematicParameters() {
        AmbientGlow a = new AmbientGlow(false, false);
        assertEquals(.32f, a.centerX, 0); assertEquals(.42f, a.centerY, 0);
        assertEquals(.62f, a.radius, 0); assertEquals(.45f, a.middle, 0);
        assertEquals(0x5C, a.innerAlpha); assertEquals(0x26, a.middleAlpha);
        assertEquals(0x5C / 255f, a.mask(.32f, .42f, 1920, 1080), .00001f);
        AmbientGlow c = new AmbientGlow(true, false), rtl = new AmbientGlow(true, true);
        assertEquals(.8f, c.centerX, 0); assertEquals(.2f, rtl.centerX, 0);
        assertEquals(.06f, c.centerY, 0); assertEquals(.78f, c.radius, 0);
        assertEquals(.5f, c.middle, 0); assertEquals(0xA6, c.innerAlpha); assertEquals(0x47, c.middleAlpha);
    }
    @Test public void syntheticCompositionAndShaderContract() {
        assertEquals(0xFF123456, AmbientGlow.composite(0xFF123456, 0xFFE07A2A, 1, 0));
        assertEquals(0xFFE07A2A, AmbientGlow.composite(0xFF000000, 0xFFE07A2A, 1, 1));
        for (String uniform : new String[]{"uniform half synthetic", "uniform half3 bgColor",
                "uniform half3 glowColor", "uniform half glowAlpha"}) assertTrue(GlassEdge.AGSL.contains(uniform));
        assertTrue(GlassEdge.AGSL.contains("0.299, 0.587, 0.114"));
        assertTrue(GlassEdge.AGSL.contains("1.4) * 1.08"));
        int[] p = {AmbientGlow.composite(0xFF102030, 0xFFE07A2A, .3f, .7f)};
        int expected = AmbientGlow.prepared(p[0]);
        BackdropBlur.tone(p, 1.4f, 1.08f, 0);
        assertEquals(p[0], expected);
    }
}
