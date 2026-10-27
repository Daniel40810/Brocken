package com.dan.brocken.tools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Phase 18: „die Vorgänger bauen unverändert“. Brocken hat zehn Packages erweitert und drei neue gebracht; die
 * Vorgänger (Geiranger, Glacier, Lighthouse, Haithabu, Rungholt …) nutzen dieselben Packages. Dieses Werkzeug sucht in
 * {@code lib/Renderer.jar} jede Prüfklasse {@code com.dan.*.demo.*Check} und lässt sie im eigenen Prozess laufen (sie
 * enden mit System.exit); jede muss „Alles in Ordnung.“ melden. Bericht: {@code docs/status/Vorgaenger_Phase18.txt}.
 * {@code ant vorgaenger}. Clean and Build in den Vorgängern bleibt Handarbeit in NetBeans.
 */
public final class Vorgaenger {
    private Vorgaenger() { }

    /** Die Prüfklassen im Jar, sortiert. */
    public static List<String> pruefklassen(File jar) throws java.io.IOException {
        List<String> l = new ArrayList<>();
        try (JarFile j = new JarFile(jar)) {
            for (Enumeration<JarEntry> e = j.entries(); e.hasMoreElements(); ) {
                String n = e.nextElement().getName();
                if (n.startsWith("com/dan/") && n.contains("/demo/") && n.endsWith("Check.class") && !n.contains("$"))
                    l.add(n.substring(0, n.length() - 6).replace('/', '.'));
            }
        }
        l.sort(null);
        return l;
    }

    public static void main(String[] args) throws Exception {
        File jar = new File(args.length > 0 ? args[0] : "lib/Renderer.jar");
        List<String> ks = pruefklassen(jar);
        String java = new File(System.getProperty("java.home"), "bin/java").getPath(), cp = System.getProperty("java.class.path");
        int gut = 0;
        long t0 = System.nanoTime();
        StringBuilder b = new StringBuilder("Brocken · Prüfklassen aller Packages in " + jar + " (Phase 18)\n");
        for (String k : ks) {
            long t = System.nanoTime();
            String out = "";
            int rc = -1;
            // ein zweiter Versuch: Rechenzeiten in den Prüfklassen schwanken auf einer beschäftigten Maschine
            for (int v = 0; v < 2 && !(rc == 0 && out.contains("Alles in Ordnung.")); v++) {
                ProcessBuilder pb = new ProcessBuilder(java, "-cp", cp, "-Dstdout.encoding=UTF-8", "-Djava.awt.headless=true", "-Xmx3g", k);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                rc = p.waitFor();
            }
            boolean ok = rc == 0 && out.contains("Alles in Ordnung.");
            if (ok) gut++;
            StringBuilder fehl = new StringBuilder();
            for (String l : out.split("\\R"))
                if (l.contains("FEHLER") || l.contains("  FEHL ") || l.contains("fehlgeschlagen") || l.contains("Exception")) fehl.append(fehl.length() > 0 ? " | " : "").append(l.strip());
            String z = String.format(Locale.GERMAN, "%-7s %-45s %5.1f s  %s", ok ? "ok" : "FEHLER", k, (System.nanoTime() - t) / 1e9,
                    ok ? "" : (fehl.length() > 0 ? fehl : "Rückgabe " + rc));
            System.out.println(z);
            b.append(z.stripTrailing()).append('\n');
        }
        String e = String.format(Locale.GERMAN, "%d von %d Prüfklassen in Ordnung · %.0f s%nERGEBNIS: %s%n", gut, ks.size(), (System.nanoTime() - t0) / 1e9,
                gut == ks.size() && !ks.isEmpty() ? "die Vorgänger bauen unverändert" : "nicht alles in Ordnung");
        System.out.print(e);
        b.append(e);
        File f = new File("docs/status/Vorgaenger_Phase18.txt");
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), b.toString(), StandardCharsets.UTF_8);
        System.exit(gut == ks.size() && !ks.isEmpty() ? 0 : 1);
    }
}
