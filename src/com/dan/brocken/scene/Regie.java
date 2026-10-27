package com.dan.brocken.scene;

import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Phase 16: die Regiebücher als Text (aus Rungholt, das sie aus Glacier hat). Die vier mitgelieferten stehen in
 * {@code regie/*.txt} neben dieser Klasse. Ein Regiebuch ist eine Folge von Stichworten in echten Sekunden seit dem
 * Start: Datum und Uhrzeit, Zeitraffer, Wetter (ein Wetter-Regiebuch des Klimas „Brocken“, eines im Kopf des Texts oder
 * eine Lage), Ansichten, Fahrten, Mitfahrt im Zug, Blick, mit dem Wind treiben, Nebelmeer ans Auge, Raueis, die
 * Walpurgisnacht, Fotos und am Ende ein Blatt mit allen Fotos in {@code docs/status}. Was ein Stichwort tut, führt die App
 * über {@link Ctx} aus; so läuft dasselbe Regiebuch auch ohne Bild (Prüfung).
 * <p>
 * {@code warte} hält die Uhr des Regiebuchs an, bis die Szene so weit ist: eine Uhrzeit, die Abfahrt oder Ankunft des
 * Zugs, das Nebelmeer. Was danach in derselben Sekunde steht, kommt, sobald die Bedingung erfüllt ist. Nach
 * {@link #WARTE_HOECHSTENS} Sekunden geht es mit einer Meldung weiter.
 * <ul>
 * <li><b>Mit dem Zug auf den Brocken</b> (12.6.2026): am Bahnhof Schierke, die Abfahrt um 10:06, auf der Lok durch den
 * Wald, die Spirale um die Kuppe, Ankunft im Nebel.</li>
 * <li><b>Über dem Nebelmeer</b> (21.10.2026): Sonnenaufgang über dem Meer, das Gespenst im Gegenpunkt, die Auflösung
 * zum Mittag.</li>
 * <li><b>Ein Tag im Nebel</b> (14./15.1.2027): die Raueislage im Zeitraffer, der Wind an der Kuppe, mit dem Wind treiben,
 * die Fahnen wachsen, am Nachmittag klart es auf.</li>
 * <li><b>Walpurgisnacht</b> (30.4.2026, die Kür): Abend am Gipfel, der Mond geht auf, ab 22 Uhr die Stimme der Sage.</li>
 * </ul>
 * Gestaltet [G]: Abläufe, Zeiten, Blickpunkte.
 */
public final class Regie {

    /** Was ein Regiebuch braucht; die App führt es aus (ohne Bild: die Prüfung). */
    public interface Ctx {
        void time(LocalDate date, double hour);
        void lapse(double x);
        /** Ein Wetter-Regiebuch des Klimas nach Namen (die Uhr geht an seinen Beginn, plus stunde) oder das aus dem Kopf. */
        void weather(String name, double stunde, Scenario kopf);
        /** Freie Regie ab einer Lage, dazu Wind von Hand (m/s an der Wetterwarte, woher), NaN = von der Regie. */
        void weatherFree(Weather.Phase p, double speed, double fromDeg);
        void view(String name);
        /** Frei stehen: x, z (Szene), Kurs, Neigung (Grad), Augenhöhe über Grund (m). */
        void place(double x, double z, double heading, double pitch, double above);
        void fahrt(String name);
        /** Mit dem nächsten Zug fahren, Blick zur Seite (Grad). */
        void mitfahren(double look);
        /** Blick bei der Mitfahrt zur Seite (Grad). */
        void blick(double look);
        /** In den Gegenpunkt der Sonne oder zu ihr schauen, die Neigung um extra Grad versetzt. */
        void sonne(boolean gegenpunkt, double extra);
        /** Mit dem Wind treiben (der Rauchfaden), so hoch über Grund (m). */
        void treiben(double above);
        /** Das Nebelmeer mit der Untergrenze basis so legen, dass es am Auge ueber m über dem Auge endet. */
        void meer(double basis, double ueber);
        /** Das Nebelmeer wieder der Regie überlassen. */
        void meerRegie();
        /** Raueis von Hand an den Zweigen am Gipfel (cm), NaN = nach der Regie. */
        void raueis(double cm);
        /** Die Walpurgisnacht von Hand an oder aus (aus: nach dem Kalender). */
        void walpurgis(boolean an);
        /** Ob die Szene so weit ist (für {@code warte}). */
        boolean ready(Warte w);
        void capture(String caption);
        void sheet(String name);
        void say(String text);
    }

    /** Worauf {@code warte} wartet: eine Uhrzeit (Stunden seit 1970) oder ein Ereignis der Szene. */
    public record Warte(String was, double hours) {
        @Override public String toString() {
            return was.equals("uhr") ? "Uhr " + java.time.Instant.ofEpochMilli((long) (hours * 3_600_000)).atZone(java.time.ZoneId.of("Europe/Berlin")).toLocalDateTime().toString().replace('T', ' ')
                    : switch (was) {
                        case "abfahrt" -> "die Abfahrt";
                        case "ankunft" -> "die Ankunft am Brocken";
                        case "meer" -> "das Nebelmeer";
                        default -> was;
                    };
        }
    }

    /** Ein Stichwort: Sekunde (echte Zeit seit dem Start), Text, Handlung; bei {@code warte} die Bedingung. */
    public record Cue(double t, String text, Consumer<Ctx> action, Warte warte) { }

    /**
     * Ein Regiebuch: Name, Beschreibung, Länge (s, ohne die Wartezeit), Stichworte (nach der Zeit geordnet), der Text,
     * aus dem es gelesen ist, die Stichworte als Zeilen und der Wetterverlauf aus dem Kopf (oder null).
     */
    public record Book(String name, String about, double length, List<Cue> cues, String text, List<Zeile> zeilen, Scenario weather) {
        @Override public String toString() { return name; }
        /** Wie viele Fotos das Regiebuch macht. */
        public int fotos() { return (int) zeilen.stream().filter(z -> z.befehl().equals("foto")).count(); }
    }

    /** Länger wartet ein {@code warte} nicht (echte Sekunden). */
    public static final double WARTE_HOECHSTENS = 600;

    // ------------------------------------------------------------------ Ablauf

    /** Bekommt den Text jedes Stichworts (der Kinomodus macht Untertitel daraus); null = niemand. */
    public volatile Consumer<String> beiText;

    private volatile Book book;
    private double t, waited, waitedTotal;
    private int next;
    private volatile String status = "";
    private int fired;

    /** Regiebuch beginnen (das erste Stichwort sofort). */
    public void start(Book b, Ctx c) {
        book = b;
        t = 0;
        next = 0;
        fired = 0;
        waited = waitedTotal = 0;
        status = b.name() + " beginnt";
        step(0, c);
    }

    public void stop() {
        Book b = book;
        book = null;
        if (b != null) status = b.name() + " angehalten";
    }

    public boolean running() { return book != null; }

    public Book book() { return book; }

    /** Uhr des Regiebuchs (s, steht beim Warten). */
    public double time() { return t; }

    /** Wie viele Stichworte schon ausgeführt sind. */
    public int fired() { return fired; }

    /** Wie lange schon gewartet wurde (s, alle {@code warte} zusammen). */
    public double waitedTotal() { return waitedTotal; }

    /** Das {@code warte}, an dem das Regiebuch gerade steht, oder null. */
    public Warte waiting() {
        Book b = book;
        if (b == null || next >= b.cues().size()) return null;
        Cue q = b.cues().get(next);
        return q.warte() != null && q.t() <= t ? q.warte() : null;
    }

    /** Um dt echte Sekunden weiter, fällige Stichworte ausführen; an einem {@code warte} steht die Uhr. */
    public void step(double dt, Ctx c) {
        Book b = book;
        if (b == null) return;
        double d = Math.max(0, dt);
        if (next < b.cues().size() && b.cues().get(next).warte() != null && b.cues().get(next).t() <= t) {
            waited += d;
            waitedTotal += d;
        } else t += d;
        while (next < b.cues().size() && b.cues().get(next).t() <= t) {
            Cue q = b.cues().get(next);
            if (q.warte() != null) {
                if (!c.ready(q.warte()) && waited < WARTE_HOECHSTENS) {
                    t = q.t();
                    status = String.format(Locale.GERMAN, "%s · wartet auf %s", b.name(), q.warte());
                    return;
                }
                status = waited >= WARTE_HOECHSTENS ? b.name() + " · " + q.warte() + " kam nicht, weiter" : b.name() + " · " + q.warte() + ": weiter";
                waited = 0;
                next++;
                fired++;
                continue;
            }
            next++;
            if (!q.text().isEmpty()) {
                status = String.format(Locale.GERMAN, "%s · %s", b.name(), q.text());
                Consumer<String> h = beiText;
                if (h != null) h.accept(q.text());   // Untertitel im Kino
            }
            try { q.action().accept(c); } catch (RuntimeException e) { status = b.name() + " · Fehler bei „" + q.text() + "“: " + e.getMessage(); }
            fired++;
        }
        if (t >= b.length() && next >= b.cues().size()) {
            book = null;
            status = b.name() + " ist zu Ende (" + fired + " Stichworte)";
        }
    }

    /** „Über dem Nebelmeer · 7:58: die Sonne geht auf · 1:10 von 5:40“ */
    public String describe() {
        Book b = book;
        if (b == null) return status.isEmpty() ? "kein Regiebuch" : status;
        return String.format(Locale.GERMAN, "%s · %d:%02d von %d:%02d", status, (int) t / 60, (int) t % 60, (int) b.length() / 60, (int) b.length() % 60);
    }

    // ------------------------------------------------------------------ die Regiebücher (als Text)

    /** Die vier mitgelieferten Regiebücher aus den Textdateien neben dieser Klasse (regie/*.txt). */
    public static final String[] DATEIEN = {"zug.txt", "nebelmeer.txt", "nebeltag.txt", "walpurgisnacht.txt"};

    /**
     * Seit Phase 17 (F26 A): die Regiebücher aus der Datenbank (mitgelieferte und eigene) oder die eigenen aus den
     * Dateien, gesetzt beim Start von {@code data.Szenendaten}; null: die Textdateien.
     */
    public static volatile List<Book> geladen;

    public static List<Book> all() {
        List<Book> g = geladen;
        return g != null && !g.isEmpty() ? new ArrayList<>(g) : mitgelieferte();
    }

    /** Die vier aus den Textdateien. */
    public static List<Book> mitgelieferte() {
        List<Book> l = new ArrayList<>();
        for (String d : DATEIEN) l.add(mitgeliefert(d));
        return l;
    }

    /** Nach Name, auch ohne Umlaute und in Teilen („Zug“, „Nebelmeer“, „Walpurgis“). */
    public static Book byName(String n) {
        String k = plain(n);
        List<Book> all = all();
        for (Book b : all) if (plain(b.name()).equals(k)) return b;
        for (Book b : all) if (plain(b.name()).contains(k)) return b;
        return null;
    }

    static String plain(String s) {
        return s.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss").replaceAll("[^a-z0-9]", "");
    }

    /** Der Text eines mitgelieferten Regiebuchs. */
    public static String text(String datei) {
        try (java.io.InputStream in = Regie.class.getResourceAsStream("regie/" + datei)) {
            if (in == null) throw new IllegalStateException("Regiebuch regie/" + datei + " fehlt im Build");
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Regiebuch regie/" + datei + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    private static Book mitgeliefert(String datei) { return parse(text(datei)); }

    /** Die Befehle der Stichworte (klein geschrieben im Text). */
    public static final List<String> BEFEHLE = List.of("zeit", "raffer", "wetter", "lage", "ansicht", "kamera", "fahrt", "mitfahren", "blick", "sonne",
            "treiben", "meer", "raueis", "walpurgis", "warte", "foto", "blatt", "sage");

    /** Die Bedingungen von {@code warte}. */
    public static final List<String> WARTEN = List.of("uhr", "abfahrt", "ankunft", "meer");

    /** Ein Stichwort, wie es im Text steht: Sekunde, Befehl, Argumente, Text fürs HUD. */
    public record Zeile(double sekunde, String befehl, String argumente, String text) { }

    /**
     * Liest ein Regiebuch aus Text. Kopfzeilen: {@code Regiebuch:}, {@code Beschreibung:}, {@code Länge:} (m:ss),
     * wahlweise {@code Wetter: hh:mm | Zeitraffer} mit folgenden {@code Schritt:}-Zeilen im Format von
     * {@link Scenario#parse}. Dann je Zeile ein Stichwort: {@code m:ss befehl argumente | Text}. Zeilen zur selben Zeit
     * ohne Text gehören zum Stichwort davor, außer {@code warte}. Fehler nennen die Zeile.
     */
    public static Book parse(String text) {
        String name = null, about = "";
        double length = Double.NaN;
        String wetterKopf = null;
        StringBuilder steps = new StringBuilder();
        List<Zeile> zeilen = new ArrayList<>();
        int no = 0;
        LocalDate[] day = {null};
        List<Warte> warten = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            no++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String low = line.toLowerCase(Locale.ROOT);
            try {
                if (low.startsWith("regiebuch:")) name = line.substring(10).strip();
                else if (low.startsWith("beschreibung:")) about = line.substring(13).strip();
                else if (low.startsWith("länge:")) length = sekunden(line.substring(6).strip());
                else if (low.startsWith("wetter:")) wetterKopf = line.substring(7).strip();
                else if (low.startsWith("schritt:")) steps.append(line).append('\n');
                else {
                    Zeile z = zeile(line);
                    if (z.befehl().equals("zeit")) day[0] = datum(z.argumente().split("\\s+")[0]);
                    if (z.befehl().equals("warte")) warten.add(warte(z.argumente(), day[0]));
                    else warten.add(null);
                    zeilen.add(z);
                }
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Zeile " + no + ": " + e.getMessage() + " („" + line + "“)");
            }
        }
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("Es fehlt die Zeile „Regiebuch: Name“");
        if (zeilen.isEmpty()) throw new IllegalArgumentException("Das Regiebuch hat keine Stichworte");
        Scenario sc = null;
        if (wetterKopf != null) {
            String[] w = wetterKopf.split("\\|");
            String beginn = w[0].strip(), raffer = w.length > 1 ? w[1].strip() : "1";
            sc = Scenario.parse("Szenario: " + name + "\nBeginn: " + beginn + "\nZeitraffer: " + raffer + "\n" + steps);
        } else if (steps.length() > 0) throw new IllegalArgumentException("Schritte ohne Zeile „Wetter: hh:mm | Zeitraffer“");
        // nach der Zeit ordnen (stabil: in derselben Sekunde gilt die Reihenfolge im Text)
        Integer[] idx = new Integer[zeilen.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, java.util.Comparator.comparingDouble(i -> zeilen.get(i).sekunde()));
        List<Zeile> sorted = new ArrayList<>();
        List<Warte> sortedW = new ArrayList<>();
        for (int i : idx) { sorted.add(zeilen.get(i)); sortedW.add(warten.get(i)); }
        List<Cue> cues = new ArrayList<>();
        for (int i = 0; i < sorted.size(); ) {
            Zeile z = sorted.get(i);
            if (sortedW.get(i) != null) { cues.add(new Cue(z.sekunde(), z.text(), x -> { }, sortedW.get(i))); i++; continue; }
            List<Zeile> grp = new ArrayList<>(List.of(z));
            int k = i + 1;
            while (k < sorted.size() && sorted.get(k).sekunde() == z.sekunde() && sorted.get(k).text().isEmpty() && sortedW.get(k) == null) grp.add(sorted.get(k++));
            List<Consumer<Ctx>> acts = new ArrayList<>();
            for (Zeile g : grp) acts.add(handlung(g, sc, name));
            cues.add(new Cue(z.sekunde(), z.text(), x -> { for (Consumer<Ctx> a : acts) a.accept(x); }, null));
            i = k;
        }
        if (Double.isNaN(length)) length = cues.get(cues.size() - 1).t() + 1;
        return new Book(name, about, length, cues, text, sorted, sc);
    }

    /** „1:48“, „0:04“, „96“ oder „1:36.5“ → Sekunden. */
    static double sekunden(String s) {
        int c = s.indexOf(':');
        if (c < 0) return Double.parseDouble(s);
        return Integer.parseInt(s.substring(0, c)) * 60 + Double.parseDouble(s.substring(c + 1));
    }

    /** Sekunden → „m:ss“. */
    public static String zeit(double s) {
        int m = (int) (s / 60);
        double r = s - m * 60;
        return Math.abs(r - Math.rint(r)) < 1e-6 ? String.format(Locale.ROOT, "%d:%02d", m, (int) Math.rint(r)) : String.format(Locale.ROOT, "%d:%04.1f", m, r);
    }

    static LocalDate datum(String s) {
        String[] d = s.split("\\.");
        if (d.length != 3) throw new IllegalArgumentException("Datum TT.MM.JJJJ erwartet");
        return LocalDate.of(Integer.parseInt(d[2]), Integer.parseInt(d[1]), Integer.parseInt(d[0]));
    }

    /** „uhr 10:06“, „uhr 01.05. 00:30“, „abfahrt“, „ankunft“, „meer“. */
    static Warte warte(String a, LocalDate day) {
        String[] p = a.strip().split("\\s+");
        String w = p[0].toLowerCase(Locale.ROOT);
        if (!WARTEN.contains(w)) throw new IllegalArgumentException("warte " + String.join("|", WARTEN) + " erwartet");
        if (!w.equals("uhr")) return new Warte(w, Double.NaN);
        if (day == null) throw new IllegalArgumentException("warte uhr vor dem ersten zeit");
        LocalDate d = day;
        String hm;
        if (p.length == 3) {
            String[] dm = p[1].split("\\.");
            d = LocalDate.of(day.getYear(), Integer.parseInt(dm[1]), Integer.parseInt(dm[0]));
            if (d.isBefore(day)) d = d.plusYears(1);
            hm = p[2];
        } else if (p.length == 2) hm = p[1];
        else throw new IllegalArgumentException("warte uhr [TT.MM.] hh:mm erwartet");
        double h = sekunden(hm) / 60;
        int sec = (int) Math.round(Math.max(0, Math.min(86_399, h * 3600)));
        long ms = java.time.ZonedDateTime.of(d, java.time.LocalTime.ofSecondOfDay(sec), java.time.ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli();
        return new Warte("uhr", ms / 3_600_000.0);
    }

    static Zeile zeile(String line) {
        String cmd = line, txt = "";
        int bar = line.indexOf(" | ");
        if (bar >= 0) { cmd = line.substring(0, bar).strip(); txt = line.substring(bar + 3).strip(); }
        String[] p = cmd.split("\\s+", 3);
        if (p.length < 2) throw new IllegalArgumentException("Zeit und Befehl erwartet");
        double t = sekunden(p[0]);
        String b = p[1].toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ü", "ue").replace("ö", "oe");
        if (!BEFEHLE.contains(b)) throw new IllegalArgumentException("unbekannter Befehl „" + p[1] + "“, bekannt sind " + String.join(", ", BEFEHLE));
        return new Zeile(t, b, p.length > 2 ? p[2].strip() : "", txt);
    }

    private static double zahl(String s) { return Double.parseDouble(s.replace(',', '.')); }

    /** Was ein Befehl tut; prüft die Argumente schon beim Lesen. */
    static Consumer<Ctx> handlung(Zeile z, Scenario sc, String buch) {
        String a = z.argumente();
        switch (z.befehl()) {
            case "zeit": {
                String[] p = a.split("\\s+");
                if (p.length != 2) throw new IllegalArgumentException("zeit TT.MM.JJJJ hh:mm erwartet");
                LocalDate date = datum(p[0]);
                double h = sekunden(p[1]) / 60;
                return x -> x.time(date, h);
            }
            case "raffer": { double v = zahl(a); return x -> x.lapse(v); }
            case "wetter": {
                if (a.isEmpty()) {
                    if (sc == null) throw new IllegalArgumentException("wetter ohne Namen und ohne Zeile „Wetter:“ im Kopf");
                    return x -> x.weather(null, 0, sc);
                }
                // „wetter Raueislage“ oder „wetter Raueislage +10“ (Stunden nach dem Beginn)
                String n = a, st = "0";
                int plus = a.lastIndexOf(" +");
                if (plus > 0) { n = a.substring(0, plus).strip(); st = a.substring(plus + 2).strip(); }
                String name = n;
                double stunde = zahl(st);
                return x -> x.weather(name, stunde, null);
            }
            case "lage": {
                String[] p = a.split("\\s+");
                Weather.Phase ph = Weather.Phase.valueOf(p[0].toUpperCase(Locale.ROOT));
                double v = p.length > 1 && !p[1].equals("-") ? zahl(p[1]) : Double.NaN;
                double deg = p.length > 2 && !p[2].equals("-") ? zahl(p[2]) : Double.NaN;
                return x -> x.weatherFree(ph, v, deg);
            }
            case "ansicht": { need(a, "ansicht Name"); return x -> x.view(a); }
            case "kamera": {
                String[] p = a.split("\\s+");
                if (p.length != 5) throw new IllegalArgumentException("kamera x z Kurs Neigung Höhe erwartet");
                double[] v = new double[5];
                for (int i = 0; i < 5; i++) v[i] = zahl(p[i]);
                return x -> x.place(v[0], v[1], v[2], v[3], v[4]);
            }
            case "fahrt": { need(a, "fahrt Name"); return x -> x.fahrt(a); }
            case "mitfahren": { double v = a.isEmpty() ? 0 : zahl(a); return x -> x.mitfahren(v); }
            case "blick": { need(a, "blick Grad"); double v = zahl(a); return x -> x.blick(v); }
            case "sonne": {
                String[] p = a.split("\\s+");
                boolean gegen = p[0].toLowerCase(Locale.ROOT).startsWith("gegen");
                if (!gegen && !p[0].equalsIgnoreCase("hin")) throw new IllegalArgumentException("sonne gegenpunkt|hin [Grad] erwartet");
                double extra = p.length > 1 ? zahl(p[1]) : 0;
                return x -> x.sonne(gegen, extra);
            }
            case "treiben": { double v = a.isEmpty() ? 40 : zahl(a); return x -> x.treiben(v); }
            case "meer": {
                if (a.equalsIgnoreCase("regie")) return Ctx::meerRegie;
                String[] p = a.split("\\s+");
                if (p.length != 2) throw new IllegalArgumentException("meer Basis Abstand oder meer regie erwartet");
                double b = zahl(p[0]), u = zahl(p[1]);
                return x -> x.meer(b, u);
            }
            case "raueis": {
                double v = a.equalsIgnoreCase("regie") ? Double.NaN : zahl(a);
                return x -> x.raueis(v);
            }
            case "walpurgis": { boolean on = an(a, "walpurgis"); return x -> x.walpurgis(on); }
            case "foto": { need(a, "foto Unterschrift"); return x -> x.capture(a); }
            case "blatt": { String n = a.isEmpty() ? buch : a; return x -> x.sheet(n); }
            case "sage": { need(a, "sage Text"); return x -> x.say(a); }
            default: throw new IllegalArgumentException(z.befehl());
        }
    }

    private static boolean an(String a, String befehl) {
        String v = a.strip().toLowerCase(Locale.ROOT);
        if (v.equals("an")) return true;
        if (v.equals("aus")) return false;
        throw new IllegalArgumentException(befehl + " an|aus erwartet");
    }

    private static void need(String a, String form) { if (a.isEmpty()) throw new IllegalArgumentException(form + " erwartet"); }
}
