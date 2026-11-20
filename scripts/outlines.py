#!/usr/bin/env python3
"""Erzeugt die Umrisse der Gemeinden und Kreise für die Karte (ADR 0121 §7, §11): offline, ohne fremden Kartendienst.

Quelle: Bundesamt für Kartographie und Geodäsie (BKG), Verwaltungsgebiete 1:250 000 (VG250), Stand 01.01.2026,
https://daten.gdz.bkg.bund.de/produkte/vg/vg250_ebenen_0101/aktuell/vg250_01-01.utm32s.shape.ebenen.zip
Lizenz: Datenlizenz Deutschland – Namensnennung – Version 2.0 (dl-de/by-2-0). Quellenvermerk: © BKG (2026)
dl-de/by-2-0, Daten verändert (vereinfacht, nur Landflächen). Der Vermerk steht in der App (Lizenzen, unter der Karte)
und im README.md (Abschnitt Lizenz). Geladen am 19.11.2026, SHA-256 der Datei:
a21bd17e3616fded6a1cae3311f6b0e8f156ba3939d113b8b760fec5664dd529

Aufruf (nur Python-Standardbibliothek), mit dem entpackten Ordner `vg250_ebenen_0101`:

    python3 scripts/outlines.py <vg250_ebenen_0101> shared/src/commonMain/composeResources/files/regions/outlines.bin

Die Grenzen werden einmal je Grenzstück gespeichert (zwei Nachbarn teilen es sich) und auf etwa TOLERANCE Meter
vereinfacht. Aufbau der Datei (alle Zahlen als Varint, Vorzeichen im Zickzack):

    "AO" 1
    Zahl der Grenzstücke, je Stück: Merker (Bit 0 Kreisgrenze, Bit 1 Außengrenze), Zahl der Punkte,
        erster Punkt (Breite, Länge in 1e-5 Grad), dann die Unterschiede zum Punkt davor
    Zahl der Gemeinden, je Gemeinde: Kreisschlüssel (5 Zeichen), Name (Länge, UTF-8),
        Zahl der Ringe, je Ring: Zahl der Grenzstücke, je Stück Nummer + 1, negativ = rückwärts
"""

import math
import struct
import sys
from pathlib import Path

TOLERANCE = 110.0  # metres
MIN_RING_AREA = 40_000.0  # m²: smaller islands and holes are left out, unless they are all a municipality has
LAND = 4  # GF: land with structure; the other values are water areas


def read_dbf(path):
    with open(path, "rb") as f:
        head = f.read(32)
        count, head_len, row_len = struct.unpack("<IHH", head[4:12])
        fields = []
        while True:
            d = f.read(32)
            if d[0] == 0x0D:
                break
            fields.append((d[:11].split(b"\0")[0].decode(), d[16]))
        f.seek(head_len)
        for _ in range(count):
            row = f.read(row_len)
            pos, out = 1, {}
            for name, size in fields:
                out[name] = row[pos:pos + size].decode("utf-8", "replace").strip()
                pos += size
            yield out


def read_shp(path):
    """Rings of every record: list of lists of (x, y)."""
    data = Path(path).read_bytes()
    pos = 100
    while pos < len(data):
        length = struct.unpack(">i", data[pos + 4:pos + 8])[0] * 2
        rec = data[pos + 8:pos + 8 + length]
        pos += 8 + length
        kind = struct.unpack("<i", rec[:4])[0]
        if kind == 0:
            yield []
            continue
        parts, points = struct.unpack("<ii", rec[36:44])
        starts = list(struct.unpack("<%di" % parts, rec[44:44 + 4 * parts])) + [points]
        xy = struct.unpack("<%dd" % (points * 2), rec[44 + 4 * parts:44 + 4 * parts + 16 * points])
        yield [[(xy[2 * i], xy[2 * i + 1]) for i in range(starts[p], starts[p + 1])] for p in range(parts)]


def utm32_to_wgs84(x, y):
    a, f = 6378137.0, 1 / 298.257222101
    e2 = f * (2 - f)
    k0 = 0.9996
    e1 = (1 - math.sqrt(1 - e2)) / (1 + math.sqrt(1 - e2))
    m = y / k0
    mu = m / (a * (1 - e2 / 4 - 3 * e2 ** 2 / 64 - 5 * e2 ** 3 / 256))
    p1 = (mu + (3 * e1 / 2 - 27 * e1 ** 3 / 32) * math.sin(2 * mu) + (21 * e1 ** 2 / 16 - 55 * e1 ** 4 / 32) * math.sin(4 * mu)
          + (151 * e1 ** 3 / 96) * math.sin(6 * mu) + (1097 * e1 ** 4 / 512) * math.sin(8 * mu))
    ep2 = e2 / (1 - e2)
    c1 = ep2 * math.cos(p1) ** 2
    t1 = math.tan(p1) ** 2
    n1 = a / math.sqrt(1 - e2 * math.sin(p1) ** 2)
    r1 = a * (1 - e2) / (1 - e2 * math.sin(p1) ** 2) ** 1.5
    d = (x - 500000.0) / (n1 * k0)
    lat = p1 - (n1 * math.tan(p1) / r1) * (d ** 2 / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 ** 2 - 9 * ep2) * d ** 4 / 24
                                           + (61 + 90 * t1 + 298 * c1 + 45 * t1 ** 2 - 252 * ep2 - 3 * c1 ** 2) * d ** 6 / 720)
    lon = (d - (1 + 2 * t1 + c1) * d ** 3 / 6 + (5 - 2 * c1 + 28 * t1 - 3 * c1 ** 2 + 8 * ep2 + 24 * t1 ** 2) * d ** 5 / 120) / math.cos(p1)
    return math.degrees(lat), 9.0 + math.degrees(lon)


def ring_area(ring):
    s = 0.0
    for i in range(len(ring) - 1):
        s += ring[i][0] * ring[i + 1][1] - ring[i + 1][0] * ring[i][1]
    return abs(s) / 2


def simplify(points, tolerance):
    """Douglas-Peucker without recursion; keeps both ends."""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = points[a]
        bx, by = points[b]
        dx, dy = bx - ax, by - ay
        norm = math.hypot(dx, dy)
        best, at = 0.0, -1
        for i in range(a + 1, b):
            px, py = points[i]
            d = abs(dy * (px - ax) - dx * (py - ay)) / norm if norm > 0 else math.hypot(px - ax, py - ay)
            if d > best:
                best, at = d, i
        if best > tolerance:
            keep[at] = True
            stack.append((a, at))
            stack.append((at, b))
    return [p for p, k in zip(points, keep) if k]


def varint(n, out):
    while n >= 0x80:
        out.append((n & 0x7F) | 0x80)
        n >>= 7
    out.append(n)


def zigzag(n, out):
    varint((n << 1) ^ (n >> 63), out)


def main(src, dst):
    src = Path(src)
    rows = list(read_dbf(src / "VG250_GEM.dbf"))
    shapes = list(read_shp(src / "VG250_GEM.shp"))
    assert len(rows) == len(shapes)

    # Rings as lists of vertex keys (decimetres), without the repeated last point.
    towns = []  # (kreis, name, [ring])
    for row, rings in zip(rows, shapes):
        if int(row["GF"] or 0) != LAND:
            continue
        keyed = []
        for ring in rings:
            pts = [(round(x * 10), round(y * 10)) for x, y in ring]
            if pts[0] == pts[-1]:
                pts.pop()
            clean = [p for i, p in enumerate(pts) if p != pts[i - 1]]
            if len(clean) >= 3:
                keyed.append(clean)
        if not keyed:
            continue
        big = max(keyed, key=lambda r: ring_area(r + r[:1]))
        keyed = [r for r in keyed if r is big or ring_area(r + r[:1]) / 100 >= MIN_RING_AREA]
        towns.append((row["ARS"][:5], row["GEN"], keyed))

    # Which rings touch a vertex.
    touch = {}
    ring_id = 0
    ring_town = []
    for t, (_, _, rings) in enumerate(towns):
        for ring in rings:
            for p in ring:
                s = touch.get(p)
                if s is None:
                    touch[p] = (ring_id,)
                elif ring_id not in s:
                    touch[p] = s + (ring_id,)
            ring_town.append(t)
            ring_id += 1

    arcs = {}  # canonical key -> index
    arc_points = []
    arc_sides = []  # set of towns
    out_towns = []
    ring_id = 0
    for t, (kreis, name, rings) in enumerate(towns):
        out_rings = []
        for ring in rings:
            n = len(ring)
            sets = [frozenset(touch[p]) for p in ring]
            cuts = [i for i in range(n) if len(sets[i]) >= 3 or sets[i] > sets[i - 1] or sets[i] > sets[(i + 1) % n]]
            pieces = []
            if not cuts:
                start = min(range(n), key=lambda i: ring[i])
                pieces.append([ring[(start + i) % n] for i in range(n + 1)])
            else:
                for a, b in zip(cuts, cuts[1:] + [cuts[0] + n]):
                    pieces.append([ring[i % n] for i in range(a, b + 1)])
            refs = []
            for piece in pieces:
                closed = piece[0] == piece[-1]
                if closed:
                    fwd = tuple(piece)
                    back = (piece[0],) + tuple(reversed(piece[1:-1])) + (piece[0],)
                    rev = back < fwd
                else:
                    rev = (piece[-1], piece[-2]) < (piece[0], piece[1])
                canon = tuple(reversed(piece)) if rev else tuple(piece)
                key = (canon[0], canon[-1], canon[1], canon[len(canon) // 2], len(canon))
                idx = arcs.get(key)
                if idx is None:
                    idx = len(arc_points)
                    arcs[key] = idx
                    arc_points.append(canon)
                    arc_sides.append(set())
                arc_sides[idx].add(t)
                refs.append(-(idx + 1) if rev else idx + 1)
            out_rings.append(refs)
            ring_id += 1
        out_towns.append((kreis, name, out_rings))

    out = bytearray(b"AO\x01")
    varint(len(arc_points), out)
    total = 0
    for pts, sides in zip(arc_points, arc_sides):
        kreise = {towns[t][0] for t in sides}
        flags = (1 if len(kreise) > 1 or len(sides) == 1 else 0) | (2 if len(sides) == 1 else 0)
        metres = [(x / 10.0, y / 10.0) for x, y in pts]
        simple = simplify(metres, TOLERANCE)
        if metres[0] == metres[-1] and len(simple) < 4:
            simple = simplify(metres, TOLERANCE / 4)
            if len(simple) < 4:
                simple = metres
        deg = [utm32_to_wgs84(x, y) for x, y in simple]
        q = [(round(lat * 1e5), round(lon * 1e5)) for lat, lon in deg]
        varint(flags, out)
        varint(len(q), out)
        prev = (0, 0)
        for p in q:
            zigzag(p[0] - prev[0], out)
            zigzag(p[1] - prev[1], out)
            prev = p
        total += len(q)
    varint(len(out_towns), out)
    for kreis, name, rings in out_towns:
        out += kreis.encode("ascii")
        raw = name.encode("utf-8")
        varint(len(raw), out)
        out += raw
        varint(len(rings), out)
        for refs in rings:
            varint(len(refs), out)
            for r in refs:
                zigzag(r, out)
    Path(dst).write_bytes(out)
    print("%d Gemeinden, %d Grenzstücke, %d Punkte, %d Bytes" % (len(out_towns), len(arc_points), total, len(out)))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
