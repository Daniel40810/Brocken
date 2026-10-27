package com.dan.brocken.test;

import com.dan.brocken.data.Chronik;
import com.dan.brocken.data.DateiSpeicher;
import com.dan.brocken.data.Daten;
import com.dan.brocken.data.Datenseite;
import com.dan.brocken.data.DbSpeicher;
import com.dan.brocken.data.Szenendaten;
import com.dan.brocken.data.Uebernahme;
import com.dan.brocken.data.Verbindung;
import com.dan.brocken.data.Zustand;
import com.dan.brocken.gl.Renderer;
import com.dan.brocken.scene.Fahrplan;
import com.dan.brocken.scene.Regie;
import com.dan.brocken.scene.SceneClock;
import com.dan.brocken.ui.Weiter;
import com.dan.store.Ablage;
import com.dan.store.Db;
import com.dan.store.Dienst;
import com.dan.store.Einrichter;
import com.dan.store.Felder;
import com.dan.store.Protokoll;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 17: die Prüfung gegen die echte Datenbank (PDBORCL, Schema BROCKEN aus {@code db/db.properties}). In NetBeans:
 * Rechtsklick, „Run File“. Sie richtet ein (wiederholbar), läuft den Einrichter ein zweites Mal (muss ohne Fehler
 * durchgehen), prüft mit ihm, rechnet Gleis und Ausschnitt auf dem Ellipsoid nach, prüft alle Geometrien auf
 * Gültigkeit, liest die Szenendaten aus der Datenbank (F26), lässt die Chronik einen Nebelmeer-Vormittag mit der
 * Bergfahrt 10:06 in die Datenbank schreiben (F29) und liest ihn über die Sichten zurück, legt einen Merkpunkt mit
 * Vorschau an (F35), speichert Regiebuch und Preset unter „~Prüfung …“, sichert eine Sitzung und stellt sie mit
 * „Weiter wie zuletzt“ her (F37, das DoD), übernimmt Dateien (F25) und räumt hinterher auf. Ende: „Alles in Ordnung.“
 * <p>
 * Die Ausgabe bitte als Text oder Bildschirmfoto schicken; jede Zeile mit FEHLER sagt, was nicht ging.
 */
public final class DbPruefung {
    private static int ok, bad;
    private static final String P = "~Prüfung ";

    static void check(boolean c, String was) {
        if (c) ok++; else bad++;
        System.out.println((c ? "  ok     " : "  FEHLER ") + was);
    }

    static double zahl(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(sql)) { return r.next() ? r.getDouble(1) : Double.NaN; }
    }

    static String text(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(sql)) { return r.next() ? r.getString(1) : null; }
    }

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        System.out.println("Brocken · Prüfung der Datenbank (Phase 17)");
        Db.Konfig k = Verbindung.konfig();
        System.out.println("Verbindung " + k.kurz());
        if (!k.hatPasswort()) {
            System.out.println("  FEHLER Kein Passwort in db/db.properties und keine Umgebungsvariable " + Verbindung.ENV
                    + ". Einmal mit dem Startbild verbinden (es trägt das Passwort ein) oder es von Hand eintragen.");
            System.exit(1);
        }
        DbSpeicher db = new DbSpeicher(k);
        System.out.println(db.wo());
        Connection c = db.verbindung();
        Einrichter ein = Verbindung.einrichter();

        System.out.println("Einrichter");
        Einrichter.Ergebnis e1 = ein.einrichten(c, Verbindung.skripte(), (t, a) -> { });
        for (String z : e1.zeilen()) if (!z.startsWith("ok")) System.out.println("    " + z);
        check(e1.ok(), "Erster Lauf: " + e1.kurz() + " (" + e1.ausgefuehrt() + " ausgeführt, " + e1.vorhanden() + " schon da)");
        Einrichter.Ergebnis e2 = ein.einrichten(c, Verbindung.skripte(), (t, a) -> { });
        check(e2.ok() && e2.fehler() == 0, "Zweiter Lauf geht ohne Fehler durch (wiederholbar): " + e2.kurz());
        Einrichter.Ergebnis pr = ein.pruefen(c, Verbindung.skripte());
        for (String z : pr.zeilen()) System.out.println("    " + z);
        check(pr.ok(), "Einrichter prüft: " + pr.kurz());
        check(ein.stufe(c) == Verbindung.VERSION, "Ausbaustufe " + ein.stufe(c));

        System.out.println("Trigger und Spatial");
        try (Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO BR_MERKPUNKT (NAME, X_M, Z_M, HERKUNFT) VALUES ('" + P + "Trigger', 0, 0, 'EIGEN')");
            try (ResultSet r = st.executeQuery("SELECT ID, ANGELEGT FROM BR_MERKPUNKT WHERE NAME = '" + P + "Trigger'")) {
                check(r.next() && r.getLong(1) > 0 && r.getTimestamp(2) != null, "Trigger setzt ID (Sequenz) und ANGELEGT");
            }
            st.executeUpdate("DELETE FROM BR_MERKPUNKT WHERE NAME = '" + P + "Trigger'");
        }
        double gleis = zahl(c, "SELECT MDSYS.SDO_GEOM.SDO_LENGTH(LINIE, 0.05, 'unit=M') FROM BR_STRECKE WHERE ART = 'GLEIS' AND NR = 0"),
                gleisSzene = zahl(c, "SELECT LAENGE_M FROM BR_STRECKE WHERE ART = 'GLEIS' AND NR = 0"),
                flaeche = zahl(c, "SELECT MDSYS.SDO_GEOM.SDO_AREA(FLAECHE, 0.05, 'unit=SQ_KM') FROM BR_AUSSCHNITT WHERE NAME = 'Hauptraster'"),
                flaecheSzene = zahl(c, "SELECT BREITE_M * HOEHE_M / 1e6 FROM BR_AUSSCHNITT WHERE NAME = 'Hauptraster'");
        check(Math.abs(gleis / gleisSzene - 1) < 0.01 && Math.abs(flaeche / flaecheSzene - 1) < 0.01, String.format(Locale.GERMAN,
                "Auf dem Ellipsoid (SDO_LENGTH, SDO_AREA): Gleis Schierke–Brocken %.2f km (Szene %.2f km), Hauptraster %.1f km² (Szene %.1f km²)",
                gleis / 1000, gleisSzene / 1000, flaeche, flaecheSzene));
        double ungueltig = 0;
        for (String m : Verbindung.METADATEN) {
            String[] tc = m.split("\\.");
            double u = zahl(c, "SELECT COUNT(*) FROM " + tc[0] + " WHERE " + tc[1] + " IS NOT NULL AND MDSYS.SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(" + tc[1] + ", 0.05) <> 'TRUE'");
            if (u > 0) System.out.println("    ungültig in " + m + ": " + (int) u + " · "
                    + text(c, "SELECT MDSYS.SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(" + tc[1] + ", 0.05) FROM " + tc[0] + " WHERE " + tc[1] + " IS NOT NULL AND MDSYS.SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(" + tc[1] + ", 0.05) <> 'TRUE' AND ROWNUM = 1"));
            ungueltig += u;
        }
        check(ungueltig == 0, "Alle Geometrien gültig (VALIDATE_GEOMETRY_WITH_CONTEXT) in " + Verbindung.METADATEN.length + " Spalten");
        String ebenen = text(c, "SELECT LISTAGG(EBENE, ',') WITHIN GROUP (ORDER BY EBENE) FROM (SELECT DISTINCT EBENE FROM BR_BROCKEN_V)");
        double bv = zahl(c, "SELECT COUNT(*) FROM BR_BROCKEN_V");
        check(ebenen != null && ebenen.contains("BAU") && ebenen.contains("GLEIS") && ebenen.contains("BAHNHOF") && bv > 30,
                String.format(Locale.GERMAN, "BR_BROCKEN_V für QGIS: %.0f Objekte in den Ebenen %s", bv, ebenen));
        double imKern = zahl(c, "SELECT COUNT(*) FROM BR_BAU b, BR_AUSSCHNITT a WHERE a.NAME = 'Kern' AND MDSYS.SDO_ANYINTERACT(b.GRUNDRISS, a.FLAECHE) = 'TRUE'"),
                bauten = zahl(c, "SELECT COUNT(*) FROM BR_BAU");
        check(imKern == bauten && bauten > 0, String.format(Locale.GERMAN, "Räumlicher Operator (SDO_ANYINTERACT): alle %.0f Bauten liegen im Kern", bauten));
        String befund = text(c, "SELECT QUELLEN FROM BR_BEFUND_V WHERE QUELLEN IS NOT NULL AND ROWNUM = 1");
        check(befund != null && !befund.isBlank(), "BR_BEFUND_V mit Quellen: " + befund);

        System.out.println("Szenendaten aus der Datenbank (F26)");
        Szenendaten sd = db.szenendaten();
        List<String> sdb = sd.anwenden("Datenbank");
        int fahrten = Fahrplan.fahrten().size();
        check(sdb.isEmpty() && fahrten == Fahrplan.ausDemCode().size() && sd.regiebuecher.size() >= Regie.DATEIEN.length,
                "Fahrplan (" + fahrten + " Fahrten), Klima, Lagen, Übergänge und " + sd.regiebuecher.size() + " Regiebücher aus der Datenbank, gleich dem Code"
                        + (sdb.isEmpty() ? "" : " – " + sdb));

        System.out.println("Sitzung und Chronik (F29)");
        Datenseite.setzen(db);
        SceneClock clock = new SceneClock();
        Renderer r = new Renderer(clock);
        Dienst.starten(Renderer.VERSION, "DbPrüfung", r.quality().name(),
                () -> new Ablage.Sicherung(Felder.alsText(Zustand.erfassen(r), "DbPrüfung"), Zustand.spalten(Zustand.erfassen(r))));
        long sid = Dienst.sitzung();
        Chronik ch = new Chronik(r, false);
        Datenseite.chronik = ch;
        r.wetter("UeberdemNebelmeer", 0);
        r.camera.mode = com.dan.brocken.camera.Camera.Mode.FREI;
        clock.lapse = 600;
        for (int i = 0; i < 90; i++) { r.step(0.5); r.bildlos(); ch.jetzt(); }
        clock.lapse = 1;
        clock.set(clock.date(), 10.0);
        r.step(0.1);
        ch.jetzt();
        clock.lapse = 30;
        for (int i = 0; i < 160; i++) { r.step(0.5); r.bildlos(); if (i % 4 == 0) ch.jetzt(); }
        ch.jetzt();
        ch.warten();
        double stunden = zahl(c, "SELECT COUNT(*) FROM BR_STUNDE WHERE SITZUNG_ID = " + sid),
                meer = zahl(c, "SELECT COUNT(*) FROM BR_NEBELMEER WHERE SITZUNG_ID = " + sid),
                zug = zahl(c, "SELECT COUNT(*) FROM BR_ZUGFAHRT WHERE SITZUNG_ID = " + sid + " AND ZUG LIKE '%10:06%'");
        check(ch.fehler == 0 && stunden >= 6 && meer >= 1 && zug >= 1, String.format(Locale.GERMAN, "%s · in der Datenbank %.0f Stunden, %.0f Nebelmeer, %.0f Bergfahrt 10:06",
                ch.zeile(), stunden, meer, zug));
        String tzv = text(c, "SELECT TO_CHAR(SZENENZEIT, 'DD.MM.YYYY HH24:MI TZD') FROM BR_STUNDE WHERE SITZUNG_ID = " + sid + " ORDER BY SZENE_MS FETCH FIRST 1 ROWS ONLY"),
                tzt = text(c, "SELECT SZENE_TEXT FROM BR_STUNDE WHERE SITZUNG_ID = " + sid + " ORDER BY SZENE_MS FETCH FIRST 1 ROWS ONLY");
        check(tzv != null && tzt != null && tzt.startsWith(tzv.substring(0, 16)), "SZENENZEIT als TIMESTAMP WITH TIME ZONE (Europe/Berlin): " + tzv + " = „" + tzt + "“");
        String tag = text(c, "SELECT SZENE_TAG FROM BR_NEBELMEER WHERE SITZUNG_ID = " + sid + " AND ROWNUM = 1");
        double karte = zahl(c, "SELECT COUNT(*) FROM BR_TAGESKARTE_V WHERE TAG = '" + tag + "' AND (OBJEKT_ID - 11000000000 IN (SELECT ID FROM BR_NEBELMEER WHERE SITZUNG_ID = " + sid
                + ") OR OBJEKT_ID - 12000000000 IN (SELECT ID FROM BR_ZUGFAHRT WHERE SITZUNG_ID = " + sid + "))");
        double fv = zahl(c, "SELECT FAHRTEN FROM BR_FAHRPLAN_V WHERE ZUG LIKE '%10:06%' AND ROWNUM = 1");
        check(karte >= 2 && fv >= 1, String.format(Locale.GERMAN, "BR_TAGESKARTE_V am %s: %.0f Objekte der Prüfsitzung (Nebelmeer als Höhenlinie, Zugfahrt auf dem Gleis); BR_FAHRPLAN_V kennt %.0f Fahrten der 10:06",
                tag, karte, fv));

        System.out.println("Merkpunkt, Regiebuch, Preset (F34, F35)");
        byte[] png = new byte[7000];
        for (int i = 0; i < png.length; i++) png[i] = (byte) (i * 13);
        long mp = db.schreiben("BR_MERKPUNKT", Daten.szene(new Daten.Zeile(), clock.epochMillis()).mit("NAME", P + "Gipfelstein").mit("NOTIZ", "aus der Prüfung")
                .mit("X_M", 2.0).mit("Z_M", -5.0).mit("LAGE", Daten.Geom.punkt(2, -5)).mit("VORSCHAU", png).mit("SITZUNG_ID", sid).mit("HERKUNFT", Daten.EIGEN));
        List<Daten.Zeile> ml = db.lesen("BR_MERKPUNKT", Map.of("ID", mp), 1);
        double[] lp = ml.isEmpty() || ml.get(0).geom("LAGE") == null ? new double[]{9e9, 9e9} : ml.get(0).geom("LAGE").xz()[0];
        double lat = zahl(c, "SELECT m.LAGE.SDO_POINT.Y FROM BR_MERKPUNKT m WHERE ID = " + mp);
        check(Arrays.equals(db.bytes("BR_MERKPUNKT", mp, "VORSCHAU"), png) && Math.abs(lp[0] - 2) < 0.05 && Math.abs(lp[1] + 5) < 0.05 && Math.abs(lat - Daten.lonLat(2, -5)[1]) < 1e-7,
                "Merkpunkt mit Vorschau (BLOB " + png.length + " Bytes), Lage als Punkt in 8307 und zurück in Szenenmetern");
        String rt = Regie.text(Regie.DATEIEN[0]).replaceFirst("(?m)^Regiebuch: .*$", "Regiebuch: " + P + "Zug");
        db.regiebuch(P + "Zug", rt, "aus der Prüfung");
        double sw = zahl(c, "SELECT COUNT(*) FROM BR_STICHWORT x JOIN BR_REGIEBUCH r ON r.ID = x.REGIEBUCH_ID WHERE r.NAME = '" + P + "Zug'"),
                swSoll = Regie.parse(rt).zeilen().size();
        boolean abgelehnt = false;
        String mitgel = Regie.parse(Regie.text(Regie.DATEIEN[0])).name();
        try { db.regiebuch(mitgel, Regie.text(Regie.DATEIEN[0]), ""); } catch (Ablage.Fehler e) { abgelehnt = true; }
        check(sw == swSoll && abgelehnt, String.format(Locale.GERMAN, "Eigenes Regiebuch mit %.0f Stichworten; das mitgelieferte „%s“ wird nicht überschrieben", sw, mitgel));
        db.preset("com.dan.fog", "TerrainFog", P + "Nebel", "aus der Prüfung", Felder.lesen(r.nebel));
        Map<String, Map<String, String>> ps = db.presets("TerrainFog");
        check(ps.containsKey(P + "Nebel") && ps.get(P + "Nebel").equals(Felder.lesen(r.nebel)) && ps.size() > 1,
                "Eigenes Preset gespeichert und gelesen (" + ps.size() + " Presets für TerrainFog)");

        System.out.println("Weiter wie zuletzt (F37, das DoD)");
        clock.set(LocalDate.of(2027, 1, 15), 13.5);
        clock.lapse = 120;
        r.wiring.hand(com.dan.weather.Weather.Var.WIND, 17);
        r.camera.place(-500, 1300, 800, 123, 7);
        r.camera.snap();
        r.showNebel = false;
        r.step(0.1);
        Dienst.sichern(false);
        double ms0 = clock.epochMillis();
        double[] e0 = r.camera.eye.clone();
        clock.set(LocalDate.of(2026, 6, 1), 8);
        clock.lapse = 1;
        r.wiring.resumeRegie();
        r.camera.place(100, 900, 100, 0, 0);
        r.camera.snap();
        r.showNebel = true;
        Daten.Sitzung ls = db.letzteSitzung(-1);
        String lsv = text(c, "SELECT ID || ' · ' || SZENE_TEXT FROM BR_LETZTE_SITZUNG_V");
        List<String> nicht = ls == null ? List.of("keine Sitzung") : Weiter.jetzt(r, ls);
        r.step(0.05);
        check(ls != null && ls.id() == sid && nicht.isEmpty() && Math.abs(clock.epochMillis() - ms0) < 60_000 && clock.lapse == 120 && !r.showNebel
                        && Math.hypot(r.camera.eye[0] - e0[0], r.camera.eye[2] - e0[2]) < 2 && r.wiring.weather.overridden(com.dan.weather.Weather.Var.WIND),
                "„Weiter wie zuletzt“ aus der Datenbank stellt die Sitzung her (BR_LETZTE_SITZUNG_V: " + lsv + ")" + (nicht.isEmpty() ? "" : " – nicht: " + nicht));
        Protokoll.info("DbPrüfung", "eine Zeile aus der Prüfung");
        Dienst.protokollSchreiben();
        ch.beenden();
        Dienst.beenden();
        c = db.verbindung();   // Dienst.beenden schließt die Ablage (wie beim Ende der App); DbSpeicher öffnet neu
        double logz = zahl(c, "SELECT COUNT(*) FROM BR_LOG WHERE SITZUNG_ID = " + sid);
        check(logz >= 1 && zahl(c, "SELECT SAUBER_BEENDET FROM BR_SITZUNG WHERE ID = " + sid) == 1, "Protokoll in BR_LOG (" + (int) logz + " Zeilen), Sitzung sauber beendet");

        System.out.println("Übernahme aus Dateien (F25)");
        File dir = Files.createTempDirectory("brocken-dbpruefung").toFile();
        DateiSpeicher d = new DateiSpeicher(dir);
        d.schreiben("BR_MERKPUNKT", Daten.szene(new Daten.Zeile(), clock.epochMillis()).mit("NAME", P + "aus Datei").mit("X_M", -40.0).mit("Z_M", 70.0)
                .mit("LAGE", Daten.Geom.punkt(-40, 70)));
        d.preset("com.dan.fog", "TerrainFog", P + "aus Datei", "", Felder.lesen(r.nebel));
        String ue = Uebernahme.uebernehmen(d, db);
        double mD = zahl(c, "SELECT COUNT(*) FROM BR_MERKPUNKT WHERE NAME = '" + P + "aus Datei' AND LAGE IS NOT NULL"),
                pD = zahl(c, "SELECT COUNT(*) FROM BR_PAKET_PRESET WHERE NAME = '" + P + "aus Datei'");
        check(mD == 1 && pD == 1 && !Uebernahme.etwasDa(d), "Übernommen: " + ue + "; die Dateien heißen *.uebernommen");
        Daten.Stand st = db.stand();
        check(st.n("BR_BAU") == Verbindung.zahlen(Verbindung.skripte()).get("Bauten") && st.n("BR_FAHRPLAN") >= 14 && st.n("BR_MERKPUNKT") >= 2, "Stand für das Startbild: " + st.zahlen());

        System.out.println("Aufräumen");
        int weg = 0;
        // alle Sitzungen der Prüfung, auch die eines abgebrochenen Laufs (sonst fände „Weiter wie zuletzt“ sie)
        String pruefSitzungen = "(SELECT ID FROM BR_SITZUNG WHERE GPU = 'DbPrüfung')";
        try (Statement x = c.createStatement()) {
            for (String t : new String[]{"BR_STUNDE", "BR_TAG", "BR_NEBELMEER", "BR_GESPENST", "BR_ZUGFAHRT", "BR_WINDFELD", "BR_REGIELAUF", "BR_MERKPUNKT", "BR_ANSICHT", "BR_LOG"})
                weg += x.executeUpdate("DELETE FROM " + t + " WHERE SITZUNG_ID IN " + pruefSitzungen);
            weg += x.executeUpdate("DELETE FROM BR_MERKPUNKT WHERE NAME LIKE '~%' AND HERKUNFT = 'EIGEN'");
            weg += x.executeUpdate("DELETE FROM BR_PAKET_PRESET WHERE NAME LIKE '~%' AND HERKUNFT = 'EIGEN'");   // Werte: ON DELETE CASCADE
            weg += x.executeUpdate("DELETE FROM BR_REGIEBUCH WHERE NAME LIKE '~%' AND HERKUNFT = 'EIGEN'");     // Stichworte: ON DELETE CASCADE
            weg += x.executeUpdate("DELETE FROM BR_SITZUNG WHERE GPU = 'DbPrüfung'");
        }
        double rest = zahl(c, "SELECT (SELECT COUNT(*) FROM BR_REGIEBUCH WHERE NAME LIKE '~%') + (SELECT COUNT(*) FROM BR_MERKPUNKT WHERE NAME LIKE '~%') "
                + "+ (SELECT COUNT(*) FROM BR_PAKET_PRESET WHERE NAME LIKE '~%') + (SELECT COUNT(*) FROM BR_STUNDE WHERE SITZUNG_ID = " + sid + ") "
                + "+ (SELECT COUNT(*) FROM BR_SITZUNG WHERE GPU = 'DbPrüfung') FROM DUAL");
        check(rest == 0, "Aufgeräumt: " + weg + " Zeilen der Prüfsitzungen, nichts mit „~“ und keine Sitzung der Prüfung bleibt");
        Fahrplan.geladen = null;
        Regie.geladen = null;
        r.physik.stop();
        db.schliessen();

        System.out.printf(Locale.GERMAN, "%d geprüft, %d Fehler · %.1f s%n", ok + bad, bad, (System.nanoTime() - t0) / 1e9);
        System.out.println(bad == 0 ? "Alles in Ordnung." : "Nicht alles in Ordnung.");
        System.exit(bad == 0 ? 0 : 1);
    }
}
