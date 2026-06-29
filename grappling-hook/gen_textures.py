#!/usr/bin/env python3
"""Generate placeholder textures for the Grappling Hook mod (no Pillow needed)."""
import struct
import zlib
import os

BASE = os.path.join(os.path.dirname(__file__), "src", "main", "resources",
                    "assets", "grapplinghook")


def write_png(path, pixels):
    height = len(pixels)
    width = len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b""))
    print("wrote", path)


# A metal grappling-claw / anchor shape.
HOOK = [
    ".......MM.......",
    ".......MM.......",
    ".......MM.......",
    ".......MM.......",
    ".....MMMMMM.....",
    ".......MM.......",
    ".......MM.......",
    ".......MM.......",
    ".......MM.......",
    "..M....MM....M..",
    "..MM...MM...MM..",
    "..MM...MM...MM..",
    "..MMM.MMMM.MMM..",
    "...MMMMMMMMMM...",
    "....MMMMMMMM....",
    "......MMMM......",
]
METAL = (170, 176, 188, 255)
EDGE = (96, 102, 112, 255)
CLEAR = (0, 0, 0, 0)


def shade(grid):
    out = []
    h = len(grid)
    w = len(grid[0])
    for y in range(h):
        row = []
        for x in range(w):
            if grid[y][x] != "M":
                row.append(CLEAR)
                continue
            # Darken the bottom/right edges for a touch of depth.
            below_empty = (y + 1 >= h or grid[y + 1][x] != "M")
            right_empty = (x + 1 >= w or grid[y][x + 1] != "M")
            row.append(EDGE if (below_empty or right_empty) else METAL)
        out.append(row)
    return out


write_png(os.path.join(BASE, "textures", "item", "grappling_hook.png"), shade(HOOK))

# Icon: the hook on a slate background, scaled x8.
BG = (60, 66, 82, 255)
icon = []
for y in range(128):
    row = []
    for x in range(128):
        ch = HOOK[y // 8][x // 8]
        row.append(METAL if ch == "M" else BG)
    icon.append(row)
write_png(os.path.join(BASE, "icon.png"), icon)
