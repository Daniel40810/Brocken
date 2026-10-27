package com.dan.brocken.scene;

/**
 * Der Ort der Szene: der Brocken im Harz, 1 141 m (Werkbuch, Abschnitte 2, 4 und 6).
 * <p>
 * Raum (F3 A): UTM Zone 32 Nord (ETRS89, EPSG:25832) mit dem Ursprung am Gipfelstein [G, aus 51,7993° N, 10,6156° O
 * gerechnet; Phase 2 setzt ihn genau mit dem DGM1]. Echter Maßstab: 1 Einheit = 1 m, x nach Osten, y nach oben, z nach
 * Süden, Nord = −z, y = 0 ist Normalhöhennull (NHN). Für die Datenbank und QGIS liegt die Lage zusätzlich in SRID 8307
 * (WGS84) vor.
 * <p>
 * Vier Ebenen (F1 A, Werkbuch Abschnitt 4): der Kern mit 3 × 3 km um den Gipfel (DGM1, bis 2 m), das Hauptraster aus
 * GLO-30 mit 12,7 × 11,9 km, der Fernring aus dem DGM200 bis rund 100 km und das Windgitter über dem Hauptraster
 * (rund 50 m, 30 Schichten bis 1 500 m über Grund, F7 A). Die Lagen der Orte unten sind aus Länge und Breite gesetzt
 * [G, zu prüfen]; Phase 2 legt sie auf das Gelände und korrigiert sie dort.
 */
public final class Site {
    private Site() { }

    /** Ursprung der Szene: der Gipfelstein [G], UTM 32 N. */
    public static final double E0 = 611_400, N0 = 5_739_940;
    public static final int SRID = 8307, EPSG_UTM = 25832;
    /** Breite und Länge des Ursprungs (aus E0/N0 gerechnet). */
    public static final double LAT, LON;
    static {
        double[] ll = Geo.UTM32N.inverse(E0, N0);
        LAT = ll[0];
        LON = ll[1];
    }

    /** Höhe des Gipfels über NHN (m): 1 141,2 m amtlich, 1 141,8 m größter Wert im GLO-30 (mit Bewuchs und Bauten). */
    public static final double GIPFEL = 1141.2;

    /** Der Ausschnitt des GLO-30 in Grad (templates/rasters_COP30): West, Süd, Ost, Nord. */
    public static final double WEST = 10.52, SOUTH = 51.74, EAST = 10.70, NORTH = 51.85;

    /** Mittlerer Erdradius in m und der wirksame Radius für die Sicht mit normaler Lichtbrechung (k = 0,13). */
    public static final double EARTH_R = 6_371_000, EARTH_R_OPTICAL = EARTH_R / (1 - 0.13);

    /** Ein rechteckiges Fenster in UTM 32 N (Ost- und Nordwerte in m). */
    public record Fenster(String name, double eMin, double nMin, double eMax, double nMax) {
        /** In der Szene: {xMin, zMin, xMax, zMax}. */
        public double[] scene() {
            double[] a = fromUtm(eMin, nMax), b = fromUtm(eMax, nMin);
            return new double[]{a[0], a[1], b[0], b[1]};
        }
        public double breite() { return eMax - eMin; }
        public double hoehe() { return nMax - nMin; }
        public boolean contains(double x, double z) {
            double[] u = toUtm(x, z);
            return u[0] >= eMin && u[0] <= eMax && u[1] >= nMin && u[1] <= nMax;
        }
    }

    /** F1 A: der Kern, 3 × 3 km um den Gipfel (Plateau, Spirale, Goetheweg oben, Moore, Klippen, Baumgrenze) [G]. */
    public static final Fenster KERN = new Fenster("Kern", 609_900, 5_738_400, 612_900, 5_741_400);
    /** F1 A: das Hauptraster nach dem GLO-30-Zuschnitt (Torfhaus bis Schierke), gerundet. */
    public static final Fenster HAUPTRASTER = new Fenster("Hauptraster", 604_700, 5_733_500, 617_400, 5_745_400);
    /** F1 A: Halbmesser des Fernrings aus dem DGM200 um den Gipfel (m) [G]. */
    public static final double FERNRING = 100_000;
    /** F7 A: das Windgitter über dem Hauptraster: Zellen in m waagerecht, Schichten, Höhe über Grund (m) [G]. */
    public static final double WIND_ZELLE = 50, WIND_HOEHE = 1500;
    public static final int WIND_SCHICHTEN = 30;

    /** Was ein Ort auf der Bühne ist (Farbe der Marke): Gipfel, Bahnhof, Weg, Ort, Berg. */
    public enum Art { GIPFEL, BAHNHOF, WEG, ORT, BERG }

    /** Ein benannter Ort [G] mit Lage in UTM 32 N und Höhe des Grunds über NHN (für die Bühne von Phase 1). */
    public record Ort(String name, double e, double n, double grund, Art art) {
        public double x() { return e - E0; }
        public double z() { return -(n - N0); }
    }

    /**
     * Die Orte aus dem Werkbuch (Abschnitt 6). Seit Phase 2 am Gelände geprüft: die Bahnhöfe Brocken und Schierke aus den
     * Empfangsgebäuden in ALKIS (belegt), der Wurmberg am höchsten Punkt des Geländes, der Goetheweg dort, wo die Linie
     * von Torfhaus zum Gipfel 930 m erreicht [G, Phase 12 legt ihn auf den Weg aus OSM]; Gipfelstein, Torfhaus und Drei
     * Annen Hohne aus Länge und Breite [G, zu prüfen]. Die Höhe {@link Ort#grund} ist der Wert aus dem Werkbuch.
     */
    public static final Ort GIPFELSTEIN = new Ort("Gipfelstein", 611_402, 5_739_945, GIPFEL, Art.GIPFEL),
            BAHNHOF_BROCKEN = new Ort("Bahnhof Brocken", 611_560, 5_740_001, 1125, Art.BAHNHOF),
            GOETHEWEG = new Ort("Goetheweg", 609_957, 5_740_125, 930, Art.WEG),
            TORFHAUS = new Ort("Torfhaus", 606_057, 5_740_625, 810, Art.ORT),
            SCHIERKE = new Ort("Bahnhof Schierke", 615_769, 5_736_242, 685, Art.BAHNHOF),
            DREI_ANNEN = new Ort("Drei Annen Hohne", 617_519, 5_740_401, 542, Art.BAHNHOF),
            WURMBERG = new Ort("Wurmberg", 611_708, 5_735_192, 971, Art.BERG);
    public static final Ort[] ORTE = {GIPFELSTEIN, BAHNHOF_BROCKEN, GOETHEWEG, TORFHAUS, SCHIERKE, DREI_ANNEN, WURMBERG};

    /**
     * Phase 5: die Wetterwarte des DWD (Station 722, 51,7986° N, 10,6183° O, 1 134,7 m), rund 190 m östlich des
     * Gipfelsteins; ihr Windgeber steht 27 m über Grund. Szenenpunkt (x, z).
     */
    public static final double[] WETTERWARTE = xz(new double[]{com.dan.brocken.wiring.WindBrocken.LAT, com.dan.brocken.wiring.WindBrocken.LON});

    /** Szenenpunkt (x, z) aus UTM 32 Nord (Ost- und Nordwert in m). */
    public static double[] fromUtm(double east, double north) { return new double[]{east - E0, -(north - N0)}; }

    /** UTM 32 Nord (Ost, Nord) aus einem Szenenpunkt (x, z). */
    public static double[] toUtm(double x, double z) { return new double[]{x + E0, N0 - z}; }

    /** Breite und Länge (Grad) eines Szenenpunkts. */
    public static double[] latLon(double x, double z) { return Geo.UTM32N.inverse(x + E0, N0 - z); }

    /** Szenenpunkt (x, z) aus Breite und Länge (Grad, als {lat, lon}). */
    public static double[] xz(double[] latLon) {
        double[] en = Geo.UTM32N.forward(latLon[0], latLon[1]);
        return fromUtm(en[0], en[1]);
    }

    /** Absenkung der Oberfläche in d m Entfernung durch die Erdkrümmung: d²/2R. */
    public static double curvatureDrop(double d) { return d * d / (2 * EARTH_R); }

    /** Entfernung der Kimm für ein Auge h m über der Ebene, mit normaler Lichtbrechung (m). */
    public static double horizon(double h) { return Math.sqrt(2 * EARTH_R_OPTICAL * Math.max(0, h)); }

    /** „51°47′57″ N · 10°36′56″ E“ */
    public static String dms(double lat, double lon) {
        return dms(lat, lat >= 0 ? "N" : "S") + " · " + dms(lon, lon >= 0 ? "E" : "W");
    }

    private static String dms(double v, String h) {
        v = Math.abs(v);
        int d = (int) v;
        double mm = (v - d) * 60;
        int m = (int) mm;
        int s = (int) Math.round((mm - m) * 60);
        if (s == 60) { s = 0; m++; }
        if (m == 60) { m = 0; d++; }
        return String.format("%d°%02d′%02d″ %s", d, m, s, h);
    }
}
