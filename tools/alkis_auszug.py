#!/usr/bin/env python3
"""
alkis_auszug.py – ALKIS-Auszug für die Szene Brocken (Phase 2, nur Python 3 mit der Standardbibliothek).

Liest aus einem ALKIS-Geopaket (templates/alkis_kreis_15085.gpkg, Landkreis Harz, oder templates/alkis_ST.gpkg) die
tatsächliche Nutzung und die Gebäude im Hauptraster mit Rand und schreibt sie in ein einfaches Binärformat, das
tools.GelaendeImport (Java, ohne SQLite-Treiber) liest:

    daten/alkis/nutzung.bin   je Fläche: Code der Nutzungsart (art_code), Name, Ringe
    daten/alkis/gebaeude.bin  je Gebäude: Code der Funktion, Funktion und Name, Ringe

Format (little endian): b"BRAL", int Version 1, int Anzahl; je Objekt: int Code, short Länge + UTF-8-Text,
int Ringe; je Ring: int Punkte, dann Punkte als double Ost, double Nord (EPSG:25832).

Aufruf im Projektordner:  python tools/alkis_auszug.py [geopaket]
SQLite schreibt hier nichts ins Projekt (der Auszug geht ohne Zwischendatei), darum läuft es auch in Ordnern, in denen
nichts gelöscht werden darf. Quellenvermerk: © GeoBasis-DE/LVermGeo ST, dl-de/by-2-0 (Daten verändert).
"""
import os
import re
import sqlite3
import struct
import sys

# Hauptraster mit 500 m Rand (EPSG:25832)
BBOX = (604200, 5733000, 617900, 5745900)


def wkb_rings(b, o=0):
    """Ringe einer WKB-Geometrie (Polygon, MultiPolygon, auch mit Z/M): Liste von Listen (x, y); dazu das Ende."""
    bo = '<' if b[o] == 1 else '>'
    t = struct.unpack_from(bo + 'I', b, o + 1)[0]
    o += 5
    dims = 2
    base = t % 1000
    if t >= 1000:
        dims = 3 if t // 1000 in (1, 2) else 4
    if t & 0x80000000 or t & 0x40000000:  # EWKB-Z/M
        dims = 2 + (1 if t & 0x80000000 else 0) + (1 if t & 0x40000000 else 0)
        base = t & 0xFFFF
    rings = []
    if base == 3:  # Polygon
        n = struct.unpack_from(bo + 'I', b, o)[0]
        o += 4
        for _ in range(n):
            m = struct.unpack_from(bo + 'I', b, o)[0]
            o += 4
            pts = []
            for k in range(m):
                v = struct.unpack_from(bo + 'd' * dims, b, o)
                pts.append((v[0], v[1]))
                o += 8 * dims
            rings.append(pts)
    elif base in (6, 7):  # MultiPolygon, GeometryCollection
        n = struct.unpack_from(bo + 'I', b, o)[0]
        o += 4
        for _ in range(n):
            r, o = wkb_rings(b, o)
            rings.extend(r)
    else:
        return [], o  # Punkte und Linien braucht der Auszug nicht
    return rings, o


def gpkg_rings(blob):
    """Ringe aus einer GeoPackage-Geometrie (Kopf „GP“, Hülle nach den Flags, dann WKB)."""
    if blob is None or blob[:2] != b'GP':
        return []
    flags = blob[3]
    env = (flags >> 1) & 7
    off = 8 + {0: 0, 1: 32, 2: 48, 3: 48, 4: 64}.get(env, 0)
    return wkb_rings(blob, off)[0]


def schreiben(pfad, objekte):
    with open(pfad, 'wb') as f:
        f.write(b'BRAL')
        f.write(struct.pack('<ii', 1, len(objekte)))
        for code, text, rings in objekte:
            t = (text or '').encode('utf-8')
            f.write(struct.pack('<ih', code, len(t)))
            f.write(t)
            f.write(struct.pack('<i', len(rings)))
            for r in rings:
                f.write(struct.pack('<i', len(r)))
                f.write(struct.pack('<' + 'd' * (2 * len(r)), *[c for p in r for c in p]))


def auszug(gpkg, ziel):
    db = sqlite3.connect('file:' + gpkg + '?mode=ro', uri=True)
    os.makedirs(ziel, exist_ok=True)
    tabellen = {r[0] for r in db.execute("select name from sqlite_master where type = 'table'")}
    for tabelle, code, text in (('nutzung', 'art_code', "n.art"), ('gebaeude', 'funktion_code', "coalesce(n.funktion, '') || '|' || coalesce(n.name, '')")):
        if 'rtree_' + tabelle + '_geom' in tabellen:
            q = (f"select n.{code}, {text}, n.geom from {tabelle} n join rtree_{tabelle}_geom r on n.fid = r.id "
                 "where r.maxx >= ? and r.minx <= ? and r.maxy >= ? and r.miny <= ?")
            zeilen = db.execute(q, (BBOX[0], BBOX[2], BBOX[1], BBOX[3]))
        else:  # ohne Raumindex (etwa ein schon geschnittener Auszug): alles lesen, unten nach der Lage filtern
            zeilen = db.execute(f"select n.{code}, {text}, n.geom from {tabelle} n")
        objekte = []
        for c, t, g in zeilen:
            rings = gpkg_rings(g)
            if not rings:
                continue
            xs = [p[0] for r in rings for p in r]
            ys = [p[1] for r in rings for p in r]
            if max(xs) < BBOX[0] or min(xs) > BBOX[2] or max(ys) < BBOX[1] or min(ys) > BBOX[3]:
                continue
            m = re.match(r'\s*(\d+)', str(c))   # auch „44001 FKT 8200“
            c = int(m.group(1)) if m else 0
            objekte.append((c, t, rings))
        pfad = os.path.join(ziel, tabelle + '.bin')
        schreiben(pfad, objekte)
        print(f'{tabelle}: {len(objekte)} Objekte, {sum(len(r) for _, _, rs in objekte for r in rs)} Punkte -> {pfad}')


if __name__ == '__main__':
    quelle = sys.argv[1] if len(sys.argv) > 1 else os.path.join('templates', 'alkis_kreis_15085.gpkg')
    if not os.path.isfile(quelle):
        sys.exit('ALKIS-Geopaket fehlt: ' + quelle)
    auszug(quelle, os.path.join('daten', 'alkis'))
