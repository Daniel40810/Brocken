package com.dan.brocken.data;

import com.dan.brocken.gl.Renderer;
import com.dan.brocken.scene.Fahrplan;
import com.dan.brocken.scene.Regie;
import com.dan.brocken.scene.SceneClock;
import com.dan.brocken.scene.Site;
import com.dan.brocken.scene.Windrechner;
import com.dan.brocken.scene.Zugbetrieb;
import com.dan.brocken.wiring.KlimaBrocken;
import com.dan.brocken.wiring.Raueis;
import com.dan.store.Ablage;
import com.dan.store.Dienst;
import com.dan.store.Protokoll;
import com.dan.weather.Weather;

import java.io.File;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Die Chronik der Szene (Phase 17, F29 A): beobachtet die laufende Szene alle 0,5 s und schreibt über den gültigen
 * {@link Speicher} in die Datenbank oder in die Dateien. Das Bild wartet nie: beobachtet wird in einem eigenen Faden,
 * geschrieben in einem zweiten.
 * <ul>
 * <li><b>Stunden</b> ({@code BR_STUNDE}): an jeder vollen Stunde der Szene Lage, Wind und Böe am Gipfel und an der
 * Wetterwarte, Sicht, Temperatur, Bedeckung, Regen, Wolkenbasis, Nebelmeer, Raueis am Mast, Schnee, Stimmen der
 * Walpurgisnacht. Springt die Uhr (zurück oder um mehr als zwei Tage), wird nichts nachgetragen.</li>
 * <li><b>Tage</b> ({@code BR_TAG}): beim Wechsel des Tags das Mittel des Tags aus seinen Stunden, dazu Schnee je
 * Höhenstufe und die Schneegrenze.</li>
 * <li><b>Nebelmeer</b> ({@code BR_NEBELMEER}): von seinem Beginn bis zur Auflösung, mit der höchsten Obergrenze.</li>
 * <li><b>Gespenst</b> ({@code BR_GESPENST}): jedes Brockengespenst am Auge mit Sonne, Glorie und Dauer.</li>
 * <li><b>Zugfahrten</b> ({@code BR_ZUGFAHRT}): jede Fahrt mit Plan und Ist, Pfiffen, Verspätung, ob man mitfuhr.</li>
 * <li><b>Windfelder</b> ({@code BR_WINDFELD}): die Kennzahlen jedes neu gerechneten Felds (F30 A).</li>
 * <li><b>Regiebücher</b> ({@code BR_REGIELAUF}): jeder Lauf mit Blatt (oder abgebrochen), ob im Kino, Tempo.</li>
 * </ul>
 */
public final class Chronik {
    /** Größte Lücke der Uhr, die noch als Lauf der Zeit gilt (ms, zwei Tage); weiter ist ein Sprung. */
    public static final double LUECKE_MS = 2 * 86_400_000.0;

    private final Renderer r;
    private final ScheduledExecutorService schauen;
    private final ExecutorService schreiben;
    private volatile boolean an = true;
    /** Zähler für Block DATEN und Prüfung. */
    public volatile int stunden, tage, meere, gespenster, fahrten, felder, regielaeufe, fehler;
    public volatile String letzte = "";

    private double zuletztMs = Double.NaN;
    // Tag
    private String tag;
    private int tagStunden, tagNebel;
    private double tMin = Double.NaN, tMax = Double.NaN, windMax = Double.NaN;
    private final Set<String> tagLagen = new LinkedHashSet<>();
    // Nebelmeer
    private Daten.Zeile meer;
    private double meerOben;
    // Gespenst
    private Daten.Zeile gespenst;
    private long gespenstSeit;
    // Zug
    private final Set<String> mitgefahren = new LinkedHashSet<>();
    // Windfeld
    private Windrechner.Ergebnis feld;
    // Regie
    private Regie.Book buch;
    private long buchBeginn;
    private boolean buchKino;
    private double buchZeit;
    private File blattVorher;

    public Chronik(Renderer r) { this(r, true); }

    /** Mit {@code laufen = false} beobachtet niemand von selbst (Prüfung: {@link #jetzt}). */
    public Chronik(Renderer r, boolean laufen) {
        this.r = r;
        schauen = Executors.newSingleThreadScheduledExecutor(x -> faden(x, "Brocken-Chronik"));
        schreiben = Executors.newSingleThreadExecutor(x -> faden(x, "Brocken-Chronik-Schreiben"));
        feld = r.windrechner.last();
        blattVorher = r.lastSheet;
        if (laufen) schauen.scheduleWithFixedDelay(this::schauen, 500, 500, TimeUnit.MILLISECONDS);
    }

    private static Thread faden(Runnable x, String n) {
        Thread t = new Thread(x, n);
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    }

    /** Die Chronik schreibt (true) oder nicht; die Beobachtung läuft weiter. */
    public void schreiben(boolean ja) { an = ja; }
    public boolean schreibt() { return an; }

    /** Für Prüfungen: einmal beobachten (im Faden des Aufrufers) und warten, bis alles geschrieben ist. */
    public void jetzt() {
        schauen();
        warten();
    }

    /** Warten, bis die Schreibschlange leer ist (höchstens 30 s). */
    public void warten() {
        try { schreiben.submit(() -> { }).get(30, TimeUnit.SECONDS); } catch (Exception e) { /* weiter */ }
    }

    /** Beim Ende: offenes Nebelmeer, Gespenst und Regiebuch schreiben. */
    public void beenden() {
        try {
            synchronized (this) {
                if (meer != null) meerEnde(r.clock.epochMillis(), false);
                if (gespenst != null) gespenstEnde();
                if (buch != null) regieEnde(false);
            }
        } catch (RuntimeException e) { /* weiter */ }
        schauen.shutdownNow();
        schreiben.shutdown();
        try { schreiben.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static Long sitzung() { long s = Dienst.sitzung(); return s > 0 ? s : null; }

    private void spaeter(String tabelle, Daten.Zeile z, Runnable gezaehlt) {
        if (!an) return;
        Long s = sitzung();
        if (s != null && z.get("SITZUNG_ID") == null) z.put("SITZUNG_ID", s);
        schreiben.execute(() -> {
            try {
                Datenseite.speicher().schreiben(tabelle, z);
                gezaehlt.run();
                letzte = tabelle.substring(3).toLowerCase(Locale.ROOT) + " " + (z.get("SZENE_TEXT") != null ? z.get("SZENE_TEXT") : "");
            } catch (Ablage.Fehler | RuntimeException e) {
                fehler++;
                Protokoll.warnung("Chronik", tabelle + " nicht geschrieben: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ beobachten

    synchronized void schauen() {
        try {
            double ms = r.clock.epochMillis();
            if (!Double.isNaN(zuletztMs)) {
                boolean sprung = ms < zuletztMs - 1000 || ms - zuletztMs > LUECKE_MS;
                long h0 = (long) Math.floor(zuletztMs / 3_600_000.0), h1 = (long) Math.floor(ms / 3_600_000.0);
                if (!sprung && h1 > h0) stunde(h1 * 3_600_000L);
                if (sprung) { tag = null; resetTag(); }
            }
            zuletztMs = ms;
            meer(ms);
            gespenst(ms);
            zuege();
            windfeld(ms);
            regie();
        } catch (RuntimeException e) {
            fehler++;
            Protokoll.warnung("Chronik", "Beobachten: " + e);
        }
    }

    private void resetTag() {
        tagStunden = tagNebel = 0;
        tMin = tMax = windMax = Double.NaN;
        tagLagen.clear();
    }

    /** Wind am Gipfelstein 10 m über Grund (m/s) aus dem Feld. */
    private double windGipfel() {
        double gx = Site.GIPFELSTEIN.x(), gz = Site.GIPFELSTEIN.z();
        double[] v = r.wiring.windAt(gx, r.terrain.height(gx, gz) + 10, gz);
        return Math.hypot(v[0], v[2]);
    }

    private void stunde(long hMs) {
        Weather.Snapshot ws = r.wiring.snapshot();
        if (ws == null) return;
        String tg = Daten.tag(hMs);
        if (tag != null && !tg.equals(tag)) tagEnde(hMs);
        tag = tg;
        double wg = windGipfel(), boe = wg * (1 + ws.gustiness() * 1.6);
        double seaA = r.nebel.seaAmount();
        double rau = r.wiring.raueis.dicke(Raueis.MAST, Site.GIPFEL) * 1000, schnee = r.wiring.schnee(Site.GIPFEL) * 100;
        Daten.Zeile z = Daten.szene(new Daten.Zeile(), hMs).mit("SZENENZEIT", hMs).mit("STUNDE", Daten.zeit(hMs).getHour())
                .mit("LAGE", ws.phase().name()).mit("LAGE_NAME", KlimaBrocken.name(ws.phase()))
                .mit("WIND_GIPFEL_MS", Daten.r(wg, 2)).mit("BOE_GIPFEL_MS", Daten.r(boe, 2)).mit("RICHTUNG_GRAD", Daten.r(ws.fromDeg(), 1))
                .mit("WIND_WARTE_MS", Daten.r(ws.wind(), 2)).mit("SICHT_KM", Daten.r(ws.effectiveVisibilityKm(), 2)).mit("TEMPERATUR_C", Daten.r(ws.temperature(), 1))
                .mit("BEDECKUNG_ACHTEL", Daten.r(ws.oktas(), 2)).mit("REGEN_MMH", Daten.r(ws.rain(), 2)).mit("FEUCHTE_PROZ", Daten.r(ws.humidity(), 1))
                .mit("WOLKENBASIS_M", Daten.r(ws.cloudBase(), 1))
                .mit("MEER_UNTEN_M", seaA > 0.05 ? Daten.r(r.nebel.seaBase(), 1) : null).mit("MEER_OBEN_M", seaA > 0.05 ? Daten.r(r.nebel.seaTop(), 1) : null)
                .mit("MEER_ANTEIL", Daten.r(seaA, 3)).mit("RAUEIS_MAST_MM", Daten.r(rau, 1)).mit("SCHNEE_GIPFEL_CM", Daten.r(schnee, 1))
                .mit("WALPURGIS", Daten.r(r.klang.walpurgis(), 3)).mit("ZEITRAFFER", Daten.r(r.clock.lapse, 2));
        spaeter("BR_STUNDE", z, () -> stunden++);
        tagStunden++;
        if (seaA > 0.5 || ws.cloudAt(Site.GIPFEL) > 0.5) tagNebel++;
        tMin = Double.isNaN(tMin) ? ws.temperature() : Math.min(tMin, ws.temperature());
        tMax = Double.isNaN(tMax) ? ws.temperature() : Math.max(tMax, ws.temperature());
        windMax = Double.isNaN(windMax) ? boe : Math.max(windMax, boe);
        tagLagen.add(KlimaBrocken.name(ws.phase()));
    }

    /** Der Tag {@link #tag} ist zu Ende (die Stunde hMs gehört schon zum nächsten). */
    private void tagEnde(long hMs) {
        if (tagStunden == 0) return;
        StringBuilder st = new StringBuilder();
        double grenze = Double.NaN;
        for (double h : KlimaBrocken.STUFEN) {
            double cm = r.wiring.schnee(h) * 100;
            st.append(st.length() > 0 ? " " : "").append(Math.round(cm));
            if (Double.isNaN(grenze) && cm >= 1) grenze = h;
        }
        LocalDate d = LocalDate.parse(tag);
        long beginn = d.atStartOfDay(SceneClock.ZONE).toInstant().toEpochMilli();
        double gx = Site.GIPFELSTEIN.x(), gz = Site.GIPFELSTEIN.z();
        Daten.Zeile z = Daten.szene(new Daten.Zeile(), beginn).mit("SCHNEE_STUFEN", st.toString()).mit("SCHNEE_GIPFEL_CM", Daten.r(r.wiring.schnee(Site.GIPFEL) * 100, 1))
                .mit("SCHNEEGRENZE_M", Double.isNaN(grenze) ? null : grenze).mit("VERWEHUNG_FAKTOR", Daten.r(r.verwehung.factorAt(gx, gz), 2))
                .mit("TEMPERATUR_MIN_C", Daten.r(tMin, 1)).mit("TEMPERATUR_MAX_C", Daten.r(tMax, 1)).mit("WIND_MAX_MS", Daten.r(windMax, 2))
                .mit("NEBELSTUNDEN", tagNebel).mit("STUNDEN", tagStunden)
                .mit("RAUEIS_MAST_MM", Daten.r(r.wiring.raueis.dicke(Raueis.MAST, Site.GIPFEL) * 1000, 1)).mit("LAGEN", String.join(", ", tagLagen));
        spaeter("BR_TAG", z, () -> tage++);
        resetTag();
    }

    private void meer(double ms) {
        double a = r.nebel.seaAmount();
        if (meer == null && a > 0.5) {
            Weather.Snapshot ws = r.wiring.snapshot();
            meer = Daten.szene(new Daten.Zeile(), ms).mit("UNTEN_M", Daten.r(r.nebel.seaBase(), 1)).mit("OBEN_M", Daten.r(r.nebel.seaTop(), 1))
                    .mit("LAGE", ws != null ? ws.phase().name() : null);
            meerOben = r.nebel.seaTop();
        } else if (meer != null) {
            if (a > 0.05) meerOben = Math.max(meerOben, r.nebel.seaTop());
            boolean weg = a < 0.2 || ms < meer.zahl("SZENE_MS") - 1000 || ms - meer.zahl("SZENE_MS") > LUECKE_MS;
            if (weg) meerEnde(ms, a < 0.2 && ms >= meer.zahl("SZENE_MS"));
        }
    }

    private void meerEnde(double ms, boolean aufgeloest) {
        Daten.Zeile z = meer;
        meer = null;
        double beginn = z.zahl("SZENE_MS");
        if (ms >= beginn && ms - beginn < LUECKE_MS) z.mit("ENDE_MS", (long) ms).mit("ENDE_TEXT", Daten.text(ms)).mit("DAUER_H", Daten.r((ms - beginn) / 3_600_000.0, 2));
        z.mit("OBEN_MAX_M", Daten.r(meerOben, 1)).mit("AUFGELOEST", aufgeloest ? 1 : 0);
        spaeter("BR_NEBELMEER", z, () -> meere++);
    }

    private void gespenst(double ms) {
        String t = r.gespenst();
        boolean ja = t != null && t.startsWith("Gespenst: ja");
        if (ja && gespenst == null) {
            double[] sun = r.clock.sun();
            double[] e = r.camera.eye;
            boolean glorie = t.contains("Glorie");
            gespenst = Daten.szene(new Daten.Zeile(), ms).mit("SONNENHOEHE_GRAD", Daten.r(sun[0], 2)).mit("SONNE_AZIMUT_GRAD", Daten.r(sun[1], 1))
                    .mit("GLORIE", glorie ? 1 : 0).mit("NEBELBOGEN", glorie && sun[0] < 40 ? 1 : 0)
                    .mit("X_M", Daten.r(e[0], 2)).mit("Z_M", Daten.r(e[2], 2)).mit("NHN_M", Daten.r(r.camera.groundLevel, 2))
                    .mit("BLICK_GRAD", Daten.r(r.camera.heading(), 1)).mit("LAGE", Daten.Geom.punkt(e[0], e[2])).mit("TEXT", t);
            gespenstSeit = System.currentTimeMillis();
        } else if (!ja && gespenst != null) gespenstEnde();
    }

    private void gespenstEnde() {
        Daten.Zeile z = gespenst;
        gespenst = null;
        z.mit("DAUER_S", Daten.r((System.currentTimeMillis() - gespenstSeit) / 1000.0, 1));
        spaeter("BR_GESPENST", z, () -> gespenster++);
    }

    private void zuege() {
        Zugbetrieb zb = r.zugbetrieb;
        if (zb == null) return;
        String mit = r.mitfahrZug();
        if (mit != null) mitgefahren.add(mit);
        for (Zugbetrieb.Bericht b; (b = zb.berichte.poll()) != null; ) {
            long tag0 = LocalDate.ofEpochDay(b.tag()).atStartOfDay(SceneClock.ZONE).toInstant().toEpochMilli();
            long abMs = tag0 + Math.round(b.istAb() * 1000);
            boolean mitg = mitgefahren.remove(b.zug());
            Daten.Zeile z = Daten.szene(new Daten.Zeile(), abMs).mit("ZUG", b.zug()).mit("RICHTUNG", b.bergauf() ? "BERG" : "TAL")
                    .mit("PLAN_AB", Fahrplan.uhr(b.planAb())).mit("PLAN_AN", Fahrplan.uhr(b.planAn())).mit("IST_AB", uhrS(b.istAb())).mit("IST_AN", uhrS(b.istAn()))
                    .mit("FAHRZEIT_MIN", Daten.r((b.istAn() - b.istAb()) / 60, 2)).mit("VERSPAETUNG_MIN", Daten.r((b.istAn() - b.planAn()) / 60, 2))
                    .mit("PFIFFE", b.pfiffe()).mit("MITGEFAHREN", mitg ? 1 : 0);
            spaeter("BR_ZUGFAHRT", z, () -> fahrten++);
        }
    }

    static String uhrS(double s) {
        int t = (int) Math.round(((s % 86400) + 86400) % 86400);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", t / 3600, t / 60 % 60, t % 60);
    }

    private void windfeld(double ms) {
        Windrechner.Ergebnis e = r.windrechner.last();
        if (e == null || e == feld) return;
        feld = e;
        var so = e.solution();
        double kalt = 0;
        double[] f = Windrechner.fenster();
        for (int j = 0; j < 40; j++)
            for (int i = 0; i < 40; i++) {
                double x = f[0] + (i + 0.5) / 40 * (f[2] - f[0]), z = f[1] + (j + 0.5) / 40 * (f[3] - f[1]);
                if (so.contains(x, z)) kalt = Math.max(kalt, so.coldAt(x, z));
            }
        Weather.Snapshot ws = r.wiring.snapshot();
        Daten.Zeile z = Daten.szene(new Daten.Zeile(), ms).mit("LAGE", ws != null ? ws.phase().name() : null).mit("SCHLUESSEL", e.key())
                .mit("SCHICHTUNG", so.stability.name()).mit("RICHTUNG_GRAD", Daten.r(so.fromDeg, 1)).mit("WIND_MS", Daten.r(so.speed, 2))
                .mit("ZELLEN_X", so.nx).mit("ZELLEN_Z", so.nz).mit("SCHICHTEN", so.n).mit("ZELLE_M", so.cell).mit("OBERGRENZE_M", Daten.r(so.ztop, 1))
                .mit("SCHRITTE", so.iterations).mit("REST", so.residual).mit("MASSE_MAX_PROZ", Daten.r(so.massMax * 100, 4)).mit("RECHENZEIT_S", Daten.r(so.millis / 1000, 2))
                .mit("FAKTOR_WARTE", Daten.r(r.wiring.windGain, 3)).mit("WIND_GIPFEL_MS", Daten.r(e.summitRaw() * r.wiring.windGain, 2))
                .mit("HANGWIND_MS", Daten.r(so.slopeFlow * r.wiring.windGain, 2)).mit("KALTLUFT_MAX_M", Daten.r(kalt, 1)).mit("ZEILE", so.describe());
        spaeter("BR_WINDFELD", z, () -> felder++);
    }

    private void regie() {
        Regie.Book b = r.regie.book();
        if (b != buch) {
            if (buch != null) regieEnde(r.lastSheet != null && r.lastSheet != blattVorher || buchZeit >= buch.length() - 0.5);
            buch = b;
            if (b != null) {
                buchBeginn = System.currentTimeMillis();
                buchKino = r.kino.laeuft();
                blattVorher = r.lastSheet;
            }
        }
        if (buch != null) {
            buchZeit = r.regie.time();
            buchKino |= r.kino.laeuft();
        }
    }

    private void regieEnde(boolean vollstaendig) {
        Regie.Book b = buch;
        buch = null;
        File blatt = r.lastSheet != null && r.lastSheet != blattVorher ? r.lastSheet : null;
        double[] tempo = r.regieTempo();
        Daten.Zeile z = new Daten.Zeile().mit("NAME", b.name()).mit("BEGINN", buchBeginn).mit("ENDE", System.currentTimeMillis()).mit("VOLLSTAENDIG", vollstaendig ? 1 : 0)
                .mit("KINO", buchKino ? 1 : 0).mit("BILDER_S_MITTEL", Daten.r(tempo[0], 2)).mit("GPU_MS_MITTEL", Daten.r(tempo[1], 3))
                .mit("GEWARTET_S", Daten.r(r.regie.waitedTotal(), 1)).mit("FOTOS", r.albumSize()).mit("QUALITAET", r.quality().name())
                .mit("BLATT_DATEI", blatt == null ? null : blatt.getPath());
        if (blatt != null && Datenseite.speicher().datenbank()) {
            try { z.mit("BLATT", java.nio.file.Files.readAllBytes(blatt.toPath())); } catch (Exception x) { /* ohne Blatt */ }
        }
        spaeter("BR_REGIELAUF", z, () -> regielaeufe++);
    }

    /** „Chronik: 12 Stunden, 1 Tag, 1 Nebelmeer, 2 Gespenster, 3 Zugfahrten, 2 Windfelder, 1 Regiebuch“ */
    public String zeile() {
        return String.format(Locale.GERMAN, "Chronik%s: %d Stunden, %d Tage, %d Nebelmeere, %d Gespenster, %d Zugfahrten, %d Windfelder, %d Regieläufe%s%s",
                an ? "" : " (angehalten)", stunden, tage, meere, gespenster, fahrten, felder, regielaeufe, fehler > 0 ? " · " + fehler + " Fehler" : "",
                letzte.isEmpty() ? "" : " · zuletzt " + letzte);
    }
}
