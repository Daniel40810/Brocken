package com.dan.brocken.ui;

import com.dan.fstyle.FTheme;

import java.awt.Color;

/**
 * Die Farben der Oberfläche aus FTheme (Bedienelemente in FTheme-Farben, die Szene hat ihren eigenen Look; aus
 * Geiranger). Liefert das Thema keine dunklen Farben, nimmt die App ihren Morgen über dem Nebelmeer, damit der Rand um
 * die Szene nicht hell aufleuchtet. Dazu die Farben des Werkbuchs: Dampf, Nebel, Brockengranit, schwarzgrüne Fichte,
 * silbergraues Totholz, Moor, Heide, die rote Lok, Abendgold.
 */
public final class Theme {
    private Theme() { }

    public static final Color NACHT = new Color(15, 19, 21), DAMPF = new Color(242, 237, 227), NEBEL = new Color(159, 192, 206),
            GRANIT = new Color(192, 146, 132), FICHTE = new Color(45, 74, 55), TOT = new Color(189, 183, 171), MOOR = new Color(164, 123, 78),
            HEIDE = new Color(168, 137, 192), LOK = new Color(212, 87, 74), GOLD = new Color(230, 185, 94);

    public static Color bg() { return pick(FTheme::getBackground, NACHT, Role.DARK); }
    public static Color surface() { return pick(FTheme::getSurface, new Color(22, 27, 30), Role.DARK); }
    public static Color ink() { return pick(FTheme::getText, new Color(232, 234, 233), Role.LIGHT); }
    public static Color muted() { return pick(FTheme::getTextMuted, new Color(147, 156, 159), Role.LIGHT); }
    public static Color accent() { return pick(FTheme::getAccent, NEBEL, Role.ANY); }
    public static Color status() { return new Color(10, 13, 14); }
    public static Color line() { return new Color(40, 48, 52); }

    private enum Role { DARK, LIGHT, ANY }

    private static Color pick(java.util.function.Function<FTheme, Color> f, Color fallback, Role role) {
        try {
            Color c = f.apply(FTheme.getInstance());
            if (c == null) return fallback;
            int lum = (c.getRed() * 3 + c.getGreen() * 6 + c.getBlue()) / 10;
            if (role == Role.DARK && lum > 70) return fallback;
            if (role == Role.LIGHT && lum < 120) return fallback;
            return c;
        } catch (RuntimeException | Error e) {
            return fallback;
        }
    }

    /** Die Farbleiste des Werkbuchs (Dampf bis Abendgold) mit ihren Anteilen, für Startbild und Symbol. */
    public static final Color[] BAND = {DAMPF, NEBEL, GRANIT, FICHTE, TOT, MOOR, HEIDE, LOK, GOLD};
    public static final double[] BAND_AT = {0, 0.10, 0.30, 0.44, 0.56, 0.66, 0.76, 0.84, 0.92, 1};
}
