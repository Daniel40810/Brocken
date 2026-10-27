package com.dan.brocken.wiring;

import com.dan.brocken.scene.Gipfel;
import com.dan.brocken.scene.Menschen;
import com.dan.brocken.scene.Terrain;
import com.dan.brocken.scene.Zugbetrieb;
import com.dan.figure.Person;
import com.dan.rail.Fahrzeug;
import com.dan.rail.Track;
import com.dan.rail.Train;
import com.dan.soundscape.Aeolian;
import com.dan.soundscape.AudioOut;
import com.dan.soundscape.Crowd;
import com.dan.soundscape.Edge;
import com.dan.soundscape.Soundscape;
import com.dan.soundscape.Steam;
import com.dan.weather.Weather;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Phase 15: der Klang des Bergs ({@code com.dan.soundscape}) mit dem Feld und der Szene verdrahtet. Je Bild bekommt der
 * Klang das Ohr (Kamera), das Wetter der Regie, die Züge, die Menschen am Gipfel und was am Berg geschieht; er rechnet
 * im eigenen Faden des Lautsprechers ({@link AudioOut}) oder für eine Datei ({@code Tempo -Dklang=…}).
 * <ul>
 * <li><b>Wind</b> aus dem Windfeld ({@code wind = wiring::windAt}): am Gipfel laut, im Wald und im Lee leise; er singt an
 * Mast, Geländer und Antennen ({@link Aeolian}) und heult an Lamellen, Fugen und Hausecken ({@link Edge}); er trägt den
 * Schall (mit dem Wind lauter, gegen ihn leiser).</li>
 * <li><b>Züge</b>: jede Lok ist eine {@link Steam} mit Auspuff im Takt der Treibräder, Rollen, Schienenstößen, Bremsen,
 * Kurvenkreischen und im Stand Bläser, Luftpumpe, Sicherheitsventil; die Pfiffe des Zugbetriebs (Abfahrt, Übergänge)
 * klingen am Schornstein.</li>
 * <li><b>Fallendes Eis</b>: fällt eine Fahne Raueis ({@link Raueis#gefallen}) oder taut und rüttelt der Wind an dickem
 * Eis an Mast und Geländer, klirrt es dort.</li>
 * <li><b>Vögel</b> nach Jahreszeit, Tageszeit, Ort und Wetter [G]: Ringdrossel und Wiesenpieper am Gipfel, der
 * Hausrotschwanz an den Häusern, Buchfink, Wintergoldhähnchen und Fichtenkreuzschnabel im Fichtenwald, nachts im
 * Spätwinter der Raufußkauz; der Kolkrabe ruft, wo die Raben fliegen ({@link #rabe}), und ab und zu fern.</li>
 * <li><b>Stimmen</b>: die Leute am Gipfelstein, am Bahnsteig und um das Ohr als Mengen ({@link Crowd}).</li>
 * <li><b>Gewitter</b>: in der Lage „Gewitter“ ab und zu Donner aus 1 bis 8 km.</li>
 * <li><b>Walpurgisnacht</b>: in der Nacht zum 1. Mai (30. April 22 Uhr bis 1. Mai 3 Uhr) oder von Hand die Stimme der
 * Sage: der Wind ruft, fern Trommeln, Gelächter und Kreischen in der Luft.</li>
 * </ul>
 */
public final class KlangBrocken {
    public final Soundscape klang = new Soundscape();
    private final Wiring wiring;
    private final Terrain terrain;
    private final Gipfel gipfel;
    private final Zugbetrieb zuege;
    private final Menschen menschen;
    private AudioOut out;
    /** Ton an (der Lautsprecher läuft), Walpurgisnacht von Hand, automatisch nach dem Kalender. */
    public volatile boolean an, walpurgisHand, walpurgisKalender = true;
    /** Lautstärke 0..2. */
    public volatile double lautstaerke = 1;
    /** Phase 16: das Rauschen des Winds am Ohr (dB; −4 nach deinem Ohr: etwas weniger Wind). */
    public volatile double windDb = -4;
    /** Phase 16: der Ton im Kino folgt dem Schwarz (0..1; sonst 1). */
    public volatile double kinoTon = 1;
    private final Map<Train, Steam> loks = new HashMap<>();
    private final Crowd amStein, amBahnsteig, umsOhr;
    private final double[] bahnsteig;
    private final Random rnd = new Random(1141);
    private int gefallen = -1;
    private double eisWarte = 6, donnerWarte = 30, rabeWarte = 60;
    private final double[] vogelWarte = new double[VOEGEL.length];
    private double walpurgis;
    /** Zuletzt gerufene Vögel (für die Anzeige). */
    private volatile String vogelText = "";
    private long voegel, eisFaelle, pfiffe;

    /** Vögel des Bergs, ihre Rufe je Minute bei bester Lage [G]. */
    static final Soundscape.Kind[] VOEGEL = {Soundscape.Kind.RINGDROSSEL, Soundscape.Kind.WIESENPIEPER, Soundscape.Kind.HAUSROTSCHWANZ,
            Soundscape.Kind.BUCHFINK, Soundscape.Kind.WINTERGOLDHAEHNCHEN, Soundscape.Kind.KREUZSCHNABEL, Soundscape.Kind.RAUFUSSKAUZ};
    static final double[] JE_MINUTE = {3, 1.5, 2, 4, 3, 0.8, 1.2};

    public KlangBrocken(Wiring w, Terrain t, Gipfel g, Zugbetrieb z, Menschen m) {
        wiring = w;
        terrain = t;
        gipfel = g;
        zuege = z;
        menschen = m;
        klang.wind = w::windAt;
        klang.waveHeight = 0;
        klang.waterLevel = -1000;
        klang.windCarry = true;
        klang.obstruction = 0.8;     // Wald, Fels und Kuppe über Land
        klang.roughness = 0.5;
        klang.seed = 1141;
        klang.quality = Soundscape.Quality.MEDIUM;
        klang.autoGainMaxDb = 14;   // eine stille Nacht wird hörbar, aber ein Pfiff soll nicht in den Begrenzer schlagen
        klang.walpurgisFromDeg = 120;   // aus dem Tal nach Schierke
        double[] st = g != null && g.stein != null ? g.stein : new double[]{0, 1141, 0};
        amStein = new Crowd("Gipfelstein", st[0], st[1] + 1.6, st[2], 0);
        amStein.radius = 12;
        bahnsteig = bahnsteig(z);
        amBahnsteig = new Crowd("Bahnsteig", bahnsteig[0], bahnsteig[1] + 1.6, bahnsteig[2], 0);
        amBahnsteig.radius = 25;
        umsOhr = new Crowd("um das Ohr", 0, 0, 0, 0);
        umsOhr.radius = 6;
        klang.crowds.add(amStein);
        klang.crowds.add(amBahnsteig);
        klang.crowds.add(umsOhr);
        if (g != null) koerper(g);
        for (int i = 0; i < vogelWarte.length; i++) vogelWarte[i] = 5 + 20 * rnd.nextDouble();
    }

    /** Mitte des Bahnsteigs am Brocken (sonst der Gipfel). */
    private static double[] bahnsteig(Zugbetrieb z) {
        if (z != null)
            for (Track.Station s : z.gleis.stations)
                if (s.name().contains("Brocken")) { Track.Pose p = z.gleis.at(s.s()); return new double[]{p.x(), p.y(), p.z()}; }
        return new double[]{0, 1125, 60};
    }

    /** Singende Körper und heulende Kanten am Gipfel [G nach Fotos]. */
    private void koerper(Gipfel g) {
        for (Gipfel.Teil t : g.teile) {
            switch (t.art()) {
                case "mast" -> {
                    klang.bodies.add(new Aeolian("Mast der Wetterwarte", t.x(), t.y() + t.b() / 2, t.z(), t.a(), t.b()));
                    klang.bodies.add(new Aeolian("Messgeber", t.x(), t.y() + t.b(), t.z(), 0.02, 3));
                }
                case "sendeturm" -> {
                    klang.bodies.add(new Aeolian("Antennen am Sendeturm", t.x(), t.y() + 0.85 * t.b(), t.z(), 0.06, 40));
                    klang.edges.add(new Edge("Gitter am Sendeturm", t.x(), t.y() + 20, t.z(), 0.03, 12));
                }
                case "turm" -> {
                    klang.edges.add(new Edge("Lamellen am Turm", t.x(), t.y() + t.b() - 2, t.z(), 0.025, 6));
                    klang.edges.add(new Edge("Ecken des Turms", t.x() + t.a() / 2, t.y() + t.b() / 2, t.z() + t.a() / 2, 0.8, t.b()));
                }
                case "gipfelstein" -> {
                    klang.bodies.add(new Aeolian("Geländer am Gipfelstein", t.x() + 4, t.y() + 1, t.z(), 0.042, 18));
                    klang.edges.add(new Edge("Fugen am Gipfelstein", t.x(), t.y() + 1, t.z(), 0.012, 2));
                }
                case "radom" -> klang.edges.add(new Edge("Naht am Radom", t.x(), t.y() + t.a(), t.z(), 0.015, 8));
                default -> { }
            }
        }
        // die zwei weitesten Ecken jedes größeren Hauses
        for (Gipfel.Bau b : g.bauten) {
            if (b.flaeche() < 80 || b.ring.length < 3) continue;
            double[] a = b.ring[0], c = b.ring[0];
            double best = 0;
            for (double[] p : b.ring) for (double[] q : b.ring) { double d = Math.hypot(p[0] - q[0], p[1] - q[1]); if (d > best) { best = d; a = p; c = q; } }
            double h = Math.max(3, b.traufe);
            klang.edges.add(new Edge("Ecke " + b.name, a[0], b.boden + h / 2, a[1], 0.6, h));
            klang.edges.add(new Edge("Ecke " + b.name, c[0], b.boden + h / 2, c[1], 0.6, h));
        }
    }

    // ------------------------------------------------------------------ Lautsprecher

    /** Den Lautsprecher an oder aus (nicht im Kopflosen). */
    public synchronized void lautsprecher(boolean ein) {
        an = ein;
        if (ein) {
            if (out == null) out = new AudioOut(klang);
            out.start();
        } else if (out != null) out.stop();
    }

    public String fehler() { return out != null ? out.error() : ""; }

    // ------------------------------------------------------------------ je Bild

    /** Ein Raben-Ruf aus dem Schwarm ({@code com.dan.flock}): ein Kolkrabe dort. */
    public void rabe(double x, double y, double z, double staerke) {
        klang.event(Soundscape.Kind.KOLKRABE, x, y, z, Math.max(0.3, staerke));
        voegel++;
        vogelText = "Kolkrabe";
    }

    /**
     * Je Bild: Ohr (Szene), Blickrichtung (Grad von Nord), Ortszeit, Sonnenhöhe (Grad), Schritt in Szenenzeit (s; 0 =
     * Uhr steht, nichts Neues geschieht).
     */
    public void feed(double[] eye, double yawDeg, ZonedDateTime lt, double sunAlt, double dt) {
        klang.earX = eye[0];
        klang.earY = eye[1];
        klang.earZ = eye[2];
        klang.earYawDeg = yawDeg;
        klang.volume = lautstaerke * Math.max(0, Math.min(1, kinoTon));
        klang.windGainDb = windDb;
        Weather.Snapshot s = wiring.snapshot();
        double temp = s.hasHeight() ? wiring.temperatureAt(eye[1]) : wiring.airTempC;
        klang.temperatureC = temp;
        klang.windSpeed = wiring.windSpeed;
        klang.windFromDeg = wiring.windFromDeg;
        klang.gustiness = wiring.gustiness;
        // Regen rauscht, Schnee fällt still
        klang.rainRate = temp > 0.5 ? Math.max(0, wiring.rainRate) : 0;
        loks();
        if (dt > 0) {
            pfiffe();
            leute();
            eis(dt, temp);
            voegel(dt, lt, sunAlt, temp, s);
            donner(dt, s);
        }
        walpurgis(lt, dt);
    }

    /** Wind am Ohr (m/s). */
    double windAmOhr() { return klang.windAt(klang.earX, klang.earY, klang.earZ); }

    private void loks() {
        if (zuege == null) return;
        List<Train> l = zuege.zuege;
        loks.keySet().removeIf(t -> !l.contains(t));
        for (Steam st : klang.steams) if (!loks.containsValue(st)) klang.steams.remove(st);
        Track g = zuege.gleis;
        for (Train t : l) {
            Steam st = loks.computeIfAbsent(t, k -> { Steam n = new Steam(k.name); klang.steams.add(n); return n; });
            double[] p = Fahrzeug.schlot(g, t);
            st.x = p[0]; st.y = p[1]; st.z = p[2];
            // der Punkt des Zugs, der dem Ohr am nächsten ist (alle 5 m)
            double best = Double.MAX_VALUE, bs = 0;
            Track.Pose bp = null;
            for (double d = 0; d <= t.laenge; d += 5) {
                Track.Pose q = g.at(t.s - t.richtung * d);
                double dd = Math.hypot(Math.hypot(q.x() - klang.earX, q.z() - klang.earZ), q.y() + 1 - klang.earY);
                if (dd < best) { best = dd; bs = d; bp = q; }
            }
            if (bp != null) { st.rx = bp.x(); st.ry = bp.y() + 0.8; st.rz = bp.z(); st.nearest = bs; }
            st.speed = t.v;
            st.beats = t.schlaege();
            st.load = t.last();
            st.standing = !t.faehrt || t.haelt() || t.v < 0.05;
            st.braking = "bremst".equals(t.zustand()) ? 0.8 : 0;
            // enge Bögen: unter 120 m Radius kreischen die Spurkränze, bei 60 m voll [G]
            double k = Math.abs(g.at(t.s - t.richtung * t.laenge / 2).curvature());
            st.squeal = Math.max(0, Math.min(1, (k - 1 / 120.0) / (1 / 60.0 - 1 / 120.0))) * (t.v > 1 ? 1 : 0);
            st.wagons = t.wagen();
        }
    }

    private void pfiffe() {
        if (zuege == null) return;
        for (double[] p; (p = zuege.pfiffe.poll()) != null; ) {
            klang.event(Soundscape.Kind.PFEIFE, p[0], p[1] + 0.5, p[2], 1, p[3]);
            pfiffe++;
        }
    }

    private void leute() {
        if (menschen == null) return;
        int stein = 0, steig = 0, nah = 0;
        double[] st = gipfel != null && gipfel.stein != null ? gipfel.stein : new double[]{0, 0, 0};
        for (Person p : menschen.personen()) {
            if (Math.hypot(p.x - st[0], p.z - st[2]) < 25) stein++;
            else if (Math.hypot(p.x - bahnsteig[0], p.z - bahnsteig[2]) < 45) steig++;
            if (Math.hypot(p.x - klang.earX, p.z - klang.earZ) < 12) nah++;
        }
        // Wandernde reden weniger als Leute, die stehen und schauen: die Hälfte spricht [G]
        amStein.people = 0.6 * stein;
        amBahnsteig.people = 0.6 * steig;
        umsOhr.people = 0.5 * nah;
        umsOhr.x = klang.earX + 3;
        umsOhr.y = klang.earY;
        umsOhr.z = klang.earZ;
    }

    /** Fallendes Eis: Fahnen, die die Raueis-Rechnung abwirft, und Stücke, die Wind und Tauen von dickem Eis lösen. */
    private void eis(double dt, double temp) {
        Raueis r = wiring.raueis;
        if (gefallen < 0) gefallen = r.gefallen;
        double h = klang.earY;
        if (r.gefallen > gefallen) {
            gefallen = r.gefallen;
            if (!Double.isNaN(r.gefallenHoehe) && Math.abs(r.gefallenHoehe - h) < 80) eisFall(Math.max(0.2, r.gefallenKg * 2));
        }
        double dick = r.dicke(Raueis.MAST, h);
        double u = windAmOhr();
        if (dick > 0.004) {
            // je mehr Eis, Wind und Tauwetter, desto öfter (bei 1 cm am Mast und 10 m/s um alle 35 s) [G]
            double rate = 0.5 * Math.min(1, dick / 0.03) * (Math.max(0, u - 4) / 6 + (temp > -1 ? 1.5 : 0));
            eisWarte -= dt * rate;
            if (eisWarte <= 0) { eisFall(0.05 + 2 * Math.min(1, dick / 0.05) * rnd.nextDouble()); eisWarte = 8 + 20 * rnd.nextDouble(); }
        }
    }

    private void eisFall(double kg) {
        // dort, wo Eis hängt: an einem Mast, Geländer oder einer Antenne in 150 m, sonst an Bäumen um das Ohr
        double x = Double.NaN, y = 0, z = 0;
        for (Aeolian b : klang.bodies)
            if (Math.hypot(b.x() - klang.earX, b.z() - klang.earZ) < 150 && rnd.nextDouble() < 0.6) { x = b.x() + 2 * rnd.nextGaussian(); y = terrain.height(b.x(), b.z()); z = b.z() + 2 * rnd.nextGaussian(); break; }
        if (Double.isNaN(x)) {
            double a = rnd.nextDouble() * 2 * Math.PI, d = 8 + 40 * rnd.nextDouble();
            x = klang.earX + d * Math.sin(a);
            z = klang.earZ - d * Math.cos(a);
            y = terrain.height(x, z) + 1;
        }
        klang.event(Soundscape.Kind.EIS, x, y, z, 1, kg);
        eisFaelle++;
    }

    /** Wie sehr der Ort um das Ohr Wald ist (0..1; neun Punkte in 80 m). */
    double wald(double x, double z) {
        int n = 0;
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) if ((terrain.artAt(x + 80 * i, z + 80 * j) & 0xFF) == Terrain.WALD) n++;
        return n / 9.0;
    }

    /**
     * Die Vögel [G]: Jahreszeit (Monat), Tageszeit (Morgen am stärksten, nachts nur der Kauz), Ort (Wald oder offen,
     * Höhe, Häuser), Wetter (Sturm, Regen, Frost und Nebel machen still).
     */
    private void voegel(double dt, ZonedDateTime lt, double sunAlt, double temp, Weather.Snapshot s) {
        int mon = lt.getMonthValue();
        double stunde = lt.getHour() + lt.getMinute() / 60.0;
        double ex = klang.earX, ez = klang.earZ, hoehe = klang.earY;
        double wald = wald(ex, ez), offen = 1 - wald;
        double haus = gipfel != null ? Math.max(0, 1 - gipfel.abstandBau(ex, ez) / 120) : 0;
        double u = windAmOhr();
        double wetter = smooth(17, 7, u) * (wiring.rainRate > 0.5 ? 0.3 : 1) * (temp < 0 ? 0.4 : 1) * (wiring.visibilityKm < 0.3 ? 0.6 : 1);
        double tag = sunAlt < -10 ? 0 : smooth(-10, -2, sunAlt) * (1 + 1.5 * Math.exp(-Math.pow((stunde - 6) / 1.8, 2)) + 0.4 * Math.exp(-Math.pow((stunde - 19.5) / 1.2, 2)));
        double nacht = smooth(-4, -10, sunAlt);
        for (int i = 0; i < VOEGEL.length; i++) {
            Soundscape.Kind k = VOEGEL[i];
            double lage = switch (k) {
                case RINGDROSSEL -> saison(mon, 4, 7) * tag * smooth(950, 1050, hoehe) * (0.4 + 0.6 * Math.min(1, 2 * Math.min(wald, offen) + 0.3));
                case WIESENPIEPER -> saison(mon, 4, 7) * tag * smooth(1000, 1080, hoehe) * offen * (sunAlt > 3 ? 1 : 0.3);
                case HAUSROTSCHWANZ -> saison(mon, 3, 9) * tag * haus;
                case BUCHFINK -> saison(mon, 3, 7) * tag * wald * smooth(1100, 950, hoehe);
                case WINTERGOLDHAEHNCHEN -> (saison(mon, 3, 7) * 0.7 + 0.3) * tag * wald;
                case KREUZSCHNABEL -> 0.7 * tag * (0.3 + wald);
                case RAUFUSSKAUZ -> saison(mon, 2, 4) * nacht * wald;
                default -> 0;
            };
            double rate = JE_MINUTE[i] / 60 * lage * (k == Soundscape.Kind.RAUFUSSKAUZ || k == Soundscape.Kind.KREUZSCHNABEL ? Math.max(wetter, 0.5 * smooth(17, 7, u)) : wetter);
            if (rate <= 1e-6) continue;
            vogelWarte[i] -= dt * rate * 30;
            if (vogelWarte[i] > 0) continue;
            vogelWarte[i] = 30 * -Math.log(1 - rnd.nextDouble());   // im Mittel ein Ruf je 1 / rate s
            double a = rnd.nextDouble() * 2 * Math.PI, d = (k == Soundscape.Kind.HAUSROTSCHWANZ ? 15 : 25) + (k == Soundscape.Kind.RAUFUSSKAUZ ? 250 : 130) * rnd.nextDouble();
            double x = ex + d * Math.sin(a), z = ez - d * Math.cos(a);
            if (k == Soundscape.Kind.HAUSROTSCHWANZ && gipfel != null && !gipfel.bauten.isEmpty()) {
                Gipfel.Bau b = gipfel.bauten.get(rnd.nextInt(gipfel.bauten.size()));
                double[] m = b.mitte();
                x = m[0]; z = m[1];
            }
            double y = terrain.height(x, z) + switch (k) {
                case WIESENPIEPER -> 15 + 25 * rnd.nextDouble();
                case KREUZSCHNABEL -> 20 + 15 * rnd.nextDouble();
                case HAUSROTSCHWANZ -> 8;
                default -> 4 + 10 * rnd.nextDouble();
            };
            klang.event(k, x, y, z, 0.6 + 0.4 * rnd.nextDouble(), k == Soundscape.Kind.KREUZSCHNABEL ? 2 + rnd.nextInt(6) : 0);
            voegel++;
            vogelText = name(k);
        }
        // ein ferner Kolkrabe (die nahen ruft der Schwarm)
        rabeWarte -= dt * tag * wetter;
        if (rabeWarte <= 0) {
            rabeWarte = 60 + 180 * rnd.nextDouble();
            double a = rnd.nextDouble() * 2 * Math.PI, d = 300 + 900 * rnd.nextDouble();
            double x = ex + d * Math.sin(a), z = ez - d * Math.cos(a);
            rabe(x, terrain.height(x, z) + 40, z, 1);
        }
    }

    /** 1 in den Monaten a bis b (Gesang), weich an den Rändern. */
    public static double saison(int mon, int a, int b) { return mon >= a && mon <= b ? 1 : mon == a - 1 || mon == b + 1 ? 0.3 : 0; }

    static String name(Soundscape.Kind k) {
        return switch (k) {
            case RINGDROSSEL -> "Ringdrossel"; case WIESENPIEPER -> "Wiesenpieper"; case HAUSROTSCHWANZ -> "Hausrotschwanz";
            case BUCHFINK -> "Buchfink"; case WINTERGOLDHAEHNCHEN -> "Wintergoldhähnchen"; case KREUZSCHNABEL -> "Fichtenkreuzschnabel";
            case RAUFUSSKAUZ -> "Raufußkauz"; default -> k.name();
        };
    }

    private void donner(double dt, Weather.Snapshot s) {
        if (s.phase() != Weather.Phase.GEWITTER) return;
        donnerWarte -= dt;
        if (donnerWarte > 0) return;
        donnerWarte = 20 + 60 * rnd.nextDouble();
        double a = rnd.nextDouble() * 2 * Math.PI, d = 1000 + 7000 * rnd.nextDouble();
        klang.event(Soundscape.Kind.DONNER, klang.earX + d * Math.sin(a), klang.earY + 500, klang.earZ - d * Math.cos(a), Math.max(0.1, 1 - d / 9000));
    }

    /** Die Nacht zum 1. Mai: 30. April 22 Uhr bis 1. Mai 3 Uhr, je eine halbe Stunde Ein- und Ausklang; oder von Hand. */
    public static double walpurgisnacht(ZonedDateTime lt) {
        double h;
        if (lt.getMonthValue() == 4 && lt.getDayOfMonth() == 30) h = lt.getHour() + lt.getMinute() / 60.0 - 24;
        else if (lt.getMonthValue() == 5 && lt.getDayOfMonth() == 1) h = lt.getHour() + lt.getMinute() / 60.0;
        else return 0;
        return smooth(-2, -1.5, h) * smooth(3, 2.5, h);
    }

    private void walpurgis(ZonedDateTime lt, double dt) {
        double ziel = Math.max(walpurgisHand ? 1 : 0, walpurgisKalender ? walpurgisnacht(lt) : 0);
        walpurgis = ziel;
        klang.walpurgis = walpurgis;
    }

    /** Ob die Walpurgisnacht gerade ruft (0..1). */
    public double walpurgis() { return walpurgis; }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private volatile String zeile = "Klang: –";
    private volatile long zeileUm;

    /**
     * „Klang: Wind 64 dB · Maschine 71 dB · … · zuletzt Ringdrossel“; einmal je Sekunde neu (die Anzeige soll nicht je
     * Bild neu setzen müssen).
     */
    public String zeile() {
        long now = System.nanoTime();
        if (now - zeileUm < 1_000_000_000L) return zeile;
        zeileUm = now;
        String w = walpurgis > 0.01 ? " · Walpurgisnacht" : "";
        String v = vogelText.isEmpty() ? "" : " · zuletzt " + vogelText;
        String e = klang.lastVoice();
        String f = fehler();
        zeile = "Klang: " + klang.describe() + (e.isEmpty() ? "" : " · " + e) + v + w + (an ? (f.isEmpty() ? "" : " · Ton: " + f) : " · Lautsprecher aus");
        return zeile;
    }

    /** Zähler für Prüfungen: Vögel, Eisfälle, Pfiffe. */
    public long[] zaehler() { return new long[]{voegel, eisFaelle, pfiffe}; }

    public String describe() {
        return String.format(Locale.GERMAN, "Klang: %d Körper, %d Kanten, %d Loks; %d Vögel, %d Eisfälle, %d Pfiffe gerufen",
                klang.bodies.size(), klang.edges.size(), klang.steams.size(), voegel, eisFaelle, pfiffe);
    }
}
