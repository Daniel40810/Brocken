import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Prüfung von {@code lib/Renderer.jar} (Ant-Target {@code check-packages}, aus Geiranger). Die Element-Packages liegen
 * nicht im Projekt Brocken: ihre Quelle ist das Projekt Renderer (F4 A), dort prüft {@code tools/PaketPruefung.java}
 * jedes Package einzeln (Paketvertrag Punkt 9). Hier wird nur geprüft, ob das Jar alles hat, was Brocken braucht: jedes
 * Package mit Klassen und README, Klassen für Java 21 (Version 65) oder älter, und bei den neuen Packages {@code wind},
 * {@code rime}, {@code rail}, {@code wetglass} und seit Phase 6 des Nebels über Gelände ({@code fog}) die Shader, seit Phase 7
 * dazu das Hochrechnen des Nebels und in {@code sky} die Tröpfchen (Mie, Glorie) und Halos, seit Phase 8 in {@code cliff}
 * Felsburgen, Blockmeer und {@code block.vert} und in {@code ground} das Biom, seit Phase 9 der Wald, seit Phase 10 das
 * Raueis (Kern, Baustein, Demo mit den Raten), seit Phase 11 das Jahr in Höhenstufen und die Verwehungen, seit Phase 12
 * das Gleis (Achse aus Flächen, Bahnkörper, Netz), seit Phase 13 die Züge (Fahrdynamik, Fahrzeuge, Netz der Lok und Wagen)
 * und die Lebensdauer je Rauchquelle, seit Phase 15 der Klang des Gebirges (Dampflok, Kanten, Vögel, Walpurgisnacht). Aufruf ohne Ant: {@code java tools/JarPruefung.java lib/Renderer.jar}.
 */
public final class JarPruefung {
    /** Was Brocken aus Renderer.jar braucht (wie {@code app.Bibliothek}: GEBRAUCHT und NEU). */
    static final String[] GEBRAUCHT = {"sky", "cloud", "weather", "rain", "fog", "lightning", "cliff", "ground", "forest", "season", "smoke", "reed", "flock",
            "soundscape", "road", "figure", "kino", "store", "weathering", "timber", "hearth", "wind", "rime", "rail", "wetglass"};
    static final String[][] SHADER = {{"wind", "gl/wind.glsl"}, {"rime", "Rime.class", "gl/rime.glsl", "gl/RimeGl.class", "demo/RimeDemo.class"}, {"rail", "Track.class", "Corridor.class", "Bed.class", "gl/RailGl.class", "gl/rail.vert", "gl/rail.frag",
                    "Train.class", "Train$Signal.class", "Fahrzeug.class", "gl/TrainGl.class", "gl/train.vert", "gl/train.frag"},
            {"wetglass", "gl/wetglass.glsl"}, {"fog", "gl/terrain_fog.frag", "gl/terrain_fog_common.glsl", "gl/terrain_fog_march.glsl", "gl/terrain_fog_up.frag"},
            {"sky", "Droplets.class", "Mie.class", "Halo.class", "gl/DropletGl.class"},
            {"cliff", "Tor.class", "Blockfield.class", "gl/block.vert", "gl/cliff.glsl", "gl/rock.frag"},
            {"ground", "Biome.class", "gl/ground.frag"},
            {"forest", "Species.class", "ForestField.class", "TreeGenerator.class", "gl/ForestGl.class", "gl/forest.vert", "gl/forest.frag"},
            {"season", "Heights.class", "Drift.class", "gl/SnowfallGl.class", "gl/snowfall.vert", "gl/snowfall.frag"},
            {"soundscape", "Steam.class", "Edge.class", "Highland.class", "Walpurgis.class", "demo/SoundscapeCheck.class"}};

    public static void main(String[] a) throws Exception {
        File jar = new File(a.length > 0 ? a[0] : "lib/Renderer.jar");
        if (!jar.isFile()) { System.err.println("check-packages: " + jar + " fehlt"); System.exit(1); }
        Map<String, int[]> p = new TreeMap<>();   // Klassen, README, höchste Version
        try (ZipFile z = new ZipFile(jar)) {
            for (ZipEntry e : java.util.Collections.list(z.entries())) {
                String n = e.getName();
                if (!n.startsWith("com/dan/") || e.isDirectory()) continue;
                String[] t = n.split("/");
                if (t.length < 4) continue;
                int[] v = p.computeIfAbsent(t[2], k -> new int[3]);
                if (n.endsWith(".class")) {
                    v[0]++;
                    try (InputStream in = z.getInputStream(e); DataInputStream d = new DataInputStream(in)) {
                        d.readInt();
                        d.readUnsignedShort();
                        v[2] = Math.max(v[2], d.readUnsignedShort());
                    }
                } else if (t.length == 4 && t[3].equals("README.md")) v[1] = 1;
            }
            int fehler = 0;
            System.out.println("check-packages: " + jar.getName() + " mit " + p.size() + " Packages (Quelle: Projekt Renderer)");
            System.out.println("  Package        Klassen  README  Java");
            for (String g : GEBRAUCHT) {
                int[] v = p.get(g);
                boolean ok = v != null && v[0] > 0 && v[1] == 1 && v[2] <= 65;
                if (!ok) fehler++;
                System.out.printf("  %-14s %-8s %-7s %-5s %s%n", g, v == null ? "–" : v[0], v == null || v[1] == 0 ? "fehlt" : "ok", v == null ? "–" : v[2] - 44, ok ? "" : "FEHLER");
            }
            for (String[] s : SHADER)
                for (int i = 1; i < s.length; i++)
                    if (z.getEntry("com/dan/" + s[0] + "/" + s[i]) == null) { fehler++; System.out.println("  FEHLER: com/dan/" + s[0] + "/" + s[i] + " fehlt"); }
            if (fehler > 0) { System.out.println("check-packages: " + fehler + " Fehler – Renderer neu bauen (Clean and Build) und dist/Renderer.jar nach lib/ kopieren"); System.exit(1); }
            System.out.println("check-packages: alle " + GEBRAUCHT.length + " gebrauchten Packages in " + jar.getName() + ", Shader von wind, rime, rail, wetglass und dem Nebel über Gelände (fog, seit Phase 6), Tröpfchen und Halos (sky, seit Phase 7), Granit, Felsburgen und Blockmeer (cliff) und Bodendecke (ground, seit Phase 8), Wald (forest, seit Phase 9) Raueis (rime mit Demo, seit Phase 10) Jahr (season mit Höhenstufen und Verwehungen, seit Phase 11) Gleis (rail mit Achse aus Flächen, Bahnkörper und Netz, seit Phase 12) Züge (rail mit Fahrdynamik, Lok und Wagen, seit Phase 13) und Klang (soundscape mit Dampflok, Kanten, Bergvögeln und Walpurgisnacht, seit Phase 15) da.");
        }
    }
}
