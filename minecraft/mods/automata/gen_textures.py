#!/usr/bin/env python3
"""Generate simple 16x16 placeholder textures (and a mod icon) for Automata.

Pure standard library so it runs anywhere. Replace the PNGs under
src/main/resources/assets/automata/textures/ with real art whenever you like.
"""
import math
import struct
import zlib
from pathlib import Path

BASE = Path(__file__).parent / "src/main/resources/assets/automata/textures"


def write_png(path: Path, pixels, w, h):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            r, g, b, a = pixels[y * w + x]
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print("wrote", path)


def machine_tile(w, h, base, border, panel):
    """A bordered machine block face with an inset panel."""
    px = []
    for y in range(h):
        for x in range(w):
            edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
            inset = 4 <= x < w - 4 and 4 <= y < h - 4
            if edge:
                px.append(border)
            elif inset:
                px.append(panel)
            else:
                px.append(base)
    return px


def gear(w, h, metal, dark, bg=(0, 0, 0, 0)):
    cx, cy = (w - 1) / 2, (h - 1) / 2
    px = []
    for y in range(h):
        for x in range(w):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            tooth = 6.6 + 1.4 * math.cos(ang * 8)
            if d <= 2.2:
                px.append(bg)            # hub hole
            elif d <= 3.4:
                px.append(dark)
            elif d <= tooth:
                px.append(metal if (int(math.degrees(ang)) // 22) % 2 == 0 else dark)
            else:
                px.append(bg)
    return px


def frame(w, h, metal, dark, bg=(0, 0, 0, 0)):
    px = []
    for y in range(h):
        for x in range(w):
            edge = x in (0, w - 1) or y in (0, h - 1)
            inner = x in (3, w - 4) or y in (3, h - 4)
            if edge:
                px.append(dark)
            elif inner and 3 <= x <= w - 4 and 3 <= y <= h - 4:
                px.append(metal)
            elif 4 <= x <= w - 5 and 4 <= y <= h - 5:
                px.append(bg)
            else:
                px.append(metal)
    return px


def speckle(w, h, base, light, dark):
    """A grainy, ash-like fill (deterministic, no RNG)."""
    px = []
    for y in range(h):
        for x in range(w):
            n = (x * 7 + y * 13 + x * y) % 5
            if n == 0:
                px.append(light)
            elif n == 1:
                px.append(dark)
            else:
                px.append(base)
    return px


def generator_face(w, h, body, dark, vent):
    """A machine face with a glowing combustion vent in the middle."""
    px = []
    for y in range(h):
        for x in range(w):
            edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
            vent_zone = 5 <= x < w - 5 and 6 <= y < h - 3
            grill = vent_zone and (y % 2 == 0)
            if edge:
                px.append(dark)
            elif grill:
                px.append(dark)
            elif vent_zone:
                px.append(vent)
            else:
                px.append(body)
    return px


def multitool(w, h, head, handle, dark, bg=(0, 0, 0, 0)):
    """A diagonal tool: metal head top-left, handle to bottom-right."""
    px = []
    for y in range(h):
        for x in range(w):
            on_diag = abs((x - y)) <= 1
            head_zone = x + y <= 12
            if on_diag and head_zone:
                px.append(dark if (x + y) % 2 else head)
            elif on_diag:
                px.append(handle)
            elif head_zone and x + y <= 8 and (x <= 6 and y <= 6):
                px.append(head if (x + y) % 3 else dark)
            else:
                px.append(bg)
    return px


STEEL = (120, 128, 140, 255)
STEEL_DK = (70, 76, 86, 255)
ORANGE = (217, 119, 87, 255)
ORANGE_DK = (150, 78, 55, 255)
PANEL = (60, 64, 72, 255)
EMBER = (230, 110, 60, 255)
EMBER_DK = (120, 40, 20, 255)
WOOD = (140, 100, 60, 255)

write_png(BASE / "block/fabricator.png",
          machine_tile(16, 16, STEEL, STEEL_DK, ORANGE), 16, 16)
write_png(BASE / "block/forge_core.png",
          machine_tile(16, 16, STEEL_DK, (40, 44, 50, 255), EMBER), 16, 16)
write_png(BASE / "block/generator.png",
          generator_face(16, 16, (90, 70, 55, 255), (45, 34, 26, 255), EMBER), 16, 16)
# Crusher: heavy steel with a dark grinding aperture. Sawmill: wood body with a steel blade band.
write_png(BASE / "block/crusher.png",
          generator_face(16, 16, STEEL, STEEL_DK, (35, 38, 44, 255)), 16, 16)
write_png(BASE / "block/sawmill.png",
          machine_tile(16, 16, WOOD, (90, 64, 38, 255), STEEL), 16, 16)
# Auto-Miner: steel body with a dark downward drill aperture. Collector: a glassy blue intake.
write_png(BASE / "block/miner.png",
          generator_face(16, 16, STEEL_DK, (38, 42, 48, 255), (150, 150, 156, 255)), 16, 16)
write_png(BASE / "block/collector.png",
          machine_tile(16, 16, (90, 150, 190, 255), (45, 80, 110, 255), (170, 220, 245, 255)), 16, 16)
# Logistics Router: steel body with a green routing core.
write_png(BASE / "block/router.png",
          machine_tile(16, 16, STEEL, STEEL_DK, (70, 190, 110, 255)), 16, 16)
# Item Sorter: steel body with a purple sorting core.
write_png(BASE / "block/sorter.png",
          machine_tile(16, 16, STEEL, STEEL_DK, (150, 90, 200, 255)), 16, 16)
write_png(BASE / "item/iron_gear.png", gear(16, 16, STEEL, STEEL_DK), 16, 16)
write_png(BASE / "item/machine_frame.png", frame(16, 16, STEEL, STEEL_DK), 16, 16)
write_png(BASE / "item/ash.png",
          speckle(16, 16, (110, 110, 114, 255), (170, 170, 174, 255), (70, 70, 74, 255)), 16, 16)
write_png(BASE / "item/iron_dust.png",
          speckle(16, 16, (150, 152, 158, 255), (195, 197, 202, 255), (96, 98, 104, 255)), 16, 16)
write_png(BASE / "item/gold_dust.png",
          speckle(16, 16, (224, 188, 70, 255), (255, 226, 120, 255), (170, 130, 40, 255)), 16, 16)
write_png(BASE / "item/copper_dust.png",
          speckle(16, 16, (200, 116, 80, 255), (236, 150, 110, 255), (150, 78, 50, 255)), 16, 16)
write_png(BASE / "item/pulsar_multitool.png",
          multitool(16, 16, ORANGE, WOOD, ORANGE_DK), 16, 16)
write_png(BASE / "item/logistics_wrench.png",
          multitool(16, 16, (70, 190, 110, 255), STEEL, (40, 120, 70, 255)), 16, 16)

# 128x128 mod icon — an orange-to-steel gradient with a gear stamped in.
icon = []
g = gear(128, 128, (235, 235, 240, 255), (180, 120, 90, 255), bg=None)
for y in range(128):
    for x in range(128):
        gp = g[y * 128 + x]
        if gp is not None:
            icon.append(gp)
        else:
            t = (x + y) / 254
            icon.append((int(217 - 100 * t), int(119 - 50 * t), int(87 + 20 * t), 255))
write_png(Path(__file__).parent / "src/main/resources/assets/automata/icon.png",
          icon, 128, 128)
