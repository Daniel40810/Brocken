# Prompt: Brocken Werkbuch

Stand 25.10.2026 · NetBeans-Projekt `C:\Users\Dan40810\Documents\NetBeansProjects\Brocken`

Dieser Prompt ist die Grundlage für das Artefakt **„Brocken Werkbuch“**. Das Werkbuch klärt zuerst Auftrag, Daten, Packages und Grundsatzfragen. **Code gibt es erst nach der Freigabe** („Phase 1, los!“). Bis dahin wird nichts im Projekt angelegt außer diesem Prompt.

---

## 0 · Was das Werkbuch sein soll

- Ein Artefakt im Stil der bisherigen Werkbücher (Geiranger, Rungholt, Glacier, Haithabu, Lighthouse): eine Spalte, zuerst dunkel, eigene Farben aus dem Motiv, eine animierte Stimmungsskizze oben (Canvas, „Stimmungsskizze, kein Bild aus dem Projekt“), eine Leiste für den Stand der Phasen, danach die nummerierten Abschnitte.
- Die Sprache ist einfach und deutsch, ohne Fremdwörter, wo es ein deutsches Wort gibt. Gesetzte Werte tragen **[G]**. Was vor dem Bau nachzulesen ist, heißt **„zu prüfen“**.
- Die Grundsatzfragen stehen als Tabelle mit A, B, gegebenenfalls C. **A ist die Empfehlung, mit Begründung.** B und C sind echte Alternativen. Die Fragen zur Datenbank kommen wie in Rungholt und Geiranger als eigener Block zu Beginn der Phase „Oberfläche und Datenbank“.
- Titel des Artefakts: **Brocken Werkbuch**. Vorschlag für die große Überschrift: *„Dreihundert Tage Nebel“* (oder etwas Besseres).
- Farben aus dem Motiv: Brockengranit (rötliches Grau), totes Fichtenholz (Silbergrau), lebende Fichte (Schwarzgrün), Raureif (Weiß mit Blaustich), Moor (Torfbraun), Wollgras (Weiß), Heidelbeere/Heide (Violett), Dampf (warmes Weiß), Lok (Schwarz mit roten Rädern), Abendgold über dem Nebelmeer.
- Stimmungsskizze: Blick über ein Nebelmeer, aus dem nur die Gipfelkuppe ragt; der Wind treibt Fetzen über die Kuppe; Raureif an Fichten; eine Dampflok schraubt sich mit ihrer Fahne um den Gipfel; im Nebel gegenüber ein Schatten mit farbigem Ring (Brockengespenst und Glorie).

---

## 1 · Auftrag und Leitidee

**Auftrag (aus Claudes Vorschlag vom 25.10.2026 übernommen):** Eine animierte 3D-Echtzeitszene des Brockens im Harz, 1 141 m. Der Brocken gilt als der windigste und nebligste Berg Deutschlands. Dazu kommen Raureif an Fichten, Masten und Felsen, das Brockengespenst, die Granitklippen, das Hochmoor, der tote Fichtenwald nach dem Borkenkäfer und die Dampflok der Brockenbahn, die sich zum Schluss um den Gipfel schraubt. Daten im Projekt. Zunächst nur das Werkbuch.

**Leitidee:** Lighthouse hatte das Wetter, Haithabu die Siedlung, Glacier die Physik, Rungholt die Zeit, Geiranger die Senkrechte. **Brocken ist das Projekt der Luft.** Bisher war der Wind in allen Szenen überall gleich. Am Brocken hat er einen Ort: Über der Kuppe wird er schneller, im Lee wirbelt er, in den Tälern wird er gebündelt, nachts fließt kalte Luft die Hänge hinab. Aus dieser Luft kommt fast alles, was man sieht: Nebel, der am Hang hochgedrückt wird, Raureif, der gegen den Wind wächst, Fichten, die als Windflüchter stehen, Schnee, der verweht, und die Fahne der Lok, die über den Gipfel gerissen wird.

**Der Kern des Projekts ist das neue Package `wind`.** In acht bestehenden Packages steht ein Windfeld unter „Grenzen“. Diese Packages bekommen mit diesem Projekt eine Abfrage für das Windfeld (siehe Abschnitt 5), und die Vorgänger können sie später nutzen.

---

## 2 · Ausgangslage im Projektordner (geprüft am 25.10.2026)

### Projekt
- NetBeans-Projekt mit Ant, **JDK 21** (`javac.source/target=21`), `main.class` noch leer, `src/` leer.
- `lib/`: `Renderer.jar` (vom 24.10., **gleich** mit `Renderer/dist/Renderer.jar`, 33 Packages einschließlich `fall` und `cliff`), `FStyle.jar`, JOGL mit Windows-Natives, `ojdbc11.jar`, `sdoapi/sdogr/sdoutl.jar`.
- **`Avatar.jar` fehlt** in `lib/` (Geiranger hatte es). → Frage F19.
- `docs/claude/` (dieser Prompt), `docs/status/` (leer, für die Statusbilder `Status_PhaseN.png`).
- `db/USER.sql`: `manage_user('Brocken', 'br')` auf PDBORCL mit Rolle DB_CONNECT. Das Schema heißt in Oracle damit `BROCKEN` (ohne Anführungszeichen groß). Das Passwort steht im Klartext in `USER.sql`: `USER.sql` und `db.properties` gehören ins `.gitignore` (es gibt noch keins).
- `db/db.properties.example` und `db/db.properties` (Schema Brocken, Dienst PDBORCL, das Passwort fragt das Studio beim ersten Verbinden ab). Der Text der Beispieldatei steht doppelt drin und verweist auf `docs/Anweisung.txt`, die es noch nicht gibt.
- `db/Role_Grantee.sql`: Rechte der Rolle DB_CONNECT als Referenz (Tabellen, Sichten, Sequenzen, Trigger, Prozeduren, Typen, Jobs, Synonyme, Materialized Views, DB-Links). `db/Objecte_nach_Schema.sql`: Objektliste der PDB als Referenz. `db/TableFormat.sql`: leer.

### Daten in `templates/`

| Datei | Inhalt | Taugt für |
|---|---|---|
| `rasters_COP30 (4).tar.gz` → `output_hh.tif` | Copernicus GLO-30, WGS84, Float32, **648 × 396 px zu 1″**, 10,52–10,70° O, 51,74–51,85° N, also rund **12,4 × 12,2 km**. In UTM 32N etwa E 604 700–617 400, N 5 733 500–5 745 400. Höhen 312,7 bis **1 141,8 m**, Maximum bei 10,615° O / 51,800° N: der Gipfel. | Hauptraster. Darin liegen Torfhaus (Westrand), Goetheweg, Schierke (Südost), die Spirale der Bahn. Drei Annen Hohne liegt genau am Ostrand (E ≈ 617 200), Wernigerode draußen. GLO-30 ist ein **Oberflächenmodell** (mit Wald). |
| `dgm200.utm32s.gridascii.zip` | BKG DGM200, ganz Deutschland, 200 m, ASCII-Raster 3 211 × 4 331 (rund 88 MB entpackt), ETRS89/UTM 32. Die `.prj` heißt „8stellen“, die Koordinaten im Kopf sind aber 6-stellig (`xllcenter 280000`): beim Import beachten. Stand der Ausgangsdaten Sachsen-Anhalt 2015–2018, Niedersachsen 1955–2018. | Fernring und Horizont: Vom Gipfel sieht man bei klarer Luft weit über 100 km [zu prüfen]. **Quellenvermerk Pflicht:** „© GeoBasis-DE / BKG 2026 (Daten verändert)“, in der App und in `docs/QUELLEN.md`. |
| `Harz_Aussengrenze.zip` | Shapefile, ETRS89/UTM 32 mit 8-stelligem Rechtswert (32…). **Es ist nicht der ganze Harz, sondern nur der niedersächsische Teil des Nationalparks Harz** (`NLP NDS 00002`, zwei Flächen, 157,7 km² und 0,74 km², Recht seit 11.08.2001), Rahmen E 591 200–613 400, N 5 725 100–5 751 200. Datenlizenz Deutschland. | Grenze des Nationalparks im Westen. **Der Gipfel (E ≈ 611 400) liegt im Teil Sachsen-Anhalt, der fehlt.** → Frage F21. |
| `heightmap_20261025195739_VZ3C.png` | 1 404 × 1 388 px, Graustufen in 8 bit (als RGBA), **ohne Georeferenz**, sehr weich; hellste Stelle unten in der Mitte. Herkunft (Heightmap-Generator?), Ausschnitt und Höhenmaßstab unbekannt. | Höchstens Vorschau oder Gegenprobe. Für das Gelände nicht geeignet (8 bit sind bei 800 m Spanne rund 3 m Stufen). → Frage F21. |

**Datenlücke:** Für den Kern um den Gipfel fehlt ein echtes Geländemodell. Niedersachsen gibt das DGM1 als OpenGeoData frei (OpenGeoData.NI). Sachsen-Anhalt bietet Geobasisdaten als Open Data an; ob das DGM1 dazugehört und in welcher Form, ist **zu prüfen** (LVermGeo, Geodatenportal). Der Gipfel liegt in Sachsen-Anhalt. → Frage F2.

---

## 3 · Das Vorbild (Fakten, auf die die Szene gebaut wird)

Quellen: Wikipedia (Brocken, Brockenbahn), Deutscher Wetterdienst, Nationalpark Harz. Im Werkbuch als Karten mit „belegt“ und „gestaltet [G]“.

**Berg und Klima**
- 1 141 m, Granit (Brockengranit), Blockfelder aus der Kaltzeit; Klippen am Gipfel: Teufelskanzel, Hexenaltar.
- Einziger Gipfel der Mittelgebirge über der natürlichen Baumgrenze; oben nur kleine Krüppelfichten.
- Jahresmittel 2,9 °C; Niederschlag rund 1 800 mm (1991–2020: 1 799 mm).
- **Nebel an bis zu 300 Tagen im Jahr**, Rekord 330 Tage (1958).
- Schneedecke über der Baumgrenze meist von September bis Mai; rund 120 Tage mit Schneefall; Rekordhöhe 380 cm (April 1970).
- **Rekordböe 263 km/h am 24.11.1984.** Rekord 29,7 °C (25.7.2019), −28,4 °C (1.2.1956).
- Wetterwarte des DWD im alten Turm, Klimareferenzstation seit 16.3.2010. Stündliche Daten im CDC des DWD [zu prüfen: Stationsnummer, Zeitraum].
- Brockengespenst: der Schatten des Betrachters auf einer Nebelwand, oft mit farbigem Ring (Glorie). Der Name kommt vom Brocken.

**Pflanzen und Wald**
- Hochmoore um den Gipfel: Wollgras, Sonnentau, Zwergbirke, Krähenbeere [weitere Arten zu prüfen]; Brockengarten seit 1890 (4 600 m², rund 1 600 Pflanzen der Gebirge).
- Fichtenwald, seit 2018 großflächig vom Borkenkäfer abgestorben: graue stehende Stämme, Bruch, liegendes Holz, junger Wald dazwischen [Anteile zu prüfen].

**Brockenbahn**
- Meterspur (1 000 mm), Teil der Harzer Schmalspurbahnen (HSB). Drei Annen Hohne (542 m) – Schierke – Brocken (1 125 m), **19 km**, größte Steigung **3,3 %**.
- Hinter dem Goetheweg umrundet die Bahn den Gipfel in einer **Spirale von rund 1½ Windungen** und kreuzt dabei noch einmal die Brockenstraße.
- Planmäßig nur Dampf, vor allem die Neubau-Dampfloks der Baureihe 99.23–24 [zu prüfen]; im Sommer bis 11 Zugpaare am Tag, im Winter bis 6; schnellster Zug 49 min.
- Eröffnet 27.3.1899; 1961–1991 Sperrgebiet (bis 1987 Güterzüge); seit 15.9.1991 wieder für alle.

**Gipfel**
- Brockenhaus mit Ausstellung, Hotel, Bahnhof, Wetterwarte, der alte Fernsehturm von 1936 (laut Wikipedia der erste der Welt, heute Hotel mit Aussicht), der Sendemast, der Brockengarten, der Rundweg [Lage aller Bauten zu prüfen].
- 1961–1989 militärisches Sperrgebiet mit Abhöranlagen von GRU und Stasi; eine Mauer aus 2 318 Betonteilen zu je 2,4 t und 3,60 m Höhe umschloss das Plateau; seit 3.12.1989 wieder offen.
- Die Brockenstraße von Schierke ist für private Autos gesperrt [zu prüfen]; man kommt mit der Bahn, zu Fuß (Goetheweg von Torfhaus, Eckerlochstieg, Brockenstraße) oder mit dem Rad.
- Sage: Walpurgisnacht, die Nacht zum 1. Mai, in der die Hexen auf dem Blocksberg tanzen; Goethe, Faust I.

---

## 4 · Raum, Ausschnitt und Daten (Vorschlag für die Fragen F1–F3)

- **Raumbezug:** UTM 32N, EPSG:25832, Meter über NHN. Ursprung am Gipfelstein [G], Nord = −Z. In der Datenbank die Lage zusätzlich in 8307 für QGIS.
- **Hauptraster:** GLO-30 (12,4 × 12,2 km), auf 25832 umprojiziert, mit dem Wald-Aufschlag des Oberflächenmodells verrechnet (wo der Wald steht, zieht man ab [G]).
- **Kern:** rund 3 × 3 km um den Gipfel: Plateau, Spirale der Bahn, Goetheweg, Moore, Klippen. Mit DGM1, falls es für Sachsen-Anhalt frei ist, sonst GLO-30 mit Feinform aus `cliff` und `ground` wie in Geiranger.
- **Fernring:** DGM200 bis rund 100 km [G], damit Horizont, Nebelmeer und die Fernsicht stimmen. Die Erdkrümmung und die Strahlenbrechung am Horizont rechnet schon `sky`.
- **Windgitter** (siehe `wind`): eigenes, gröberes Gitter über dem Hauptraster, zur Höhe hin gestreckt.

---

## 5 · Packages

Der Paketvertrag gilt wie in Geiranger: Kern nur JDK, `gl/` zusätzlich JOGL, `demo/` zusätzlich FStyle; README mit „Was es kann“, „Benutzung“, „Aufbau“, „Grenzen“; öffentliche Felder, Presets, `seed`, Qualitätsstufen; `check-packages` (`tools/PaketPruefung.java`) muss grün sein. **Neue Packages und Erweiterungen entstehen im Projekt Renderer** (`src/com/dan/…`), Brocken baut `Renderer.jar` neu (wie Geiranger F4 A).

### Neu

**`com.dan.wind` – Wind über Gelände** (Größe XL [G])
- Ein Windfeld über einem Höhenfeld. Es kennt kein Brocken und keine App: das Gelände und die Großwetterlage (Wind in der Höhe, Schichtung) kommen von außen.
- Vorschlag Verfahren: ein diagnostisches, massenkonsistentes Feld (wie MATHEW nach Sherman 1978, WindNinja): Startfeld aus dem logarithmischen Profil über der Rauigkeit (Wald, Moor, Fels) und dem Wind der Lage, dann so angepasst, dass keine Luft entsteht oder verschwindet (Poisson-Gleichung auf einem geländefolgenden Gitter). Mit der Schichtung lässt sich steuern, ob die Luft über den Berg geht oder um ihn herum.
- Dazu: Beschleunigung über der Kuppe (Prüfung gegen die Faustformel nach Jackson und Hunt [zu prüfen]), Ablösung und Wirbel im Lee als Näherung, Bündelung in Tälern und Sätteln, Böen als Feld, das mit dem Wind zieht (Prüfung gegen die Böigkeit der Lage aus `weather`), Hangwind am Tag und Kaltluft, die nachts abfließt und sich in Senken sammelt.
- Ausgabe: `windAt(x, y, z, t)`, `gustAt`, `turbulenceAt`, eine kleine 3D-Textur für die Shader, eine Schnittstelle `Wind` (nur JDK), die andere Packages ohne Abhängigkeit annehmen können.
- Rechnen auf dem Prozessor in eigenem Faden, nur bei Wechsel der Lage neu; die Böen laufen im Shader. Ziel: das Feld kostet auf der Grafikkarte unter 0,3 ms [G].
- Prüfung: Ein Windprofil am Gipfel gegen die Stundenwerte der DWD-Station bei passender Lage [zu prüfen, ob die Daten reichen].

**`com.dan.rime` – Raureif und Eisansatz** (Größe M [G])
- Raueis aus Nebeltröpfchen, die bei Frost am Objekt gefrieren: es wächst **gegen den Wind** als Fahnen. Die Menge hängt ab von Flüssigwasser im Nebel, Wind, Temperatur und Größe des Objekts (Fangkoeffizient) [Formeln zu prüfen, z. B. nach Makkonen]. Dazu Raureif aus klaren Nächten (sublimiert, fein, ohne Richtung) und Klareis bei Eisregen.
- Zustand je Objekt oder Fläche (Dicke, Richtung, Art), abbauend mit Sonne, Wärme und Wind; Lasten für Bäume (→ `forest`: Kronenbruch).
- Ein Shader-Baustein `rime.glsl` wie `weathering.glsl`, den fremde Shader einbinden (Fichten, Masten, Geländer, Schilder, Fels, Gleis, Oberleitung gibt es nicht).

**`com.dan.rail` – Schienenweg und Züge** (Größe L [G])
- Gleis nach Achse: Bögen mit Übergang, Überhöhung, Steigung, Spur (Meter- und Normalspur), Schwellen, Schotter, Bahnhöfe mit Weichen, Bahnübergänge (Kreuzung mit Wegen aus `road`, die `road` selbst nicht kennt). Das Gelände wird für die Trasse geformt wie bei `road` (Einschnitt, Damm).
- Zug: Lok und Wagen, Fahrdynamik aus Zugkraft, Steigungs- und Bogenwiderstand, Bremsen; Fahrplan als Daten.
- Dampflok: Auspuffschläge im Takt der Räder als Quelle für `smoke` (Dampf weiß, Rauch grau), Zylinderdampf beim Anfahren, Pfiff; Schneeschleuder als Kür.
- Kennt keinen Brocken: Achse, Fahrplan und Gelände kommen von außen.

### Erweitert (alles im Projekt Renderer, mit Hinweis „seit Brocken Phase N“ in der README)

| Package | Was dazukommt | Welche Grenze es schließt |
|---|---|---|
| `fog` | Nebel über Gelände: Wolke am Berg (Stau), Hangnebel, Kaltluftnebel in Senken, **Nebelmeer** bei Inversion mit dem Gipfel darüber; Wind aus `wind` | „Gelände kennt er nicht“, „sammelt sich nicht in Senken“ |
| `sky` | **Glorie** (nach Tröpfchengröße), **Brockengespenst** (Schatten des Auges auf der Nebelwand), Nebelbogen, Halos (22°, Nebensonnen) | — (neu) |
| `forest` | Totholz in Stadien (stehend grau, gebrochen, liegend), junger Wald, **Windflüchter** an der Baumgrenze, Windwurf und Bruch aus `wind`, Last aus `rime` | „Sturmbruch gibt es nicht“ |
| `season` | Verwehungen und Wechten aus `wind`, Schnee je Höhe | „Verwehungen verteilt der Benutzer“ |
| `cliff` | Granit (Wollsäcke, Blockmeere, Klippen), andere Farben und Klüfte als Gneis | „ein anderer Fels braucht andere Werte“ |
| `ground` | Biom „Brocken“: Hochmoor (Wollgras, Sonnentau, Torfmoos), Zwergsträucher (Heidelbeere, Krähenbeere, Besenheide), Blockfeld | — |
| `smoke` | Wind aus `wind` statt eines Profils, Böen in der Fahne, Dampf (verdunstet) neben Rauch | „Wind ist überall gleich“ |
| `reed` | Windkarte von außen (Wollgras, Gras) | „mit einer Windkarte von außen ließe sich das später ersetzen“ |
| `flock` | Aufwind am Hang aus `wind` statt am Zylinder; Arten am Brocken [zu prüfen, z. B. Kolkrabe, Ringdrossel] | „kein Strömungsfeld“ |
| `weather` | Lagen für das Mittelgebirge (Westlage mit Stau, Inversion mit Nebelmeer, Föhn im Lee [zu prüfen], Raueislage, Sommergewitter) und Höhe als Eingang (Temperatur, Wolkenbasis) | „Es gibt keinen Ort“ |
| `soundscape` | Wind am Gelände (Kanten, Mast, Abspannseile), Zug (Auspuffschläge, Pfiff, Bremsen, Schienenstoß), fallendes Raueis | „Wind trägt den Schall nur als Pegel“ (teilweise) |

### Aus `Renderer.jar` übernommen
`cloud`, `rain`, `lightning`, `road` (Brockenstraße, Wege), `timber` (nur, wo es passt), `figure` (Wanderer, Gruppen), `kino`, `store`, `weathering` (Masten, Geländer). `ocean`, `spray`, `tide`, `mudflat`, `seal`, `buoyancy`, `ice`, `fracture`, `wave` werden nicht gebraucht.

### Nebenbefund (Frage F19)
`wetglass`, `beacon` und `lighthouse.person` liegen noch im Projekt Lighthouse, `Avatar` als eigenes JAR. Geiranger konnte `lighthouse.person` darum nicht lesen. Vorschlag: in Phase 1 `wetglass` (Scheiben der Lok und des Brockenhauses im Nebel) ins Renderer holen; Avatar als `lib/Avatar.jar` wie in Geiranger.

---

## 6 · Was man in der Szene sehen soll (Rohstoff für die Abschnitte und Phasen)

- **Ein Tag mit Westlage:** Nebel an der Kuppe, der Wind treibt Fetzen über das Plateau; man sieht die Beschleunigung an Gras, Wollgras und Fahnen von Raueis; im Lee am Osthang ruhiger.
- **Inversion:** Morgens liegt ein Nebelmeer auf 600–800 m [G], der Gipfel in der Sonne, der Wurmberg und die fernen Kuppen als Inseln; steht man mit der Sonne im Rücken, erscheint das Brockengespenst mit Glorie. Zur Mittagszeit löst sich das Meer von unten auf.
- **Raueislage im Winter:** Raueisfahnen an den Krüppelfichten, am Geländer, am Mast; bei Sonne fällt Eis herab und klirrt.
- **Schnee:** Verwehungen hinter Klippen und Bauten, Wechten an Kanten, die Bahn mit Schneeschleuder (Kür).
- **Die Bahn:** von Schierke durch Wald und Totholz, die Spirale um den Gipfel, die Fahne aus Dampf und Rauch, die der Wind über die Kuppe reißt. Mitfahren auf dem offenen Wagen oder im Führerstand.
- **Der Wald:** von unten nach oben dichter Fichtenwald, Totholzflächen mit jungem Wald, dann Krüppelfichten als Windflüchter, dann Moor und Blockfeld.
- **Menschen:** Wanderer auf dem Goetheweg, Besucher am Gipfelstein, an der Teufelskanzel, am Bahnhof.
- **Klang:** Wind, der an Kanten und Seilen pfeift; die Lok von fern im Wald und nah; Raueis, das fällt; Vögel.
- **Walpurgisnacht** (Kür, Frage F18): 30. April, Nacht, Feuer und Menschen am Gipfel [zu prüfen, was dort heute stattfindet], die Sage vom Hexentanz als Stimme im Klang oder als Regiebuch, ähnlich den Glocken von Rungholt.

---

## 7 · Technik

- **Engine:** die GL-Engine aus Geiranger (jüngster Stand: Qualitätsstufen, Physik-Faden, Dauerlauf, Kino, Prüfung, Chronik, Startbild mit „Weiter wie zuletzt“), in Phase 1 entschlackt.
- **Fenster:** `com.dan.fframe.FFrame` aus FStyle, Bedienfeld mit Blöcken wie in den Vorgängern (KAMERA, ZEIT, WETTER, WIND, NEBEL, BAHN, JAHR, KLANG, REGIEBÜCHER, DATEN, TECHNIK); maximiert starten.
- **Rechner:** AMD Ryzen 7 7735HS, integrierte Radeon (2 GB), 64 GB RAM. Ziel 60 Bilder/s in der Standardstufe, nicht unter 30 [G]; Geiranger lief im Dauerlauf mit Ø 37,5. Budgets je Durchgang im Werkbuch angeben.
- **Prüfungen:** je Phase eine Prüfdatei (`pruefen_*.txt`) und ein Statusbild von dir in `docs/status`. `check-packages` im Projekt Renderer bleibt grün. Am Ende der Dauerlauf.
- **Blender:** wie Geiranger F8 A als Werkbank über MCP (Gelände gegen Karte und Fotos prüfen, Kameras setzen, Gipfelbauten vormodellieren), die Szene läuft in Java. → F20.

---

## 8 · Datenbank (Fragen F25 ff. erst zu Beginn der Phase „Oberfläche und Datenbank“)

Schema BROCKEN auf PDBORCL, Präfix `BR_` [G], Einrichter aus `com.dan.store`, Geometrien in 8307, Sichten für QGIS, Startdaten aus dem Code, „Ohne Datenbank“ mit Dateien in `daten/`. Mögliche Inhalte: Klima und Stundenwerte des DWD, Lagen, Windfelder je Lage (als Gitter oder Kennzahlen), Fahrplan der HSB, Bauten, Wege, Klippen, Chronik (Wetter, Nebelmeer, Gespenster, Raueis, Zugfahrten, Regieläufe).

---

## 9 · Vorschlag für die Phasen (das Werkbuch schneidet sie endgültig zu)

| Nr. | Phase | Kern |
|---|---|---|
| 0 | Konzept | dieses Werkbuch, Fragen F1–F24 |
| 1 | Gerüst | FFrame, Engine aus Geiranger, Marken, Ansichten, Hüllen von `wind`, `rime`, `rail` im Renderer, `.gitignore` |
| 2 | Gelände | GLO-30, (DGM1), DGM200-Fernring, CDLOD, Orte und Ansichten |
| 3 | Himmel und Wetter | `sky`, `cloud`, `weather` mit Klima „Brocken“ und Höhe |
| 4 | `wind` – Kern | Feld, Prüfung, Demo mit Pfeilen und Rauchfäden |
| 5 | `wind` in der Szene | Abfrage für `reed`, `smoke`, `flock`, `fog`, `forest`, `season`, `soundscape` |
| 6 | Nebel am Berg | Stau, Hangnebel, Kaltluft, Nebelmeer |
| 7 | Licht im Nebel | Brockengespenst, Glorie, Nebelbogen, Halos |
| 8 | Fels und Boden | Granitklippen und Blockfelder (`cliff`), Hochmoor (`ground`) |
| 9 | Wald | Fichte, Totholz, junger Wald, Windflüchter, Windwurf |
| 10 | `rime` | Raueis, Raureif, Klareis an Bäumen, Bauten, Fels |
| 11 | Das Jahr | Schnee je Höhe, Verwehung, Wechten, Schmelze |
| 12 | `rail` – Gleis | Trasse mit Spirale, Bahnhöfe Schierke und Brocken, Bahnübergang |
| 13 | Zug | Dampflok, Wagen, Fahrdynamik, Fahrplan, Dampf und Rauch, Mitfahren |
| 14 | Gipfel und Menschen | Brockenhaus, Hotel, Turm, Wetterwarte, Mast, Garten, Wege, Wanderer |
| 15 | Klang | Wind am Gelände, Bahn, Raueis, Vögel |
| 16 | Regie und Kino | drei Regiebücher (z. B. „Ein Tag im Nebel“, „Mit dem Zug auf den Brocken“, „Über dem Nebelmeer“), Kinomodus |
| 17 | Oberfläche und Datenbank | F25 ff., Schema BROCKEN |
| 18 | Schliff | Qualitätsstufen, Programmsymbol, Quellen, README, `wind`/`rime`/`rail` in `Renderer.jar`, Dauerlauf |

---

## 10 · Grundsatzfragen (Kandidaten F1–F24; A = Empfehlung)

| Nr. | Frage | A (Empfehlung) | B | C |
|---|---|---|---|---|
| F1 | Ausschnitt | Hauptraster GLO-30 12,4 × 12,2 km, Kern 3 × 3 km um den Gipfel, Fernring DGM200 bis rund 100 km | Nur GLO-30, Horizont gemalt | Größer bis Wernigerode (neue Kachel nötig) |
| F2 | Gelände im Kern | DGM1 Sachsen-Anhalt (und Niedersachsen für den Westen), falls frei; sonst GLO-30 mit Feinform | GLO-30 mit Feinform, ohne DGM1 | DGM200 und alles prozedural |
| F3 | Raumbezug | UTM 32N / 25832, Ursprung Gipfelstein, 8307 in der DB | Ursprung am Bahnhof Brocken | — |
| F4 | Packages | `Renderer.jar` als Bibliothek; neue Packages im Projekt Renderer | Kopie nach `src/` und Rückfluss am Ende | — |
| F5 | Engine | aus Geiranger | aus Rungholt | — |
| F6 | Verfahren `wind` | diagnostisch, massenkonsistent auf geländefolgendem Gitter | nur Faustformeln (Beschleunigung über Kuppe, Lee-Abschwächung) | echte Strömungsrechnung (Lattice-Boltzmann auf der Grafikkarte), teuer |
| F7 | Auflösung und Takt `wind` | rund 50 m waagerecht, 30 Schichten gestreckt, neu bei Wechsel der Lage, Böen im Shader [G] | 100 m, eine Schicht pro Höhe über Grund | 25 m im Kern, verschachtelt |
| F8 | Anschluss der Vorgänger | Schnittstelle `Wind` nur JDK; jedes Package nimmt sie optional, ohne sie bleibt alles wie bisher | jedes Package rechnet selbst | — |
| F9 | Raueis | eigenes Package `rime` | als Teil von `weathering` | als Teil von `ice` |
| F10 | Bahn | eigenes Package `rail` | als Wegart in `road` | — |
| F11 | Trasse | aus OSM (Lage) und Gelände (Höhe), Spirale geprüft gegen Karte [zu prüfen: Lizenz ODbL im Quellenvermerk] | aus einer Karte nachgezeichnet wie Geiranger | gestaltet |
| F12 | Zug und Fahrplan | Fahrplan der HSB [zu prüfen, Stand 2026], Lok 99.23–24 mit drei bis fünf Wagen | ein Zug im Kreis, ohne Fahrplan | — |
| F13 | Licht im Nebel | Glorie und Gespenst gerechnet (Mie nach Tröpfchengröße), Halos genähert | nur gemalte Ringe | ohne |
| F14 | Nebelmeer | aus Inversion in `weather` und Kaltluft aus `wind` | als feste Schicht mit Regler | — |
| F15 | Totholz | Stand heute nach Luftbildern [zu prüfen], Anteile je Höhe | ein Regler „Jahr des Waldes“ von 2017 bis heute | gesunder Wald |
| F16 | Gipfelbauten | Brockenhaus, Hotel, Turm, Wetterwarte, Mast, Bahnhof, Garten, nach Luftbild und Fotos [G] | nur Silhouetten | — |
| F17 | Zeitebene | nur heute | Schalter „1989“: Mauer, Abhöranlagen, Sperrgebiet, keine Wanderer | Schalter „1899“: Eröffnung der Bahn |
| F18 | Walpurgisnacht | als Regiebuch und Stimme im Klang (Kür, wie die Glocken von Rungholt) | nur als Datum im Kalender | ohne |
| F19 | Menschen und Hilfs-Packages | `Avatar.jar` nach `lib/` für Nahes, `figure` für Gruppen; `wetglass` aus Lighthouse ins Renderer | nur `figure` | — |
| F20 | Blender | Werkbank über MCP (Prüfen, Kameras, Bauten vorformen) | Geometriequelle für die Bauten | ohne |
| F21 | Nationalparkgrenze und Heightmap-PNG | Grenze Sachsen-Anhalt nachliefern oder aus Open Data holen; das PNG nur als Vorschau, wenn du Herkunft und Ausschnitt nennst | Grenze nur NI zeigen | beides weglassen |
| F22 | Klimadaten | DWD-Station Brocken (CDC, Stunden- und Tageswerte) [zu prüfen] als Klima und zum Prüfen von `wind` | Klima nur aus den Monatsmitteln | — |
| F23 | Schema und Präfix | BROCKEN mit `BR_` | ohne Präfix | — |
| F24 | Programmsymbol | `Brocken.ico`, gemalt: Kuppe über dem Nebelmeer mit Dampffahne | Gespenst mit Glorie | — |

---

## 11 · Risiken (für den Abschnitt „Risiken“)

- **Kosten des Nebels:** Nebel über Gelände, Nebelmeer, Wolken und Glorie zugleich auf der Radeon. → halbe Auflösung, Rauschen aus 3D-Texturen, Qualitätsstufen ab Phase 6.
- **`wind` falsch, aber schön:** Ein Feld, das gut aussieht und physikalisch nicht stimmt. → Prüfung gegen Faustformeln und DWD-Werte, Demo mit Rauchfäden.
- **Daten für den Kern:** Kein DGM1 für Sachsen-Anhalt → GLO-30 mit Feinform; der Wald im Oberflächenmodell muss heraus.
- **Spirale der Bahn im 1″-Raster** nicht zu sehen (wie die Kehren in Geiranger) → Achse aus OSM oder Karte, Einschnitt und Damm im Feinraster.
- **Viele Rückflüsse:** acht Packages bekommen eine Abfrage. → nur optional, Vorgänger bauen unverändert; Prüfung in allen Projekten am Ende.

---

## 12 · Ausblick (eigener Abschnitt am Ende des Werkbuchs)

Die drei übrigen Vorschläge vom 25.10.2026. Für jeden: Leitidee, neue Packages, was die übrigen davon haben, Umfang, Risiko. Darstellung wie die Ideenkarten (`.ideas`).

1. **Rotes Kliff, Sylt – das Projekt der Brandung.** Der Weststrand bei Kampen: Kliff, Sandbänke, Brandungsrinnen, Dünen mit Strandhafer, der Leuchtturm; bei Sturmflut bricht das Kliff ab. Neu: **`surf`** (Höherwerden im Flachen, Beugung an Bänken, Brechen schäumend oder überschlagend nach der Iribarren-Zahl, Weißwasserwalze, Auf- und Rücklauf auf nassem Sand mit `mudflat`, Rückstrom) und **`dune`** (Flugsand nach Bagnold, wandernde Dünen, Windrippel, Sandfahnen; Strandhafer aus `reed` bremst) – **`dune` braucht `wind` aus Brocken.** Erweitert: `cliff` (Geschiebelehm, Kaolinsand), `fracture` (Abbruch nach Unterspülung). Daten: Küstenhöhen der BAW wie in Rungholt [zu prüfen], DGM1 Schleswig-Holstein. Rückfluss: Thunder und Lighthouse bekommen echte Brandung. Umfang wie Rungholt.
2. **Kieler Förde, Windjammerparade – das Projekt des Segels.** Rahsegler, Schoner und Jollen, Laboe am Ausgang, Zuschauer an der Kiellinie, Feuerwerk. Neu: **`cloth`** (Tuch als Simulation, Position-Based Dynamics: Segel, Flaggen, Planen, Wäsche; Flaggen auch für Geiranger und Haithabu), **`sail`** (Segelkraft aus dem scheinbaren Wind, Takelage, Krängung), **`firework`** (Raketen und Sterne als Formel aus Nummer und Zeit wie `rain` und `spray`, Licht als Lichtpunkte wie `lightning`, Knall mit Laufzeit über `soundscape`, Rauch aus `smoke`). Erweitert: `buoyancy` mit Ruder, Antrieb und Ausweichregeln nach der KVR. Risiko: am schwersten für die Radeon.
3. **Lofoten in der Polarnacht – das Projekt des Lichts.** Reine oder Hamnøy im Dezember: rote Rorbuer auf Pfählen, Stockfischgestelle, Schnee, schwarzes Meer, Polarlicht; Fortsetzung von Geiranger. Neu: **`aurora`** (Bögen, Vorhänge, Korona; Grün 557,7 nm unten, Rot 630 nm oben; Stärke nach Kp; Bewegung als Welle entlang des Bogens), **`house`** (gestrichene Holzhäuser mit Fenstern, Licht innen, Laternen; die Lösung aus Geiranger herausgelöst), viele Lichter mit Schatten (Grenze von `hearth`). Erweitert: `season` (Verwehungen aus `wind`).

Reihenfolge-Empfehlung im Ausblick: Brocken → Rotes Kliff (nutzt `wind`) → Lofoten oder Kieler Förde.

---

## 13 · Quellen (Startliste für Abschnitt 14 des Werkbuchs)

- Wikipedia: Brocken; Brockenbahn (de/en)
- Deutscher Wetterdienst: Klimareferenzstation Brocken; Climate Data Center (CDC) [zu prüfen]
- Nationalparkverwaltung Harz [zu prüfen]
- Copernicus DEM GLO-30 (über OpenTopography): `templates/rasters_COP30 (4).tar.gz`
- BKG: DGM200 (© GeoBasis-DE / BKG, Daten verändert): `templates/dgm200.utm32s.gridascii.zip`
- Nationalparkgrenze Niedersachsen (Datenlizenz Deutschland): `templates/Harz_Aussengrenze.zip`
- OpenGeoData.NI (DGM1 Niedersachsen); Geodatenportal Sachsen-Anhalt (Open Data) [zu prüfen]
- Harzer Schmalspurbahnen: Fahrplan [zu prüfen]
- Fachliches: Sherman 1978 (MATHEW), WindNinja; Jackson & Hunt 1975; Makkonen (Eisansatz); Bagnold (für `dune` im Ausblick)

---

## 14 · Auftrag an Claude beim Erstellen des Werkbuchs

1. Diesen Prompt lesen, dazu das Geiranger Werkbuch als Vorlage für Aufbau und Stil.
2. Fakten „zu prüfen“ vor dem Schreiben kurz nachschlagen, wo es schnell geht; sonst so markieren.
3. Das Werkbuch als Artefakt „Brocken Werkbuch“ anlegen: Kopf mit Stimmungsskizze, Stand (Phase 0, Fragen offen), Abschnitte 1–14 nach dem Muster von Geiranger, Ausblick am Ende.
4. Grundsatzfragen F1–F24 offen lassen (Pill „offen“), A als Empfehlung markieren.
5. Keinen Code und keine Dateien im Projekt anlegen. Nach „alle A“ (oder anderen Antworten) und „Phase 1, los!“ beginnt der Bau.
