package com.dan.brocken.scene;

import java.util.Locale;

/**
 * Die Bühne von Phase 1: ein Platzhalter für das Gelände, bis Phase 2 GLO-30, DGM1 und DGM200 einliest. Statt einer
 * Ebene wie in Geiranger steht hier eine gerechnete Kuppe, damit die Ansichten (Gipfelstein, Bahnsteig, Nebelmeer)
 * schon in Phase 1 sinnvoll stehen [G]:
 * <ul>
 * <li>eine Hochfläche auf rund 640 m, nach Osten fallend (Torfhaus rund 700 m, Drei Annen Hohne rund 570 m),</li>
 * <li>darauf die Kuppe des Brockens mit 1 141,2 m am Gipfelstein, h = A·exp(−(r/R)^P) mit R = 2 600 m, P = 1,6,</li>
 * <li>der Wurmberg [zu prüfen] als zweite, kleinere Kuppe im Süden (971 m),</li>
 * <li>ab 12 km um den Gipfel sinkt alles weich auf das Vorland mit 200 m (bis 30 km), der Fernring bleibt flach.</li>
 * </ul>
 * Seit Phase 2 ist die Kuppe nur noch der Ersatz, wenn {@code daten/gelaende} fehlt: {@link Terrain#ausBuehne()} rechnet
 * aus ihr die drei Raster, gezeichnet wird sie dann wie das echte Gelände (mit den Marken aus {@code shader/marken.glsl}).
 */
public final class Buehne {
    private Buehne() { }

    /** Höhe der Hochfläche am Ursprung (m), Gefälle nach Osten (m je m), Grenze des Gefälles (m), Höhe des Vorlands (m). */
    public static final double BASIS = 640, GEFAELLE = 0.012, GEFAELLE_BIS = 8000, VORLAND = 200;
    /** Kuppe: Halbmesser R (m) und Form P; Höhe A ergibt sich aus dem Gipfel. */
    public static final double KUPPE_R = 2600, KUPPE_P = 1.6;
    /** Wurmberg [G, zu prüfen]: Lage (Szene), Höhe der Beule über dem Grund (m), Halbmesser (m). */
    public static final double WURM_X = Site.WURMBERG.x(), WURM_Z = Site.WURMBERG.z(), WURM_H = 300, WURM_R = 1200;
    /** Übergang auf das Vorland: von, bis (m vom Gipfel). */
    public static final double VORLAND_VON = 12_000, VORLAND_BIS = 30_000;

    /** Höhe der Bühne über NHN (m) am Ort (x, z). */
    public static double height(double x, double z) {
        double r = Math.hypot(x, z);
        double base = BASIS - GEFAELLE * Math.max(-GEFAELLE_BIS, Math.min(GEFAELLE_BIS, x));
        double a = Site.GIPFEL - BASIS;
        double h = base + a * Math.exp(-Math.pow(r / KUPPE_R, KUPPE_P));
        double dw = Math.hypot(x - WURM_X, z - WURM_Z) / WURM_R;
        h += WURM_H * Math.exp(-dw * dw);
        double t = smooth(VORLAND_VON, VORLAND_BIS, r);
        return h + (VORLAND - h) * t;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** „Kern“, „Hauptraster“, „Fernring“ oder „außerhalb“ am Ort (x, z). */
    public static String zone(double x, double z) {
        if (Site.KERN.contains(x, z)) return Site.KERN.name();
        if (Site.HAUPTRASTER.contains(x, z)) return Site.HAUPTRASTER.name();
        if (Math.hypot(x, z) <= Site.FERNRING) return "Fernring";
        return "außerhalb";
    }

    /** Der nächste Ort aus {@link Site#ORTE} und sein Abstand in der Ebene: „1,9 km bis Goetheweg“. */
    public static String naechsterOrt(double x, double z) {
        Site.Ort best = null;
        double bd = Double.MAX_VALUE;
        for (Site.Ort o : Site.ORTE) {
            double d = Math.hypot(x - o.x(), z - o.z());
            if (d < bd) { bd = d; best = o; }
        }
        if (best == null) return "—";
        return bd < 1000 ? String.format(Locale.GERMAN, "%.0f m bis %s", bd, best.name()) : String.format(Locale.GERMAN, "%.1f km bis %s", bd / 1000, best.name());
    }
}
