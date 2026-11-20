#!/usr/bin/env python3
"""Erzeugt die Töne für „Hilfe rufen“ und den Warnton eigener Kanäle (ADR 0121 §14).

Beide Töne sind hier errechnet, also frei von Rechten Dritter. Aufruf (nur Python-Standardbibliothek, auf dem Mac
zusätzlich `afconvert` für iOS):

    python3 scripts/sounds.py

Schreibt `androidApp/src/main/res/raw/<name>.wav` (22 050 Hz, mono, 16 Bit) und `iosApp/iosApp/Sounds/<name>.caf`.

  sos      „Hilfe rufen“: drei schnelle, steigende Töne, dann eine Pause, zehn Sekunden lang. Dringend und klar
           anders als der Alarm der Kanäle und der amtliche Ton, damit man ohne Hinsehen weiß, dass ein Mensch ruft.
  warnung  eigene und geteilte Kanäle: zwei ruhige, fallende Töne, viermal. Deutlich, aber kein Alarm.
"""

import math
import struct
import subprocess
import wave
from pathlib import Path

RATE = 22050
ROOT = Path(__file__).resolve().parent.parent


def tone(freq, seconds, volume=0.7, overtone=0.25):
    n = int(RATE * seconds)
    out = []
    for i in range(n):
        t = i / RATE
        # Soft start and end, so that nothing clicks.
        edge = min(1.0, i / (RATE * 0.012), (n - i) / (RATE * 0.03))
        v = math.sin(2 * math.pi * freq * t) + overtone * math.sin(2 * math.pi * 2 * freq * t)
        out.append(volume * edge * v / (1 + overtone))
    return out


def silence(seconds):
    return [0.0] * int(RATE * seconds)


def sos():
    samples = []
    while len(samples) < RATE * 10:
        for freq in (784, 988, 1319):
            samples += tone(freq, 0.16, 0.85) + silence(0.05)
        samples += silence(0.45)
    return samples[:RATE * 10]


def warnung():
    samples = []
    for _ in range(4):
        samples += tone(659, 0.32, 0.6, 0.15) + silence(0.06) + tone(523, 0.46, 0.6, 0.15) + silence(0.75)
    return samples


def write(name, samples):
    wav = ROOT / "androidApp/src/main/res/raw" / (name + ".wav")
    with wave.open(str(wav), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples))
    caf = ROOT / "iosApp/iosApp/Sounds" / (name + ".caf")
    subprocess.run(["afconvert", "-f", "caff", "-d", "LEI16@22050", "-c", "1", str(wav), str(caf)], check=True)
    print(name, round(len(samples) / RATE, 2), "s", wav.stat().st_size, caf.stat().st_size)


if __name__ == "__main__":
    write("sos", sos())
    write("warnung", warnung())
