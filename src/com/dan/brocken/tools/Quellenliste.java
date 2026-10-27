package com.dan.brocken.tools;

import com.dan.brocken.data.Daten;
import com.dan.brocken.data.Startdaten;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Phase 18: {@code docs/QUELLEN.md} aus {@code data/quellen.txt} und {@code data/befunde.txt} – dieselben Zeilen wie in
 * {@code BR_QUELLE}, {@code BR_BEFUND} und {@code BR_BEFUND_QUELLE}. {@code ant quellen}; die Prüfung meldet, wenn die
 * Datei nicht mehr zu den Listen passt.
 */
public final class Quellenliste {
    private Quellenliste() { }

    public static void main(String[] args) throws IOException {
        File f = new File(args.length > 0 ? args[0] : "docs/QUELLEN.md");
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        String t = text();
        Files.writeString(f.toPath(), t, StandardCharsets.UTF_8);
        System.out.println("Quellenliste: " + f + " (" + t.length() + " Zeichen)");
    }

    private static String zelle(String s) { return s == null ? "" : s.replace("|", "\\|").replace("\n", " ").strip(); }

    private static final Map<String, String> BEFUND = Map.of("BELEGT", "belegt", "GEMESSEN", "gemessen", "GESTALTET", "gestaltet [G]");

    /** Der ganze Text (Markdown). */
    public static String text() {
        List<Daten.Zeile> q = Startdaten.quellen();
        List<Startdaten.Befund> b = Startdaten.befunde();
        Map<String, Integer> art = new TreeMap<>();
        for (Startdaten.Befund x : b) art.merge(x.zeile().text("BEFUND"), 1, Integer::sum);
        Map<String, Integer> nutzung = new LinkedHashMap<>();
        for (Daten.Zeile z : q) nutzung.put(z.text("KURZ"), 0);
        for (Startdaten.Befund x : b) for (String k : x.quellen()) nutzung.merge(k, 1, Integer::sum);
        StringBuilder s = new StringBuilder();
        s.append("# Brocken – Quellen und Befunde\n\n");
        s.append("Erzeugt von `tools.Quellenliste` (`ant quellen`) aus `src/com/dan/brocken/data/quellen.txt` und `befunde.txt`. Dieselben Zeilen stehen in den Tabellen ")
                .append("`BR_QUELLE`, `BR_BEFUND` und `BR_BEFUND_QUELLE` (Sicht `BR_BEFUND_V`). Jeder Befund ist **belegt** (eine Quelle nennt es), **gemessen** ")
                .append("(aus Daten wie dem DGM1 oder den Klimareihen des DWD abgeleitet) oder **gestaltet** [G] (so gebaut, dass es glaubhaft ist, ohne Beleg).\n\n");
        s.append(q.size()).append(" Quellen · ").append(b.size()).append(" Befunde (");
        s.append(art.getOrDefault("BELEGT", 0)).append(" belegt, ").append(art.getOrDefault("GEMESSEN", 0)).append(" gemessen, ")
                .append(art.getOrDefault("GESTALTET", 0)).append(" gestaltet)\n\n");
        s.append("Geodaten: © GeoBasis-DE / LVermGeo ST (DGM1, ALKIS), © GeoBasis-DE / BKG (DGM200), Copernicus GLO-30 (© DLR e.V. 2010–2014 und © Airbus Defence and Space GmbH 2014–2018, ")
                .append("bereitgestellt unter COPERNICUS durch die Europäische Union und ESA). Klima: Deutscher Wetterdienst. Die Lizenzvermerke der einzelnen Daten stehen in den Quellen.\n\n");
        s.append("## Quellen\n\n| Kurz | Art | Titel | Abgerufen | Bemerkung | Befunde |\n|---|---|---|---|---|---|\n");
        for (Daten.Zeile z : q) {
            String titel = zelle(z.text("TITEL")), url = z.text("URL");
            s.append("| ").append(zelle(z.text("KURZ"))).append(" | ").append(zelle(z.text("ART"))).append(" | ")
                    .append(url == null ? titel : "[" + titel + "](" + url.strip() + ")").append(" | ")
                    .append(zelle(z.text("ABGERUFEN"))).append(" | ").append(zelle(z.text("BEMERKUNG"))).append(" | ")
                    .append(nutzung.getOrDefault(z.text("KURZ"), 0)).append(" |\n");
        }
        s.append("\n## Befunde\n\n| Thema | Gegenstand | Befund | Text | Quellen |\n|---|---|---|---|---|\n");
        for (Startdaten.Befund x : b) {
            Daten.Zeile z = x.zeile();
            s.append("| ").append(zelle(z.text("THEMA"))).append(" | ").append(zelle(z.text("GEGENSTAND"))).append(" | ")
                    .append(BEFUND.getOrDefault(z.text("BEFUND"), zelle(z.text("BEFUND")))).append(" | ").append(zelle(z.text("TEXT"))).append(" | ")
                    .append(String.join(", ", x.quellen())).append(" |\n");
        }
        s.append("\n## Gestaltet [G]\n\nWas die Szene zeigt, ohne dass eine Quelle es so nennt, ist im Werkbuch und im Code mit [G] markiert: ")
                .append("etwa die Waldkarte und die Bodenkarte (aus ALKIS und Gelände geschätzt), Farben und Dachhöhen der Bauten, die Vögel nach Ort und Stunde, ")
                .append("die Menschen nach Andrang, die Stimmen der Walpurgisnacht. Die Befunde oben mit „gestaltet“ nennen die wichtigsten.\n");
        return s.toString();
    }
}
