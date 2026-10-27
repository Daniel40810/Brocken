package com.dan.brocken.data;

import com.dan.store.DateiAblage;
import com.dan.store.Felder;
import com.dan.store.Skript;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Der Speicher ohne Datenbank (F25 A): Sitzungen und Protokoll wie {@link DateiAblage} (Ordner {@code sitzungen/},
 * {@code protokoll.txt}), die Zeilen der Chronik und das Eigene je Tabelle in {@code zeilen/BR_….txt}, eine Zeile Text je
 * Zeile ({@link Daten.Zeile#kodieren}). Die Spalten prüft er gegen {@code db/sql/01_tabellen.sql}, damit die Dateien
 * später ohne Verlust in die Datenbank übernommen werden ({@link Uebernahme}). Mitgeliefertes kommt aus dem Code.
 */
public final class DateiSpeicher extends DateiAblage implements Speicher {

    public DateiSpeicher(File dir) { super(dir); }

    /** Der Ordner daten/ der App (-Dbrocken.daten). */
    public static File ordner() {
        String d = System.getProperty("brocken.daten");
        return new File(d != null ? d : "daten");
    }

    @Override public String wo() { return "Dateien in " + dir.getPath(); }

    // ------------------------------------------------------------------ Spalten aus 01_tabellen.sql

    private static volatile Map<String, Map<String, String>> spalten;

    /** Tabelle → Spalte → Typ aus db/sql/01_tabellen.sql (leer, wenn es die Datei nicht gibt). */
    public static Map<String, Map<String, String>> spalten() {
        Map<String, Map<String, String>> m = spalten;
        if (m != null) return m;
        m = new HashMap<>();
        File f = new File(Verbindung.skripte(), "01_tabellen.sql");
        if (f.isFile()) {
            try {
                for (Skript.Spalte s : Skript.spalten(Skript.zerlegen(Files.readString(f.toPath(), StandardCharsets.UTF_8))))
                    m.computeIfAbsent(s.tabelle(), x -> new LinkedHashMap<>()).put(s.name(), s.definition().toUpperCase(java.util.Locale.ROOT));
            } catch (IOException e) { /* ohne Prüfung */ }
        }
        spalten = m;
        return m;
    }

    private static void pruefe(String tabelle, Daten.Zeile z) throws Fehler {
        Map<String, Map<String, String>> s = spalten();
        if (s.isEmpty()) return;
        Map<String, String> t = s.get(tabelle);
        if (t == null) throw new Fehler("Tabelle " + tabelle + " gibt es nicht");
        for (String k : z.keySet()) if (!t.containsKey(k)) throw new Fehler("Spalte " + tabelle + "." + k + " gibt es nicht");
    }

    // ------------------------------------------------------------------ Zeilen

    private File datei(String tabelle) { return sub("zeilen", tabelle + ".txt"); }

    private final Map<String, Long> naechste = new HashMap<>();

    private synchronized List<Daten.Zeile> alle(String tabelle) throws Fehler {
        File f = datei(tabelle);
        if (!f.isFile()) return new ArrayList<>();
        try {
            List<Daten.Zeile> l = new ArrayList<>();
            for (String s : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) if (!s.isBlank() && !s.startsWith("#")) l.add(Daten.Zeile.dekodieren(s));
            return l;
        } catch (IOException e) {
            throw new Fehler(f + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    @Override public synchronized long schreiben(String tabelle, Daten.Zeile z) throws Fehler {
        pruefe(tabelle, z);
        Long n = naechste.get(tabelle);
        if (n == null) {
            long max = 0;
            for (Daten.Zeile x : alle(tabelle)) max = Math.max(max, x.ganz("ID", 0));
            n = max + 1;
        }
        Daten.Zeile w = new Daten.Zeile();
        w.put("ID", n);
        for (Map.Entry<String, Object> e : z.entrySet()) if (!e.getKey().equals("ID")) w.put(e.getKey(), e.getValue());
        if (spalten().getOrDefault(tabelle, Map.of()).containsKey("HERKUNFT") && !w.containsKey("HERKUNFT")) w.put("HERKUNFT", Daten.EIGEN);
        if (!w.containsKey("ANGELEGT") && spalten().getOrDefault(tabelle, Map.of()).containsKey("ANGELEGT")) w.put("ANGELEGT", System.currentTimeMillis());
        try {
            File f = datei(tabelle);
            Files.createDirectories(f.getAbsoluteFile().getParentFile().toPath());
            Files.writeString(f.toPath(), w.kodieren() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new Fehler(tabelle + " nicht geschrieben: " + e.getMessage(), e);
        }
        naechste.put(tabelle, n + 1);
        return n;
    }

    private static boolean passt(Daten.Zeile z, Map<String, Object> wo) {
        if (wo == null) return true;
        for (Map.Entry<String, Object> e : wo.entrySet()) {
            Object v = z.get(e.getKey()), w = e.getValue();
            if (v instanceof Number a && w instanceof Number b) { if (a.doubleValue() != b.doubleValue()) return false; }
            else if (w instanceof Boolean b) { if (z.ja(e.getKey()) != b) return false; }
            else if (!Objects.equals(v == null ? null : v.toString(), w == null ? null : w.toString())) return false;
        }
        return true;
    }

    @Override public List<Daten.Zeile> lesen(String tabelle, Map<String, Object> wo, int hoechstens) throws Fehler {
        List<Daten.Zeile> l = alle(tabelle);
        Collections.reverse(l);
        List<Daten.Zeile> r = new ArrayList<>();
        for (Daten.Zeile z : l) {
            if (!passt(z, wo)) continue;
            z.values().removeIf(v -> v instanceof byte[]);
            r.add(z);
            if (r.size() >= hoechstens) break;
        }
        return r;
    }

    @Override public byte[] bytes(String tabelle, long id, String spalte) throws Fehler {
        for (Daten.Zeile z : alle(tabelle)) if (z.ganz("ID", -1) == id) return z.bytes(spalte);
        return null;
    }

    @Override public synchronized void loeschen(String tabelle, long id) throws Fehler {
        List<Daten.Zeile> l = alle(tabelle);
        StringBuilder b = new StringBuilder();
        boolean weg = false;
        for (Daten.Zeile z : l) {
            if (z.ganz("ID", -1) == id) {
                if (Daten.MITGELIEFERT.equals(z.text("HERKUNFT"))) throw new Fehler("Zeile " + id + " ist mitgeliefert und wird nicht gelöscht");
                weg = true;
                continue;
            }
            b.append(z.kodieren()).append('\n');
        }
        if (weg) schreibe(datei(tabelle), b.toString());
    }

    // ------------------------------------------------------------------ Presets und Regiebücher

    @Override public void preset(String paket, String klasse, String name, String beschreibung, Map<String, String> werte) throws Fehler {
        for (Daten.Zeile z : alle("BR_PAKET_PRESET"))
            if (klasse.equals(z.text("KLASSE")) && name.equals(z.text("NAME"))) loeschen("BR_PAKET_PRESET", z.ganz("ID", -1));
        for (Startdaten.Preset p : Startdaten.presets())
            if (p.klasse().equals(klasse) && p.name().equals(name)) throw new Fehler("„" + name + "“ ist mitgeliefert; bitte einen eigenen Namen wählen");
        // die Werte als Text in der Spalte BESCHREIBUNG mitzuführen hieße die Tabelle verbiegen: sie gehen als Zeilen von BR_PAKET_WERT mit
        long id = schreiben("BR_PAKET_PRESET", new Daten.Zeile().mit("PAKET", paket).mit("KLASSE", klasse).mit("NAME", name).mit("BESCHREIBUNG", beschreibung)
                .mit("HERKUNFT", Daten.EIGEN));
        Map<String, String> ty = Startdaten.typen(paket + "." + klasse);
        for (Map.Entry<String, String> e : werte.entrySet())
            schreiben("BR_PAKET_WERT", new Daten.Zeile().mit("PAKET_PRESET_ID", id).mit("FELD", e.getKey()).mit("TYP", ty.getOrDefault(e.getKey(), "STRING"))
                    .mit("WERT", e.getValue()));
    }

    @Override public Map<String, Map<String, String>> presets(String klasse) throws Fehler {
        Map<String, Map<String, String>> m = new LinkedHashMap<>();
        for (Startdaten.Preset p : Startdaten.presets())
            if (klasse == null || p.klasse().equals(klasse)) m.put((klasse == null ? p.klasse() + "." : "") + p.name(), new LinkedHashMap<>(p.felder()));
        Map<Long, Map<String, String>> werte = new HashMap<>();
        for (Daten.Zeile w : alle("BR_PAKET_WERT"))
            werte.computeIfAbsent(w.ganz("PAKET_PRESET_ID", -1), x -> new LinkedHashMap<>()).put(w.text("FELD"), w.text("WERT") == null ? "" : w.text("WERT"));
        for (Daten.Zeile z : alle("BR_PAKET_PRESET"))
            if (klasse == null || klasse.equals(z.text("KLASSE")))
                m.put((klasse == null ? z.text("KLASSE") + "." : "") + z.text("NAME"), werte.getOrDefault(z.ganz("ID", -1), Map.of()));
        return m;
    }

    @Override public void regiebuch(String name, String text, String beschreibung) throws Fehler {
        for (String d : com.dan.brocken.scene.Regie.DATEIEN)
            if (com.dan.brocken.scene.Regie.parse(com.dan.brocken.scene.Regie.text(d)).name().equals(name))
                throw new Fehler("„" + name + "“ ist mitgeliefert; bitte einen eigenen Namen wählen");
        com.dan.brocken.scene.Regie.Book bk = com.dan.brocken.scene.Regie.parse(text);
        for (Daten.Zeile z : alle("BR_REGIEBUCH")) if (name.equals(z.text("NAME"))) loeschen("BR_REGIEBUCH", z.ganz("ID", -1));
        Startdaten.Kopf k = Startdaten.Kopf.aus(bk);
        schreiben("BR_REGIEBUCH", new Daten.Zeile().mit("NAME", name).mit("BESCHREIBUNG", beschreibung).mit("SZENE_MS", k.szeneMs())
                .mit("SZENE_TEXT", k.szeneMs() == null ? null : Daten.text(k.szeneMs())).mit("ZEITRAFFER", k.raffer()).mit("LAENGE_S", Daten.r(bk.length(), 1))
                .mit("FOTOS", k.fotos()).mit("WARTEN", k.warten()).mit("BLATT", k.blatt() ? 1 : 0).mit("WETTER", Startdaten.wetterBlock(text)).mit("TEXT", text)
                .mit("HERKUNFT", Daten.EIGEN));
    }

    // ------------------------------------------------------------------ Stand

    @Override public Daten.Stand stand() throws Fehler {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String t : DbSpeicher.ZAEHLEN) m.put(t, alle(t).size());
        m.put("BR_ANSICHT_GEMERKT", m.get("BR_ANSICHT"));
        m.put("BR_REGIEBUCH_EIGEN", m.get("BR_REGIEBUCH"));
        m.put("BR_PAKET_PRESET_EIGEN", m.get("BR_PAKET_PRESET"));
        // das Mitgelieferte kommt ohne Datenbank aus dem Code
        m.put("BR_REGIEBUCH", m.get("BR_REGIEBUCH") + com.dan.brocken.scene.Regie.DATEIEN.length);
        m.put("BR_ANSICHT", m.get("BR_ANSICHT") + com.dan.brocken.camera.Ansichten.ALLE.size());
        m.put("BR_PAKET_PRESET", m.get("BR_PAKET_PRESET") + Startdaten.presets().size());
        m.put("BR_FAHRPLAN", com.dan.brocken.scene.Fahrplan.ausDemCode().size());
        m.put("BR_LAGE", com.dan.brocken.wiring.KlimaBrocken.NAMEN.length);
        m.put("BR_WETTERBUCH", com.dan.brocken.wiring.KlimaBrocken.ausDemCode().size());
        m.put("BR_SITZUNG", dateien(sub("sitzungen")).size());
        return new Daten.Stand(m, letzteSitzung(-1));
    }

    @Override public Szenendaten szenendaten() { return null; }

    @Override public Daten.Sitzung letzteSitzung(long ausser) throws Fehler {
        LinkedHashMap<String, String> m = letzteSitzungText(ausser);
        if (m == null) return null;
        java.util.function.Function<String, Long> lang = k -> { double d = com.dan.store.Zahl.lies(m.get(k)); return Double.isNaN(d) ? null : (long) d; };
        double rs = com.dan.store.Zahl.lies(m.get(REGIE_SEKUNDE));
        String rb = m.get(REGIEBUCH);
        return new Daten.Sitzung(lang.apply("id"), lang.apply("beginn"), lang.apply("ende"), "true".equals(m.get("sauber")), m.get("zustand"), lang.apply("zustandZeit"),
                rb == null || rb.isEmpty() ? null : rb, Double.isNaN(rs) ? null : rs, lang.apply(SZENE_MS), m.get(LAGE));
    }

    /** Die Dateien mit Zeilen, die „Übernehmen“ in die Datenbank trägt. */
    public List<File> zeilenDateien() {
        List<File> l = new ArrayList<>();
        for (String t : Speicher.CHRONIK) l.add(datei(t));
        for (String t : Speicher.EIGENES) l.add(datei(t));
        l.add(datei("BR_PAKET_WERT"));
        return l;
    }

    /** Für die Übernahme: alle Zeilen einer Tabelle in der Reihenfolge des Schreibens (mit Bytes). */
    public List<Daten.Zeile> alleZeilen(String tabelle) throws Fehler { return alle(tabelle); }

    /** Der Zustand einer Sitzung als Textformat (für Prüfungen). */
    public LinkedHashMap<String, String> zustand(long id) throws Fehler {
        File z = sub("sitzungen", id + ".zustand");
        try {
            return z.isFile() ? Felder.ausText(Files.readString(z.toPath(), StandardCharsets.UTF_8)) : null;
        } catch (IOException e) {
            throw new Fehler("Zustand nicht lesbar: " + e.getMessage(), e);
        }
    }
}
