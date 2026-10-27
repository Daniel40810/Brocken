package com.dan.brocken.data;

import com.dan.brocken.scene.Fahrplan;
import com.dan.brocken.scene.Regie;
import com.dan.store.Db;
import com.dan.store.Protokoll;
import com.dan.store.Zahl;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Der Speicher im Schema BROCKEN (Phase 17, F25–F37 „alle A“). Zeilen schreibt er allgemein: die Spalten und ihre Typen
 * liest er einmal aus {@code USER_TAB_COLUMNS}, Geometrien gehen als WKT in {@code SDO_GEOMETRY(…, 8307)}, Punkte als
 * {@code SDO_POINT_TYPE}, Bytes in BLOB, lange Texte in CLOB, Szenenzeit als Zahl (ms) in TIMESTAMP-Spalten.
 * Mitgeliefertes fasst er nicht an; löschen lässt sich nur, was HERKUNFT = 'EIGEN' trägt.
 */
public final class DbSpeicher implements Speicher {
    private final Db.Konfig konfig;
    private Connection c;
    private final String wo;
    private final Map<String, Map<String, String>> typen = new HashMap<>();

    public DbSpeicher(Db.Konfig k) throws Fehler {
        this.konfig = k;
        try {
            c = Db.oeffnen(k);
            wo = "Datenbank " + Db.einWert(c, "select user || ' auf ' || sys_context('USERENV','CON_NAME') from dual");
        } catch (SQLException e) {
            throw new Fehler("Keine Verbindung zu " + k.kurz() + ": " + Db.erste(e), e);
        }
    }

    /** Für Prüfungen und den Einrichter: die offene Verbindung. */
    public synchronized Connection verbindung() throws Fehler {
        try {
            if (c == null || c.isClosed() || !c.isValid(3)) c = Db.oeffnen(konfig);
            return c;
        } catch (SQLException e) {
            throw new Fehler("Verbindung verloren: " + Db.erste(e), e);
        }
    }

    @Override public String wo() { return wo; }
    @Override public boolean datenbank() { return true; }

    private interface Arbeit<T> { T tun(Connection c) throws SQLException; }

    private synchronized <T> T mit(String was, Arbeit<T> a) throws Fehler {
        Connection cc = verbindung();
        try {
            return a.tun(cc);
        } catch (SQLException e) {
            throw new Fehler(was + ": " + Db.erste(e), e);
        }
    }

    /** In einer Transaktion: alles oder nichts. */
    private synchronized <T> T zusammen(String was, Arbeit<T> a) throws Fehler {
        Connection cc = verbindung();
        try {
            cc.setAutoCommit(false);
            T t = a.tun(cc);
            cc.commit();
            return t;
        } catch (SQLException e) {
            try { cc.rollback(); } catch (SQLException e2) { /* weiter */ }
            throw new Fehler(was + ": " + Db.erste(e), e);
        } finally {
            try { cc.setAutoCommit(true); } catch (SQLException e) { /* weiter */ }
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private static int zahl(Connection c, String sql, Object... a) throws SQLException {
        String v = Db.einWert(c, sql, a);
        return v == null ? 0 : Integer.parseInt(v);
    }

    /** Spalte → Typ (DATA_TYPE von USER_TAB_COLUMNS, etwa NUMBER, VARCHAR2, CLOB, BLOB, SDO_GEOMETRY, TIMESTAMP(6) WITH TIME ZONE). */
    synchronized Map<String, String> typen(Connection cc, String tabelle) throws SQLException {
        Map<String, String> m = typen.get(tabelle);
        if (m != null) return m;
        m = new LinkedHashMap<>();
        try (PreparedStatement st = cc.prepareStatement("SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, CHAR_LENGTH FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ? ORDER BY COLUMN_ID")) {
            st.setString(1, tabelle);
            try (ResultSet r = st.executeQuery()) {
                while (r.next()) {
                    String t = r.getString(2);
                    if (t.startsWith("VARCHAR2")) t = "VARCHAR2:" + Math.max(r.getInt(4), 1);
                    m.put(r.getString(1), t);
                }
            }
        }
        if (m.isEmpty()) throw new SQLException("Tabelle " + tabelle + " gibt es nicht (Einrichten?)");
        typen.put(tabelle, m);
        return m;
    }

    private static final DateTimeFormatter TZ = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT);

    /** Platzhalter für einen Wert je nach Spaltentyp. */
    private static String platz(String typ, Object v) {
        if (v == null) return "?";
        if (typ.equals("SDO_GEOMETRY")) {
            Daten.Geom g = (Daten.Geom) v;
            return g.art().equals("PUNKT") ? "MDSYS.SDO_GEOMETRY(2001, 8307, MDSYS.SDO_POINT_TYPE(?, ?, NULL), NULL, NULL)" : "MDSYS.SDO_GEOMETRY(?, 8307)";
        }
        if (typ.contains("WITH TIME ZONE")) return "TO_TIMESTAMP_TZ(?, 'YYYY-MM-DD HH24:MI:SS TZR')";
        return "?";
    }

    /** Einen Wert binden; gibt den nächsten Index zurück. */
    private static int binden(PreparedStatement st, int i, String typ, Object v) throws SQLException {
        if (v == null) {
            if (typ.equals("SDO_GEOMETRY")) st.setNull(i, Types.STRUCT, "MDSYS.SDO_GEOMETRY");
            else st.setNull(i, typ.equals("BLOB") ? Types.BLOB : typ.equals("CLOB") ? Types.CLOB : typ.startsWith("VARCHAR2") ? Types.VARCHAR
                    : typ.startsWith("TIMESTAMP") ? Types.TIMESTAMP : typ.equals("DATE") ? Types.DATE : Types.NUMERIC);
            return i + 1;
        }
        if (typ.equals("SDO_GEOMETRY")) {
            Daten.Geom g = (Daten.Geom) v;
            if (g.art().equals("PUNKT")) {
                double[] ll = Daten.lonLat(g.xz()[0][0], g.xz()[0][1]);
                st.setDouble(i, Math.round(ll[0] * 1e8) / 1e8);
                st.setDouble(i + 1, Math.round(ll[1] * 1e8) / 1e8);
                return i + 2;
            }
            String w = g.wkt();
            st.setCharacterStream(i, new StringReader(w), w.length());
            return i + 1;
        }
        if (typ.equals("BLOB")) {
            byte[] b = (byte[]) v;
            st.setBinaryStream(i, new ByteArrayInputStream(b), b.length);
            return i + 1;
        }
        if (typ.equals("CLOB")) {
            String s = v.toString();
            st.setCharacterStream(i, new StringReader(s), s.length());
            return i + 1;
        }
        if (typ.contains("WITH TIME ZONE")) {
            long ms = (long) ((Number) v).doubleValue();
            st.setString(i, TZ.format(Daten.zeit(ms)) + " Europe/Berlin");
            return i + 1;
        }
        if (typ.startsWith("TIMESTAMP")) {
            st.setTimestamp(i, new Timestamp((long) ((Number) v).doubleValue()));
            return i + 1;
        }
        if (typ.equals("DATE")) {
            st.setDate(i, java.sql.Date.valueOf(v.toString()));
            return i + 1;
        }
        if (typ.startsWith("VARCHAR2")) {
            String s = v.toString();
            int max = Integer.parseInt(typ.substring(9));
            if (s.length() > max) s = s.substring(0, Math.max(0, max - 1)) + "…";
            st.setString(i, s);
            return i + 1;
        }
        // NUMBER
        if (v instanceof Boolean b) st.setInt(i, b ? 1 : 0);
        else if (v instanceof Long || v instanceof Integer) st.setLong(i, ((Number) v).longValue());
        else if (v instanceof Number n) st.setDouble(i, n.doubleValue());
        else {
            double d = Zahl.lies(v.toString());
            if (Double.isNaN(d)) st.setNull(i, Types.NUMERIC); else st.setDouble(i, d);
        }
        return i + 1;
    }

    /** INSERT einer Zeile in der offenen Verbindung; gibt die neue ID zurück (0 ohne Sequence). */
    static long einfuegen(Connection cc, Map<String, String> typ, String tabelle, Daten.Zeile z) throws SQLException {
        StringBuilder cols = new StringBuilder(), vals = new StringBuilder();
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Object> e : z.entrySet()) {
            String t = typ.get(e.getKey());
            if (t == null) throw new SQLException("Spalte " + tabelle + "." + e.getKey() + " gibt es nicht");
            if (e.getKey().equals("ID") || e.getValue() == null) continue;   // leer: die Vorgabe der Spalte (NULL oder DEFAULT)
            keys.add(e.getKey());
            cols.append(cols.length() > 0 ? ", " : "").append(e.getKey());
            vals.append(vals.length() > 0 ? ", " : "").append(platz(t, e.getValue()));
        }
        try (PreparedStatement st = cc.prepareStatement("INSERT INTO " + tabelle + " (" + cols + ") VALUES (" + vals + ")")) {
            int i = 1;
            for (String k : keys) i = binden(st, i, typ.get(k), z.get(k));
            st.executeUpdate();
        }
        if (!typ.containsKey("ID") || tabelle.equals("BR_LOG")) return 0;
        String id = Db.einWert(cc, "SELECT TO_CHAR(" + tabelle + "_SEQ.CURRVAL) FROM DUAL");
        return id == null ? 0 : Long.parseLong(id);
    }

    @Override public long schreiben(String tabelle, Daten.Zeile z) throws Fehler {
        return zusammen("In " + tabelle + " schreiben", cc -> einfuegen(cc, typen(cc, tabelle), tabelle, z));
    }

    @Override public List<Daten.Zeile> lesen(String tabelle, Map<String, Object> wo, int hoechstens) throws Fehler {
        return mit("Aus " + tabelle + " lesen", cc -> {
            Map<String, String> typ = typen(cc, tabelle);
            StringBuilder sel = new StringBuilder();
            for (Map.Entry<String, String> e : typ.entrySet()) {
                if (e.getValue().equals("BLOB")) continue;
                sel.append(sel.length() > 0 ? ", " : "");
                if (e.getValue().equals("SDO_GEOMETRY")) sel.append("MDSYS.SDO_UTIL.TO_WKTGEOMETRY(").append(e.getKey()).append(") AS ").append(e.getKey());
                else if (e.getValue().contains("WITH TIME ZONE")) sel.append("TO_CHAR(").append(e.getKey()).append(", 'YYYY-MM-DD HH24:MI:SS TZR') AS ").append(e.getKey());
                else sel.append(e.getKey());
            }
            StringBuilder w = new StringBuilder();
            List<Object> args = new ArrayList<>();
            if (wo != null)
                for (Map.Entry<String, Object> e : wo.entrySet()) {
                    if (!typ.containsKey(e.getKey())) throw new SQLException("Spalte " + tabelle + "." + e.getKey() + " gibt es nicht");
                    w.append(w.length() > 0 ? " AND " : " WHERE ").append(e.getKey()).append(" = ?");
                    args.add(e.getValue());
                }
            String sql = "SELECT " + sel + " FROM " + tabelle + w + (typ.containsKey("ID") ? " ORDER BY ID DESC" : "") + " FETCH FIRST " + Math.max(1, hoechstens) + " ROWS ONLY";
            List<Daten.Zeile> l = new ArrayList<>();
            try (PreparedStatement st = cc.prepareStatement(sql)) {
                for (int i = 0; i < args.size(); i++) st.setObject(i + 1, args.get(i) instanceof Boolean b ? (b ? 1 : 0) : args.get(i));
                try (ResultSet r = st.executeQuery()) {
                    ResultSetMetaData md = r.getMetaData();
                    while (r.next()) {
                        Daten.Zeile z = new Daten.Zeile();
                        for (int k = 1; k <= md.getColumnCount(); k++) {
                            String n = md.getColumnName(k), t = typ.getOrDefault(n, "");
                            Object v;
                            if (t.equals("SDO_GEOMETRY")) { String s = clob(r, k); v = s == null ? null : Daten.Geom.ausWkt(s); }
                            else if (t.equals("CLOB")) v = clob(r, k);
                            else if (t.startsWith("TIMESTAMP") && !t.contains("ZONE")) { Timestamp ts = r.getTimestamp(k); v = ts == null ? null : ts.getTime(); }
                            else if (t.equals("DATE")) { java.sql.Date d = r.getDate(k); v = d == null ? null : d.toLocalDate().toString(); }
                            else if (t.equals("NUMBER")) {
                                java.math.BigDecimal b = r.getBigDecimal(k);
                                v = b == null ? null : b.scale() <= 0 || b.stripTrailingZeros().scale() <= 0 ? (Object) b.longValue() : (Object) b.doubleValue();
                            } else v = r.getString(k);
                            z.put(n, v);
                        }
                        l.add(z);
                    }
                }
            }
            return l;
        });
    }

    private static String clob(ResultSet r, int k) throws SQLException {
        Object o = r.getObject(k);
        if (o == null) return null;
        if (o instanceof Clob cl) return cl.getSubString(1, (int) cl.length());
        return o.toString();
    }

    @Override public byte[] bytes(String tabelle, long id, String spalte) throws Fehler {
        return mit("BLOB lesen", cc -> {
            if (!"BLOB".equals(typen(cc, tabelle).get(spalte))) throw new SQLException(tabelle + "." + spalte + " ist kein BLOB");
            try (PreparedStatement st = cc.prepareStatement("SELECT " + spalte + " FROM " + tabelle + " WHERE ID = ?")) {
                st.setLong(1, id);
                try (ResultSet r = st.executeQuery()) {
                    if (!r.next()) return null;
                    Blob b = r.getBlob(1);
                    return b == null ? null : b.getBytes(1, (int) b.length());
                }
            }
        });
    }

    @Override public void loeschen(String tabelle, long id) throws Fehler {
        zusammen("Löschen", cc -> {
            Map<String, String> typ = typen(cc, tabelle);
            if (!typ.containsKey("HERKUNFT")) throw new SQLException(tabelle + " hat keine eigenen Zeilen");
            if (zahl(cc, "SELECT COUNT(*) FROM " + tabelle + " WHERE ID = ? AND HERKUNFT = 'MITGELIEFERT'", id) > 0)
                throw new SQLException("Zeile " + id + " ist mitgeliefert und wird nicht gelöscht");
            try (PreparedStatement st = cc.prepareStatement("DELETE FROM " + tabelle + " WHERE ID = ? AND HERKUNFT = 'EIGEN'")) { st.setLong(1, id); st.executeUpdate(); }
            return null;
        });
    }

    // ------------------------------------------------------------------ Presets und Regiebücher

    @Override public void preset(String paket, String klasse, String name, String beschreibung, Map<String, String> werte) throws Fehler {
        Map<String, String> ty = Startdaten.typen(paket + "." + klasse);
        zusammen("Preset speichern", cc -> {
            if (zahl(cc, "SELECT COUNT(*) FROM BR_PAKET_PRESET WHERE PAKET = ? AND KLASSE = ? AND NAME = ? AND HERKUNFT = 'MITGELIEFERT'", paket, klasse, name) > 0)
                throw new SQLException("„" + name + "“ ist mitgeliefert; bitte einen eigenen Namen wählen");
            try (PreparedStatement st = cc.prepareStatement("DELETE FROM BR_PAKET_PRESET WHERE PAKET = ? AND KLASSE = ? AND NAME = ? AND HERKUNFT = 'EIGEN'")) {
                st.setString(1, paket); st.setString(2, klasse); st.setString(3, name); st.executeUpdate();
            }
            long id = einfuegen(cc, typen(cc, "BR_PAKET_PRESET"), "BR_PAKET_PRESET", new Daten.Zeile().mit("PAKET", paket).mit("KLASSE", klasse).mit("NAME", name)
                    .mit("BESCHREIBUNG", beschreibung).mit("HERKUNFT", Daten.EIGEN));
            try (PreparedStatement st = cc.prepareStatement("INSERT INTO BR_PAKET_WERT (PAKET_PRESET_ID, FELD, TYP, WERT) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<String, String> e : werte.entrySet()) {
                    st.setLong(1, id);
                    st.setString(2, e.getKey());
                    st.setString(3, ty.getOrDefault(e.getKey(), "STRING"));
                    if (e.getValue() == null || e.getValue().isEmpty()) st.setNull(4, Types.VARCHAR); else st.setString(4, e.getValue());
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
    }

    @Override public Map<String, Map<String, String>> presets(String klasse) throws Fehler {
        return mit("Presets lesen", cc -> {
            Map<String, Map<String, String>> m = new LinkedHashMap<>();
            try (PreparedStatement st = cc.prepareStatement("SELECT p.KLASSE, p.NAME, p.HERKUNFT, w.FELD, w.WERT FROM BR_PAKET_PRESET p LEFT JOIN BR_PAKET_WERT w ON w.PAKET_PRESET_ID = p.ID"
                    + (klasse == null ? "" : " WHERE p.KLASSE = ?") + " ORDER BY p.HERKUNFT DESC, p.KLASSE, p.NAME, w.FELD")) {
                if (klasse != null) st.setString(1, klasse);
                try (ResultSet r = st.executeQuery()) {
                    while (r.next()) {
                        String k = (klasse == null ? r.getString(1) + "." : "") + r.getString(2);
                        Map<String, String> f = m.computeIfAbsent(k, x -> new LinkedHashMap<>());
                        if (r.getString(4) != null) f.put(r.getString(4), r.getString(5) == null ? "" : r.getString(5));
                    }
                }
            }
            return m;
        });
    }

    @Override public void regiebuch(String name, String text, String beschreibung) throws Fehler {
        Regie.Book bk = Regie.parse(text);
        Startdaten.Kopf k = Startdaten.Kopf.aus(bk);
        zusammen("Regiebuch speichern", cc -> {
            if (zahl(cc, "SELECT COUNT(*) FROM BR_REGIEBUCH WHERE NAME = ? AND HERKUNFT = 'MITGELIEFERT'", name) > 0)
                throw new SQLException("„" + name + "“ ist mitgeliefert; bitte einen eigenen Namen wählen");
            try (PreparedStatement st = cc.prepareStatement("DELETE FROM BR_REGIEBUCH WHERE NAME = ? AND HERKUNFT = 'EIGEN'")) { st.setString(1, name); st.executeUpdate(); }
            Daten.Zeile z = new Daten.Zeile().mit("NAME", name).mit("BESCHREIBUNG", beschreibung).mit("SZENE_MS", k.szeneMs())
                    .mit("SZENE_TEXT", k.szeneMs() == null ? null : Daten.text(k.szeneMs())).mit("ZEITRAFFER", k.raffer()).mit("LAENGE_S", Daten.r(bk.length(), 1))
                    .mit("FOTOS", k.fotos()).mit("WARTEN", k.warten()).mit("BLATT", k.blatt() ? 1 : 0).mit("WETTER", Startdaten.wetterBlock(text)).mit("TEXT", text)
                    .mit("HERKUNFT", Daten.EIGEN);
            long id = einfuegen(cc, typen(cc, "BR_REGIEBUCH"), "BR_REGIEBUCH", z);
            int nr = 0;
            Map<String, String> ty = typen(cc, "BR_STICHWORT");
            for (Regie.Zeile x : bk.zeilen())
                einfuegen(cc, ty, "BR_STICHWORT", new Daten.Zeile().mit("REGIEBUCH_ID", id).mit("NR", ++nr).mit("SEKUNDE", Daten.r(x.sekunde(), 2))
                        .mit("BEFEHL", x.befehl().toUpperCase(Locale.ROOT)).mit("ARGUMENTE", x.argumente().isEmpty() ? null : x.argumente())
                        .mit("TEXT", x.text().isEmpty() ? null : x.text()));
            return null;
        });
    }

    // ------------------------------------------------------------------ Stand und Stammdaten

    /** Die Tabellen, die Startbild und Block DATEN zählen. */
    static final String[] ZAEHLEN = {"BR_BAU", "BR_FELS", "BR_ORT", "BR_WEG", "BR_FAHRPLAN", "BR_LAGE", "BR_REGIEBUCH", "BR_WETTERBUCH", "BR_ANSICHT",
            "BR_PAKET_PRESET", "BR_QUELLE", "BR_BEFUND", "BR_STUNDE", "BR_TAG", "BR_NEBELMEER", "BR_GESPENST", "BR_ZUGFAHRT", "BR_WINDFELD", "BR_REGIELAUF",
            "BR_MERKPUNKT", "BR_SITZUNG"};

    @Override public Daten.Stand stand() throws Fehler {
        return mit("Stand", cc -> {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (String t : ZAEHLEN) m.put(t, zahl(cc, "SELECT COUNT(*) FROM " + t));
            m.put("BR_ANSICHT_GEMERKT", zahl(cc, "SELECT COUNT(*) FROM BR_ANSICHT WHERE ART = 'GEMERKT'"));
            m.put("BR_REGIEBUCH_EIGEN", zahl(cc, "SELECT COUNT(*) FROM BR_REGIEBUCH WHERE HERKUNFT = 'EIGEN'"));
            m.put("BR_PAKET_PRESET_EIGEN", zahl(cc, "SELECT COUNT(*) FROM BR_PAKET_PRESET WHERE HERKUNFT = 'EIGEN'"));
            return new Daten.Stand(m, letzte(cc, -1));
        });
    }

    @Override public Szenendaten szenendaten() throws Fehler {
        return mit("Szenendaten", cc -> {
            Szenendaten d = new Szenendaten();
            try (Statement st = cc.createStatement()) {
                try (ResultSet r = st.executeQuery("SELECT RICHTUNG, ABFAHRT_S, ANKUNFT_S, ZUG FROM BR_FAHRPLAN WHERE AKTIV = 1 AND JAHR = "
                        + "(SELECT MAX(JAHR) FROM BR_FAHRPLAN WHERE AKTIV = 1) ORDER BY ABFAHRT_S")) {
                    while (r.next())
                        d.fahrplan.add(new Fahrplan.Fahrt("BERG".equals(r.getString(1)), r.getInt(2), r.getInt(3),
                                r.getString(4) != null ? r.getString(4) : ("BERG".equals(r.getString(1)) ? "Bergfahrt " : "Talfahrt ") + Fahrplan.uhr(r.getInt(2))
                                        + ("BERG".equals(r.getString(1)) ? " ab Schierke" : " ab Brocken")));
                }
                try (ResultSet r = st.executeQuery("SELECT MONAT, TEMPERATUR_C, NIEDERSCHLAG_MM, EINGANG_C, NASSE_TAGE FROM BR_KLIMA_MONAT")) {
                    while (r.next()) {
                        int m = r.getInt(1) - 1;
                        if (m < 0 || m > 11) continue;
                        for (int k = 0; k < 4; k++) { double v = r.getDouble(k + 2); d.klima[m][k] = r.wasNull() ? Double.NaN : v; }
                    }
                }
                String[] w = {"WIND_MS", "RICHTUNG_GRAD", "BOEIGKEIT", "BEDECKUNG_ACHTEL", "HOHE_SCHICHT", "WOLKENART", "REGEN_MMH", "SICHT_KM", "NEBEL", "LUFTDRUCK_HPA",
                        "TEMPERATUR_C", "FEUCHTE_PROZ", "DUENUNG_M", "GEWITTER"};
                String[] s = {"LAPSE_K_KM", "WOLKENBASIS_M", "WOLKENOBERGRENZE_M", "INVERSION_UNTEN_M", "INVERSION_OBEN_M", "INVERSION_K"};
                try (ResultSet r = st.executeQuery("SELECT PHASE, " + String.join(", ", w) + ", " + String.join(", ", s) + ", VERWEILEN_H FROM BR_LAGE")) {
                    while (r.next()) {
                        String p = r.getString(1);
                        double[] z = new double[w.length];
                        for (int k = 0; k < w.length; k++) {
                            if (w[k].equals("WOLKENART")) { z[k] = Startdaten.wolkenart(r.getString(k + 2)); continue; }
                            double v = r.getDouble(k + 2);
                            z[k] = r.wasNull() ? Double.NaN : v;
                        }
                        double[] sch = new double[s.length];
                        for (int k = 0; k < s.length; k++) { double v = r.getDouble(w.length + 2 + k); sch[k] = r.wasNull() ? Double.NaN : v; }
                        if (Double.isNaN(sch[5])) sch[5] = 0;
                        d.ziele.put(p, z);
                        d.schichtung.put(p, sch);
                        double v = r.getDouble(w.length + s.length + 2);
                        if (!r.wasNull()) d.verweilen.put(p, v);
                    }
                }
                try (ResultSet r = st.executeQuery("SELECT VON_PHASE, NACH_PHASE, ANTEIL FROM BR_LAGE_UEBERGANG")) {
                    while (r.next()) d.uebergaenge.computeIfAbsent(r.getString(1), x -> new LinkedHashMap<>()).put(r.getString(2), r.getDouble(3));
                }
                try (ResultSet r = st.executeQuery("SELECT NAME, TEXT FROM BR_REGIEBUCH ORDER BY HERKUNFT DESC, ID")) {
                    while (r.next()) { Clob cl = r.getClob(2); if (cl != null) d.regiebuecher.put(r.getString(1), cl.getSubString(1, (int) cl.length())); }
                }
                try (ResultSet r = st.executeQuery("SELECT TEXT FROM BR_WETTERBUCH ORDER BY HERKUNFT DESC, ID")) {
                    while (r.next()) { Clob cl = r.getClob(1); if (cl != null) d.wetterbuecher.add(cl.getSubString(1, (int) cl.length())); }
                }
            }
            return d;
        });
    }

    // ------------------------------------------------------------------ Sitzungen

    @Override public long sitzungBeginnen(String version, String gpu, String qualitaet) throws Fehler {
        return zusammen("Sitzung beginnen", cc -> {
            long id = einfuegen(cc, typen(cc, "BR_SITZUNG"), "BR_SITZUNG", new Daten.Zeile().mit("RECHNER", com.dan.store.DateiAblage.rechner())
                    .mit("BENUTZER", System.getProperty("user.name", "")).mit("APP_VERSION", version).mit("GPU", gpu).mit("QUALITAET", qualitaet));
            try (Statement st = cc.createStatement()) { st.executeUpdate("DELETE FROM BR_LOG WHERE ZEIT < SYSTIMESTAMP - INTERVAL '90' DAY"); }
            return id;
        });
    }

    @Override public void sitzungSichern(long id, Sicherung s) throws Fehler {
        Map<String, String> sp = s.spalten();
        double ms = Zahl.lies(sp.get(SZENE_MS)), zr = Zahl.lies(sp.get(ZEITRAFFER)), rs = Zahl.lies(sp.get(REGIE_SEKUNDE)),
                ax = Zahl.lies(sp.get(AUGE_X)), az = Zahl.lies(sp.get(AUGE_Z));
        zusammen("Sitzung sichern", cc -> {
            Daten.Geom auge = Double.isNaN(ax) || Double.isNaN(az) ? null : Daten.Geom.punkt(ax, az);
            try (PreparedStatement st = cc.prepareStatement("UPDATE BR_SITZUNG SET ZUSTAND = ?, ZUSTAND_ZEIT = SYSTIMESTAMP, "
                    + "REGIEBUCH_ID = (SELECT MAX(ID) FROM BR_REGIEBUCH WHERE NAME = ?), REGIE_SEKUNDE = ?, SZENE_MS = ?, SZENE_TEXT = ?, SZENE_TAG = ?, "
                    + "ZEITRAFFER = ?, LAGE = ?, KAMERA = ?, AUGE = " + (auge == null ? "NULL" : platz("SDO_GEOMETRY", auge)) + " WHERE ID = ?")) {
                int i = binden(st, 1, "CLOB", s.zustand());
                i = binden(st, i, "VARCHAR2:60", sp.get(REGIEBUCH));
                i = binden(st, i, "NUMBER", Double.isNaN(rs) ? null : rs);
                i = binden(st, i, "NUMBER", Double.isNaN(ms) ? null : (long) ms);
                i = binden(st, i, "VARCHAR2:40", Double.isNaN(ms) ? null : Daten.text(ms));
                i = binden(st, i, "VARCHAR2:10", Double.isNaN(ms) ? null : Daten.tag(ms));
                i = binden(st, i, "NUMBER", Double.isNaN(zr) ? null : zr);
                i = binden(st, i, "VARCHAR2:16", sp.get(LAGE));
                i = binden(st, i, "VARCHAR2:12", sp.get(KAMERA));
                if (auge != null) i = binden(st, i, "SDO_GEOMETRY", auge);
                st.setLong(i, id);
                st.executeUpdate();
            }
            return null;
        });
    }

    @Override public void sitzungBeenden(long id) throws Fehler {
        zusammen("Sitzung beenden", cc -> {
            try (PreparedStatement st = cc.prepareStatement("UPDATE BR_SITZUNG SET ENDE = SYSTIMESTAMP, SAUBER_BEENDET = 1 WHERE ID = ?")) { st.setLong(1, id); st.executeUpdate(); }
            return null;
        });
    }

    private static Daten.Sitzung letzte(Connection c, long ausser) throws SQLException {
        try (PreparedStatement st = c.prepareStatement("SELECT s.ID, s.BEGINN, s.ENDE, s.SAUBER_BEENDET, s.ZUSTAND, s.ZUSTAND_ZEIT, r.NAME AS REGIEBUCH, s.REGIE_SEKUNDE, "
                + "s.SZENE_MS, s.LAGE FROM BR_SITZUNG s LEFT JOIN BR_REGIEBUCH r ON r.ID = s.REGIEBUCH_ID WHERE s.ZUSTAND IS NOT NULL AND s.ID <> ? "
                + "ORDER BY s.ZUSTAND_ZEIT DESC NULLS LAST, s.ID DESC FETCH FIRST 1 ROWS ONLY")) {
            st.setLong(1, ausser);
            try (ResultSet r = st.executeQuery()) {
                if (!r.next()) return null;
                Timestamp b = r.getTimestamp(2), e = r.getTimestamp(3), zz = r.getTimestamp(6);
                Clob cl = r.getClob(5);
                double rs = r.getDouble(8);
                Double regieS = r.wasNull() ? null : rs;
                long ms = r.getLong(9);
                Long szene = r.wasNull() ? null : ms;
                return new Daten.Sitzung(r.getLong(1), b.getTime(), e == null ? null : e.getTime(), r.getInt(4) == 1, cl == null ? null : cl.getSubString(1, (int) cl.length()),
                        zz == null ? null : zz.getTime(), r.getString(7), regieS, szene, r.getString(10));
            }
        }
    }

    @Override public Daten.Sitzung letzteSitzung(long ausser) throws Fehler { return mit("Letzte Sitzung", cc -> letzte(cc, ausser)); }

    // ------------------------------------------------------------------ Protokoll

    @Override public void protokoll(List<Protokoll.Zeile> zeilen, Long sitzung) throws Fehler {
        if (zeilen.isEmpty()) return;
        zusammen("Protokoll", cc -> {
            List<Long> ids = new ArrayList<>();
            try (PreparedStatement st = cc.prepareStatement("SELECT BR_LOG_SEQ.NEXTVAL FROM DUAL CONNECT BY LEVEL <= ?")) {
                st.setInt(1, zeilen.size());
                try (ResultSet r = st.executeQuery()) { while (r.next()) ids.add(r.getLong(1)); }
            }
            try (PreparedStatement st = cc.prepareStatement("INSERT INTO BR_LOG (ID, SITZUNG_ID, ZEIT, STUFE, QUELLE, TEXT, SZENENZEIT, ORT, DETAIL) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (int i = 0; i < zeilen.size(); i++) {
                    Protokoll.Zeile z = zeilen.get(i);
                    st.setLong(1, ids.get(i));
                    if (sitzung == null || sitzung < 0) st.setNull(2, Types.NUMERIC); else st.setLong(2, sitzung);
                    st.setTimestamp(3, new Timestamp(z.zeitMs()));
                    st.setString(4, z.stufe());
                    st.setString(5, z.quelle().length() > 60 ? z.quelle().substring(0, 60) : z.quelle());
                    st.setString(6, z.text().length() > 1000 ? z.text().substring(0, 997) + " …" : z.text());
                    binden(st, 7, "VARCHAR2:40", z.szenenzeit());
                    binden(st, 8, "VARCHAR2:40", z.ort());
                    binden(st, 9, "CLOB", z.detail());
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
    }

    @Override public List<String> letzteZeilen(int n) throws Fehler {
        return mit("Protokoll lesen", cc -> {
            List<String> l = new ArrayList<>();
            try (PreparedStatement st = cc.prepareStatement("SELECT ZEIT, STUFE, QUELLE, TEXT FROM BR_LOG ORDER BY ID DESC FETCH FIRST ? ROWS ONLY")) {
                st.setInt(1, n);
                try (ResultSet r = st.executeQuery()) {
                    while (r.next())
                        l.add(0, Protokoll.zeile(new Protokoll.Zeile(r.getTimestamp(1).getTime(), r.getString(2), r.getString(3), r.getString(4), null, null, null)));
                }
            }
            return l;
        });
    }

    @Override public synchronized void schliessen() {
        try { if (c != null) c.close(); } catch (SQLException e) { /* zu */ }
        c = null;
    }
}
