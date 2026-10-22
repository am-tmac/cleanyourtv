package com.gathin.tvhome;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Repeatable desktop preview of GlassEdge's Java mirror, not a device/GPU screenshot.
 * From launcher, with JDK 17:
 * javac -d /tmp/cytv-preview-classes app/src/main/java/com/gathin/tvhome/{GlassEdge,BackdropBlur,LightGlassTone,AmbientGlow}.java tools/PlanATigrisEdgePreview.java
 * java -Djava.awt.headless=true -cp /tmp/cytv-preview-classes com.gathin.tvhome.PlanATigrisEdgePreview /tmp/cytv-mockup/bing.jpg /tmp/cytv-mockup/planA-tigris-edge-preview.png
 * Optional third argument: wallpaper dim percent (excluded from the glass, as in production).
 * Reference: 1920x1080, dock (117,692)..(1803,950), radius 39px, density 1.5.
 */
public final class PlanATigrisEdgePreview {
    private PlanATigrisEdgePreview() { }

    public static void main(String[] args) throws Exception {
        boolean synthetic = args[0].equals("none");
        int sw = 1920, sh = 1080;
        BufferedImage source = synthetic ? new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB)
                : ImageIO.read(new File(args[0]));
        BufferedImage screen = cover(source, sw, sh);
        BufferedImage small = cover(source, sw / 8, sh / 8);
        int bw = small.getWidth(), bh = small.getHeight();
        int[] backdrop = small.getRGB(0, 0, bw, bh, null, 0, bw);
        AmbientGlow geometry = new AmbientGlow(false, false);
        if (synthetic) {
            for (int y = 0; y < sh; y++) for (int x = 0; x < sw; x++)
                screen.setRGB(x, y, AmbientGlow.composite(0xFF000000, 0xFFE07A2A,
                        geometry.mask((x+.5f)/sw, (y+.5f)/sh, sw, sh), 1));
            for (int y = 0; y < bh; y++) for (int x = 0; x < bw; x++) {
                int a = Math.round(255 * geometry.mask((x+.5f)/bw, (y+.5f)/bh, sw, sh));
                backdrop[y*bw+x] = 0xFF000000 | a * 0x010101;
            }
        }
        BackdropBlur.blur(backdrop, bw, bh, 5, 3);
        if (!synthetic) LightGlassTone.prepare(backdrop);
        float luminance = LightGlassTone.dockLuminance(backdrop, bw, bh);
        if (synthetic) luminance = LightGlassTone.luminance(AmbientGlow.prepared(
                AmbientGlow.composite(0xFF000000, 0xFFE07A2A, luminance, 1)));
        float tint = LightGlassTone.adaptiveTint(luminance);
        if (synthetic) {
            int[] dither = new int[backdrop.length];
            java.util.Arrays.fill(dither, 0xFF646464);
            LightGlassTone.prepare(dither);
            for (int i = 0; i < backdrop.length; i++)
                backdrop[i] = (backdrop[i] & 0xFFFF00FF) | (dither[i] & 0x0000FF00);
        }
        Graphics2D g = screen.createGraphics();
        int dim = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0, Math.min(100, dim)) / 100f));
        g.setColor(Color.BLACK); g.fillRect(0, 0, sw, sh);
        g.setComposite(AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawGlass(g, backdrop, bw, bh, 117, 692, 1686, 258, 39, tint, synthetic, true);
        drawGlass(g, backdrop, bw, bh, 1338, 123, 254, 82, 41, tint, synthetic, false);
        drawGlass(g, backdrop, bw, bh, 1614, 123, 192, 82, 41, tint, synthetic, false);
        g.dispose();
        ImageIO.write(screen, "png", new File(args[1]));
        System.out.println(args[1] + " dock L=" + luminance + " tint=" + tint);
    }

    private static void drawGlass(Graphics2D g, int[] backdrop, int bw, int bh,
                                  int left, int top, int w, int h, float radius, float tint,
                                  boolean synthetic, boolean shadow) {
        int sw = 1920, sh = 1080;
        float density = 1.5f;
        if (shadow) {
        g.setStroke(new BasicStroke(8 * density));
        for (int i = 0; i < 3; i++) {
            float grow = (4 + 8 * i) * density;
            g.setColor(new Color(0, 0, 0, 0x16 - 6 * i));
            g.draw(new RoundRectangle2D.Float(left - grow, top - grow + 10 * density,
                    w + 2 * grow, h + 2 * grow, 2 * (radius + grow), 2 * (radius + grow)));
        }
        }
        int pw = w + 2, ph = h + 2;
        BufferedImage glass = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        float zone = GlassEdge.zone(h, radius);
        float[] n = new float[2];
        for (int j = 0; j < ph; j++) {
            for (int i = 0; i < pw; i++) {
                float x = i - .5f, y = j - .5f;
                float sdf = GlassEdge.sdf(x, y, w, h, radius), alpha = GlassEdge.alpha(sdf);
                if (alpha <= 0) continue;
                GlassEdge.normal(x, y, w, h, radius, n);
                float off = GlassEdge.offset(-sdf, zone);
                float sx = (left + x + n[0] * off) * bw / sw - .5f;
                float sy = (top + y + n[1] * off) * bh / sh - .5f;
                float facing = GlassEdge.facing(n[0], n[1]);
                float rim = GlassEdge.rim(-sdf, facing), glow = GlassEdge.glow(-sdf, zone, facing);
                int prepared = synthetic ? AmbientGlow.prepared(AmbientGlow.composite(0xFF000000, 0xFFE07A2A,
                        sample(backdrop, bw, bh, sx, sy, 16) / 255f, 1)) : 0;
                int color = Math.round(alpha * 255) << 24;
                for (int shift = 16; shift >= 0; shift -= 8) {
                    float c = synthetic ? (prepared >> shift) & 255 : sample(backdrop, bw, bh, sx, sy, shift);
                    if (synthetic) c = Math.max(0, Math.min(255,
                            c + sample(backdrop, bw, bh, sx, sy, 8) - 108));
                    c += (255 - c) * tint;
                    c += (255 - c) * rim;
                    c += (255 - c) * glow;
                    color |= Math.max(0, Math.min(255, Math.round(c))) << shift;
                }
                glass.setRGB(i, j, color);
            }
        }
        g.drawImage(glass, left - 1, top - 1, null);
    }

    private static BufferedImage cover(BufferedImage source, int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double scale = Math.max(w / (double) source.getWidth(), h / (double) source.getHeight());
        AffineTransform m = new AffineTransform();
        m.translate((w - source.getWidth() * scale) / 2, (h - source.getHeight() * scale) / 2);
        m.scale(scale, scale);
        g.drawImage(source, m, null); g.dispose();
        return image;
    }

    private static float sample(int[] pixels, int w, int h, float x, float y, int shift) {
        x = Math.max(0, Math.min(w - 1, x)); y = Math.max(0, Math.min(h - 1, y));
        int x0 = (int) x, y0 = (int) y, x1 = Math.min(w - 1, x0 + 1), y1 = Math.min(h - 1, y0 + 1);
        float fx = x - x0, fy = y - y0;
        float a = (pixels[y0 * w + x0] >> shift) & 255;
        float b = (pixels[y0 * w + x1] >> shift) & 255;
        float c = (pixels[y1 * w + x0] >> shift) & 255;
        float d = (pixels[y1 * w + x1] >> shift) & 255;
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }
}
