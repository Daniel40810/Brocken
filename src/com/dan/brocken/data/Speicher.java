package com.dan.brocken.data;

import com.dan.store.Ablage;

import java.util.List;
import java.util.Map;

/**
 * Wohin die App speichert: in das Schema BROCKEN ({@link DbSpeicher}) oder, ohne Datenbank, in Dateien im Ordner
 * {@code daten/} ({@link DateiSpeicher}, F25 A). Beide können dasselbe; die Oberfläche kennt nur diese Schnittstelle.
 * Sitzung und Protokoll kommen aus {@link Ablage} (com.dan.store).
 * <p>
 * Geschrieben wird zeilenweise ({@link Daten.Zeile}, Spalten wie in {@code 01_tabellen.sql}): die Chronik
 * ({@code BR_STUNDE}, {@code BR_TAG}, {@code BR_NEBELMEER}, {@code BR_GESPENST}, {@code BR_ZUGFAHRT},
 * {@code BR_WINDFELD}, {@code BR_REGIELAUF}) und Eigenes ({@code BR_MERKPUNKT}, {@code BR_ANSICHT} gemerkt,
 * {@code BR_REGIEBUCH} eigen, {@code BR_PAKET_PRESET} eigen). Mitgeliefertes schreibt nur der Einrichter
 * ({@code 04_startdaten.sql}); löschen lässt sich nur Eigenes.
 */
public interface Speicher extends Ablage {

    /** Schlüssel der Werte, die eine {@link Ablage.Sicherung} über die Sitzung mitbringt (Spalten von BR_SITZUNG). */
    String SZENE_MS = "szeneMs", ZEITRAFFER = "zeitraffer", LAGE = "lage", REGIEBUCH = "regiebuch", REGIE_SEKUNDE = "regieSekunde", KAMERA = "kamera",
            AUGE_X = "augeX", AUGE_Z = "augeZ";

    /** Die Tabellen der Chronik, die „Übernehmen“ aus den Dateien in die Datenbank trägt. */
    List<String> CHRONIK = List.of("BR_STUNDE", "BR_TAG", "BR_NEBELMEER", "BR_GESPENST", "BR_ZUGFAHRT", "BR_WINDFELD", "BR_REGIELAUF");
    /** Die Tabellen mit eigenen Zeilen (HERKUNFT = 'EIGEN'). */
    List<String> EIGENES = List.of("BR_MERKPUNKT", "BR_ANSICHT", "BR_REGIEBUCH", "BR_PAKET_PRESET");

    Daten.Stand stand() throws Fehler;

    /** Eine Zeile anlegen; gibt ihre Nummer zurück. Werte mit unbekannter Spalte werden abgelehnt. */
    long schreiben(String tabelle, Daten.Zeile z) throws Fehler;

    /** Zeilen lesen (ohne BLOB), neueste zuerst; {@code wo} ist ein Filter auf Spalten (Spalte → Wert) oder leer. */
    List<Daten.Zeile> lesen(String tabelle, Map<String, Object> wo, int hoechstens) throws Fehler;

    /** Ein BLOB einer Zeile (Vorschau, Blatt) oder null. */
    byte[] bytes(String tabelle, long id, String spalte) throws Fehler;

    /** Eine eigene Zeile löschen (mitgelieferte nie). */
    void loeschen(String tabelle, long id) throws Fehler;

    /** Ein Preset eines Package mit seinen Werten (Feld → Wert) schreiben; ersetzt ein eigenes gleichen Namens. */
    void preset(String paket, String klasse, String name, String beschreibung, Map<String, String> werte) throws Fehler;

    /** Die Presets eines Package (klasse = einfacher Name, null für alle): Name → Feld → Wert; mitgelieferte und eigene. */
    Map<String, Map<String, String>> presets(String klasse) throws Fehler;

    /** Ein eigenes Regiebuch (Text im Format von scene.Regie) unter eigenem Namen; ersetzt ein eigenes gleichen Namens. */
    void regiebuch(String name, String text, String beschreibung) throws Fehler;

    /** Die letzte Sitzung mit Zustand vor der laufenden (oder null). */
    Daten.Sitzung letzteSitzung(long ausser) throws Fehler;

    /** Die letzten n Zeilen des Protokolls zum Anzeigen. */
    List<String> letzteZeilen(int n) throws Fehler;

    /** Die Stammdaten aus der Datenbank (F26 A) oder null, wenn der Code gilt (ohne Datenbank). */
    Szenendaten szenendaten() throws Fehler;
}
