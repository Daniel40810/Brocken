package com.dan.brocken.ui;

import com.dan.brocken.app.Bibliothek;
import com.dan.brocken.camera.Ansichten;
import com.dan.brocken.camera.Camera;
import com.dan.brocken.gl.ComputeProbe;
import com.dan.brocken.gl.Quality;
import com.dan.brocken.gl.Renderer;
import com.dan.brocken.gl.SceneView;
import com.dan.brocken.scene.SceneClock;
import com.dan.brocken.scene.Sun;
import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcollapsiblepanel.FCollapsiblePanel;
import com.dan.fcombobox.FComboBox;
import com.dan.fslider.FSlider;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Das Bedienfeld rechts als Klappblöcke (aus Geiranger, entschlackt). Die Blöcke stehen nach dem Werkbuch (Abschnitt
 * 8) schon alle da: KAMERA, ZEIT, WETTER, WIND, NEBEL, BAHN, JAHR, KLANG, REGIEBÜCHER, DATEN, TECHNIK. In Phase 1
 * arbeiten KAMERA (neun Ansichten, frei fliegen, kreisen), ZEIT (Datum, Uhrzeit, Zeitraffer, Sonne) und TECHNIK
 * (Qualität, Auflösung, Belichtung, HUD, Marken, Foto, Shader, Tempo, Compute-Test, Physik, Packages), seit Phase 3
 * WETTER (Regie mit dem Klima „Brocken“, Regiebücher, Lagen, Handregler, Wolken und Schatten), seit Phase 4 WIND
 * (Windfeld, Pfeile, Rauchfäden, neu rechnen); die übrigen sagen, in welcher Phase sie kommen. Offen bleibt, was zuletzt offen war (je Rechner gemerkt). Jeder Regler schreibt direkt in
 * die öffentlichen Felder von Uhr, Kamera und Renderer; es gibt keinen zweiten Zustand.
 */
public final class ControlPanel extends JPanel implements javax.swing.Scrollable {
    private final SceneView view;
    private final Renderer r;
    private final SceneClock clock;
    private final JLabel dateLbl = label(""), timeLbl = label(""), sunLbl = small(""), camLbl = small(""), perfLbl = small(""), computeLbl = small(""),
            glLbl = small(""), physLbl = small(""), gelLbl = small(""), hudLbl = small(""), scaleLbl = label(""), qualLbl = small(""), pkgLbl = small(""),
            wetterLbl = small(""), windLbl = small(""), himmelLbl = small(""), coverLbl = label(""), visLbl = label(""), feldLbl = small(""), lebenLbl = small("");
    private final FCheckBox arrowsBox = new FCheckBox("Pfeile des Windfelds"), faedenBox = new FCheckBox("Rauchfäden (30 m über Grund, 5 km luvwärts)");
    /** Phase 5: was sich im Wind bewegt. */
    /** Phase 6: Nebel über Gelände. */
    private final FCheckBox nebelBox = new FCheckBox("Nebel über Gelände"), talRegieBox = new FCheckBox("Talnebel nach der Regie");
    private final FSlider talSl = new FSlider(0, 120, 0);
    private final javax.swing.JLabel nebelLbl = small(""), talLbl = small("");
    /** Phase 7: Licht im Nebel. */
    private final FCheckBox glorieBox = new FCheckBox("Glorie, Nebelbogen, Gespenst (Mie)"), tropfenRegieBox = new FCheckBox("Tröpfchen nach der Lage"),
            haloRegieBox = new FCheckBox("Halos nach dem hohen Schleier");
    private final FSlider tropfenSl = new FSlider(2, 30, 10), haloSl = new FSlider(0, 100, 0);
    private final javax.swing.JLabel lichtLbl = small(""), tropfenLbl = small(""), haloLbl = small("");
    private final FCheckBox waldBox = new FCheckBox("Wald (Bäume)"), felsBox = new FCheckBox("Felsburgen und Blockmeer"),
            bodenBox = new FCheckBox("Boden nach der Karte, Bodendecke");
    private final FCheckBox gleisBox = new FCheckBox("Gleis zeigen"), betonBox = new FCheckBox("Schwellen aus Beton");
    private final FCheckBox zugBox = new FCheckBox("Züge nach Fahrplan (Sommer 2026)");
    private final FCheckBox gipfelBox = new FCheckBox("Bauten am Gipfel"), menschenBox = new FCheckBox("Menschen (Wanderer, Fahrgäste)");
    private final javax.swing.JLabel gipfelZeile = small("");
    private final FCheckBox tonBox = new FCheckBox("Ton (Lautsprecher)"), walpurgisBox = new FCheckBox("Walpurgisnacht jetzt"),
            walpurgisKalBox = new FCheckBox("Walpurgisnacht in der Nacht zum 1. Mai");
    private final FSlider lautSl = new FSlider(0, 200, 100), windDbSl = new FSlider(-15, 6, -4);
    private final javax.swing.JLabel windDbLbl = small("");
    private final javax.swing.JLabel klangZeile = small("");
    private final javax.swing.JLabel regieZeile = small("");
    private final javax.swing.JLabel zugZeile = small("");
    private final javax.swing.JLabel bahnZeile = small("");
    private final FCheckBox schneeBox = new FCheckBox("Schnee zeigen"), schneeRegieBox = new FCheckBox("Schnee nach dem Jahr");
    private final FSlider schneeSl = new FSlider(0, 300, 0);
    private final javax.swing.JLabel schneeLbl = small(""), jahrZeile = small("");
    private final FCheckBox raueisBox = new FCheckBox("Raueis zeigen"), raueisRegieBox = new FCheckBox("Raueis nach der Regie");
    private final FSlider raueisSl = new FSlider(0, 30, 0);
    private final javax.swing.JLabel raueisLbl = small(""), raueisZeile = small("");
    private final FCheckBox grasBox = new FCheckBox("Gras im Wind"), rauchBox = new FCheckBox("Rauch am Brockenhotel"), rabenBox = new FCheckBox("Kolkraben im Aufwind");
    private final FSlider coverSl = new FSlider(0, 80, 25), visSl = new FSlider(0, 100, 60);
    private final FCheckBox cloudsBox = new FCheckBox("Wolken (com.dan.cloud)"), shadowBox = new FCheckBox("Schatten der Kuppe je Sonnenstand"),
            messBox = new FCheckBox("Belichtung im Bild messen");
    private final FSlider time = new FSlider(0, 24 * 60 - 1, 9 * 60), scaleSl = new FSlider(50, 100, 85), expSl = new FSlider(-30, 30, 0);
    private final FCheckBox free = new FCheckBox("Frei fliegen (F)"), orbit = new FCheckBox("Von selbst kreisen (R)"),
            marks = new FCheckBox("Marken (Raster, Höhenlinien, Kern, Orte)"), autoBox = new FCheckBox("Auflösung selbst regeln");
    private FComboBox lapseBox, qualBox, hudBox;
    private boolean fromScene;
    private int ticks;
    private JPanel target = this;
    private static final Preferences PREFS = prefs();
    private final java.util.List<Object[]> bloecke = new java.util.ArrayList<>();
    /** Phase 17: Block DATEN. */
    private DatenBlock daten;
    private final java.util.List<FCollapsiblePanel> zu = new java.util.ArrayList<>();
    private static final double[] LAPSE = {0, 1, 10, 60, 180, 720, 3600};

    /** Die Reihenfolge der Blöcke (Werkbuch, Abschnitt 8). */
    public static final String[] REIHENFOLGE = {"KAMERA", "ZEIT", "WETTER", "WIND", "NEBEL", "BAHN", "JAHR", "KLANG", "REGIEBÜCHER", "DATEN", "TECHNIK"};

    private static Preferences prefs() {
        try { return Preferences.userRoot().node("com/dan/brocken/bedienfeld"); } catch (RuntimeException | Error e) { return null; }
    }

    public ControlPanel(SceneView view) {
        this.view = view;
        this.r = view.renderer();
        this.clock = r.clock;
        setBackground(Theme.bg());
        setOpaque(true);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 8, 16, 10));

        // ============================================================== KAMERA
        block("KAMERA", true);
        put(camLbl);
        JPanel v1 = grid(2), v2 = grid(2), v3 = grid(2), v4 = grid(2), v5 = grid(2);
        JPanel[] rows = {v1, v1, v2, v2, v3, v3, v4, v4, v5};
        for (Ansichten.Ansicht a : Ansichten.ALLE) {
            FButton b = new FButton(a.key() + " " + a.name());
            b.setToolTipText(Ansichten.describe(a, Renderer.PHASE));
            b.addActionListener(e -> ansicht(a));
            rows[a.key() - 1].add(b);
            view.setKeyAction(java.awt.event.KeyEvent.VK_0 + a.key(), () -> javax.swing.SwingUtilities.invokeLater(() -> ansicht(a)));
            view.setKeyAction(java.awt.event.KeyEvent.VK_NUMPAD0 + a.key(), () -> javax.swing.SwingUtilities.invokeLater(() -> ansicht(a)));
        }
        put(v1);
        put(v2);
        put(v3);
        put(v4);
        put(v5);
        free.addActionListener(e -> { view.setMode(free.isSelected() ? Camera.Mode.FREI : Camera.Mode.KREISEN); view.requestFocusInWindow(); });
        orbit.addActionListener(e -> { view.setAutoOrbit(orbit.isSelected()); view.requestFocusInWindow(); });
        put(free);
        put(orbit);
        view.setModeListener(m -> javax.swing.SwingUtilities.invokeLater(() -> free.setSelected(m == Camera.Mode.FREI)));
        view.setOrbitListener(b -> javax.swing.SwingUtilities.invokeLater(() -> orbit.setSelected(b)));
        JPanel home = grid(1);
        FButton hb = new FButton("Pos1 Grundansicht");
        hb.addActionListener(e -> { view.goHome(); view.requestFocusInWindow(); });
        home.add(hb);
        put(home);
        put(small("<html><body style='width:215px'>Die Ansichten stehen seit Phase 2 auf dem Gelände (Bahnhöfe nach ALKIS). "
                + "Mitfahren auf dem Zug kommt mit Phase 13.</body></html>"));

        // ============================================================== ZEIT
        block("ZEIT", true);
        put(dateLbl);
        JPanel days = grid(3);
        FButton minus = new FButton("− Tag"), heute = new FButton("21.10.2026"), plus = new FButton("+ Tag");
        minus.addActionListener(e -> { clock.shiftDays(-1); syncFromScene(); view.requestFocusInWindow(); });
        plus.addActionListener(e -> { clock.shiftDays(1); syncFromScene(); view.requestFocusInWindow(); });
        heute.setToolTipText("Zurück zum Start der Szene: " + new SceneClock().label());
        heute.addActionListener(e -> { clock.set(SceneClock.START_DATE, SceneClock.START_HOUR); syncFromScene(); view.requestFocusInWindow(); });
        days.add(minus); days.add(heute); days.add(plus);
        put(days);
        FComboBox monthBox = new FComboBox(new Object[]{"Monat springen …", "21. Januar", "21. Februar", "21. März", "21. April", "21. Mai", "21. Juni",
                "21. Juli", "21. August", "21. September", "21. Oktober", "21. November", "21. Dezember"});
        monthBox.setToolTipText("Zum 21. eines Monats im laufenden Jahr, gleiche Uhrzeit (Schnee, Raueis und Heide kommen mit den Phasen 10 und 11)");
        monthBox.addActionListener(e -> {
            int i = monthBox.getSelectedIndex();
            if (i <= 0) return;
            clock.set(LocalDate.of(clock.date().getYear(), i, 21), clock.hour());
            monthBox.setSelectedIndex(0);
            syncFromScene();
            view.requestFocusInWindow();
        });
        put(monthBox);
        put(timeLbl);
        put(time);
        time.addChangeListener(e -> { if (!fromScene) clock.setHour(time.getValue() / 60.0); labels(); });
        put(label("Zeitraffer"));
        lapseBox = new FComboBox(new Object[]{"Uhr steht", "Echtzeit", "10-fach", "1 min/s", "3 min/s", "12 min/s (ein Tag in 2 min)", "1 h/s"});
        lapseBox.setSelectedIndex(1);
        lapseBox.addActionListener(e -> { clock.lapse = LAPSE[Math.max(0, lapseBox.getSelectedIndex())]; view.requestFocusInWindow(); });
        put(lapseBox);
        put(sunLbl);
        put(small("<html><body style='width:215px'>Deutsche Zeit (MEZ, im Sommer MESZ). Am Brocken steht die Sonne gegen 12:18 Uhr MEZ "
                + "im Süden. Himmel, Sonne, Mond und Sterne rechnet seit Phase 3 com.dan.sky.</body></html>"));

        // ============================================================== WETTER (Phase 3)
        block("WETTER", true);
        put(wetterLbl);
        java.util.List<Object> buecher = new java.util.ArrayList<>();
        buecher.add("Regiebuch …");
        for (com.dan.weather.Scenario sc : com.dan.brocken.wiring.KlimaBrocken.regiebuecher()) buecher.add(sc.name());
        FComboBox bookBox = new FComboBox(buecher.toArray());
        bookBox.setToolTipText("Ein Wetter-Regiebuch ab seinem Beginn am Tag der Uhr, mit seinem Zeitraffer");
        bookBox.addActionListener(e -> {
            int i = bookBox.getSelectedIndex();
            if (i <= 0) return;
            com.dan.weather.Scenario sc = com.dan.brocken.wiring.KlimaBrocken.regiebuecher().get(i - 1);
            String w = r.wetter(sc.name(), 0);
            clock.lapse = sc.lapse();
            lapseBox.setSelectedIndex(nearestLapse(sc.lapse()));
            clock.lapse = sc.lapse();
            if (w != null) r.hudMeldung(w);
            bookBox.setSelectedIndex(0);
            syncFromScene();
            view.requestFocusInWindow();
        });
        put(bookBox);
        java.util.List<Object> lagen = new java.util.ArrayList<>();
        lagen.add("Lage von Hand …");
        for (com.dan.weather.Weather.Phase ph : com.dan.weather.Weather.Phase.values()) lagen.add(com.dan.brocken.wiring.KlimaBrocken.name(ph));
        FComboBox phaseBox = new FComboBox(lagen.toArray());
        phaseBox.setToolTipText("Freie Regie ab jetzt, beginnend mit dieser Lage (danach wählt das Klima „Brocken“)");
        phaseBox.addActionListener(e -> {
            int i = phaseBox.getSelectedIndex();
            if (i <= 0) return;
            String w = r.wetter(com.dan.weather.Weather.Phase.values()[i - 1].name(), 0);
            if (w != null) r.hudMeldung("Freie Regie: " + w);
            phaseBox.setSelectedIndex(0);
            view.requestFocusInWindow();
        });
        put(phaseBox);
        put(coverLbl);
        put(coverSl);
        coverSl.setToolTipText("Bedeckung der unteren Schicht in Achteln, von Hand (hält bis „Regie weiter“)");
        coverSl.addChangeListener(e -> { if (!fromScene) r.wiring.hand(com.dan.weather.Weather.Var.COVER, coverSl.getValue() / 10.0); });
        put(visLbl);
        put(visSl);
        visSl.setToolTipText("Sicht unter den Wolken, von Hand (1 bis 200 km, hält bis „Regie weiter“)");
        visSl.addChangeListener(e -> { if (!fromScene) r.wiring.hand(com.dan.weather.Weather.Var.VISIBILITY, visKm(visSl.getValue())); });
        JPanel regie = grid(1);
        FButton weiter = new FButton("Regie weiter");
        weiter.setToolTipText("Alle Handregler lösen; die Größen gleiten mit ihrer Trägheit zurück zur Regie");
        weiter.addActionListener(e -> { r.wiring.resumeRegie(); r.hudMeldung("Regie weiter"); view.requestFocusInWindow(); });
        regie.add(weiter);
        put(regie);
        cloudsBox.setSelected(r.showClouds);
        cloudsBox.addActionListener(e -> { r.showClouds = cloudsBox.isSelected(); view.requestFocusInWindow(); });
        put(cloudsBox);
        shadowBox.setSelected(r.schatten.on);
        shadowBox.addActionListener(e -> { r.schatten.on = shadowBox.isSelected(); r.schatten.invalidate(); view.requestFocusInWindow(); });
        put(shadowBox);
        messBox.setSelected(r.bildMessung);
        messBox.addActionListener(e -> { r.bildMessung = messBox.isSelected(); view.requestFocusInWindow(); });
        put(messBox);
        put(windLbl);
        put(himmelLbl);
        put(small("<html><body style='width:215px'>Die Werte der Regie gelten am Gipfel (Wetterwarte, 1 134 m); Temperatur, Wolke und Sicht in "
                + "jeder anderen Höhe rechnet sie aus der Schichtung. Das Klima trifft die Monatsmittel der Station (DWD 1991–2020). "
                + "Den Nebel am Berg bringt Phase 6, bis dahin zeigen die Wolken Nebelmeer und Kappe.</body></html>"));

        // ============================================================== folgen
        // ============================================================== WIND (Phase 4)
        block("WIND", true);
        put(feldLbl);
        arrowsBox.setSelected(r.showArrows);
        arrowsBox.addActionListener(e -> { r.showArrows = arrowsBox.isSelected(); view.requestFocusInWindow(); });
        put(arrowsBox);
        put(label("Höhe der Pfeile über Grund"));
        FComboBox hBox = new FComboBox(new Object[]{"10 m", "50 m", "200 m", "600 m"});
        hBox.addActionListener(e -> { r.arrowHeight = new double[]{10, 50, 200, 600}[Math.max(0, hBox.getSelectedIndex())]; view.requestFocusInWindow(); });
        put(hBox);
        faedenBox.setSelected(r.showFaeden);
        faedenBox.addActionListener(e -> { r.showFaeden = faedenBox.isSelected(); view.requestFocusInWindow(); });
        put(faedenBox);
        JPanel neuP = grid(2);
        FButton neuB = new FButton("Feld neu rechnen");
        neuB.setToolTipText("Das Windfeld im Faden „Windfeld“ neu rechnen (sonst nur, wenn sich Richtung, Schichtung oder Erwärmung ändern); neue Böen");
        neuB.addActionListener(e -> { r.wiring.wind.seed++; r.hudMeldung("Windfeld rechnet neu"); view.requestFocusInWindow(); });
        neuP.add(neuB);
        FButton wegB = new FButton("Eigene Fäden weg");
        wegB.setToolTipText("Die mit Strg + Klick gesetzten Rauchfäden entfernen");
        wegB.addActionListener(e -> { r.eigeneFaedenWeg(); view.requestFocusInWindow(); });
        neuP.add(wegB);
        put(neuP);
        view.setCtrlClickListener((x, y, w, h) -> {
            String m = r.fadenSetzen(x, y, w, h);
            if (m != null) r.hudMeldung(m);
            if (m != null && m.startsWith("Rauchfaden") && !r.showFaeden) {   // ein gesetzter Faden soll zu sehen sein
                r.showFaeden = true;
                javax.swing.SwingUtilities.invokeLater(() -> faedenBox.setSelected(true));
            }
        });
        // Phase 5: Gras, Rauch und Kolkraben im Feld
        put(label("Im Wind"));
        grasBox.setSelected(r.leben.showGras);
        grasBox.addActionListener(e -> { r.leben.showGras = grasBox.isSelected(); view.requestFocusInWindow(); });
        put(grasBox);
        rauchBox.setSelected(r.leben.showRauch);
        rauchBox.addActionListener(e -> { r.leben.showRauch = rauchBox.isSelected(); view.requestFocusInWindow(); });
        put(rauchBox);
        rabenBox.setSelected(r.leben.showRaben);
        rabenBox.addActionListener(e -> { r.leben.showRaben = rabenBox.isSelected(); view.requestFocusInWindow(); });
        put(rabenBox);
        put(lebenLbl);
        put(small("<html><body style='width:215px'>Massenkonsistent nach Sherman (1978) wie WindNinja: das Startfeld aus Profil, Rauigkeit (ALKIS), "
                + "Lee hinter Kanten, Hangwind und Kaltluft wird so angepasst, dass keine Luft entsteht oder verschwindet. Die Stärke ist so geeicht, "
                + "dass an der Wetterwarte in 27 m (Höhe des Windgebers) der Wind der Regie weht; die Regie trifft die Stundenwerte der Station 1991–2020 (Mittel 10,8 m/s, im Januar 14, im August 8,6 m/s). Strg + Klick in die Szene setzt einen eigenen Rauchfaden (seit Phase 17; Umschalt + Klick setzt einen Merkpunkt). Pfeile: blau still, weiß mäßig, gelb stark, rot Sturm. Gras, Rauch und Kolkraben nehmen "
                + "das Feld, ab Phase 6 der Nebel.</body></html>"));
        // ============================================================== WALD UND FELS (Phase 8 und 9)
        block("WALD UND FELS", true);
        waldBox.setSelected(r.wald.showWald);
        waldBox.addActionListener(e -> { r.wald.showWald = waldBox.isSelected(); view.requestFocusInWindow(); });
        put(waldBox);
        felsBox.setSelected(r.fels.showFels);
        felsBox.addActionListener(e -> { r.fels.showFels = felsBox.isSelected(); view.requestFocusInWindow(); });
        put(felsBox);
        bodenBox.setSelected(r.showBoden);
        bodenBox.addActionListener(e -> { r.showBoden = bodenBox.isSelected(); r.leben.showBoden = bodenBox.isSelected(); view.requestFocusInWindow(); });
        put(bodenBox);
        put(small("<html><body style='width:215px'>Der Bergfichtenwald nach dem Borkenkäfer: rund 90 % der Fichten sind seit 2018 abgestorben "
                + "(Nationalpark Harz). Grau stehend, gebrochen, liegend nach Nordost (Stürme aus Südwest), dazwischen junge Fichten, oben "
                + "Ebereschen, unten Birken; an der Baumgrenze (rund 1 100 m) Windflüchter. Die Karte ist geschätzt, ohne Luftbild. Ansicht 9: "
                + "Totholz.</body></html>"));
        // ============================================================== RAUEIS (Phase 10)
        block("RAUEIS", true);
        put(raueisZeile);
        raueisBox.setSelected(r.showRaueis);
        raueisBox.addActionListener(e -> { r.showRaueis = raueisBox.isSelected(); view.requestFocusInWindow(); });
        put(raueisBox);
        raueisRegieBox.setSelected(Double.isNaN(r.wiring.raueis.hand));
        raueisRegieBox.addActionListener(e -> {
            r.wiring.raueis.hand = raueisRegieBox.isSelected() ? Double.NaN : raueisSl.getValue() / 100.0;
            view.requestFocusInWindow();
        });
        put(raueisRegieBox);
        put(raueisLbl);
        raueisSl.addChangeListener(e -> {
            if (fromScene) return;
            raueisRegieBox.setSelected(false);
            r.wiring.raueis.hand = raueisSl.getValue() / 100.0;
        });
        put(raueisSl);
        FButton raueisB = new FButton("Raueislage");
        raueisB.setToolTipText("Regiebuch „Raueislage“: ab 22 Uhr am Vorabend Nordwest, Frost und Wolke am Berg; um 15 Uhr klart es auf");
        raueisB.addActionListener(e -> {
            raueisRegieBox.setSelected(true);
            r.wiring.raueis.hand = Double.NaN;
            if (clock.local().getMonthValue() > 3 && clock.local().getMonthValue() < 11) clock.set(java.time.LocalDate.of(clock.local().getYear() + 1, 1, 14), clock.hour());
            r.wetter("Raueislage", 17);
            r.hudMeldung("Regiebuch „Raueislage“, 17 Stunden nach Beginn: die Fahnen in der Sonne");
            view.requestFocusInWindow();
        });
        put(raueisB);
        put(small("<html><body style='width:215px'>Eis aus unterkühlten Wolkentröpfchen nach Makkonen (2000), in Bändern zu 50 m: Zweige, Masten, "
                + "Fels. Die Fahnen wachsen gegen den Wind, oben mehr als unten; im Sonnenschein und über 0 °C taut es, Fahnen fallen. Von Hand: "
                + "Dicke an den Zweigen am Gipfel.</body></html>"));
        // ============================================================== BAHN (Phase 12)
        block("BAHN", true);
        put(bahnZeile);
        gleisBox.setSelected(r.showGleis);
        gleisBox.addActionListener(e -> { r.showGleis = gleisBox.isSelected(); view.requestFocusInWindow(); });
        put(gleisBox);
        betonBox.addActionListener(e -> { r.gleisBeton(betonBox.isSelected()); view.requestFocusInWindow(); });
        put(betonBox);
        if (r.strecke != null) {
            JPanel bp = grid(3);
            com.dan.rail.Track gl = r.strecke.gleis;
            java.util.List<Object[]> ziele = new java.util.ArrayList<>();
            for (com.dan.rail.Track.Station st : gl.stations) ziele.add(new Object[]{"Bf " + st.name(), st.s(), "Am Bahnhof " + st.name() + ", auf dem Bahnsteig"});
            if (!r.strecke.uebergaenge.isEmpty()) {
                double[] ue = r.strecke.uebergaenge.get(r.strecke.uebergaenge.size() - 1);
                ziele.add(new Object[]{"Übergang", (ue[0] + ue[1]) / 2, "Der Bahnübergang an der Brockenstraße"});
            }
            for (Object[] z : ziele) {
                FButton b = new FButton((String) z[0]);
                b.setToolTipText((String) z[2]);
                double sz = (Double) z[1];
                b.addActionListener(e -> {
                    com.dan.rail.Track.Pose p = gl.at(Math.max(0, sz - 40));
                    double hd = Math.toRadians(p.heading());
                    // 40 m davor, 3,5 m rechts der Achse, Blick entlang des Gleises
                    double x = p.x() + Math.cos(hd) * 3.5, zz = p.z() + Math.sin(hd) * 3.5;
                    r.camera.place(x, r.groundHeight(x, zz) + 1.7, zz, p.heading(), 4);
                    r.hudMeldung((String) z[2]);
                    view.requestFocusInWindow();
                });
                bp.add(b);
            }
            put(bp);
        }
        // Phase 13: Züge, Mitfahren
        put(zugZeile);
        zugBox.setSelected(r.showZuege);
        zugBox.addActionListener(e -> { r.showZuege = zugBox.isSelected(); view.requestFocusInWindow(); });
        put(zugBox);
        if (r.zugbetrieb != null && r.zugbetrieb.ok()) {
            JPanel zp = grid(2);
            FButton mit = new FButton("Mitfahren");
            mit.setToolTipText("Auf der Lok des nächsten Zugs, aus dem Führerhaus gelehnt (Ziehen schaut umher, eine Taste zum Gehen beendet)");
            mit.addActionListener(e -> {
                r.hudMeldung(r.mitfahren() ? "Mitfahrt auf der Lok · Ziehen schaut umher" : "Gerade fährt kein Zug (Fahrplan 10:06 bis 19:05)");
                view.requestFocusInWindow();
            });
            FButton bf = new FButton("10:00 Schierke");
            bf.setToolTipText("Uhr auf 10:00, am Bahnhof Schierke: die erste Bergfahrt fährt um 10:06");
            bf.addActionListener(e -> {
                clock.set(clock.date(), 10.0);
                com.dan.rail.Track gl = r.strecke.gleis;
                com.dan.rail.Track.Pose p = gl.at(r.strecke.sSchierke + 120);
                double hd = Math.toRadians(p.heading()), x = p.x() + Math.cos(hd) * 6, zz = p.z() + Math.sin(hd) * 6;
                r.camera.place(x, r.groundHeight(x, zz) + 1.7, zz, (p.heading() + 200) % 360, 4);
                r.hudMeldung("10:00 am Bahnhof Schierke · die Bergfahrt 10:06 steht bereit");
                syncFromScene();
                view.requestFocusInWindow();
            });
            zp.add(mit);
            zp.add(bf);
            put(zp);
        }
        put(small("<html><body style='width:215px'>Die Brockenbahn aus com.dan.rail. Die Achse ist die Mittellinie der ALKIS-Flächen „Bahnverkehr“ "
                + "(OpenStreetMap war nicht erreichbar). Die Gradiente steigt höchstens 33 ‰, in den Bahnhöfen ist sie eben. Damm und Einschnitt sind ins Gelände gelegt; "
                + "wo das Höhenmodell neben dem Gleis zu hoch liegt (Wald), gleicht es flach an. Schotter, Schienen, Schwellen, in Bögen überhöht; "
                + "Bahnübergänge, Bahnsteige. Seit Phase 13 fahren die Züge nach dem Fahrplan Sommer 2026: Lok der Baureihe 99.23–24 mit fünf Wagen, "
                + "Zugkraft aus Leistung und Reibung, Widerstände aus Steigung und Bogen; eingleisig mit Kreuzungen in Schierke und Goetheweg. "
                + "Der Dampf zieht mit dem Wind relativ zur Lok.</body></html>"));
        // ============================================================== GIPFEL (Phase 14)
        block("GIPFEL", true);
        put(gipfelZeile);
        gipfelBox.setSelected(r.showGipfel);
        gipfelBox.addActionListener(e -> { r.showGipfel = gipfelBox.isSelected(); view.requestFocusInWindow(); });
        put(gipfelBox);
        menschenBox.setSelected(r.showMenschen);
        menschenBox.addActionListener(e -> { r.showMenschen = menschenBox.isSelected(); view.requestFocusInWindow(); });
        put(menschenBox);
        JPanel gp = grid(3);
        for (String[] a : new String[][]{{"Bahnsteig", "bahnsteig", "Auf dem Bahnsteig, zum Gipfel"}, {"Gipfelstein", "gipfelstein", "Über die Brockenuhr zum Hotel und zum Sendeturm"},
                {"Von oben", "gipfel", "Über der Kuppe, 90 m hoch"}}) {
            FButton b = new FButton(a[0]);
            b.setToolTipText(a[2]);
            b.addActionListener(e -> { if (r.kameraGipfel(a[1])) r.hudMeldung(a[2]); view.requestFocusInWindow(); });
            gp.add(b);
        }
        put(gp);
        put(small("<html><body style='width:215px'>Die Bauten nach den ALKIS-Grundrissen: Brockenhotel mit dem alten Fernsehturm (Radom), "
                + "Brockenhaus mit Kuppel, Wetterwarte, Bahnhof, Wolkenhäuschen; der Sendeturm (168 m), Gipfelstein und Brockenuhr, der Zaun um den Brockengarten. "
                + "Höhen, Dächer und Farben geschätzt. Wanderer auf der Brockenstraße und dem Rundweg, Fahrgäste zwischen Bahnsteig und Gipfelstein, "
                + "Besucher am Stein – nach Monat, Stunde und Wetter (com.dan.figure).</body></html>"));
        // ============================================================== KLANG (Phase 15)
        block("KLANG", true);
        put(klangZeile);
        // der Ton läuft von Beginn an (-Dton=aus: stumm)
        if (!"aus".equals(System.getProperty("ton"))) r.klang.lautsprecher(true);
        tonBox.setSelected(r.klang.an);
        tonBox.addActionListener(e -> {
            r.klang.lautsprecher(tonBox.isSelected());
            String f = r.klang.fehler();
            if (!f.isEmpty()) r.hudMeldung("Ton: " + f);
            view.requestFocusInWindow();
        });
        put(tonBox);
        put(small("Lautstärke"));
        lautSl.addChangeListener(e -> r.klang.lautstaerke = lautSl.getValue() / 100.0);
        put(lautSl);
        put(windDbLbl);
        windDbSl.addChangeListener(e -> { r.klang.windDb = windDbSl.getValue(); windDbLbl.setText(String.format(Locale.GERMAN, "Wind am Ohr %+d dB", windDbSl.getValue())); });
        windDbLbl.setText(String.format(Locale.GERMAN, "Wind am Ohr %+d dB", windDbSl.getValue()));
        put(windDbSl);
        walpurgisBox.setSelected(r.klang.walpurgisHand);
        walpurgisBox.addActionListener(e -> { r.klang.walpurgisHand = walpurgisBox.isSelected(); view.requestFocusInWindow(); });
        put(walpurgisBox);
        walpurgisKalBox.setSelected(r.klang.walpurgisKalender);
        walpurgisKalBox.addActionListener(e -> { r.klang.walpurgisKalender = walpurgisKalBox.isSelected(); view.requestFocusInWindow(); });
        put(walpurgisKalBox);
        put(small("<html><body style='width:215px'>Der Klang am Ohr der Kamera (com.dan.soundscape): Wind aus dem Windfeld, am Gipfel laut, im Wald leise; "
                + "er singt an Mast, Antennen und Geländer und heult an Lamellen, Fugen und Hausecken. Die Loks mit Auspuff im Takt der Räder, "
                + "Schienenstößen, Bremsen, Pfiffen vor der Abfahrt und den Übergängen; im Stand Luftpumpe und Sicherheitsventil. Fallendes Raueis, "
                + "Vögel nach Jahres- und Tageszeit, Ort und Wetter (Ringdrossel, Wiesenpieper, Hausrotschwanz, Buchfink, Goldhähnchen, Kreuzschnabel, "
                + "Raufußkauz, Kolkrabe), Stimmen am Gipfelstein und am Bahnsteig, Donner im Gewitter. In der Walpurgisnacht die Stimme der Sage: "
                + "der Wind ruft, fern Trommeln, Gelächter und Kreischen in der Luft. Alles erzeugt, keine Aufnahmen.</body></html>"));
        // ============================================================== JAHR (Phase 11)
        block("JAHR", true);
        put(jahrZeile);
        schneeBox.setSelected(r.showSchnee);
        schneeBox.addActionListener(e -> { r.showSchnee = schneeBox.isSelected(); view.requestFocusInWindow(); });
        put(schneeBox);
        schneeRegieBox.setSelected(Double.isNaN(r.wiring.schneeHand));
        schneeRegieBox.addActionListener(e -> {
            r.wiring.schneeHand = schneeRegieBox.isSelected() ? Double.NaN : schneeSl.getValue() / 100.0;
            view.requestFocusInWindow();
        });
        put(schneeRegieBox);
        put(schneeLbl);
        schneeSl.addChangeListener(e -> {
            if (fromScene) return;
            schneeRegieBox.setSelected(false);
            r.wiring.schneeHand = schneeSl.getValue() / 100.0;
        });
        put(schneeSl);
        JPanel jp = grid(3);
        String[][] tage = {{"Erster Schnee", "2026-11-20", "20. November 2026: oben 20 cm, die Schneegrenze bei 700 m"},
                {"Hochwinter", "2027-02-10", "10. Februar 2027: 1,8 m am Gipfel, Verwehungen, Wechten"},
                {"Schmelze", "2027-04-25", "25. April 2027: unten aper, am Gipfel noch 50 cm, in Mulden mehr"}};
        for (String[] tg : tage) {
            FButton b = new FButton(tg[0]);
            b.setToolTipText(tg[2]);
            b.addActionListener(e -> {
                schneeRegieBox.setSelected(true);
                r.wiring.schneeHand = Double.NaN;
                clock.set(java.time.LocalDate.parse(tg[1]), 12);
                r.wetter("KLAR", 0);
                r.jahrJetzt();
                r.hudMeldung(tg[0] + ": " + tg[2]);
                view.requestFocusInWindow();
            });
            jp.add(b);
        }
        put(jp);
        put(small("<html><body style='width:215px'>Das Jahr aus com.dan.season in 16 Stufen von 400 bis 1 150 m, gewürfelt nach dem Klima der "
                + "Wetterwarte (DWD 1991–2020), mit dem Wetter der Regie weitergerechnet. Der Wind aus West bis Südwest nimmt den Schnee von "
                + "Kuppe und Luvhängen und legt ihn in Mulden und Leehänge; an Kanten Wechten. Heide und Wollgras blühen unten früher.</body></html>"));
        // ============================================================== NEBEL (Phase 6)
        block("NEBEL", true);
        put(nebelLbl);
        nebelBox.setSelected(r.showNebel);
        nebelBox.addActionListener(e -> { r.showNebel = nebelBox.isSelected(); view.requestFocusInWindow(); });
        put(nebelBox);
        talRegieBox.setSelected(r.wiring.valleyHand < 0);
        talRegieBox.addActionListener(e -> {
            r.wiring.valleyHand = talRegieBox.isSelected() ? -1 : talSl.getValue() / 100.0;
            view.requestFocusInWindow();
        });
        put(talRegieBox);
        put(talLbl);
        talSl.addChangeListener(e -> {
            if (fromScene) return;
            talRegieBox.setSelected(false);
            r.wiring.valleyHand = talSl.getValue() / 100.0;
        });
        put(talSl);
        JPanel nbP = grid(2);
        FButton meerB = new FButton("Über dem Nebelmeer");
        meerB.setToolTipText("Regiebuch „Über dem Nebelmeer“: ab 6:30 Uhr, das Meer löst sich bis Mittag von unten auf");
        meerB.addActionListener(e -> { r.wetter("Über dem Nebelmeer", 0); r.hudMeldung("Regiebuch „Über dem Nebelmeer“"); view.requestFocusInWindow(); });
        nbP.add(meerB);
        FButton stauB = new FButton("Westlage mit Stau");
        stauB.setToolTipText("Regiebuch „Westlage mit Stau“: die Kappe legt sich auf den Berg, am Westhang hängt der Nebel tiefer");
        stauB.addActionListener(e -> { r.wetter("Westlage mit Stau", 0); r.hudMeldung("Regiebuch „Westlage mit Stau“"); view.requestFocusInWindow(); });
        nbP.add(stauB);
        put(nbP);
        put(small("<html><body style='width:215px'>Nebelmeer bei Inversion und Talnebel aus der Regie (Grenzen der unteren Schicht); am Tag löst "
                + "es sich von unten auf, nachts schließt es sich wieder. Talnebel füllt die Kaltluftseen des Windfelds in ruhigen, feuchten Nächten. "
                + "Liegt eine Decke auf dem Berg, hängt der Nebel am Luvhang tiefer (Aufwind aus dem Feld).</body></html>"));
        // Phase 7: Licht im Nebel
        sub("Licht im Nebel");
        put(lichtLbl);
        glorieBox.setSelected(r.showGlorie);
        glorieBox.addActionListener(e -> { r.showGlorie = glorieBox.isSelected(); view.requestFocusInWindow(); });
        put(glorieBox);
        tropfenRegieBox.setSelected(r.wiring.tropfenHand <= 0);
        tropfenRegieBox.addActionListener(e -> {
            r.wiring.tropfenHand = tropfenRegieBox.isSelected() ? -1 : tropfenSl.getValue();
            view.requestFocusInWindow();
        });
        put(tropfenRegieBox);
        put(tropfenLbl);
        tropfenSl.addChangeListener(e -> {
            if (fromScene || tropfenSl.getValueIsAdjusting()) return;
            tropfenRegieBox.setSelected(false);
            r.wiring.tropfenHand = tropfenSl.getValue();
        });
        put(tropfenSl);
        haloRegieBox.setSelected(r.wiring.haloHand < 0);
        haloRegieBox.addActionListener(e -> {
            r.wiring.haloHand = haloRegieBox.isSelected() ? -1 : haloSl.getValue() / 100.0;
            view.requestFocusInWindow();
        });
        put(haloRegieBox);
        put(haloLbl);
        haloSl.addChangeListener(e -> {
            if (fromScene) return;
            haloRegieBox.setSelected(false);
            r.wiring.haloHand = haloSl.getValue() / 100.0;
        });
        put(haloSl);
        JPanel liP = grid(2);
        FButton gegenB = new FButton("In den Gegenpunkt"), sonneB = new FButton("Zur Sonne"), meerAugeB = new FButton("Meer ans Auge"), meerRegieB = new FButton("Meer nach Regie");
        gegenB.setToolTipText("Das Auge schaut in den Gegenpunkt der Sonne: dort stehen Glorie, Gespenst und (38° darum) der Nebelbogen");
        gegenB.addActionListener(e -> { r.blickZurSonne(true); view.requestFocusInWindow(); });
        sonneB.setToolTipText("Das Auge schaut zur Sonne: 22°-Ring und Nebensonnen, wenn Eiskristalle in der Luft sind");
        sonneB.addActionListener(e -> { r.blickZurSonne(false); view.requestFocusInWindow(); });
        meerAugeB.setToolTipText("Hebt das Nebelmeer (bei Inversion), bis seine Obergrenze 3 m über dem Auge liegt: wer in der obersten Schicht steht, sieht bei tiefer Sonne sein Gespenst");
        meerAugeB.addActionListener(e -> {
            double top = r.meerAmAuge(Math.max(300, r.camera.eye[1] - 400), 3);
            r.hudMeldung(r.nebel.snapshot().sea() ? String.format(Locale.GERMAN, "Nebelmeer von Hand: Obergrenze %.0f m", top) : "Kein Nebelmeer in dieser Lage (Inversion nötig)");
            view.requestFocusInWindow();
        });
        meerRegieB.addActionListener(e -> { r.wiring.meerBasisHand = r.wiring.meerTopHand = Double.NaN; r.nebelJetzt(); view.requestFocusInWindow(); });
        liP.add(gegenB); liP.add(sonneB); liP.add(meerAugeB); liP.add(meerRegieB);
        put(liP);
        put(small("<html><body style='width:215px'>Der Nebel streut nach Mie mit den Tröpfchen der Lage: farbige Ringe der Glorie um den "
                + "Gegenpunkt der Sonne (erster roter Ring ≈ 24° / r), der fast weiße Nebelbogen rund 38° darum. Steht das Auge (bis 2,5 m über Grund), "
                + "wirft es seinen Schatten in den Nebel: das Brockengespenst, nur mit der Sonne im Rücken und mit Nebel in den ersten rund 50 m. "
                + "Dazu der Schatten der Kuppe auf dem Nebelmeer und Halos an Eiskristallen (22°-Ring, Nebensonnen).</body></html>"));
        // ============================================================== REGIEBÜCHER (Phase 16)
        block("REGIEBÜCHER", true);
        put(regieZeile);
        java.util.List<com.dan.brocken.scene.Regie.Book> buecherR;
        try { buecherR = com.dan.brocken.scene.Regie.all(); } catch (RuntimeException ex) { buecherR = java.util.List.of(); put(small("Regiebücher nicht lesbar: " + ex.getMessage())); }
        final java.util.List<com.dan.brocken.scene.Regie.Book> rb = buecherR;
        FComboBox regieBox = new FComboBox(rb.stream().map(com.dan.brocken.scene.Regie.Book::name).toArray());
        regieBox.setToolTipText("Eines der vier Regiebücher (Text in scene/regie)");
        put(regieBox);
        JPanel rp = grid(3);
        FButton kinoB = new FButton("Im Kino"), abspielB = new FButton("Abspielen"), haltB = new FButton("Anhalten");
        kinoB.setToolTipText("Vollbild mit Balken, Vor- und Abspann, Untertitel; Esc verlässt das Kino, die Leertaste hält an");
        abspielB.setToolTipText("Das Regiebuch im Fenster, mit HUD-Zeile „Regie“");
        haltB.setToolTipText("Das Regiebuch anhalten; die Szene bleibt, wo sie ist");
        kinoB.addActionListener(e -> {
            int i = regieBox.getSelectedIndex();
            if (i < 0 || i >= rb.size()) return;
            r.kinoStart(rb.get(i));
            view.requestFocusInWindow();
        });
        abspielB.addActionListener(e -> {
            int i = regieBox.getSelectedIndex();
            if (i < 0 || i >= rb.size()) return;
            r.regieStart(rb.get(i));
            r.hudMeldung("Regiebuch „" + rb.get(i).name() + "“");
            view.requestFocusInWindow();
        });
        haltB.addActionListener(e -> { r.regieStop(); r.camera.stopFahrt(); r.camera.stopMitfahrt(); syncFromScene(); view.requestFocusInWindow(); });
        rp.add(kinoB); rp.add(abspielB); rp.add(haltB);
        put(rp);
        put(small("<html><body style='width:215px'>Vier Regiebücher als Text (scene/regie/*.txt): „Mit dem Zug auf den Brocken“ (12. Juni, die Bergfahrt 10:06 "
                + "durch den Wald, die Spirale, Ankunft im Nebel), „Über dem Nebelmeer“ (21. Oktober, Sonnenaufgang, das Gespenst, die Auflösung zum Mittag), "
                + "„Ein Tag im Nebel“ (15. Januar, die Raueislage im Zeitraffer, mit dem Wind treiben) und als Kür „Walpurgisnacht“ (30. April, ab 22 Uhr die "
                + "Stimme der Sage). Jedes macht Fotos und am Ende ein Blatt in docs/status. Im Kino ohne Bedienfeld und HUD, Balken 2,39 : 1, "
                + "Untertitel aus den Stichworten; Esc verlässt das Kino, das Regiebuch läuft weiter.</body></html>"));
        // ============================================================== DATEN (Phase 17, F37 A)
        block("DATEN", false);
        daten = new DatenBlock(view, r);
        put(daten);

        // ============================================================== TECHNIK
        block("TECHNIK", false);
        put(perfLbl);
        put(label("Qualität"));
        qualBox = new FComboBox(Quality.values());
        qualBox.setSelectedItem(r.quality());
        qualBox.addActionListener(e -> { r.setQuality((Quality) qualBox.getSelectedItem()); view.requestFocusInWindow(); });
        put(qualBox);
        put(qualLbl);
        autoBox.setSelected(r.autoScale);
        autoBox.addActionListener(e -> { r.autoScale = autoBox.isSelected(); if (!r.autoScale) r.renderScale = scaleSl.getValue() / 100.0; view.requestFocusInWindow(); });
        put(autoBox);
        put(scaleLbl);
        put(scaleSl);
        scaleSl.addChangeListener(e -> {
            if (fromScene) return;
            r.autoScale = false;
            autoBox.setSelected(false);
            r.renderScale = scaleSl.getValue() / 100.0;
        });
        put(label("Belichtung (Blenden von Hand)"));
        put(expSl);
        expSl.addChangeListener(e -> { if (!fromScene) r.exposureBias = expSl.getValue() / 10.0; });
        put(label("HUD"));
        hudBox = new FComboBox(new Object[]{"kurz", "voll", "aus"});
        hudBox.addActionListener(e -> {
            int i = hudBox.getSelectedIndex();
            r.showHud = i != 2;
            r.hudFull = i == 1;
            view.requestFocusInWindow();
        });
        put(hudBox);
        marks.setSelected(r.showMarks);
        marks.addActionListener(e -> { r.showMarks = marks.isSelected(); view.requestFocusInWindow(); });
        put(marks);
        JPanel pic = grid(2);
        FButton photo = new FButton("Foto (F12)"), shaders = new FButton("Shader neu (F5)");
        photo.addActionListener(e -> { r.photoRequest = true; view.requestFocusInWindow(); });
        shaders.addActionListener(e -> { r.requestShaderReload(); view.requestFocusInWindow(); });
        pic.add(photo); pic.add(shaders);
        put(pic);
        // Phase 18: der Dauerlauf (Abnahme „fertig“)
        JPanel dlRow = grid(2);
        FButton dlGo = new FButton("Dauerlauf 30 min"), dlStop = new FButton("Abbrechen");
        dlGo.setToolTipText("<html>Runden aus zwei Regiebüchern im Kino und einem Tag frei im Zeitraffer 360, mindestens zwei<br>"
                + "(dann lief jedes Regiebuch einmal), sonst bis 30 Minuten um sind; am Ende Bericht und Kurven in docs/status</html>");
        dlGo.addActionListener(e -> {
            boolean ok = com.dan.brocken.app.Dauerlauf.starten(r, 30, f -> r.hudMeldung("Dauerlauf fertig: " + (f != null ? f.getName() : "ohne Bericht")));
            r.hudMeldung(ok ? "Dauerlauf beginnt (mindestens zwei Runden, 30 min)" : "Ein Dauerlauf läuft schon");
            view.requestFocusInWindow();
        });
        dlStop.addActionListener(e -> com.dan.brocken.app.Dauerlauf.anhalten());
        dlRow.add(dlGo);
        dlRow.add(dlStop);
        put(dlRow);
        put(small("<html><body style='width:215px'>Fotos gehen nach docs/status. Marken: weiß der Rand des Hauptrasters, gold der Kern "
                + "(dort ab Phase 2 das DGM1 bis 2 m), blau das 1-km-Raster und die Höhenlinien alle 50 m; die Orte weiß (Gipfel), rot "
                + "(Bahnhöfe), grün (Wege), hell (Orte), violett (Berge).</body></html>"));
        sub("Gelände");
        put(gelLbl);
        sub("Grafikkarte");
        put(computeLbl);
        put(glLbl);
        put(physLbl);
        sub("Packages");
        put(pkgLbl);
        sub("Alle Zeilen des HUD");
        put(hudLbl);
        sub("Protokoll (Phase 17)");
        put(daten.protokoll);

        reihenfolge();
        for (Object[] b : bloecke) ((FCollapsiblePanel) b[0]).setBodyComponent((JComponent) b[1]);
        for (FCollapsiblePanel p : zu) p.setExpanded(false, false);
        add(Box.createVerticalGlue());
        view.setKeyAction(java.awt.event.KeyEvent.VK_F1, () -> javax.swing.SwingUtilities.invokeLater(this::tasten));
        view.setKeyAction(java.awt.event.KeyEvent.VK_SPACE, () -> javax.swing.SwingUtilities.invokeLater(() ->
                lapseBox.setSelectedIndex(clock.lapse == 0 ? 1 : 0)));
        syncFromScene();
        new Timer(500, e -> {
            // Befund aus Glacier: solange eine Auswahlliste offen ist, nichts auffrischen; neue Texte ändern die Höhe
            // der Zeilen, der Block legt sich neu aus, und Swing schließt die Liste, sobald sich ihr Feld bewegt
            if (popupOpen(this) || menuOpen()) return;
            syncFromScene();
            if (ticks++ % 2 == 0) technik();
            nachmessen();
        }).start();
    }

    private void reihenfolge() {
        java.util.Map<String, FCollapsiblePanel> m = new java.util.LinkedHashMap<>();
        for (Object[] b : bloecke) m.putIfAbsent(((FCollapsiblePanel) b[0]).getTitle(), (FCollapsiblePanel) b[0]);   // seit Phase 15: der echte Block vor dem Platzhalter gleichen Namens
        removeAll();
        boolean erst = true;
        for (String t : REIHENFOLGE) {
            FCollapsiblePanel p = m.remove(t);
            if (p == null) continue;
            if (!erst) add(Box.createVerticalStrut(6));
            add(p);
            erst = false;
        }
        for (FCollapsiblePanel p : m.values()) { add(Box.createVerticalStrut(6)); add(p); }
    }

    /** Für Prüfungen: die Titel der Blöcke, wie sie im Bedienfeld stehen. */
    public java.util.List<String> bloecke() {
        java.util.List<String> l = new java.util.ArrayList<>();
        for (Component c : getComponents()) if (c instanceof FCollapsiblePanel p) l.add(p.getTitle());
        return l;
    }

    private final java.util.Map<FCollapsiblePanel, Integer> hoehe = new java.util.HashMap<>();

    /**
     * Wie in Geiranger: FCollapsiblePanel merkt sich die Höhe des Inhalts beim Aufklappen. Wachsen danach Zeilen,
     * schneidet der Block unten ab. Darum alle halbe Sekunde nachmessen und offene Blöcke neu setzen.
     */
    private void nachmessen() {
        boolean changed = false;
        for (Object[] b : bloecke) {
            FCollapsiblePanel p = (FCollapsiblePanel) b[0];
            if (!p.isExpanded()) { hoehe.remove(p); continue; }
            JComponent body = (JComponent) b[1];
            body.invalidate();
            int h = body.getPreferredSize().height;
            Integer old = hoehe.put(p, h);
            if (old == null || old != h) { p.setExpanded(true, false); changed = true; }
        }
        if (changed) { revalidate(); repaint(); }
    }

    /** Ist irgendwo im Bedienfeld eine Auswahlliste aufgeklappt? */
    private static boolean popupOpen(java.awt.Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof javax.swing.JComboBox<?> b && b.isPopupVisible()) return true;
            if (k instanceof java.awt.Container cc && popupOpen(cc)) return true;
        }
        return false;
    }

    /** Ist irgendein Aufklappmenü offen (auch die Liste einer Auswahl)? Dann darf sich nichts neu auslegen. */
    public static boolean menuOpen() { return javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length > 0; }

    /** Text nur setzen, wenn er sich ändert (sonst legt Swing den Block bei jedem Takt neu aus). */
    private static void txt(JLabel l, String t) { if (!t.equals(l.getText())) l.setText(t); }

    private void ansicht(Ansichten.Ansicht a) {
        if (r.placeView(a)) {
            free.setSelected(r.camera.mode == Camera.Mode.FREI);
            orbit.setSelected(false);
        }
        view.requestFocusInWindow();
    }

    private void tasten() {
        StringBuilder an = new StringBuilder();
        for (Ansichten.Ansicht a : Ansichten.ALLE) an.append(an.length() > 0 ? ", " : "").append(a.key()).append(' ').append(a.name());
        com.dan.foptionpane.FOptionPane.showMessageDialog(this, "<html><body style='width:420px'>"
                + "<b>Szene</b><br>Links ziehen: drehen · rechts ziehen: verschieben · Rad, Bild↑/↓, +/−: zoomen<br>"
                + "Pfeile: drehen · W A S D: gleiten · Q/E: ab und auf (frei) · Umschalt: schneller<br>"
                + "F: frei fliegen · R: von selbst kreisen · Pos1: Grundansicht · H: HUD kurz, voll, aus<br>"
                + "Leertaste: Uhr anhalten und weiter · F12: Foto nach docs/status · F5: Shader neu laden<br>"
                + "Strg + Klick: einen Rauchfaden in den Wind setzen (bis zu " + Renderer.EIGENE_FAEDEN + ")<br>Umschalt + Klick: einen Merkpunkt setzen (mit Notiz, Block DATEN)<br>V: die Ansicht merken (Block DATEN)<br>"
                + "Ansichten: " + an + "<br><br>"
                + "<b>Fenster</b><br>F1: diese Übersicht · F11: nur die Szene (Bedienfeld und Statuszeile weg und wieder da)</body></html>",
                "Brocken · Tasten", com.dan.foptionpane.FOptionPane.INFORMATION_MESSAGE);
    }

    /** Sicht in km für den Regler 0..100 (logarithmisch 1 … 200 km) und zurück. */
    static double visKm(int v) { return Math.exp(Math.log(1) + (Math.log(200) - Math.log(1)) * v / 100.0); }
    static int visPos(double km) { return (int) Math.round(100 * Math.log(Math.max(1, Math.min(200, km))) / Math.log(200)); }

    /** Die Stufe des Zeitraffers, die einem Faktor am nächsten liegt. */
    static int nearestLapse(double f) {
        int best = 1;
        for (int i = 1; i < LAPSE.length; i++) if (Math.abs(Math.log(LAPSE[i] / Math.max(1e-9, f))) < Math.abs(Math.log(LAPSE[best] / Math.max(1e-9, f)))) best = i;
        return best;
    }

    /** Regler nach der Szene stellen (alle 0,5 s). */
    private void syncFromScene() {
        fromScene = true;
        int m = (int) Math.round(clock.hour() * 60) % (24 * 60);
        if (!time.getValueIsAdjusting()) time.setValue(m);
        if (!scaleSl.getValueIsAdjusting()) scaleSl.setValue((int) Math.round(r.renderScale * 100));
        if (!expSl.getValueIsAdjusting()) expSl.setValue((int) Math.round(r.exposureBias * 10));
        if (!coverSl.getValueIsAdjusting()) coverSl.setValue((int) Math.round(r.wiring.oktas * 10));
        if (!visSl.getValueIsAdjusting()) visSl.setValue(visPos(r.wiring.visibilityKm));
        fromScene = false;
        labels();
    }

    private void labels() {
        txt(dateLbl, clock.local().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d. MMMM u", Locale.GERMANY)));
        txt(timeLbl, "Uhrzeit " + clock.timeLabel());
        double[] s = clock.sun();
        double day = clock.local().toLocalDate().atStartOfDay(SceneClock.ZONE).toInstant().toEpochMilli();
        double up = Sun.crossing(day, clock.lat, clock.lon, true), down = Sun.crossing(day, clock.lat, clock.lon, false);
        txt(sunLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>Sonne %.1f° hoch, aus %03.0f° (%s) · Aufgang %s, Untergang %s</body></html>",
                s[0], s[1], compass(s[1]), hm(up), hm(down)));
        txt(camLbl, "<html><body style='width:215px'>" + esc(r.cameraLine()) + "</body></html>");
        txt(scaleLbl, String.format(Locale.GERMAN, "Auflösung %.0f %% (%s)", r.renderScale * 100, r.autoScale ? "geregelt, " + r.scaleSteps() + "× geändert" : "fest"));
        txt(qualLbl, "<html><body style='width:215px'>" + r.quality().summary() + "</body></html>");
        com.dan.weather.Weather.Snapshot ws = r.wiring.snapshot();
        txt(wetterLbl, "<html><body style='width:215px'>" + esc(r.wiring.weatherLine(r.camera.eye[1])) + "</body></html>");
        txt(coverLbl, String.format(Locale.GERMAN, "Bedeckung %.1f/8 %s%s", ws.oktas(), r.wiring.cloudType.label, r.wiring.weather.overridden(com.dan.weather.Weather.Var.COVER) ? " (von Hand)" : ""));
        txt(visLbl, String.format(Locale.GERMAN, "Sicht %.0f km%s", r.wiring.visibilityKm, r.wiring.weather.overridden(com.dan.weather.Weather.Var.VISIBILITY) ? " (von Hand)" : ""));
        txt(windLbl, "<html><body style='width:215px'>" + esc(r.wiring.windLine()) + String.format(Locale.GERMAN, " · Sonne am Auge: Gelände %.0f %%, Wolken %.0f %%",
                r.sunAtEye() * 100, r.cloudShade() * 100) + "</body></html>");
        txt(himmelLbl, "<html><body style='width:215px'>" + esc(r.wetterZeilen()) + "</body></html>");
        txt(feldLbl, "<html><body style='width:215px'>" + esc(r.windZeile().replace("!", "")) + "</body></html>");
        txt(nebelLbl, "<html><body style='width:215px'>" + esc(r.nebelZeile()) + "</body></html>");
        txt(lichtLbl, "<html><body style='width:215px'>" + esc(r.lichtZeile()) + "</body></html>");
        txt(tropfenLbl, String.format(Locale.GERMAN, "Tröpfchen: %.0f µm%s", r.wiring.tropfen.radiusUm, r.wiring.tropfenHand > 0 ? " (von Hand)" : " (Lage)"));
        if (!tropfenSl.getValueIsAdjusting()) { boolean f = fromScene; fromScene = true; tropfenSl.setValue((int) Math.round(r.wiring.tropfen.radiusUm)); fromScene = f; }
        txt(haloLbl, String.format(Locale.GERMAN, "Halo: %s", r.wiring.haloHand < 0 ? "nach dem hohen Schleier" : String.format(Locale.GERMAN, "%.0f %% (von Hand)", r.wiring.haloHand * 100)));
        double tal = r.nebel.valleyFill;
        txt(talLbl, String.format(java.util.Locale.GERMAN, "Talnebel: %.0f %% der Kaltluft%s", tal * 100, r.wiring.valleyHand < 0 ? " (Regie)" : " (von Hand)"));
        if (!talSl.getValueIsAdjusting()) { boolean f = fromScene; fromScene = true; talSl.setValue((int) Math.round(tal * 100)); fromScene = f; }
        txt(raueisZeile, "<html><body style='width:215px'>" + esc(r.raueisZeile()) + "</body></html>");
        txt(jahrZeile, "<html><body style='width:215px'>" + esc(r.jahrZeile()) + "</body></html>");
        txt(bahnZeile, "<html><body style='width:215px'>" + esc(r.bahnZeile()) + "</body></html>");
        txt(zugZeile, "<html><body style='width:215px'>" + esc(r.zugZeile()) + "</body></html>");
        txt(gipfelZeile, "<html><body style='width:215px'>" + esc(r.gipfelZeile()) + "</body></html>");
        txt(klangZeile, "<html><body style='width:215px'>" + esc(r.klang.zeile()) + "</body></html>");
        txt(regieZeile, "<html><body style='width:215px'>" + esc(r.regieLine()) + "</body></html>");
        double sh = Double.isNaN(r.wiring.schneeHand) ? r.wiring.schnee(1141) : r.wiring.schneeHand;
        txt(schneeLbl, String.format(Locale.GERMAN, "Schnee am Gipfel: %.0f cm%s", sh * 100, Double.isNaN(r.wiring.schneeHand) ? " (Jahr)" : " (von Hand)"));
        if (!schneeSl.getValueIsAdjusting()) { boolean f = fromScene; fromScene = true; schneeSl.setValue((int) Math.round(Math.min(300, sh * 100))); fromScene = f; }
        double rh = Double.isNaN(r.wiring.raueis.hand) ? r.wiring.raueis.dicke(com.dan.brocken.wiring.Raueis.ZWEIG, 1141) : r.wiring.raueis.hand;
        txt(raueisLbl, String.format(Locale.GERMAN, "Zweige am Gipfel: %.0f cm%s", rh * 100, Double.isNaN(r.wiring.raueis.hand) ? " (Regie)" : " (von Hand)"));
        if (!raueisSl.getValueIsAdjusting()) { boolean f = fromScene; fromScene = true; raueisSl.setValue((int) Math.round(Math.min(30, rh * 100))); fromScene = f; }
        txt(lebenLbl, "<html><body style='width:215px'>" + esc(r.leben.zeile(r.camera.eye[0], r.camera.eye[2], r.camera.groundLevel)) + "</body></html>");
    }

    private void technik() {
        String dl = com.dan.brocken.app.Dauerlauf.zeile();
        txt(perfLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>%.0f Bilder/s · Grafikkarte %.2f ms · Prozessor %.2f ms<br>%s%s</body></html>",
                r.fps(), r.gpuMs(), r.cpuMs(), r.passTimes(), dl.isEmpty() ? "" : "<br><b>" + dl + "</b>"));
        ComputeProbe.Result c = r.compute();
        txt(computeLbl, "<html><body style='width:215px'>" + (c == null ? "Compute-Test läuft …" : c.line()) + "</body></html>");
        txt(glLbl, "<html><body style='width:215px'>" + r.glInfo() + (r.reversedDepth() ? " · umgekehrte Tiefe" : "") + "</body></html>");
        txt(physLbl, "<html><body style='width:215px'>" + r.physik.describe() + "</body></html>");
        java.util.Properties gp = r.terrain.props;
        txt(gelLbl, "<html><body style='width:215px'>" + esc(r.terrain.describe()) + (r.terrain.measured() ? "<br>Waldaufschlag " + gp.getProperty("abgleich.waldaufschlag", "–")
                + " m abgezogen (gemessen auf " + gp.getProperty("abgleich.zellen", "–") + " Zellen), Nähte " + gp.getProperty("naht.sprung", "–") + " m. Neu einlesen: ant gelaende." : "<br>Kuppe der Bühne: erst ant gelaende.") + "</body></html>");
        txt(pkgLbl, "<html><body style='width:215px'>" + esc(Bibliothek.kurz().replace("!", "")) + ": " + String.join(", ", Bibliothek.pakete())
                + ".<br>Neu für Brocken: " + esc(Bibliothek.neuStand()) + " (Hüllen seit Phase 1). Die Prüfung <i>check-packages</i> läuft nach jedem Übersetzen.</body></html>");
        StringBuilder h = new StringBuilder("<html><body style='width:215px'>");
        for (String l : r.hudLines()) h.append(esc(l.startsWith("!") ? l.substring(1) : l)).append("<br>");
        txt(hudLbl, h.append("</body></html>").toString());
    }

    private static String esc(String t) { return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    static String hm(double epochMs) {
        if (Double.isNaN(epochMs)) return "—";
        return String.format("%tR", Instant.ofEpochMilli((long) epochMs).atZone(SceneClock.ZONE));
    }

    static String compass(double deg) {
        String[] n = {"Nord", "Nordnordost", "Nordost", "Ostnordost", "Ost", "Ostsüdost", "Südost", "Südsüdost",
                "Süd", "Südsüdwest", "Südwest", "Westsüdwest", "West", "Westnordwest", "Nordwest", "Nordnordwest"};
        return n[(int) Math.round(((deg % 360) + 360) % 360 / 22.5) % 16];
    }

    // ------------------------------------------------------------ Aufbau

    /** Ein Klappblock (FCollapsiblePanel); offen bleibt, was zuletzt offen war. */
    private void block(String title, boolean offen) {
        FCollapsiblePanel p = new FCollapsiblePanel() {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setTitle(title);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel inhalt = new JPanel();
        inhalt.setOpaque(false);
        inhalt.setLayout(new BoxLayout(inhalt, BoxLayout.Y_AXIS));
        inhalt.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 8));
        bloecke.add(new Object[]{p, inhalt});
        String key = "offen." + title;
        boolean auf = PREFS == null ? offen : PREFS.getBoolean(key, offen);
        if (!auf) zu.add(p);
        p.addFCollapsiblePanelListener((panel, expanded) -> {
            if (PREFS != null) try { PREFS.putBoolean(key, expanded); } catch (RuntimeException ex) { /* nicht gemerkt */ }
        });
        if (getComponentCount() > 0) add(Box.createVerticalStrut(6));
        add(p);
        target = inhalt;
    }

    /** Eine Zwischenüberschrift im Block. */
    private void sub(String text) {
        JLabel h = new JLabel(text);
        h.setForeground(Theme.accent());
        h.setFont(new Font("SansSerif", Font.BOLD, 11));
        h.setBorder(BorderFactory.createEmptyBorder(10, 0, 3, 0));
        put(h);
    }

    private void put(Component c) { target.add(row(c)); }

    private static JPanel grid(int n) {
        JPanel p = new JPanel(new java.awt.GridLayout(1, n, 6, 0));
        p.setOpaque(false);
        return p;
    }

    /** Eine Zeile, die mit ihrem Inhalt wächst. */
    private static JComponent row(Component c) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        p.add(c, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    static JLabel label(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Theme.ink());
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return l;
    }

    static JLabel small(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Theme.muted());
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return l;
    }

    // ------------------------------------------------------------ Scrollable: nur senkrecht
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(java.awt.Rectangle rr, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(java.awt.Rectangle rr, int o, int d) { return 120; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
