package com.dan.brocken.data;

import com.dan.store.Db;
import com.dan.store.Einrichter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Das Schema BROCKEN (Phase 17, Werkbuch Abschnitt 9, F25–F37 „alle A“): Ort von {@code db/}, Zugang aus
 * {@code db/db.properties} und der Einrichter mit allem, was er für Brocken prüft. Schema und Benutzer legt
 * {@code db/USER.sql} an (Brocken auf PDBORCL, Rolle DB_CONNECT).
 */
public final class Verbindung {
    private Verbindung() { }

    public static final String PRAEFIX = "BR_";
    public static final String ENV = "BROCKEN_DB_PASSWORD";
    /** Die Ausbaustufe dieser App (BR_SCHEMA.VERSION). */
    public static final int VERSION = 1;
    public static final String[] SICHTEN = {"BR_BROCKEN_V", "BR_TAGESKARTE_V", "BR_FAHRPLAN_V", "BR_JAHR_V", "BR_GESPENST_V", "BR_BEFUND_V", "BR_LETZTE_SITZUNG_V"};
    /** Die Geometriespalten der Tabellen (je eine mit räumlichem Index). */
    public static final String[] METADATEN = {"BR_AUSSCHNITT.FLAECHE", "BR_HOEHENLINIE.LINIE", "BR_FELS.LAGE", "BR_BODEN.FLAECHE", "BR_STRECKE.LINIE",
            "BR_BAHNHOF.LAGE", "BR_BAHNUEBERGANG.LAGE", "BR_BAU.GRUNDRISS", "BR_ORT.LAGE", "BR_WEG.LINIE", "BR_SITZUNG.AUGE", "BR_GESPENST.LAGE",
            "BR_ANSICHT.LAGE", "BR_FAHRT.WEG", "BR_MERKPUNKT.LAGE"};

    /** Der Ordner db: -Dbrocken.db.dir, sonst ./db, sonst db neben dem Jar. */
    public static File ordner() { return Db.ordner("brocken.db.dir", Verbindung.class); }

    public static File skripte() { return new File(ordner(), "sql"); }

    /**
     * Der Zugang aus db/db.properties. Fehlt die Datei, wird sie aus der Vorlage db.properties.example angelegt (ohne
     * Passwort); das Startbild fragt dann danach und trägt es nach dem Verbinden ein.
     */
    public static Db.Konfig konfig() throws IOException {
        File f = new File(ordner(), "db.properties"), vorlage = new File(ordner(), "db.properties.example");
        if (!f.isFile() && vorlage.isFile()) Files.copy(vorlage.toPath(), f.toPath());
        return Db.konfig(f, "Brocken", ENV);
    }

    /** Die räumlichen Indizes (je Geometriespalte einer: BR_BAU_GRUNDRISS_SX …). */
    public static String[] raeumlich() {
        String[] r = new String[METADATEN.length];
        for (int i = 0; i < r.length; i++) r[i] = METADATEN[i].replace('.', '_') + "_SX";
        return r;
    }

    /** Die Zahlen der Startdaten aus der Zeile {@code -- ZAHLEN: Art=n; …} von 04_startdaten.sql. */
    public static Map<String, Integer> zahlen(File dir) {
        Map<String, Integer> m = new LinkedHashMap<>();
        try (var lines = Files.lines(new File(dir, "04_startdaten.sql").toPath(), StandardCharsets.UTF_8)) {
            String z = lines.filter(l -> l.startsWith("-- ZAHLEN:")).findFirst().orElse(null);
            if (z != null)
                for (String p : z.substring(10).split(";")) {
                    String[] kv = p.strip().split("=");
                    if (kv.length == 2) m.put(kv[0].strip(), Integer.parseInt(kv[1].strip()));
                }
        } catch (IOException | RuntimeException e) { /* dann prüft der Einrichter ohne Zahlen */ }
        return m;
    }

    /** Die SQL-Abfrage je Zahl der Startdaten (Schlüssel wie in der Zeile ZAHLEN). */
    public static final Map<String, String> ZAEHLEN = new LinkedHashMap<>();
    static {
        String m = " WHERE HERKUNFT = 'MITGELIEFERT'";
        String[][] t = {{"Quellen", "BR_QUELLE"}, {"Befunde", "BR_BEFUND"}, {"Gelände", "BR_GELAENDE"}, {"Ausschnitte", "BR_AUSSCHNITT"},
                {"Höhenlinien", "BR_HOEHENLINIE"}, {"Felsen", "BR_FELS"}, {"Böden", "BR_BODEN"}, {"Lagen", "BR_LAGE"}, {"Übergänge", "BR_LAGE_UEBERGANG"},
                {"Klimamonate", "BR_KLIMA_MONAT"}, {"Höhenstufen", "BR_HOEHENSTUFE"}, {"Strecke", "BR_STRECKE"}, {"Bahnhöfe", "BR_BAHNHOF"},
                {"Bahnübergänge", "BR_BAHNUEBERGANG"}, {"Fahrzeuge", "BR_FAHRZEUG"}, {"Fahrplan", "BR_FAHRPLAN"}, {"Bauten", "BR_BAU"}, {"Orte", "BR_ORT"},
                {"Wege", "BR_WEG"}, {"Wetterbücher", "BR_WETTERBUCH"}, {"Regiebücher", "BR_REGIEBUCH"}, {"Fahrten", "BR_FAHRT"}, {"Presets", "BR_PAKET_PRESET"}};
        for (String[] x : t) ZAEHLEN.put(x[0], "SELECT COUNT(*) FROM " + x[1] + m);
        ZAEHLEN.put("Belege", "SELECT COUNT(*) FROM BR_BEFUND_QUELLE x JOIN BR_BEFUND b ON b.ID = x.BEFUND_ID WHERE b.HERKUNFT = 'MITGELIEFERT'");
        ZAEHLEN.put("Stichworte", "SELECT COUNT(*) FROM BR_STICHWORT x JOIN BR_REGIEBUCH r ON r.ID = x.REGIEBUCH_ID WHERE r.HERKUNFT = 'MITGELIEFERT'");
        ZAEHLEN.put("Ansichten", "SELECT COUNT(*) FROM BR_ANSICHT" + m + " AND ART = 'FEST'");
        ZAEHLEN.put("Stützpunkte", "SELECT COUNT(*) FROM BR_STUETZPUNKT x JOIN BR_FAHRT f ON f.ID = x.FAHRT_ID WHERE f.HERKUNFT = 'MITGELIEFERT'");
        ZAEHLEN.put("Presetwerte", "SELECT COUNT(*) FROM BR_PAKET_WERT x JOIN BR_PAKET_PRESET p ON p.ID = x.PAKET_PRESET_ID WHERE p.HERKUNFT = 'MITGELIEFERT'");
    }

    /** Der Einrichter für das Schema BROCKEN. */
    public static Einrichter einrichter() {
        Einrichter e = new Einrichter();
        e.praefix = PRAEFIX;
        e.version = VERSION;
        e.sichten = SICHTEN.clone();
        e.metadaten = METADATEN.clone();
        e.raeumlich = raeumlich();
        e.startdaten = () -> {
            List<Einrichter.Soll> l = new ArrayList<>();
            Map<String, Integer> z = zahlen(skripte());
            for (Map.Entry<String, String> q : ZAEHLEN.entrySet()) if (z.containsKey(q.getKey())) l.add(new Einrichter.Soll(q.getKey(), q.getValue(), z.get(q.getKey())));
            return l;
        };
        e.rechnet = () -> List.of(
                new Einrichter.Soll("Brockenbahn (km auf dem Ellipsoid, Szene 16,5 im Hauptraster)",
                        "SELECT TO_CHAR(MDSYS.SDO_GEOM.SDO_LENGTH(LINIE, 0.05, 'unit=KM'), 'FM990.00') FROM BR_STRECKE WHERE ART = 'GLEIS' AND NR = 0", -1),
                new Einrichter.Soll("Hauptraster (km² auf dem Ellipsoid, Szene 12,7 × 11,9 = 151)",
                        "SELECT TO_CHAR(MDSYS.SDO_GEOM.SDO_AREA(FLAECHE, 0.05, 'unit=SQ_KM'), 'FM9990.0') FROM BR_AUSSCHNITT WHERE ART = 'HAUPTRASTER'", -1),
                new Einrichter.Soll("Bauten am Gipfel (Grundfläche m²)",
                        "SELECT TO_CHAR(COUNT(*)) || ' Bauten, ' || TO_CHAR(ROUND(SUM(MDSYS.SDO_GEOM.SDO_AREA(GRUNDRISS, 0.05, 'unit=SQ_M')))) || ' m²' FROM BR_BAU WHERE ART <> 'GARTEN'", -1),
                new Einrichter.Soll("Moore (ha, räumlich)", "SELECT TO_CHAR(ROUND(MDSYS.SDO_GEOM.SDO_AREA(FLAECHE, 0.05, 'unit=SQ_M') / 10000, 1)) FROM BR_BODEN WHERE ART = 'MOOR'", -1),
                new Einrichter.Soll("Felsen in 300 m um den Gipfelstein (räumlich)",
                        "SELECT TO_CHAR(COUNT(*)) FROM BR_FELS f, BR_ORT o WHERE o.NAME = 'Gipfelstein' AND MDSYS.SDO_WITHIN_DISTANCE(f.LAGE, o.LAGE, 'distance=300 unit=M') = 'TRUE'", -1),
                new Einrichter.Soll("Fahrplan (Berg- und Talfahrten)",
                        "SELECT TO_CHAR(SUM(CASE WHEN RICHTUNG = 'BERG' THEN 1 ELSE 0 END)) || ' bergauf, ' || TO_CHAR(SUM(CASE WHEN RICHTUNG = 'TAL' THEN 1 ELSE 0 END)) || ' bergab' FROM BR_FAHRPLAN WHERE AKTIV = 1", -1),
                new Einrichter.Soll("Chronik: Stunden, Tage, Nebelmeere, Gespenster, Zugfahrten",
                        "SELECT (SELECT COUNT(*) FROM BR_STUNDE) || ', ' || (SELECT COUNT(*) FROM BR_TAG) || ', ' || (SELECT COUNT(*) FROM BR_NEBELMEER) || ', ' "
                                + "|| (SELECT COUNT(*) FROM BR_GESPENST) || ', ' || (SELECT COUNT(*) FROM BR_ZUGFAHRT) FROM DUAL", -1),
                new Einrichter.Soll("Tageskarte: Objekte in BR_TAGESKARTE_V", "SELECT TO_CHAR(COUNT(*)) FROM BR_TAGESKARTE_V", -1));
        return e;
    }
}
