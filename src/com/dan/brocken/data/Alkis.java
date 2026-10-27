package com.dan.brocken.data;

import com.dan.brocken.scene.Terrain;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Der ALKIS-Auszug (Phase 2): tatsächliche Nutzung und Gebäude aus {@code daten/alkis/*.bin}, geschrieben von
 * {@code tools/alkis_auszug.py} aus dem Geopaket des LVermGeo Sachsen-Anhalt (dl-de/by-2-0). Lage in UTM 32 N
 * (EPSG:25832). Dazu die Zuordnung der Nutzungsarten zu {@link Terrain#ART} und das Rastern von Flächen (gerade-ungerade
 * Regel, so dass Löcher in Flächen frei bleiben).
 */
public final class Alkis {
    /** Ein Objekt: Code (art_code bzw. funktion_code), Text (Art bzw. „Funktion|Name“), Ringe als {e0, n0, e1, n1, …}. */
    public record Objekt(int code, String text, double[][] ringe) {
        /** Umgebendes Rechteck {eMin, nMin, eMax, nMax}. */
        public double[] box() {
            double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (double[] r : ringe)
                for (int k = 0; k < r.length; k += 2) {
                    b[0] = Math.min(b[0], r[k]); b[1] = Math.min(b[1], r[k + 1]);
                    b[2] = Math.max(b[2], r[k]); b[3] = Math.max(b[3], r[k + 1]);
                }
            return b;
        }
        /** Name eines Gebäudes (Teil nach „|“), sonst leer. */
        public String name() { int i = text.indexOf('|'); return i < 0 ? "" : text.substring(i + 1); }
        /** Funktion eines Gebäudes bzw. Art einer Fläche. */
        public String art() { int i = text.indexOf('|'); return i < 0 ? text : text.substring(0, i); }
    }

    public static final String NUTZUNG = "nutzung.bin", GEBAEUDE = "gebaeude.bin";

    /** Ordner {@code daten/alkis}. */
    public static File ordner() {
        String d = System.getProperty("brocken.daten");
        return new File(d != null ? d : "daten", "alkis");
    }

    /** Liest eine Datei des Auszugs. */
    public static List<Objekt> lesen(File f) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(f.toPath())).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        b.get(magic);
        if (!new String(magic, StandardCharsets.US_ASCII).equals("BRAL")) throw new IOException(f + ": kein ALKIS-Auszug (BRAL)");
        int version = b.getInt(), n = b.getInt();
        if (version != 1) throw new IOException(f + ": Version " + version + " unbekannt");
        List<Objekt> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int code = b.getInt();
            byte[] t = new byte[b.getShort() & 0xffff];
            b.get(t);
            double[][] r = new double[b.getInt()][];
            for (int k = 0; k < r.length; k++) {
                r[k] = new double[b.getInt() * 2];
                for (int p = 0; p < r[k].length; p++) r[k][p] = b.getDouble();
            }
            l.add(new Objekt(code, new String(t, StandardCharsets.UTF_8), r));
        }
        return l;
    }

    /**
     * Nutzungsart der Szene zum ALKIS-Code (tatsächliche Nutzung, Objektartenkatalog): 43002 Wald, 43003 Gehölz,
     * 43004 Heide, 43005/43006 Moor und Sumpf, 43007 Unland, 43001 Landwirtschaft, 44xxx Gewässer, 42001 Straße,
     * 42006/42009 Weg und Platz, 42010 Bahn, 41007 Tagebau (Fels), 41010 Grünanlage und Sport (offen), sonst 41xxx
     * Siedlung (auch das Gipfelplateau: „Fläche besonderer funktionaler Prägung / Kultur“).
     */
    public static byte art(int code, String text) {
        switch (code) {
            case 43002: return Terrain.WALD;
            case 43003: return Terrain.GEHOELZ;
            case 43004: return Terrain.HEIDE;
            case 43005: case 43006: return Terrain.MOOR;
            case 43007: case 41007: return Terrain.FELS;
            case 43001: case 41010: return Terrain.OFFEN;
            case 42001: return Terrain.STRASSE;
            case 42006: case 42009: return Terrain.WEG;
            case 42010: return Terrain.BAHN;
            default:
        }
        if (code / 1000 == 44) return Terrain.WASSER;
        if (code / 1000 == 41) return Terrain.SIEDLUNG;
        if (code / 1000 == 42) return Terrain.WEG;
        if (code / 1000 == 43) return Terrain.OFFEN;
        return Terrain.KEINE;
    }

    /**
     * Rastert eine Fläche auf ein Gitter (n × m Zellen zu {@code cell} m, Nordwestecke bei Ost e0, Nord n0): jede Zelle,
     * deren Mitte in der Fläche liegt, bekommt {@code wert}. Zeilen von Nord nach Süd.
     */
    public static void rastern(Objekt o, byte[] ziel, int n, int m, double e0, double n0, double cell, byte wert) {
        double[] box = o.box();
        int j0 = Math.max(0, (int) Math.floor((n0 - box[3]) / cell)), j1 = Math.min(m - 1, (int) Math.ceil((n0 - box[1]) / cell));
        double[] xs = new double[64];
        for (int j = j0; j <= j1; j++) {
            double y = n0 - (j + 0.5) * cell;
            int c = 0;
            for (double[] r : o.ringe()) {
                int k = r.length / 2;
                for (int a = 0; a < k; a++) {
                    int b = (a + 1) % k;
                    double ya = r[2 * a + 1], yb = r[2 * b + 1];
                    if ((ya > y) == (yb > y)) continue;
                    double x = r[2 * a] + (y - ya) * (r[2 * b] - r[2 * a]) / (yb - ya);
                    if (c == xs.length) xs = java.util.Arrays.copyOf(xs, c * 2);
                    xs[c++] = x;
                }
            }
            java.util.Arrays.sort(xs, 0, c);
            for (int p = 0; p + 1 < c; p += 2) {
                int i0 = Math.max(0, (int) Math.ceil((xs[p] - e0) / cell - 0.5)), i1 = Math.min(n - 1, (int) Math.floor((xs[p + 1] - e0) / cell - 0.5));
                for (int i = i0; i <= i1; i++) ziel[j * n + i] = wert;
            }
        }
    }

    private Alkis() { }
}
