#!/usr/bin/env python3
"""Relight the generated textures: vertical top-light gradient + deterministic
grain. Run once, right after gen_textures.py (re-running the full pipeline is
idempotent because gen_textures.py regenerates from scratch)."""
import os
import struct
import zlib

BASE = os.path.join(os.path.dirname(__file__), "src", "main", "resources", "assets", "gadgets", "textures")
SIG = b"\x89PNG\r\n\x1a\n"


def read_png(path):
    data = open(path, "rb").read()
    assert data[:8] == SIG, path
    pos, idat, w, h = 8, b"", None, None
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + ln]
        pos += 12 + ln
        if tag == b"IHDR":
            w, h = struct.unpack(">II", chunk[:8])
        elif tag == b"IDAT":
            idat += chunk
    raw = zlib.decompress(idat)
    stride = w * 4 + 1
    rows = []
    for y in range(h):
        r = raw[y * stride:(y + 1) * stride]
        assert r[0] == 0, path
        rows.append([(r[1 + 4 * x], r[2 + 4 * x], r[3 + 4 * x], r[4 + 4 * x]) for x in range(w)])
    return rows


def write_png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(SIG + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def polish(rows):
    h = len(rows)
    out = []
    for y, row in enumerate(rows):
        light = 1.06 - (0.12 * y / max(1, h - 1))
        orow = []
        for x, (r, g, b, a) in enumerate(row):
            if a == 0:
                orow.append((r, g, b, a))
                continue
            n = ((x * 73856093 ^ y * 19349663) % 9) - 4
            f = lambda c: max(0, min(255, int(c * light) + n))
            orow.append((f(r), f(g), f(b), a))
        out.append(orow)
    return out


for sub in ("block", "item"):
    d = os.path.join(BASE, sub)
    for name in sorted(os.listdir(d)):
        if name.endswith(".png"):
            p = os.path.join(d, name)
            write_png(p, polish(read_png(p)))
            print("polished", sub + "/" + name)
