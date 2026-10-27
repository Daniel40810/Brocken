-- =====================================================================================================
-- Brocken · Datenbank · 03_sichten.sql
-- Sieben Sichten: der Brocken als eine Ebene für QGIS (Bauten, Felsen, Orte, Wege, Bahnhöfe, Gleis, Boden,
-- Merkpunkte), die Tageskarte (alles eines Szenentags: Gespenster, Nebelmeer als Höhenlinie, Zugfahrten auf dem
-- Gleis, Merkpunkte), der Fahrplan mit dem, was die Chronik über ihn weiß, das Jahr je Szenentag gegen das Klima,
-- die Gespenster mit der Lage der Stunde, die Befunde mit ihren Quellen und die letzte Sitzung für „Weiter wie
-- zuletzt“. Längen und Flächen auf dem Ellipsoid, Toleranz 5 cm. CREATE OR REPLACE: beliebig oft wiederholbar.
-- =====================================================================================================

-- Der Brocken als eine Ebene für QGIS. OBJEKT_ID = Ebene × 10^9 + ID; Stil nach EBENE und ART. QGIS teilt die
-- Ebene nach Geometrieart (Punkte, Linien, Flächen).
CREATE OR REPLACE VIEW BR_BROCKEN_V AS
SELECT 1000000000 + b.ID AS OBJEKT_ID, 'BAU' AS EBENE, b.ART, b.NAME, b.TRAUFE_M + NVL(b.DACH_HOEHE_M, 0) AS WERT, b.GRUNDRISS AS GEOM
FROM BR_BAU b
UNION ALL
SELECT 2000000000 + f.ID, 'FELS', f.ART, f.NAME, f.HOEHE_M, f.LAGE
FROM BR_FELS f
UNION ALL
SELECT 3000000000 + o.ID, 'ORT', o.ART, o.NAME, o.NHN_M, o.LAGE
FROM BR_ORT o
UNION ALL
SELECT 4000000000 + w.ID, 'WEG', w.ART, w.NAME, w.LAENGE_M, w.LINIE
FROM BR_WEG w
UNION ALL
SELECT 5000000000 + h.ID, 'BAHNHOF', 'BAHNHOF', h.NAME, h.HOEHE_M, h.LAGE
FROM BR_BAHNHOF h
UNION ALL
SELECT 6000000000 + s.ID, 'GLEIS', s.ART, s.NAME, s.LAENGE_M, s.LINIE
FROM BR_STRECKE s WHERE s.ART IN ('GLEIS', 'AUSWEICHE')
UNION ALL
SELECT 7000000000 + d.ID, 'BODEN', d.ART, d.NAME, d.FLAECHE_M2, d.FLAECHE
FROM BR_BODEN d
UNION ALL
SELECT 8000000000 + m.ID, 'MERKPUNKT', 'EIGEN', m.NAME, m.NHN_M, m.LAGE
FROM BR_MERKPUNKT m;

-- Die Tageskarte: der Brocken (ohne Tag, immer da), dazu je Szenentag die Gespenster, das Nebelmeer als Höhenlinie
-- seiner Obergrenze (auf 50 m gerundet), jede Zugfahrt auf dem Gleis und die Merkpunkte. QGIS: Ebene
-- BR_TAGESKARTE_V, Schlüssel OBJEKT_ID, Filter  "TAG" = '2026-10-21' OR "TAG" IS NULL , Stil nach EBENE und ART.
CREATE OR REPLACE VIEW BR_TAGESKARTE_V AS
SELECT r.OBJEKT_ID, r.EBENE, CAST(NULL AS VARCHAR2(10)) AS TAG, r.ART, r.NAME AS TEXT, r.WERT, r.GEOM
FROM BR_BROCKEN_V r WHERE r.EBENE <> 'MERKPUNKT'
UNION ALL
SELECT 10000000000 + g.ID, 'GESPENST', g.SZENE_TAG, CASE WHEN g.GLORIE = 1 THEN 'GLORIE' ELSE 'SCHATTEN' END, g.SZENE_TEXT, g.SONNENHOEHE_GRAD, g.LAGE
FROM BR_GESPENST g
UNION ALL
SELECT 11000000000 + n.ID, 'NEBELMEER', n.SZENE_TAG, n.LAGE, n.SZENE_TEXT || ' bis ' || NVL(n.ENDE_TEXT, 'Ende der Sitzung'), n.OBEN_M, h.LINIE
FROM BR_NEBELMEER n
JOIN BR_HOEHENLINIE h ON h.HOEHE_M = ROUND(n.OBEN_M / 50) * 50
UNION ALL
SELECT 12000000000 + z.ID, 'ZUGFAHRT', z.SZENE_TAG, z.RICHTUNG, z.ZUG || ' · ist ' || z.IST_AB || '–' || z.IST_AN, z.VERSPAETUNG_MIN, s.LINIE
FROM BR_ZUGFAHRT z
CROSS JOIN (SELECT LINIE FROM BR_STRECKE WHERE ART = 'GLEIS' AND NR = 0) s
UNION ALL
SELECT 13000000000 + m.ID, 'MERKPUNKT', m.SZENE_TAG, 'EIGEN', m.NAME || NVL2(m.NOTIZ, ': ' || m.NOTIZ, ''), m.NHN_M, m.LAGE
FROM BR_MERKPUNKT m;

-- Der Fahrplan mit Fahrzeit und dem, was die Chronik über jeden Zug weiß (Fahrten, mittlere Verspätung, Pfiffe).
CREATE OR REPLACE VIEW BR_FAHRPLAN_V AS
SELECT f.ID,
       f.RICHTUNG,
       f.ZUG,
       f.ABFAHRT,
       f.ANKUNFT,
       f.FAHRZEIT_MIN,
       f.VERKEHRSTAGE,
       f.GUELTIG_AB,
       f.GUELTIG_BIS,
       f.JAHR,
       f.AKTIV,
       q.KURZ AS QUELLE,
       (SELECT COUNT(*) FROM BR_ZUGFAHRT z WHERE z.ZUG = f.ZUG) AS FAHRTEN,
       (SELECT ROUND(AVG(z.VERSPAETUNG_MIN), 1) FROM BR_ZUGFAHRT z WHERE z.ZUG = f.ZUG) AS VERSPAETUNG_MITTEL_MIN,
       (SELECT ROUND(AVG(z.PFIFFE), 1) FROM BR_ZUGFAHRT z WHERE z.ZUG = f.ZUG) AS PFIFFE_MITTEL,
       f.HERKUNFT
FROM BR_FAHRPLAN f
LEFT JOIN BR_QUELLE q ON q.ID = f.QUELLE_ID;

-- Das Jahr je Szenentag aus der Chronik gegen das Klima der Wetterwarte (Monatsmittel).
CREATE OR REPLACE VIEW BR_JAHR_V AS
SELECT t.SZENE_TAG AS TAG,
       TO_NUMBER(SUBSTR(t.SZENE_TAG, 6, 2)) AS MONAT,
       t.SCHNEE_GIPFEL_CM,
       t.SCHNEEGRENZE_M,
       t.SCHNEE_STUFEN,
       t.TEMPERATUR_MIN_C,
       t.TEMPERATUR_MAX_C,
       k.TEMPERATUR_C AS KLIMA_TEMPERATUR_C,
       t.WIND_MAX_MS,
       k.WIND_MS AS KLIMA_WIND_MS,
       t.NEBELSTUNDEN,
       t.STUNDEN,
       t.RAUEIS_MAST_MM,
       t.LAGEN,
       t.SITZUNG_ID
FROM BR_TAG t
LEFT JOIN BR_KLIMA_MONAT k ON k.MONAT = TO_NUMBER(SUBSTR(t.SZENE_TAG, 6, 2));

-- Die Gespenster mit Monat, Stunde und der Lage dieser Stunde aus der Chronik.
CREATE OR REPLACE VIEW BR_GESPENST_V AS
SELECT g.ID,
       g.SZENE_TEXT,
       g.SZENE_TAG,
       TO_NUMBER(SUBSTR(g.SZENE_TAG, 6, 2)) AS MONAT,
       g.SONNENHOEHE_GRAD,
       g.SONNE_AZIMUT_GRAD,
       g.GLORIE,
       g.NEBELBOGEN,
       g.DAUER_S,
       g.NHN_M,
       g.BLICK_GRAD,
       (SELECT MAX(s.LAGE_NAME) KEEP (DENSE_RANK LAST ORDER BY s.SZENE_MS) FROM BR_STUNDE s
         WHERE s.SZENE_MS <= g.SZENE_MS AND s.SZENE_MS > g.SZENE_MS - 3600000) AS LAGE_NAME,
       (SELECT MAX(s.MEER_OBEN_M) KEEP (DENSE_RANK LAST ORDER BY s.SZENE_MS) FROM BR_STUNDE s
         WHERE s.SZENE_MS <= g.SZENE_MS AND s.SZENE_MS > g.SZENE_MS - 3600000) AS MEER_OBEN_M,
       g.TEXT,
       g.SITZUNG_ID,
       g.LAGE
FROM BR_GESPENST g;

-- Die Befunde mit ihren Quellen.
CREATE OR REPLACE VIEW BR_BEFUND_V AS
SELECT b.ID,
       b.THEMA,
       b.GEGENSTAND,
       b.BEFUND,
       b.TEXT,
       (SELECT LISTAGG(q.KURZ, ', ') WITHIN GROUP (ORDER BY q.KURZ)
          FROM BR_BEFUND_QUELLE x JOIN BR_QUELLE q ON q.ID = x.QUELLE_ID WHERE x.BEFUND_ID = b.ID) AS QUELLEN,
       b.HERKUNFT
FROM BR_BEFUND b;

-- Die letzte Sitzung mit Zustand (für „Weiter wie zuletzt“ und zum Nachsehen).
CREATE OR REPLACE VIEW BR_LETZTE_SITZUNG_V AS
SELECT s.ID,
       s.BEGINN,
       s.ENDE,
       s.SAUBER_BEENDET,
       s.APP_VERSION,
       s.QUALITAET,
       s.SZENE_TEXT,
       s.ZEITRAFFER,
       s.LAGE,
       r.NAME AS REGIEBUCH,
       s.REGIE_SEKUNDE,
       s.KAMERA,
       s.ZUSTAND_ZEIT
FROM BR_SITZUNG s
LEFT JOIN BR_REGIEBUCH r ON r.ID = s.REGIEBUCH_ID
WHERE s.ZUSTAND IS NOT NULL
ORDER BY s.ZUSTAND_ZEIT DESC NULLS LAST, s.ID DESC
FETCH FIRST 1 ROWS ONLY;

-- Ende 03_sichten.sql
