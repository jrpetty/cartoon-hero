#!/usr/bin/env python3
"""Generate the 20 Mob Trumps campaign mission banners (170x60 PNGs).

Authored at half size and drawn at 2x in the briefing panel, so every pixel
lands on an exact 2x2 block and the art stays crisp instead of smearing.
Each scene is built from the mission's NAME, not just its category -- Shallow
Graves and The Long Night are both Undead, and they should not look alike.
"""
import os
from PIL import Image, ImageDraw

W, H = 170, 60
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mission")
os.makedirs(OUT, exist_ok=True)

BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def clamp(v):
    return max(0, min(255, int(v)))


def vgrad(img, y0, y1, c0, c1, dither=7):
    px = img.load()
    for y in range(max(0, y0), min(y1, H)):
        t = (y - y0) / max(1, (y1 - y0 - 1))
        for x in range(W):
            d = (BAYER[y % 4][x % 4] / 15.0 - 0.5) * dither
            px[x, y] = tuple(clamp(c0[i] + (c1[i] - c0[i]) * t + d) for i in range(3))


def glow(img, cx, cy, r, color, strength):
    px = img.load()
    for y in range(max(0, cy - r), min(H, cy + r + 1)):
        for x in range(max(0, cx - r), min(W, cx + r + 1)):
            d2 = (x - cx) ** 2 + (y - cy) ** 2
            if d2 >= r * r:
                continue
            a = strength * (1 - (d2 ** 0.5) / r) ** 2
            c = px[x, y]
            px[x, y] = tuple(clamp(c[i] + color[i] * a) for i in range(3))


def blend(img, x, y, color, a):
    if 0 <= x < W and 0 <= y < H:
        px = img.load()
        c = px[x, y]
        px[x, y] = tuple(clamp(c[i] * (1 - a) + color[i] * a) for i in range(3))


def speck(img, seed, count, color, alpha, top=0, bot=H):
    """Deterministic scatter — dust, embers, stars, rain, ash."""
    for i in range(count):
        x = (i * 37 + seed * 13) % W
        y = top + ((i * 53 + seed * 29) % max(1, bot - top))
        blend(img, x, y, color, alpha if i % 3 else alpha * 0.55)


def ridge(d, base, amp, color, seed=0, step=11):
    """A jagged horizon line filled down to the bottom."""
    pts = [(-2, H + 2)]
    x = -2
    i = 0
    while x < W + step:
        h = base + ((i * 7 + seed * 5) % (2 * amp + 1)) - amp
        pts.append((x, h))
        x += step
        i += 1
    pts.append((W + 2, H + 2))
    d.polygon(pts, fill=color)


def trees(d, y0, y1, color, spacing, width, seed=0):
    x = -4
    i = 0
    while x < W + spacing:
        top = y0 + ((i * 5 + seed * 3) % 7)
        d.polygon([(x, y1), (x + width // 2, top), (x + width, y1)], fill=color)
        x += spacing
        i += 1


def pines(d, y1, color, spacing, seed=0):
    x = 0
    i = 0
    while x < W + spacing:
        h = 22 + ((i * 11 + seed * 7) % 12)
        w = 7 + (i + seed) % 4
        for k in range(3):
            ty = y1 - h + k * (h // 3)
            hw = w * (k + 1) // 3 + 1
            d.polygon([(x - hw, ty + h // 3), (x, ty), (x + hw, ty + h // 3)], fill=color)
        x += spacing
        i += 1


def save(img, name):
    img.save(os.path.join(OUT, name + ".png"))
    print("wrote", name)


def new():
    return Image.new("RGB", (W, H), (0, 0, 0))


# ---------------------------------------------------------------- 1 farm dawn
def first_pasture():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, 42, (250, 196, 132), (186, 214, 238))
    glow(img, 132, 14, 30, (255, 210, 120), 1.0)
    d.ellipse([126, 8, 140, 22], fill=(255, 244, 196))
    d.ellipse([-30, 30, 90, 70], fill=(126, 178, 82))
    d.ellipse([70, 34, 200, 78], fill=(104, 158, 66))
    vgrad(img, 42, H, (98, 152, 62), (68, 116, 42), 5)
    # barn
    d.polygon([(22, 26), (48, 26), (48, 44), (22, 44)], fill=(158, 56, 44))
    d.polygon([(18, 27), (35, 17), (52, 27)], fill=(112, 38, 30))
    d.rectangle([32, 34, 38, 44], fill=(78, 32, 24))
    # fence marching to the right
    d.rectangle([0, 44, W, 45], fill=(150, 118, 76))
    d.rectangle([0, 48, W, 49], fill=(134, 104, 66))
    for fx in range(4, W, 13):
        d.rectangle([fx, 41, fx + 2, 53], fill=(160, 126, 82))
    speck(img, 3, 40, (255, 240, 190), 0.35, 0, 30)
    save(img, "first_pasture")


# ------------------------------------------------------- 2 birch woods, dapple
def woodland_wanderers():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, H, (146, 186, 150), (58, 96, 68))
    glow(img, 62, 4, 44, (240, 250, 210), 0.9)
    # three receding bands of woodland, each mistier than the last
    for depth, (col, sp, wdt, top) in enumerate((((96, 132, 104), 9, 3, 2),
                                                 ((74, 108, 84), 13, 4, 6),
                                                 ((52, 82, 62), 19, 6, 10))):
        x = -4 - depth * 3
        i = 0
        while x < W + 12:
            d.rectangle([x, top, x + wdt, 50 - depth * 2], fill=col)
            x += sp + (i % 3)
            i += 1
        for y in range(30 + depth * 5, 40 + depth * 5):
            for xx in range(W):
                blend(img, xx, y, (198, 220, 200), 0.10)
    # four foreground birches, varied thickness and lean
    for bx, bw, lean in ((14, 9, 0), (52, 7, 1), (98, 11, -1), (142, 8, 0)):
        for y in range(0, 56):
            x = bx + (lean * y) // 40
            d.rectangle([x, y, x + bw, y + 1], fill=(232, 236, 220))
            d.rectangle([x, y, x + 2, y + 1], fill=(196, 202, 186))
            d.rectangle([x + bw - 1, y, x + bw, y + 1], fill=(174, 180, 164))
        for ny in range(5, 54, 8 + bw % 3):
            x = bx + (lean * ny) // 40
            d.rectangle([x + 2, ny, x + bw - 2, ny + 2], fill=(58, 64, 52))
    # ferny undergrowth
    vgrad(img, 48, H, (62, 94, 58), (36, 60, 36), 5)
    for i in range(34):
        fx = (i * 11 + 3) % W
        fy = 48 + (i % 5)
        d.polygon([(fx, fy + 6), (fx + 2, fy), (fx + 4, fy + 6)], fill=(78, 116, 66))
    speck(img, 7, 40, (250, 255, 220), 0.45, 2, 44)
    save(img, "woodland_wanderers")


# --------------------------------------------------------- 3 sunlit shallows
def shallow_water():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, 14, (176, 226, 246), (120, 196, 232))
    vgrad(img, 14, H, (74, 168, 214), (26, 96, 152))
    d.rectangle([0, 13, W, 15], fill=(226, 246, 252))
    # light shafts falling from the waterline
    for i, sx in enumerate(range(6, W, 19)):
        for y in range(15, 52):
            a = 0.20 * (1 - (y - 15) / 38.0)
            for k in range(3 + (i % 2)):
                blend(img, sx + (y - 15) // 4 + k, y, (220, 248, 255), a)
    # sea floor and coral
    d.ellipse([-20, 44, 70, 74], fill=(212, 196, 148))
    d.ellipse([50, 46, 190, 78], fill=(200, 184, 138))
    vgrad(img, 52, H, (198, 182, 136), (166, 150, 108), 4)
    for cx, cc in ((28, (226, 96, 120)), (74, (240, 168, 72)), (120, (150, 96, 200)), (152, (86, 190, 158))):
        d.rectangle([cx, 44, cx + 2, 54], fill=cc)
        d.rectangle([cx - 4, 47, cx - 2, 54], fill=cc)
        d.rectangle([cx + 4, 46, cx + 6, 54], fill=cc)
    speck(img, 11, 30, (240, 252, 255), 0.4, 16, 50)
    save(img, "shallow_water")


# ------------------------------------------------------ 4 moonlit graveyard
def shallow_graves():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, 42, (30, 38, 62), (62, 70, 92))
    glow(img, 30, 10, 26, (170, 190, 230), 0.9)
    d.ellipse([24, 4, 38, 18], fill=(228, 236, 248))
    ridge(d, 38, 3, (26, 32, 48), seed=2, step=17)
    # a dead tree leaning over the plot
    d.rectangle([144, 16, 148, 46], fill=(26, 26, 30))
    for by, bl in ((20, -9), (26, 8), (32, -6)):
        d.line([(146, by), (146 + bl, by - 5)], fill=(26, 26, 30), width=2)
    vgrad(img, 42, H, (50, 56, 46), (24, 28, 24), 4)
    # mounds first, then leaning markers of differing shapes
    for i, (gx, lean) in enumerate(((16, -2), (44, 1), (72, -1), (100, 2), (126, -2))):
        d.ellipse([gx - 12, 43, gx + 12, 53], fill=(42, 48, 40))
        d.ellipse([gx - 9, 42, gx + 8, 49], fill=(52, 58, 48))
        top = 28 + (i % 3) * 3
        if i % 2 == 0:
            d.rectangle([gx - 3, top, gx + 3 + lean, 47], fill=(126, 130, 122))
            d.ellipse([gx - 3, top - 4, gx + 3 + lean, top + 4], fill=(126, 130, 122))
        else:
            d.polygon([(gx - 2, top), (gx + 2 + lean, top), (gx + 2 + lean, 47), (gx - 2, 47)],
                      fill=(112, 116, 108))
            d.rectangle([gx - 6, top + 4, gx + 6 + lean, top + 6], fill=(112, 116, 108))
        d.rectangle([gx - 3, 44, gx + 4, 45], fill=(34, 40, 32))
    # clawed earth and creeping fog
    for i in range(18):
        cx = (i * 19 + 7) % W
        d.rectangle([cx, 46 + (i % 4), cx + 3, 47 + (i % 4)], fill=(70, 62, 48))
    for y in range(44, 56):
        for x in range(W):
            blend(img, x, y, (152, 168, 172), 0.17 + 0.02 * ((x + y) % 3))
    speck(img, 13, 40, (206, 218, 234), 0.35, 0, 30)
    save(img, "shallow_graves")


# ---------------------------------------------------------- 5 dust stampede
def stampede():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, 40, (196, 150, 96), (236, 202, 148))
    glow(img, 96, 10, 36, (255, 216, 150), 0.7)
    ridge(d, 36, 3, (154, 116, 74), seed=5, step=21)
    vgrad(img, 40, H, (168, 128, 82), (118, 88, 56), 5)
    # a running herd in three depths, dust boiling behind
    for depth, (col, base, sc) in enumerate((((92, 70, 48), 40, 4), ((66, 48, 34), 46, 6),
                                             ((40, 28, 20), 53, 8))):
        for i in range(6 - depth):
            bx = 6 + i * (26 + depth * 5) + depth * 9
            d.rectangle([bx, base - sc, bx + sc * 2, base], fill=col)
            d.rectangle([bx + sc * 2 - 2, base - sc - 2, bx + sc * 2 + 3, base - sc + 2], fill=col)
            d.rectangle([bx + 1, base, bx + 2, base + sc // 2], fill=col)
            d.rectangle([bx + sc * 2 - 3, base, bx + sc * 2 - 2, base + sc // 2], fill=col)
    for y in range(30, H):
        for x in range(W):
            if (x * 3 + y * 5) % 11 < 3:
                blend(img, x, y, (222, 190, 146), 0.22)
    speck(img, 17, 60, (240, 214, 168), 0.45, 24, H)
    save(img, "stampede")


# --------------------------------------------------------- 6 hissing cavern
def things_that_hiss():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, H, (18, 24, 20), (8, 12, 10))
    glow(img, 85, 34, 52, (30, 96, 46), 0.85)
    glow(img, 85, 34, 22, (74, 176, 84), 0.5)
    # cave mouth framing the glow
    d.polygon([(0, 0), (W, 0), (W, 10), (108, 12), (86, 4), (58, 14), (0, 11)], fill=(10, 14, 12))
    for i in range(10):
        sx = 6 + i * 18
        d.polygon([(sx, 10), (sx + 4, 26 + (i % 3) * 5), (sx + 8, 10)], fill=(12, 17, 13))
        d.polygon([(sx + 6, H), (sx + 10, H - 20 - (i % 4) * 4), (sx + 14, H)], fill=(12, 17, 13))
    # creeper faces receding into the dark
    for cx, cy, s, a in ((84, 30, 5, 1.0), (46, 36, 4, 0.62), (124, 37, 4, 0.62), (20, 41, 3, 0.36)):
        col = tuple(clamp(c * a) for c in (96, 210, 104))
        dk = tuple(clamp(c * a) for c in (10, 24, 12))
        d.rectangle([cx - s * 2, cy - s * 2, cx + s * 2, cy + s * 2], fill=col)
        d.rectangle([cx - s - 1, cy - s, cx - 1, cy], fill=dk)
        d.rectangle([cx + 1, cy - s, cx + s + 1, cy], fill=dk)
        d.rectangle([cx - 1, cy + 1, cx + 1, cy + s], fill=dk)
        d.rectangle([cx - s, cy + s - 1, cx + s, cy + s], fill=dk)
    speck(img, 19, 30, (120, 220, 130), 0.35)
    save(img, "things_that_hiss")


# ------------------------------------------------------- 7 dark pines, eyes
def tooth_and_claw():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, 46, (44, 56, 62), (24, 34, 40))
    glow(img, 140, 8, 26, (150, 176, 190), 0.55)
    pines(d, 50, (26, 38, 36), 13, seed=1)
    pines(d, 55, (16, 25, 24), 17, seed=4)
    vgrad(img, 52, H, (20, 28, 24), (10, 15, 13), 4)
    # pairs of eyes at three depths
    for ex, ey, gap, col in ((30, 44, 3, (240, 196, 90)), (66, 47, 3, (240, 196, 90)),
                             (100, 43, 2, (200, 226, 240)), (132, 48, 3, (240, 150, 90)),
                             (14, 50, 2, (200, 226, 240))):
        d.rectangle([ex, ey, ex + 1, ey + 1], fill=col)
        d.rectangle([ex + gap, ey, ex + gap + 1, ey + 1], fill=col)
        glow(img, ex + gap // 2, ey, 5, tuple(c // 3 for c in col), 0.5)
    speck(img, 23, 24, (170, 200, 210), 0.25, 0, 40)
    save(img, "tooth_and_claw")


# ---------------------------------------------------------- 8 market at dusk
def trading_post():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, 34, (244, 168, 104), (120, 108, 152))
    glow(img, 22, 26, 30, (255, 190, 110), 0.85)
    ridge(d, 32, 2, (92, 78, 96), seed=3, step=25)
    vgrad(img, 36, H, (128, 106, 78), (86, 70, 50), 4)
    # stalls with striped awnings
    for i, sx in enumerate((10, 54, 100, 140)):
        d.rectangle([sx, 30, sx + 26, 48], fill=(120, 92, 62))
        for k in range(7):
            col = (206, 88, 72) if k % 2 == 0 else (238, 226, 200)
            d.rectangle([sx + k * 4, 26, sx + k * 4 + 3, 30], fill=col)
        d.rectangle([sx + 2, 26, sx + 24, 27], fill=(78, 58, 38))
        d.rectangle([sx + 8, 38, sx + 18, 48], fill=(64, 48, 32))
    # bell tower with a lit lantern
    d.rectangle([118, 8, 134, 40], fill=(104, 82, 56))
    d.polygon([(114, 9), (126, 1), (138, 9)], fill=(140, 60, 46))
    d.rectangle([123, 12, 129, 19], fill=(226, 182, 74))
    glow(img, 126, 15, 12, (255, 200, 90), 0.7)
    for lx in (30, 74, 152):
        d.rectangle([lx, 22, lx + 2, 25], fill=(255, 214, 130))
        glow(img, lx + 1, 23, 9, (255, 200, 100), 0.6)
    save(img, "trading_post")


# ------------------------------------------------------------ 9 deep monument
def the_deep():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, H, (16, 54, 82), (4, 16, 32))
    glow(img, 85, 34, 50, (26, 96, 110), 0.7)
    # prismarine monument stepping back into the murk
    for step, (x0, y0, x1, y1, col) in enumerate((
            (44, 22, 126, 56, (44, 96, 100)),
            (56, 14, 114, 56, (56, 116, 118)),
            (70, 6, 100, 56, (68, 136, 136)))):
        d.rectangle([x0, y0, x1, y1], fill=col)
        for bx in range(x0 + 3, x1 - 2, 7):
            d.rectangle([bx, y0 + 3, bx + 4, y0 + 5], fill=tuple(c + 16 for c in col))
    # the eye
    d.rectangle([80, 20, 90, 30], fill=(226, 208, 140))
    d.rectangle([83, 23, 87, 27], fill=(60, 44, 30))
    glow(img, 85, 25, 20, (200, 170, 90), 0.55)
    vgrad(img, 54, H, (26, 62, 74), (12, 32, 44), 4)
    speck(img, 29, 36, (150, 220, 230), 0.30)
    save(img, "the_deep")


# ---------------------------------------------------------- 10 crimson ashes
def ashlands():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, H, (96, 22, 24), (36, 8, 10))
    glow(img, 85, 52, 60, (220, 90, 30), 0.9)
    # crimson stems
    for i, sx in enumerate((14, 40, 74, 112, 148)):
        top = 6 + (i * 5) % 10
        d.rectangle([sx, top, sx + 5, 48], fill=(86, 34, 44))
        d.ellipse([sx - 8, top - 6, sx + 13, top + 8], fill=(146, 40, 56))
        d.ellipse([sx - 5, top - 3, sx + 10, top + 5], fill=(178, 56, 68))
    # lava lake
    vgrad(img, 46, H, (240, 132, 32), (150, 42, 14), 6)
    for y in range(46, H, 3):
        for x in range(0, W, 2):
            if (x * 5 + y * 3) % 7 < 2:
                blend(img, x, y, (255, 216, 120), 0.5)
    d.rectangle([0, 45, W, 46], fill=(60, 20, 16))
    speck(img, 31, 60, (255, 190, 90), 0.65, 0, 48)
    speck(img, 37, 40, (90, 70, 66), 0.4, 0, 44)
    save(img, "ashlands")


# ------------------------------------------------------- 11 skeleton ridgeline
def the_long_night():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, 44, (10, 12, 30), (28, 30, 58))
    speck(img, 41, 70, (220, 230, 255), 0.7, 0, 34)
    glow(img, 110, 12, 26, (150, 165, 220), 0.75)
    d.ellipse([104, 6, 118, 20], fill=(232, 238, 252))
    d.ellipse([100, 4, 112, 18], fill=(16, 18, 40))
    ridge(d, 40, 4, (14, 16, 34), seed=6, step=15)
    vgrad(img, 46, H, (20, 22, 34), (10, 11, 18), 4)
    # an army cresting the ridge — ranks of bone-pale spears and skulls
    for rank, (base, col, sp) in enumerate((((42), (78, 84, 96), 9), ((48), (54, 60, 70), 7))):
        for i, sx in enumerate(range(2 + rank * 3, W, sp)):
            d.rectangle([sx, base - 9 - (i % 3), sx + 1, base], fill=col)
            d.rectangle([sx - 1, base - 2, sx + 2, base + 1], fill=col)
    speck(img, 43, 20, (170, 185, 220), 0.25, 34, 52)
    save(img, "the_long_night")


# ---------------------------------------------------------- 12 raid in the rain
def raid_bells():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, 40, (48, 44, 54), (74, 66, 70))
    ridge(d, 36, 3, (34, 32, 40), seed=8, step=19)
    vgrad(img, 40, H, (52, 46, 40), (28, 26, 24), 4)
    # burning rooftops
    for i, sx in enumerate((6, 48, 96, 138)):
        d.polygon([(sx, 40), (sx + 14, 28), (sx + 28, 40)], fill=(96, 44, 36))
        d.rectangle([sx + 3, 40, sx + 25, 52], fill=(74, 58, 42))
        fy = 26 - (i % 2) * 3
        d.polygon([(sx + 11, fy), (sx + 14, fy - 8), (sx + 17, fy)], fill=(255, 168, 50))
        d.polygon([(sx + 13, fy), (sx + 14, fy - 4), (sx + 15, fy)], fill=(255, 232, 150))
        glow(img, sx + 14, fy - 4, 16, (230, 130, 40), 0.75)
    # the raid banner
    d.rectangle([80, 4, 82, 40], fill=(58, 48, 40))
    d.rectangle([82, 6, 100, 26], fill=(126, 200, 216))
    d.rectangle([86, 10, 96, 14], fill=(60, 62, 70))
    d.rectangle([88, 16, 94, 22], fill=(60, 62, 70))
    for i in range(70):
        rx = (i * 29 + 3) % W
        ry = (i * 47 + 5) % (H - 6)
        for k in range(4):
            blend(img, rx + k // 2, ry + k, (176, 198, 208), 0.34)
    save(img, "raid_bells")


# ------------------------------------------------------------- 13 the cave-in
def cave_in():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, H, (44, 41, 50), (14, 13, 17))
    # the roof has gone: a ragged hole letting a hard shaft of daylight down
    for y in range(0, H):
        t = y / float(H)
        x0 = int(26 + t * 16)
        x1 = int(60 + t * 52)
        for x in range(x0, min(W, x1)):
            blend(img, x, y, (214, 224, 240), 0.34 * (1 - t * 0.7))
    d.polygon([(24, -2), (62, -2), (66, 7), (44, 12), (28, 8)], fill=(226, 236, 250))
    d.polygon([(28, 2), (58, 1), (60, 6), (44, 9), (32, 6)], fill=(246, 250, 255))
    glow(img, 44, 4, 26, (150, 165, 190), 0.8)
    # jagged remains of the ceiling, hanging down all along the top
    for i in range(11):
        sx = i * 17 - 8
        sh = 5 + (i * 5) % 13
        d.polygon([(sx, -1), (sx + 15, -1), (sx + 10, sh), (sx + 4, sh - 4)], fill=(24, 22, 30))
    # rubble walls closing in from both sides
    for side, x0 in ((0, -8), (1, W - 28)):
        for i in range(9):
            bx = x0 + ((i * 7) % 10) + side * 3
            by = i * 7 - 2
            d.polygon([(bx, by), (bx + 32, by + 2), (bx + 29, by + 9), (bx + 2, by + 8)],
                      fill=(50, 47, 58) if i % 2 else (33, 31, 39))
            d.line([(bx + 2, by + 1), (bx + 30, by + 3)], fill=(72, 68, 82))
    # boulders mid-fall, lit on top by the shaft, streaked above
    for rx, ry, rs in ((74, 14, 8), (108, 30, 10), (58, 38, 6), (132, 11, 6), (90, 47, 7)):
        for k in range(6):
            blend(img, rx + rs // 2, ry - 3 - k * 3, (190, 196, 210), 0.26 - k * 0.04)
        d.polygon([(rx, ry + 3), (rx + rs // 2, ry - rs // 2), (rx + rs + 3, ry + 1),
                   (rx + rs + 1, ry + rs + 1), (rx + 3, ry + rs + 2)], fill=(40, 38, 47))
        d.polygon([(rx + 1, ry + 3), (rx + rs // 2, ry - rs // 3), (rx + rs + 1, ry + 1)],
                  fill=(122, 122, 136))
        d.polygon([(rx + 2, ry + 4), (rx + rs // 2, ry), (rx + rs - 1, ry + 2)],
                  fill=(84, 82, 96))
    # dust piling up on the floor
    vgrad(img, 49, H, (54, 51, 60), (24, 23, 28), 4)
    for y in range(45, H):
        for x in range(W):
            if (x * 5 + y * 7) % 9 < 3:
                blend(img, x, y, (170, 172, 188), 0.18)
    speck(img, 47, 90, (196, 198, 214), 0.34)
    save(img, "cave_in")


# ------------------------------------------------------ 14 forge, iron, emerald
def iron_and_emerald():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, 40, (36, 42, 52), (66, 72, 78))
    glow(img, 85, 30, 52, (60, 190, 120), 0.45)
    ridge(d, 38, 2, (28, 32, 40), seed=4, step=23)
    # village wall with a gate the golem is standing in
    d.rectangle([0, 22, W, 50], fill=(74, 66, 54))
    for bx in range(0, W, 11):
        d.rectangle([bx, 22, bx + 9, 34], fill=(86, 78, 64))
        d.rectangle([bx + 5, 35, bx + 14, 47], fill=(80, 72, 58))
    d.rectangle([0, 20, W, 23], fill=(96, 88, 72))
    d.rectangle([56, 24, 114, 52], fill=(20, 18, 16))
    d.polygon([(56, 26), (85, 14), (114, 26)], fill=(58, 50, 40))
    # the iron golem filling the gateway
    d.rectangle([72, 26, 98, 52], fill=(150, 152, 148))
    d.rectangle([72, 26, 98, 30], fill=(178, 180, 176))
    d.rectangle([78, 18, 92, 28], fill=(162, 164, 160))
    d.rectangle([80, 22, 84, 24], fill=(226, 232, 226))
    d.rectangle([87, 22, 91, 24], fill=(226, 232, 226))
    d.rectangle([64, 28, 72, 48], fill=(132, 134, 130))
    d.rectangle([98, 28, 106, 48], fill=(132, 134, 130))
    for vy in range(32, 48, 5):
        d.rectangle([76, vy, 94, vy + 1], fill=(96, 132, 84))
    # emerald lanterns burning along the wall
    for lx in (16, 40, 130, 154):
        d.rectangle([lx, 12, lx + 6, 20], fill=(46, 208, 118))
        d.rectangle([lx + 1, 13, lx + 5, 19], fill=(160, 252, 200))
        d.rectangle([lx + 2, 8, lx + 4, 12], fill=(70, 64, 52))
        glow(img, lx + 3, 16, 18, (40, 190, 110), 0.75)
    vgrad(img, 50, H, (46, 44, 38), (24, 24, 20), 4)
    speck(img, 53, 26, (120, 250, 180), 0.45, 6, 46)
    save(img, "iron_and_emerald")


# ------------------------------------------------------- 15 fortress over lava
def the_fortress():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, 40, (74, 20, 22), (128, 42, 26))
    glow(img, 85, 56, 62, (240, 120, 30), 0.95)
    # nether brick bridge and arches
    d.rectangle([0, 24, W, 34], fill=(48, 22, 26))
    d.rectangle([0, 24, W, 26], fill=(66, 32, 36))
    for ax in range(-4, W, 30):
        d.rectangle([ax, 4, ax + 12, 26], fill=(42, 19, 23))
        d.rectangle([ax + 2, 6, ax + 10, 24], fill=(56, 26, 31))
        d.polygon([(ax + 2, 8), (ax + 6, 3), (ax + 10, 8)], fill=(42, 19, 23))
    for px in range(6, W, 30):
        d.rectangle([px, 34, px + 5, 48], fill=(40, 18, 22))
    # lava beneath
    vgrad(img, 46, H, (250, 148, 36), (162, 46, 12), 6)
    for y in range(46, H, 2):
        for x in range(0, W, 3):
            if (x * 7 + y * 5) % 9 < 2:
                blend(img, x, y, (255, 226, 140), 0.55)
    speck(img, 59, 50, (255, 196, 100), 0.6, 20, 48)
    save(img, "the_fortress")


# ---------------------------------------------------------- 16 void-touched
def void_touched():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, H, (14, 8, 24), (4, 2, 8))
    speck(img, 61, 90, (190, 170, 230), 0.55)
    glow(img, 85, 32, 44, (86, 40, 130), 0.7)
    # end stone plate hanging in the void
    d.ellipse([18, 34, 152, 58], fill=(216, 222, 172))
    d.ellipse([26, 32, 144, 50], fill=(234, 240, 190))
    d.polygon([(30, 50), (140, 50), (110, 60), (60, 60)], fill=(178, 184, 138))
    # obsidian pillar and a shulker box shape
    d.rectangle([76, 12, 92, 40], fill=(24, 14, 36))
    d.rectangle([78, 12, 84, 40], fill=(38, 22, 54))
    d.rectangle([106, 26, 122, 40], fill=(140, 96, 176))
    d.rectangle([106, 26, 122, 31], fill=(178, 130, 214))
    # purple rifts
    for i in range(7):
        rx = 10 + i * 24
        ry = 6 + (i * 13) % 22
        d.rectangle([rx, ry, rx + 1, ry + 6], fill=(186, 120, 232))
        glow(img, rx, ry + 3, 8, (110, 50, 160), 0.6)
    save(img, "void_touched")


# ------------------------------------------------------------- 17 the mansion
def the_mansion():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, 46, (26, 28, 38), (44, 44, 54))
    glow(img, 24, 10, 22, (140, 150, 190), 0.5)
    pines(d, 52, (14, 20, 22), 15, seed=2)
    # the facade
    d.rectangle([28, 12, 142, 56], fill=(72, 56, 42))
    d.rectangle([28, 12, 142, 16], fill=(56, 42, 32))
    for i in range(5):
        bx = 32 + i * 22
        d.rectangle([bx, 10, bx + 14, 14], fill=(58, 44, 34))
    # lit windows, one dark
    for row, wy in enumerate((22, 38)):
        for i in range(5):
            wx = 36 + i * 22
            lit = not (row == 1 and i == 2)
            d.rectangle([wx, wy, wx + 10, wy + 10], fill=(232, 190, 96) if lit else (26, 22, 20))
            d.rectangle([wx + 4, wy, wx + 5, wy + 10], fill=(70, 54, 40))
            d.rectangle([wx, wy + 4, wx + 10, wy + 5], fill=(70, 54, 40))
            if lit:
                glow(img, wx + 5, wy + 5, 12, (200, 150, 60), 0.45)
    d.rectangle([78, 40, 92, 56], fill=(44, 32, 24))
    for y in range(48, H):
        for x in range(W):
            blend(img, x, y, (120, 130, 140), 0.14)
    save(img, "the_mansion")


# ---------------------------------------------------------- 18 floating isles
def outer_isles():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, H, (18, 10, 30), (6, 3, 12))
    speck(img, 67, 80, (200, 180, 240), 0.5)
    glow(img, 120, 24, 40, (80, 50, 130), 0.6)
    # islands at three depths, each with a chorus stalk
    for x0, y0, w, h, top, bot, ch in ((6, 40, 46, 9, (206, 214, 164), (150, 156, 116), True),
                                       (66, 26, 62, 11, (226, 232, 182), (168, 174, 128), True),
                                       (128, 44, 38, 7, (188, 196, 148), (136, 142, 104), False)):
        d.ellipse([x0, y0, x0 + w, y0 + h], fill=top)
        d.polygon([(x0 + 4, y0 + h // 2), (x0 + w - 4, y0 + h // 2),
                   (x0 + w - 14, y0 + h + 9), (x0 + 12, y0 + h + 9)], fill=bot)
        if ch:
            sx = x0 + w // 2
            d.rectangle([sx, y0 - 10, sx + 2, y0 + 2], fill=(126, 84, 150))
            d.ellipse([sx - 3, y0 - 14, sx + 5, y0 - 8], fill=(168, 118, 190))
    # a distant end city spire
    d.rectangle([148, 14, 154, 44], fill=(96, 92, 62))
    d.polygon([(145, 15), (151, 6), (157, 15)], fill=(140, 118, 176))
    glow(img, 151, 10, 12, (150, 110, 200), 0.5)
    save(img, "outer_isles")


# ------------------------------------------------------------- 19 reckoning
def reckoning():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, 44, (24, 18, 34), (58, 34, 40))
    glow(img, 85, 40, 58, (150, 60, 50), 0.7)
    # lightning forking down the sky
    for bx, seed in ((44, 0), (128, 3)):
        x, y = bx, 0
        while y < 30:
            nx = x + (-3 if (y // 4 + seed) % 2 else 4)
            d.line([(x, y), (nx, y + 4)], fill=(240, 230, 255))
            d.line([(x + 1, y), (nx + 1, y + 4)], fill=(170, 150, 220))
            x, y = nx, y + 4
        glow(img, bx, 14, 22, (120, 100, 190), 0.55)
    ridge(d, 42, 4, (26, 18, 26), seed=9, step=13)
    vgrad(img, 46, H, (44, 30, 32), (20, 14, 16), 4)
    # two boss silhouettes facing off
    d.polygon([(24, 46), (34, 22), (44, 46)], fill=(16, 12, 16))
    for hx in (28, 34, 40):
        d.rectangle([hx, 24, hx + 3, 28], fill=(38, 34, 30))
    d.polygon([(112, 48), (128, 18), (144, 48)], fill=(14, 10, 14))
    d.rectangle([122, 22, 134, 30], fill=(30, 26, 24))
    for ex in (124, 130):
        d.rectangle([ex, 25, ex + 2, 27], fill=(255, 96, 70))
        glow(img, ex + 1, 26, 7, (200, 60, 40), 0.7)
    speck(img, 71, 30, (200, 150, 150), 0.28, 30, H)
    save(img, "reckoning")


# ------------------------------------------------------------ 20 last trump
def last_trump():
    img = new(); d = ImageDraw.Draw(img)
    vgrad(img, 0, H, (22, 14, 10), (6, 4, 4))
    glow(img, 85, 34, 70, (170, 120, 40), 0.85)
    glow(img, 85, 30, 34, (255, 190, 70), 0.55)
    # dragon wings spread across the whole banner
    for side in (-1, 1):
        cx = 85
        for i in range(6):
            x0 = cx + side * (10 + i * 12)
            y0 = 22 - i * 2
            y1 = 38 + i * 3
            d.polygon([(cx + side * 8, 28), (x0, y0), (x0 + side * 10, y1)], fill=(24, 16, 30))
            d.polygon([(cx + side * 8, 28), (x0, y0 + 2), (x0 + side * 7, y1 - 3)], fill=(38, 26, 48))
    # three thrones for the three bosses
    for tx in (44, 85, 126):
        d.rectangle([tx - 8, 34, tx + 8, 56], fill=(46, 34, 22))
        d.rectangle([tx - 8, 30, tx + 8, 35], fill=(70, 52, 32))
        d.rectangle([tx - 2, 22, tx + 2, 32], fill=(198, 154, 60))
    # crown over the centre
    d.polygon([(76, 16), (76, 8), (80, 13), (85, 4), (90, 13), (94, 8), (94, 16)],
              fill=(255, 212, 96))
    d.rectangle([76, 16, 94, 19], fill=(255, 212, 96))
    d.point((85, 6), fill=(255, 250, 220))
    glow(img, 85, 12, 16, (255, 210, 90), 0.7)
    speck(img, 73, 70, (255, 216, 110), 0.7)
    save(img, "last_trump")


BUILDERS = [first_pasture, woodland_wanderers, shallow_water, shallow_graves, stampede,
            things_that_hiss, tooth_and_claw, trading_post, the_deep, ashlands,
            the_long_night, raid_bells, cave_in, iron_and_emerald, the_fortress,
            void_touched, the_mansion, outer_isles, reckoning, last_trump]

for b in BUILDERS:
    b()

names = [b.__name__ for b in BUILDERS]
sheet = Image.new("RGB", (2 * W * 2 + 30, 10 * H * 2 + 110), (26, 26, 30))
for i, n in enumerate(names):
    im = Image.open(os.path.join(OUT, n + ".png")).resize((W * 2, H * 2), Image.NEAREST)
    sheet.paste(im, (10 + (i % 2) * (W * 2 + 10), 10 + (i // 2) * (H * 2 + 10)))
sheet.save(os.path.join(OUT, "_sheet.png"))
print("sheet done ->", os.path.join(OUT, "_sheet.png"))
