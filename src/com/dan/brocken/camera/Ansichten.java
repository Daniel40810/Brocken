package com.dan.brocken.camera;

import com.dan.brocken.scene.Bodenkarte;
import com.dan.brocken.scene.Buehne;
import com.dan.brocken.scene.Site;

import java.util.List;
import java.util.Locale;

/**
 * Die neun Ansichten nach Namen auf den Tasten 1 bis 9 (Werkbuch, Abschnitte 6 und 7). Seit Phase 1 auf der Kuppe der
 * Bühne, ab Phase 2 auf dem Gelände; die Lagen kommen aus den Orten in {@link Site} [G]:
 * <ol>
 * <li><b>Gipfelstein</b>: auf dem Plateau, Blick nach Westnordwest über Torfhaus; am Morgen steht die Sonne im Rücken
 * (die Richtung, in der das Brockengespenst erscheint, Phase 7).</li>
 * <li><b>Bahnsteig</b>: am Bahnhof Brocken, Blick nach Südosten, wo die Spirale den Gipfel hinab nach Schierke läuft.</li>
 * <li><b>Goetheweg</b>: am Weg von Torfhaus, Blick den Westhang hinauf zur Kuppe (die Seite der Westlage mit Stau).</li>
 * <li><b>Schierke</b>: am Bahnhof Schierke, Blick nach Nordwesten hinauf zum Gipfel.</li>
 * <li><b>Über dem Nebelmeer</b>: frei schwebend 4 km nordöstlich auf 1 050 m, Blick auf die Kuppe; in einer Inversion
 * (Phase 6) liegt das Nebelmeer darunter.</li>
 * <li><b>Vogelauge</b>: 9 km südlich, 3 km über Grund, Blick nach Norden über das ganze Hauptraster.</li>
 * <li><b>Teufelskanzel</b> (seit Phase 8): rund 25 m südwestlich der Felsburg auf der Kuppe, Blick auf Teufelskanzel und
 * Hexenaltar, davor Blöcke und Heide.</li>
 * <li><b>Goethemoor</b> (seit Phase 8): im Moor am Goetheweg, Blick nach Osten zur Kuppe über Torfmoos und Wollgras.</li>
 * <li><b>Totholz</b> (seit Phase 9): im abgestorbenen Fichtenwald westlich des Gipfels auf 850 m, Blick nach Osten
 * hinauf: graue stehende und gebrochene Fichten, liegende, dazwischen junge Fichten und Ebereschen.</li>
 * </ol>
 */
public final class Ansichten {
    /**
     * Eine Ansicht: Taste, Name, Lage (x, z), Blickrichtung (Grad von Nord), Neigung (Grad nach unten, negativ nach
     * oben), Augenhöhe über Grund (m) und ab welcher Phase sie einen Ort hat.
     */
    public record Ansicht(int key, String name, double x, double z, double heading, double pitch, double eyeAboveGround, int phase) {
        @Override public String toString() { return key + " " + name; }
    }

    public static final List<Ansicht> ALLE = List.of(
            new Ansicht(1, "Gipfelstein", Site.GIPFELSTEIN.x() + 6, Site.GIPFELSTEIN.z() + 4, 292, 3, 1.7, 1),
            new Ansicht(2, "Bahnsteig", Site.BAHNHOF_BROCKEN.x(), Site.BAHNHOF_BROCKEN.z(), 140, 5, 1.7, 1),
            new Ansicht(3, "Goetheweg", Site.GOETHEWEG.x(), Site.GOETHEWEG.z(), 82, -5, 1.7, 1),
            new Ansicht(4, "Schierke", Site.SCHIERKE.x(), Site.SCHIERKE.z(), 318, -4, 1.7, 1),
            new Ansicht(5, "Über dem Nebelmeer", 2800, -2800, 225, 1, 375, 1),
            new Ansicht(6, "Vogelauge", 0, 9000, 0, 22, 3000, 1),
            new Ansicht(7, "Teufelskanzel", Bodenkarte.FELSBURGEN[0].x() - 13, Bodenkarte.FELSBURGEN[0].z() + 21, 30, 2, 1.7, 8),
            new Ansicht(8, "Goethemoor", Site.GOETHEWEG.x() - 70, Site.GOETHEWEG.z() + 45, 78, 6, 1.7, 8),
            new Ansicht(9, "Totholz", -1780, 260, 82, -2, 1.7, 9));

    /** Taste der Ansicht „Gipfelstein“ (der Standort für das Brockengespenst ab Phase 7). */
    public static final int GIPFELSTEIN = 1;

    /** Ob die Ansicht schon einen Ort hat. */
    public static boolean ready(Ansicht a, int phase) { return a.phase() <= phase; }

    public static Ansicht byName(String name) {
        for (Ansicht a : ALLE) if (a.name().equalsIgnoreCase(name) || a.name().replace(" ", "").equalsIgnoreCase(name)) return a;
        // ohne Sonderzeichen (Umgebungsvariablen unter Windows): „UeberdemNebelmeer“, „Gipfelstein“ …
        String n = name.replace(" ", "").toLowerCase(Locale.ROOT);
        for (Ansicht a : ALLE) {
            String k = a.name().replace(" ", "").toLowerCase(Locale.ROOT).replace("ü", "ue").replace("ö", "oe").replace("ä", "ae").replace("ß", "ss");
            if (k.equals(n)) return a;
        }
        try { return byKey(Integer.parseInt(name.trim())); } catch (NumberFormatException e) { return null; }
    }

    public static Ansicht byKey(int key) {
        for (Ansicht a : ALLE) if (a.key() == key) return a;
        return null;
    }

    /** Stellt die Kamera auf die Ansicht über dem Grund {@code ground}; false, wenn sie in dieser Phase noch keinen Ort hat. */
    public static boolean apply(Ansicht a, Camera c, int phase, java.util.function.DoubleBinaryOperator ground) {
        if (!ready(a, phase)) return false;
        c.stopFahrt();
        c.stopMitfahrt();
        c.diving = false;
        double g = ground.applyAsDouble(a.x(), a.z());
        c.groundLevel = g;
        c.waterLevel = g;
        c.place(a.x(), g + a.eyeAboveGround(), a.z(), a.heading(), a.pitch());
        return true;
    }

    /** Wie oben, über der Bühne von Phase 1. */
    public static boolean apply(Ansicht a, Camera c, int phase) { return apply(a, c, phase, Buehne::height); }

    /** „1 Gipfelstein (Auge 1,7 m über Grund, Blick 292°)“ */
    public static String describe(Ansicht a, int phase) {
        if (!ready(a, phase)) return a + " (folgt in Phase " + a.phase() + ")";
        return String.format(Locale.GERMAN, "%s (Auge %.1f m über Grund, Blick %03.0f°)", a, a.eyeAboveGround(), a.heading());
    }

    private Ansichten() { }
}
