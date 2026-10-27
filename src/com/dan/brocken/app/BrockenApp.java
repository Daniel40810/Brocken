package com.dan.brocken.app;

import com.dan.brocken.gl.Renderer;
import com.dan.brocken.gl.SceneView;
import com.dan.brocken.scene.SceneClock;
import com.dan.brocken.ui.AppIcon;
import com.dan.brocken.ui.ControlPanel;
import com.dan.brocken.ui.Startbild;
import com.dan.brocken.ui.Theme;
import com.dan.fframe.FFrame;
import com.dan.foptionpane.FOptionPane;
import com.jogamp.opengl.GLProfile;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Rectangle;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Brocken — Dreihundert Tage Nebel: der Brocken im Harz mit Wind, Nebel, Raueis, dem toten Fichtenwald und der
 * Brockenbahn als 3D-Szene auf der Grafikkarte (JOGL, OpenGL 4.3). „Das Projekt der Luft“ (Werkbuch, Abschnitt 1).
 * <p>
 * Einstieg: Startbild, dann FFrame beim Start maximiert (Taskleiste bleibt frei), Größe frei veränderbar; Szene in
 * der Mitte, Bedienfeld rechts, Statuszeile unten. Die Element-Packages kommen aus {@code lib/Renderer.jar} (F4 A);
 * die neuen {@code wind}, {@code rime} und {@code rail} entstehen im Projekt Renderer. Das Gerüst ist aus Geiranger
 * übernommen und entschlackt (F5 A).
 * <p>
 * Schalter: {@code -Dbrocken.splash=false} ohne Startbild, {@code -Dbrocken.gljpanel=true} leichter GL-Rückfall,
 * {@code -Dbrocken.shaders=<ordner>} Shader von der Platte (F5 lädt neu), {@code -Dbrocken.daten=<ordner>} Ordner für
 * Dateien der App.
 */
public final class BrockenApp {
    /** Für Werkzeuge: Fenster und Szene, sobald sie stehen. */
    public static volatile FFrame frame;
    public static volatile SceneView scene;
    public static volatile ControlPanel controls;
    /** Fehler in Fäden seit dem Start (für den Dauerlauf ab Phase 18). */
    public static final AtomicInteger FEHLER = new AtomicInteger();

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        // Der GL-Canvas ist ein schweres AWT-Element: Menüs und Tooltips müssen darüber liegen können
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println("Faden " + t.getName() + ": " + e);
            FEHLER.incrementAndGet();
            e.printStackTrace();
            fehlerDatei("Faden " + t.getName(), e);
        });
        GLProfile.initSingleton();
        SwingUtilities.invokeLater(() -> {
            try {
                open();
            } catch (Throwable e) {
                // ohne Konsole (javaw, Verknüpfung) bliebe ein Fehler beim Start sonst unsichtbar (Befund aus Haithabu)
                e.printStackTrace();
                File f = fehlerDatei("Start", e);
                FOptionPane.showMessageDialog(null, "Brocken ließ sich nicht starten:\n" + e + (e.getStackTrace().length > 0 ? "\n  bei " + e.getStackTrace()[0] : "")
                        + "\n\nDie ganze Meldung steht in\n" + (f != null ? f.getAbsolutePath() : "der Konsole") + ".", "Brocken", FOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    /** Ordner für Dateien der App ({@code daten/}): Fehler, ab Phase 2 das importierte Gelände. */
    public static File datenOrdner() {
        String d = System.getProperty("brocken.daten");
        return new File(d != null ? d : "daten");
    }

    /** Schreibt einen Fehler mit Stapel nach daten/fehler.txt (angehängt) und gibt die Datei zurück, oder null. */
    static File fehlerDatei(String wo, Throwable e) {
        try {
            File f = new File(datenOrdner(), "fehler.txt");
            f.getParentFile().mkdirs();
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            String t = java.time.LocalDateTime.now().withNano(0) + " · " + Renderer.VERSION + " · Java " + System.getProperty("java.version") + " · " + wo + "\n" + sw + "\n";
            java.nio.file.Files.writeString(f.toPath(), t, java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            return f;
        } catch (Exception x) {
            return null;
        }
    }

    private static void open() {
        // Phase 17: das Startbild verbindet mit der Datenbank (oder daten/); „Weiter wie zuletzt“ bringt eine Sitzung mit
        Startbild.Wahl wahl = "false".equals(System.getProperty("brocken.splash")) ? Startbild.stumm() : Startbild.zeigen();
        if (wahl.beenden()) System.exit(0);
        // F26 A: Fahrplan, Klima, Lagen und Regiebücher aus der Datenbank, bevor die Szene entsteht; der Code ist der Rückfall
        com.dan.brocken.data.Szenendaten.laden(com.dan.brocken.data.Datenseite.speicher());
        SceneClock clock = new SceneClock();
        Renderer rd = new Renderer(clock);
        FFrame f = new FFrame("Brocken · Dreihundert Tage Nebel · " + Renderer.VERSION);
        AppIcon.install(f);
        if (!Boolean.getBoolean("brocken.gljpanel")) makeOpaque(f);
        SceneView view;
        try {
            view = new SceneView(rd);
        } catch (RuntimeException e) {
            FOptionPane.showMessageDialog(null, "OpenGL lässt sich nicht starten:\n" + e.getMessage()
                    + "\n\nBitte den Grafiktreiber aktualisieren. Als Rückfall: Start mit -Dbrocken.gljpanel=true.", "Brocken", FOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        ControlPanel controls = new ControlPanel(view);
        JLabel status = new JLabel("Die Szene startet …");
        status.setForeground(new Color(170, 180, 184));
        status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        status.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        String gel = rd.terrain.measured() ? "Gelände " + rd.terrain.source + (rd.terrain.hasKern() ? " (Kern DGM1)" : " (Kern ohne DGM1)") : "ohne daten/gelaende: Kuppe der Bühne";
        JLabel right = new JLabel(gel + " · " + Startbild.statuszeile());
        new javax.swing.Timer(2000, e -> right.setText(gel + " · " + Startbild.statuszeile())).start();
        right.setFont(new Font("SansSerif", Font.PLAIN, 12));
        right.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        right.setForeground(Theme.GOLD);
        rd.setStatusListener(s -> SwingUtilities.invokeLater(() -> status.setText(s)));
        rd.setErrorListener(msg -> SwingUtilities.invokeLater(() -> FOptionPane.showMessageDialog(f, msg, "Brocken · Grafik", FOptionPane.ERROR_MESSAGE)));

        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(Theme.bg());
        root.add(view, BorderLayout.CENTER);
        JScrollPane side = new JScrollPane(controls, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        side.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.line()));
        side.getViewport().setBackground(Theme.bg());
        side.setBackground(Theme.bg());
        try {
            com.dan.fscrollbar.FScrollBar bar = new com.dan.fscrollbar.FScrollBar(javax.swing.JScrollBar.VERTICAL);
            bar.setUnitIncrement(16);
            bar.setBlockIncrement(120);
            side.setVerticalScrollBar(bar);
        } catch (RuntimeException | LinkageError e) {
            side.getVerticalScrollBar().setUnitIncrement(16);
        }
        side.setPreferredSize(new Dimension(320, 600));
        root.add(side, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(Theme.status());
        south.add(status, BorderLayout.CENTER);
        south.add(right, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);

        f.setPreferredFrameSize(new Dimension(1480, 900));
        f.setSize(1480, 900);
        f.setMinimumSize(new Dimension(800, 520));
        f.setLocationRelativeTo(null);
        f.setResizable(true);
        f.setVisible(true);
        startMaximized(f);
        view.start();
        // Phase 17 (F29, F37): Sitzung, Protokoll alle 5 s, Zustand alle 2 min und beim Ende; die Chronik
        com.dan.store.Protokoll.kontext(() -> com.dan.brocken.data.Daten.text(clock.epochMillis()),
                () -> String.format(java.util.Locale.ROOT, "%.0f %.0f %.0f", rd.camera.eye[0], rd.camera.eye[1], rd.camera.eye[2]));
        com.dan.store.Dienst.starten(Renderer.VERSION, "", rd.quality().name(), () -> sicherung(rd));
        com.dan.store.Protokoll.info("App", "Brocken " + Renderer.VERSION + " gestartet · " + com.dan.brocken.data.Datenseite.wo() + " · "
                + com.dan.brocken.data.Szenendaten.zeile);
        com.dan.brocken.data.Chronik ch = new com.dan.brocken.data.Chronik(rd);
        com.dan.brocken.data.Datenseite.chronik = ch;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { ch.beenden(); } catch (RuntimeException e) { System.err.println("Brocken: Chronik nicht beendet: " + e); }
            try { com.dan.store.Dienst.beenden(); } catch (RuntimeException e) { System.err.println("Brocken: Sitzung nicht beendet: " + e); }
            view.stop();
        }, "Brocken-Ende"));
        // F11: nur die Szene (Bedienfeld und Statuszeile weg und wieder da)
        view.setKeyAction(java.awt.event.KeyEvent.VK_F11, () -> SwingUtilities.invokeLater(() -> {
            boolean an = !side.isVisible();
            side.setVisible(an);
            south.setVisible(an);
            root.revalidate();
        }));
        // Phase 16: der Kinomodus – Bedienfeld, Statuszeile und Titelleiste weg, das Fenster über den ganzen Bildschirm
        rd.kinoFenster = an -> SwingUtilities.invokeLater(() -> kinoFenster(f, view, side, south, root, an));
        // Phase 17: „Weiter wie zuletzt“ oder ein Regiebuch im Kino aus dem Startbild
        if (wahl.weiter() != null) com.dan.brocken.ui.Weiter.anwenden(rd, wahl.weiter(), () -> view.setMode(rd.camera.mode));
        if (wahl.kino() != null) com.dan.brocken.ui.Weiter.kino(rd, wahl.kino());
        // Phase 18: -Dbrocken.dauerlauf=Minuten startet den Dauerlauf gleich
        String dl = System.getProperty("brocken.dauerlauf");
        if (dl != null) Dauerlauf.starten(rd, Double.parseDouble(dl), f2 -> rd.hudMeldung("Dauerlauf fertig: " + (f2 != null ? f2.getName() : "ohne Bericht")));
        view.requestFocusInWindow();
        frame = f;
        scene = view;
        BrockenApp.controls = controls;
        System.out.println("Brocken " + Renderer.VERSION + " gestartet · " + clock.label() + " · Renderer.jar: " + Bibliothek.kurz());
    }

    /** Phase 17: der Zustand für die Sitzung (alle 2 min und beim Ende). */
    private static com.dan.store.Ablage.Sicherung sicherung(Renderer r) {
        java.util.LinkedHashMap<String, String> z = com.dan.brocken.data.Zustand.erfassen(r);
        return new com.dan.store.Ablage.Sicherung(com.dan.store.Felder.alsText(z, "Brocken · Sitzung · " + Renderer.VERSION), com.dan.brocken.data.Zustand.spalten(z));
    }

    private static boolean kinoVoll, kinoSeite = true, kinoSueden = true;
    private static int kinoZustand;
    private static java.awt.Rectangle kinoRahmen;

    /** Phase 16: Vollbild für das Kino und zurück (aus Rungholt und Glacier). */
    private static void kinoFenster(FFrame f, SceneView view, java.awt.Component side, java.awt.Component south, JPanel root, boolean an) {
        if (an == kinoVoll) return;
        java.awt.Component bar = taskbar(f);
        if (an) {
            kinoSeite = side.isVisible();
            kinoSueden = south.isVisible();
            kinoZustand = f.getExtendedState();
            kinoRahmen = f.getBounds();
            kinoVoll = true;
            side.setVisible(false);
            south.setVisible(false);
            if (bar != null) bar.setVisible(false);
            java.awt.Rectangle schirm = f.getGraphicsConfiguration().getBounds();
            f.setExtendedState(java.awt.Frame.NORMAL);
            f.setBounds(schirm);
            kinoForm(f);
            view.zeigerVerbergen(true);
            f.toFront();
        } else {
            kinoVoll = false;
            side.setVisible(kinoSeite);
            south.setVisible(kinoSueden);
            if (bar != null) bar.setVisible(true);
            if (kinoRahmen != null) f.setBounds(kinoRahmen);
            if ((kinoZustand & java.awt.Frame.MAXIMIZED_BOTH) != 0) startMaximized(f);
            kinoForm(f);
            view.zeigerVerbergen(false);
        }
        root.revalidate();
        root.repaint();
        view.requestFocusInWindow();
    }

    /** Die Titelleiste des FFrame (für das Kino ausblenden). */
    private static java.awt.Component taskbar(FFrame f) {
        try {
            for (java.lang.reflect.Field fd : FFrame.class.getDeclaredFields()) {
                if (!fd.getType().getSimpleName().equals("FTaskbar")) continue;
                fd.setAccessible(true);
                return (java.awt.Component) fd.get(f);
            }
        } catch (Exception e) {
            System.err.println("FFrame-Titelleiste nicht gefunden: " + e);
        }
        return null;
    }

    /** Im Kino eckig über den ganzen Schirm, sonst die runden Ecken des FFrame. */
    private static void kinoForm(FFrame f) {
        try {
            boolean max = kinoVoll || (f.getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) != 0;
            f.setShape(max ? null : new java.awt.geom.RoundRectangle2D.Double(0, 0, f.getWidth(), f.getHeight(), 20, 20));
        } catch (UnsupportedOperationException | java.awt.IllegalComponentStateException e) {
            // Form nicht möglich: dann eben eckig
        }
    }

    /**
     * Startet maximiert (MAXIMIZED_BOTH), ohne die Taskleiste zu verdecken. Die normale Größe von 1480 × 900 bleibt
     * als Rückfall; „Wiederherstellen“ des FFrame stellt sie wieder her (wie in Geiranger).
     */
    private static void startMaximized(FFrame f) {
        Rectangle normal = f.getBounds();
        f.setMaximizedBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        f.setExtendedState(f.getExtendedState() | Frame.MAXIMIZED_BOTH);
        try {
            // Die Titelleiste des FFrame führt einen eigenen Maximiert-Zustand; ihn angleichen
            for (java.lang.reflect.Field fd : FFrame.class.getDeclaredFields()) {
                if (!fd.getType().getSimpleName().equals("FTaskbar")) continue;
                fd.setAccessible(true);
                Object bar = fd.get(f);
                Class<?> bc = bar.getClass();
                java.lang.reflect.Field mx = bc.getDeclaredField("maximized"), rb = bc.getDeclaredField("restoreBounds"),
                        btn = bc.getDeclaredField("btnMaximize");
                mx.setAccessible(true);
                rb.setAccessible(true);
                btn.setAccessible(true);
                mx.setBoolean(bar, true);
                rb.set(bar, normal);
                Object icon = btn.get(bar);
                icon.getClass().getMethod("setType", com.dan.ficons.FIconType.class).invoke(icon, com.dan.ficons.FIconType.RESTORE);
                ((javax.swing.JComponent) icon).setToolTipText("Wiederherstellen");
                ((java.awt.Component) icon).addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                        SwingUtilities.invokeLater(() -> {
                            if ((f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
                                f.setExtendedState(f.getExtendedState() & ~Frame.MAXIMIZED_BOTH);
                                f.setBounds(normal);
                            }
                        });
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("FFrame-Titelleiste nicht angeglichen: " + e);
        }
    }

    /**
     * Das FFrame ist ein durchscheinendes Fenster (runde Ecken). Darin zeigt Windows schwere Elemente wie den
     * GL-Canvas nicht; darum wird es deckend gemacht und die Rundung als Fensterform gesetzt.
     */
    private static void makeOpaque(FFrame f) {
        f.setBackground(Theme.bg());
        java.awt.event.ComponentAdapter shape = new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { reshape(f); }
            @Override public void componentShown(java.awt.event.ComponentEvent e) { reshape(f); }
        };
        f.addComponentListener(shape);
        f.addWindowStateListener(e -> reshape(f));
    }

    private static void reshape(FFrame f) {
        try {
            boolean max = (f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
            f.setShape(max ? null : new java.awt.geom.RoundRectangle2D.Double(0, 0, f.getWidth(), f.getHeight(), 20, 20));
        } catch (UnsupportedOperationException | IllegalComponentStateException e) {
            // Form nicht möglich: dann eben eckig
        }
    }

    private BrockenApp() { }
}
