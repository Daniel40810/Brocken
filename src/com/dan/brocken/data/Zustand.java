package com.dan.brocken.data;

import com.dan.brocken.camera.Camera;
import com.dan.brocken.gl.Quality;
import com.dan.brocken.gl.Renderer;
import com.dan.store.Felder;
import com.dan.store.Zahl;
import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Der Zustand der Szene als Text (F37 A, „Weiter wie zuletzt“): Uhr und Zeitraffer, Kamera, Wetterregie (Regiebuch mit
 * Beginn oder freie Lage, Handregler), Qualität, Ton mit Wind am Ohr, die Ebenen und Regler des Renderers, Hände an
 * Nebel, Halo, Tröpfchen, Schnee und Raueis, dazu das laufende Regiebuch mit Stelle. Dasselbe Format dient für
 * gemerkte Ansichten und die Dateien ohne Datenbank.
 * <p>
 * Was die Regie jedes Bild neu setzt (Wind, Wolken, Nebel aus dem Wetter), kommt nicht zurück: es folgt aus Uhr und
 * Regie. Was gerade lief (Mitfahrt, Treiben, ein Regiebuch), beginnt man neu; das Regiebuch nennt der HUD.
 * <p>
 * Schlüssel der Form {@code teil.feld}: {@code renderer.showNebel}, {@code kamera.x}, {@code regie.hand.WIND}.
 */
public final class Zustand {
    private Zustand() { }

    /** Felder des Renderers, die zum Lauf gehören (Anfragen, Standbild, Auflösung von selbst geregelt). */
    static final Set<String> OHNE_RENDERER = Set.of("frozen", "photoRequest", "photoName", "renderScale", "ohneBlatt", "fotoVorlauf",
            // seit Phase 18: die Hilfslinien (Marken, Pfeile, Rauchfäden) kommen nie aus einer Sitzung zurück, auch nicht aus einer älteren
            "showMarks", "showArrows", "showFaeden");
    /** Felder der Verdrahtung, die von Hand gesetzt werden (alles andere setzt die Regie). */
    static final Set<String> WIRING = Set.of("regie", "haloHand", "tropfenHand", "schneeHand", "jahrFuehrt", "meerBasisHand", "meerTopHand", "valleyHand");
    static final Set<String> KLANG = Set.of("an", "walpurgisHand", "walpurgisKalender", "lautstaerke", "windDb");

    private static Map<String, String> teil(Object o, Set<String> nur, Set<String> ohne) {
        LinkedHashMap<String, String> m = Felder.lesen(o, ohne);
        if (nur != null) m.keySet().retainAll(nur);
        return m;
    }

    /** Erfasst den Zustand (aus dem Swing-Faden oder dem Faden der Daten; die Felder sind volatile). */
    public static LinkedHashMap<String, String> erfassen(Renderer r) {
        LinkedHashMap<String, String> z = new LinkedHashMap<>();
        z.put("app.version", Renderer.VERSION);
        z.put("app.qualitaet", r.quality().name());
        z.put("uhr.ms", Long.toString((long) Math.floor(r.clock.epochMillis())));
        z.put("uhr.text", Daten.text(r.clock.epochMillis()));
        z.put("uhr.lapse", Zahl.text(r.clock.lapse));
        Camera c = r.camera;
        z.put("kamera.modus", c.mode == Camera.Mode.KREISEN ? "KREISEN" : "FREI");
        z.put("kamera.kreisen", Boolean.toString(c.autoOrbit));
        double[] k = kamera(r);
        String[] n = {"x", "y", "z", "gier", "nick"};
        for (int i = 0; i < 5; i++) z.put("kamera." + n[i], Zahl.text(Math.round(k[i] * 1000) / 1000.0));
        z.put("kamera.ziel", Zahl.text(c.tx) + " " + Zahl.text(c.ty) + " " + Zahl.text(c.tz) + " " + Zahl.text(c.yaw) + " " + Zahl.text(c.pitch) + " " + Zahl.text(c.dist));
        if (c.mitfahrt != null) z.put("kamera.mitfahrt", "true");
        Weather w = r.wiring.weather;
        Weather.Snapshot ws = w.snapshot();
        z.put("regie.an", Boolean.toString(r.wiring.regie));
        z.put("regie.lage", ws != null ? ws.phase().name() : "KLAR");
        Scenario sc = w.scenario();
        if (sc != null && ws != null && ws.step() >= 0) {
            z.put("regie.text", sc.toText());
            z.put("regie.beginn", Zahl.text(w.scenarioStart()));
        }
        double[] h = w.handValues();
        for (int i = 0; i < h.length; i++) if (!Double.isNaN(h[i])) z.put("regie.hand." + Weather.Var.values()[i].name(), Zahl.text(h[i]));
        var bk = r.regie.book();
        if (bk != null) {
            z.put("regiebuch.name", bk.name());
            z.put("regiebuch.sekunde", Zahl.text(Math.round(r.regie.time() * 100) / 100.0));
            if (r.kino.laeuft()) z.put("regiebuch.kino", "true");
        }
        if (!Double.isNaN(r.wiring.raueis.hand)) z.put("raueis.hand", Zahl.text(r.wiring.raueis.hand));
        for (Map.Entry<String, String> f : teil(r, null, OHNE_RENDERER).entrySet()) z.put("renderer." + f.getKey(), f.getValue());
        for (Map.Entry<String, String> f : teil(r.wiring, WIRING, null).entrySet()) z.put("wiring." + f.getKey(), f.getValue());
        for (Map.Entry<String, String> f : teil(r.klang, KLANG, null).entrySet()) z.put("klang." + f.getKey(), f.getValue());
        return z;
    }

    /** Das laufende Objekt einer Klasse mit Presets (Startdaten.PAKETE und STANDARD, einfacher Name) in der Szene, oder null. */
    public static Object objekt(Renderer r, String klasse) {
        return switch (klasse) {
            case "Clouds" -> r.wetter.clouds;
            case "TerrainFog" -> r.nebel;
            case "WindField" -> r.wiring.wind;
            case "Cliff" -> r.fels.granit;
            case "Soundscape" -> r.klang.klang;
            case "Droplets" -> r.wiring.tropfen;
            case "Flock" -> r.leben.raben;
            case "Sky" -> r.wetter.sky;
            case "Rime" -> r.wiring.raueis.rime;
            case "Smoke" -> r.leben.rauch;
            case "Kino" -> r.kino;
            default -> null;
        };
    }

    /** Die Spalten von BR_SITZUNG aus einem Zustand (für {@code Ablage.Sicherung}). */
    public static Map<String, String> spalten(Map<String, String> z) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(Speicher.SZENE_MS, z.getOrDefault("uhr.ms", ""));
        m.put(Speicher.ZEITRAFFER, z.getOrDefault("uhr.lapse", ""));
        m.put(Speicher.LAGE, z.getOrDefault("regie.lage", ""));
        m.put(Speicher.KAMERA, z.getOrDefault("kamera.modus", ""));
        m.put(Speicher.AUGE_X, z.getOrDefault("kamera.x", ""));
        m.put(Speicher.AUGE_Z, z.getOrDefault("kamera.z", ""));
        if (z.containsKey("regiebuch.name")) { m.put(Speicher.REGIEBUCH, z.get("regiebuch.name")); m.put(Speicher.REGIE_SEKUNDE, z.get("regiebuch.sekunde")); }
        return m;
    }

    /** Kamera für Ansichten: {x, y, z, Gier, Nick} aus dem laufenden Bild. */
    public static double[] kamera(Renderer r) {
        Camera c = r.camera;
        double[] e = c.eye, f = c.fwd;
        double nick = Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, f[1]))));
        return new double[]{e[0], e[1], e[2], c.heading(), nick};
    }

    /** Szenenzeit des Zustands (ms) oder null. */
    public static Long szeneMs(Map<String, String> z) {
        double v = Zahl.lies(z.get("uhr.ms"));
        return Double.isNaN(v) ? null : (long) v;
    }

    public static Quality qualitaet(Map<String, String> z) {
        try { return Quality.valueOf(z.get("app.qualitaet")); } catch (RuntimeException e) { return null; }
    }

    /**
     * Stellt den Zustand her (aus dem Swing-Faden). Die Qualität stellt der Aufrufer ein; hier alles andere. Gibt
     * zurück, was nicht ging.
     */
    public static List<String> anwenden(Renderer r, Map<String, String> z) {
        List<String> fehler = new ArrayList<>();
        Object[][] teile = {{"renderer", r, null, OHNE_RENDERER}, {"wiring", r.wiring, WIRING, null}, {"klang", r.klang, KLANG, null}};
        for (Object[] t : teile) {
            Map<String, String> m = Felder.teil(z, (String) t[0]);
            m.keySet().removeIf(x -> x.contains("."));
            @SuppressWarnings("unchecked") Set<String> nur = (Set<String>) t[2];
            @SuppressWarnings("unchecked") Set<String> ohne = (Set<String>) t[3];
            if (nur != null) m.keySet().retainAll(nur);
            for (String f : Felder.schreiben(t[1], m, ohne)) fehler.add(t[0] + "." + f);
        }
        Long ms = szeneMs(z);
        if (ms != null) r.clock.setEpochMillis(ms);
        double lapse = Zahl.lies(z.get("uhr.lapse"));
        if (!Double.isNaN(lapse)) r.clock.lapse = lapse;
        double rh = Zahl.lies(z.get("raueis.hand"));
        r.wiring.raueis.hand = rh;
        Camera c = r.camera;
        c.stopFahrt();
        c.stopMitfahrt();
        String[] ziel = z.getOrDefault("kamera.ziel", "").split(" ");
        if ("KREISEN".equals(z.get("kamera.modus")) && ziel.length == 6) {
            c.mode = Camera.Mode.KREISEN;
            c.lookAt(Zahl.lies(ziel[0]), Zahl.lies(ziel[1]), Zahl.lies(ziel[2]), Zahl.lies(ziel[3]), Zahl.lies(ziel[4]), Zahl.lies(ziel[5]));
            c.autoOrbit = "true".equals(z.get("kamera.kreisen"));
            c.snap();
        } else {
            double x = Zahl.lies(z.get("kamera.x")), y = Zahl.lies(z.get("kamera.y")), zz = Zahl.lies(z.get("kamera.z"));
            if (!Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(zz)) {
                c.mode = Camera.Mode.FREI;
                c.place(x, y, zz, Zahl.lies(z.get("kamera.gier")), Zahl.lies(z.get("kamera.nick")));
                c.snap();
            }
        }
        Weather w = r.wiring.weather;
        double now = r.clock.epochMillis() / 1000.0;
        String text = z.get("regie.text");
        boolean gestartet = false;
        if (text != null) {
            try {
                Scenario sc = Scenario.parse(text);
                double b = Zahl.lies(z.get("regie.beginn"));
                w.start(sc, Double.isNaN(b) ? now : b);
                gestartet = true;
            } catch (RuntimeException ex) {
                fehler.add("regie.text: " + ex.getMessage());
            }
        }
        if (!gestartet) {
            Weather.Phase p;
            try { p = Weather.Phase.valueOf(z.getOrDefault("regie.lage", "KLAR")); } catch (IllegalArgumentException ex) { p = Weather.Phase.KLAR; }
            w.startFree(now, p);
        }
        r.wiring.resumeRegie();
        for (Weather.Var v : Weather.Var.values()) {
            double hv = Zahl.lies(z.get("regie.hand." + v.name()));
            if (!Double.isNaN(hv)) r.wiring.hand(v, hv);
        }
        r.wiring.regie = !"false".equals(z.get("regie.an"));
        r.nachSprung();
        return fehler;
    }
}
