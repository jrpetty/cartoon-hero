#!/usr/bin/env python3
"""Generate the 10 Mob Trumps category card backgrounds (146x78 PNGs)."""
import os
from PIL import Image, ImageDraw

W, H = 146, 78
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "bg")
os.makedirs(OUT, exist_ok=True)

BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def clamp(v):
    return max(0, min(255, int(v)))


def vgrad(img, y0, y1, c0, c1, dither=7):
    """Vertical gradient with ordered dithering so it never bands."""
    px = img.load()
    for y in range(y0, min(y1, H)):
        t = (y - y0) / max(1, (y1 - y0 - 1))
        for x in range(W):
            d = (BAYER[y % 4][x % 4] / 15.0 - 0.5) * dither
            px[x, y] = tuple(clamp(c0[i] + (c1[i] - c0[i]) * t + d) for i in range(3))


def glow(img, cx, cy, r, color, strength):
    """Additive radial glow with quadratic falloff."""
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


def rows(draw, pts, color):
    draw.polygon(pts, fill=color)


def save(img, name):
    img.save(os.path.join(OUT, name + ".png"))
    print("wrote", name)


def new():
    return Image.new("RGB", (W, H), (0, 0, 0))


# --- FARM: dawn sky, sun, hills, red barn, fence, wheat ---
def farm():
    img = new()
    d = ImageDraw.Draw(img)
    vgrad(img, 0, 60, (126, 186, 232), (214, 233, 244))
    glow(img, 118, 12, 26, (255, 224, 120), 0.9)
    d.ellipse([112, 6, 124, 18], fill=(255, 240, 170))
    # clouds
    for cx, cy, w in [(28, 10, 16), (60, 16, 12), (95, 8, 10)]:
        d.ellipse([cx, cy, cx + w, cy + 5], fill=(240, 248, 252))
        d.ellipse([cx + 4, cy - 3, cx + w - 2, cy + 3], fill=(246, 251, 253))
    # hills
    d.ellipse([-40, 38, 80, 92], fill=(114, 178, 66))
    d.ellipse([60, 42, 200, 100], fill=(96, 160, 52))
    vgrad(img, 58, H, (88, 148, 46), (66, 116, 34), 5)
    # barn
    d.polygon([(18, 34), (52, 34), (52, 56), (18, 56)], fill=(164, 54, 43))
    d.polygon([(14, 36), (35, 24), (56, 36)], fill=(116, 38, 30))
    d.rectangle([31, 44, 39, 56], fill=(84, 34, 24))
    d.line([(31, 44), (39, 56)], fill=(126, 52, 38))
    d.line([(39, 44), (31, 56)], fill=(126, 52, 38))
    d.rectangle([22, 38, 27, 43], fill=(240, 238, 220))
    # fence
    for fx in range(4, W - 2, 12):
        d.rectangle([fx, 62, fx + 1, 70], fill=(122, 92, 56))
    d.rectangle([0, 63, W, 64], fill=(134, 102, 62))
    d.rectangle([0, 67, W, 68], fill=(122, 92, 56))
    # wheat tufts
    for wx in range(2, W - 2, 5):
        hgt = 4 + (wx * 7) % 4
        d.line([(wx, H - 1), (wx, H - hgt)], fill=(226, 190, 82))
        blend(img, wx, H - hgt - 1, (240, 214, 120), 0.9)
    save(img, "farm")


# --- CREATURE: layered pine forest with sun rays and fireflies ---
def creature():
    img = new()
    d = ImageDraw.Draw(img)
    vgrad(img, 0, 60, (150, 205, 190), (222, 238, 208))
    glow(img, 30, 10, 30, (255, 245, 190), 0.55)

    def pine(cx, top, wid, hgt, col):
        d.polygon([(cx, top), (cx - wid, top + hgt), (cx + wid, top + hgt)], fill=col)
        d.polygon([(cx, top + hgt * 0.35), (cx - wid * 1.2, top + hgt * 1.35),
                   (cx + wid * 1.2, top + hgt * 1.35)], fill=col)

    for cx in range(6, W, 22):        # far row, hazy
        pine(cx, 18 + (cx % 7), 8, 14, (116, 165, 130))
    for cx in range(-4, W + 8, 26):   # mid row
        pine(cx + 9, 24 + (cx % 5), 10, 18, (74, 128, 84))
    vgrad(img, 56, H, (64, 118, 56), (42, 84, 38), 5)
    for cx in range(2, W, 30):        # near row, dark
        pine(cx, 30 + (cx % 6), 13, 24, (38, 82, 46))
        d.rectangle([cx - 1, 52 + (cx % 6), cx + 1, 62 + (cx % 6)], fill=(66, 46, 30))
    # fireflies
    for fx, fy in [(24, 44), (58, 38), (90, 46), (120, 40), (72, 58), (108, 60)]:
        glow(img, fx, fy, 4, (255, 240, 140), 0.9)
        blend(img, fx, fy, (255, 252, 200), 1.0)
    save(img, "creature")


# --- VILLAGE: dusk, lit cottages, path, lamp post ---
def village():
    img = new()
    d = ImageDraw.Draw(img)
    vgrad(img, 0, 58, (238, 180, 120), (250, 226, 178))
    glow(img, 118, 16, 24, (255, 214, 130), 0.8)
    d.ellipse([113, 11, 123, 21], fill=(255, 236, 176))
    # distant hills
    d.ellipse([-30, 40, 90, 80], fill=(150, 158, 92))
    d.ellipse([70, 44, 190, 84], fill=(136, 146, 84))
    vgrad(img, 54, H, (128, 138, 76), (96, 106, 58), 5)

    def house(x, y, w, h, wall, roofc):
        d.rectangle([x, y, x + w, y + h], fill=wall)
        d.polygon([(x - 3, y), (x + w // 2, y - 8), (x + w + 3, y)], fill=roofc)
        wy = y + 3
        d.rectangle([x + 3, wy, x + 7, wy + 4], fill=(255, 214, 110))   # lit window
        d.rectangle([x + w - 7, wy, x + w - 3, wy + 4], fill=(255, 214, 110))
        d.rectangle([x + w // 2 - 2, y + h - 8, x + w // 2 + 2, y + h], fill=(88, 58, 34))

    house(14, 38, 30, 20, (226, 208, 168), (140, 74, 44))
    house(96, 42, 26, 17, (214, 196, 152), (112, 60, 38))
    # path
    d.polygon([(58, H), (66, 58), (86, 58), (100, H)], fill=(168, 132, 84))
    for py in range(60, H, 4):
        d.point((72 + (py % 6), py), fill=(140, 108, 66))
    # lamp post with glow
    d.rectangle([52, 40, 53, 58], fill=(52, 40, 30))
    glow(img, 53, 38, 9, (255, 220, 130), 1.0)
    d.rectangle([51, 36, 55, 40], fill=(255, 232, 160))
    save(img, "village")


# --- AQUATIC: deep ocean, god rays, kelp, coral, bubbles ---
def aquatic():
    img = new()
    d = ImageDraw.Draw(img)
    vgrad(img, 0, H, (74, 172, 218), (10, 52, 96))
    # god rays
    ray = Image.new("L", (W, H), 0)
    rd = ImageDraw.Draw(ray)
    for rx in [18, 52, 88, 122]:
        rd.polygon([(rx, 0), (rx + 12, 0), (rx + 30, H), (rx + 6, H)], fill=46)
    img.paste(Image.new("RGB", (W, H), (210, 240, 255)), (0, 0), ray)
    # sea floor
    vgrad(img, 66, H, (56, 96, 88), (40, 72, 62), 5)
    for sx in range(0, W, 3):
        blend(img, sx, 66 + (sx % 3), (80, 120, 100), 0.5)
    # kelp
    for kx, ph in [(14, 0), (30, 2), (104, 1), (132, 3)]:
        for ky in range(66, 28 - ph * 4, -2):
            off = 2 if (ky // 4 + ph) % 2 == 0 else -1
            d.rectangle([kx + off, ky, kx + off + 2, ky + 2], fill=(46, 122, 74))
    # coral: a branching red fan and an orange brain dome
    d.rectangle([60, 56, 62, 68], fill=(224, 96, 88))
    d.line([(61, 60), (55, 54)], fill=(224, 96, 88), width=2)
    d.line([(61, 62), (67, 55)], fill=(236, 122, 104), width=2)
    d.line([(56, 55), (54, 51)], fill=(236, 122, 104))
    d.line([(66, 56), (69, 52)], fill=(224, 96, 88))
    d.ellipse([72, 62, 84, 70], fill=(240, 158, 84))
    for bx in range(74, 83, 3):
        d.arc([bx - 2, 63, bx + 2, 69], 180, 360, fill=(214, 128, 62))
    # bubbles
    for bx, by, r in [(24, 40, 2), (28, 28, 1), (118, 44, 2), (114, 30, 1), (80, 20, 1)]:
        d.ellipse([bx - r, by - r, bx + r, by + r], outline=(200, 235, 250))
        blend(img, bx - 1, by - 1, (240, 252, 255), 0.8)
    save(img, "aquatic")


# --- UNDEAD: moonlit graveyard, fog, dead tree, bats ---
def undead():
    img = new()
    d = ImageDraw.Draw(img)
    vgrad(img, 0, H, (30, 38, 74), (56, 42, 72))
    glow(img, 32, 16, 22, (200, 210, 235), 0.7)
    d.ellipse([25, 9, 39, 23], fill=(226, 231, 240))
    d.ellipse([28, 11, 34, 17], fill=(204, 210, 224))  # crater shading
    # stars
    for sx, sy in [(70, 8), (92, 14), (118, 6), (134, 18), (56, 22), (106, 24)]:
        blend(img, sx, sy, (230, 235, 245), 0.9)
    # ground
    vgrad(img, 58, H, (44, 36, 54), (28, 22, 36), 5)
    # dead tree
    d.line([(112, 60), (112, 34)], fill=(20, 16, 24), width=2)
    d.line([(112, 42), (102, 32)], fill=(20, 16, 24))
    d.line([(112, 38), (122, 28)], fill=(20, 16, 24))
    d.line([(122, 28), (128, 26)], fill=(20, 16, 24))
    # graves
    for gx, gy, w, h in [(20, 52, 10, 14), (48, 56, 9, 11), (78, 53, 11, 13)]:
        d.rectangle([gx, gy, gx + w, gy + h], fill=(120, 128, 146))
        d.ellipse([gx, gy - w // 2, gx + w, gy + w // 2 + 2], fill=(120, 128, 146))
        d.rectangle([gx + 2, gy + 2, gx + w - 2, gy + 3], fill=(84, 92, 108))
    # fog bands
    for fy, a in [(60, 0.28), (65, 0.22), (70, 0.16)]:
        for fx in range(W):
            wob = int(2 * ((fx // 7) % 2))
            blend(img, fx, fy + wob, (170, 182, 205), a)
    # bats
    for bx, by in [(84, 16), (96, 10)]:
        d.point((bx, by), fill=(12, 12, 18))
        d.point((bx - 2, by - 1), fill=(12, 12, 18))
        d.point((bx + 2, by - 1), fill=(12, 12, 18))
    save(img, "undead")


# --- MONSTER: cave with stalactites, moss, glowing eyes, ore ---
def monster():
    img = new()
    d = ImageDraw.Draw(img)
    vgrad(img, 0, H, (58, 62, 76), (22, 25, 33))
    # stalactites
    for sx, sw, sh in [(8, 5, 14), (30, 7, 20), (58, 4, 10), (84, 6, 16), (112, 5, 12), (134, 6, 18)]:
        d.polygon([(sx - sw, 0), (sx + sw, 0), (sx, sh)], fill=(38, 41, 52))
    # stalagmites
    for sx, sw, sh in [(20, 6, 12), (70, 5, 9), (124, 7, 13)]:
        d.polygon([(sx - sw, H), (sx + sw, H), (sx, H - sh)], fill=(34, 37, 47))
    # cave floor
    vgrad(img, 68, H, (40, 44, 55), (28, 30, 40), 4)
    # moss
    for mx, my, w in [(10, 66, 14), (52, 70, 18), (100, 67, 16)]:
        d.ellipse([mx, my, mx + w, my + 5], fill=(58, 96, 48))
    # ore sparkles
    for ox, oy, col in [(24, 30, (96, 220, 220)), (98, 44, (96, 220, 220)),
                        (130, 34, (150, 120, 220)), (48, 50, (220, 190, 90))]:
        d.point((ox, oy), fill=col)
        blend(img, ox + 1, oy, col, 0.5)
        blend(img, ox, oy + 1, col, 0.5)
    # a lurking silhouette around the glowing eyes
    d.ellipse([62, 18, 88, 38], fill=(17, 19, 26))
    d.rectangle([66, 30, 84, 44], fill=(17, 19, 26))
    # torch on the left wall
    d.rectangle([13, 36, 15, 44], fill=(92, 66, 40))
    glow(img, 14, 33, 9, (255, 180, 80), 0.9)
    d.rectangle([13, 31, 15, 35], fill=(255, 200, 110))
    # glowing eyes in the dark
    glow(img, 74, 26, 7, (60, 190, 60), 0.5)
    d.rectangle([69, 24, 71, 26], fill=(120, 255, 90))
    d.rectangle([77, 24, 79, 26], fill=(120, 255, 90))
    save(img, "monster")


# --- END: void, stars, obsidian pillars, end stone island ---
def end():
    img = new()
    d = ImageDraw.Draw(img)
    vgrad(img, 0, H, (44, 30, 66), (14, 8, 26))
    # star field
    for i in range(50):
        sx = (i * 41 + 13) % W
        sy = (i * 29 + 7) % (H - 14)
        b = 140 + (i * 37) % 110
        col = (b - 20, b - 35, b) if i % 3 else (b, b - 10, b)
        blend(img, sx, sy, col, 0.9 if i % 4 else 1.0)
    # distant pillars with crystal glints
    for px_, pw, ph in [(24, 5, 34), (58, 7, 44), (108, 6, 38)]:
        d.rectangle([px_ - pw, H - 10 - ph, px_ + pw, H - 10], fill=(24, 16, 36))
        d.rectangle([px_ - pw + 1, H - 10 - ph, px_ - pw + 2, H - 10], fill=(48, 34, 66))
        glow(img, px_, H - 12 - ph, 5, (230, 170, 240), 0.9)
        d.point((px_, H - 12 - ph), fill=(255, 220, 255))
    # end stone island
    d.ellipse([-20, 66, 170, 96], fill=(221, 223, 165))
    d.ellipse([-20, 70, 170, 100], fill=(196, 198, 138))
    for ex in range(0, W, 4):
        blend(img, ex, 67 + (ex % 3), (238, 240, 190), 0.6)
    save(img, "end")


# --- NETHER: crimson cavern, lava lake, basalt, embers ---
def nether():
    img = new()
    d = ImageDraw.Draw(img)
    vgrad(img, 0, H, (66, 18, 16), (128, 40, 24))
    # ceiling
    for sx, sw, sh in [(16, 6, 10), (48, 5, 14), (86, 7, 9), (120, 5, 12)]:
        d.polygon([(sx - sw, 0), (sx + sw, 0), (sx, sh)], fill=(46, 13, 12))
        d.point((sx, sh + 2), fill=(255, 150, 60))  # drip
    # basalt columns
    for bx, bw, bh in [(20, 5, 26), (126, 6, 30)]:
        d.rectangle([bx - bw, H - 14 - bh, bx + bw, H - 12], fill=(40, 38, 46))
        d.rectangle([bx - bw + 1, H - 14 - bh, bx - bw + 2, H - 12], fill=(66, 62, 74))
    # lava lake
    vgrad(img, 62, H, (255, 150, 40), (255, 210, 70), 8)
    d.rectangle([0, 61, W, 62], fill=(255, 236, 150))
    glow(img, 40, 62, 16, (255, 170, 60), 0.7)
    glow(img, 104, 62, 18, (255, 170, 60), 0.7)
    # ember trails
    for ex, ey in [(30, 48), (34, 38), (72, 44), (76, 32), (112, 50), (116, 40), (58, 26)]:
        blend(img, ex, ey, (255, 190, 90), 0.95)
        blend(img, ex, ey + 1, (255, 140, 60), 0.5)
    save(img, "nether")


# --- ILLAGER: storm, rain, dark mansion, ominous banner, lightning ---
def illager():
    img = new()
    d = ImageDraw.Draw(img)
    vgrad(img, 0, 52, (56, 74, 70), (34, 48, 44))
    # lightning flash wedge
    for y in range(0, 30):
        for x in range(96 - y // 2, 104 - y // 3):
            blend(img, x, y, (210, 230, 220), 0.25 - y * 0.007)
    d.line([(100, 2), (96, 12), (101, 12), (95, 26)], fill=(232, 244, 238), width=1)
    # hill + mansion silhouette
    d.ellipse([-30, 44, 190, 96], fill=(28, 38, 32))
    d.rectangle([44, 26, 96, 50], fill=(22, 30, 26))
    d.polygon([(40, 26), (70, 14), (100, 26)], fill=(16, 22, 19))
    for wx in range(50, 92, 12):
        d.rectangle([wx, 32, wx + 4, 37], fill=(120, 150, 110))  # eerie lit windows
    vgrad(img, 58, H, (30, 40, 34), (18, 26, 21), 4)
    # ominous banner
    d.rectangle([116, 28, 130, 52], fill=(30, 42, 38))
    d.rectangle([117, 30, 129, 34], fill=(178, 188, 160))
    d.rectangle([121, 36, 125, 46], fill=(178, 188, 160))
    d.rectangle([115, 27, 131, 28], fill=(70, 56, 34))
    # rain
    for i in range(46):
        rx = (i * 23 + 5) % W
        ry = (i * 37 + 11) % (H - 8)
        for k in range(4):
            blend(img, rx + k // 2, ry + k, (170, 195, 190), 0.30)
    save(img, "illager")


# --- BOSS: dark arena, gold radial, arches, crown, flecks ---
def boss():
    img = new()
    d = ImageDraw.Draw(img)
    vgrad(img, 0, H, (16, 12, 24), (6, 5, 10))
    glow(img, W // 2, 40, 46, (120, 90, 40), 0.55)
    glow(img, W // 2, 40, 24, (200, 150, 60), 0.45)
    # colosseum arches silhouette
    for ax in range(4, W, 24):
        d.rectangle([ax, 30, ax + 14, 64], fill=(12, 10, 18))
        d.ellipse([ax, 24, ax + 14, 38], fill=(12, 10, 18))
        d.rectangle([ax + 2, 34, ax + 12, 64], fill=(24, 19, 34))
        d.ellipse([ax + 2, 28, ax + 12, 40], fill=(24, 19, 34))
    # arena floor
    vgrad(img, 64, H, (34, 27, 44), (20, 16, 28), 4)
    d.rectangle([0, 64, W, 65], fill=(64, 52, 80))
    # crown
    cx = W // 2
    d.polygon([(cx - 9, 16), (cx - 9, 10), (cx - 5, 14), (cx, 7), (cx + 5, 14),
               (cx + 9, 10), (cx + 9, 16)], fill=(255, 208, 84))
    d.rectangle([cx - 9, 16, cx + 9, 19], fill=(255, 208, 84))
    d.point((cx, 9), fill=(255, 245, 200))
    glow(img, cx, 13, 12, (255, 210, 90), 0.5)
    # gold flecks
    for i in range(26):
        fx = (i * 43 + 9) % W
        fy = (i * 59 + 21) % (H - 6)
        blend(img, fx, fy, (255, 213, 74), 0.85 if i % 3 else 0.5)
    save(img, "boss")


farm(); creature(); village(); aquatic(); undead(); monster(); end(); nether(); illager(); boss()

# contact sheet at 3x for inspection
names = ["farm", "creature", "village", "aquatic", "undead",
         "monster", "end", "nether", "illager", "boss"]
sheet = Image.new("RGB", (2 * W * 3 + 30, 5 * H * 3 + 60), (30, 30, 34))
for i, n in enumerate(names):
    im = Image.open(os.path.join(OUT, n + ".png")).resize((W * 3, H * 3), Image.NEAREST)
    sheet.paste(im, (10 + (i % 2) * (W * 3 + 10), 10 + (i // 2) * (H * 3 + 10)))
sheet.save(os.path.join(OUT, "_sheet.png"))
print("sheet done")
