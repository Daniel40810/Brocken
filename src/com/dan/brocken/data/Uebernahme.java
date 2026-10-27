package com.dan.brocken.data;

import com.dan.store.Ablage;
import com.dan.store.Protokoll;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Übernahme aus den Dateien in die Datenbank (F25 A): die Chronik (Stunden, Tage, Nebelmeere, Gespenster, Zugfahrten,
 * Windfelder, Läufe der Regiebücher mit Blatt) und das Eigene (Merkpunkte, gemerkte Ansichten, Regiebücher, Presets).
 * Die Nummern der Sitzungen aus den Dateien gibt es in der Datenbank nicht; die Zeilen kommen ohne Sitzung hinein.
 * Danach heißen die Dateien {@code *.uebernommen}, damit sie kein zweites Mal kommen.
 */
public final class Uebernahme {
    private Uebernahme() { }

    /** Was übernommen wurde, als Text („12 Stunden, 1 Tage …“). */
    public static String uebernehmen(DateiSpeicher von, Speicher nach) throws Ablage.Fehler {
        Map<String, Integer> n = new LinkedHashMap<>();
        for (String t : Speicher.CHRONIK) {
            int k = 0;
            for (Daten.Zeile z : von.alleZeilen(t)) {
                Daten.Zeile w = ohne(z);
                if (t.equals("BR_REGIELAUF") && w.get("BLATT") == null && w.text("BLATT_DATEI") != null) {
                    try { w.mit("BLATT", java.nio.file.Files.readAllBytes(new File(w.text("BLATT_DATEI")).toPath())); } catch (Exception x) { /* ohne Blatt */ }
                }
                nach.schreiben(t, w);
                k++;
            }
            n.put(t, k);
        }
        int m = 0, a = 0, rb = 0, pr = 0;
        for (Daten.Zeile z : von.alleZeilen("BR_MERKPUNKT")) { nach.schreiben("BR_MERKPUNKT", ohne(z)); m++; }
        for (Daten.Zeile z : von.alleZeilen("BR_ANSICHT")) { nach.schreiben("BR_ANSICHT", ohne(z)); a++; }
        for (Daten.Zeile z : von.alleZeilen("BR_REGIEBUCH")) {
            try { nach.regiebuch(z.text("NAME"), z.text("TEXT"), z.text("BESCHREIBUNG")); rb++; }
            catch (Ablage.Fehler e) { Protokoll.warnung("Übernahme", "Regiebuch „" + z.text("NAME") + "“: " + e.getMessage()); }
        }
        Map<Long, Map<String, String>> werte = new HashMap<>();
        for (Daten.Zeile w : von.alleZeilen("BR_PAKET_WERT"))
            werte.computeIfAbsent(w.ganz("PAKET_PRESET_ID", -1), x -> new LinkedHashMap<>()).put(w.text("FELD"), w.text("WERT") == null ? "" : w.text("WERT"));
        for (Daten.Zeile z : von.alleZeilen("BR_PAKET_PRESET")) {
            try {
                nach.preset(z.text("PAKET"), z.text("KLASSE"), z.text("NAME"), z.text("BESCHREIBUNG"), werte.getOrDefault(z.ganz("ID", -1), Map.of()));
                pr++;
            } catch (Ablage.Fehler e) { Protokoll.warnung("Übernahme", "Preset „" + z.text("NAME") + "“: " + e.getMessage()); }
        }
        List<String> umbenannt = new ArrayList<>();
        for (File f : von.zeilenDateien()) {
            if (!f.isFile()) continue;
            File neu = new File(f.getPath() + ".uebernommen");
            for (int i = 2; neu.exists(); i++) neu = new File(f.getPath() + "." + i + ".uebernommen");
            if (f.renameTo(neu)) umbenannt.add(f.getName());
        }
        String t = String.format(Locale.GERMAN, "%d Stunden, %d Tage, %d Nebelmeere, %d Gespenster, %d Zugfahrten, %d Windfelder, %d Regieläufe, "
                        + "%d Merkpunkte, %d Ansichten, %d Regiebücher, %d Presets",
                n.get("BR_STUNDE"), n.get("BR_TAG"), n.get("BR_NEBELMEER"), n.get("BR_GESPENST"), n.get("BR_ZUGFAHRT"), n.get("BR_WINDFELD"), n.get("BR_REGIELAUF"),
                m, a, rb, pr);
        Protokoll.info("Übernahme", t + " aus " + von.wo() + " nach " + nach.wo() + (umbenannt.isEmpty() ? "" : "; umbenannt: " + String.join(", ", umbenannt)));
        return t;
    }

    /** Ohne Nummer und Sitzung (die gibt es in der Datenbank nicht). */
    static Daten.Zeile ohne(Daten.Zeile z) {
        Daten.Zeile w = new Daten.Zeile(z);
        w.remove("ID");
        w.remove("SITZUNG_ID");
        return w;
    }

    /** Ob es in den Dateien etwas zu übernehmen gibt. */
    public static boolean etwasDa(DateiSpeicher d) {
        for (File f : d.zeilenDateien()) if (f.isFile()) return true;
        return false;
    }
}
