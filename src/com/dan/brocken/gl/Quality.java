package com.dan.brocken.gl;

import java.util.Locale;

/**
 * Qualitätsstufen der App (aus Geiranger, entschlackt). Phase 1: der Anteil der Bildgröße, in dem gerechnet wird, und
 * das Zeitbudget; Phase 2: Sichtweite und Punktabstand des Geländes, im Kern feiner (F1 A); Phase 3: Stufe der Wolken
 * ({@code com.dan.cloud}) und Größe der Karte für die Schatten des Geländes; Phase 7: der Nebel rechnet in „Mittel“ in drittel, in
 * „Hoch“ in halber Auflösung ({@code com.dan.fog.TerrainFog.Quality}). Jede weitere Phase hängt die Stufe ihres Packages an (Phase 2 Gelände,
 * 3 Himmel und Wolken, 4 und 5 {@code wind}, 6 Nebel, 9 Wald, 10 {@code rime}, 12 und 13 {@code rail} …); die
 * Packages haben ihre eigene Kopie dieser Aufzählung (Paketvertrag Punkt 7) und folgen der App eins zu eins über
 * {@link #ordinal()}.
 * <p>
 * Ziel (Werkbuch, Abschnitt 11): „Mittel“ hält 60 Bilder/s in Full HD auf der Radeon 680M mit 2 GB, nie unter 30;
 * „Hoch“ und „Ultra“ sind für Standbilder und Kino.
 */
public enum Quality {
    NIEDRIG("Niedrig", 0.67, 2.2, 3, 1, com.dan.cloud.Clouds.Quality.FLAT, 512, com.dan.wind.WindField.Quality.LOW),
    MITTEL("Mittel", 0.85, 2.6, 2, 0, com.dan.cloud.Clouds.Quality.MEDIUM, 1024, com.dan.wind.WindField.Quality.MEDIUM),
    HOCH("Hoch", 1.0, 3.0, 2, 0, com.dan.cloud.Clouds.Quality.HIGH, 1024, com.dan.wind.WindField.Quality.MEDIUM),
    ULTRA("Ultra", 1.0, 3.4, 1, 0, com.dan.cloud.Clouds.Quality.ULTRA, 2048, com.dan.wind.WindField.Quality.MEDIUM);

    public final String label;
    /** Anteil der Bildgröße, in dem gerechnet wird. */
    public final double renderScale;
    /** Phase 2: Sichtweite jeder Geländestufe in ihren Knotengrößen (CDLOD). */
    public final double terrainRange;
    /** Phase 2: feinste Stufe außerhalb des Kerns und in ihm (0 = 2 m, 1 = 4 m, 2 = 8 m, 3 = 16 m Punktabstand). */
    public final int terrainFine, terrainKern;
    /** Phase 3: Stufe der Wolken (flach, 1/4, 1/2 Auflösung) und Kantenlänge der Schattenkarte über dem Hauptraster (Texel). */
    public final com.dan.cloud.Clouds.Quality clouds;
    public final int shadowN;
    /** Phase 4: Stufe des Windfelds (Niedrig 100 m und 16 Schichten, sonst 50 m und 30 Schichten nach F7 A). */
    public final com.dan.wind.WindField.Quality wind;

    Quality(String label, double renderScale, double terrainRange, int terrainFine, int terrainKern, com.dan.cloud.Clouds.Quality clouds, int shadowN,
            com.dan.wind.WindField.Quality wind) {
        this.label = label;
        this.renderScale = renderScale;
        this.terrainRange = terrainRange;
        this.terrainFine = terrainFine;
        this.terrainKern = terrainKern;
        this.clouds = clouds;
        this.shadowN = shadowN;
        this.wind = wind;
    }

    /**
     * Bildzeit, die die Stufe höchstens verbrauchen darf (ms): „Mittel“ 16,5 ms nach dem Budget im Werkbuch
     * (Abschnitt 11: Summe 16,5 ms je Bild), „Hoch“ das Doppelte, „Niedrig“ die Hälfte.
     */
    public double budgetMs() {
        return switch (this) { case NIEDRIG -> 8.0; case MITTEL -> 16.5; case HOCH -> 33.0; case ULTRA -> 50.0; };
    }

    /** Was die Stufe einstellt, als eine Zeile (Bedienfeld, README). */
    public String summary() {
        com.dan.fog.TerrainFog.Quality f = com.dan.fog.TerrainFog.Quality.values()[ordinal()];
        return String.format(Locale.GERMAN, "%s: %d %% der Auflösung, Budget %.1f ms, Gelände %.1f Knoten, am Auge %d m, im Kern %d m, Wolken %s, Schatten %d², Wind %.0f m · %d Schichten, Nebel %d Schritte in %s Auflösung", label,
                Math.round(renderScale * 100), budgetMs(), terrainRange, 2 << terrainFine, 2 << terrainKern, clouds.name().toLowerCase(Locale.ROOT), shadowN, wind.cell, wind.layers,
                f.steps, f.scale == 1 ? "voller" : f.scale == 2 ? "halber" : "drittel");
    }

    @Override public String toString() { return label; }
}
