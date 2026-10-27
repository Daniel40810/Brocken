package com.dan.brocken.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 13: der Fahrplan der Brockenbahn zwischen Schierke und dem Brocken, Sommer 2026 (gültig ab 2. April 2026;
 * nach bahndampf.de, „Brockenbahn – Fahrplan, Fahrpreise“, Stand 15.09.2026, aus dem Fahrplan der HSB). Bergfahrten
 * mit Abfahrt Schierke und Ankunft Brocken, Talfahrten mit Abfahrt Brocken und Abfahrt Schierke. Er gilt in der Szene
 * an jedem Tag [G]: Winterfahrplan, Fahrten nur an bestimmten Tagen (A, B, Ba) und der Ersatzverkehr mit Bussen sind
 * nicht unterschieden; alle Züge fahren mit Dampf (das „Harzkamel“ freitags bis sonntags fehlt) [G].
 */
public final class Fahrplan {
    /** Eine Fahrt: bergauf (true) oder bergab, Abfahrt am Start und Abfahrt bzw. Ankunft am anderen Bahnhof (s des Tages). */
    public record Fahrt(boolean bergauf, int ab, int an, String name) {
        /** Fahrzeit nach Plan (s). */
        public int dauer() { return an - ab; }
    }

    /** Bergfahrten: Abfahrt Schierke, Ankunft Brocken. */
    static final String[][] BERG = {
            {"10:06", "10:39"}, {"11:03", "11:36"}, {"11:54", "12:27"}, {"12:58", "13:31"},
            {"13:48", "14:22"}, {"14:37", "15:30"}, {"15:47", "16:20"}, {"17:21", "17:54"}};
    /** Talfahrten: Abfahrt Brocken, Abfahrt Schierke. */
    static final String[][] TAL = {
            {"11:51", "12:52"}, {"12:42", "13:39"}, {"14:45", "15:26"}, {"15:45", "16:33"}, {"16:42", "17:19"}, {"18:30", "19:05"}};

    public static final String QUELLE = "bahndampf.de, Brockenbahn – Fahrplan Sommer 2026 (Stand 15.09.2026)";

    private Fahrplan() { }

    public static int sek(String hm) { return Integer.parseInt(hm.substring(0, 2)) * 3600 + Integer.parseInt(hm.substring(3)) * 60; }

    public static String uhr(double sek) {
        int m = (int) Math.floor(((sek % 86400) + 86400) % 86400 / 60);
        return String.format(Locale.GERMAN, "%02d:%02d", m / 60, m % 60);
    }

    /**
     * Seit Phase 17 (F31 A): der Fahrplan aus der Datenbank (BR_FAHRPLAN, aktive Zeilen), gesetzt beim Start von
     * {@code data.Szenendaten}; null: es gilt der Fahrplan oben.
     */
    public static volatile List<Fahrt> geladen;

    /** Alle Fahrten des Tages, nach der Abfahrt geordnet. */
    public static List<Fahrt> fahrten() {
        List<Fahrt> g = geladen;
        if (g != null && !g.isEmpty()) return new ArrayList<>(g);
        return ausDemCode();
    }

    /** Der Fahrplan, wie er im Code steht (für die Startdaten und die Prüfung). */
    public static List<Fahrt> ausDemCode() {
        List<Fahrt> f = new ArrayList<>();
        for (String[] b : BERG) f.add(new Fahrt(true, sek(b[0]), sek(b[1]), "Bergfahrt " + b[0] + " ab Schierke"));
        for (String[] t : TAL) f.add(new Fahrt(false, sek(t[0]), sek(t[1]), "Talfahrt " + t[0] + " ab Brocken"));
        f.sort((a, b) -> Integer.compare(a.ab(), b.ab()));
        return f;
    }

    /** Die Bergfahrten. */
    public static List<Fahrt> bergfahrten() {
        List<Fahrt> f = new ArrayList<>();
        for (Fahrt x : fahrten()) if (x.bergauf()) f.add(x);
        return f;
    }

    /** Die Talfahrten. */
    public static List<Fahrt> talfahrten() {
        List<Fahrt> f = new ArrayList<>();
        for (Fahrt x : fahrten()) if (!x.bergauf()) f.add(x);
        return f;
    }
}
