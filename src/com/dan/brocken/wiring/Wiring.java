package com.dan.brocken.wiring;

import com.dan.cloud.CloudType;
import com.dan.cloud.Clouds;
import com.dan.sky.Sky;
import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Die Verdrahtung der App (Paketvertrag Punkt 4, aus Geiranger, schlank): hier und nur hier reden die Packages
 * miteinander. Phase 3: die Wetterregie ({@code com.dan.weather}) mit dem Klima „Brocken“ ({@link KlimaBrocken}) setzt
 * je Bild Wind, Böen, Bedeckung, Wolkenart, Regen, Sicht und die Schichtung; daraus bekommen der Himmel
 * ({@code sky}: Zeit, Ort, Augenhöhe, Sicht) und die Wolken ({@code cloud}: Bedeckung, Art, Basis über NHN, Zug) ihre
 * Eingänge. Die Werte der Regie gelten am Gipfel ({@link KlimaBrocken#STATION}); in jeder anderen Höhe rechnet sie die
 * Regie selbst (Temperatur mit der Schichtung, Wolke und Sicht aus Basis und Obergrenze).
 * <p>
 * Phase 4: das Windfeld ({@code com.dan.wind}, F6 A) über dem Hauptraster bekommt aus der Regie Richtung, Schichtung
 * (nach Lage und Sonne) und die Erwärmung des Bodens (Hangwind, Kaltluft), aus dem Gelände Höhe und Rauigkeit (nach der
 * Nutzung aus ALKIS). Seine Stärke wird so geeicht, dass es am Gipfel den Wind der Regie trifft ({@link #windGain}).
 * Ab Phase 5 nehmen Gras, Rauch, Vögel und Nebel das Feld; ab Phase 6 kommt der Nebel aus {@code fog}, bis dahin zeigen
 * die Wolken auch das Nebelmeer (Hochnebel unter der Inversion) und die Kappe am Berg (Stau).
 */
public final class Wiring {
    public final Clock clock;
    /** Die Wetterregie mit dem Klima „Brocken“; Tagesgang in Ortszeit. */
    public final Weather weather = new Weather();
    /** Ob die Regie die Eingänge setzt (die App schaltet sie ein; Prüfungen setzen die Felder selbst). */
    public volatile boolean regie = true;
    /** Wind am Gipfel (m/s), Richtung, aus der er kommt (°), Böigkeit, Wind in der laufenden Böe. */
    public volatile double windSpeed = 7, windFromDeg = 80, gustiness = 0.18, gustNow = 7;
    /** Bedeckung in Achteln, hohe Schicht 0..1, Regen in mm/h, Sicht unter den Wolken in km. */
    public volatile double oktas = 2.5, highCover = 0.1, rainRate, visibilityKm = 60;
    public volatile CloudType cloudType = CloudType.CUMULUS;
    /** Temperatur am Gipfel (°C), Luftdruck auf Meereshöhe (hPa), Feuchte (%). */
    public volatile double airTempC = 9, pressureHPa = 1020, humidity = 80;
    /** Zeichnet die App echte Wolken? Dann bekommt der Himmel keinen Schleier. */
    public volatile boolean cloudsDrawn;
    private volatile Weather.Snapshot snap;

    public Wiring(Clock clock) {
        this.clock = clock;
        KlimaBrocken.setzen(weather);
        wind.fromDeg = windFromDeg;
        wind.top = com.dan.brocken.scene.Site.WIND_HOEHE;
        wind.seed = 1141;
    }

    // ------------------------------------------------------------------ Phase 4: Windfeld

    /** Das Windfeld über dem Hauptraster (Eingänge setzt {@link #feedWind}; gelöst im Faden „Windfeld“ der App). */
    public final com.dan.wind.WindField wind = new com.dan.wind.WindField();
    /** Faktor auf das gelöste Feld, damit es an der Wetterwarte in Höhe des Windgebers (27 m über Grund, seit Phase 5) den Wind der Regie trifft. */
    public volatile double windGain = 1;
    /** Wind des gelösten Felds an der Wetterwarte in 27 m ohne Faktor (m/s), aus dem letzten Lösen. */
    public volatile double windSummitRaw = Double.NaN;

    /** Rauigkeit z0 (m) je Nutzungsart aus ALKIS (Reihenfolge wie {@code Terrain.ART}) [G]; ohne Angabe Wald (Nationalpark). */
    public static final double[] Z0 = {0.8, 0.9, 0.5, 0.05, 0.03, 0.1, 0.03, 0.0005, 0.5, 0.05, 0.03, 0.05};

    /** Gelände und Rauigkeit an das Windfeld (einmal nach dem Laden). */
    public void setTerrain(com.dan.brocken.scene.Terrain t) { setTerrainOn(wind, t); }

    /** Höhe und Rauigkeit (nach ALKIS, {@link #Z0}) eines Geländes an ein Windfeld. */
    public static void setTerrainOn(com.dan.wind.WindField f, com.dan.brocken.scene.Terrain t) {
        f.ground = t::height;
        f.roughness = (x, z) -> { int a = t.artAt(x, z) & 0xFF; return a < Z0.length ? Z0[a] : 0.5; };
    }

    /**
     * Schichtung des Windfelds nach Lage und Sonne [G]: Inversion und Talnebel → Inversion; Raueis → stabil; Gewitter →
     * labil; bei ruhigen Lagen am Tag mit Sonne labil, nachts stabil; Fronten, Stau, Sturm, Föhn neutral.
     */
    public static com.dan.wind.WindField.Stability stabilityFor(Weather.Phase p, double sunAlt, double oktas) {
        return switch (p) {
            case INVERSION, NEBEL -> com.dan.wind.WindField.Stability.INVERSION;
            case RAUEIS -> com.dan.wind.WindField.Stability.STABIL;
            case GEWITTER -> com.dan.wind.WindField.Stability.LABIL;
            case KLAR, AUFKLAREND, AUFZIEHEND -> sunAlt > 8 && oktas < 5 ? com.dan.wind.WindField.Stability.LABIL
                    : sunAlt < -2 ? com.dan.wind.WindField.Stability.STABIL : com.dan.wind.WindField.Stability.NEUTRAL;
            default -> com.dan.wind.WindField.Stability.NEUTRAL;
        };
    }

    /** Erwärmung des Bodens −1 … 1 aus Sonnenhöhe und Bedeckung: am Tag nach der Sonne, nachts nach dem klaren Himmel [G]. */
    public static double heatingFor(double sunAlt, double oktas) {
        double c = Math.min(1, Math.max(0, oktas / 8));
        if (sunAlt > 0) return Math.min(1, 1.3 * Math.sin(Math.toRadians(sunAlt))) * (1 - 0.75 * c);
        double night = Math.min(1, -sunAlt / 6);
        return -night * (1 - 0.8 * c);
    }

    /**
     * Dem Windfeld Richtung, Schichtung und Erwärmung aus der Regie (je Bild; neu gelöst wird erst, wenn sich
     * {@link #windKey} ändert). Die Stärke der Lage ist ein Schätzwert (am Gipfel rund doppelt so viel wie im Vorland [G]),
     * geeicht wird nach dem Lösen mit {@link #windGain}.
     */
    public void feedWind(double sunAlt) {
        Weather.Snapshot s = snapshot();
        wind.fromDeg = windFromDeg;
        wind.stability = stabilityFor(s.phase(), sunAlt, oktas);
        wind.heating = heatingFor(sunAlt, oktas);
        wind.speed = Math.max(0.5, windSpeed * 0.45);
        double raw = windSummitRaw;
        if (!Double.isNaN(raw) && raw > 0.05) windGain = Math.max(0.05, Math.min(20, windSpeed / raw));
    }

    /** Schlüssel des Felds: ändert er sich, muss neu gelöst werden (Richtung auf 10°, Schichtung, Erwärmung auf 0,4, Stufe, seed). */
    public String windKey() {
        return Math.round(wind.fromDeg / 10.0) % 36 + "/" + wind.stability + "/" + Math.round(wind.heating * 2.5) + "/" + wind.quality + "/" + wind.seed;
    }

    /** Wind am Ort mit dem Faktor (m/s, {u Ost, v oben, w Süd}). */
    public double[] windAt(double x, double y, double z) {
        double[] v = wind.windAt(x, y, z, clock.t());
        double k = wind.solution != null ? windGain : 1;
        return new double[]{v[0] * k, v[1] * k, v[2] * k};
    }

    /** Der letzte Stand der Regie (nie null nach {@link #feedWeather}). */
    public Weather.Snapshot snapshot() {
        Weather.Snapshot s = snap;
        return s != null ? s : feedWeather(0);
    }

    /** Die Regie einen Schritt weiter (Szenenzeit aus der Uhr, Böen in Echtzeit) und ihre Werte übernehmen. */
    public Weather.Snapshot feedWeather(double realDt) {
        Weather.Snapshot s = weather.update(clock.epochMillis() / 1000.0, realDt);
        apply(s);
        return s;
    }

    /** Handregler: eine Größe der Regie sofort setzen und halten, bis „Regie weiter“. */
    public void hand(Weather.Var v, double value) {
        weather.set(v, value);
        Weather.Snapshot s = weather.snapshot();
        if (s != null) apply(s);
    }

    /** „Regie weiter“: alle Handregler lösen. */
    public void resumeRegie() { weather.release(); }

    /** Freie Regie ab jetzt, beginnend mit einer Lage. */
    public void startFree(Weather.Phase p) {
        regie = true;
        weather.startFree(clock.epochMillis() / 1000.0, p);
        apply(weather.update(clock.epochMillis() / 1000.0, 0));
    }

    /** Ein Regiebuch ab seinem Beginn am Tag der Uhr (die App stellt die Uhr dorthin). */
    public void start(Scenario s) {
        regie = true;
        weather.start(s, startOf(s));
        apply(weather.update(clock.epochMillis() / 1000.0, 0));
    }

    /** Beginn eines Regiebuchs am Tag der Uhr (s seit 1970). */
    public double startOf(Scenario s) {
        ZoneId z = ZoneId.of("Europe/Berlin");
        ZonedDateTime d = Instant.ofEpochMilli((long) clock.epochMillis()).atZone(z).toLocalDate().atStartOfDay(z);
        return d.toEpochSecond() + s.startHour() * 3600;
    }

    private void apply(Weather.Snapshot s) {
        if (s == null) return;
        snap = s;
        windSpeed = s.wind();
        windFromDeg = s.fromDeg();
        gustiness = s.gustiness();
        gustNow = s.gustNow();
        oktas = s.oktas();
        highCover = s.high();
        cloudType = CloudType.valueOf(s.cloud().name());
        rainRate = Math.max(0, s.rain());
        visibilityKm = Math.max(0.2, s.visibilityKm() * (1 - 0.97 * s.fog()));
        airTempC = s.temperature();
        pressureHPa = s.pressure();
        humidity = s.humidity();
    }

    /** Temperatur in der Höhe h (m über NHN) nach der Schichtung der Regie. */
    public double temperatureAt(double h) { return snapshot().temperatureAt(h); }

    /**
     * Dem Himmel Zeit, Ort, Augenhöhe, Sicht und Bedeckung (je Bild, vor {@code sky.step}). Die Sicht ist die unter den
     * Wolken (in der Wolke rechnen die Wolken selbst den Nebel); Rückstrahlung des Bodens [G]: Fichtenwald, um 0,10.
     */
    public void feed(Sky sky, double eyeAsl, double latDeg, double lonDeg) {
        sky.epochMillis = clock.epochMillis();
        sky.latDeg = latDeg;
        sky.lonDeg = lonDeg;
        sky.eyeHeight = Math.max(1, eyeAsl);
        sky.visibilityKm = Math.max(1, Math.min(220, visibilityKm));
        // seit Phase 11 mit Schnee: um das Auge die Decke in seiner Höhe, Wald hält sie halb verdeckt [G]
        double sn = Math.min(1, schnee(eyeAsl) / 0.15);
        sky.groundAlbedo = 0.10 + 0.45 * sn;
        sky.cloudCover = cloudsDrawn ? 0 : oktas / 8;
        sky.cloudVeil = !cloudsDrawn;
        sky.rainbow = 0;
        // Phase 7: Halos am hohen Schleier (Zirrostratus) [G: ab rund 20 % hoher Bedeckung], verdeckt von einer
        // dichten unteren Schicht, außer das Auge steht über ihr; Nebensonnen etwas seltener als der Ring
        Weather.Snapshot s = snapshot();
        boolean ueber = !Double.isNaN(s.cloudTop()) && eyeAsl > s.cloudTop();
        double frei = ueber ? 1 : 1 - smooth(5, 8, oktas);
        double ring = haloHand >= 0 ? haloHand : smooth(0.15, 0.6, highCover) * 0.8 * frei;
        sky.halo = ring;
        sky.parhelia = haloHand >= 0 ? haloHand * 0.8 : ring * 0.6;
    }

    /** Phase 7: Halos von Hand (0..1), −1 = nach dem hohen Schleier der Regie. */
    public volatile double haloHand = -1;

    // ------------------------------------------------------------------ Phase 7: Licht im Nebel

    /** Die Tröpfchen des Nebels (Halbmesser und Breite der Verteilung) für Glorie und Nebelbogen. */
    public final com.dan.sky.Droplets tropfen = com.dan.sky.Droplets.nebelmeer();
    /** Halbmesser der Tröpfchen von Hand (µm), −1 = nach der Lage. */
    public volatile double tropfenHand = -1;

    /**
     * Phase 7: Tröpfchen nach der Lage [G nach Laven 2005 und üblichen Messwerten]: im Nebelmeer (Obergrenze einer
     * Inversion) 10 µm mit schmaler Verteilung (8 %) – lebhafte Glorie; in der Kappe am Berg (Stau, Raueis) 13 µm, 15 %;
     * Talnebel allein 5 µm, 25 %. Von Hand nur der Halbmesser, die Breite bleibt die der Lage.
     */
    public void feed(com.dan.sky.Droplets d, com.dan.fog.TerrainFog f) {
        Weather.Phase p = snapshot().phase();
        com.dan.fog.TerrainFog.Snapshot fs = f.snapshot();
        double r, w;
        if (fs.sea()) { r = 10; w = 0.08; }
        else if (fs.cap() || p == Weather.Phase.STAU || p == Weather.Phase.RAUEIS) { r = 13; w = 0.15; }
        else if (fs.valley()) { r = 5; w = 0.25; }
        else { r = 10; w = 0.08; }
        d.radiusUm = tropfenHand > 0 ? tropfenHand : r;
        d.width = w;
    }

    // ------------------------------------------------------------------ Phase 11: das Jahr

    /** Das Jahr in Höhenstufen ({@code com.dan.season}): Schnee je Höhe, Kronenschnee, Reif, Pflanzen. */
    public final com.dan.season.Heights jahr = KlimaBrocken.jahr();
    /** Schnee von Hand (m am Gipfel; darunter nach den Stufen der Regie verteilt), NaN = nach dem Jahr. */
    public volatile double schneeHand = Double.NaN;
    /** Ob das gewürfelte Jahr die Temperatur der Regie verschiebt (Abweichung des Tages; Prüfungen schalten es ab). */
    public volatile boolean jahrFuehrt = true;

    /** Datum der Uhr (Jahr, Tag im Jahr, Stunde) in Ortszeit. */
    public int[] datum() {
        ZonedDateTime d = Instant.ofEpochMilli((long) clock.epochMillis()).atZone(ZoneId.of("Europe/Berlin"));
        return new int[]{d.getYear(), com.dan.season.Year.dayOfYear(d.getMonthValue(), d.getDayOfMonth())};
    }

    public double stunde() {
        ZonedDateTime d = Instant.ofEpochMilli((long) clock.epochMillis()).atZone(ZoneId.of("Europe/Berlin"));
        return d.getHour() + d.getMinute() / 60.0 + d.getSecond() / 3600.0;
    }

    /**
     * Phase 11: das Jahr einen Schritt in Szenenzeit mit dem Wetter der Regie (Temperatur je Höhe nach der Schichtung,
     * Niederschlag am Gipfel, Sonne, Bedeckung, Wind am Gipfel). Springt die Uhr, rechnet jede Stufe aus dem gewürfelten
     * Jahr vor. Die Abweichung des gewürfelten Tags am Gipfel geht an die Regie ({@code weather.anomaly}), damit Regie und
     * Schnee zum selben Winter gehören.
     */
    public void feedJahr(double sceneDt, boolean sprung, double sunAlt) {
        int[] d = datum();
        double h = stunde();
        com.dan.season.Year oben = jahr.years[jahr.years.length - 1];
        if (jahrFuehrt) weather.anomaly = oben.anomaly(d[0], d[1]);
        if (sprung) { jahr.settle(d[0], d[1], h); return; }
        Weather.Snapshot s = snapshot();
        jahr.step(sceneDt, d[0], d[1], h, s::temperatureAt, Math.max(0, s.rain()) / Math.max(0.2, 1 + KlimaBrocken.NIEDERSCHLAG_JE_KM * (s.refHeight() - KlimaBrocken.STATION) / 1000),
                sunAlt, s.oktas(), s.wind());
    }

    /** Schneehöhe auf ebenem, nicht verwehtem Boden in der Höhe z (m), mit dem Handregler. */
    public double schnee(double z) {
        double hand = schneeHand;
        if (!Double.isNaN(hand)) return hand * smooth(KlimaBrocken.STUFEN[0], KlimaBrocken.STATION, z) * (0.3 + 0.7 * smooth(400, 1141, z));
        return jahr.snowDepth(z);
    }

    /** Schneehöhen aller Stufen für den Gelände-Shader (m). */
    public float[] schneeStufen() {
        float[] r = new float[KlimaBrocken.STUFEN.length];
        for (int i = 0; i < r.length; i++) r[i] = (float) schnee(KlimaBrocken.STUFEN[i]);
        return r;
    }

    /** „Schnee: 600 m 12 cm, 900 m 45 cm, 1 141 m 80 cm · Schneegrenze 640 m · Kronen 40 % · nass“ */
    public String jahrZeile() {
        if (!Double.isNaN(schneeHand)) return String.format(Locale.GERMAN, "Schnee von Hand: %.0f cm am Gipfel, %.0f cm in 900 m, %.0f cm in 600 m",
                schnee(1141) * 100, schnee(900) * 100, schnee(600) * 100);
        String z = jahr.describe(600, 900, 1141);
        double kr = jahr.treeSnow(1000), nass = jahr.snowWet(1141);
        return z + String.format(Locale.GERMAN, " · Kronen %.0f %%", kr * 100) + (nass > 0.5 ? " · nass" : "");
    }

    // ------------------------------------------------------------------ Phase 10: Raueis

    /** Das Eis am Berg in Höhenbändern ({@code com.dan.rime}). */
    public final Raueis raueis = new Raueis();

    /** Mittlerer Durchmesser der Tröpfchen für das Eis (µm): zweimal der Halbmesser der Glorie. */
    public double tropfenDurchmesser() { return 2 * tropfen.radiusUm; }

    /**
     * Phase 10: das Eis einen Schritt in Szenenzeit; springt die Uhr, wird ein laufendes Regiebuch von seinem Beginn
     * durchgerechnet ({@link Raueis#vorlauf}), in freier Regie bleibt das Eis, wie es ist.
     */
    public void feedRaueis(double sceneDt, boolean sprung, double sunAlt) {
        double now = clock.epochMillis() / 1000.0;
        if (sprung) {
            Scenario sc = weather.scenario();
            if (sc != null) raueis.vorlauf(sc, weather.scenarioStart(), now, weather.seed, tropfenDurchmesser(),
                    t -> com.dan.brocken.scene.Sun.position(t * 1000, com.dan.brocken.scene.Site.LAT, com.dan.brocken.scene.Site.LON)[0]);
            return;
        }
        raueis.step(snapshot(), sceneDt, tropfenDurchmesser(), sunAlt, now);
    }

    /** Phase 7: Grenzen des Nebelmeers von Hand (m über NHN; NaN = nach der Regie), etwa um es an den Gipfel zu heben. */
    public volatile double meerBasisHand = Double.NaN, meerTopHand = Double.NaN;

    /**
     * Den Wolken Bedeckung, Art, hohe Schicht, die Basis aus der Schichtung der Regie (m über NHN) und den Zug: in
     * Wolkenhöhe 1,3-fach und 15° rechtsdrehend gegen den Wind am Gipfel [G]. Die Schattenkarte rechnet in der Höhe des
     * Grunds unter dem Auge, höchstens bis knapp unter die Basis.
     */
    public void feed(Clouds c, double groundAtEye) {
        Weather.Snapshot s = snapshot();
        c.oktas = oktas;
        c.type = cloudType;
        c.highCover = highCover;
        c.windSpeed = Math.max(2, windSpeed * 1.3);
        c.windFromDeg = (windFromDeg + 15) % 360;
        c.lens = 0;
        c.baseHeight = s.hasHeight() && !Double.isNaN(s.cloudBase()) ? s.cloudBase() : Double.NaN;
        double base = Double.isNaN(c.baseHeight) ? cloudType.base : c.baseHeight;
        c.shadowHeight = Math.max(0, Math.min(groundAtEye, base - 10));
        c.precipitation = rainRate > 0 ? Math.min(1, 0.35 + rainRate / 25) : 0;
    }

    // ------------------------------------------------------------------ Phase 6: Nebel über Gelände

    /** Liegt in dieser Lage ein Nebelmeer (Hochnebel unter der Inversion, Talnebel)? Dann übernimmt der Nebel die untere Schicht. */
    public static boolean meerLage(Weather.Phase p) { return p == Weather.Phase.INVERSION || p == Weather.Phase.NEBEL; }

    /**
     * Phase 6: dem Nebel über Gelände die Lage geben [G]:
     * <ul>
     * <li>Nebelmeer bei Inversion und Talnebel: Grenzen der unteren Schicht aus der Regie; Erwärmung aus der Sonne
     * (nur der hohe Schleier dämpft, das Meer selbst nicht – die Sonne trifft seine Obergrenze und den Boden darunter).</li>
     * <li>Talnebel in der Kaltluft: nachts und bis die Sonne gut 10° hoch steht, bei feuchter Luft (in den Tälern
     * rund 10 % feuchter als am Gipfel, unter dem Meer gesättigt) und wenig Wind.</li>
     * <li>Stau: eine Decke ab 6/8 mit der Basis bis 400 m über dem Gipfel legt sich auf den Berg; der Gürtel am Luvhang
     * kommt aus dem Aufwind des Felds. Sicht im Gürtel rund das 1,6-fache der Sicht in der Wolke.</li>
     * </ul>
     * Gibt zurück, ob der Nebel die untere Wolkenschicht übernimmt.
     */
    public boolean feed(com.dan.fog.TerrainFog f, double sunAlt) {
        Weather.Snapshot s = snapshot();
        boolean meer = meerLage(s.phase()) && s.hasHeight() && !Double.isNaN(s.cloudBase()) && s.cloudTop() > s.cloudBase();
        f.seaBaseTarget = meer ? (Double.isNaN(meerBasisHand) ? s.cloudBase() : meerBasisHand) : Double.NaN;
        f.seaTopTarget = meer ? (Double.isNaN(meerTopHand) ? s.cloudTop() : meerTopHand) : Double.NaN;
        f.heating = heatingFor(sunAlt, highCover * 8);
        f.windSpeed = windSpeed * 0.45;
        f.windFromDeg = windFromDeg;
        double morgen = smooth(12, -2, sunAlt), feucht = meer ? 1 : smooth(80, 96, s.humidity() + 10), ruhig = 1 - smooth(4, 10, windSpeed);
        f.valleyFill = valleyHand >= 0 ? valleyHand : 1.1 * morgen * feucht * ruhig;
        boolean cap = !meer && s.hasHeight() && !Double.isNaN(s.cloudBase()) && s.cloudBase() < com.dan.brocken.scene.Site.GIPFEL + 400 && s.oktas() >= 6;
        f.capBase = cap ? s.cloudBase() : Double.NaN;
        if (cap) f.capVisibility = Math.max(60, s.visibilityAt(s.cloudBase() + 50) * 1000 * 1.6);
        return meer;
    }

    /** Talnebel von Hand (Anteil der Kaltluft 0..1,2), −1 = nach der Regie. */
    public volatile double valleyHand = -1;

    /**
     * Phase 6: wie weit das Nebelmeer jetzt schon von unten aufgelöst ist (m): {@code burnRate} · ∫ Erwärmung dt seit
     * Beginn der Lage mit Meer (höchstens zwei Tage zurück); nachts schließt es sich wieder (3 h).
     */
    public double nebelVerbrannt(double burnRate) {
        double now = clock.epochMillis() / 1000.0;
        if (!meerLage(snapshot().phase())) return 0;
        double start = now;
        java.util.List<Weather.Segment> l = weather.segments(now - 48 * 3600, now);
        for (int i = l.size() - 1; i >= 0; i--) {
            Weather.Segment g = l.get(i);
            if (g.t0() > now) continue;
            if (!meerLage(g.phase())) break;
            start = Math.min(start, g.t0());
        }
        start = Math.max(start, now - 48 * 3600);
        double sum = 0;
        for (double t = start; t < now; t += 300) {
            double dt = Math.min(300, now - t);
            double alt = com.dan.brocken.scene.Sun.position((t + dt / 2) * 1000, com.dan.brocken.scene.Site.LAT, com.dan.brocken.scene.Site.LON)[0];
            double h = heatingFor(alt, highCover * 8);
            if (h > 0) sum += h * dt / 3600;
            else sum *= Math.exp(-dt / 3600 / 3.0);
        }
        return burnRate * sum;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** „Regie: Über dem Nebelmeer – Die Sonne über dem Nebelmeer · Inversion · 8/8 Stratus 450–850 m · 15,0 °C am Gipfel“ */
    public String weatherLine(double eyeAsl) {
        Weather.Snapshot s = snapshot();
        String src = !regie ? "von Hand" : (s.scenario().isEmpty() ? "freie Regie, " + KlimaBrocken.name(s.phase()) : s.scenario() + ": " + s.stepText())
                + (weather.overrides() > 0 ? ", " + weather.overrides() + " von Hand" : "");
        return String.format(Locale.GERMAN, "Wetter %s · %.0f/8 %s %.0f–%.0f m · Regen %.1f mm/h · Sicht %.0f km · %.1f °C am Gipfel, %.1f °C am Auge · %.0f hPa",
                src, oktas, cloudType.label, s.cloudBase(), s.cloudTop(), rainRate, visibilityKm, airTempC, s.temperatureAt(eyeAsl), pressureHPa);
    }

    /** „Wind 12 m/s aus 260° (Bft 6), Böe 15 m/s“ */
    public String windLine() {
        return String.format(Locale.GERMAN, "Wind am Gipfel %.0f m/s aus %03.0f° (Bft %d), Böe %.0f m/s · Feuchte %.0f %%", windSpeed, windFromDeg,
                Weather.beaufort(windSpeed), gustNow, humidity);
    }
}
