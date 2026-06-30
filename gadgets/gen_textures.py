#!/usr/bin/env python3
"""Generate placeholder textures for the Gadgets mod (pure Python, no Pillow)."""
import struct
import zlib
import os

BASE = os.path.join(os.path.dirname(__file__), "src", "main", "resources", "assets", "gadgets", "textures")


def write_png(path, pixels):
    h = len(pixels)
    w = len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b""))
    print("wrote", path)


def grid(rows, palette):
    return [[palette[ch] for ch in row] for row in rows]


C = lambda r, g, b, a=255: (r, g, b, a)
CLEAR = (0, 0, 0, 0)

# --- Player Sensor block: dark metal with a glowing eye ---
SENSOR = [
    "MMMMMMMMMMMMMMMM",
    "MddddddddddddddM",
    "Mdd..........ddM",
    "Md...EEEEEE...dM",
    "Md..EEEEEEEE..dM",
    "Md..EE.WW.EE..dM",
    "Md..EE.WW.EE..dM",
    "Md..EEEEEEEE..dM",
    "Md..EEEEEEEE..dM",
    "Md..EE.WW.EE..dM",
    "Md..EE.WW.EE..dM",
    "Md...EEEEEE...dM",
    "Mdd..........ddM",
    "MddddddddddddddM",
    "MMMMMMMMMMMMMMMM",
    "MMMMMMMMMMMMMMMM",
]
sensor_pal = {"M": C(40, 42, 48), "d": C(70, 74, 82), ".": C(55, 58, 66),
              "E": C(220, 60, 60), "W": C(255, 200, 120)}
write_png(os.path.join(BASE, "block", "player_sensor.png"), grid(SENSOR, sensor_pal))

# --- Filter Hopper block: dark metal funnel with a slot ---
HOPPER = [
    "MMMMMMMMMMMMMMMM",
    "MmmmmmmmmmmmmmmM",
    "Mm..........mmM" + "",
    "Mm.kkkkkkkkk.mM",
    "Mm.k.......k.mM",
    "Mm.k.YYYYY.k.mM",
    "Mm.k.Y...Y.k.mM",
    "Mm.kk.....kk.mM",
    "Mm..kk...kk..mM",
    "Mm...kk.kk...mM",
    "Mm....kkk....mM",
    "Mm.....k.....mM",
    "Mm...........mM",
    "MmmmmmmmmmmmmmM",
    "MMMMMMMMMMMMMMMM",
    "MMMMMMMMMMMMMMMM",
]
# normalize widths to 16
HOPPER = [(r + "M" * 16)[:16] for r in HOPPER]
hopper_pal = {"M": C(40, 42, 48), "m": C(70, 74, 82), ".": C(55, 58, 66),
              "k": C(30, 32, 36), "Y": C(120, 200, 255)}
write_png(os.path.join(BASE, "block", "filter_hopper.png"), grid(HOPPER, hopper_pal))

# --- Rope Arrow item: arrow with a coil of rope ---
ROPE = [
    "................",
    ".............ww.",
    "............wwww",
    "...........ww.ww",
    "..........ww..ww",
    ".........ww..ww.",
    "ssss....ww..ww..",
    "ssssssssww.ww...",
    "ssssssssww.ww...",
    "ssss....ww..ww..",
    ".........ww..ww.",
    "..........ww..ww",
    "...........ww.ww",
    "............wwww",
    ".............ww.",
    "................",
]
rope_pal = {".": CLEAR, "s": C(120, 120, 130), "w": C(170, 120, 60)}
write_png(os.path.join(BASE, "item", "rope_arrow.png"), grid(ROPE, rope_pal))

# --- Light Arrow item: arrow with a glowing tip ---
LIGHT = [
    "................",
    "................",
    "............GGG.",
    "...........GYYYG",
    "..........GYWWYG",
    "sss......GYWWWYG",
    "ssssssssssYWWWYG",
    "ssssssssssYWWWYG",
    "sss......GYWWWYG",
    "..........GYWWYG",
    "...........GYYYG",
    "............GGG.",
    "................",
    "................",
    "................",
    "................",
]
light_pal = {".": CLEAR, "s": C(120, 120, 130),
             "G": C(255, 220, 90), "Y": C(255, 240, 160), "W": C(255, 255, 235)}
write_png(os.path.join(BASE, "item", "light_arrow.png"), grid(LIGHT, light_pal))
