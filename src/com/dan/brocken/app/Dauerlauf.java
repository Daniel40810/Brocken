package com.dan.brocken.app;

import com.dan.brocken.camera.Ansichten;
import com.dan.brocken.camera.Camera;
import com.dan.brocken.gl.Renderer;
import com.dan.brocken.scene.Regie;
import com.dan.brocken.scene.SceneClock;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 18: der Dauerlauf (Abnahme „fertig“, aus Rungholt). Die App läuft mit Fenster und Grafikkarte in Runden; jede
 * Runde zeigt zwei Regiebücher im Kino und einen Tag frei im Zeitraffer:
 * <ol>
 * <li>ungerade Runden „Mit dem Zug auf den Brocken“ und „Über dem Nebelmeer“, gerade „Ein Tag im Nebel“ und
 * „Walpurgisnacht“ – nach zwei Runden lief jedes Regiebuch einmal;</li>
 * <li>danach der 21. Oktober frei im Zeitraffer 360 (24 Stunden in vier Minuten) über dem Nebelmeer, die Kamera kreist
 * (Ansicht 5): Regie, Wind, Nebel, Wolken, Züge, Menschen, Chronik;</li>
 * <li>am Ende jeder Runde zurück an den Anfang (21.10.2026 09:00, Ansicht 5) – dort misst er den Speicher.</li>
 * </ol>
 * Es laufen mindestens zwei Runden, sonst so viele, bis die Minuten um sind (Vorgabe 30; eine Runde dauert rund
 * 16 min). Alle 5 s misst er Bilder/s, Grafikkarte, Prozessor, Speicher, Fäden, Menschen, Züge, Schritte der Physik,
 * die nicht nachkamen, und Fehler. Am Ende schreibt er {@code docs/status/Dauerlauf_….png} (Kurven) und {@code …txt}
 * mit dem Urteil:
 * <ul>
 * <li>keine Fehler (Fäden, GL, Chronik, Datenbank);</li>
 * <li>jede Runde vollständig: beide Regiebücher bis zum Abspann und der freie Tag ganz;</li>
 * <li>im Mittel mindestens 30 Bilder/s (Ziel des Werkbuchs: 60 in „Mittel“, nie unter 30);</li>
 * <li>kein Leck: der Speicher nach einer Bereinigung am Ende jeder Runde wächst von der ersten bis zur letzten Runde um
 * höchstens 10 % und 40 MB, die Zahl der Fäden um höchstens fünf;</li>
 * <li>die Grenzen der Szene halten: höchstens 200 Menschen und vier Züge zugleich;</li>
 * <li>die Physik rechnete mindestens 99 % der Szenenzeit.</li>
 * </ul>
 * Start: Block TECHNIK → „Dauerlauf 30 min“, oder {@code -Dbrocken.dauerlauf=Minuten} beim Start. Esc beendet nur das
 * Kino, nicht den Dauerlauf; der endet von selbst oder mit „Abbrechen“.
 */
public final class Dauerlauf {
    /** Ein Messpunkt. */
    public record Punkt(double minute, double fps, double gpu, double cpu, double heapMb, double lebendMb, int faeden, int menschen, int zuege,
                 long nichtNach, int fehler, int runde) { }

    private static volatile Dauerlauf laufend;
    public static final double RAFFER = 360, FREI_STUNDEN = 24;
    public static final int MIN_RUNDEN = 2, MAX_MENSCHEN = 200, MAX_ZUEGE = 4;
    /** Die Regiebücher je Runde (ungerade, gerade). */
    public static final String[][] KINO = {{"Mit dem Zug", "Nebelmeer"}, {"Tag im Nebel", "Walpurgis"}};

    private final Renderer r;
    private final double minuten;
    private final List<Punkt> punkte = new ArrayList<>();
    private final List<double[]> runden = new ArrayList<>();   // je Runde: Minute, Speicher nach Bereinigung (MB), Kinos durch, freier Tag (1/0), Fäden
    private final List<String> gelaufen = new ArrayList<>();
    private final long beginn = System.nanoTime();
    private volatile boolean aus;
    private volatile String stand = "";
    private int runde;
    private int fehler0, chronik0;

    private Dauerlauf(Renderer r, double minuten) {
        this.r = r;
        this.minuten = minuten;
    }

    public static boolean laeuft() { return laufend != null; }

    /** „Dauerlauf Runde 1 · 12 von 30 min · Kino: Über dem Nebelmeer“ oder leer. */
    public static String zeile() { Dauerlauf d = laufend; return d == null ? "" : d.stand; }

    /** Startet den Dauerlauf (einer zur Zeit); danach läuft {@code fertig} mit der Datei des Berichts (im Faden des Laufs). */
    public static synchronized boolean starten(Renderer r, double minuten, java.util.function.Consumer<File> fertig) {
        if (laufend != null) return false;
        Dauerlauf d = new Dauerlauf(r, Math.max(1, minuten));
        laufend = d;
        Thread t = new Thread(() -> {
            File f = null;
            try { f = d.lauf(); }
            catch (Exception e) { System.err.println("Dauerlauf: " + e); e.printStackTrace(); BrockenApp.FEHLER.incrementAndGet(); }
            finally { laufend = null; }
            if (fertig != null) fertig.accept(f);
        }, "Brocken-Dauerlauf");
        t.setDaemon(true);
        t.start();
        return true;
    }

    public static void anhalten() { Dauerlauf d = laufend; if (d != null) d.aus = true; }

    private double minute() { return (System.nanoTime() - beginn) / 6e10; }

    private static int chronikFehler() {
        com.dan.brocken.data.Chronik c = com.dan.brocken.data.Datenseite.chronik;
        return c == null ? 0 : c.fehler;
    }

    private File lauf() throws Exception {
        fehler0 = BrockenApp.FEHLER.get();
        chronik0 = chronikFehler();
        long nichtNach0 = r.physik.dropped(), schritte0 = r.physik.steps();
        verloren0 = r.physik.lostSeconds();
        // warten, bis die Szene steht (Windfeld und Wege der Menschen)
        for (int i = 0; i < 480 && (!r.windDa() || !r.menschen.wegeFertig()); i++) Thread.sleep(250);
        r.ohneBlatt = true;
        try {
            while (!aus && (runde < MIN_RUNDEN || minute() < minuten)) runde();
        } finally {
            r.ohneBlatt = false;
            if (r.kino.laeuft()) r.kinoVerlassen();
            if (r.regie.running()) r.regieStop();
            r.clock.lapse = 1;
        }
        long nichtNach = r.physik.dropped() - nichtNach0, schritte = Math.max(1, r.physik.steps() - schritte0);
        return bericht(fehler(), nichtNach, schritte);
    }

    private int fehler() { return BrockenApp.FEHLER.get() - fehler0 + chronikFehler() - chronik0; }

    private void runde() throws InterruptedException {
        runde++;
        int kinos = 0;
        for (String name : KINO[(runde - 1) % 2]) {
            Regie.Book b = Regie.byName(name);
            if (b != null && kino(b)) { kinos++; gelaufen.add(b.name()); }
        }
        boolean tag = !aus && frei();
        if (!aus) anfang();
        Thread.sleep(2000);
        synchronized (runden) { runden.add(new double[]{minute(), heapNachGc(), kinos, tag ? 1 : 0, ManagementFactory.getThreadMXBean().getThreadCount()}); }
    }

    /** Zurück an den Anfang: 21.10.2026 09:00, Ansicht 5, die Kamera kreist. */
    private void anfang() throws InterruptedException {
        java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
        r.imBild(() -> {
            try {
                if (r.regie.running()) r.regieStop();
                r.camera.stopFahrt();
                r.camera.stopMitfahrt();
                r.clock.lapse = 1;
                r.clock.set(SceneClock.START_DATE, SceneClock.START_HOUR);
                r.wiring.resumeRegie();
                r.wetter("UeberdemNebelmeer", 0);
                r.clock.set(SceneClock.START_DATE, SceneClock.START_HOUR);
                r.placeView(Ansichten.byKey(5));
                r.camera.mode = Camera.Mode.KREISEN;
                r.nachSprung();
            } finally { l.countDown(); }
        });
        l.await(30, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Ein Tag frei im Zeitraffer; true, wenn er ganz lief. */
    private boolean frei() throws InterruptedException {
        anfang();
        r.clock.lapse = RAFFER;
        double h0 = r.clock.epochMillis() / 3_600_000.0, bis = h0 + FREI_STUNDEN;
        long naechste = 0;
        while (!aus && r.clock.epochMillis() / 3_600_000.0 < bis) {
            stand = String.format(Locale.GERMAN, "Dauerlauf Runde %d · %.0f von %.0f min · frei im Zeitraffer %.0f (%s)", runde, minute(), minuten, RAFFER, r.clock.label());
            naechste = messen(naechste);
            Thread.sleep(250);
        }
        r.clock.lapse = 1;
        return !aus;
    }

    /** Ein Regiebuch im Kino bis zum Abspann; true, wenn es durchlief (nicht abgebrochen). */
    private boolean kino(Regie.Book b) throws InterruptedException {
        if (aus) return false;
        long naechste = 0;
        r.imBild(() -> r.kinoStart(b));
        Thread.sleep(1500);
        while (!aus && (r.kino.laeuft() || r.regie.running())) {
            stand = String.format(Locale.GERMAN, "Dauerlauf Runde %d · %.0f von %.0f min · Kino: %s", runde, minute(), minuten, b.name());
            naechste = messen(naechste);
            Thread.sleep(250);
        }
        if (r.kino.laeuft()) r.kinoVerlassen();
        return !aus;
    }

    private double verloren0;
    private int letzteMinute = -1;

    private long messen(long naechste) {
        long jetzt = System.currentTimeMillis();
        if (jetzt < naechste) return naechste;
        Runtime rt = Runtime.getRuntime();
        double heap = (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
        double lebend = 0;
        for (java.lang.management.MemoryPoolMXBean mp : ManagementFactory.getMemoryPoolMXBeans()) {
            if (mp.getType() != java.lang.management.MemoryType.HEAP) continue;
            java.lang.management.MemoryUsage u = mp.getCollectionUsage();
            if (u != null) lebend += u.getUsed() / 1048576.0;
        }
        int menschen = r.menschen == null ? 0 : r.menschen.personen().size();
        int zuege = r.zugbetrieb == null ? 0 : r.zugbetrieb.zuege.size();
        if ((int) minute() != letzteMinute) { letzteMinute = (int) minute(); System.out.println(stand + String.format(Locale.GERMAN, " · %.0f Bilder/s · %s", r.fps(), r.clock.label())); }
        synchronized (punkte) {
            punkte.add(new Punkt(minute(), r.fps(), r.gpuMs(), r.cpuMs(), heap, lebend, ManagementFactory.getThreadMXBean().getThreadCount(), menschen, zuege,
                    r.physik.dropped(), fehler(), runde));
        }
        return jetzt + 5000;
    }

    /** Speicher nach einer Bereinigung (MB). */
    private static double heapNachGc() {
        System.gc();
        try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
    }

    /** Das Urteil als Zeilen {ok|FEHLER, Text}; auch für die Prüfung ohne Grafikkarte. */
    public static List<String[]> urteil(List<Punkt> p, List<double[]> rs, int fehler, long nichtNach, double nachAnteil, double verloren) {
        int n = p.size();
        double fps = 0, fpsMin = Double.MAX_VALUE, gpu = 0;
        int menschMax = 0, zugMax = 0, faedenMax = 0, unter30 = 0;
        for (Punkt q : p) {
            fps += q.fps(); gpu += q.gpu();
            if (q.minute() > 1) { fpsMin = Math.min(fpsMin, q.fps()); if (q.fps() < 30) unter30++; }
            menschMax = Math.max(menschMax, q.menschen());
            zugMax = Math.max(zugMax, q.zuege());
            faedenMax = Math.max(faedenMax, q.faeden());
        }
        fps /= Math.max(1, n);
        gpu /= Math.max(1, n);
        boolean vollstaendig = rs.size() >= MIN_RUNDEN && rs.stream().allMatch(x -> x[2] >= 2 && x[3] > 0);
        StringBuilder rz = new StringBuilder();
        for (int i = 0; i < rs.size(); i++)
            rz.append(i == 0 ? "" : ", ").append(String.format(Locale.GERMAN, "%.0f Kinos%s", rs.get(i)[2], rs.get(i)[3] > 0 ? " + Tag frei" : ""));
        double erst = rs.isEmpty() ? 0 : rs.get(0)[1], letzt = rs.isEmpty() ? 0 : rs.get(rs.size() - 1)[1];
        double fErst = rs.isEmpty() ? 0 : rs.get(0)[4], fLetzt = rs.isEmpty() ? 0 : rs.get(rs.size() - 1)[4];
        boolean dicht = rs.size() < 2 || (letzt <= erst * 1.10 + 40 && fLetzt <= fErst + 5);
        StringBuilder sz = new StringBuilder();
        for (int i = 0; i < rs.size(); i++) sz.append(i == 0 ? "" : ", ").append(String.format(Locale.GERMAN, "%.0f MB/%.0f Fäden", rs.get(i)[1], rs.get(i)[4]));
        List<String[]> u = new ArrayList<>();
        u.add(new String[]{fehler == 0 ? "ok" : "FEHLER", fehler + " Fehler (Fäden, GL, Chronik, Datenbank)"});
        u.add(new String[]{vollstaendig ? "ok" : "FEHLER", String.format(Locale.GERMAN, "%d Runden: %s", rs.size(), rz.length() == 0 ? "keine" : rz)});
        u.add(new String[]{fps >= 30 ? "ok" : "FEHLER", String.format(Locale.GERMAN, "Ø %.1f Bilder/s (geringste %.0f, unter 30 in %d von %d Messungen), Grafikkarte Ø %.2f ms",
                fps, fpsMin == Double.MAX_VALUE ? 0 : fpsMin, unter30, n, gpu)});
        u.add(new String[]{dicht ? "ok" : "FEHLER", "Speicher und Fäden nach Bereinigung am Ende jeder Runde " + (sz.length() == 0 ? "—" : sz) + " (höchstens +10 % und 40 MB, +5 Fäden)"});
        u.add(new String[]{menschMax <= MAX_MENSCHEN && zugMax <= MAX_ZUEGE ? "ok" : "FEHLER", String.format(Locale.GERMAN,
                "höchstens %d Menschen (Grenze %d), %d Züge zugleich (Grenze %d), %d Fäden", menschMax, MAX_MENSCHEN, zugMax, MAX_ZUEGE, faedenMax)});
        u.add(new String[]{nachAnteil >= 0.99 ? "ok" : "FEHLER", String.format(Locale.GERMAN, "Physik rechnete %.2f %% der Zeit (%.1f s verfallen, %d×)",
                nachAnteil * 100, verloren, nichtNach)});
        return u;
    }

    private File bericht(int fehler, long nichtNach, long schritte) throws Exception {
        List<Punkt> p;
        synchronized (punkte) { p = new ArrayList<>(punkte); }
        List<double[]> rs;
        synchronized (runden) { rs = new ArrayList<>(runden); }
        double verloren = r.physik.lostSeconds() - verloren0, gerechnet = schritte * com.dan.brocken.scene.Physik.DT;
        double nachAnteil = gerechnet / Math.max(1e-9, gerechnet + verloren);
        List<String[]> urteil = urteil(p, rs, fehler, nichtNach, nachAnteil, verloren);
        boolean gut = urteil.stream().allMatch(u -> u[0].equals("ok"));
        String stempel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        File dir = r.photoDir != null ? r.photoDir : new File("docs/status");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
        StringBuilder t = new StringBuilder();
        t.append("Brocken · Dauerlauf ").append(stempel).append(" · ").append(Renderer.VERSION).append(" · ").append(String.format(Locale.GERMAN, "%.1f min", minute()))
                .append(" · ").append(r.quality().label).append(" · ").append(r.glInfo()).append('\n');
        t.append("Regiebücher im Kino: ").append(gelaufen.isEmpty() ? "keins" : String.join(", ", gelaufen)).append('\n');
        for (String[] u : urteil) t.append(String.format("%-7s %s%n", u[0], u[1]));
        t.append(gut ? "ERGEBNIS: Dauerlauf bestanden\n" : "ERGEBNIS: Dauerlauf nicht bestanden\n");
        t.append("\nMinute;Runde;Bilder/s;Grafikkarte ms;Prozessor ms;Speicher MB;lebend MB;Fäden;Menschen;Züge;nicht nachgekommen;Fehler\n");
        for (Punkt q : p)
            t.append(String.format(Locale.GERMAN, "%.2f;%d;%.1f;%.2f;%.2f;%.0f;%.0f;%d;%d;%d;%d;%d%n", q.minute(), q.runde(), q.fps(), q.gpu(), q.cpu(), q.heapMb(), q.lebendMb(), q.faeden(),
                    q.menschen(), q.zuege(), q.nichtNach(), q.fehler()));
        File txt = new File(dir, "Dauerlauf_" + stempel + ".txt");
        Files.writeString(txt.toPath(), t.toString(), StandardCharsets.UTF_8);
        File png = new File(dir, "Dauerlauf_" + stempel + ".png");
        ImageIO.write(bild(p, rs, urteil, gut, minute(), r.quality().label), "png", png);
        System.out.print(t.substring(0, t.indexOf("\nMinute")));
        return png;
    }

    public static BufferedImage bild(List<Punkt> p, List<double[]> rs, List<String[]> urteil, boolean gut, double minuten, String stufe) {
        int w = 1600, h = 900, l = 80, rt = 30, top = 90, bot = 250;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(14, 18, 24));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(232, 236, 240));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        g.drawString(String.format(Locale.GERMAN, "Brocken · Dauerlauf %.0f min · %d Runden · %s · %s", minuten, rs.size(), stufe, gut ? "bestanden" : "nicht bestanden"), l, 40);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.setColor(new Color(150, 162, 176));
        g.drawString("Bilder/s (blau, 0–90) · lebender Speicher (gelb, 0–Höchstwert) · Menschen (weiß, 0–200) · Züge (rot, 0–4) · Runden (senkrecht) · 30 und 60 Bilder/s gestrichelt", l, 66);
        int pw = w - l - rt, ph = h - top - bot;
        g.setColor(new Color(42, 52, 64));
        for (int i = 0; i <= 6; i++) g.drawLine(l, top + ph * i / 6, l + pw, top + ph * i / 6);
        g.setColor(new Color(90, 100, 110));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{6, 6}, 0));
        for (double f : new double[]{30, 60}) { int y = top + (int) (ph * (1 - f / 90)); g.drawLine(l, y, l + pw, y); }
        g.setStroke(new BasicStroke(1f));
        double tMax = p.isEmpty() ? 1 : Math.max(1, p.get(p.size() - 1).minute());
        g.setColor(new Color(72, 88, 100));
        for (double[] x : rs) { int xx = l + (int) (pw * Math.min(1, x[0] / tMax)); g.drawLine(xx, top, xx, top + ph); }
        double hMax = 1;
        for (Punkt q : p) hMax = Math.max(hMax, q.lebendMb());
        kurve(g, p, q -> q.fps() / 90, tMax, l, top, pw, ph, new Color(64, 150, 210));
        final double hm = hMax;
        kurve(g, p, q -> q.lebendMb() / hm, tMax, l, top, pw, ph, new Color(226, 192, 120));
        kurve(g, p, q -> q.menschen() / (double) MAX_MENSCHEN, tMax, l, top, pw, ph, new Color(240, 244, 248));
        kurve(g, p, q -> q.zuege() / (double) MAX_ZUEGE, tMax, l, top, pw, ph, new Color(212, 98, 60));
        g.setColor(new Color(150, 162, 176));
        for (int m = 0; m <= (int) tMax; m += Math.max(1, (int) tMax / 10)) {
            int x = l + (int) (pw * m / tMax);
            g.drawString(m + " min", x - 12, top + ph + 20);
        }
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        int y = h - bot + 60;
        for (String[] u : urteil) {
            g.setColor(u[0].equals("ok") ? new Color(70, 180, 160) : new Color(222, 92, 72));
            g.drawString(String.format("%-7s", u[0]), l, y);
            g.setColor(new Color(232, 236, 240));
            g.drawString(u[1], l + 90, y);
            y += 26;
        }
        g.dispose();
        return im;
    }

    private static void kurve(Graphics2D g, List<Punkt> p, java.util.function.ToDoubleFunction<Punkt> f, double tMax, int l, int top, int pw, int ph, Color c) {
        if (p.size() < 2) return;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < p.size(); i++) {
            double x = l + pw * p.get(i).minute() / tMax, y = top + ph * (1 - Math.max(0, Math.min(1, f.applyAsDouble(p.get(i)))));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        g.setColor(c);
        g.setStroke(new BasicStroke(2f));
        g.draw(path);
    }
}
