package com.dan.brocken.tools;

import com.dan.brocken.camera.Ansichten;
import com.dan.brocken.gl.Quality;
import com.dan.brocken.gl.Renderer;
import com.dan.brocken.scene.SceneClock;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.io.File;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Tempo-Probe und Standbild ohne Fenster (aus Geiranger, entschlackt). Rechnet die Szene unsichtbar in fester Größe,
 * misst die Wanduhr je Bild bis glFinish und die Zeit jedes Durchgangs auf der Grafikkarte und schreibt auf Wunsch ein
 * PNG. So lässt sich jede Phase vorher und nachher vergleichen und im Leistungsbudget (Werkbuch, Abschnitt 11)
 * eintragen.
 * <pre>
 * Tempo [bilder] [breite] [höhe] [png|-] [NIEDRIG|MITTEL|HOCH|ULTRA] [uhrzeit] [datum JJJJ-MM-TT]
 * Tempo 120 1920 1080 docs/status/Status_Phase1.png MITTEL 9 2026-10-21
 * </pre>
 * Schalter (als -D… aus Ant oder als Umgebungsvariable in Großbuchstaben): {@code ansicht} = Name oder Taste einer
 * Ansicht (Gipfelstein, Bahnsteig, Goetheweg, Schierke, UeberdemNebelmeer, Vogelauge, Teufelskanzel, Goethemoor), {@code kamera} =
 * x,z,Richtung,Neigung,Höhe über Grund (frei stehend), {@code hud} = kurz|voll|aus, {@code marken} = an|aus (seit Phase 17 aus); seit Phase 3
 * {@code wetter} = Regiebuch („Über dem Nebelmeer“ oder ohne Umlaute UeberdemNebelmeer) oder Lage (INVERSION, STAU, …) und {@code regiestunde} = Stunden nach
 * dem Beginn des Regiebuchs, {@code wolken} = aus; seit Phase 4 {@code pfeile} = aus, {@code faeden} = aus, {@code pfeilhoehe} = m;
 * seit Phase 5 {@code handwind} = Richtung,Stärke (an der Wetterwarte), {@code gras}, {@code rauch}, {@code raben} = aus,
 * {@code vorlauf} = Sekunden, die die Kolkraben vor den Bildern fliegen, und {@code kamera} = rabe[,Abstand] (das Auge
 * folgt dem höchsten Kolkraben im Aufwind); seit Phase 6 {@code nebel} = aus; seit Phase 7 {@code meer} = Basis,Obergrenze
 * (m über NHN, das Nebelmeer von Hand), {@code meerauge} = Basis,m (die Obergrenze am Auge so viele m über ihm),
 * {@code blick} = gegenpunkt|sonne (nach dem Wetter: das Auge schaut in den
 * Gegenpunkt der Sonne oder zu ihr, die Neigung um {@code blickneigung} Grad versetzt), {@code halo} = 0..1,
 * {@code tropfen} = Halbmesser in µm, {@code glorie} = aus, {@code nebelaufloesung} = 1|2|3; seit Phase 8
 * {@code boden} = aus (Bodenkarte und Bodendecke), {@code fels} = aus (Granit, Felsburgen, Blockmeer) und die Ansichten
 * Teufelskanzel und Goethemoor; seit Phase 9 {@code wald} = aus (vor den Bildern werden die Netze gebacken und alle
 * Bäume um das Auge gepflanzt); seit Phase 10 {@code raueis} = aus | cm (von Hand an den Zweigen am Gipfel; sonst
 * nach der Regie, ein Regiebuch wird von seinem Beginn an durchgerechnet); seit Phase 11 {@code schnee} = aus | cm
 * (von Hand am Gipfel; sonst nach dem gewürfelten Jahr bis zum Datum); seit Phase 12 {@code gleis} = aus; seit Phase 13
 * {@code zug} = aus oder mit (Mitfahrt auf der Lok des nächsten Zugs, {@code zugblick} Grad zur Seite); seit Phase 14
 * {@code gipfel} = aus, {@code menschen} = aus, {@code kamera} = bahnsteig | gipfelstein | gipfel; seit Phase 15
 * {@code klang} = Datei (WAV: nach den Bildern läuft die Uhr {@code klangsekunden} s in Echtzeit, Schritte zu 50 ms, und
 * der Klang am Ohr der Kamera wird mitgerechnet, ohne Lautsprecher) und {@code walpurgis} = an (die Stimme von Hand); seit
 * Phase 16 {@code regie} = Name eines Regiebuchs (es läuft in Schritten zu 0,2 s durch, zu jedem Foto ein Bild, am Ende
 * das Blatt in docs/status).
 */
public final class Tempo {
    private Tempo() { }

    private static String opt(String prop) {
        String v = System.getProperty(prop);
        return v != null ? v : System.getenv(prop.toUpperCase(Locale.ROOT));
    }

    public static void main(String[] a) throws Exception {
        int frames = a.length > 0 ? Integer.parseInt(a[0]) : 120;
        int w = a.length > 1 ? Integer.parseInt(a[1]) : 1920, h = a.length > 2 ? Integer.parseInt(a[2]) : 1080;
        File png = a.length > 3 && !a[3].equals("-") ? new File(a[3]) : null;
        Quality q = a.length > 4 ? Quality.valueOf(a[4]) : Quality.MITTEL;
        double hour = a.length > 5 ? Double.parseDouble(a[5].replace(',', '.')) : SceneClock.START_HOUR;
        LocalDate day = a.length > 6 && !a[6].equals("-") ? LocalDate.parse(a[6]) : SceneClock.START_DATE;

        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        d.setAutoSwapBufferMode(false);
        SceneClock clock = new SceneClock(day, hour);
        clock.lapse = 0;
        Renderer r = new Renderer(clock);
        r.frozen = true;
        r.setQuality(q);
        r.autoScale = false;
        String hud = opt("hud");
        if ("aus".equals(hud)) r.showHud = false;
        if ("voll".equals(hud)) r.hudFull = true;
        if ("aus".equals(opt("marken"))) r.showMarks = false;
        if ("an".equals(opt("marken"))) r.showMarks = true;   // seit Phase 17 sind die Marken von Anfang an aus
        String we = opt("wetter");
        if (we != null && !we.isBlank()) {
            String rs = opt("regiestunde");
            String gesetzt = r.wetter(we, rs == null || rs.isBlank() ? 0 : Double.parseDouble(rs.replace(',', '.')));
            System.out.println(gesetzt == null ? "FEHLER: kein Wetter „" + we + "“" : "Wetter: " + gesetzt);
        }
        if ("aus".equals(opt("wolken"))) r.showClouds = false;
        if ("aus".equals(opt("pfeile"))) r.showArrows = false;
        if ("aus".equals(opt("faeden"))) r.showFaeden = false;
        if ("an".equals(opt("pfeile"))) r.showArrows = true;   // seit Phase 17a von Anfang an aus
        if ("an".equals(opt("faeden"))) r.showFaeden = true;
        String ph = opt("pfeilhoehe");
        if (ph != null && !ph.isBlank()) r.arrowHeight = Double.parseDouble(ph);
        String hw = opt("handwind");
        if (hw != null && !hw.isBlank()) { String[] p = hw.split(","); r.handWind(Double.parseDouble(p[0]), Double.parseDouble(p[1])); }
        if ("aus".equals(opt("gras"))) r.leben.showGras = false;
        if ("aus".equals(opt("rauch"))) r.leben.showRauch = false;
        if ("aus".equals(opt("raben"))) r.leben.showRaben = false;
        if ("aus".equals(opt("nebel"))) r.showNebel = false;
        // Phase 7: Nebelmeer, Halo, Tröpfchen, Glorie, Auflösung des Nebels von Hand
        String me = opt("meer");
        if (me != null && !me.isBlank()) {
            String[] p = me.split(",");
            r.wiring.meerBasisHand = Double.parseDouble(p[0]);
            r.wiring.meerTopHand = Double.parseDouble(p[1]);
            r.nebelJetzt();
        }
        String ha = opt("halo");
        if (ha != null && !ha.isBlank()) r.wiring.haloHand = Double.parseDouble(ha.replace(',', '.'));
        String tr = opt("tropfen");
        if (tr != null && !tr.isBlank()) r.wiring.tropfenHand = Double.parseDouble(tr.replace(',', '.'));
        if ("aus".equals(opt("glorie"))) r.showGlorie = false;
        if ("aus".equals(opt("boden"))) { r.showBoden = false; r.leben.showBoden = false; }
        if ("aus".equals(opt("fels"))) r.fels.showFels = false;
        if ("aus".equals(opt("wald"))) r.wald.showWald = false;
        // Phase 10: Raueis aus oder von Hand; sonst rechnet es das Regiebuch von seinem Beginn bis zur Regiestunde
        String re = opt("raueis");
        if ("aus".equals(re)) r.showRaueis = false;
        else if (re != null && !re.isBlank()) r.wiring.raueis.hand = Double.parseDouble(re.replace(',', '.')) / 100;
        r.raueisJetzt();
        System.out.println(r.raueisZeile());
        // Phase 11: Schnee aus oder von Hand; sonst das gewürfelte Jahr bis zum Datum
        String sn = opt("schnee");
        if ("aus".equals(sn)) r.showSchnee = false;
        else if (sn != null && !sn.isBlank()) r.wiring.schneeHand = Double.parseDouble(sn.replace(',', '.')) / 100;
        if ("aus".equals(opt("gleis"))) r.showGleis = false;
        if ("aus".equals(opt("zug"))) r.showZuege = false;
        if ("aus".equals(opt("gipfel"))) r.showGipfel = false;
        if ("aus".equals(opt("menschen"))) r.showMenschen = false;
        r.jahrJetzt();
        System.out.println(r.jahrZeile());
        String na = opt("nebelaufloesung");
        if (na != null && !na.isBlank()) r.nebelScale = Integer.parseInt(na.trim());
        String an = opt("ansicht"), ka = opt("kamera");
        if (ka != null && ka.startsWith("rabe")) {
            // erst das Feld, dann die Kolkraben fliegen lassen, dann das Auge zu ihnen
        } else if (ka != null && (ka.equals("bahnsteig") || ka.equals("gipfelstein") || ka.equals("gipfel"))) {
            if (!r.kameraGipfel(ka)) System.err.println("FEHLER: keine Ansicht „" + ka + "“");
        } else if (ka != null && !ka.isBlank()) {
            String[] p = ka.split(",");
            double x = Double.parseDouble(p[0]), z = Double.parseDouble(p[1]);
            double hd = p.length > 2 ? Double.parseDouble(p[2]) : 0, pt = p.length > 3 ? Double.parseDouble(p[3]) : 10, up = p.length > 4 ? Double.parseDouble(p[4]) : 2;
            r.camera.place(x, r.groundHeight(x, z) + up, z, hd, pt);
        } else if (an != null && !an.isBlank()) {
            Ansichten.Ansicht v = Ansichten.byName(an);
            if (v == null) System.err.println("FEHLER: keine Ansicht „" + an + "“");
            else r.placeView(v);
        }
        // Phase 7: das Nebelmeer an das Auge legen (Untergrenze, Obergrenze über dem Auge)
        String ma = opt("meerauge");
        if (ma != null && !ma.isBlank()) {
            String[] p = ma.split(",");
            double top = r.meerAmAuge(Double.parseDouble(p[0]), Double.parseDouble(p[1]));
            System.out.println(String.format(Locale.GERMAN, "Nebelmeer von Hand: Obergrenze im Mittel %.0f m, am Auge %.1f m über ihm", top, Double.parseDouble(p[1])));
        }
        // Phase 7: in den Gegenpunkt der Sonne schauen (das Gespenst) oder zu ihr
        String bl = opt("blick");
        if (bl != null && !bl.isBlank()) {
            double[] sp = r.clock.sun();
            String bn = opt("blickneigung");
            double extra = bn == null || bn.isBlank() ? 0 : Double.parseDouble(bn.replace(',', '.'));
            boolean gegen = bl.startsWith("gegen");
            double yaw = gegen ? (sp[1] + 180) % 360 : sp[1], pitch = (gegen ? sp[0] : -sp[0]) + extra;
            double[] e = r.camera.eye.clone();
            r.camera.place(e[0], e[1], e[2], yaw, pitch);
            System.out.println(String.format(Locale.GERMAN, "Blick %s: %03.0f°, Neigung %.1f° (Sonne %.1f° über der Kimm aus %03.0f°)",
                    gegen ? "in den Gegenpunkt" : "zur Sonne", yaw, pitch, sp[0], sp[1]));
        }
        // Phase 13: mit dem nächsten Zug mitfahren (zug=mit, zugblick = Grad zur Seite)
        if ("mit".equals(opt("zug"))) {
            r.step(0.01);
            if (!r.mitfahren()) System.err.println("FEHLER: kein Zug zum Mitfahren um diese Zeit");
            String zb = opt("zugblick");
            if (zb != null && !zb.isBlank()) r.camera.mitBlick(Double.parseDouble(zb.replace(',', '.')));
        }
        if (r.zugbetrieb != null) { r.step(0.01); System.out.println(r.zugZeile()); }
        // Phase 4: das Windfeld vor den Bildern lösen (sonst rechnet der Faden, während die Bilder schon laufen)
        if (r.terrain.measured()) System.out.println("Windfeld: " + r.windJetzt().text());
        String vl = opt("vorlauf");
        double vor = vl == null || vl.isBlank() ? (ka != null && ka.startsWith("rabe") ? 360 : 0) : Double.parseDouble(vl);
        if (vor > 0) { r.step(0.01); r.leben.vorlauf(vor); System.out.println(String.format(Locale.GERMAN, "Kolkraben %.0f s vorgeflogen: %d im Aufwind", vor, r.leben.raben.ravensSoaring())); }
        if (ka != null && ka.startsWith("rabe")) {
            String[] p = ka.split(",");
            if (!r.kameraZumRaben(p.length > 1 ? Double.parseDouble(p[1]) : 45)) System.err.println("FEHLER: kein Kolkrabe da");
        }
        d.addGLEventListener(r);
        d.display();   // init
        // Phase 9: der Wald – die Netze backen im Hintergrund, die Kacheln um das Auge alle pflanzen
        for (int i = 0; i < 1200 && r.wald.showWald && !r.wald.bereit(); i++) { Thread.sleep(50); r.step(1 / 60.0); d.display(); }
        if (r.wald.showWald) r.wald.pflanzen(r.camera.eye);
        // Phase 14: die Wege der Menschen rechnen im eigenen Faden (rund 5 s)
        for (int i = 0; i < 600 && r.menschen != null && !r.menschen.bereit(); i++) Thread.sleep(50);
        if (r.menschen != null) System.out.println(r.menschen.describeWege());
        for (int i = 0; i < 20; i++) { r.step(1 / 60.0); d.display(); }
        double best = Double.MAX_VALUE, sum = 0;
        for (int i = 0; i < frames; i++) {
            long t0 = System.nanoTime();
            r.step(1 / 60.0);
            d.display();
            d.getContext().makeCurrent();
            d.getGL().glFinish();
            d.getContext().release();
            double ms = (System.nanoTime() - t0) / 1e6;
            best = Math.min(best, ms);
            sum += ms;
        }
        if (r.error() != null) System.err.println("FEHLER: " + r.error());
        System.out.println(r.glInfo());
        System.out.println(r.compute() == null ? "Compute-Test: nicht gelaufen" : r.compute().line());
        System.out.printf(Locale.GERMAN, "%d×%d %s, %s: bestes Bild %.2f ms, Mittel %.2f ms (%.0f Bilder/s)%n", w, h, q.label, clock.label(), best, sum / frames, 1000 / (sum / frames));
        System.out.println("Durchgänge (ms): " + r.passTimes());
        for (String l : r.hudLines()) System.out.println("  " + l);
        // Phase 16: ein Regiebuch durchlaufen lassen; gezeichnet wird nur, wenn es ein Foto macht (am Ende das Blatt)
        String rg = opt("regie");
        if (rg != null && !rg.isBlank()) {
            com.dan.brocken.scene.Regie.Book b = com.dan.brocken.scene.Regie.byName(rg);
            if (b == null) { System.err.println("FEHLER: kein Regiebuch „" + rg + "“"); d.destroy(); System.exit(1); }
            regie(r, d, b);
        }
        // Phase 15: Hörprobe – die Uhr läuft in Echtzeit, der Klang rechnet mit (ohne Bilder)
        String kf = opt("klang");
        if (kf != null && !kf.isBlank()) klang(r, clock, new File(kf), opt("klangsekunden"), "an".equals(opt("walpurgis")));
        if (png != null) {
            File dir = png.getAbsoluteFile().getParentFile();
            if (dir != null) dir.mkdirs();
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            javax.imageio.ImageIO.write(img[0], "png", png);
            System.out.println("Bild: " + png.getPath());
        }
        d.destroy();
        System.exit(r.error() == null ? 0 : 1);
    }

    /**
     * Phase 16: ein Regiebuch in Schritten zu 0,2 s ohne Bild; zu jedem Foto wird der Wald um das Auge gepflanzt und
     * ein Bild gezeichnet, am Ende schreibt das Regiebuch sein Blatt nach docs/status.
     */
    static void regie(Renderer r, GLOffscreenAutoDrawable d, com.dan.brocken.scene.Regie.Book b) {
        long t0 = System.nanoTime();
        r.regieStart(b);
        r.frozen = true;   // die Schritte macht diese Schleife, nicht der Bildlauf
        int steps = 0, bilder = 0;
        String last = "";
        while ((r.regie.running() || r.regieWantsFrame()) && steps < 20_000) {
            if (r.regie.running()) r.step(0.2);
            steps++;
            if (r.regieWantsFrame()) {
                if (r.fotoFaellig()) {
                    // erst einregeln: Wald um das Auge, dann zwölf Bilder zu 50 ms (Belichtung aus dem Bild, Wolken auf der Grafikkarte), dann das Foto
                    if (r.wald.showWald) r.wald.pflanzen(r.camera.eye);
                    int vor = Integer.getInteger("regievorlauf", 12);
                    r.fotoVorlauf = vor;
                    for (int k = 0; k < vor; k++) { r.step(0.05); d.display(); }
                }
                d.display();
                bilder++;
                if (Boolean.getBoolean("regiehud")) for (String hl : r.hudLines()) if (hl.startsWith("Wolken") || hl.startsWith("Wetter") || hl.startsWith("Nebel")) System.out.println("    " + hl);
            }
            String l = r.regieLine().replaceAll(" · \\d+:\\d\\d von .*", "");
            if (!l.equals(last)) { System.out.println("  " + r.clock.label() + " · " + l); last = l; }
        }
        System.out.printf(Locale.GERMAN, "Regiebuch „%s“: %s nach %.0f s (%.0f s gewartet), %d Bilder, %.0f s gerechnet · Blatt: %s%n", b.name(),
                r.regie.running() ? "LÄUFT NOCH" : "zu Ende", steps * 0.2, r.regie.waitedTotal(), bilder, (System.nanoTime() - t0) / 1e9,
                r.lastSheet == null ? "keins" : r.lastSheet.getPath());
    }

    /** Hörprobe: seconds s Szene in Echtzeit (Schritte zu 50 ms), der Klang am Ohr der Kamera als WAV (44,1 kHz, die Spitze auf −1 dB ausgesteuert). */
    static void klang(Renderer r, SceneClock clock, File wav, String seconds, boolean walpurgis) throws java.io.IOException {
        double sec = seconds == null || seconds.isBlank() ? 30 : Double.parseDouble(seconds.replace(',', '.'));
        com.dan.soundscape.Soundscape s = r.klang.klang;
        s.quality = com.dan.soundscape.Soundscape.Quality.HIGH;
        r.klang.walpurgisHand = walpurgis;
        r.frozen = false;
        clock.lapse = 1;
        int sr = s.sampleRate(), block = sr / 20;
        // zwei Sekunden Vorlauf (das Ohr passt sich an), dann die Probe
        for (int i = 0; i < 40; i++) { r.step(0.05); s.render(0.05); }
        int n = (int) Math.round(sec * 20);
        float[] all = new float[2 * n * block];
        long t0 = System.nanoTime();
        double peak = 0;
        String[] zeilen = new String[4];
        for (int i = 0; i < n; i++) {
            r.step(0.05);
            float[] o = new float[2 * block];
            s.render(o, block);
            System.arraycopy(o, 0, all, 2 * i * block, o.length);
            for (float v : o) peak = Math.max(peak, Math.abs(v));
            if (i == n / 4 || i == n / 2 || i == 3 * n / 4 || i == n - 1) {
                int k = i == n / 4 ? 0 : i == n / 2 ? 1 : i == 3 * n / 4 ? 2 : 3;
                try { Thread.sleep(1001); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                zeilen[k] = clock.label() + " · " + r.klang.zeile();
            }
        }
        File dir = wav.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        // wie beim Aufnehmen ausgesteuert: die Spitze auf −1 dB (höchstens 24 dB hinauf, nie hinab)
        double gain = Math.min(Math.pow(10, 24 / 20.0), Math.max(1, 0.89 / Math.max(1e-6, peak)));
        for (int i = 0; i < all.length; i++) all[i] *= (float) gain;
        com.dan.soundscape.Wav.write(wav, all, sr);
        for (String z : zeilen) System.out.println("  " + z);
        System.out.println(r.klang.describe());
        System.out.printf(Locale.GERMAN, "Klang: %.0f s in %.1f s gerechnet, Spitze %.2f, ausgesteuert %+.1f dB · %s%n", sec, (System.nanoTime() - t0) / 1e9, peak, 20 * Math.log10(gain), wav.getPath());
    }
}
