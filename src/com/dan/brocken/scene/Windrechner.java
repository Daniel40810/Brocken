package com.dan.brocken.scene;

import com.dan.wind.WindField;
import com.dan.wind.WindSolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 4 (F7 A): das Windfeld im eigenen Faden „Windfeld“. Ändern sich Richtung, Schichtung oder Erwärmung, rechnet er
 * das massenkonsistente Feld über dem Hauptraster neu ({@code WindField.solve}), misst den Wind am Gipfel, tastet das
 * Feld für die 3D-Textur ab und zieht die Rauchfäden. Fertig ist ein unveränderliches {@link Ergebnis}; der Bildlauf
 * holt es ab, ohne zu warten (Grafikkarte im GL-Faden). Im Bildlauf wird nie gerechnet.
 */
public final class Windrechner {
    /** Ein fertiges Feld: Lösung ohne Faktor, Abtastung für die Textur, Wind am Gipfel, Rauchfäden, Zeit. */
    public record Ergebnis(int version, String key, WindSolution solution, WindField.Grid grid, float[] sample, double summitRaw, List<float[]> faeden,
                           double millis, String text) { }

    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Windfeld");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile Ergebnis last;
    private volatile String pendingKey, runningKey;
    private volatile String fehler;
    private int version;
    /** Rauchfäden: Zahl, Höhe über Grund am Start (m), Schritt (m), größte Länge (m). */
    public static final int FAEDEN = 24;
    public static final double FADEN_H = 30, FADEN_STEP = 30, FADEN_LEN = 16_000;

    /** Das Fenster des Felds (Szene): das Hauptraster. */
    public static double[] fenster() { return Site.HAUPTRASTER.scene(); }

    public Ergebnis last() { return last; }
    public boolean busy() { return busy.get(); }
    public String fehler() { return fehler; }

    /**
     * Neu rechnen, wenn key sich vom gerechneten unterscheidet (die Eingänge werden kopiert; der Bildlauf rechnet
     * weiter mit dem alten Feld). Läuft schon eine Rechnung, wird die neueste Anfrage danach gerechnet.
     */
    public void request(WindField f, String key) {
        Ergebnis e = last;
        if (e != null && key.equals(e.key()) || key.equals(runningKey) || key.equals(pendingKey)) return;
        pendingKey = key;
        WindField copy = f.copy();
        exec.submit(() -> {
            if (!key.equals(pendingKey)) return;   // überholt
            runningKey = key;
            pendingKey = null;
            busy.set(true);
            try {
                last = rechnen(copy, key, ++version);
                fehler = null;
            } catch (RuntimeException | OutOfMemoryError ex) {
                fehler = "Windfeld: " + ex;
                System.err.println("Brocken: " + fehler);
            } finally {
                busy.set(false);
                runningKey = null;
            }
        });
    }

    /** Sofort und im aufrufenden Faden rechnen (Werkzeuge, Prüfung). */
    public Ergebnis jetzt(WindField f, String key) {
        Ergebnis e = rechnen(f.copy(), key, ++version);
        last = e;
        return e;
    }

    static Ergebnis rechnen(WindField f, String key, int version) {
        long t0 = System.nanoTime();
        double[] r = fenster();
        WindSolution so = f.solve(r[0], r[1], r[2], r[3]);
        // seit Phase 5: an der Wetterwarte in Höhe des Windgebers (dort misst die Regie)
        double summit = so.speedAt(Site.WETTERWARTE[0], com.dan.brocken.wiring.WindBrocken.GEBER, Site.WETTERWARTE[1]);
        WindField.Grid g = f.grid(r[0], r[1], r[2], r[3]);
        float[] sample = f.sample(g, 0);
        List<float[]> faeden = faeden(f, so);
        double ms = (System.nanoTime() - t0) / 1e6;
        String text = String.format(Locale.GERMAN, "%s · mit Abtastung und Fäden %.1f s", so.describe(), ms / 1000);
        return new Ergebnis(version, key, so, g, sample, summit, faeden, ms, text);
    }

    /**
     * Rauchfäden: {@link #FAEDEN} Quellen auf einer Linie quer zum Wind, 5 km luvwärts vom Gipfel, in {@link #FADEN_H} m
     * über Grund; jeder Faden folgt dem Feld (auch hinauf und hinab, nie tiefer als 5 m über Grund) bis zum Rand.
     * Punkte als x, y, z, Weg (m) hintereinander.
     */
    public static List<float[]> faeden(WindField f, WindSolution so) {
        double[] d = f.toVector(), q = {-d[2], 0, d[0]};
        List<float[]> out = new ArrayList<>();
        for (int s = 0; s < FAEDEN; s++) {
            double off = (s - (FAEDEN - 1) / 2.0) * 300;
            double x = Site.GIPFELSTEIN.x() - d[0] * 5000 + q[0] * off, z = Site.GIPFELSTEIN.z() - d[2] * 5000 + q[2] * off;
            float[] p = faden(so, x, z, FADEN_H);
            if (p != null) out.add(p);
        }
        return out;
    }

    /**
     * Ein Rauchfaden ab (x, z) in h m über Grund: folgt dem Feld (auch hinauf und hinab, nie tiefer als 5 m über Grund)
     * bis zum Rand, höchstens {@link #FADEN_LEN}; null außerhalb oder in Ruhe.
     */
    public static float[] faden(WindSolution so, double x, double z, double h) {
        if (!so.contains(x, z)) return null;
        double y = so.groundAt(x, z) + h, path = 0;
        float[] pts = new float[(int) (FADEN_LEN / FADEN_STEP + 2) * 4];
        int n = 0;
        for (int i = 0; i * FADEN_STEP < FADEN_LEN; i++) {
            pts[n++] = (float) x;
            pts[n++] = (float) y;
            pts[n++] = (float) z;
            pts[n++] = (float) path;
            double[] v = so.at(x, y, z);
            double sp = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            if (sp < 0.05) break;
            x += v[0] / sp * FADEN_STEP;
            y += v[1] / sp * FADEN_STEP;
            z += v[2] / sp * FADEN_STEP;
            path += FADEN_STEP;
            if (!so.contains(x, z)) break;
            y = Math.max(y, so.groundAt(x, z) + 5);
        }
        return n >= 8 ? java.util.Arrays.copyOf(pts, n) : null;
    }

    public void stop() { exec.shutdownNow(); }
}
