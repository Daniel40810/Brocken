package com.dan.brocken.scene;

import com.dan.brocken.data.Alkis;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 14: die Bauten am Gipfel (F16 A: im eigenen Programm der Szene, nach Luftbild und Fotos [G]).
 * <ul>
 * <li><b>Grundrisse</b> aus den ALKIS-Gebäuden im Umkreis von {@link #UMKREIS} m um den Gipfel; Name und Art des
 * Gebäudes wählen die Bauweise ({@link #bauweise}): Höhe bis zur Traufe, Dach, Wand (Putz, Granit, Beton, Holz),
 * Geschosshöhe für die Fenster. Steil- und Walmdächer liegen auf dem kleinsten umschreibenden Rechteck, flache Dächer
 * auf dem Grundriss.</li>
 * <li><b>Besonderes</b>: der alte Fernsehturm im Brockenhotel (1936/37, Gebäude 53 m nach Wikipedia; hier Turm bis
 * 42 m mit verglastem Aussichtsgeschoss und Radom darauf [G]), die Kuppel des Brockenhauses (ehemalige Abhörstation,
 * „Stasi-Moschee“), der Turm der Wetterwarte mit Messmast, der Sendeturm von 1973 (Betonsockel 53 m auf vier Beinen mit
 * drei Plattformen, Stahlrohr bis 168 m seit 2007, nach Wikipedia „Brocken Transmitter“) auf der Fläche „Funk- und
 * Fernmeldeanlage“, das Wolkenhäuschen (1736) mit Zeltdach, der Gipfelstein in der Mitte der „Brockenuhr“ (ein
 * freier Kreis von rund 40 m) und der Zaun um den Brockengarten (ALKIS „Grünanlage“; 1890 gegründet, 4 600 m²).</li>
 * <li><b>Gelände</b>: Unter jedem Bau liegt das Gelände eben auf seiner Bodenhöhe (dem Mittel des Geländes im
 * Grundriss), daneben läuft es über {@link #RAND} m aus ({@link Strecke#einebnen}); das GLO-30 trägt am Gipfel die
 * Bauten als Buckel.</li>
 * </ul>
 */
public final class Gipfel {
    public static final double UMKREIS = 260, RAND = 8;

    /** Wand: Putz (hell), Granit, Beton, Holz. */
    public enum Wand { PUTZ, GRANIT, BETON, HOLZ }

    /** Dach: flach (mit Attika), Walm (auf dem Rechteck), Zelt (Pyramide). */
    public enum Dach { FLACH, WALM, ZELT }

    /** Ein Bau: Name, Grundriss {x, z}…, Boden (m), tiefster Grund (m), Traufe über dem Boden (m), Dach, Dachhöhe, Wand, Geschoss. */
    public static final class Bau {
        public final String name, art;
        public final double[][] ring;
        public double boden, fuss, traufe, dachHoehe, geschoss = 3.2;
        public Dach dach = Dach.FLACH;
        public Wand wand = Wand.PUTZ;
        public boolean fenster = true;

        Bau(String name, String art, double[][] ring) { this.name = name; this.art = art; this.ring = ring; }

        public double[] mitte() { return Gipfel.mitte(ring); }
        public double flaeche() { return Math.abs(Gipfel.flaeche(ring)); }
    }

    /** Ein besonderes Teil: Art und Zahlen (Ort, Maße), siehe {@link #teile}. */
    public record Teil(String art, double x, double y, double z, double a, double b, double c) { }

    public final List<Bau> bauten = new ArrayList<>();
    public final List<Teil> teile = new ArrayList<>();
    /** Zaun um den Brockengarten {x, z}… (leer, wenn die Fläche fehlt). */
    public double[][] garten = new double[0][];
    /** Der gepflasterte Platz am Bahnsteig des Brockens {x, z}… und seine Höhe (leer ohne Strecke). */
    public double[][] platz = new double[0][];
    public double platzHoehe;
    /** Gipfelstein {x, y, z} und der Halbmesser der Brockenuhr. */
    public double[] stein;
    public static final double UHR = 20;
    private long millis;

    private Gipfel() { }

    static double[][] ring(Alkis.Objekt o) {
        double[] r = o.ringe()[0];
        int n = r.length / 2;
        // doppelte Punkte und den Schlusspunkt weglassen
        List<double[]> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double x = r[i * 2] - Site.E0, z = -(r[i * 2 + 1] - Site.N0);
            if (!l.isEmpty() && Math.hypot(l.get(l.size() - 1)[0] - x, l.get(l.size() - 1)[1] - z) < 0.3) continue;
            l.add(new double[]{x, z});
        }
        if (l.size() > 2 && Math.hypot(l.get(0)[0] - l.get(l.size() - 1)[0], l.get(0)[1] - l.get(l.size() - 1)[1]) < 0.3) l.remove(l.size() - 1);
        double[][] a = l.toArray(new double[0][]);
        // gegen den Uhrzeigersinn im (x, z)-System: Fläche > 0
        if (flaeche(a) < 0) for (int i = 0; i < a.length / 2; i++) { double[] t = a[i]; a[i] = a[a.length - 1 - i]; a[a.length - 1 - i] = t; }
        return a;
    }

    /** Aus dem ALKIS-Auszug; ohne Auszug leer. Das Gelände wird noch nicht verändert ({@link #einebnen}). */
    public static Gipfel build(Terrain t) {
        long t0 = System.currentTimeMillis();
        Gipfel g = new Gipfel();
        File gf = new File(Alkis.ordner(), Alkis.GEBAEUDE), nf = new File(Alkis.ordner(), Alkis.NUTZUNG);
        double[] funk = null, steinAlkis = null;
        try {
            if (gf.isFile())
                for (Alkis.Objekt o : Alkis.lesen(gf)) {
                    double[] b = o.box();
                    double cx = (b[0] + b[2]) / 2 - Site.E0, cz = -((b[1] + b[3]) / 2 - Site.N0);
                    if (Math.hypot(cx, cz) > UMKREIS) continue;
                    Bau bau = new Bau(o.name().isBlank() ? o.art() : o.name().trim(), o.art(), ring(o));
                    if (bau.ring.length < 3) continue;
                    g.bauten.add(bau);
                }
            if (nf.isFile())
                for (Alkis.Objekt o : Alkis.lesen(nf)) {
                    double[] b = o.box();
                    double cx = (b[0] + b[2]) / 2 - Site.E0, cz = -((b[1] + b[3]) / 2 - Site.N0);
                    if (Math.hypot(cx, cz) > UMKREIS) continue;
                    if (o.text().contains("Funk")) funk = mitte(ring(o));
                    else if (o.text().contains("Grünanlage")) g.garten = ring(o);
                    else if (o.text().contains("ffentliche Zwecke") && b[2] - b[0] < 8) steinAlkis = mitte(ring(o));
                }
        } catch (java.io.IOException e) {
            System.err.println("Brocken: Gipfel: " + e.getMessage());
        }
        for (Bau b : g.bauten) {
            // Boden: Mittel des Geländes im Grundriss (Raster 2 m), Fuß: das tiefste
            double sum = 0, lo = Double.MAX_VALUE;
            int n = 0;
            double[] bx = box(b.ring);
            for (double x = bx[0]; x <= bx[2]; x += 2)
                for (double z = bx[1]; z <= bx[3]; z += 2) {
                    if (!innen(b.ring, x, z)) continue;
                    double h = t.height(x, z);
                    sum += h; n++;
                    lo = Math.min(lo, h);
                }
            for (double[] p : b.ring) lo = Math.min(lo, t.height(p[0], p[1]));
            double[] m = b.mitte();
            b.boden = n > 0 ? sum / n : t.height(m[0], m[1]);
            b.fuss = Math.min(lo, b.boden) - 1;
            bauweise(b, g);
        }
        // der Sendeturm (1973) auf der Funkfläche, sonst 130 m nordwestlich vom Gipfel (Wikipedia)
        double[] st = funk != null ? funk : new double[]{-92, -92};
        double yst = t.height(st[0], st[1]);
        g.teile.add(new Teil("sendeturm", st[0], yst, st[1], 53, 168, 0));
        // Gipfelstein: ALKIS-Kreis, sonst der Ursprung
        double[] gs = steinAlkis != null ? steinAlkis : new double[]{2, -5};
        g.stein = new double[]{gs[0], t.height(gs[0], gs[1]), gs[1]};
        g.teile.add(new Teil("gipfelstein", gs[0], g.stein[1], gs[1], 1.9, 1.4, 1.1));
        g.millis = System.currentTimeMillis() - t0;
        return g;
    }

    /** Die Bauweise je Gebäude [G nach Fotos und Beschreibungen]. */
    static void bauweise(Bau b, Gipfel g) {
        double[] m = b.mitte();
        String n = b.name;
        if (n.contains("Brockenhotel")) {
            b.traufe = 10.5; b.wand = Wand.PUTZ;
            // der alte Fernsehturm: Turm 12 × 12 m bis 42 m, oben das verglaste Aussichtsgeschoss, darauf das Radom
            double tx = -15, tz = -77;
            if (!innen(b.ring, tx, tz)) { tx = m[0]; tz = m[1]; }
            g.teile.add(new Teil("turm", tx, b.boden, tz, 12, 42, 3.4));
            g.teile.add(new Teil("radom", tx, b.boden + 42, tz, 5.6, 0, 0));
        } else if (n.contains("Brockenhaus")) {
            b.traufe = 10; b.wand = Wand.PUTZ;
            g.teile.add(new Teil("kuppel", m[0], b.boden + 10, m[1], 7.5, 2.0, 0));
        } else if (n.contains("Wolkenh")) {
            b.traufe = 3.2; b.wand = Wand.GRANIT; b.dach = Dach.ZELT; b.dachHoehe = 2.6; b.geschoss = 3.2;
        } else if (n.contains("Bahnhof")) {
            // Phase 15 nach dem Foto vom Bahnhof: zwei Geschosse aus Granit, fast flaches Dach
            b.traufe = 7.6; b.wand = Wand.GRANIT; b.dach = Dach.WALM; b.dachHoehe = 1.2; b.geschoss = 3.8;
        } else if (n.contains("Wetter")) {
            b.traufe = 7.5; b.wand = Wand.PUTZ;
            g.teile.add(new Teil("turm", m[0] + 1, b.boden, m[1] - 1, 5, 17, 3.4));
            g.teile.add(new Teil("mast", m[0] + 1, b.boden + 17, m[1] - 1, 0.12, 7, 0));
        } else if (b.art.contains("Pump")) {
            b.traufe = 3; b.wand = Wand.BETON; b.fenster = false;
        } else if (b.art.contains("Bildung")) {
            b.traufe = 3; b.wand = Wand.HOLZ; b.dach = Dach.WALM; b.dachHoehe = 2.2; b.geschoss = 3;
        } else if (b.flaeche() > 600 && m[0] < -40) {
            b.traufe = 8; b.wand = Wand.BETON;   // Betriebsgebäude am Sendeturm
        } else if (b.flaeche() > 300) {
            b.traufe = 7; b.wand = Wand.PUTZ;    // Nebengebäude des Hotels, Gaststätte am Bahnhof
            if (rechteckig(b.ring) > 0.75 && b.flaeche() < 500) { b.dach = Dach.WALM; b.dachHoehe = 3.5; b.traufe = 4.5; b.wand = Wand.HOLZ; }
        } else {
            b.traufe = 3.5; b.wand = Wand.PUTZ; b.dach = Dach.WALM; b.dachHoehe = 2.2; b.geschoss = 3.5;
        }
    }

    /**
     * Das Gelände unter den Bauten eben legen (über die Korrektur der Strecke). Bauten nahe am Gleis (eine Ecke bis 15 m) stehen auf
     * der Höhe des Bahnsteigs; der Platz neben dem Bahnsteig am Brocken wird ebenso eben und läuft über 20 m aus.
     */
    public void einebnen(Strecke s) {
        List<double[][]> r = new ArrayList<>();
        List<Double> h = new ArrayList<>(), rand = new ArrayList<>();
        for (Bau b : bauten) {
            double nah = Double.MAX_VALUE;
            for (double[] q : b.ring) nah = Math.min(nah, s.abstand(q[0], q[1]));
            if (nah < 15) {
                double y = s.gleis.at(s.sBrocken).y() + 0.2;
                b.fuss = Math.min(b.fuss, y - 1);
                b.boden = y;
            }
            r.add(b.ring); h.add(b.boden); rand.add(RAND);
        }
        platz = s.bahnhofsplatz();
        platzHoehe = s.gleis.at(s.sBrocken).y() + 0.2;
        r.add(platz); h.add(platzHoehe); rand.add(20.0);
        s.einebnen(r, h.stream().mapToDouble(Double::doubleValue).toArray(), rand.stream().mapToDouble(Double::doubleValue).toArray());
    }

    // ------------------------------------------------------------------ Geometrie

    /** Fläche mit Vorzeichen im (x, z)-System. */
    public static double flaeche(double[][] p) {
        double a = 0;
        for (int i = 0, j = p.length - 1; i < p.length; j = i++) a += p[j][0] * p[i][1] - p[i][0] * p[j][1];
        return a / 2;
    }

    public static double[] mitte(double[][] p) {
        double a = flaeche(p), cx = 0, cz = 0;
        if (Math.abs(a) < 1e-6) { for (double[] q : p) { cx += q[0]; cz += q[1]; } return new double[]{cx / p.length, cz / p.length}; }
        for (int i = 0, j = p.length - 1; i < p.length; j = i++) {
            double f = p[j][0] * p[i][1] - p[i][0] * p[j][1];
            cx += (p[j][0] + p[i][0]) * f;
            cz += (p[j][1] + p[i][1]) * f;
        }
        return new double[]{cx / (6 * a), cz / (6 * a)};
    }

    public static double[] box(double[][] p) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] q : p) { b[0] = Math.min(b[0], q[0]); b[1] = Math.min(b[1], q[1]); b[2] = Math.max(b[2], q[0]); b[3] = Math.max(b[3], q[1]); }
        return b;
    }

    public static boolean innen(double[][] p, double x, double z) {
        boolean in = false;
        for (int i = 0, j = p.length - 1; i < p.length; j = i++)
            if ((p[i][1] > z) != (p[j][1] > z) && x < p[j][0] + (z - p[j][1]) / (p[i][1] - p[j][1]) * (p[i][0] - p[j][0])) in = !in;
        return in;
    }

    /** Abstand zum Rand (m; innen negativ). */
    public static double abstand(double[][] p, double x, double z) {
        double d = Double.MAX_VALUE;
        for (int i = 0, j = p.length - 1; i < p.length; j = i++) {
            double ax = p[j][0], az = p[j][1], bx = p[i][0], bz = p[i][1], ex = bx - ax, ez = bz - az;
            double t = Math.max(0, Math.min(1, ((x - ax) * ex + (z - az) * ez) / Math.max(1e-12, ex * ex + ez * ez)));
            d = Math.min(d, Math.hypot(x - ax - t * ex, z - az - t * ez));
        }
        return innen(p, x, z) ? -d : d;
    }

    /**
     * Das kleinste umschreibende Rechteck (je Kante des Grundrisses geprüft): {Mitte x, Mitte z, Richtung der langen
     * Seite x, z, halbe Länge, halbe Breite}.
     */
    public static double[] rechteck(double[][] p) {
        double best = Double.MAX_VALUE;
        double[] r = null;
        for (int i = 0, j = p.length - 1; i < p.length; j = i++) {
            double ux = p[i][0] - p[j][0], uz = p[i][1] - p[j][1], l = Math.hypot(ux, uz);
            if (l < 1e-6) continue;
            ux /= l; uz /= l;
            double a0 = 1e18, a1 = -1e18, b0 = 1e18, b1 = -1e18;
            for (double[] q : p) {
                double a = q[0] * ux + q[1] * uz, b = -q[0] * uz + q[1] * ux;
                a0 = Math.min(a0, a); a1 = Math.max(a1, a); b0 = Math.min(b0, b); b1 = Math.max(b1, b);
            }
            double area = (a1 - a0) * (b1 - b0);
            if (area < best) {
                best = area;
                double ca = (a0 + a1) / 2, cb = (b0 + b1) / 2;
                double cx = ca * ux - cb * uz, cz = ca * uz + cb * ux;
                double ha = (a1 - a0) / 2, hb = (b1 - b0) / 2;
                r = ha >= hb ? new double[]{cx, cz, ux, uz, ha, hb} : new double[]{cx, cz, -uz, ux, hb, ha};
            }
        }
        return r;
    }

    /** Fläche des Grundrisses durch die des umschreibenden Rechtecks (1 = Rechteck). */
    public static double rechteckig(double[][] p) {
        double[] r = rechteck(p);
        return Math.abs(flaeche(p)) / Math.max(1e-6, 4 * r[4] * r[5]);
    }

    /** Dreiecke eines einfachen Polygons (Ohren abschneiden), Indizes zu je drei. */
    public static int[] dreiecke(double[][] p) {
        int n = p.length;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        int[] out = new int[Math.max(0, (n - 2) * 3)];
        int o = 0, guard = 0;
        while (idx.size() > 3 && guard++ < n * n) {
            boolean cut = false;
            for (int k = 0; k < idx.size(); k++) {
                int a = idx.get((k + idx.size() - 1) % idx.size()), b = idx.get(k), c = idx.get((k + 1) % idx.size());
                double cr = (p[b][0] - p[a][0]) * (p[c][1] - p[a][1]) - (p[b][1] - p[a][1]) * (p[c][0] - p[a][0]);
                if (cr <= 1e-9) continue;   // nicht konvex (gegen den Uhrzeigersinn: Kreuzprodukt > 0)
                boolean leer = true;
                for (int q : idx) {
                    if (q == a || q == b || q == c) continue;
                    if (imDreieck(p[q], p[a], p[b], p[c])) { leer = false; break; }
                }
                if (!leer) continue;
                out[o++] = a; out[o++] = b; out[o++] = c;
                idx.remove(k);
                cut = true;
                break;
            }
            if (!cut) break;
        }
        if (idx.size() == 3) { out[o++] = idx.get(0); out[o++] = idx.get(1); out[o++] = idx.get(2); }
        return java.util.Arrays.copyOf(out, o);
    }

    static boolean imDreieck(double[] q, double[] a, double[] b, double[] c) {
        double d1 = (q[0] - b[0]) * (a[1] - b[1]) - (a[0] - b[0]) * (q[1] - b[1]);
        double d2 = (q[0] - c[0]) * (b[1] - c[1]) - (b[0] - c[0]) * (q[1] - c[1]);
        double d3 = (q[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (q[1] - a[1]);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    /** Liegt (x, z) in einem Bau oder nahe am Sendeturm (für Gras, Blöcke, Wanderer)? Abstand zum nächsten Bau (m). */
    public double abstandBau(double x, double z) {
        double d = Double.MAX_VALUE;
        for (Bau b : bauten) {
            double[] bx = box(b.ring);
            if (x < bx[0] - 30 || x > bx[2] + 30 || z < bx[1] - 30 || z > bx[3] + 30) continue;
            d = Math.min(d, abstand(b.ring, x, z));
        }
        for (Teil t : teile) if (t.art().equals("sendeturm")) d = Math.min(d, Math.hypot(x - t.x(), z - t.z()) - 8);
        if (stein != null) d = Math.min(d, Math.hypot(x - stein[0], z - stein[2]) - UHR);
        if (platz.length > 2) d = Math.min(d, abstand(platz, x, z));
        return d;
    }

    /** „Gipfel: 12 Bauten (Brockenhotel mit Turm, …), Sendeturm 168 m, Gipfelstein, Garten (0,1 s)“ */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (Bau b : bauten) if (!b.name.equals(b.art)) sb.append(sb.length() > 0 ? ", " : "").append(b.name);
        return String.format(Locale.GERMAN, "Gipfel: %d Bauten (%s), Sendeturm 168 m, alter Fernsehturm mit Radom, Gipfelstein, %s (%.1f s)",
                bauten.size(), sb, garten.length > 0 ? "Zaun um den Brockengarten" : "ohne Garten", millis / 1000.0);
    }
}
