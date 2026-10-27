package com.dan.brocken.scene;

import com.dan.rail.Fahrzeug;
import com.dan.rail.Track;
import com.dan.rail.Train;
import com.dan.smoke.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Phase 13: der Betrieb der Brockenbahn nach dem {@link Fahrplan} auf der {@link Strecke}.
 * <ul>
 * <li><b>Züge</b>: Dampflok der Baureihe 99.23–24 mit fünf Wagen ({@link Train#brockenzug}), Fahrdynamik aus
 * {@code com.dan.rail}. Bergfahrten kommen vom Ostrand (Richtung Drei Annen Hohne) so früh, dass sie vor ihrer
 * Abfahrt in Schierke stehen; Talfahrten beginnen am Brocken (mit dem Zug, der dort steht, sonst einem neuen) und
 * verlassen die Szene am Ostrand. Bergab höchstens 30 km/h [G].</li>
 * <li><b>Eingleisig mit Kreuzungen</b>: drei Abschnitte (Ostrand–Schierke, Schierke–Goetheweg, Goetheweg–Brocken), in
 * jedem höchstens ein Zug. Ein Zug fährt aus einem Halt erst, wenn der nächste Abschnitt frei ist ({@link Train.Signal});
 * Talfahrten nehmen in Schierke und Goetheweg das Ausweichgleis und warten in Goetheweg so lange, dass sie zur
 * Abfahrt in Schierke ankommen – dort kreuzen sie die Bergfahrt. Der Bahnhof Brocken zählt zum letzten Abschnitt.</li>
 * <li><b>Uhr</b>: der Betrieb läuft in Szenenzeit; springt die Uhr (Start, Regiebuch, Zeitregler), rechnet er vom
 * Morgen des Tages bis zur neuen Zeit nach (Schritte zu 0,5 s).</li>
 * <li><b>Dampf</b>: je Lok eine Quelle für {@code com.dan.smoke} am Schornstein; Wärme, Menge und Schwärze nach der
 * Last (bei voller Kraft dunkel, rollend weiß), 40 s Lebensdauer [G]. Der Wind am Schornstein ist der Wind relativ zur
 * Lok ({@link #windRelativ}).</li>
 * <li><b>Pfiff</b> beim Abfahren und vor Bahnübergängen (150 m davor) als Meldung.</li>
 * </ul>
 */
public final class Zugbetrieb {
    /** Beginn des Betriebstags (s des Tages) und Ende (alle Züge fort). */
    public static final int MORGEN = 8 * 3600, NACHT = 21 * 3600;
    /** So viel früher als nötig kommt eine Bergfahrt in Schierke an (s); so früh steht eine Talfahrt am Brocken bereit (s). */
    public static final double VORLAUF_PUFFER = 480, BEREIT = 900;
    public static final double V_TAL = 30 / 3.6;
    /** Schritt der Rechnung (s). */
    public static final double SCHRITT = 0.5;

    public final Strecke strecke;
    public final Track gleis;
    /** Die Züge in der Szene (der Bildlauf liest, nur {@link #step} schreibt). */
    public final List<Train> zuege = new CopyOnWriteArrayList<>();
    private final Map<Train, Fahrplan.Fahrt> plan = new HashMap<>();
    private final Train[] belegt = new Train[3];
    private final List<Fahrplan.Fahrt> offen = new ArrayList<>();
    private final double sS, sG, sB;
    private final double vorlauf, talGS;
    private long tag = Long.MIN_VALUE;
    private double uhr = Double.NaN;
    private boolean nachrechnen;
    /** Ankünfte der Bergfahrten: Plan ab, Plan an, ist ab Schierke, ist an Brocken (s des Tages). */
    public final List<double[]> ankuenfte = new CopyOnWriteArrayList<>();
    /** Phase 15: Pfiffe seit dem letzten Abholen {x, y, z, Dauer s} (der Klang holt sie ab; beim Nachrechnen keine). */
    public final java.util.Queue<double[]> pfiffe = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final Map<Train, Double> abSchierke = new HashMap<>();
    /**
     * Phase 17: je Fahrt ein Bericht für die Chronik (BR_ZUGFAHRT), sobald der Zug am Ziel ist: Name, bergauf, Plan
     * ab und an, ist ab und an (s des Tages), Pfiffe; nachgerechnete Fahrten (Sprung der Uhr) kommen nicht hinein.
     */
    public record Bericht(long tag, String zug, boolean bergauf, double planAb, double planAn, double istAb, double istAn, int pfiffe) { }
    /** Phase 17: die Berichte, bis die Chronik sie abholt (höchstens 64). */
    public final java.util.Queue<Bericht> berichte = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final Map<Train, Double> abfahrt = new HashMap<>();
    private final Map<Train, Integer> pfiffZahl = new HashMap<>();
    private volatile String meldung = "";
    private volatile double meldungUhr = -1e9;
    private volatile double[][] loks = new double[0][];
    private long rechenNs;

    public Zugbetrieb(Strecke s) {
        this.strecke = s;
        this.gleis = s.gleis;
        sS = s.sSchierke;
        sG = s.sGoetheweg;
        sB = s.sBrocken;
        vorlauf = probeVorlauf();
        talGS = sG > 0 ? probeTal() : 0;
    }

    /** Ist der Betrieb möglich (Schierke und Brocken auf dem Gleis)? */
    public boolean ok() { return sS > 0 && sB > sS; }

    // ------------------------------------------------------------------ Halte

    double haltSchierke(int r) { return sS + r * 45; }
    double haltGoethe(int r) { return sG + r * 50; }
    double haltBrocken() { return Math.min(gleis.length() - 0.5, sB + 40); }

    int abschnitt(double s) { return s < sS ? 0 : sG > 0 && s < sG ? 1 : 2; }

    /** Fahrzeit vom Ostrand bis zum Halt in Schierke (s), aus einer Probefahrt. */
    double probeVorlauf() {
        if (!(sS > 0)) return 0;
        Train t = Train.brockenzug("Probe");
        t.s = t.laenge + 1;
        t.halte.add(new Train.Halt(haltSchierke(1), 0, "Schierke"));
        Train.Halt[] h = {null};
        t.onHalt = (z, hh, sek) -> h[0] = hh;
        for (int i = 0; i < 20000 && h[0] == null && t.faehrt; i++) t.step(gleis, SCHRITT);
        return t.zeit();
    }

    /** Fahrzeit bergab von Goetheweg bis Schierke (s). */
    double probeTal() {
        Train t = Train.brockenzug("Probe");
        t.vMax = V_TAL;
        t.richtung = -1;
        t.s = haltGoethe(-1);
        t.halte.add(new Train.Halt(haltSchierke(-1), 0, "Schierke"));
        Train.Halt[] h = {null};
        t.onHalt = (z, hh, sek) -> h[0] = hh;
        for (int i = 0; i < 20000 && h[0] == null && t.faehrt; i++) t.step(gleis, SCHRITT);
        return t.zeit();
    }

    // ------------------------------------------------------------------ Uhr

    /**
     * Je Bild: Tag (fortlaufend) und Sekunde des Tages der Szenenuhr. Läuft die Uhr normal, rechnet der Betrieb bis
     * dorthin weiter; springt sie zurück oder auf einen anderen Tag, beginnt er am Morgen und rechnet nach.
     */
    public void step(long tagNr, double sekunde) {
        if (!ok()) return;
        long t0 = System.nanoTime();
        if (tagNr != tag || Double.isNaN(uhr) || sekunde < uhr - 1) {
            neu(tagNr);
            nachrechnen = true;
        } else nachrechnen = sekunde - uhr > 120;
        double ziel = Math.max(sekunde, MORGEN);
        while (uhr < ziel - 1e-9) tick(Math.min(SCHRITT, ziel - uhr));
        nachrechnen = false;
        loks = lokDaten();
        rechenNs = System.nanoTime() - t0;
    }

    private void neu(long tagNr) {
        tag = tagNr;
        uhr = MORGEN;
        zuege.clear();
        plan.clear();
        abSchierke.clear();
        abfahrt.clear();
        pfiffZahl.clear();
        ankuenfte.clear();
        pfiffe.clear();
        java.util.Arrays.fill(belegt, null);
        offen.clear();
        offen.addAll(Fahrplan.fahrten());
    }

    /** Ein Schritt von h Sekunden: Züge einsetzen, fahren, fertige entfernen. */
    void tick(double h) {
        uhr += h;
        einsetzen();
        for (Train t : zuege) if (t.faehrt) t.step(gleis, h);
        if (uhr > NACHT) { zuege.clear(); plan.clear(); java.util.Arrays.fill(belegt, null); }
    }

    private void einsetzen() {
        for (int i = 0; i < offen.size(); i++) {
            Fahrplan.Fahrt f = offen.get(i);
            boolean done = f.bergauf() ? bergfahrt(f) : talfahrt(f);
            if (done) offen.remove(i--);
        }
    }

    private Train neuerZug(Fahrplan.Fahrt f) {
        Train t = Train.brockenzug(f.name());
        t.zeit(uhr);
        t.signal = this::frei;
        t.onHalt = this::amHalt;
        t.onPfiff = (z, s, grund) -> pfiff(z, grund);
        t.onAnkunft = this::ankunft;
        plan.put(t, f);
        return t;
    }

    /** Eine Bergfahrt: am Ostrand einsetzen, sobald es Zeit ist und der erste Abschnitt frei. */
    private boolean bergfahrt(Fahrplan.Fahrt f) {
        if (uhr < f.ab() - vorlauf - VORLAUF_PUFFER) return false;
        if (uhr > f.an() + 1800) return true;          // zu spät (etwa Uhr nach der Ankunft gestellt): fällt aus
        if (belegt[0] != null) return false;
        Train t = neuerZug(f);
        t.s = t.laenge + 1;
        t.halte.add(new Train.Halt(haltSchierke(1), 60, f.ab(), "Schierke"));
        if (sG > 0) t.halte.add(new Train.Halt(haltGoethe(1), 0, "Goetheweg"));
        t.halte.add(new Train.Halt(haltBrocken(), 0, "Brocken"));
        for (double[] ue : strecke.uebergaenge) t.pfiffe.add(new double[]{ue[0] - 150});
        belegt[0] = t;
        zuege.add(t);
        return true;
    }

    /** Eine Talfahrt: am Brocken bereitstellen (mit dem Zug, der dort steht), wenn der letzte Abschnitt frei ist. */
    private boolean talfahrt(Fahrplan.Fahrt f) {
        if (uhr < f.ab() - BEREIT) return false;
        if (uhr > f.an() + 1800) return true;
        Train steht = stehtAmBrocken();
        if (steht == null && belegt[2] != null) return false;   // eine Bergfahrt ist unterwegs zum Brocken
        if (steht != null) { zuege.remove(steht); plan.remove(steht); }
        Train t = neuerZug(f);
        t.vMax = V_TAL;
        t.richtung = -1;
        t.s = haltBrocken() - t.laenge;
        t.versatz = strecke::versatz;
        t.halte.add(new Train.Halt(t.s, 0, f.ab(), "Brocken"));
        if (sG > 0) t.halte.add(new Train.Halt(haltGoethe(-1), 0, f.an() - talGS - 120, "Goetheweg"));
        t.halte.add(new Train.Halt(haltSchierke(-1), 60, f.an(), "Schierke"));
        for (int k = strecke.uebergaenge.size() - 1; k >= 0; k--) t.pfiffe.add(new double[]{strecke.uebergaenge.get(k)[1] + 150});
        belegt[2] = t;
        zuege.add(t);
        return true;
    }

    /** Der Zug, der am Brocken angekommen ist und steht (null: keiner). */
    Train stehtAmBrocken() {
        for (Train t : zuege) if (!t.faehrt && t.richtung > 0) return t;
        return null;
    }

    /** Das Signal: in den nächsten Abschnitt nur, wenn ihn kein anderer Zug hat. */
    boolean frei(Train t, Train.Halt h) {
        int n = abschnitt(h.s() + t.richtung * 2);
        if (belegt[n] != null && belegt[n] != t) return false;
        belegt[n] = t;
        // fährt eine Bergfahrt in den letzten Abschnitt, wird der Zug, der noch am Brocken steht, abgestellt
        if (n == 2 && t.richtung > 0) {
            Train steht = stehtAmBrocken();
            if (steht != null) { zuege.remove(steht); plan.remove(steht); }
        }
        if (t.richtung > 0 && "Schierke".equals(h.name())) abSchierke.put(t, uhr);
        if (t.richtung < 0 && "Brocken".equals(h.name()) && !nachrechnen) abfahrt.put(t, uhr);
        return true;
    }

    void amHalt(Train t, Train.Halt h, double sek) {
        for (int k = 0; k < belegt.length; k++) if (belegt[k] == t) belegt[k] = null;
        if (t.richtung < 0 && "Brocken".equals(h.name())) belegt[2] = t;
    }

    void ankunft(Train t, double sek) {
        for (int k = 0; k < belegt.length; k++) if (belegt[k] == t) belegt[k] = null;
        Fahrplan.Fahrt f = plan.get(t);
        double ab = t.richtung > 0 ? abSchierke.getOrDefault(t, Double.NaN) : abfahrt.getOrDefault(t, Double.NaN);
        if (f != null && !nachrechnen && !Double.isNaN(ab) && berichte.size() < 64)
            berichte.add(new Bericht(tag, f.name(), t.richtung > 0, f.ab(), f.an(), ab, sek, pfiffZahl.getOrDefault(t, 0)));
        pfiffZahl.remove(t);
        abfahrt.remove(t);
        if (t.richtung > 0) {
            if (f != null) ankuenfte.add(new double[]{f.ab(), f.an(), abSchierke.getOrDefault(t, Double.NaN), sek});
        } else {
            zuege.remove(t);
            plan.remove(t);
        }
    }

    void pfiff(Train t, String grund) {
        if (nachrechnen) return;
        pfiffZahl.merge(t, 1, Integer::sum);
        // Phase 15: für den Klang (Schornstein, Dauer des Tons in s; Abfahrt mäßig lang, vor Übergängen lang [G])
        if (pfiffe.size() < 16) {
            double[] p = Fahrzeug.schlot(gleis, t);
            pfiffe.add(new double[]{p[0], p[1], p[2], "Abfahrt".equals(grund) ? 1.6 : 2.6});
        }
        meldung = String.format(Locale.GERMAN, "Pfiff · %s · %s %s", t.name, grund, Fahrplan.uhr(uhr));
        meldungUhr = uhr;
    }

    // ------------------------------------------------------------------ Dampf und Wind

    /** Je Lok {x, y, z, vx, vz, Last, steht (0/1)} am Schornstein. */
    private double[][] lokDaten() {
        List<double[]> l = new ArrayList<>();
        for (Train t : zuege) {
            double[] p = Fahrzeug.schlot(gleis, t);
            Track.Pose q = gleis.at(t.s);
            double h = Math.toRadians(q.heading()), v = t.v * t.richtung;
            l.add(new double[]{p[0], p[1], p[2], Math.sin(h) * v, -Math.cos(h) * v, t.last(), t.faehrt && t.v > 0.05 ? 0 : 1});
        }
        return l.toArray(new double[0][]);
    }

    /** Die Dampfquellen der Loks für {@code com.dan.smoke} [G]. */
    public List<Source> dampf() {
        List<Source> l = new ArrayList<>();
        double[][] d = loks;
        for (int i = 0; i < d.length; i++) {
            double last = d[i][5];
            boolean steht = d[i][6] > 0.5;
            double heat = steht ? 120 : 250 + 1250 * last, amount = steht ? 0.3 : 0.4 + 0.6 * last, black = steht ? 0.04 : 0.05 + 0.5 * last * last;
            l.add(new Source(d[i][0], d[i][1], d[i][2], heat, amount, black, 9923 + i, 40));
        }
        return l;
    }

    /**
     * Wind am Ort, relativ zur Lok, wenn der Ort ein Schornstein ist (bis 1 m): so liegt die Fahne einer fahrenden Lok
     * hinter ihr, die einer stehenden geht mit dem Wind.
     */
    public double[] windRelativ(double[] wind, double x, double y, double z) {
        for (double[] d : loks)
            if (Math.abs(d[0] - x) < 1 && Math.abs(d[2] - z) < 1 && Math.abs(d[1] - y) < 1)
                return new double[]{wind[0] - d[3], wind[1], wind[2] - d[4]};
        return wind;
    }

    // ------------------------------------------------------------------ Mitfahren und Anzeige

    /** Der Zug für die Mitfahrt: der fahrende, der dem Ort am nächsten ist, sonst der nächste stehende. */
    public Train mitfahrZug(double x, double z) {
        Train best = null;
        double bd = Double.MAX_VALUE;
        for (int pass = 0; pass < 2 && best == null; pass++)
            for (Train t : zuege) {
                if (pass == 0 && (!t.faehrt || t.haelt())) continue;
                Track.Pose p = gleis.at(t.s);
                double d = Math.hypot(p.x() - x, p.z() - z);
                if (d < bd) { bd = d; best = t; }
            }
        return best;
    }

    /** Für die Kamera: {x, y, z, Kurs, Stampfen, Rollen} des Lokführers, der sich aus dem Führerhaus lehnt. */
    public double[] fuehrerstand(Train t) {
        double[] e = Fahrzeug.fuehrerstand(gleis, t), n = Fahrzeug.neigung(gleis, t);
        return new double[]{e[0], e[1], e[2], e[3], n[0], n[1]};
    }

    /** Die letzte Meldung (Pfiff), solange sie frisch ist (2 min Szenenzeit), sonst leer. */
    public String meldung() { return uhr - meldungUhr < 120 ? meldung : ""; }

    public double uhr() { return uhr; }
    public double vorlauf() { return vorlauf; }
    public double talGS() { return talGS; }
    public double rechenMs() { return rechenNs / 1e6; }

    /** „Züge: Bergfahrt 10:06 ab Schierke · 4,2 km · 24 km/h · volle Kraft; … · nächste Abfahrt …“ */
    public String describe() {
        if (!ok()) return "Züge: kein Betrieb (Bahnhöfe fehlen)";
        StringBuilder sb = new StringBuilder();
        for (Train t : zuege) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(String.format(Locale.GERMAN, "%s · km %.1f · %.0f km/h · %s", t.name, t.s / 1000, t.v * 3.6, t.faehrt ? t.zustand() : "steht am Brocken"));
        }
        String next = "";
        for (Fahrplan.Fahrt f : Fahrplan.fahrten()) if (f.ab() > uhr) { next = " · nächste: " + f.name() + " " + Fahrplan.uhr(f.ab()); break; }
        return "Züge: " + (sb.length() == 0 ? "keiner unterwegs" : sb) + next;
    }
}
