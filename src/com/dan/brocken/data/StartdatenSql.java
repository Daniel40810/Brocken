package com.dan.brocken.data;

import com.dan.brocken.gl.Renderer;
import com.dan.store.Zahl;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schreibt die mitgelieferten Daten als SQL: {@code db/sql/04_startdaten.sql} (erzeugt von {@code tools.StartdatenSql},
 * nicht von Hand ändern). MERGE je Zeile über ihre Schlüssel; Geometrien und lange Texte kommen danach mit UPDATE, große
 * Geometrien (über 4 000 Zeichen WKT) in einem PL/SQL-Block über {@code DBMS_LOB.APPEND}. Kinder (Belege, Stichworte,
 * Stützpunkte, Presetwerte) werden gelöscht und neu angelegt. Eigene Zeilen (HERKUNFT = 'EIGEN') bleiben unberührt.
 * Beliebig oft wiederholbar.
 */
public final class StartdatenSql {
    private StartdatenSql() { }

    static final String M = "'MITGELIEFERT'";

    static String s(String v) {
        if (v == null) return "NULL";
        return "'" + v.replace("'", "''").replace("\r", "").replace("\n", "' || CHR(10) || '") + "'";
    }

    /** Ein Text in Stücken zu höchstens 1 000 Zeichen (ohne Zeilenumbrüche im Literal). */
    static java.util.List<String> stuecke(String t) {
        java.util.List<String> l = new java.util.ArrayList<>();
        for (int i = 0; i < t.length(); ) {
            int e = Math.min(t.length(), i + 1000);
            if (e < t.length() && Character.isHighSurrogate(t.charAt(e - 1))) e--;
            l.add(t.substring(i, e));
            i = e;
        }
        return l;
    }

    /** Ein langer Text als CLOB in Stücken. */
    static String clob(String v) {
        if (v == null) return "NULL";
        StringBuilder b = new StringBuilder();
        List<String> st = stuecke(v.replace("\r", ""));
        for (int i = 0; i < st.size(); i++) b.append(i > 0 ? "\n    || " : "").append("TO_CLOB(").append(s(st.get(i))).append(")");
        return b.length() == 0 ? "TO_CLOB('')" : b.toString();
    }

    /** Eine Zahl mit allen Stellen, die sie hat (höchstens acht nach dem Komma). */
    static String n(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "NULL";
        return java.math.BigDecimal.valueOf(Math.round(v * 1e8) / 1e8).stripTrailingZeros().toPlainString();
    }

    static String punkt(double x, double z) {
        double[] ll = Daten.lonLat(x, z);
        return "MDSYS.SDO_GEOMETRY(2001, 8307, MDSYS.SDO_POINT_TYPE(" + n(ll[0]) + ", " + n(ll[1]) + ", NULL), NULL, NULL)";
    }

    /** Die Geometrie als SQL-Ausdruck (null, wenn sie in einen PL/SQL-Block muss). */
    static String geom(Daten.Geom g) {
        if (g == null) return "NULL";
        if (g.art().equals("PUNKT")) return punkt(g.xz()[0][0], g.xz()[0][1]);
        String w = g.wkt();
        if (w.length() <= 3900) return "MDSYS.SDO_GEOMETRY(" + s(w) + ", 8307)";
        return null;
    }

    static String quelle(String kurz) { return kurz == null ? "NULL" : "(SELECT MAX(ID) FROM BR_QUELLE WHERE KURZ = " + s(kurz) + ")"; }

    /** Ein Wert als SQL nach dem Typ der Spalte. */
    static String wert(String typ, Object v) {
        if (v == null) return "NULL";
        if (v instanceof Daten.Geom g) return geom(g);
        if (v instanceof Boolean b) return b ? "1" : "0";
        if (v instanceof Number x) return n(x.doubleValue());
        String t = v.toString();
        if (typ != null && typ.startsWith("DATE")) return "DATE '" + t + "'";
        if (typ != null && typ.startsWith("CLOB")) return clob(t);
        return s(t);
    }

    private static void kopf(StringBuilder b, String t) {
        b.append("\n-- ").append("-".repeat(Math.max(4, 100 - t.length()))).append(' ').append(t).append('\n');
    }

    private static String bedingung(Startdaten.Tabelle t, Daten.Zeile z, Map<String, String> typ) {
        StringBuilder w = new StringBuilder();
        for (String k : t.schluessel()) w.append(w.length() > 0 ? " AND " : "").append(k).append(" = ").append(wert(typ.get(k), z.get(k)));
        return w.toString();
    }

    /** MERGE der einfachen Spalten, danach UPDATE der Geometrien und langen Texte. */
    static void zeile(StringBuilder b, Startdaten.Tabelle t, Daten.Zeile z, Map<String, String> typ) {
        Map<String, String> einfach = new LinkedHashMap<>(), spaeter = new LinkedHashMap<>();
        Map<String, Daten.Geom> gross = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : z.entrySet()) {
            String k = e.getKey();
            if (k.equals("STUETZPUNKTE")) continue;
            if (k.equals("QUELLE")) { einfach.put("QUELLE_ID", quelle((String) e.getValue())); continue; }
            String ty = typ.get(k);
            if (ty == null) throw new IllegalStateException("Spalte " + t.name() + "." + k + " gibt es in 01_tabellen.sql nicht");
            Object v = e.getValue();
            if (v instanceof Daten.Geom g && !g.art().equals("PUNKT")) {
                String x = geom(g);
                if (x == null) gross.put(k, g); else spaeter.put(k, x);
            } else if (ty.startsWith("CLOB") && v != null) spaeter.put(k, wert(ty, v));
            else einfach.put(k, wert(ty, v));
        }
        StringBuilder using = new StringBuilder(), on = new StringBuilder(), upd = new StringBuilder(), cols = new StringBuilder(), vals = new StringBuilder();
        for (String k : t.schluessel()) {
            String v = einfach.get(k);
            using.append(using.length() > 0 ? ", " : "").append(v).append(" AS ").append(k);
            on.append(on.length() > 0 ? " AND " : "").append("t.").append(k).append(" = q.").append(k);
        }
        for (Map.Entry<String, String> e : einfach.entrySet()) {
            boolean key = java.util.Arrays.asList(t.schluessel()).contains(e.getKey());
            if (!key) upd.append(upd.length() > 0 ? ", " : "").append("t.").append(e.getKey()).append(" = ").append(e.getValue());
            cols.append(cols.length() > 0 ? ", " : "").append(e.getKey());
            vals.append(vals.length() > 0 ? ", " : "").append(e.getValue());
        }
        // Spalten CLOB NOT NULL brauchen beim Anlegen einen Wert
        for (String k : spaeter.keySet())
            if (typ.get(k).startsWith("CLOB") && typ.get(k).contains("NOT NULL")) { cols.append(", ").append(k); vals.append(", TO_CLOB('-')"); }
        b.append("MERGE INTO ").append(t.name()).append(" t USING (SELECT ").append(using).append(" FROM DUAL) q ON (").append(on).append(")\n");
        if (upd.length() > 0) b.append("WHEN MATCHED THEN UPDATE SET ").append(upd).append(" WHERE t.HERKUNFT = ").append(M).append('\n');
        b.append("WHEN NOT MATCHED THEN INSERT (").append(cols).append(", HERKUNFT)\n  VALUES (").append(vals).append(", ").append(M).append(");\n");
        String wo = " WHERE " + bedingung(t, z, typ) + " AND HERKUNFT = " + M;
        if (!spaeter.isEmpty()) {
            StringBuilder u = new StringBuilder();
            for (Map.Entry<String, String> e : spaeter.entrySet()) u.append(u.length() > 0 ? ",\n  " : "").append(e.getKey()).append(" = ").append(e.getValue());
            b.append("UPDATE ").append(t.name()).append(" SET ").append(u).append('\n').append(" ").append(wo.strip()).append(";\n");
        }
        for (Map.Entry<String, Daten.Geom> e : gross.entrySet()) {
            b.append("DECLARE\n  w CLOB;\nBEGIN\n  DBMS_LOB.CREATETEMPORARY(w, TRUE);\n");
            for (String st : stuecke(e.getValue().wkt())) b.append("  DBMS_LOB.APPEND(w, ").append(s(st)).append(");\n");
            b.append("  UPDATE ").append(t.name()).append(" SET ").append(e.getKey()).append(" = MDSYS.SDO_GEOMETRY(w, 8307)").append(wo).append(";\n");
            b.append("  DBMS_LOB.FREETEMPORARY(w);\nEND;\n/\n");
        }
    }

    /** Die Zahlen der mitgelieferten Zeilen je Art (Zeile „-- ZAHLEN:“, der Einrichter prüft sie). */
    public static Map<String, Integer> zahlen(List<Startdaten.Tabelle> alle) {
        Map<String, Integer> n = new LinkedHashMap<>();
        Map<String, Integer> je = new LinkedHashMap<>();
        for (Startdaten.Tabelle t : alle) je.put(t.name(), t.zeilen().size());
        String[][] k = {{"Quellen", "BR_QUELLE"}, {"Befunde", "BR_BEFUND"}, {"Gelände", "BR_GELAENDE"}, {"Ausschnitte", "BR_AUSSCHNITT"},
                {"Höhenlinien", "BR_HOEHENLINIE"}, {"Felsen", "BR_FELS"}, {"Böden", "BR_BODEN"}, {"Lagen", "BR_LAGE"}, {"Übergänge", "BR_LAGE_UEBERGANG"},
                {"Klimamonate", "BR_KLIMA_MONAT"}, {"Höhenstufen", "BR_HOEHENSTUFE"}, {"Strecke", "BR_STRECKE"}, {"Bahnhöfe", "BR_BAHNHOF"},
                {"Bahnübergänge", "BR_BAHNUEBERGANG"}, {"Fahrzeuge", "BR_FAHRZEUG"}, {"Fahrplan", "BR_FAHRPLAN"}, {"Bauten", "BR_BAU"}, {"Orte", "BR_ORT"},
                {"Wege", "BR_WEG"}, {"Wetterbücher", "BR_WETTERBUCH"}, {"Regiebücher", "BR_REGIEBUCH"}, {"Ansichten", "BR_ANSICHT"}, {"Fahrten", "BR_FAHRT"}};
        for (String[] x : k) n.put(x[0], je.getOrDefault(x[1], 0));
        int belege = 0;
        for (Startdaten.Befund f : Startdaten.befunde()) belege += f.quellen().size();
        n.put("Belege", belege);
        int sw = 0, sp = 0, pw = 0;
        for (Startdaten.Tabelle t : alle) {
            if (t.name().equals("BR_REGIEBUCH")) for (Daten.Zeile z : t.zeilen()) sw += Startdaten.stichworte(z.text("TEXT")).size();
            if (t.name().equals("BR_FAHRT")) for (Daten.Zeile z : t.zeilen()) sp += ((double[][]) z.get("STUETZPUNKTE")).length;
        }
        n.put("Stichworte", sw);
        n.put("Stützpunkte", sp);
        n.put("Presets", Startdaten.presets().size());
        for (Startdaten.Preset p : Startdaten.presets()) pw += p.felder().size();
        n.put("Presetwerte", pw);
        return n;
    }

    /** Das ganze Skript. */
    public static String sql(Renderer r, File gelaendeDir) {
        Map<String, Map<String, String>> spalten = DateiSpeicher.spalten();
        if (spalten.isEmpty()) throw new IllegalStateException("db/sql/01_tabellen.sql fehlt; die Startdaten brauchen die Spalten");
        List<Startdaten.Tabelle> alle = Startdaten.alles(r, gelaendeDir);
        StringBuilder b = new StringBuilder();
        b.append("""
                -- =====================================================================================================
                -- Brocken · Datenbank · 04_startdaten.sql
                -- ERZEUGT aus com.dan.brocken.data.Startdaten (tools.StartdatenSql) – nicht von Hand ändern.
                -- Mitgelieferte Zeilen (HERKUNFT = 'MITGELIEFERT') werden angelegt oder wiederhergestellt; eigene Zeilen
                -- bleiben unberührt. Beliebig oft wiederholbar. Werkbuch Abschnitt 9, F25–F37 „alle A“ (26.10.2026):
                -- Quellen und Befunde, Gelände mit Prüfsummen und Höhenlinien (F27), Felsen und Böden, Lagen, Übergänge,
                -- Klima und Höhenstufen (F36), Strecke, Bahnhöfe, Fahrzeuge und Fahrplan (F31), Bauten, Orte und Wege (F32),
                -- Wetter- und Regiebücher, Ansichten und Fahrten (F33), Presets der Packages (F34). Lage in 8307 (F28).
                -- =====================================================================================================
                """);
        StringBuilder zl = new StringBuilder("-- ZAHLEN:");
        for (Map.Entry<String, Integer> e : zahlen(alle).entrySet()) zl.append(' ').append(e.getKey()).append('=').append(e.getValue()).append(';');
        b.append(zl).append("\n\n-- Die Quellen enthalten „&“: in SQL Developer keine Ersetzungsvariablen\nSET DEFINE OFF\n");

        kopf(b, "Ausbaustufe");
        b.append("MERGE INTO BR_SCHEMA t USING (SELECT 1 AS VERSION FROM DUAL) q ON (t.VERSION = q.VERSION)\n")
                .append("WHEN NOT MATCHED THEN INSERT (VERSION, APP_VERSION, TEXT) VALUES (1, 'Phase 17', ")
                .append(s("Datenbank: 39 Tabellen, Gelände, Boden, Klima, Bahn und Gipfel als Karte, Chronik von Wetter, Nebelmeer, Gespenstern und Zügen"))
                .append(");\n");

        for (Startdaten.Tabelle t : alle) {
            Map<String, String> typ = spalten.get(t.name());
            if (typ == null) throw new IllegalStateException("Tabelle " + t.name() + " gibt es in 01_tabellen.sql nicht");
            kopf(b, t.name() + " (" + t.zeilen().size() + ")");
            for (Daten.Zeile z : t.zeilen()) zeile(b, t, z, typ);
            switch (t.name()) {
                case "BR_BEFUND" -> {
                    b.append("DELETE FROM BR_BEFUND_QUELLE WHERE BEFUND_ID IN (SELECT ID FROM BR_BEFUND WHERE HERKUNFT = ").append(M).append(");\n");
                    for (Startdaten.Befund f : Startdaten.befunde())
                        for (String q : f.quellen())
                            b.append("INSERT INTO BR_BEFUND_QUELLE (BEFUND_ID, QUELLE_ID) SELECT b.ID, q.ID FROM BR_BEFUND b, BR_QUELLE q WHERE b.THEMA = ")
                                    .append(s(f.zeile().text("THEMA"))).append(" AND b.GEGENSTAND = ").append(s(f.zeile().text("GEGENSTAND"))).append(" AND b.HERKUNFT = ")
                                    .append(M).append(" AND q.KURZ = ").append(s(q)).append(";\n");
                }
                case "BR_REGIEBUCH" -> {
                    for (Daten.Zeile z : t.zeilen()) {
                        String eltern = "FROM BR_REGIEBUCH WHERE NAME = " + s(z.text("NAME")) + " AND HERKUNFT = " + M;
                        b.append("DELETE FROM BR_STICHWORT WHERE REGIEBUCH_ID IN (SELECT ID ").append(eltern).append(");\n");
                        for (Daten.Zeile x : Startdaten.stichworte(z.text("TEXT")))
                            b.append("INSERT INTO BR_STICHWORT (REGIEBUCH_ID, NR, SEKUNDE, BEFEHL, ARGUMENTE, TEXT) SELECT ID, ").append(n(x.zahl("NR"))).append(", ")
                                    .append(n(x.zahl("SEKUNDE"))).append(", ").append(s(x.text("BEFEHL"))).append(", ").append(s(x.text("ARGUMENTE"))).append(", ")
                                    .append(s(x.text("TEXT"))).append(' ').append(eltern).append(";\n");
                    }
                }
                case "BR_FAHRT" -> {
                    for (Daten.Zeile z : t.zeilen()) {
                        String eltern = "FROM BR_FAHRT WHERE NAME = " + s(z.text("NAME")) + " AND HERKUNFT = " + M;
                        b.append("DELETE FROM BR_STUETZPUNKT WHERE FAHRT_ID IN (SELECT ID ").append(eltern).append(");\n");
                        double[][] sp = (double[][]) z.get("STUETZPUNKTE");
                        for (int i = 0; i < sp.length; i++)
                            b.append("INSERT INTO BR_STUETZPUNKT (FAHRT_ID, NR, ZEIT_S, KAM_X, KAM_Y, KAM_Z, KAM_GIER_GRAD, KAM_NICK_GRAD) SELECT ID, ").append(i + 1)
                                    .append(", ").append(n(Daten.r(sp[i][0], 2))).append(", ").append(n(Daten.r(sp[i][1], 2))).append(", ").append(n(Daten.r(sp[i][2], 2)))
                                    .append(", ").append(n(Daten.r(sp[i][3], 2))).append(", ").append(n(Daten.r(sp[i][4], 2))).append(", ").append(n(Daten.r(sp[i][5], 2)))
                                    .append(' ').append(eltern).append(";\n");
                    }
                }
                default -> { }
            }
        }

        kopf(b, "Presets der Packages (" + Startdaten.presets().size() + ")");
        for (Startdaten.Preset p : Startdaten.presets()) {
            b.append("MERGE INTO BR_PAKET_PRESET t USING (SELECT ").append(s(p.paket())).append(" AS PAKET, ").append(s(p.klasse())).append(" AS KLASSE, ")
                    .append(s(p.name())).append(" AS NAME FROM DUAL) q ON (t.PAKET = q.PAKET AND t.KLASSE = q.KLASSE AND t.NAME = q.NAME)\n")
                    .append("WHEN MATCHED THEN UPDATE SET t.BESCHREIBUNG = ").append(s(p.beschreibung())).append(" WHERE t.HERKUNFT = ").append(M).append('\n')
                    .append("WHEN NOT MATCHED THEN INSERT (PAKET, KLASSE, NAME, BESCHREIBUNG, HERKUNFT)\n  VALUES (").append(s(p.paket())).append(", ").append(s(p.klasse()))
                    .append(", ").append(s(p.name())).append(", ").append(s(p.beschreibung())).append(", ").append(M).append(");\n");
            String eltern = "FROM BR_PAKET_PRESET WHERE PAKET = " + s(p.paket()) + " AND KLASSE = " + s(p.klasse()) + " AND NAME = " + s(p.name()) + " AND HERKUNFT = " + M;
            b.append("DELETE FROM BR_PAKET_WERT WHERE PAKET_PRESET_ID IN (SELECT ID ").append(eltern).append(");\n");
            Map<String, String> ty = Startdaten.typen(p.paket() + "." + p.klasse());
            for (Map.Entry<String, String> e : p.felder().entrySet())
                b.append("INSERT INTO BR_PAKET_WERT (PAKET_PRESET_ID, FELD, TYP, WERT) SELECT ID, ").append(s(e.getKey())).append(", ").append(s(ty.getOrDefault(e.getKey(), "STRING")))
                        .append(", ").append(s(e.getValue().isEmpty() ? null : e.getValue())).append(' ').append(eltern).append(";\n");
        }
        b.append("\nCOMMIT;\n\n-- Ende 04_startdaten.sql\n");
        return b.toString();
    }

    static String zahl(double v) { return Zahl.text(v); }
}
