#!/usr/bin/env python3
"""Erzeugt die grobe Weltkarte und die Europakarte der App (ADR 0121 §11): Länder, Fernstraßen, Flüsse, Seen, Städte.

Quelle: Natural Earth (naturalearthdata.com), gemeinfrei (public domain). Dateien, geladen am 19.11.2026 von
https://naciscdn.org/naturalearth/, mit SHA-256 der Zip-Datei:

    50m/cultural/ne_50m_admin_0_countries        5fed433373581fa648920435f937d95f2d3c0200e067409c6478dcdf1b853139
    10m/cultural/ne_10m_roads                    880b235383a1e1ccf33f4ea6f82b6755a2ffbce07e3974e412b43e88fe954ae9
    10m/physical/ne_10m_rivers_lake_centerlines  ded71b01870855ccfe19b51f2ec14c9bb48fae23c0e9f3c11974d426433b5c38
    10m/physical/ne_10m_lakes                    0803a06f9c3cb4671d89b68c48b142aad9366ba40f665245e12a913fbc61722a
    10m/cultural/ne_10m_populated_places         cd149186f03d2603e0410da399b980a4357d0ac32d3a2305a49ed3dffcc41d7b

Aufruf (nur Python-Standardbibliothek), mit dem Ordner, in dem die Zip-Dateien je in einen Ordner ihres Namens
entpackt sind:

    python3 scripts/worldmap.py <ordner> shared/src/commonMain/composeResources/files/regions

Schreibt `world.amp` (Länder als Flächen, Ländernamen, große Städte der Welt) und `europe.amp` (Fernstraßen, Flüsse,
Seen und Städte Europas ohne Deutschland; Deutschland steht genauer in `base.amp` aus `scripts/maptiles.py`).
Das Format ist das von `scripts/maptiles.py`. Namen stehen als „Deutsch<Tab>Englisch“, wenn sie sich unterscheiden.
Arten zusätzlich: 22 Land (ein Punkt, der Name), 30 Landfläche. Städte mit ihrem Rang wie in `scripts/maptiles.py`.
"""

import struct
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from maptiles import chain, length_km, place_kind, simplify, write, ORDER  # noqa: E402

ORDER.update({30: 0, 22: 13})

EUROPE = (34.0, -11.5, 71.5, 45.0)  # south, west, north, east


def clean(v):
    return v.replace("\x00", "").strip()


def read_dbf(path):
    with open(path, "rb") as f:
        head = f.read(32)
        count, head_len, row_len = struct.unpack("<IHH", head[4:12])
        fields = []
        while True:
            d = f.read(32)
            if d[0] == 0x0D:
                break
            fields.append((d[:11].split(b"\0")[0].decode().upper(), d[16]))
        f.seek(head_len)
        for _ in range(count):
            row = f.read(row_len)
            pos, out = 1, {}
            for name, size in fields:
                out[name] = clean(row[pos:pos + size].decode("utf-8", "replace"))
                pos += size
            yield out


def read_shp(path):
    """Parts of every record as lists of (lat, lon); a point is one part with one entry."""
    data = Path(path).read_bytes()
    pos = 100
    while pos < len(data):
        length = struct.unpack(">i", data[pos + 4:pos + 8])[0] * 2
        rec = data[pos + 8:pos + 8 + length]
        pos += 8 + length
        kind = struct.unpack("<i", rec[:4])[0]
        if kind == 0:
            yield []
        elif kind in (1, 11, 21):
            x, y = struct.unpack("<2d", rec[4:20])
            yield [[(y, x)]]
        else:
            parts, points = struct.unpack("<ii", rec[36:44])
            starts = list(struct.unpack("<%di" % parts, rec[44:44 + 4 * parts])) + [points]
            base = 44 + 4 * parts
            xy = struct.unpack("<%dd" % (points * 2), rec[base:base + 16 * points])
            yield [[(xy[2 * i + 1], xy[2 * i]) for i in range(starts[p], starts[p + 1])] for p in range(parts)]


def layer(folder, name):
    d = Path(folder) / name
    return zip(read_dbf(d / (name + ".dbf")), read_shp(d / (name + ".shp")))


def ring_area(ring):
    s = 0.0
    for i in range(len(ring) - 1):
        s += ring[i][1] * ring[i + 1][0] - ring[i + 1][1] * ring[i][0]
    return abs(s) / 2


def inside(rings, lat, lon):
    odd = False
    for ring in rings:
        for (alat, alon), (blat, blon) in zip(ring, ring[1:]):
            if (alat > lat) != (blat > lat) and lon < (blon - alon) * (lat - alat) / (blat - alat) + alon:
                odd = not odd
    return odd


def in_europe(lat, lon):
    return EUROPE[0] <= lat <= EUROPE[2] and EUROPE[1] <= lon <= EUROPE[3]


def both(de, en):
    de, en = de or en, en or de
    return de if de == en else de + "\t" + en


def main(src, dst):
    dst = Path(dst)
    stamp = int(time.time())
    world, europe, germany = [], [], []

    countries = []
    for row, parts in layer(src, "ne_50m_admin_0_countries"):
        if row["ISO_A2"] == "AQ" or not parts:
            continue
        if row["ISO_A2"] == "DE":
            germany = parts
        biggest = max(ring_area(p) for p in parts)
        for ring in parts:
            # Small islands are left out at this scale, unless they are all a country has.
            if ring_area(ring) < 0.02 and ring_area(ring) < biggest:
                continue
            simple = simplify(ring, 1800)
            if len(simple) >= 4:
                world.append((30, None, simple))
        try:
            countries.append((float(row["POP_EST"] or 0), both(row["NAME_DE"], row["NAME"]), float(row["LABEL_Y"]), float(row["LABEL_X"])))
        except ValueError:
            pass
    # Small before large: the app places names from the end.
    for _, name, lat, lon in sorted(countries):
        world.append((22, name, [(lat, lon)]))

    places = []
    for row, parts in layer(src, "ne_10m_populated_places"):
        if not parts:
            continue
        lat, lon = parts[0][0]
        pop = int(float(row["POP_MAX"] or 0))
        capital = row["ADM0CAP"].startswith("1")
        name = both(row["NAME_DE"], row["NAME_EN"] or row["NAME"])
        if in_europe(lat, lon):
            if row["ADM0_A3"] != "DEU" and (pop >= 100_000 or capital):
                places.append((pop, place_kind({"place": "city", "name": name, "population": str(pop)}), name, lat, lon, europe))
        elif pop >= 2_000_000 or (capital and pop >= 300_000):
            places.append((pop, place_kind({"place": "city", "name": name, "population": str(pop)}), name, lat, lon, world))
    for pop, kind, name, lat, lon, into in sorted(places, key=lambda p: p[0]):
        into.append((kind, name, [(lat, lon)]))

    def outside_germany(pts):
        lat, lon = pts[len(pts) // 2]
        return in_europe(lat, lon) and not inside(germany, lat, lon)

    roads = {1: [], 2: []}
    for row, parts in layer(src, "ne_10m_roads"):
        kind = {"Major Highway": 1, "Beltway": 1, "Bypass": 1, "Secondary Highway": 2}.get(row["TYPE"])
        if kind:
            roads[kind] += [p for p in parts if len(p) >= 2 and outside_germany(p)]
    for kind, lines in roads.items():
        for line in chain(lines):
            if length_km(line) >= 3:
                europe.append((kind, None, simplify(line, 400)))

    rivers = {}
    for row, parts in layer(src, "ne_10m_rivers_lake_centerlines"):
        if int(float(row["SCALERANK"] or 99)) <= 8 and row["FEATURECLA"] == "River":
            rivers.setdefault(row["NAME"], []).extend(p for p in parts if len(p) >= 2 and outside_germany(p))
    for name, lines in rivers.items():
        for line in chain(lines):
            europe.append((11, name or None, simplify(line, 400)))

    for row, parts in layer(src, "ne_10m_lakes"):
        if int(float(row["SCALERANK"] or 99)) <= 6:
            for ring in parts[:1]:
                if in_europe(*ring[0]) and ring_area(ring) > 0.01:
                    europe.append((10, row["NAME"] or None, simplify(ring, 400)))

    print("world.amp  %d Objekte, %d Bytes" % (len(world), write(dst / "world.amp", "world", stamp, world)))
    print("europe.amp %d Objekte, %d Bytes" % (len(europe), write(dst / "europe.amp", "europe", stamp, europe)))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
