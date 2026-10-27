package com.dan.brocken.tools;

import com.dan.brocken.app.BrockenApp;
import com.dan.brocken.data.Verbindung;
import com.dan.brocken.gl.Renderer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Schreibt {@code db/sql/04_startdaten.sql} aus dem Code (Phase 17, F26 A): baut die Szene ohne Fenster (Gelände,
 * Bodenkarte, Strecke, Gipfel, Wege), sammelt die Startdaten ({@code data.Startdaten}) und schreibt das Skript
 * ({@code data.StartdatenSql}). Im Projektordner starten: {@code ant startdaten} oder „Run File“.
 */
public final class StartdatenSql {
    private StartdatenSql() { }

    public static void main(String[] args) throws Exception {
        long t0 = System.currentTimeMillis();
        Renderer r = new Renderer();
        String sql = com.dan.brocken.data.StartdatenSql.sql(r, new File(BrockenApp.datenOrdner(), "gelaende"));
        File f = new File(Verbindung.skripte(), "04_startdaten.sql");
        Files.writeString(f.toPath(), sql, StandardCharsets.UTF_8);
        String zahlen = sql.lines().filter(l -> l.startsWith("-- ZAHLEN:")).findFirst().orElse("");
        System.out.printf("%s geschrieben: %,d Zeichen, %,d Zeilen (%.1f s)%n%s%n", f.getPath(), sql.length(), sql.lines().count(),
                (System.currentTimeMillis() - t0) / 1000.0, zahlen);
        System.exit(0);
    }
}
