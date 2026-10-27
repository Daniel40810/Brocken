package com.dan.brocken.ui;

import com.dan.brocken.data.Chronik;
import com.dan.brocken.data.DateiSpeicher;
import com.dan.brocken.data.Daten;
import com.dan.brocken.data.Datenseite;
import com.dan.brocken.data.DbSpeicher;
import com.dan.brocken.data.Speicher;
import com.dan.brocken.data.Startdaten;
import com.dan.brocken.data.Szenendaten;
import com.dan.brocken.data.Uebernahme;
import com.dan.brocken.data.Verbindung;
import com.dan.brocken.data.Zustand;
import com.dan.brocken.gl.Renderer;
import com.dan.brocken.gl.SceneView;
import com.dan.brocken.scene.Buehne;
import com.dan.fbutton.FButton;
import com.dan.fcombobox.FComboBox;
import com.dan.foptionpane.FOptionPane;
import com.dan.store.Ablage;
import com.dan.store.Dienst;
import com.dan.store.Felder;
import com.dan.store.Protokoll;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Block DATEN (Phase 17, F37 A): Speicherort, Verbinden, Einrichten, Prüfen, Weiter wie zuletzt, Übernehmen, Sitzung
 * sichern; die Chronik als Tageskarte; Merkpunkte (Umschalt + Klick ins Gelände), gemerkte Ansichten (Taste V), Presets
 * der Packages und eigene Regiebücher. Gelesen und geschrieben wird im Faden der Daten ({@link Dienst#spaeter}); die
 * Szene ändert sich nur im Faden des Bildlaufs ({@link Renderer#imBild}).
 */
public final class DatenBlock extends JPanel {
    private final SceneView view;
    private final Renderer r;
    private final JLabel datenLbl = small(""), standLbl = small(""), szeneLbl = small(""), chronikLbl = small(""), tagLbl = small(""), merkLbl = small(""),
            gemerktLbl = small(""), presetLbl = small(""), regieLbl = small("");
    private final FComboBox tagBox = new FComboBox(), merkBox = new FComboBox(), gemerktBox = new FComboBox(), paketBox, presetBox = new FComboBox();
    private final com.dan.fcheckbox.FCheckBox chronikBox = new com.dan.fcheckbox.FCheckBox("Chronik schreiben");
    private List<Daten.Zeile> merkListe = List.of(), gemerktListe = List.of();
    private Map<String, Map<String, String>> presetListe = Map.of();
    private volatile String note = "";
    private long naechsteListe;
    private boolean fuellt;
    /** Das Protokoll für Block TECHNIK. */
    public final JLabel protokoll = small("");

    public DatenBlock(SceneView view, Renderer r) {
        this.view = view;
        this.r = r;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        put(datenLbl);
        JPanel g = new JPanel(new GridLayout(0, 2, 6, 4));
        g.setOpaque(false);
        FButton verb = new FButton("Verbinden …"), ohne = new FButton("Ohne Datenbank"), einr = new FButton("Einrichten"), pruef = new FButton("Prüfen"),
                weiter = new FButton("Weiter wie zuletzt"), ueber = new FButton("Übernehmen"), sichern = new FButton("Sitzung sichern");
        verb.setToolTipText("Mit dem Schema aus db/db.properties verbinden (fragt nach dem Passwort, wenn keins hinterlegt ist)");
        verb.addActionListener(e -> verbinden());
        ohne.setToolTipText("Mit Dateien in daten/ weiterarbeiten");
        ohne.addActionListener(e -> { Datenseite.setzen(new DateiSpeicher(DateiSpeicher.ordner())); neuLesen(); });
        einr.setToolTipText("Die Skripte aus db/sql ausführen und alles prüfen; löscht nie etwas");
        einr.addActionListener(e -> einrichter(false));
        pruef.setToolTipText("Nur prüfen: Tabellen, Spalten, Sichten, Metadaten, räumliche Indizes, Zahl der Startdaten");
        pruef.addActionListener(e -> einrichter(true));
        weiter.setToolTipText("Die letzte gesicherte Sitzung herstellen: Uhr, Zeitraffer, Wetter, Kamera, Qualität, Ton, Ebenen und Regler");
        weiter.addActionListener(e -> weiterWieZuletzt());
        ueber.setToolTipText("Chronik, Merkpunkte, gemerkte Ansichten, eigene Regiebücher und Presets aus daten/ in die Datenbank (die Dateien heißen danach *.uebernommen)");
        ueber.addActionListener(e -> uebernehmen());
        sichern.setToolTipText("Den Zustand jetzt sichern (sonst alle 2 Minuten und beim Ende)");
        sichern.addActionListener(e -> Dienst.spaeter(() -> { Dienst.sichern(false); SwingUtilities.invokeLater(this::zeilen); }));
        g.add(verb); g.add(ohne); g.add(einr); g.add(pruef); g.add(weiter); g.add(ueber); g.add(sichern);
        chronikBox.setSelected(true);
        chronikBox.setOpaque(false);
        chronikBox.addActionListener(e -> { Chronik c = Datenseite.chronik; if (c != null) c.schreiben(chronikBox.isSelected()); });
        g.add(chronikBox);
        put(g);
        put(standLbl);
        put(szeneLbl);

        sub("Chronik und Tageskarte");
        put(chronikLbl);
        tagBox.setToolTipText("Ein Szenentag aus der Chronik");
        tagBox.addActionListener(e -> { if (!fuellt) tageskarte(); });
        put(tagBox);
        put(tagLbl);

        sub("Merkpunkte (Umschalt + Klick ins Gelände)");
        put(merkBox);
        FButton mh = new FButton("Hin"), ml = new FButton("Löschen");
        mh.addActionListener(e -> merkpunktHin());
        ml.addActionListener(e -> loeschen(merkBox, merkListe, "BR_MERKPUNKT", this::listeMerk));
        put(grid(mh, ml));
        put(merkLbl);
        view.setShiftClickListener((x, y, w, h) -> SwingUtilities.invokeLater(() -> merkpunkt(x, y, w, h)));

        sub("Gemerkte Ansichten (Taste V)");
        put(gemerktBox);
        FButton gh = new FButton("Hin"), gm = new FButton("Merken (V)"), gl = new FButton("Löschen");
        gh.addActionListener(e -> ansichtHin());
        gm.addActionListener(e -> ansichtMerken());
        gl.addActionListener(e -> loeschen(gemerktBox, gemerktListe, "BR_ANSICHT", this::listeGemerkt));
        put(grid(gh, gm, gl));
        put(gemerktLbl);
        view.setKeyAction(java.awt.event.KeyEvent.VK_V, () -> SwingUtilities.invokeLater(this::ansichtMerken));

        sub("Presets der Packages");
        List<String> pk = new ArrayList<>();
        for (String k : Startdaten.PAKETE) pk.add(k.substring(k.lastIndexOf('.') + 1) + " · " + k.substring(0, k.lastIndexOf('.')));
        for (String k : Startdaten.STANDARD) pk.add(k.substring(k.lastIndexOf('.') + 1) + " · " + k.substring(0, k.lastIndexOf('.')));
        paketBox = new FComboBox(pk.toArray());
        paketBox.addActionListener(e -> { if (!fuellt) listePresets(null); });
        put(paketBox);
        put(presetBox);
        FButton an = new FButton("Anwenden"), unter = new FButton("Speichern unter …");
        an.setToolTipText("Alle Felder des Presets in das laufende Objekt der Szene schreiben");
        an.addActionListener(e -> presetAnwenden());
        unter.setToolTipText("Die Felder, wie sie jetzt in der Szene stehen, als eigenes Preset");
        unter.addActionListener(e -> presetSpeichern());
        put(grid(an, unter));
        put(presetLbl);

        sub("Eigene Regiebücher");
        FButton laden = new FButton("Aus Datei …");
        laden.setToolTipText("Ein Regiebuch als Text (Format wie scene/regie/*.txt) unter seinem Namen speichern; es steht nach dem nächsten Start im Block REGIEBÜCHER");
        laden.addActionListener(e -> regiebuchLaden());
        put(laden);
        put(regieLbl);
        put(small("<html><body style='width:215px'>Das Protokoll geht alle 5 s in BR_LOG (90 Tage) oder daten/protokoll.txt, der Zustand alle 2 min und beim "
                + "Ende in BR_SITZUNG oder daten/sitzungen, die Chronik in BR_STUNDE, BR_TAG, BR_NEBELMEER, BR_GESPENST, BR_ZUGFAHRT oder daten/zeilen. "
                + "Schema BROCKEN auf PDBORCL, SRID 8307. In QGIS: BR_BROCKEN_V (Bauten, Felsen, Orte, Wege, Gleis, Boden, Merkpunkte) und "
                + "BR_TAGESKARTE_V mit dem Filter TAG.</body></html>"));
        new Timer(2000, e -> zeilen()).start();
        SwingUtilities.invokeLater(this::neuLesen);
    }

    // ------------------------------------------------------------------ Bausteine

    static JLabel small(String t) { return ControlPanel.small(t); }

    private void put(Component c) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        p.add(c, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(p);
    }

    private void sub(String text) {
        JLabel h = new JLabel(text);
        h.setForeground(Theme.accent());
        h.setFont(new Font("SansSerif", Font.BOLD, 11));
        h.setBorder(BorderFactory.createEmptyBorder(10, 0, 3, 0));
        put(h);
    }

    private static JPanel grid(JComponent... c) {
        JPanel p = new JPanel(new GridLayout(1, c.length, 6, 0));
        p.setOpaque(false);
        for (JComponent x : c) p.add(x);
        return p;
    }

    private static String esc(String t) { return t == null ? "" : t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    private static void txt(JLabel l, String t) { if (!t.equals(l.getText())) l.setText(t); }

    private static String html(String t) { return "<html><body style='width:215px'>" + t + "</body></html>"; }

    private static Speicher sp() { return Datenseite.speicher(); }

    /** Im Faden der Daten arbeiten, danach auf dem Swing-Faden weiter; Fehler landen im Protokoll und in einer Meldung. */
    private <T> void hinten(String was, Callable<T> arbeit, Consumer<T> danach) {
        Dienst.spaeter(() -> {
            try {
                T t = arbeit.call();
                SwingUtilities.invokeLater(() -> danach.accept(t));
            } catch (Exception ex) {
                Protokoll.fehler("Daten", was + ": " + ex.getMessage(), ex instanceof RuntimeException ? ex : null);
                SwingUtilities.invokeLater(() -> {
                    note = "";
                    FOptionPane.showMessageDialog(this, was + " ging nicht:\n" + ex.getMessage(), "Brocken · " + was, FOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    /** Eine Liste in eine Auswahl, ohne dass sie dabei auslöst. */
    private void fuellen(FComboBox box, List<?> l, String wahl) {
        fuellt = true;
        try {
            box.removeAllItems();
            int k = -1;
            for (int i = 0; i < l.size(); i++) { box.addItem(l.get(i)); if (l.get(i).toString().equals(wahl)) k = i; }
            if (k >= 0) box.setSelectedIndex(k); else if (!l.isEmpty()) box.setSelectedIndex(0);
        } finally { fuellt = false; }
    }

    private static String gewaehlt(FComboBox box) { Object o = box.getSelectedItem(); return o == null ? null : o.toString(); }

    // ------------------------------------------------------------------ Zeilen

    /** Alle 2 s: Zeilen von DATEN und Chronik; alle 15 s die Listen aus dem Speicher. */
    void zeilen() {
        if (ControlPanel.menuOpen()) return;
        long gs = Dienst.gesichert();
        txt(datenLbl, html(esc(sp().wo()) + (note.isEmpty() ? "" : " · " + esc(note))
                + "<br>Sitzung " + Dienst.sitzung() + (gs > 0 ? ", gesichert " + String.format("%tR", gs) + " Uhr (Rechner)" : ", noch nicht gesichert")
                + (Datenseite.einrichterStand.isEmpty() ? "" : "<br>Einrichter: " + esc(Datenseite.einrichterStand))
                + (Dienst.letzterFehler().isEmpty() ? "" : "<br>Zuletzt: " + esc(Dienst.letzterFehler()))));
        txt(szeneLbl, html(esc(Szenendaten.zeile) + (Szenendaten.befunde.isEmpty() ? "" : "<br>" + esc(String.join("; ", Szenendaten.befunde)))));
        Chronik c = Datenseite.chronik;
        txt(chronikLbl, html(esc(c == null ? "Chronik: startet mit der Szene" : c.zeile())));
        StringBuilder pb = new StringBuilder();
        for (Protokoll.Zeile z : Protokoll.zuletzt(8)) {
            String t = Protokoll.zeile(z);
            pb.append(esc(t.length() > 150 ? t.substring(0, 150) + " …" : t)).append("<br>");
        }
        txt(protokoll, html(pb.toString()));
        long now = System.currentTimeMillis();
        if (now < naechsteListe) return;
        naechsteListe = now + 15_000;
        Dienst.spaeter(() -> {
            String st = Startbild.stand(sp(), "");
            List<String> tage = new ArrayList<>();
            try {
                LinkedHashSet<String> s = new LinkedHashSet<>();
                s.add(Daten.tag(r.clock.epochMillis()));
                for (Daten.Zeile z : sp().lesen("BR_STUNDE", null, 400)) s.add(z.text("SZENE_TAG"));
                tage.addAll(s);
            } catch (Ablage.Fehler ex) { /* ohne Tage */ }
            SwingUtilities.invokeLater(() -> {
                if (ControlPanel.menuOpen()) return;
                txt(standLbl, st.replace("width:620px", "width:215px"));
                String w = gewaehlt(tagBox);
                fuellen(tagBox, tage, w);
                if (w == null) tageskarte();
            });
        });
    }

    /** Nach einem Wechsel des Speichers alle Listen neu lesen. */
    private void neuLesen() {
        listePresets(null);
        listeGemerkt();
        listeMerk();
        naechsteListe = 0;
        zeilen();
    }

    // ------------------------------------------------------------------ Tageskarte

    private void tageskarte() {
        String tag = gewaehlt(tagBox);
        if (tag == null) return;
        hinten("Tageskarte", () -> {
            Map<String, Object> wo = Map.of("SZENE_TAG", tag);
            List<Daten.Zeile> h = sp().lesen("BR_STUNDE", wo, 48), m = sp().lesen("BR_NEBELMEER", wo, 20), g = sp().lesen("BR_GESPENST", wo, 50),
                    z = sp().lesen("BR_ZUGFAHRT", wo, 40), t = sp().lesen("BR_TAG", wo, 1);
            StringBuilder b = new StringBuilder("<b>Tageskarte " + tag + "</b>");
            if (h.isEmpty() && m.isEmpty() && g.isEmpty() && z.isEmpty()) return html(b.append(" · noch nichts in der Chronik").toString());
            if (!h.isEmpty()) {
                LinkedHashSet<String> lagen = new LinkedHashSet<>();
                double tmin = Double.NaN, tmax = Double.NaN, wmax = Double.NaN;
                for (int i = h.size() - 1; i >= 0; i--) {
                    Daten.Zeile x = h.get(i);
                    lagen.add(x.text("LAGE_NAME"));
                    double tc = x.zahl("TEMPERATUR_C"), w = x.zahl("BOE_GIPFEL_MS");
                    tmin = Double.isNaN(tmin) ? tc : Math.min(tmin, tc);
                    tmax = Double.isNaN(tmax) ? tc : Math.max(tmax, tc);
                    wmax = Double.isNaN(wmax) ? w : Math.max(wmax, w);
                }
                b.append(String.format(Locale.GERMAN, "<br>%d Stunden · %s · %.1f bis %.1f °C · Böen bis %.0f m/s", h.size(), esc(String.join(", ", lagen)), tmin, tmax, wmax));
            }
            if (!t.isEmpty()) b.append(String.format(Locale.GERMAN, "<br>Schnee am Gipfel %.0f cm, Schneegrenze %s · Nebelstunden %d",
                    t.get(0).zahl("SCHNEE_GIPFEL_CM"), t.get(0).text("SCHNEEGRENZE_M") == null ? "keine" : t.get(0).text("SCHNEEGRENZE_M") + " m",
                    t.get(0).ganz("NEBELSTUNDEN", 0)));
            for (Daten.Zeile x : m)
                b.append(String.format(Locale.GERMAN, "<br>Nebelmeer ab %s, %.0f–%.0f m%s", esc(x.text("SZENE_TEXT")), x.zahl("UNTEN_M"), x.zahl("OBEN_MAX_M"),
                        x.text("ENDE_TEXT") == null ? "" : ", aufgelöst " + esc(x.text("ENDE_TEXT"))));
            for (Daten.Zeile x : g)
                b.append(String.format(Locale.GERMAN, "<br>Brockengespenst %s, Sonne %.1f°%s, %.0f s", esc(x.text("SZENE_TEXT")), x.zahl("SONNENHOEHE_GRAD"),
                        x.ja("GLORIE") ? ", mit Glorie" : "", x.zahl("DAUER_S")));
            for (Daten.Zeile x : z)
                b.append(String.format(Locale.GERMAN, "<br>%s: %s–%s, %+.1f min, %d Pfiffe%s", esc(x.text("ZUG")), x.text("IST_AB"), x.text("IST_AN"),
                        x.zahl("VERSPAETUNG_MIN"), x.ganz("PFIFFE", 0), x.ja("MITGEFAHREN") ? ", mitgefahren" : ""));
            return html(b.toString());
        }, s -> txt(tagLbl, s));
    }

    // ------------------------------------------------------------------ Verbinden, Einrichten, Übernehmen, Weiter

    private void verbinden() {
        note = "verbinde …";
        hinten("Verbinden", Verbindung::konfig, k -> {
            com.dan.store.Db.Konfig kk = k;
            char[] neu = null;
            if (!k.hatPasswort()) {
                com.dan.ftextfield.FPasswordfield pw = new com.dan.ftextfield.FPasswordfield();
                JPanel p = new JPanel(new BorderLayout(0, 6));
                p.add(new JLabel("Passwort für " + k.kurz() + ":"), BorderLayout.NORTH);
                p.add(pw, BorderLayout.CENTER);
                if (FOptionPane.showConfirmDialog(this, p, "Brocken · Datenbank", FOptionPane.OK_CANCEL_OPTION) != FOptionPane.OK_OPTION || pw.getPassword().length == 0) {
                    note = "";
                    return;
                }
                neu = pw.getPassword();
                kk = k.mitPasswort(neu);
            }
            char[] eingabe = neu;
            com.dan.store.Db.Konfig kf = kk;
            hinten("Verbinden", () -> {
                String[] grund = {""};
                DbSpeicher d = Datenseite.verbinden(kf, grund);
                if (d == null) throw new Ablage.Fehler(grund[0]);
                if (eingabe != null) try { com.dan.store.Db.passwortSpeichern(kf, eingabe); } catch (Exception ex) { Protokoll.warnung("Daten", "Passwort nicht eingetragen: " + ex.getMessage()); }
                int st = Verbindung.einrichter().stufe(d.verbindung());
                return new Object[]{d, st};
            }, o -> {
                DbSpeicher d = (DbSpeicher) o[0];
                int st = (Integer) o[1];
                if (st < Verbindung.VERSION) {
                    if (FOptionPane.showConfirmDialog(this, d.wo() + " ist noch nicht eingerichtet (Stufe " + st + ").\nJetzt einrichten? (löscht nie etwas)",
                            "Brocken · Datenbank", FOptionPane.YES_NO_OPTION) != FOptionPane.YES_OPTION) { d.schliessen(); note = "nicht eingerichtet"; return; }
                    Datenseite.setzen(d);
                    einrichter(false);
                    return;
                }
                Datenseite.setzen(d);
                note = "verbunden";
                neuLesen();
            });
        });
    }

    private void einrichter(boolean nurPruefen) {
        if (!(sp() instanceof DbSpeicher d)) {
            FOptionPane.showMessageDialog(this, "Erst mit der Datenbank verbinden („Verbinden …“).", "Brocken · Einrichter", FOptionPane.ERROR_MESSAGE);
            return;
        }
        note = nurPruefen ? "prüfe …" : "richte ein …";
        hinten(nurPruefen ? "Prüfen" : "Einrichten", () -> {
            var e = Verbindung.einrichter();
            return nurPruefen ? e.pruefen(d.verbindung(), Verbindung.skripte())
                    : e.einrichten(d.verbindung(), Verbindung.skripte(), (t, a) -> note = String.format(Locale.GERMAN, "%.0f %% · %s", a * 100, t));
        }, er -> {
            Startbild.merkeEinrichter(er.kurz());
            note = "";
            if (er.ok()) Dienst.spaeter(Dienst::neuBeginnen);
            Protokoll.info("Einrichter", (nurPruefen ? "Prüfen: " : "Einrichten: ") + er.kurz());
            for (String z : er.zeilen()) if (!z.startsWith("ok") && !z.contains("alles in Ordnung")) Protokoll.warnung("Einrichter", z);
            JLabel t = small("<html><body style='width:560px'>" + esc(String.join("\n", er.zeilen())).replace("\n", "<br>") + "</body></html>");
            javax.swing.JScrollPane spn = new javax.swing.JScrollPane(t);
            spn.setPreferredSize(new Dimension(640, 420));
            FOptionPane.showMessageDialog(this, spn, "Brocken · " + (nurPruefen ? "Prüfen: " : "Einrichten: ") + er.kurz(),
                    er.ok() ? FOptionPane.INFORMATION_MESSAGE : FOptionPane.WARNING_MESSAGE);
            neuLesen();
        });
    }

    private void uebernehmen() {
        if (!sp().datenbank()) {
            FOptionPane.showMessageDialog(this, "Erst mit der Datenbank verbinden („Verbinden …“).", "Brocken · Übernehmen", FOptionPane.ERROR_MESSAGE);
            return;
        }
        DateiSpeicher von = new DateiSpeicher(DateiSpeicher.ordner());
        if (!Uebernahme.etwasDa(von)) { FOptionPane.showMessageDialog(this, "In " + von.wo() + " liegt nichts zum Übernehmen.", "Brocken · Übernahme", FOptionPane.INFORMATION_MESSAGE); return; }
        note = "übernehme …";
        hinten("Übernehmen", () -> Uebernahme.uebernehmen(von, sp()), z -> {
            note = "übernommen";
            FOptionPane.showMessageDialog(this, "Aus " + von.wo() + " nach " + sp().wo() + ":\n" + z, "Brocken · Übernahme", FOptionPane.INFORMATION_MESSAGE);
            neuLesen();
        });
    }

    private void weiterWieZuletzt() {
        hinten("Weiter wie zuletzt", () -> {
            Daten.Sitzung s = sp().letzteSitzung(Dienst.sitzung());
            if (s == null || s.zustand() == null) throw new Ablage.Fehler("Keine gesicherte Sitzung in " + sp().wo());
            return s;
        }, s -> Weiter.anwenden(r, s, () -> view.setMode(r.camera.mode)));
    }

    // ------------------------------------------------------------------ Vorschau

    /** Ein kleines Bild der Szene (320 Punkte breit) als PNG, danach weiter mit {@code danach} im Faden der Daten. */
    private void vorschau(Consumer<byte[]> danach) {
        r.grabRequest = img -> {
            byte[] png = null;
            try {
                java.awt.image.BufferedImage s = new java.awt.image.BufferedImage(320, Math.max(1, (int) Math.round(320.0 * img.getHeight() / img.getWidth())),
                        java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g2 = s.createGraphics();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(img, 0, 0, s.getWidth(), s.getHeight(), null);
                g2.dispose();
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(s, "png", bo);
                png = bo.toByteArray();
            } catch (Exception ex) { /* ohne Vorschau */ }
            byte[] v = png;
            Dienst.spaeter(() -> danach.accept(v));
        };
    }

    private static Long sitzung() { long s = Dienst.sitzung(); return s > 0 ? s : null; }

    // ------------------------------------------------------------------ Merkpunkte (F35 A)

    private void merkpunkt(int x, int y, int w, int h) {
        double[] b = r.bodenUnter(x, y, w, h);
        if (b == null) { r.hudMeldung("Merkpunkt: kein Gelände getroffen"); return; }
        String ort = Buehne.naechsterOrt(b[0], b[2]);
        String notiz = FOptionPane.showInputDialog(this, String.format(Locale.GERMAN, "Merkpunkt %s, %.0f m über NHN, %.0f m vom Auge.\nNotiz:", ort, b[1], b[3]),
                "Brocken · Merkpunkt", FOptionPane.QUESTION_MESSAGE);
        if (notiz == null) return;
        double ms = r.clock.epochMillis();
        double[] e = r.camera.eye.clone();
        Daten.Zeile z = Daten.szene(new Daten.Zeile(), ms).mit("NAME", "Merkpunkt " + Daten.text(ms)).mit("NOTIZ", notiz.isBlank() ? null : notiz.strip())
                .mit("X_M", Daten.r(b[0], 2)).mit("Z_M", Daten.r(b[2], 2)).mit("NHN_M", Daten.r(b[1], 2)).mit("AUGE_X", Daten.r(e[0], 2)).mit("AUGE_Y", Daten.r(e[1], 2))
                .mit("AUGE_Z", Daten.r(e[2], 2)).mit("BLICK_GRAD", Daten.r(r.camera.heading(), 1)).mit("LAGE", Daten.Geom.punkt(b[0], b[2])).mit("LAGE_TEXT", ort)
                .mit("SITZUNG_ID", sitzung()).mit("HERKUNFT", Daten.EIGEN);
        vorschau(png -> {
            z.mit("VORSCHAU", png);
            try {
                sp().schreiben("BR_MERKPUNKT", z);
                Protokoll.info("Merkpunkt", z.text("NAME") + " · " + ort);
                SwingUtilities.invokeLater(() -> { r.hudMeldung("Merkpunkt gesetzt · " + ort); listeMerk(); });
            } catch (Ablage.Fehler ex) {
                Protokoll.fehler("Merkpunkt", "nicht gespeichert: " + ex.getMessage(), null);
            }
        });
    }

    /** Eine Zeile in der Auswahl: Name und Notiz. */
    private record Eintrag(Daten.Zeile z, String text) {
        @Override public String toString() { return text; }
    }

    private void listeMerk() {
        hinten("Merkpunkte lesen", () -> sp().lesen("BR_MERKPUNKT", null, 100), l -> {
            merkListe = l;
            List<Eintrag> e = new ArrayList<>();
            for (Daten.Zeile z : l) e.add(new Eintrag(z, z.text("NAME") + (z.text("NOTIZ") == null ? "" : " · " + z.text("NOTIZ"))));
            fuellen(merkBox, e, null);
            txt(merkLbl, html(l.size() + " Merkpunkte in " + esc(sp().wo())));
        });
    }

    private void merkpunktHin() {
        int i = merkBox.getSelectedIndex();
        if (i < 0 || i >= merkListe.size()) return;
        Daten.Zeile z = merkListe.get(i);
        double ax = z.zahl("AUGE_X"), ay = z.zahl("AUGE_Y"), az = z.zahl("AUGE_Z"), mx = z.zahl("X_M"), mz = z.zahl("Z_M"), ms = z.zahl("SZENE_MS");
        r.imBild(() -> {
            r.camera.stopFahrt();
            r.camera.stopMitfahrt();
            r.camera.mode = com.dan.brocken.camera.Camera.Mode.FREI;
            double gier = Math.toDegrees(Math.atan2(mx - ax, -(mz - az)));
            r.camera.place(ax, ay, az, (gier + 360) % 360, 8);
            r.camera.snap();
            if (!Double.isNaN(ms)) r.clock.setEpochMillis(ms);
            r.nachSprung();
            r.hudMeldung(z.text("NAME") + (z.text("NOTIZ") == null ? "" : " · " + z.text("NOTIZ")));
        });
        view.setMode(com.dan.brocken.camera.Camera.Mode.FREI);
        view.requestFocusInWindow();
    }

    private void loeschen(FComboBox box, List<Daten.Zeile> liste, String tabelle, Runnable danach) {
        int i = box.getSelectedIndex();
        if (i < 0 || i >= liste.size()) return;
        long id = liste.get(i).ganz("ID", -1);
        hinten("Löschen", () -> { sp().loeschen(tabelle, id); return null; }, x -> danach.run());
    }

    // ------------------------------------------------------------------ Ansichten (F33 A)

    private void ansichtMerken() {
        double[] k = Zustand.kamera(r);
        double ms = r.clock.epochMillis();
        String name = Daten.text(ms) + " · " + Buehne.naechsterOrt(k[0], k[2]);
        String zustand = Felder.alsText(Zustand.erfassen(r), "Brocken · gemerkte Ansicht");
        Daten.Zeile z = Daten.szene(new Daten.Zeile(), ms).mit("NAME", name).mit("ART", "GEMERKT").mit("KAM_X", Daten.r(k[0], 2)).mit("KAM_Y", Daten.r(k[1], 2))
                .mit("KAM_Z", Daten.r(k[2], 2)).mit("KAM_GIER_GRAD", Daten.r(k[3], 2)).mit("KAM_NICK_GRAD", Daten.r(k[4], 2))
                .mit("AUGE_UEBER_GRUND_M", Daten.r(k[1] - r.camera.groundLevel, 2)).mit("LAGE", Daten.Geom.punkt(k[0], k[2])).mit("ZUSTAND", zustand)
                .mit("SITZUNG_ID", sitzung()).mit("HERKUNFT", Daten.EIGEN);
        vorschau(png -> {
            z.mit("VORSCHAU", png);
            try {
                sp().schreiben("BR_ANSICHT", z);
                SwingUtilities.invokeLater(() -> { r.hudMeldung("Ansicht gemerkt: " + name); listeGemerkt(); });
            } catch (Ablage.Fehler ex) {
                Protokoll.fehler("Ansicht", "nicht gemerkt: " + ex.getMessage(), null);
            }
        });
    }

    private void listeGemerkt() {
        hinten("Ansichten lesen", () -> sp().lesen("BR_ANSICHT", Map.of("ART", "GEMERKT"), 100), l -> {
            gemerktListe = l;
            List<Eintrag> e = new ArrayList<>();
            for (Daten.Zeile z : l) e.add(new Eintrag(z, z.text("NAME")));
            fuellen(gemerktBox, e, null);
            txt(gemerktLbl, html(l.size() + " gemerkte Ansichten in " + esc(sp().wo()) + "; die festen liegen auf den Tasten 1–9"));
        });
    }

    private void ansichtHin() {
        int i = gemerktBox.getSelectedIndex();
        if (i < 0 || i >= gemerktListe.size()) return;
        Daten.Zeile a = gemerktListe.get(i);
        String zustand = a.text("ZUSTAND");
        r.imBild(() -> {
            if (zustand != null) {
                List<String> f = Zustand.anwenden(r, Felder.ausText(zustand));
                if (!f.isEmpty()) Protokoll.warnung("Ansicht", "nicht hergestellt: " + String.join(", ", f));
            } else {
                r.camera.stopFahrt();
                r.camera.stopMitfahrt();
                r.camera.mode = com.dan.brocken.camera.Camera.Mode.FREI;
                r.camera.place(a.zahl("KAM_X"), a.zahl("KAM_Y"), a.zahl("KAM_Z"), a.zahl("KAM_GIER_GRAD"), a.zahl("KAM_NICK_GRAD"));
                r.camera.snap();
            }
            r.hudMeldung("Ansicht: " + a.text("NAME"));
        });
        view.setMode(com.dan.brocken.camera.Camera.Mode.FREI);
        view.requestFocusInWindow();
    }

    // ------------------------------------------------------------------ Presets (F34 A)

    private String paket() { String s = gewaehlt(paketBox); return s == null ? null : s.substring(s.indexOf(" · ") + 3); }
    private String klasse() { String s = gewaehlt(paketBox); return s == null ? null : s.substring(0, s.indexOf(" · ")); }

    private void listePresets(String wahl) {
        String k = klasse();
        if (k == null) return;
        hinten("Presets lesen", () -> sp().presets(k), l -> {
            presetListe = l;
            fuellen(presetBox, new ArrayList<>(l.keySet()), wahl);
            Object o = Zustand.objekt(r, k);
            txt(presetLbl, html(l.size() + " Presets · " + (o == null ? "nicht in der Szene" : "in der Szene")));
        });
    }

    private void presetAnwenden() {
        String n = gewaehlt(presetBox), k = klasse();
        if (n == null || k == null) return;
        Map<String, String> f = presetListe.get(n);
        Object o = Zustand.objekt(r, k);
        if (o == null || f == null) { txt(presetLbl, html(esc(k) + " läuft nicht in der Szene.")); return; }
        r.imBild(() -> {
            List<String> nicht = Felder.schreiben(o, f);
            SwingUtilities.invokeLater(() -> txt(presetLbl, html("„" + esc(n) + "“ angewandt: " + (f.size() - nicht.size()) + " Felder"
                    + (nicht.isEmpty() ? "" : ", nicht: " + esc(String.join(", ", nicht))))));
            Protokoll.info("Preset", k + " ← „" + n + "“");
        });
        view.requestFocusInWindow();
    }

    private void presetSpeichern() {
        String p = paket(), k = klasse();
        Object o = Zustand.objekt(r, k);
        if (o == null) { txt(presetLbl, html(esc(k) + " läuft nicht in der Szene.")); return; }
        String n = FOptionPane.showInputDialog(this, "Name des Presets für " + k, "Brocken · Preset speichern", FOptionPane.QUESTION_MESSAGE);
        if (n == null || n.isBlank()) return;
        LinkedHashMap<String, String> werte = Felder.lesen(o);
        hinten("Preset speichern", () -> { sp().preset(p, k, n.strip(), "Aus der Szene am " + java.time.LocalDate.now(), werte); return null; }, x -> listePresets(n.strip()));
    }

    // ------------------------------------------------------------------ Regiebücher

    private void regiebuchLaden() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser(new java.io.File("."));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Regiebuch (*.txt)", "txt"));
        if (fc.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        java.io.File f = fc.getSelectedFile();
        hinten("Regiebuch laden", () -> {
            String t = java.nio.file.Files.readString(f.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            com.dan.brocken.scene.Regie.Book b = com.dan.brocken.scene.Regie.parse(t);
            sp().regiebuch(b.name(), t, b.about());
            return b.name();
        }, n -> txt(regieLbl, html("„" + esc(n) + "“ gespeichert in " + esc(sp().wo()) + "; im Block REGIEBÜCHER nach dem nächsten Start.")));
    }
}
