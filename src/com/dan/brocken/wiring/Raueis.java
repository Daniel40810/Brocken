package com.dan.brocken.wiring;

import com.dan.rime.Rime;
import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.util.Locale;

/**
 * Phase 10: das Eis am Berg aus {@code com.dan.rime}, in Höhenbändern zu 50 m von 600 bis 1 150 m, je Band drei
 * Objekte:
 * <ul>
 * <li><b>Zweig</b> (4 mm, rau): Nadeln und dürre Zweige der Fichten, Krüppelfichten, Heide – die Fahnen am Wald;</li>
 * <li><b>Mast</b> (25 cm, rau): Masten, Geländer, Schilder, der Sendeturm von unten – Kanten und Schrauben fangen;</li>
 * <li><b>Fels</b> (2 m, rau): Granit, Blöcke, Mauern, der Boden zwischen ihnen.</li>
 * </ul>
 * Die Lage je Band aus der Regie ({@link Weather.Snapshot}): Temperatur nach der Schichtung, Flüssigwasser nach der
 * Höhe über der Wolkenbasis [G: in einer Stauwolke von rund 0,1 g/m³ an der Basis bis 0,4 g/m³ 300 m darüber,
 * adiabatischer Anstieg], Wind nach der Höhe (am Gipfel der Wind der Regie, in 600 m gut die Hälfte [G]), Richtung
 * der Regie, Tröpfchen wie für die Glorie ({@link Wiring#tropfen}, Durchmesser = 2 · Halbmesser), Sonne nach Höhe und
 * Bedeckung. Gerechnet wird in Szenenzeit; springt die Uhr, rechnet {@link #vorlauf} ein Regiebuch von seinem Beginn
 * bis jetzt durch.
 */
public final class Raueis {
    public static final double UNTEN = 600, OBEN = 1150, SCHRITT = 50;
    public static final int BAENDER = (int) Math.round((OBEN - UNTEN) / SCHRITT) + 1;
    public static final int ZWEIG = 0, MAST = 1, FELS = 2;
    public static final String[] NAMEN = {"Zweig", "Mast", "Fels"};
    public static final double[] DURCHMESSER = {0.004, 0.25, 2.0};
    /** Zweige in der Krone, die im Wind stehen (m) [G]: Krüppelfichte von 4 m an der Baumgrenze. */
    public static final double ZWEIGMETER = 60;

    public final Rime rime = new Rime();
    /** ansatz[band][objekt] */
    public final Rime.Ansatz[][] ansatz = new Rime.Ansatz[BAENDER][3];
    /** Von Hand (m, am Gipfel, Raueis aus der Richtung der Regie); NaN = nach der Regie. */
    public volatile double hand = Double.NaN;
    /** Eis, das zuletzt fiel: Anzahl Fahnen, kg je Meter, Höhe, Zeit (s seit 1970). */
    public volatile int gefallen;
    public volatile double gefallenKg, gefallenHoehe = Double.NaN, gefallenUm = Double.NaN;
    private double uhr = Double.NaN;
    private double fromDeg = 270;

    public Raueis() {
        rime.seed = 1141;
        rime.quality = Rime.Quality.LOW;
        leeren();
        rime.onAbfall = (a, kg) -> {
            for (int b = 0; b < BAENDER; b++)
                for (int k = 0; k < 3; k++)
                    if (ansatz[b][k] == a) gefallenHoehe = hoehe(b);
            gefallen++;
            gefallenKg = kg;
            gefallenUm = uhr;
        };
    }

    /** Alles Eis weg (Sommer, neue Lage ohne Vorlauf). */
    public void leeren() {
        for (int b = 0; b < BAENDER; b++)
            for (int k = 0; k < 3; k++) ansatz[b][k] = new Rime.Ansatz(DURCHMESSER[k], true);
    }

    public static double hoehe(int band) { return UNTEN + band * SCHRITT; }

    /**
     * Die Lage in der Höhe h aus einem Stand der Regie. tropfenUm: mittlerer Durchmesser (µm); sunAlt: Sonnenhöhe (°).
     */
    public static Rime.Lage lage(Weather.Snapshot s, double h, double tropfenUm, double sunAlt) {
        Rime.Lage l = new Rime.Lage();
        l.tempC = s.temperatureAt(h);
        double in = s.cloudAt(h);
        boolean nass = s.phase() == Weather.Phase.RAUEIS || s.phase() == Weather.Phase.STAU || s.phase() == Weather.Phase.NEBEL
                || s.phase() == Weather.Phase.FRONT || s.phase() == Weather.Phase.INVERSION;
        double ueber = Double.isNaN(s.cloudBase()) ? 0 : Math.max(0, h - s.cloudBase());
        l.lwc = nass ? in * Math.min(0.4, 0.1 + ueber * 0.001) : in * 0.05;
        // Nieselregen und Regen bei leichtem Frost (bis −4 °C; kälter fällt Schnee [G]): große Tropfen, Klareis;
        // 1 mm/h Niesel sind rund 0,08 g/m³ [G]
        if (s.rain() > 0.2 && l.tempC < 0 && l.tempC > -4) { l.lwc = Math.max(l.lwc, 0.08 * s.rain()); l.tropfenUm = 300; }
        else l.tropfenUm = tropfenUm;
        l.wind = s.wind() * (0.55 + 0.45 * smooth(UNTEN, 1141, h));
        l.fromDeg = s.fromDeg();
        l.sonne = sunAlt > 0 ? 900 * Math.sin(Math.toRadians(sunAlt)) * (1 - 0.85 * Math.min(1, s.oktas() / 8)) * (1 - in) : 0;
        l.feuchte = s.humidity() >= 97 ? 1 : 0.9;
        return l;
    }

    /** Einen Schritt in Szenenzeit (s). */
    public void step(Weather.Snapshot s, double dt, double tropfenUm, double sunAlt, double nowEpoch) {
        uhr = nowEpoch;
        if (dt <= 0 || s == null || !s.hasHeight()) return;
        fromDeg = s.fromDeg();
        for (int b = 0; b < BAENDER; b++) {
            Rime.Lage l = lage(s, hoehe(b), tropfenUm, sunAlt);
            for (int k = 0; k < 3; k++) rime.step(ansatz[b][k], l, dt);
        }
    }

    /**
     * Ein Regiebuch von seinem Beginn (s seit 1970) bis jetzt durchrechnen, in Schritten zu 10 min (eine eigene Regie
     * mit dem Klima „Brocken“ und demselben seed; ohne Böen). Gibt die Zahl der Schritte zurück.
     */
    public int vorlauf(Scenario sc, double start, double now, long seed, double tropfenUm, java.util.function.DoubleUnaryOperator sunAltAt) {
        leeren();
        if (now <= start) return 0;
        Weather w = new Weather();
        KlimaBrocken.setzen(w);
        w.seed = seed;
        w.start(sc, start);
        int n = 0;
        int f = gefallen;
        for (double t = start; t < now; t += 600, n++) {
            double dt = Math.min(600, now - t);
            Weather.Snapshot s = w.update(t + dt, 0);
            step(s, dt, tropfenUm, sunAltAt.applyAsDouble(t + dt), t + dt);
        }
        gefallen = f;   // was im Vorlauf fiel, ist nicht zu hören
        return n;
    }

    /** Dicke des Eises (m) am Objekt k in der Höhe h (zwischen den Bändern linear; darunter keins, darüber das oberste). */
    public double dicke(int k, double h) {
        if (!Double.isNaN(hand)) return k == ZWEIG ? hand * smooth(UNTEN + 200, OBEN - 100, h) : hand * 0.6 * smooth(UNTEN + 200, OBEN - 100, h);
        double u = (h - UNTEN) / SCHRITT;
        if (u <= 0) return u < -1 ? 0 : ansatz[0][k].dicke * (1 + u);
        int i = (int) Math.floor(u);
        if (i >= BAENDER - 1) return ansatz[BAENDER - 1][k].dicke;
        double f = u - i;
        return ansatz[i][k].dicke * (1 - f) + ansatz[i + 1][k].dicke * f;
    }

    /** Die Art des Eises am Gipfel (das oberste Band, nach dem Zweig). */
    public Rime.Art art() {
        if (!Double.isNaN(hand)) return hand > 0 ? Rime.Art.RAUEIS : Rime.Art.KEINS;
        for (int b = BAENDER - 1; b >= 0; b--) if (ansatz[b][ZWEIG].art != Rime.Art.KEINS) return ansatz[b][ZWEIG].art;
        return Rime.Art.KEINS;
    }

    /** Woher der Wind kam, gegen den die Fahnen wachsen (Grad): die Richtung des obersten Ansatzes, sonst der Regie. */
    public double fahneVon() {
        Rime.Ansatz a = ansatz[BAENDER - 1][ZWEIG];
        return Double.isNaN(hand) && !Double.isNaN(a.richtung) ? a.richtung : fromDeg;
    }

    /**
     * Höhenband für Pakete, die nur eine Dicke kennen (forest, cliff): {base, full, dicke} – unter base kein Eis (5 % der
     * obersten Dicke), ab full die Dicke (80 %).
     */
    public double[] band(int k) {
        double top = dicke(k, OBEN), base = OBEN, full = OBEN;
        if (top <= 0) return new double[]{1e5, 1e5 + 1, 0};
        for (double h = OBEN; h >= UNTEN - SCHRITT; h -= 10) {
            double d = dicke(k, h);
            if (d >= 0.8 * top) full = h;
            if (d >= 0.05 * top) base = h;
        }
        return new double[]{base, Math.max(base + 10, full), top};
    }

    /** Dicken aller Bänder für den Gelände-Shader (m): [objekt][band]. */
    public float[] dicken(int k) {
        float[] r = new float[BAENDER];
        for (int b = 0; b < BAENDER; b++) r[b] = (float) dicke(k, hoehe(b));
        return r;
    }

    /** Last an einer Krüppelfichte in der Höhe h (kg). */
    public double last(double h) {
        int b = (int) Math.max(0, Math.min(BAENDER - 1, Math.round((h - UNTEN) / SCHRITT)));
        return Rime.baumLast(ansatz[b][ZWEIG], ZWEIGMETER);
    }

    /** Rate am Zweig im obersten Band jetzt (mm/h). */
    public static double rateGipfel(Weather.Snapshot s, double tropfenUm, double sunAlt) {
        return Rime.mmJeStunde(DURCHMESSER[ZWEIG], true, lage(s, 1141, tropfenUm, sunAlt));
    }

    /** „Raueis am Gipfel: Zweig 6,3 cm nach 285°, Mast 4,1 cm, Fels 3,0 cm · ab 820 m · Last 120 kg je Krüppelfichte · jetzt 9 mm/h“ */
    public String zeile(Weather.Snapshot s, double tropfenUm, double sunAlt) {
        if (!Double.isNaN(hand)) return String.format(Locale.GERMAN, "Raueis von Hand: %.0f cm an den Zweigen am Gipfel, Fahnen nach %03.0f°", hand * 100, fahneVon());
        Rime.Art art = art();
        double rate = s != null && s.hasHeight() ? rateGipfel(s, tropfenUm, sunAlt) : 0;
        String jetzt = rate > 0.05 ? String.format(Locale.GERMAN, " · wächst %.1f mm/h", rate) : "";
        String fiel = gefallen > 0 ? String.format(Locale.GERMAN, " · %d Fahnen gefallen, zuletzt %.2f kg/m in %.0f m", gefallen, gefallenKg, gefallenHoehe) : "";
        if (art == Rime.Art.KEINS) return "Raueis: keins" + jetzt + fiel;
        double[] bd = band(ZWEIG);
        Rime.Ansatz z = ansatz[BAENDER - 1][ZWEIG];
        return String.format(Locale.GERMAN, "%s am Gipfel: Zweig %.1f cm%s, Mast %.1f cm, Fels %.1f cm · ab %.0f m · Last %.0f kg je Krüppelfichte%s%s",
                name(art), z.dicke * 100, Double.isNaN(z.richtung) ? "" : String.format(Locale.GERMAN, " nach %03.0f°", z.richtung),
                ansatz[BAENDER - 1][MAST].dicke * 100, ansatz[BAENDER - 1][FELS].dicke * 100, bd[0], last(1141), jetzt, fiel);
    }

    public static String name(Rime.Art a) {
        return switch (a) { case RAUEIS -> "Raueis"; case RAUREIF -> "Raureif"; case KLAREIS -> "Klareis"; default -> "kein Eis"; };
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
