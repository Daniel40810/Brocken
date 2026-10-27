package com.dan.brocken.data;

import com.dan.brocken.scene.Fahrplan;
import com.dan.brocken.scene.Regie;
import com.dan.brocken.wiring.KlimaBrocken;
import com.dan.store.Protokoll;
import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Stammdaten, die die App beim Start aus der Datenbank liest (F26 A, F31 A, F36 A): der Fahrplan, das Klima je
 * Monat, die Lagen mit Zielwerten, Schichtung und Verweildauer, die Übergänge der freien Regie, die Regiebücher und
 * die Wetter-Regiebücher. {@link #anwenden} setzt sie in die Szene, bevor sie entsteht; fehlt die Datenbank, gilt der
 * Code (ohne Datenbank kommen nur die eigenen Regiebücher aus den Dateien dazu).
 * <p>
 * Was nicht passt (eine Lage, die es im Code nicht gibt, ein Monat zu wenig, ein Regiebuch, das sich nicht lesen
 * lässt), bleibt beim Code und steht in {@link #befunde}.
 */
public final class Szenendaten {
    /** Woher die Szene ihre Daten hat: „Datenbank“, „Dateien“ oder „Code“. */
    public static volatile String herkunft = "Code";
    /** Was beim letzten Anwenden nicht ging oder vom Code abwich (für Block DATEN und Prüfung). */
    public static volatile List<String> befunde = List.of();
    /** Kurz: „Fahrplan 14 Fahrten, Klima 12 Monate, 13 Lagen, 48 Übergänge, 4 Regiebücher, 4 Wetterbücher“. */
    public static volatile String zeile = "Szenendaten aus dem Code";

    public final List<Fahrplan.Fahrt> fahrplan = new ArrayList<>();
    /** Je Monat (0..11): Temperatur, Niederschlag, Eingang der Regie, nasse Tage; NaN, wo leer. */
    public final double[][] klima = new double[12][4];
    public final Map<String, double[]> ziele = new LinkedHashMap<>(), schichtung = new LinkedHashMap<>();
    public final Map<String, Double> verweilen = new LinkedHashMap<>();
    public final Map<String, Map<String, Double>> uebergaenge = new LinkedHashMap<>();
    /** Regiebücher: Name → Text (mitgelieferte und eigene). */
    public final Map<String, String> regiebuecher = new LinkedHashMap<>();
    public final List<String> wetterbuecher = new ArrayList<>();

    public Szenendaten() {
        for (double[] k : klima) java.util.Arrays.fill(k, Double.NaN);
    }

    /** Setzt die Daten in die Klassen der Szene (vor dem Bau des Renderers). Gibt die Befunde zurück. */
    public List<String> anwenden(String woher) {
        List<String> b = new ArrayList<>();
        // Fahrplan (F31 A): die App fährt nach der Tabelle
        if (!fahrplan.isEmpty()) {
            List<Fahrplan.Fahrt> f = new ArrayList<>(fahrplan);
            f.sort((x, y) -> Integer.compare(x.ab(), y.ab()));
            Fahrplan.geladen = List.copyOf(f);
        } else if (woher.equals("Datenbank")) b.add("Fahrplan: keine aktive Zeile in BR_FAHRPLAN, es gilt der Code");
        // Klima je Monat (F36 A)
        int monate = 0;
        for (int m = 0; m < 12; m++) {
            double[] k = klima[m];
            if (Double.isNaN(k[0]) || Double.isNaN(k[1]) || Double.isNaN(k[2])) continue;
            KlimaBrocken.DWD[m] = k[0];
            KlimaBrocken.DWD_NIEDERSCHLAG[m] = k[1];
            KlimaBrocken.EINGANG[m] = k[2];
            if (!Double.isNaN(k[3])) KlimaBrocken.NASSE_TAGE[m] = k[3];
            monate++;
        }
        if (monate > 0 && monate < 12) b.add("Klima: nur " + monate + " Monate in BR_KLIMA_MONAT, die übrigen aus dem Code");
        // Lagen mit Zielwerten und Schichtung
        int lagen = 0;
        for (Map.Entry<String, double[]> e : ziele.entrySet()) {
            Weather.Phase p = phase(e.getKey());
            if (p == null || p.ordinal() >= KlimaBrocken.ZIELE.length) { b.add("Lage " + e.getKey() + " gibt es in der Szene nicht"); continue; }
            double[] z = e.getValue(), alt = KlimaBrocken.ZIELE[p.ordinal()];
            for (int i = 0; i < Math.min(z.length, alt.length); i++) if (!Double.isNaN(z[i])) alt[i] = z[i];
            double[] s = schichtung.get(e.getKey());
            if (s != null) {
                double[] sa = KlimaBrocken.SCHICHTUNG[p.ordinal()];
                for (int i = 0; i < Math.min(s.length, sa.length); i++) sa[i] = s[i];   // NaN heißt hier: aus Temperatur und Taupunkt
            }
            Double v = verweilen.get(e.getKey());
            if (v != null && !v.isNaN() && v > 0) KlimaBrocken.VERWEILEN[p.ordinal()] = v;
            lagen++;
        }
        // Übergänge: je Zeile, die in der Datenbank steht, ganz aus der Datenbank (fehlende Anteile 0)
        int ue = 0;
        for (Map.Entry<String, Map<String, Double>> e : uebergaenge.entrySet()) {
            Weather.Phase von = phase(e.getKey());
            if (von == null || von.ordinal() >= KlimaBrocken.UEBERGAENGE.length) { b.add("Übergang von " + e.getKey() + ": Lage unbekannt"); continue; }
            double[] zeile = new double[KlimaBrocken.UEBERGAENGE[von.ordinal()].length];
            double sum = 0;
            for (Map.Entry<String, Double> n : e.getValue().entrySet()) {
                Weather.Phase nach = phase(n.getKey());
                if (nach == null || nach.ordinal() >= zeile.length) { b.add("Übergang nach " + n.getKey() + ": Lage unbekannt"); continue; }
                zeile[nach.ordinal()] = n.getValue();
                sum += n.getValue();
                ue++;
            }
            if (sum <= 0) { b.add("Übergänge von " + e.getKey() + " summieren sich zu 0, es gilt der Code"); continue; }
            for (int i = 0; i < zeile.length; i++) KlimaBrocken.UEBERGAENGE[von.ordinal()][i] = zeile[i] / sum;
            if (Math.abs(sum - 1) > 1e-6) b.add(String.format(Locale.GERMAN, "Übergänge von %s summieren sich zu %.3f; auf 1 gebracht", e.getKey(), sum));
        }
        // Regiebücher
        List<Regie.Book> books = new ArrayList<>();
        for (Map.Entry<String, String> e : regiebuecher.entrySet()) {
            try {
                Regie.Book bk = Regie.parse(e.getValue());
                books.add(bk);
            } catch (RuntimeException ex) {
                b.add("Regiebuch „" + e.getKey() + "“ lässt sich nicht lesen: " + ex.getMessage());
            }
        }
        if (!books.isEmpty()) {
            // die mitgelieferten aus dem Code, die es in den Daten nicht gibt, bleiben
            List<Regie.Book> alle = new ArrayList<>(books);
            for (Regie.Book m : Regie.mitgelieferte()) if (alle.stream().noneMatch(x -> x.name().equals(m.name()))) alle.add(m);
            Regie.geladen = List.copyOf(alle);
        }
        // Wetter-Regiebücher
        List<Scenario> wb = new ArrayList<>();
        for (String t : wetterbuecher) {
            try { wb.add(Scenario.parse(t)); } catch (RuntimeException ex) { b.add("Wetterbuch lässt sich nicht lesen: " + ex.getMessage()); }
        }
        if (!wb.isEmpty()) KlimaBrocken.geladen = List.copyOf(wb);
        herkunft = woher;
        zeile = String.format(Locale.GERMAN, "Szenendaten aus %s: Fahrplan %d Fahrten, Klima %d Monate, %d Lagen, %d Übergänge, %d Regiebücher, %d Wetterbücher",
                woher.equals("Datenbank") ? "der Datenbank" : woher.equals("Dateien") ? "Code und Dateien" : "dem Code",
                Fahrplan.fahrten().size(), monate, lagen, ue, Regie.all().size(), KlimaBrocken.regiebuecher().size());
        befunde = List.copyOf(b);
        Protokoll.info("Daten", zeile + (b.isEmpty() ? "" : " · " + String.join("; ", b)));
        return b;
    }

    static Weather.Phase phase(String n) {
        try { return Weather.Phase.valueOf(n); } catch (RuntimeException e) { return null; }
    }

    /** Die Stammdaten aus dem Speicher setzen; ohne Datenbank nur die eigenen Regiebücher aus den Dateien. Fehler: der Code gilt. */
    public static void laden(Speicher s) {
        try {
            Szenendaten d = s.szenendaten();
            if (d != null) { d.anwenden("Datenbank"); return; }
            Szenendaten f = new Szenendaten();
            for (Daten.Zeile z : s.lesen("BR_REGIEBUCH", Map.of("HERKUNFT", Daten.EIGEN), 200))
                if (z.text("TEXT") != null) f.regiebuecher.put(z.text("NAME"), z.text("TEXT"));
            f.anwenden(f.regiebuecher.isEmpty() ? "Code" : "Dateien");
        } catch (Exception e) {
            befunde = List.of("Szenendaten nicht gelesen: " + e.getMessage());
            Protokoll.warnung("Daten", "Szenendaten nicht gelesen, es gilt der Code: " + e.getMessage());
        }
    }
}
