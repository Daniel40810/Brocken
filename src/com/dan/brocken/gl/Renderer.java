package com.dan.brocken.gl;

import com.dan.brocken.app.Bibliothek;
import com.dan.brocken.camera.Ansichten;
import com.dan.brocken.camera.Camera;
import com.dan.brocken.scene.Buehne;
import com.dan.brocken.scene.Physik;
import com.dan.brocken.scene.SceneClock;
import com.dan.brocken.scene.Site;
import com.dan.brocken.scene.Sun;
import com.dan.brocken.scene.Terrain;
import com.dan.brocken.scene.Windrechner;
import com.dan.brocken.wiring.Wiring;
import com.dan.cloud.Clouds;
import com.dan.cloud.gl.CloudGl;
import com.dan.sky.Sky;
import com.dan.sky.gl.SkyGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Die Engine auf der Grafikkarte, übernommen aus Geiranger und entschlackt (F5 A): alles, was zum Fjord, zu den
 * Fällen, zur Fv63, zum Ort, zu Schiffen und Menschen gehörte, ist heraus; geblieben ist das Gerüst, an das die Phasen
 * 4 bis 18 ihre Durchgänge hängen. Je Bild seit Phase 3:
 * <ol>
 * <li><b>Schatten</b> (seit Phase 3): die Schatten der Kuppe und der Täler je Sonnenstand ({@link SchattenKarte},
 * ein Streifen je Bild, sobald sich die Sonne gedreht hat).</li>
 * <li><b>Himmel</b> (seit Phase 3 aus {@code com.dan.sky}): Streuung über 51,8° N in der Augenhöhe, Sonne, Mond,
 * Sterne in einen HDR-Puffer (RGBA16F, umgekehrte 32F-Tiefe), dazu das Rundumbild mit Wolken für Gelände und Dunst und
 * die Karte der Wolkenschatten; ohne {@code sky} der Verlauf von Phase 1.</li>
 * <li><b>Gelände</b> (seit Phase 2): Kern (DGM1, 2 m), Hauptraster (GLO-30, 12,5 m) und Fernring (DGM200, 200 m) aus
 * {@link Terrain} als CDLOD ({@link TerrainLod}), im Kern bis 2 m Punktabstand, mit Erdkrümmung; Farben nach der
 * Nutzungsart aus ALKIS, seit Phase 8 der Boden, darauf zuschaltbar die Marken ({@code terrain.frag}, {@code marken.glsl}); seit Phase 3
 * im Licht von Sonne, Mond und Himmel, mit Schatten von Kuppe und Wolken und Dunst nach der Sicht. Ohne
 * {@code daten/gelaende} die Kuppe der Bühne von Phase 1 ({@link Buehne}).</li>
 * <li><b>Nebel</b> (seit Phase 6, {@code com.dan.fog.TerrainFog}): Nebelmeer, Talnebel und Stau am Luvhang als Bilddurchgang über die Szene;
 * seit Phase 7 in halber oder drittel Auflösung nach der Stufe, mit dem Licht der Tröpfchen nach Mie ({@code com.dan.sky.Droplets}:
 * Glorie, Nebelbogen), dem Schatten des Wanderers am Auge (Brockengespenst) und dem Schatten der Kuppe im Nebel.</li>
 * <li><b>Leben</b> (seit Phase 5, {@link Leben}): Gras, Kolkraben und der Rauch am Brockenhotel, alle im Wind des Felds.</li>
 * <li><b>Wind</b> (seit Phase 4, {@code com.dan.wind}): Pfeile des massenkonsistenten Felds über dem Gelände (aus der
 * 3D-Textur, {@code wind_pfeile.vert}) und Rauchfäden; gelöst wird im Faden „Windfeld“ ({@link Windrechner}), wenn sich
 * Richtung, Schichtung oder Erwärmung ändern.</li>
 * <li><b>Wolken</b> (seit Phase 3, {@code com.dan.cloud}): nach dem Gelände mit seiner Tiefe, damit Nebelmeer und
 * Kappe am Berg vor den Hängen liegen. Die Wetterregie mit dem Klima „Brocken“ ({@link Wiring}) setzt die Eingänge.</li>
 * <li><b>Nachbearbeitung</b>: Filmkurve, Gamma ins Fenster; seit Phase 3 mit Augenanpassung an Himmel und Bild
 * ({@link Belichtung}), nachts mit Stäbchensehen.</li>
 * <li><b>HUD</b>: Tempo (Bilder/s, Zeit der Grafikkarte je Durchgang, Prozessor), Uhr mit Datum, Sonne, Kamera.</li>
 * </ol>
 * Geblieben aus Geiranger: Qualitätsstufen mit Auflösungsregelung, Zeitmessung jedes Durchgangs auf der Grafikkarte
 * (Zeitstempel, ein Bild versetzt gelesen), Compute-Test beim Start, HUD kurz/voll/aus, Foto (F12), Shader neu laden
 * (F5), Standbilder ohne Fenster ({@code tools.Tempo}), der Faden der Physik ({@link Physik}) mit festem Schritt. Im
 * Bildlauf werden keine Dateien gelesen. Seit Phase 16 Regiebücher ({@code scene.Regie}) und Kino ({@code com.dan.kino});
 * Chronik und Dauerlauf kommen mit Phase 17 und 18.
 */
public final class Renderer implements GLEventListener {
    public static final int MIN_MAJOR = 4, MIN_MINOR = 3;
    public static final String VERSION = "Phase 18";
    /** Phase des Stands (für Ansichten und Bedienfeld). */
    public static final int PHASE = 12;
    public static final String[] PASSES = {"Schatten", "Himmel", "Gelände", "Fels", "Wald", "Bahn", "Gipfel", "Menschen", "Leben", "Nebel", "Wind", "Wolken", "Nachbearbeitung", "HUD"};
    private static final int MARKS = PASSES.length + 1;
    /** Nah- und Fernebene (m): bis über den Fernring. */
    static final double NEAR = 0.5, FAR = 150_000;

    public final SceneClock clock;
    public final Camera camera = new Camera();
    /** Der Faden der Physik (F5 A); Phase 1 ohne Aufgaben. */
    public final Physik physik = new Physik();
    /** Phase 2: das Gelände (Kern, Hauptraster, Fernring) und seine Detailstufen. */
    public final Terrain terrain;
    public final TerrainLod lod = new TerrainLod();
    /** Phase 3: Wetterregie und Verdrahtung, Himmel und Wolken, Schatten des Geländes, Messung der Belichtung. */
    public final Wiring wiring;
    public final Wetter wetter = new Wetter();
    public final SchattenKarte schatten = new SchattenKarte();
    private final Belichtung belichtung = new Belichtung();
    /** Wolken zeigen, Belichtung im Bild messen (sonst aus Himmel und Blickfeld geschätzt). */
    public volatile boolean showClouds = true, bildMessung = !Boolean.getBoolean("brocken.belichtung.schaetzen");
    /** Phase 4: das Windfeld (Faden „Windfeld“), Pfeile und Rauchfäden in der Szene, Höhe der Pfeile über Grund (m). */
    public final Windrechner windrechner = new Windrechner();
    /** Phase 5: was sich im Wind bewegt – Gras, Rauch am Brockenhotel, Kolkraben (Durchgang „Leben“). */
    public final Leben leben;
    /** Phase 8: was auf dem Boden liegt und wächst (geschätzt aus dem Gelände) und Fels aus com.dan.cliff. */
    public final com.dan.brocken.scene.Bodenkarte boden;
    public final Fels fels;
    private int bodenTex;
    /** Phase 9: der Wald (geschätzte Karte über dem Hauptraster) und seine Bäume aus com.dan.forest. */
    public final com.dan.brocken.scene.Waldkarte waldkarte;
    /** Phase 12: die Brockenbahn (null ohne ALKIS-Auszug) und ihr Gleis als Netz. */
    public final com.dan.brocken.scene.Strecke strecke;
    final com.dan.rail.gl.RailGl gleisGl = new com.dan.rail.gl.RailGl();
    private int trasseTex, trasseMaske;
    private boolean gleisOk;
    /** Phase 12: Gleis zeigen. */
    public volatile boolean showGleis = true;
    /** Phase 14: die Bauten am Gipfel und die Menschen (Wanderer, Besucher, Fahrgäste). */
    public final com.dan.brocken.scene.Gipfel gipfel;
    final GipfelGl gipfelGl = new GipfelGl();
    public final com.dan.brocken.scene.Menschen menschen;
    final com.dan.figure.gl.FigureGl figurGl = new com.dan.figure.gl.FigureGl();
    private boolean figurOk;
    public volatile boolean showGipfel = true, showMenschen = true;
    /** Phase 15: der Klang des Bergs (Wind, Züge, Vögel, Eis, Stimmen, Walpurgisnacht); der Lautsprecher läuft nur in der App. */
    public final com.dan.brocken.wiring.KlangBrocken klang;
    /** Phase 13: der Betrieb nach Fahrplan (null ohne Strecke), Züge und Ausweichgleise als Netz; Züge zeigen. */
    public final com.dan.brocken.scene.Zugbetrieb zugbetrieb;
    final com.dan.rail.gl.TrainGl zugGl = new com.dan.rail.gl.TrainGl();
    final java.util.List<com.dan.rail.gl.RailGl> ausweichGl = new java.util.ArrayList<>();
    private boolean zugOk;
    public volatile boolean showZuege = true;
    /** Phase 11: Verwehungen und Wechten über dem Hauptraster ({@code com.dan.season.Drift}, Wind aus 250°). */
    public final com.dan.season.Drift verwehung;
    private int driftTex;
    /** Phase 11: Schnee zeigen (Gelände, Wald, Fels, Bodendecke, Flocken). */
    public volatile boolean showSchnee = true;
    public final Wald wald;
    private int waldTex;
    /** Phase 6: Nebel über Gelände (Nebelmeer, Talnebel, Stau am Luvhang) aus {@code com.dan.fog}. */
    public final com.dan.fog.TerrainFog nebel = new com.dan.fog.TerrainFog();
    final com.dan.fog.gl.TerrainFogGl nebelGl = new com.dan.fog.gl.TerrainFogGl();
    public volatile boolean showNebel = true;
    /** Phase 7: die Phasenfunktion der Tröpfchen als Textur; Glorie, Gespenst und Schatten der Kuppe im Nebel an/aus. */
    final com.dan.sky.gl.DropletGl tropfenGl = new com.dan.sky.gl.DropletGl();
    public volatile boolean showGlorie = true;
    /** Phase 7: so hoch (m über Grund) steht das Auge höchstens noch als Wanderer, der einen Schatten wirft. */
    public static final double STEHEND = 2.5;
    private volatile String gespenst = "";
    private double gespenstTimer;
    /** Übernimmt der Nebel gerade die untere Wolkenschicht (Nebelmeer)? */
    private volatile boolean nebelMeer;
    private double nebelUhr = Double.NaN;
    final com.dan.wind.gl.WindGl windGl = new com.dan.wind.gl.WindGl();
    /** Pfeile und Rauchfäden des Windfelds: Werkzeug zum Bauen, seit Phase 17a wie die Marken von Anfang an aus (Block WIND). */
    public volatile boolean showArrows, showFaeden;
    public volatile double arrowHeight = 10;
    private Windrechner.Ergebnis windSeen, windUploaded;
    private double windGainUploaded = -1;
    private int fadenVao, fadenVbo, fadenPunkte;
    private int[] fadenStart = new int[0], fadenCount = new int[0];
    private int fadenAuto;
    /** Phase 4: Quellen der Rauchfäden, die Strg + Klick setzt (bis Phase 16 Umschalt) (x, z; höchstens {@link #EIGENE_FAEDEN}, der älteste fällt weg). */
    private final java.util.List<double[]> eigeneQuellen = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile boolean eigeneNeu;
    public static final int EIGENE_FAEDEN = 8;
    /** Anpassung des Auges: 0 = feste Belichtung des Tags, 1 = volle Anpassung an das Bild (wie com.dan.sky). */
    public volatile double adaptation = 0.85;
    /** Gewicht der Bildmessung gegen den Himmel (Rest) und ihr Faktor (mittleres Grau statt Mittel). */
    public static final double BILD_FOLGT = 0.75, BILD_GAIN = 2.0;
    /** Wie stark eine dunkle Decke die Anpassung mitnimmt (Exponent auf com.dan.cloud.brightnessFactor). */
    public static final double STORM_ADAPT = 0.6;
    private double adaptLum = -1, viewLum = -1, viewTimer, cloudShade = 1, sunAtEye = 1, sunAtEyeTimer;
    private boolean frameSky;
    private Sky.State frameSt;
    private double framePre = 1;

    private volatile Quality quality = Quality.MITTEL;
    public volatile double renderScale = quality.renderScale;
    /** Auflösung nach der Zeit der Grafikkarte regeln (nie unter {@link #MIN_SCALE}, nie über der Stufe). */
    public volatile boolean autoScale = true;
    public volatile double targetFps = 60;
    public static final double MIN_SCALE = 0.5;
    private long scaleNanos;
    private int scaleSteps;
    /** HUD an, kurz oder voll (H). */
    public volatile boolean showHud = true, hudFull;
    /** Die Marken zur Ausrichtung (Raster, Höhenlinien, Hauptraster, Kern, Orte); seit Phase 1 Hilfe für den Bau, seit
     *  Phase 17 von Anfang an aus (das Gelände sah mit ihnen aus wie der Platzhalter), im Block TECHNIK zuzuschalten. */
    public volatile boolean showMarks;
    /** Szene steht (für Standbilder und die Leertaste im Kino später). */
    public volatile boolean frozen;
    /** Belichtung von Hand in Blenden. */
    public volatile double exposureBias;
    public volatile boolean photoRequest;
    /** Ein Bild abholen (einmal, im GL-Faden), etwa für Werkzeuge. */
    public volatile Consumer<BufferedImage> grabRequest;
    /** Ordner und Name des nächsten Fotos (ohne Angabe docs/status und {@code Brocken_Datum_Zeit}). */
    public volatile File photoDir;
    public volatile String photoName;

    private final Shader skyShader = new Shader("Himmel", null, "fullscreen.vert", "sky.frag"),
            terrainShader = new Shader("Gelände", null, "terrain.vert", "terrain.frag"),
            schattenShader = new Shader("Schatten", null, "fullscreen.vert", "schatten.frag"),
            lumShader = new Shader("Belichtung", null, "fullscreen.vert", "lum.frag"),
            pfeilShader = new Shader("Windpfeile", null, "wind_pfeile.vert", "wind_linie.frag"),
            fadenShader = new Shader("Rauchfäden", null, "wind_faden.vert", "wind_linie.frag"),
            post = new Shader("Nachbearbeitung", null, "fullscreen.vert", "post.frag"),
            overlay = new Shader("Überlagerung", null, "fullscreen.vert", "overlay.frag");
    private final Shader[] all = {skyShader, terrainShader, schattenShader, lumShader, pfeilShader, fadenShader, post, overlay};
    private final HudOverlay hud = new HudOverlay();
    private int emptyVao, hdrTex, depthTex, hdrFbo, hdrColorFbo, hdrW, hdrH, surfW = 1, surfH = 1, defaultFbo;
    private boolean reversedZ, ready;
    private final int[][] stamp = new int[2][MARKS];
    private final double[] passMs = new double[MARKS - 1];
    private int frame;
    private long lastNanos, statsNanos;
    private int statsFrames;
    private double fps, gpuMs, cpuMs, worstMs;
    private volatile String glInfo = "", error;
    private volatile ComputeProbe.Result compute;
    private volatile boolean reloadShaders;
    private Consumer<String> statusListener = s -> { };
    private Consumer<String> errorListener = s -> { };
    private volatile String photoMsg = "", meldung = "";
    private volatile long photoMsgUntil, meldungUntil;

    public Renderer(SceneClock clock) { this(clock, Terrain.load(Terrain.defaultDir())); }

    public Renderer(SceneClock clock, Terrain terrain) {
        this.clock = clock;
        this.terrain = terrain;
        this.wiring = new Wiring(clock);
        wiring.setTerrain(terrain);
        this.boden = com.dan.brocken.scene.Bodenkarte.build(terrain);
        System.out.println("Brocken: " + boden.describe());
        this.leben = new Leben(wiring, terrain, boden);
        this.fels = new Fels(terrain, boden);
        // Phase 12: die Strecke vor dem Wald (am Gleis wachsen keine Bäume) und der Bahnkörper ans Gelände
        this.strecke = com.dan.brocken.scene.Strecke.build(terrain);
        if (strecke != null) {
            terrain.korrektur = strecke.korrektur();
            Leben.gleis = strecke::abstand;
            System.out.println("Brocken: " + strecke.describe());
        } else Leben.gleis = null;
        // Phase 14: die Bauten am Gipfel; das Gelände darunter eben (in der Änderung der Strecke)
        this.gipfel = com.dan.brocken.scene.Gipfel.build(terrain);
        if (strecke != null) {
            gipfel.einebnen(strecke);
            terrain.korrektur = strecke.korrektur();
            Leben.gleis = (x, z) -> Math.min(strecke.abstand(x, z), gipfel.abstandBau(x, z) + 1.5);
        }
        System.out.println("Brocken: " + gipfel.describe());
        // Phase 13: Züge nach Fahrplan; der Dampf am Schornstein zieht mit dem Wind relativ zur Lok
        this.zugbetrieb = strecke != null ? new com.dan.brocken.scene.Zugbetrieb(strecke) : null;
        if (zugbetrieb != null && zugbetrieb.ok()) {
            System.out.println(String.format(Locale.GERMAN, "Brocken: Zugbetrieb · Vorlauf Ostrand–Schierke %.1f min · Goetheweg–Schierke bergab %.1f min",
                    zugbetrieb.vorlauf() / 60, zugbetrieb.talGS() / 60));
            leben.rauch.wind = (x, y, z) -> zugbetrieb.windRelativ(wiring.windAt(x, y, z), x, y, z);
        }
        this.menschen = new com.dan.brocken.scene.Menschen(terrain, strecke, gipfel, zugbetrieb);
        System.out.println("Brocken: " + menschen.describeWege());
        this.klang = new com.dan.brocken.wiring.KlangBrocken(wiring, terrain, gipfel, zugbetrieb, menschen);
        leben.raben.onCall = (x, y, z, st, g) -> klang.rabe(x, y, z, st);
        System.out.println("Brocken: " + klang.describe());
        this.waldkarte = com.dan.brocken.scene.Waldkarte.build(terrain, boden);
        System.out.println("Brocken: " + waldkarte.describe());
        this.wald = new Wald(terrain, boden, waldkarte);
        // Phase 11: Verwehungen aus dem Gelände nach dem Wind des Winters (über das Jahr am Gipfel meist aus West bis
        // Südwest [G]), auf dem Raster der Waldkarte
        this.verwehung = new com.dan.season.Drift();
        verwehung.fromDeg = 250;
        verwehung.stepM = 25;
        // Wechten: das Raster zu 25 m glättet Kanten; schon ein Abbruch ab 10° bis 60 m hinter einer freien Kante zählt [G]
        verwehung.corniceDrop = 10;
        verwehung.corniceSx = 5;
        verwehung.leeReach = 60;
        verwehung.build(terrain::height, waldkarte.x0, waldkarte.z0, com.dan.brocken.scene.Waldkarte.CELL, waldkarte.nx, waldkarte.nz);
        System.out.println("Brocken: " + verwehung.describe());
        // Phase 6: der Nebel kennt Gelände, Kaltluft und Wind über das Hauptraster
        nebel.ground = terrain::height;
        nebel.cold = (x, z) -> wiring.wind.coldPoolAt(x, z, 0);
        nebel.wind = wiring::windAt;
        nebel.windAnchor = new double[]{0, Site.GIPFEL + 30, 0};
        double[] hr = Site.HAUPTRASTER.scene();
        nebel.mapRect = new double[]{hr[0], hr[1], Math.max(hr[2] - hr[0], hr[3] - hr[1])};
        nebel.seed = 1141;
        wetter.sky.latDeg = Site.LAT;
        wetter.sky.lonDeg = Site.LON;
        wetter.sky.epochMillis = clock.epochMillis();
        lod.use(terrain);
        setQuality(quality);
        camera.groundLevel = groundHeight(camera.eye[0], camera.eye[2]);
        camera.waterLevel = camera.groundLevel;
        camera.targetGround = groundHeight(camera.tx, camera.tz);
        camera.snap();
    }

    /** Für Prüfungen und Werkzeuge: Uhr am Start, Gelände aus daten/gelaende. */
    public Renderer() { this(new SceneClock()); }

    public void setStatusListener(Consumer<String> l) { statusListener = l; }
    public void setErrorListener(Consumer<String> l) { errorListener = l; }
    public String glInfo() { return glInfo; }
    public String error() { return error; }
    public double fps() { return fps; }
    public double gpuMs() { return gpuMs; }
    public double cpuMs() { return cpuMs; }
    public boolean reversedDepth() { return reversedZ; }
    public ComputeProbe.Result compute() { return compute; }
    public Quality quality() { return quality; }
    public void requestShaderReload() { reloadShaders = true; }
    public double[] lastPassTimes() { return passMs.clone(); }
    public long frames() { return frame; }
    public double takeWorstFrameMs() { double w = worstMs; worstMs = 0; return w; }
    public double fovy() { return Camera.FOVY; }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Alle Shader-Programme mit ihren Dateien (für die Prüfung). */
    public static String[][] shaderFiles() {
        Renderer r = new Renderer();
        String[][] f = new String[r.all.length][];
        for (int i = 0; i < f.length; i++) f[i] = r.all[i].files();
        return f;
    }

    public void setQuality(Quality q) {
        quality = q;
        renderScale = q.renderScale;
        lod.lodRange = q.terrainRange;
        lod.finest = q.terrainFine;
        lod.finestKern = q.terrainKern;
        wetter.clouds.quality = q.clouds;
        wiring.wind.quality = q.wind;
        nebel.quality = com.dan.fog.TerrainFog.Quality.values()[q.ordinal()];
        // Phase 10: der Rechenschritt des Eises (600 s bis 60 s) folgt der Stufe
        wiring.raueis.rime.quality = com.dan.rime.Rime.Quality.values()[q.ordinal()];
        // Phase 5: Gras und Rauch folgen der Stufe eins zu eins (Paketvertrag Punkt 7)
        leben.gras.quality = com.dan.reed.Reed.Quality.values()[q.ordinal()];
        leben.rauch.quality = com.dan.smoke.Smoke.Quality.values()[q.ordinal()];
        // Phase 9: Reichweite, Stufen und Abstand des Walds
        if (wald != null) wald.quality(com.dan.forest.ForestField.Quality.values()[q.ordinal()]);
    }

    /** Höhe des Grunds am Ort: das Gelände (ohne daten/gelaende die Kuppe der Bühne). */
    public double groundHeight(double x, double z) { return terrain.height(x, z); }

    /** Stellt die Kamera auf eine Ansicht nach Namen. */
    public boolean placeView(Ansichten.Ansicht a) {
        boolean ok = Ansichten.apply(a, camera, PHASE, this::groundHeight);
        if (ok) hudMeldung("Ansicht " + a);
        return ok;
    }

    /**
     * Wetter nach Namen (Bedienfeld, Werkzeuge): ein Regiebuch des Klimas „Brocken“ (die Uhr geht an seinen Beginn am
     * Tag der Uhr, plus {@code stunde}) oder eine Lage (Name der Regie oder des Klimas, etwa INVERSION oder „Inversion“,
     * freie Regie ab jetzt). Rückgabe: was gesetzt wurde, oder null.
     */
    public String wetter(String name, double stunde) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        for (com.dan.weather.Scenario sc : com.dan.brocken.wiring.KlimaBrocken.regiebuecher())
            if (sc.name().equalsIgnoreCase(n) || schlicht(sc.name()).equals(schlicht(n))) {
                clock.set(clock.date(), sc.startHour());
                wiring.start(sc);
                if (stunde > 0) clock.setEpochMillis(clock.epochMillis() + stunde * 3_600_000);
                wiring.feedWeather(0);
                readapt();
                nebelUhr = Double.NaN;
                return "Regiebuch " + sc.name();
            }
        for (com.dan.weather.Weather.Phase p : com.dan.weather.Weather.Phase.values())
            if (p.name().equalsIgnoreCase(n) || schlicht(p.label).equals(schlicht(n)) || schlicht(com.dan.brocken.wiring.KlimaBrocken.name(p)).equals(schlicht(n))) {
                wiring.startFree(p);
                readapt();
                nebelUhr = Double.NaN;
                return "Lage " + com.dan.brocken.wiring.KlimaBrocken.name(p);
            }
        return null;
    }

    /** Namen ohne Leerzeichen, Satzzeichen und Umlaute („Über dem Nebelmeer“ = „UeberdemNebelmeer“). */
    static String schlicht(String t) {
        return t.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss").replaceAll("[^a-z0-9]", "");
    }

    /** Eine Meldung für 5 s im HUD (und in der Statuszeile). */
    public void hudMeldung(String text) {
        meldung = text;
        meldungUntil = System.currentTimeMillis() + 5000;
    }

    /**
     * Belichtung: seit Phase 3 aus der Augenanpassung an Himmel und Bild ({@code com.dan.sky}, gemessen mit
     * {@link Belichtung}), ohne Himmel aus der Sonnenhöhe des Platzhalters (am Tag rund 1,9).
     */
    public double exposure() {
        if (wetter.skyOk && adaptLum > 0) return Sky.State.exposureFor(adaptLum, adaptation) * Math.pow(2, exposureBias);
        double b = Sun.skyBrightness(clock.sun()[0]);
        return 0.6 * Math.pow(b, -0.85) * Math.pow(2, exposureBias);
    }

    /** Augenanpassung und Schatten neu (nach Sprüngen der Uhr oder der Kamera). */
    public void readapt() {
        viewLum = -1;
        adaptLum = -1;
        sunAtEyeTimer = 0;
        belichtung.reset();
        schatten.invalidate();
        if (wetter.skyOk) adapt(wetter.state(), 1);
    }

    /** Wolkenschatten am Auge (1 = Sonne) und Schatten des Geländes am Auge (1 = Sonne). */
    public double cloudShade() { return cloudShade; }
    public double sunAtEye() { return sunAtEye; }

    private void adapt(Sky.State st, double dt) {
        viewTimer -= dt;
        if (viewLum <= 0 || viewTimer <= 0) {
            viewLum = st.viewLuminance(camera.fwd.clone(), new double[]{0, 1, 0}, fovy(), surfW / (double) Math.max(1, surfH));
            viewTimer = 0.2;
        }
        double gemessen = bildMessung ? belichtung.lum() : -1;
        boolean above = camera.eye[1] > wetter.clouds.snapshot().top();
        double target = gemessen > 0
                ? Math.pow(Math.max(1e-14, st.adaptation), 1 - BILD_FOLGT) * Math.pow(Math.max(1e-14, gemessen * BILD_GAIN), BILD_FOLGT)
                : Math.sqrt(Math.max(1e-14, st.adaptation) * Math.max(1e-14, viewLum))
                * (wetter.cloudsOk && showClouds && !above ? Math.pow(wetter.clouds.brightnessFactor(), STORM_ADAPT) : 1);
        if (adaptLum <= 0 || frozen || dt > 0.5) adaptLum = target;
        else adaptLum = Math.exp(Math.log(adaptLum) + (Math.log(target) - Math.log(adaptLum)) * Math.min(1, dt / 1.0));
    }

    // ------------------------------------------------------------------ GL

    @Override
    public void init(GLAutoDrawable d) {
        initGl(d.getGL().getGL4(), d.getContext().getGLVersionNumber().getMajor(), d.getContext().getGLVersionNumber().getMinor(),
                d.getContext().getDefaultDrawFramebuffer());
    }

    /**
     * Start auf einer Grafikkarte ohne Fenster-Hülle (für {@link #init} und für Prüfstände, die GL selbst stellen):
     * Version, Bildpuffer des Ziels.
     */
    public void initGl(GL4 gl, int major, int minor, int targetFbo) {
        String renderer = gl.glGetString(GL.GL_RENDERER), vendor = gl.glGetString(GL.GL_VENDOR), version = gl.glGetString(GL.GL_VERSION);
        glInfo = renderer + " · OpenGL " + major + "." + minor;
        System.out.println("Brocken: " + vendor + " · " + renderer + " · " + version);
        if (major < MIN_MAJOR || (major == MIN_MAJOR && minor < MIN_MINOR)) {
            fail("Die Grafikkarte meldet OpenGL " + major + "." + minor + " (" + renderer + ", Treiber " + version
                    + "). Brocken braucht OpenGL " + MIN_MAJOR + "." + MIN_MINOR + ".\nBitte den Grafiktreiber aktualisieren.");
            return;
        }
        gl.setSwapInterval(1);
        defaultFbo = targetFbo;
        reversedZ = (major > 4 || minor >= 5) || gl.isExtensionAvailable("GL_ARB_clip_control");
        if (reversedZ) gl.glClipControl(GL4.GL_LOWER_LEFT, GL4.GL_ZERO_TO_ONE);
        try {
            for (Shader s : all) s.build(gl);
        } catch (RuntimeException e) {
            fail(e.getMessage());
            return;
        }
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        emptyVao = v[0];
        for (int[] s : stamp) gl.glGenQueries(MARKS, s, 0);
        compute = ComputeProbe.run(gl);
        System.out.println("Brocken: " + compute.line());
        lod.upload(gl, terrain);
        System.out.println("Brocken: Gelände " + terrain.describe());
        wetter.init(gl, errorListener);
        wiring.cloudsDrawn = wetter.cloudsOk;
        try {
            nebelGl.init(gl);
        } catch (RuntimeException e) {
            System.err.println("Brocken: Nebel über Gelände: " + e.getMessage());
        }
        bodenTex = Gpu.texture2D(gl, com.dan.brocken.scene.Bodenkarte.N, com.dan.brocken.scene.Bodenkarte.N, boden.rgba());
        waldTex = Gpu.texture2D(gl, waldkarte.nx, waldkarte.nz, waldkarte.rgba());
        driftTex = Gpu.texture2D(gl, verwehung.nx, verwehung.nz, driftRgba());
        if (strecke != null) {
            com.dan.rail.Bed b = strecke.bett;
            trasseMaske = Gpu.texture2D(gl, b.nx, b.nz, b.rgba());
            int[] tx = new int[1];
            gl.glGenTextures(1, tx, 0);
            trasseTex = tx[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, trasseTex);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R32F, b.nx, b.nz, 0, GL4.GL_RED, GL.GL_FLOAT, com.jogamp.common.nio.Buffers.newDirectFloatBuffer(b.delta));
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            try {
                gleisGl.init(gl);
                gleisGl.upload(gl, strecke.gleis, strecke.uebergaenge);
                gleisOk = true;
                // Phase 13: Ausweichgleise und Züge
                for (com.dan.rail.Track a : strecke.ausweichen) {
                    com.dan.rail.gl.RailGl r = new com.dan.rail.gl.RailGl();
                    r.init(gl);
                    r.upload(gl, a, java.util.List.of());
                    ausweichGl.add(r);
                }
                zugGl.init(gl);
                zugOk = true;
            } catch (RuntimeException e) {
                System.err.println("Brocken: Gleis: " + e.getMessage());
            }
        }
        try {
            gipfelGl.init(gl, gipfel, terrain);
        } catch (RuntimeException e) {
            System.err.println("Brocken: Gipfel: " + e.getMessage());
        }
        try {
            figurGl.init(gl);
            figurOk = true;
        } catch (RuntimeException e) {
            System.err.println("Brocken: Menschen: " + e.getMessage());
        }
        try {
            wald.init(gl);
        } catch (RuntimeException e) {
            System.err.println("Brocken: Wald: " + e.getMessage());
        }
        try {
            fels.init(gl);
        } catch (RuntimeException e) {
            System.err.println("Brocken: Fels (Granit, Felsburgen, Blockmeer): " + e.getMessage());
        }
        try {
            leben.init(gl);
        } catch (RuntimeException e) {
            System.err.println("Brocken: Leben (Gras, Rauch, Kolkraben): " + e.getMessage());
        }
        belichtung.init(gl);
        try {   // Phase 16: der Kinomodus (com.dan.kino)
            com.dan.kino.KinoMaler km = new com.dan.kino.KinoMaler();
            km.kopfFarbe = new java.awt.Color(159, 192, 206);   // Nebelblau, wie das Werkbuch
            kinoGl = new com.dan.kino.gl.KinoGl(kino, km);
            kinoGl.init(gl);
        } catch (RuntimeException e) {
            System.err.println("Brocken: Kino nicht verfügbar (ohne Balken und Blenden): " + e.getMessage());
            kinoGl = null;
        }
        physik.start();
        lastNanos = System.nanoTime();
        statsNanos = lastNanos;
        camera.snap();
        ready = true;
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
        surfW = Math.max(1, d.getSurfaceWidth());
        surfH = Math.max(1, d.getSurfaceHeight());
    }

    /** Uhr, Physik und Kamera um dt weiterrechnen (vom Bildlauf aufgerufen; Standbilder rufen es mit festem dt). */
    /** Phase 17: Arbeiten aus anderen Fäden (Weiter wie zuletzt, Ansicht, Preset), im Bildlauf vor dem Schritt ausgeführt. */
    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> imBild = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** Phase 17: eine Arbeit im Faden des Bildlaufs (vor dem nächsten Schritt). */
    public void imBild(Runnable r) { imBild.add(r); }

    /** Phase 17: nach einem Sprung von Uhr und Regie (Weiter wie zuletzt): Augen, Jahr, Eis und Nebel sofort auf den Stand der Uhr. */
    public void nachSprung() {
        readapt();
        jahrJetzt();
        raueisJetzt();
        nebelJetzt();
    }

    public void step(double dt) {
        for (Runnable r; (r = imBild.poll()) != null; ) {
            try { r.run(); } catch (RuntimeException e) { System.err.println("Brocken: Arbeit im Bildlauf: " + e); com.dan.store.Protokoll.fehler("Bildlauf", String.valueOf(e), e); }
        }
        clock.step(dt);
        regie.step(dt, regieCtx);   // Phase 16: fällige Stichworte des Regiebuchs (Uhr, Wetter, Ansicht, Zug)
        kinoSchritt(dt);
        treibenStep(dt);
        physik.advance(dt);
        // Phase 13: die Züge vor der Kamera (die Mitfahrt liest ihre Lage)
        zugStep();
        // Phase 14: Menschen am Berg nach Uhr, Monat und Wetter
        menschenStep();
        camera.groundLevel = groundHeight(camera.eye[0], camera.eye[2]);
        camera.waterLevel = camera.groundLevel;
        camera.targetGround = groundHeight(camera.tx, camera.tz);
        camera.step(dt);
        // Phase 3: zuerst die Wetterregie, dann bekommen Himmel und Wolken ihre Eingänge
        boolean cloudsShown = wetter.cloudsOk && showClouds;
        if (wiring.regie) wiring.feedWeather(dt);
        wiring.cloudsDrawn = cloudsShown;
        wiring.feed(wetter.sky, camera.eye[1], Site.LAT, Site.LON);
        wiring.feed(wetter.clouds, camera.groundLevel);
        // Phase 6: der Nebel über Gelände; beim Nebelmeer übernimmt er die untere Schicht (die Wolken behalten nur ihren Schatten)
        nebelStep(dt);
        wiring.feed(wiring.tropfen, nebel);
        if (wetter.skyOk) {
            Sky.State st = wetter.sky.step(dt);
            wetter.clouds.step(dt);
            double[] key = Wetter.keyDir(st);
            cloudShade = cloudsShown ? Clouds.shadow(wetter.clouds.snapshot(), camera.eye[0], camera.eye[1], camera.eye[2], key) : 1;
            sunAtEyeTimer -= dt;
            if (sunAtEyeTimer <= 0 || frozen) {
                sunAtEye = terrain.measured() ? com.dan.brocken.scene.Kuppenschatten.licht(terrain, camera.eye[0], camera.eye[1], camera.eye[2], key) : 1;
                sunAtEyeTimer = 0.5;
            }
            adapt(st, dt);
            gespenstTimer -= dt;
            if (gespenstTimer <= 0 || frozen) { gespenst = gespenstText(st.sunDir); gespenstTimer = 0.5; }
        }
        // Phase 4: das Windfeld bekommt Richtung, Schichtung und Erwärmung; gelöst wird im eigenen Faden
        windStep();
        // Phase 11: das Jahr (Schnee je Höhe) in Szenenzeit, dann Schnee am Auge an Gras, Bodendecke und Flocken
        jahrStep(dt);
        // Phase 10: das Eis am Berg in Szenenzeit (springt die Uhr, rechnet es das Regiebuch von Beginn)
        raueisStep(dt);
        // Phase 5: Gras, Rauch und Kolkraben im Wind des Felds
        leben.step(frozen ? 0 : dt, clock.local().getDayOfYear(), clock.sun()[0], wiring.oktas, wiring);
        // Phase 15: der Klang am Ohr der Kamera (Vögel, Eis, Pfiffe in Echtzeit, stehende Uhr: nichts Neues)
        klang.feed(camera.eye, Math.toDegrees(Math.atan2(camera.fwd[0], -camera.fwd[2])), clock.local(), clock.sun()[0], frozen ? 0 : dt);
    }

    /**
     * Phase 6: Lage an den Nebel, einen Schritt weiter; springt die Uhr (Start, Regiebuch, Zeitregler), wird das
     * Nebelmeer auf den Stand gesetzt, den die Sonne seit Beginn der Lage von unten aufgelöst hat.
     */
    private void nebelStep(double dt) {
        boolean meer = wiring.feed(nebel, clock.sun()[0]);
        double now = clock.epochMillis() / 1000.0;
        double sceneDt = frozen ? 0 : dt * Math.max(0, clock.lapse);
        if (Double.isNaN(nebelUhr) || Math.abs(now - nebelUhr - sceneDt) > 600) nebel.settle(wiring.nebelVerbrannt(nebel.burnRate));
        else nebel.step(Math.max(0, now - nebelUhr));
        nebelUhr = now;
        nebelMeer = meer && showNebel;
        // die Wolken behalten die Schicht nur noch als Schatten, so stark wie das Meer noch da ist
        if (nebelMeer) wetter.clouds.oktas = Math.min(wetter.clouds.oktas, 8 * nebel.seaAmount());
    }

    private double jahrUhr = Double.NaN;

    /** Phase 11: das Jahr einen Schritt; ein Sprung der Uhr (über 1 h neben der Szenenzeit) rechnet aus dem gewürfelten Jahr vor. */
    private void jahrStep(double dt) {
        double now = clock.epochMillis() / 1000.0;
        double sceneDt = frozen ? 0 : dt * Math.max(0, clock.lapse);
        boolean sprung = Double.isNaN(jahrUhr) || Math.abs(now - jahrUhr - sceneDt) > 3600;
        wiring.feedJahr(sprung ? 0 : Math.max(0, now - jahrUhr), sprung, clock.sun()[0]);
        jahrUhr = now;
        double ex = camera.eye[0], ez = camera.eye[2];
        leben.schnee = showSchnee ? schneeAm(ex, camera.groundLevel, ez) : 0;
        com.dan.weather.Weather.Snapshot ws = wiring.snapshot();
        double t = ws.temperatureAt(camera.eye[1]);
        double rain = Math.max(0, ws.rain());
        leben.schneefall = showSchnee ? rain * (1 - smooth(1.0, 3.0, t)) : 0;
        double[] v = wiring.windAt(ex, camera.eye[1], ez);
        double sp = Math.hypot(v[0], v[2]);
        leben.flockenWind = new double[]{sp > 1e-6 ? v[0] / sp : 1, sp > 1e-6 ? v[2] / sp : 0, sp};
    }

    /** Phase 11: das Jahr sofort auf den Stand der Uhr (nach Datum, Regiebuch; für Standbilder). */
    public void jahrJetzt() {
        wiring.feedJahr(0, true, clock.sun()[0]);
        jahrUhr = clock.epochMillis() / 1000.0;
    }

    /**
     * Phase 11: Schneehöhe am Ort (m): die Stufe der Höhe mal Verwehung; im Wald verweht wenig (die Bäume halten den
     * Schnee), unter dichten lebenden Fichten bleibt ein Teil in den Kronen [G].
     */
    public double schneeAm(double x, double y, double z) {
        double d = wiring.schnee(y), f = verwehung.factorAt(x, z), wd = waldkarte.cover(x, z);
        return d * (f + (1 - f) * 0.7 * wd);
    }

    private byte[] driftRgba() {
        byte[] rg = verwehung.rg(), out = new byte[verwehung.nx * verwehung.nz * 4];
        for (int k = 0; k < verwehung.nx * verwehung.nz; k++) { out[k * 4] = rg[k * 2]; out[k * 4 + 1] = rg[k * 2 + 1]; out[k * 4 + 3] = (byte) 255; }
        return out;
    }

    /** „Jahr: Schnee … · Verwehungen …“ für HUD und Bedienfeld. */
    public String jahrZeile() {
        double here = schneeAm(camera.eye[0], camera.groundLevel, camera.eye[2]);
        return "Jahr: " + wiring.jahrZeile() + String.format(Locale.GERMAN, " · hier %.0f cm (Verwehung ×%.1f%s)", here * 100,
                verwehung.factorAt(camera.eye[0], camera.eye[2]), verwehung.corniceAt(camera.eye[0], camera.eye[2]) > 0.3 ? ", Wechte" : "")
                + (leben.schneefall > 0.01 ? String.format(Locale.GERMAN, " · Schneefall %.1f mm/h", leben.schneefall) : "") + (showSchnee ? "" : " · aus");
    }

    private double raueisUhr = Double.NaN;

    /** Phase 10: Raueis einen Schritt; ein Sprung der Uhr (über 10 min neben der Szenenzeit) rechnet nach. */
    private void raueisStep(double dt) {
        double now = clock.epochMillis() / 1000.0;
        double sceneDt = frozen ? 0 : dt * Math.max(0, clock.lapse);
        boolean sprung = Double.isNaN(raueisUhr) || Math.abs(now - raueisUhr - sceneDt) > 600;
        wiring.feedRaueis(sprung ? 0 : Math.max(0, now - raueisUhr), sprung, clock.sun()[0]);
        raueisUhr = now;
        // die Halme am Auge: Reif nach dem Eis an den Zweigen in dieser Höhe (1 cm Reif, ab 2 cm dick weiß) [G]
        com.dan.brocken.wiring.Raueis r = wiring.raueis;
        leben.gras.frost = showRaueis && r.art() != com.dan.rime.Rime.Art.KEINS
                ? Math.min(2, r.dicke(com.dan.brocken.wiring.Raueis.ZWEIG, camera.groundLevel) / 0.01) : 0;
        leben.reif = (float) (0.15 * leben.gras.frost);
    }

    /** Phase 10: das Eis sofort auf den Stand der Uhr (nach Regiebuch, Zeitsprung; für Standbilder). */
    public void raueisJetzt() {
        wiring.feedRaueis(0, true, clock.sun()[0]);
        raueisUhr = clock.epochMillis() / 1000.0;
    }

    /** Phase 6: den Nebel sofort auf den Stand der Uhr setzen (nach Regiebuch, Lage, Zeitsprung). */
    public void nebelJetzt() {
        wiring.feed(nebel, clock.sun()[0]);
        nebel.settle(wiring.nebelVerbrannt(nebel.burnRate));
        nebelUhr = clock.epochMillis() / 1000.0;
    }

    /** Phase 4: Eingänge an das Windfeld, Anfrage an den Faden, ein fertiges Feld übernehmen (ohne zu warten). */
    private void windStep() {
        wiring.feedWind(clock.sun()[0]);
        if (terrain.measured() || Boolean.getBoolean("brocken.wind.buehne")) windrechner.request(wiring.wind, wiring.windKey());
        Windrechner.Ergebnis e = windrechner.last();
        if (e != null && e != windSeen) {
            windSeen = e;
            nebel.mapVersion++;     // Phase 6: Kaltluft und Gürtel neu
            wiring.wind.solution = e.solution();
            wiring.windSummitRaw = e.summitRaw();
            wiring.feedWind(clock.sun()[0]);
        }
    }

    /** Für Werkzeuge und Prüfung: das Windfeld sofort lösen (im aufrufenden Faden) und übernehmen. */
    /** Ob schon ein Windfeld gerechnet ist (ohne zu rechnen; für den Dauerlauf). */
    public boolean windDa() { return windSeen != null; }

    public Windrechner.Ergebnis windJetzt() {
        wiring.feedWind(clock.sun()[0]);
        Windrechner.Ergebnis e = windrechner.jetzt(wiring.wind, wiring.windKey());
        windSeen = null;
        windStep();
        return e;
    }

    @Override
    public void display(GLAutoDrawable d) {
        displayGl(d.getGL().getGL4(), d.getSurfaceWidth(), d.getSurfaceHeight(), d.getContext().getDefaultDrawFramebuffer());
    }

    /** Ein Bild in das Ziel targetFbo der Größe w × h (für {@link #display} und Prüfstände). */
    public void displayGl(GL4 gl, int width, int height, int targetFbo) {
        if (!ready) {
            gl.glClearColor(0.06f, 0.075f, 0.08f, 1);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            return;
        }
        if (reloadShaders) {
            reloadShaders = false;
            try {
                for (Shader s : all) s.build(gl);
                hudMeldung("Shader neu geladen");
                System.out.println("Shader neu geladen.");
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
                errorListener.accept(e.getMessage());
            }
        }
        defaultFbo = targetFbo;
        readMarks(gl);
        long now = System.nanoTime();
        double rawMs = (now - lastNanos) / 1e6;
        if (frame > 2 && rawMs > worstMs) worstMs = rawMs;
        double dt = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (!frozen) step(dt);

        surfW = Math.max(1, width);
        surfH = Math.max(1, height);
        regulateScale(now);
        int w = Math.max(64, (int) Math.round(surfW * renderScale)), h = Math.max(64, (int) Math.round(surfH * renderScale));
        if (w != hdrW || h != hdrH) resizeTargets(gl, w, h);
        renderFrame(gl, w, h);
        // Phase 16: Fotos und Blatt des Regiebuchs (ohne Balken), dann das Kino über das fertige Bild
        String cap = captureCaption;
        if (cap != null && fotoVorlauf > 0) { fotoVorlauf--; cap = null; }   // Tempo: erst einige Bilder zum Einregeln der Belichtung
        if (cap != null) {
            captureCaption = null;
            album.add(readDefault(gl, surfW, surfH));
            albumCaps.add(cap + "\n" + clock.label() + " · " + String.format(Locale.GERMAN, "%.0f Bilder/s · Grafikkarte %.1f ms", fps, gpuMs));
        }
        if (regie.running()) { albumFps += fps; albumGpu += gpuMs; albumSamples++; }
        String sn = sheetName;
        if (sn != null) { sheetName = null; writeSheet(sn); }
        kinoBild(gl, now);
        if (photoRequest) { photoRequest = false; takePhoto(gl, surfW, surfH); }
        Consumer<BufferedImage> gr = grabRequest;
        if (gr != null) { grabRequest = null; try { gr.accept(readDefault(gl, surfW, surfH)); } catch (RuntimeException e) { System.err.println("Brocken: Bild nicht abgeholt: " + e); } }
        cpuMs = cpuMs * 0.95 + (System.nanoTime() - now) / 1e6 * 0.05;
        frame++;
        stats(now, w, h);
    }

    void renderFrame(GL4 gl, int w, int h) {
        mark(gl, 0);
        frameSky = wetter.skyOk;
        Sky.State st = frameSky ? wetter.state() : null;
        frameSt = st;
        if (frameSky && adaptLum <= 0) adapt(st, 1);
        double pre = frameSky ? exposure() : 1;
        framePre = pre;
        double[] sp = clock.sun();
        double[] sun = frameSky ? st.sunDir : Sun.direction(sp[0], sp[1]);
        double[] key = frameSky ? Wetter.keyDir(st) : sun;
        float[] proj = Mat4.perspective(fovy(), w / (double) h, NEAR, FAR, reversedZ);
        float[] vp = Mat4.mul(proj, camera.view());
        float[] inv = Mat4.invert(vp);
        boolean cloudsNow = frameSky && wetter.cloudsOk && showClouds;

        // 1 Schatten der Kuppe: ein Streifen je Bild, sobald sich die Lichtquelle gedreht hat
        schatten.size(gl, quality.shadowN);
        if (terrain.measured()) {
            common(gl, schattenShader, vp, inv, sun);
            lod.bind(gl, schattenShader);
            schatten.step(gl, schattenShader, emptyVao, key);
        }
        mark(gl, 1);

        // 2 Himmel: com.dan.sky ins Bild, dann das Rundumbild mit Wolken (Gelände, Dunst) und die Wolkenschatten;
        //   ohne com.dan.sky der Verlauf aus Phase 1
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearDepth(reversedZ ? 0 : 1);
        gl.glClearColor(0, 0, 0, 1);
        gl.glDepthMask(true);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindVertexArray(emptyVao);
        CloudGl.Light cl = null;
        Clouds.Snapshot cs = wetter.clouds.snapshot();
        if (frameSky) {
            wetter.skyGl.update(gl, st);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            SkyGl.View sv = new SkyGl.View();
            sv.viewProj = vp;
            sv.invViewProj = inv;
            sv.reversedZ = reversedZ;
            sv.width = w;
            sv.height = h;
            sv.fovyDeg = fovy();
            sv.preExposure = pre;
            sv.time = clock.t();
            wetter.skyGl.draw(gl, st, sv);
            if (wetter.skyCheck.isEmpty() && frame > 30) wetter.skyCheck = wetter.skyGl.selfCheck(gl, st);
            gl.glBindVertexArray(emptyVao);
            if (cloudsNow) {
                cl = wetter.cloudLight(st, pre);
                wetter.cloudGl.update(gl, cs, cl, camera.eye.clone());
                if (wetter.cloudCheck.isEmpty() && frame > 40) wetter.cloudCheck = wetter.cloudGl.selfCheck(gl, cs, cl);
            }
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, wetter.panoFbo);
            gl.glViewport(0, 0, Wetter.PANO_W, Wetter.PANO_H);
            wetter.skyGl.drawPanorama(gl, st, pre);
            if (cloudsNow && !nebelMeer) wetter.cloudGl.drawPanorama(gl, cs, cl, camera.eye.clone());
            gl.glBindTexture(GL.GL_TEXTURE_2D, wetter.panoTex);
            gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glBindVertexArray(emptyVao);
        } else {
            common(gl, skyShader, vp, inv, sun);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        mark(gl, 2);

        // 3 Gelände: Kern, Hauptraster und Fernring als CDLOD, im Licht des Himmels, mit Schatten und Marken
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        common(gl, terrainShader, vp, inv, sun);
        lod.bind(gl, terrainShader);
        terrainShader.set(gl, "uMarks", showMarks ? 1 : 0);
        rect(gl, terrainShader, "uMarkHaupt", Site.HAUPTRASTER);
        rect(gl, terrainShader, "uMarkKern", Site.KERN);
        float[] pts = points();
        terrainShader.vec4Array(gl, "uPoints", pts);
        terrainShader.set(gl, "uPointCount", pts.length / 4);
        // Phase 3: Schatten der Kuppe (Einheiten 12, 13) und Wolkenschatten (14, R unter der Schicht, G über ihr)
        boolean shade = schatten.on && terrain.measured() && schatten.ready();
        gl.glActiveTexture(GL.GL_TEXTURE0 + 12);
        gl.glBindTexture(GL.GL_TEXTURE_2D, shade ? schatten.mainTexture() : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0 + 13);
        gl.glBindTexture(GL.GL_TEXTURE_2D, shade ? schatten.fernTexture() : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0 + 14);
        gl.glBindTexture(GL.GL_TEXTURE_2D, cloudsNow ? wetter.cloudGl.shadowTexture() : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        terrainShader.set(gl, "uShadeMain", 12);
        terrainShader.set(gl, "uShadeFern", 13);
        terrainShader.set(gl, "uCloudShadow", 14);
        terrainShader.set(gl, "uShadeOn", shade ? 1 : 0);
        double[] mr = schatten.mainRect(), fr = schatten.fernRect();
        terrainShader.set(gl, "uShadeRectMain", (float) mr[0], (float) mr[1], (float) mr[2]);
        terrainShader.set(gl, "uShadeRectFern", (float) fr[0], (float) fr[1], (float) fr[2]);
        double[] cr = cloudsNow ? wetter.cloudGl.shadowRect() : new double[]{0, 0, 1};
        terrainShader.set(gl, "uCloudRect", (float) cr[0], (float) cr[1], (float) Math.max(1, cr[2]), cloudsNow && !Boolean.getBoolean("brocken.ohne.wolkenschatten") ? 1f : 0f);
        terrainShader.set(gl, "uCloudLayer", (float) cs.base(), (float) cs.top());
        // Phase 8: Bodenkarte (Einheit 6) und Granit (Einheiten 5 und 15)
        gl.glActiveTexture(GL.GL_TEXTURE0 + 6);
        gl.glBindTexture(GL.GL_TEXTURE_2D, bodenTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        terrainShader.set(gl, "uBoden", 6);
        terrainShader.set(gl, "uBodenRect", (float) com.dan.brocken.scene.Bodenkarte.X0, (float) com.dan.brocken.scene.Bodenkarte.Z0,
                (float) com.dan.brocken.scene.Bodenkarte.SIZE, bodenTex != 0 && showBoden ? 1f : 0f);
        double[] bj = com.dan.brocken.scene.Bodenkarte.jahr(clock.local().getDayOfYear());
        terrainShader.set(gl, "uBodenJahr", (float) bj[0], (float) bj[1], (float) bj[2], (float) bj[3]);
        // Phase 9: Waldkarte (Einheit 4); nah, wo die Bäume stehen, zeigt der Grund den Waldboden, fern die Kronen
        gl.glActiveTexture(GL.GL_TEXTURE0 + 4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, waldTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        terrainShader.set(gl, "uWald", 4);
        terrainShader.set(gl, "uWaldRect", (float) waldkarte.x0, (float) waldkarte.z0, (float) (waldkarte.nx * com.dan.brocken.scene.Waldkarte.CELL),
                waldTex != 0 ? 1f : 0f);
        terrainShader.set(gl, "uWaldRectZ", (float) (waldkarte.nz * com.dan.brocken.scene.Waldkarte.CELL));
        double wr = wald.showWald && wald.ready() ? wald.feld.radius : 0;
        // der Waldboden nur nah (bis rund 100 m); weiter weg trägt die Farbe von oben, auch wo Bäume stehen: das
        // Totholz ist licht und deckt den Boden nicht, von fern sieht man ihn grau durch die Stämme
        terrainShader.set(gl, "uWaldNah", (float) Math.min(90, wr * 0.45), (float) Math.min(380, wr * 0.8));
        boolean granitOn = fels.ready() && fels.showFels;
        terrainShader.set(gl, "uFelsOn", granitOn ? 1 : 0);
        if (granitOn) fels.bindTerrain(gl, terrainShader.id());
        bindRaueis(gl, terrainShader.id());
        // Phase 11: Schnee je Stufe, Verwehungen (Einheit 3), Kronenschnee; Heide und Wollgras unten früher
        gl.glActiveTexture(GL.GL_TEXTURE0 + 3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, driftTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        terrainShader.set(gl, "uDrift", 3);
        // Phase 12: Bahnkörper (Einheit 2 Änderung der Höhe, Einheit 1 Schotter und Planum)
        gl.glActiveTexture(GL.GL_TEXTURE0 + 2);
        gl.glBindTexture(GL.GL_TEXTURE_2D, trasseTex);
        gl.glActiveTexture(GL.GL_TEXTURE0 + 1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, trasseMaske);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        terrainShader.set(gl, "uTrasse", 2);
        terrainShader.set(gl, "uTrasseMaske", 1);
        if (strecke != null) {
            com.dan.rail.Bed b = strecke.bett;
            terrainShader.set(gl, "uTrasseRect", (float) b.x0, (float) b.z0, (float) (b.nx * b.cell), (float) (b.nz * b.cell));
        }
        terrainShader.set(gl, "uTrasseOn", strecke != null && trasseTex != 0 ? 1 : 0);
        int tsp = terrainShader.id();
        float[] stufen = wiring.schneeStufen();
        if (!showSchnee) java.util.Arrays.fill(stufen, 0f);
        gl.glUniform1fv(gl.glGetUniformLocation(tsp, "uSchnee"), stufen.length, stufen, 0);
        terrainShader.set(gl, "uSchneeOn", showSchnee ? 1 : 0);
        terrainShader.set(gl, "uSchneeNass", (float) wiring.jahr.snowWet(camera.eye[1]), (float) (showSchnee ? wiring.jahr.treeSnow(1000) : 0));
        double[] bjU = com.dan.brocken.scene.Bodenkarte.jahr(Math.min(365, clock.local().getDayOfYear() + 12));
        terrainShader.set(gl, "uBodenJahrUnten", (float) bjU[0], (float) bjU[1], (float) bjU[2], (float) bjU[3]);
        lod.draw(gl, terrainShader, camera.eye, vp);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 3);

        // 4 Fels (Phase 8): Felsburgen und Blockmeer aus com.dan.cliff
        drawFels(gl, vp, sun, h);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 4);

        // 5 Wald (Phase 9): Bäume aus com.dan.forest
        drawWald(gl, vp, sun, cloudsNow);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 5);

        // 6 Bahn (Phase 12): das Gleis aus com.dan.rail
        drawGleis(gl, vp, sun);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 6);

        // 7 Gipfel (Phase 14): die Bauten am Gipfel, Gipfelstein, Brockenuhr, Garten
        drawGipfel(gl, vp, sun);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 7);

        // 8 Menschen (Phase 14): Wanderer und Besucher aus com.dan.figure
        drawMenschen(gl, vp, sun);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 8);

        // 4 Leben (Phase 5): Gras und Kolkraben mit Tiefe, dann der Rauch ins Farbziel (liest die Tiefe)
        drawLeben(gl, vp, sun, w, h);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 9);

        // 5 Nebel (Phase 6): über Gelände, ins Farbziel, liest die Tiefe
        drawNebel(gl, vp, inv, sun, w, h);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 10);

        // 6 Wind: Pfeile und Rauchfäden des Felds (Tiefe lesen, nicht schreiben; durchscheinend)
        drawWind(gl, vp, inv, sun);
        gl.glDisable(GL.GL_DEPTH_TEST);
        mark(gl, 11);

        // 6 Wolken nach dem Gelände mit seiner Tiefe (ins Farbziel ohne Tiefe: die Tiefe wird gelesen)
        if (cloudsNow && !nebelMeer) {
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrColorFbo);
            gl.glViewport(0, 0, w, h);
            CloudGl.View cv = new CloudGl.View();
            cv.viewProj = vp;
            cv.invViewProj = inv;
            cv.eye = camera.eye.clone();
            cv.reversedZ = reversedZ;
            cv.width = w;
            cv.height = h;
            cv.depthTexture = depthTex;
            wetter.cloudGl.draw(gl, cs, cl, cv);
            gl.glBindVertexArray(emptyVao);
        }
        mark(gl, 12);

        // Belichtung im Bild messen (alle 0,2 s, gelesen ohne Warten beim nächsten Mal)
        if (frameSky && bildMessung) {
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrColorFbo);
            belichtung.step(gl, lumShader, hdrTex, emptyVao, pre, frozen);
        }

        // 5 Nachbearbeitung ins Fenster (mit com.dan.sky ist das Bild schon vorbelichtet)
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, defaultFbo);
        gl.glViewport(0, 0, surfW, surfH);
        post.use(gl);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        post.set(gl, "uHdr", 0);
        post.set(gl, "uExposure", frameSky ? 1f : (float) exposure());
        post.set(gl, "uScotopic", (float) (frameSky ? Wetter.scotopic(st) : 0));
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        mark(gl, 13);

        // 9 HUD. Seit Phase 10 vorher abschicken: malt das HUD im selben Faden (Standbilder, Tempo), stand die
        // Grafikkarte sonst leer, bis es fertig war, und die Lücke zählte zur Nachbearbeitung (Radeon Phase 9: 6,5 ms
        // mit vollem HUD, 0,3 ms ohne)
        gl.glFlush();
        if (showHud && !kino.laeuft() && captureCaption == null) {   // Phase 16: nicht im Kino und nicht auf den Fotos des Regiebuchs
            hud.refresh(gl, 250, this::paintHud, frozen);
            hud.draw(gl, overlay, emptyVao, 12, Math.max(0, surfH - hud.height - 12), surfW, surfH);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        mark(gl, 14);
    }

    /** Phase 6: der Nebel über Gelände mit dem Licht des Himmels (vorbelichtet wie die Wolken). */
    private void drawNebel(GL4 gl, float[] vp, float[] inv, double[] sun, int w, int h) {
        if (!showNebel || !nebelGl.ready()) return;
        nebelGl.update(gl, nebel);
        com.dan.fog.TerrainFog.Snapshot s = nebel.snapshot();
        if (!s.any()) return;
        com.dan.fog.gl.TerrainFogGl.View v = new com.dan.fog.gl.TerrainFogGl.View();
        v.invViewProj = inv;
        v.eye = camera.eye.clone();
        v.reversedZ = reversedZ;
        v.width = w;
        v.height = h;
        v.depthTexture = depthTex;
        v.frame = (int) frame;
        v.maxDistance = 90_000;
        v.curvature = 1 / (2 * 6_371_000 / 0.87);
        v.scale = nebelScale;
        com.dan.fog.gl.TerrainFogGl.Light l = new com.dan.fog.gl.TerrainFogGl.Light();
        if (frameSky && frameSt != null) {
            Sky.State st = frameSt;
            double pre = framePre;
            l.sunDir = st.sunDir.clone();
            l.sunColor = Wetter.scale(st.sunColor, pre);
            l.moonDir = st.moonDir.clone();
            l.moonColor = Wetter.scale(st.moonColor, pre);
            // über dem Nebelmeer ist der Himmel klar; unter einer Decke (Stau) grau wie am Boden
            boolean klar = nebelMeer || camera.eye[1] > wetter.clouds.snapshot().top();
            double[] amb = Wetter.scale(wetter.ambient(st, showClouds, klar), pre / Math.PI);
            l.ambientTop = amb;
            l.ambientHorizon = Wetter.scale(amb, 1.15);
        } else {
            l.sunDir = sun.clone();
            l.sunColor = new double[]{2.5, 2.4, 2.2};
            l.ambientTop = new double[]{0.25, 0.3, 0.4};
            l.ambientHorizon = new double[]{0.3, 0.33, 0.4};
        }
        l.sunUnderCap = 0.04;   // unter einer dicken Decke kommt kaum direkte Sonne an
        // Phase 7: Tröpfchen nach Mie (Glorie, Nebelbogen), der Wanderer am Auge als Schattenwerfer (Gespenst), der
        // Schatten der Kuppe aus der Schattenkarte (Höhe, ab der die Sonne scheint)
        if (showGlorie) {
            tropfenGl.update(gl, wiring.tropfen);
            l.phaseTexture = tropfenGl.texture();
            l.casters = wanderer();
            if (schatten.on && terrain.measured() && schatten.ready()) {
                l.sunHeightTexture = schatten.mainTexture();
                l.sunHeightRect = schatten.mainRect();
            }
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrColorFbo);
        gl.glViewport(0, 0, w, h);
        nebelGl.draw(gl, s, v, l);
        if (nebelCheck.isEmpty() && frame > 50) nebelCheck = nebelGl.selfCheck(gl, nebel);
        nebelAufloesung = nebelGl.resolution(s, w, h);
    }

    private volatile String nebelAufloesung = "";

    /** Phase 7: das Auge als stehender Wanderer (Füße auf dem Grund, Auge im Kopf), wenn es höchstens {@link #STEHEND} m über Grund ist; sonst null. */
    public double[] wanderer() {
        double agl = camera.eye[1] - camera.groundLevel;
        if (agl > STEHEND || agl < 0.3) return null;
        return com.dan.fog.TerrainFog.person(camera.eye[0], camera.groundLevel, camera.eye[2], agl / 0.93);
    }

    /**
     * Phase 7: sieht das Auge sein Brockengespenst? Die Sonne im Rücken (über −0,5°), ein Wanderer am Auge und Nebel im
     * Gegenpunkt näher als der Kernschatten (54 m). Gesucht wird mit dem Nebel des Prozessors in Schritten von 1 m.
     */
    public String gespenstText(double[] sunDir) {
        double alt = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sunDir[1]))));
        if (!showGlorie) return "Glorie und Gespenst aus";
        if (alt < -0.5) return "Gespenst: nein, die Sonne ist unter der Kimm";
        double[] a = com.dan.sky.Droplets.antisolar(sunDir);
        double fog = -1;
        for (double t = 0.5; t <= 400; t += t < 60 ? 1 : 5) {
            if (nebel.density(camera.eye[0] + a[0] * t, camera.eye[1] + a[1] * t, camera.eye[2] + a[2] * t) > 0) { fog = t; break; }
        }
        double umbra = com.dan.sky.Droplets.umbraLength(0.25);
        String glorie = tropfenGl.table() != null ? String.format(Locale.GERMAN, ", Glorie %.1f°", tropfenGl.table().gloryRingDeg()) : "";
        if (fog < 0) return String.format(Locale.GERMAN, "Gespenst: nein, im Gegenpunkt (%.0f° unter der Kimm) kein Nebel bis 400 m", alt);
        if (wanderer() == null) return String.format(Locale.GERMAN, "Gespenst: Glorie im Nebel %.0f m voraus%s; Schatten nur, wenn das Auge steht (bis %.1f m über Grund)", fog, glorie, STEHEND);
        double deg = com.dan.sky.Droplets.spectreDeg(sunDir, Math.max(fog, 0.25 * umbra), 0.25);
        if (fog > umbra) return String.format(Locale.GERMAN, "Gespenst: nein, Nebel erst %.0f m voraus (Kernschatten %.0f m)%s", fog, umbra, glorie);
        return fog < 2 ? String.format(Locale.GERMAN, "Gespenst: ja, das Auge steht im Nebel, der Schatten reicht %.0f m weit%s", umbra, glorie)
                : String.format(Locale.GERMAN, "Gespenst: ja, Schatten im Nebel ab %.0f m voraus, dort %.1f° breit%s", fog, deg, glorie);
    }

    /** „Licht im Nebel: r 10 µm (σ 8 %): Glorie, erster roter Ring 2,1°, Nebelbogen 38,1° · Gespenst: … · Halo …“ */
    public String lichtZeile() {
        com.dan.sky.Droplets.Table t = tropfenGl.table();
        String tr = !showGlorie ? "aus (Henyey-Greenstein)" : t == null ? "Tröpfchen werden gerechnet …" : t.describe();
        Sky.State st = frameSt;
        String halo = st == null || st.halo + st.parhelia <= 0.01 ? "kein Halo"
                : String.format(Locale.GERMAN, "Halo %.0f %%, Nebensonnen %.0f %% (%.1f° im Azimut neben der Sonne)", st.halo * 100, st.parhelia * 100,
                com.dan.sky.Halo.parhelionDeg(st.sunAlt, com.dan.sky.Halo.RED));
        return "Licht im Nebel: " + tr + (gespenst.isEmpty() ? "" : " · " + gespenst) + " · " + halo + (nebelAufloesung.isEmpty() ? "" : " · Nebel in " + nebelAufloesung);
    }

    /**
     * Phase 7: das Nebelmeer von Hand mit der Untergrenze {@code basis} (m über NHN) so legen, dass seine wogende
     * Obergrenze genau am Auge {@code ueberAuge} m über dem Auge liegt (Werkzeuge, Bedienfeld „Meer ans Auge“): wer in
     * der obersten Schicht steht, sieht bei tiefer Sonne sein Gespenst. Gibt die Obergrenze im Mittel zurück.
     */
    public double meerAmAuge(double basis, double ueberAuge) {
        wiring.meerBasisHand = basis;
        double want = camera.eye[1] + ueberAuge, top = want;
        for (int i = 0; i < 5; i++) {
            wiring.meerTopHand = top;
            nebelJetzt();
            if (!nebel.snapshot().sea()) break;
            top += want - com.dan.fog.TerrainFog.seaTopAt(nebel.snapshot(), camera.eye[0], camera.eye[2]);
        }
        return wiring.meerTopHand;
    }

    /** Phase 7: das Auge in den Gegenpunkt der Sonne (oder zu ihr) drehen, am Ort bleibend. */
    public void blickZurSonne(boolean gegenpunkt) {
        double[] sp = clock.sun();
        double yaw = gegenpunkt ? (sp[1] + 180) % 360 : sp[1], pitch = gegenpunkt ? sp[0] : -sp[0];
        double[] e = camera.eye.clone();
        camera.place(e[0], e[1], e[2], yaw, pitch);
        hudMeldung(String.format(Locale.GERMAN, "%s: %03.0f°, %.0f° %s der Kimm", gegenpunkt ? "Blick in den Gegenpunkt" : "Blick zur Sonne", yaw,
                Math.abs(sp[0]), (gegenpunkt ? -sp[0] : sp[0]) >= 0 ? "über" : "unter"));
    }

    /** Phase 7: der Text zum Brockengespenst (alle 0,5 s neu). */
    public String gespenst() { return gespenst; }

    private volatile String nebelCheck = "";
    /** Phase 7: Auflösung des Nebels von Hand (1 voll, 2 halb, 3 drittel; 0 = nach der Stufe). */
    public volatile int nebelScale = Integer.getInteger("brocken.nebel.scale", 0);

    /** „Nebel: Nebelmeer 520–840 m (100 %) · am Auge Sicht 90 m · Karte 192² …“ für HUD und Bedienfeld. */
    public String nebelZeile() {
        double vis = nebel.visibilityAt(camera.eye[0], camera.eye[1], camera.eye[2]);
        return "Nebel: " + nebel.describe() + (vis < 5000 ? String.format(Locale.GERMAN, " · am Auge Sicht %.0f m", vis) : "")
                + (nebelMeer ? " · die untere Wolkenschicht zeichnet der Nebel" : "") + (nebelGl.info().isEmpty() ? "" : " · " + nebelGl.info())
                + (nebelCheck.isEmpty() ? "" : " · " + nebelCheck);
    }

    /**
     * Phase 5: Wind der Regie von Hand (Richtung, aus der er kommt, und Stärke an der Wetterwarte in 27 m), für
     * Werkzeuge und Prüfung; die Regie läuft für die übrigen Größen weiter.
     */
    public void handWind(double fromDeg, double speed) {
        wiring.hand(com.dan.weather.Weather.Var.DIRECTION, fromDeg);
        wiring.hand(com.dan.weather.Weather.Var.WIND, speed);
        wiring.feedWeather(0.01);
    }

    /**
     * Phase 5: das Auge in abstand m südlich unter den Kolkraben, der am höchsten im Aufwind kreist (sonst den höchsten),
     * mit dem Blick auf ihn. false, wenn keiner da ist.
     */
    public boolean kameraZumRaben(double abstand) {
        com.dan.flock.Flock.Gull best = null;
        double score = -1e9;
        for (com.dan.flock.Flock.Gull g : leben.raben.gulls()) {
            if (!g.raven || g.state == com.dan.flock.Flock.State.AWAY) continue;
            double sc = g.pos[1] - groundHeight(g.pos[0], g.pos[2]);
            if (sc > score) { score = sc; best = g; }
        }
        if (best == null) return false;
        double x = best.pos[0] - 0.25 * abstand, z = best.pos[2] + 0.95 * abstand;
        double y = Math.max(groundHeight(x, z) + 3, best.pos[1] - 0.35 * abstand);
        double dx = best.pos[0] - x, dy = best.pos[1] - y, dz = best.pos[2] - z;
        camera.setMode(Camera.Mode.FREI);
        camera.place(x, y, z, Math.toDegrees(Math.atan2(dx, -dz)), -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
        camera.snap();
        return true;
    }

    /** Phase 5: Licht für Gras, Kolkraben und Rauch (vorbelichtet wie das Gelände), dann zeichnen. */
    private void drawLeben(GL4 gl, float[] vp, double[] sun, int w, int h) {
        double[] sunDir, sunE, moonDir, moonE, skyPi;
        double fog;
        if (frameSky && frameSt != null) {
            Sky.State st = frameSt;
            double pre = framePre;
            boolean above = camera.eye[1] > wetter.clouds.snapshot().top();
            sunDir = st.sunDir.clone();
            sunE = Wetter.scale(st.sunColor, pre * cloudShade);
            moonDir = st.moonDir.clone();
            moonE = Wetter.scale(st.moonColor, pre * cloudShade);
            skyPi = Wetter.scale(wetter.ambient(st, showClouds, above), pre / Math.PI);
            fog = Wetter.fogDistance(st);
        } else {
            sunDir = sun.clone();
            sunE = new double[]{2.5, 2.4, 2.2};
            moonDir = new double[]{0, -1, 0};
            moonE = new double[3];
            skyPi = new double[]{0.25, 0.3, 0.4};
            fog = 60_000;
        }
        leben.drawOpaque(gl, vp, camera.eye, reversedZ, sunDir, sunE, moonDir, moonE, skyPi, fog, sunAtEye, clock.t(), camera.eye[1] - camera.groundLevel);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrColorFbo);
        gl.glViewport(0, 0, w, h);
        leben.drawRauch(gl, vp, camera.eye, reversedZ, depthTex, NEAR, FAR, sunDir, sunE, moonDir, moonE, skyPi, fog);
        // Phase 11: Flocken mit Tiefentest gegen die Szene
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        leben.drawFlocken(gl, vp, camera.eye, reversedZ, w, h, fovy(), sunDir, sunE, moonDir, moonE, skyPi, fog, clock.t());
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrColorFbo);
    }

    /** Phase 8: zeigt der Boden die Bodenkarte (Heide, Moor, Blockfeld)? */
    public volatile boolean showBoden = true;

    /** Phase 8: Felsburgen und Blockmeer im Licht wie das Gelände. */
    private void drawFels(GL4 gl, float[] vp, double[] sun, int h) {
        double[] sunDir, sunE, skyPi;
        double fog;
        if (frameSky && frameSt != null) {
            Sky.State st = frameSt;
            double pre = framePre;
            boolean above = camera.eye[1] > wetter.clouds.snapshot().top();
            sunDir = st.sunDir.clone();
            sunE = Wetter.scale(st.sunColor, pre * cloudShade);
            skyPi = Wetter.scale(wetter.ambient(st, showClouds, above), pre / Math.PI);
            fog = Wetter.fogDistance(st);
        } else {
            sunDir = sun.clone();
            sunE = new double[]{2.5, 2.4, 2.2};
            skyPi = new double[]{0.25, 0.3, 0.4};
            fog = 60_000;
        }
        fels.raueis = raueisBand(com.dan.brocken.wiring.Raueis.FELS);
        fels.draw(gl, vp, camera.eye, reversedZ, Math.toRadians(fovy()) / Math.max(1, h), sunDir, sunE, skyPi, sunAtEye, fog, 0,
                showSchnee ? Math.min(1, wiring.schnee(camera.eye[1]) / 0.3) * 0.85 : 0);
    }

    /** Phase 9: der Wald im Licht wie Gras und Kolkraben, mit Wind am Auge und den Wolkenschatten. */
    /** Phase 12: das Gleis im Licht wie das Gelände (Sonne als Bestrahlung, Himmel als Bestrahlung/π), mit Schnee. */
    private void drawGleis(GL4 gl, float[] vp, double[] sun) {
        if (!gleisOk || !showGleis) return;
        com.dan.rail.gl.RailGl.View v = new com.dan.rail.gl.RailGl.View();
        v.viewProj = vp;
        v.eye = camera.eye.clone();
        v.reversedZ = reversedZ;
        com.dan.rail.gl.RailGl.Light l = new com.dan.rail.gl.RailGl.Light();
        if (frameSky && frameSt != null) {
            Sky.State st = frameSt;
            boolean above = camera.eye[1] > wetter.clouds.snapshot().top();
            l.sunDir = st.sunDir.clone();
            l.sunColor = Wetter.scale(st.sunColor, framePre * cloudShade * sunAtEye);
            l.skyColor = Wetter.scale(wetter.ambient(st, showClouds, above), framePre / Math.PI);
            l.fogColor = Leben.scale(l.skyColor, 2.5);
            l.fogDistance = Wetter.fogDistance(st);
        } else l.sunDir = sun.clone();
        l.snow = showSchnee ? Math.min(1, wiring.schnee(camera.eye[1]) / 0.1) : 0;
        l.wetness = Math.min(1, wiring.rainRate / 2);
        // hoch über dem Gelände weiter (die Spirale von oben), sonst 1,6 km
        gleisGl.range = 1600 + 2 * Math.max(0, camera.eye[1] - camera.groundLevel);
        gleisGl.draw(gl, v, l);
        for (com.dan.rail.gl.RailGl a : ausweichGl) { a.range = gleisGl.range; a.draw(gl, v, l); }
        // Phase 13: die Züge (nass glänzt der Lack, Schnee bleibt nur wenig auf den Dächern)
        if (zugOk && showZuege && zugbetrieb != null) {
            zugGl.range = 3000 + 2 * Math.max(0, camera.eye[1] - camera.groundLevel);
            zugGl.draw(gl, v, l, strecke.gleis, zugbetrieb.zuege);
        }
    }

    /** Phase 13: Tag und Sekunde der Szenenuhr (Ortszeit) an den Zugbetrieb, dann der Dampf an den Rauch. */
    private void zugStep() {
        if (zugbetrieb == null || !zugbetrieb.ok()) return;
        java.time.ZonedDateTime lt = clock.local();
        zugbetrieb.step(lt.toLocalDate().toEpochDay(), lt.toLocalTime().toSecondOfDay() + lt.getNano() / 1e9);
        java.util.List<com.dan.smoke.Source> q = new java.util.ArrayList<>();
        q.add(leben.hotel);
        if (showZuege) q.addAll(zugbetrieb.dampf());
        leben.rauch.setSources(q);
    }

    /** Phase 14: die Menschen einen Schritt (Szenenzeit, Ortszeit), Wetter am Gipfel. */
    private void menschenStep() {
        if (menschen == null) return;
        java.time.ZonedDateTime lt = clock.local();
        com.dan.weather.Weather.Snapshot ws = wiring.snapshot();
        double sicht = ws.visibilityAt(1141) * 1000;
        menschen.step(lt.toLocalDate().toEpochDay(), lt.toLocalTime().toSecondOfDay() + lt.getNano() / 1e9, lt.getMonthValue(),
                Math.max(0, ws.rain()), wiring.windSpeed, sicht, camera.eye);
    }

    /** Phase 13: mit dem nächsten fahrenden Zug mitfahren (sonst dem nächsten, der steht); false ohne Zug. */
    public boolean mitfahren() {
        if (zugbetrieb == null) return false;
        com.dan.rail.Train t = zugbetrieb.mitfahrZug(camera.eye[0], camera.eye[2]);
        if (t == null) return false;
        camera.startMitfahrt(() -> zugbetrieb.fuehrerstand(t));
        mitZug = t;
        return true;
    }

    private volatile com.dan.rail.Train mitZug;

    /** Phase 17: der Zug, auf dem das Auge mitfährt (Name), oder null. */
    public String mitfahrZug() {
        com.dan.rail.Train t = camera.mitfahrt != null ? mitZug : null;
        return t == null ? null : t.name;
    }

    /** Phase 17: Bilder je s und Grafikkarte (ms) im Mittel des laufenden oder letzten Regiebuchs. */
    public double[] regieTempo() { return new double[]{albumFps / Math.max(1, albumSamples), albumGpu / Math.max(1, albumSamples)}; }

    /** „Züge: … · Pfiff …“ für HUD und Bedienfeld. */
    public String zugZeile() {
        if (zugbetrieb == null) return "Züge: keine Strecke";
        String m = zugbetrieb.meldung();
        com.dan.rail.Train t = camera.mitfahrt != null ? mitZug : null;
        return zugbetrieb.describe() + (t != null ? String.format(Locale.GERMAN, " · Mitfahrt: %s, Last %.0f %%, %.1f Auspuffschläge je s", t.name, t.last() * 100, t.schlaege()) : "")
                + (m.isEmpty() ? "" : " · " + m) + (showZuege ? "" : " · aus");
    }

    /** Phase 14: die Bauten am Gipfel im Licht wie das Gleis, mit Schnee, Nässe, Raueis und Fenstern in der Nacht. */
    private void drawGipfel(GL4 gl, float[] vp, double[] sun) {
        if (!gipfelGl.ready() || !showGipfel) return;
        com.dan.rail.gl.RailGl.Light rl = railLicht(sun);
        GipfelGl.Licht l = new GipfelGl.Licht();
        l.sunDir = rl.sunDir; l.sunColor = rl.sunColor; l.skyColor = rl.skyColor; l.fogColor = rl.fogColor; l.fogDistance = rl.fogDistance;
        l.snow = rl.snow; l.wetness = rl.wetness;
        double alt = clock.sun()[0];
        l.nacht = 1 - smooth(-4, 6, alt);
        l.pre = framePre / com.dan.sky.Sky.CD_PER_UNIT;
        l.zeit = clock.t();
        if (showRaueis) {
            double[] rb = raueisBand(com.dan.brocken.wiring.Raueis.MAST);
            l.rimeBase = rb[0]; l.rimeFull = rb[1]; l.rime = rb[2]; l.rimeFrom = rb[4];
        }
        gipfelGl.draw(gl, vp, camera.eye, reversedZ, l);
    }

    /** Phase 14: die Menschen aus com.dan.figure im Licht der Szene (Sonne, Mond, Himmel, Dunst, Schatten der Wolken). */
    private void drawMenschen(GL4 gl, float[] vp, double[] sun) {
        if (!figurOk || !showMenschen || menschen == null) return;
        java.util.List<com.dan.figure.Person> ps = menschen.personen();
        if (ps.isEmpty()) return;
        com.dan.figure.gl.FigureGl.View v = new com.dan.figure.gl.FigureGl.View();
        v.viewProj = vp;
        v.eye = camera.eye.clone();
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        com.dan.figure.gl.FigureGl.Light l = new com.dan.figure.gl.FigureGl.Light();
        com.dan.rail.gl.RailGl.Light rl = railLicht(sun);
        l.sunDir = rl.sunDir;
        l.sunColor = rl.sunColor;
        // figure mischt den Himmel aus Horizont und Zenit und nimmt den Horizont für den Dunst [G: so nahe am Licht des Gleises]
        l.skyTop = Leben.scale(rl.skyColor, 0.6);
        l.skyHorizon = Leben.scale(rl.skyColor, 1.6);
        l.sunShadow = 1;
        l.fogDistance = rl.fogDistance;
        figurGl.draw(gl, ps, v, l);
    }

    /** Phase 12/14: Licht für Gleis, Züge, Gipfel und Menschen. */
    com.dan.rail.gl.RailGl.Light railLicht(double[] sun) {
        com.dan.rail.gl.RailGl.Light l = new com.dan.rail.gl.RailGl.Light();
        if (frameSky && frameSt != null) {
            Sky.State st = frameSt;
            boolean above = camera.eye[1] > wetter.clouds.snapshot().top();
            l.sunDir = st.sunDir.clone();
            l.sunColor = Wetter.scale(st.sunColor, framePre * cloudShade * sunAtEye);
            l.skyColor = Wetter.scale(wetter.ambient(st, showClouds, above), framePre / Math.PI);
            l.fogColor = Leben.scale(l.skyColor, 2.5);
            l.fogDistance = Wetter.fogDistance(st);
        } else l.sunDir = sun.clone();
        l.snow = showSchnee ? Math.min(1, wiring.schnee(camera.eye[1]) / 0.1) : 0;
        l.wetness = Math.min(1, wiring.rainRate / 2);
        return l;
    }

    /**
     * Phase 14: Ansichten am Gipfel: „bahnsteig“ (auf dem Bahnsteig, zum Gipfel, der Zug am rechten Rand), „gipfelstein“ (über die
     * Brockenuhr zum Hotel und Sendeturm), „gipfel“ (von oben über die Kuppe). false, wenn es den Ort nicht gibt.
     */
    public boolean kameraGipfel(String wo) {
        double[] st = gipfel.stein;
        switch (wo) {
            case "bahnsteig" -> {
                if (strecke == null) return false;
                com.dan.rail.Track g = strecke.gleis;
                com.dan.rail.Track.Pose p = g.at(strecke.sBrocken - 20);
                com.dan.rail.Track.Station sn = g.stationAt(strecke.sBrocken);
                double h = Math.toRadians(p.heading()), side = sn == null || sn.side() == 0 ? 1 : sn.side();
                double x = p.x() + Math.cos(h) * 3.4 * side, z = p.z() + Math.sin(h) * 3.4 * side;
                // zum Gipfel, den Zug am rechten Rand (der Bahnsteig liegt auf der Seite des Gipfels)
                double hd = st == null ? p.heading() : Math.toDegrees(Math.atan2(st[0] - x, -(st[2] - z))) + 22;
                camera.place(x, groundHeight(x, z) + 1.7, z, (hd + 720) % 360, -2);
            }
            case "gipfelstein" -> {
                if (st == null) return false;
                double x = st[0] + 25, z = st[2] + 22;
                double hd = Math.toDegrees(Math.atan2(st[0] - x, -(st[2] - z))) - 6;
                camera.place(x, groundHeight(x, z) + 1.7, z, (hd + 360) % 360, 4);
            }
            case "gipfel" -> {
                if (st == null) return false;
                double x = st[0] + 210, z = st[2] + 84;
                double hd = Math.toDegrees(Math.atan2(st[0] - x, -(st[2] - z)));
                camera.place(x, groundHeight(x, z) + 90, z, (hd + 360) % 360, 22);
            }
            default -> { return false; }
        }
        return true;
    }

    /** „Gipfel: … · Menschen: …“ für HUD und Bedienfeld. */
    public String gipfelZeile() {
        return gipfel.describe() + (gipfelGl.ready() ? " · " + gipfelGl.info() : "") + (showGipfel ? "" : " · aus")
                + " · " + (menschen == null ? "keine Menschen" : menschen.describe()) + (showMenschen ? "" : " · aus");
    }

    /** Phase 12: Schwellen aus Beton (sonst Holz). */
    public void gleisBeton(boolean b) { gleisGl.concrete = b; }

    /** „Bahn: Brockenbahn … · hier 120 m vom Gleis“ für HUD und Bedienfeld. */
    public String bahnZeile() {
        if (strecke == null) return "Bahn: keine Strecke (kein ALKIS-Auszug)";
        double d = strecke.abstand(camera.eye[0], camera.eye[2]);
        return "Bahn: " + strecke.describe() + (gleisOk ? " · " + gleisGl.info() : "")
                + (d < 1000 ? String.format(Locale.GERMAN, " · hier %.0f m vom Gleis", d) : "") + (showGleis ? "" : " · aus");
    }

    private void drawWald(GL4 gl, float[] vp, double[] sun, boolean cloudsNow) {
        double[] sunDir, sunE, moonDir, moonE, skyPi;
        double fog;
        if (frameSky && frameSt != null) {
            Sky.State st = frameSt;
            double pre = framePre;
            boolean above = camera.eye[1] > wetter.clouds.snapshot().top();
            sunDir = st.sunDir.clone();
            sunE = Wetter.scale(st.sunColor, pre);
            moonDir = st.moonDir.clone();
            moonE = Wetter.scale(st.moonColor, pre);
            skyPi = Wetter.scale(wetter.ambient(st, showClouds, above), pre / Math.PI);
            fog = Wetter.fogDistance(st);
        } else {
            sunDir = sun.clone();
            sunE = new double[]{2.5, 2.4, 2.2};
            moonDir = new double[]{0, -1, 0};
            moonE = new double[3];
            skyPi = new double[]{0.25, 0.3, 0.4};
            fog = 60_000;
        }
        double[] v = wiring.windAt(camera.eye[0], camera.eye[1], camera.eye[2]);
        double sp = Math.hypot(v[0], v[2]);
        boolean shadow = cloudsNow && !Boolean.getBoolean("brocken.ohne.wolkenschatten");
        // Phase 11: Schnee in den Kronen in Augenhöhe (gestuft, sonst backt der Wald jedes Bild neu)
        double kr = showSchnee ? Math.round(wiring.jahr.treeSnow(camera.eye[1]) * 5) / 5.0 : 0;
        double[] rb = raueisBand(com.dan.brocken.wiring.Raueis.ZWEIG);
        wald.waldGl.setRime(Math.min(0.3, rb[2]), (int) rb[3], rb[4], rb[0], rb[1]);
        wald.draw(gl, vp, camera.eye, reversedZ, clock.local().getDayOfYear(), (float) kr, new double[]{v[0], v[2]}, sp, Math.min(1, wiring.gustiness * 2.5),
                sunDir, sunE, moonDir, moonE, skyPi, fog, sunAtEye, shadow ? wetter.cloudGl.shadowTexture() : 0,
                shadow ? wetter.cloudGl.shadowRect() : new double[]{0, 0, 1}, 0, 0, clock.t());
    }

    // ------------------------------------------------------------------ Phase 10: Raueis

    /** Raueis zeigen (Gelände, Wald, Fels). */
    public volatile boolean showRaueis = true;

    /** {base, full, dicke, art, woher} eines Objekts für forest und cliff (die nur eine Dicke und ein Band kennen). */
    double[] raueisBand(int k) {
        com.dan.brocken.wiring.Raueis r = wiring.raueis;
        com.dan.rime.Rime.Art a = showRaueis ? r.art() : com.dan.rime.Rime.Art.KEINS;
        double[] b = r.band(k);
        return new double[]{b[0], b[1], a == com.dan.rime.Rime.Art.KEINS ? 0 : b[2], a.ordinal(), r.fahneVon()};
    }

    /** Die Bänder des Eises an den Gelände-Shader (rime.glsl und uRaueisZweig/uRaueisFels). */
    private void bindRaueis(GL4 gl, int prog) {
        com.dan.brocken.wiring.Raueis r = wiring.raueis;
        com.dan.rime.Rime.Art a = showRaueis ? r.art() : com.dan.rime.Rime.Art.KEINS;
        double von = Math.toRadians(r.fahneVon());
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uRimeArt"), a.ordinal());
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uRimeVon"), (float) Math.sin(von), 0f, (float) -Math.cos(von));
        gl.glUniform1fv(gl.glGetUniformLocation(prog, "uRaueisZweig"), 12, r.dicken(com.dan.brocken.wiring.Raueis.ZWEIG), 0);
        gl.glUniform1fv(gl.glGetUniformLocation(prog, "uRaueisFels"), 12, r.dicken(com.dan.brocken.wiring.Raueis.FELS), 0);
    }

    /** „Raueis am Gipfel: …“ für HUD und Bedienfeld. */
    public String raueisZeile() {
        return wiring.raueis.zeile(wiring.snapshot(), wiring.tropfenDurchmesser(), clock.sun()[0]) + (showRaueis ? "" : " · aus");
    }

    /** „Boden … · Fels …“ für HUD und Bedienfeld. */
    public String bodenZeile() {
        return boden.describe() + " · hier " + bodenHier() + " · " + fels.zeile() + " · " + leben.bodenZeile();
    }

    /** Was die Bodenkarte am Auge sagt: „Moor 80 %, Heide 10 %“ oder „Wald“. */
    public String bodenHier() {
        double x = camera.eye[0], z = camera.eye[2];
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 4; k++) {
            double v = boden.at(k, x, z);
            if (v > 0.05) sb.append(sb.length() > 0 ? ", " : "").append(com.dan.brocken.scene.Bodenkarte.NAMEN[k]).append(String.format(Locale.GERMAN, " %.0f %%", v * 100));
        }
        return sb.length() == 0 ? Terrain.ART[terrain.artAt(x, z)] : sb.toString();
    }

    /** Phase 4: Textur des Felds bei neuem Feld oder geändertem Faktor hochladen, Pfeile und Rauchfäden zeichnen. */
    private void drawWind(GL4 gl, float[] vp, float[] inv, double[] sun) {
        Windrechner.Ergebnis e = windSeen;
        if (e == null) return;
        double k = wiring.windGain;
        if (e != windUploaded || Math.abs(k / windGainUploaded - 1) > 0.08) {
            float[] d = e.sample().clone();
            float f = (float) k;
            for (int i = 0; i < d.length; i++) d[i] *= f;
            windGl.upload(gl, e.grid(), d);
            windGainUploaded = k;
            if (e != windUploaded) eigeneNeu = true;
            windUploaded = e;
        }
        if (eigeneNeu) { eigeneNeu = false; uploadFaeden(gl, e); }
        if (!showArrows && !showFaeden && fadenAuto == fadenStart.length) return;
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        float ink = (float) inkBrightness();
        if (showArrows) {
            common(gl, pfeilShader, vp, inv, sun);
            lod.bind(gl, pfeilShader);
            windGl.bind(gl, pfeilShader.id(), 15);
            double agl = Math.max(20, camera.eye[1] - camera.groundLevel);
            double sp = Math.max(40, Math.min(600, Math.round(agl * 0.12 / 20) * 20.0));
            int n = 40;
            double cx = Math.floor(camera.tx / sp) * sp - n / 2.0 * sp, cz = Math.floor(camera.tz / sp) * sp - n / 2.0 * sp;
            pfeilShader.set(gl, "uArrowGrid", (float) cx, (float) cz, (float) sp, (float) n);
            pfeilShader.set(gl, "uArrowH", (float) arrowHeight);
            pfeilShader.set(gl, "uArrowScale", 20f);
            pfeilShader.set(gl, "uGain", 1f);
            pfeilShader.set(gl, "uInk", ink);
            gl.glBindVertexArray(emptyVao);
            gl.glDrawArraysInstanced(GL.GL_LINES, 0, 6, n * n);
        }
        if (fadenVao != 0 && fadenStart.length > (showFaeden ? 0 : fadenAuto)) {
            common(gl, fadenShader, vp, inv, sun);
            fadenShader.set(gl, "uLen", (float) Windrechner.FADEN_LEN);
            fadenShader.set(gl, "uInk", ink);
            gl.glBindVertexArray(fadenVao);
            // die eigenen (Strg + Klick) immer, die 24 aus dem Rechner nach dem Schalter
            for (int i = showFaeden ? 0 : fadenAuto; i < fadenStart.length; i++) gl.glDrawArrays(GL.GL_LINE_STRIP, fadenStart[i], fadenCount[i]);
        }
        gl.glBindVertexArray(emptyVao);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
    }

    /** Helligkeit der Pfeile und Fäden: wie heller Grund im vollen Licht (wie die Marken), vorbelichtet. */
    private double inkBrightness() {
        if (!frameSky || frameSt == null) return Sun.skyBrightness(clock.sun()[0]) / 0.32;
        Sky.State st = frameSt;
        double[] a = wetter.ambient(st, showClouds, camera.eye[1] > wetter.clouds.snapshot().top());
        double l = 0;
        double[] lw = {0.2126, 0.7152, 0.0722};
        for (int c = 0; c < 3; c++) l += lw[c] * (st.sunColor[c] * Math.max(0, st.sunDir[1]) / Math.PI + a[c] / Math.PI + st.moonColor[c] * Math.max(0, st.moonDir[1]) / Math.PI);
        // nie dunkler als ein mittleres Grau nach der Belichtung: die Pfeile sind eine Marke, auch nachts lesbar
        return Math.max(0.25, l * 0.8 * framePre);
    }

    /**
     * Phase 17: der Punkt im Gelände, den der Blick durch den Bildpunkt (px, py) trifft: {x, y, z, Abstand} oder null
     * (Himmel getroffen). Mit Erdkrümmung wie in den Shadern.
     */
    public double[] bodenUnter(int px, int py, int w, int h) {
        if (w <= 0 || h <= 0) return null;
        float[] inv = Mat4.invert(Mat4.mul(Mat4.perspective(fovy(), w / (double) h, NEAR, FAR, reversedZ), camera.view()));
        double[] q = Mat4.apply(inv, 2.0 * (px + 0.5) / w - 1, 1 - 2.0 * (py + 0.5) / h, 0.5);
        double[] o = camera.eye.clone(), d = {q[0] / q[3] - o[0], q[1] / q[3] - o[1], q[2] / q[3] - o[2]};
        double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        for (int c = 0; c < 3; c++) d[c] /= l;
        double t0 = 0, t = 0;
        boolean hit = false;
        while (t < 40_000) {
            double x = o[0] + d[0] * t, z = o[2] + d[2] * t, dh = Math.hypot(x - o[0], z - o[2]);
            // Erdkrümmung wie in den Shadern: das Gelände sinkt mit dem Abstand
            if (o[1] + d[1] * t < groundHeight(x, z) - dh * dh / (2 * 6_371_000 / 0.87)) { hit = true; break; }
            t0 = t;
            t += Math.max(2, t * 0.004);
        }
        if (!hit) return null;
        for (int k = 0; k < 24; k++) {
            double m = (t0 + t) / 2, x = o[0] + d[0] * m, z = o[2] + d[2] * m, dh = Math.hypot(x - o[0], z - o[2]);
            if (o[1] + d[1] * m < groundHeight(x, z) - dh * dh / (2 * 6_371_000 / 0.87)) t = m; else t0 = m;
        }
        double x = o[0] + d[0] * t, z = o[2] + d[2] * t;
        return new double[]{x, groundHeight(x, z), z, t};
    }

    /**
     * Phase 4: ein Klick in die Szene setzt einen Rauchfaden in {@link Windrechner#FADEN_H} m über dem Grund, den der
     * Blick dort trifft (seit Phase 17 mit Strg; Umschalt + Klick setzt einen Merkpunkt). Gibt eine Meldung zurück
     * oder null (kein Feld, Himmel getroffen, außerhalb des Felds).
     */
    public String fadenSetzen(int px, int py, int w, int h) {
        Windrechner.Ergebnis e = windSeen;
        if (e == null) return null;
        double[] b = bodenUnter(px, py, w, h);
        if (b == null) return null;
        double x = b[0], z = b[2], t = b[3];
        if (!e.solution().contains(x, z)) return "außerhalb des Windfelds";
        eigeneQuellen.add(new double[]{x, z});
        while (eigeneQuellen.size() > EIGENE_FAEDEN) eigeneQuellen.remove(0);
        eigeneNeu = true;
        return String.format(Locale.GERMAN, "Rauchfaden gesetzt · %s · %.0f m vom Auge (%d von %d)", Buehne.naechsterOrt(x, z), t, eigeneQuellen.size(), EIGENE_FAEDEN);
    }

    /** Die mit Strg + Klick gesetzten Rauchfäden entfernen. */
    public void eigeneFaedenWeg() { eigeneQuellen.clear(); eigeneNeu = true; }

    public int eigeneFaeden() { return eigeneQuellen.size(); }

    private void uploadFaeden(GL4 gl, Windrechner.Ergebnis e) {
        java.util.List<float[]> alle = new java.util.ArrayList<>(e.faeden());
        fadenAuto = alle.size();
        for (double[] s : eigeneQuellen) { float[] f = Windrechner.faden(e.solution(), s[0], s[1], Windrechner.FADEN_H); if (f != null) alle.add(f); }
        int total = 0;
        for (float[] f : alle) total += f.length / 4;
        float[] all = new float[total * 4];
        fadenStart = new int[alle.size()];
        fadenCount = new int[alle.size()];
        int o = 0, i = 0;
        for (float[] f : alle) {
            System.arraycopy(f, 0, all, o * 4, f.length);
            fadenStart[i] = o;
            fadenCount[i] = f.length / 4;
            o += f.length / 4;
            i++;
        }
        if (fadenVao == 0) {
            int[] t = new int[1];
            gl.glGenVertexArrays(1, t, 0);
            fadenVao = t[0];
            gl.glGenBuffers(1, t, 0);
            fadenVbo = t[0];
        }
        gl.glBindVertexArray(fadenVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, fadenVbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) all.length * 4, Buffers.newDirectFloatBuffer(all), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 4, GL.GL_FLOAT, false, 16, 0);
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        fadenPunkte = total;
    }

    private static void rect(GL4 gl, Shader s, String u, Site.Fenster f) {
        double[] r = f.scene();
        s.set(gl, u, (float) r[0], (float) r[1], (float) r[2], (float) r[3]);
    }

    /** Die Orte als vec4 (x, z, Halbmesser m, Art) für marken.glsl. */
    static float[] points() {
        Site.Ort[] o = Site.ORTE;
        float[] p = new float[Math.min(16, o.length) * 4];
        for (int i = 0; i < p.length / 4; i++) {
            p[i * 4] = (float) o[i].x();
            p[i * 4 + 1] = (float) o[i].z();
            p[i * 4 + 2] = o[i].art() == Site.Art.GIPFEL ? 18f : 35f;
            p[i * 4 + 3] = o[i].art().ordinal();
        }
        return p;
    }

    private void common(GL4 gl, Shader s, float[] vp, float[] inv, double[] sun) {
        s.use(gl);
        // Phase 3: mit com.dan.sky Himmel aus dem Rundumbild (Einheit 7), Sonne und Mond vorbelichtet, Dunst aus der Sicht
        s.set(gl, "uSkyMode", frameSky ? 1 : 0);
        if (frameSky) {
            Sky.State st = frameSt;
            double pre = framePre;
            gl.glActiveTexture(GL.GL_TEXTURE7);
            gl.glBindTexture(GL.GL_TEXTURE_2D, wetter.panoTex);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            boolean above = camera.eye[1] > wetter.clouds.snapshot().top();
            s.set(gl, "uPano", 7);
            s.set(gl, "uSunColor", Wetter.scale(st.sunColor, pre));
            s.set(gl, "uMoonColor", Wetter.scale(st.moonColor, pre));
            s.set(gl, "uMoonDir", st.moonDir);
            s.set(gl, "uFogDist", (float) Wetter.fogDistance(st));
            s.set(gl, "uPre", (float) pre);
            s.set(gl, "uSkyAmb", Wetter.scale(wetter.ambient(st, showClouds, above), pre / Math.PI));
        }
        s.mat(gl, "uVP", vp);
        s.mat(gl, "uInvVP", inv);
        s.set(gl, "uCam", camera.eye);
        s.set(gl, "uSun", sun);
        s.set(gl, "uTime", (float) (clock.t() % 10_000));
        s.set(gl, "uLevel", 0f);
        s.set(gl, "uReversed", reversedZ ? 1 : 0);
    }

    private void resizeTargets(GL4 gl, int w, int h) {
        Gpu.deleteFbos(gl, hdrFbo, hdrColorFbo);
        Gpu.deleteTextures(gl, hdrTex, depthTex);
        hdrTex = Gpu.texture(gl, w, h, GL.GL_RGBA16F, false);
        depthTex = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        hdrFbo = Gpu.fbo(gl, hdrTex, depthTex);
        hdrColorFbo = Gpu.fbo(gl, hdrTex, 0);
        hdrW = w;
        hdrH = h;
    }

    // ------------------------------------------------------------------ Zeitmessung (Tempo)

    private void mark(GL4 gl, int i) { gl.glQueryCounter(stamp[frame & 1][i], GL4.GL_TIMESTAMP); }

    /** Liest die Zeitstempel des vorigen Bilds, sobald sie da sind (kein Warten im Bildlauf). */
    private void readMarks(GL4 gl) {
        if (frame < 2) return;
        int o = (frame - 1) & 1;
        int[] av = new int[1];
        gl.glGetQueryObjectiv(stamp[o][MARKS - 1], GL4.GL_QUERY_RESULT_AVAILABLE, av, 0);
        if (av[0] == 0) return;
        long[] t = new long[MARKS];
        for (int i = 0; i < MARKS; i++) gl.glGetQueryObjecti64v(stamp[o][i], GL4.GL_QUERY_RESULT, t, i);
        for (int i = 0; i < MARKS - 1; i++) passMs[i] = passMs[i] * 0.9 + Math.max(0, t[i + 1] - t[i]) / 1e6 * 0.1;
        gpuMs = gpuMs * 0.9 + Math.max(0, t[MARKS - 1] - t[0]) / 1e6 * 0.1;
    }

    /** „Himmel 0,10 · Bühne 0,60 · …“ in ms (gleitendes Mittel). */
    public String passTimes() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < passMs.length; i++)
            sb.append(i > 0 ? " · " : "").append(PASSES[i]).append(' ').append(String.format(Locale.GERMAN, "%.2f", passMs[i]));
        return sb.toString();
    }

    /** Die n teuersten Durchgänge: „Bühne 1,20 · Himmel 0,10“. */
    public String topPasses(int n) {
        Integer[] idx = new Integer[passMs.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(passMs[b], passMs[a]));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, idx.length); i++)
            sb.append(i > 0 ? " · " : "").append(PASSES[idx[i]]).append(' ').append(String.format(Locale.GERMAN, "%.2f", passMs[idx[i]]));
        return sb.toString();
    }

    private void stats(long now, int w, int h) {
        statsFrames++;
        if (now - statsNanos < 500_000_000L) return;
        fps = statsFrames / ((now - statsNanos) / 1e9);
        statsFrames = 0;
        statsNanos = now;
        statusListener.accept(statusLine(w, h));
    }

    /** Auflösung nach der Zeit der Grafikkarte regeln (siehe {@link #autoScale}). */
    private void regulateScale(long now) {
        if (!autoScale || frame < 60 || now - scaleNanos < 1_500_000_000L) return;
        scaleNanos = now;
        double budget = 1000 / Math.max(10, targetFps), top = quality.renderScale, s = renderScale;
        double want = s * Math.sqrt(budget * 0.9 / Math.max(0.1, gpuMs));
        double next = s;
        if (gpuMs > budget * 1.05) next = Math.max(MIN_SCALE, Math.max(want, s - 0.15));
        else if (gpuMs < budget * 0.7 && s < top) next = Math.min(top, Math.min(want, s + 0.05));
        next = Math.round(next * 20) / 20.0;
        if (Math.abs(next - s) >= 0.045) { renderScale = next; scaleSteps++; }
    }

    /** Wie oft die Regelung die Auflösung geändert hat. */
    public int scaleSteps() { return scaleSteps; }

    /** Für Prüfungen: die Regelung mit vorgegebener Grafikkartenzeit und Bildnummer. */
    public void regulateForTest(double gpuMsNow, long nowNanos) {
        gpuMs = gpuMsNow;
        frame = Math.max(frame, 60);
        regulateScale(nowNanos);
    }

    public String statusLine(int w, int h) {
        String s = String.format(Locale.GERMAN, "%s · %.0f Bilder/s · Grafikkarte %.2f ms · Prozessor %.2f ms · %d×%d (%s%s) · %s",
                clock.label(), fps, gpuMs, cpuMs, w, h, quality.label, autoScale ? String.format(Locale.GERMAN, ", %.0f %%", renderScale * 100) : "", glInfo);
        long nowMs = System.currentTimeMillis();
        if (nowMs < meldungUntil) s = meldung + " · " + s;
        if (nowMs < photoMsgUntil) s = photoMsg + " · " + s;
        return s;
    }

    // ------------------------------------------------------------------ HUD

    BufferedImage paintHud() {
        String[] lines = hudFull ? hudLines() : hudShort();
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        java.awt.FontMetrics fm = pg.getFontMetrics(font);
        int tw = 0;
        for (String l : lines) tw = Math.max(tw, fm.stringWidth(l.startsWith("!") ? l.substring(1) : l));
        pg.dispose();
        int w = Math.max(200, tw + 22), lh = 17, h = 16 + lines.length * lh;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(15, 19, 21, 190));
        g.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
        g.setColor(new Color(159, 192, 206, 225));   // Nebelblau, wie das Werkbuch
        g.setStroke(new BasicStroke(2f));
        g.drawLine(8, 1, 60, 1);
        g.setFont(font);
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            boolean warn = l.startsWith("!");
            if (warn) l = l.substring(1);
            g.setColor(i == 0 ? new Color(232, 234, 233) : warn ? new Color(224, 163, 63) : new Color(186, 194, 196));
            g.drawString(l, 10, 18 + i * lh);
        }
        g.dispose();
        return im;
    }

    /** Das kurze HUD: Tempo, Uhr und Sonne, Kamera. */
    String[] hudShort() {
        java.util.List<String> l = new java.util.ArrayList<>();
        String q = autoScale ? String.format(Locale.GERMAN, "%s %.0f %%", quality.label, renderScale * 100) : quality.label;
        l.add(String.format(Locale.GERMAN, "%.0f Bilder/s · Grafikkarte %.1f ms · Prozessor %.1f ms · %s %d×%d", fps, gpuMs, cpuMs, q, hdrW, hdrH));
        l.add(passTimes());
        double[] sp = clock.sun();
        l.add(String.format(Locale.GERMAN, "%s · Sonne %.0f° aus %03.0f° · %s", clock.label(), sp[0], sp[1], wetterKurz()));
        l.add(cameraLine() + " · H: volles HUD, F1: Tasten");
        if (regie.running()) l.add(regieLine());
        if (System.currentTimeMillis() < meldungUntil) l.add(meldung);
        if (error != null) l.add("!" + error.split("\n")[0]);
        return l.toArray(new String[0]);
    }

    /** „Windfeld 254 × 238 × 30 … · Gipfel 14 m/s (Faktor 2,1) · am Auge 9 m/s aus 255°, Böe 13 m/s · Kaltluft 0 m“ */
    public String windZeile() {
        Windrechner.Ergebnis e = windSeen;
        if (e == null) return (windrechner.fehler() != null ? "!" + windrechner.fehler() : "Windfeld: " + (windrechner.busy() ? "rechnet …" : "noch nicht gerechnet"))
                + " · " + wiring.wind.stability.name().toLowerCase(Locale.ROOT);
        double[] v = wiring.windAt(camera.eye[0], camera.eye[1], camera.eye[2]);
        double sp = Math.hypot(v[0], v[2]), from = (Math.toDegrees(Math.atan2(-v[0], v[2])) + 360) % 360;
        double gust = wiring.wind.gustAt(camera.eye[0], camera.eye[1], camera.eye[2], clock.t()) * wiring.windGain;
        return String.format(Locale.GERMAN, "Windfeld %s, %s, Hangwind %+.1f m/s%s · Wetterwarte in 27 m %.0f m/s (Faktor %.2f) · am Auge %.1f m/s aus %03.0f°, Böe %.1f m/s · Kaltluft hier %.0f m",
                e.solution().describe(), e.solution().stability.name().toLowerCase(Locale.ROOT), e.solution().slopeFlow * wiring.windGain,
                windrechner.busy() ? " · rechnet neu …" : "", e.summitRaw() * wiring.windGain, wiring.windGain, sp, from, gust,
                wiring.wind.coldPoolAt(camera.eye[0], camera.eye[2], 0));
    }

    /** Himmel und Wolken in zwei Zeilen (Bedienfeld). */
    public String wetterZeilen() {
        String[] d = wetter.describe(showClouds, cloudShade);
        return d[0].replace("!", "") + " · " + d[1].replace("!", "") + " · " + schatten.describe();
    }

    /** „Inversion · 8/8 Stratus 450–850 m · 15 °C am Auge“ für das kurze HUD. */
    String wetterKurz() {
        com.dan.weather.Weather.Snapshot s = wiring.snapshot();
        double eye = camera.eye[1];
        String wo = s.cloudAt(eye) > 0.5 ? "in der Wolke" : eye > s.cloudTop() && s.oktas() > 4 ? "über den Wolken" : "";
        return String.format(Locale.GERMAN, "%s · %.0f/8 %s %.0f–%.0f m%s · %.1f °C", com.dan.brocken.wiring.KlimaBrocken.name(s.phase()), s.oktas(),
                wiring.cloudType.label, s.cloudBase(), s.cloudTop(), wo.isEmpty() ? "" : " · " + wo, s.temperatureAt(eye));
    }

    /** „Kreisen · Auge 1 240 m über NHN, 620 m über Grund · Blick 272° · Kern · 1,9 km bis Goetheweg“ */
    public String cameraLine() {
        return String.format(Locale.GERMAN, "%s · Auge %.0f m über NHN, %.0f m über Grund · Blick %03.0f° · %s · %s", camera.modeName(), camera.eye[1],
                camera.eye[1] - camera.groundLevel, camera.heading(), Buehne.zone(camera.eye[0], camera.eye[2]), Buehne.naechsterOrt(camera.eye[0], camera.eye[2]));
    }

    /** Das volle HUD (H): alles, was die Phase weiß. */
    public String[] hudLines() {
        double[] sp = clock.sun();
        ComputeProbe.Result c = compute;
        double[] ll = Site.latLon(camera.eye[0], camera.eye[2]);
        double[] utm = Site.toUtm(camera.eye[0], camera.eye[2]);
        double kimm = Site.horizon(camera.eye[1] - Buehne.VORLAND);
        return new String[]{
                String.format(Locale.GERMAN, "%.0f Bilder/s   Grafikkarte %.2f ms   Prozessor %.2f ms   Budget %.1f ms", fps, gpuMs, cpuMs, quality.budgetMs()),
                passTimes(),
                (c == null ? "!Compute-Test läuft …" : (c.ok() ? "" : "!") + c.line()),
                String.format(Locale.GERMAN, "%s · Sonne %.1f° / %03.0f° · Belichtung %.2g (%s)", clock.label(), sp[0], sp[1], exposure(),
                        !frameSky ? "Platzhalter-Himmel" : bildMessung && belichtung.lum() > 0 ? "Himmel und Bild" : "Himmel"),
                wiring.weatherLine(camera.eye[1]),
                wiring.windLine() + String.format(Locale.GERMAN, " · Sonne am Auge: Gelände %.0f %%, Wolken %.0f %%", sunAtEye * 100, cloudShade * 100),
                wetter.describe(showClouds, cloudShade)[0],
                wetter.describe(showClouds, cloudShade)[1],
                schatten.describe(),
                windZeile(),
                leben.zeile(camera.eye[0], camera.eye[2], camera.groundLevel),
                bodenZeile(),
                "Wald: " + waldkarte.describe() + " · " + wald.zeile(camera.eye[0], camera.eye[2]),
                raueisZeile(),
                jahrZeile(),
                bahnZeile(),
                zugZeile(),
                gipfelZeile(),
                klang.zeile(),
                regieLine(),
                nebelZeile(),
                lichtZeile(),
                physik.describe(),
                (terrain.measured() ? "" : "!") + "Gelände: " + terrain.describe(),
                String.format(Locale.GERMAN, "Gelände %d Knoten, %,d Punkte · fein bis %d m, im Kern %d m · Grund hier %.1f m über NHN · %s",
                        lod.nodes, lod.vertices, 2 << lod.finest, 2 << lod.finestKern, camera.groundLevel, Terrain.ART[terrain.artAt(camera.eye[0], camera.eye[2])]),
                cameraLine(),
                String.format(Locale.GERMAN, "UTM 32 N  E %.0f  N %.0f · %s · Kimm über dem Vorland %.0f km", utm[0], utm[1], Site.dms(ll[0], ll[1]), kimm / 1000),
                "Renderer.jar: " + Bibliothek.kurz(),
                "Neu für Brocken: " + Bibliothek.neuStand(),
                String.format(Locale.GERMAN, "%s · %d×%d · %s", quality.label, hdrW, hdrH, reversedZ ? "umgekehrte Tiefe" : "klassische Tiefe"),
                glInfo};
    }


    // ================================================================== Phase 16: Regiebücher und Kino

    /** Phase 16: die Regiebücher (Text mit Blatt am Ende), ihre Fotos und was sie sagen. */
    public final com.dan.brocken.scene.Regie regie = new com.dan.brocken.scene.Regie();
    private final RegieCtx regieCtx = new RegieCtx();
    /**
     * Phase 16: der Kinomodus (com.dan.kino). Ein Regiebuch im Kino: ohne Bedienfeld und HUD, Balken 2,39 : 1, Vor- und
     * Abspann, Untertitel aus den Stichworten, Überblendung bei jedem Schnitt, ruhiger Schwenk, Filmkorn, der Ton folgt
     * dem Schwarz.
     */
    public final com.dan.kino.Kino kino = new com.dan.kino.Kino();
    private com.dan.kino.gl.KinoGl kinoGl;
    private final java.util.List<Runnable> kinoSchnitte = new java.util.ArrayList<>();
    private double kinoYaw;
    private boolean kinoFensterAn;
    private com.dan.brocken.scene.Regie.Book kinoBuch;
    /** true, wenn das Kino beginnt (Bedienfeld weg, Vollbild), false, wenn es zu Ende ist; im Bildlauf gerufen. */
    public volatile Consumer<Boolean> kinoFenster = an -> { };
    private volatile String captureCaption, sheetName, regieMsg = "";
    private final java.util.List<BufferedImage> album = new java.util.ArrayList<>();
    private final java.util.List<String> albumCaps = new java.util.ArrayList<>();
    private double albumFps, albumGpu;
    private int albumSamples;
    /** Das zuletzt geschriebene Blatt (für Tempo und die Prüfung); null: noch keins. */
    public volatile File lastSheet;
    /** Ohne Blatt (Prüfung). */
    public volatile boolean ohneBlatt;

    /** Ein Regiebuch von vorn (das Album beginnt leer). */
    public void regieStart(com.dan.brocken.scene.Regie.Book b) {
        album.clear();
        albumCaps.clear();
        albumFps = albumGpu = 0;
        albumSamples = 0;
        regieMsg = "";
        frozen = false;
        regie.start(b, regieCtx);
    }

    public void regieStop() {
        regie.stop();
        if (kino.laeuft()) kino.abbrechen();
    }

    /** Ein Regiebuch im Kino (Vorspann mit Titel und Beschreibung, am Ende der Abspann). */
    public void kinoStart(com.dan.brocken.scene.Regie.Book b) {
        frozen = false;
        regie.beiText = kino::untertitel;
        kinoBuch = b;
        kino.beginnen("Brocken · Dreihundert Tage Nebel", b.name(), b.about());
        regieStart(b);
    }

    /** Das Kino verlassen (Esc); das Regiebuch läuft im Fenster weiter. */
    public void kinoVerlassen() {
        frozen = false;
        kino.abbrechen();
    }

    /** Die Zeilen des Abspanns (erste Zeile groß). */
    java.util.List<String> abspann(com.dan.brocken.scene.Regie.Book b) {
        java.util.List<String> z = new java.util.ArrayList<>();
        z.add(b != null ? b.name() : "Brocken");
        z.add("Ort: der Brocken im Harz, 1 141 m");
        z.add("Szene: " + clock.label());
        z.add("Gelände: Copernicus GLO-30, BKG DGM200, LVermGeo Sachsen-Anhalt DGM1 und ALKIS");
        z.add("Wetter: Klima der Wetterwarte Brocken (DWD 1991–2020), gestaltet");
        z.add("Brockenbahn: Fahrplan Sommer 2026 nach der HSB");
        z.add("Himmel, Nebel, Wind, Raueis, Wald, Bahn, Menschen und Klang: com.dan");
        if (!album.isEmpty()) z.add("Fotos: " + album.size() + " auf dem Blatt in docs/status");
        z.add("Brocken · Dreihundert Tage Nebel · " + VERSION);
        return z;
    }

    /** Ein Schnitt (Ansicht, Fahrt, Mitfahrt). Im Kino wartet er auf das Merken des Bilds für die Überblendung. */
    private void schnitt(Runnable r) {
        if (kino.schnitt()) kinoSchnitte.add(r);
        else if (!kinoSchnitte.isEmpty()) kinoSchnitte.add(r);   // hinter einem wartenden Schnitt in Reihe
        else { r.run(); kinoYaw = 0; }
    }

    /** Die wartenden Schnitte ausführen (nach dem Merken oder ohne Kino). */
    private void schnitteJetzt() {
        if (kinoSchnitte.isEmpty()) return;
        java.util.List<Runnable> l = new java.util.ArrayList<>(kinoSchnitte);
        kinoSchnitte.clear();
        for (Runnable x : l) x.run();
        kinoYaw = 0;
    }

    /** Kino weiter, Abspann am Ende des Regiebuchs, Schwenk und Ton. */
    private void kinoSchritt(double dt) {
        com.dan.kino.Kino.Lage l = kino.lage();
        if ((l == com.dan.kino.Kino.Lage.VORSPANN || l == com.dan.kino.Kino.Lage.FILM) && !regie.running() && !regieWantsFrame())
            kino.beenden(abspann(kinoBuch));
        kino.schritt(l == com.dan.kino.Kino.Lage.VORSPANN && regie.waiting() != null ? 0 : dt);   // wartet das Regiebuch im Vorspann, bleibt die Titeltafel
        if (!kino.laeuft() && !kinoSchnitte.isEmpty() && !kino.schnittFaellig()) schnitteJetzt();
        // der Schwenk: nur bei stehender Kamera, als Änderung der Blickrichtung
        double s = kino.schwenk();
        if (camera.fahrt == null && camera.mitfahrt == null && kinoSchnitte.isEmpty()) {
            double d = s - kinoYaw;
            if (camera.mode == Camera.Mode.FREI) camera.fyaw += d; else camera.yaw += d;
        }
        kinoYaw = s;
        // der Ton folgt dem Schwarz
        klang.kinoTon = kino.sichtbar() ? kino.ton() : 1;
    }

    /** Ohne Bild (Prüfung): was der Bildlauf nach dem Zeichnen tut – Schnitte ausführen, Fotos und Blatt verwerfen. */
    public void bildlos() {
        if (kino.schnittFaellig()) { schnitteJetzt(); kino.geschnitten(); }
        if (captureCaption != null) { albumCaps.add(captureCaption); captureCaption = null; }
        sheetName = null;
    }

    /** Tempo: so viele Bilder vor dem Foto zeichnen, ohne es zu nehmen (Belichtung und Wald regeln sich ein). */
    public volatile int fotoVorlauf;

    /** Ob ein Foto des Regiebuchs auf das nächste Bild wartet. */
    public boolean fotoFaellig() { return captureCaption != null; }

    /** Für die Prüfung: wie viele Fotos das laufende Regiebuch schon gemacht hat (ohne Bild: gezählt). */
    public int albumSize() { return albumCaps.size(); }

    /** Ob ein Foto oder das Blatt auf das nächste Bild wartet (Tempo zeichnet dann sofort). */
    public boolean regieWantsFrame() { return captureCaption != null || sheetName != null || kino.schnittFaellig(); }

    /** „Regie: Über dem Nebelmeer · 7:52: die Sonne geht auf · 1:10 von 6:00 · 3 Fotos“ (HUD, Bedienfeld). */
    public String regieLine() {
        return "Regie: " + regie.describe() + (albumCaps.isEmpty() ? "" : " · " + albumCaps.size() + " Fotos") + (regieMsg.isEmpty() ? "" : " · " + regieMsg)
                + (kino.laeuft() ? " · Kino" : "");
    }

    /** Was die Regiebücher in der App tun. */
    private final class RegieCtx implements com.dan.brocken.scene.Regie.Ctx {
        @Override public void time(java.time.LocalDate date, double hour) { clock.set(date, hour); readapt(); }
        @Override public void lapse(double x) { clock.lapse = x; }
        @Override public void weather(String name, double stunde, com.dan.weather.Scenario kopf) {
            wiring.regie = true;
            wiring.resumeRegie();
            if (kopf != null) {
                wiring.start(kopf);
                wiring.feedWeather(0);
                readapt();
                if (Boolean.getBoolean("regiedebug")) System.out.println("Regie-Wetter: " + clock.label() + " · " + wiring.snapshot().phase() + " · Beginn " + wiring.startOf(kopf));
                return;
            }
            if (wetter(name, stunde) == null) regieMsg = "Wetter „" + name + "“ gibt es nicht";
        }
        @Override public void weatherFree(com.dan.weather.Weather.Phase p, double speed, double fromDeg) {
            wiring.regie = true;
            wiring.resumeRegie();
            wiring.startFree(p);
            if (!Double.isNaN(speed)) wiring.hand(com.dan.weather.Weather.Var.WIND, speed);
            if (!Double.isNaN(fromDeg)) wiring.hand(com.dan.weather.Weather.Var.DIRECTION, fromDeg);
            readapt();
        }
        @Override public void view(String name) {
            schnitt(() -> {
                frei();
                if (!placeNamed(name)) regieMsg = "Ansicht „" + name + "“ gibt es nicht";
                readapt();
            });
        }
        @Override public void place(double x, double z, double heading, double pitch, double above) {
            schnitt(() -> {
                frei();
                camera.setMode(Camera.Mode.FREI);
                camera.place(x, groundHeight(x, z) + above, z, heading, pitch);
                camera.snap();
                readapt();
            });
        }
        @Override public void fahrt(String name) {
            schnitt(() -> {
                frei();
                com.dan.brocken.camera.Fahrt f = fahrtNamed(name);
                if (f == null) regieMsg = "Fahrt „" + name + "“ gibt es nicht";
                else camera.startFahrt(f);
            });
        }
        @Override public void mitfahren(double look) {
            schnitt(() -> {
                frei();
                if (!Renderer.this.mitfahren()) regieMsg = "kein Zug zum Mitfahren";
                else camera.mitBlick(look);
            });
        }
        @Override public void blick(double look) { camera.mitBlick(look); }
        @Override public void sonne(boolean gegenpunkt, double extra) {
            schnitt(() -> {
                blickZurSonne(gegenpunkt);
                double[] e = camera.eye.clone();
                camera.place(e[0], e[1], e[2], camera.fyaw, camera.fpitch + extra);
                camera.snap();
            });
        }
        @Override public void treiben(double above) { schnitt(() -> { frei(); treibenStart(above); }); }
        @Override public void meer(double basis, double ueber) {
            schnitt(() -> meerAmAuge(basis < 0 ? Math.max(300, camera.eye[1] + basis) : basis, ueber));
        }
        @Override public void meerRegie() {
            wiring.meerBasisHand = wiring.meerTopHand = Double.NaN;
            nebelJetzt();
        }
        @Override public void raueis(double cm) {
            wiring.raueis.hand = Double.isNaN(cm) ? Double.NaN : cm / 100;
            raueisJetzt();
        }
        @Override public void walpurgis(boolean an) { klang.walpurgisHand = an; }
        @Override public boolean ready(com.dan.brocken.scene.Regie.Warte w) {
            return switch (w.was()) {
                case "uhr" -> clock.epochMillis() / 3_600_000.0 >= w.hours();
                case "abfahrt" -> zugbetrieb != null && zugbetrieb.zuege.stream().anyMatch(t -> t.richtung > 0 && t.faehrt && !t.haelt() && t.v > 0.5
                        && strecke != null && t.s > strecke.sSchierke + 5);
                case "ankunft" -> zugbetrieb != null && (mitZug != null ? !mitZug.faehrt && mitZug.richtung > 0
                        : zugbetrieb.zuege.stream().anyMatch(t -> t.richtung > 0 && !t.faehrt));
                case "meer" -> nebelMeer;
                default -> true;
            };
        }
        @Override public void capture(String caption) { captureCaption = caption; }
        @Override public void sheet(String name) { sheetName = name; }
        @Override public void say(String text) { hudMeldung(text); kino.untertitel(text); }
    }

    /** Alles, was die Kamera führt, anhalten (Fahrt, Mitfahrt, Treiben). */
    private void frei() {
        camera.stopFahrt();
        camera.stopMitfahrt();
        treib = null;
        mitZug = null;
    }

    /**
     * Phase 16: eine Ansicht nach Namen: die Tasten 1 bis 9 (Ansichten), dazu „Bahnsteig“ und „Von oben“ am Gipfel
     * (Phase 14) und „Rabe“ (unter dem Kolkraben, der am höchsten kreist).
     */
    public boolean placeNamed(String name) {
        String k = schlicht(name);
        if (k.equals("bahnsteig")) return kameraGipfel("bahnsteig");
        if (k.equals("vonoben") || k.equals("gipfel")) return kameraGipfel("gipfel");
        if (k.startsWith("rabe")) return kameraZumRaben(45);
        Ansichten.Ansicht a = Ansichten.byName(name);
        if (a != null) { camera.setMode(Camera.Mode.FREI); return placeView(a); }
        return false;
    }

    /**
     * Phase 16: eine Fahrt nach Namen. „Spirale“: in 35 s neben der Bahn um die Kuppe, 90 m außen und 45 m über dem
     * Gleis, vom Goetheweg bis vor den Bahnhof, der Blick schräg zum Gipfel. „Zum Gipfel“: aus 600 m Höhe im Südosten
     * hinab zum Gipfelstein.
     */
    public com.dan.brocken.camera.Fahrt fahrtNamed(String name) {
        String k = schlicht(name);
        if (k.startsWith("spirale") && strecke != null) {
            com.dan.rail.Track g = strecke.gleis;
            double s0 = strecke.sGoetheweg > 0 ? strecke.sGoetheweg : strecke.sBrocken - 3500, s1 = strecke.sBrocken - 250;
            int n = Math.max(8, (int) ((s1 - s0) / 80));
            double[][] pts = new double[n + 1][];
            double turn = 0, lastH = Double.NaN;
            for (int i = 0; i <= n; i++) {
                com.dan.rail.Track.Pose p = g.at(s0 + (s1 - s0) * i / n);
                double dx = p.x() - Site.GIPFELSTEIN.x(), dz = p.z() - Site.GIPFELSTEIN.z(), l = Math.max(1, Math.hypot(dx, dz));
                double x = p.x() + dx / l * 90, z = p.z() + dz / l * 90;
                pts[i] = new double[]{x, Math.max(p.y(), groundHeight(x, z)) + 45, z};
                if (!Double.isNaN(lastH)) turn += ((p.heading() - lastH + 540) % 360) - 180;
                lastH = p.heading();
            }
            // dreht die Bahn links herum (gegen den Uhrzeiger von oben), liegt der Gipfel links
            return new com.dan.brocken.camera.Fahrt("Spirale", pts, 35, turn < 0 ? -35 : 35, 10);
        }
        if (k.startsWith("zumgipfel")) {
            double gx = Site.GIPFELSTEIN.x(), gz = Site.GIPFELSTEIN.z();
            double[][] pts = new double[12][];
            for (int i = 0; i < 12; i++) {
                double u = i / 11.0, d = 1400 * (1 - u) + 40, a = Math.toRadians(135);
                double x = gx + Math.sin(a) * d, z = gz - Math.cos(a) * d;
                pts[i] = new double[]{x, groundHeight(x, z) + 600 * (1 - u) * (1 - u) + 8, z};
            }
            return new com.dan.brocken.camera.Fahrt("Zum Gipfel", pts, 30, 0, 8);
        }
        return null;
    }

    // ---------------------------------------------------------- mit dem Wind treiben (der Rauchfaden, Phase 16)

    private volatile double[] treib;
    private double treibHoehe = 40, treibKurs;

    /** Das Auge treibt mit dem Wind des Felds, so hoch über Grund wie above (m), wie ein Rauchfaden. */
    public void treibenStart(double above) {
        double[] e = camera.eye;
        treibHoehe = Math.max(3, above);
        treib = new double[]{e[0], Math.max(e[1], groundHeight(e[0], e[2]) + treibHoehe), e[2]};
        double[] v = wiring.windAt(e[0], e[1], e[2]);
        treibKurs = Math.toDegrees(Math.atan2(v[0], -v[2]));
        double[] t = treib;
        camera.startMitfahrt(() -> { double[] q = treib != null ? treib : t; return new double[]{q[0], q[1], q[2], treibKurs, 0, 0}; }, 0);
    }

    /** Ob das Auge gerade mit dem Wind treibt. */
    public boolean treibt() { return treib != null && camera.mitfahrt != null; }

    private void treibenStep(double dt) {
        double[] t = treib;
        if (t == null) return;
        if (camera.mitfahrt == null) { treib = null; return; }
        double[] v = wiring.windAt(t[0], t[1], t[2]);
        double sp = Math.hypot(v[0], v[2]);
        double h = Math.max(0, Math.min(0.2, dt));
        double gx = t[0] + v[0] * h, gz = t[2] + v[2] * h;
        double want = groundHeight(gx, gz) + treibHoehe;
        double gy = t[1] + v[1] * h + (want - t[1]) * Math.min(1, h * 0.6);
        treib = new double[]{gx, Math.max(gy, groundHeight(gx, gz) + 2), gz};
        if (sp > 0.3) {
            double k = Math.toDegrees(Math.atan2(v[0], -v[2]));
            treibKurs += (((k - treibKurs) + 540) % 360 - 180) * Math.min(1, h * 0.8);
        }
    }

    /** Die Fotos des Regiebuchs als ein Blatt nach docs/status (zwei Spalten, Unterschrift in zwei Zeilen; aus Glacier). */
    private void writeSheet(String name) {
        if (album.isEmpty() || ohneBlatt) return;
        try {
            int n = album.size(), cols = 2, rows = (n + 1) / 2, cw = 960, ch = (int) Math.round(album.get(0).getHeight() * cw / (double) album.get(0).getWidth()), cap = 50, head = 44;
            BufferedImage out = new BufferedImage(cols * cw, head + rows * (ch + cap), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(15, 19, 21));
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.setColor(new Color(232, 234, 233));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            g.drawString(String.format(Locale.GERMAN, "Brocken · Regiebuch „%s“ · %s · Ø %.0f Bilder/s · Grafikkarte Ø %.2f ms · %s", name, quality.label,
                    albumFps / Math.max(1, albumSamples), albumGpu / Math.max(1, albumSamples), glInfo.replaceAll(" ·.*", "")), 14, 29);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
            for (int i = 0; i < n; i++) {
                int cx = (i % cols) * cw, cy = head + (i / cols) * (ch + cap);
                g.drawImage(album.get(i), cx, cy, cw, ch, null);
                g.setColor(new Color(159, 192, 206));
                String[] ln = albumCaps.get(i).split("\n");
                for (int k = 0; k < ln.length; k++) g.drawString(ln[k], cx + 10, cy + ch + 21 + 20 * k);
            }
            g.dispose();
            File dir = photoDir != null ? photoDir : new File("docs/status");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
            String fn = "Regiebuch_" + schlicht(name).replaceAll("[^a-z0-9]", "") + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".png";
            File f = new File(dir, fn);
            javax.imageio.ImageIO.write(out, "png", f);
            photoMsg = "Regiebuch gespeichert: " + f.getPath();
            lastSheet = f;
            System.out.println(photoMsg);
        } catch (Exception e) {
            photoMsg = "Blatt des Regiebuchs fehlgeschlagen: " + e.getMessage();
            System.err.println(photoMsg);
        }
        photoMsgUntil = System.currentTimeMillis() + 6000;
    }

    /** Schnitt mit gemerktem Bild, dann Balken, Schwarz, Untertitel; Fenster auf und zu. */
    private void kinoBild(GL4 gl, long now) {
        if (kinoGl != null && kinoGl.bereit()) {
            try {
                if (kino.schnittFaellig()) {
                    kinoGl.merken(gl, defaultFbo, surfW, surfH);
                    schnitteJetzt();
                    kino.geschnitten();
                }
                kinoGl.zeichnen(gl, defaultFbo, surfW, surfH, now / 1e9);
            } catch (RuntimeException e) {
                System.err.println("Brocken: Kino: " + e);
                kinoGl = null;
            }
        } else if (kino.schnittFaellig()) { schnitteJetzt(); kino.geschnitten(); }
        boolean an = kino.laeuft();
        if (an != kinoFensterAn) {
            kinoFensterAn = an;
            try { kinoFenster.accept(an); } catch (RuntimeException e) { System.err.println("Brocken: Kinofenster: " + e); }
        }
        if (kino.endeMelden()) hudMeldung("Kino zu Ende" + (lastSheet != null ? " · Blatt: " + lastSheet.getName() : ""));
    }

    // ------------------------------------------------------------------ Bilder

    private void takePhoto(GL4 gl, int w, int h) {
        try {
            BufferedImage img = readDefault(gl, w, h);
            File dir = photoDir != null ? photoDir : new File("docs/status");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
            String name = photoName != null ? photoName : "Brocken_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            photoName = null;
            File f = new File(dir, name + ".png");
            javax.imageio.ImageIO.write(img, "png", f);
            photoMsg = "Foto gespeichert: " + f.getPath();
            System.out.println(photoMsg);
        } catch (Exception e) {
            photoMsg = "Foto fehlgeschlagen: " + e.getMessage();
            System.err.println(photoMsg);
        }
        photoMsgUntil = System.currentTimeMillis() + 5000;
    }

    /** Liest das fertige Fensterbild (nach Nachbearbeitung und HUD). */
    BufferedImage readDefault(GL4 gl, int w, int h) {
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, defaultFbo);
        gl.glReadBuffer(defaultFbo == 0 ? GL.GL_BACK : GL.GL_COLOR_ATTACHMENT0);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            int o = (h - 1 - y) * w * 4;
            for (int x = 0; x < w; x++) {
                int i = o + x * 4;
                row[x] = ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255);
            }
            img.setRGB(0, y, w, 1, row, 0, w);
        }
        return img;
    }

    /** Für Werkzeuge: das letzte Bild als Bild (im GL-Faden aufrufen). */
    public BufferedImage grab(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        defaultFbo = d.getContext().getDefaultDrawFramebuffer();
        return readDefault(gl, d.getSurfaceWidth(), d.getSurfaceHeight());
    }

    // ------------------------------------------------------------------ Ende

    private void fail(String msg) {
        error = msg;
        System.err.println(msg);
        errorListener.accept(msg);
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        physik.stop();
        GL4 gl = d.getGL().getGL4();
        for (Shader s : all) s.dispose(gl);
        hud.dispose(gl);
        lod.dispose(gl);
        windGl.dispose(gl);
        windrechner.stop();
        if (fadenVao != 0) { gl.glDeleteVertexArrays(1, new int[]{fadenVao}, 0); gl.glDeleteBuffers(1, new int[]{fadenVbo}, 0); }
        wetter.dispose(gl);
        leben.dispose(gl);
        fels.dispose(gl);
        wald.dispose(gl);
        Gpu.deleteTextures(gl, waldTex);
        Gpu.deleteTextures(gl, driftTex);
        Gpu.deleteTextures(gl, trasseTex);
        Gpu.deleteTextures(gl, trasseMaske);
        if (gleisOk) gleisGl.dispose(gl);
        for (com.dan.rail.gl.RailGl a : ausweichGl) a.dispose(gl);
        if (zugOk) zugGl.dispose(gl);
        gipfelGl.dispose(gl);
        if (figurOk) figurGl.dispose(gl);
        Gpu.deleteTextures(gl, bodenTex);
        nebelGl.dispose(gl);
        tropfenGl.dispose(gl);
        schatten.dispose(gl);
        belichtung.dispose(gl);
        Gpu.deleteFbos(gl, hdrFbo, hdrColorFbo);
        Gpu.deleteTextures(gl, hdrTex, depthTex);
        if (emptyVao != 0) gl.glDeleteVertexArrays(1, new int[]{emptyVao}, 0);
        for (int[] s : stamp) if (s[0] != 0) gl.glDeleteQueries(MARKS, s, 0);
        ready = false;
    }
}
