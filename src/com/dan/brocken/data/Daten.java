package com.dan.brocken.data;

import com.dan.brocken.scene.SceneClock;
import com.dan.brocken.scene.Site;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Datenseite von Brocken (Phase 17, Werkbuch Abschnitt 9, F25–F37 „alle A“): Szenenzeit, Geometrie und die Zeile
 * als Bauteil, das {@link Speicher} schreibt und liest. Eine Zeile ist eine geordnete Abbildung Spalte → Wert (Text,
 * Zahl, Wahrheitswert, {@link Geom}, Bytes für BLOB); die Spalten heißen wie in {@code db/sql/01_tabellen.sql}. So
 * schreibt dieselbe Chronik in die Datenbank und in die Dateien, und „Übernehmen“ trägt die Zeilen aus den Dateien
 * unverändert in die Datenbank.
 * <p>
 * Lage in Szenenmetern (x nach Osten, z nach Süden, Ursprung am Gipfelstein); Länge und Breite in 8307 rechnet
 * {@link #lonLat} (F28 A).
 */
public final class Daten {
    private Daten() { }

    /** HERKUNFT: mitgeliefert (stellt der Einrichter wieder her) oder eigen (fasst er nie an). */
    public static final String MITGELIEFERT = "MITGELIEFERT", EIGEN = "EIGEN";

    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm", Locale.GERMANY);

    // ------------------------------------------------------------------ Szenenzeit

    public static ZonedDateTime zeit(double epochMs) { return Instant.ofEpochMilli((long) Math.floor(epochMs)).atZone(SceneClock.ZONE); }

    /** „21.10.2026 07:52 MESZ“ (SZENE_TEXT). */
    public static String text(double epochMs) {
        ZonedDateTime z = zeit(epochMs);
        return ZEIT.format(z) + " " + (SceneClock.ZONE.getRules().isDaylightSavings(z.toInstant()) ? "MESZ" : "MEZ");
    }

    /** „2026-10-21“ (SZENE_TAG). */
    public static String tag(double epochMs) { return zeit(epochMs).toLocalDate().toString(); }

    /** „07:52“ */
    public static String uhr(double epochMs) { ZonedDateTime z = zeit(epochMs); return String.format(Locale.ROOT, "%02d:%02d", z.getHour(), z.getMinute()); }

    /** Setzt SZENE_MS, SZENE_TEXT und SZENE_TAG einer Zeile. */
    public static Zeile szene(Zeile z, double epochMs) {
        long ms = (long) Math.floor(epochMs);
        z.put("SZENE_MS", ms);
        z.put("SZENE_TEXT", text(ms));
        z.put("SZENE_TAG", tag(ms));
        return z;
    }

    /** {lon, lat} eines Szenenpunkts. */
    public static double[] lonLat(double x, double z) {
        double[] ll = Site.latLon(x, z);
        return new double[]{ll[1], ll[0]};
    }

    /** Szenenpunkt {x, z} aus Länge und Breite. */
    public static double[] xz(double lon, double lat) { return Site.xz(new double[]{lat, lon}); }

    /** Auf n Stellen gerundet (für Spalten mit Nachkommastellen); NaN bleibt NaN. */
    public static double r(double v, int n) { double k = Math.pow(10, n); return Math.round(v * k) / k; }

    // ------------------------------------------------------------------ Zeile

    /** Eine Zeile einer Tabelle: Spalte → Wert in der Reihenfolge des Einfügens. */
    public static final class Zeile extends LinkedHashMap<String, Object> {
        public Zeile() { }
        public Zeile(Map<String, Object> m) { super(m); }
        /** Ein Wert; NaN und unendlich werden zu leer (NULL). */
        public Zeile mit(String spalte, Object wert) {
            if (wert instanceof Double d && (d.isNaN() || d.isInfinite())) wert = null;
            if (wert instanceof Float f && (f.isNaN() || f.isInfinite())) wert = null;
            put(spalte, wert);
            return this;
        }
        public String text(String k) { Object v = get(k); return v == null ? null : v.toString(); }
        public double zahl(String k) {
            Object v = get(k);
            if (v instanceof Number n) return n.doubleValue();
            if (v instanceof Boolean b) return b ? 1 : 0;
            if (v == null) return Double.NaN;
            try { return Double.parseDouble(v.toString().replace(',', '.')); } catch (NumberFormatException e) { return Double.NaN; }
        }
        public long ganz(String k, long sonst) { double d = zahl(k); return Double.isNaN(d) ? sonst : Math.round(d); }
        public boolean ja(String k) { return zahl(k) == 1 || "true".equals(text(k)); }
        public Geom geom(String k) { return get(k) instanceof Geom g ? g : null; }
        public byte[] bytes(String k) { return get(k) instanceof byte[] b ? b : null; }

        // -------------------------------------------------- als eine Textzeile (Dateien ohne Datenbank)

        /**
         * Eine Zeile Text: Spalten durch Tabulator getrennt, je {@code SPALTE=t:wert} mit dem Typ s (Text), n (Zahl), g
         * (Geometrie als WKT in Länge und Breite), b (Bytes in Base64) oder 0 (leer). Tabulator, Zeilenumbruch und
         * Gegenstrich im Text werden mit \ geschützt.
         */
        public String kodieren() {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, Object> e : entrySet()) {
                if (b.length() > 0) b.append('\t');
                b.append(e.getKey()).append('=');
                Object v = e.getValue();
                if (v == null) b.append("0:");
                else if (v instanceof Number n) b.append("n:").append(com.dan.store.Zahl.text(n.doubleValue()));
                else if (v instanceof Boolean x) b.append("n:").append(x ? 1 : 0);
                else if (v instanceof Geom g) b.append("g:").append(g.wkt());
                else if (v instanceof byte[] by) b.append("b:").append(Base64.getEncoder().encodeToString(by));
                else b.append("s:").append(schuetzen(v.toString()));
            }
            return b.toString();
        }

        public static Zeile dekodieren(String line) {
            Zeile z = new Zeile();
            for (String p : line.split("\t")) {
                int i = p.indexOf('=');
                if (i <= 0 || p.length() < i + 3) continue;
                String k = p.substring(0, i), t = p.substring(i + 1, i + 2), v = p.substring(i + 3);
                switch (t) {
                    case "n" -> { double d = com.dan.store.Zahl.lies(v); z.put(k, d == Math.rint(d) && Math.abs(d) < 9e15 ? (Object) (long) d : (Object) d); }
                    case "g" -> z.put(k, Geom.ausWkt(v));
                    case "b" -> z.put(k, Base64.getDecoder().decode(v));
                    case "s" -> z.put(k, entschuetzen(v));
                    default -> z.put(k, null);
                }
            }
            return z;
        }

        static String schuetzen(String s) { return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "").replace("\n", "\\n"); }

        static String entschuetzen(String s) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length()) {
                    char n = s.charAt(++i);
                    b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
                } else b.append(c);
            }
            return b.toString();
        }
    }

    // ------------------------------------------------------------------ Geometrie

    /**
     * Eine Geometrie in Szenenmetern: PUNKT, LINIE, MULTILINIE, FLAECHE oder MULTIFLAECHE. Bei MULTIFLAECHE ist
     * {@link #ringe} je Teil die Liste der Ringe (erster Ring außen, dann Löcher); sonst stehen die Punkte in {@link #teile}.
     */
    public record Geom(String art, List<double[][]> teile, List<List<double[][]>> ringe) {
        public Geom {
            teile = List.copyOf(teile);
            List<List<double[][]>> r = new ArrayList<>();
            if (ringe != null) for (List<double[][]> x : ringe) r.add(List.copyOf(x));
            ringe = List.copyOf(r);
        }
        public static Geom punkt(double x, double z) { return new Geom("PUNKT", List.<double[][]>of(new double[][]{{x, z}}), null); }
        public static Geom linie(double[][] p) { return new Geom("LINIE", List.<double[][]>of(p), null); }
        public static Geom multilinie(List<double[][]> p) { return new Geom("MULTILINIE", p, null); }
        public static Geom flaeche(double[][] p) { return new Geom("FLAECHE", List.<double[][]>of(p), null); }
        /** Mehrere Flächen mit Löchern: je Teil {außen, Loch, Loch …}. */
        public static Geom multiflaeche(List<List<double[][]>> teile) {
            List<double[][]> aussen = new ArrayList<>();
            for (List<double[][]> t : teile) aussen.add(t.get(0));
            return new Geom("MULTIFLAECHE", aussen, teile);
        }
        public double[][] xz() { return teile.get(0); }

        private static double[][] ll(double[][] xz) {
            double[][] r = new double[xz.length][];
            for (int i = 0; i < xz.length; i++) r[i] = lonLat(xz[i][0], xz[i][1]);
            return r;
        }

        /** Ein Ring in Länge und Breite, geschlossen, außen gegen den Uhrzeigersinn, Löcher mit ihm (wie Oracle es will). */
        private static String ring(double[][] xz, boolean aussen) {
            double[][] p = ll(xz);
            double a = 0;
            for (int i = 0; i < p.length; i++) { double[] u = p[i], v = p[(i + 1) % p.length]; a += u[0] * v[1] - v[0] * u[1]; }
            if (a > 0 != aussen) {
                double[][] q = new double[p.length][];
                for (int i = 0; i < p.length; i++) q[i] = p[p.length - 1 - i];
                p = q;
            }
            StringBuilder b = new StringBuilder("(");
            for (int i = 0; i <= p.length; i++) {
                double[] q = p[i % p.length];
                b.append(i > 0 ? ", " : "").append(n7(q[0])).append(' ').append(n7(q[1]));
            }
            return b.append(')').toString();
        }

        private static String n7(double v) {
            String s = String.format(Locale.ROOT, "%.7f", v);
            while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
            return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
        }

        /** Als WKT in Länge und Breite. */
        public String wkt() {
            return switch (art) {
                case "PUNKT" -> { double[] p = lonLat(xz()[0][0], xz()[0][1]); yield com.dan.store.Wkt.punkt(p[0], p[1]); }
                case "LINIE" -> com.dan.store.Wkt.linie(ll(xz()));
                case "FLAECHE" -> "POLYGON (" + ring(xz(), true) + ")";
                case "MULTIFLAECHE" -> {
                    StringBuilder b = new StringBuilder("MULTIPOLYGON (");
                    for (int t = 0; t < ringe.size(); t++) {
                        b.append(t > 0 ? ", " : "").append('(');
                        List<double[][]> r = ringe.get(t);
                        for (int i = 0; i < r.size(); i++) b.append(i > 0 ? ", " : "").append(ring(r.get(i), i == 0));
                        b.append(')');
                    }
                    yield b.append(')').toString();
                }
                default -> {
                    StringBuilder b = new StringBuilder("MULTILINESTRING (");
                    for (int i = 0; i < teile.size(); i++) {
                        String l = com.dan.store.Wkt.linie(ll(teile.get(i)));
                        b.append(i > 0 ? ", " : "").append(l.substring(l.indexOf('(')));
                    }
                    yield b.append(')').toString();
                }
            };
        }

        /** Aus WKT in Länge und Breite (wie {@code SDO_UTIL.TO_WKTGEOMETRY} es liefert) zurück in Szenenmeter. */
        public static Geom ausWkt(String wkt) {
            if (wkt == null || wkt.isBlank()) return null;
            String w = wkt.strip();
            if (w.startsWith("MULTIPOLYGON") || w.startsWith("POLYGON")) {
                List<List<double[][]>> teile = new ArrayList<>();
                boolean multi = w.startsWith("MULTI");
                int ringTiefe = multi ? 3 : 2, tiefe = 0, start = -1;
                List<double[][]> akt = null;
                for (int i = 0; i < w.length(); i++) {
                    char ch = w.charAt(i);
                    if (ch == '(') {
                        tiefe++;
                        if (tiefe == ringTiefe - 1) { akt = new ArrayList<>(); teile.add(akt); }
                        if (tiefe == ringTiefe) start = i;
                    } else if (ch == ')') {
                        if (tiefe == ringTiefe && start >= 0 && akt != null) {
                            double[][] ll = com.dan.store.Wkt.punkte(w.substring(start, i + 1));
                            int n = ll.length > 3 ? ll.length - 1 : ll.length;
                            double[][] p = new double[n][];
                            for (int k = 0; k < n; k++) p[k] = Daten.xz(ll[k][0], ll[k][1]);
                            akt.add(p);
                            start = -1;
                        }
                        tiefe--;
                    }
                }
                if (!multi) return flaeche(teile.get(0).get(0));
                return multiflaeche(teile);
            }
            String art = w.startsWith("POINT") ? "PUNKT" : w.startsWith("MULTILINESTRING") ? "MULTILINIE" : "LINIE";
            List<double[][]> teile = new ArrayList<>();
            String[] stuecke = art.equals("MULTILINIE") ? w.substring(w.indexOf('(') + 1, w.lastIndexOf(')')).split("\\)\\s*,\\s*\\(") : new String[]{w};
            for (String s : stuecke) {
                double[][] ll = com.dan.store.Wkt.punkte(s);
                double[][] p = new double[ll.length][];
                for (int i = 0; i < ll.length; i++) p[i] = Daten.xz(ll[i][0], ll[i][1]);
                teile.add(p);
            }
            return new Geom(art, teile, null);
        }

        /** Länge aller Linien (m, in der Ebene der Szene). */
        public double laenge() {
            double s = 0;
            for (double[][] p : teile) for (int i = 0; i + 1 < p.length; i++) s += Math.hypot(p[i + 1][0] - p[i][0], p[i + 1][1] - p[i][1]);
            return s;
        }

        /** Fläche (m², nach Gauß; bei MULTIFLAECHE ohne die Löcher). */
        public double flaecheM2() {
            if (art.equals("MULTIFLAECHE")) {
                double s = 0;
                for (List<double[][]> t : ringe) for (int i = 0; i < t.size(); i++) s += (i == 0 ? 1 : -1) * gauss(t.get(i));
                return s;
            }
            return gauss(xz());
        }

        static double gauss(double[][] p) {
            double a = 0;
            for (int i = 0; i < p.length; i++) { double[] u = p[i], v = p[(i + 1) % p.length]; a += u[0] * v[1] - v[0] * u[1]; }
            return Math.abs(a) / 2;
        }

        /** Zahl aller Punkte. */
        public int punkte() {
            int n = 0;
            if (art.equals("MULTIFLAECHE")) { for (List<double[][]> t : ringe) for (double[][] r : t) n += r.length; return n; }
            for (double[][] p : teile) n += p.length;
            return n;
        }
    }

    // ------------------------------------------------------------------ Betrieb

    /** Die letzte Sitzung für „Weiter wie zuletzt“. */
    public record Sitzung(long id, long beginnMs, Long endeMs, boolean sauber, String zustand, Long zustandMs, String regiebuch, Double regieSekunde,
                          Long szeneMs, String lage) {
        public String szeneText() { return szeneMs == null ? "" : Daten.text(szeneMs); }
    }

    /** Was Startbild und Block DATEN über den Stand zeigen: Zahl der Zeilen je Tabelle (ohne Präfix), dazu die letzte Sitzung. */
    public record Stand(Map<String, Integer> zahlen, Sitzung letzte) {
        public int n(String tabelle) { return zahlen.getOrDefault(tabelle, 0); }
    }
}
