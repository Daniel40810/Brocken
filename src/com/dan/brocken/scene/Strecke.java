package com.dan.brocken.scene;

import com.dan.brocken.data.Alkis;
import com.dan.rail.Bed;
import com.dan.rail.Corridor;
import com.dan.rail.Track;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 12: die Brockenbahn im Hauptraster als Gleis aus {@code com.dan.rail}.
 * <ul>
 * <li><b>Achse</b>: OpenStreetMap war aus dem Werkzeug nicht erreichbar; die Achse kommt darum aus den Flächen der
 * Nutzung „Bahnverkehr“ im ALKIS-Auszug ({@code daten/alkis/nutzung.bin}), verdünnt auf ihre Mittellinie
 * ({@link Corridor}, Raster 2 m) und vom Ostrand des Hauptrasters (Richtung Drei Annen Hohne) bis zum Bahnhof
 * Brocken verfolgt, über 30 m geglättet.</li>
 * <li><b>Bahnhöfe</b> nach den Empfangsgebäuden im ALKIS: Schierke und Brocken (je die nächste Stelle des Gleises).</li>
 * <li><b>Gradiente</b> ({@link Track#profile}): nirgends über 33 ‰ (Brockenbahn: größte Steigung 33 ‰ [G]), in den
 * Bahnhöfen höchstens 4 ‰; zwischen den Hüllen unter und über dem Gelände bei 30 % [G] – das Höhenmodell (GLO-30)
 * liegt im Wald neben dem Gleis eher zu hoch.</li>
 * <li><b>Bahnkörper</b> ({@link Bed}, Raster 2 m): Planum 4 m, Böschung 1 : 1,5 bis 3 m Höhe, darüber gleicht das
 * Gelände flach an (1 : 6); als {@link Terrain.Korrektur} an das Gelände.</li>
 * <li><b>Bahnübergänge</b>: wo die Achse eine Fläche „Straßenverkehr“ quert.</li>
 * <li>Seit Phase 13 die <b>Kreuzungsbahnhöfe</b> Schierke und Goetheweg mit je einem Ausweichgleis links der Bergfahrt
 * (4,2 m neben dem Hauptgleis, 200 m lang [G]); Goetheweg liegt dort, wo die Gradiente 956 m erreicht (Höhe des
 * Bahnhofs nach Wikipedia); seit Phase 14 nicht mehr eben (die Strecke steigt fast überall mit 33 ‰). Der Bahnkörper
 * trägt alle Gleise.</li>
 * </ul>
 */
public final class Strecke {
    /** Woher die Strecke kommt (Ostrand, Richtung Drei Annen Hohne) und wo die Empfangsgebäude stehen (UTM 32). */
    public static final double[] OST = {617_350, 5_735_750};
    public static final double GRENZE = 0.033, BAHNHOF = 0.004, ANTEIL_DAMM = 0.3;
    /** Phase 14: Höhe des Bahnhofs Brocken (m; Wikipedia, „Brocken“: 1 125 m ü. NHN). */
    public static final double BROCKEN_HOEHE = 1125;
    /** Phase 13: Höhe des Bahnhofs Goetheweg (m), Ausweichgleise: Versatz (m, − links der Bergfahrt), halbe Länge, Weichen (m) [G]. */
    public static final double GOETHEWEG = 956, AUSWEICHE = -4.2, AUSWEICHE_HALB = 100, WEICHE = 35;

    public final Track gleis;
    public final Bed bett;
    public final List<double[]> uebergaenge;
    public final Corridor trasse;
    /** Phase 13: Ausweichgleise (Schierke, Goetheweg) und die Stellen der Bahnhöfe auf dem Hauptgleis (m; −1 = keiner). */
    public final List<Track> ausweichen = new ArrayList<>();
    public double sSchierke = -1, sGoetheweg = -1, sBrocken;
    private final double[] boden;
    private final long millis;

    private Strecke(Track gleis, Bed bett, List<double[]> ue, Corridor trasse, double[] boden, long millis) {
        this.gleis = gleis; this.bett = bett; this.uebergaenge = ue; this.trasse = trasse; this.boden = boden; this.millis = millis;
    }

    static double[] szene(double e, double n) { return new double[]{e - Site.E0, -(n - Site.N0)}; }

    static double[][] ringe(Alkis.Objekt o) {
        double[][] r = new double[o.ringe().length][];
        for (int k = 0; k < r.length; k++) {
            double[] q = o.ringe()[k].clone();
            for (int p = 0; p < q.length; p += 2) { q[p] -= Site.E0; q[p + 1] = -(q[p + 1] - Site.N0); }
            r[k] = q;
        }
        return r;
    }

    /** Aus dem ALKIS-Auszug und dem Gelände (ohne Korrektur); null ohne Auszug oder ohne Verbindung. */
    public static Strecke build(Terrain t) {
        long t0 = System.currentTimeMillis();
        File nf = new File(Alkis.ordner(), Alkis.NUTZUNG), gf = new File(Alkis.ordner(), Alkis.GEBAEUDE);
        if (!nf.isFile() || !gf.isFile()) return null;
        List<double[][]> bahn = new ArrayList<>(), strasse = new ArrayList<>();
        double[] schierke = null, brocken = null;
        double[][] brockenRing = null;
        try {
            for (Alkis.Objekt o : Alkis.lesen(nf)) {
                if (o.code() == 42010) bahn.add(ringe(o));
                else if (o.code() == 42001) strasse.add(ringe(o));
            }
            for (Alkis.Objekt o : Alkis.lesen(gf)) {
                if (!o.name().startsWith("Bahnhof")) continue;
                double[] b = o.box(), m = szene((b[0] + b[2]) / 2, (b[1] + b[3]) / 2);
                if (o.name().contains("Schierke")) schierke = m;
                if (o.name().contains("Brocken")) { brocken = m; brockenRing = Gipfel.ring(o); }
            }
        } catch (java.io.IOException e) {
            System.err.println("Brocken: Strecke: " + e.getMessage());
            return null;
        }
        if (bahn.isEmpty() || brocken == null) return null;
        Corridor c = Corridor.of(bahn, 2);
        double[] ost = szene(OST[0], OST[1]);
        double[][] p = c.path(ost[0], ost[1], brocken[0], brocken[1], 30, 2);
        if (p.length < 100) return null;
        // Phase 14: das Gleis endet vor dem Empfangsgebäude am Brocken (die Mittellinie der Fläche läuft sonst hinein)
        if (brockenRing != null) {
            for (int i = p.length / 2; i < p.length; i++)
                if (Gipfel.abstand(brockenRing, p[i][0], p[i][1]) < 4) { p = java.util.Arrays.copyOf(p, Math.max(100, i - 1)); break; }
        }
        double ds = 2;
        double[] g = new double[p.length];
        for (int i = 0; i < p.length; i++) g[i] = t.roh(p[i][0], p[i][1]);
        List<double[]> st = new ArrayList<>();
        double sSchierke = schierke == null ? -1 : nearestS(p, schierke, ds), sBrocken = nearestS(p, brocken, ds), len = (p.length - 1) * ds;
        sBrocken = Math.min(sBrocken, len - 40);   // der Bahnhof liegt am Ende des Gleises
        if (sSchierke >= 0) st.add(new double[]{sSchierke - 60, sSchierke + 60});
        st.add(new double[]{sBrocken - 40, sBrocken + 40});
        double[] pr = Track.profile(g, ds, GRENZE, 120, st.toArray(new double[0][]), BAHNHOF, ANTEIL_DAMM);
        // Phase 13: Goetheweg, wo die Gradiente zwischen Schierke und Brocken 956 m erreicht, dann noch einmal mit ihm
        double sGoethe = -1;
        for (int i = (int) (Math.max(0, sSchierke) / ds); i < pr.length; i++) if (pr[i] >= GOETHEWEG) { sGoethe = i * ds; break; }
        // Phase 14: Goetheweg wird nicht eben gelegt – die Strecke steigt fast überall mit 33 ‰, ein ebener Kreuzungsbahnhof
        // kostete am Brocken 4,6 m Höhe (der Bahnhof Brocken liegt nach Wikipedia auf 1 125 m)
        if (!(sGoethe > 0 && sGoethe < sBrocken - 300)) sGoethe = -1;
        // Phase 14: der Bahnhof Brocken liegt auf 1 125 m (Wikipedia); die Gradiente wird davor mit höchstens 33 ‰ zu
        // ihm angehoben (das Größere aus Gradiente und Kegel; beide steigen höchstens 33 ‰, also auch das Ergebnis)
        int ib = (int) Math.round(sBrocken / ds);
        for (int i = 0; i < pr.length; i++) {
            double d = Math.max(0, (ib - 20 - i) * ds);   // der Bahnhof selbst (± 40 m) bleibt eben
            pr[i] = Math.max(pr[i], BROCKEN_HOEHE - GRENZE * d);
        }
        double[][] ax = new double[p.length][];
        for (int i = 0; i < p.length; i++) ax[i] = new double[]{p[i][0], pr[i], p[i][1]};
        Track tr = new Track("Brockenbahn", ax, Track.METERSPUR, Track.Quality.HIGH);
        tr.seed = 1141;
        if (sSchierke >= 0) tr.stations.add(new Track.Station("Schierke", sSchierke, 120));
        // Phase 14: Goetheweg ohne Bahnsteig (Kreuzung), am Brocken liegt der Bahnsteig links, zum Gipfel und zum Empfangsgebäude
        if (sGoethe > 0) tr.stations.add(new Track.Station("Goetheweg", sGoethe, 140, 0));
        tr.stations.add(new Track.Station("Brocken", sBrocken, 80, -1));
        List<Track> aus = new ArrayList<>();
        if (sSchierke >= 0) aus.add(tr.parallel("Ausweichgleis Schierke", sSchierke - AUSWEICHE_HALB, sSchierke + AUSWEICHE_HALB, AUSWEICHE, WEICHE));
        if (sGoethe > 0) aus.add(tr.parallel("Ausweichgleis Goetheweg", sGoethe - AUSWEICHE_HALB, sGoethe + AUSWEICHE_HALB, AUSWEICHE, WEICHE));
        List<double[]> ue = tr.crossings(strasse);
        Bed b = new Bed();
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] q : p) { box[0] = Math.min(box[0], q[0]); box[1] = Math.min(box[1], q[1]); box[2] = Math.max(box[2], q[0]); box[3] = Math.max(box[3], q[1]); }
        double m = b.reach + 10, cell = 2;
        int nx = (int) Math.ceil((box[2] - box[0] + 2 * m) / cell), nz = (int) Math.ceil((box[3] - box[1] + 2 * m) / cell);
        List<Track> alle = new ArrayList<>(aus);
        alle.add(0, tr);
        b.build(alle, t::roh, box[0] - m, box[1] - m, cell, nx, nz);
        Strecke sk = new Strecke(tr, b, ue, c, g, System.currentTimeMillis() - t0);
        sk.roh = t::roh;
        sk.ausweichen.addAll(aus);
        sk.sSchierke = sSchierke;
        sk.sGoetheweg = sGoethe;
        sk.sBrocken = sBrocken;
        return sk;
    }

    static double nearestS(double[][] p, double[] q, double ds) {
        double bd = Double.MAX_VALUE, s = 0;
        for (int i = 0; i < p.length; i++) {
            double d = Math.hypot(p[i][0] - q[0], p[i][1] - q[1]);
            if (d < bd) { bd = d; s = i * ds; }
        }
        return s;
    }

    private double extraLo, extraHi;

    /**
     * Phase 14: Flächen (etwa Grundrisse am Gipfel) eben auf ihre Höhe legen, über rand m auslaufend; geht in dieselbe
     * Änderung des Geländes wie der Bahnkörper (Raster und Textur der Trasse).
     */
    public void einebnen(List<double[][]> ringe, double[] hoehen, double rand) {
        double[] r = new double[ringe.size()];
        java.util.Arrays.fill(r, rand);
        einebnen(ringe, hoehen, r);
    }

    /**
     * Wie oben, je Fläche ein eigener Rand (m). Erst laufen alle Ränder aus, dann werden die Flächen selbst gesetzt: so
     * hebt der Rand eines Nachbarn keine Fläche wieder an.
     */
    public void einebnen(List<double[][]> ringe, double[] hoehen, double[] raender) {
        for (int pass = 0; pass < 2; pass++)
            for (int k = 0; k < ringe.size(); k++) {
                double rand = raender[k];
                double[][] rg = ringe.get(k);
                double[] bx = Gipfel.box(rg);
                int i0 = (int) Math.floor((bx[0] - rand - 2 - bett.x0) / bett.cell), i1 = (int) Math.ceil((bx[2] + rand + 2 - bett.x0) / bett.cell);
                int j0 = (int) Math.floor((bx[1] - rand - 2 - bett.z0) / bett.cell), j1 = (int) Math.ceil((bx[3] + rand + 2 - bett.z0) / bett.cell);
                for (int j = Math.max(0, j0); j <= Math.min(bett.nz - 1, j1); j++)
                    for (int i = Math.max(0, i0); i <= Math.min(bett.nx - 1, i1); i++) {
                        double x = bett.x0 + (i + 0.5) * bett.cell, z = bett.z0 + (j + 0.5) * bett.cell;
                        double d = Gipfel.abstand(rg, x, z);
                        boolean kern = d <= 1.5;
                        if (kern != (pass == 1)) continue;
                        double w = kern ? 1 : Math.max(0, 1 - (d - 1.5) / rand);
                        if (w <= 0) continue;
                        w = w * w * (3 - 2 * w);
                        int c = j * bett.nx + i;
                        double h = roh.applyAsDouble(x, z) + bett.delta[c];
                        bett.delta[c] += (float) (w * (hoehen[k] - h));
                        extraLo = Math.min(extraLo, bett.delta[c]);
                        extraHi = Math.max(extraHi, bett.delta[c]);
                    }
            }
    }

    private java.util.function.DoubleBinaryOperator roh;

    /** Die Korrektur des Geländes durch den Bahnkörper. */
    public Terrain.Korrektur korrektur() {
        double[] box = {bett.x0, bett.z0, bett.x0 + bett.nx * bett.cell, bett.z0 + bett.nz * bett.cell};
        double[] ew = bett.earthwork(), sp = {Math.min(-ew[0], extraLo), Math.max(ew[1], extraHi)};
        return new Terrain.Korrektur() {
            @Override public double delta(double x, double z) { return bett.deltaAt(x, z); }
            @Override public double[] box() { return box; }
            @Override public double[] spanne() { return sp; }
        };
    }

    /**
     * Phase 14: der Platz neben dem Bahnsteig am Brocken (auf der Seite des Bahnsteigs, 1,4 bis 16 m von der Achse,
     * über die Länge des Bahnhofs) als Fläche {x, z}…, und die Höhe der Schiene dort.
     */
    public double[][] bahnhofsplatz() {
        Track.Station st = gleis.stationAt(sBrocken);
        double side = st == null || st.side() == 0 ? 1 : st.side();
        List<double[]> a = new ArrayList<>(), b = new ArrayList<>();
        for (double s = sBrocken - 45; s <= Math.min(gleis.length(), sBrocken + 45); s += 5) {
            Track.Pose p = gleis.at(s);
            double h = Math.toRadians(p.heading()), cx = Math.cos(h) * side, cz = Math.sin(h) * side;
            a.add(new double[]{p.x() + cx * 1.4, p.z() + cz * 1.4});
            b.add(0, new double[]{p.x() + cx * 16, p.z() + cz * 16});
        }
        a.addAll(b);
        double[][] r = a.toArray(new double[0][]);
        if (Gipfel.flaeche(r) < 0) java.util.Collections.reverse(java.util.Arrays.asList(r));
        return r;
    }

    /** Phase 13: Versatz der Ausweichgleise an der Stelle s des Hauptgleises (m, − links der Bergfahrt; 0 außerhalb). */
    public double versatz(double s) {
        double o = 0;
        for (double c : new double[]{sSchierke, sGoetheweg})
            if (c >= 0) o += Track.offset(s, c - AUSWEICHE_HALB, c + AUSWEICHE_HALB, AUSWEICHE, WEICHE);
        return o;
    }

    /** Abstand zur Achse (m; weit weg sehr groß). */
    public double abstand(double x, double z) { return bett.distAt(x, z); }

    /** Größte Abweichung des Höhenmodells von der Gradiente an der Achse (m, + Gelände höher). */
    public double[] abweichung() {
        double hi = 0, lo = 0;
        for (int i = 0; i < boden.length; i++) {
            double d = boden[i] - gleis.at(i * 2).y();
            hi = Math.max(hi, d);
            lo = Math.min(lo, d);
        }
        return new double[]{lo, hi};
    }

    /** „Brockenbahn 16,5 km · 3,3 % · 1,5 Umläufe um den Gipfel · Schierke 693 m, Brocken 1 121 m · 2 Bahnübergänge (2,1 s)“ */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (Track.Station st : gleis.stations) sb.append(sb.length() > 0 ? ", " : "").append(st.name()).append(String.format(Locale.GERMAN, " %.0f m", gleis.at(st.s()).y()));
        return String.format(Locale.GERMAN, "%s · %.1f km im Hauptraster · größte Steigung %.1f %% · %.2f Umläufe um den Gipfel · Bahnhöfe %s · %d Bahnübergänge · %s (%.1f s)",
                gleis.name, gleis.length() / 1000, gleis.maxGradient(10) * 100, Math.abs(umlaeufe()), sb, uebergaenge.size(), bett.describe(), millis / 1000.0);
    }

    /** Wie oft das Gleis um den Gipfel läuft: ab der Stelle, an der es sich ihm zum letzten Mal von außerhalb 2 km nähert. */
    public double umlaeufe() {
        double s0 = 0;
        for (double s = 0; s <= gleis.length(); s += 10) {
            Track.Pose p = gleis.at(s);
            if (Math.hypot(p.x(), p.z()) > 2000) s0 = s;
        }
        return gleis.turnsAround(0, 0, s0, gleis.length());
    }
}
