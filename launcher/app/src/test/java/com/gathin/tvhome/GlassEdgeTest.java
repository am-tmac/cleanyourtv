package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

/** Failure cases specified before implementation: wrong SDF sign, inward/corner normals,
 * interior distortion, asymmetric light reversed, unlit glow, broken AA, shader/mirror drift. */
public class GlassEdgeTest {
    private static final float EPS = 0.0001f;

    @Test public void sdfInsideOutsideAndRoundedCorner() {
        assertEquals(-50, GlassEdge.sdf(100, 50, 200, 100, 20), EPS);
        assertEquals(0, GlassEdge.sdf(100, 0, 200, 100, 20), EPS);
        assertEquals(3, GlassEdge.sdf(100, -3, 200, 100, 20), EPS);
        assertEquals((float) Math.sqrt(800) - 20, GlassEdge.sdf(0, 0, 200, 100, 20), EPS);
        float corner = 20 - 20 / (float) Math.sqrt(2);
        assertEquals(0, GlassEdge.sdf(corner, corner, 200, 100, 20), EPS);
    }

    @Test public void outwardNormalsOnEverySideAndCorner() {
        float[][] cases = {{0,50,-1,0}, {200,50,1,0}, {100,0,0,-1}, {100,100,0,1},
                {0,0,-1,-1}, {200,0,1,-1}, {0,100,-1,1}, {200,100,1,1}};
        float[] n = new float[2];
        for (float[] c : cases) {
            GlassEdge.normal(c[0], c[1], 200, 100, 20, n);
            float len = (float) Math.hypot(c[2], c[3]);
            assertEquals(c[2] / len, n[0], EPS);
            assertEquals(c[3] / len, n[1], EPS);
        }
    }

    @Test public void pillNormalsAndSdfStayFiniteAtCentre() {
        float[] n = new float[2];
        GlassEdge.normal(100, 25, 200, 50, 25, n);
        assertTrue(Float.isFinite(n[0]) && Float.isFinite(n[1]));
        assertEquals(-25, GlassEdge.sdf(100, 25, 200, 50, 25), EPS);
        assertEquals(0, GlassEdge.sdf(0, 25, 200, 50, 25), EPS);
    }

    @Test public void refractionZeroDeepInsideMaximumAtRimAndQuadratic() {
        assertEquals(26, GlassEdge.zone(100, 50), EPS);
        assertEquals(14, GlassEdge.zone(100, 10), EPS);
        float zone = GlassEdge.zone(258, 39);
        assertEquals(zone * .9f, GlassEdge.offset(0, zone), EPS);
        assertEquals(zone * .9f / 4, GlassEdge.offset(zone / 2, zone), EPS);
        assertEquals(0, GlassEdge.offset(zone, zone), EPS);
        assertEquals(0, GlassEdge.offset(zone * 2, zone), EPS);
    }

    @Test public void topLeftRimBrighterThanBottomRightAndEndsAtRimWidth() {
        float topLeft = GlassEdge.facing(-.70710677f, -.70710677f);
        float bottomRight = GlassEdge.facing(.70710677f, .70710677f);
        assertTrue(GlassEdge.rim(0, topLeft) > GlassEdge.rim(0, bottomRight));
        assertEquals(0, GlassEdge.rim(2.2f, topLeft), EPS);
        assertEquals(0, GlassEdge.rim(30, topLeft), EPS);
        assertTrue(GlassEdge.rim(0, bottomRight) > 0);
    }

    @Test public void innerGlowOnlyInsideLitBand() {
        assertEquals(.144f, GlassEdge.glow(0, 30, GlassEdge.facing(0, -1)), EPS);
        assertEquals(0, GlassEdge.glow(0, 30, GlassEdge.facing(0, 1)), EPS);
        assertEquals(0, GlassEdge.glow(30, 30, 1), EPS);
        assertEquals(0, GlassEdge.glow(40, 30, 1), EPS);
    }

    @Test public void antialiasCoverageClampsAndCrossesHalfAtBoundary() {
        assertEquals(1, GlassEdge.alpha(-1), EPS);
        assertEquals(.75f, GlassEdge.alpha(-.25f), EPS);
        assertEquals(.5f, GlassEdge.alpha(0), EPS);
        assertEquals(.25f, GlassEdge.alpha(.25f), EPS);
        assertEquals(0, GlassEdge.alpha(1), EPS);
    }

    @Test public void cornerNormalTurnsSmoothlyAcrossTheDiagonal() {
        // Dock-sized box: just either side of the 45-degree line inside the edge band, the normal used to
        // flip between (-1,0) and (0,-1) - the mitred seam. It must now be nearly the same direction.
        float[] a = new float[2], b = new float[2];
        GlassEdge.normal(50, 51, 1686, 258, 39, a);
        GlassEdge.normal(51, 50, 1686, 258, 39, b);
        assertTrue(a[0] * b[0] + a[1] * b[1] > .99f);
        assertEquals(1, Math.hypot(a[0], a[1]), EPS);
        // Straight edges are unchanged.
        GlassEdge.normal(843, 10, 1686, 258, 39, a);
        assertEquals(0, a[0], EPS); assertEquals(-1, a[1], EPS);
    }

    @Test public void shaderContainsMirrorEquationsAndAllTigrisConstants() {
        String s = GlassEdge.AGSL;
        for (String constant : new String[]{"0.45", "0.9", "0.3", "0.7", "0.35", "0.6",
                "0.16", "0.26", "1.4", "2.2"}) assertTrue(constant, s.contains(constant));
        for (String equation : new String[]{"-(n.x * 0.45 + n.y * 0.9)",
                "0.3 + 0.7 * max(facing, 0.0) + 0.35 * max(-facing, 0.0)",
                "min(size.y * 0.26, radius * 1.4)", "t * t * zone * 0.9",
                "(0.35 + 0.6 * light)", "0.16 * max(facing, 0.0)",
                "clamp(0.5 - sdf, 0.0, 1.0)", "backdrop.eval(p + n * off)",
                "mix(color.rgb, half3(1.0), tint)", "min(radius + zone, min(size.x, size.y) * 0.5)"}) assertTrue(equation, s.contains(equation));
    }
}
