"""The Obsidian Edge item texture.

A macuahuitl: a flat hardwood paddle with prismatic obsidian blades set down
both long edges, a cord-bound haft and a flared pommel.

Three things make it read at 16 pixels:

  * a dead-regular rake - exactly one column of travel every two rows - so the
    stepping is uniform and the eye reads a straight weapon rather than noise;
  * teeth that alternate sides on that same two-row beat, so each edge saws in
    step with the rake instead of fighting it;
  * glass on every edge pixel, with the wood kept dark and desaturated, so the
    silhouette is obsidian and the wood is only what holds it.
"""

import struct, zlib

W = H = 16

PAL = {
    '.': (0, 0, 0, 0),
    'W': (0x96, 0x68, 0x3A, 255),   # haft/paddle, lit face
    'w': (0x6C, 0x47, 0x27, 255),   # haft/paddle, mid
    'v': (0x46, 0x2B, 0x15, 255),   # haft/paddle, shadowed face
    'c': (0xE0, 0xCB, 0x9E, 255),   # binding cord, lit
    'C': (0xA4, 0x8B, 0x5E, 255),   # binding cord, shadowed
    '*': (0xD4, 0xCE, 0xFF, 255),   # glass catching the light
    'O': (0x7A, 0x6E, 0xAE, 255),   # obsidian, lit face
    'o': (0x45, 0x3A, 0x66, 255),   # obsidian, mid
    'x': (0x24, 0x1D, 0x3A, 255),   # obsidian, shadowed face
}

CX = {15: 2, 14: 2, 13: 3, 12: 3, 11: 4, 10: 4, 9: 5, 8: 5,
      7: 6, 6: 6, 5: 7, 4: 7, 3: 8, 2: 8, 1: 9, 0: 9}

HEAD_TOP, HEAD_BOT = 1, 9
COLLAR = 10


def write_png(path, w, h, px):
    def chunk(tag, data):
        c = tag + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw += bytes(px[y][x])
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b'IDAT', zlib.compress(bytes(raw), 9)))
        f.write(chunk(b'IEND', b''))


def build():
    g = [['.'] * W for _ in range(H)]

    def put(y, x, ch):
        if 0 <= x < W and 0 <= y < H:
            g[y][x] = ch

    # --- the head ---------------------------------------------------------
    for y in range(HEAD_TOP, HEAD_BOT + 1):
        c = CX[y]
        put(y, c - 1, 'W'); put(y, c, 'w'); put(y, c + 1, 'v')
        put(y, c - 2, 'O'); put(y, c + 2, 'o')
        if y == HEAD_TOP:
            continue                                  # the tip carries no teeth
        if y % 2 == 0:
            put(y, c - 3, 'O')                        # tooth out of the lit edge
        else:
            put(y, c + 3, 'x')                        # tooth out of the shadowed edge

    # --- the point --------------------------------------------------------
    put(0, CX[0] - 1, 'O'); put(0, CX[0], 'o')

    # --- the collar: head socketed into haft ------------------------------
    c = CX[COLLAR]
    for dx, ch in ((-2, 'O'), (-1, 'o'), (0, 'o'), (1, 'o'), (2, 'x')):
        put(COLLAR, c + dx, ch)

    # --- the haft: two cord bindings, bare wood between, flared pommel ----
    for y, spec in (
        (11, ((-1, 'c'), (0, 'C'), (1, 'C'))),
        (12, ((-1, 'W'), (0, 'w'), (1, 'v'))),
        (13, ((-1, 'c'), (0, 'C'), (1, 'C'))),
        (14, ((-1, 'W'), (0, 'w'), (1, 'v'))),
        (15, ((-1, 'W'), (0, 'w'), (1, 'w'), (2, 'v'))),
    ):
        c = CX[y]
        for dx, ch in spec:
            put(y, c + dx, ch)

    # --- two chips of glass catching the light ----------------------------
    for gy in (8, 4):
        gx = CX[gy] - 2
        if g[gy][gx] in 'Oo':
            g[gy][gx] = '*'
    return g


def to_px(g):
    return [[list(PAL[g[y][x]]) for x in range(W)] for y in range(H)]


def upscale(px, k, pad=2, bg=(0x20, 0x22, 0x28, 255)):
    w, h = W * k + pad * 2, H * k + pad * 2
    out = [[list(bg) for _ in range(w)] for _ in range(h)]
    for y in range(H):
        for x in range(W):
            c = px[y][x]
            if c[3] == 0:
                continue
            for j in range(k):
                for i in range(k):
                    out[pad + y * k + j][pad + x * k + i] = list(c)
    return w, h, out


TARGET = ('src/main/resources/assets/aztecabyss/textures/item/obsidian_edge.png')

if __name__ == '__main__':
    import os, sys

    g = build()
    for y in range(H):
        print('%2d|%s|' % (y, ''.join(g[y])))
    px = to_px(g)
    print('filled', sum(1 for y in range(H) for x in range(W) if px[y][x][3]))

    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    write_png(os.path.join(root, TARGET), W, H, px)
    print('wrote', os.path.join(root, TARGET))

    # Optional: a magnified proof sheet, for looking at what changed.
    if len(sys.argv) > 1:
        w, h, big = upscale(px, 22)
        write_png(sys.argv[1], w, h, big)
        print('wrote', sys.argv[1])
