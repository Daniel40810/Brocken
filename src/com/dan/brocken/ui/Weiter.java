package com.dan.brocken.ui;

import com.dan.brocken.data.Daten;
import com.dan.brocken.data.Zustand;
import com.dan.brocken.gl.Quality;
import com.dan.brocken.gl.Renderer;
import com.dan.store.Felder;
import com.dan.store.Protokoll;

import java.util.List;
import java.util.Map;

/**
 * „Weiter wie zuletzt“ (Phase 17, F37 A): einen gesicherten Zustand herstellen. Uhr, Zeitraffer, Wetterregie mit
 * Handreglern, Kamera, Qualität, Ton mit Wind am Ohr, die Ebenen und Regler kommen zurück; was gerade lief (Mitfahrt,
 * Treiben, ein Regiebuch), beginnt man neu – der HUD sagt, welches Regiebuch lief. Hergestellt wird im Faden des
 * Bildlaufs ({@link Renderer#imBild}), damit nichts mitten im Bild springt.
 */
public final class Weiter {
    private Weiter() { }

    /** Herstellen, sobald der Bildlauf den nächsten Schritt macht; danach läuft {@code danach} (darf null sein). */
    public static void anwenden(Renderer r, Daten.Sitzung s, Runnable danach) {
        if (s == null || s.zustand() == null) return;
        r.imBild(() -> {
            jetzt(r, s);
            if (danach != null) javax.swing.SwingUtilities.invokeLater(danach);
        });
    }

    /** Ein Regiebuch im Kino, sobald der Bildlauf läuft. */
    public static void kino(Renderer r, com.dan.brocken.scene.Regie.Book b) { r.imBild(() -> r.kinoStart(b)); }

    /** Sofort herstellen (im Faden des Bildlaufs oder in Prüfungen). Gibt zurück, was nicht ging. */
    public static List<String> jetzt(Renderer r, Daten.Sitzung s) {
        Map<String, String> z = Felder.ausText(s.zustand());
        Quality q = Zustand.qualitaet(z);
        if (q != null && q != r.quality()) r.setQuality(q);
        List<String> f = Zustand.anwenden(r, z);
        Protokoll.info("Sitzung", "Weiter wie zuletzt (Sitzung " + s.id() + "): " + z.size() + " Werte" + (f.isEmpty() ? "" : ", nicht hergestellt: " + String.join(", ", f)));
        String rest = z.containsKey("regiebuch.name") ? " · „" + z.get("regiebuch.name") + "“ lief: Block REGIEBÜCHER" : "";
        r.hudMeldung("Weiter wie zuletzt · " + z.getOrDefault("uhr.text", "") + rest);
        return f;
    }
}
