package com.dan.brocken.tools;

import com.dan.brocken.data.Alkis;
import com.dan.brocken.scene.Geo;
import com.dan.brocken.scene.Site;
import com.dan.brocken.scene.Terrain;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Der Import des Geländes (Phase 2, Ant-Target {@code gelaende}). Liest aus {@code templates/}:
 * <ol>
 * <li><b>GLO-30</b> ({@code rasters_COP30*.tar.gz} oder ein ausgepacktes {@code *.tif}, WGS84, 1″) und tastet es auf das
 * Hauptraster um (UTM 32 N, 12,5 m, bikubisch);</li>
 * <li><b>DGM200</b> ({@code dgm200*.zip} mit dem ASCII-Raster des BKG) für den Fernring (200 m) und für den Abgleich;</li>
 * <li><b>ALKIS</b> ({@code daten/alkis/nutzung.bin}, aus {@code tools/alkis_auszug.py}) für die Nutzungsart je Zelle;</li>
 * <li><b>DGM1</b>, wenn vorhanden ({@code templates/dgm1/*.tif}, {@code *.xyz}, {@code *.txt}, {@code *.asc}), für das
 * Kernraster (2 m, gemittelt).</li>
 * </ol>
 * GLO-30 ist ein Oberflächenmodell: über Wald liegt es auf dem Kronendach. Wie hoch, misst der Import selbst: auf ebenen
 * 200-m-Zellen des DGM200 (Boden) wird der Unterschied GLO-30 − DGM200 gegen den Waldanteil aus ALKIS ausgeglichen,
 * d = a + b · Wald. b ist der Waldaufschlag; er wird über Wald abgezogen, an den Rändern weich (F2 A). a ist der
 * Versatz auf offenen Flächen; er wird nur gemeldet, nicht abgezogen: dort trägt GLO-30 Bewuchs und Bauten, und der
 * Bezug (EGM2008 gegen NHN) weicht im Harz nur um Dezimeter ab. Abgezogen läge der Gipfel 1,4 m zu tief (Befund Phase 2). Wo ALKIS nichts sagt (Niedersachsen), gilt ein Waldanteil von
 * {@link #WALD_OHNE_ANGABE} unter {@link #WALD_BIS} m [G].
 * <p>
 * Schreibt {@code daten/gelaende/} ({@link Terrain}) und das Statusbild {@code docs/status/Gelaende_Import.png}.
 * Aufruf: {@code GelaendeImport [templates] [daten/gelaende] [bild.png]}.
 */
public final class GelaendeImport {
    /** Waldanteil, wo ALKIS nichts sagt (Nationalpark Niedersachsen), und bis zu welcher Höhe [G]. */
    public static final double WALD_OHNE_ANGABE = 0.85, WALD_BIS = 1050;
    /** Breite (m), auf der das DGM1 an Datenlücken (fehlende Kacheln) in das Hauptraster überblendet. */
    public static final double LUECKE = 150;
    /** Höchste Neigung (m/m) einer DGM200-Zelle, die in den Abgleich eingeht. */
    static final double ABGLEICH_NEIGUNG = 0.08;

    private GelaendeImport() { }

    public static void main(String[] a) throws Exception {
        File tpl = new File(a.length > 0 ? a[0] : "templates"), out = new File(a.length > 1 ? a[1] : Terrain.defaultDir().getPath());
        File png = new File(a.length > 2 ? a[2] : "docs/status/Gelaende_Import.png");
        long t0 = System.nanoTime();
        Bericht b = importieren(tpl, Alkis.ordner());
        b.terrain.save(out);
        System.out.println("Gelände: " + out.getPath() + " · " + b.terrain.describe());
        for (String l : b.zeilen) System.out.println("  " + l);
        bild(b, png);
        System.out.printf(Locale.GERMAN, "Bild: %s · %.1f s%n", png.getPath(), (System.nanoTime() - t0) / 1e9);
    }

    /** Was der Import gerechnet hat (für das Bild und die Prüfung). */
    public static final class Bericht {
        public Terrain terrain;
        public double versatz, aufschlag, rest;
        public int abgleichZellen, dgm1Kacheln;
        public double gloAnteil, dgm1Anteil;
        public final List<String> zeilen = new ArrayList<>();
    }

    public static Bericht importieren(File tpl, File alkisDir) throws IOException {
        Bericht r = new Bericht();
        GeoTiff glo = glo30(tpl);
        Raster d200 = dgm200(tpl);
        r.zeilen.add("GLO-30: " + glo);
        r.zeilen.add("DGM200: Ausschnitt " + d200.nx + " × " + d200.ny + " Zellen zu 200 m");

        // 1 Fernring aus dem DGM200
        short[] fern = new short[Terrain.FN * Terrain.FN];
        for (int j = 0; j < Terrain.FN; j++)
            for (int i = 0; i < Terrain.FN; i++) {
                double[] u = Site.toUtm(Terrain.fcx(i), Terrain.fcz(j));
                double v = d200.at(u[0], u[1]);
                fern[j * Terrain.FN + i] = Terrain.encode(Double.isNaN(v) ? 100 : v);
            }

        // 2 Nutzungsart aus ALKIS
        int n = Terrain.N;
        byte[] art = new byte[n * n];
        File nb = new File(alkisDir, Alkis.NUTZUNG);
        if (nb.isFile()) {
            List<Alkis.Objekt> nu = Alkis.lesen(nb);
            double e0 = Terrain.X0 + Site.E0, n0 = Site.N0 - Terrain.Z0;
            for (Alkis.Objekt o : nu) Alkis.rastern(o, art, n, n, e0, n0, Terrain.CELL, Alkis.art(o.code(), o.text()));
            r.zeilen.add("ALKIS: " + nu.size() + " Flächen der tatsächlichen Nutzung (© GeoBasis-DE/LVermGeo ST, dl-de/by-2-0)");
        } else r.zeilen.add("ALKIS fehlt (" + nb + "): ohne Nutzungsarten, überall Wald angenommen");

        // 3 GLO-30 auf das Hauptraster
        float[] g = new float[n * n];
        int innen = 0;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double[] ll = Site.latLon(Terrain.cx(i), Terrain.cz(j));
                double v = sampleGeo(glo, ll[0], ll[1]);
                g[j * n + i] = (float) v;
                if (!Double.isNaN(v)) innen++;
            }
        r.gloAnteil = innen / (double) (n * n);
        // leicht glätten (Binomial 1-2-1, zweimal je Richtung, σ ≈ 1 Zelle): GLO-30 (19 × 31 m) auf 12,5 m umgetastet
        // zeigt sonst ein Schwebungsmuster in der Neigung, senkrechte Streifen im Licht (Befund Phase 2); GLO-30 löst
        // ohnehin nur rund 30 m auf
        for (int pass = 0; pass < 2; pass++) g = binomial(binomial(g, n, true), n, false);

        // 4 Abgleich: GLO-30 − DGM200 = a + b · Wald auf ebenen 200-m-Zellen (nur wo ALKIS etwas sagt)
        double[] wald = new double[n * n];
        for (int k = 0; k < n * n; k++) wald[k] = waldAnteil(art[k], g[k]);
        double sw = 0, sd = 0, sww = 0, swd = 0, s1 = 0;
        List<double[]> paare = new ArrayList<>();
        int per = (int) Math.round(200 / Terrain.CELL);
        for (int bj = 1; bj < n / per - 1; bj++)
            for (int bi = 1; bi < n / per - 1; bi++) {
                double sum = 0, ws = 0;
                int c = 0, ohne = 0;
                for (int q = 0; q < per; q++)
                    for (int p = 0; p < per; p++) {
                        int k = (bj * per + q) * n + bi * per + p;
                        if (Float.isNaN(g[k])) { c = -1; break; }
                        if (art[k] == Terrain.KEINE) ohne++;
                        sum += g[k];
                        ws += wald[k];
                        c++;
                    }
                if (c <= 0 || ohne > 0) continue;
                double x = Terrain.X0 + (bi + 0.5) * per * Terrain.CELL, z = Terrain.Z0 + (bj + 0.5) * per * Terrain.CELL;
                double[] u = Site.toUtm(x, z);
                double d0 = d200.at(u[0], u[1]);
                double sl = Math.hypot(d200.at(u[0] + 200, u[1]) - d200.at(u[0] - 200, u[1]), d200.at(u[0], u[1] + 200) - d200.at(u[0], u[1] - 200)) / 400;
                if (Double.isNaN(d0) || Double.isNaN(sl) || sl > ABGLEICH_NEIGUNG) continue;
                double f = ws / c, d = sum / c - d0;
                if (f > 0.15 && f < 0.85) continue;   // nur klar bewaldet oder klar offen
                paare.add(new double[]{f, d});
                s1++; sw += f; sd += d; sww += f * f; swd += f * d;
            }
        double det = s1 * sww - sw * sw;
        if (s1 >= 20 && Math.abs(det) > 1e-9) {
            r.aufschlag = (s1 * swd - sw * sd) / det;
            r.versatz = (sd - r.aufschlag * sw) / s1;
        } else { r.aufschlag = 15; r.versatz = 0; }   // [G], zu wenig Zellen
        double rr = 0;
        for (double[] p : paare) rr += Math.pow(p[1] - r.versatz - r.aufschlag * p[0], 2);
        r.rest = paare.isEmpty() ? 0 : Math.sqrt(rr / paare.size());
        r.abgleichZellen = paare.size();
        r.zeilen.add(String.format(Locale.GERMAN, "Abgleich GLO-30 gegen DGM200 auf %d ebenen 200-m-Zellen: Waldaufschlag %.1f m (abgezogen), Versatz offen %+.2f m (nur gemeldet), Rest %.1f m",
                paare.size(), r.aufschlag, r.versatz, r.rest));

        // 5 Hauptraster: GLO-30 − Versatz − Aufschlag · Wald (über 2 Zellen weich), wo GLO-30 fehlt, das DGM200
        double[] wb = blur(blur(wald, n), n);
        short[] h = new short[n * n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                double v;
                if (Float.isNaN(g[k])) {
                    double[] u = Site.toUtm(Terrain.cx(i), Terrain.cz(j));
                    v = d200.at(u[0], u[1]);
                } else v = g[k] - Math.max(0, r.aufschlag) * wb[k];   // Versatz nur gemeldet (siehe Klassenkommentar)
                h[k] = Terrain.encode(v);
            }

        // 6 Kern aus dem DGM1 (wenn vorhanden)
        Terrain ohneKern = new Terrain(h, fern, null, art, new Properties());
        short[] kern = dgm1(new File(tpl, "dgm1"), ohneKern, r);

        Properties p = new Properties();
        p.setProperty("quelle", "GLO-30 1″ + DGM200" + (kern != null ? " + DGM1" : ""));
        p.setProperty("glo30", glo.toString());
        p.setProperty("abgleich.versatz", String.format(Locale.ROOT, "%.3f", r.versatz));
        p.setProperty("abgleich.waldaufschlag", String.format(Locale.ROOT, "%.3f", r.aufschlag));
        p.setProperty("abgleich.rest", String.format(Locale.ROOT, "%.3f", r.rest));
        p.setProperty("abgleich.zellen", Integer.toString(r.abgleichZellen));
        p.setProperty("glo30.anteil", String.format(Locale.ROOT, "%.4f", r.gloAnteil));
        p.setProperty("dgm1.kacheln", Integer.toString(r.dgm1Kacheln));
        p.setProperty("dgm1.anteil", String.format(Locale.ROOT, "%.4f", r.dgm1Anteil));
        p.setProperty("quellenvermerk", "Copernicus DEM GLO-30 (© DLR e.V. 2010–2014 und © Airbus Defence and Space GmbH 2014–2018, bereitgestellt "
                + "unter COPERNICUS durch die EU und ESA); © GeoBasis-DE / BKG 2026 (Daten verändert); © GeoBasis-DE/LVermGeo ST (Daten verändert)"
                + (kern != null ? "; © LGLN (Daten verändert)" : ""));
        r.terrain = new Terrain(h, fern, kern, art, p);
        for (Site.Ort o : Site.ORTE) {
            double y = r.terrain.height(o.x(), o.z());
            p.setProperty("ort." + o.name(), String.format(Locale.ROOT, "%.1f", y));
            r.zeilen.add(String.format(Locale.GERMAN, "%-18s %7.1f m (Werkbuch %5.0f m) · %s", o.name(), y, o.grund(), Terrain.ART[r.terrain.artAt(o.x(), o.z())]));
        }
        double[] gi = gipfel(r.terrain);
        p.setProperty("gipfel", String.format(Locale.ROOT, "%.2f %.1f %.1f", gi[2], gi[0], gi[1]));
        r.zeilen.add(String.format(Locale.GERMAN, "höchster Punkt %.1f m bei x %.0f, z %.0f (%.0f m vom Ursprung)", gi[2], gi[0], gi[1], Math.hypot(gi[0], gi[1])));
        double[] na = naehte(r.terrain);
        p.setProperty("naht.sprung", String.format(Locale.ROOT, "%.4f", na[0]));
        r.zeilen.add(String.format(Locale.GERMAN, "Nähte: größter Sprung %.3f m (Hauptraster/Fernring und Kern/Hauptraster, je 1 cm über die Kante)", na[0]));
        double[] s = r.terrain.shares();
        StringBuilder sb = new StringBuilder("Nutzung im Hauptraster:");
        for (int k = 0; k < s.length; k++) if (s[k] >= 0.001) sb.append(String.format(Locale.GERMAN, " %s %.1f %%", Terrain.ART[k], s[k] * 100));
        r.zeilen.add(sb.toString());
        return r;
    }

    /** Binomialfilter 1-2-1 waagerecht oder senkrecht; wo ein Nachbar fehlt (NaN), bleibt der Wert. */
    static float[] binomial(float[] a, int n, boolean x) {
        float[] b = new float[a.length];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int k = j * n + i, l = x ? (i > 0 ? k - 1 : k) : (j > 0 ? k - n : k), r = x ? (i < n - 1 ? k + 1 : k) : (j < n - 1 ? k + n : k);
                float v = (a[l] + 2 * a[k] + a[r]) / 4;
                b[k] = Float.isNaN(v) ? a[k] : v;
            }
        return b;
    }

    /** Waldanteil einer Zelle nach der Nutzungsart; ohne Angabe (Niedersachsen) [G] unter {@link #WALD_BIS} m. */
    static double waldAnteil(byte art, double h) {
        if (art == Terrain.WALD) return 1;
        if (art == Terrain.GEHOELZ) return 0.7;
        if (art == Terrain.KEINE) return Double.isNaN(h) || h < WALD_BIS ? WALD_OHNE_ANGABE : 0.2;
        return 0;
    }

    static double[] blur(double[] a, int n) {
        double[] b = new double[a.length];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double s = 0;
                int c = 0;
                for (int q = -1; q <= 1; q++)
                    for (int p = -1; p <= 1; p++) {
                        int ii = i + p, jj = j + q;
                        if (ii < 0 || jj < 0 || ii >= n || jj >= n) continue;
                        s += a[jj * n + ii];
                        c++;
                    }
                b[j * n + i] = s / c;
            }
        return b;
    }

    /**
     * Bikubisch (Catmull-Rom) im geografischen Raster (Pixelmitten), NaN außerhalb. Bilinear wäre die Neigung an jeder
     * Rasterzeile geknickt; GLO-30 hat Nord-Süd 31 m Abstand, auf 12,5 m umgetastet zeichneten sich die Knicke als
     * Streifen in Ost-West-Richtung ab (Befund Phase 2).
     */
    static double sampleGeo(GeoTiff t, double lat, double lon) {
        double u = (lon - t.east0) / t.sizeX - 0.5, v = (t.north0 - lat) / t.sizeY - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        if (i < 0 || j < 0 || i + 1 >= t.width || j + 1 >= t.height) return Double.NaN;
        double fx = u - i, fz = v - j;
        double[] row = new double[4];
        for (int q = -1; q <= 2; q++) {
            int jj = Math.max(0, Math.min(t.height - 1, j + q));
            row[q + 1] = cubic(t.at(Math.max(0, i - 1), jj), t.at(i, jj), t.at(i + 1, jj), t.at(Math.min(t.width - 1, i + 2), jj), fx);
        }
        return cubic(row[0], row[1], row[2], row[3], fz);
    }

    static double cubic(double p0, double p1, double p2, double p3, double t) {
        return p1 + 0.5 * t * (p2 - p0 + t * (2 * p0 - 5 * p1 + 4 * p2 - p3 + t * (3 * (p1 - p2) + p3 - p0)));
    }

    /** Der höchste Punkt im Kern (x, z, Höhe) auf 1 m genau. */
    static double[] gipfel(Terrain t) {
        double[] best = {0, 0, -1e9};
        for (double z = -400; z <= 400; z += 5) for (double x = -400; x <= 400; x += 5) { double y = t.height(x, z); if (y > best[2]) best = new double[]{x, z, y}; }
        double bx = best[0], bz = best[1];
        for (double z = bz - 5; z <= bz + 5; z += 1) for (double x = bx - 5; x <= bx + 5; x += 1) { double y = t.height(x, z); if (y > best[2]) best = new double[]{x, z, y}; }
        return best;
    }

    /** Größter Sprung der Höhe über die Kanten der Überblendbänder (je 1 cm davor und dahinter, alle 10 m entlang der Kanten). */
    public static double[] naehte(Terrain t) {
        double max = 0;
        List<double[]> kanten = new ArrayList<>();
        double s = Terrain.N * Terrain.CELL;
        kanten.add(new double[]{Terrain.X0, Terrain.Z0, s});
        kanten.add(new double[]{Terrain.X0 + Terrain.NAHT, Terrain.Z0 + Terrain.NAHT, s - 2 * Terrain.NAHT});
        if (t.hasKern()) {
            double ks = Terrain.KN * Terrain.KCELL;
            kanten.add(new double[]{Terrain.KX0, Terrain.KZ0, ks});
            kanten.add(new double[]{Terrain.KX0 + Terrain.KNAHT, Terrain.KZ0 + Terrain.KNAHT, ks - 2 * Terrain.KNAHT});
        }
        for (double[] k : kanten)
            for (double u = 0; u <= k[2]; u += 10) {
                double[][] pt = {{k[0] + u, k[1], 0, 1}, {k[0] + u, k[1] + k[2], 0, 1}, {k[0], k[1] + u, 1, 0}, {k[0] + k[2], k[1] + u, 1, 0}};
                for (double[] q : pt) {
                    double e = 0.01;
                    double a = t.height(q[0] - q[2] * e, q[1] - q[3] * e), b = t.height(q[0] + q[2] * e, q[1] + q[3] * e);
                    max = Math.max(max, Math.abs(b - a));
                }
            }
        return new double[]{max};
    }

    // ------------------------------------------------------------------ Quellen

    /** GLO-30: ein *.tif in templates oder das erste *.tif aus rasters_COP30*.tar.gz. */
    static GeoTiff glo30(File tpl) throws IOException {
        File[] tifs = tpl.listFiles((d, nm) -> nm.toLowerCase(Locale.ROOT).endsWith(".tif"));
        if (tifs != null && tifs.length > 0) return GeoTiff.read(tifs[0]);
        File[] tgz = tpl.listFiles((d, nm) -> nm.startsWith("rasters_COP30") && nm.endsWith(".tar.gz"));
        if (tgz == null || tgz.length == 0) throw new IOException("GLO-30 fehlt in " + tpl + " (rasters_COP30*.tar.gz oder *.tif)");
        File tmp = File.createTempFile("glo30", ".tif");
        tmp.deleteOnExit();
        try (InputStream in = new GZIPInputStream(Files.newInputStream(tgz[0].toPath()))) {
            byte[] head = new byte[512];
            while (in.readNBytes(head, 0, 512) == 512) {
                String name = new String(head, 0, 100, StandardCharsets.US_ASCII).trim().replace("\0", "");
                if (name.isEmpty()) break;
                long size = Long.parseLong(new String(head, 124, 12, StandardCharsets.US_ASCII).trim().replace("\0", ""), 8);
                byte[] data = in.readNBytes((int) size);
                in.readNBytes((int) ((512 - size % 512) % 512));
                if (name.toLowerCase(Locale.ROOT).endsWith(".tif")) {
                    Files.write(tmp.toPath(), data);
                    return GeoTiff.read(tmp);
                }
            }
        }
        throw new IOException(tgz[0] + ": kein .tif im Archiv");
    }

    /** Ein Ausschnitt eines Rasters in UTM (Zellmitten), Zeilen von Nord nach Süd. */
    static final class Raster {
        final double e0, n0, cell;   // Mitte der Nordwestzelle
        final int nx, ny;
        final float[] v;
        Raster(double e0, double n0, double cell, int nx, int ny) { this.e0 = e0; this.n0 = n0; this.cell = cell; this.nx = nx; this.ny = ny; v = new float[nx * ny]; }
        double at(double e, double n) {
            double u = (e - e0) / cell, w = (n0 - n) / cell;
            int i = (int) Math.floor(u), j = (int) Math.floor(w);
            if (i < 0 || j < 0 || i + 1 >= nx || j + 1 >= ny) return Double.NaN;
            double fx = u - i, fz = w - j;
            return (v[j * nx + i] * (1 - fx) + v[j * nx + i + 1] * fx) * (1 - fz) + (v[(j + 1) * nx + i] * (1 - fx) + v[(j + 1) * nx + i + 1] * fx) * fz;
        }
    }

    /** DGM200 (ESRI-ASCII im Zip des BKG): nur der Ausschnitt des Fernrings mit 2 km Rand. */
    static Raster dgm200(File tpl) throws IOException {
        File[] z = tpl.listFiles((d, nm) -> nm.startsWith("dgm200") && nm.endsWith(".zip"));
        if (z == null || z.length == 0) throw new IOException("DGM200 fehlt in " + tpl + " (dgm200*.zip)");
        double eMin = Terrain.FX0 + Site.E0 - 2000, eMax = Terrain.FX0 + Site.E0 + Terrain.FN * Terrain.FCELL + 2000;
        double nMax = Site.N0 - Terrain.FZ0 + 2000, nMin = Site.N0 - Terrain.FZ0 - Terrain.FN * Terrain.FCELL - 2000;
        try (ZipFile zf = new ZipFile(z[0])) {
            ZipEntry asc = null;
            for (ZipEntry e : java.util.Collections.list(zf.entries())) if (e.getName().endsWith(".asc")) asc = e;
            if (asc == null) throw new IOException(z[0] + ": kein .asc");
            try (BufferedReader br = new BufferedReader(new InputStreamReader(zf.getInputStream(asc), StandardCharsets.US_ASCII), 1 << 16)) {
                int ncols = 0, nrows = 0;
                double xll = 0, yll = 0, cs = 0, nod = -9999;
                for (int k = 0; k < 6; k++) {
                    String[] t = br.readLine().trim().split("\\s+");
                    switch (t[0].toLowerCase(Locale.ROOT)) {
                        case "ncols" -> ncols = Integer.parseInt(t[1]);
                        case "nrows" -> nrows = Integer.parseInt(t[1]);
                        case "xllcenter", "xllcorner" -> xll = Double.parseDouble(t[1]) + (t[0].toLowerCase(Locale.ROOT).endsWith("corner") ? 100 : 0);
                        case "yllcenter", "yllcorner" -> yll = Double.parseDouble(t[1]) + (t[0].toLowerCase(Locale.ROOT).endsWith("corner") ? 100 : 0);
                        case "cellsize" -> cs = Double.parseDouble(t[1]);
                        case "nodata_value" -> nod = Double.parseDouble(t[1]);
                        default -> throw new IOException("DGM200: Kopfzeile " + t[0] + " unbekannt");
                    }
                }
                // Achtung: die .prj nennt „8stellen“, die Koordinaten sind 6-stellig (Werkbuch, Abschnitt 4)
                double top = yll + (nrows - 1) * cs;
                int i0 = Math.max(0, (int) Math.floor((eMin - xll) / cs)), i1 = Math.min(ncols - 1, (int) Math.ceil((eMax - xll) / cs));
                int j0 = Math.max(0, (int) Math.floor((top - nMax) / cs)), j1 = Math.min(nrows - 1, (int) Math.ceil((top - nMin) / cs));
                Raster r = new Raster(xll + i0 * cs, top - j0 * cs, cs, i1 - i0 + 1, j1 - j0 + 1);
                for (int j = 0; j <= j1; j++) {
                    String line = br.readLine();
                    if (j < j0) continue;
                    String[] t = line.trim().split("\\s+");
                    for (int i = i0; i <= i1; i++) {
                        double v = Double.parseDouble(t[i]);
                        r.v[(j - j0) * r.nx + i - i0] = v == nod ? Float.NaN : (float) v;
                    }
                }
                return r;
            }
        }
    }

    /**
     * Das Kernraster aus den DGM1-Kacheln in {@code dir} (GeoTIFF in UTM 32 N oder XYZ-Text „Ost Nord Höhe“): jeder Wert
     * fällt in seine 2-m-Zelle, gemittelt; Zellen ohne Wert nehmen die Höhe aus Hauptraster und Fernring. Ohne Kacheln null.
     */
    static short[] dgm1(File dir, Terrain basis, Bericht r) throws IOException {
        File[] f = dir.isDirectory() ? dir.listFiles((d, nm) -> nm.matches("(?i).*\\.(tif|tiff|xyz|txt|asc)$")) : null;
        if (f == null || f.length == 0) {
            r.zeilen.add("DGM1: keine Kacheln in " + dir + " – der Kern nimmt das Hauptraster");
            return null;
        }
        int kn = Terrain.KN;
        double[] sum = new double[kn * kn];
        int[] cnt = new int[kn * kn];
        double ke0 = Terrain.KX0 + Site.E0, kn0 = Site.N0 - Terrain.KZ0;
        for (File x : f) {
            if (x.getName().toLowerCase(Locale.ROOT).matches(".*\\.tiff?$")) {
                GeoTiff t = GeoTiff.read(x);
                if (t.geographic()) throw new IOException(x + ": DGM1 in Länge/Breite – erwartet UTM 32 N (EPSG:25832)");
                double e0 = t.east0 > 1_000_000 ? t.east0 - 32_000_000 : t.east0;   // 8-stelliger Rechtswert mit Zone
                for (int j = 0; j < t.height; j++)
                    for (int i = 0; i < t.width; i++) {
                        float v = t.at(i, j);
                        if (Float.isNaN(v) || v < -100 || v > 3000) continue;
                        add(e0 + (i + 0.5) * t.sizeX, t.north0 - (j + 0.5) * t.sizeY, v, ke0, kn0, sum, cnt);
                    }
            } else {
                try (BufferedReader br = Files.newBufferedReader(x.toPath(), StandardCharsets.ISO_8859_1)) {
                    String l;
                    while ((l = br.readLine()) != null) {
                        String[] t = l.trim().split("[\\s,;]+");
                        if (t.length < 3) continue;
                        try {
                            double e = Double.parseDouble(t[0]), nn = Double.parseDouble(t[1]), v = Double.parseDouble(t[2]);
                            if (e > 1_000_000) e -= 32_000_000;
                            add(e, nn, v, ke0, kn0, sum, cnt);
                        } catch (NumberFormatException ex) { /* Kopfzeile */ }
                    }
                }
            }
            r.dgm1Kacheln++;
        }
        // Abstand jeder belegten Zelle zur nächsten Lücke (Schachbrett-Abstand in zwei Durchgängen, in Zellen): an Lücken
        // (fehlende Kacheln, Rand der Daten) blendet das DGM1 auf LUECKE m weich in Hauptraster und Fernring über
        float[] dist = new float[kn * kn];
        float big = kn * 2f;
        for (int c = 0; c < dist.length; c++) dist[c] = cnt[c] > 0 ? big : 0;
        for (int j = 0; j < kn; j++)
            for (int i = 0; i < kn; i++) {
                int c = j * kn + i;
                if (dist[c] == 0) continue;
                float d = big;
                if (i > 0) d = Math.min(d, dist[c - 1] + 1);
                if (j > 0) d = Math.min(d, dist[c - kn] + 1);
                if (i > 0 && j > 0) d = Math.min(d, dist[c - kn - 1] + 1.414f);
                if (i < kn - 1 && j > 0) d = Math.min(d, dist[c - kn + 1] + 1.414f);
                dist[c] = Math.min(dist[c], d);
            }
        for (int j = kn - 1; j >= 0; j--)
            for (int i = kn - 1; i >= 0; i--) {
                int c = j * kn + i;
                if (dist[c] == 0) continue;
                float d = dist[c];
                if (i < kn - 1) d = Math.min(d, dist[c + 1] + 1);
                if (j < kn - 1) d = Math.min(d, dist[c + kn] + 1);
                if (i < kn - 1 && j < kn - 1) d = Math.min(d, dist[c + kn + 1] + 1.414f);
                if (i > 0 && j < kn - 1) d = Math.min(d, dist[c + kn - 1] + 1.414f);
                dist[c] = d;
            }
        short[] k = new short[kn * kn];
        int voll = 0;
        for (int j = 0; j < kn; j++)
            for (int i = 0; i < kn; i++) {
                int c = j * kn + i;
                double b = basis.baseHeight(Terrain.kcx(i), Terrain.kcz(j));
                double v = b;
                if (cnt[c] > 0) {
                    voll++;
                    double t = Math.max(0, Math.min(1, dist[c] * Terrain.KCELL / LUECKE));
                    v = b + (sum[c] / cnt[c] - b) * t * t * (3 - 2 * t);
                }
                k[c] = Terrain.encode(v);
            }
        r.dgm1Anteil = voll / (double) (kn * kn);
        r.zeilen.add(String.format(Locale.GERMAN, "DGM1: %d Kacheln, %.1f %% des Kernrasters belegt", r.dgm1Kacheln, r.dgm1Anteil * 100));
        return k;
    }

    private static void add(double e, double n, double v, double ke0, double kn0, double[] sum, int[] cnt) {
        int i = (int) Math.floor((e - ke0) / Terrain.KCELL), j = (int) Math.floor((kn0 - n) / Terrain.KCELL);
        if (i < 0 || j < 0 || i >= Terrain.KN || j >= Terrain.KN) return;
        sum[j * Terrain.KN + i] += v;
        cnt[j * Terrain.KN + i]++;
    }

    // ------------------------------------------------------------------ Bild

    /** Farbe der Nutzungsart für Karte und Bild (dieselben Töne wie terrain.frag, in sRGB). */
    public static final int[] FARBE = {0x55705c, 0x2d4a37, 0x50694a, 0x8a6e86, 0x7f6448, 0xaa8c82, 0x7c9a5c, 0x46708f, 0xb4aaa0, 0xc8bea8, 0x5a5a5f, 0x6b5b52};

    static void bild(Bericht b, File png) throws IOException {
        Terrain t = b.terrain;
        int n = Terrain.N, w = n + 520;
        BufferedImage im = new BufferedImage(w, n, BufferedImage.TYPE_INT_RGB);
        double az = Math.toRadians(315), el = Math.toRadians(40);
        double lx = Math.sin(az) * Math.cos(el), lz = -Math.cos(az) * Math.cos(el), ly = Math.sin(el);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = Terrain.cx(i), z = Terrain.cz(j), e = Terrain.CELL;
                double hx = t.height(x + e, z) - t.height(x - e, z), hz = t.height(x, z + e) - t.height(x, z - e);
                double nx = -hx, ny = 2 * e, nz = -hz, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double s = Math.max(0, (nx * lx + ny * ly + nz * lz) / nl);
                int c = FARBE[t.art[j * n + i]];
                double k = 0.35 + 0.85 * s;
                int r = (int) Math.min(255, ((c >> 16) & 255) * k), g = (int) Math.min(255, ((c >> 8) & 255) * k), bl = (int) Math.min(255, (c & 255) * k);
                im.setRGB(i, j, (r << 16) | (g << 8) | bl);
            }
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Höhenlinien alle 50 m (100 m kräftiger)
        for (int j = 1; j < n; j++)
            for (int i = 1; i < n; i++) {
                double a = t.at(i, j), l = t.at(i - 1, j), u = t.at(i, j - 1);
                for (int step : new int[]{100, 50})
                    if (Math.floor(a / step) != Math.floor(l / step) || Math.floor(a / step) != Math.floor(u / step)) {
                        int c = im.getRGB(i, j);
                        int f = step == 100 ? 70 : 35;
                        im.setRGB(i, j, (Math.min(255, ((c >> 16) & 255) + f) << 16) | (Math.min(255, ((c >> 8) & 255) + f) << 8) | Math.min(255, (c & 255) + f));
                        break;
                    }
            }
        g.setStroke(new BasicStroke(2f));
        rect(g, Site.HAUPTRASTER, new Color(240, 240, 240));
        rect(g, Site.KERN, new Color(230, 185, 94));
        if (t.hasKern()) {
            g.setColor(new Color(159, 192, 206));
            g.drawRect(px(Terrain.KX0), pz(Terrain.KZ0), (int) (Terrain.KN * Terrain.KCELL / Terrain.CELL), (int) (Terrain.KN * Terrain.KCELL / Terrain.CELL));
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        for (Site.Ort o : Site.ORTE) {
            int x = px(o.x()), y = pz(o.z());
            g.setColor(Color.WHITE);
            g.fillOval(x - 4, y - 4, 8, 8);
            g.setColor(new Color(15, 19, 21));
            g.drawOval(x - 4, y - 4, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString(o.name(), x + 7, y + 4);
        }
        // Tafel
        g.setColor(new Color(15, 19, 21));
        g.fillRect(n, 0, w - n, n);
        g.setColor(new Color(232, 234, 233));
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 28));
        g.drawString("Brocken · Gelände", n + 20, 44);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        int y = 76;
        for (String l : b.zeilen) for (String part : wrap(l, 64)) { g.drawString(part, n + 20, y); y += 16; }
        y += 10;
        for (int k = 0; k < Terrain.ART.length; k++) {
            g.setColor(new Color(FARBE[k]));
            g.fillRect(n + 20, y - 10, 14, 12);
            g.setColor(new Color(186, 194, 196));
            g.drawString(Terrain.ART[k], n + 42, y);
            y += 16;
        }
        g.setColor(new Color(147, 156, 159));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        y = n - 52;
        for (String part : wrap(t.props.getProperty("quellenvermerk", ""), 86)) { g.drawString(part, n + 20, y); y += 14; }
        g.dispose();
        File dir = png.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        javax.imageio.ImageIO.write(im, "png", png);
    }

    static int px(double x) { return (int) Math.round((x - Terrain.X0) / Terrain.CELL); }
    static int pz(double z) { return (int) Math.round((z - Terrain.Z0) / Terrain.CELL); }

    static void rect(Graphics2D g, Site.Fenster f, Color c) {
        double[] s = f.scene();
        g.setColor(c);
        g.drawRect(px(s[0]), pz(s[1]), px(s[2]) - px(s[0]), pz(s[3]) - pz(s[1]));
    }

    static List<String> wrap(String s, int w) {
        List<String> l = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            if (cur.length() + word.length() + 1 > w && cur.length() > 0) { l.add(cur.toString()); cur.setLength(0); }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
        }
        if (cur.length() > 0) l.add(cur.toString());
        return l;
    }

    /** Für die Prüfung: Höhe eines geografischen Punkts im GLO-30. */
    static double gloAt(GeoTiff t, double lat, double lon) { return sampleGeo(t, lat, lon); }

    /** Für die Prüfung: UTM eines Punkts. */
    static double[] utm(double lat, double lon) { return Geo.UTM32N.forward(lat, lon); }
}
