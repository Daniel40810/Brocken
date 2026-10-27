package com.dan.brocken.gl;

import com.dan.cloud.Clouds;
import com.dan.cloud.gl.CloudGl;
import com.dan.sky.Sky;
import com.dan.sky.gl.SkyGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.Locale;

/**
 * Phase 3: Himmel und Wolken aus {@code Renderer.jar} (F4 A, aus Geiranger): {@code com.dan.sky} (Sonne, Mond, Sterne,
 * Streuung über 51,8° N, Augenhöhe bis über den Gipfel) und {@code com.dan.cloud} (die untere Schicht mit der Basis aus
 * der Schichtung der Regie, darüber der hohe Schleier, ihr Schatten). Seit Brocken Phase 3 liegen die Wolken über dem
 * Gelände (Tiefe der Szene): das Nebelmeer reicht an die Hänge, die Kappe sitzt auf dem Berg. Jedes Package startet
 * für sich; was nicht startet, fehlt, der Rest läuft (ohne Himmel bleibt der Verlauf aus Phase 1).
 */
public final class Wetter {
    public final Sky sky = new Sky();
    final SkyGl skyGl = new SkyGl();
    public final Clouds clouds = new Clouds();
    final CloudGl cloudGl = new CloudGl();
    boolean skyOk, cloudsOk;
    String skyCheck = "", cloudCheck = "";
    /** Das Rundumbild des Himmels (mit Wolken), vorbelichtet: Himmel für Gelände und Dunst. */
    int panoTex, panoFbo;
    public static final int PANO_W = 512, PANO_H = 256;

    /** Alles starten, jedes für sich; Meldungen an err. */
    void init(GL4 gl, java.util.function.Consumer<String> err) {
        // Phase 4: mit Mip-Stufen; der Dunst nimmt eine grobe Stufe (sonst zeichneten die Wolken an der Kimm senkrechte Streifen ins Gelände)
        panoTex = Gpu.texture(gl, PANO_W, PANO_H, GL.GL_RGBA16F, true);
        gl.glBindTexture(GL.GL_TEXTURE_2D, panoTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        panoFbo = Gpu.fbo(gl, panoTex, 0);
        try {
            skyGl.init(gl);
            skyOk = true;
            System.out.println("Brocken: Himmel " + skyGl.info());
        } catch (RuntimeException e) {
            fail(err, "Der Himmel (com.dan.sky) lässt sich nicht starten, es bleibt der Verlauf aus Phase 1", e);
        }
        if (skyOk) {
            try {
                cloudGl.init(gl);
                cloudsOk = true;
                System.out.println("Brocken: Wolken " + cloudGl.info());
            } catch (RuntimeException e) {
                fail(err, "Die Wolken (com.dan.cloud) lassen sich nicht starten, es bleibt der Schleier des Himmels", e);
            }
        }
    }

    private static void fail(java.util.function.Consumer<String> err, String what, RuntimeException e) {
        System.err.println("Brocken: " + what + ": " + e.getMessage());
        err.accept(what + ".\n" + e.getMessage());
    }

    /** Der aktuelle Himmelszustand (nie null nach dem ersten Schritt). */
    public Sky.State state() {
        Sky.State st = sky.state();
        return st != null ? st : sky.update();
    }

    static boolean sunIsKey(Sky.State st) { return st.sunAlt > -4; }

    /** Richtung des Hauptlichts: die Sonne, nachts der Mond (für Schatten der Kuppe und der Wolken). */
    static double[] keyDir(Sky.State st) { return sunIsKey(st) ? st.sunDirTrue : st.moonDirTrue; }

    /** Licht für die Wolken aus dem Himmelszustand, vorbelichtet; von unten Fichtenwald und Moor (dunkel). */
    CloudGl.Light cloudLight(Sky.State st, double pre) {
        CloudGl.Light l = new CloudGl.Light();
        boolean sunKey = sunIsKey(st);
        l.dir = keyDir(st);
        for (int i = 0; i < 8; i++) l.colorAt[i] = scale(sunKey ? st.sunColorAt(CloudGl.LIGHT_HEIGHTS[i]) : st.moonColorAt(CloudGl.LIGHT_HEIGHTS[i]), pre);
        double[] e = st.skyIrradiance;
        l.ambTop = scale(e, pre / Math.PI);
        double s = Math.max(0, st.sunDir[1]), m = Math.max(0, st.moonDir[1]);
        l.ambBot = new double[3];
        for (int c = 0; c < 3; c++) l.ambBot[c] = (e[c] + st.sunColor[c] * s + st.moonColor[c] * m) * 0.08 / Math.PI * pre;
        l.haze = st.groundExtinction;
        l.flash = new double[]{0, 1, 0, 0};
        l.flashCount = 0;
        return l;
    }

    /**
     * Himmelslicht auf eine waagerechte Fläche mit den Wolken (Bestrahlung, Einheiten von com.dan.sky): der Himmel
     * zwischen den Wolken, dazu das Sonnenlicht, das die Seiten der Wolken zurückwerfen (bei halber Bedeckung am
     * meisten), unter einer geschlossenen Decke grau mit der Helligkeit der Art (wie {@code CloudType.overcastBrightness}
     * gegen den klaren Tag) [G]. Über der Decke (above) der klare Himmel; ohne gezeichnete Wolken der Himmel allein.
     */
    double[] ambient(Sky.State st, boolean cloudsShown, boolean above) {
        double[] e = st.skyIrradiance;
        if (!cloudsOk || !cloudsShown || above) return e.clone();
        double c = Math.max(0, Math.min(8, clouds.oktas)) / 8, sy = Math.max(0, st.sunDir[1]);
        double[] sunH = scale(st.sunColor, sy);
        double lumE = 0.2126 * (e[0] + sunH[0]) + 0.7152 * (e[1] + sunH[1]) + 0.0722 * (e[2] + sunH[2]);
        double over = Math.pow(c, 1.5), grey = lumE * clouds.type.overcastBrightness;
        double[] r = new double[3];
        for (int k = 0; k < 3; k++) r[k] = (1 - over) * (e[k] * (1 - 0.4 * c) + sunH[k] * 1.2 * c * (1 - c)) + over * grey;
        return r;
    }

    /** Dunstlänge für exp(−d/L) aus der Sichtweite (Koschmieder). */
    static double fogDistance(Sky.State st) { return st.visibilityKm * 1000 / 3.912; }

    /** Anteil des Stäbchensehens (nachts farblos und bläulich), aus der Anpassung des Himmels. */
    static double scotopic(Sky.State st) {
        double l = Math.log10(Math.max(1e-12, st.adaptation * Sky.CD_PER_UNIT));
        double t = Math.max(0, Math.min(1, (l - Math.log10(0.003)) / (Math.log10(3) - Math.log10(0.003))));
        return 1 - t * t * (3 - 2 * t);
    }

    static double[] scale(double[] v, double k) { return new double[]{v[0] * k, v[1] * k, v[2] * k}; }

    /** „Himmel … · Wolken …“ für das volle HUD (zwei Zeilen). */
    String[] describe(boolean cloudsShown, double cloudShade) {
        Sky.State st = skyOk ? state() : null;
        String a = st == null ? "!Himmel: com.dan.sky nicht gestartet, Verlauf aus Phase 1"
                : String.format(Locale.GERMAN, "Himmel %s · Sonne %.1f° · Mond %.0f %% · Sicht %.0f km%s", skyGl.info(), st.sunAlt, st.moonFraction * 100,
                st.visibilityKm, skyCheck.isEmpty() ? "" : " · " + skyCheck);
        String b = cloudsOk ? String.format(Locale.GERMAN, "Wolken %s · Sonne am Auge %.0f %%%s", cloudsShown ? clouds.describe() : "aus", cloudShade * 100,
                cloudCheck.isEmpty() ? "" : " · " + cloudCheck) : "!Wolken fehlen";
        return new String[]{a, b};
    }

    void dispose(GL4 gl) {
        if (skyOk) skyGl.dispose(gl);
        if (cloudsOk) cloudGl.dispose(gl);
        Gpu.deleteFbos(gl, panoFbo);
        Gpu.deleteTextures(gl, panoTex);
    }
}
