package com.dan.brocken.wiring;

import com.dan.weather.Weather;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Phase 5: der Wind der Regie gegen die Stundenwerte der Wetterwarte Brocken (DWD, Station 722, Windgeber 27 m über
 * Grund auf 1 134,7 m; Produkt „stundenwerte_FF“, Jahre 1991–2020, 258 225 Stunden). Die Regie gibt den Wind an der
 * Station in Höhe des Gebers ({@link #GEBER}); das Windfeld wird darauf geeicht ({@code Wiring.feedWind}).
 * <p>
 * Die Lagen und ihre Zielwerte stehen in {@link KlimaBrocken}. Hier stehen die Zahlen der Station, der Jahresgang
 * ({@link #JAHR}, Faktoren auf den Wind der Lagen je Monat, geeicht mit {@code tools.KlimaEichung}) und die Streuung von
 * Abschnitt zu Abschnitt. {@link #simulieren} lässt die freie Regie Stunde für Stunde laufen und misst wie die Station.
 */
public final class WindBrocken {
    private WindBrocken() { }

    /** Höhe des Windgebers über Grund (m) und Ort der Wetterwarte (Grad). */
    public static final double GEBER = 27, LAT = 51.7986, LON = 10.6183;

    /** Monatsmittel der Station (m/s), 1991–2020. */
    public static final double[] MONAT = {13.99, 13.01, 11.62, 9.42, 8.93, 8.67, 8.64, 8.57, 9.72, 11.53, 12.20, 13.67};
    /** Mittel und Standardabweichung der Stundenwerte (m/s). */
    public static final double MITTEL = 10.81, SD = 5.57;
    /** Perzentile 10, 50, 90 und 99 % (m/s; Klassen zu 1 m/s, ± 0,5). */
    public static final double[] PERZENTIL = {4.5, 10.5, 18.5, 26.5};
    /** Anteil der Richtungen in zwölf Sektoren zu 30° um 0°, 30° … 330° (%), ohne Stille. */
    public static final double[] SEKTOR = {4.9, 3.8, 4.2, 5.2, 4.1, 4.2, 7.3, 13.8, 19.3, 16.8, 9.5, 6.9};
    /** Mittlerer Wind je Sektor (m/s). */
    public static final double[] SEKTOR_MITTEL = {7.7, 7.4, 8.0, 8.4, 8.6, 8.5, 8.6, 13.0, 13.9, 12.1, 9.8, 8.9};
    /** Tagesgang: Mittel um 3 Uhr und um 15 Uhr MEZ (m/s); am Gipfel nachts etwas mehr Wind. */
    public static final double NACHT = 11.10, NACHMITTAG = 10.33;

    /**
     * Faktoren auf den Wind der Lagen je Monat ({@code Weather.windYear}), so geeicht, dass die freie Regie im Mittel
     * über 30 Jahre die Monatsmittel der Station trifft ({@code tools.KlimaEichung}, Würfel 1 bis 3).
     */
    public static final double[] JAHR = {1.455, 1.309, 1.190, 0.919, 0.828, 0.817, 0.819, 0.798, 0.959, 1.198, 1.247, 1.465};
    /** Streuung des Winds (Logarithmus) und der Richtung (Grad) von Abschnitt zu Abschnitt [G]. */
    public static final double STREUUNG = 0.18, RICHTUNG = 35;

    /** Setzt Jahresgang und Streuung in eine Regie (von {@link KlimaBrocken#setzen} gerufen). */
    public static void setzen(Weather w, double[] jahr) {
        w.windYear = (jahr != null ? jahr : JAHR).clone();
        w.windSpread = STREUUNG;
        w.directionSpread = RICHTUNG;
    }

    /** Was die Regie über Jahre ergibt, gemessen wie die Station. */
    public record Lauf(double[] monat, double mittel, double sd, double[] perzentil, double[] sektor, double[] sektorMittel, double stunden) {
        /** Größte Abweichung der Monatsmittel (m/s). */
        public double fehlerMonat() {
            double f = 0;
            for (int m = 0; m < 12; m++) f = Math.max(f, Math.abs(monat[m] - MONAT[m]));
            return f;
        }

        /** Größte Abweichung der Anteile je Sektor (Prozentpunkte). */
        public double fehlerSektor() {
            double f = 0;
            for (int k = 0; k < 12; k++) f = Math.max(f, Math.abs(sektor[k] - SEKTOR[k]));
            return f;
        }

        /** Anteil von Südwest bis Nordwest (210° bis 300°) in % – an der Station 47,8 %. */
        public double west() { return sektor[8] + sektor[9] + sektor[10] / 2 + sektor[7] / 2; }

        public String zeile() {
            StringBuilder b = new StringBuilder();
            for (int m = 0; m < 12; m++) b.append(m > 0 ? " " : "").append(String.format(Locale.GERMAN, "%.1f", monat[m]));
            return b.toString();
        }

        public String sektoren() {
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < 12; k++) b.append(k > 0 ? " " : "").append(String.format(Locale.GERMAN, "%.0f", sektor[k]));
            return b.toString();
        }
    }

    /** Die freie Regie {@code jahre} Jahre ab 2001, stündlich, Würfel {@code seed}; jahr: Faktoren (null: {@link #JAHR}). */
    public static Lauf simulieren(int jahre, long seed, double[] jahr) {
        Weather w = new Weather();
        KlimaBrocken.setzen(w);
        setzen(w, jahr);
        w.seed = seed;
        ZoneId z = ZoneId.of("Europe/Berlin");
        double t0 = LocalDate.of(2001, 1, 1).atStartOfDay(z).toEpochSecond(), t1 = LocalDate.of(2001 + jahre, 1, 1).atStartOfDay(z).toEpochSecond();
        w.startFree(t0, Weather.Phase.KLAR);
        double[] sum = new double[12], n = new double[12], sek = new double[12], sekSum = new double[12];
        int[] hist = new int[61];
        double s1 = 0, s2 = 0, all = 0, dirN = 0;
        for (double t = t0; t < t1; t += 3600) {
            Weather.Snapshot s = w.update(t, 1.0);
            int m = java.time.Instant.ofEpochSecond((long) t).atZone(z).getMonthValue() - 1;
            double v = s.wind();
            sum[m] += v;
            n[m]++;
            s1 += v;
            s2 += v * v;
            all++;
            hist[Math.min(60, (int) Math.floor(v))]++;
            if (v > 0.3) {
                int k = (int) Math.floor(((s.fromDeg() + 15) % 360 + 360) % 360 / 30) % 12;
                sek[k]++;
                sekSum[k] += v;
                dirN++;
            }
        }
        double[] mon = new double[12], sh = new double[12], sm = new double[12];
        for (int m = 0; m < 12; m++) mon[m] = sum[m] / n[m];
        for (int k = 0; k < 12; k++) { sh[k] = 100 * sek[k] / Math.max(1, dirN); sm[k] = sekSum[k] / Math.max(1, sek[k]); }
        double mean = s1 / all, sd = Math.sqrt(Math.max(0, s2 / all - mean * mean));
        double[] q = {0.1, 0.5, 0.9, 0.99}, p = new double[4];
        for (int i = 0; i < 4; i++) {
            double c = 0;
            for (int b = 0; b < hist.length; b++) { c += hist[b]; if (c >= q[i] * all) { p[i] = b + 0.5; break; } }
        }
        return new Lauf(mon, mean, sd, p, sh, sm, all);
    }

    /** Mittel mehrerer Läufe (Würfel 1 bis k). */
    public static Lauf mittel(int jahre, int wuerfel, double[] jahr) {
        double[] mon = new double[12], p = new double[4], sh = new double[12], sm = new double[12];
        double mean = 0, sd = 0, h = 0;
        for (int k = 1; k <= wuerfel; k++) {
            Lauf l = simulieren(jahre, k, jahr);
            for (int m = 0; m < 12; m++) mon[m] += l.monat[m] / wuerfel;
            for (int i = 0; i < 4; i++) p[i] += l.perzentil[i] / wuerfel;
            for (int i = 0; i < 12; i++) { sh[i] += l.sektor[i] / wuerfel; sm[i] += l.sektorMittel[i] / wuerfel; }
            mean += l.mittel / wuerfel;
            sd += l.sd / wuerfel;
            h += l.stunden;
        }
        return new Lauf(mon, mean, sd, p, sh, sm, h);
    }
}
