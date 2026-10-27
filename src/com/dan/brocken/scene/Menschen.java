package com.dan.brocken.scene;

import com.dan.brocken.data.Alkis;
import com.dan.figure.Hike;
import com.dan.figure.Outfit;
import com.dan.figure.Person;
import com.dan.rail.Corridor;
import com.dan.rail.Track;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Phase 14: Menschen am Berg mit {@code com.dan.figure} (F19 A: {@code figure} für Gruppen).
 * <ul>
 * <li><b>Wege</b> aus dem ALKIS: die Flächen „Straßenverkehr“ und „Weg“ zur Mittellinie verdünnt ({@link Corridor}, Raster
 * 2 m, im eigenen Faden, rund 5 s). Daraus die Brockenstraße von Schierke zum Gipfel, der Weg vom Bahnsteig zum Gipfelstein,
 * vom Gipfelstein zum Brockenhaus und ein Rundweg um die Kuppe durch vier Punkte [G].</li>
 * <li><b>Wanderer</b> auf der Brockenstraße hinauf und hinab in Gruppen von einem bis vier, alle {@link #TAKT} s eine
 * Gelegenheit; wie viele kommen, hängt am Monat, an der Stunde und am Wetter (Regen, Sturm, Nebel) [G]. Wo eine Gruppe
 * gerade ist, folgt aus ihrer Startzeit; nach einem Sprung der Uhr stehen alle gleich an ihrem Ort.</li>
 * <li><b>Fahrgäste</b>: kommt eine Bergfahrt am Brocken an, gehen zwei Gruppen vom Bahnsteig zum Gipfelstein, halten dort
 * für das Gipfelfoto und gehen weiter zum Brockenhaus; vor jeder Talfahrt kommen Gruppen vom Gipfelstein zum Bahnsteig.</li>
 * <li><b>Am Gipfelstein</b> stehen am Tag einige Besucher in der Brockenuhr und schauen in die Weite.</li>
 * </ul>
 * Kleidung nach dem Monat ({@link Outfit#mountain}). Alles in Szenenzeit; ein Schritt rechnet nur die Gruppen nah am Auge
 * mit Gang und Haltung, ferne werden nur versetzt.
 */
public final class Menschen {
    /** Abstand der Gelegenheiten für Wandergruppen (s) und wie lange eine Gruppe am Gipfelstein hält (s). */
    public static final double TAKT = 240, GIPFELFOTO = 240;
    /** Weiter als so viele m vom Auge wird keine Gruppe gerechnet. */
    public static final double REICHWEITE = 2500;

    /** Ein Weg: Name, Punkte {x, z}, Länge. */
    public record Weg(String name, double[][] xz, double laenge) {
        static Weg of(String name, double[][] p) {
            double l = 0;
            for (int i = 1; i < p.length; i++) l += Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
            return new Weg(name, p, l);
        }
        Weg rueck(String n) {
            double[][] r = new double[xz.length][];
            for (int i = 0; i < xz.length; i++) r[i] = xz[xz.length - 1 - i];
            return new Weg(n, r, laenge);
        }
        /** Der Rest ab Strecke d. */
        double[][] ab(double d) {
            List<double[]> o = new ArrayList<>();
            double s = 0;
            for (int i = 1; i < xz.length; i++) {
                double l = Math.hypot(xz[i][0] - xz[i - 1][0], xz[i][1] - xz[i - 1][1]);
                if (o.isEmpty() && s + l >= d) {
                    double t = (d - s) / Math.max(1e-9, l);
                    o.add(new double[]{xz[i - 1][0] + (xz[i][0] - xz[i - 1][0]) * t, xz[i - 1][1] + (xz[i][1] - xz[i - 1][1]) * t});
                }
                if (!o.isEmpty()) o.add(xz[i]);
                s += l;
            }
            if (o.size() < 2) o.add(0, xz[Math.max(0, xz.length - 2)]);
            return o.toArray(new double[0][]);
        }
    }

    /** Eine Gruppe unterwegs. */
    final class Gang {
        final Hike hike;
        final String was;
        final double start;
        final long key;
        Gang(Hike h, String was, double start, long key) { this.hike = h; this.was = was; this.start = start; this.key = key; }
    }

    private final Terrain terrain;
    private final Strecke strecke;
    private final Gipfel gipfel;
    private final Zugbetrieb zuege;
    private volatile Weg strasse, bahnsteigStein, steinHaus, rund;
    private volatile String wegeText = "Wege werden gerechnet …";
    private final List<Gang> gaenge = new ArrayList<>();
    private final List<Person> stehend = new ArrayList<>();
    private final java.util.Set<Long> erledigt = new java.util.HashSet<>();
    private volatile List<Person> alle = List.of();
    private long tag = Long.MIN_VALUE;
    private double uhr = Double.NaN;
    private int monat = 10;
    private double andrang;
    private final Hike.World welt;

    public Menschen(Terrain t, Strecke s, Gipfel g, Zugbetrieb z) {
        this.terrain = t;
        this.strecke = s;
        this.gipfel = g;
        this.zuege = z;
        this.welt = new Hike.World() {
            @Override public double ground(double x, double zz) { return terrain.height(x, zz); }
            @Override public double depth(double x, double zz) { return 0; }
        };
        Thread th = new Thread(this::wege, "Wege");
        th.setDaemon(true);
        th.start();
    }

    /** Die Wege aus dem ALKIS (im Faden „Wege“). */
    void wege() {
        long t0 = System.currentTimeMillis();
        File nf = new File(Alkis.ordner(), Alkis.NUTZUNG);
        if (!nf.isFile()) { wegeText = "Wege: kein ALKIS-Auszug"; return; }
        List<double[][]> fl = new ArrayList<>();
        try {
            for (Alkis.Objekt o : Alkis.lesen(nf))
                if (o.code() == 42001 || o.code() == 42006) fl.add(Strecke.ringe(o));
        } catch (java.io.IOException e) {
            wegeText = "Wege: " + e.getMessage();
            return;
        }
        Corridor c = Corridor.of(fl, 2);
        c.gap = 40;
        double[] st = gipfel.stein != null ? new double[]{gipfel.stein[0], gipfel.stein[2]} : new double[]{0, 0};
        double[][] p = c.path(4300, 3650, st[0], st[1], 12, 4);
        if (p.length > 1) strasse = Weg.of("Brockenstraße", p);
        // Bahnsteig und Gipfelstein, Gipfelstein und Brockenhaus: über die offene Kuppe fast gerade (die Mittellinie der
        // großen Wegfläche der Kuppe macht Umwege); am Wolkenhäuschen und am Hotel vorbei [G]
        double[] bs = bahnsteig();
        if (bs != null) bahnsteigStein = Weg.of("Bahnsteig–Gipfelstein", fein(new double[][]{bs, {bs[0] - 12, bs[1] + 4}, st}, 4));
        Gipfel.Bau haus = null;
        for (Gipfel.Bau b : gipfel.bauten) if (b.name.contains("Brockenhaus")) haus = b;
        if (haus != null) {
            double[] m = haus.mitte(), bx = Gipfel.box(haus.ring);
            steinHaus = Weg.of("Gipfelstein–Brockenhaus", fein(new double[][]{st, {25, -60}, {24, -78}, {18, -102}, {m[0], bx[3] + 3}}, 4));
        }
        // Rundweg durch vier Punkte rund 200 m um den Gipfelstein
        List<double[]> ring = new ArrayList<>();
        double[][] pts = new double[4][];
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45 + 90 * k);
            pts[k] = c.nearest(st[0] + 190 * Math.cos(a), st[1] + 190 * Math.sin(a));
        }
        boolean ok = true;
        for (int k = 0; k < 4 && ok; k++) {
            double[][] q = c.path(pts[k][0], pts[k][1], pts[(k + 1) % 4][0], pts[(k + 1) % 4][1], 6, 2);
            double l = Weg.of("", q).laenge();
            ok = q.length > 1 && l < 600;
            for (double[] x : q) ring.add(x);
        }
        if (ok) rund = Weg.of("Rundweg", ring.toArray(new double[0][]));
        wegeText = String.format(Locale.GERMAN, "Wege aus ALKIS (%s): %s, %s, %s, %s (%.1f s)", c.describe().replace("Trasse", "Raster"),
                strasse == null ? "ohne Brockenstraße" : String.format(Locale.GERMAN, "Brockenstraße %.1f km", strasse.laenge() / 1000),
                bahnsteigStein == null ? "ohne Weg vom Bahnsteig" : String.format(Locale.GERMAN, "Bahnsteig–Gipfelstein %.0f m", bahnsteigStein.laenge()),
                steinHaus == null ? "ohne Weg zum Brockenhaus" : String.format(Locale.GERMAN, "zum Brockenhaus %.0f m", steinHaus.laenge()),
                rund == null ? "ohne Rundweg" : String.format(Locale.GERMAN, "Rundweg %.1f km", rund.laenge() / 1000), (System.currentTimeMillis() - t0) / 1000.0);
    }

    /** Ein Linienzug, alle step m nachgetastet. */
    static double[][] fein(double[][] p, double step) {
        List<double[]> o = new ArrayList<>();
        for (int i = 1; i < p.length; i++) {
            double l = Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
            int n = Math.max(1, (int) Math.ceil(l / step));
            for (int k = 0; k < n; k++) o.add(new double[]{p[i - 1][0] + (p[i][0] - p[i - 1][0]) * k / n, p[i - 1][1] + (p[i][1] - p[i - 1][1]) * k / n});
        }
        o.add(p[p.length - 1]);
        return o.toArray(new double[0][]);
    }

    static double[][] ankleben(double[] a, double[][] q, double[] b) {
        double[][] r = new double[q.length + 2][];
        r[0] = a;
        System.arraycopy(q, 0, r, 1, q.length);
        r[r.length - 1] = b;
        return r;
    }

    /** Ein Punkt auf dem Bahnsteig am Brocken (auf seiner Seite des Gleises, wo die Wagen halten). */
    double[] bahnsteig() {
        if (strecke == null) return null;
        Track g = strecke.gleis;
        Track.Pose p = g.at(strecke.sBrocken - 10);
        Track.Station st = g.stationAt(strecke.sBrocken);
        double h = Math.toRadians(p.heading()), side = st == null || st.side() == 0 ? 1 : st.side();
        return new double[]{p.x() + Math.cos(h) * 3 * side, p.z() + Math.sin(h) * 3 * side};
    }

    public boolean bereit() { return strasse != null || bahnsteigStein != null; }

    // ------------------------------------------------------------------ Andrang

    /**
     * Andrang 0..1 [G]: Monat (Sommer voll, Winter 0,4), Stunde (8:30 bis 19:30, Mittag voll), Wetter (Regen über
     * 0,5 mm/h ×0,4, Wind am Gipfel über 15 m/s ×0,3, Sicht unter 200 m ×0,7).
     */
    public static double andrang(int monat, double stunde, double regen, double windGipfel, double sicht) {
        double[] m = {0.4, 0.4, 0.45, 0.6, 0.85, 1, 1, 1, 0.9, 0.8, 0.45, 0.5};
        double h = smooth(8.5, 10.5, stunde) * (1 - smooth(17.5, 19.5, stunde));
        double w = (regen > 0.5 ? 0.4 : 1) * (windGipfel > 15 ? 0.3 : 1) * (sicht < 200 ? 0.7 : 1);
        return m[Math.max(0, Math.min(11, monat - 1))] * h * w;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static double hash(long k) {
        long z = k * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    // ------------------------------------------------------------------ je Bild

    /**
     * Je Bild: Tag, Sekunde des Tages, Monat, Wetter (Regen mm/h, Wind am Gipfel m/s, Sicht m) und das Auge. Springt die
     * Uhr, stehen alle Gruppen sofort an ihrem Ort.
     */
    public void step(long tagNr, double sekunde, int mon, double regen, double windGipfel, double sicht, double[] auge) {
        boolean sprung = tagNr != tag || Double.isNaN(uhr) || sekunde < uhr - 1 || sekunde - uhr > 600;
        double dt = sprung ? 0 : sekunde - uhr;
        if (sprung) { gaenge.clear(); erledigt.clear(); }
        tag = tagNr;
        uhr = sekunde;
        monat = mon;
        andrang = andrang(mon, sekunde / 3600, regen, windGipfel, sicht);
        einsetzen(auge);
        List<Gang> fertig = new ArrayList<>();
        for (Gang g : gaenge) {
            double[] c = g.hike.centre();
            boolean nah = Math.hypot(c[0] - auge[0], c[1] - auge[2]) < REICHWEITE;
            if (dt > 0) g.hike.step(nah ? dt : dt, welt, null);
            if (g.hike.state == Hike.State.ANGEKOMMEN) fertig.add(g);
        }
        gaenge.removeAll(fertig);
        stehende(sekunde);
        List<Person> l = new ArrayList<>();
        for (Gang g : gaenge) {
            double[] c = g.hike.centre();
            if (Math.hypot(c[0] - auge[0], c[1] - auge[2]) < REICHWEITE + 500) l.addAll(g.hike.people());
        }
        l.addAll(stehend);
        alle = Collections.unmodifiableList(l);
    }

    /** Neue Gruppen nach Takt, Fahrplan und Andrang. */
    private void einsetzen(double[] auge) {
        Weg s = strasse;
        if (s != null) {
            // Gelegenheiten der letzten Stunden: wer jetzt auf der Straße ist, kommt dazu (Tempo 1,1 m/s bergauf, 1,25 bergab)
            for (int dir = 0; dir < 2; dir++) {
                double v = dir == 0 ? 1.1 : 1.25, dauer = s.laenge() / v;
                for (double t0 = Math.floor((uhr - dauer) / TAKT) * TAKT; t0 <= uhr; t0 += TAKT) {
                    long key = tag * 1_000_000L + (long) (t0 / TAKT) * 10 + dir;
                    if (erledigt.contains(key)) continue;
                    double stunde = t0 / 3600;
                    double a = andrang(monat, stunde, 0, 0, 1000) * (andrang > 0 ? 1 : 0);
                    if (t0 < 0 || hash(key) > 0.55 * a) { erledigt.add(key); continue; }
                    double d = (uhr - t0) * v;
                    if (d >= s.laenge() - 5) { erledigt.add(key); continue; }
                    Weg w = dir == 0 ? s : s.rueck("Brockenstraße hinab");
                    int n = 1 + (int) (hash(key + 7) * 3.99);
                    gaenge.add(gang(w.ab(d), n, dir == 0 ? "Brockenstraße hinauf" : "Brockenstraße hinab", t0, key, v, false));
                    erledigt.add(key);
                }
            }
        }
        // Fahrgäste: nach jeder Ankunft einer Bergfahrt, vor jeder Talfahrt
        Weg bs = bahnsteigStein, sh = steinHaus;
        if (bs != null && zuege != null) {
            for (double[] an : zuege.ankuenfte) {
                double t0 = an[3] + 60;
                for (int k = 0; k < 2; k++) {
                    long key = tag * 1_000_000L + 900_000 + (long) an[3] * 4 + k;
                    if (erledigt.contains(key)) continue;
                    double start = t0 + k * 50, dauer = bs.laenge() / 1.0 + GIPFELFOTO + (sh == null ? 0 : sh.laenge());
                    if (uhr < start) continue;
                    erledigt.add(key);
                    if (uhr > start + dauer) continue;
                    double[][] route = sh == null ? bs.xz() : verbinden(bs.xz(), sh.xz());
                    Weg w = Weg.of("Bahnsteig–Gipfel", route);
                    Gang g = gang(w.xz(), 4 + (int) (hash(key) * 6), "Fahrgäste zum Gipfel", start, key, 1.0, true);
                    g.hike.stops.add(new Hike.Stop(bs.laenge() - 6, GIPFELFOTO, "Gipfelfoto"));
                    vorspulen(g, uhr - start);
                    gaenge.add(g);
                }
            }
            for (Fahrplan.Fahrt f : Fahrplan.talfahrten()) {
                double start = f.ab() - 20 * 60 - (bs.laenge() / 1.0);
                long key = tag * 1_000_000L + 800_000 + f.ab();
                if (erledigt.contains(key) || uhr < start) continue;
                erledigt.add(key);
                if (uhr > f.ab()) continue;
                Gang g = gang(bs.rueck("Gipfelstein–Bahnsteig").xz(), 5 + (int) (hash(key) * 6), "Fahrgäste zum Zug", start, key, 1.05, true);
                // am Bahnsteig warten sie bis zur Abfahrt, dann sind sie eingestiegen
                g.hike.stops.add(new Hike.Stop(bs.laenge() - 1.5, Math.max(30, f.ab() - (start + bs.laenge() / 1.05)), "wartet auf den Zug"));
                vorspulen(g, uhr - start);
                gaenge.add(g);
            }
        }
        // Rundweg: alle drei Takte eine Gelegenheit
        Weg r = rund;
        if (r != null) {
            double v = 1.0, dauer = r.laenge() / v;
            for (double t0 = Math.floor((uhr - dauer) / (3 * TAKT)) * 3 * TAKT; t0 <= uhr; t0 += 3 * TAKT) {
                long key = tag * 1_000_000L + 700_000 + (long) (t0 / TAKT);
                if (erledigt.contains(key)) continue;
                erledigt.add(key);
                if (t0 < 0 || hash(key) > 0.7 * andrang(monat, t0 / 3600, 0, 0, 1000) * (andrang > 0 ? 1 : 0)) continue;
                double d = (uhr - t0) * v;
                if (d >= r.laenge() - 5) continue;
                gaenge.add(gang(r.ab(d), 1 + (int) (hash(key + 3) * 2.99), "Rundweg", t0, key, v, false));
            }
        }
    }

    static double[][] verbinden(double[][] a, double[][] b) {
        double[][] r = new double[a.length + b.length][];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private Gang gang(double[][] route, int n, String was, double start, long key, double v, boolean gruppe) {
        Hike h = new Hike(route, Math.max(0, n - 1), monat, key);
        h.guideSpeed = v;
        SplittableRandom r = new SplittableRandom(key ^ 0x5DEECE66DL);
        for (Person p : h.people()) p.outfit = Outfit.mountain(monat, r, p.role == Person.Role.CHILD);
        for (Person p : h.people()) p.y = terrain.height(p.x, p.z);
        return new Gang(h, was, start, key);
    }

    private void vorspulen(Gang g, double t) {
        if (t > 1) g.hike.step(Math.min(t, 3600), welt, null);
    }

    /** Am Gipfelstein: einige Besucher im Kreis der Brockenuhr, alle {@link #TAKT} s wechselt einer [G]. */
    private void stehende(double sekunde) {
        stehend.clear();
        if (gipfel.stein == null || andrang <= 0.01) return;
        int n = (int) Math.round(6 * andrang);
        long slot = (long) (sekunde / TAKT);
        for (int i = 0; i < n; i++) {
            long key = tag * 1_000_000L + 600_000 + (slot + i) % 997 * 31 + i;
            SplittableRandom r = new SplittableRandom(key);
            double a = r.nextDouble() * 2 * Math.PI, d = 3 + r.nextDouble() * 9;
            Person p = new Person(500 + i, Person.Role.ADULT, Outfit.mountain(monat, r, false));
            p.x = gipfel.stein[0] + Math.cos(a) * d;
            p.z = gipfel.stein[2] + Math.sin(a) * d;
            p.y = terrain.height(p.x, p.z);
            // Blick nach außen in die Weite oder zum Stein (Foto)
            boolean foto = r.nextDouble() < 0.35;
            double hx = foto ? gipfel.stein[0] - p.x : p.x - gipfel.stein[0], hz = foto ? gipfel.stein[2] - p.z : p.z - gipfel.stein[2];
            p.heading = Math.atan2(hx, -hz);
            p.scale = 0.92 + 0.16 * r.nextDouble();
            p.point = foto ? 0.6 : 0;
            p.look = 0.4 * Math.sin(sekunde * 0.1 + i);
            stehend.add(p);
        }
    }

    /** Alle Personen, die zu zeichnen sind (unveränderlich, vom letzten Schritt). */
    public List<Person> personen() { return alle; }

    public double andrang() { return andrang; }
    public int gruppen() { return gaenge.size(); }
    public String describeWege() { return wegeText; }

    /** Phase 17: die gerechneten Wege (für die Startdaten, BR_WEG); leer, solange der Faden „Wege“ rechnet. */
    public List<Weg> wegeListe() {
        List<Weg> l = new ArrayList<>();
        for (Weg w : new Weg[]{strasse, bahnsteigStein, steinHaus, rund}) if (w != null) l.add(w);
        return l;
    }

    /** Phase 17: ob der Faden „Wege“ fertig ist (mit oder ohne Wege). */
    public boolean wegeFertig() { return !wegeText.endsWith("…"); }

    /** „Menschen: 23 (Andrang 80 %) · 7 Gruppen: 3 auf der Brockenstraße, …“ */
    public String describe() {
        int str = 0, fg = 0, rw = 0;
        for (Gang g : gaenge) {
            if (g.was.startsWith("Brockenstraße")) str++;
            else if (g.was.startsWith("Fahrgäste")) fg++;
            else rw++;
        }
        return String.format(Locale.GERMAN, "Menschen: %d (Andrang %.0f %%) · %d Gruppen: %d auf der Brockenstraße, %d Fahrgäste, %d auf dem Rundweg · %d am Gipfelstein",
                alle.size(), andrang * 100, gaenge.size(), str, fg, rw, stehend.size());
    }
}
