#!/usr/bin/env python3
"""Generate simple 16x16 placeholder textures (and a mod icon) for ClaudeCraft.

Uses only the Python standard library so it runs anywhere. Re-run this if you
want to tweak the colors; the real art can replace these PNGs later.
"""
import struct
import zlib
from pathlib import Path

BASE = Path(__file__).parent / "src/main/resources/assets/claudecraft/textures"


def write_png(path: Path, pixels, w, h):
    """pixels: list of (r,g,b,a) rows-major, length w*h."""
    raw = bytearray()
    for y in range(h):
        raw.append(0)  # filter type 0 (None) per scanline
        for x in range(w):
            r, g, b, a = pixels[y * w + x]
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)  # 8-bit RGBA
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print("wrote", path)


def solid_tile(w, h, base, border, accent=None):
    """A bordered tile with an optional diagonal accent stripe."""
    px = []
    for y in range(h):
        for x in range(w):
            if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                px.append(border)
            elif accent and (x + y) % 6 == 0:
                px.append(accent)
            else:
                px.append(base)
    return px


def round_blob(w, h, fill, outline, bg=(0, 0, 0, 0)):
    """A filled circle-ish blob centered in the tile (for item icons)."""
    cx, cy, r = (w - 1) / 2, (h - 1) / 2, w * 0.42
    px = []
    for y in range(h):
        for x in range(w):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r - 1:
                px.append(fill)
            elif d <= r:
                px.append(outline)
            else:
                px.append(bg)
    return px


# Anthropic-ish warm orange palette.
ORANGE = (217, 119, 87, 255)
ORANGE_DARK = (160, 80, 55, 255)
ORANGE_LIGHT = (240, 160, 120, 255)
YELLOW = (255, 224, 130, 255)
YELLOW_BRIGHT = (255, 245, 200, 255)

write_png(BASE / "item/claude_core.png",
          round_blob(16, 16, ORANGE, ORANGE_DARK), 16, 16)
write_png(BASE / "item/anthropic_apple.png",
          round_blob(16, 16, (200, 60, 60, 255), (120, 30, 30, 255)), 16, 16)
write_png(BASE / "block/claude_block.png",
          solid_tile(16, 16, ORANGE, ORANGE_DARK, ORANGE_LIGHT), 16, 16)
write_png(BASE / "block/glowing_claude_lamp.png",
          solid_tile(16, 16, YELLOW, (200, 170, 80, 255), YELLOW_BRIGHT), 16, 16)

# 128x128 mod icon.
icon = []
for y in range(128):
    for x in range(128):
        t = (x + y) / 254
        r = int(217 + (255 - 217) * t)
        g = int(119 + (224 - 119) * t)
        b = int(87 + (130 - 87) * t)
        icon.append((r, g, b, 255))
write_png(Path(__file__).parent / "src/main/resources/assets/claudecraft/icon.png",
          icon, 128, 128)
