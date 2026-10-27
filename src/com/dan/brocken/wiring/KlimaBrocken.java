package com.dan.brocken.wiring;

import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 3: das Klima „Brocken“ für {@code com.dan.weather} (F22 A). Die Werte der Regie gelten für die Wetterwarte des
 * DWD auf dem Gipfel ({@link #STATION}, Bezugshöhe der Regie); Temperatur, Wolke und Sicht in jeder anderen Höhe rechnet
 * die Regie seit Brocken Phase 3 selbst ({@code Weather.refHeight}, {@code Weather.layers}).
 * <p>
 * <b>Zahlen der Station</b> (DWD, Normalwerte 1991–2020, Brocken 1 134 m): Monatsmittel {@link #DWD}, Jahr 3,9 °C,
 * 1 799 mm Niederschlag, 87 % Feuchte, 1 463 Sonnenstunden; Mittel des Winds rund 42 km/h, an rund 300 Tagen im Jahr
 * Nebel (1895–2019 im Mittel 306,6). Die Lagen und ihre Zielwerte sind gestaltet [G]: am Gipfel windig, oft in der
 * Wolke, im Winter Raueis, bei Hochdruck im Winterhalbjahr Inversionen mit Hochnebel darunter.
 * <p>
 * <b>Eichung:</b> Die Regie legt die Lagen um das Monatsmittel, das sie bekommt; Tagesgang, Wechsel der Lagen und die
 * Jahreszeit verschieben das Mittel der Szene etwas. {@link #EINGANG} ist darum so gewählt, dass das Mittel über 30
 * gewürfelte Jahre die Station trifft ({@code tools.KlimaEichung}; die Prüfung rechnet nach, Werkbuch Phase 3: auf 0,5 K).
 */
public final class KlimaBrocken {
    private KlimaBrocken() { }

    /** Höhe der Wetterwarte (m über NHN): die Bezugshöhe der Regie. */
    public static final double STATION = 1134;

    /** Monatsmittel der Lufttemperatur an der Station (°C, DWD 1991–2020). */
    public static final double[] DWD = {-3.3, -3.3, -1.2, 2.9, 6.8, 9.8, 12.1, 12.0, 8.3, 4.4, 0.6, -2.2};

    /** Mittlerer Monatsniederschlag an der Station (mm, DWD 1991–2020); Jahr 1 799 mm. */
    public static final double[] DWD_NIEDERSCHLAG = {202.9, 145.8, 147.5, 86.9, 120.3, 117.2, 159.3, 131.1, 152.7, 171.0, 163.2, 204.0};

    /** Mittlere Zahl der Nebeltage im Jahr (1895–2019). */
    public static final double DWD_NEBELTAGE = 306.6;

    /**
     * Monatsmittel, die die Regie bekommt (°C): so geeicht, dass die Szene im Mittel {@link #DWD} trifft
     * ({@code tools.KlimaEichung}, 30 Jahre, Würfel 1 bis 3).
     */
    public static final double[] EINGANG = {-3.44, -3.51, -1.69, 2.40, 6.56, 9.47, 12.03, 12.11, 7.83, 3.99, 0.23, -2.48};

    /** Die Namen der Lagen am Brocken (Reihenfolge wie {@link Weather.Phase}): die Regie heißt sie nach der See. */
    public static final String[] NAMEN = {"klar", "aufziehend", "Front (Westlage)", "Sturm", "Gewitter", "abklingend, Schauer", "aufklarend",
            "Talnebel", "Föhn", "Orkan", "Westlage mit Stau", "Inversion", "Raueislage"};

    public static String name(Weather.Phase p) { return p.ordinal() < NAMEN.length ? NAMEN[p.ordinal()] : p.label; }

    /**
     * Zielwerte je Lage am Gipfel (Spalten wie {@link Weather.Var}) [G]. Wind nach dem Mittel der Station (rund
     * 12 m/s) gestaffelt, Richtungen seit Phase 5 so, dass die Sektoren die Station treffen ({@link WindBrocken}); die Sicht gilt unter den Wolken, in der Wolke rechnet die Regie sie aus der Schichtung; die
     * Temperatur liegt wie bei der Regie um 11,5 °C (das Klima verschiebt sie), der Luftdruck auf Meereshöhe.
     */
    public static final double[][] ZIELE = {
            //  Wind  Richt Böig Bed  hoch Wolke Regen Sicht Nebel Druck  Temp Feuchte Dünung Gewitter
            {7.0, 230, 0.18, 2.5, 0.10, 0, 0.0, 60, 0.0, 1025, 13.0, 70, 0, 0},  // klar: Hochdruck, am Gipfel meist aus Südwest (Phase 5, Station)
            {10.0, 200, 0.20, 5.0, 0.70, 1, 0.0, 35, 0.0, 1012, 12.5, 82, 0, 0},  // aufziehend: Cirren, dann Schollen
            {15.0, 245, 0.22, 8.0, 0.20, 3, 0.8, 12, 0.0, 1002, 11.5, 97, 0, 0},  // Front: Landregen aus Westen, Gipfel in der Wolke
            {20.0, 255, 0.25, 7.5, 0.00, 3, 1.5, 10, 0.0, 990, 10.5, 95, 0, 0},   // Sturm
            {12.0, 240, 0.35, 7.0, 0.00, 4, 6.0, 6, 0.0, 1008, 15.0, 88, 0, 3},  // Sommergewitter
            {13.0, 310, 0.25, 6.0, 0.10, 1, 0.35, 20, 0.0, 1008, 9.5, 90, 0, 0},   // abklingend: Schauer aus Nordwest
            {9.0, 330, 0.20, 3.5, 0.10, 0, 0.0, 40, 0.0, 1016, 10.5, 80, 0, 0},   // aufklarend
            {3.0, 110, 0.08, 7.0, 0.10, 2, 0.0, 50, 0.0, 1028, 12.0, 65, 0, 0},   // Talnebel nach klarer Nacht, der Gipfel darüber
            {16.0, 170, 0.35, 2.0, 0.30, 0, 0.0, 90, 0.0, 1006, 16.0, 45, 0, 0},  // Föhn bei Süd [zu prüfen]
            {26.0, 270, 0.30, 8.0, 0.00, 3, 3.0, 6, 0.0, 970, 8.5, 97, 0, 1},     // Orkan
            {14.0, 260, 0.20, 8.0, 0.30, 2, 0.28, 15, 0.0, 1012, 11.0, 98, 0, 0},  // Westlage mit Stau: Kappe am Berg, Sprühregen
            {4.0, 80, 0.08, 7.5, 0.05, 2, 0.0, 80, 0.0, 1034, 15.0, 45, 0, 0},   // Inversion: oben warm und trocken, unten Hochnebel
            {12.0, 280, 0.20, 8.0, 0.00, 2, 0.05, 15, 0.0, 1015, 8.0, 99, 0, 0}};  // Raueislage: Frost, Wind, Nebel am Berg

    /**
     * Schichtung je Lage (Spalten wie {@code Weather.L_…}: K/km, Wolkenbasis, Obergrenze, Inversion unten, oben, wie viel
     * wärmer) in m über NHN [G]. NaN bei der Basis: aus Temperatur und Taupunkt am Gipfel.
     */
    public static final double[][] SCHICHTUNG = {
            {6.5, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0},   // klar: Haufen über dem Gipfel
            {6.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0},
            {5.0, 750, 3500, Double.NaN, Double.NaN, 0},                // Front: Nimbostratus, der Gipfel steckt drin
            {6.0, 850, 3000, Double.NaN, Double.NaN, 0},
            {7.5, 1300, 11000, Double.NaN, Double.NaN, 0},              // Gewitter: Basis über dem Gipfel
            {6.5, 1050, 2500, Double.NaN, Double.NaN, 0},               // Schauer: Basis knapp unter dem Gipfel
            {6.5, 1120, 2000, Double.NaN, Double.NaN, 0},               // aufklarend: Reste der Decke streifen den Gipfel
            {4.0, 250, 560, 560, 760, 4},                               // Talnebel in den Tälern unter einer Bodeninversion
            {9.0, 2500, 3500, Double.NaN, Double.NaN, 0},               // Föhn: trockenadiabatisch, Wolken hoch
            {6.0, 650, 4000, Double.NaN, Double.NaN, 0},
            {5.5, 850, 2000, Double.NaN, Double.NaN, 0},                // Stau: Kappe ab 850 m
            {6.5, 450, 850, 850, 1050, 8},                              // Inversion: Hochnebel 450–850 m, darüber 8 K wärmer
            {6.0, 800, 1700, Double.NaN, Double.NaN, 0}};               // Raueislage: der Gipfel im unterkühlten Nebel

    /** Übergänge der freien Regie (Zeile: von, Spalte: nach; Reihenfolge wie {@link Weather.Phase}) [G]. */
    public static final double[][] UEBERGAENGE = {
            //  klar aufz Front Sturm Gew  abkl aufkl Nebel Föhn Orkan Stau Inv  Raueis
            {0, 0.32, 0, 0, 0, 0, 0, 0.12, 0.05, 0, 0.26, 0.15, 0.10},
            {0.10, 0, 0.70, 0, 0, 0, 0, 0, 0, 0, 0.20, 0, 0},
            {0, 0, 0, 0.20, 0.05, 0.45, 0, 0, 0, 0, 0.30, 0, 0},
            {0, 0, 0, 0, 0.05, 0.55, 0, 0, 0, 0.05, 0.25, 0, 0.10},
            {0, 0, 0, 0, 0, 1.00, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0.10, 0, 0, 0, 0.55, 0, 0, 0, 0.25, 0, 0.10},
            {0.60, 0, 0, 0, 0, 0, 0, 0.20, 0, 0, 0, 0.20, 0},
            {0.60, 0.20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.20, 0},
            {0.40, 0.60, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0.50, 0, 0.50, 0, 0, 0, 0, 0, 0, 0},
            {0.10, 0, 0.20, 0, 0, 0.20, 0.30, 0, 0, 0, 0, 0, 0.20},
            {0.50, 0.30, 0, 0, 0, 0, 0, 0.20, 0, 0, 0, 0, 0},
            {0.30, 0, 0, 0, 0, 0, 0.40, 0, 0, 0, 0.30, 0, 0}};

    /** Mittlere Verweildauer je Lage (h) [G]. */
    public static final double[] VERWEILEN = {10, 6, 8, 8, 2, 6, 5, 8, 8, 8, 14, 20, 16};

    /** Setzt das Klima in eine Wetterregie (Zeitzone Europe/Berlin, Sommer um den 15. Juli, Bezugshöhe die Station). */
    public static void setzen(Weather w) {
        w.zone = ZoneId.of("Europe/Berlin");
        w.climate = EINGANG.clone();
        w.summerDay = 196;
        w.targets = copy(ZIELE);
        w.transitions = copy(UEBERGAENGE);
        w.dwellHours = VERWEILEN.clone();
        w.layers = copy(SCHICHTUNG);
        w.refHeight = STATION;
        w.seed = 1141;
        WindBrocken.setzen(w, null);   // seit Phase 5: Jahresgang und Streuung des Winds nach der Station
    }

    static double[][] copy(double[][] a) {
        double[][] r = new double[a.length][];
        for (int i = 0; i < a.length; i++) r[i] = a[i].clone();
        return r;
    }

    // ------------------------------------------------------------------ Nachrechnen (Eichung, Prüfung)

    /** Was ein Lauf der freien Regie über Jahre ergibt: Monatsmittel, Niederschlag, Nebeltage, Anteile der Lagen. */
    public record Lauf(double[] monat, double[] niederschlag, double nebeltage, double sonne, double[] anteil, int jahre) {
        /** Größte Abweichung der Monatsmittel von der Station (K). */
        public double fehler() {
            double f = 0;
            for (int m = 0; m < 12; m++) f = Math.max(f, Math.abs(monat[m] - DWD[m]));
            return f;
        }

        public double jahresNiederschlag() { double s = 0; for (double v : niederschlag) s += v; return s; }

        public String zeile() {
            StringBuilder b = new StringBuilder();
            for (int m = 0; m < 12; m++) b.append(m > 0 ? " " : "").append(String.format(Locale.GERMAN, "%+.1f", monat[m]));
            return b.toString();
        }
    }

    /**
     * Lässt die freie Regie mit diesem Klima {@code jahre} Jahre ab 2001 laufen (stündlich, Würfel {@code seed}) und
     * misst an der Station.
     */
    public static Lauf simulieren(int jahre, long seed, double[] eingang) {
        Weather w = new Weather();
        setzen(w);
        if (eingang != null) w.climate = eingang.clone();
        w.seed = seed;
        ZoneId z = ZoneId.of("Europe/Berlin");
        double t0 = LocalDate.of(2001, 1, 1).atStartOfDay(z).toEpochSecond(), t1 = LocalDate.of(2001 + jahre, 1, 1).atStartOfDay(z).toEpochSecond();
        w.startFree(t0, Weather.Phase.KLAR);
        double[] sum = new double[12], n = new double[12], rain = new double[12];
        double[] share = new double[Weather.Phase.values().length];
        int fogDays = 0, days = 0, dayFog = 0, lastDay = -1;
        double sun = 0;
        for (double t = t0; t < t1; t += 3600) {
            Weather.Snapshot s = w.update(t, 1.0);
            var lt = java.time.Instant.ofEpochSecond((long) t).atZone(z);
            int m = lt.getMonthValue() - 1, d = lt.getDayOfYear() + lt.getYear() * 400;
            if (d != lastDay) {
                if (lastDay >= 0) { days++; if (dayFog > 0) fogDays++; }
                lastDay = d;
                dayFog = 0;
            }
            double temp = s.temperatureAt(STATION);
            sum[m] += temp;
            n[m]++;
            rain[m] += s.rain();
            share[s.phase().ordinal()]++;
            if (s.visibilityAt(STATION) < 1.0) dayFog++;
            double alt = com.dan.brocken.scene.Sun.position(t * 1000, 51.79925, 10.61558)[0];
            // Sonnenschein am Gipfel: nicht in der Wolke, und die untere Schicht deckt nur, wenn sie über ihm liegt (Nebelmeer)
            double unten = s.cloudTop() > STATION ? Math.pow(Math.min(1, s.oktas() / 8), 1.5) : 0;
            if (alt > 3) sun += (1 - s.cloudAt(STATION)) * (1 - unten) * (1 - 0.5 * s.high());
        }
        double[] mean = new double[12], rm = new double[12];
        for (int m = 0; m < 12; m++) { mean[m] = sum[m] / n[m]; rm[m] = rain[m] / jahre; }
        double tot = 0;
        for (double v : share) tot += v;
        for (int i = 0; i < share.length; i++) share[i] /= tot;
        return new Lauf(mean, rm, fogDays / (double) Math.max(1, days) * 365.25, sun / jahre, share, jahre);
    }

    /** Mittel mehrerer Läufe (Würfel 1 bis k). */
    public static Lauf mittel(int jahre, int wuerfel, double[] eingang) {
        double[] mean = new double[12], rain = new double[12], share = new double[Weather.Phase.values().length];
        double fog = 0, sun = 0;
        for (int k = 1; k <= wuerfel; k++) {
            Lauf l = simulieren(jahre, (long) k, eingang);
            for (int m = 0; m < 12; m++) { mean[m] += l.monat[m] / wuerfel; rain[m] += l.niederschlag[m] / wuerfel; }
            for (int i = 0; i < share.length; i++) share[i] += l.anteil[i] / wuerfel;
            fog += l.nebeltage / wuerfel;
            sun += l.sonne / wuerfel;
        }
        return new Lauf(mean, rain, fog, sun, share, jahre * wuerfel);
    }

    // ------------------------------------------------------------------ Regiebücher (Abnahme Phase 3)

    private static final Weather.Var W = Weather.Var.WIND, D = Weather.Var.DIRECTION, C = Weather.Var.COVER, V = Weather.Var.VISIBILITY,
            H = Weather.Var.HUMIDITY, T = Weather.Var.TEMPERATURE, R = Weather.Var.RAIN, K = Weather.Var.CLOUD, G = Weather.Var.GUST,
            HI = Weather.Var.HIGH;

    /**
     * „Über dem Nebelmeer“: ein Morgen mit Inversion im Oktober. Unten liegt Hochnebel bis 850 m, der Gipfel steht in
     * warmer, klarer Luft; am Mittag sinkt das Meer und reißt in den Tälern auf. Ab 6:30 Uhr, Zeitraffer 4 min/s.
     */
    public static Scenario nebelmeer() {
        List<Scenario.Step> l = new ArrayList<>();
        l.add(Scenario.Step.of(2.0, Weather.Phase.INVERSION, "Vor Sonnenaufgang: das Nebelmeer liegt still", "Gipfelstein", W, 3, D, 110, C, 8, V, 90, H, 45, T, 15));
        l.add(Scenario.Step.of(2.5, Weather.Phase.INVERSION, "Die Sonne über dem Nebelmeer", "UeberdemNebelmeer", W, 3.5, C, 8, V, 90, T, 15.5));
        l.add(Scenario.Step.of(2.0, Weather.Phase.INVERSION, "Das Meer sinkt und reißt in den Tälern auf", "Vogelauge", W, 4, C, 6, T, 16));
        l.add(Scenario.Step.of(4.0, Weather.Phase.KLAR, "Ein klarer Herbsttag", "Gipfelstein", W, 6, D, 90, C, 2, V, 70, H, 60, T, 14));
        return new Scenario("Über dem Nebelmeer", 6.5, 240, l);
    }

    /** „Westlage mit Stau“: eine Front aus Westen, die Kappe legt sich auf den Berg, Sprühregen; abends Schauer, dann klart es auf. */
    public static Scenario stau() {
        List<Scenario.Step> l = new ArrayList<>();
        l.add(Scenario.Step.of(3.0, Weather.Phase.AUFZIEHEND, "Wolken ziehen aus Westen auf", "Schierke", W, 10, D, 220, C, 5, HI, 0.7, T, 12));
        l.add(Scenario.Step.of(5.0, Weather.Phase.FRONT, "Landregen, der Gipfel verschwindet", "Schierke", W, 15, D, 245, C, 8, K, 3, R, 3, T, 11.5));
        l.add(Scenario.Step.of(6.0, Weather.Phase.STAU, "Westlage mit Stau: die Kappe sitzt auf dem Berg", "Gipfelstein", W, 14, D, 260, C, 8, R, 1, T, 11));
        l.add(Scenario.Step.of(3.0, Weather.Phase.ABKLINGEND, "Schauer aus Nordwest", "Vogelauge", W, 12, D, 300, C, 6, R, 1, T, 10));
        l.add(Scenario.Step.of(3.0, Weather.Phase.AUFKLAREND, "Es klart auf", "Goetheweg", W, 9, D, 310, C, 3, R, 0, T, 10));
        return new Scenario("Westlage mit Stau", 7.0, 360, l);
    }

    // ------------------------------------------------------------------ Phase 11: das Jahr in Höhenstufen

    /** Tage mit mindestens 1 mm Niederschlag je Monat an der Station [G: rund 210 im Jahr, im Winter die meisten]. */
    public static final double[] NASSE_TAGE = {20, 17, 19, 15, 16, 16, 17, 16, 16, 17, 19, 21};
    /**
     * Temperaturabnahme mit der Höhe im Mittel (K je km) [G: Wernigerode 234 m rund 9,1 °C, Brocken 3,9 °C], Zunahme des
     * Niederschlags (Anteil je km; Schierke 610 m rund 1 300 mm, Brocken 1 800 mm [G]), Verspätung der Pflanzen (Tage je
     * 100 m, Hopkins [G]).
     */
    public static final double LAPSE = 5.8, NIEDERSCHLAG_JE_KM = 0.55, VERSPAETUNG = 4;
    /** Stufen des Jahrs: 400 bis 1 150 m alle 50 m (16, so viele nimmt der Gelände-Shader). */
    public static final double[] STUFEN = com.dan.season.Heights.range(400, 1150, 50);

    /** Das Klima der Station für {@code com.dan.season} (DWD 1991–2020, nasse Tage [G]). */
    public static com.dan.season.Climate klima() {
        return new com.dan.season.Climate(DWD, DWD_NIEDERSCHLAG, NASSE_TAGE);
    }

    /**
     * Das Jahr in Höhenstufen ({@code com.dan.season.Heights}): an trockenen Tagen meist Nebel oder Wolke (6/8 beim
     * Vorrechnen, weniger Sonne auf dem Schnee), Schnee bis rund 2 °C (trockene Bergluft, Schnee geht zwischen 1 und 3 °C
     * in Regen über [G]). Geeicht so, dass am Gipfel in 30 gewürfelten Jahren im Mittel rund 160 Tage Schnee liegt
     * (DWD 1961–1990: 178 Tage; seither wärmer) und in Schierke an rund 60 Tagen 5 cm (LAU 2001–2025: 66 Tage).
     */
    public static com.dan.season.Heights jahr() {
        com.dan.season.Heights hs = new com.dan.season.Heights(klima(), STATION, STUFEN, LAPSE, NIEDERSCHLAG_JE_KM, VERSPAETUNG);
        for (com.dan.season.Year y : hs.years) {
            y.dryOktas = 6;
            y.rainLow = 1.0;
            y.rainHigh = 3.0;
        }
        hs.seed(7, 0);   // Startwert 7: der Winter 2026/27 schneereich (am 10. Februar 1,8 m am Gipfel)
        return hs;
    }

    /**
     * Ein Jahr am Gipfel in einer Verwehung: so viel Schnee, als fiele faktor-mal so viel (1 = freie ebene Fläche wie
     * die Messstelle, 2 = Mulde oder Leehang, in den der Wind die doppelte Menge legt [G]); Einstellungen wie {@link #jahr}.
     */
    public static com.dan.season.Year gipfelJahr(double faktor) {
        double[] p = DWD_NIEDERSCHLAG.clone();
        for (int m = 0; m < 12; m++) p[m] *= faktor;
        com.dan.season.Year y = new com.dan.season.Year(new com.dan.season.Climate(DWD, p, NASSE_TAGE));
        y.dryOktas = 6;
        y.rainLow = 1.0;
        y.rainHigh = 3.0;
        y.seed = 7;
        return y;
    }

    /**
     * „Raueislage“ (Januar): Nordwestwind, Frost, der Gipfel steckt einen halben Tag im unterkühlten Nebel; dann klart es
     * auf. Seit Phase 10 beginnt es um 22 Uhr am Vorabend, damit das Aufklaren am Nachmittag in die Sonne fällt
     * (Eis wächst 2–14 Uhr, die Fahnen in der Sonne ab rund 15 Uhr).
     */
    public static Scenario raueis() {
        List<Scenario.Step> l = new ArrayList<>();
        l.add(Scenario.Step.of(4.0, Weather.Phase.STAU, "Feuchte Luft aus Nordwest, der Berg in der Wolke", "Gipfelstein", W, 12, D, 290, C, 8, T, 9));
        l.add(Scenario.Step.of(12.0, Weather.Phase.RAUEIS, "Raueislage: Frost, Wind und Nebel am Gipfel", "Gipfelstein", W, 13, D, 285, C, 8, T, 8, H, 99));
        l.add(Scenario.Step.of(6.0, Weather.Phase.AUFKLAREND, "Es klart auf, alles ist weiß", "Vogelauge", W, 8, D, 320, C, 2, T, 7));
        return new Scenario("Raueislage", 22.0, 600, l);
    }

    /** „Ein Sommertag mit Gewitter“: klarer Morgen, Quellwolken über dem Harz, am Nachmittag Gewitter, abends klar. */
    public static Scenario sommergewitter() {
        List<Scenario.Step> l = new ArrayList<>();
        l.add(Scenario.Step.of(4.0, Weather.Phase.KLAR, "Ein klarer Sommermorgen", "Gipfelstein", W, 5, D, 120, C, 1, T, 13, H, 65));
        l.add(Scenario.Step.of(4.0, Weather.Phase.KLAR, "Quellwolken über dem Harz", "Vogelauge", W, 6, D, 200, C, 4, T, 15.5));
        l.add(Scenario.Step.of(3.0, Weather.Phase.AUFZIEHEND, "Die Türme wachsen", "Schierke", W, 8, D, 220, C, 6, K, 4, T, 15.5));
        l.add(Scenario.Step.of(2.0, Weather.Phase.GEWITTER, "Gewitter über dem Brocken", "Schierke", W, 12, C, 7, R, 10, G, 0.35));
        l.add(Scenario.Step.of(4.0, Weather.Phase.AUFKLAREND, "Nach dem Gewitter", "Gipfelstein", W, 7, D, 290, C, 3, R, 0, T, 12));
        return new Scenario("Ein Sommertag mit Gewitter", 6.0, 360, l);
    }

    /** Seit Phase 17 (F36 A): die Wetter-Regiebücher aus der Datenbank (BR_WETTERBUCH); null: die aus dem Code. */
    public static volatile List<Scenario> geladen;

    /** Die Regiebücher für das Bedienfeld. */
    public static List<Scenario> regiebuecher() {
        List<Scenario> g = geladen;
        return g != null && !g.isEmpty() ? g : ausDemCode();
    }

    /** Die Wetter-Regiebücher, wie sie im Code stehen (für die Startdaten). */
    public static List<Scenario> ausDemCode() { return List.of(nebelmeer(), stau(), raueis(), sommergewitter()); }
}
