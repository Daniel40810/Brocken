package com.dan.brocken.test;

import com.dan.brocken.app.Bibliothek;
import com.dan.brocken.camera.Ansichten;
import com.dan.brocken.camera.Camera;
import com.dan.brocken.gl.Leben;
import com.dan.brocken.gl.Mat4;
import com.dan.brocken.gl.Quality;
import com.dan.brocken.gl.Renderer;
import com.dan.brocken.gl.Shader;
import com.dan.brocken.gl.TerrainLod;
import com.dan.brocken.scene.Terrain;
import com.dan.brocken.scene.Buehne;
import com.dan.brocken.scene.Geo;
import com.dan.brocken.scene.Physik;
import com.dan.brocken.scene.SceneClock;
import com.dan.brocken.scene.Site;
import com.dan.brocken.scene.Sun;
import com.dan.brocken.scene.Windrechner;
import com.dan.brocken.ui.AppIcon;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Prüfprogramm der App ohne Grafikkarte und ohne Datenbank (aus Geiranger, entschlackt). In NetBeans: Rechtsklick →
 * „Run File“, oder Ant-Target {@code pruefung}. Endet mit „ERGEBNIS: alles in Ordnung“ oder mit der Liste der
 * Verstöße (Rückgabewert 1). Wächst mit jeder Phase. Die Packages haben zusätzlich je ihre eigene Prüfklasse in
 * {@code .demo} ({@code WindCheck}, {@code RimeCheck}, {@code RailCheck}, seit Phase 7 {@code FogCheck} und {@code SkyCheck}), die hier mitlaufen.
 */
public final class Pruefung {
    private static int ok, bad;

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        mathe();
        ort();
        sonne();
        uhr();
        buehne();
        gelaende();
        alkis();
        wetter();
        wind();
        leben();
        nebel();
        licht();
        boden();
        wald();
        raueis();
        jahr();
        bahn();
        zug();
        gipfel();
        klang();
        regie();
        daten();
        ansichten();
        kamera();
        qualitaet();
        physik();
        shader();
        bibliothek();
        pakete();
        symbol();
        schliff();
        projekt();
        System.out.printf(Locale.GERMAN, "%n%d Prüfungen, %d Verstöße, %.1f s%n", ok + bad, bad, (System.nanoTime() - t0) / 1e9);
        System.out.println(bad == 0 ? "ERGEBNIS: alles in Ordnung" : "ERGEBNIS: " + bad + " Verstöße");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean c, String what) {
        if (c) ok++; else bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    static void head(String t) { System.out.println("\n" + t); }

    static void mathe() {
        head("Mathematik");
        float[] m = Mat4.perspective(55, 16 / 9.0, 0.5, 150_000, true);
        float[] inv = Mat4.invert(m);
        float[] id = Mat4.mul(m, inv);
        double e = 0;
        for (int i = 0; i < 16; i++) e = Math.max(e, Math.abs(id[i] - (i % 5 == 0 ? 1 : 0)));
        check(e < 1e-3, "Projektion · Umkehrung = Einheit");
        double[] p = Mat4.apply(Mat4.lookAt(0, 0, 0, 0, 0, -1), 0, 0, -10);
        check(Math.abs(p[2] + 10) < 1e-6, "Blick nach Norden: −z bleibt vorn");
    }

    static void ort() {
        head("Ort (F3 A)");
        double[] en = Geo.UTM32N.forward(Site.LAT, Site.LON);
        check(Math.abs(en[0] - Site.E0) < 0.01 && Math.abs(en[1] - Site.N0) < 0.01, "UTM hin und zurück am Ursprung");
        check(Math.abs(Site.LAT - 51.7993) < 0.001 && Math.abs(Site.LON - 10.6156) < 0.001, String.format(Locale.GERMAN, "Ursprung bei 51,7993° N, 10,6156° O (%.4f, %.4f)", Site.LAT, Site.LON));
        check(Site.KERN.contains(0, 0) && Site.HAUPTRASTER.contains(0, 0), "der Gipfel liegt im Kern und im Hauptraster");
        check(Math.abs(Site.KERN.breite() - 3000) < 1 && Math.abs(Site.KERN.hoehe() - 3000) < 1, "Kern 3 × 3 km");
        check(Math.abs(Site.HAUPTRASTER.breite() - 12_700) < 1 && Math.abs(Site.HAUPTRASTER.hoehe() - 11_900) < 1, "Hauptraster 12,7 × 11,9 km");
        for (Site.Ort o : Site.ORTE) {
            boolean in = o == Site.DREI_ANNEN ? !Site.HAUPTRASTER.contains(o.x(), o.z()) && Math.hypot(o.x(), o.z()) < Site.FERNRING : Site.HAUPTRASTER.contains(o.x(), o.z());
            check(in, o.name() + (o == Site.DREI_ANNEN ? " knapp östlich des Hauptrasters (im Fernring)" : " im Hauptraster"));
        }
        double[] xz = Site.xz(new double[]{51.79925, 10.61558});
        check(Math.hypot(xz[0], xz[1]) < 10, "der Gipfelstein (51,79925° N, 10,61558° O) liegt am Ursprung");
        check(Math.abs(Site.horizon(941) / 1000 - 117) < 3, String.format(Locale.GERMAN, "vom Gipfel über dem Vorland reicht die Kimm rund 117 km (%.0f km)", Site.horizon(941) / 1000));
    }

    static void sonne() {
        head("Sonne");
        SceneClock c = new SceneClock(LocalDate.of(2026, 6, 21), 13.3);
        double[] s = c.sun();
        check(Math.abs(s[0] - 61.6) < 1.0, String.format(Locale.GERMAN, "21. Juni mittags: Sonne rund 61,6° hoch (%.1f°)", s[0]));
        check(Math.abs(s[1] - 180) < 6, String.format(Locale.GERMAN, "um 13:18 MESZ steht sie im Süden (%.0f°)", s[1]));
        c = new SceneClock();
        s = c.sun();
        check(s[0] > 4 && s[0] < 12 && s[1] > 115 && s[1] < 140, String.format(Locale.GERMAN, "Start 21.10. 9:00 MESZ: Sonne tief im Südosten (%.1f° aus %.0f°)", s[0], s[1]));
        double day = c.local().toLocalDate().atStartOfDay(SceneClock.ZONE).toInstant().toEpochMilli();
        double up = Sun.crossing(day, c.lat, c.lon, true);
        check(!Double.isNaN(up) && up < c.epochMillis(), "am 21. Oktober ist die Sonne um 9 Uhr schon auf");
        double[] d = Sun.direction(s[0], s[1]);
        check(d[0] > 0 && d[2] > 0, "Sonne im Südosten: x > 0 (Ost), z > 0 (Süd)");
    }

    static void uhr() {
        head("Uhr");
        SceneClock c = new SceneClock();
        check(c.label().equals("21.10.2026 09:00 MESZ"), "Start: " + c.label());
        c.shiftDays(10);
        check(c.zoneLabel().equals("MEZ"), "am 31. Oktober gilt MEZ");
        c.lapse = 3600;
        double h = c.hour();
        c.step(1);
        check(Math.abs(c.hour() - (h + 1) % 24) < 1e-6, "Zeitraffer 1 h/s");
    }

    static void buehne() {
        head("Bühne (Platzhalter bis Phase 2)");
        check(Math.abs(Buehne.height(0, 0) - Site.GIPFEL) < 1, String.format(Locale.GERMAN, "Gipfel %.1f m", Buehne.height(0, 0)));
        double max = 0;
        for (int i = -60; i <= 60; i++) for (int j = -60; j <= 60; j++) max = Math.max(max, Buehne.height(i * 100, j * 100));
        check(max <= Buehne.height(0, 0) + 1e-9, "der Gipfel ist der höchste Punkt im Hauptraster");
        check(Math.abs(Buehne.height(Site.WURMBERG.x(), Site.WURMBERG.z()) - Site.WURMBERG.grund()) < 30, String.format(Locale.GERMAN, "Wurmberg rund 971 m (%.0f m)", Buehne.height(Site.WURMBERG.x(), Site.WURMBERG.z())));
        for (Site.Ort o : new Site.Ort[]{Site.BAHNHOF_BROCKEN, Site.GOETHEWEG, Site.SCHIERKE, Site.TORFHAUS})
            check(Math.abs(Buehne.height(o.x(), o.z()) - o.grund()) < 110, String.format(Locale.GERMAN, "%s: Bühne %.0f m, Werkbuch %.0f m (± 110 m)", o.name(), Buehne.height(o.x(), o.z()), o.grund()));
        check(Math.abs(Buehne.height(50_000, 0) - Buehne.VORLAND) < 1e-6, "im Fernring flach auf dem Vorland");
        check(Buehne.zone(0, 0).equals("Kern") && Buehne.zone(5000, 0).equals("Hauptraster") && Buehne.zone(30_000, 0).equals("Fernring")
                && Buehne.zone(200_000, 0).equals("außerhalb"), "Zonen: Kern, Hauptraster, Fernring, außerhalb");
        check(Buehne.naechsterOrt(5, 5).contains("Gipfelstein"), "nächster Ort am Gipfel: " + Buehne.naechsterOrt(5, 5));
        Terrain b = Terrain.ausBuehne();
        double e = 0;
        for (int k = 0; k < 200; k++) { double x = -6000 + k * 61.3, z = 5000 - k * 47.9; e = Math.max(e, Math.abs(b.height(x, z) - Buehne.height(x, z))); }
        check(!b.measured() && e < 0.5, String.format(Locale.GERMAN, "ohne daten/gelaende: das Gelände ist die Kuppe (größte Abweichung %.2f m)", e));
    }

    static void gelaende() throws Exception {
        head("Gelände (Phase 2)");
        // Kodierung: 3,7 cm Stufe, NHN genau
        check(Terrain.decode(Terrain.encode(0)) == 0 && Math.abs(Terrain.decode(Terrain.encode(1141.2)) - 1141.2) < 0.02, "16-Bit-Höhen: NHN genau, Stufe 3,7 cm");
        check(Math.abs(Terrain.N * Terrain.CELL - 12_800) < 1e-9 && Math.abs(Terrain.FN * Terrain.FCELL - 204_800) < 1e-9 && Math.abs(Terrain.KN * Terrain.KCELL - 3072) < 1e-9,
                "Raster: Hauptraster 12,8 km zu 12,5 m, Fernring 204,8 km zu 200 m, Kern 3 072 m zu 2 m");
        double[] h = Site.HAUPTRASTER.scene(), k = Site.KERN.scene();
        check(h[0] >= Terrain.X0 && h[2] <= Terrain.X0 + Terrain.N * Terrain.CELL && h[1] >= Terrain.Z0 && h[3] <= Terrain.Z0 + Terrain.N * Terrain.CELL, "das Hauptraster deckt das Fenster Hauptraster");
        check(k[0] >= Terrain.KX0 && k[2] <= Terrain.KX0 + Terrain.KN * Terrain.KCELL && k[1] >= Terrain.KZ0 && k[3] <= Terrain.KZ0 + Terrain.KN * Terrain.KCELL, "das Kernraster deckt den Kern");
        check(Math.abs(Terrain.FX0 - Terrain.MX + Site.FERNRING) < 3000, "der Fernring reicht rund 100 km um die Mitte");
        // ein Kern, der 3 m über dem Hauptraster liegt: die Nähte bleiben stetig
        Terrain b = Terrain.ausBuehne();
        short[] kern = new short[Terrain.KN * Terrain.KN];
        for (int j = 0; j < Terrain.KN; j++) for (int i = 0; i < Terrain.KN; i++) kern[j * Terrain.KN + i] = Terrain.encode(b.baseHeight(Terrain.kcx(i), Terrain.kcz(j)) + 3);
        Terrain mk = new Terrain(b.h, b.fern, kern, b.art, new java.util.Properties());
        double na = com.dan.brocken.tools.GelaendeImport.naehte(mk)[0];
        check(na < 0.5, String.format(Locale.GERMAN, "Kern 3 m über dem Hauptraster: größter Sprung an den Nähten %.3f m (unter 0,5 m)", na));
        check(Math.abs(mk.height(0, 40) - b.height(0, 40) - 3) < 0.05 && Math.abs(mk.height(5000, 0) - b.height(5000, 0)) < 1e-9, "innen gilt der Kern, außen das Hauptraster");
        // CDLOD ohne Grafikkarte: am Gipfelstein Knoten bis 2 m, in Torfhaus bis 8 m
        TerrainLod lod = new TerrainLod(b);
        lod.finest = 2;
        lod.finestKern = 0;
        lod.collect(new double[]{0, 1145, 0}, null);
        float[] sel = lod.selection(false), q = lod.selection(true);
        double fein = Double.MAX_VALUE;
        for (int i = 0; i < sel.length; i += 4) fein = Math.min(fein, sel[i + 2] / TerrainLod.DIM);
        for (int i = 0; i < q.length; i += 4) fein = Math.min(fein, q[i + 2] / (TerrainLod.DIM / 2.0));
        check(Math.abs(fein - 2) < 1e-9, String.format(Locale.GERMAN, "am Gipfel feinster Punktabstand 2 m (%d Knoten)", lod.nodes));
        lod.collect(new double[]{Site.TORFHAUS.x(), 800, Site.TORFHAUS.z()}, null);
        sel = lod.selection(false);
        q = lod.selection(true);
        double feinT = Double.MAX_VALUE;
        for (int i = 0; i < sel.length; i += 4) if (Math.hypot(sel[i] + sel[i + 2] / 2 - Site.TORFHAUS.x(), sel[i + 1] + sel[i + 2] / 2 - Site.TORFHAUS.z()) < 300) feinT = Math.min(feinT, sel[i + 2] / TerrainLod.DIM);
        for (int i = 0; i < q.length; i += 4) if (Math.hypot(q[i] + q[i + 2] / 2 - Site.TORFHAUS.x(), q[i + 1] + q[i + 2] / 2 - Site.TORFHAUS.z()) < 300) feinT = Math.min(feinT, q[i + 2] / (TerrainLod.DIM / 2.0));
        check(Math.abs(feinT - 8) < 1e-9, "in Torfhaus (außerhalb des Kerns) feinster Punktabstand 8 m");
        // das importierte Gelände, wenn es da ist
        Terrain t = Terrain.load(Terrain.defaultDir());
        if (!t.measured()) { check(false, "daten/gelaende fehlt – erst ant gelaende"); return; }
        System.out.println("  " + t.describe());
        double[] g = {0, 0, -1e9};
        for (double z = -300; z <= 300; z += 2) for (double x = -300; x <= 300; x += 2) { double y = t.height(x, z); if (y > g[2]) g = new double[]{x, z, y}; }
        check(Math.abs(g[2] - Site.GIPFEL) < 0.6 && Math.hypot(g[0], g[1]) < 100,
                String.format(Locale.GERMAN, "der Gipfel steht auf %.1f m (amtlich 1 141,2 m), %.0f m vom Gipfelstein", g[2], Math.hypot(g[0], g[1])));
        double nt = com.dan.brocken.tools.GelaendeImport.naehte(t)[0];
        check(nt < 0.5, String.format(Locale.GERMAN, "kein Sprung an den Nähten größer als 0,5 m (%.3f m)", nt));
        for (Site.Ort o : new Site.Ort[]{Site.BAHNHOF_BROCKEN, Site.SCHIERKE}) {
            double y = t.height(o.x(), o.z());
            check(Math.abs(y - o.grund()) < 6, String.format(Locale.GERMAN, "%s auf %.1f m (Werkbuch %.0f m)", o.name(), y, o.grund()));
        }
        check(t.artAt(Site.GOETHEWEG.x(), Site.GOETHEWEG.z()) == Terrain.WALD && t.artAt(Site.BAHNHOF_BROCKEN.x() + 30, Site.BAHNHOF_BROCKEN.z()) != Terrain.WALD,
                "Nutzung: am Goetheweg Wald, am Bahnhof Brocken kein Wald");
        double wald = t.shares()[Terrain.WALD] + t.shares()[Terrain.KEINE];
        check(wald > 0.8, String.format(Locale.GERMAN, "Wald und Nationalpark Niedersachsen decken %.0f %% des Hauptrasters", wald * 100));
        double auf = Double.parseDouble(t.props.getProperty("abgleich.waldaufschlag", "0"));
        check(auf > 3 && auf < 20, String.format(Locale.GERMAN, "Waldaufschlag gemessen: %.1f m (plausibel 3 bis 20 m)", auf));
    }

    static void alkis() throws Exception {
        head("ALKIS");
        check(com.dan.brocken.data.Alkis.art(43002, "Wald") == Terrain.WALD && com.dan.brocken.data.Alkis.art(44001, "x") == Terrain.WASSER
                && com.dan.brocken.data.Alkis.art(42010, "Bahnverkehr") == Terrain.BAHN && com.dan.brocken.data.Alkis.art(41009, "Kultur") == Terrain.SIEDLUNG, "Nutzungsarten zugeordnet");
        // ein Quadrat 100 × 100 m mit einem Loch 40 × 40 m, gerastert zu 10 m
        com.dan.brocken.data.Alkis.Objekt o = new com.dan.brocken.data.Alkis.Objekt(43002, "Wald",
                new double[][]{{0, 0, 100, 0, 100, 100, 0, 100}, {30, 30, 70, 30, 70, 70, 30, 70}});
        byte[] z = new byte[20 * 20];
        com.dan.brocken.data.Alkis.rastern(o, z, 20, 20, -50, 150, 10, (byte) 1);
        int c = 0;
        for (byte v : z) c += v;
        check(c == 100 - 16, "Rastern: 100 Zellen minus 16 im Loch (" + c + ")");
        File f = new File(com.dan.brocken.data.Alkis.ordner(), com.dan.brocken.data.Alkis.GEBAEUDE);
        if (f.isFile()) {
            boolean bahnhof = false;
            for (com.dan.brocken.data.Alkis.Objekt g : com.dan.brocken.data.Alkis.lesen(f)) if (g.name().equals("Bahnhof Brocken")) bahnhof = true;
            check(bahnhof, "ALKIS-Gebäude: „Bahnhof Brocken“ ist da");
        }
    }

    /** Phase 3: Klima „Brocken“ gegen die Station (DoD: Monatsmittel auf 0,5 K), Regie, Schichtung, Verdrahtung, Schatten. */
    static void wetter() {
        head("Himmel und Wetter (Phase 3)");
        com.dan.brocken.wiring.KlimaBrocken.Lauf l = com.dan.brocken.wiring.KlimaBrocken.mittel(30, 3, null);
        check(l.fehler() <= 0.5, String.format(Locale.GERMAN, "Monatsmittel am Gipfel über 90 gewürfelte Jahre: %s °C, größte Abweichung von der Station %.2f K (DoD: 0,5 K)",
                l.zeile(), l.fehler()));
        com.dan.brocken.wiring.KlimaBrocken.Lauf l2 = com.dan.brocken.wiring.KlimaBrocken.simulieren(30, 4711L, null);
        check(l2.fehler() <= 0.5, String.format(Locale.GERMAN, "mit einem anderen Würfel (4711, 30 Jahre) %.2f K", l2.fehler()));
        double ns = l.jahresNiederschlag();
        check(Math.abs(ns - 1799) < 180, String.format(Locale.GERMAN, "Niederschlag %.0f mm im Jahr (Station 1 799 mm, ± 10 %%)", ns));
        check(Math.abs(l.nebeltage() - com.dan.brocken.wiring.KlimaBrocken.DWD_NEBELTAGE) < 31, String.format(Locale.GERMAN, "Nebeltage am Gipfel %.0f (Station %.0f, ± 10 %%)",
                l.nebeltage(), com.dan.brocken.wiring.KlimaBrocken.DWD_NEBELTAGE));
        check(l.sonne() > 1100 && l.sonne() < 1900, String.format(Locale.GERMAN, "Sonnenstunden am Gipfel rund %.0f (Station 1 463, grob geschätzt)", l.sonne()));
        int n = com.dan.weather.Weather.Phase.values().length;
        check(com.dan.brocken.wiring.KlimaBrocken.ZIELE.length == n && com.dan.brocken.wiring.KlimaBrocken.UEBERGAENGE.length == n
                        && com.dan.brocken.wiring.KlimaBrocken.SCHICHTUNG.length == n && com.dan.brocken.wiring.KlimaBrocken.NAMEN.length == n,
                "das Klima hat für alle " + n + " Lagen Zielwerte, Übergänge, Schichtung und Namen");
        boolean zeilen = true;
        for (double[] z : com.dan.brocken.wiring.KlimaBrocken.UEBERGAENGE) { double sum = 0; for (double v : z) sum += v; zeilen &= Math.abs(sum - 1) < 1e-9; }
        check(zeilen, "jede Zeile der Übergänge summiert sich zu 1");

        Renderer r = new Renderer();
        String w = r.wetter("INVERSION", 0);
        com.dan.weather.Weather.Snapshot s = r.wiring.snapshot();
        check("Lage Inversion".equals(w) && s.cloudAt(Site.GIPFEL) < 0.01 && s.cloudAt(600) > 0.9 && s.temperatureAt(Site.GIPFEL) > s.temperatureAt(600) + 3,
                String.format(Locale.GERMAN, "Inversion: Hochnebel %.0f–%.0f m, der Gipfel darüber %.1f °C, in 600 m %.1f °C", s.cloudBase(), s.cloudTop(),
                        s.temperatureAt(Site.GIPFEL), s.temperatureAt(600)));
        com.dan.cloud.Clouds c = new com.dan.cloud.Clouds();
        r.wiring.feed(c, 560);
        check(Math.abs(c.baseHeight - s.cloudBase()) < 1e-9 && c.type == com.dan.cloud.CloudType.STRATUS && c.oktas > 7 && c.shadowHeight < c.baseHeight,
                String.format(Locale.GERMAN, "die Wolken bekommen Basis %.0f m, %s %.1f/8, Schattenkarte in %.0f m", c.baseHeight, c.type.label, c.oktas, c.shadowHeight));
        com.dan.cloud.Clouds.Snapshot cs = c.snapshot();
        double[] sun = {0.3, 0.4, 0.86};
        check(com.dan.cloud.Clouds.shadow(cs, 0, Site.GIPFEL, 0, sun) > 0.9 && com.dan.cloud.Clouds.shadow(cs, 3000, 400, 3000, sun) < 0.1,
                "Nebelmeer: der Gipfel in der Sonne, der Grund unter der Decke im Schatten");
        r.wetter("STAU", 0);
        s = r.wiring.snapshot();
        check(s.cloudAt(Site.GIPFEL) > 0.9 && s.visibilityAt(Site.GIPFEL) < 0.1 && s.cloudAt(500) < 0.01,
                String.format(Locale.GERMAN, "Westlage mit Stau: der Gipfel in der Wolke (Basis %.0f m, Sicht %.0f m), Schierke darunter", s.cloudBase(), s.visibilityAt(Site.GIPFEL) * 1000));
        String b = r.wetter("Über dem Nebelmeer", 2.5);
        s = r.wiring.snapshot();
        check(b != null && Math.abs(r.clock.hour() - 9.0) < 0.01 && s.phase() == com.dan.weather.Weather.Phase.INVERSION,
                String.format(Locale.GERMAN, "Regiebuch „Über dem Nebelmeer“ nach 2,5 h: %s, %s (%s)", r.clock.timeLabel(), s.stepText(), com.dan.brocken.wiring.KlimaBrocken.name(s.phase())));
        check(r.wetter("gibt es nicht", 0) == null && com.dan.brocken.wiring.KlimaBrocken.regiebuecher().size() == 4, "vier Regiebücher, unbekannte Namen bleiben ohne Wirkung");
        r.step(0.1);
        String[] hud = r.hudLines();
        boolean wl = false;
        for (String h : hud) wl |= h.startsWith("Wetter ");
        check(wl && java.util.Arrays.asList(Renderer.PASSES).containsAll(java.util.List.of("Schatten", "Himmel", "Gelände", "Leben", "Nebel", "Wind", "Wolken")),
                "HUD mit der Zeile des Wetters, Durchgänge " + String.join(", ", Renderer.PASSES));

        // Schatten der Kuppe je Sonnenstand
        Terrain t = r.terrain;
        if (t.measured()) {
            SceneClock k = new SceneClock(LocalDate.of(2026, 10, 21), 13.3);
            double[] sp = k.sun();
            double mittag = com.dan.brocken.scene.Kuppenschatten.licht(t, 0, t.height(0, 0) + 1.7, 0, Sun.direction(sp[0], sp[1]));
            double g = com.dan.brocken.scene.Kuppenschatten.sonneAb(t, k, 0, 0), sch = com.dan.brocken.scene.Kuppenschatten.sonneAb(t, k, Site.SCHIERKE.x(), Site.SCHIERKE.z());
            double up = Sun.crossing(LocalDate.of(2026, 10, 21).atStartOfDay(SceneClock.ZONE).toInstant().toEpochMilli(), k.lat, k.lon, true);
            double upH = java.time.Instant.ofEpochMilli((long) up).atZone(SceneClock.ZONE).getHour() + java.time.Instant.ofEpochMilli((long) up).atZone(SceneClock.ZONE).getMinute() / 60.0;
            check(mittag > 0.99 && g < upH + 0.4 && sch > g, String.format(Locale.GERMAN, "21.10.: der Gipfel hat ab %s Sonne (Aufgang %s), Bahnhof Schierke im Tal erst ab %s; mittags volles Licht",
                    hhmm(g), hhmm(upH), hhmm(sch)));
        }
    }

    /**
     * Phase 4: das massenkonsistente Feld auf dem echten Gelände (DoD: Masse auf 1 %, Beschleunigung über der Kuppe nach
     * der Faustformel von Walmsley, Taylor und Salmon 1989), die Eichung am Gipfel und die Rauchfäden.
     */
    static void wind() {
        head("Wind (Phase 4)");
        Renderer r = new Renderer();
        Terrain t = r.terrain;
        if (!t.measured()) { check(false, "daten/gelaende fehlt – ohne Gelände kein Windfeld"); return; }
        r.wetter("STAU", 0);
        r.handWind(260, 14);    // seit Phase 5 streut die Regie die Richtung; die Prüfung hält sie fest
        com.dan.brocken.wiring.Wiring w = r.wiring;
        Windrechner.Ergebnis e = r.windJetzt();
        com.dan.wind.WindSolution so = e.solution();
        System.out.println("  " + e.text());
        check(so.massMax < 0.01, String.format(Locale.GERMAN, "Masse erhalten: größter Fehler je Zelle %.4f %% (DoD: 1 %%)", so.massMax * 100));
        check(so.residual < 1e-3 && so.iterations < w.wind.maxIterations, String.format(Locale.GERMAN, "der Löser kommt an: %d Schritte, Rest %.0e", so.iterations, so.residual));
        // Faustformel: Höhe H der Kuppe über dem Umland luvwärts, L halbe Breite in halber Höhe; Bezug 4 km luvwärts in 10 m
        double[] d = w.wind.toVector();
        double gip = t.height(0, 0), um = 0;
        int nu = 0;
        for (int a = 0; a < 36; a++) { double ang = Math.toRadians(a * 10); um += t.height(4500 * Math.cos(ang), 4500 * Math.sin(ang)); nu++; }
        um /= nu;
        double hH = gip - um, lL = 0;
        for (double s = 0; s < 4500; s += 25) if (t.height(-d[0] * s, -d[2] * s) < gip - hH / 2) { lL = s; break; }
        if (lL == 0) lL = 4500;
        double sg = so.speedAt(0, 10, 0), su = so.speedAt(-d[0] * 4000, 10, -d[2] * 4000);
        double ds = sg / su - 1, faust = 1.6 * hH / lL * Math.exp(-4 * 10 / lL);
        check(ds > 0.5 * faust && ds < 2.0 * faust, String.format(Locale.GERMAN,
                "Beschleunigung am Gipfel in 10 m: %+.0f %% gegen 4 km luvwärts; Faustformel 1,6 · H/L mit H %.0f m, L %.0f m: %+.0f %% (halb bis doppelt; Umland nicht flach, zu prüfen)",
                ds * 100, hH, lL, faust * 100));
        double gx = Site.GIPFELSTEIN.x(), gz = Site.GIPFELSTEIN.z(), gy = so.groundAt(gx, gz) + 10;
        double gipfel = Math.hypot(r.wiring.windAt(gx, gy, gz)[0], r.wiring.windAt(gx, gy, gz)[2]);
        double[] ww = Site.WETTERWARTE;
        double wy = so.groundAt(ww[0], ww[1]) + com.dan.brocken.wiring.WindBrocken.GEBER;
        double warte = Math.hypot(r.wiring.windAt(ww[0], wy, ww[1])[0], r.wiring.windAt(ww[0], wy, ww[1])[2]);
        check(Math.abs(warte - w.windSpeed) < 0.15 * w.windSpeed + 0.2, String.format(Locale.GERMAN,
                "geeicht (seit Phase 5 wie die Station): an der Wetterwarte in 27 m %.1f m/s, die Regie sagt %.1f m/s (Faktor %.2f); am Gipfelstein in 10 m %.1f m/s",
                warte, w.windSpeed, w.windGain, gipfel));
        StringBuilder orte = new StringBuilder();
        boolean leiser = true;
        for (Site.Ort o : new Site.Ort[]{Site.TORFHAUS, Site.SCHIERKE, Site.WURMBERG, Site.GOETHEWEG}) {
            double v = so.speedAt(o.x(), 10, o.z()) * w.windGain;
            leiser &= v < gipfel;
            orte.append(String.format(Locale.GERMAN, " · %s %.1f", o.name(), v));
        }
        check(leiser, "überall sonst weniger Wind als am Gipfel (m/s in 10 m)" + orte);
        check(e.faeden().size() >= Windrechner.FAEDEN / 2, String.format(Locale.GERMAN, "%d Rauchfäden gezogen, jeder ab %.0f m über Grund", e.faeden().size(), Windrechner.FADEN_H));
        // eine ruhige Nacht: Kaltluft in den Tälern, Hangwind hinab
        com.dan.wind.WindField n = w.wind.copy();
        n.speed = 1;
        n.stability = com.dan.wind.WindField.Stability.INVERSION;
        n.heating = -1;
        n.quality = com.dan.wind.WindField.Quality.LOW;
        double[] f = Windrechner.fenster();
        com.dan.wind.WindSolution ns = n.solve(f[0], f[1], f[2], f[3]);
        double kalt = 0;
        for (double z = f[1]; z < f[3]; z += 200) for (double x = f[0]; x < f[2]; x += 200) kalt = Math.max(kalt, ns.coldAt(x, z));
        check(ns.massMax < 0.01 && kalt > 20 && ns.coldAt(0, 0) < 1, String.format(Locale.GERMAN, "Nacht mit Inversion: Kaltluftseen bis %.0f m tief, am Gipfel keiner; Masse %.4f %%",
                kalt, ns.massMax * 100));
        check(r.windZeile().startsWith("Windfeld ") && r.windZeile().contains("Wetterwarte in 27 m"), "Zeile im HUD und im Bedienfeld: " + r.windZeile());
        r.physik.stop();
        r.windrechner.stop();
    }

    /**
     * Phase 5: der Wind der Regie gegen die Stundenwerte der Station (DWD 1991–2020), das Gras im Wind des Felds
     * (DoD: am Gipfel sichtbar stärker als am Osthang), Kolkraben im Aufwind, Rauch im Wind am Austritt, die Schnittstelle
     * {@code Wind} in den sieben Packages.
     */
    static void leben() throws Exception {
        head("Wind in der Szene (Phase 5)");
        com.dan.brocken.wiring.WindBrocken.Lauf l = com.dan.brocken.wiring.WindBrocken.mittel(30, 3, null);
        check(l.fehlerMonat() <= 0.5, String.format(Locale.GERMAN, "Monatsmittel des Winds über 90 gewürfelte Jahre: %s m/s, größte Abweichung von der Station %.2f m/s (± 0,5)",
                l.zeile(), l.fehlerMonat()));
        double[] sp = com.dan.brocken.wiring.WindBrocken.PERZENTIL;
        boolean perz = true;
        for (int i = 0; i < 4; i++) perz &= Math.abs(l.perzentil()[i] - sp[i]) <= 1.01;
        check(Math.abs(l.mittel() - com.dan.brocken.wiring.WindBrocken.MITTEL) < 0.3 && Math.abs(l.sd() - com.dan.brocken.wiring.WindBrocken.SD) < 0.6 && perz,
                String.format(Locale.GERMAN, "Stundenwerte: Mittel %.2f m/s (Station 10,81), Streuung %.2f (5,57), 10/50/90/99 %%: %.1f %.1f %.1f %.1f (4,5 10,5 18,5 26,5)",
                        l.mittel(), l.sd(), l.perzentil()[0], l.perzentil()[1], l.perzentil()[2], l.perzentil()[3]));
        check(l.fehlerSektor() <= 4, String.format(Locale.GERMAN, "Richtungen in 12 Sektoren ab Nord: %s %% (Station 5 4 4 5 4 4 7 14 19 17 10 7), größte Abweichung %.1f Punkte",
                l.sektoren(), l.fehlerSektor()));

        Renderer r = new Renderer();
        Terrain t = r.terrain;
        if (!t.measured()) { check(false, "daten/gelaende fehlt – ohne Gelände kein Windfeld"); return; }
        r.wetter("KLAR", 0);
        r.handWind(265, 14);
        r.windJetzt();
        r.step(0.01);
        // DoD: Gras am Gipfel (Heide westlich des Gipfelsteins) gegen den Osthang (900 m östlich, im Lee)
        double gx = -100, gz = 0, ox = 900, oz = 0;
        double ng = r.leben.neigung(gx, gz, t.height(gx, gz)), no = r.leben.neigung(ox, oz, t.height(ox, oz));
        double[] vg = r.leben.gras.windAt(gx, t.height(gx, gz), gz), vo = r.leben.gras.windAt(ox, t.height(ox, oz), oz);
        check(ng > 2 * no && ng > 0.3, String.format(Locale.GERMAN, "DoD: Westwind 14 m/s an der Wetterwarte – das Gras am Gipfel neigt sich %.0f %% seiner Höhe (Wind %.1f m/s in 10 m), am Osthang %.0f %% (%.1f m/s)",
                ng * 100, vg[2], no * 100, vo[2]));
        double[] fw = r.wiring.windAt(gx, t.height(gx, gz) + 10, gz);
        check(Math.abs(vg[0] - fw[0] / Math.hypot(fw[0], fw[2])) < 1e-6, "das Gras nimmt den Wind des Felds (mit dem Faktor der Eichung) 10 m über seinem Boden");
        check(r.leben.gras.wind != null && r.leben.rauch.wind != null && r.leben.raben.wind != null, "Gras, Rauch und Kolkraben hängen am Feld (wiring::windAt)");
        // Rauch: zieht mit dem Wind am Schornstein
        com.dan.smoke.Source q = r.leben.rauch.sources().get(0);
        double[] wr = r.leben.rauch.windFor(q), fr = r.wiring.windAt(q.x(), q.y(), q.z());
        check(Math.abs(wr[0] - fr[0] / Math.hypot(fr[0], fr[2])) < 1e-6 && Math.abs(com.dan.smoke.Plume.roofWind(wr[2]) - Math.hypot(fr[0], fr[2])) < 1e-6,
                String.format(Locale.GERMAN, "Rauch am Brockenhotel zieht mit dem Wind am Schornstein (%.1f m/s), steigt %.0f m", Math.hypot(fr[0], fr[2]), r.leben.rauch.finalRise(q)));
        // Kolkraben: nach sechs Minuten im Aufwind am Westhang
        r.leben.vorlauf(360);
        double up = 0, west = 0;
        int n = 0;
        for (com.dan.flock.Flock.Gull g : r.leben.raben.gulls()) {
            if (!g.raven) continue;
            n++;
            up += r.leben.raben.updraft(g.pos[0], g.pos[1], g.pos[2]);
            if (g.pos[0] < 0) west++;
        }
        check(n == com.dan.brocken.gl.Leben.RABEN && up / n > 0.5 && west >= n - 1 && r.leben.raben.ravensSoaring() >= 2,
                String.format(Locale.GERMAN, "%d Kolkraben nach 6 min: %d im Aufwind am Westhang (im Mittel %.1f m/s Steigen), keine Möwen", n, r.leben.raben.ravensSoaring(), up / n));
        check(r.leben.raben.gullCount == 0 && r.leben.raben.present(com.dan.flock.Species.MOEWE) == 0, "keine Möwen am Brocken");
        double[] gr = com.dan.brocken.gl.Leben.GRAS;
        check(gr[Terrain.HEIDE] > 0.5 && gr[Terrain.WALD] > 0 && gr[Terrain.WASSER] == 0, "Gras nach ALKIS: dicht auf der Heide, Reitgras im Wald, nicht im Wasser");
        check(com.dan.brocken.gl.Leben.season(290) > 0.7 && com.dan.brocken.gl.Leben.season(190) == 0 && com.dan.brocken.gl.Leben.season(30) == 1,
                "Jahreszeit des Grases: im Juli grün, im Oktober strohgelb, im Januar grau");
        // die Schnittstelle Wind in den sieben Packages (Vorgänger bauen unverändert: ohne Feld bleibt alles wie bisher)
        int da = 0;
        for (String p : new String[]{"reed", "smoke", "flock", "fog", "forest", "season", "soundscape"}) {
            try { Class<?> c = Class.forName("com.dan." + p + ".Wind"); if (c.isInterface()) da++; } catch (ClassNotFoundException e) { }
        }
        check(da == 7, da + " von 7 Packages haben die Schnittstelle Wind (reed, smoke, flock, fog, forest, season, soundscape)");
        check(r.leben.zeile(gx, gz, t.height(gx, gz)).contains("Kolkraben"), "Zeile im HUD: " + r.leben.zeile(gx, gz, t.height(gx, gz)));
        r.physik.stop();
        r.windrechner.stop();
    }

    /**
     * Phase 6: Nebel über Gelände. DoD: das Nebelmeer steht bei Inversion und löst sich bis Mittag von unten auf (im
     * Budget: auf der Radeon zu messen). Dazu Talnebel, Stau am Luvhang, die Übergabe der unteren Wolkenschicht.
     */
    static void nebel() throws Exception {
        head("Nebel am Berg (Phase 6)");
        Renderer r = new Renderer();
        Terrain t = r.terrain;
        double[] stunden = {2, 3.5, 4.5, 5.5};
        double[] base = new double[4], top = new double[4], amt = new double[4];
        double vg = 0, vs = 0;
        for (int i = 0; i < 4; i++) {
            r.wetter("Über dem Nebelmeer", stunden[i]);
            r.step(0.01);
            base[i] = r.nebel.seaBase();
            top[i] = r.nebel.seaTop();
            amt[i] = r.nebel.seaAmount();
            if (i == 0) {
                vg = r.nebel.visibilityAt(0, t.height(0, 0) + 2, 0);
                vs = r.nebel.visibilityAt(Site.SCHIERKE.x(), t.height(Site.SCHIERKE.x(), Site.SCHIERKE.z()) + 2, Site.SCHIERKE.z());
                check(r.wetter.clouds.oktas <= 8 * r.nebel.seaAmount() + 1e-9, "beim Nebelmeer übernimmt der Nebel die untere Schicht; die Wolken behalten nur den Schatten");
            }
        }
        check(amt[0] > 0.95 && Math.abs(base[0] - 450) < 30 && Math.abs(top[0] - 850) < 30 && vg > 50_000 && vs < 200,
                String.format(Locale.GERMAN, "DoD: „Über dem Nebelmeer“ um 8:30 – Nebelmeer %.0f–%.0f m, der Gipfel in klarer Luft, in Schierke Sicht %.0f m", base[0], top[0], vs));
        check(base[1] > base[0] + 50 && base[2] > base[1] + 50 && top[2] > 780 && amt[2] > 0.9,
                String.format(Locale.GERMAN, "es löst sich von unten auf: Untergrenze 8:30 %.0f m, 10:00 %.0f m, 11:00 %.0f m; Obergrenze bleibt bei %.0f m", base[0], base[1], base[2], top[2]));
        check(amt[3] < 0.05, String.format(Locale.GERMAN, "um 12 Uhr ist das Meer fort (%.0f %%), noch in der Lage Inversion", amt[3] * 100));
        // im Lauf (Zeitraffer 60) dasselbe wie beim Sprung
        r.wetter("Über dem Nebelmeer", 2);
        r.step(0.01);
        r.clock.lapse = 60;
        r.frozen = false;
        for (int i = 0; i < 90; i++) r.step(1.0);
        double lauf = r.nebel.seaBase();
        r.frozen = true;
        r.clock.lapse = 0;
        r.nebelJetzt();
        check(Math.abs(lauf - r.nebel.seaBase()) < 25, String.format(Locale.GERMAN, "im Lauf 90 min Zeitraffer: Untergrenze %.0f m, nach dem Sprung gerechnet %.0f m", lauf, r.nebel.seaBase()));
        // Stau: Gürtel am Luvhang (Westen) unter der Decke, nicht im Lee
        r.wetter("Westlage mit Stau", 9);
        r.windJetzt();
        r.step(0.01);
        double cb = r.nebel.capBase;
        double luv = 0, lee = 0;
        for (int i = 0; i < 40; i++) {
            double xl = -1500 - i * 50, xe = 1500 + i * 50;
            luv = Math.max(luv, r.nebel.density(xl, cb - 80, 0));
            lee = Math.max(lee, r.nebel.density(xe, cb - 80, 0));
        }
        check(!Double.isNaN(cb) && luv > 0.003 && lee < luv * 0.2, String.format(Locale.GERMAN,
                "Stau: Decke ab %.0f m, 80 m darunter am Luvhang Sicht %.0f m, am Leehang %s", cb, com.dan.fog.Fog.SIGMA_PER_VIS / Math.max(1e-9, luv),
                lee <= 0 ? "klar" : String.format(Locale.GERMAN, "%.0f m", com.dan.fog.Fog.SIGMA_PER_VIS / lee)));
        // Talnebel: ruhige Nacht der Lage Talnebel
        r.wetter("NEBEL", 0);
        r.clock.setHour(5);
        r.step(0.01);
        check(r.nebel.valleyFill > 0.8 && !Double.isNaN(r.nebel.seaBaseTarget), String.format(Locale.GERMAN, "Lage Talnebel um 5 Uhr: Kaltluft zu %.0f %% mit Nebel gefüllt, Nebelmeer %.0f–%.0f m",
                r.nebel.valleyFill * 100, r.nebel.seaBaseTarget, r.nebel.seaTopTarget));
        r.wiring.valleyHand = 0.4;
        r.step(0.01);
        check(Math.abs(r.nebel.valleyFill - 0.4) < 1e-9, "Talnebel von Hand (Block NEBEL)");
        check(r.nebelZeile().startsWith("Nebel: "), "Zeile im HUD: " + r.nebelZeile());
        r.physik.stop();
        r.windrechner.stop();
    }

    /** Phase 7: Licht im Nebel – Glorie nach der Tröpfchengröße, das Gespenst nur mit der Sonne im Rücken, Halos, Auflösung des Nebels. */
    static void licht() throws Exception {
        head("Licht im Nebel (Phase 7)");
        // DoD 1: der Ring der Glorie passt zur Tröpfchengröße (Laven 2005: erster roter Ring ≈ 24° / r)
        StringBuilder sb = new StringBuilder();
        boolean passt = true;
        double[] ringe = new double[3];
        double[] rs = {5, 10, 20};
        for (int i = 0; i < 3; i++) {
            ringe[i] = com.dan.sky.Droplets.table(rs[i], 0.05).gloryRingDeg();
            passt &= Math.abs(ringe[i] * rs[i] / 24 - 1) < 0.15;
            sb.append(i > 0 ? ", " : "").append(String.format(Locale.GERMAN, "%.0f µm %.2f° (24°/r %.2f°)", rs[i], ringe[i], 24 / rs[i]));
        }
        check(passt && ringe[0] > 3 * ringe[2], "DoD: der Ring der Glorie passt zur Tröpfchengröße – " + sb);
        Renderer r = new Renderer();
        // Tröpfchen nach der Lage
        r.wetter("Über dem Nebelmeer", 2);
        r.step(0.01);
        double rMeer = r.wiring.tropfen.radiusUm, wMeer = r.wiring.tropfen.width;
        r.wetter("Westlage mit Stau", 9);
        r.step(0.01);
        double rStau = r.wiring.tropfen.radiusUm;
        r.wiring.tropfenHand = 18;
        r.step(0.01);
        double rHand = r.wiring.tropfen.radiusUm;
        r.wiring.tropfenHand = -1;
        check(rMeer == 10 && wMeer < 0.1 && rStau == 13 && rHand == 18, String.format(Locale.GERMAN,
                "Tröpfchen nach der Lage: Nebelmeer %.0f µm (σ %.0f %%), Stau %.0f µm, von Hand %.0f µm", rMeer, wMeer * 100, rStau, rHand));
        // DoD 2: das Gespenst nur mit der Sonne im Rücken. Auf dem Gipfelstein, das Nebelmeer 3 m über dem Auge, 8:30 Uhr
        r.wetter("Über dem Nebelmeer", 2);
        r.placeView(Ansichten.byName("Gipfelstein"));
        r.step(0.01);
        double top = r.meerAmAuge(700, 3);
        double[] sp = r.clock.sun();
        double[] sun = Sun.direction(sp[0], sp[1]);
        String ja = r.gespenstText(sun);
        double[] w = r.wanderer();
        double[] anti = com.dan.sky.Droplets.antisolar(sun);
        double[] e = r.camera.eye;
        double hinten = com.dan.fog.TerrainFog.sunShadow(w, sun, Math.toRadians(0.2665), e[0] + anti[0] * 20, e[1] + anti[1] * 20, e[2] + anti[2] * 20);
        double vorn = com.dan.fog.TerrainFog.sunShadow(w, sun, Math.toRadians(0.2665), e[0] + sun[0] * 20, e[1] + sun[1] * 20, e[2] + sun[2] * 20);
        check(w != null && ja.startsWith("Gespenst: ja") && hinten < 0.05 && vorn == 1, String.format(Locale.GERMAN,
                "DoD: Sonne %.1f° im Rücken, Nebelmeer bis %.0f m – „%s“; 20 m im Gegenpunkt Schatten (%.2f), 20 m zur Sonne voll Licht (%.0f)", sp[0], top, ja, hinten, vorn));
        double[] vorSonne = Sun.direction(-3, sp[1]);
        String nacht = r.gespenstText(vorSonne);
        check(nacht.startsWith("Gespenst: nein"), "mit der Sonne unter der Kimm kein Gespenst: „" + nacht + "“");
        r.wiring.meerBasisHand = r.wiring.meerTopHand = Double.NaN;
        r.nebelJetzt();
        String tief = r.gespenstText(sun);
        check(tief.startsWith("Gespenst: nein"), "liegt das Meer tief (450–850 m), steht kein Nebel im Kernschatten: „" + tief + "“");
        r.placeView(Ansichten.byName("UeberdemNebelmeer"));
        r.step(0.01);
        check(r.wanderer() == null, "wer 375 m über Grund schwebt, wirft keinen Schatten in den Nebel (nur die Glorie bleibt)");
        // Halos: von Hand und aus dem hohen Schleier, unter einer dichten Decke verdeckt
        com.dan.sky.Sky sky = new com.dan.sky.Sky();
        r.wiring.haloHand = 0.7;
        r.wiring.feed(sky, 1142, Site.LAT, Site.LON);
        double hand = sky.halo, handP = sky.parhelia;
        r.wiring.haloHand = -1;
        r.wiring.highCover = 0.7;
        r.wiring.oktas = 2;
        r.wiring.feed(sky, 1142, Site.LAT, Site.LON);
        double schleier = sky.halo;
        r.wiring.oktas = 8;
        r.wiring.feed(sky, 300, Site.LAT, Site.LON);
        double zu = sky.halo;
        check(Math.abs(hand - 0.7) < 1e-9 && handP > 0 && schleier > 0.5 && zu < 0.05, String.format(Locale.GERMAN,
                "Halos: von Hand %.0f %% (Nebensonnen %.0f %%), hoher Schleier 70 %% → %.0f %%, unter 8/8 Decke %.0f %%", hand * 100, handP * 100, schleier * 100, zu * 100));
        // Auflösung des Nebels, Schatten der Kuppe im Nebel, Zeilen
        check(com.dan.fog.TerrainFog.Quality.values()[Quality.MITTEL.ordinal()].scale == 3 && Quality.MITTEL.summary().contains("Nebel"),
                "Mittel rechnet den Nebel in drittel Auflösung: " + Quality.MITTEL.summary());
        String sf = Shader.read("schatten.frag");
        check(sf.contains("need") && sf.contains("max(ground, need)"), "schatten.frag gibt in G die Höhe, ab der die Sonne scheint (Schatten der Kuppe im Nebel)");
        check(r.lichtZeile().startsWith("Licht im Nebel: "), "Zeile im HUD: " + r.lichtZeile());
        r.physik.stop();
        r.windrechner.stop();
    }

    /** Phase 8: Fels und Boden (Bodenkarte, Felsburgen, Blockmeer, Jahreszeit, Bodendecke, Shader). */
    static void boden() throws Exception {
        head("Fels und Boden (Phase 8)");
        Terrain t = Terrain.load(Terrain.defaultDir());
        com.dan.brocken.scene.Bodenkarte b = com.dan.brocken.scene.Bodenkarte.build(t), b2 = com.dan.brocken.scene.Bodenkarte.build(t);
        check(java.util.Arrays.equals(b.rgba(), b2.rgba()), "Bodenkarte: gleiches Gelände, gleiche Karte · " + b.describe());
        double[] an = b.anteile();
        check(an[0] > 0.005 && an[0] < 0.1 && an[2] > 0.01 && an[2] < 0.15 && an[1] > 0.001, String.format(Locale.GERMAN,
                "Anteile plausibel: Moor %.1f %% (0,5–10), Blockfeld %.1f %% (1–15), Heide %.1f %%", an[0] * 100, an[2] * 100, an[1] * 100));
        double[] gm = Site.xz(new double[]{51.8005, 10.5935}), bf = Site.xz(new double[]{51.7870, 10.5900});
        check(b.moor(gm[0], gm[1]) > 0.8 && b.moor(bf[0], bf[1]) > 0.8, String.format(Locale.GERMAN,
                "Goethemoor (%.2f) und Brockenfeld (%.2f) sind Moor", b.moor(gm[0], gm[1]), b.moor(bf[0], bf[1])));
        // auf steilen Hängen kein Moor: alle Zellen mit mehr als 20° Neigung
        double steilMoor = 0;
        int steil = 0;
        for (double x = -2900; x < 2900; x += 37)
            for (double z = -2900; z < 2900; z += 41) {
                double gx = (t.height(x + 10, z) - t.height(x - 10, z)) / 20, gz = (t.height(x, z + 10) - t.height(x, z - 10)) / 20;
                if (Math.toDegrees(Math.atan(Math.hypot(gx, gz))) > 20 && t.artAt(x, z) != Terrain.MOOR) { steil++; steilMoor = Math.max(steilMoor, b.moor(x, z)); }
            }
        check(steil > 10 && steilMoor < 0.05, String.format(Locale.GERMAN, "kein Moor an Hängen über 20° (%d Stellen, höchstens %.2f)", steil, steilMoor));
        com.dan.brocken.scene.Bodenkarte.Felsburg tk = com.dan.brocken.scene.Bodenkarte.FELSBURGEN[0], ha = com.dan.brocken.scene.Bodenkarte.FELSBURGEN[1];
        double[] ll = Site.latLon(tk.x(), tk.z());
        double dTk = Math.hypot((ll[0] - 51.79769) * 111_200, (ll[1] - 10.61625) * 111_200 * Math.cos(Math.toRadians(51.8)));
        check(tk.name().equals("Teufelskanzel") && dTk < 1 && ha.name().equals("Hexenaltar") && Math.hypot(ha.x() - tk.x(), ha.z() - tk.z()) < 25,
                String.format(Locale.GERMAN, "Teufelskanzel auf 51,79769° N 10,61625° O (± %.2f m), Hexenaltar %.0f m daneben", dTk, Math.hypot(ha.x() - tk.x(), ha.z() - tk.z())));
        check(b.heide(tk.x() + 30, tk.z()) > 0.5 && b.fels(tk.x(), tk.z()) > 0.9 && b.block(tk.x() + 12, tk.z()) > 0.3, String.format(Locale.GERMAN,
                "an der Teufelskanzel Fels (%.2f), Blöcke (%.2f) und Heide der Kuppe (%.2f)", b.fels(tk.x(), tk.z()), b.block(tk.x() + 12, tk.z()), b.heide(tk.x() + 30, tk.z())));
        // Jahreszeit: Wollgras im Juni, Heideblüte im August, rotes Heidelbeerlaub im Oktober, Winter im Januar
        double[] juni = com.dan.brocken.scene.Bodenkarte.jahr(166), aug = com.dan.brocken.scene.Bodenkarte.jahr(232),
                okt = com.dan.brocken.scene.Bodenkarte.jahr(282), jan = com.dan.brocken.scene.Bodenkarte.jahr(15);
        check(juni[0] > 0.9 && aug[0] < 0.01 && aug[1] > 0.9 && juni[1] < 0.01 && okt[2] > 0.8 && aug[2] < 0.01 && jan[3] > 0.99 && aug[3] < 0.01,
                "Jahreszeit des Bodens: Wollgras im Juni, Besenheide im August, Heidelbeere rot im Oktober, braun im Winter");
        // Renderer: Felsburgen auf dem Boden, Blockmeer um die Teufelskanzel, Bodendecke
        Renderer r = new Renderer();
        com.dan.cliff.Tor tor = r.fels.burgen.get(0);
        double fuss = t.height(tor.x, tor.z), hoehe = tor.top() - fuss;
        check(Math.abs(tor.y - fuss) < 0.01 && Math.abs(hoehe / tk.hoehe() - 1) < 0.15 && r.fels.burgen.size() == 2, String.format(Locale.GERMAN,
                "Teufelskanzel steht auf dem Gelände (%.1f m über NHN), %.1f m hoch (soll %.1f)", fuss, hoehe, tk.hoehe()));
        float[] inst = r.fels.blockmeer.instances(tk.x(), tk.z(), 1e-3);
        int n = r.fels.blockmeer.count(), ohne = 0;
        for (int i = 0; i < n; i++) if (b.block(inst[i * 8], inst[i * 8 + 2]) <= 0) ohne++;
        float[] inMoor = r.fels.blockmeer.instances(gm[0], gm[1], 1e-3);
        int nMoor = 0;
        for (int i = 0; i < r.fels.blockmeer.count(); i++) if (Math.hypot(inMoor[i * 8] - gm[0], inMoor[i * 8 + 2] - gm[1]) < 60) nMoor++;
        check(n > 200 && ohne == 0 && nMoor == 0, String.format(Locale.GERMAN, "Blockmeer: %,d Blöcke um die Teufelskanzel, alle auf Blockfeld; im Goethemoor keiner", n));
        float[] o = new float[5];
        Leben.bedeckung(b, t, gm[0], gm[1], o);
        float moorNass = o[4], moorBlumen = o[1];
        Leben.bedeckung(b, t, tk.x() + 30, tk.z(), o);
        check(moorNass > 0.9 && moorBlumen > 0.7 && o[4] < 0.3 && o[0] > 0.8, String.format(Locale.GERMAN,
                "Bodendecke: im Moor nass (%.2f) mit Wollgras (%.2f), in der Heide trocken (%.2f) und dicht (%.2f)", moorNass, moorBlumen, o[4], o[0]));
        check(Math.abs(r.boden.moor(gm[0], gm[1]) - b.moor(gm[0], gm[1])) < 1e-9 && r.leben.boden.biome.name.contains("Brocken"),
                "der Renderer nimmt dieselbe Karte und das Biom Brocken (" + r.leben.boden.biome.name + ")");
        // die Ansichten von Phase 8 sehen, was sie sollen
        Ansichten.Ansicht av = Ansichten.byName("Teufelskanzel");
        double dx = tk.x() - av.x(), dz = tk.z() - av.z(), hdg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
        check(Math.abs(((hdg - av.heading()) + 540) % 360 - 180) < 15 && Math.hypot(dx, dz) > 15 && Math.hypot(dx, dz) < 40, String.format(Locale.GERMAN,
                "Ansicht Teufelskanzel: %.0f m vor der Felsburg, Blick %.0f° (zur Burg %.0f°)", Math.hypot(dx, dz), av.heading(), hdg));
        Ansichten.Ansicht am = Ansichten.byName("Goethemoor");
        check(b.moor(am.x(), am.z()) > 0.8, String.format(Locale.GERMAN, "Ansicht Goethemoor steht im Moor (%.2f)", b.moor(am.x(), am.z())));
        String tf = Shader.source("terrain.frag", null).text;
        check(tf.contains("uBoden") && tf.contains("cliffMaterial") && tf.contains("uBodenJahr"), "terrain.frag malt den Boden nach der Karte und den Fels aus cliff.glsl");
        check(Renderer.PASSES[3].equals("Fels"), "eigener Durchgang „Fels“ in der Zeitmessung");
        check(r.bodenZeile().startsWith("Boden "), "Zeile im HUD: " + r.bodenZeile());
        r.physik.stop();
        r.windrechner.stop();
    }

    /** Phase 9: der Wald (Waldkarte, Baumgrenze, Totholz, Windwurf, Windflüchter, Shader). */
    static void wald() throws Exception {
        head("Wald (Phase 9)");
        Terrain t = Terrain.load(Terrain.defaultDir());
        com.dan.brocken.scene.Bodenkarte b = com.dan.brocken.scene.Bodenkarte.build(t);
        com.dan.brocken.scene.Waldkarte k = com.dan.brocken.scene.Waldkarte.build(t, b), k2 = com.dan.brocken.scene.Waldkarte.build(t, b);
        check(java.util.Arrays.equals(k.rgba(), k2.rgba()), "Waldkarte: gleiches Gelände, gleiche Karte · " + k.describe());
        double tot = k.totImWald();
        check(tot > 0.8 && tot < 0.95, String.format(Locale.GERMAN, "DoD Totholz: %.0f %% der Fichten tot (Nationalpark Harz 2023: rund 90 %% des ursprünglichen Fichtenbestands)", tot * 100));
        // Baumgrenze: Bäume je Platz nach der Höhe (Summe der Dichten aller Arten), in Bändern zu 20 m über den Kern
        Renderer r = new Renderer();
        double[] sum = new double[80];
        int[] cnt = new int[80];
        for (double x = -1500; x < 1500; x += 23)
            for (double z = -1500; z < 1500; z += 29) {
                double h = t.height(x, z);
                int band = (int) ((h - 700) / 20);
                if (band < 0 || band >= sum.length) continue;
                double d = 0;
                for (int s = 0; s < com.dan.brocken.gl.Wald.ARTEN.size(); s++) d += r.wald.dichte(x, z, s);
                sum[band] += d;
                cnt[band]++;
            }
        double grenze = 0, unten = 0;
        int nu = 0;
        for (int i = 0; i < sum.length; i++) {
            if (cnt[i] < 5) continue;
            double m = sum[i] / cnt[i];
            if (m > 0.05) grenze = 700 + (i + 1) * 20;
            if (700 + i * 20 < 950) { unten += m; nu++; }
        }
        check(grenze >= 1080 && grenze <= 1140 && unten / nu > 0.6, String.format(Locale.GERMAN,
                "DoD Baumgrenze bei %.0f m (rund 1 100 m [G]): darüber unter 5 %% der Plätze bestockt, unter 950 m im Mittel %.0f %%", grenze, 100 * unten / nu));
        // Dichten: nie mehr als ein Baum je Platz
        double worst = 0;
        for (double x = -6000; x < 6000; x += 97)
            for (double z = -5000; z < 5500; z += 103) {
                double d = 0;
                for (int s = 0; s < com.dan.brocken.gl.Wald.ARTEN.size(); s++) d += r.wald.dichte(x, z, s);
                worst = Math.max(worst, d);
            }
        check(worst <= 1.0001, String.format(Locale.GERMAN, "höchstens ein Baum je Platz (größte Summe der Dichten %.3f)", worst));
        // in der Ansicht Totholz: tote Fichten, liegende nach Nordost, Windflüchter ungedreht
        Ansichten.Ansicht av = Ansichten.byName("Totholz");
        check(k.cover(av.x(), av.z()) > 0.9 && k.dead(av.x(), av.z()) > 0.9, String.format(Locale.GERMAN, "Ansicht Totholz: Wald %.0f %%, tot %.0f %%",
                100 * k.cover(av.x(), av.z()), 100 * k.dead(av.x(), av.z())));
        com.dan.forest.ForestField f = r.wald.feld;
        double[] eye = {av.x(), t.height(av.x(), av.z()) + 1.7, av.z()};
        f.completeNow(eye);
        f.update(eye, null, 0);
        int[] n = new int[com.dan.brocken.gl.Wald.ARTEN.size()];
        double yawWorst = 0;
        int fl = 0;
        double flagWorst = 0;
        for (int bk = 0; bk < f.buckets(); bk++) {
            int s = f.speciesOf(bk);
            n[s] += f.count[bk];
            for (int i = 0; i < f.count[bk]; i++) {
                double yw = f.bucket[bk][i * com.dan.forest.ForestField.STRIDE + 3];
                if (s == com.dan.brocken.gl.Wald.LIEGEND) {
                    double az = Math.toDegrees(Math.atan2(Math.cos(yw), -Math.sin(yw)));
                    yawWorst = Math.max(yawWorst, Math.abs(((az - (com.dan.brocken.scene.Waldkarte.STURM_AUS - 180)) + 540) % 360 - 180));
                }
                if (s == com.dan.brocken.gl.Wald.FLAGGE) { fl++; flagWorst = Math.max(flagWorst, Math.abs(Math.toDegrees(yw))); }
            }
        }
        int fichten = n[0] + n[1] + n[2] + n[3] + n[4], tote = n[1] + n[2] + n[3] + n[4];
        check(fichten > 1000 && tote > 0.8 * fichten, String.format(Locale.GERMAN, "um die Ansicht %,d alte Fichten, davon %.0f %% tot (%,d grau, %,d gebrochen, %,d liegend, %,d rot); %,d junge, %,d Ebereschen",
                fichten, 100.0 * tote / Math.max(1, fichten), n[2], n[3], n[4], n[1], n[5], n[7]));
        check(n[4] > 50 && yawWorst <= 35.01, String.format(Locale.GERMAN, "liegende Stämme nach %03.0f° (Sturm aus %03.0f°), größte Abweichung %.1f°",
                com.dan.brocken.scene.Waldkarte.STURM_AUS - 180, com.dan.brocken.scene.Waldkarte.STURM_AUS, yawWorst));
        check(fl > 20 && flagWorst <= 10.01, String.format(Locale.GERMAN, "%d Windflüchter im Umkreis, alle ungedreht (± %.1f°): die Fahne zeigt nach Lee", fl, flagWorst));
        String tf = Shader.source("terrain.frag", null).text;
        check(tf.contains("uWald") && tf.contains("uWaldNah"), "terrain.frag: Waldboden nah, Kronen von oben fern (Waldkarte)");
        check(Renderer.PASSES[4].equals("Wald"), "eigener Durchgang „Wald“ in der Zeitmessung");
        check(r.wald.zeile(av.x(), av.z()).contains("tot"), "Zeile im HUD: " + r.wald.zeile(av.x(), av.z()));
        r.physik.stop();
        r.windrechner.stop();
    }

    static void raueis() throws Exception {
        head("Raueis (Phase 10)");
        com.dan.weather.Scenario sc = com.dan.brocken.wiring.KlimaBrocken.raueis();
        double t0 = java.time.ZonedDateTime.of(2027, 1, 14, 22, 0, 0, 0, java.time.ZoneId.of("Europe/Berlin")).toEpochSecond();
        com.dan.brocken.wiring.Raueis ri = new com.dan.brocken.wiring.Raueis(), r2 = new com.dan.brocken.wiring.Raueis();
        java.util.function.DoubleUnaryOperator sonne = tt -> com.dan.brocken.scene.Sun.position(tt * 1000, Site.LAT, Site.LON)[0];
        // Regie bis zum Ende der Raueislage (4 h Stau, 12 h Raueis): die Richtung des Winds dann
        com.dan.weather.Weather w = new com.dan.weather.Weather();
        com.dan.brocken.wiring.KlimaBrocken.setzen(w);
        w.start(sc, t0);
        com.dan.weather.Weather.Snapshot sn = null;
        for (double tt = t0; tt <= t0 + 15.9 * 3600; tt += 600) sn = w.update(tt, 0);
        int schritte = ri.vorlauf(sc, t0, t0 + 15.9 * 3600, 1141, 26, sonne);
        r2.vorlauf(sc, t0, t0 + 15.9 * 3600, 1141, 26, sonne);
        int oben = com.dan.brocken.wiring.Raueis.BAENDER - 1, z = com.dan.brocken.wiring.Raueis.ZWEIG;
        com.dan.rime.Rime.Ansatz a = ri.ansatz[oben][z];
        double abw = Math.abs(((a.richtung - sn.fromDeg()) + 540) % 360 - 180);
        check(a.art == com.dan.rime.Rime.Art.RAUEIS && a.dicke > 0.03 && abw < 15, String.format(Locale.GERMAN,
                "DoD: nach 16 h Raueislage wachsen die Fahnen am Gipfel gegen den Wind: %s (Wind aus %03.0f°, %.0f° daneben; %d Schritte)", a, sn.fromDeg(), abw, schritte));
        double unten = ri.dicke(z, 650), mitte = ri.dicke(z, 900), gipfel = ri.dicke(z, 1141);
        check(unten < 0.005 && mitte > unten && gipfel > mitte, String.format(Locale.GERMAN,
                "mehr Eis mit der Höhe (Wolkenbasis rund 800 m): 650 m %.1f cm, 900 m %.1f cm, Gipfel %.1f cm", unten * 100, mitte * 100, gipfel * 100));
        double[] band = ri.band(z);
        check(band[0] > 650 && band[0] < 900 && band[1] > band[0], String.format(Locale.GERMAN, "Band für Wald und Fels: ab %.0f m, voll ab %.0f m", band[0], band[1]));
        check(a.dicke == r2.ansatz[oben][z].dicke, "gleicher Lauf, gleiches Eis");
        double last = ri.last(1141);
        check(last > 20 && last < 1000, String.format(Locale.GERMAN, "Last an einer Krüppelfichte am Gipfel: %.0f kg (60 m Zweige)", last));
        // Raten: am Gipfel in der Raueislage
        com.dan.weather.Weather w2 = new com.dan.weather.Weather();
        com.dan.brocken.wiring.KlimaBrocken.setzen(w2);
        w2.start(sc, t0);
        com.dan.weather.Weather.Snapshot mitten = null;
        for (double tt = t0; tt <= t0 + 10 * 3600; tt += 600) mitten = w2.update(tt, 0);
        StringBuilder tab = new StringBuilder();
        for (int k = 0; k < 3; k++)
            tab.append(k > 0 ? ", " : "").append(com.dan.brocken.wiring.Raueis.NAMEN[k]).append(String.format(Locale.GERMAN, " %.1f mm/h",
                    com.dan.rime.Rime.mmJeStunde(com.dan.brocken.wiring.Raueis.DURCHMESSER[k], true, com.dan.brocken.wiring.Raueis.lage(mitten, 1141, 26, 0))));
        double rate = com.dan.brocken.wiring.Raueis.rateGipfel(mitten, 26, 0);
        check(mitten.phase() == com.dan.weather.Weather.Phase.RAUEIS && rate > 2 && rate < 30, String.format(Locale.GERMAN,
                "Raten am Gipfel um 8 Uhr (%s): %s", com.dan.brocken.wiring.Raueis.lage(mitten, 1141, 26, 0), tab));
        // Sommer: kein Eis
        com.dan.brocken.wiring.Raueis so = new com.dan.brocken.wiring.Raueis();
        double s0 = java.time.ZonedDateTime.of(2026, 7, 10, 6, 0, 0, 0, java.time.ZoneId.of("Europe/Berlin")).toEpochSecond();
        so.vorlauf(com.dan.brocken.wiring.KlimaBrocken.sommergewitter(), s0, s0 + 17 * 3600, 1141, 20, sonne);
        check(so.art() == com.dan.rime.Rime.Art.KEINS, "ein Sommertag mit Gewitter: kein Eis (" + so.zeile(null, 20, 30) + ")");
        // Die Demo zeigt die Raten (DoD): RimeDemo --bild in einem eigenen Prozess
        File bild = File.createTempFile("rime", ".png");
        ProcessBuilder pb = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(), "-cp", System.getProperty("java.class.path"),
                "-Djava.awt.headless=true", "-Dstdout.encoding=UTF-8", "com.dan.rime.demo.RimeDemo", "--bild", bild.getPath(), "8");
        pb.redirectErrorStream(true);
        Process pr = pb.start();
        String out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean ok = pr.waitFor() == 0 && bild.length() > 10_000;
        String src = new String(com.dan.rime.demo.RimeDemo.class.getResourceAsStream("RimeDemo.class").readAllBytes(), StandardCharsets.ISO_8859_1);
        check(ok && src.contains("Raten (mm/h)"), "DoD: RimeDemo zeigt die Raten (mm/h nach Durchmesser und Wind, α1) · Standbild " + bild.length() / 1024 + " KiB" + (ok ? "" : " · " + out.trim()));
        bild.delete();
        String tf = Shader.source("terrain.frag", null).text;
        check(tf.contains("/com/dan/rime/gl/rime.glsl") && tf.contains("uRaueisZweig"), "terrain.frag: Raueis nach Höhenbändern aus rime.glsl");
        Renderer r = new Renderer();
        r.raueisJetzt();
        check(r.raueisZeile().startsWith("Raueis") || r.raueisZeile().startsWith("Raureif") || r.raueisZeile().startsWith("Klareis"), "Zeile im HUD: " + r.raueisZeile());
        r.physik.stop();
        r.windrechner.stop();
    }

    static void jahr() throws Exception {
        head("Das Jahr (Phase 11)");
        // DoD: am Gipfel liegt Schnee von September bis Mai – 30 gewürfelte Winter in den Stufen der App
        int n = 30, hoch = com.dan.brocken.wiring.KlimaBrocken.STUFEN.length - 1;
        int[] erste = new int[n], letzte = new int[n], leeLetzte = new int[n];
        int leeMai = 0;
        double tage = 0, schierke5 = 0, maxH = 0;
        int sepOkt = 0, mai = 0;
        for (int k = 0; k < n; k++) {
            com.dan.season.Heights hs = com.dan.brocken.wiring.KlimaBrocken.jahr();
            com.dan.season.Year oben = hs.years[hoch], unten = hs.years[4];   // 1 150 m und 600 m
            oben.seed = unten.seed = 100 + k;
            java.util.List<com.dan.season.Year.State> lo = oben.trace(2027, 7), lu = unten.trace(2027, 7);
            int f = 999, l = -999, c = 0, c5 = 0;
            boolean so = false, ma = false;
            double mx = 0;
            for (int i = 0; i < lo.size(); i++) {
                int d = com.dan.season.Year.FIRST + i;
                if (d > 243) break;   // ein Winter: 1. September bis 31. August
                double s1 = lo.get(i).snowDepth();
                mx = Math.max(mx, s1);
                if (s1 >= 0.01) { c++; if (f == 999) f = d; l = d; if (d <= -61) so = true; if (d >= 121 && d <= 151) ma = true; }
                if (lu.get(i).snowDepth() >= 0.05) c5++;
            }
            erste[k] = f; letzte[k] = l; tage += c; schierke5 += c5; maxH += mx;
            // dieselben Winter in einer Mulde am Gipfel (der Wind legt die doppelte Menge hinein)
            com.dan.season.Year lee = com.dan.brocken.wiring.KlimaBrocken.gipfelJahr(2);
            lee.seed = 100 + k;
            java.util.List<com.dan.season.Year.State> ll = lee.trace(2027, 7);
            int le = -999;
            boolean lm = false;
            for (int i = 0; i < ll.size(); i++) {
                int dd = com.dan.season.Year.FIRST + i;
                if (dd > 243) break;
                if (ll.get(i).snowDepth() >= 0.01) { le = dd; if (dd >= 121 && dd <= 151) lm = true; }
            }
            leeLetzte[k] = le;
            if (lm) leeMai++;
            if (so) sepOkt++;
            if (ma) mai++;
        }
        java.util.Arrays.sort(erste);
        java.util.Arrays.sort(letzte);
        tage /= n; schierke5 /= n; maxH /= n;
        java.util.Arrays.sort(leeLetzte);
        check(erste[n / 2] <= -61 && sepOkt >= n / 2 && leeLetzte[n / 2] >= 121 && leeMai >= 2 * n / 3, String.format(Locale.GERMAN,
                "DoD: am Gipfel liegt Schnee von September bis Mai – der erste (Median) am %s, in %d von %d Wintern schon im September oder Oktober; "
                        + "auf freier Fläche der letzte am %s (in %d Wintern im Mai), in Mulden und Leehängen (Verwehung ×2) am %s, in %d Wintern im Mai",
                tag(erste[n / 2]), sepOkt, n, tag(letzte[n / 2]), mai, tag(leeLetzte[n / 2]), leeMai));
        check(tage > 130 && tage < 190 && maxH > 0.8, String.format(Locale.GERMAN,
                "am Gipfel %.0f Tage mit Schnee je Winter (DWD 1961–1990: 178, seither wärmer), höchste Schneehöhe im Mittel %.0f cm", tage, maxH * 100));
        check(schierke5 > 40 && schierke5 < 115, String.format(Locale.GERMAN,
                "in 600 m (Schierke) %.0f Tage mit mindestens 5 cm (LAU Sachsen-Anhalt: 1961–1990 rund 114, 2001–2025 rund 66)", schierke5));
        com.dan.season.Heights hs = com.dan.brocken.wiring.KlimaBrocken.jahr();
        hs.settle(2027, com.dan.season.Year.dayOfYear(2, 10), 12);
        check(hs.snowDepth(1141) > 1 && hs.snowDepth(1141) > hs.snowDepth(900) && hs.snowDepth(900) > hs.snowDepth(600),
                "der Winter der App (Startwert 7) am 10. Februar 2027: " + hs.describe(600, 900, 1141));
        // Verwehungen und Wechten über dem Gelände
        Renderer r = new Renderer();
        com.dan.season.Drift d = r.verwehung;
        double[] sh = d.shares();
        double gipfel = d.factorAt(0, 0), mean = 0;
        for (float v : d.factor) mean += v;
        mean /= d.factor.length;
        int wechtenKern = 0;
        for (int j = 0; j < d.nz; j++)
            for (int i = 0; i < d.nx; i++) {
                double x = d.x0 + (i + 0.5) * d.cell, z = d.z0 + (j + 0.5) * d.cell;
                if (Site.KERN.contains(x, z) && d.cornice[j * d.nx + i] > 0.5) wechtenKern++;
            }
        check(gipfel < 1 && sh[0] > 0.03 && sh[1] > 0.01 && Math.abs(mean - 1) < 1e-3 && sh[2] > 0, String.format(Locale.GERMAN,
                "Verwehung: am Gipfel ×%.2f (die Kuppe wird abgeblasen), Mittel %.3f, %d Zellen mit Wechte im Kern · %s", gipfel, mean, wechtenKern, d.describe()));
        r.jahrJetzt();
        check(r.jahrZeile().contains("Schnee"), "Zeile im HUD: " + r.jahrZeile());
        check(Math.abs(r.wiring.weather.anomaly - r.wiring.jahr.years[hoch].anomaly(r.wiring.datum()[0], r.wiring.datum()[1])) < 1e-9,
                String.format(Locale.GERMAN, "die Regie folgt dem gewürfelten Tag (Abweichung %.1f K)", r.wiring.weather.anomaly));
        String tf = Shader.source("terrain.frag", null).text;
        check(tf.contains("uSchnee[16]") && tf.contains("uDrift") && tf.contains("uBodenJahrUnten"), "terrain.frag: Schnee je Stufe, Verwehung und Wechten, Heide unten früher");
        r.physik.stop();
        r.windrechner.stop();
    }

    static void bahn() throws Exception {
        head("Die Bahn (Phase 12)");
        Terrain t = Terrain.load(Terrain.defaultDir());
        com.dan.brocken.scene.Strecke st = com.dan.brocken.scene.Strecke.build(t);
        check(st != null, "Strecke aus dem ALKIS-Auszug (Nutzung „Bahnverkehr“, Empfangsgebäude): " + (st == null ? "fehlt" : st.trasse.describe()));
        if (st == null) return;
        com.dan.rail.Track g = st.gleis;
        double steig = g.maxGradient(10), um = Math.abs(st.umlaeufe());
        check(steig <= 0.0331, String.format(Locale.GERMAN, "DoD: die Steigung liegt nirgends über 3,3 %% (größte über 10 m: %.2f %%; Brockenbahn: 33 ‰)", steig * 100));
        check(um > 1.3 && um < 1.7, String.format(Locale.GERMAN, "DoD: die Spirale läuft %.2f-mal um den Gipfel (rund 1½)", um));
        check(g.length() > 14_000 && g.length() < 19_000, String.format(Locale.GERMAN, "Länge im Hauptraster %.1f km (Drei Annen Hohne – Brocken 19 km)", g.length() / 1000));
        StringBuilder bh = new StringBuilder();
        boolean beide = g.stations.size() >= 2 && g.stations.get(0).name().equals("Schierke") && g.stations.get(g.stations.size() - 1).name().equals("Brocken"), eben = true;
        for (com.dan.rail.Track.Station s : g.stations) {
            double gr = Math.abs(g.at(s.s1() - 1).y() - g.at(s.s0() + 1).y()) / (s.length() - 2);
            if (s.side() != 0) eben &= gr <= 0.0045;   // seit Phase 14: Goetheweg (ohne Bahnsteig) steigt mit der Strecke
            bh.append(bh.length() > 0 ? ", " : "").append(String.format(Locale.GERMAN, "%s bei %.1f km in %.0f m (%.1f ‰)", s.name(), s.s() / 1000, g.at(s.s()).y(), gr * 1000));
        }
        check(beide && eben, "Bahnhöfe Schierke und Brocken eben, (seit Phase 13) Goetheweg: " + bh);
        check(!st.uebergaenge.isEmpty(), String.format(Locale.GERMAN, "%d Bahnübergänge mit Straßen (Brockenstraße)", st.uebergaenge.size()));
        // Gelände mit Bahnkörper: auf dem Planum genau, neben dem Gleis unverändert
        t.korrektur = st.korrektur();
        double fehler = 0;
        for (double s = 100; s < g.length(); s += 997) {
            com.dan.rail.Track.Pose p = g.at(s);
            fehler = Math.max(fehler, Math.abs(t.height(p.x(), p.z()) - (p.y() - st.bett.depth)));
        }
        com.dan.rail.Track.Pose p0 = g.at(5000);
        double weit = Math.abs(t.height(p0.x() + 400, p0.z() + 400) - t.roh(p0.x() + 400, p0.z() + 400));
        double[] ab = st.abweichung();
        check(fehler < 0.15 && weit == 0, String.format(Locale.GERMAN, "Bahnkörper im Gelände: das Planum liegt auf %.2f m genau, 400 m daneben unverändert; "
                + "Höhenmodell an der Achse %.1f bis +%.1f m gegen die Gradiente · %s", fehler, ab[0], ab[1], st.bett.describe()));
        // am Gleis keine Bäume, kein Gras auf dem Schotter
        Renderer r = new Renderer();
        com.dan.rail.Track.Pose q = r.strecke.gleis.at(8000);
        double baum = 0;
        for (int sp = 0; sp < com.dan.brocken.gl.Wald.ARTEN.size(); sp++) baum += r.wald.dichte(q.x() + 1, q.z(), sp);
        check(baum == 0 && r.strecke.abstand(q.x(), q.z()) < 2, "auf dem Gleis keine Bäume (Schutzstreifen 6 m)");
        check(Renderer.PASSES[5].equals("Bahn") && r.bahnZeile().contains("Umläufe"), "eigener Durchgang „Bahn“, Zeile im HUD: " + r.bahnZeile());
        String th = Shader.source("terrain.frag", null).text;
        check(th.contains("uTrasse") && th.contains("uTrasseMaske"), "Gelände-Shader: Damm und Einschnitt (Höhe), Schotter und Planum (Farbe)");
        r.physik.stop();
        r.windrechner.stop();
    }

    /** Phase 13: Züge nach Fahrplan, Kreuzungen, Dampf im Wind, Mitfahrt. */
    static void zug() throws Exception {
        head("Die Züge (Phase 13)");
        Terrain t = Terrain.load(Terrain.defaultDir());
        com.dan.brocken.scene.Strecke st = com.dan.brocken.scene.Strecke.build(t);
        if (st == null) { check(false, "keine Strecke"); return; }
        double hg = st.sGoetheweg > 0 ? st.gleis.at(st.sGoetheweg).y() : 0;
        check(st.sGoetheweg > st.sSchierke && Math.abs(hg - 956) < 5 && st.ausweichen.size() == 2, String.format(Locale.GERMAN,
                "Kreuzungsbahnhof Goetheweg bei %.1f km in %.0f m (Wikipedia: 956 m), Ausweichgleise in Schierke und Goetheweg", st.sGoetheweg / 1000, hg));
        com.dan.brocken.scene.Zugbetrieb z = new com.dan.brocken.scene.Zugbetrieb(st);
        // der ganze Tag in Schritten zu 10 s: keine zwei Züge auf demselben Gleis an derselben Stelle
        int kollision = 0, maxZuege = 0;
        boolean dampfMitWind = false, dampfHinten = false, pfiff = false;
        String dampfText = "", hintenText = "";
        for (double sek = 8 * 3600; sek <= 20 * 3600; sek += 10) {
            z.step(1, sek);
            java.util.List<com.dan.rail.Train> l = z.zuege;
            maxZuege = Math.max(maxZuege, l.size());
            for (int i = 0; i < l.size(); i++)
                for (int j = i + 1; j < l.size(); j++) {
                    com.dan.rail.Train p = l.get(i), q = l.get(j);
                    double p0 = Math.min(p.s, p.s - p.richtung * p.laenge), p1 = Math.max(p.s, p.s - p.richtung * p.laenge);
                    double q0 = Math.min(q.s, q.s - q.richtung * q.laenge), q1 = Math.max(q.s, q.s - q.richtung * q.laenge);
                    double op = p.versatz == null ? 0 : p.versatz.applyAsDouble((p0 + p1) / 2), oq = q.versatz == null ? 0 : q.versatz.applyAsDouble((q0 + q1) / 2);
                    if (p0 < q1 && q0 < p1 && Math.abs(op - oq) < 2.5) kollision++;
                }
            pfiff |= !z.meldung().isEmpty();
            // DoD: die Fahne mit dem Wind – am stehenden Zug in Schierke (10:03) und hinter dem fahrenden (10:20, schwacher Wind)
            if (sek == 10 * 3600 + 180 || sek == 10 * 3600 + 1200) {
                boolean steht = sek < 10 * 3600 + 600;
                double[] wind = steht ? new double[]{4, 0, -3} : new double[]{0.5, 0, 0};
                com.dan.smoke.Smoke sm = new com.dan.smoke.Smoke();
                sm.setSources(z.dampf());
                sm.wind = (x, y, zz) -> z.windRelativ(wind, x, y, zz);
                if (sm.sources().isEmpty()) continue;
                com.dan.smoke.Source src = sm.sources().get(0);
                double[] w = sm.windFor(src);
                float[] c = com.dan.smoke.Plume.constants(src, w[0], w[1], w[2], 0, 40, src.life(), 1, 0);
                double[] m = com.dan.smoke.Plume.center(c, 20);
                double dx = m[0] - src.x(), dz = m[2] - src.z(), d = Math.hypot(dx, dz);
                com.dan.rail.Train zz = l.get(0);
                com.dan.rail.Track.Pose po = st.gleis.at(zz.s);
                double hd = Math.toRadians(po.heading()), fx = Math.sin(hd) * zz.richtung, fz = -Math.cos(hd) * zz.richtung;
                if (steht) {
                    double cw = (dx * 4 - dz * 3) / (d * 5);
                    dampfMitWind = zz.v == 0 && cw > 0.98;
                    dampfText = String.format(Locale.GERMAN, "%s steht (%s), Wind 5 m/s nach 053°: die Fahne zieht nach %03.0f° (cos %.3f), nach 20 s %.0f m weit, %.0f m hoch",
                            zz.name, zz.zustand(), (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360, cw, d, m[1] - src.y());
                } else {
                    double back = -(dx * fx + dz * fz) / d;
                    dampfHinten = zz.v > 3 && back > 0.9;
                    hintenText = String.format(Locale.GERMAN, "fährt %.0f km/h bei 0,5 m/s Wind: die Fahne liegt hinter der Lok (cos %.2f), %s", zz.v * 3.6, back, src.black() > 0.2 ? "dunkel (volle Kraft)" : "hell");
                }
            }
        }
        check(kollision == 0 && maxZuege >= 3, String.format(Locale.GERMAN, "Eingleisig mit Kreuzungen: bis %d Züge zugleich, nie zwei auf demselben Gleis an derselben Stelle", maxZuege));
        StringBuilder sb = new StringBuilder();
        boolean alle = z.ankuenfte.size() == com.dan.brocken.scene.Fahrplan.bergfahrten().size(), dod = false;
        int puenktlich = 0;
        for (double[] a : z.ankuenfte) {
            double plan = a[1] - a[0], ist = a[3] - a[2];
            boolean ok = Math.abs(a[2] - a[0]) < 90 && a[3] - a[1] < 3 * 60;
            if (ok) puenktlich++;
            if (a[0] == 10 * 3600 + 6 * 60) dod = Math.abs(ist - plan) <= 0.1 * plan && Math.abs(a[2] - a[0]) < 90;
            sb.append(sb.length() > 0 ? " · " : "").append(String.format(Locale.GERMAN, "%s→%s ist %.1f min", com.dan.brocken.scene.Fahrplan.uhr(a[0]), com.dan.brocken.scene.Fahrplan.uhr(a[3]), ist / 60));
        }
        check(dod, "DoD: der Zug ab Schierke 10:06 ist in der Zeit des Fahrplans am Gipfel (33 min, ±10 %): " + (z.ankuenfte.isEmpty() ? "keiner" : sb.toString().split(" · ")[0]));
        check(alle && puenktlich == z.ankuenfte.size(), String.format(Locale.GERMAN, "alle %d Bergfahrten pünktlich ab Schierke und höchstens 3 min nach Plan am Brocken: %s", z.ankuenfte.size(), sb));
        check(dampfMitWind, "DoD: die Fahne geht mit dem Wind – " + dampfText);
        check(dampfHinten, "Dampf der fahrenden Lok: " + hintenText);
        check(pfiff, "Pfiffe beim Abfahren und vor den Bahnübergängen als Meldung");
        // Sprung der Uhr: von 8 Uhr nachgerechnet, so schnell, dass ein Bild kaum stockt
        com.dan.brocken.scene.Zugbetrieb z2 = new com.dan.brocken.scene.Zugbetrieb(st);
        long t0 = System.nanoTime();
        z2.step(1, 15 * 3600);
        double ms = (System.nanoTime() - t0) / 1e6;
        check(ms < 1500 && !z2.zuege.isEmpty(), String.format(Locale.GERMAN, "Sprung der Uhr auf 15:00 nachgerechnet in %.0f ms: %s", ms, z2.describe()));
        // Mitfahrt: Auge rechts neben dem Führerhaus, über der Schiene
        com.dan.rail.Train m = z2.mitfahrZug(0, 0);
        double[] e = m == null ? null : z2.fuehrerstand(m);
        double ab = e == null ? 0 : st.abstand(e[0], e[2]);
        check(e != null && ab > 1.2 && ab < 6 && e[1] > st.gleis.at(m.s).y() + 2, String.format(Locale.GERMAN, "Mitfahrt: auf der Lok von %s, %.1f m neben der Achse, Blick nach %03.0f°", m == null ? "–" : m.name, ab, e == null ? 0 : e[3]));
        String sh = Shader.source("terrain.frag", null).text;
        check(com.dan.rail.gl.RailGl.source("train.vert").contains("uModel") && com.dan.rail.gl.RailGl.source("train.frag").contains("uLicht") && sh != null,
                "Shader der Züge (train.vert, train.frag) im Renderer.jar");
    }

    /** Phase 14: Bauten am Gipfel, eben im Gelände, Bahnhof auf 1 125 m, Menschen nach Andrang, Ansicht vom Bahnsteig. */
    static void gipfel() throws Exception {
        head("Gipfel und Menschen (Phase 14)");
        Terrain t = Terrain.load(Terrain.defaultDir());
        com.dan.brocken.scene.Strecke st = com.dan.brocken.scene.Strecke.build(t);
        if (st == null) { check(false, "keine Strecke"); return; }
        t.korrektur = st.korrektur();
        com.dan.brocken.scene.Gipfel g = com.dan.brocken.scene.Gipfel.build(t);
        g.einebnen(st);
        t.korrektur = st.korrektur();
        StringBuilder namen = new StringBuilder();
        boolean alle = true;
        for (String n : new String[]{"Brockenhotel", "Brockenhaus", "Wolkenh", "Bahnhof Brocken", "Wetter"}) {
            boolean da = false;
            for (com.dan.brocken.scene.Gipfel.Bau b : g.bauten) da |= b.name.contains(n);
            alle &= da;
        }
        for (com.dan.brocken.scene.Gipfel.Bau b : g.bauten) if (!b.name.equals(b.art)) namen.append(namen.length() > 0 ? ", " : "").append(b.name);
        check(alle && g.bauten.size() >= 10, String.format(Locale.GERMAN, "%d Bauten aus den ALKIS-Grundrissen am Gipfel, darunter %s und die Wetterstation", g.bauten.size(), namen));
        com.dan.brocken.scene.Gipfel.Teil sender = null, radom = null, kuppel = null;
        for (com.dan.brocken.scene.Gipfel.Teil p : g.teile) {
            if (p.art().equals("sendeturm")) sender = p;
            if (p.art().equals("radom")) radom = p;
            if (p.art().equals("kuppel")) kuppel = p;
        }
        double ds = sender == null ? 0 : Math.hypot(sender.x() - g.stein[0], sender.z() - g.stein[2]);
        double richtung = sender == null ? 0 : (Math.toDegrees(Math.atan2(sender.x() - g.stein[0], -(sender.z() - g.stein[2]))) + 360) % 360;
        check(sender != null && sender.b() == 168 && ds > 100 && ds < 200 && richtung > 270 && richtung < 360, String.format(Locale.GERMAN,
                "Sendeturm 168 m (Wikipedia: Sockel 53 m, Rohr seit 2007 bis 168 m) %.0f m vom Gipfelstein nach %03.0f° (Wikipedia: der Gipfel liegt rund 130 m südöstlich)", ds, richtung));
        check(radom != null && kuppel != null, "alter Fernsehturm des Brockenhotels mit Radom, Kuppel auf dem Brockenhaus");
        // eben unter den Bauten
        double schief = 0;
        String schiefster = "";
        for (com.dan.brocken.scene.Gipfel.Bau b : g.bauten) {
            double[] bx = com.dan.brocken.scene.Gipfel.box(b.ring);
            for (double x = bx[0]; x <= bx[2]; x += 1.5)
                for (double z = bx[1]; z <= bx[3]; z += 1.5)
                    if (com.dan.brocken.scene.Gipfel.abstand(b.ring, x, z) < -2) {
                        double d = Math.abs(t.height(x, z) - b.boden);
                        if (d > schief) { schief = d; schiefster = b.name; }
                    }
        }
        check(schief < 0.35, String.format(Locale.GERMAN, "das Gelände unter den Bauten liegt eben auf ihrem Boden (höchstens %.2f m daneben, %s)", schief, schiefster));
        double yb = st.gleis.at(st.sBrocken).y();
        com.dan.brocken.scene.Gipfel.Bau bahnhof = null;
        for (com.dan.brocken.scene.Gipfel.Bau b : g.bauten) if (b.name.contains("Bahnhof Brocken")) bahnhof = b;
        com.dan.rail.Track.Pose ende = st.gleis.at(st.gleis.length());
        double vorGebaeude = bahnhof == null ? 0 : com.dan.brocken.scene.Gipfel.abstand(bahnhof.ring, ende.x(), ende.z());
        check(Math.abs(yb - 1125) < 0.5 && st.gleis.maxGradient(10) <= 0.0331 && vorGebaeude > 2 && bahnhof != null && Math.abs(bahnhof.boden - yb - 0.2) < 0.05,
                String.format(Locale.GERMAN, "Bahnhof Brocken auf %.1f m (Wikipedia: 1 125 m), Steigung weiter höchstens 3,3 %%, das Gleis endet %.1f m vor dem Empfangsgebäude, das auf Höhe des Bahnsteigs steht", yb, vorGebaeude));
        com.dan.rail.Track.Station sb = st.gleis.stationAt(st.sBrocken);
        check(sb != null && sb.side() == -1 && g.platz.length > 2 && Math.abs(t.height(com.dan.brocken.scene.Gipfel.mitte(g.platz)[0], com.dan.brocken.scene.Gipfel.mitte(g.platz)[1]) - g.platzHoehe) < 0.3,
                "Bahnsteig am Brocken auf der Seite des Gipfels, daneben ein ebener, gepflasterter Platz");
        // Menschen
        com.dan.brocken.scene.Zugbetrieb z = new com.dan.brocken.scene.Zugbetrieb(st);
        com.dan.brocken.scene.Menschen m = new com.dan.brocken.scene.Menschen(t, st, g, z);
        for (int i = 0; i < 600 && !m.bereit(); i++) Thread.sleep(50);
        Thread.sleep(300);
        check(m.bereit() && m.describeWege().contains("Brockenstraße") && m.describeWege().contains("Bahnsteig–Gipfelstein"), m.describeWege());
        double[] auge = {g.stein[0], g.stein[1] + 2, g.stein[2]};
        double sek = 10 * 3600 + 45 * 60;
        z.step(1, sek);
        m.step(1, sek, 8, 0, 6, 50_000, auge);
        int sommer = m.personen().size();
        String sommerText = m.describe();
        boolean fahrgaeste = sommerText.contains(" 1 Fahrgäste") || sommerText.contains(" 2 Fahrgäste") || sommerText.contains(" 3 Fahrgäste");
        // eine Minute weiter: alle gehen (die Gruppen bewegen sich)
        double[] vorher = m.personen().isEmpty() ? null : new double[]{m.personen().get(0).x, m.personen().get(0).z};
        for (int i = 0; i < 60; i++) { sek += 1; z.step(1, sek); m.step(1, sek, 8, 0, 6, 50_000, auge); }
        boolean bewegt = vorher != null && Math.hypot(m.personen().get(0).x - vorher[0], m.personen().get(0).z - vorher[1]) > 10;
        com.dan.brocken.scene.Menschen n = new com.dan.brocken.scene.Menschen(t, st, g, z);
        for (int i = 0; i < 600 && !n.bereit(); i++) Thread.sleep(50);
        Thread.sleep(300);
        n.step(1, 3 * 3600, 8, 0, 6, 50_000, auge);
        int nacht = n.personen().size();
        n.step(2, 10 * 3600 + 45 * 60, 8, 3, 6, 50_000, auge);
        int regen = n.personen().size();
        n.step(3, 10 * 3600 + 45 * 60, 1, 0, 6, 50_000, auge);
        int winter = n.personen().size();
        check(sommer >= 20 && fahrgaeste && bewegt && nacht == 0 && regen < sommer && winter < sommer, String.format(Locale.GERMAN,
                "Menschen nach Andrang: an einem schönen Augusttag um 10:45 %d (%s), eine Minute später %s; um 3 Uhr %d, bei Regen %d, im Januar %d",
                sommer, sommerText, bewegt ? "weiter gegangen" : "stehen sie", nacht, regen, winter));
        // DoD: das Statusbild vom Bahnsteig – vom Bahnsteig aus sind Turm mit Radom, Sendeturm, Kuppel und Bahnhof frei zu sehen (Gelände dazwischen tiefer als die Sichtlinie)
        com.dan.rail.Track.Pose p = st.gleis.at(st.sBrocken - 20);
        double hh = Math.toRadians(p.heading());
        double ex = p.x() - Math.cos(hh) * 3.4, ez = p.z() - Math.sin(hh) * 3.4, ey = t.height(ex, ez) + 1.7;
        StringBuilder sicht = new StringBuilder();
        boolean frei = true;
        Object[][] ziele = {{"Radom", radom == null ? null : new double[]{radom.x(), radom.y() + 6, radom.z()}},
                {"Sendeturm", sender == null ? null : new double[]{sender.x(), sender.y() + 100, sender.z()}},
                {"Kuppel", kuppel == null ? null : new double[]{kuppel.x(), kuppel.y() + 4, kuppel.z()}},
                {"Bahnhof", bahnhof == null ? null : new double[]{bahnhof.mitte()[0], bahnhof.boden + 4, bahnhof.mitte()[1]}}};
        for (Object[] zl : ziele) {
            double[] q = (double[]) zl[1];
            boolean ok = q != null;
            if (ok) for (double f = 0.05; f < 0.95; f += 0.01) {
                double x = ex + (q[0] - ex) * f, zz = ez + (q[2] - ez) * f, y = ey + (q[1] - ey) * f;
                if (t.height(x, zz) > y) { ok = false; break; }
            }
            frei &= ok;
            sicht.append(sicht.length() > 0 ? ", " : "").append(zl[0]).append(ok ? " frei" : " verdeckt");
        }
        check(frei, "DoD (Statusbild vom Bahnsteig; ob es die Fotos trifft, sagst du): vom Bahnsteig aus " + sicht + " – Tempo -Dkamera=bahnsteig");
        String sh = Shader.source("gipfel.frag", null).text;
        check(sh.contains("uRime") && sh.contains("uNacht") && Renderer.PASSES[6].equals("Gipfel") && Renderer.PASSES[7].equals("Menschen"),
                "eigene Durchgänge „Gipfel“ und „Menschen“, Shader mit Fenstern, Nacht, Schnee und Raueis");
    }

    /** Tag d (FIRST … 365, negativ im Vorjahr) als „17. Oktober“. */
    static String tag(int d) {
        int dd = d <= 0 ? d + 365 : d;
        java.time.LocalDate l = java.time.LocalDate.ofYearDay(2027, Math.max(1, Math.min(365, dd)));
        return l.getDayOfMonth() + ". " + l.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.GERMAN);
    }

    static String hhmm(double h) {
        if (Double.isNaN(h)) return "nie";
        int m = (int) Math.round(h * 60);
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    static void ansichten() {
        head("Ansichten");
        check(Ansichten.ALLE.size() == 9 && Ansichten.byName("Teufelskanzel").key() == 7 && Ansichten.byName("Goethemoor").key() == 8
                && Ansichten.byName("Totholz").key() == 9, "neun Ansichten (seit Phase 8 Teufelskanzel und Goethemoor, seit Phase 9 Totholz)");
        for (Ansichten.Ansicht a : Ansichten.ALLE) {
            Camera c = new Camera();
            boolean placed = Ansichten.apply(a, c, Renderer.PHASE);
            double g = Buehne.height(c.eye[0], c.eye[2]);
            check(placed && c.eye[1] >= g + Camera.MIN_EYE - 1e-6 && Site.HAUPTRASTER.contains(a.x(), a.z()) || placed && a.name().equals("Vogelauge"),
                    String.format(Locale.GERMAN, "%s: Auge %.0f m über NHN, %.1f m über Grund", a, c.eye[1], c.eye[1] - g));
        }
        check(Ansichten.byName("UeberdemNebelmeer") != null && Ansichten.byName("gipfelstein") != null && Ansichten.byName("6") != null, "Namen ohne Umlaute und Tasten");
        // Gipfelstein: am Morgen steht die Sonne im Rücken (Gegenrichtung des Blicks ± 90°)
        double[] s = new SceneClock().sun();
        double diff = Math.abs(((Ansichten.byKey(Ansichten.GIPFELSTEIN).heading() + 180 - s[1]) % 360 + 540) % 360 - 180);
        check(diff < 90, String.format(Locale.GERMAN, "vom Gipfelstein am Morgen die Sonne im Rücken (%.0f° daneben)", diff));
    }

    static void kamera() {
        head("Kamera");
        Renderer r = new Renderer();
        r.camera.home();
        r.camera.snap();
        for (int i = 0; i < 300; i++) r.step(1 / 60.0);
        check(r.camera.eye[1] > r.groundHeight(r.camera.eye[0], r.camera.eye[2]) + Camera.MIN_EYE, "Grundansicht über der Kuppe");
        r.camera.setMode(Camera.Mode.FREI);
        r.camera.kDown = true;
        r.camera.kRun = true;
        for (int i = 0; i < 2000; i++) r.step(1 / 30.0);
        r.camera.kDown = false;
        double g = r.groundHeight(r.camera.eye[0], r.camera.eye[2]);
        check(r.camera.eye[1] >= g + Camera.MIN_EYE - 0.05, String.format(Locale.GERMAN, "frei hinab: das Auge bleibt über dem Grund (%.1f m)", r.camera.eye[1] - g));
        check(r.cameraLine().contains("über NHN"), "Kamerazeile: " + r.cameraLine());
        r.physik.stop();
    }

    static void qualitaet() {
        head("Qualität");
        Renderer r = new Renderer();
        check(r.quality() == Quality.MITTEL && Quality.MITTEL.budgetMs() == 16.5, "Start auf „Mittel“, Budget 16,5 ms (Werkbuch, Abschnitt 11)");
        r.renderScale = 0.85;
        r.autoScale = true;
        r.regulateForTest(30, 10_000_000_000L);
        check(r.renderScale < 0.85 && r.renderScale >= Renderer.MIN_SCALE, String.format(Locale.GERMAN, "zu langsam: Auflösung sinkt (%.0f %%)", r.renderScale * 100));
        r.regulateForTest(5, 20_000_000_000L);
        check(r.renderScale <= Quality.MITTEL.renderScale, "nie über der Stufe");
        for (Quality q : Quality.values()) check(q.terrainKern <= q.terrainFine && q.summary().startsWith(q.label) && q.clouds != null && q.shadowN >= 512, q.summary());
    }

    static void physik() {
        head("Physik");
        Physik p = new Physik();
        int[] n = {0};
        p.add((t, dt) -> n[0]++);
        p.advance(0.05);
        int s = p.drain();
        check(s == 6 && n[0] == 6, "0,05 s = 6 Schritte zu 1/120 s");
        p.advance(5);
        p.drain();
        check(p.dropped() == 1 && p.lostSeconds() > 4.7, "zu viel auf einmal verfällt und wird gezählt");
    }

    static void shader() {
        head("Shader");
        for (String[] prog : Renderer.shaderFiles())
            for (String f : prog) {
                boolean good;
                try {
                    String src = Shader.source(f, null).text;
                    good = src.startsWith("#version 430 core") && src.contains("void main");
                } catch (RuntimeException e) {
                    good = false;
                }
                check(good, f + " liest sich mit allen #include");
            }
        String st = Shader.source("terrain.frag", null).text;
        check(st.contains("uniform vec4 uPoints[16]") && Site.ORTE.length <= 16, "terrain.frag nimmt alle Orte (marken.glsl)");
        Renderer frisch = new Renderer();
        check(!frisch.showMarks, "die Marken (Raster, Höhenlinien, Kern) sind von Anfang an aus, zuzuschalten im Block TECHNIK (seit Phase 17)");
        check(!frisch.showArrows && !frisch.showFaeden, "Pfeile und Rauchfäden des Windfelds sind von Anfang an aus, zuzuschalten im Block WIND; Strg + Klick setzt einen Faden und zeigt ihn (seit Phase 17a)");
        check(st.contains("uCloudLayer") && st.contains("uShadeMain") && st.contains("uSkyAmb"), "terrain.frag im Licht von com.dan.sky, mit Schatten der Kuppe und der Wolken");
        check(Shader.source("schatten.frag", null).text.contains("heightAt"), "schatten.frag läuft über das Gelände wie Kuppenschatten");
        String th = Shader.read("terrain_height.glsl");
        check(th.contains("uKNaht") && th.contains("uNaht") && th.contains("lvl + 2.64"), "terrain_height.glsl blendet Kern, Hauptraster und Fernring wie Terrain.height");
    }

    /** Phase 15: der Klang am Berg (Körper und Kanten am Gipfel, Züge, Pfiff, Vögel, Kalender der Walpurgisnacht). */
    static void klang() throws Exception {
        head("Klang (Phase 15)");
        java.time.ZoneId z = java.time.ZoneId.of("Europe/Berlin");
        double w1 = com.dan.brocken.wiring.KlangBrocken.walpurgisnacht(java.time.ZonedDateTime.of(2026, 4, 30, 23, 30, 0, 0, z)),
                w2 = com.dan.brocken.wiring.KlangBrocken.walpurgisnacht(java.time.ZonedDateTime.of(2026, 5, 1, 1, 0, 0, 0, z)),
                w3 = com.dan.brocken.wiring.KlangBrocken.walpurgisnacht(java.time.ZonedDateTime.of(2026, 5, 1, 4, 0, 0, 0, z)),
                w4 = com.dan.brocken.wiring.KlangBrocken.walpurgisnacht(java.time.ZonedDateTime.of(2026, 4, 30, 20, 0, 0, 0, z)),
                w5 = com.dan.brocken.wiring.KlangBrocken.walpurgisnacht(java.time.ZonedDateTime.of(2026, 6, 12, 23, 30, 0, 0, z));
        check(w1 == 1 && w2 == 1 && w3 == 0 && w4 == 0 && w5 == 0, "Walpurgisnacht nach dem Kalender: 30. April 23:30 und 1. Mai 1:00 ja, 4:00, 20:00 und im Juni nein");
        SceneClock clock = new SceneClock(java.time.LocalDate.of(2026, 6, 12), 11 + 49.5 / 60);
        Renderer r = new Renderer(clock);
        com.dan.brocken.wiring.KlangBrocken k = r.klang;
        boolean gel = false, lam = false;
        for (com.dan.soundscape.Aeolian b : k.klang.bodies) gel |= b.name().contains("Geländer");
        for (com.dan.soundscape.Edge e : k.klang.edges) lam |= e.name().contains("Lamellen");
        check(k.klang.bodies.size() >= 4 && k.klang.edges.size() >= 10 && gel && lam && k.klang.wind != null,
                String.format(Locale.GERMAN, "am Gipfel singen %d Körper (Mast, Antennen, Geländer) und heulen %d Kanten (Lamellen, Fugen, Hausecken) im Wind des Felds",
                        k.klang.bodies.size(), k.klang.edges.size()));
        if (!r.kameraGipfel("bahnsteig")) { check(false, "keine Ansicht Bahnsteig"); return; }
        r.frozen = false;
        clock.lapse = 1;
        k.klang.quality = com.dan.soundscape.Soundscape.Quality.LOW;
        for (int i = 0; i < 600 && r.menschen != null && !r.menschen.bereit(); i++) Thread.sleep(50);
        // von 11:49:30 bis 11:51:40: die Talfahrt steht, pfeift um 11:51 und fährt an
        double maschStand = -1, maschAb = 0;
        long ms0 = System.nanoTime();
        for (int i = 0; i < 130 * 20; i++) {
            r.step(0.05);
            k.klang.render(0.05);
            double sek = clock.local().toLocalTime().toSecondOfDay();
            if (maschStand < 0 && sek >= 11 * 3600 + 50 * 60 + 30) maschStand = k.klang.levels()[6];
            if (sek >= 11 * 3600 + 51 * 60) maschAb = Math.max(maschAb, k.klang.levels()[6]);
        }
        double rechen = (System.nanoTime() - ms0) / 1e9;
        double fahrt = 0;
        for (com.dan.soundscape.Steam st : k.klang.steams) if (!st.standing) fahrt = Math.max(fahrt, st.speed);
        long[] zl = k.zaehler();
        check(!k.klang.steams.isEmpty() && zl[2] >= 1 && maschStand > 30 && maschAb > 40 && fahrt > 0.5,
                String.format(Locale.GERMAN, "Bahnsteig, 11:49:30 bis 11:51:40: %d Lok(s) im Klang, %d Pfiff(e); die Lok um 11:50:30 im Stand %.0f dB (Bläser, Pumpe oder Ventil), nach dem Pfiff bis %.0f dB und fährt mit %.1f m/s",
                        k.klang.steams.size(), zl[2], maschStand, maschAb, fahrt));
        check(zl[0] >= 3, String.format(Locale.GERMAN, "Juni, Mittag, am Gipfel: %d Vögel in 130 s (%s)", zl[0], k.zeile()));
        check(rechen < 120, String.format(Locale.GERMAN, "130 s Szene samt Klang ohne Bilder in %.0f s gerechnet", rechen));
        // Januar, Nacht, Sturm: keine Singvögel
        check(com.dan.brocken.wiring.KlangBrocken.saison(1, 4, 7) == 0 && com.dan.brocken.wiring.KlangBrocken.saison(5, 4, 7) == 1,
                "Ringdrossel und Wiesenpieper singen von April bis Juli, im Januar nicht");
        r.physik.stop();
    }

    /** Phase 16: die vier Regiebücher ohne Bild, ohne Eingriff bis zum Ende (das DoD), und das Kino. */
    static void regie() throws Exception {
        head("Regiebücher und Kino (Phase 16)");
        java.util.List<com.dan.brocken.scene.Regie.Book> all;
        try { all = com.dan.brocken.scene.Regie.all(); } catch (RuntimeException e) { check(false, "die vier Regiebücher lesen: " + e.getMessage()); return; }
        SceneClock c = new SceneClock();
        Renderer r = new Renderer(c);
        for (int i = 0; i < 600 && r.menschen != null && !r.menschen.bereit(); i++) Thread.sleep(50);
        int views = 0, bad = 0;
        StringBuilder unbekannt = new StringBuilder();
        for (com.dan.brocken.scene.Regie.Book b : all)
            for (com.dan.brocken.scene.Regie.Zeile z : b.zeilen()) {
                if (z.befehl().equals("ansicht")) { views++; if (!r.placeNamed(z.argumente())) { bad++; unbekannt.append(' ').append(z.argumente()); } }
                if (z.befehl().equals("fahrt")) { views++; if (r.fahrtNamed(z.argumente()) == null) { bad++; unbekannt.append(' ').append(z.argumente()); } }
            }
        boolean blatt = all.stream().allMatch(b -> b.zeilen().stream().anyMatch(z -> z.befehl().equals("blatt")));
        int fotos = all.stream().mapToInt(com.dan.brocken.scene.Regie.Book::fotos).sum();
        check(all.size() == 4 && blatt && bad == 0 && fotos >= 24, String.format(Locale.GERMAN, "vier Regiebücher aus scene/regie/*.txt (%s): %d Fotos, jedes mit Blatt, %d Ansichten und Fahrten, alle bekannt%s",
                String.join(", ", all.stream().map(com.dan.brocken.scene.Regie.Book::name).toList()), fotos, views, unbekannt.length() == 0 ? "" : " – unbekannt:" + unbekannt));
        boolean caught = false;
        try { com.dan.brocken.scene.Regie.parse("Regiebuch: X\n0:00 zeit 1.1.2026 10:00\n0:05 warte ebbe\n"); } catch (IllegalArgumentException e) { caught = e.getMessage().startsWith("Zeile 3"); }
        check(caught, "ein falsches warte nennt die Zeile");
        // das DoD: alle vier ohne Eingriff bis zum Ende, in Schritten zu 0,2 s ohne Bild
        r.ohneBlatt = true;
        StringBuilder bericht = new StringBuilder();
        boolean alle = true;
        boolean mit = false, spirale = false, angekommen = false, gespenstMeer = false, getrieben = false, walp = false, raueis = false;
        long t0 = System.nanoTime();
        for (com.dan.brocken.scene.Regie.Book b : all) {
            r.regieStart(b);
            int steps = 0;
            while (r.regie.running() && steps < 8000) {
                r.step(0.2);
                r.bildlos();
                steps++;
                String d = r.regie.describe();
                mit |= r.camera.mitfahrt != null && d.contains("zieht an");
                spirale |= r.camera.fahrt != null && "Spirale".equals(r.camera.fahrt.name);
                angekommen |= d.contains("Ankunft am Brocken") && r.camera.eye[1] > 1100;
                gespenstMeer |= d.contains("Brockengespenst") && r.nebel.snapshot().sea();
                getrieben |= r.treibt();
                walp |= r.klang.walpurgis() > 0.99 && c.local().getMonthValue() == 4;
                raueis |= d.contains("toten Fichten") && r.wiring.raueis.dicke(com.dan.brocken.wiring.Raueis.ZWEIG, 1000) > 0.005;
            }
            boolean ende = !r.regie.running();
            alle &= ende && r.albumSize() == b.fotos();
            bericht.append(String.format(Locale.GERMAN, "%s%s %s %.0f s (%d Fotos)", bericht.length() > 0 ? "; " : "", b.name(), ende ? "endet nach" : "läuft noch nach", steps * 0.2, r.albumSize()));
        }
        double sek = (System.nanoTime() - t0) / 1e9;
        check(alle, "DoD: alle vier laufen ohne Eingriff durch – " + bericht);
        check(mit && spirale && angekommen, "„Mit dem Zug auf den Brocken“: auf der Lok ab Schierke, die Spirale um die Kuppe, angekommen am Bahnhof Brocken");
        check(gespenstMeer && getrieben && walp && raueis, String.format(Locale.GERMAN,
                "Nebelmeer am Auge für das Gespenst %s, mit dem Wind getrieben %s, Raueis an den toten Fichten %s, die Stimme der Walpurgisnacht %s (alle vier in %.0f s gerechnet)",
                gespenstMeer ? "ja" : "nein", getrieben ? "ja" : "nein", raueis ? "ja" : "nein", walp ? "ja" : "nein", sek));
        // das Kino: Vor- und Abspann, Untertitel aus den Stichworten
        r.kinoStart(all.get(1));
        java.util.List<com.dan.kino.Kino.Lage> seen = new java.util.ArrayList<>();
        String ut = null, abspann = null;
        int steps = 0;
        while (steps++ < 400 && (r.kino.laeuft() || seen.isEmpty())) {
            r.step(0.1);
            r.bildlos();
            com.dan.kino.Kino.Lage l = r.kino.lage();
            if (seen.isEmpty() || seen.get(seen.size() - 1) != l) seen.add(l);
            com.dan.kino.Kino.Tafel t = r.kino.tafel();
            if (t != null && t.art() == com.dan.kino.Kino.Art.UNTERTITEL && ut == null) ut = t.zeilen().get(0);
            if (t != null && t.art() == com.dan.kino.Kino.Art.ABSPANN && abspann == null) abspann = String.join(" / ", t.zeilen());
            if (steps == 120) r.regie.stop();
        }
        check(seen.equals(java.util.List.of(com.dan.kino.Kino.Lage.VORSPANN, com.dan.kino.Kino.Lage.FILM, com.dan.kino.Kino.Lage.ABSPANN, com.dan.kino.Kino.Lage.AUSKLANG, com.dan.kino.Kino.Lage.AUS))
                        && ut != null && ut.startsWith("6:30") && abspann != null && abspann.startsWith("Über dem Nebelmeer") && abspann.contains("DGM1"),
                "Kino: " + String.join(" → ", seen.stream().map(Enum::name).toList()) + "; erster Untertitel „" + ut + "“; Abspann „" + (abspann == null ? "—" : abspann.substring(0, Math.min(60, abspann.length()))) + " …“");
        r.kinoVerlassen();
        r.physik.stop();
    }

    /** Phase 17: die Datenbank ohne Oracle: Skripte, Startdaten, Speicher in Dateien, Chronik, Sitzung, Übernahme (F25–F37). */
    static void daten() throws Exception {
        head("Oberfläche und Datenbank (Phase 17)");
        File dir = com.dan.brocken.data.Verbindung.skripte();
        java.util.Map<String, java.util.List<com.dan.store.Skript.Anweisung>> sk = new java.util.LinkedHashMap<>();
        for (String f : new String[]{"01_tabellen.sql", "02_spatial.sql", "03_sichten.sql", "04_startdaten.sql", "99_entfernen.sql"}) {
            File x = new File(dir, f);
            sk.put(f, x.isFile() ? com.dan.store.Skript.zerlegen(Files.readString(x.toPath(), StandardCharsets.UTF_8)) : java.util.List.of());
        }
        java.util.List<com.dan.store.Skript.Spalte> sp = com.dan.store.Skript.spalten(sk.get("01_tabellen.sql"));
        java.util.Set<String> tab = new java.util.TreeSet<>();
        for (com.dan.store.Skript.Spalte x : sp) tab.add(x.tabelle());
        long seq = sk.get("01_tabellen.sql").stream().filter(a -> a.sql().startsWith("CREATE SEQUENCE")).count(),
                trg = sk.get("01_tabellen.sql").stream().filter(a -> a.plsql()).count(),
                idn = sk.get("01_tabellen.sql").stream().filter(a -> a.sql().toUpperCase(Locale.ROOT).contains("IDENTITY")).count();
        check(tab.size() == 39 && tab.stream().allMatch(t -> t.startsWith("BR_")) && idn == 0 && seq == 38 && trg == 37,
                String.format(Locale.GERMAN, "01_tabellen.sql: %d Tabellen BR_, %d Spalten, %d Sequenzen, %d Trigger, nie IDENTITY (F23 A, Hauskonvention)", tab.size(), sp.size(), seq, trg));
        java.util.Map<String, String> typ = new java.util.HashMap<>();
        for (com.dan.store.Skript.Spalte x : sp) typ.put(x.tabelle() + "." + x.name(), x.typ());
        String spat = Files.readString(new File(dir, "02_spatial.sql").toPath(), StandardCharsets.UTF_8);
        boolean geo = true;
        StringBuilder gfehl = new StringBuilder();
        for (String m : com.dan.brocken.data.Verbindung.METADATEN) {
            boolean g = "MDSYS.SDO_GEOMETRY".equals(typ.get(m)) && spat.contains(m.replace('.', '_') + "_SX ON ") && spat.contains("'" + m.substring(0, m.indexOf('.')) + "', '" + m.substring(m.indexOf('.') + 1) + "'");
            if (!g) gfehl.append(' ').append(m);
            geo &= g;
        }
        int geoSpalten = (int) sp.stream().filter(x -> x.typ().equals("MDSYS.SDO_GEOMETRY")).count();
        check(geo && geoSpalten == com.dan.brocken.data.Verbindung.METADATEN.length,
                geoSpalten + " Geometriespalten in 8307 (F28 A), jede mit Metadaten und räumlichem Index in 02_spatial.sql" + (gfehl.length() == 0 ? "" : " – fehlt:" + gfehl));
        String sicht = Files.readString(new File(dir, "03_sichten.sql").toPath(), StandardCharsets.UTF_8);
        boolean sv = sk.get("03_sichten.sql").size() == com.dan.brocken.data.Verbindung.SICHTEN.length;
        for (String v : com.dan.brocken.data.Verbindung.SICHTEN) sv &= sicht.contains("CREATE OR REPLACE VIEW " + v + " AS");
        check(sv, com.dan.brocken.data.Verbindung.SICHTEN.length + " Sichten für QGIS: " + String.join(", ", com.dan.brocken.data.Verbindung.SICHTEN));
        // die Startdaten: aktuell (aus dem Code erzeugt) und mit den Zahlen, die der Einrichter prüft
        SceneClock c = new SceneClock();
        Renderer r = new Renderer(c);
        String neu = com.dan.brocken.data.StartdatenSql.sql(r, new File("daten/gelaende"));
        String alt = Files.readString(new File(dir, "04_startdaten.sql").toPath(), StandardCharsets.UTF_8);
        check(neu.equals(alt), "04_startdaten.sql ist aktuell (aus dem Code erzeugt, " + sk.get("04_startdaten.sql").size() + " Anweisungen)"
                + (neu.equals(alt) ? "" : " – bitte ant startdaten (neu " + neu.length() + " Zeichen, Datei " + alt.length() + ")"));
        java.util.Map<String, Integer> z = com.dan.brocken.data.Verbindung.zahlen(dir);
        boolean zk = z.keySet().equals(com.dan.brocken.data.Verbindung.ZAEHLEN.keySet()) && z.values().stream().allMatch(n -> n > 0);
        check(zk, "Startdaten je Art (der Einrichter zählt sie nach): " + z);
        // jede Spalte der Startdaten gibt es in 01_tabellen.sql (sonst schlüge das Einrichten fehl)
        boolean spalten = true;
        StringBuilder sf = new StringBuilder();
        for (com.dan.brocken.data.Startdaten.Tabelle t : com.dan.brocken.data.Startdaten.alles(r, new File("daten/gelaende")))
            for (com.dan.brocken.data.Daten.Zeile zz : t.zeilen())
                for (String k : zz.keySet()) {
                    String kk = k.equals("QUELLE") ? "QUELLE_ID" : k;
                    if (k.equals("STUETZPUNKTE") || typ.containsKey(t.name() + "." + kk)) continue;
                    spalten = false;
                    if (sf.length() < 200) sf.append(' ').append(t.name()).append('.').append(k);
                }
        check(spalten, "jede Spalte der Startdaten steht in 01_tabellen.sql" + (sf.length() == 0 ? "" : " – fehlt:" + sf));
        // seit Phase 17a: keine Fläche, deren Ringe sich kreuzen oder berühren (Oracle meldete ORA-13349 in BR_BODEN)
        int fl = 0, ri = 0;
        StringBuilder kf = new StringBuilder();
        for (com.dan.brocken.data.Startdaten.Tabelle t : com.dan.brocken.data.Startdaten.alles(r, new File("daten/gelaende")))
            for (com.dan.brocken.data.Daten.Zeile zz : t.zeilen())
                for (Object o : zz.values()) {
                    if (!(o instanceof com.dan.brocken.data.Daten.Geom g) || !g.art().endsWith("FLAECHE")) continue;
                    java.util.List<double[][]> alle = new java.util.ArrayList<>();
                    if (g.art().equals("FLAECHE")) alle.addAll(g.teile()); else for (java.util.List<double[][]> x : g.ringe()) alle.addAll(x);
                    fl++;
                    ri += alle.size();
                    if (!com.dan.brocken.data.Konturen.kreuzend(alle).isEmpty() && kf.length() < 200) kf.append(' ').append(t.name()).append('/').append(zz.text("NAME"));
                }
        check(fl > 0 && kf.length() == 0, fl + " Flächen der Startdaten mit " + ri + " Ringen: keiner kreuzt oder berührt einen anderen (gültig für Oracle)" + (kf.length() == 0 ? "" : " – kreuzt:" + kf));
        // die Zeile als Text hin und zurück (Dateien ohne Datenbank)
        com.dan.brocken.data.Daten.Zeile zt = new com.dan.brocken.data.Daten.Zeile().mit("A", "Tab\tund\nZeile \\ Ende").mit("B", 1141.25).mit("C", 7L)
                .mit("D", com.dan.brocken.data.Daten.Geom.punkt(12, -34)).mit("E", new byte[]{1, 2, 3}).mit("F", null).mit("G", Double.NaN);
        com.dan.brocken.data.Daten.Zeile zr = com.dan.brocken.data.Daten.Zeile.dekodieren(zt.kodieren());
        double[] pz = zr.geom("D").xz()[0];
        check(zr.text("A").equals(zt.text("A")) && zr.zahl("B") == 1141.25 && zr.ganz("C", 0) == 7 && Math.abs(pz[0] - 12) < 0.01 && Math.abs(pz[1] + 34) < 0.01
                && java.util.Arrays.equals(zr.bytes("E"), new byte[]{1, 2, 3}) && zr.containsKey("F") && zr.get("G") == null, "eine Zeile als Text hin und zurück (Tab, Zeilenumbruch, Zahl, Punkt in 8307, Bytes, leer)");
        // F26 A: Szenendaten aus „der Datenbank“ (hier aus dem Code gebaut) setzen den Fahrplan und das Klima
        com.dan.brocken.data.Szenendaten sd = new com.dan.brocken.data.Szenendaten();
        sd.fahrplan.addAll(com.dan.brocken.scene.Fahrplan.ausDemCode().subList(0, 3));
        for (int m = 0; m < 12; m++) { sd.klima[m][0] = com.dan.brocken.wiring.KlimaBrocken.DWD[m]; sd.klima[m][1] = com.dan.brocken.wiring.KlimaBrocken.DWD_NIEDERSCHLAG[m];
            sd.klima[m][2] = com.dan.brocken.wiring.KlimaBrocken.EINGANG[m]; sd.klima[m][3] = com.dan.brocken.wiring.KlimaBrocken.NASSE_TAGE[m]; }
        for (String rb : com.dan.brocken.scene.Regie.DATEIEN) { String t = com.dan.brocken.scene.Regie.text(rb); sd.regiebuecher.put(com.dan.brocken.scene.Regie.parse(t).name(), t); }
        java.util.List<String> sdb = sd.anwenden("Datenbank");
        boolean fp = com.dan.brocken.scene.Fahrplan.fahrten().size() == 3;
        com.dan.brocken.scene.Fahrplan.geladen = null;
        com.dan.brocken.scene.Regie.geladen = null;
        com.dan.brocken.data.Szenendaten.herkunft = "Code";
        check(fp && sdb.isEmpty() && com.dan.brocken.scene.Fahrplan.fahrten().size() == 14, "F26 A: Fahrplan, Klima und Regiebücher aus der Datenbank gelten, der Code ist der Rückfall (" + sd.fahrplan.size() + " Fahrten gesetzt, danach wieder 14)");
        // der Speicher in Dateien: Sitzung, Chronik, Merkpunkt, Preset, Weiter wie zuletzt, Übernahme
        File tmp = Files.createTempDirectory("brocken-daten").toFile();
        com.dan.brocken.data.DateiSpeicher ds = new com.dan.brocken.data.DateiSpeicher(new File(tmp, "a"));
        com.dan.brocken.data.Datenseite.setzen(ds);
        com.dan.store.Dienst.starten(Renderer.VERSION, "Prüfung", r.quality().name(), () -> new com.dan.store.Ablage.Sicherung(
                com.dan.store.Felder.alsText(com.dan.brocken.data.Zustand.erfassen(r), "Prüfung"), com.dan.brocken.data.Zustand.spalten(com.dan.brocken.data.Zustand.erfassen(r))));
        long sitz = com.dan.store.Dienst.sitzung();
        // Chronik: der Nebelmeer-Tag im Zeitraffer, dann die Bergfahrt 10:06 bis zur Ankunft
        com.dan.brocken.data.Chronik ch = new com.dan.brocken.data.Chronik(r, false);
        com.dan.brocken.data.Datenseite.chronik = ch;
        r.wetter("UeberdemNebelmeer", 0);
        r.camera.mode = com.dan.brocken.camera.Camera.Mode.FREI;
        c.lapse = 600;
        for (int i = 0; i < 90; i++) { r.step(0.5); r.bildlos(); ch.jetzt(); }
        c.lapse = 1;
        c.set(c.date(), 10.0);
        r.step(0.1);
        ch.jetzt();
        c.lapse = 30;
        for (int i = 0; i < 160; i++) { r.step(0.5); r.bildlos(); if (i % 4 == 0) ch.jetzt(); }
        ch.jetzt();
        ch.beenden();
        com.dan.brocken.data.Daten.Stand st = ds.stand();
        check(ch.fehler == 0 && st.n("BR_STUNDE") >= 6 && st.n("BR_NEBELMEER") >= 1 && st.n("BR_ZUGFAHRT") >= 1 && st.n("BR_STUNDE") == ch.stunden,
                "F29 A: " + ch.zeile());
        java.util.List<com.dan.brocken.data.Daten.Zeile> zf = ds.lesen("BR_ZUGFAHRT", null, 5);
        com.dan.brocken.data.Daten.Zeile f0 = zf.isEmpty() ? null : zf.get(zf.size() - 1);
        check(f0 != null && f0.text("ZUG").contains("10:06") && f0.text("IST_AN") != null && f0.ganz("SITZUNG_ID", -1) == sitz,
                "Zugfahrt in der Chronik: " + (f0 == null ? "—" : f0.text("ZUG") + ", ist " + f0.text("IST_AB") + "–" + f0.text("IST_AN") + ", "
                        + String.format(Locale.GERMAN, "%+.1f min", f0.zahl("VERSPAETUNG_MIN")) + ", " + f0.ganz("PFIFFE", 0) + " Pfiffe, Sitzung " + f0.ganz("SITZUNG_ID", -1)));
        java.util.List<com.dan.brocken.data.Daten.Zeile> nm = ds.lesen("BR_NEBELMEER", null, 5);
        check(!nm.isEmpty() && nm.get(nm.size() - 1).zahl("OBEN_MAX_M") > 500, "Nebelmeer in der Chronik: ab " + (nm.isEmpty() ? "—" : nm.get(nm.size() - 1).text("SZENE_TEXT")
                + String.format(Locale.GERMAN, ", %.0f–%.0f m", nm.get(nm.size() - 1).zahl("UNTEN_M"), nm.get(nm.size() - 1).zahl("OBEN_MAX_M"))
                + (nm.get(nm.size() - 1).text("ENDE_TEXT") == null ? "" : ", bis " + nm.get(nm.size() - 1).text("ENDE_TEXT"))));
        // F35 A: ein Merkpunkt; F34 A: ein eigenes Preset
        long mp = ds.schreiben("BR_MERKPUNKT", com.dan.brocken.data.Daten.szene(new com.dan.brocken.data.Daten.Zeile(), c.epochMillis()).mit("NAME", "Prüfung").mit("NOTIZ", "am Gipfelstein")
                .mit("X_M", 2.0).mit("Z_M", -5.0).mit("LAGE", com.dan.brocken.data.Daten.Geom.punkt(2, -5)).mit("VORSCHAU", new byte[]{9, 8, 7}).mit("SITZUNG_ID", sitz));
        ds.preset("com.dan.fog", "TerrainFog", "Prüfung", "aus der Prüfung", com.dan.store.Felder.lesen(r.nebel));
        java.util.Map<String, java.util.Map<String, String>> pr = ds.presets("TerrainFog");
        check(java.util.Arrays.equals(ds.bytes("BR_MERKPUNKT", mp, "VORSCHAU"), new byte[]{9, 8, 7}) && pr.containsKey("Prüfung") && pr.containsKey("nebelmeer"),
                "Merkpunkt mit Vorschau und eigenes Preset in den Dateien (" + pr.size() + " Presets für TerrainFog)");
        boolean abgelehnt = false;
        try { ds.schreiben("BR_STUNDE", new com.dan.brocken.data.Daten.Zeile().mit("GIBTS_NICHT", 1)); } catch (com.dan.store.Ablage.Fehler e) { abgelehnt = e.getMessage().contains("GIBTS_NICHT"); }
        check(abgelehnt, "eine Spalte, die es in 01_tabellen.sql nicht gibt, wird abgelehnt (auch ohne Datenbank)");
        // F37 A, das DoD: „Weiter wie zuletzt“ stellt die letzte Sitzung her
        c.set(java.time.LocalDate.of(2027, 1, 15), 13.5);
        c.lapse = 120;
        r.wiring.hand(com.dan.weather.Weather.Var.WIND, 17);
        r.camera.mode = com.dan.brocken.camera.Camera.Mode.FREI;
        r.camera.place(-500, 1300, 800, 123, 7);
        r.camera.snap();
        r.showNebel = false;
        r.klang.windDb = -9;
        r.step(0.1);
        com.dan.store.Dienst.sichern(false);
        double ms0 = c.epochMillis();
        double[] e0 = r.camera.eye.clone();
        c.set(java.time.LocalDate.of(2026, 6, 1), 8);
        c.lapse = 1;
        r.wiring.resumeRegie();
        r.camera.place(100, 900, 100, 0, 0);
        r.camera.snap();
        r.showNebel = true;
        r.klang.windDb = 0;
        com.dan.brocken.data.Daten.Sitzung ls = com.dan.brocken.data.Datenseite.speicher().letzteSitzung(-1);
        java.util.List<String> nicht = ls == null ? java.util.List.of("keine Sitzung") : com.dan.brocken.ui.Weiter.jetzt(r, ls);
        r.step(0.05);
        boolean her = ls != null && ls.id() == sitz && nicht.isEmpty() && Math.abs(c.epochMillis() - ms0) < 60_000 && c.lapse == 120 && !r.showNebel && r.klang.windDb == -9
                && Math.hypot(r.camera.eye[0] - e0[0], r.camera.eye[2] - e0[2]) < 2 && r.wiring.weather.overridden(com.dan.weather.Weather.Var.WIND);
        check(her, String.format(Locale.GERMAN, "DoD: „Weiter wie zuletzt“ stellt die Sitzung %d her (Uhr %s, Zeitraffer %.0f, Auge %.0f/%.0f m, Wind von Hand, Nebel aus, Wind am Ohr %+.0f dB)%s",
                ls == null ? -1 : ls.id(), com.dan.brocken.data.Daten.text(c.epochMillis()), c.lapse, r.camera.eye[0], r.camera.eye[2], r.klang.windDb,
                nicht.isEmpty() ? "" : " – nicht: " + nicht));
        r.showNebel = true;
        r.klang.windDb = -4;
        r.wiring.resumeRegie();
        c.lapse = 1;
        // F25 A: Übernahme aus den Dateien (hier in einen zweiten Ordner, wie in die Datenbank)
        com.dan.store.Dienst.beenden();
        com.dan.brocken.data.DateiSpeicher nach = new com.dan.brocken.data.DateiSpeicher(new File(tmp, "b"));
        String ue = com.dan.brocken.data.Uebernahme.uebernehmen(ds, nach);
        check(nach.stand().n("BR_STUNDE") == st.n("BR_STUNDE") && nach.stand().n("BR_MERKPUNKT") == 1 && nach.presets("TerrainFog").containsKey("Prüfung")
                && !com.dan.brocken.data.Uebernahme.etwasDa(ds), "F25 A: Übernehmen trägt Chronik und Eigenes weiter und benennt die Dateien um: " + ue);
        com.dan.brocken.data.Datenseite.setzen(new com.dan.brocken.data.DateiSpeicher(new File(tmp, "c")));
        com.dan.brocken.data.Datenseite.chronik = null;
        r.windrechner.stop();
        r.physik.stop();
    }

    static void bibliothek() {
        head("Renderer.jar (F4 A)");
        check(!Bibliothek.pakete().isEmpty(), Bibliothek.kurz());
        check(Bibliothek.fehlend().isEmpty(), "alles da, was das Werkbuch braucht" + (Bibliothek.fehlend().isEmpty() ? "" : ": es fehlen " + Bibliothek.fehlend()));
        check(!Bibliothek.neuStand().contains("fehlt"), "neu für Brocken: " + Bibliothek.neuStand());
    }

    /** Die Prüfklassen der neuen Packages laufen mit (in einem eigenen Prozess, weil sie mit System.exit enden). */
    static void pakete() throws Exception {
        head("Die Prüfklassen der Packages (wind seit Phase 4 mit Kern, weather und cloud seit Phase 3, fog und sky seit Phase 7, cliff und ground seit Phase 8, forest seit Phase 9, season seit Phase 11, soundscape seit Phase 15)");
        for (String k : new String[]{"com.dan.wind.demo.WindCheck", "com.dan.rime.demo.RimeCheck", "com.dan.rail.demo.RailCheck",
                "com.dan.weather.demo.WeatherCheck", "com.dan.cloud.demo.CloudCheck", "com.dan.fog.demo.FogCheck", "com.dan.sky.demo.SkyCheck",
                "com.dan.cliff.demo.CliffCheck", "com.dan.ground.demo.GroundCheck", "com.dan.forest.demo.ForestCheck",
                "com.dan.season.demo.SeasonCheck", "com.dan.soundscape.demo.SoundscapeCheck"}) {
            boolean da;
            try { Class.forName(k); da = true; } catch (ClassNotFoundException e) { da = false; }
            if (!da) { check(false, k + " fehlt in Renderer.jar"); continue; }
            String out = "";
            int rc = -1;
            ruhe();
            // seit Phase 15 ein zweiter Versuch: Rechenzeiten in den Prüfklassen schwanken, wenn hier noch Fäden laufen
            for (int versuch = 0; versuch < 2 && !(rc == 0 && out.contains("Alles in Ordnung.")); versuch++) {
                ProcessBuilder pb = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(), "-cp", System.getProperty("java.class.path"),
                        "-Dstdout.encoding=UTF-8", "-Xmx3g", k);
                pb.redirectErrorStream(true);
                Process pr = pb.start();
                out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                rc = pr.waitFor();
            }
            boolean gut = rc == 0 && out.contains("Alles in Ordnung.");
            StringBuilder fehl = new StringBuilder();
            for (String l : out.split("\\R")) if (l.contains("FEHLER") || l.contains("  FEHL ") || l.contains("fehlgeschlagen") || l.contains("Exception")) fehl.append(fehl.length() > 0 ? " | " : "").append(l.trim());
            check(gut, k.substring(k.lastIndexOf('.') + 1) + ": " + (gut ? "Alles in Ordnung." : fehl.length() > 0 ? fehl.toString() : out.trim()));
        }
    }

    /** Seit Phase 17: warten, bis kein Windfeld der Renderer aus den Abschnitten davor mehr rechnet (höchstens 2 min) – die Prüfklassen messen Rechenzeiten. */
    static void ruhe() throws InterruptedException {
        long bis = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < bis && Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> t.getName().equals("Windfeld") && t.getState() == Thread.State.RUNNABLE)) Thread.sleep(200);
        System.gc();
    }

    static void symbol() {
        head("Programmsymbol (F24 A)");
        for (int s : AppIcon.SIZES) {
            java.awt.image.BufferedImage im = AppIcon.paint(s);
            int c = im.getRGB(s / 2, s / 2) >>> 24;
            check(im.getWidth() == s && c > 200, "Symbol " + s + " px gemalt");
        }
    }

    /** Phase 18: Schliff – Symbol als Dateien, Startdateien, Quellenliste, README mit Bildern, Dauerlauf, Hilfslinien. */
    static void schliff() throws Exception {
        head("Schliff (Phase 18)");
        check(Renderer.VERSION.equals("Phase 18"), "Version " + Renderer.VERSION);
        // Brocken.ico: sieben Größen als PNG, jede so groß, wie das Verzeichnis sagt
        File ico = new File("Brocken.ico");
        boolean icoOk = false;
        String icoText = "fehlt";
        if (ico.isFile()) {
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(Files.readAllBytes(ico.toPath())).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int typ = bb.getShort(2), n = bb.getShort(4);
            icoOk = typ == 1 && n == AppIcon.SIZES.length;
            for (int i = 0; i < n && icoOk; i++) {
                int w = bb.get(6 + 16 * i) & 255, len = bb.getInt(6 + 16 * i + 8), off = bb.getInt(6 + 16 * i + 12);
                java.awt.image.BufferedImage im = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bb.array(), off, len));
                icoOk = im != null && im.getWidth() == (w == 0 ? 256 : w) && im.getWidth() == AppIcon.SIZES[i];
            }
            icoText = n + " Größen, " + ico.length() / 1024 + " KB";
        }
        check(icoOk, "Brocken.ico für die Verknüpfung (" + icoText + "; ant symbol)");
        boolean pngs = true;
        for (int s : AppIcon.SIZES) pngs &= AppIcon.class.getResource("icon/brocken_" + s + ".png") != null;
        check(pngs, "die PNGs des Symbols liegen im Jar (com/dan/brocken/ui/icon), die App malt sie nicht mehr selbst");
        // die Startdateien
        boolean bat = true;
        StringBuilder bf = new StringBuilder();
        for (String[] b : new String[][]{{"Brocken.bat", "dist\\Brocken.jar"}, {"Brocken_Konsole.bat", "dist\\Brocken.jar"}, {"Brocken_Verknuepfung.bat", "Brocken.ico"}}) {
            File f = new File(b[0]);
            String t = f.isFile() ? Files.readString(f.toPath(), StandardCharsets.ISO_8859_1) : "";
            boolean ok = t.contains(b[1]) && t.contains("\r\n") && !t.replace("\r\n", "").contains("\n") && t.contains("cd /d \"%~dp0\"");
            if (!ok) bf.append(' ').append(b[0]);
            bat &= ok;
        }
        check(bat, "Brocken.bat, Brocken_Konsole.bat, Brocken_Verknuepfung.bat (Zeilenenden CRLF, im Projektordner)" + (bf.length() == 0 ? "" : " – nicht in Ordnung:" + bf));
        // Quellenliste und README
        File q = new File("docs/QUELLEN.md");
        String qa = q.isFile() ? Files.readString(q.toPath(), StandardCharsets.UTF_8) : "";
        check(qa.equals(com.dan.brocken.tools.Quellenliste.text()), "docs/QUELLEN.md passt zu quellen.txt und befunde.txt (" + com.dan.brocken.data.Startdaten.quellen().size()
                + " Quellen, " + com.dan.brocken.data.Startdaten.befunde().size() + " Befunde)" + (qa.isEmpty() ? " – fehlt, ant quellen" : ""));
        String rm = Files.readString(new File("README.md").toPath(), StandardCharsets.UTF_8);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\]\\((docs/bilder/[^)]+)\\)").matcher(rm);
        int bilder = 0, fehlt = 0;
        while (m.find()) { bilder++; if (!new File(m.group(1)).isFile()) fehlt++; }
        check(bilder >= 9 && fehlt == 0 && rm.contains("Brocken_Verknuepfung.bat") && rm.contains("QUELLEN.md"),
                "README mit " + bilder + " Bildern aus docs/bilder" + (fehlt == 0 ? "" : ", " + fehlt + " fehlen") + ", Starten, Verknüpfung, Quellen");
        String bx = Files.readString(new File("build.xml").toPath(), StandardCharsets.UTF_8);
        boolean ziele = true;
        for (String z : new String[]{"symbol", "quellen", "vorgaenger", "stufen", "tempo", "dbpruefung", "startdaten"}) ziele &= bx.contains("<target name=\"" + z + "\"");
        check(ziele, "ant symbol, quellen, vorgaenger, stufen, tempo, dbpruefung, startdaten");
        java.util.List<String> pk = com.dan.brocken.tools.Vorgaenger.pruefklassen(new File("lib/Renderer.jar"));
        check(pk.size() >= 30 && pk.contains("com.dan.fracture.demo.FractureCheck") && pk.contains("com.dan.wind.demo.WindCheck"),
                "ant vorgaenger findet " + pk.size() + " Prüfklassen in Renderer.jar (auch die der Vorgänger: fracture, ocean, tide, timber …)");
        // der Dauerlauf: die Regiebücher, das Urteil, das Bild
        java.util.Set<String> namen = new java.util.LinkedHashSet<>();
        for (String[] rr : com.dan.brocken.app.Dauerlauf.KINO) for (String n : rr) { com.dan.brocken.scene.Regie.Book b = com.dan.brocken.scene.Regie.byName(n); if (b != null) namen.add(b.name()); }
        check(namen.size() == 4 && namen.size() == com.dan.brocken.scene.Regie.DATEIEN.length, "Dauerlauf: in zwei Runden jedes Regiebuch einmal im Kino (" + String.join(", ", namen) + ")");
        java.util.List<com.dan.brocken.app.Dauerlauf.Punkt> pg = new java.util.ArrayList<>();
        for (int i = 0; i < 400; i++) pg.add(new com.dan.brocken.app.Dauerlauf.Punkt(i * 5 / 60.0, 55 + 5 * Math.sin(i), 12, 4, 1500, 900, 60, 30, 2, 0, 0, i < 200 ? 1 : 2));
        java.util.List<double[]> rg = java.util.List.of(new double[]{16, 900, 2, 1, 60}, new double[]{32, 920, 2, 1, 61});
        java.util.List<String[]> ug = com.dan.brocken.app.Dauerlauf.urteil(pg, rg, 0, 0, 1.0, 0);
        java.util.List<double[]> rb = java.util.List.of(new double[]{16, 900, 2, 1, 60}, new double[]{32, 1200, 1, 1, 80});
        java.util.List<com.dan.brocken.app.Dauerlauf.Punkt> pb = new java.util.ArrayList<>();
        for (com.dan.brocken.app.Dauerlauf.Punkt x : pg) pb.add(new com.dan.brocken.app.Dauerlauf.Punkt(x.minute(), 20, 40, 4, 1500, 900, 60, 250, 6, 3, 1, x.runde()));
        java.util.List<String[]> ub = com.dan.brocken.app.Dauerlauf.urteil(pb, rb, 2, 3, 0.95, 4);
        long gutOk = ug.stream().filter(u -> u[0].equals("ok")).count(), schlechtFehler = ub.stream().filter(u -> u[0].equals("FEHLER")).count();
        java.awt.image.BufferedImage db = com.dan.brocken.app.Dauerlauf.bild(pg, rg, ug, true, 32, "Mittel");
        check(gutOk == ug.size() && schlechtFehler == ub.size() && db.getWidth() == 1600,
                "Dauerlauf-Urteil: ein guter Lauf besteht in allen " + ug.size() + " Punkten, ein schlechter fällt in allen durch (Fehler, Runde, Bilder/s, Leck, Grenzen, Physik); Kurvenbild 1600 × 900");
        // Hilfslinien kommen nicht aus einer Sitzung zurück (Status_Phase17d: Rauchfäden nach „Weiter wie zuletzt“)
        Renderer rz = new Renderer();
        java.util.Map<String, String> alt = new java.util.LinkedHashMap<>(com.dan.brocken.data.Zustand.erfassen(rz));
        alt.put("renderer.showFaeden", "true");
        alt.put("renderer.showArrows", "true");
        alt.put("renderer.showMarks", "true");
        com.dan.brocken.data.Zustand.anwenden(rz, alt);
        check(!rz.showFaeden && !rz.showArrows && !rz.showMarks && !com.dan.brocken.data.Zustand.erfassen(rz).containsKey("renderer.showFaeden"),
                "Hilfslinien (Marken, Pfeile, Rauchfäden) werden nicht gesichert und kommen auch aus einer älteren Sitzung nicht zurück");
        rz.physik.stop();
        rz.windrechner.stop();
    }

    static void projekt() throws Exception {
        head("Projekt");
        File gi = new File(".gitignore");
        String t = gi.isFile() ? Files.readString(gi.toPath(), StandardCharsets.UTF_8) : "";
        check(t.contains("db/USER.sql") && t.contains("db/db.properties"), ".gitignore hält USER.sql und db.properties (Passwort im Klartext) heraus");
        check(t.contains("/templates/") && t.contains("/build/") && t.contains("/dist/"), ".gitignore hält templates, build und dist heraus");
        check(new File("docs/status").isDirectory(), "docs/status für die Statusbilder");
        check(new File("tools/JarPruefung.java").isFile(), "tools/JarPruefung.java für check-packages");
        // seit Phase 8: FStyle.jar bringt alte Kopien von ground, forest, road und river mit; Renderer.jar muss davor stehen
        String pp = Files.readString(new File("nbproject/project.properties").toPath(), StandardCharsets.ISO_8859_1);
        int iR = pp.indexOf("${file.reference.Renderer.jar}", pp.indexOf("javac.classpath")), iF = pp.indexOf("${file.reference.FStyle.jar}", pp.indexOf("javac.classpath"));
        check(iR > 0 && iF > 0 && iR < iF, "Renderer.jar steht im Klassenpfad vor FStyle.jar (das alte Kopien von ground, forest, road und river enthält)");
    }

    private Pruefung() { }
}
