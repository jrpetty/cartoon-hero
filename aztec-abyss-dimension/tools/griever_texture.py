#!/usr/bin/env python3
# Regenerate with: python3 tools/griever_texture.py src/main/resources/assets/aztecabyss/textures/entity
"""Paints the Griever's plate and glow textures onto the vanilla spider UV layout.

Spider model boxes (texOffs, size w,h,d):
  head   (32,4)  8x8x8      body0 (0,0) 6x6x6
  body1  (0,12) 10x8x12     legs  (18,0) 16x2x2
Box unwrap at (u,v) with size (w,h,d):
  top (u+d, v) w x d     bottom (u+d+w, v) w x d
  sides row at v+d, height h: left (u) d, front (u+d) w, right (u+d+w) d, back (u+2d+w) w
"""
import random, sys
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else '.'
rng = random.Random(20261004)

SEAM = (11, 12, 15)
DARK = (20, 22, 26)
BASE = (31, 34, 40)
PLATE = (40, 44, 52)
WET = (56, 62, 72)
SHEEN = (104, 114, 128)
METAL_D = (54, 56, 62)
METAL = (76, 80, 88)
METAL_H = (128, 134, 144)
FLESH = (58, 34, 38)
FLESH_H = (86, 50, 52)
SLIME = (60, 74, 48)
RUST = (84, 52, 34)

plate = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
glow = Image.new('RGBA', (64, 32), (0, 0, 0, 0))

def px(img, x, y, c, a=255):
    if 0 <= x < 64 and 0 <= y < 32:
        img.putpixel((x, y), (c[0], c[1], c[2], a))

def jitter(c, amt=6):
    d = rng.randint(-amt, amt)
    return tuple(max(0, min(255, v + d)) for v in c)

def fill(x0, y0, w, h, c, amt=5):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px(plate, x, y, jitter(c, amt))

def faces(u, v, w, h, d):
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'left': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'right': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
    }

def armour(rect, band=3, horizontal=True, base=PLATE):
    """Overlapping plates: each band lit along its leading edge, a seam behind it."""
    x0, y0, w, h = rect
    fill(x0, y0, w, h, base)
    if horizontal:
        for y in range(y0, y0 + h):
            k = (y - y0) % band
            for x in range(x0, x0 + w):
                if k == 0:
                    px(plate, x, y, jitter(WET, 6))
                elif k == band - 1:
                    px(plate, x, y, jitter(SEAM, 3))
    else:
        for x in range(x0, x0 + w):
            k = (x - x0) % band
            for y in range(y0, y0 + h):
                if k == 0:
                    px(plate, x, y, jitter(WET, 6))
                elif k == band - 1:
                    px(plate, x, y, jitter(SEAM, 3))

def slime(rect, n):
    x0, y0, w, h = rect
    for _ in range(n):
        x = rng.randrange(x0, x0 + w)
        y = rng.randrange(y0, y0 + h)
        px(plate, x, y, jitter(SLIME, 8))
        if rng.random() < 0.5 and y + 1 < y0 + h:
            px(plate, x, y + 1, jitter(SLIME, 8))

# --- head --------------------------------------------------------------
head = faces(32, 4, 8, 8, 8)
armour(head['top'], band=3, horizontal=True)
x0, y0, w, h = head['top']
for y in range(y0, y0 + h):          # a crest down the middle of the skull
    px(plate, x0 + 3, y, jitter(METAL_D, 4))
    px(plate, x0 + 4, y, jitter(METAL, 6))
fill(*head['bottom'], FLESH)
for k in ('left', 'right', 'back'):
    armour(head[k], band=4, horizontal=True, base=BASE)
x0, y0, w, h = head['front']
fill(x0, y0, w, h, DARK, 3)
for x in range(x0, x0 + w):          # brow ridge
    px(plate, x, y0, jitter(PLATE, 4))
    px(plate, x, y0 + 1, jitter(WET, 5))
for x in (x0 + 1, x0 + 2, x0 + 5, x0 + 6):   # mandibles, metal, hanging into the jaw
    for y in range(y0 + 5, y0 + 8):
        px(plate, x, y, jitter(METAL if y < y0 + 7 else METAL_H, 4))
for x in (x0 + 3, x0 + 4):
    px(plate, x, y0 + 6, jitter(FLESH_H, 4))
    px(plate, x, y0 + 7, jitter(FLESH, 4))

# --- neck (ribbed) -------------------------------------------------------
neck = faces(0, 0, 6, 6, 6)
for k, r in neck.items():
    armour(r, band=3, horizontal=(k in ('top', 'bottom')), base=BASE)

# --- abdomen -------------------------------------------------------------
belly = faces(0, 12, 10, 8, 12)
x0, y0, w, h = belly['top']
armour(belly['top'], band=3, horizontal=True)
for y in range(y0, y0 + h):          # the spine: a ridge of metal studs
    for x in (x0 + 4, x0 + 5):
        px(plate, x, y, jitter(METAL_D, 4))
    if (y - y0) % 3 == 1:
        px(plate, x0 + 4, y, jitter(METAL_H, 6))
        px(plate, x0 + 5, y, jitter(METAL, 6))
slime(belly['top'], 7)
fill(*belly['bottom'], FLESH, 7)
x0, y0, w, h = belly['bottom']
for y in range(y0 + 1, y0 + h, 3):
    for x in range(x0 + 1, x0 + w - 1):
        px(plate, x, y, jitter(FLESH_H, 6))
for k in ('left', 'right', 'front'):
    armour(belly[k], band=4, horizontal=False)
    slime(belly[k], 4)
x0, y0, w, h = belly['back']
armour(belly['back'], band=3, horizontal=False, base=BASE)
for y in range(y0 + 2, y0 + h):      # the sting, folded against the rear plate
    px(plate, x0 + 4, y, jitter(METAL, 5))
    px(plate, x0 + 5, y, jitter(METAL_D, 5))
px(plate, x0 + 4, y0 + h - 1, METAL_H)
px(plate, x0 + 5, y0 + h - 1, METAL_H)

# --- legs: jointed metal, rust at the joints, a pale claw at each end ----------
legs = faces(18, 0, 16, 2, 2)
for k in ('top', 'bottom', 'front', 'back'):
    x0, y0, w, h = legs[k]
    for x in range(x0, x0 + w):
        t = x - x0
        if t in (0, 1, w - 2, w - 1):
            c = METAL_H
        elif t % 5 == 0:
            c = RUST
        elif t % 5 == 4:
            c = SEAM
        else:
            c = METAL if k in ('top', 'front') else METAL_D
        for y in range(y0, y0 + h):
            px(plate, x, y, jitter(c, 5))
for k in ('left', 'right'):
    fill(*legs[k], METAL_D, 4)

# --- glow: eight eyes, a mouth, and the vents along the spine -----------------
AMBER = (255, 176, 48)
CORE = (255, 236, 170)
EMBER = (226, 92, 24)
BLOOD = (196, 32, 18)
fx, fy = 40, 12                      # the head's front face
for (x, y, c) in [
        (fx + 1, fy + 2, CORE), (fx + 2, fy + 2, AMBER), (fx + 1, fy + 3, AMBER), (fx + 2, fy + 3, EMBER),
        (fx + 5, fy + 2, AMBER), (fx + 6, fy + 2, CORE), (fx + 5, fy + 3, EMBER), (fx + 6, fy + 3, AMBER),
        (fx + 0, fy + 1, EMBER), (fx + 7, fy + 1, EMBER),
        (fx + 3, fy + 1, AMBER), (fx + 4, fy + 1, AMBER),
        (fx + 0, fy + 4, EMBER), (fx + 7, fy + 4, EMBER)]:
    px(glow, x, y, c)
for x in (fx + 3, fx + 4):
    px(glow, x, fy + 6, BLOOD, 220)
x0, y0, w, h = belly['top']
for y in range(y0 + 1, y0 + h, 3):   # vents either side of the spine
    px(glow, x0 + 2, y, BLOOD, 200)
    px(glow, x0 + 7, y, BLOOD, 200)

plate.save(f'{OUT}/griever.png')
glow.save(f'{OUT}/griever_eyes.png')
print('written', OUT)
