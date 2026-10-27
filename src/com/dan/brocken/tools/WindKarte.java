package com.dan.brocken.tools;

import com.dan.brocken.scene.Site;
import com.dan.brocken.scene.Terrain;
import com.dan.brocken.scene.Windrechner;
import com.dan.brocken.wiring.Wiring;
import com.dan.wind.WindField;
import com.dan.wind.WindSolution;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Phase 4: das Windfeld über dem Hauptraster als Karte, ohne Grafikkarte. Drei Lagen nebeneinander (Westlage mit Stau,
 * Ostwind bei Sonne, Nacht mit Inversion): Farbe nach der Geschwindigkeit in 10 m über Grund (geteilt durch den Wind
 * der Lage im flachen Wald), Höhenlinien, Pfeile, Rauchfäden in 30 m, die Orte. Darunter je ein Schnitt in Windrichtung
 * über den Gipfel. Schreibt {@code docs/status/Wind_Phase4.png} und die Zahlen ans Ende (Gipfel, Orte, Masse).
 * <pre>WindKarte [datei.png] [NIEDRIG|MITTEL]</pre>
 */
public final class WindKarte {
    private WindKarte() { }

    record Lage(String name, double speed, double fromDeg, WindField.Stability st, double heating) { }

    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "docs/status/Wind_Phase4.png");
        WindField.Quality q = a.length > 1 && a[1].equalsIgnoreCase("NIEDRIG") ? WindField.Quality.LOW : WindField.Quality.MEDIUM;
        Terrain t = Terrain.load(Terrain.defaultDir());
        if (!t.measured()) System.out.println("Hinweis: ohne daten/gelaende – die Kuppe der Bühne");
        Lage[] lagen = {new Lage("Westlage mit Stau, Bft 7 am Gipfel", 6.5, 260, WindField.Stability.NEUTRAL, 0.1),
                new Lage("Ostwind bei Sonne (Hochdruck, Mittag)", 3.0, 90, WindField.Stability.LABIL, 0.8),
                new Lage("Klare Nacht mit Inversion: Kaltluft, Hangwind hinab", 1.0, 120, WindField.Stability.INVERSION, -1)};
        int pw = 620, ph = 580, sh = 220;
        BufferedImage im = new BufferedImage(pw * lagen.length, ph + sh + 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(15, 19, 21));
        g.fillRect(0, 0, im.getWidth(), im.getHeight());
        double[] r = Windrechner.fenster();
        for (int l = 0; l < lagen.length; l++) {
            Lage lg = lagen[l];
            WindField f = new WindField();
            Wiring.setTerrainOn(f, t);
            f.speed = lg.speed;
            f.fromDeg = lg.fromDeg;
            f.stability = lg.st;
            f.heating = lg.heating;
            f.top = Site.WIND_HOEHE;
            f.quality = q;
            WindSolution so = f.solve(r[0], r[1], r[2], r[3]);
            System.out.printf(Locale.GERMAN, "%s: %s%n", lg.name, so.describe());
            double ref = Math.max(0.3, f.profile(10, 0.9));
            karte(g, l * pw, 0, pw, ph, t, f, so, r, ref);
            schnitt(g, l * pw, ph, pw, sh, t, f, so);
            g.setColor(new Color(232, 234, 233));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            g.drawString(lg.name, l * pw + 12, 22);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            double gipfel = so.speedAt(Site.GIPFELSTEIN.x(), 10, Site.GIPFELSTEIN.z());
            String orte = String.format(Locale.GERMAN, "10 m über Grund: Gipfel %.1f · Torfhaus %.1f · Schierke %.1f · Wurmberg %.1f m/s (Lage %.1f m/s über Wald)",
                    gipfel, so.speedAt(Site.TORFHAUS.x(), 10, Site.TORFHAUS.z()), so.speedAt(Site.SCHIERKE.x(), 10, Site.SCHIERKE.z()),
                    so.speedAt(Site.WURMBERG.x(), 10, Site.WURMBERG.z()), ref);
            System.out.println("  " + orte + String.format(Locale.GERMAN, " · Kaltluft bei Schierke %.0f m", so.coldAt(Site.SCHIERKE.x(), Site.SCHIERKE.z())));
            g.drawString(orte, l * pw + 12, 40);
            g.drawString(String.format(Locale.GERMAN, "%d × %d × %d Zellen · %d Schritte · Masse höchstens %.3f %% · %.1f s", so.nx, so.nz, so.n, so.iterations,
                    so.massMax * 100, so.millis / 1000), l * pw + 12, 56);
        }
        g.setColor(new Color(186, 194, 196));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString("Brocken Phase 4 · com.dan.wind, massenkonsistent (Sherman 1978, wie WindNinja) · Farbe: Wind in 10 m über Grund geteilt durch die Lage über Wald "
                + "(blau 0,3 … gelb 1 … rot 3) · Pfeile 10 m · weiße Fäden 30 m · Schnitt in Windrichtung über den Gipfel", 12, im.getHeight() - 14);
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        javax.imageio.ImageIO.write(im, "png", out);
        System.out.println("Bild: " + out.getPath());
    }

    static Color farbe(double q) {
        // 0,3 blau … 1 gelbgrün … 3 rot (logarithmisch)
        double t = Math.max(0, Math.min(1, (Math.log(q) - Math.log(0.3)) / (Math.log(3) - Math.log(0.3))));
        float[][] c = {{0.15f, 0.25f, 0.65f}, {0.25f, 0.6f, 0.75f}, {0.75f, 0.8f, 0.35f}, {0.95f, 0.6f, 0.2f}, {0.85f, 0.2f, 0.15f}};
        double p = t * (c.length - 1);
        int i = Math.min(c.length - 2, (int) p);
        double f = p - i;
        return new Color((float) (c[i][0] + (c[i + 1][0] - c[i][0]) * f), (float) (c[i][1] + (c[i + 1][1] - c[i][1]) * f), (float) (c[i][2] + (c[i + 1][2] - c[i][2]) * f));
    }

    static void karte(Graphics2D g, int ox, int oy, int w, int h, Terrain t, WindField f, WindSolution so, double[] r, double ref) {
        double sx = w / (r[2] - r[0]), sz = h / (r[3] - r[1]), s = Math.min(sx, sz);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < h; py += 2)
            for (int px = 0; px < w; px += 2) {
                double x = r[0] + px / s, z = r[1] + py / s;
                if (x > r[2] || z > r[3]) continue;
                double q = so.speedAt(x, 10, z) / ref;
                double gr = t.height(x, z), gx = t.height(x + 25, z) - t.height(x - 25, z), gz = t.height(x, z + 25) - t.height(x, z - 25);
                double shade = 0.75 + 0.25 * Math.max(-1, Math.min(1, (-gx * 0.7 - gz * 0.7) / 30));
                Color c = farbe(q);
                int rgb = new Color((int) Math.min(255, c.getRed() * shade), (int) Math.min(255, c.getGreen() * shade), (int) Math.min(255, c.getBlue() * shade)).getRGB();
                for (int dy = 0; dy < 2 && py + dy < h; dy++) for (int dx = 0; dx < 2 && px + dx < w; dx++) im.setRGB(px + dx, py + dy, rgb);
            }
        g.drawImage(im, ox, oy, null);
        // Höhenlinien alle 100 m
        g.setColor(new Color(255, 255, 255, 50));
        for (int py = 0; py < h; py += 2)
            for (int px = 0; px < w; px += 2) {
                double x = r[0] + px / s, z = r[1] + py / s;
                double a = t.height(x, z), b = t.height(x + 2 / s, z), c = t.height(x, z + 2 / s);
                if (Math.floor(a / 100) != Math.floor(b / 100) || Math.floor(a / 100) != Math.floor(c / 100)) g.fillRect(ox + px, oy + py, 1, 1);
            }
        g.setStroke(new BasicStroke(1.1f));
        double step = 500;
        for (double z = r[1] + step / 2; z < r[3]; z += step)
            for (double x = r[0] + step / 2; x < r[2]; x += step) {
                double[] v = so.at(x, so.groundAt(x, z) + 10, z);
                double u = Math.hypot(v[0], v[2]);
                if (u < 0.05) continue;
                double px = ox + (x - r[0]) * s, py = oy + (z - r[1]) * s, l = Math.min(step * s * 0.9, 4 + u / ref * 9);
                double ex = px + v[0] / u * l, ey = py + v[2] / u * l;
                g.setColor(new Color(20, 20, 20, 160));
                g.drawLine((int) px, (int) py, (int) ex, (int) ey);
                g.fillOval((int) ex - 2, (int) ey - 2, 4, 4);
            }
        g.setColor(new Color(245, 242, 235, 200));
        for (float[] p : Windrechner.faeden(f, so)) {
            Path2D path = new Path2D.Double();
            for (int i = 0; i < p.length; i += 4) {
                double px = ox + (p[i] - r[0]) * s, py = oy + (p[i + 2] - r[1]) * s;
                if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            g.draw(path);
        }
        for (Site.Ort o : Site.ORTE) {
            double px = ox + (o.x() - r[0]) * s, py = oy + (o.z() - r[1]) * s;
            if (px < ox || px > ox + w || py < oy || py > oy + h) continue;
            g.setColor(Color.WHITE);
            g.drawOval((int) px - 4, (int) py - 4, 8, 8);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.drawString(o.name(), (int) px + 6, (int) py + 4);
        }
    }

    static void schnitt(Graphics2D g, int ox, int oy, int w, int h, Terrain t, WindField f, WindSolution so) {
        double[] d = f.toVector();
        double half = 6000, top = 1900, bot = 300, sx = w / (2 * half), sy = (h - 20) / (top - bot);
        g.setColor(new Color(20, 26, 29));
        g.fillRect(ox, oy, w, h);
        g.setStroke(new BasicStroke(1f));
        for (double s = -half + 250; s < half; s += 500)
            for (double hh = 10; hh < 1200; hh *= 1.7) {
                double x = Site.GIPFELSTEIN.x() + d[0] * s, z = Site.GIPFELSTEIN.z() + d[2] * s;
                if (!so.contains(x, z)) continue;
                double gr = so.groundAt(x, z), y = gr + hh;
                if (y > top) break;
                double[] v = so.at(x, y, z);
                double along = v[0] * d[0] + v[2] * d[2], up = v[1], u = Math.hypot(along, up);
                if (u < 0.05) continue;
                double px = ox + (s + half) * sx, py = oy + h - 10 - (y - bot) * sy, l = 4 + u * 1.4;
                g.setColor(farbe(u / Math.max(0.3, f.profile(10, 0.9))));
                g.drawLine((int) px, (int) py, (int) (px + along / u * l), (int) (py - up / u * l));
            }
        Path2D gp = new Path2D.Double();
        gp.moveTo(ox, oy + h);
        for (int i = 0; i <= w; i += 2) {
            double s = -half + i / sx, x = Site.GIPFELSTEIN.x() + d[0] * s, z = Site.GIPFELSTEIN.z() + d[2] * s;
            gp.lineTo(ox + i, oy + h - 10 - (t.height(x, z) - bot) * sy);
        }
        gp.lineTo(ox + w, oy + h);
        gp.closePath();
        g.setColor(new Color(45, 74, 55));
        g.fill(gp);
        g.setColor(new Color(186, 194, 196));
        g.drawString("Schnitt in Windrichtung über den Gipfel (±6 km, 300–1 900 m)", ox + 10, oy + 16);
    }
}
