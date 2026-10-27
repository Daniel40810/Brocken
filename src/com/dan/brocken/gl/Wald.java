package com.dan.brocken.gl;

import com.dan.brocken.scene.Bodenkarte;
import com.dan.brocken.scene.Terrain;
import com.dan.brocken.scene.Waldkarte;
import com.dan.forest.ForestField;
import com.dan.forest.Species;
import com.dan.forest.gl.ForestGl;
import com.jogamp.opengl.GL4;

import java.util.List;
import java.util.Locale;

/**
 * Phase 9: der Wald aus {@code com.dan.forest} (Durchgang „Wald“): der Bergfichtenwald nach dem Borkenkäfer, wie er
 * 2026 dasteht [G nach der {@link Waldkarte}]. Je Platz (6,5 m in „Mittel“) höchstens ein Baum, die Art nach diesen
 * Anteilen am Ort:
 * <ul>
 * <li>die lebenden Fichten (ein Zehntel), dazu einzelne frisch abgestorbene mit roten Nadeln;</li>
 * <li>die toten: stehend grau, gebrochen, liegend – liegend mehr, wo der Ort offen zum Sturm liegt, und dann nach
 * Nordost gefallen (Sturm aus 245°, Streuung ± 35°);</li>
 * <li>die Verjüngung zwischen dem Totholz: junge Fichten, oben Ebereschen, unten Birken;</li>
 * <li>an der Baumgrenze Windflüchter (ungedreht, die Fahne nach Lee), am Moorrand Moorbirken, unter 700 m Buchen.</li>
 * </ul>
 */
public final class Wald {
    /** Die Arten in der Reihenfolge der Dichte. */
    public static final int FICHTE = 0, ROT = 1, GRAU = 2, BRUCH = 3, LIEGEND = 4, JUNG = 5, FLAGGE = 6, EBERESCHE = 7, BIRKE = 8, BUCHE = 9;
    public static final List<Species> ARTEN = List.of(Species.spruce(), Species.spruceRed(), Species.spruceDead(), Species.spruceSnag(),
            Species.spruceLog(), Species.spruceYoung(), Species.spruceFlag(), Species.rowan(), Species.downyBirch(), Species.beech());
    public static final String[] NAMEN = {"Fichte", "rot", "grau", "gebrochen", "liegend", "jung", "Windflüchter", "Eberesche", "Moorbirke", "Buche"};
    /** Varianten je Art. */
    public static final int VARIANTEN = 3;

    public final Waldkarte karte;
    public final ForestField feld;
    final ForestGl waldGl;
    public volatile boolean showWald = true;
    private final Bodenkarte boden;
    private final Terrain gelaende;
    private boolean ready;

    public Wald(Terrain t, Bodenkarte b, Waldkarte k) {
        this.karte = k;
        this.boden = b;
        this.gelaende = t;
        waldGl = new ForestGl(ARTEN, VARIANTEN, 1141);
        feld = new ForestField(ARTEN.size(), VARIANTEN, this::dichte, (x, z) -> (float) t.height(x, z), 1141);
        feld.setHeights(waldGl.heights());
        feld.yaw[LIEGEND] = ForestField.yawToward(Waldkarte.STURM_AUS - 180);
        feld.yawSpread[LIEGEND] = Math.toRadians(35);
        feld.yaw[FLAGGE] = 0;
        feld.yawSpread[FLAGGE] = Math.toRadians(10);
    }

    /** Dichte der Art s an (x, z), so dass die Summe über alle Arten der Bestockung entspricht. */
    public double dichte(double x, double z, int s) {
        // ForestField fragt je Platz alle Arten nacheinander ab: die Anteile einmal je Platz rechnen
        double[] c0 = cache.get();
        if (c0[0] != x || c0[1] != z) {
            c0[0] = x;
            c0[1] = z;
            double[] w = anteile(x, z);
            System.arraycopy(w, 0, c0, 2, w.length);
        }
        return c0[2 + s];
    }

    private final ThreadLocal<double[]> cache = ThreadLocal.withInitial(() -> {
        double[] a = new double[2 + ARTEN.size()];
        a[0] = Double.NaN;
        return a;
    });

    /** Die Dichten aller Arten an (x, z). */
    double[] anteile(double x, double z) {
        double c = karte.cover(x, z), fl = karte.flag(x, z);
        // Phase 12: am Gleis keine Bäume (Lichtraum und Schutzstreifen, 6 m von der Achse [G])
        java.util.function.DoubleBinaryOperator gl = Leben.gleis;
        if (gl != null && gl.applyAsDouble(x, z) < 6) return new double[ARTEN.size()];
        double m = boden.moor(x, z);
        double[] w = new double[ARTEN.size()];
        if (c <= 0.002 && fl <= 0.002 && m <= 0.002) return w;
        double d = karte.dead(x, z), be = karte.beech(x, z), fi = 1 - be, ex = karte.exposure(x, z);
        double oben = smooth(750, 950, gelaende.height(x, z));   // oben Ebereschen, unten Birken
        w[FICHTE] = fi * (1 - d) * 0.75;
        w[JUNG] = fi * (0.25 * (1 - d) + 0.26 * d);
        w[ROT] = fi * d * 0.025;
        w[GRAU] = fi * d * 0.42;
        w[BRUCH] = fi * d * 0.13;
        w[LIEGEND] = fi * d * 0.12 * (0.5 + 1.3 * ex);
        w[EBERESCHE] = fi * d * 0.08 * oben;
        w[BIRKE] = fi * d * 0.06 * (1 - oben);
        w[BUCHE] = be;
        double sum = 0;
        for (double v : w) sum += v;
        double k = sum > 0 ? c / sum : 0;
        for (int i = 0; i < w.length; i++) w[i] *= k;
        w[FLAGGE] = fl * 0.26;
        w[BIRKE] += 4 * m * (1 - m) * 0.12;
        // Wald, Windflüchter und Birken am Moorrand zusammen: höchstens ein Baum je Platz
        double all = 0;
        for (double v : w) all += v;
        if (all > 1) for (int i = 0; i < w.length; i++) w[i] /= all;
        return w;
    }

    void init(GL4 gl) {
        waldGl.init(gl);
        ready = true;
    }

    boolean ready() { return ready && waldGl.ready(); }

    /** Sind die Netze gebacken und hochgeladen (für Standbilder)? */
    public boolean bereit() { return ready(); }

    /** Qualität: Reichweite, Stufen und Abstand der Plätze. */
    void quality(ForestField.Quality q) {
        feld.apply(q);
        // Brocken: viele Bäume je Fläche, viele davon dürr und fein verzweigt – die feinen Stufen kürzer als im Paket
        // (Budget Wald 2,5 ms auf der Radeon) [G]
        feld.radius = q.radius * 0.85;
        feld.lod[0] = 6;
        feld.lod[1] = 18;
        feld.lod[2] = q.lod2 * 0.55;
        feld.lod[3] = q.lod3 * 0.7;
    }

    /** Je Bild: Jahreszeit, Wind am Auge, Bäume wählen und hochladen, zeichnen. */
    void draw(GL4 gl, float[] vp, double[] eye, boolean reversed, int doy, float snow, double[] windTo, double windSpeed, double gust,
              double[] sunDir, double[] sunE, double[] moonDir, double[] moonE, double[] skyPi, double fogDist, double sunAtEye,
              int cloudTex, double[] cloudRect, int skyTex, double wet, double t) {
        if (!ready || !showWald) return;
        waldGl.setSeason(doy, snow);
        waldGl.setWind(windTo[0], windTo[1], windSpeed, gust);
        feld.update(eye, vp, 12);
        waldGl.update(gl, feld);
        ForestGl.View v = new ForestGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        v.reversedZ = reversed;
        ForestGl.Light l = new ForestGl.Light();
        l.sunDir = sunDir.clone();
        l.sunColor = Leben.scale(sunE, sunAtEye / Math.PI);
        l.moonDir = moonDir.clone();
        l.moonColor = Leben.scale(moonE, 1 / Math.PI);
        l.skyColor = skyPi.clone();
        l.fogColor = Leben.scale(skyPi, 2.5);
        l.fogDistance = fogDist;
        l.wetness = wet;
        l.shadowTexture = cloudTex;
        l.shadowRect = cloudRect.clone();
        l.skyTexture = skyTex;
        waldGl.draw(gl, v, l, t);
    }

    /** Für Standbilder: alle Kacheln um das Auge pflanzen. */
    public void pflanzen(double[] eye) { feld.completeNow(eye); }

    /** „Wald: 5 600 Bäume, 3,1 Mio. Dreiecke · hier 70 % Wald, 88 % tot“ */
    public String zeile(double x, double z) {
        int[] n = new int[ARTEN.size()];
        for (int b = 0; b < feld.buckets(); b++) n[feld.speciesOf(b)] += feld.count[b];
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < n.length; s++) if (n[s] > 0) sb.append(sb.length() > 0 ? ", " : "").append(NAMEN[s]).append(' ').append(String.format(Locale.GERMAN, "%,d", n[s]));
        return String.format(Locale.GERMAN, "%s · %s · hier Wald %.0f %%, tot %.0f %%, Windflüchter %.0f %%, offen zum Sturm %.0f %%", waldGl.info(),
                sb.length() == 0 ? "keine Bäume im Blick" : sb, 100 * karte.cover(x, z), 100 * karte.dead(x, z), 100 * karte.flag(x, z), 100 * karte.exposure(x, z));
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    void dispose(GL4 gl) {
        waldGl.dispose(gl);
        ready = false;
    }
}
