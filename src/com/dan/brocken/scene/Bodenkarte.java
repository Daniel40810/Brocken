package com.dan.brocken.scene;

import com.dan.cliff.CNoise;

import java.util.Locale;

/**
 * Phase 8: was auf dem Boden liegt und wächst, 6 × 6 km um den Gipfel zu 5 m, vier Anteile 0..1 je Zelle:
 * <b>Moor</b>, <b>Heide</b> (Zwergsträucher: Besenheide, Heidelbeere, Krähenbeere), <b>Blockfeld</b> (Blockmeere und
 * Blockschutt) und <b>Fels</b> (Klippen, steile Stellen).
 * <p>
 * ALKIS kennt am Brocken fast nur Wald und die Heide der Kuppe; Moore und Blockfelder fehlen. Die Karte ist darum
 * geschätzt [G] aus dem Gelände und wenigen gesetzten Orten:
 * <ul>
 * <li><b>Moor</b>: flach (unter rund 5°), in einer Mulde oder auf einem Sattel (tiefer als das Mittel im Umkreis von
 * 60 m), zwischen 750 und 1 060 m, fleckig; dazu die Moore, die man kennt: das Goethemoor am Goetheweg und das
 * Brockenfeld im Südwesten [G, Lage aus der Karte geschätzt], beide auch am Hang bis rund 15° (Hangmoore); ALKIS „Moor und Sumpf“ und „Wasser“ zählen voll.</li>
 * <li><b>Heide</b>: über der Waldgrenze (1 085 bis 1 115 m) [G] und wo ALKIS „Heide“ sagt; nicht auf Bauten, Wegen und
 * Gleisen.</li>
 * <li><b>Blockfeld</b>: an Hängen von 10° bis 35° über 820 m in Flecken von rund 150 m; im Wald dünner Blockschutt
 * auf Hängen; dicht um die Felsburgen.</li>
 * <li><b>Fels</b>: steiler als 38° und ALKIS „Fels und Unland“, dazu der Fuß der Felsburgen.</li>
 * </ul>
 * Die Felsburgen Teufelskanzel und Hexenaltar stehen in {@link #FELSBURGEN}. Nur Java (Rauschen aus com.dan.cliff).
 */
public final class Bodenkarte {
    public static final int N = 1200;
    public static final double CELL = 5, SIZE = N * CELL, X0 = -SIZE / 2, Z0 = -SIZE / 2;
    public static final int MOOR = 0, HEIDE = 1, BLOCK = 2, FELS = 3;
    public static final String[] NAMEN = {"Moor", "Heide", "Blockfeld", "Fels"};

    /** Eine Felsburg: Name, Lage (x, z), Höhe und Breite (m), Ausrichtung (Grad), Seed. */
    public record Felsburg(String name, double x, double z, double hoehe, double breite, double richtung, int seed) { }

    /**
     * Teufelskanzel (51,79769° N, 10,61625° O, Wikipedia) und der Hexenaltar gleich daneben [G: rund 18 m nördlich];
     * Höhen und Breiten nach Fotos [G].
     */
    public static final Felsburg[] FELSBURGEN;
    static {
        double[] tk = Site.xz(new double[]{51.79769, 10.61625});
        FELSBURGEN = new Felsburg[]{
                new Felsburg("Teufelskanzel", tk[0], tk[1], 4.5, 6.5, 25, 3),
                new Felsburg("Hexenaltar", tk[0] + 4, tk[1] - 18, 3.0, 4.5, 70, 8)};
    }

    /** Ein bekanntes Moor: Mitte (Breite, Länge), Halbmesser (m) [G]. */
    record Moor(String name, double lat, double lon, double radius) { }

    /** Goethemoor am Goetheweg, Brockenfeld im Südwesten [G: Lage und Ausdehnung geschätzt]. */
    static final Moor[] MOORE = {
            new Moor("Goethemoor", 51.8005, 10.5935, 220),
            new Moor("Brockenfeld", 51.7870, 10.5900, 520)};

    private final byte[] data;   // RGBA je Zelle: Moor, Heide, Blockfeld, Fels
    private final double[] share = new double[4];
    private final long millis;

    private Bodenkarte(byte[] data, long millis) {
        this.data = data;
        this.millis = millis;
        for (int i = 0; i < N * N; i++) for (int k = 0; k < 4; k++) share[k] += (data[i * 4 + k] & 0xFF) / 255.0;
        for (int k = 0; k < 4; k++) share[k] /= (double) N * N;
    }

    /** Die Karte als RGBA8 (Zeilen nach z, Spalten nach x), für die Grafikkarte. */
    public byte[] rgba() { return data; }

    /** Ein Anteil (MOOR, HEIDE, BLOCK, FELS) am Ort, bilinear; außerhalb 0. */
    public double at(int k, double x, double z) {
        double u = (x - X0) / CELL - 0.5, v = (z - Z0) / CELL - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fu = u - i, fv = v - j, s = 0;
        for (int b = 0; b <= 1; b++)
            for (int a = 0; a <= 1; a++) {
                int ii = i + a, jj = j + b;
                if (ii < 0 || jj < 0 || ii >= N || jj >= N) continue;
                s += (data[(jj * N + ii) * 4 + k] & 0xFF) / 255.0 * (a == 0 ? 1 - fu : fu) * (b == 0 ? 1 - fv : fv);
            }
        return s;
    }

    public double moor(double x, double z) { return at(MOOR, x, z); }
    public double heide(double x, double z) { return at(HEIDE, x, z); }
    public double block(double x, double z) { return at(BLOCK, x, z); }
    public double fels(double x, double z) { return at(FELS, x, z); }

    /** Mittlere Anteile über die ganze Karte. */
    public double[] anteile() { return share.clone(); }

    /** „Boden 6 km zu 5 m: Moor 3,1 %, Heide 1,2 %, Blockfeld 4,0 %, Fels 0,2 % (0,4 s, geschätzt)“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Boden %.0f km zu %.0f m: Moor %.1f %%, Heide %.1f %%, Blockfeld %.1f %%, Fels %.1f %% (%.1f s, geschätzt)",
                SIZE / 1000, CELL, share[0] * 100, share[1] * 100, share[2] * 100, share[3] * 100, millis / 1000.0);
    }

    /** Die Karte aus dem Gelände rechnen (rund eine halbe Sekunde). */
    public static Bodenkarte build(Terrain t) {
        long t0 = System.currentTimeMillis();
        int n = N;
        float[] h = new float[n * n];
        java.util.stream.IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) h[j * n + i] = (float) t.height(X0 + (i + 0.5) * CELL, Z0 + (j + 0.5) * CELL);
        });
        // Mittel im Umkreis (Kasten 2r+1 Zellen) aus der Summentabelle
        double[] sat = new double[(n + 1) * (n + 1)];
        for (int j = 0; j < n; j++) {
            double row = 0;
            for (int i = 0; i < n; i++) {
                row += h[j * n + i];
                sat[(j + 1) * (n + 1) + i + 1] = sat[j * (n + 1) + i + 1] + row;
            }
        }
        int r = 12;   // 60 m
        double[][] moore = new double[MOORE.length][];
        for (int k = 0; k < MOORE.length; k++) {
            double[] p = Site.xz(new double[]{MOORE[k].lat(), MOORE[k].lon()});
            moore[k] = new double[]{p[0], p[1], MOORE[k].radius()};
        }
        byte[] out = new byte[n * n * 4];
        java.util.stream.IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                double x = X0 + (i + 0.5) * CELL, z = Z0 + (j + 0.5) * CELL, y = h[j * n + i];
                int i0 = Math.max(0, i - 2), i1 = Math.min(n - 1, i + 2), j0 = Math.max(0, j - 2), j1 = Math.min(n - 1, j + 2);   // ±10 m: das Raster hat 12,5 m
                double gx = (h[j * n + i1] - h[j * n + i0]) / ((i1 - i0) * CELL), gz = (h[j1 * n + i] - h[j0 * n + i]) / ((j1 - j0) * CELL);
                double slope = Math.toDegrees(Math.atan(Math.hypot(gx, gz)));
                int a0 = Math.max(0, i - r), a1 = Math.min(n, i + r + 1), b0 = Math.max(0, j - r), b1 = Math.min(n, j + r + 1);
                double mean = (sat[b1 * (n + 1) + a1] - sat[b0 * (n + 1) + a1] - sat[b1 * (n + 1) + a0] + sat[b0 * (n + 1) + a0]) / ((a1 - a0) * (b1 - b0));
                double hollow = mean - y;   // positiv in einer Mulde
                int art = t.artAt(x, z) & 0xFF;
                boolean bebaut = art == Terrain.SIEDLUNG || art == Terrain.WEG || art == Terrain.STRASSE || art == Terrain.BAHN;
                double fleck = CNoise.value(x / 90, 0.5, z / 90, 81) * 0.65 + CNoise.value(x / 25, 1.5, z / 25, 82) * 0.35;

                // Moor
                double moor = smooth(5.5, 2.0, slope) * smooth(-0.4, 1.2, hollow + 0.3 * smooth(3, 0.5, slope)) * smooth(740, 790, y) * (1 - smooth(1040, 1070, y))
                        * smooth(0.42, 0.6, fleck);
                for (double[] m : moore) {
                    double d = Math.hypot(x - m[0], z - m[1]) / m[2];
                    moor = Math.max(moor, (1 - smooth(0.6, 1.0, d + 0.25 * (fleck - 0.5))) * smooth(20, 12, slope));   // Hangmoore: bis rund 15°
                }
                if (art == Terrain.MOOR) moor = Math.max(moor, 1);
                if (art == Terrain.WASSER) moor = Math.max(moor, 0.7 * smooth(10, 4, slope));   // Bäche am Hang sind kein Moor

                // Heide
                double heide = smooth(1085, 1115, y);
                if (art == Terrain.HEIDE) heide = 1;
                heide *= 1 - 0.8 * moor;

                // Blockfeld und Fels
                double blockFleck = CNoise.value(x / 150, 2.5, z / 150, 83) * 0.7 + CNoise.value(x / 40, 3.5, z / 40, 84) * 0.3;
                double block = smooth(9, 14, slope) * (1 - smooth(32, 40, slope)) * smooth(820, 880, y) * smooth(0.56, 0.7, blockFleck);
                double schutt = (art == Terrain.WALD || art == Terrain.KEINE ? 0.12 : 0.05) * smooth(6, 14, slope) * smooth(0.4, 0.6, blockFleck);
                block = Math.max(block, schutt) * (1 - smooth(0.3, 0.7, moor));   // im Moor keine Blöcke
                double fels = smooth(34, 42, slope);
                if (art == Terrain.FELS) fels = Math.max(fels, 0.8);
                for (Felsburg f : FELSBURGEN) {
                    double d = Math.hypot(x - f.x(), z - f.z());
                    block = Math.max(block, 0.7 * (1 - smooth(f.breite(), f.breite() * 5, d)));
                    fels = Math.max(fels, 1 - smooth(f.breite() * 0.4, f.breite() * 0.9, d));
                }
                if (bebaut) { moor = 0; heide = 0; block = 0; fels = 0; }
                int o = (j * n + i) * 4;
                out[o] = b(moor);
                out[o + 1] = b(heide);
                out[o + 2] = b(block);
                out[o + 3] = b(fels);
            }
        });
        return new Bodenkarte(out, System.currentTimeMillis() - t0);
    }

    /**
     * Die Jahreszeit des Bodens am Tag im Jahr [G, nach den Blütezeiten am Brocken]: {Wollgras blüht (Juni), Besenheide
     * blüht (August), Heidelbeerlaub rot (Oktober), Winter (braun, November bis April)}, je 0..1.
     */
    public static double[] jahr(int doy) {
        double d = doy;
        return new double[]{
                smooth(140, 160, d) * (1 - smooth(185, 205, d)),
                smooth(205, 222, d) * (1 - smooth(250, 272, d)),
                smooth(255, 282, d) * (1 - smooth(305, 325, d)),
                1 - smooth(105, 140, d) * (1 - smooth(305, 330, d))};
    }

    static byte b(double v) { return (byte) Math.round(Math.max(0, Math.min(1, v)) * 255); }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
