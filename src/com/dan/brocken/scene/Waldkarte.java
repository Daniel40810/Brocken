package com.dan.brocken.scene;

import com.dan.cliff.CNoise;

import java.util.Locale;

/**
 * Phase 9: der Wald über dem Hauptraster zu 25 m: wo Wald steht ({@link #COVER}), welcher Anteil der Fichten tot ist
 * ({@link #DEAD}), wo an der Baumgrenze Windflüchter stehen ({@link #FLAG}) und wo Buchen beigemischt sind
 * ({@link #BEECH}); dazu, wie offen ein Ort zum Sturm aus Westsüdwest liegt ({@link #exposure}).
 * <p>
 * Geschätzt [G], ein Luftbild (DOP20) liegt nicht vor:
 * <ul>
 * <li><b>Wald</b>, wo ALKIS „Wald“ oder „Gehölz“ sagt und in Niedersachsen („ohne Angabe“); nicht im Moor, nicht auf
 * der Heide, halb im Blockfeld. Geschlossen bis rund 1 020 m, darüber licht, über 1 090 m keiner (Baumgrenze am
 * Brocken bei rund 1 100 m).</li>
 * <li><b>Windflüchter</b> von 980 bis 1 130 m, am Rand der Heide.</li>
 * <li><b>Totholz</b>: rund 90 % der Fichten sind seit 2018 abgestorben (Nationalpark Harz, 2023); in Flecken von einigen
 * hundert Metern zwischen 60 und 98 %, zur Baumgrenze hin weniger (der Käfer mag die Kälte nicht).</li>
 * <li><b>Buche</b> unter 700 m beigemischt (bis 60 % unter 500 m).</li>
 * <li><b>Sturm</b>: Lee-Winkel nach Winstral (wie im Windfeld) zur Richtung 245°, bis 400 m; wo nichts luvwärts
 * schützt, liegen mehr Stämme (Windwurf).</li>
 * </ul>
 */
public final class Waldkarte {
    public static final double CELL = 25;
    public static final int COVER = 0, DEAD = 1, FLAG = 2, BEECH = 3;
    /** Woher die Stürme kommen, die den Wald werfen (Grad) [G: Kyrill 2007, Friederike 2018 aus West bis Südwest]. */
    public static final double STURM_AUS = 245;
    /** Anteil der abgestorbenen Fichten im Mittel (Nationalpark Harz 2023: „90 Prozent des ursprünglichen Fichtenbestands“). */
    public static final double TOT = 0.9;

    public final double x0, z0;
    public final int nx, nz;
    private final byte[] data;    // RGBA: Wald, Anteil tot, Windflüchter, Buche
    private final float[] expo;   // 0 geschützt .. 1 offen zum Sturm
    private final long millis;
    private final double[] share = new double[4];

    private Waldkarte(double x0, double z0, int nx, int nz, byte[] data, float[] expo, long millis) {
        this.x0 = x0; this.z0 = z0; this.nx = nx; this.nz = nz; this.data = data; this.expo = expo; this.millis = millis;
        for (int i = 0; i < nx * nz; i++) for (int k = 0; k < 4; k++) share[k] += (data[i * 4 + k] & 0xFF) / 255.0;
        for (int k = 0; k < 4; k++) share[k] /= (double) nx * nz;
    }

    public byte[] rgba() { return data; }
    public double size() { return Math.max(nx, nz) * CELL; }

    /** Ein Kanal (COVER, DEAD, FLAG, BEECH) am Ort, bilinear; außerhalb 0. */
    public double at(int k, double x, double z) {
        double u = (x - x0) / CELL - 0.5, v = (z - z0) / CELL - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fu = u - i, fv = v - j, s = 0;
        for (int b = 0; b <= 1; b++)
            for (int a = 0; a <= 1; a++) {
                int ii = i + a, jj = j + b;
                if (ii < 0 || jj < 0 || ii >= nx || jj >= nz) continue;
                s += (data[(jj * nx + ii) * 4 + k] & 0xFF) / 255.0 * (a == 0 ? 1 - fu : fu) * (b == 0 ? 1 - fv : fv);
            }
        return s;
    }

    public double cover(double x, double z) { return at(COVER, x, z); }
    public double dead(double x, double z) { return at(DEAD, x, z); }
    public double flag(double x, double z) { return at(FLAG, x, z); }
    public double beech(double x, double z) { return at(BEECH, x, z); }

    /** Wie offen der Ort zum Sturm liegt (0 geschützt, 1 offen), nächste Zelle. */
    public double exposure(double x, double z) {
        int i = (int) Math.floor((x - x0) / CELL), j = (int) Math.floor((z - z0) / CELL);
        if (i < 0 || j < 0 || i >= nx || j >= nz) return 0.5;
        return expo[j * nx + i];
    }

    /** Mittlere Anteile: Wald, tot (über alles), Windflüchter, Buche. */
    public double[] anteile() { return share.clone(); }

    /** Anteil der Fichten, die tot sind, gewichtet mit dem Wald (über das Hauptraster). */
    public double totImWald() {
        double a = 0, b = 0;
        for (int i = 0; i < nx * nz; i++) {
            double c = (data[i * 4] & 0xFF) / 255.0, d = (data[i * 4 + 1] & 0xFF) / 255.0, be = (data[i * 4 + 3] & 0xFF) / 255.0;
            a += c * (1 - be) * d;
            b += c * (1 - be);
        }
        return b > 0 ? a / b : 0;
    }

    /** „Wald 12,7 km zu 25 m: 72 % bewaldet, davon 88 % der Fichten tot, Windflüchter 0,4 %, Buche 3 % (0,5 s, geschätzt)“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Wald %.1f km zu %.0f m: %.0f %% bewaldet, %.0f %% der Fichten tot, Windflüchter %.1f %%, Buche %.0f %% (%.1f s, geschätzt)",
                size() / 1000, CELL, share[0] * 100, totImWald() * 100, share[2] * 100, share[3] * 100, millis / 1000.0);
    }

    /** Die Karte über dem Hauptraster aus Gelände, Nutzung und Bodenkarte rechnen. */
    public static Waldkarte build(Terrain t, Bodenkarte boden) {
        long t0 = System.currentTimeMillis();
        double[] hr = Site.HAUPTRASTER.scene();
        double x0 = hr[0], z0 = hr[1];
        int nx = (int) Math.ceil((hr[2] - hr[0]) / CELL), nz = (int) Math.ceil((hr[3] - hr[1]) / CELL);
        byte[] out = new byte[nx * nz * 4];
        float[] expo = new float[nx * nz];
        double a = Math.toRadians(STURM_AUS), ux = Math.sin(a), uz = -Math.cos(a);   // zum Sturm hin (luvwärts)
        java.util.stream.IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                double x = x0 + (i + 0.5) * CELL, z = z0 + (j + 0.5) * CELL, h = t.height(x, z);
                int art = t.artAt(x, z) & 0xFF;
                double wald = art == Terrain.WALD || art == Terrain.KEINE ? 1 : art == Terrain.GEHOELZ ? 0.7 : 0;
                double moor = boden.moor(x, z), heide = boden.heide(x, z), block = boden.block(x, z), fels = boden.fels(x, z);
                boolean bebaut = art == Terrain.SIEDLUNG || art == Terrain.WEG || art == Terrain.STRASSE || art == Terrain.BAHN || art == Terrain.WASSER;
                double frei = (1 - moor) * (1 - heide) * (1 - 0.5 * block) * (1 - fels);
                double cover = wald * (1 - smooth(1020, 1090, h)) * frei;
                double flag = bebaut ? 0 : smooth(980, 1040, h) * (1 - smooth(1100, 1135, h)) * (1 - moor) * (1 - 0.6 * heide) * (1 - fels)
                        * (wald > 0 || art == Terrain.HEIDE || art == Terrain.OFFEN ? 1 : 0.3);
                double fleck = CNoise.value(x / 320, 0.5, z / 320, 91) * 0.7 + CNoise.value(x / 90, 1.5, z / 90, 92) * 0.3;
                double dead = clamp(TOT + (fleck - 0.5) * 0.8, 0.55, 0.98) * (1 - 0.4 * smooth(950, 1080, h));
                double beech = 0.6 * smooth(700, 500, h);
                // Sturm: der größte Winkel zu einer Kante luvwärts bis 400 m (positiv = Schutz)
                double best = -1;
                for (double d = 12.5; d <= 400; d += 12.5) best = Math.max(best, Math.atan2(t.height(x + ux * d, z + uz * d) - h, d));
                expo[j * nx + i] = (float) smooth(0.12, -0.04, best);
                int o = (j * nx + i) * 4;
                out[o] = b(bebaut ? 0 : cover);
                out[o + 1] = b(dead);
                out[o + 2] = b(flag);
                out[o + 3] = b(beech);
            }
        });
        return new Waldkarte(x0, z0, nx, nz, out, expo, System.currentTimeMillis() - t0);
    }

    static byte b(double v) { return (byte) Math.round(clamp(v, 0, 1) * 255); }
    static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
