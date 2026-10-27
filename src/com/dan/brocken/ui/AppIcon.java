package com.dan.brocken.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Das Programmsymbol (F24 A): die Kuppe des Brockens über dem Nebelmeer mit dem Turm, die Dampffahne der Lok, die der
 * Wind nach Osten zieht, der Morgenhimmel, Goldkante wie die Vorgänger. Phase 1 malt es, Phase 18 legt die PNGs ins Jar
 * ({@code com/dan/brocken/ui/icon/brocken_NN.png}) und schreibt {@code Brocken.ico} für die Verknüpfung. Fehlt eine
 * Größe, malt die App sie selbst.
 */
public final class AppIcon {
    public static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private AppIcon() { }

    public static List<Image> images() {
        List<Image> l = new ArrayList<>();
        for (int s : SIZES) l.add(load(s));
        return l;
    }

    public static BufferedImage load(int size) {
        try (InputStream in = AppIcon.class.getResourceAsStream("icon/brocken_" + size + ".png")) {
            if (in != null) {
                BufferedImage b = javax.imageio.ImageIO.read(in);
                if (b != null) return b;
            }
        } catch (java.io.IOException e) { /* gemaltes Symbol */ }
        return paint(size);
    }

    /** Das Symbol, in jeder Größe neu gemalt. */
    public static BufferedImage paint(int s) {
        BufferedImage im = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double r = s * 0.18, inset = Math.max(1, s * 0.035);
        // Goldkante
        g.setPaint(new GradientPaint(0, 0, new Color(240, 210, 120), s, s, new Color(150, 105, 30)));
        g.fill(new RoundRectangle2D.Double(0, 0, s, s, r * 2, r * 2));
        RoundRectangle2D inner = new RoundRectangle2D.Double(inset, inset, s - 2 * inset, s - 2 * inset, r * 1.7, r * 1.7);
        g.setClip(inner);
        double fog = s * 0.66;
        // Morgenhimmel: oben Blau, über dem Nebel Gold
        g.setPaint(new GradientPaint(0, 0, new Color(27, 44, 69), 0, (float) fog, new Color(240, 200, 138)));
        g.fill(new Rectangle2D.Double(0, 0, s, fog + 1));
        // Kuppe
        Path2D k = new Path2D.Double();
        k.moveTo(-1, fog + 2);
        k.curveTo(s * 0.18, fog - s * 0.04, s * 0.30, s * 0.34, s * 0.50, s * 0.32);
        k.curveTo(s * 0.70, s * 0.34, s * 0.82, fog - s * 0.04, s + 1, fog + 2);
        k.closePath();
        g.setPaint(new GradientPaint(0, (float) (s * 0.32), new Color(96, 84, 80), 0, (float) fog, new Color(36, 38, 40)));
        g.fill(k);
        // Turm mit Kuppel
        double tw = Math.max(1.2, s * 0.07), th = s * 0.15, tx = s * 0.53;
        g.setColor(new Color(40, 36, 34));
        g.fill(new Rectangle2D.Double(tx - tw / 2, s * 0.33 - th, tw, th + 1));
        g.fill(new Ellipse2D.Double(tx - tw * 0.7, s * 0.33 - th - tw * 0.45, tw * 1.4, tw * 0.9));
        // Dampffahne: von der Westflanke über die Kuppe nach Osten
        if (s >= 24) {
            g.setColor(new Color(242, 237, 227, 215));
            for (int i = 0; i < 7; i++) {
                double u = i / 6.0, cx = s * (0.30 + 0.62 * u), cy = s * (0.44 - 0.10 * u), rr = s * (0.035 + 0.05 * u);
                g.setColor(new Color(242, 237, 227, (int) (215 * (1 - 0.65 * u))));
                g.fill(new Ellipse2D.Double(cx - rr, cy - rr, rr * 2, rr * 2));
            }
            g.setColor(new Color(23, 19, 15));
            g.fill(new Rectangle2D.Double(s * 0.24, s * 0.46, s * 0.09, s * 0.04));
            g.setColor(new Color(200, 70, 60));
            g.fill(new Rectangle2D.Double(s * 0.24, s * 0.50, s * 0.09, Math.max(1, s * 0.012)));
        }
        // Nebelmeer in Lagen
        for (int i = 0; i < 3; i++) {
            double y = fog - s * 0.02 + i * s * 0.09;
            Path2D w = new Path2D.Double();
            w.moveTo(-1, s + 1);
            w.lineTo(-1, y);
            int n = 6;
            for (int j = 0; j <= n; j++) {
                double x = s * j / (double) n;
                w.quadTo(x - s / (2.0 * n), y - s * (0.035 + 0.01 * ((i + j) % 3)), x, y);
            }
            w.lineTo(s + 1, s + 1);
            w.closePath();
            int c = 232 - i * 16;
            g.setColor(new Color(c, c - 4, c - 12, 235));
            g.fill(w);
        }
        g.setClip(null);
        g.setColor(new Color(255, 236, 180, 120));
        g.setStroke(new BasicStroke((float) Math.max(0.6, s * 0.012)));
        g.draw(inner);
        g.dispose();
        return im;
    }

    /** Setzt das Symbol am Fenster und, wo unterstützt, in der Taskleiste. */
    public static void install(java.awt.Window w) {
        List<Image> l = images();
        w.setIconImages(l);
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar tb = java.awt.Taskbar.getTaskbar();
                if (tb.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) tb.setIconImage(l.get(l.size() - 1));
            }
        } catch (Exception | Error ignored) {
            // Windows nimmt ohnehin die Fenstersymbole
        }
    }
}
