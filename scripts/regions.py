#!/usr/bin/env python3
"""Erzeugt die Gebietstabelle für amtliche Warnungen (Kreis nach ungefährem Standort, nur auf dem Handy).

Quelle: Statistisches Bundesamt (Destatis), Gemeindeverzeichnis-Informationssystem GV-ISys,
„Alle politisch selbständigen Gemeinden mit ausgewählten Merkmalen am 31.12.2025 (4. Quartal)“,
https://www.destatis.de/DE/Themen/Laender-Regionen/Regionales/Gemeindeverzeichnis/Administrativ/Archiv/GVAuszugQ/AuszugGV4QAktuell.html
Datei AuszugGV4QAktuell.xlsx (erschienen Januar 2026). Geografische Mittelpunktkoordinaten (WGS 84) der Gemeinden:
Bundesamt für Kartographie und Geodäsie (BKG). © Statistisches Bundesamt (Destatis), 2026; Vervielfältigung und
Verbreitung, auch auszugsweise, mit Quellenangabe gestattet. Die Quellenangabe steht in der App (Lizenz) und im
README.md (Abschnitt Lizenz). Geladen am 04.11.2026, SHA-256 der Datei:
9409b968fdfc58c6e57237db556b48ccd31c845308ba046e0471e3fde149935e

Aufruf (nur Python-Standardbibliothek):

    python3 scripts/regions.py <AuszugGV4QAktuell.xlsx> shared/src/commonMain/composeResources/files/regions

Schreibt zwei Dateien:
  kreise.csv     Kreisschlüssel (5 Stellen des ARS);Name, z. B. „06412;Frankfurt am Main, Stadt“
  gemeinden.csv  je Gemeinde mit Koordinaten: Kreisschlüssel;Breite;Länge in Millionstel Grad, nach Schlüssel sortiert
"""

import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
SHEET = "xl/worksheets/sheet2.xml"  # „Onlineprodukt_Gemeinden…“; sheet 1 is the table of contents.

# Columns of the sheet (rows 3 and 4 of the file).
SATZART, TEXTKENNZEICHEN, LAND, RB, KREIS, NAME, LAENGE, BREITE = "A", "B", "C", "D", "E", "H", "O", "P"


def rows(path):
    """Zeilen des Blatts als {Spalte: Text}."""
    with zipfile.ZipFile(path) as z:
        strings = [
            "".join(t.text or "" for t in si.iter("{%s}t" % NS["m"]))
            for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall("m:si", NS)
        ]
        for _, el in ET.iterparse(z.open(SHEET)):
            if not el.tag.endswith("}row"):
                continue
            row = {}
            for c in el.findall("m:c", NS):
                v = c.find("m:v", NS)
                if v is None:
                    continue
                col = "".join(ch for ch in c.get("r") if ch.isalpha())
                row[col] = strings[int(v.text)] if c.get("t") == "s" else v.text
            yield row
            el.clear()


def kreis_name(name, kind):
    """Name, wie Menschen ihn kennen: „Landkreis Offenbach“ statt nur „Offenbach“, „Frankfurt am Main“ ohne „, Stadt“."""
    if kind in ("41", "42"):  # independent city (kreisfreie Stadt, Stadtkreis)
        return name.split(",")[0].strip()
    if "kreis" in name.lower() or "region" in name.lower():
        return name
    prefix = {"43": "Kreis", "44": "Landkreis", "45": "Regionalverband"}.get(kind)
    return f"{prefix} {name}" if prefix else name


def micro(text):
    return round(float(text.replace(",", ".")) * 1_000_000)


def main(source, target):
    kreise, gemeinden = {}, []
    for row in rows(source):
        satzart = row.get(SATZART, "").strip()
        key = row.get(LAND, "") + row.get(RB, "") + row.get(KREIS, "")
        if satzart == "40" and len(key) == 5:
            kreise[key] = kreis_name(row[NAME].strip(), row.get(TEXTKENNZEICHEN, "").strip())
        elif satzart == "60" and row.get(LAENGE) and row.get(BREITE):
            lat, lon = micro(row[BREITE]), micro(row[LAENGE])
            # Without centre point (0;0): the uninhabited German-Luxembourg territory on Moselle, Sauer and Our.
            if lat and lon:
                gemeinden.append((key, lat, lon))
    missing = {k for k, _, _ in gemeinden} - kreise.keys()
    if missing or not gemeinden:
        sys.exit(f"Unerwarteter Aufbau der Datei: {len(gemeinden)} Gemeinden, Kreise fehlen: {sorted(missing)}")
    out = Path(target)
    out.mkdir(parents=True, exist_ok=True)
    (out / "kreise.csv").write_text("".join(f"{k};{n}\n" for k, n in sorted(kreise.items())), encoding="utf-8")
    (out / "gemeinden.csv").write_text("".join(f"{k};{lat};{lon}\n" for k, lat, lon in sorted(gemeinden)), encoding="utf-8")
    print(f"{len(kreise)} Kreise, {len(gemeinden)} Gemeinden nach {out}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
