package com.dan.brocken.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Linien gleicher Höhe und Umrisse aus einem Raster (Phase 17, für die Startdaten): Marching Squares mit verbundenen
 * Stücken, danach vereinfacht nach Douglas und Peucker. Für Umrisse ({@link #flaechen}) wird das Raster am Rand mit
 * „außen“ gepolstert, so dass jede Linie ein geschlossener Ring ist; Löcher erkennt die Verschachtelung.
 */
public final class Konturen {
    private Konturen() { }

    /** Ein Raster: Werte je Zelle, Breite nx, Höhe nz, Mitte der Zelle (0, 0) bei (x0, z0), Abstand zelle. */
    public record Raster(float[] v, int nx, int nz, double x0, double z0, double zelle) {
        double at(int i, int j) { return v[j * nx + i]; }
    }

    /** Die Linien der Höhe {@code wert}, vereinfacht auf {@code toleranz} m; kürzer als {@code minLaenge} fällt weg. */
    public static List<double[][]> linien(Raster r, double wert, double toleranz, double minLaenge) {
        List<double[][]> l = new ArrayList<>();
        for (double[][] p : verbinden(segmente(r, wert))) {
            double[][] s = vereinfachen(p, toleranz);
            if (laenge(s) >= minLaenge && s.length >= 2) l.add(s);
        }
        return l;
    }

    /**
     * Die Flächen, in denen das Raster über {@code wert} liegt: je Fläche {außen, Loch, Loch …}, gegen den Uhrzeigersinn
     * außen (in x, z gerechnet). Kleiner als {@code minFlaeche} m² fällt weg, Löcher unter {@code minLoch} m² auch.
     */
    public static List<List<double[][]>> flaechen(Raster r, double wert, double toleranz, double minFlaeche, double minLoch) {
        // polstern: ein Rand von einer Zelle mit -unendlich, damit jede Linie geschlossen ist
        int nx = r.nx + 2, nz = r.nz + 2;
        float[] v = new float[nx * nz];
        java.util.Arrays.fill(v, -1e9f);
        for (int j = 0; j < r.nz; j++) System.arraycopy(r.v, j * r.nx, v, (j + 1) * nx + 1, r.nx);
        Raster p = new Raster(v, nx, nz, r.x0 - r.zelle, r.z0 - r.zelle, r.zelle);
        List<double[][]> roh = new ArrayList<>();
        for (double[][] q : verbinden(segmente(p, wert))) if (q.length >= 4) roh.add(q);
        // vereinfachen, ohne dass sich Ringe kreuzen oder berühren (Oracle: ORA-13349 und Verwandte); wo doch, halbe
        // Toleranz für die beteiligten Ringe, zuletzt ganz ohne – die Ringe aus Marching Squares kreuzen sich nie
        double[] tol = new double[roh.size()];
        java.util.Arrays.fill(tol, toleranz);
        List<double[][]> ringe = new ArrayList<>();
        for (int runde = 0; ; runde++) {
            ringe.clear();
            for (int i = 0; i < roh.size(); i++) ringe.add(ring(roh.get(i), tol[i]));
            java.util.Set<Integer> schlecht = kreuzend(ringe);
            if (schlecht.isEmpty()) break;
            for (int i : schlecht) tol[i] = runde >= 6 ? 0 : tol[i] / 2;
        }
        for (int i = ringe.size() - 1; i >= 0; i--) if (ringe.get(i).length < 3) ringe.remove(i);
        // Tiefe der Verschachtelung: gerade = außen, ungerade = Loch
        int n = ringe.size();
        double[] a = new double[n];
        for (int i = 0; i < n; i++) a[i] = Math.abs(flaeche(ringe.get(i)));
        int[] tiefe = new int[n], eltern = new int[n];
        for (int i = 0; i < n; i++) {
            eltern[i] = -1;
            double[] pt = ringe.get(i)[0];
            double best = Double.MAX_VALUE;
            for (int k = 0; k < n; k++) {
                if (k == i || a[k] <= a[i]) continue;
                if (innen(ringe.get(k), pt[0], pt[1])) {
                    tiefe[i]++;
                    if (a[k] < best) { best = a[k]; eltern[i] = k; }
                }
            }
        }
        Map<Integer, List<double[][]>> teile = new HashMap<>();
        List<Integer> folge = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (tiefe[i] % 2 != 0 || a[i] < minFlaeche) continue;
            List<double[][]> t = new ArrayList<>();
            t.add(ccw(ringe.get(i), true));
            teile.put(i, t);
            folge.add(i);
        }
        for (int i = 0; i < n; i++) {
            if (tiefe[i] % 2 == 0 || a[i] < minLoch) continue;
            List<double[][]> t = teile.get(eltern[i]);
            if (t != null) t.add(ccw(ringe.get(i), false));
        }
        List<List<double[][]>> l = new ArrayList<>();
        folge.sort((x, y) -> Double.compare(a[y], a[x]));
        for (int i : folge) l.add(teile.get(i));
        return l;
    }

    /** Ein Ring vereinfacht, ohne den doppelten Endpunkt; Toleranz 0 lässt ihn, wie er ist. */
    private static double[][] ring(double[][] q, double tol) {
        double[][] s = tol > 0 ? vereinfachen(q, tol) : q;
        if (s.length >= 4 && Math.abs(s[0][0] - s[s.length - 1][0]) < 1e-6 && Math.abs(s[0][1] - s[s.length - 1][1]) < 1e-6)
            s = java.util.Arrays.copyOf(s, s.length - 1);
        return s;
    }

    /**
     * Die Ringe, deren Kanten eine andere Kante kreuzen oder berühren (im selben Ring außer den Nachbarkanten, und
     * zwischen Ringen). Kanten in einem Gitter, damit es auch bei vielen Ringen schnell bleibt.
     */
    public static java.util.Set<Integer> kreuzend(List<double[][]> ringe) {
        java.util.Set<Integer> s = new java.util.TreeSet<>();
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        int kanten = 0;
        for (double[][] r : ringe) for (double[] q : r) { minX = Math.min(minX, q[0]); maxX = Math.max(maxX, q[0]); minZ = Math.min(minZ, q[1]); maxZ = Math.max(maxZ, q[1]); kanten++; }
        if (kanten == 0) return s;
        double g = Math.max(1, Math.sqrt((maxX - minX + 1) * (maxZ - minZ + 1) / Math.max(1, kanten)) * 2);
        int gx = (int) ((maxX - minX) / g) + 1, gz = (int) ((maxZ - minZ) / g) + 1;
        Map<Integer, List<int[]>> zellen = new HashMap<>();
        for (int ri = 0; ri < ringe.size(); ri++) {
            double[][] r = ringe.get(ri);
            if (r.length < 3) continue;
            for (int k = 0; k < r.length; k++) {
                double[] a = r[k], b = r[(k + 1) % r.length];
                int i0 = (int) ((Math.min(a[0], b[0]) - minX) / g), i1 = (int) ((Math.max(a[0], b[0]) - minX) / g),
                        j0 = (int) ((Math.min(a[1], b[1]) - minZ) / g), j1 = (int) ((Math.max(a[1], b[1]) - minZ) / g);
                for (int j = j0; j <= j1; j++) for (int i = i0; i <= i1; i++) zellen.computeIfAbsent(j * gx + i, x -> new ArrayList<>()).add(new int[]{ri, k});
            }
        }
        for (List<int[]> l : zellen.values())
            for (int u = 0; u < l.size(); u++)
                for (int w = u + 1; w < l.size(); w++) {
                    int[] e = l.get(u), f = l.get(w);
                    if (s.contains(e[0]) && s.contains(f[0])) continue;
                    double[][] ra = ringe.get(e[0]), rb = ringe.get(f[0]);
                    if (e[0] == f[0]) {
                        int n = ra.length, d = Math.abs(e[1] - f[1]);
                        if (d == 1 || d == n - 1) continue;   // Nachbarkanten teilen nur ihre Ecke
                    }
                    if (schneiden(ra[e[1]], ra[(e[1] + 1) % ra.length], rb[f[1]], rb[(f[1] + 1) % rb.length])) { s.add(e[0]); s.add(f[0]); }
                }
        return s;
    }

    /** Ob sich zwei Strecken schneiden oder berühren (auch kollinear überlappend), mit 1 cm Spiel. */
    static boolean schneiden(double[] a, double[] b, double[] c, double[] d) {
        double d1 = kreuz(c, d, a), d2 = kreuz(c, d, b), d3 = kreuz(a, b, c), d4 = kreuz(a, b, d);
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true;
        return abstand(a, c, d) < 0.01 || abstand(b, c, d) < 0.01 || abstand(c, a, b) < 0.01 || abstand(d, a, b) < 0.01;
    }

    private static double kreuz(double[] o, double[] a, double[] b) { return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]); }

    // ------------------------------------------------------------------ Marching Squares

    /** Stücke je Zelle als {x1, z1, x2, z2}. */
    static List<double[]> segmente(Raster r, double w) {
        List<double[]> s = new ArrayList<>();
        for (int j = 0; j + 1 < r.nz; j++)
            for (int i = 0; i + 1 < r.nx; i++) {
                double a = r.at(i, j), b = r.at(i + 1, j), c = r.at(i + 1, j + 1), d = r.at(i, j + 1);
                int k = (a > w ? 1 : 0) | (b > w ? 2 : 0) | (c > w ? 4 : 0) | (d > w ? 8 : 0);
                if (k == 0 || k == 15) continue;
                double x = r.x0 + i * r.zelle, z = r.z0 + j * r.zelle, h = r.zelle;
                double[] oben = {x + h * t(a, b, w), z}, rechts = {x + h, z + h * t(b, c, w)}, unten = {x + h * t(d, c, w), z + h}, links = {x, z + h * t(a, d, w)};
                // Richtung so, dass „über“ links liegt (dann laufen die Ringe außen einheitlich)
                switch (k) {
                    case 1 -> add(s, links, oben);
                    case 2 -> add(s, oben, rechts);
                    case 3 -> add(s, links, rechts);
                    case 4 -> add(s, rechts, unten);
                    case 5 -> { add(s, links, unten); add(s, rechts, oben); }
                    case 6 -> add(s, oben, unten);
                    case 7 -> add(s, links, unten);
                    case 8 -> add(s, unten, links);
                    case 9 -> add(s, unten, oben);
                    case 10 -> { add(s, oben, links); add(s, unten, rechts); }
                    case 11 -> add(s, unten, rechts);
                    case 12 -> add(s, rechts, links);
                    case 13 -> add(s, rechts, oben);
                    case 14 -> add(s, oben, links);
                    default -> { }
                }
            }
        return s;
    }

    private static double t(double a, double b, double w) { return a == b ? 0.5 : Math.max(0, Math.min(1, (w - a) / (b - a))); }

    private static void add(List<double[]> s, double[] p, double[] q) { s.add(new double[]{p[0], p[1], q[0], q[1]}); }

    private static long key(double x, double z) { return Math.round(x * 64) * 1_000_003L + Math.round(z * 64); }

    /** Die Stücke zu Linien verbinden (über gemeinsame Endpunkte, in beide Richtungen). */
    static List<double[][]> verbinden(List<double[]> seg) {
        Map<Long, List<Integer>> an = new HashMap<>();
        for (int i = 0; i < seg.size(); i++) {
            double[] s = seg.get(i);
            an.computeIfAbsent(key(s[0], s[1]), x -> new ArrayList<>()).add(i);
            an.computeIfAbsent(key(s[2], s[3]), x -> new ArrayList<>()).add(i);
        }
        boolean[] weg = new boolean[seg.size()];
        List<double[][]> l = new ArrayList<>();
        for (int i = 0; i < seg.size(); i++) {
            if (weg[i]) continue;
            weg[i] = true;
            java.util.ArrayDeque<double[]> d = new java.util.ArrayDeque<>();
            double[] s = seg.get(i);
            d.add(new double[]{s[0], s[1]});
            d.add(new double[]{s[2], s[3]});
            for (int ende = 0; ende < 2; ende++) {
                while (true) {
                    double[] p = ende == 0 ? d.peekLast() : d.peekFirst();
                    Integer nx = null;
                    for (int k : an.getOrDefault(key(p[0], p[1]), List.of())) if (!weg[k]) { nx = k; break; }
                    if (nx == null) break;
                    weg[nx] = true;
                    double[] q = seg.get(nx);
                    double[] other = key(q[0], q[1]) == key(p[0], p[1]) ? new double[]{q[2], q[3]} : new double[]{q[0], q[1]};
                    if (ende == 0) d.addLast(other); else d.addFirst(other);
                }
            }
            l.add(d.toArray(new double[0][]));
        }
        return l;
    }

    // ------------------------------------------------------------------ Vereinfachen und Geometrie

    /** Douglas–Peucker. */
    public static double[][] vereinfachen(double[][] p, double tol) {
        if (p.length < 3) return p;
        boolean[] keep = new boolean[p.length];
        keep[0] = keep[p.length - 1] = true;
        java.util.ArrayDeque<int[]> st = new java.util.ArrayDeque<>();
        st.push(new int[]{0, p.length - 1});
        while (!st.isEmpty()) {
            int[] ab = st.pop();
            int a = ab[0], b = ab[1];
            double dmax = -1;
            int imax = -1;
            for (int i = a + 1; i < b; i++) {
                double d = abstand(p[i], p[a], p[b]);
                if (d > dmax) { dmax = d; imax = i; }
            }
            if (dmax > tol && imax > 0) { keep[imax] = true; st.push(new int[]{a, imax}); st.push(new int[]{imax, b}); }
        }
        List<double[]> r = new ArrayList<>();
        for (int i = 0; i < p.length; i++) if (keep[i]) r.add(p[i]);
        return r.toArray(new double[0][]);
    }

    static double abstand(double[] p, double[] a, double[] b) {
        double dx = b[0] - a[0], dz = b[1] - a[1], l2 = dx * dx + dz * dz;
        if (l2 < 1e-12) return Math.hypot(p[0] - a[0], p[1] - a[1]);
        double t = Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dz) / l2));
        return Math.hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dz);
    }

    public static double laenge(double[][] p) {
        double s = 0;
        for (int i = 0; i + 1 < p.length; i++) s += Math.hypot(p[i + 1][0] - p[i][0], p[i + 1][1] - p[i][1]);
        return s;
    }

    /** Fläche mit Vorzeichen (x, z). */
    public static double flaeche(double[][] p) {
        double a = 0;
        for (int i = 0; i < p.length; i++) { double[] u = p[i], v = p[(i + 1) % p.length]; a += u[0] * v[1] - v[0] * u[1]; }
        return a / 2;
    }

    static boolean innen(double[][] p, double x, double z) {
        boolean in = false;
        for (int i = 0, j = p.length - 1; i < p.length; j = i++)
            if ((p[i][1] > z) != (p[j][1] > z) && x < (p[j][0] - p[i][0]) * (z - p[i][1]) / (p[j][1] - p[i][1]) + p[i][0]) in = !in;
        return in;
    }

    private static double[][] ccw(double[][] p, boolean aussen) {
        boolean pos = flaeche(p) > 0;
        if (pos == aussen) return p;
        double[][] q = new double[p.length][];
        for (int i = 0; i < p.length; i++) q[i] = p[p.length - 1 - i];
        return q;
    }
}
