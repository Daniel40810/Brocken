package com.dan.brocken.gl;

import com.dan.brocken.scene.Bodenkarte;
import com.dan.brocken.scene.Terrain;
import com.dan.cliff.Blockfield;
import com.dan.cliff.Cliff;
import com.dan.cliff.Tor;
import com.dan.cliff.gl.CliffGl;
import com.jogamp.opengl.GL4;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 8: Fels und Blöcke aus {@code com.dan.cliff} (Durchgang „Fels“):
 * <ul>
 * <li><b>Granit</b> ({@link Cliff#granit()}): das Material des Felsens im Gelände ({@code terrain.frag} nimmt
 * {@code cliff.glsl} und mischt es nach dem Anteil Fels der {@link Bodenkarte} und der Neigung), Wollsäcke als Feinform.</li>
 * <li><b>Felsburgen</b>: Teufelskanzel und Hexenaltar ({@link Bodenkarte#FELSBURGEN}) als Netze aus gestapelten,
 * gerundeten Blöcken.</li>
 * <li><b>Blockmeer</b>: Blöcke um das Auge, wo die Bodenkarte Blockfeld sagt, halb im Boden, instanziert.</li>
 * </ul>
 */
public final class Fels {
    public final Cliff granit = Cliff.granit();
    public final Blockfield blockmeer = new Blockfield();
    public final List<Tor> burgen = new ArrayList<>();
    final CliffGl felsGl = new CliffGl();
    public volatile boolean showFels = true;
    /** Phase 10: {base, full, dicke, art, woher} des Eises (vom Renderer je Bild). */
    volatile double[] raueis = {1e5, 1e5 + 1, 0, 0, 270};
    private boolean ready;
    private int blocks;

    /** Einheit der (leeren) Wandkarte im Gelände-Shader; das Rauschen liegt immer auf {@link CliffGl#NOISE_UNIT}. */
    static final int MAP_UNIT = 5;

    public Fels(Terrain t, Bodenkarte b) {
        granit.seed = 1141;
        granit.topMoss = 0.45;   // [G] Blöcke im Fichtenwald bemoost, auf der Kuppe weniger (ein Wert für alle)
        blockmeer.seed = 1141;
        blockmeer.radius = 220;     // seit Phase 9 (Radeon Phase 8: 3 ms im Blockmeer bei 350 m); weiter trägt das Gelände
        blockmeer.minPixels = 4;
        blockmeer.density = (x, z) -> {
            java.util.function.DoubleBinaryOperator gl = Leben.gleis;
            return gl != null && gl.applyAsDouble(x, z) < 5 ? 0 : b.block(x, z);
        };
        blockmeer.ground = t::height;
        for (Bodenkarte.Felsburg f : Bodenkarte.FELSBURGEN) burgen.add(new Tor(f.x(), t.height(f.x(), f.z()), f.z(), f.hoehe(), f.breite(), f.richtung(), f.seed()));
    }

    void init(GL4 gl) {
        felsGl.init(gl);
        felsGl.setTors(burgen);
        ready = true;
    }

    boolean ready() { return ready; }

    /** Granit an den Gelände-Shader binden (Fels erzwungen; der Shader mischt nach dem Anteil Fels). */
    void bindTerrain(GL4 gl, int program) {
        if (!ready) return;
        felsGl.bind(gl, program, granit, MAP_UNIT);
        gl.glUniform1f(gl.glGetUniformLocation(program, "uCliffForce"), 1f);
    }

    /** Felsburgen und Blockmeer zeichnen; Licht vorbelichtet wie das Gelände (Sonne als Bestrahlung, Himmel als Bestrahlung/π). */
    void draw(GL4 gl, float[] vp, double[] eye, boolean reversed, double pixelAngle, double[] sunDir, double[] sunE, double[] skyPi, double shade,
              double fogDist, double rainWet, double snow) {
        if (!ready || !showFels) { blocks = 0; return; }
        CliffGl.View v = new CliffGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        v.reversedZ = reversed;
        v.pixelAngle = pixelAngle;
        CliffGl.Light l = new CliffGl.Light();
        l.sunDir = sunDir.clone();
        l.sunColor = sunE.clone();
        l.ambient = skyPi.clone();
        l.fogColor = Leben.scale(skyPi, 2.5);
        l.fogDistance = fogDist;
        l.shade = shade;
        l.rainWet = rainWet;
        l.snow = snow;
        l.display = 0;
        // Phase 10: Raueis auf Blöcken und Felsburgen (Band und Dicke aus der Wiring)
        l.rimeBase = raueis[0];
        l.rimeFull = raueis[1];
        l.rimeThickness = raueis[2];
        l.rimeKind = (int) raueis[3];
        l.rimeFromDeg = raueis[4];
        felsGl.drawTors(gl, granit, v, l);
        blocks = felsGl.drawBlocks(gl, granit, blockmeer, v, l);
    }

    /** „Granit · Felsburgen Teufelskanzel 4,5 m, Hexenaltar 3,0 m · Blockmeer 2 544 Blöcke um das Auge“ */
    public String zeile() {
        StringBuilder sb = new StringBuilder("Fels: Granit, Wollsäcke " + String.format(Locale.GERMAN, "%.1f m", granit.jointSpacing) + " · Felsburgen ");
        for (int i = 0; i < burgen.size(); i++)
            sb.append(i > 0 ? ", " : "").append(Bodenkarte.FELSBURGEN[i].name()).append(String.format(Locale.GERMAN, " %.1f m", burgen.get(i).top() - burgen.get(i).y));
        sb.append(String.format(Locale.GERMAN, " · Blockmeer %,d Blöcke um das Auge", blocks));
        return sb.toString();
    }

    public int blocks() { return blocks; }

    void dispose(GL4 gl) {
        felsGl.dispose(gl);
        ready = false;
    }
}
