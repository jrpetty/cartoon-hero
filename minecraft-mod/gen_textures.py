#!/usr/bin/env python3
"""Generate placeholder PNG textures for the Cartoon Hero mod.

Pure-Python (no Pillow) so it runs anywhere. Swap these out for real art
whenever you like — the file names/locations are what matter.
"""
import struct
import zlib
import os

BASE = os.path.join(os.path.dirname(__file__), "src", "main", "resources",
                    "assets", "cartoonhero", "textures")


def write_png(path, pixels):
    """pixels: list of rows, each row a list of (r,g,b,a) tuples."""
    height = len(pixels)
    width = len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter type 0 (None) per scanline
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)  # 8-bit RGBA
    idat = zlib.compress(bytes(raw), 9)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b""))
    print("wrote", path)


# ---- Hero Emblem: a gold star on transparent background ----
STAR = [
    "................",
    ".......XX.......",
    ".......XX.......",
    "......XXXX......",
    "......XXXX......",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "...XXXXXXXXXX...",
    "....XXXXXXXX....",
    "....XXXXXXXX....",
    "...XXXX..XXXX...",
    "...XXX....XXX...",
    "..XXX......XXX..",
    "..XX........XX..",
    "................",
    "................",
]
GOLD = (255, 210, 63, 255)
CLEAR = (0, 0, 0, 0)
star_pixels = [[GOLD if ch == "X" else CLEAR for ch in row] for row in STAR]
write_png(os.path.join(BASE, "item", "hero_emblem.png"), star_pixels)


# ---- Cartoon Bricks: staggered teal bricks with dark mortar ----
BRICK = (54, 197, 192, 255)
MORTAR = (27, 122, 119, 255)


def brick_pixel(x, y):
    # Horizontal mortar lines.
    if y % 8 == 0:
        return MORTAR
    # Vertical mortar, staggered every 8 rows.
    offset = 0 if (y // 8) % 2 == 0 else 8
    if (x + offset) % 16 == 0:
        return MORTAR
    return BRICK


brick_pixels = [[brick_pixel(x, y) for x in range(16)] for y in range(16)]
write_png(os.path.join(BASE, "block", "cartoon_bricks.png"), brick_pixels)


# ---- Mod icon: the star scaled up x8 on a comic-blue background ----
BG = (38, 110, 220, 255)
icon = []
for y in range(128):
    row = []
    for x in range(128):
        sx, sy = x // 8, y // 8
        ch = STAR[sy][sx]
        row.append(GOLD if ch == "X" else BG)
    icon.append(row)
ICON = os.path.join(os.path.dirname(__file__), "src", "main", "resources",
                    "assets", "cartoonhero", "icon.png")
write_png(ICON, icon)
