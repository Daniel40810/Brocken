package com.dan.brocken.tools;

import com.dan.brocken.wiring.KlimaBrocken;
import com.dan.weather.Weather;

import java.util.Locale;

/**
 * Phase 3: eicht die Monatsmittel, die die Wetterregie bekommt ({@link KlimaBrocken#EINGANG}), bis das Mittel der Szene
 * über 30 gewürfelte Jahre die Station trifft. Drei Runden: Eingang += Station − Szene. Gibt die neue Zeile für
 * {@code EINGANG} aus, dazu Niederschlag, Nebeltage und die Anteile der Lagen; seit Phase 5 auch die Zeile {@code JAHR}
 * für den Wind ({@code wiring.WindBrocken}) gegen die Stundenwerte der Station. Ohne Fenster, ohne Grafikkarte.
 * <pre>KlimaEichung [jahre] [würfel] [runden]</pre>
 */
public final class KlimaEichung {
    private KlimaEichung() { }

    public static void main(String[] a) {
        int jahre = a.length > 0 ? Integer.parseInt(a[0]) : 30, wuerfel = a.length > 1 ? Integer.parseInt(a[1]) : 3, runden = a.length > 2 ? Integer.parseInt(a[2]) : 3;
        double[] e = KlimaBrocken.EINGANG.clone();
        KlimaBrocken.Lauf l = null;
        for (int r = 0; r <= runden; r++) {
            l = KlimaBrocken.mittel(jahre, wuerfel, e);
            System.out.printf(Locale.GERMAN, "Runde %d: Szene %s · größte Abweichung %.2f K%n", r, l.zeile(), l.fehler());
            if (r == runden) break;
            for (int m = 0; m < 12; m++) e[m] += KlimaBrocken.DWD[m] - l.monat()[m];
        }
        StringBuilder b = new StringBuilder("EINGANG = {");
        for (int m = 0; m < 12; m++) b.append(m > 0 ? ", " : "").append(String.format(Locale.ROOT, "%.2f", e[m]));
        System.out.println(b.append("};"));
        System.out.printf(Locale.GERMAN, "Niederschlag %.0f mm (Station 1 799 mm), Nebeltage %.0f (Station %.0f), Sonne %.0f h (Station 1 463 h)%n",
                l.jahresNiederschlag(), l.nebeltage(), KlimaBrocken.DWD_NEBELTAGE, l.sonne());
        StringBuilder s = new StringBuilder("Anteile: ");
        for (Weather.Phase p : Weather.Phase.values()) s.append(String.format(Locale.GERMAN, "%s %.1f %% · ", KlimaBrocken.name(p), l.anteil()[p.ordinal()] * 100));
        System.out.println(s);
        // seit Phase 5: der Jahresgang des Winds (Faktoren je Monat) gegen die Monatsmittel der Station
        double[] j = com.dan.brocken.wiring.WindBrocken.JAHR.clone();
        com.dan.brocken.wiring.WindBrocken.Lauf w = null;
        for (int r = 0; r <= runden; r++) {
            w = com.dan.brocken.wiring.WindBrocken.mittel(jahre, wuerfel, j);
            System.out.printf(Locale.GERMAN, "Wind Runde %d: Szene %s m/s · größte Abweichung %.2f m/s%n", r, w.zeile(), w.fehlerMonat());
            if (r == runden) break;
            for (int m = 0; m < 12; m++) j[m] *= com.dan.brocken.wiring.WindBrocken.MONAT[m] / w.monat()[m];
        }
        StringBuilder jb = new StringBuilder("JAHR = {");
        for (int m = 0; m < 12; m++) jb.append(m > 0 ? ", " : "").append(String.format(Locale.ROOT, "%.3f", j[m]));
        System.out.println(jb.append("};"));
        System.out.printf(Locale.GERMAN, "Wind: Mittel %.2f m/s (Station %.2f), Streuung %.2f (%.2f), Perzentile 10/50/90/99 %%: %.1f %.1f %.1f %.1f (Station 4,5 10,5 18,5 26,5), Sektoren %s (Station 5 4 4 5 4 4 7 14 19 17 10 7)%n",
                w.mittel(), com.dan.brocken.wiring.WindBrocken.MITTEL, w.sd(), com.dan.brocken.wiring.WindBrocken.SD, w.perzentil()[0], w.perzentil()[1], w.perzentil()[2], w.perzentil()[3], w.sektoren());
    }
}
