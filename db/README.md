# db – Datenbank vom Brocken

Schema `BROCKEN` auf PDBORCL (angelegt mit `USER.sql`), Präfix `BR_`, Spatial mit SRID 8307 (WGS 84, Länge und Breite). Werkbuch Abschnitt 9, Fragen F25–F37 „alle A“ (Phase 17). Der Zugang steht in `db.properties` (Vorlage `db.properties.example`, nie ins Git); fehlt die Datei, legt die App sie aus der Vorlage an, fragt im Startbild nach dem Passwort und trägt es nach dem ersten erfolgreichen Verbinden ein (oder Umgebungsvariable `BROCKEN_DB_PASSWORD`). Ohne Datenbank läuft die App weiter und speichert in den Ordner `daten/` des Projekts (`daten/zeilen/BR_*.txt`, `daten/sitzungen/`, `daten/protokoll.txt`; dieselben Spalten wie die Tabellen, gegen `sql/01_tabellen.sql` geprüft); beim nächsten Verbinden trägt „Übernehmen“ im Block DATEN alles in die Datenbank und benennt die Dateien in `*.uebernommen` um (F25).

## Einrichten

Startbild oder Block DATEN, Knopf **Einrichten**. Der Einrichter (`com.dan.store.Einrichter`) führt die Skripte in `sql/` der Reihe nach aus und prüft danach alles: Tabellen, Spalten mit Typ, Sequenzen, Trigger, Sichten, Spatial-Metadaten, räumliche Indizes und die Zahl der mitgelieferten Zeilen (28 Arten); dazu rechnet er Gleis und Ausschnitt auf dem Ellipsoid nach. Am Ende steht „alles in Ordnung“ oder genau, was fehlt. Er löscht nie etwas und lässt sich beliebig oft wiederholen; fehlende Spalten einer älteren Ausbaustufe fügt er an.

| Skript | Inhalt |
|---|---|
| `sql/01_tabellen.sql` | 39 Tabellen, 38 Sequenzen, 37 Trigger (Sequence + Trigger, nie IDENTITY; `BR_LOG` mit Sequenz im Cache ohne Trigger), 25 Indizes |
| `sql/02_spatial.sql` | Metadaten und räumlicher Index (`SPATIAL_INDEX_V2`) für 15 Geometriespalten; Metadaten für die drei Sichten mit Geometrie (für QGIS) |
| `sql/03_sichten.sql` | Sieben Sichten: **`BR_BROCKEN_V`** (Bauten, Felsen, Orte, Wege, Bahnhöfe, Gleis, Boden, Merkpunkte als eine Ebene), **`BR_TAGESKARTE_V`** (dazu je Szenentag Gespenster, Nebelmeer als Höhenlinie, Zugfahrten auf dem Gleis, Merkpunkte), `BR_FAHRPLAN_V` (mit Fahrten, Verspätung und Pfiffen aus der Chronik), `BR_JAHR_V` (Tage gegen das Klima), `BR_GESPENST_V`, `BR_BEFUND_V`, `BR_LETZTE_SITZUNG_V` |
| `sql/04_startdaten.sql` | 35 Quellen, 22 Befunde, 4 Gelände mit 3 Ausschnitten und 16 Höhenlinien, 2 Felsburgen, 4 Böden, 13 Lagen mit 44 Übergängen, 12 Klimamonate, 16 Höhenstufen, die Strecke (69 Teile), 3 Bahnhöfe, 2 Bahnübergänge, 3 Fahrzeuge, 14 Fahrten im Fahrplan, 12 Bauten, 14 Orte, 4 Wege, 4 Wetter- und 4 Regiebücher mit 104 Stichworten, 9 Ansichten, 2 Fahrten mit 73 Stützpunkten, 27 Presets. Erzeugt aus `com.dan.brocken.data.Startdaten` mit `ant startdaten` (`tools.StartdatenSql`), nicht von Hand ändern; die Prüfung meldet, wenn die Datei nicht mehr zum Code passt |
| `sql/99_entfernen.sql` | Entfernt alle `BR_`-Objekte samt Inhalt. Nur von Hand für einen Neuanfang, die App führt es nie aus |

Dieselben Skripte lassen sich in SQL Developer als Skript ausführen (F5). Bei einem zweiten Lauf meldet Oracle ORA-00955 (Name schon vergeben); das ist harmlos.

Meldet der räumliche Index ORA-01031 oder ORA-13249, fehlt dem Schema ein direkt erteiltes Recht (eine Rolle reicht dafür nicht). Als DBA: `GRANT CREATE TABLE, CREATE SEQUENCE TO BROCKEN;`, dann noch einmal einrichten.

## Prüfen

`ant dbpruefung` oder `test/com/dan/brocken/test/DbPruefung.java` in NetBeans mit „Run File“. Sie richtet ein, läuft den Einrichter ein zweites Mal, prüft mit ihm, rechnet Gleis und Hauptraster auf dem Ellipsoid nach, prüft alle Geometrien auf Gültigkeit, fragt einen räumlichen Operator (alle Bauten im Kern), liest die Szenendaten aus der Datenbank (F26), lässt die Chronik einen Nebelmeer-Vormittag mit der Bergfahrt 10:06 schreiben und liest ihn über die Sichten zurück (F29), legt einen Merkpunkt mit Vorschau an (F35), speichert Regiebuch und Preset unter „~Prüfung …“ (F34), sichert eine Sitzung und stellt sie mit „Weiter wie zuletzt“ her (F37, das DoD), übernimmt Dateien (F25) und räumt danach auf. Ende: „Alles in Ordnung.“

## Szenenzeit

Die Szenenzeit steht als `SZENE_MS` (Millisekunden seit 1970), als `SZENE_TEXT` („21.10.2026 07:42 MESZ“) und als `SZENE_TAG` (`'2026-10-21'`, zum Filtern). `BR_STUNDE` hat dazu `SZENENZEIT` als TIMESTAMP WITH TIME ZONE in Europe/Berlin. Lagen stehen in 8307 und zusätzlich in Szenenmetern `X_M`/`Z_M` (x = Ost − 611 400, z = −(Nord − 5 739 940), UTM 32N).

## Tabellen

| Thema | Tabelle | Inhalt |
|---|---|---|
| Betrieb | `BR_SCHEMA` | Ausbaustufe (Phase 17 = 1) |
| | `BR_SITZUNG` | Sitzungen; Zustand (CLOB) alle zwei Minuten und beim Ende, Szenenzeit, Zeitraffer, Lage, Regiebuch mit Stelle, Kamera, Auge als Punkt |
| | `BR_LOG` | Protokoll (alle 5 s); älter als 90 Tage räumt die App beim Start weg |
| Quellen | `BR_QUELLE`, `BR_BEFUND`, `BR_BEFUND_QUELLE` | Quellen; Befunde je Gegenstand: `BELEGT`, `GEMESSEN` oder `GESTALTET`, mit ihren Quellen |
| Gelände | `BR_GELAENDE`, `BR_AUSSCHNITT`, `BR_HOEHENLINIE` | Die Dateien in `daten/gelaende` mit SHA-256; Kern, Hauptraster und Fernring als Flächen; Höhenlinien zu 50 m als Mehrfachlinien |
| Boden | `BR_FELS`, `BR_BODEN` | Felsburgen als Punkte; Moor, Heide, Blockfeld, Fels als Mehrfachflächen aus der Bodenkarte |
| Wetter | `BR_LAGE`, `BR_LAGE_UEBERGANG`, `BR_KLIMA_MONAT`, `BR_HOEHENSTUFE` | Die Lagen der Regie und ihre Übergänge, das Klima der Wetterwarte (DWD 1991–2020), die 16 Stufen des Jahrs; die App liest sie beim Start (F26), der Code ist der Rückfall |
| Bahn | `BR_STRECKE`, `BR_BAHNHOF`, `BR_BAHNUEBERGANG`, `BR_FAHRZEUG`, `BR_FAHRPLAN` | Gleis, Ausweichen und Abschnitte mit Steigung; Bahnhöfe, Übergänge; Lok und Wagen; der Fahrplan Sommer 2026 |
| Gipfel | `BR_BAU`, `BR_ORT`, `BR_WEG` | Bauten mit Grundriss aus ALKIS, Orte, Brockenstraße und Rundweg |
| Regie | `BR_WETTERBUCH`, `BR_REGIEBUCH`, `BR_STICHWORT`, `BR_REGIELAUF` | Wetter- und Regiebücher als Text und in Stichworten; jeder Lauf, im Kino oder nicht |
| Chronik | `BR_STUNDE`, `BR_TAG`, `BR_NEBELMEER`, `BR_GESPENST`, `BR_ZUGFAHRT`, `BR_WINDFELD` | Jede Stunde (Lage, Wetter am Gipfel, Nebelmeer, Schnee, Raueis); jeder Tag; jedes Nebelmeer mit Grenzen; jedes Brockengespenst mit Sonne und Glorie; jede Zugfahrt mit Plan, Ist, Verspätung und Pfiffen; jede Lösung des Windfelds (F29) |
| Kamera | `BR_ANSICHT`, `BR_FAHRT`, `BR_STUETZPUNKT` | Feste Ansichten 1–9 und gemerkte (Taste V) mit Zustand und Vorschau; Fahrten |
| Eigenes | `BR_MERKPUNKT` | Umschalt + Klick in die Szene: Punkt mit Notiz, Szenenzeit, Auge und Vorschau (F35) |
| Packages | `BR_PAKET_PRESET`, `BR_PAKET_WERT` | Presets je Package, ein öffentliches Feld je Zeile (F34) |

Mitgelieferte Zeilen (`HERKUNFT = 'MITGELIEFERT'`) stellt der Einrichter bei jedem Lauf wieder her; eigene Zeilen (`EIGEN`) fasst er nie an. Die App überschreibt Mitgeliefertes nicht: ein Regiebuch oder Preset bekommt einen eigenen Namen.

## QGIS

1. Satellitenbild als Grundkarte (etwa XYZ-Kacheln „Esri World Imagery“).
2. Neue Verbindung zu Oracle: Datenbank `PDBORCL`, Benutzer `Brocken`. Unter den Tabellen erscheinen die Geometriespalten mit SRID 8307.
3. Alles auf einmal: `BR_BROCKEN_V` (Geometrie `GEOM`, Schlüssel `OBJEKT_ID`, Stil nach `EBENE` und `ART`); dazu `BR_HOEHENLINIE` (Geometrie `LINIE`).
4. Die Tageskarte: `BR_TAGESKARTE_V`, Filter `"TAG" = '2026-10-21' OR "TAG" IS NULL`, Stil nach `EBENE` und `ART`.

Ohne Datenbank zeichnet die App dieselbe Karte als PNG (Block DATEN, „Tageskarte“).
