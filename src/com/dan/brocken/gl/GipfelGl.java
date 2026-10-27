package com.dan.brocken.gl;

import com.dan.brocken.scene.Gipfel;
import com.dan.brocken.scene.Terrain;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 14: die Bauten am Gipfel als ein Netz (Durchgang „Gipfel“). Wände vom tiefsten Grund bis zur Traufe mit
 * Fenstern im Shader (Raster nach Geschoss und Achsmaß, nachts zum Teil erleuchtet), flache Dächer auf dem Grundriss,
 * Walm- und Zeltdächer auf dem umschreibenden Rechteck; Fernsehturm mit Aussichtsgeschoss und Radom, Kuppel des
 * Brockenhauses, Turm und Mast der Wetterwarte, Sendeturm (Beine, Schaft, drei Plattformen, Stahlrohr, Hindernisfeuer),
 * Gipfelstein, die Brockenuhr als heller Kreis aus Grus, der Zaun um den Brockengarten. Licht wie das Gleis
 * (Sonne als Bestrahlung, Himmel, Dunst), dazu Schnee auf allem, was nach oben schaut, Nässe und Raueis auf der
 * Luvseite (Dicke und Richtung aus {@code wiring.Raueis}). Shader: {@code gipfel.vert}, {@code gipfel.frag}.
 */
public final class GipfelGl {
    /** Stoffe (Attribut 3): matt, Fassade mit Fenstern, Glasband, Leuchte, Dach, Metall/Lack, Granit, Grus, Holz. */
    static final float MATT = 0, FASSADE = 1, GLAS = 2, LEUCHTE = 3, DACH = 4, METALL = 5, GRANIT = 6, GRUS = 7, HOLZ = 8;

    static final float[] PUTZ_F = {0.50f, 0.48f, 0.43f}, GRANIT_F = {0.30f, 0.29f, 0.28f}, BETON_F = {0.44f, 0.44f, 0.43f}, HOLZ_F = {0.22f, 0.15f, 0.09f},
            DACH_F = {0.10f, 0.10f, 0.11f}, SCHIEFER = {0.07f, 0.075f, 0.085f}, WEISS = {0.70f, 0.71f, 0.72f}, ROHR = {0.62f, 0.63f, 0.64f},
            ROT = {1.0f, 0.05f, 0.03f}, SIGNALROT = {0.55f, 0.07f, 0.06f}, GRUS_F = {0.30f, 0.29f, 0.26f}, ZAUN = {0.20f, 0.14f, 0.09f};

    private final Shader shader = new Shader("Gipfel", null, "gipfel.vert", "gipfel.frag");
    private int vao, vbo, verts;
    private boolean ready;
    private String info = "";

    /** Netz bauen und hochladen (im GL-Faden). */
    public void init(GL4 gl, Gipfel g, Terrain t) {
        shader.build(gl);
        List<Float> v = new ArrayList<>(1 << 16);
        for (Gipfel.Bau b : g.bauten) bau(v, b);
        for (Gipfel.Teil p : g.teile) teil(v, p);
        if (g.stein != null) uhr(v, g.stein, t);
        if (g.garten.length > 2) zaun(v, g.garten, t);
        if (g.platz.length > 2) platz(v, g.platz, g.platzHoehe);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(v.size());
        for (Float f : v) fb.put(f);
        fb.flip();
        verts = v.size() / 12;
        int[] ids = new int[2];
        gl.glGenVertexArrays(1, ids, 0);
        gl.glGenBuffers(1, ids, 1);
        vao = ids[0]; vbo = ids[1];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) fb.limit() * 4, fb, GL.GL_STATIC_DRAW);
        int[] size = {3, 3, 3, 1, 2};
        long off = 0;
        for (int a = 0; a < 5; a++) {
            gl.glEnableVertexAttribArray(a);
            gl.glVertexAttribPointer(a, size[a], GL.GL_FLOAT, false, 48, off);
            off += size[a] * 4L;
        }
        gl.glBindVertexArray(0);
        info = String.format(Locale.GERMAN, "%,d Dreiecke", verts / 3);
        ready = true;
    }

    public boolean ready() { return ready; }
    public String info() { return info; }

    // ------------------------------------------------------------------ Netz

    static void vert(List<Float> v, double x, double y, double z, double nx, double ny, double nz, float[] c, float mat, double u, double w) {
        v.add((float) x); v.add((float) y); v.add((float) z);
        v.add((float) nx); v.add((float) ny); v.add((float) nz);
        v.add(c[0]); v.add(c[1]); v.add(c[2]); v.add(mat);
        v.add((float) u); v.add((float) w);
    }

    /** Ein Viereck a, b, c, d (gegen den Uhrzeigersinn von außen) mit Normale und uv je Ecke. */
    static void quad(List<Float> v, double[] a, double[] b, double[] c, double[] d, double[] n, float[] col, float mat, double[] uv) {
        double[][] p = {a, b, c, a, c, d};
        int[] k = {0, 1, 2, 0, 2, 3};
        for (int i = 0; i < 6; i++) vert(v, p[i][0], p[i][1], p[i][2], n[0], n[1], n[2], col, mat, uv == null ? 0 : uv[k[i] * 2], uv == null ? 0 : uv[k[i] * 2 + 1]);
    }

    static void tri(List<Float> v, double[] a, double[] b, double[] c, float[] col, float mat) {
        double ex = b[0] - a[0], ey = b[1] - a[1], ez = b[2] - a[2], fx = c[0] - a[0], fy = c[1] - a[1], fz = c[2] - a[2];
        double nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx, l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-12) return;
        for (double[] q : new double[][]{a, b, c}) vert(v, q[0], q[1], q[2], nx / l, ny / l, nz / l, col, mat, 0, 0);
    }

    static float[] wand(Gipfel.Wand w) {
        return switch (w) { case PUTZ -> PUTZ_F; case GRANIT -> GRANIT_F; case BETON -> BETON_F; case HOLZ -> HOLZ_F; };
    }

    static float stoff(Gipfel.Wand w, boolean fenster) {
        if (fenster) return FASSADE;
        return w == Gipfel.Wand.GRANIT ? GRANIT : w == Gipfel.Wand.HOLZ ? HOLZ : MATT;
    }

    /** Wände, Dach. */
    static void bau(List<Float> v, Gipfel.Bau b) {
        double[][] r = b.ring;
        float[] col = wand(b.wand);
        // Fassade: Granit und Holz bekommen ihre Fenster ebenso (Stoff in der Farbe: Granit dunkel, Holz braun)
        float mat = b.fenster ? FASSADE : stoff(b.wand, false);
        double top = b.boden + b.traufe, along = 0;
        for (int i = 0; i < r.length; i++) {
            double[] p = r[i], q = r[(i + 1) % r.length];
            double dx = q[0] - p[0], dz = q[1] - p[1], l = Math.hypot(dx, dz);
            if (l < 1e-6) continue;
            double[] n = {dz / l, 0, -dx / l};   // außen (Ring gegen den Uhrzeigersinn)
            double u0 = along, u1 = along + l;
            // uv: Strecke längs der Wand (m), Höhe über dem Boden (m); das Geschoss geht über die Farbe … in den Shader als Achsmaß
            quad(v, new double[]{p[0], b.fuss, p[1]}, new double[]{q[0], b.fuss, q[1]}, new double[]{q[0], top, q[1]}, new double[]{p[0], top, p[1]}, n,
                    col, mat + (float) Math.min(0.9, b.geschoss / 10), new double[]{u0, b.fuss - b.boden, u1, b.fuss - b.boden, u1, b.traufe, u0, b.traufe});
            along = u1;
        }
        if (b.dach == Gipfel.Dach.FLACH) {
            int[] t = Gipfel.dreiecke(r);
            for (int k = 0; k + 2 < t.length; k += 3) {
                double[] a = {r[t[k]][0], top, r[t[k]][1]}, c1 = {r[t[k + 1]][0], top, r[t[k + 1]][1]}, c2 = {r[t[k + 2]][0], top, r[t[k + 2]][1]};
                // gegen den Uhrzeigersinn in (x, z) heißt von oben gesehen im Uhrzeigersinn: umdrehen für die Normale nach oben
                tri(v, a, c2, c1, DACH_F, DACH);
            }
            // Attika: ein schmaler Rand
            for (int i = 0; i < r.length; i++) {
                double[] p = r[i], q = r[(i + 1) % r.length];
                double dx = q[0] - p[0], dz = q[1] - p[1], l = Math.hypot(dx, dz);
                if (l < 1e-6) continue;
                double[] n = {dz / l, 0, -dx / l};
                quad(v, new double[]{p[0], top, p[1]}, new double[]{q[0], top, q[1]}, new double[]{q[0], top + 0.5, q[1]}, new double[]{p[0], top + 0.5, p[1]}, n, col, MATT, null);
            }
        } else {
            double[] o = Gipfel.rechteck(r);
            double cx = o[0], cz = o[1], ux = o[2], uz = o[3], ha = o[4] + 0.45, hb = o[5] + 0.45, h = b.dachHoehe;
            float[] dc = b.wand == Gipfel.Wand.HOLZ ? DACH_F : SCHIEFER;
            double[] c00 = pt(cx, cz, ux, uz, -ha, -hb, top), c10 = pt(cx, cz, ux, uz, ha, -hb, top), c11 = pt(cx, cz, ux, uz, ha, hb, top), c01 = pt(cx, cz, ux, uz, -ha, hb, top);
            if (b.dach == Gipfel.Dach.ZELT || ha - hb < 0.5) {
                double[] s = {cx, top + h, cz};
                tri(v, c00, s, c10, dc, DACH); tri(v, c10, s, c11, dc, DACH); tri(v, c11, s, c01, dc, DACH); tri(v, c01, s, c00, dc, DACH);
            } else {
                double[] f0 = pt(cx, cz, ux, uz, -(ha - hb), 0, top + h), f1 = pt(cx, cz, ux, uz, ha - hb, 0, top + h);
                tri(v, c00, f0, c01, dc, DACH);                // Walm
                tri(v, c10, c11, f1, dc, DACH);
                quadAuto(v, c00, c10, f1, f0, dc, DACH);
                quadAuto(v, c11, c01, f0, f1, dc, DACH);
            }
            // Dachboden schließen (von unten sichtbar im Überstand)
            quadAuto(v, c00, c01, c11, c10, dc, DACH);
        }
    }

    static double[] pt(double cx, double cz, double ux, double uz, double a, double b, double y) {
        return new double[]{cx + ux * a - uz * b, y, cz + uz * a + ux * b};
    }

    static void quadAuto(List<Float> v, double[] a, double[] b, double[] c, double[] d, float[] col, float mat) {
        tri(v, a, b, c, col, mat);
        tri(v, a, c, d, col, mat);
    }

    static void kasten(List<Float> v, double cx, double y0, double cz, double hx, double hz, double y1, float[] col, float mat, double geschoss) {
        double[][] r = {{cx - hx, cz - hz}, {cx + hx, cz - hz}, {cx + hx, cz + hz}, {cx - hx, cz + hz}};
        double along = 0;
        for (int i = 0; i < 4; i++) {
            double[] p = r[i], q = r[(i + 1) % 4];
            double dx = q[0] - p[0], dz = q[1] - p[1], l = Math.hypot(dx, dz);
            double[] n = {dz / l, 0, -dx / l};
            float m = mat == FASSADE ? FASSADE + (float) Math.min(0.9, geschoss / 10) : mat;
            quad(v, new double[]{p[0], y0, p[1]}, new double[]{q[0], y0, q[1]}, new double[]{q[0], y1, q[1]}, new double[]{p[0], y1, p[1]}, n, col, m,
                    new double[]{along, 0, along + l, 0, along + l, y1 - y0, along, y1 - y0});
            along += l;
        }
        quadAuto(v, new double[]{r[0][0], y1, r[0][1]}, new double[]{r[3][0], y1, r[3][1]}, new double[]{r[2][0], y1, r[2][1]}, new double[]{r[1][0], y1, r[1][1]}, col, mat == FASSADE ? DACH : mat);
    }

    /** Zylinder um (cx, cz) von y0 bis y1, Halbmesser r0 unten, r1 oben, mit Deckel oben. */
    static void zylinder(List<Float> v, double cx, double cz, double y0, double y1, double r0, double r1, int n, float[] col, float mat) {
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * k / n, b = 2 * Math.PI * (k + 1) / n;
            double[] p0 = {cx + r0 * Math.cos(a), y0, cz + r0 * Math.sin(a)}, q0 = {cx + r0 * Math.cos(b), y0, cz + r0 * Math.sin(b)};
            double[] p1 = {cx + r1 * Math.cos(a), y1, cz + r1 * Math.sin(a)}, q1 = {cx + r1 * Math.cos(b), y1, cz + r1 * Math.sin(b)};
            double am = (a + b) / 2, slope = (r0 - r1) / Math.max(1e-6, y1 - y0), nl = Math.sqrt(1 + slope * slope);
            double[] nn = {Math.cos(am) / nl, slope / nl, Math.sin(am) / nl};
            quad(v, p0, q0, q1, p1, nn, col, mat, null);
            tri(v, new double[]{cx, y1, cz}, q1, p1, col, mat);
        }
    }

    /** Kugel oder Halbkugel (oben) um (cx, cy, cz). */
    static void kugel(List<Float> v, double cx, double cy, double cz, double r, boolean halb, int n, float[] col, float mat) {
        int m = n / 2;
        for (int j = halb ? m / 2 : 0; j < m; j++) {
            double t0 = Math.PI * j / m - Math.PI / 2, t1 = Math.PI * (j + 1) / m - Math.PI / 2;
            for (int k = 0; k < n; k++) {
                double a = 2 * Math.PI * k / n, b = 2 * Math.PI * (k + 1) / n;
                double[][] p = {sp(a, t0), sp(b, t0), sp(b, t1), sp(a, t1)};
                int[] o = {0, 1, 2, 0, 2, 3};
                for (int i : o) vert(v, cx + r * p[i][0], cy + r * p[i][1], cz + r * p[i][2], p[i][0], p[i][1], p[i][2], col, mat, 0, 0);
            }
        }
    }

    static double[] sp(double a, double t) { return new double[]{Math.cos(t) * Math.cos(a), Math.sin(t), Math.cos(t) * Math.sin(a)}; }

    static void teil(List<Float> v, Gipfel.Teil p) {
        switch (p.art()) {
            case "turm" -> {
                double h = p.a() / 2, top = p.y() + p.b();
                kasten(v, p.x(), p.y(), p.z(), h, h, top - 3.4, PUTZ_F, FASSADE, p.c());
                kasten(v, p.x(), top - 3.4, p.z(), h - 0.3, h - 0.3, top, WEISS, GLAS, 0);
                kasten(v, p.x(), top, p.z(), h + 0.3, h + 0.3, top + 0.4, DACH_F, MATT, 0);
            }
            case "radom" -> {
                zylinder(v, p.x(), p.z(), p.y(), p.y() + 1.6, p.a() * 0.75, p.a() * 0.75, 24, WEISS, METALL);
                kugel(v, p.x(), p.y() + 1.6 + p.a() * 0.82, p.z(), p.a(), false, 28, WEISS, METALL);
            }
            case "kuppel" -> {
                zylinder(v, p.x(), p.z(), p.y(), p.y() + p.b(), p.a(), p.a(), 32, WEISS, METALL);
                kugel(v, p.x(), p.y() + p.b(), p.z(), p.a(), true, 32, WEISS, METALL);
            }
            case "mast" -> {
                zylinder(v, p.x(), p.z(), p.y(), p.y() + p.b(), p.a(), p.a() * 0.6, 8, ROHR, METALL);
                kasten(v, p.x(), p.y() + p.b() * 0.6, p.z(), 0.6, 0.08, p.y() + p.b() * 0.6 + 0.08, ROHR, METALL, 0);
            }
            case "sendeturm" -> sendeturm(v, p);
            case "gipfelstein" -> stein(v, p);
            default -> { }
        }
    }

    /** Der Sendeturm: vier Beine, Schaft, drei Plattformen, Stahlrohr bis zur Spitze, Hindernisfeuer [G in den Maßen]. */
    static void sendeturm(List<Float> v, Gipfel.Teil p) {
        double x = p.x(), z = p.z(), y = p.y() - 2, sockel = p.a(), ges = p.b();
        for (int i = -1; i <= 1; i += 2)
            for (int k = -1; k <= 1; k += 2) kasten(v, x + i * 5.5, y, z + k * 5.5, 0.9, 0.9, y + 30, BETON_F, MATT, 0);
        zylinder(v, x, z, y, p.y() + sockel, 3.2, 3.0, 16, BETON_F, MATT);
        double[][] deck = {{28, 34, 9.0}, {38, 44, 10.0}, {48.5, sockel, 8.0}};
        for (double[] d : deck) {
            zylinder(v, x, z, p.y() + d[0] - 0.6, p.y() + d[0], d[2] - 1.5, d[2], 28, BETON_F, MATT);
            zylinder(v, x, z, p.y() + d[0], p.y() + d[1], d[2], d[2], 28, BETON_F, FASSADE + 0.25f);
        }
        // das Stahlrohr rot und weiß gebändert (Flugsicherung; so auf dem Foto vom Bahnhof, Phase 15), Bänder zu rund 9 m
        double top = ges - 6, len = top - sockel;
        int bands = Math.max(3, (int) Math.round(len / 9));
        for (int i = 0; i < bands; i++) {
            double a0 = sockel + len * i / bands, a1 = sockel + len * (i + 1) / bands;
            double r0 = 1.35 + (0.85 - 1.35) * i / bands, r1 = 1.35 + (0.85 - 1.35) * (i + 1) / bands;
            zylinder(v, x, z, p.y() + a0, p.y() + a1, r0, r1, 14, (bands - 1 - i) % 2 == 0 ? SIGNALROT : WEISS, METALL);
        }
        zylinder(v, x, z, p.y() + 105, p.y() + 140, 1.55, 1.45, 14, WEISS, METALL);     // Antennenfelder (weiße Verkleidung)
        zylinder(v, x, z, p.y() + ges - 6, p.y() + ges, 0.45, 0.2, 8, ROHR, METALL);
        for (double hh : new double[]{sockel + 1, 110, ges - 0.3}) kasten(v, x + 1.1, p.y() + hh, z, 0.25, 0.25, p.y() + hh + 0.5, ROT, LEUCHTE, 0);
    }

    /** Der Gipfelstein: ein gerundeter Granitblock [G]. */
    static void stein(List<Float> v, Gipfel.Teil p) {
        int n = 14, m = 8;
        java.util.SplittableRandom r = new java.util.SplittableRandom(1142);
        double[][] k = new double[(m + 1) * n][];
        for (int j = 0; j <= m; j++)
            for (int i = 0; i < n; i++) {
                double t = Math.PI * j / m - Math.PI / 2, a = 2 * Math.PI * i / n, f = 1 + 0.18 * (r.nextDouble() - 0.5);
                // ein gerundeter Quader (Superellipse, Exponent 0,35): kantiger als eine Kugel, oben flach [G]
                double ct = Math.cos(t), st = Math.sin(t), ca = Math.cos(a), sa = Math.sin(a);
                double ex = 0.35, cx = Math.signum(ca) * Math.pow(Math.abs(ca), ex), sz = Math.signum(sa) * Math.pow(Math.abs(sa), ex);
                double ch = Math.signum(ct) * Math.pow(Math.abs(ct), ex), sh = Math.signum(st) * Math.pow(Math.abs(st), 0.6);
                double y = sh * 0.5 + 0.5;   // 0 unten … 1 oben
                k[j * n + i] = new double[]{p.x() + ch * cx * p.b() / 2 * f, p.y() - 0.4 + y * (p.a() + 0.4), p.z() + ch * sz * p.c() / 2 * f};
            }
        for (int j = 0; j < m; j++)
            for (int i = 0; i < n; i++) {
                double[] a = k[j * n + i], b = k[j * n + (i + 1) % n], c = k[(j + 1) * n + (i + 1) % n], d = k[(j + 1) * n + i];
                tri(v, a, c, b, GRANIT_F, GRANIT);
                tri(v, a, d, c, GRANIT_F, GRANIT);
            }
    }

    /** Die Brockenuhr: ein Kreis aus hellem Grus um den Gipfelstein, dicht über dem Gelände. */
    static void uhr(List<Float> v, double[] s, Terrain t) {
        int n = 48, m = 6;
        for (int j = 0; j < m; j++)
            for (int i = 0; i < n; i++) {
                double r0 = Gipfel.UHR * j / m, r1 = Gipfel.UHR * (j + 1) / m, a = 2 * Math.PI * i / n, b = 2 * Math.PI * (i + 1) / n;
                double[] p00 = g(t, s[0] + r0 * Math.cos(a), s[2] + r0 * Math.sin(a)), p01 = g(t, s[0] + r0 * Math.cos(b), s[2] + r0 * Math.sin(b));
                double[] p10 = g(t, s[0] + r1 * Math.cos(a), s[2] + r1 * Math.sin(a)), p11 = g(t, s[0] + r1 * Math.cos(b), s[2] + r1 * Math.sin(b));
                float e = (float) (j == m - 1 ? 0.6 : 1);
                float[] c = {GRUS_F[0] * e, GRUS_F[1] * e, GRUS_F[2] * e};
                tri(v, p00, p11, p10, c, GRUS);
                if (j > 0) tri(v, p00, p01, p11, c, GRUS);
            }
    }

    /** Der Platz am Bahnsteig: Pflaster, eben auf seiner Höhe. */
    static void platz(List<Float> v, double[][] r, double y) {
        int[] t = Gipfel.dreiecke(r);
        float[] c = {0.085f, 0.085f, 0.08f};
        for (int k = 0; k + 2 < t.length; k += 3)
            tri(v, new double[]{r[t[k]][0], y + 0.15, r[t[k]][1]}, new double[]{r[t[k + 2]][0], y + 0.15, r[t[k + 2]][1]}, new double[]{r[t[k + 1]][0], y + 0.15, r[t[k + 1]][1]}, c, GRUS);
    }

    static double[] g(Terrain t, double x, double z) { return new double[]{x, t.height(x, z) + 0.1, z}; }

    /** Zaun aus Pfosten und zwei Latten um den Garten. */
    static void zaun(List<Float> v, double[][] r, Terrain t) {
        for (int i = 0; i < r.length; i++) {
            double[] p = r[i], q = r[(i + 1) % r.length];
            double l = Math.hypot(q[0] - p[0], q[1] - p[1]);
            int n = Math.max(1, (int) Math.ceil(l / 2.5));
            for (int k = 0; k < n; k++) {
                double f0 = (double) k / n, f1 = (double) (k + 1) / n;
                double x0 = p[0] + (q[0] - p[0]) * f0, z0 = p[1] + (q[1] - p[1]) * f0, x1 = p[0] + (q[0] - p[0]) * f1, z1 = p[1] + (q[1] - p[1]) * f1;
                double y0 = t.height(x0, z0), y1 = t.height(x1, z1);
                kasten(v, x0, y0 - 0.2, z0, 0.05, 0.05, y0 + 1.1, ZAUN, HOLZ, 0);
                for (double hh : new double[]{0.45, 0.95}) {
                    double dx = x1 - x0, dz = z1 - z0, ll = Math.hypot(dx, dz), nx = dz / ll * 0.03, nz = -dx / ll * 0.03;
                    quadAuto(v, new double[]{x0 + nx, y0 + hh, z0 + nz}, new double[]{x1 + nx, y1 + hh, z1 + nz}, new double[]{x1 + nx, y1 + hh + 0.09, z1 + nz},
                            new double[]{x0 + nx, y0 + hh + 0.09, z0 + nz}, ZAUN, HOLZ);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Zeichnen

    /** Licht und Zustand je Bild. */
    public static final class Licht {
        public double[] sunDir = {0.4, 0.7, 0.3}, sunColor = {2.5, 2.4, 2.2}, skyColor = {0.25, 0.3, 0.4}, fogColor = {0.6, 0.65, 0.7};
        public double fogDistance = 25_000, snow, wetness, nacht, zeit, pre = 1e-4;
        /** Raueis: Dicke (m) an Masten, von wo der Wind kommt (Grad), unterste und volle Höhe (m). */
        public double rime, rimeFrom = 250, rimeBase = 1e5, rimeFull = 1e5 + 1;
    }

    public void draw(GL4 gl, float[] vp, double[] eye, boolean reversedZ, Licht l) {
        if (!ready) return;
        shader.use(gl);
        shader.mat(gl, "uVP", vp);
        shader.set(gl, "uCam", eye);
        shader.set(gl, "uSunDir", l.sunDir);
        shader.set(gl, "uSunColor", l.sunColor);
        shader.set(gl, "uSkyColor", l.skyColor);
        shader.set(gl, "uFogColor", l.fogColor);
        shader.set(gl, "uFogDist", (float) l.fogDistance);
        shader.set(gl, "uSnowWet", (float) l.snow, (float) l.wetness);
        shader.set(gl, "uNacht", (float) l.nacht);
        shader.set(gl, "uZeit", (float) (l.zeit % 1000));
        shader.set(gl, "uPre", (float) l.pre);
        double a = Math.toRadians(l.rimeFrom);
        shader.set(gl, "uRime", (float) l.rime, (float) Math.sin(a), (float) -Math.cos(a), 0f);
        shader.set(gl, "uRimeBand", (float) l.rimeBase, (float) l.rimeFull);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, verts);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    public int triangles() { return verts / 3; }

    public void dispose(GL4 gl) {
        shader.dispose(gl);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (vbo != 0) gl.glDeleteBuffers(1, new int[]{vbo}, 0);
        vao = vbo = 0;
        ready = false;
    }
}
