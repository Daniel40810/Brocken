package com.dan.brocken.data;

import com.dan.brocken.camera.Ansichten;
import com.dan.brocken.gl.Renderer;
import com.dan.brocken.scene.Bodenkarte;
import com.dan.brocken.scene.Fahrplan;
import com.dan.brocken.scene.Gipfel;
import com.dan.brocken.scene.Menschen;
import com.dan.brocken.scene.Regie;
import com.dan.brocken.scene.SceneClock;
import com.dan.brocken.scene.Site;
import com.dan.brocken.scene.Strecke;
import com.dan.brocken.scene.Terrain;
import com.dan.brocken.wiring.KlimaBrocken;
import com.dan.brocken.wiring.WindBrocken;
import com.dan.store.Felder;
import com.dan.store.Zahl;
import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;

/**
 * Die mitgelieferten Daten (Phase 17, Werkbuch Abschnitt 9, F25–F37 „alle A“): Quellen und Befunde aus
 * {@code quellen.txt} und {@code befunde.txt} in diesem Package; Gelände, Ausschnitte und Höhenlinien aus
 * {@code daten/gelaende}; Felsen und Böden aus der Bodenkarte; Lagen, Übergänge, Klima und Höhenstufen aus
 * {@link KlimaBrocken}; Strecke, Bahnhöfe, Übergänge, Fahrzeuge und Fahrplan aus der Bahn; Bauten, Orte und Wege vom
 * Gipfel; Wetter- und Regiebücher, Ansichten, Fahrten und die Presets der Packages aus dem Code. Daraus schreibt
 * {@link StartdatenSql} das Skript {@code db/sql/04_startdaten.sql}; ohne Datenbank gilt der Code selbst.
 * <p>
 * Eine Zeile trägt die Spalten ihrer Tabelle; {@code QUELLE} (ein Kurzzeichen) wird im Skript zu {@code QUELLE_ID}.
 */
public final class Startdaten {
    private Startdaten() { }

    /** Eine Tabelle der Startdaten: Name, Schlüsselspalten (für MERGE), Zeilen. */
    public record Tabelle(String name, String[] schluessel, List<Daten.Zeile> zeilen) { }

    /** Ein Befund mit den Kurzzeichen seiner Quellen. */
    public record Befund(Daten.Zeile zeile, List<String> quellen) { }

    /** Ein Preset eines Package. */
    public record Preset(String paket, String klasse, String name, String beschreibung, Map<String, String> felder) { }

    // ------------------------------------------------------------------ Textdateien im Package

    static List<String[]> zeilen(String datei) {
        try (InputStream in = Startdaten.class.getResourceAsStream(datei)) {
            if (in == null) throw new IllegalStateException(datei + " fehlt im Package com.dan.brocken.data");
            List<String[]> l = new ArrayList<>();
            for (String z : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (z.isBlank() || z.startsWith("#")) continue;
                String[] p = z.split("\\|", -1);
                for (int i = 0; i < p.length; i++) p[i] = p[i].strip();
                l.add(p);
            }
            return l;
        } catch (IOException e) {
            throw new IllegalStateException(datei + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    private static String leer(String s) { return s == null || s.isEmpty() ? null : s; }

    public static List<Daten.Zeile> quellen() {
        List<Daten.Zeile> l = new ArrayList<>();
        for (String[] p : zeilen("quellen.txt"))
            l.add(new Daten.Zeile().mit("KURZ", p[0]).mit("ART", p[1]).mit("ABGERUFEN", leer(p[2])).mit("TITEL", p[3]).mit("URL", leer(p[4]))
                    .mit("BEMERKUNG", p.length > 5 ? leer(p[5]) : null));
        return l;
    }

    public static List<Befund> befunde() {
        List<Befund> l = new ArrayList<>();
        for (String[] p : zeilen("befunde.txt")) {
            List<String> q = new ArrayList<>();
            if (p.length > 4) for (String x : p[4].split(",")) if (!x.isBlank()) q.add(x.strip());
            l.add(new Befund(new Daten.Zeile().mit("THEMA", p[0]).mit("GEGENSTAND", p[1]).mit("BEFUND", p[2]).mit("TEXT", p[3]), q));
        }
        return l;
    }

    // ------------------------------------------------------------------ Gelände (F27 A)

    /** SHA-256 einer Datei (hex) oder null, wenn sie fehlt. */
    public static String sha256(File f) {
        if (!f.isFile()) return null;
        try (InputStream in = new java.io.FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            StringBuilder b = new StringBuilder();
            for (byte x : md.digest()) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static Double zahl(java.util.Properties p, String k) {
        String v = p.getProperty(k);
        if (v == null) return null;
        double d = Zahl.lies(v);
        return Double.isNaN(d) ? null : d;
    }

    public static List<Daten.Zeile> gelaende(File dir, java.util.Properties p) {
        String vermerk = p.getProperty("quellenvermerk");
        List<Daten.Zeile> l = new ArrayList<>();
        Object[][] f = {
                {"Kern DGM1", "kern.r16", "Höhen im Kern 3 × 3 km um den Gipfel, DGM1 auf 2 m verdichtet (16 bit)", Terrain.KCELL, Terrain.KN, "DGM1_ST", zahl(p, "dgm1.anteil")},
                {"Hauptraster", "hoehe.r16", "Höhen im Hauptraster, GLO-30 ohne Waldaufschlag, wo es fehlt DGM200 (16 bit)", Terrain.CELL, Terrain.N, "GLO30", zahl(p, "glo30.anteil")},
                {"Fernring DGM200", "fern.r16", "Höhen im Fernring bis rund 100 km, DGM200 (16 bit)", Terrain.FCELL, Terrain.FN, "DGM200", null},
                {"Nutzungsart ALKIS", "art.u8", "Nutzungsart je Zelle des Hauptrasters aus ALKIS (0 ohne Angabe, 1 Wald … 11 Bahn)", Terrain.CELL, Terrain.N, "ALKIS_HARZ", null}};
        for (Object[] x : f) {
            File d = new File(dir, (String) x[1]);
            Daten.Zeile z = new Daten.Zeile().mit("NAME", x[0]).mit("DATEI", x[1]).mit("INHALT", x[2]).mit("ZELLE_M", x[3]).mit("ZELLEN_X", x[4]).mit("ZELLEN_Z", x[4])
                    .mit("SHA256", sha256(d)).mit("BYTES", d.isFile() ? d.length() : null).mit("ANTEIL", x[6]).mit("QUELLE", x[5]).mit("VERMERK", vermerk);
            if (x[1].equals("hoehe.r16"))
                z.mit("AUFSCHLAG_M", zahl(p, "abgleich.waldaufschlag")).mit("VERSATZ_M", zahl(p, "abgleich.versatz")).mit("REST_M", zahl(p, "abgleich.rest"))
                        .mit("BEMERKUNG", "Abgleich GLO-30 gegen DGM200 auf " + p.getProperty("abgleich.zellen", "?") + " ebenen 200-m-Zellen: Waldaufschlag abgezogen, Versatz nur gemeldet; "
                                + "Sprung an der Naht zum Kern " + p.getProperty("naht.sprung", "?") + " m");
            if (x[1].equals("kern.r16")) z.mit("BEMERKUNG", p.getProperty("dgm1.kacheln", "?") + " Kacheln; Gipfel " + p.getProperty("gipfel", "?"));
            l.add(z);
        }
        return l;
    }

    static Daten.Geom rechteckUtm(double e0, double n0, double e1, double n1) {
        double[] a = Site.fromUtm(e0, n0), b = Site.fromUtm(e1, n0), c = Site.fromUtm(e1, n1), d = Site.fromUtm(e0, n1);
        return Daten.Geom.flaeche(new double[][]{a, b, c, d});
    }

    public static List<Daten.Zeile> ausschnitte() {
        List<Daten.Zeile> l = new ArrayList<>();
        Site.Fenster h = Site.HAUPTRASTER, k = Site.KERN;
        l.add(new Daten.Zeile().mit("NAME", "Hauptraster").mit("ART", "HAUPTRASTER").mit("OST_MIN", h.eMin()).mit("NORD_MIN", h.nMin()).mit("OST_MAX", h.eMax())
                .mit("NORD_MAX", h.nMax()).mit("BREITE_M", h.breite()).mit("HOEHE_M", h.hoehe()).mit("ZELLE_M", Terrain.CELL)
                .mit("FLAECHE", rechteckUtm(h.eMin(), h.nMin(), h.eMax(), h.nMax())).mit("BEMERKUNG", "GLO-30 auf 12,5 m (F1 A); Fenster des Windfelds"));
        l.add(new Daten.Zeile().mit("NAME", "Kern").mit("ART", "KERN").mit("OST_MIN", k.eMin()).mit("NORD_MIN", k.nMin()).mit("OST_MAX", k.eMax())
                .mit("NORD_MAX", k.nMax()).mit("BREITE_M", k.breite()).mit("HOEHE_M", k.hoehe()).mit("ZELLE_M", Terrain.KCELL)
                .mit("FLAECHE", rechteckUtm(k.eMin(), k.nMin(), k.eMax(), k.nMax())).mit("BEMERKUNG", "DGM1 auf 2 m, Gipfel und Spirale (F2 A)"));
        double half = Terrain.FN * Terrain.FCELL / 2, me = Terrain.MX + Site.E0, mn = Site.N0 - Terrain.MZ;
        l.add(new Daten.Zeile().mit("NAME", "Fernring").mit("ART", "FERNRING").mit("OST_MIN", me - half).mit("NORD_MIN", mn - half).mit("OST_MAX", me + half)
                .mit("NORD_MAX", mn + half).mit("BREITE_M", 2 * half).mit("HOEHE_M", 2 * half).mit("ZELLE_M", Terrain.FCELL)
                .mit("FLAECHE", rechteckUtm(me - half, mn - half, me + half, mn + half)).mit("BEMERKUNG", "DGM200 bis rund 100 km, mit Erdkrümmung"));
        return l;
    }

    /** Höhenlinien alle 50 m im Hauptraster (auf 6 m vereinfacht, Stücke unter 120 m fallen weg). */
    public static List<Daten.Zeile> hoehenlinien(Terrain t) {
        float[] v = new float[Terrain.N * Terrain.N];
        for (int j = 0; j < Terrain.N; j++) for (int i = 0; i < Terrain.N; i++) v[j * Terrain.N + i] = (float) t.at(i, j);
        Konturen.Raster r = new Konturen.Raster(v, Terrain.N, Terrain.N, Terrain.cx(0), Terrain.cz(0), Terrain.CELL);
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (float x : v) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
        List<Daten.Zeile> l = new ArrayList<>();
        for (int h = (int) Math.ceil(lo / 50) * 50; h <= hi; h += 50) {
            List<double[][]> li = Konturen.linien(r, h, 6, 120);
            if (li.isEmpty()) continue;
            double len = 0;
            for (double[][] p : li) len += Konturen.laenge(p);
            l.add(new Daten.Zeile().mit("HOEHE_M", h).mit("ART", h % 100 == 0 ? "100" : "50").mit("TEILE", li.size()).mit("LAENGE_M", Daten.r(len, 1))
                    .mit("LINIE", Daten.Geom.multilinie(li)));
        }
        return l;
    }

    // ------------------------------------------------------------------ Boden und Fels

    public static List<Daten.Zeile> felsen(Terrain t) {
        List<Daten.Zeile> l = new ArrayList<>();
        for (Bodenkarte.Felsburg f : Bodenkarte.FELSBURGEN)
            l.add(new Daten.Zeile().mit("NAME", f.name()).mit("ART", "FELSBURG").mit("HOEHE_M", f.hoehe()).mit("BREITE_M", f.breite()).mit("RICHTUNG_GRAD", f.richtung())
                    .mit("X_M", Daten.r(f.x(), 2)).mit("Z_M", Daten.r(f.z(), 2)).mit("NHN_M", Daten.r(t.height(f.x(), f.z()), 2)).mit("LAGE", Daten.Geom.punkt(f.x(), f.z()))
                    .mit("BEFUND", f.name().equals("Teufelskanzel") ? "BELEGT" : "GESTALTET").mit("QUELLE", "WP_TEUFELSKANZEL")
                    .mit("BEMERKUNG", "Granit mit Wollsäcken (com.dan.cliff); Höhe und Breite nach Fotos [G]"));
        return l;
    }

    /** Moor, Heide, Blockmeer und Fels aus der Bodenkarte (Anteil über 0,5), Umriss auf 10 m vereinfacht. */
    public static List<Daten.Zeile> boeden(Bodenkarte b) {
        String[] art = {"MOOR", "HEIDE", "BLOCKMEER", "FELS"}, name = {"Moore", "Heide", "Blockmeer", "Fels"};
        double[] anteil = b.anteile();
        byte[] d = b.rgba();
        List<Daten.Zeile> l = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            float[] v = new float[Bodenkarte.N * Bodenkarte.N];
            for (int i = 0; i < v.length; i++) v[i] = (d[i * 4 + k] & 0xFF) / 255f;
            Konturen.Raster r = new Konturen.Raster(v, Bodenkarte.N, Bodenkarte.N, Bodenkarte.X0 + Bodenkarte.CELL / 2, Bodenkarte.Z0 + Bodenkarte.CELL / 2, Bodenkarte.CELL);
            List<List<double[][]>> f = Konturen.flaechen(r, 0.5, 10, 2000, 1000);
            if (f.isEmpty()) continue;
            Daten.Geom g = Daten.Geom.multiflaeche(f);
            l.add(new Daten.Zeile().mit("NAME", name[k] + " (Bodenkarte)").mit("ART", art[k]).mit("SCHWELLE", 0.5).mit("TEILE", f.size())
                    .mit("FLAECHE_M2", Daten.r(g.flaecheM2(), 1)).mit("ANTEIL", Daten.r(anteil[k], 4)).mit("FLAECHE", g).mit("BEFUND", "GESTALTET")
                    .mit("BEMERKUNG", "Aus scene.Bodenkarte (6 km zu 5 m, aus Gelände, Neigung und bekannten Mooren [G]); Teile unter 2 000 m² fallen weg"));
        }
        return l;
    }

    // ------------------------------------------------------------------ Wind und Klima (F30, F36)

    /** Die Wolkenart als Zahl (Ordinalzahl von Weather.CloudKind) aus dem Namen; NaN für leer. */
    public static double wolkenart(String name) {
        if (name == null) return Double.NaN;
        try { return Weather.CloudKind.valueOf(name).ordinal(); } catch (RuntimeException e) { return Double.NaN; }
    }

    static String wolkenartName(double v) {
        Weather.CloudKind[] k = Weather.CloudKind.values();
        return Double.isNaN(v) ? null : k[(int) Math.max(0, Math.min(k.length - 1, Math.round(v)))].name();
    }

    public static final String[] WETTER = {"WIND_MS", "RICHTUNG_GRAD", "BOEIGKEIT", "BEDECKUNG_ACHTEL", "HOHE_SCHICHT", "WOLKENART", "REGEN_MMH", "SICHT_KM", "NEBEL",
            "LUFTDRUCK_HPA", "TEMPERATUR_C", "FEUCHTE_PROZ", "DUENUNG_M", "GEWITTER"};
    public static final String[] SCHICHT = {"LAPSE_K_KM", "WOLKENBASIS_M", "WOLKENOBERGRENZE_M", "INVERSION_UNTEN_M", "INVERSION_OBEN_M", "INVERSION_K"};

    public static List<Daten.Zeile> lagen() {
        List<Daten.Zeile> l = new ArrayList<>();
        Weather.Phase[] ps = Weather.Phase.values();
        for (int i = 0; i < KlimaBrocken.ZIELE.length && i < ps.length; i++) {
            Daten.Zeile z = new Daten.Zeile().mit("PHASE", ps[i].name()).mit("NR", i).mit("NAME", KlimaBrocken.NAMEN[i]);
            double[] w = KlimaBrocken.ZIELE[i];
            for (int k = 0; k < WETTER.length; k++) z.mit(WETTER[k], k == 5 ? (Object) wolkenartName(w[k]) : (Object) w[k]);
            double[] s = KlimaBrocken.SCHICHTUNG[i];
            for (int k = 0; k < SCHICHT.length; k++) z.mit(SCHICHT[k], s[k]);
            z.mit("VERWEILEN_H", KlimaBrocken.VERWEILEN[i]).mit("BESCHREIBUNG", "Zielwerte am Gipfel (Wetterwarte 1 134 m) [G]; Schichtung in m über NHN");
            l.add(z);
        }
        return l;
    }

    public static List<Daten.Zeile> uebergaenge() {
        List<Daten.Zeile> l = new ArrayList<>();
        Weather.Phase[] ps = Weather.Phase.values();
        for (int i = 0; i < KlimaBrocken.UEBERGAENGE.length; i++)
            for (int k = 0; k < KlimaBrocken.UEBERGAENGE[i].length; k++)
                if (KlimaBrocken.UEBERGAENGE[i][k] > 0)
                    l.add(new Daten.Zeile().mit("VON_PHASE", ps[i].name()).mit("NACH_PHASE", ps[k].name()).mit("ANTEIL", KlimaBrocken.UEBERGAENGE[i][k]));
        return l;
    }

    public static List<Daten.Zeile> klimaMonate() {
        List<Daten.Zeile> l = new ArrayList<>();
        for (int m = 0; m < 12; m++)
            l.add(new Daten.Zeile().mit("MONAT", m + 1).mit("TEMPERATUR_C", KlimaBrocken.DWD[m]).mit("NIEDERSCHLAG_MM", KlimaBrocken.DWD_NIEDERSCHLAG[m])
                    .mit("EINGANG_C", KlimaBrocken.EINGANG[m]).mit("NASSE_TAGE", KlimaBrocken.NASSE_TAGE[m]).mit("WIND_MS", WindBrocken.MONAT[m]).mit("QUELLE", "DWD_KLIMA"));
        return l;
    }

    public static List<Daten.Zeile> hoehenstufen() {
        double t = 0, p = 0;
        for (int m = 0; m < 12; m++) { t += KlimaBrocken.DWD[m] / 12; p += KlimaBrocken.DWD_NIEDERSCHLAG[m]; }
        List<Daten.Zeile> l = new ArrayList<>();
        for (double h : KlimaBrocken.STUFEN) {
            double dh = h - KlimaBrocken.STATION;
            l.add(new Daten.Zeile().mit("HOEHE_M", (long) h).mit("TEMPERATUR_JAHR_C", Daten.r(t - KlimaBrocken.LAPSE * dh / 1000, 2))
                    .mit("NIEDERSCHLAG_JAHR_MM", Daten.r(p * (1 + KlimaBrocken.NIEDERSCHLAG_JE_KM * dh / 1000), 1))
                    .mit("VERSPAETUNG_TAGE", Daten.r(KlimaBrocken.VERSPAETUNG * dh / 100, 1)).mit("LAPSE_K_KM", KlimaBrocken.LAPSE));
        }
        return l;
    }

    // ------------------------------------------------------------------ Bahn (F31)

    static double[][] linie(com.dan.rail.Track g, double s0, double s1, double schritt) {
        int n = Math.max(1, (int) Math.ceil((s1 - s0) / schritt));
        double[][] p = new double[n + 1][];
        for (int i = 0; i <= n; i++) { com.dan.rail.Track.Pose q = g.at(s0 + (s1 - s0) * i / n); p[i] = new double[]{q.x(), q.z()}; }
        return p;
    }

    public static List<Daten.Zeile> strecke(Strecke s) {
        List<Daten.Zeile> l = new ArrayList<>();
        if (s == null) return l;
        com.dan.rail.Track g = s.gleis;
        double len = g.length();
        l.add(new Daten.Zeile().mit("ART", "GLEIS").mit("NR", 0).mit("NAME", "Brockenbahn (im Hauptraster)").mit("S_VON_M", 0.0).mit("S_BIS_M", Daten.r(len, 1))
                .mit("HOEHE_VON_M", Daten.r(g.at(0).y(), 2)).mit("HOEHE_BIS_M", Daten.r(g.at(len).y(), 2)).mit("STEIGUNG_MAX_PROMILLE", Daten.r(g.maxGradient(10) * 1000, 2))
                .mit("LAENGE_M", Daten.r(len, 1)).mit("LINIE", Daten.Geom.linie(Konturen.vereinfachen(linie(g, 0, len, 10), 1))));
        int nr = 0;
        for (double a = 0; a < len - 1; a += 250) {
            double b = Math.min(len, a + 250);
            double ya = g.at(a).y(), yb = g.at(b).y(), mx = 0;
            for (double x = a; x <= b; x += 10) mx = Math.max(mx, Math.abs(g.at(x).gradient()));
            l.add(new Daten.Zeile().mit("ART", "ABSCHNITT").mit("NR", ++nr).mit("NAME", String.format(Locale.GERMAN, "km %.2f–%.2f", a / 1000, b / 1000))
                    .mit("S_VON_M", Daten.r(a, 1)).mit("S_BIS_M", Daten.r(b, 1)).mit("HOEHE_VON_M", Daten.r(ya, 2)).mit("HOEHE_BIS_M", Daten.r(yb, 2))
                    .mit("STEIGUNG_PROMILLE", Daten.r((yb - ya) / Math.max(1, b - a) * 1000, 2)).mit("STEIGUNG_MAX_PROMILLE", Daten.r(mx * 1000, 2))
                    .mit("LAENGE_M", Daten.r(b - a, 1)).mit("LINIE", Daten.Geom.linie(linie(g, a, b, 10))));
        }
        int k = 0;
        for (com.dan.rail.Track a : s.ausweichen) {
            double la = a.length();
            l.add(new Daten.Zeile().mit("ART", "AUSWEICHE").mit("NR", ++k).mit("NAME", a.name).mit("S_VON_M", 0.0).mit("S_BIS_M", Daten.r(la, 1))
                    .mit("HOEHE_VON_M", Daten.r(a.at(0).y(), 2)).mit("HOEHE_BIS_M", Daten.r(a.at(la).y(), 2)).mit("LAENGE_M", Daten.r(la, 1))
                    .mit("LINIE", Daten.Geom.linie(linie(a, 0, la, 5))));
        }
        return l;
    }

    public static List<Daten.Zeile> bahnhoefe(Strecke s) {
        List<Daten.Zeile> l = new ArrayList<>();
        if (s == null) return l;
        Object[][] b = {{"Bahnhof Schierke", s.sSchierke}, {"Bahnhof Goetheweg", s.sGoetheweg}, {"Bahnhof Brocken", s.sBrocken}};
        for (Object[] x : b) {
            double sv = (double) x[1];
            if (sv < 0) continue;
            com.dan.rail.Track.Pose p = s.gleis.at(sv);
            l.add(new Daten.Zeile().mit("NAME", x[0]).mit("S_M", Daten.r(sv, 1)).mit("HOEHE_M", Daten.r(p.y(), 2)).mit("X_M", Daten.r(p.x(), 2)).mit("Z_M", Daten.r(p.z(), 2))
                    .mit("LAGE", Daten.Geom.punkt(p.x(), p.z())).mit("BEMERKUNG", "Stelle am Hauptgleis (Mitte des Bahnsteigs)"));
        }
        return l;
    }

    public static List<Daten.Zeile> bahnuebergaenge(Strecke s) {
        List<Daten.Zeile> l = new ArrayList<>();
        if (s == null) return l;
        int nr = 0;
        for (double[] u : s.uebergaenge) {
            com.dan.rail.Track.Pose p = s.gleis.at((u[0] + u[1]) / 2);
            l.add(new Daten.Zeile().mit("NR", ++nr).mit("NAME", "Bahnübergang " + nr).mit("S_VON_M", Daten.r(u[0], 1)).mit("S_BIS_M", Daten.r(u[1], 1))
                    .mit("X_M", Daten.r(p.x(), 2)).mit("Z_M", Daten.r(p.z(), 2)).mit("LAGE", Daten.Geom.punkt(p.x(), p.z())));
        }
        return l;
    }

    public static List<Daten.Zeile> fahrzeuge() {
        com.dan.rail.Train z = com.dan.rail.Train.brockenzug("Brockenzug");
        List<Daten.Zeile> l = new ArrayList<>();
        l.add(new Daten.Zeile().mit("NAME", "Lok 99.23–24").mit("ART", "LOK").mit("ANZAHL", 1).mit("LAENGE_M", com.dan.rail.Train.LOK_LAENGE).mit("MASSE_T", 64.5)
                .mit("LEISTUNG_KW", Daten.r(z.leistung / 1000, 1)).mit("V_MAX_KMH", Daten.r(z.vMax * 3.6, 1)).mit("TREIBRAD_M", z.treibrad).mit("HAFTUNG", z.haftung)
                .mit("BESCHREIBUNG", "Dampflok 1′E1′h2t, 700 PS, Treibräder 1 000 mm, 40 km/h; Leistung am Rad 430 kW [G]"));
        l.add(new Daten.Zeile().mit("NAME", "Wagen").mit("ART", "WAGEN").mit("ANZAHL", z.wagen()).mit("LAENGE_M", com.dan.rail.Train.WAGEN_LAENGE)
                .mit("MASSE_T", com.dan.rail.Train.WAGEN_MASSE).mit("BESCHREIBUNG", "Personenwagen, besetzt [G]"));
        l.add(new Daten.Zeile().mit("NAME", "Brockenzug").mit("ART", "ZUG").mit("ANZAHL", 1).mit("LAENGE_M", Daten.r(z.laenge, 2)).mit("MASSE_T", Daten.r(z.masse, 1))
                .mit("LEISTUNG_KW", Daten.r(z.leistung / 1000, 1)).mit("V_MAX_KMH", Daten.r(z.vMax * 3.6, 1)).mit("TREIBRAD_M", z.treibrad).mit("HAFTUNG", z.haftung)
                .mit("BESCHREIBUNG", "Lok mit " + z.wagen() + " Wagen (com.dan.rail.Train.brockenzug)"));
        return l;
    }

    public static List<Daten.Zeile> fahrplan() {
        List<Daten.Zeile> l = new ArrayList<>();
        for (Fahrplan.Fahrt f : Fahrplan.ausDemCode())
            l.add(new Daten.Zeile().mit("RICHTUNG", f.bergauf() ? "BERG" : "TAL").mit("ABFAHRT", Fahrplan.uhr(f.ab())).mit("ANKUNFT", Fahrplan.uhr(f.an()))
                    .mit("ABFAHRT_S", f.ab()).mit("ANKUNFT_S", f.an()).mit("FAHRZEIT_MIN", Math.round(f.dauer() / 60.0)).mit("ZUG", f.name())
                    .mit("VERKEHRSTAGE", "täglich [G]").mit("GUELTIG_AB", "2026-04-02").mit("JAHR", 2026).mit("AKTIV", 1).mit("QUELLE", "BAHNDAMPF"));
        return l;
    }

    // ------------------------------------------------------------------ Gipfel (F32)

    public static List<Daten.Zeile> bauten(Gipfel g) {
        List<Daten.Zeile> l = new ArrayList<>();
        if (g == null) return l;
        Map<String, Integer> n = new HashMap<>();
        for (Gipfel.Bau b : g.bauten) {
            int k = n.merge(b.name, 1, Integer::sum);
            double[] m = b.mitte();
            l.add(new Daten.Zeile().mit("NAME", k == 1 ? b.name : b.name + " (" + k + ")").mit("ART", b.art).mit("BODEN_NHN_M", Daten.r(b.boden, 2))
                    .mit("TRAUFE_M", Daten.r(b.traufe, 2)).mit("DACH", b.dach.name()).mit("DACH_HOEHE_M", Daten.r(b.dachHoehe, 2)).mit("GESCHOSS_M", Daten.r(b.geschoss, 2))
                    .mit("WAND", b.wand.name()).mit("FLAECHE_M2", Daten.r(b.flaeche(), 1)).mit("X_M", Daten.r(m[0], 2)).mit("Z_M", Daten.r(m[1], 2))
                    .mit("GRUNDRISS", Daten.Geom.flaeche(b.ring)).mit("BEFUND", "BELEGT").mit("BEMERKUNG", "Grundriss aus ALKIS, Höhen nach Fotos [G]"));
        }
        if (g.garten.length >= 3) {
            double[] m = Gipfel.mitte(g.garten);
            l.add(new Daten.Zeile().mit("NAME", "Brockengarten").mit("ART", "GARTEN").mit("FLAECHE_M2", Daten.r(Math.abs(Gipfel.flaeche(g.garten)), 1))
                    .mit("X_M", Daten.r(m[0], 2)).mit("Z_M", Daten.r(m[1], 2)).mit("GRUNDRISS", Daten.Geom.flaeche(g.garten)).mit("BEFUND", "BELEGT")
                    .mit("BEMERKUNG", "Botanischer Garten seit 1890 mit Zaun (rund 4 600 m²)"));
        }
        return l;
    }

    static final Map<String, String> TEIL = Map.of("sendeturm", "Sendeturm", "turm", "Turm", "radom", "Radom", "kuppel", "Kuppel", "mast", "Mast");

    public static List<Daten.Zeile> orte(Gipfel g, Terrain t) {
        List<Daten.Zeile> l = new ArrayList<>();
        for (Site.Ort o : Site.ORTE)
            l.add(new Daten.Zeile().mit("NAME", o.name()).mit("ART", o.art().name()).mit("X_M", Daten.r(o.x(), 2)).mit("Z_M", Daten.r(o.z(), 2))
                    .mit("NHN_M", Daten.r(t.height(o.x(), o.z()), 2)).mit("LAGE", Daten.Geom.punkt(o.x(), o.z())).mit("BEMERKUNG", "Ort des HUD (scene.Site)"));
        double[] w = Site.WETTERWARTE;
        l.add(new Daten.Zeile().mit("NAME", "Wetterwarte Brocken").mit("ART", "WARTE").mit("X_M", Daten.r(w[0], 2)).mit("Z_M", Daten.r(w[1], 2))
                .mit("NHN_M", Daten.r(t.height(w[0], w[1]), 2)).mit("HOEHE_M", WindBrocken.GEBER).mit("LAGE", Daten.Geom.punkt(w[0], w[1]))
                .mit("BEMERKUNG", "DWD-Station 722, Windgeber in 27 m"));
        if (g != null) {
            Map<String, Integer> n = new HashMap<>();
            for (Gipfel.Teil x : g.teile) {
                if (x.art().equals("gipfelstein")) continue;
                String nm = TEIL.getOrDefault(x.art(), x.art());
                int k = n.merge(nm, 1, Integer::sum);
                l.add(new Daten.Zeile().mit("NAME", k == 1 ? nm : nm + " " + k).mit("ART", "TEIL").mit("X_M", Daten.r(x.x(), 2)).mit("Z_M", Daten.r(x.z(), 2))
                        .mit("NHN_M", Daten.r(x.y(), 2)).mit("HOEHE_M", x.art().equals("sendeturm") || x.art().equals("turm") ? (Object) x.b() : null)
                        .mit("LAGE", Daten.Geom.punkt(x.x(), x.z())).mit("BEMERKUNG", "Teil am Gipfel (scene.Gipfel): " + x.art()));
            }
        }
        // Gipfelstein heißt in Site.ORTE schon so; die Teile mit gleichem Namen bekommen eine Nummer
        Map<String, Integer> doppelt = new HashMap<>();
        for (Daten.Zeile z : l) doppelt.merge(z.text("NAME"), 1, Integer::sum);
        for (Daten.Zeile z : l) if (doppelt.get(z.text("NAME")) > 1 && "TEIL".equals(z.text("ART"))) z.put("NAME", z.text("NAME") + " (Teil)");
        return l;
    }

    /** Die Wege der Wanderer; wartet höchstens 60 s auf den Faden „Wege“. */
    public static List<Daten.Zeile> wege(Menschen m) {
        List<Daten.Zeile> l = new ArrayList<>();
        if (m == null) return l;
        long bis = System.currentTimeMillis() + 60_000;
        while (!m.wegeFertig() && System.currentTimeMillis() < bis) {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        for (Menschen.Weg w : m.wegeListe())
            l.add(new Daten.Zeile().mit("NAME", w.name()).mit("ART", w.name().contains("straße") ? "STRASSE" : w.name().startsWith("Rund") ? "RUNDWEG" : "PFAD")
                    .mit("LAENGE_M", Daten.r(w.laenge(), 1)).mit("PUNKTE", w.xz().length).mit("LINIE", Daten.Geom.linie(Konturen.vereinfachen(w.xz(), 1)))
                    .mit("BEMERKUNG", "Aus den Wegflächen von ALKIS (scene.Menschen)"));
        return l;
    }

    // ------------------------------------------------------------------ Regie (F33, F36)

    public static List<Daten.Zeile> wetterbuecher() {
        List<Daten.Zeile> l = new ArrayList<>();
        for (Scenario s : KlimaBrocken.ausDemCode())
            l.add(new Daten.Zeile().mit("NAME", s.name()).mit("BEGINN_STUNDE", s.startHour()).mit("SCHRITTE", s.steps().size()).mit("LAENGE_H", Daten.r(s.hours(), 2))
                    .mit("BESCHREIBUNG", s.steps().get(0).text()).mit("TEXT", s.toText()));
        return l;
    }

    /** Kopfwerte eines Regiebuchs für BR_REGIEBUCH: Szenenzeit des ersten zeit, Zeitraffer, Fotos, Warten, Blatt. */
    public record Kopf(Long szeneMs, Double raffer, int fotos, int warten, boolean blatt) {
        public static Kopf aus(Regie.Book b) {
            Long t = null;
            Double r = null;
            int fotos = 0, warten = 0;
            boolean blatt = false;
            for (Regie.Zeile z : b.zeilen()) {
                switch (z.befehl()) {
                    case "zeit" -> {
                        if (t != null) break;
                        try {
                            String[] p = z.argumente().strip().split("\\s+");
                            String[] d = p[0].split("\\.");
                            String[] u = p.length > 1 ? p[1].split(":") : new String[]{"0", "0"};
                            t = ZonedDateTime.of(LocalDate.of(Integer.parseInt(d[2]), Integer.parseInt(d[1]), Integer.parseInt(d[0])),
                                    LocalTime.of(Integer.parseInt(u[0]), Integer.parseInt(u[1])), SceneClock.ZONE).toInstant().toEpochMilli();
                        } catch (RuntimeException e) { /* bleibt leer */ }
                    }
                    case "raffer" -> { if (r == null) { double v = Zahl.lies(z.argumente().strip().split("\\s+")[0]); r = Double.isNaN(v) ? null : v; } }
                    case "foto" -> fotos++;
                    case "warte" -> warten++;
                    case "blatt" -> blatt = true;
                    default -> { }
                }
            }
            return new Kopf(t, r, fotos, warten, blatt);
        }
    }

    /** Die Zeilen „Wetter:“ und „Schritt:“ eines Regiebuchs (der Wetterplan) oder null. */
    public static String wetterBlock(String text) {
        StringBuilder b = new StringBuilder();
        for (String z : text.split("\\R")) {
            String s = z.strip(), low = s.toLowerCase(Locale.ROOT);
            if (low.startsWith("wetter:") || low.startsWith("schritt:")) b.append(b.length() > 0 ? "\n" : "").append(s);
        }
        return b.length() == 0 ? null : b.toString();
    }

    public static List<Daten.Zeile> regiebuecher() {
        List<Daten.Zeile> l = new ArrayList<>();
        for (String d : Regie.DATEIEN) {
            String t = Regie.text(d);
            Regie.Book b = Regie.parse(t);
            Kopf k = Kopf.aus(b);
            l.add(new Daten.Zeile().mit("NAME", b.name()).mit("BESCHREIBUNG", b.about()).mit("DATEI", d).mit("SZENE_MS", k.szeneMs())
                    .mit("SZENE_TEXT", k.szeneMs() == null ? null : Daten.text(k.szeneMs())).mit("ZEITRAFFER", k.raffer()).mit("LAENGE_S", Daten.r(b.length(), 1))
                    .mit("FOTOS", k.fotos()).mit("WARTEN", k.warten()).mit("BLATT", k.blatt() ? 1 : 0).mit("WETTER", wetterBlock(t)).mit("TEXT", t));
        }
        return l;
    }

    /** Die Stichworte eines Regiebuchs (Text) als Zeilen von BR_STICHWORT ohne REGIEBUCH_ID. */
    public static List<Daten.Zeile> stichworte(String text) {
        List<Daten.Zeile> l = new ArrayList<>();
        int nr = 0;
        for (Regie.Zeile x : Regie.parse(text).zeilen())
            l.add(new Daten.Zeile().mit("NR", ++nr).mit("SEKUNDE", Daten.r(x.sekunde(), 2)).mit("BEFEHL", x.befehl().toUpperCase(Locale.ROOT))
                    .mit("ARGUMENTE", x.argumente().isEmpty() ? null : x.argumente()).mit("TEXT", x.text().isEmpty() ? null : x.text()));
        return l;
    }

    // ------------------------------------------------------------------ Kamera (F33)

    public static List<Daten.Zeile> ansichten(Terrain t) {
        List<Daten.Zeile> l = new ArrayList<>();
        for (Ansichten.Ansicht a : Ansichten.ALLE)
            l.add(new Daten.Zeile().mit("NAME", a.name()).mit("ART", "FEST").mit("TASTE", a.key()).mit("KAM_X", Daten.r(a.x(), 2))
                    .mit("KAM_Y", Daten.r(t.height(a.x(), a.z()) + a.eyeAboveGround(), 2)).mit("KAM_Z", Daten.r(a.z(), 2)).mit("KAM_GIER_GRAD", a.heading())
                    .mit("KAM_NICK_GRAD", a.pitch()).mit("AUGE_UEBER_GRUND_M", a.eyeAboveGround()).mit("LAGE", Daten.Geom.punkt(a.x(), a.z())));
        return l;
    }

    /** Die Fahrten „Spirale“ und „Zum Gipfel“ mit Stützpunkten {Zeit, x, y, z, Blick, Neigung} (Schlüssel STUETZPUNKTE). */
    public static List<Daten.Zeile> fahrten(Renderer r) {
        List<Daten.Zeile> l = new ArrayList<>();
        String[][] f = {{"Spirale", "GLEIS", "Neben dem Gleis 90 m außen und 45 m hoch um die Kuppe, vom Goetheweg bis vor den Bahnhof Brocken"},
                {"Zum Gipfel", "FLUG", "Aus 600 m Höhe von Südosten im Bogen hinab zum Gipfelstein"}};
        for (String[] x : f) {
            com.dan.brocken.camera.Fahrt fa = r.fahrtNamed(x[0]);
            if (fa == null) continue;
            double[][] sp = fa.stuetzpunkte();
            double[][] weg = new double[sp.length][];
            for (int i = 0; i < sp.length; i++) weg[i] = new double[]{sp[i][1], sp[i][3]};
            Daten.Zeile z = new Daten.Zeile().mit("NAME", x[0]).mit("ART", x[1]).mit("DAUER_S", Daten.r(fa.duration, 1)).mit("LAENGE_M", Daten.r(fa.length, 1))
                    .mit("BESCHREIBUNG", x[2]).mit("WEG", Daten.Geom.linie(weg));
            z.put("STUETZPUNKTE", sp);
            l.add(z);
        }
        return l;
    }

    // ------------------------------------------------------------------ Presets (F34)

    /** Die Klassen, deren Presets mitkommen: jede öffentliche statische Methode ohne Argument, die die Klasse liefert. */
    public static final String[] PAKETE = {"com.dan.cloud.Clouds", "com.dan.fog.TerrainFog", "com.dan.wind.WindField", "com.dan.cliff.Cliff",
            "com.dan.soundscape.Soundscape", "com.dan.sky.Droplets", "com.dan.flock.Flock"};
    /** Klassen ohne eigene Presets: ihr Stand nach dem Bau als „Standard“. */
    public static final String[] STANDARD = {"com.dan.sky.Sky", "com.dan.rime.Rime", "com.dan.smoke.Smoke", "com.dan.kino.Kino"};
    private static List<Preset> presets;
    /** Felder, die den Augenblick tragen, nicht die Einstellung (Sky.epochMillis ist die Uhr beim Bau) – nicht in die Startdaten. */
    static final Set<String> ZUSTAND = Set.of("epochMillis");

    public static synchronized List<Preset> presets() {
        if (presets != null) return presets;
        List<Preset> l = new ArrayList<>();
        for (String k : PAKETE) {
            try {
                Class<?> c = Class.forName(k);
                List<Method> ms = new ArrayList<>();
                for (Method m : c.getMethods())
                    if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 && m.getReturnType() == c) ms.add(m);
                ms.sort(Comparator.comparing(Method::getName));
                for (Method m : ms)
                    l.add(new Preset(c.getPackageName(), c.getSimpleName(), m.getName(), "Preset " + m.getName() + "() aus " + c.getPackageName(), Felder.lesen(m.invoke(null))));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Preset von " + k + " nicht lesbar: " + e, e);
            }
        }
        for (String k : STANDARD) {
            try {
                Class<?> c = Class.forName(k);
                l.add(new Preset(c.getPackageName(), c.getSimpleName(), "Standard", "Stand nach dem Bau (new " + c.getSimpleName() + "())",
                        Felder.lesen(c.getConstructor().newInstance(), ZUSTAND)));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Standard von " + k + " nicht lesbar: " + e, e);
            }
        }
        presets = Collections.unmodifiableList(l);
        return presets;
    }

    /** Die Typen der Felder einer Klasse (DOUBLE, INT, BOOLEAN, STRING, ENUM). */
    public static Map<String, String> typen(String klasse) {
        try {
            return Felder.typen(Class.forName(klasse));
        } catch (ClassNotFoundException e) {
            return Map.of();
        }
    }

    // ------------------------------------------------------------------ alles

    /** Alle Tabellen der Startdaten in der Reihenfolge des Skripts (Kinder: Belege, Stichworte, Stützpunkte, Presetwerte im Skript). */
    public static List<Tabelle> alles(Renderer r, File gelaendeDir) {
        List<Tabelle> l = new ArrayList<>();
        l.add(new Tabelle("BR_QUELLE", new String[]{"KURZ"}, quellen()));
        List<Daten.Zeile> bef = new ArrayList<>();
        for (Befund b : befunde()) bef.add(b.zeile());
        l.add(new Tabelle("BR_BEFUND", new String[]{"THEMA", "GEGENSTAND"}, bef));
        l.add(new Tabelle("BR_GELAENDE", new String[]{"NAME"}, gelaende(gelaendeDir, r.terrain.props)));
        l.add(new Tabelle("BR_AUSSCHNITT", new String[]{"NAME"}, ausschnitte()));
        l.add(new Tabelle("BR_HOEHENLINIE", new String[]{"HOEHE_M"}, hoehenlinien(r.terrain)));
        l.add(new Tabelle("BR_FELS", new String[]{"NAME"}, felsen(r.terrain)));
        l.add(new Tabelle("BR_BODEN", new String[]{"ART"}, boeden(r.boden)));
        l.add(new Tabelle("BR_LAGE", new String[]{"PHASE"}, lagen()));
        l.add(new Tabelle("BR_LAGE_UEBERGANG", new String[]{"VON_PHASE", "NACH_PHASE"}, uebergaenge()));
        l.add(new Tabelle("BR_KLIMA_MONAT", new String[]{"MONAT"}, klimaMonate()));
        l.add(new Tabelle("BR_HOEHENSTUFE", new String[]{"HOEHE_M"}, hoehenstufen()));
        l.add(new Tabelle("BR_STRECKE", new String[]{"ART", "NR"}, strecke(r.strecke)));
        l.add(new Tabelle("BR_BAHNHOF", new String[]{"NAME"}, bahnhoefe(r.strecke)));
        l.add(new Tabelle("BR_BAHNUEBERGANG", new String[]{"NR"}, bahnuebergaenge(r.strecke)));
        l.add(new Tabelle("BR_FAHRZEUG", new String[]{"NAME"}, fahrzeuge()));
        l.add(new Tabelle("BR_FAHRPLAN", new String[]{"RICHTUNG", "ABFAHRT_S", "JAHR"}, fahrplan()));
        l.add(new Tabelle("BR_BAU", new String[]{"NAME"}, bauten(r.gipfel)));
        l.add(new Tabelle("BR_ORT", new String[]{"NAME"}, orte(r.gipfel, r.terrain)));
        l.add(new Tabelle("BR_WEG", new String[]{"NAME"}, wege(r.menschen)));
        l.add(new Tabelle("BR_WETTERBUCH", new String[]{"NAME"}, wetterbuecher()));
        l.add(new Tabelle("BR_REGIEBUCH", new String[]{"NAME"}, regiebuecher()));
        l.add(new Tabelle("BR_ANSICHT", new String[]{"NAME", "ART"}, ansichten(r.terrain)));
        l.add(new Tabelle("BR_FAHRT", new String[]{"NAME"}, fahrten(r)));
        return l;
    }

    /** Für Prüfungen: die Zahlen auf einen Blick. */
    public static String zusammenfassung(List<Tabelle> t) {
        StringBuilder b = new StringBuilder();
        for (Tabelle x : t) b.append(b.length() > 0 ? ", " : "").append(x.zeilen().size()).append(' ').append(x.name().substring(3).toLowerCase(Locale.ROOT));
        return b.append(", ").append(presets().size()).append(" presets").toString();
    }

    /** Leere Liste als Hilfe. */
    static <T> List<T> keine() { return new ArrayList<>(); }

    static final Map<String, String> LEER = new LinkedHashMap<>();
}
