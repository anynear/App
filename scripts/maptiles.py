#!/usr/bin/env python3
"""Erzeugt die Kartendaten der Karte (ADR 0121 §11): die leichte Deutschlandkarte in der App und die Kacheln zum Nachladen.

Quelle: OpenStreetMap (© OpenStreetMap-Mitwirkende, Open Database License, https://www.openstreetmap.org/copyright).
Der Vermerk steht in der App unter „Über anynear“ bei der Lizenz und im README.md (Abschnitt Lizenz).

Eingabe ist die Antwort der Overpass-Schnittstelle (`out geom;`) oder, für ein ganzes Land, ein OpenStreetMap-Auszug
(`.osm.pbf`, z. B. von download.geofabrik.de). Aufruf (`base` und `tiles` nur mit der Python-Standardbibliothek, `pbf`
braucht zusätzlich das Paket `osmium`, `pip install osmium`):

    python3 scripts/maptiles.py base  <overpass.json> shared/src/commonMain/composeResources/files/regions/base.amp
    python3 scripts/maptiles.py tiles <overpass.json> <ordner> [süd west nord ost]
    python3 scripts/maptiles.py pbf   <auszug.osm.pbf> <ordner> [süd west nord ost]

`pbf`: dieselben Kacheln wie `tiles`, aus einem Auszug statt aus Overpass, mit begrenztem Arbeitsspeicher: Erst
schreibt es einen kleinen Auszug nur mit dem, was auf die Karte kommt (`<ordner>.work/filtered.osm.pbf`), dann liest
es ihn Objekt für Objekt, schneidet auf Kacheln und legt die Stücke je Region in eine Zwischendatei; zuletzt wird
Region für Region zu Kacheln. Im Speicher liegen nie mehr als die Stücke einer Region und ein Puffer. Flächen aus
Relationen (Seen, Wälder aus mehreren Wegen) setzt `osmium` zu Ringen zusammen; Löcher fallen weg. Der Ordner wird
vorher von alten Kacheln geleert, `<ordner>.work` danach gelöscht. Der Stand der Daten kommt aus dem Kopf des Auszugs.
Mit Rahmen werden nur Kacheln geschrieben, die ihn berühren (ohne: alle, in denen etwas liegt).

`base`: Autobahnen, große Flüsse und Städte für ganz Deutschland, kommt mit der App. Abfrage:

    [out:json][timeout:600];
    area["ISO3166-1"="DE"][admin_level=2]->.de;
    ( way["highway"~"^(motorway|trunk)$"](area.de); way["waterway"="river"](area.de);
      node["place"~"^(city|town)$"](area.de); );
    out geom;

`tiles`: schreibt je Kachel eine Datei `<z>-<x>-<y>` (Kacheln wie bei Webkarten, Mercator). Ebene 10 (etwa 25 km):
Hauptstraßen, Bahn, Flüsse, große Wasser- und Waldflächen, Orte. Ebene 12 (etwa 6 km): alle Straßen und Wege, Bäche,
kleine Flächen, Viertel. Was über eine Kachelgrenze reicht, wird dort geschnitten. Mit dem Rahmen werden nur Kacheln
geschrieben, die ganz in den abgefragten Daten liegen. Abfrage (Gebiet als `[bbox:süd,west,nord,ost]`):

    [out:json][timeout:240][bbox:50.015,8.472,50.227,8.800];
    ( way["highway"~"^(motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|
        tertiary_link|residential|unclassified|living_street|pedestrian|service|footway|path|cycleway|track|steps)$"];
      way["railway"~"^(rail|light_rail|subway|tram)$"]; way["waterway"~"^(river|stream|canal)$"];
      way["natural"~"^(water|wood)$"]; way["landuse"~"^(forest|meadow|grass|cemetery|allotments)$"];
      way["leisure"~"^(park|garden|pitch|sports_centre)$"]; relation["natural"="water"]; relation["landuse"="forest"];
      node["place"~"^(city|town|village|suburb|quarter|neighbourhood)$"]; );
    out geom;

Aufbau einer Datei (Zahlen als Varint, Vorzeichen im Zickzack, Koordinaten in 1e-5 Grad):

    "AM" 2, Schlüssel (Länge, Text: `base` oder `<z>-<x>-<y>`), Stand der Daten (Unix-Sekunden)
    Rahmen: Süd, West, Nord, Ost
    Zahl der Namen, je Name: Länge, UTF-8
    Zahl der Objekte, je Objekt: Art, Name + 1 (0 = ohne), Zahl der Punkte,
        erster Punkt (Breite, Länge), dann die Unterschiede zum Punkt davor

Arten: 1 Autobahn, 2 Hauptstraße, 3 Nebenstraße, 4 Wohnstraße, 5 Weg, 6 Bahn, 10 Wasserfläche, 11 Wasserlauf,
12 Wald, 13 Grün, 20 Ort, 21 Stadt (nur `base`). Die Objekte stehen in der Reihenfolge, in der sie gezeichnet werden.
"""

import calendar
import json
import math
import os
import re
import shutil
import sys
import time
from pathlib import Path

REGION, NEAR = 10, 12

ROADS = {
    "motorway": 1, "motorway_link": 1, "trunk": 1, "trunk_link": 1,
    "primary": 2, "primary_link": 2, "secondary": 2, "secondary_link": 2,
    "tertiary": 3, "tertiary_link": 3, "unclassified": 3,
    "residential": 4, "living_street": 4, "pedestrian": 4, "service": 4,
    "footway": 5, "path": 5, "cycleway": 5, "track": 5, "steps": 5,
}
GREEN = {"meadow", "grass", "cemetery", "allotments", "park", "garden", "pitch", "sports_centre"}
ORDER = {13: 1, 12: 2, 10: 3, 11: 4, 5: 5, 4: 6, 3: 7, 6: 8, 2: 9, 1: 10, 20: 11, 21: 12}


def kind_of(tags):
    if "highway" in tags:
        if tags["highway"] == "service" and "name" not in tags:
            return None
        if tags["highway"] in ("footway", "path", "steps") and tags.get("footway") in ("sidewalk", "crossing"):
            return None
        return ROADS.get(tags["highway"])
    if "railway" in tags:
        return None if tags.get("tunnel") == "yes" or tags.get("service") else 6
    if "waterway" in tags:
        return 11
    if tags.get("natural") == "water":
        return 10
    if tags.get("natural") == "wood" or tags.get("landuse") == "forest":
        return 12
    if tags.get("landuse") in GREEN or tags.get("leisure") in GREEN:
        return 13
    return None


def is_area(kind):
    return kind in (10, 12, 13)


def mx(lon):
    return (lon + 180.0) / 360.0


def my(lat):
    r = math.radians(lat)
    return (1 - math.log(math.tan(r) + 1 / math.cos(r)) / math.pi) / 2


def lon_of(x):
    return x * 360.0 - 180.0


def lat_of(y):
    return math.degrees(2 * math.atan(math.exp(math.pi * (1 - 2 * y))) - math.pi / 2)


def simplify(points, tolerance_m):
    """Douglas-Peucker on (lat, lon); keeps both ends."""
    if len(points) < 3:
        return points
    tol = tolerance_m / 111_200.0
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    shrink = math.cos(math.radians(points[0][0]))
    while stack:
        a, b = stack.pop()
        ay, ax = points[a]
        by, bx = points[b]
        dx, dy = (bx - ax) * shrink, by - ay
        norm = math.hypot(dx, dy)
        best, at = 0.0, -1
        for i in range(a + 1, b):
            py, px = points[i]
            ex, ey = (px - ax) * shrink, py - ay
            d = abs(dy * ex - dx * ey) / norm if norm > 0 else math.hypot(ex, ey)
            if d > best:
                best, at = d, i
        if best > tol:
            keep[at] = True
            stack.append((a, at))
            stack.append((at, b))
    return [p for p, k in zip(points, keep) if k]


def area_m2(points):
    s = 0.0
    shrink = math.cos(math.radians(points[0][0]))
    for i in range(len(points) - 1):
        s += points[i][1] * shrink * points[i + 1][0] - points[i + 1][1] * shrink * points[i][0]
    return abs(s) / 2 * 111_200.0 ** 2


def clip_line(points, x0, y0, x1, y1):
    """Pieces of the line (x, y) inside the box, cut at its border (Liang-Barsky per segment)."""
    pieces, cur = [], []
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        dx, dy = bx - ax, by - ay
        t0, t1, ok = 0.0, 1.0, True
        for p, q in ((-dx, ax - x0), (dx, x1 - ax), (-dy, ay - y0), (dy, y1 - ay)):
            if p == 0:
                if q < 0:
                    ok = False
                    break
            else:
                t = q / p
                if p < 0:
                    if t > t1:
                        ok = False
                        break
                    t0 = max(t0, t)
                else:
                    if t < t0:
                        ok = False
                        break
                    t1 = min(t1, t)
        if not ok:
            if cur:
                pieces.append(cur)
                cur = []
            continue
        a = (ax + t0 * dx, ay + t0 * dy)
        b = (ax + t1 * dx, ay + t1 * dy)
        if not cur or cur[-1] != a:
            if cur:
                pieces.append(cur)
            cur = [a]
        cur.append(b)
        if t1 < 1.0:
            pieces.append(cur)
            cur = []
    if cur:
        pieces.append(cur)
    return [p for p in pieces if len(p) >= 2]


def clip_ring(points, x0, y0, x1, y1):
    """The ring (x, y), closed, cut to the box (Sutherland-Hodgman)."""
    ring = points[:-1] if points[0] == points[-1] else points[:]
    for inside, cross in (
        (lambda p: p[0] >= x0, lambda a, b: (x0, a[1] + (b[1] - a[1]) * (x0 - a[0]) / (b[0] - a[0]))),
        (lambda p: p[0] <= x1, lambda a, b: (x1, a[1] + (b[1] - a[1]) * (x1 - a[0]) / (b[0] - a[0]))),
        (lambda p: p[1] >= y0, lambda a, b: (a[0] + (b[0] - a[0]) * (y0 - a[1]) / (b[1] - a[1]), y0)),
        (lambda p: p[1] <= y1, lambda a, b: (a[0] + (b[0] - a[0]) * (y1 - a[1]) / (b[1] - a[1]), y1)),
    ):
        out = []
        for i, cur in enumerate(ring):
            prev = ring[i - 1]
            if inside(cur):
                if not inside(prev):
                    out.append(cross(prev, cur))
                out.append(cur)
            elif inside(prev):
                out.append(cross(prev, cur))
        ring = out
        if len(ring) < 3:
            return None
    return ring + [ring[0]]


def varint(n, out):
    while n >= 0x80:
        out.append((n & 0x7F) | 0x80)
        n >>= 7
    out.append(n)


def zigzag(n, out):
    varint((n << 1) ^ (n >> 63), out)


def encode(pts):
    """Points (lat, lon) as they stand in a file: (count, bytes, south, west, north, east in 1e-5 degrees)."""
    body = bytearray()
    plat = plon = 0
    lats, lons = [], []
    for lat, lon in pts:
        qlat, qlon = round(lat * 1e5), round(lon * 1e5)
        zigzag(qlat - plat, body)
        zigzag(qlon - plon, body)
        plat, plon = qlat, qlon
        lats.append(qlat)
        lons.append(qlon)
    return len(pts), bytes(body), min(lats), min(lons), max(lats), max(lons)


def pack(key, stamp, items):
    """A file from encoded objects (kind, name or None, count, bytes, south, west, north, east), in drawing order."""
    names, index = [], {}
    out = bytearray(b"AM\x02")
    raw = key.encode("ascii")
    varint(len(raw), out)
    out += raw
    varint(stamp, out)
    zigzag(min(i[4] for i in items), out)
    zigzag(min(i[5] for i in items), out)
    zigzag(max(i[6] for i in items), out)
    zigzag(max(i[7] for i in items), out)
    body = bytearray()
    for kind, name, count, blob, *_ in items:
        nid = 0
        if name:
            if name not in index:
                names.append(name)
                index[name] = len(names)
            nid = index[name]
        varint(kind, body)
        varint(nid, body)
        varint(count, body)
        body += blob
    varint(len(names), out)
    for n in names:
        raw = n.encode("utf-8")
        varint(len(raw), out)
        out += raw
    varint(len(items), out)
    out += body
    return out


def write(path, key, stamp, features):
    """features: (kind, name or None, [(lat, lon)])."""
    features = sorted(features, key=lambda f: ORDER[f[0]])
    out = pack(key, stamp, [(kind, name) + encode(pts) for kind, name, pts in features])
    Path(path).write_bytes(out)
    return len(out)


def read(src):
    data = json.loads(Path(src).read_text())
    stamp = calendar.timegm(time.strptime(data["osm3s"]["timestamp_osm_base"], "%Y-%m-%dT%H:%M:%SZ"))
    return data["elements"], stamp


def ways(elements):
    """(tags, [(lat, lon)]) of every way and of the outer ways of relations."""
    for el in elements:
        tags = el.get("tags", {})
        if el["type"] == "way" and "geometry" in el:
            yield tags, [(p["lat"], p["lon"]) for p in el["geometry"] if p]
        elif el["type"] == "relation":
            for m in el.get("members", []):
                if m.get("type") == "way" and m.get("role") == "outer" and "geometry" in m:
                    yield tags, [(p["lat"], p["lon"]) for p in m["geometry"] if p]


def length_km(points):
    total = 0.0
    for (alat, alon), (blat, blon) in zip(points, points[1:]):
        dy = (blat - alat) * 111.2
        dx = (blon - alon) * 111.2 * math.cos(math.radians((alat + blat) / 2))
        total += math.hypot(dx, dy)
    return total


def chain(lines):
    """Joins lines that meet end to end, so that a road of many short pieces becomes a few long ones."""
    ends = {}
    for i, line in enumerate(lines):
        ends.setdefault(line[0], []).append(i)
        ends.setdefault(line[-1], []).append(i)
    used, out = [False] * len(lines), []
    for i, line in enumerate(lines):
        if used[i]:
            continue
        used[i] = True
        cur = list(line)
        for at_end in (True, False):
            while True:
                tip = cur[-1] if at_end else cur[0]
                nxt = next((j for j in ends.get(tip, []) if not used[j]), None)
                # Only where exactly two pieces meet: at a junction the lines stay apart.
                if nxt is None or len(ends[tip]) != 2:
                    break
                used[nxt] = True
                piece = list(lines[nxt])
                if at_end:
                    cur += piece[1:] if piece[0] == tip else piece[::-1][1:]
                else:
                    cur = (piece[:-1] if piece[-1] == tip else piece[::-1][:-1]) + cur
        out.append(cur)
    return out


def base(src, dst):
    elements, stamp = read(src)
    features = []
    rivers = {}
    roads = []
    for tags, pts in ways(elements):
        if len(pts) < 2:
            continue
        if tags.get("highway") in ("motorway", "trunk"):
            roads.append(pts)
        elif tags.get("waterway") == "river" and "name" in tags:
            rivers.setdefault(tags["name"], []).append(pts)
    # Only the rivers one knows from a map of the country: those that are long.
    for name, parts in rivers.items():
        if sum(length_km(p) for p in parts) >= 150:
            for p in chain(parts):
                features.append((11, name, simplify(p, 180)))
    for p in chain(roads):
        if length_km(p) >= 1.5:
            features.append((1, None, simplify(p, 180)))
    places = []
    for el in elements:
        tags = el.get("tags", {})
        if el["type"] == "node" and tags.get("place") in ("city", "town") and "name" in tags:
            digits = "".join(ch for ch in tags.get("population", "") if ch.isdigit())
            places.append((int(digits) if digits else 0, 21 if tags["place"] == "city" else 20, tags["name"], [(el["lat"], el["lon"])]))
    # Small before large: the app places names from the end, so the largest cities win the room.
    for _, kind, name, pts in sorted(places, key=lambda p: p[0]):
        features.append((kind, name, pts))
    size = write(dst, "base", stamp, features)
    print("%d Objekte, %d Bytes" % (len(features), size))


def pick(tags, pts):
    """What a way or ring becomes on the map: (level, kind, name or None, points); None if it is left out."""
    kind = kind_of(tags)
    if not kind or len(pts) < 2:
        return None
    name = tags.get("name")
    if is_area(kind):
        if pts[0] != pts[-1] or len(pts) < 4:
            return None
        size = area_m2(pts)
        big = size >= {10: 20_000, 12: 100_000, 13: 200_000}[kind]
        if not big and size < (400 if kind == 10 else 2500):
            return None
        pts = simplify(pts, 10 if big else 4)
        if len(pts) < 4:
            return None
        return REGION if big else NEAR, kind, name if kind == 10 else None, pts
    if kind in (1, 2, 6) or (kind == 11 and tags.get("waterway") == "river"):
        return REGION, kind, name if kind != 6 else None, simplify(pts, 6)
    return NEAR, kind, name if kind in (3, 4, 11) else None, simplify(pts, 2.5)


def place_level(tags):
    """The level a named place is shown on; None if the node is not one."""
    if "place" not in tags or "name" not in tags:
        return None
    return NEAR if tags["place"] in ("quarter", "neighbourhood") else REGION


def cut(z, kind, pts):
    """The pieces of an object per tile of level z: (x, y, [(lat, lon)])."""
    n = 1 << z
    xy = [(mx(lon) * n, my(lat) * n) for lat, lon in pts]
    xs = [p[0] for p in xy]
    ys = [p[1] for p in xy]
    x0, x1, y0, y1 = int(min(xs)), int(max(xs)), int(min(ys)), int(max(ys))
    if x0 == x1 and y0 == y1:
        # Most objects lie in one tile: nothing to cut.
        yield x0, y0, pts
        return
    for tx in range(x0, x1 + 1):
        for ty in range(y0, y1 + 1):
            if is_area(kind):
                ring = clip_ring(xy, tx, ty, tx + 1, ty + 1)
                parts = [ring] if ring else []
            else:
                parts = clip_line(xy, tx, ty, tx + 1, ty + 1) if len(xy) > 1 else [xy]
            for part in parts:
                yield tx, ty, [(lat_of(y / n), lon_of(x / n)) for x, y in part]


def outside(z, tx, ty, frame, whole):
    """True if the tile is not wanted: with `whole` it must lie in the frame entirely, otherwise touch it."""
    south, west, north, east = frame
    n = 1 << z
    w, e, t, b = lon_of(tx / n), lon_of((tx + 1) / n), lat_of(ty / n), lat_of((ty + 1) / n)
    if whole:
        return w < west or e > east or t > north or b < south
    return e < west or w > east or b > north or t < south


def report(written):
    total = 0
    for z, sizes in written.items():
        if sizes:
            print("Ebene %d: %d Kacheln, %d bis %d Bytes, Mittel %d, zusammen %d" % (z, len(sizes), min(sizes), max(sizes), sum(sizes) // len(sizes), sum(sizes)))
            total += sum(sizes)
    print("zusammen %d Bytes" % total)


def tiles(src, out_dir, frame=None):
    elements, stamp = read(src)
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    per_tile = {}  # (z, x, y) -> features

    def put(z, kind, name, pts):
        for tx, ty, geo in cut(z, kind, pts):
            per_tile.setdefault((z, tx, ty), []).append((kind, name, geo))

    for tags, pts in ways(elements):
        picked = pick(tags, pts)
        if picked:
            put(*picked)
    for el in elements:
        tags = el.get("tags", {})
        if el["type"] == "node" and place_level(tags):
            put(place_level(tags), 20, tags["name"], [(el["lat"], el["lon"])])

    written = {REGION: [], NEAR: []}
    for (z, tx, ty), features in sorted(per_tile.items()):
        if frame and outside(z, tx, ty, frame, whole=True):
            continue
        written[z].append(write(out_dir / ("%d-%d-%d" % (z, tx, ty)), "%d-%d-%d" % (z, tx, ty), stamp, features))
    report(written)


TILE_NAME = re.compile(r"^(10|12)-\d+-\d+$")
SPILL_BYTES = 48 << 20


def unvarint(buf, pos):
    shift = out = 0
    while True:
        b = buf[pos]
        pos += 1
        out |= (b & 0x7F) << shift
        if b < 0x80:
            return out, pos
        shift += 7


def unzigzag(buf, pos):
    v, pos = unvarint(buf, pos)
    return (v >> 1) ^ -(v & 1), pos


def peak_mb():
    """The most memory this run has held so far (macOS counts bytes, Linux kilobytes)."""
    import resource
    peak = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    return peak >> 20 if sys.platform == "darwin" else peak >> 10


def pbf(src, out_dir, frame=None):
    import osmium  # only this mode needs it

    started = time.time()
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.iterdir():
        if TILE_NAME.match(old.name):
            old.unlink()
    work = Path(str(out_dir).rstrip("/") + ".work")
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)
    filtered = work / "filtered.osm.pbf"

    header = osmium.io.Reader(str(src)).header()
    stand = header.get("osmosis_replication_timestamp") or header.get("timestamp")
    stamp = calendar.timegm(time.strptime(stand, "%Y-%m-%dT%H:%M:%SZ")) if stand else int(os.path.getmtime(src))

    # 1. Only what the map shows, with the nodes and member ways it needs. Three passes over the file, each of them
    #    reads only what it looks at; what is wanted is kept as a set of ids.
    wanted = osmium.IdTracker()
    kept = 0
    shapes = osmium.filter.KeyFilter("highway", "railway", "waterway", "natural", "landuse", "leisure")
    for obj in osmium.FileProcessor(str(src), osmium.osm.WAY | osmium.osm.RELATION).with_filter(shapes):
        if obj.is_way():
            if not kind_of(obj.tags):
                continue
            wanted.add_way(obj.id)
        elif obj.tags.get("type") == "multipolygon" and is_area(kind_of(obj.tags) or 0):
            wanted.add_relation(obj.id)
        else:
            continue
        wanted.add_references(obj)
        kept += 1
    for obj in osmium.FileProcessor(str(src), osmium.osm.NODE).with_filter(osmium.filter.KeyFilter("place")):
        if place_level(obj.tags):
            wanted.add_node(obj.id)
            kept += 1
    wanted.complete_backward_references(str(src), relation_depth=1)
    with osmium.SimpleWriter(str(filtered), overwrite=True) as writer:
        for obj in osmium.FileProcessor(str(src)).with_filter(wanted.id_filter()):
            writer.add(obj)
    del wanted
    print("Auswahl: %d Objekte, %d MB, nach %d s, Speicher bis %d MB" % (kept, filtered.stat().st_size >> 20, time.time() - started, peak_mb()), flush=True)

    # 2. Object by object: cut to tiles, the pieces of a region go to its spill file.
    spill, spilled = {}, 0

    def flush():
        nonlocal spilled
        for region, buf in spill.items():
            with open(work / region, "ab") as f:
                f.write(buf)
        spill.clear()
        spilled = 0

    def put(ident, z, kind, name, pts):
        nonlocal spilled
        for tx, ty, geo in cut(z, kind, pts):
            if frame and outside(z, tx, ty, frame, whole=False):
                continue
            shift = z - REGION
            buf = spill.setdefault("%d-%d" % (tx >> shift, ty >> shift), bytearray())
            before = len(buf)
            count, blob, south, west, north, east = encode(geo)
            raw = name.encode("utf-8") if name else b""
            for v in (z, tx, ty, kind, ident, len(raw)):
                varint(v, buf)
            buf += raw
            for v in (south, west, north, east):
                zigzag(v, buf)
            varint(count, buf)
            varint(len(blob), buf)
            buf += blob
            spilled += len(buf) - before
        if spilled > SPILL_BYTES:
            flush()

    seen = 0
    areas_filter = osmium.filter.KeyFilter("natural", "landuse", "leisure")
    # Nodes without tags only carry the place of a way: they go to the index, not through Python.
    named = osmium.filter.EmptyTagFilter().enable_for(osmium.osm.NODE)
    reader = osmium.FileProcessor(str(filtered)).with_locations("sparse_file_array," + str(work / "nodes.idx")).with_areas(areas_filter).with_filter(named)
    for obj in reader:
        if obj.is_node():
            level = place_level(obj.tags)
            if level:
                put(obj.id * 4, level, 20, obj.tags["name"], [(obj.location.lat, obj.location.lon)])
        elif obj.is_way():
            if not kind_of(obj.tags):
                continue  # a member of a relation, without tags of its own
            picked = pick(obj.tags, [(n.location.lat, n.location.lon) for n in obj.nodes if n.location.valid()])
            if picked:
                put(obj.id * 4 + 1, *picked)
        elif obj.is_area() and not obj.from_way():
            for ring in obj.outer_rings():
                picked = pick(obj.tags, [(n.location.lat, n.location.lon) for n in ring])
                if picked:
                    put(obj.orig_id() * 4 + 2, *picked)
        seen += 1
        if seen % 2_000_000 == 0:
            print("  %d Mio. Objekte gelesen, nach %d s" % (seen // 1_000_000, time.time() - started), flush=True)
    flush()
    del reader
    print("Zerschnitten nach %d s, Speicher bis %d MB" % (time.time() - started, peak_mb()), flush=True)

    # 3. Region by region: sort into drawing order (and by OSM id, so that the same data gives the same bytes) and write.
    written, points, most = {REGION: [], NEAR: []}, 0, 0
    for path in sorted(work.iterdir()):
        if not re.match(r"^\d+-\d+$", path.name):
            continue
        buf = path.read_bytes()
        per_tile, pos = {}, 0
        while pos < len(buf):
            head = []
            for _ in range(6):
                v, pos = unvarint(buf, pos)
                head.append(v)
            z, tx, ty, kind, ident, nlen = head
            name = buf[pos:pos + nlen].decode("utf-8") if nlen else None
            pos += nlen
            box = []
            for _ in range(4):
                v, pos = unzigzag(buf, pos)
                box.append(v)
            count, pos = unvarint(buf, pos)
            blen, pos = unvarint(buf, pos)
            per_tile.setdefault((z, tx, ty), []).append((ORDER[kind], ident, kind, name, count, buf[pos:pos + blen], *box))
            pos += blen
        for (z, tx, ty), items in sorted(per_tile.items()):
            items.sort(key=lambda i: i[:2])  # stable: pieces of one object stay in their order
            key = "%d-%d-%d" % (z, tx, ty)
            out = pack(key, stamp, [i[2:] for i in items])
            (out_dir / key).write_bytes(out)
            written[z].append(len(out))
            n = sum(i[4] for i in items)
            points += n
            most = max(most, n)
        path.unlink()
    shutil.rmtree(work, ignore_errors=True)
    report(written)
    print("Stand der Daten %s, %d Punkte, höchstens %d in einer Kachel, %d s, Speicher bis %d MB" % (time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(stamp)), points, most, time.time() - started, peak_mb()))


if __name__ == "__main__":
    if sys.argv[1] == "base":
        base(sys.argv[2], sys.argv[3])
    else:
        frame = [float(v) for v in sys.argv[4:8]] if len(sys.argv) >= 8 else None
        (pbf if sys.argv[1] == "pbf" else tiles)(sys.argv[2], sys.argv[3], frame)
