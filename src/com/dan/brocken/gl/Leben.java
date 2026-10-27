package com.dan.brocken.gl;

import com.dan.brocken.scene.Bodenkarte;
import com.dan.brocken.scene.Site;
import com.dan.brocken.scene.Terrain;
import com.dan.brocken.wiring.Wiring;
import com.dan.flock.Flock;
import com.dan.flock.Species;
import com.dan.flock.gl.FlockGl;
import com.dan.ground.Biome;
import com.dan.ground.Meadow;
import com.dan.ground.gl.GroundGl;
import com.dan.reed.Reed;
import com.dan.reed.gl.ReedGl;
import com.dan.smoke.Smoke;
import com.dan.smoke.Source;
import com.dan.smoke.gl.SmokeGl;
import com.jogamp.opengl.GL4;

import java.util.List;
import java.util.Locale;

/**
 * Phase 5: was sich im Wind bewegt (Durchgang „Leben“). Alle drei nehmen den Wind des Felds über ihre eigene
 * Schnittstelle {@code Wind} ({@code wiring::windAt}, mit dem Faktor der Eichung):
 * <ul>
 * <li><b>Gras</b> ({@code com.dan.reed}): Wiesengras nach der Nutzung aus ALKIS – auf der Heide der Kuppe dicht, im Wald
 * das Reitgras zwischen den Fichten [G], im Moor mit Binsen. Jeder Halm neigt sich nach dem Wind 10 m über seinem Boden
 * (Windkarte um die Kamera); am Gipfel stärker als am Osthang (DoD Phase 5).</li>
 * <li><b>Rauch</b> ({@code com.dan.smoke}): die Abluft der Heizung über dem Brockenhotel [G: Schornstein 18 m über
 * Grund, 150 kW], im Wind an ihrem Austritt; bei Sturm verweht sie fast unsichtbar.</li>
 * <li><b>Kolkraben</b> ({@code com.dan.flock}): fünf Kolkraben [G] kreisen im Aufwind am Hang, den das Feld liefert;
 * keine Möwen.</li>
 * <li>Seit Phase 8 die <b>Bodendecke</b> ({@code com.dan.ground}, Biom {@code Biome.brocken()}): Zwergsträucher
 * (Heidelbeere, Krähenbeere, Besenheide), Drahtschmiele, im Moor Wollgras, Sonnentau und Torfmoos, auf dem Blockfeld
 * Steine, im Umkreis von 16 m um das Auge. Was wo wächst, sagt die {@link Bodenkarte}: im Moor nass (Feuchte 1), in
 * der Heide trocken (0,15), im Wald nur wenig. Das Gras aus {@code com.dan.reed} wird dort entsprechend dünner.</li>
 * </ul>
 */
public final class Leben {
    public final Reed gras = Reed.breeze();
    public final Smoke rauch = new Smoke();
    /** Die Quelle am Schornstein des Brockenhotels (seit Phase 13 kommen die Loks dazu, siehe {@code Zugbetrieb}). */
    public final Source hotel;
    public final Flock raben = new Flock();
    final ReedGl grasGl = new ReedGl();
    final SmokeGl rauchGl = new SmokeGl();
    final FlockGl rabenGl = new FlockGl();
    /** Phase 8: Bodendecke des Brockens. */
    public final Meadow boden;
    final GroundGl bodenGl = new GroundGl();
    public volatile boolean showGras = true, showRauch = true, showRaben = true, showBoden = true;
    /** Phase 10: Raueis auf der Bodendecke als leichter Belag 0..0,3 (über den Schnee von com.dan.ground: weiße Spitzen). */
    public volatile float reif;
    /** Phase 11: Schnee am Auge (m, mit Verwehung), Schneefall (mm/h Schneewasser), Wind für die Flocken (wohin x, z; m/s). */
    public volatile double schnee, schneefall;
    public volatile double[] flockenWind = {1, 0, 0};
    /** Phase 11: fallender Schnee aus {@code com.dan.season}. */
    final com.dan.season.gl.SnowfallGl flockenGl = new com.dan.season.gl.SnowfallGl();
    public volatile boolean showFlocken = true;
    /** Reichweite der Bodendecke um das Auge (m) [G]. */
    public static final float BODEN_RADIUS = 16;
    private boolean ready;
    private Reed.Density dichte;
    private Reed.Ground grund;

    /** Schornstein des Brockenhotels (ALKIS „Brockenhotel“, Mitte bei −21, −84) [G: Lage am Dach, Höhe 18 m]. */
    public static final double[] SCHORNSTEIN = {-14, 18, -90};
    /** Wärmeleistung der Heizung (kW) [G]. Bei viel Wind verweht die Fahne fast unsichtbar; sichtbar wird sie an ruhigen Tagen und Abenden. */
    public static final double HEIZUNG = 150;
    /** Zahl der Kolkraben [G]. */
    public static final int RABEN = 5;

    /** Dichte des Grases je Nutzungsart (Reihenfolge wie {@code Terrain.ART}) [G]. */
    public static final double[] GRAS = {0.45, 0.45, 0.5, 0.8, 0.6, 0.15, 1.0, 0, 0.15, 0.05, 0, 0.1};

    public Leben(Wiring w, Terrain t) { this(w, t, null); }

    /** Phase 12: Abstand zum Gleis (m); auf dem Schotter wächst nichts. Null = ohne Gleis. */
    public static volatile java.util.function.DoubleBinaryOperator gleis;

    /** Wie viel Bewuchs neben dem Gleis bleibt: nichts auf Schotter und Planum (bis 2,5 m), dann zunehmend [G]. */
    static double gleisFrei(double x, double z) {
        java.util.function.DoubleBinaryOperator g = gleis;
        if (g == null) return 1;
        double d = g.applyAsDouble(x, z);
        return d >= 4 ? 1 : Math.max(0, (d - 2.5) / 1.5);
    }

    public Leben(Wiring w, Terrain t, Bodenkarte karte) {
        gras.wind = w::windAt;
        rauch.wind = w::windAt;
        raben.wind = w::windAt;
        gras.seed = 1141;
        gras.gust = 0.35;
        dichte = (x, z, k) -> {
            int a = t.artAt(x, z) & 0xFF;
            double g = (a < GRAS.length ? GRAS[a] : 0.3) * gleisFrei(x, z);
            if (karte != null) g *= 1 - 0.85 * Math.max(karte.heide(x, z), karte.moor(x, z));   // dort trägt die Bodendecke
            return switch (k) {
                case GRAS -> g;
                case BINSE -> a == Terrain.MOOR ? 0.5 : 0;
                default -> 0;
            };
        };
        grund = t::height;
        boden = new Meadow(Biome.brocken(), new com.dan.ground.Site() {
            @Override public float height(double x, double z) { return (float) t.height(x, z); }

            @Override public void cover(double x, double z, float[] o) { bedeckung(karte, t, x, z, o); }
        });
        boden.radius = BODEN_RADIUS;
        boden.near = 7;
        boden.seed = 1141;
        double y0 = t.height(SCHORNSTEIN[0], SCHORNSTEIN[2]);
        hotel = new Source(SCHORNSTEIN[0], y0 + SCHORNSTEIN[1], SCHORNSTEIN[2], HEIZUNG, 1, 0.03, 1141);
        rauch.setSources(List.of(hotel));
        raben.seed = 1141;
        raben.gullCount = 0;
        raben.ravens = true;
        raben.mix[Species.KRAEHE.ordinal()] = RABEN;
        raben.homeX = 0;
        raben.homeZ = 0;
        raben.homeRadius = 1800;
        raben.ground = t::height;
    }

    /**
     * Bedeckung der Bodendecke [G]: Gras (Zwergsträucher und Drahtschmiele), Blumen (Wollgras, Sonnentau), Steine,
     * offene Erde (Torfmoos), Feuchte. Im Wald wenig, auf Bauten und Wegen nichts.
     */
    public static void bedeckung(Bodenkarte k, Terrain t, double x, double z, float[] o) {
        int a = t.artAt(x, z) & 0xFF;
        double moor = k == null ? (a == Terrain.MOOR ? 1 : 0) : k.moor(x, z), heide = k == null ? (a == Terrain.HEIDE ? 1 : 0) : k.heide(x, z);
        double block = k == null ? 0 : k.block(x, z);
        boolean bebaut = a == Terrain.SIEDLUNG || a == Terrain.WEG || a == Terrain.STRASSE || a == Terrain.BAHN || a == Terrain.WASSER;
        double wald = Math.max(0, 1 - Math.max(Math.max(moor, heide), block));
        double g = bebaut ? 0 : Math.min(1, heide * 0.95 + moor * 0.85 + block * 0.35 + wald * 0.25);
        double fr = gleisFrei(x, z);
        g *= fr;
        o[0] = (float) g;
        o[1] = (float) (bebaut ? 0 : (moor * 0.9 + heide * 0.15) * fr);
        o[2] = (float) (bebaut ? 0 : Math.min(1, block * 0.9 + heide * 0.1) * fr);
        o[3] = (float) (bebaut ? 0 : moor * 0.95);
        o[4] = (float) Math.min(1, 0.15 + 0.85 * moor);
    }

    void init(GL4 gl) {
        bodenGl.init(gl);
        grasGl.init(gl);
        rauchGl.init(gl);
        rabenGl.init(gl);
        try {
            flockenGl.init(gl);
        } catch (RuntimeException e) {
            System.err.println("Brocken: Schneefall: " + e.getMessage());
        }
        ready = true;
    }

    /** Jahreszeit des Grases 0 Sommer … 1 Winter aus dem Tag im Jahr (am Gipfel spät grün, früh gelb) [G]. */
    public static double season(int doy) {
        if (doy < 110 || doy > 335) return 1;
        if (doy < 165) return 1 - (doy - 110) / 55.0;
        if (doy < 215) return 0;
        if (doy < 290) return 0.75 * (doy - 215) / 75.0;
        return 0.75 + 0.25 * (doy - 290) / 45.0;
    }

    /** Je Bild: Zeit, Jahreszeit, Licht, Wind der Lage für die Fallbacks, Stabilität für den Rauch. */
    void step(double dt, int doy, double sunAlt, double oktas, Wiring w) {
        // Phase 11: die Bodendecke unter Schnee (der Schnee von com.dan.ground drückt sie nieder, 40 cm decken sie zu [G])
        boden.setSeason(doy, (float) Math.max(reif, Math.min(1, schnee / 0.4)));
        boden.wind.speed = (float) w.windSpeed;
        boden.wind.direction = (float) w.windFromDeg;
        boden.wind.gustiness = (float) Math.max(0.2, Math.min(0.8, w.gustiness * 2));
        gras.season = season(doy);
        gras.snowDepth = schnee;
        gras.windSpeed = w.windSpeed;
        gras.windFromDeg = w.windFromDeg;
        gras.gust = Math.max(0.2, Math.min(0.6, w.gustiness * 1.6));
        rauch.windSpeed = w.windSpeed;
        rauch.windFromDeg = w.windFromDeg;
        rauch.stability = Smoke.stability(sunAlt, w.windSpeed, oktas / 8);
        rauch.step(dt);
        raben.windSpeed = w.windSpeed;
        raben.windFromDeg = w.windFromDeg;
        raben.ambientLux = sunAlt > -6 ? 400 + 100_000 * Math.max(0, Math.sin(Math.toRadians(sunAlt))) : 5;
        raben.dayOfYear = doy;
        if (showRaben) raben.step(dt);
    }

    /** Zeichnet Gras und Kolkraben ins Ziel mit Tiefe; Licht vorbelichtet (Sonne und Mond als Bestrahlung, Himmel als Bestrahlung/π). */
    void drawOpaque(GL4 gl, float[] vp, double[] eye, boolean reversed, double[] sunDir, double[] sun, double[] moonDir, double[] moon, double[] skyPi,
                    double fogDist, double sunAtEye, double t, double aboveGround) {
        if (!ready) return;
        // Bodendecke nur nah am Boden (Augenhöhe unter 12 m)
        bodenGl.update(gl, boden, (float) t, eye[0], eye[2], !showBoden || aboveGround > 12);
        if (showBoden && aboveGround <= 12) {
            GroundGl.View v = new GroundGl.View();
            v.viewProj = vp;
            v.eye = eye.clone();
            v.reversedZ = reversed;
            GroundGl.Light l = new GroundGl.Light();
            l.sunDir = sunDir.clone();
            l.sunColor = scale(sun, sunAtEye / Math.PI);
            l.moonDir = moonDir.clone();
            l.moonColor = scale(moon, 1 / Math.PI);
            l.skyColor = skyPi.clone();
            l.fogColor = scale(skyPi, 2.5);
            l.fogDistance = fogDist;
            bodenGl.draw(gl, v, l);
        }
        // Gras nur, wenn das Auge nah genug am Boden ist, dass Halme in Reichweite liegen
        if (showGras && aboveGround < gras.range(Reed.Kind.BINSE) * 1.2) {
            grasGl.update(gl, gras, eye[0], eye[2], dichte, grund);
            ReedGl.View v = new ReedGl.View();
            v.viewProj = vp;
            v.eye = eye.clone();
            v.reversedZ = reversed;
            ReedGl.Light l = new ReedGl.Light();
            l.sunDir = sunDir.clone();
            l.sunColor = scale(sun, sunAtEye / Math.PI);
            l.moonDir = moonDir.clone();
            l.moonColor = scale(moon, 1 / Math.PI);
            l.skyColor = skyPi.clone();
            l.skyWeight = 1;
            l.fogColor = scale(skyPi, 2.5);
            l.fogDistance = fogDist;
            grasGl.draw(gl, gras, v, l, t);
        }
        if (showRaben) {
            FlockGl.View v = new FlockGl.View();
            v.viewProj = vp;
            v.eye = eye.clone();
            v.reversedZ = reversed;
            FlockGl.Light l = new FlockGl.Light();
            l.sunDir = sunDir.clone();
            l.sunColor = sun.clone();
            l.moonDir = moonDir.clone();
            l.moonColor = moon.clone();
            l.skyTop = skyPi.clone();
            l.skyHorizon = scale(skyPi, 1.3);
            l.fogDistance = fogDist;
            rabenGl.draw(gl, raben, v, l);
        }
    }

    /** Phase 11: fallender Schnee um das Auge (nach den undurchsichtigen Flächen, mit Tiefentest, gemischt). */
    void drawFlocken(GL4 gl, float[] vp, double[] eye, boolean reversed, int w, int h, double fovy, double[] sunDir, double[] sun, double[] moonDir,
                     double[] moon, double[] skyPi, double fogDist, double t) {
        if (!ready || !showFlocken || !flockenGl.ready() || schneefall <= 0.01) return;
        com.dan.season.gl.SeasonGl.View v = new com.dan.season.gl.SeasonGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        v.reversedZ = reversed;
        v.width = w;
        v.height = h;
        v.fovyDeg = fovy;
        com.dan.season.gl.SeasonGl.Light l = new com.dan.season.gl.SeasonGl.Light();
        l.sunDir = sunDir.clone();
        l.sunColor = scale(sun, 1 / Math.PI);
        l.moonDir = moonDir.clone();
        l.moonColor = scale(moon, 1 / Math.PI);
        l.sky = skyPi.clone();
        l.fogColor = scale(skyPi, 2.5);
        l.fogDistance = fogDist;
        double[] fw = flockenWind;
        flockenGl.draw(gl, v, l, schneefall, new double[]{fw[0], fw[1]}, fw[2], t);
    }

    /** Zeichnet den Rauch durchscheinend ins Farbziel ohne Tiefe; die Tiefe der Szene wird gelesen. */
    void drawRauch(GL4 gl, float[] vp, double[] eye, boolean reversed, int depthTex, double near, double far, double[] sunDir, double[] sun, double[] moonDir,
                   double[] moon, double[] skyPi, double fogDist) {
        if (!ready || !showRauch) return;
        rauch.select(eye[0], eye[1], eye[2]);
        rauchGl.update(gl, rauch);
        SmokeGl.View v = new SmokeGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        v.reversedZ = reversed;
        v.depthTexture = depthTex;
        v.near = near;
        v.far = far;
        SmokeGl.Light l = new SmokeGl.Light();
        l.sunDir = sunDir.clone();
        l.sunColor = scale(sun, 1 / Math.PI);
        l.moonDir = moonDir.clone();
        l.moonColor = scale(moon, 1 / Math.PI);
        l.skyTop = skyPi.clone();
        l.skyHorizon = scale(skyPi, 1.3);
        l.fogColor = scale(skyPi, 2.5);
        l.fogDistance = fogDist;
        rauchGl.draw(gl, v, l, 1, rauch.time());
    }

    /** „Gras 12 400 Halme (Windkarte), am Auge 45 % geneigt · Rauch … · 5 Kolkraben, 3 im Aufwind“ */
    public String zeile(double x, double z, double ground) {
        int n = 0, soar = raben.ravensSoaring();
        for (Flock.Gull g : raben.gulls()) if (g.raven && g.state != Flock.State.AWAY) n++;
        double rise = rauch.sources().isEmpty() ? 0 : rauch.finalRise(rauch.sources().get(0));
        return String.format(Locale.GERMAN, "Gras %,d Halme%s, am Auge %.0f %% der Halmhöhe geneigt · Rauch am Brockenhotel, steigt %.0f m · %d Kolkraben, %d im Aufwind",
                grasGl.instances(), grasGl.windMapOn() ? " (Windkarte)" : "", 100 * neigung(x, z, ground), rise, n, soar);
    }

    /** Kolkraben ohne Bild vorlaufen lassen (Werkzeuge, Prüfung): s Sekunden in Schritten zu 1/30 s. */
    public void vorlauf(double s) {
        for (double t = 0; t < s; t += 1 / 30.0) raben.step(1 / 30.0);
    }

    /** Neigung des Grases (Anteil der Halmhöhe, ohne Böen) an einem Ort mit dem Wind dort. */
    public double neigung(double x, double z, double ground) {
        double[] w = gras.windAt(x, ground, z);
        return Reed.lean(w[2], Reed.Kind.GRAS.stiffness);
    }

    /** „Bodendecke 180 000 Dreiecke …“ */
    public String bodenZeile() { return bodenGl.info(); }

    void dispose(GL4 gl) {
        bodenGl.dispose(gl);
        grasGl.dispose(gl);
        rauchGl.dispose(gl);
        rabenGl.dispose(gl);
        flockenGl.dispose(gl);
        ready = false;
    }

    static double[] scale(double[] a, double k) { return new double[]{a[0] * k, a[1] * k, a[2] * k}; }
}
